package me.daddychurchill.CityWorld.worldgen;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;

/**
 * The seam {@code CityWorldChunkGenerator.createBiomes} drives: a CityWorld biome source that picks a
 * biome for a column from its terrain height (and, for MODERN, the climate). Both
 * {@link CityWorldBiomeSource} (elevation only — CLASSIC) and {@link CityWorldClimateBiomeSource}
 * (elevation × temperature × humidity — MODERN) implement it, so the generator classifies uniformly;
 * the elevation source simply ignores the climate arguments.
 */
public interface CityWorldBiomes {

    /**
     * @param terrainY      the column's terrain (land, not water) surface height
     * @param temperature   0..1 climate temperature at the column (MODERN only; ignored by CLASSIC)
     * @param humidity      0..1 climate humidity at the column (MODERN only; ignored by CLASSIC)
     * @param decayedNature whether the world is nature-decayed (deserts everything)
     */
    Holder<Biome> classify(CityWorldGenerator generator, int terrainY, double temperature, double humidity,
            boolean decayedNature);
}
