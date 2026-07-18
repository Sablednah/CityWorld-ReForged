package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
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
 * Reads a <b>Litematica {@code .litematic}</b> and converts it to a native {@link StructureTemplate}.
 *
 * <p>Like a {@code .nbt}/{@code .schem} the palette is already modern block states — here in the
 * vanilla {@code {Name, Properties}} form, so {@link NbtUtils#readBlockState} reads each directly. The
 * work is the packing: block ids live in a bit-packed {@code long[]} (Litematica's own array, whose
 * entries may straddle two longs), and a file can hold several regions, each at its own position and
 * with a possibly-negative size (growing in the negative direction). Regions are stitched into one
 * template against their common minimum corner, with negative axes un-mirrored so the build keeps its
 * intended orientation. Block entities/entities are not carried yet.
 */
public final class LitematicSchematic {

    private record Region(int sx, int sy, int sz, int px, int py, int pz, ListTag palette, long[] blockStates) {}

    private final List<Region> regions;

    private LitematicSchematic(List<Region> regions) {
        this.regions = regions;
    }

    public static LitematicSchematic read(InputStream in) throws IOException {
        CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        CompoundTag regionsTag = root.getCompound("Regions").orElse(new CompoundTag());
        List<Region> regions = new ArrayList<>();
        for (String key : regionsTag.keySet()) {
            CompoundTag rg = regionsTag.getCompound(key).orElse(null);
            if (rg == null)
                continue;
            CompoundTag size = rg.getCompound("Size").orElse(new CompoundTag());
            CompoundTag pos = rg.getCompound("Position").orElse(new CompoundTag());
            regions.add(new Region(
                    size.getInt("x").orElse(0), size.getInt("y").orElse(0), size.getInt("z").orElse(0),
                    pos.getInt("x").orElse(0), pos.getInt("y").orElse(0), pos.getInt("z").orElse(0),
                    rg.getList("BlockStatePalette").orElse(new ListTag()),
                    rg.getLongArray("BlockStates").orElse(new long[0])));
        }
        return new LitematicSchematic(regions);
    }

    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter) {
        // Common minimum corner across all regions, so the stitched template starts at (0,0,0).
        int gMinX = Integer.MAX_VALUE, gMinY = Integer.MAX_VALUE, gMinZ = Integer.MAX_VALUE;
        int gMaxX = Integer.MIN_VALUE, gMaxY = Integer.MIN_VALUE, gMaxZ = Integer.MIN_VALUE;
        for (Region r : regions) {
            int minX = min(r.px, r.sx), minY = min(r.py, r.sy), minZ = min(r.pz, r.sz);
            gMinX = Math.min(gMinX, minX);
            gMinY = Math.min(gMinY, minY);
            gMinZ = Math.min(gMinZ, minZ);
            gMaxX = Math.max(gMaxX, minX + Math.abs(r.sx) - 1);
            gMaxY = Math.max(gMaxY, minY + Math.abs(r.sy) - 1);
            gMaxZ = Math.max(gMaxZ, minZ + Math.abs(r.sz) - 1);
        }
        if (regions.isEmpty() || gMinX > gMaxX)
            return empty(blockGetter);

        ListTag paletteList = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (Region r : regions) {
            BlockState[] states = parsePalette(r.palette, blockGetter);
            if (states.length == 0)
                continue;
            int asx = Math.abs(r.sx), asy = Math.abs(r.sy), asz = Math.abs(r.sz);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, states.length - 1)));
            long mask = (1L << bits) - 1L;
            int minX = min(r.px, r.sx), minY = min(r.py, r.sy), minZ = min(r.pz, r.sz);

            for (int ly = 0; ly < asy; ly++) {
                for (int lz = 0; lz < asz; lz++) {
                    for (int lx = 0; lx < asx; lx++) {
                        long i = (long) ly * asx * asz + (long) lz * asx + lx;
                        int id = bitAt(r.blockStates, i, bits, mask);
                        if (id < 0 || id >= states.length)
                            continue;
                        BlockState state = states[id];
                        if (state.isAir())
                            continue;
                        // Blocks are stored in normal orientation from the region's minimum corner; a
                        // negative Size only moves where that corner is (handled by min()), it does NOT
                        // mirror the stored data. So place straight from the min corner — mirroring here
                        // would flip positions without flipping facings and detach ladders/torches.
                        int cx = minX + lx;
                        int cy = minY + ly;
                        int cz = minZ + lz;

                        Integer idx = paletteIndex.get(state);
                        if (idx == null) {
                            idx = paletteList.size();
                            paletteList.add(NbtUtils.writeBlockState(state));
                            paletteIndex.put(state, idx);
                        }
                        CompoundTag block = new CompoundTag();
                        block.put("pos", intList(cx - gMinX, cy - gMinY, cz - gMinZ));
                        block.putInt("state", idx);
                        blockList.add(block);
                    }
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(gMaxX - gMinX + 1, gMaxY - gMinY + 1, gMaxZ - gMinZ + 1));
        tag.put("palette", paletteList);
        tag.put("blocks", blockList);
        tag.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        template.load(blockGetter, tag);
        return template;
    }

    /** The minimum coordinate on an axis given a position and a (possibly negative) size. */
    private static int min(int pos, int size) {
        return size >= 0 ? pos : pos + size + 1;
    }

    private static BlockState[] parsePalette(ListTag palette, HolderGetter<Block> blockGetter) {
        BlockState[] out = new BlockState[palette.size()];
        for (int i = 0; i < palette.size(); i++)
            out[i] = NbtUtils.readBlockState(blockGetter, palette.getCompoundOrEmpty(i));
        return out;
    }

    /**
     * Read one entry from Litematica's bit array — like the vanilla pre-1.16 packing, entries may
     * straddle a long boundary, so a two-long read is needed when the entry crosses one.
     */
    private static int bitAt(long[] arr, long index, int bits, long mask) {
        long startBit = index * bits;
        int startArr = (int) (startBit >> 6);
        int endArr = (int) (((index + 1) * bits - 1) >> 6);
        int offset = (int) (startBit & 63);
        if (startArr < 0 || startArr >= arr.length)
            return 0;
        if (startArr == endArr)
            return (int) ((arr[startArr] >>> offset) & mask);
        if (endArr >= arr.length)
            return (int) ((arr[startArr] >>> offset) & mask);
        return (int) (((arr[startArr] >>> offset) | (arr[endArr] << (64 - offset))) & mask);
    }

    private StructureTemplate empty(HolderGetter<Block> blockGetter) {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(1, 1, 1));
        tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag());
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
