package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads a legacy MCEdit/pre-1.13 {@code .schematic} (gzipped NBT: {@code Blocks}/{@code Data} byte
 * arrays with numeric ids, {@code Width}/{@code Height}/{@code Length} shorts) and converts it to a
 * modern {@link StructureTemplate} — the vanilla {@code .nbt} representation.
 *
 * <p>This is the one-time conversion the whole schematic pipeline funnels through: once a legacy
 * schematic is a {@code StructureTemplate}, it loads, places and re-saves through native code. Block
 * ids are mapped by {@link LegacyBlocks}; air is dropped so the placed structure overlays terrain
 * rather than punching a box of air around itself.
 */
public final class LegacySchematic {

    public final int width;
    public final int height;
    public final int length;
    private final byte[] blocks;
    private final byte[] data;
    /** Block-entity nbt to attach, keyed by {@link #index}. Currently just sign text. */
    private final Map<Integer, CompoundTag> blockEntities;

    private LegacySchematic(int width, int height, int length, byte[] blocks, byte[] data,
            Map<Integer, CompoundTag> blockEntities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.data = data;
        this.blockEntities = blockEntities;
    }

    public static LegacySchematic read(InputStream in) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        int w = tag.getShort("Width").orElse((short) 0);
        int h = tag.getShort("Height").orElse((short) 0);
        int l = tag.getShort("Length").orElse((short) 0);
        byte[] b = tag.getByteArray("Blocks").orElse(new byte[0]);
        byte[] d = tag.getByteArray("Data").orElse(new byte[0]);
        if (b.length != w * h * l)
            throw new IOException("Schematic Blocks length " + b.length + " != " + w + "x" + h + "x" + l);
        LegacySchematic schem = new LegacySchematic(w, h, l, b, d, new HashMap<>());
        schem.readBlockEntities(tag);
        return schem;
    }

    /**
     * Pull the legacy {@code TileEntities} we can carry into a {@link StructureTemplate}. Only signs
     * for now — the plain {@code Text1..Text4} lines become a modern sign's {@code front_text}.
     * Container contents (chests/furnaces) need a separate item-id mapping and are still left empty.
     */
    private void readBlockEntities(CompoundTag root) {
        ListTag list = root.getList("TileEntities").orElse(null);
        if (list == null)
            return;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag te = list.getCompoundOrEmpty(i);
            if (!"Sign".equals(te.getStringOr("id", "")))
                continue;
            int x = te.getIntOr("x", 0);
            int y = te.getIntOr("y", 0);
            int z = te.getIntOr("z", 0);
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length)
                continue;
            String[] lines = {
                te.getStringOr("Text1", ""), te.getStringOr("Text2", ""),
                te.getStringOr("Text3", ""), te.getStringOr("Text4", "")
            };
            blockEntities.put(index(x, y, z), signNbt(lines));
        }
    }

    /** Modern sign block-entity nbt: {@code front_text.messages} from the four legacy lines. */
    private static CompoundTag signNbt(String[] lines) {
        CompoundTag be = new CompoundTag();
        be.put("front_text", signText(lines));
        be.put("back_text", signText(new String[] { "", "", "", "" }));
        be.putBoolean("is_waxed", false);
        return be;
    }

    private static CompoundTag signText(String[] lines) {
        ListTag messages = new ListTag();
        // Each message is a text component; a bare string decodes as a literal, which is exactly what
        // the plain legacy lines are (pre-1.8 signs stored raw text, not JSON).
        for (int i = 0; i < 4; i++)
            messages.add(StringTag.valueOf(i < lines.length ? lines[i] : ""));
        CompoundTag text = new CompoundTag();
        text.put("messages", messages);
        return text;
    }

    /** MCEdit block ordering: index = (y * length + z) * width + x. */
    private int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    private int idAt(int x, int y, int z) {
        return blocks[index(x, y, z)] & 0xFF;
    }

    private int dataAt(int x, int y, int z) {
        int i = index(x, y, z);
        return i < data.length ? data[i] & 0x0F : 0;
    }

    /** The mapped modern block at a local coordinate (air where the legacy id is 0). */
    public BlockState stateAt(int x, int y, int z) {
        return stateFor(x, y, z, idAt(x, y, z));
    }

    /**
     * Map one block, decoding a door from both of its halves (the hinge lives on the upper block, so
     * a per-block decode can't produce a correct double door). Everything else is a plain id+data map.
     */
    private BlockState stateFor(int x, int y, int z, int id) {
        if (LegacyBlocks.isDoor(id)) {
            int d = dataAt(x, y, z);
            boolean upper = (d & 8) != 0;
            int lowerData, upperData;
            if (upper) {
                upperData = d;
                lowerData = (y > 0 && idAt(x, y - 1, z) == id) ? dataAt(x, y - 1, z) : 0;
            } else {
                lowerData = d;
                upperData = (y + 1 < height && idAt(x, y + 1, z) == id) ? dataAt(x, y + 1, z) : 0;
            }
            return LegacyBlocks.doorState(id, lowerData, upperData, upper);
        }
        return LegacyBlocks.of(id, dataAt(x, y, z));
    }

    /**
     * Build the vanilla structure NBT (the same shape {@link StructureTemplate#save} writes) and load
     * it into a fresh template — so downstream placement and re-saving are all native.
     */
    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter) {
        ListTag palette = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int id = idAt(x, y, z);
                    if (LegacyBlocks.isAir(id))
                        continue;
                    BlockState state = stateFor(x, y, z, id);
                    Integer idx = paletteIndex.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(NbtUtils.writeBlockState(state));
                        paletteIndex.put(state, idx);
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(x, y, z));
                    block.putInt("state", idx);
                    CompoundTag nbt = blockEntities.get(index(x, y, z));
                    if (nbt != null && (id == 63 || id == 68)) // only attach sign nbt to sign blocks
                        block.put("nbt", nbt);
                    blockList.add(block);
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(width, height, length));
        tag.put("palette", palette);
        tag.put("blocks", blockList);
        tag.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        template.load(blockGetter, tag);
        return template;
    }

    private static ListTag intList(int a, int b, int c) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(a));
        list.add(IntTag.valueOf(b));
        list.add(IntTag.valueOf(c));
        return list;
    }
}
