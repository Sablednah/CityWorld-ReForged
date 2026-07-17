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

    private LegacySchematic(int width, int height, int length, byte[] blocks, byte[] data) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.data = data;
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
        return new LegacySchematic(w, h, l, b, d);
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
        return LegacyBlocks.of(idAt(x, y, z), dataAt(x, y, z));
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
                    BlockState state = LegacyBlocks.of(id, dataAt(x, y, z));
                    Integer idx = paletteIndex.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(NbtUtils.writeBlockState(state));
                        paletteIndex.put(state, idx);
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(x, y, z));
                    block.putInt("state", idx);
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
