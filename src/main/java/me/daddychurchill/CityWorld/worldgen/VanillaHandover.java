package me.daddychurchill.CityWorld.worldgen;

import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A CityWorld biome source that can hand its answers over to vanilla's: a vanilla-terrain world
 * ({@code cities.vanillaTerrain}) keeps whatever source its preset named, and the chunk generator binds vanilla's
 * overworld source and this world's real climate sampler here before any biome is asked for. From then on
 * {@code getNoiseBiome} answers vanilla's, and the possible biomes include vanilla's — which is what lets its
 * structures place and its features decorate. Bound at {@code createState}, the first thing a level asks, so
 * the memoised possible-biome list is never built without them.
 */
public interface VanillaHandover {

    void bindVanilla(BiomeSource vanilla, Climate.Sampler sampler);
}
