package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads a <b>Litematica {@code .litematic}</b> and converts it to a native {@link StructureTemplate}
 * via {@link Templates} (which data-fixes older files).
 *
 * <p>The palette is already the vanilla {@code {Name, Properties}} form, so it feeds the structure tag
 * directly. The work is the packing: ids live in a bit-packed {@code long[]} (Litematica's own array,
 * whose entries may straddle two longs), and a file can hold several regions, each at its own position
 * and with a possibly-negative size — the sign only fixes where the region's minimum corner sits; the
 * data itself is stored in normal orientation from that corner. Regions are stitched into one template
 * against their common minimum corner. Tile entities are carried across.
 */
public final class LitematicSchematic {

    private record Region(int sx, int sy, int sz, int px, int py, int pz,
            ListTag palette, long[] blockStates, ListTag tileEntities) {}

    private final int dataVersion;
    private final List<Region> regions;

    private LitematicSchematic(int dataVersion, List<Region> regions) {
        this.dataVersion = dataVersion;
        this.regions = regions;
    }

    public static LitematicSchematic read(InputStream in) throws IOException {
        CompoundTag root = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        int dv = root.getInt("MinecraftDataVersion").orElse(0);
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
                    rg.getLongArray("BlockStates").orElse(new long[0]),
                    rg.getList("TileEntities").orElse(new ListTag())));
        }
        return new LitematicSchematic(dv, regions);
    }

    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter, boolean keepAir) {
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
            return Templates.build(emptyTag(), 0, blockGetter);

        ListTag paletteList = new ListTag();
        Map<CompoundTag, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (Region r : regions) {
            int n = r.palette.size();
            if (n == 0)
                continue;
            CompoundTag[] states = new CompoundTag[n];
            boolean[] air = new boolean[n];
            for (int i = 0; i < n; i++) {
                states[i] = r.palette.getCompoundOrEmpty(i);
                air[i] = Templates.isAir(states[i].getString("Name").orElse(""));
            }
            int asx = Math.abs(r.sx), asy = Math.abs(r.sy), asz = Math.abs(r.sz);
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(Math.max(1, n - 1)));
            long mask = (1L << bits) - 1L;
            int minX = min(r.px, r.sx), minY = min(r.py, r.sy), minZ = min(r.pz, r.sz);
            Map<Long, CompoundTag> be = tileEntities(r, asx, asz);

            for (int ly = 0; ly < asy; ly++) {
                for (int lz = 0; lz < asz; lz++) {
                    for (int lx = 0; lx < asx; lx++) {
                        long flat = (long) ly * asx * asz + (long) lz * asx + lx;
                        int id = bitAt(r.blockStates, flat, bits, mask);
                        if (id < 0 || id >= n || (air[id] && !keepAir))
                            continue;
                        CompoundTag stateTag = states[id];
                        Integer idx = paletteIndex.get(stateTag);
                        if (idx == null) {
                            idx = paletteList.size();
                            paletteList.add(stateTag.copy());
                            paletteIndex.put(stateTag, idx);
                        }
                        CompoundTag block = new CompoundTag();
                        block.put("pos", Templates.intList(minX + lx - gMinX, minY + ly - gMinY, minZ + lz - gMinZ));
                        block.putInt("state", idx);
                        CompoundTag beNbt = be.get(flat);
                        if (beNbt != null)
                            block.put("nbt", beNbt);
                        blockList.add(block);
                    }
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("size", Templates.intList(gMaxX - gMinX + 1, gMaxY - gMinY + 1, gMaxZ - gMinZ + 1));
        tag.put("palette", paletteList);
        tag.put("blocks", blockList);
        tag.put("entities", new ListTag());
        return Templates.build(tag, dataVersion, blockGetter);
    }

    /** Region tile entities keyed by the region-local flat index, in {@code {id, ...}} shape. */
    private static Map<Long, CompoundTag> tileEntities(Region r, int asx, int asz) {
        Map<Long, CompoundTag> out = new HashMap<>();
        for (int i = 0; i < r.tileEntities.size(); i++) {
            CompoundTag te = r.tileEntities.getCompoundOrEmpty(i).copy();
            int x = te.getInt("x").orElse(0);
            int y = te.getInt("y").orElse(0);
            int z = te.getInt("z").orElse(0);
            te.remove("x");
            te.remove("y");
            te.remove("z");
            int asy = Math.abs(r.sy);
            if (x < 0 || x >= asx || y < 0 || y >= asy || z < 0 || z >= asz)
                continue;
            out.put((long) y * asx * asz + (long) z * asx + x, te);
        }
        return out;
    }

    /** The minimum coordinate on an axis given a position and a (possibly negative) size. */
    private static int min(int pos, int size) {
        return size >= 0 ? pos : pos + size + 1;
    }

    /** Read one entry from Litematica's bit array (entries may straddle a long boundary). */
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

    private static CompoundTag emptyTag() {
        CompoundTag tag = new CompoundTag();
        tag.put("size", Templates.intList(1, 1, 1));
        tag.put("palette", new ListTag());
        tag.put("blocks", new ListTag());
        tag.put("entities", new ListTag());
        return tag;
    }
}
