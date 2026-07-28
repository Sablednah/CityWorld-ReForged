package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

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

    public static final int PIT_DEPTH = 5;

    public static void draw(RealBlocks chunk, int penX1, int penX2, int penZ1, int penZ2, int streetY,
            boolean sunken, Material ground) {
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
                        chunk.setPipeBlock(lx, streetY + 1, lz, Material.OAK_FENCE, c);
                        chunk.setPipeBlock(lx, streetY + 2, lz, Material.OAK_FENCE, c);
                    }
                } else if (wall) {
                    for (int wy = pitFloor; wy <= streetY; wy++)
                        chunk.setBlock(lx, wy, lz, Material.POLISHED_ANDESITE); // lined moat wall
                    chunk.setPipeBlock(lx, streetY + 1, lz, Material.OAK_FENCE,
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
            int lx = 1 + odds.getRandomInt(14), lz = 1 + odds.getRandomInt(14);
            int wx = oX + lx, wz = oZ + lz;
            if (wx <= x1 + 1 || wx >= x2 - 1 || wz <= z1 + 1 || wz >= z2 - 1)
                continue; // keep a clear border off the walls
            int h = 1 + odds.getRandomInt(2);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int mx = lx + dx, mz = lz + dz;
                    if (mx < 0 || mx > 15 || mz < 0 || mz > 15)
                        continue;
                    int mh = dx == 0 && dz == 0 ? h : h - 1; // domed: centre tallest
                    for (int j = 1; j <= mh; j++)
                        chunk.setBlock(mx, floorY + j, mz, ground);
                }
        }
    }
}
