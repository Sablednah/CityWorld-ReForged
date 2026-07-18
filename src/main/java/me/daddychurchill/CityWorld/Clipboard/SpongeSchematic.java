package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads a <b>WorldEdit "Sponge" {@code .schem}</b> (the common modern download format) and converts it
 * to a native {@link StructureTemplate}.
 *
 * <p>Unlike the legacy {@code .schematic} this needs no numeric-id mapping: the palette already names
 * modern block states as strings (e.g. {@code minecraft:deepslate_tile_wall[up=true,...]}), which
 * {@link BlockStateParser} turns straight into a {@link BlockState}. Handles both Sponge v2 (palette
 * and {@code BlockData} at the root) and v3 (nested under a {@code Blocks} compound). Block entities
 * are not carried yet (their Sponge form differs from the template's) — blocks place, contents don't.
 */
public final class SpongeSchematic {

    private final int width;
    private final int height;
    private final int length;
    private final Map<Integer, BlockState> palette;
    private final int[] indices; // palette id per position, (y*length + z)*width + x

    private SpongeSchematic(int width, int height, int length, Map<Integer, BlockState> palette, int[] indices) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.palette = palette;
        this.indices = indices;
    }

    public static SpongeSchematic read(InputStream in) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        // v3 wraps everything in a "Schematic" compound; v2 keeps it at the root.
        CompoundTag root = tag.getCompound("Schematic").orElse(tag);

        int w = root.getShort("Width").orElse((short) 0);
        int h = root.getShort("Height").orElse((short) 0);
        int l = root.getShort("Length").orElse((short) 0);

        // v3: Blocks -> {Palette, Data}; v2: Palette + BlockData at the root.
        CompoundTag blocksTag = root.getCompound("Blocks").orElse(null);
        CompoundTag paletteTag;
        byte[] blockData;
        if (blocksTag != null) {
            paletteTag = blocksTag.getCompound("Palette").orElse(new CompoundTag());
            blockData = blocksTag.getByteArray("Data").orElse(new byte[0]);
        } else {
            paletteTag = root.getCompound("Palette").orElse(new CompoundTag());
            blockData = root.getByteArray("BlockData").orElse(new byte[0]);
        }

        Map<Integer, BlockState> palette = new HashMap<>();
        for (String key : paletteTag.keySet())
            palette.put(paletteTag.getInt(key).orElse(0), parse(key));

        int[] indices = decodeVarints(blockData, w * h * l);
        return new SpongeSchematic(w, h, l, palette, indices);
    }

    private static final Set<String> unknown = ConcurrentHashMap.newKeySet();

    private static BlockState parse(String blockState) {
        try {
            return BlockStateParser.parseForBlock(BuiltInRegistries.BLOCK, blockState, false).blockState();
        } catch (Exception e) {
            if (unknown.add(blockState))
                CityWorldMod.LOGGER.info("SpongeSchematic: could not parse block '{}' (placed as air)", blockState);
            return Blocks.AIR.defaultBlockState();
        }
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

    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter) {
        ListTag paletteList = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    BlockState state = palette.getOrDefault(indices[(y * length + z) * width + x],
                            Blocks.AIR.defaultBlockState());
                    if (state.isAir())
                        continue;
                    Integer idx = paletteIndex.get(state);
                    if (idx == null) {
                        idx = paletteList.size();
                        paletteList.add(NbtUtils.writeBlockState(state));
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
        tag.put("palette", paletteList);
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
