package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.Material;

/**
 * Draws one chunk-slice of a zoo enclosure from its WORLD-coordinate bounds, so the same code serves both a
 * single-chunk pen and a multi-chunk one — each chunk renders only the part of the pen inside its own 0..15
 * columns. Two styles: a ground-level <em>fenced</em> paddock, or a <em>sunken</em> pit (the animals down a
 * lined moat wall with a safety rail round the top, so visitors look down into it — the old-zoo look).
 */
public final class ZooEnclosure {

    private ZooEnclosure() {}

    public static final int PIT_DEPTH = 5;

    /** Lay the floor + walls of the pen area [penX1..penX2] x [penZ1..penZ2] (inclusive, world coords) that
     *  intersects this chunk. {@code ground} is the themed surface the animals stand on. */
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
                        chunk.setBlock(lx, streetY + 1, lz, Material.OAK_FENCE);
                        chunk.setBlock(lx, streetY + 2, lz, Material.OAK_FENCE);
                    }
                } else if (wall) {
                    // a lined moat wall the animals can't climb, capped with a safety rail
                    for (int wy = pitFloor; wy <= streetY; wy++)
                        chunk.setBlock(lx, wy, lz, Material.POLISHED_ANDESITE);
                    chunk.setBlock(lx, streetY + 1, lz, Material.OAK_FENCE);
                } else {
                    // dig the pit and floor it with the themed ground
                    for (int wy = pitFloor + 1; wy <= streetY; wy++)
                        chunk.setBlock(lx, wy, lz, Material.AIR);
                    chunk.setBlock(lx, pitFloor, lz, ground);
                }
            }
    }

    /** The Y an animal stands on inside this enclosure (pit floor when sunken, ground otherwise). */
    public static int animalY(int streetY, boolean sunken) {
        return (sunken ? streetY - PIT_DEPTH : streetY) + 1;
    }
}
