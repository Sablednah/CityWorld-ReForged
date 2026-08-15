package me.daddychurchill.CityWorld.Plats.Rural;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A rare farm variant: a fish pond. A shallow water pool dug into a grassy lot, dressed with lily pads
 * and reeds, a barrel on the bank (the fisherman's workstation, so a villager can take up angling) and
 * a few fish swimming in it. Placed as a one-off by {@link me.daddychurchill.CityWorld.Context.FarmContext}
 * (like the barn and water tower), gated on shops + aboveground fluids.
 */
public class FishPondLot extends IsolatedLot {

    public FishPondLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new FishPondLot(platmap, chunkX, chunkZ);
    }

    @Override
    public boolean allowsWildDecoration() {
        return false; // don't let vanilla drop trees/grass into the dug-out pond
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return generator.streetLevel;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return generator.streetLevel;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        // clear the air above, lay a grassy rim over a couple of dirt layers (the pond floor)
        chunk.airoutLayer(generator, generator.streetLevel + 1, DataContext.FloorHeight * 2, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 2, 2, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        generator.reportLocation("fishpond", "Fish Pond", chunk);

        int surfaceY = generator.streetLevel;
        double cx = 7.5, cz = 7.5;
        double r = 5.0 + chunkOdds.getRandomInt(0, 2); // 5 or 6 — a rough, irregular edge
        List<int[]> banks = new ArrayList<>();
        List<int[]> water = new ArrayList<>();

        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++) {
                double d = Math.hypot(x - cx, z - cz);
                if (d <= r) {
                    // two blocks of water, its surface flush with the grassy rim
                    chunk.setBlocks(x, x + 1, surfaceY - 1, surfaceY + 1, z, z + 1, Material.WATER);
                    water.add(new int[] { x, z });
                } else if (d <= r + 1.4) {
                    banks.add(new int[] { x, z });
                }
            }

        // a barrel FIRST (before any reeds), so the fisherman's workstation is guaranteed. Walk east from
        // the centre to the first non-water cell (the bank), then force a solid base + barrel there — a
        // little fishing spot that reads right even where the pond sits in otherwise wet/low terrain.
        int bx = 8;
        while (bx < 14 && chunk.isWater(bx, surfaceY, 8))
            bx++;
        int[] barrelCell = { bx, 8 };
        chunk.setBlock(bx, surfaceY, 8, generator.oreProvider.surfaceMaterial);
        chunk.setBlock(bx, surfaceY + 1, 8, Material.BARREL, BlockFace.WEST);

        // reeds on the bank (not on the barrel), lily pads on the water
        for (int[] b : banks) {
            if (b[0] == barrelCell[0] && b[1] == barrelCell[1])
                continue;
            if (chunkOdds.playOdds(Odds.oddsUnlikely) && chunk.isEmpty(b[0], surfaceY + 1, b[1])) {
                int h = 1 + chunkOdds.getRandomInt(0, 2);
                chunk.setBlocks(b[0], surfaceY + 1, surfaceY + 1 + h, b[1], Material.SUGAR_CANE);
            }
        }
        for (int[] w : water)
            if (chunkOdds.playOdds(Odds.oddsUnlikely))
                chunk.setBlock(w[0], surfaceY + 1, w[1], Material.LILY_PAD);

        // stock the pond
        generator.spawnProvider.spawnSeaAnimals(generator, chunk, chunkOdds, 8, surfaceY, 8);
        generator.spawnProvider.spawnSeaAnimals(generator, chunk, chunkOdds, 6, surfaceY, 9);

        // the angler, at the barrel
        generator.spawnProvider.spawnWorker(generator, chunk, chunkOdds, barrelCell[0], surfaceY + 1, barrelCell[1],
                net.minecraft.resources.Identifier.withDefaultNamespace("fisherman"));
    }

    /** The dominant horizontal direction from (x,z) toward the pond centre. */
    private static BlockFace inward(int x, int z) {
        int dx = 8 - x, dz = 8 - z;
        if (Math.abs(dx) >= Math.abs(dz))
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }
}
