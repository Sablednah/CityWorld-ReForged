package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * The CityWorld End's biome source ({@code cityworld:end}).
 *
 * <p><b>The middle stays vanilla.</b> Within 64 sections of the origin — vanilla's own radius, the test
 * {@code TheEndBiomeSource} uses — every column is {@code minecraft:the_end}, so the central island, the
 * obsidian pillars (an {@code end_spike} feature of that biome) and the dragon's arena are exactly what a
 * vanilla End would grow there. Outside it, CityWorld's islands take over and the biome follows the island's
 * own height: the tall ones are highlands, the middling ones midlands, low shelves barrens, and the scraps
 * small islands.
 *
 * <p><b>Why not vanilla's End source.</b> {@code TheEndBiomeSource} reads {@code sampler.erosion()}, a density
 * function only a {@code NoiseBasedChunkGenerator} is given; under CityWorld's generator that sampler is a
 * dummy and every column would answer the same. The heights here come from CityWorld's own terrain instead.
 *
 * <p>All five biomes are in {@link #collectPossibleBiomes}, which is what lets end cities place at all —
 * vanilla drops any structure set whose biomes the source cannot produce, and end cities want
 * highlands/midlands.
 */
public class CityWorldEndBiomeSource extends BiomeSource implements CityWorldBiomes {

    public static final MapCodec<CityWorldEndBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, CityWorldEndBiomeSource::new));

    /** Vanilla's own central radius, in sections: {@code x*x + z*z <= 4096} is 64 sections, 1,024 blocks. */
    private static final long CENTRE_SECTIONS_SQUARED = 4096L;

    private final HolderGetter<Biome> biomes;
    private final List<Holder<Biome>> possible;
    private volatile CityWorldGenerator context;

    public CityWorldEndBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
        this.possible = Stream
                .of(Biomes.THE_END, Biomes.END_HIGHLANDS, Biomes.END_MIDLANDS, Biomes.END_BARRENS,
                        Biomes.SMALL_END_ISLANDS)
                .map(key -> (Holder<Biome>) biomes.getOrThrow(key)).toList();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return possible.stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        // The centre is the dragon's, whatever the terrain says.
        long sectionX = SectionPos.blockToSectionCoord(net.minecraft.core.QuartPos.toBlock(x));
        long sectionZ = SectionPos.blockToSectionCoord(net.minecraft.core.QuartPos.toBlock(z));
        if (sectionX * sectionX + sectionZ * sectionZ <= CENTRE_SECTIONS_SQUARED)
            return b(Biomes.THE_END);
        Holder<Biome> biome = CityWorldBiomeLookup.biomeAt(this, x, y, z);
        return biome != null ? biome : b(Biomes.END_BARRENS);
    }

    @Override
    public Holder<Biome> classify(CityWorldGenerator generator, int terrainY, double temperature, double humidity,
            boolean decayedNature) {
        int sea = generator.seaLevel;
        if (terrainY >= sea + 24)
            return b(Biomes.END_HIGHLANDS); // the tall islands: chorus forests and end cities
        if (terrainY >= sea + 8)
            return b(Biomes.END_MIDLANDS);
        if (terrainY >= sea - 8)
            return b(Biomes.END_BARRENS);
        return b(Biomes.SMALL_END_ISLANDS);
    }

    private Holder<Biome> b(ResourceKey<Biome> key) {
        return biomes.getOrThrow(key);
    }

    @Override
    public HolderGetter<Biome> biomeRegistry() {
        return biomes;
    }

    @Override
    public CaveRegions.Pool cavePool() {
        return NO_CAVES;
    }

    @Override
    public SurfaceRegions.Pools surfacePools() {
        return NO_SURFACE_POOLS;
    }

    private static final CaveRegions.Pool NO_CAVES = CaveRegions.none();
    private static final SurfaceRegions.Pools NO_SURFACE_POOLS = SurfaceRegions.none();

    @Override
    public void bindContext(CityWorldGenerator context) {
        CityWorldGenerator bound = this.context;
        if (bound == context)
            return;
        if (bound != null)
            throw new IllegalStateException("CityWorld: this biome source is already bound to another world's "
                    + "context; rebinding would classify every biome against the wrong terrain.");
        this.context = context;
    }

    @Override
    public CityWorldGenerator boundContext() {
        return context;
    }
}
