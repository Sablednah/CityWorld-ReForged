package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;

/**
 * The MODERN biome source — CityWorld's terrain <em>elevation</em> crossed with a slow
 * <em>temperature</em> and <em>humidity</em> field ({@link CityWorldGenerator#getTemperature}), so the
 * world grows the full spread of overworld biomes: deserts and savannas in warm-dry lowlands, jungles
 * and swamps in warm-wet, snowy plains and taiga where it's cold, cherry groves and dark forests in the
 * temperate hills, badlands and windswept peaks up high, and warm/lukewarm/cold/frozen oceans by
 * latitude. CLASSIC keeps the plain elevation-only {@link CityWorldBiomeSource}; this is the richer twin.
 *
 * <p>Like its sibling, the seeded classification (terrain height + climate noise) belongs to the
 * generator, which drives {@code createBiomes}; the source only holds the biome palette (resolved from
 * the registry via the codec) and the {@link #classify} matrix. Biomes it can't place from terrain —
 * mushroom fields, the cave biomes, deep dark — are left to a future "bio-dome" set-piece.
 */
public class CityWorldClimateBiomeSource extends BiomeSource implements CityWorldBiomes {

    public static final MapCodec<CityWorldClimateBiomeSource> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryOps.retrieveGetter(Registries.BIOME)).apply(i, CityWorldClimateBiomeSource::new));

    // Every biome the matrix below can return — also this source's possibleBiomes().
    private static final List<ResourceKey<Biome>> PALETTE = List.of(
            Biomes.DEEP_FROZEN_OCEAN, Biomes.DEEP_COLD_OCEAN, Biomes.DEEP_OCEAN, Biomes.DEEP_LUKEWARM_OCEAN,
            Biomes.FROZEN_OCEAN, Biomes.COLD_OCEAN, Biomes.OCEAN, Biomes.LUKEWARM_OCEAN, Biomes.WARM_OCEAN,
            Biomes.BEACH, Biomes.SNOWY_BEACH, Biomes.STONY_SHORE, Biomes.MANGROVE_SWAMP,
            Biomes.DESERT, Biomes.SAVANNA, Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS, Biomes.MEADOW, Biomes.SWAMP,
            Biomes.JUNGLE, Biomes.FLOWER_FOREST, Biomes.SNOWY_PLAINS, Biomes.ICE_SPIKES, Biomes.MUSHROOM_FIELDS,
            Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.DARK_FOREST, Biomes.CHERRY_GROVE, Biomes.TAIGA,
            Biomes.SNOWY_TAIGA, Biomes.SAVANNA_PLATEAU, Biomes.BADLANDS, Biomes.WOODED_BADLANDS, Biomes.PALE_GARDEN,
            Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE, Biomes.OLD_GROWTH_BIRCH_FOREST,
            Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA, Biomes.WINDSWEPT_FOREST,
            Biomes.WINDSWEPT_HILLS, Biomes.WINDSWEPT_GRAVELLY_HILLS, Biomes.WINDSWEPT_SAVANNA, Biomes.ERODED_BADLANDS,
            Biomes.GROVE, Biomes.SNOWY_SLOPES, Biomes.JAGGED_PEAKS, Biomes.FROZEN_PEAKS, Biomes.STONY_PEAKS);

    private final HolderGetter<Biome> biomes;
    private final List<Holder<Biome>> possible;

    public CityWorldClimateBiomeSource(HolderGetter<Biome> biomes) {
        this.biomes = biomes;
        this.possible = PALETTE.stream().map(k -> (Holder<Biome>) biomes.getOrThrow(k)).toList();
    }

    @Override
    protected MapCodec<? extends BiomeSource> codec() {
        return CODEC;
    }

    @Override
    protected Stream<Holder<Biome>> collectPossibleBiomes() {
        return possible.stream();
    }

    /** Plains fallback for off-chunk queries; the real per-column choice happens in createBiomes. */
    @Override
    public Holder<Biome> getNoiseBiome(int x, int y, int z, Climate.Sampler sampler) {
        return b(Biomes.PLAINS);
    }

    private Holder<Biome> b(ResourceKey<Biome> key) {
        return biomes.getOrThrow(key);
    }

    // --- the matrix ---------------------------------------------------------------------------

    // Elevation bands (% of the land range above sea). MODERN widens the lowland band from the CLASSIC
    // source's thin 8% to 15% so the many lowland-only biomes (swamp, plains, meadow, flower forest,
    // sunflower plains, mushroom fields, snowy plains…) get a real shot instead of a coastal sliver.
    private static final int LOWLAND_PCT = 15;
    private static final int HILL_PCT = 45;
    private static final int HIGHLAND_PCT = 72;

    /**
     * The biome for a column: oceans (by temperature) below sea, a shore at the waterline, then the
     * land tiers (lowland / hills / highland / peaks) each split by temperature and humidity.
     */
    @Override
    public Holder<Biome> classify(CityWorldGenerator g, int terrainY, double temp, double humid, boolean decayedNature) {
        if (terrainY < g.deepseaLevel)
            return deepOcean(temp);
        if (terrainY < g.seaLevel)
            return ocean(temp);
        if (terrainY <= g.seaLevel)
            return shore(temp, humid, decayedNature);

        int range = Math.max(1, g.landRange);
        int aboveSea = terrainY - g.seaLevel;
        if (aboveSea <= range * LOWLAND_PCT / 100)
            return lowland(temp, humid, decayedNature);
        if (aboveSea <= range * HILL_PCT / 100)
            return hill(temp, humid, decayedNature);
        if (aboveSea <= range * HIGHLAND_PCT / 100)
            return highland(temp, humid, decayedNature);
        return peak(temp);
    }

    // temperature buckets
    private static boolean cold(double t) { return t < 0.35; }
    private static boolean temperate(double t) { return t < 0.6; }
    private static boolean warm(double t) { return t < 0.8; }
    // humidity buckets
    private static boolean dry(double h) { return h < 0.4; }
    private static boolean wet(double h) { return h > 0.65; }

    private Holder<Biome> deepOcean(double t) {
        if (t < 0.2) return b(Biomes.DEEP_FROZEN_OCEAN);
        if (cold(t)) return b(Biomes.DEEP_COLD_OCEAN);
        if (warm(t)) return b(Biomes.DEEP_OCEAN);
        return b(Biomes.DEEP_LUKEWARM_OCEAN);
    }

    private Holder<Biome> ocean(double t) {
        if (t < 0.2) return b(Biomes.FROZEN_OCEAN);
        if (cold(t)) return b(Biomes.COLD_OCEAN);
        if (temperate(t)) return b(Biomes.OCEAN);
        if (warm(t)) return b(Biomes.LUKEWARM_OCEAN);
        return b(Biomes.WARM_OCEAN);
    }

    private Holder<Biome> shore(double t, double h, boolean decayed) {
        if (decayed) return b(Biomes.DESERT);
        if (t >= 0.6 && h > 0.5) return b(Biomes.MANGROVE_SWAMP); // warm/hot + damp coast (widened for presence)
        if (cold(t)) return b(Biomes.SNOWY_BEACH);
        return b(Biomes.BEACH);
    }

    private Holder<Biome> lowland(double t, double h, boolean decayed) {
        if (decayed) return b(Biomes.DESERT);
        if (cold(t)) return t < 0.15 && dry(h) ? b(Biomes.ICE_SPIKES)
                : wet(h) ? b(Biomes.SNOWY_TAIGA) : b(Biomes.SNOWY_PLAINS);
        // mushroom fields kept genuinely rare (vanilla's rarest biome) — only the warm, very-humid
        // corner of temperate lowland; everything else damp stays flower forest.
        if (temperate(t)) return dry(h) ? b(Biomes.PLAINS)
                : (t > 0.5 && h > 0.9) ? b(Biomes.MUSHROOM_FIELDS)
                : wet(h) ? b(Biomes.FLOWER_FOREST) : b(Biomes.MEADOW);
        // swamp widened (h>0.5, not just wet>0.65) so warm humid lowlands read as swamp more often
        if (warm(t)) return dry(h) ? b(Biomes.SAVANNA) : h > 0.5 ? b(Biomes.SWAMP) : b(Biomes.SUNFLOWER_PLAINS);
        return dry(h) ? b(Biomes.DESERT) : wet(h) ? b(Biomes.JUNGLE) : b(Biomes.SAVANNA); // hot
    }

    private Holder<Biome> hill(double t, double h, boolean decayed) {
        if (decayed) return b(Biomes.BADLANDS);
        if (cold(t)) return dry(h) ? b(Biomes.SNOWY_TAIGA) : b(Biomes.TAIGA);
        if (temperate(t)) return dry(h) ? b(Biomes.FOREST)
                : h > 0.8 ? b(Biomes.PALE_GARDEN) : wet(h) ? b(Biomes.DARK_FOREST) : b(Biomes.CHERRY_GROVE);
        if (warm(t)) return dry(h) ? b(Biomes.SAVANNA_PLATEAU) : wet(h) ? b(Biomes.SPARSE_JUNGLE) : b(Biomes.BIRCH_FOREST);
        return dry(h) ? b(Biomes.BADLANDS) : wet(h) ? b(Biomes.BAMBOO_JUNGLE) : b(Biomes.WOODED_BADLANDS); // hot
    }

    private Holder<Biome> highland(double t, double h, boolean decayed) {
        if (decayed) return b(Biomes.ERODED_BADLANDS);
        if (cold(t)) return wet(h) ? b(Biomes.OLD_GROWTH_SPRUCE_TAIGA) : b(Biomes.OLD_GROWTH_PINE_TAIGA);
        if (temperate(t)) return dry(h) ? b(Biomes.WINDSWEPT_FOREST) : wet(h) ? b(Biomes.OLD_GROWTH_BIRCH_FOREST)
                : b(Biomes.GROVE);
        if (warm(t)) return dry(h) ? (h < 0.2 ? b(Biomes.WINDSWEPT_GRAVELLY_HILLS) : b(Biomes.WINDSWEPT_HILLS))
                : b(Biomes.WINDSWEPT_FOREST);
        return dry(h) ? b(Biomes.ERODED_BADLANDS) : b(Biomes.WINDSWEPT_SAVANNA); // hot
    }

    private Holder<Biome> peak(double t) {
        if (t < 0.3) return b(Biomes.FROZEN_PEAKS);
        if (temperate(t)) return b(Biomes.JAGGED_PEAKS);
        if (warm(t)) return b(Biomes.SNOWY_SLOPES);
        return b(Biomes.STONY_PEAKS);
    }
}
