package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A decoration pass that lines the edges of a lava pool with basalt. Lava pools fill whatever cave void
 * they sit in (see {@code ShapeProvider.lavaFillAt}), so where the pool's noise region ends abruptly
 * inside a winding cave tunnel the lava meets a flat, unnaturally straight wall of raw stone/deepslate.
 * Bulging basalt into the open cave air bordering the pool (sides and floor, never the open top) softens
 * that edge into something that reads as a natural basalt-lined lava pocket. Only pools — cells with at
 * least one other nearby lava neighbour — get lined, so a lone flowing drip stays untouched.
 */
public final class LavaLakes {

    private LavaLakes() {}

    private static final int[][] HORIZONTAL_AND_DOWN = { { 1, 0, 0 }, { -1, 0, 0 }, { 0, 0, 1 }, { 0, 0, -1 },
            { 0, -1, 0 } };

    public static void apply(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, Odds odds) {
        if (!(chunk instanceof RealBlocks real))
            return;

        int top = generator.oreProvider.lavaFieldLevel;
        int bottom = real.minY + 2;

        for (int x = 0; x < real.width; x++)
            for (int z = 0; z < real.width; z++)
                for (int y = bottom; y <= top; y++) {
                    if (!real.isType(x, y, z, Material.LAVA))
                        continue;
                    if (countLavaNeighbors(real, x, y, z) < 2)
                        continue; // a lone flowing drip — leave it be
                    for (int[] d : HORIZONTAL_AND_DOWN) {
                        int nx = x + d[0], ny = y + d[1], nz = z + d[2];
                        if (real.isEmpty(nx, ny, nz))
                            real.setBlock(nx, ny, nz, Material.BASALT);
                    }
                }
    }

    private static int countLavaNeighbors(RealBlocks real, int x, int y, int z) {
        int count = 0;
        if (real.isType(x + 1, y, z, Material.LAVA))
            count++;
        if (real.isType(x - 1, y, z, Material.LAVA))
            count++;
        if (real.isType(x, y, z + 1, Material.LAVA))
            count++;
        if (real.isType(x, y, z - 1, Material.LAVA))
            count++;
        if (real.isType(x, y + 1, z, Material.LAVA))
            count++;
        if (real.isType(x, y - 1, z, Material.LAVA))
            count++;
        return count;
    }
}
