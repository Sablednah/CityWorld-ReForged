package me.daddychurchill.CityWorld.worldgen;

import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * A biome source that varies biome by CityWorld's own terrain — so grass, water and foliage colour
 * (and biome-appropriate mobs) follow the land instead of being one flat plains everywhere.
 *
 * <p>It carries a small <b>palette</b> of eight biomes keyed to CityWorld's height bands (the same
 * bands the shaper used when it pushed biomes into the now-discarded {@code BiomeGrid}): deep sea,
 * sea, the beach at the waterline, low land, hills, high hills, snowy peaks, and a dry biome for
 * nature-decayed worlds. Because the palette lives in the biome-source JSON, different world styles
 * can use different biomes for the same band — CLASSIC keeps the 1.14 look, MODERN can pick a modern
 * palette (cherry grove, groves, jagged peaks, …).
 *
 * <p>The actual per-column classification needs CityWorld's seeded terrain height, which a
 * {@code BiomeSource} doesn't have — so {@link CityWorldChunkGenerator#createBiomes} drives it (the
 * generator holds the context) and only reaches back here for {@link #classify} and the palette. This
 * source's own {@link #getNoiseBiome} is a plains fallback for the rare off-chunk query.
 */
public class CityWorldBiomeSource extends BiomeSource implements CityWorldBiomes {

    public static final MapCodec<CityWorldBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Biome.CODEC.fieldOf("deep_ocean").forGetter(s -> s.deepOcean),
            Biome.CODEC.fieldOf("ocean").forGetter(s -> s.ocean),
            Biome.CODEC.fieldOf("beach").forGetter(s -> s.beach),
            Biome.CODEC.fieldOf("low").forGetter(s -> s.low),
            Biome.CODEC.fieldOf("mid").forGetter(s -> s.mid),
            Biome.CODEC.fieldOf("high").forGetter(s -> s.high),
            Biome.CODEC.fieldOf("peak").forGetter(s -> s.peak),
            Biome.CODEC.fieldOf("dry").forGetter(s -> s.dry)
    ).apply(i, CityWorldBiomeSource::new));

    private final Holder<Biome> deepOcean;
    private final Holder<Biome> ocean;
    private final Holder<Biome> beach;
    private final Holder<Biome> low;
    private final Holder<Biome> mid;
    private final Holder<Biome> high;
    private final Holder<Biome> peak;
    private final Holder<Biome> dry;

    public CityWorldBiomeSource(Holder<Biome> deepOcean, Holder<Biome> ocean, Holder<Biome> beach, Holder<Biome> low,
            Holder<Biome> mid, Holder<Biome> high, Holder<Biome> peak, Holder<Biome> dry) {
        this.deepOcean = deepOcean;
        this.ocean = ocean;
        this.beach = beach;
        this.low = low;
        this.mid = mid;
        this.high = high;
        this.peak = peak;
        this.dry = dry;
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return Stream.of(deepOcean, ocean, beach, low, mid, high, peak, dry);
    }

    /** Plains fallback — the real per-column choice is made in {@code createBiomes}; this is only hit for off-chunk queries. */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return low;
    }

    /**
     * The biome for a column whose <em>terrain</em> (land, not water) surface sits at {@code terrainY}.
     * Replays 1.8 CityWorld's exact bands (see the ported {@code ShapeProvider_Normal}): deep ocean below
     * the deep-sea line, ocean below sea, beach at the waterline, then FOREST from the waterline up to the
     * shaper's {@code treeLevel}, BIRCH_FOREST to {@code evergreenLevel}, TAIGA to {@code snowLevel}, and
     * snowy peaks above — keyed to the very same levels the shaper places trees at, not an approximation.
     * A nature-decayed world is dry (desert) throughout, exactly as the shaper forced.
     */
    @Override
    public Holder<Biome> classify(CityWorldGenerator g, int terrainY, double temperature, double humidity,
            boolean decayedNature) {
        if (decayedNature)
            return dry;
        if (terrainY < g.deepseaLevel)
            return deepOcean;
        if (terrainY < g.seaLevel)
            return ocean;
        if (terrainY <= g.seaLevel)
            return beach; // the flush waterline
        if (terrainY < g.treeLevel)
            return low; // forest — from the waterline up (1.8 had no plains band)
        if (terrainY < g.evergreenLevel)
            return mid; // birch forest
        if (terrainY < g.snowLevel)
            return high; // taiga
        return peak; // snowy peaks
    }
}
