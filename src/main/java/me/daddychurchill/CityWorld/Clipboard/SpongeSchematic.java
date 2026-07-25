package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads a <b>WorldEdit "Sponge" {@code .schem}</b> (the common modern download format) and converts it
 * to a native {@link StructureTemplate} via {@link Templates} (which data-fixes older files).
 *
 * <p>The palette already names modern block states as strings (e.g.
 * {@code minecraft:deepslate_tile_wall[up=true,...]}); the block ids live in a varint-packed
 * {@code BlockData} array. Handles Sponge v2 (root-level palette/data) and v3 (nested under
 * {@code Blocks}). Block entities (chests, signs, pots, ...) are carried across.
 */
public final class SpongeSchematic {

    private final int width;
    private final int height;
    private final int length;
    private final int dataVersion;
    private final Map<Integer, String> palette; // id -> "minecraft:foo[props]"
    private final int[] indices;                // palette id per position, (y*length + z)*width + x
    private final ListTag blockEntities;

    private SpongeSchematic(int width, int height, int length, int dataVersion, Map<Integer, String> palette,
            int[] indices, ListTag blockEntities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.dataVersion = dataVersion;
        this.palette = palette;
        this.indices = indices;
        this.blockEntities = blockEntities;
    }

    public static SpongeSchematic read(InputStream in) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        CompoundTag root = tag.getCompound("Schematic").orElse(tag); // v3 nests under "Schematic"

        int w = root.getShort("Width").orElse((short) 0);
        int h = root.getShort("Height").orElse((short) 0);
        int l = root.getShort("Length").orElse((short) 0);
        int dv = root.getInt("DataVersion").orElse(0);

        CompoundTag blocksTag = root.getCompound("Blocks").orElse(null); // v3
        CompoundTag paletteTag;
        byte[] blockData;
        ListTag blockEntities;
        if (blocksTag != null) {
            paletteTag = blocksTag.getCompound("Palette").orElse(new CompoundTag());
            blockData = blocksTag.getByteArray("Data").orElse(new byte[0]);
            blockEntities = blocksTag.getList("BlockEntities").orElse(new ListTag());
        } else {
            paletteTag = root.getCompound("Palette").orElse(new CompoundTag());
            blockData = root.getByteArray("BlockData").orElse(new byte[0]);
            blockEntities = root.getList("BlockEntities").orElse(new ListTag());
        }

        Map<Integer, String> palette = new HashMap<>();
        for (String key : paletteTag.keySet())
            palette.put(paletteTag.getInt(key).orElse(0), key);

        return new SpongeSchematic(w, h, l, dv, palette, decodeVarints(blockData, w * h * l), blockEntities);
    }

    /** Sponge {@code BlockData} is a stream of unsigned LEB128 varints, one palette id per block. */
    private static int[] decodeVarints(byte[] data, int count) {
        int[] out = new int[Math.max(count, 0)];
        int pos = 0, i = 0;
        while (i < data.length && pos < out.length) {
            int value = 0, shift = 0, b;
            do {
                b = data[i++] & 0xFF;
                value |= (b & 0x7F) << shift;
                shift += 7;
            } while ((b & 0x80) != 0 && i < data.length);
            out[pos++] = value;
        }
        return out;
    }

    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter, boolean keepAir) {
        Map<Integer, CompoundTag> beByIndex = blockEntities(); // schematic-index -> block entity nbt

        ListTag paletteList = new ListTag();
        Map<String, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int flat = (y * length + z) * width + x;
                    String descriptor = palette.get(indices[flat]);
                    if (descriptor == null)
                        continue;
                    if (!keepAir && Templates.isAir(Templates.nameOf(descriptor)))
                        continue;
                    Integer idx = paletteIndex.get(descriptor);
                    if (idx == null) {
                        idx = paletteList.size();
                        paletteList.add(Templates.blockStateToTag(descriptor));
                        paletteIndex.put(descriptor, idx);
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", Templates.intList(x, y, z));
                    block.putInt("state", idx);
                    CompoundTag be = beByIndex.get(flat);
                    if (be != null)
                        block.put("nbt", be);
                    blockList.add(block);
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("size", Templates.intList(width, height, length));
        tag.put("palette", paletteList);
        tag.put("blocks", blockList);
        tag.put("entities", new ListTag());
        return Templates.build(tag, dataVersion, blockGetter);
    }

    /** Block entities keyed by flat schematic index, in the modern {@code {id, ...}} shape. */
    private Map<Integer, CompoundTag> blockEntities() {
        Map<Integer, CompoundTag> out = new HashMap<>();
        for (int i = 0; i < blockEntities.size(); i++) {
            CompoundTag be = blockEntities.getCompoundOrEmpty(i).copy();
            int[] pos = be.getIntArray("Pos").orElse(null);
            if (pos == null || pos.length != 3)
                continue;
            be.remove("Pos");
            // Sponge names the type "Id"; the structure loader/data-fixer want lowercase "id".
            be.getString("Id").ifPresent(id -> be.putString("id", id));
            be.remove("Id");
            if (pos[0] < 0 || pos[0] >= width || pos[1] < 0 || pos[1] >= height || pos[2] < 0 || pos[2] >= length)
                continue;
            out.put((pos[1] * length + pos[2]) * width + pos[0], be);
        }
        return out;
    }
}
