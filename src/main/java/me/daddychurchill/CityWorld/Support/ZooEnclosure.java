package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.ZooCatalog;

/**
 * Draws one chunk-slice of a zoo enclosure from its WORLD-coordinate bounds, so the same code serves both a
 * single-chunk pen and a multi-chunk one — each chunk renders only the part of the pen inside its own 0..15
 * columns. Two styles: a ground-level <em>fenced</em> paddock, or a <em>sunken</em> pit (a lined moat wall
 * with a rail round the top, so visitors look down into it) with a glass-walled viewing bridge across it.
 *
 * <p>Fences are placed with their connections set explicitly ({@link SupportBlocks#setPipeBlock}); worldgen
 * {@code setBlock} skips the neighbour update, so a plain fence would render (and collide) as a loose post.
 */
public final class ZooEnclosure {

    private ZooEnclosure() {}

    public static final int PIT_DEPTH = 8;

    public static void draw(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int streetY,
            boolean sunken, Material ground) {
        draw(chunk, penX1, penX2, penZ1, penZ2, streetY, sunken, ground, Material.OAK_FENCE, 2);
    }

    /** As above with the habitat's {@code fence} block (a fence, iron bars or a pane: anything that connects with
     *  the four boolean sides) {@code fenceHeight} high round a fenced pen. */
    public static void draw(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int streetY,
            boolean sunken, Material ground, Material fence, int fenceHeight) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        int pitFloor = streetY - PIT_DEPTH;
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (wx < penX1 || wx > penX2 || wz < penZ1 || wz > penZ2)
                    continue;
                boolean wall = wx == penX1 || wx == penX2 || wz == penZ1 || wz == penZ2;
                if (!sunken) {
                    chunk.setBlock(lx, streetY, lz, ground);
                    if (wall) {
                        BlockFace[] c = fenceCons(wx, wz, penX1, penX2, penZ1, penZ2);
                        for (int fy = 1; fy <= fenceHeight; fy++)
                            chunk.setPipeBlock(lx, streetY + fy, lz, fence, c);
                    }
                } else if (wall) {
                    for (int wy = pitFloor; wy <= streetY; wy++)
                        chunk.setBlock(lx, wy, lz, Material.POLISHED_ANDESITE); // lined moat wall
                    chunk.setPipeBlock(lx, streetY + 1, lz, fence,
                            fenceCons(wx, wz, penX1, penX2, penZ1, penZ2)); // safety rail
                } else {
                    for (int wy = pitFloor + 1; wy <= streetY; wy++)
                        chunk.setBlock(lx, wy, lz, Material.AIR); // dig the pit
                    chunk.setBlock(lx, pitFloor, lz, ground);
                }
            }
        if (sunken)
            viewingBridge(chunk, penX1, penX2, penZ1, penZ2, streetY);
    }

    /** A 2-wide glass-walled walkway across the middle of a sunken pit, at street level, so visitors can
     *  cross over the exhibit and look down into it from either side. */
    private static void viewingBridge(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int streetY) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        int midZ = (penZ1 + penZ2) / 2; // the bridge runs E-W across the pit at this Z
        for (int lx = 0; lx < 16; lx++) {
            int wx = oX + lx;
            if (wx < penX1 || wx > penX2)
                continue;
            for (int wz = midZ; wz <= midZ + 1; wz++) { // the 2-wide deck
                int lz = wz - oZ;
                if (lz < 0 || lz > 15)
                    continue;
                chunk.setBlock(lx, streetY, lz, Material.SMOOTH_STONE);
                chunk.setBlock(lx, streetY + 1, lz, Material.AIR);
                chunk.setBlock(lx, streetY + 2, lz, Material.AIR);
            }
            for (int wz : new int[] { midZ - 1, midZ + 2 }) { // a glass rail on each side of the deck
                int lz = wz - oZ;
                if (lz < 0 || lz > 15)
                    continue;
                chunk.setBlock(lx, streetY + 1, lz, Material.GLASS);
                chunk.setBlock(lx, streetY + 2, lz, Material.GLASS);
            }
        }
    }

    /** Draw just the connected fence ring of a pen (world bounds, inclusive) from {@code y1} to {@code y2}.
     *  For the small single-chunk pens, whose fence would otherwise be loose posts. */
    public static void fencePerimeter(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int y1, int y2) {
        fencePerimeter(chunk, penX1, penX2, penZ1, penZ2, y1, y2, Material.OAK_FENCE);
    }

    /** As above in the habitat's {@code fence} block. */
    public static void fencePerimeter(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int y1, int y2,
            Material fence) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (!isPerimeter(wx, wz, penX1, penX2, penZ1, penZ2))
                    continue;
                BlockFace[] c = fenceCons(wx, wz, penX1, penX2, penZ1, penZ2);
                for (int y = y1; y <= y2; y++)
                    chunk.setPipeBlock(lx, y, lz, fence, c);
            }
    }

    /** Which of the 4 horizontal faces this perimeter fence connects along — to its neighbours that are also
     *  on the pen perimeter (handles straight runs and corners, across chunk seams). */
    private static BlockFace[] fenceCons(int wx, int wz, int penX1, int penX2, int penZ1, int penZ2) {
        List<BlockFace> f = new ArrayList<>(2);
        if (isPerimeter(wx, wz - 1, penX1, penX2, penZ1, penZ2))
            f.add(BlockFace.NORTH);
        if (isPerimeter(wx, wz + 1, penX1, penX2, penZ1, penZ2))
            f.add(BlockFace.SOUTH);
        if (isPerimeter(wx - 1, wz, penX1, penX2, penZ1, penZ2))
            f.add(BlockFace.WEST);
        if (isPerimeter(wx + 1, wz, penX1, penX2, penZ1, penZ2))
            f.add(BlockFace.EAST);
        return f.toArray(new BlockFace[0]);
    }

    private static boolean isPerimeter(int wx, int wz, int penX1, int penX2, int penZ1, int penZ2) {
        return wx >= penX1 && wx <= penX2 && wz >= penZ1 && wz <= penZ2
                && (wx == penX1 || wx == penX2 || wz == penZ1 || wz == penZ2);
    }

    /** The Y an animal stands on inside this enclosure (pit floor when sunken, ground otherwise). */
    public static int animalY(int streetY, boolean sunken) {
        return (sunken ? streetY - PIT_DEPTH : streetY) + 1;
    }

    /**
     * Light terraforming: raise a few gentle 1-2 high mounds of {@code ground} on the floor of the area
     * that falls in this chunk, so a big flat pen/pit/dome floor gets some contour instead of reading as a
     * billiard table. Strictly inside the given bounds (leaves a border), so mounds never touch the walls.
     */
    public static void mounds(RealBlocks chunk, int x1, int x2, int z1, int z2, int floorY, Material ground,
            Odds odds, int count) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        for (int i = 0; i < count; i++) {
            int lx = odds.getRandomInt(16), lz = odds.getRandomInt(16);
            int r = 2 + odds.getRandomInt(3); // radius 2-4 — broad, overlapping hummocks
            int h = 1 + odds.getRandomInt(3); // up to 3 tall
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    int mx = lx + dx, mz = lz + dz;
                    if (mx < 0 || mx > 15 || mz < 0 || mz > 15)
                        continue;
                    int wx = oX + mx, wz = oZ + mz;
                    if (wx <= x1 + 1 || wx >= x2 - 1 || wz <= z1 + 1 || wz >= z2 - 1)
                        continue; // keep the mound off the walls
                    double d = Math.hypot(dx, dz);
                    if (d > r + 0.4)
                        continue;
                    int mh = (int) Math.round(h * (1.0 - d / (r + 1.0))); // smooth domed falloff
                    for (int j = 1; j <= mh; j++)
                        chunk.setBlock(mx, floorY + j, mz, ground);
                }
        }
    }

    /** How high a pen's fence stands: two, or four for a tall animal (a giraffe looks over two). */
    public static int fenceHeight(ZooCatalog.Entry resident) {
        return resident.animal().tall() ? 4 : 2;
    }

    /**
     * Fits out the part of a pen (world bounds {@code penX1..penX2} x {@code penZ1..penZ2}, the fence ring included)
     * that falls in this chunk with its {@link ZooCatalog.Habitat}: ground scattered through, a pool of
     * {@code poolHalf} each way round the pen centre with its {@code pool_blocks}, then rocks, trees and plants
     * {@code count} times each on the dry inner floor (one cell off the fence). Counts are per chunk, so a big pen
     * is planted throughout. {@code floorY} is the ground's own Y.
     */
    public static void furnish(RealBlocks chunk, ZooCatalog.Habitat habitat, int penX1, int penX2, int penZ1,
            int penZ2, int floorY, int poolHalf, Odds odds) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        int cx = (penX1 + penX2) / 2, cz = (penZ1 + penZ2) / 2;
        boolean pool = habitat.pool();

        // scatter through the ground, and the pool (cleared above, so a mound never stands in it)
        for (ZooCatalog.Scatter sc : habitat.scatter()) {
            Material m = ZooCatalog.Habitat.block(sc.block(), null);
            if (m == null)
                continue;
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    if (inner(oX + lx, oZ + lz, penX1, penX2, penZ1, penZ2) && odds.getRandomDouble() < sc.chance())
                        chunk.setBlock(lx, floorY, lz, m);
        }
        if (pool)
            for (int lx = 0; lx < 16; lx++)
                for (int lz = 0; lz < 16; lz++)
                    if (inPool(oX + lx, oZ + lz, cx, cz, poolHalf)) {
                        chunk.setBlocks(lx, floorY - 1, floorY + 1, lz, Material.WATER);
                        chunk.setBlocks(lx, floorY + 1, floorY + 4, lz, Material.AIR);
                    }
        // where things may go in this chunk: water cells, and the dry inner floor (off the fence and the pool)
        List<int[]> water = new ArrayList<>(), dry = new ArrayList<>();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (pool && inPool(wx, wz, cx, cz, poolHalf))
                    water.add(new int[] { lx, lz });
                else if (dry(wx, wz, penX1, penX2, penZ1, penZ2, cx, cz, pool, poolHalf))
                    dry.add(new int[] { lx, lz });
            }

        for (ZooCatalog.PoolBlock pb : habitat.poolBlocks()) {
            Material m = ZooCatalog.Habitat.block(pb.block(), null);
            for (int i = 0; m != null && !water.isEmpty() && i < pb.count(); i++) {
                int[] c = water.get(odds.getRandomInt(water.size()));
                chunk.setBlock(c[0], pb.surface() ? floorY : floorY + 1, c[1], m);
            }
        }

        // rocks to climb: a 3x3 crag, the given height, wholly on dry floor
        for (ZooCatalog.Plant rock : habitat.rocks()) {
            Material m = ZooCatalog.Habitat.block(rock.block(), null);
            for (int i = 0; m != null && !dry.isEmpty() && i < rock.count(); i++) {
                int[] c = dry.get(odds.getRandomInt(dry.size()));
                int lx = c[0], lz = c[1], h = rock.height().roll(odds);
                boolean fits = lx >= 1 && lx <= 14 && lz >= 1 && lz <= 14;
                for (int dx = -1; fits && dx <= 1; dx++)
                    for (int dz = -1; dz <= 1; dz++)
                        fits &= dry(oX + lx + dx, oZ + lz + dz, penX1, penX2, penZ1, penZ2, cx, cz, pool, poolHalf);
                if (fits)
                    chunk.setBlocks(lx - 1, lx + 2, floorY + 1, floorY + 1 + h, lz - 1, lz + 2, m);
            }
        }

        // trees: a short trunk under a persistent canopy, a hive in the leaves if it has one
        for (ZooCatalog.Tree tree : habitat.trees()) {
            Material log = ZooCatalog.Habitat.block(tree.log(), null);
            Material leaves = ZooCatalog.Habitat.block(tree.leaves(), null);
            Material hive = tree.hive().map(h -> ZooCatalog.Habitat.block(h, null)).orElse(null);
            for (int i = 0; log != null && leaves != null && !dry.isEmpty() && i < tree.count(); i++) {
                int[] c = dry.get(odds.getRandomInt(dry.size()));
                int lx = c[0], lz = c[1];
                if (!chunk.isEmpty(lx, floorY + 1, lz))
                    continue;
                chunk.setBlocks(lx, floorY + 1, floorY + 4, lz, log);
                for (int x = lx - 1; x <= lx + 1; x++)
                    for (int y = floorY + 4; y <= floorY + 5; y++)
                        for (int z = lz - 1; z <= lz + 1; z++)
                            if (x >= 0 && x < 16 && z >= 0 && z < 16 && chunk.isEmpty(x, y, z))
                                chunk.setLeaves(x, y, z, leaves);
                chunk.setLeaves(lx, floorY + 6, lz, leaves);
                if (hive != null && lx + 1 < 16)
                    chunk.setBlock(lx + 1, floorY + 4, lz, hive); // nestled in the canopy's edge
            }
        }

        // plants: a column on the dry floor where nothing stands yet (on top of any mound)
        for (ZooCatalog.Plant plant : habitat.plants()) {
            Material m = ZooCatalog.Habitat.block(plant.block(), null);
            for (int i = 0; m != null && !dry.isEmpty() && i < plant.count(); i++) {
                int[] c = dry.get(odds.getRandomInt(dry.size()));
                int lx = c[0], lz = c[1], gy = floorY;
                while (gy < floorY + 4 && !chunk.isEmpty(lx, gy + 1, lz))
                    gy++;
                if (chunk.isEmpty(lx, gy + 1, lz) && !chunk.isEmpty(lx, gy, lz) && !chunk.isType(lx, gy, lz, Material.WATER))
                    chunk.setBlocks(lx, gy + 1, gy + 1 + plant.height().roll(odds), lz, m);
            }
        }
    }

    /**
     * A decayed world's pen, broken open: the fence knocked through in runs of four (two runs in five gone, by
     * world position, so a big pen's chunks agree). Its animals are long gone (none spawn in a decayed world).
     */
    public static void breakOpen(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int y1, int y2) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (!isPerimeter(wx, wz, penX1, penX2, penZ1, penZ2))
                    continue;
                int h = ((wx + wz) >> 2) * 0x9E3779B1; // runs of four along the fence
                h ^= h >>> 15;
                if (Math.floorMod(h, 5) < 2)
                    chunk.setBlocks(lx, y1, y2 + 1, lz, Material.AIR);
            }
    }

    /** Inside the pen, one cell off its fence. */
    private static boolean inner(int wx, int wz, int penX1, int penX2, int penZ1, int penZ2) {
        return wx >= penX1 + 2 && wx <= penX2 - 2 && wz >= penZ1 + 2 && wz <= penZ2 - 2;
    }

    private static boolean inPool(int wx, int wz, int cx, int cz, int half) {
        return wx >= cx - half && wx < cx + half && wz >= cz - half && wz < cz + half;
    }

    private static boolean dry(int wx, int wz, int penX1, int penX2, int penZ1, int penZ2, int cx, int cz,
            boolean pool, int poolHalf) {
        return inner(wx, wz, penX1, penX2, penZ1, penZ2) && !(pool && inPool(wx, wz, cx, cz, poolHalf));
    }
}
