package me.daddychurchill.CityWorld.worldgen;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.world.level.biome.Biome;

import org.jspecify.annotations.Nullable;

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

    /**
     * The registry handle the source resolves biomes through.
     *
     * <p><b>Do not call {@code get(ResourceKey)} on this to test whether a biome exists.</b> During
     * datapack decode it <em>registers</em> an unknown key as an unbound promise, and the registry
     * freeze then fails with "Unbound values in registry". That is what {@link CaveRegions}' tag is for.
     */
    HolderGetter<Biome> biomeRegistry();

    /**
     * The cave pool for this world, resolved from {@link CaveRegions#CAVE_POOL} on first use.
     *
     * <p>Lazy on purpose: tags are not bound when the codec builds the source, so a constructor would
     * see an empty pool and cache it forever.
     */
    CaveRegions.Pool cavePool();

    /**
     * Hands the source the per-world context — the seeded terrain shaper and climate it needs to answer
     * {@code getNoiseBiome} for real.
     *
     * <p>Called by {@link CityWorldChunkGenerator} the moment its context exists. It cannot happen at
     * construction (the codec carries no seed) nor at {@code createState} (the context needs the level's
     * vertical bounds, which arrive with the first chunk), so until it does, {@link #boundContext}
     * returns {@code null} and the source falls back to its old constant answer. See
     * {@link CityWorldBiomeLookup} for why that window is harmless.
     *
     * <p>Rebinding a <em>different</em> context is a bug — it would mean one source instance serving
     * two worlds, and every biome after the switch would be classified against the wrong terrain.
     * Implementations reject it loudly rather than silently generating a wrong world.
     */
    void bindContext(CityWorldGenerator context);

    /** The bound context, or {@code null} if {@link #bindContext} hasn't happened yet. */
    @Nullable
    CityWorldGenerator boundContext();
}
