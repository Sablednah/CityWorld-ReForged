package me.daddychurchill.CityWorld.worldgen;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * The ruined-city Nether's biome source ({@code cityworld:nether}) — CityWorld's climate field mapped onto
 * Nether biomes, so the same seed's warm/dry and cool/wet regions become basalt deltas and warped forests.
 *
 * <p><b>Membership is a tag</b>, {@code #cityworld:nether_pool} (shipped as {@code #minecraft:is_nether} plus
 * {@code #c:is_nether}), so BoP's Nether biomes — or any mod's — join without code. Each biome owns a point
 * in (temperature, humidity) space and a column takes the nearest point. The five vanilla biomes sit at fixed,
 * readable points (crimson hot-wet, warped cold-wet, soul sand cold-dry, basalt hot-dry, wastes in the middle);
 * a modded biome's point is seeded from its id, so adding or removing one never moves the others.
 *
 * <p><b>The points live in rank space, not raw climate.</b> Measured over ~12 km with raw anchors, the split
 * was basalt 26%, crimson 28%, wastes 22%, soul sand 12%, warped 11%: {@code climateWarmth} leans temperature
 * warm (median 0.615), so the two cold biomes got half the ground of the warm ones — and a different warmth
 * setting would skew it differently. Each axis is instead converted to "hotter than this fraction of the
 * world", from a one-off sample of the bound world's own climate, which is uniform whatever the settings.
 * (A 3x3-chunk probe reading 2,287 wastes to 17 basalt was one climate region, not an imbalance.)
 *
 * <p>No cave or surface pools: underground stays the Nether biome above it. Vanilla's own Nether biome
 * source cannot be used — it samples a noise router that only a {@code NoiseBasedChunkGenerator} gets.
 */
public class CityWorldNetherBiomeSource extends BiomeSource implements CityWorldBiomes {

    public static final MapCodec<CityWorldNetherBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, CityWorldNetherBiomeSource::new));

    public static final TagKey<Biome> NETHER_POOL = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("cityworld", "nether_pool"));

    private static final Map<ResourceKey<Biome>, double[]> ANCHORS = Map.of(
            Biomes.NETHER_WASTES, new double[] { 0.5, 0.5 },
            Biomes.CRIMSON_FOREST, new double[] { 0.8, 0.8 },
            Biomes.WARPED_FOREST, new double[] { 0.2, 0.8 },
            Biomes.SOUL_SAND_VALLEY, new double[] { 0.2, 0.2 },
            Biomes.BASALT_DELTAS, new double[] { 0.8, 0.2 });

    private record Site(Holder<Biome> biome, double temperature, double humidity) {}

    private final HolderGetter<Biome> biomes;

    /** Resolved from the tag on first use — tags are not bound when the codec builds the source. */
    private volatile List<Holder<Biome>> pool;
    /** Needs the world seed, so built once the context is bound. */
    private volatile List<Site> sites;
    /** Sorted samples of the bound world's temperature and humidity — see the class note on rank space. */
    private volatile double[] temperatureRanks, humidityRanks;
    private volatile CityWorldGenerator context;

    public CityWorldNetherBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
    }

    private List<Holder<Biome>> pool() {
        List<Holder<Biome>> local = pool;
        if (local == null) {
            synchronized (this) {
                local = pool;
                if (local == null) {
                    List<Holder<Biome>> members = biomes.get(NETHER_POOL)
                            .<HolderSet<Biome>>map(named -> named)
                            .map(set -> set.stream().map(h -> (Holder<Biome>) h).distinct()
                                    .sorted(Comparator.comparing(CityWorldNetherBiomeSource::idOf)).toList())
                            .orElse(List.of());
                    // An empty tag must not leave the dimension with no biome at all.
                    pool = local = members.isEmpty() ? List.of(biomes.getOrThrow(Biomes.NETHER_WASTES)) : members;
                }
            }
        }
        return local;
    }

    private List<Site> sites(CityWorldGenerator generator) {
        List<Site> local = sites;
        if (local == null) {
            synchronized (this) {
                local = sites;
                if (local == null) {
                    // 96 x 96 samples, 128 blocks apart: ~12 km, far wider than a climate region.
                    int side = 96, n = 0;
                    double[] temps = new double[side * side], humids = new double[side * side];
                    for (int i = 0; i < side; i++)
                        for (int j = 0; j < side; j++) {
                            int x = (i - side / 2) * 128, z = (j - side / 2) * 128;
                            temps[n] = generator.getTemperature(x, z);
                            humids[n++] = generator.getHumidity(x, z);
                        }
                    java.util.Arrays.sort(temps);
                    java.util.Arrays.sort(humids);
                    temperatureRanks = temps;
                    humidityRanks = humids;
                    sites = local = pool().stream().map(h -> site(h, generator.getWorldSeed())).toList();
                }
            }
        }
        return local;
    }

    private static Site site(Holder<Biome> biome, long worldSeed) {
        double[] anchor = biome.unwrapKey().map(ANCHORS::get).orElse(null);
        if (anchor != null)
            return new Site(biome, anchor[0], anchor[1]);
        java.util.Random random = new java.util.Random(worldSeed ^ (idOf(biome).hashCode() * 0x9E3779B97F4A7C15L));
        return new Site(biome, 0.1 + 0.8 * random.nextDouble(), 0.1 + 0.8 * random.nextDouble());
    }

    /** The fraction of the sorted samples below {@code value}. */
    private static double rank(double[] sorted, double value) {
        int i = java.util.Arrays.binarySearch(sorted, value);
        return (double) (i < 0 ? -i - 1 : i) / sorted.length;
    }

    private static String idOf(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return pool().stream();
    }

    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        Holder<Biome> biome = CityWorldBiomeLookup.biomeAt(this, x, y, z);
        return biome != null ? biome : biomes.getOrThrow(Biomes.NETHER_WASTES);
    }

    @Override
    public Holder<Biome> classify(CityWorldGenerator generator, int terrainY, double temperature, double humidity,
            boolean decayedNature) {
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        List<Site> all = sites(generator);
        double rankT = rank(temperatureRanks, temperature), rankH = rank(humidityRanks, humidity);
        for (Site site : all) {
            double dt = site.temperature() - rankT, dh = site.humidity() - rankH;
            double distance = dt * dt + dh * dh;
            if (distance < bestDistance) {
                bestDistance = distance;
                best = site;
            }
        }
        return best != null ? best.biome() : biomes.getOrThrow(Biomes.NETHER_WASTES);
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
