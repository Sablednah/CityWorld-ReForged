package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A free-floating blimp/airship drifting high over the wild — the rare cousin of the hot-air balloon. An
 * elongated envelope with a small crewed gondola slung underneath. Extra-rare in MODERN (see
 * {@code NatureContext}); the FLOATING style has its own {@code FloatingBlimpLot}.
 */
public class AirshipLot extends ConstructLot {

    public AirshipLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        trulyIsolated = true;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new AirshipLot(platmap, chunkX, chunkZ);
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return blockYs.getMaxHeight() + 25;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        generateSurface(generator, chunk, false);

        int atY = getBottomY(generator);
        generator.reportLocation("airship", "Airship", chunk);
        int rangeY = Math.max(2, chunk.height - 40 - atY);
        generator.structureInAirProvider.generateBlimp(generator, chunk, atY + chunkOdds.getRandomInt(rangeY),
                chunkOdds);
    }
}
