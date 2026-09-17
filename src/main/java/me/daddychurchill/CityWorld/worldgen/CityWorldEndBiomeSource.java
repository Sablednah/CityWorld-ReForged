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
 * The CityWorld End's biome source ({@code cityworld:end}): vanilla's End biomes, exactly where vanilla puts them.
 *
 * <p>The CityWorld End keeps vanilla's terrain throughout (see {@code ShapeProvider_TheEnd}), so its biomes are
 * vanilla's too: {@code the_end} within 64 sections of the origin, and beyond it highlands, midlands, barrens or
 * small islands from the end-islands field, with {@code TheEndBiomeSource}'s own sample point and thresholds. That
 * is what makes chorus grow where vanilla would grow it, and lets end cities find their highlands.
 *
 * <p><b>Why not simply use vanilla's source.</b> {@code TheEndBiomeSource} reads {@code sampler.erosion()}, and
 * the sampler a non-noise generator is handed is a dummy that answers zero everywhere. {@link EndTerrain} wraps
 * the real End noise for this world; the chunk generator binds it here as soon as it exists. (The first CityWorld
 * End classified by the height of CityWorld's own islands instead; with vanilla's islands that would put the
 * biomes in different places from the terrain they describe.)
 *
 * <p>All five biomes are in {@link #collectPossibleBiomes}, which is what lets end cities place at all —
 * vanilla drops any structure set whose biomes the source cannot produce.
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
        // Beyond it the islands are vanilla's, so the biomes are too: the same field, the same section-centre
        // sample and the same thresholds as TheEndBiomeSource — chorus grows where vanilla would grow it, and end
        // cities find their highlands and midlands where the land really is high.
        EndTerrain field = terrain;
        if (field == null)
            return b(Biomes.END_BARRENS);
        double erosion = field.erosionAt(((int) sectionX * 2 + 1) * 8, ((int) sectionZ * 2 + 1) * 8);
        if (erosion > 0.25)
            return b(Biomes.END_HIGHLANDS);
        if (erosion >= -0.0625)
            return b(Biomes.END_MIDLANDS);
        return erosion < -0.21875 ? b(Biomes.SMALL_END_ISLANDS) : b(Biomes.END_BARRENS);
    }

    private volatile EndTerrain terrain;

    /** Vanilla's End noise for this world, handed over by the chunk generator as soon as it exists. */
    public void bindTerrain(EndTerrain terrain) {
        this.terrain = terrain;
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
