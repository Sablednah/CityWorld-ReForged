package me.daddychurchill.CityWorld.worldgen;

import java.util.Arrays;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;

import org.jspecify.annotations.Nullable;

/**
 * The shared body of {@code getNoiseBiome} for both CityWorld biome sources — the piece that makes
 * CityWorld's biome map genuinely <b>three-dimensional</b>.
 *
 * <p><b>Why this had to move out of {@code createBiomes}.</b> CityWorld used to classify columns in
 * {@link CityWorldChunkGenerator#createBiomes}, because that is where the seeded terrain height is to
 * hand, and left {@code getNoiseBiome} as a constant stub. That was invisible to half of vanilla:
 * {@code Structure.isValidBiome} asks the <em>biome source</em> — {@code getNoiseBiome(qx, qy, qz,
 * sampler)} — at structure-start time, a chunk stage that runs <em>before</em> biomes are filled. So
 * every structure saw "plains, everywhere, at every height". Ancient cities (gated on
 * {@code deep_dark}) could never place; trial chambers would have placed uniformly. Answering here
 * instead fixes the structure pipeline and the chunk's biomes at once, because vanilla's own
 * {@code createBiomes} is just {@code fillBiomesFromNoise(biomeSource, sampler)}.
 *
 * <p><b>The one window where there is no answer.</b> The per-world {@link CityWorldGenerator} context
 * needs the level's vertical bounds, which only arrive with a chunk — and
 * {@code ServerLevel}'s constructor calls {@code ensureStructuresGenerated()} before any chunk exists,
 * to lay out the stronghold rings. During that window {@link #biomeAt} returns {@code null} and the
 * caller falls back to its old constant. That is not a regression: it is exactly the answer the stub
 * gave, in the only place it is still reachable, and stronghold rings only ever ask "is this biome in
 * {@code #stronghold_biased_to}".
 *
 * <p><b>Cost.</b> {@code fillBiomesFromNoise} asks per quart — 4×4 columns × 96 quart-heights for a
 * full-height chunk, so ~1,500 calls where only 16 columns are distinct. The per-column inputs
 * (terrain height, temperature, humidity) are therefore cached per thread; only the cheap band choice
 * runs per Y.
 */
public final class CityWorldBiomeLookup {

    private CityWorldBiomeLookup() {
    }


    /**
     * The biome at a quart position, or {@code null} if the world context isn't bound yet (see the
     * class note) and the caller should use its own fallback.
     */
    public static @Nullable Holder<Biome> biomeAt(CityWorldBiomes source, int quartX, int quartY, int quartZ) {
        CityWorldGenerator context = source.boundContext();
        if (context == null)
            return null;

        int blockX = QuartPos.toBlock(quartX);
        int blockY = QuartPos.toBlock(quartY);
        int blockZ = QuartPos.toBlock(quartZ);

        Column column = COLUMNS.get().at(context, blockX, blockZ);

        // Underground and inside a cave patch? Then the patch's biome wins over the surface's. The
        // margin keeps a patch from bleeding into surface grass and foliage colour on a hillside.
        if (blockY < column.terrainY - context.getSettings().caves.surfaceMargin()) {
            Holder<Biome> cave = source.cavePool().at(context.getWorldSeed(), blockX, blockY, blockZ);
            if (cave != null)
                return cave;
        }

        // Above the waterline, an installed TerraBlender mod may have a biome for this climate. Opt-in,
        // and only for land: oceans, shores and peaks stay CityWorld's, because those are decided by
        // terrain facts we know exactly and a climate lookup would only get wrong.
        if (context.getSettings().useModdedBiomes && blockY > context.seaLevel) {
            Holder<Biome> modded = moddedBiome(source, context, column, blockX, blockZ);
            if (modded != null)
                return modded;
        }

        return source.classify(context, column.terrainY, column.temperature, column.humidity,
                context.getSettings().includeDecayedNature);
    }

    /**
     * Asks the TerraBlender bridge for a biome at this column's climate, or {@code null} if there is no
     * bridge (TerraBlender absent, or no mod registered any overworld region).
     *
     * <p><b>The axes are the whole story here.</b> Vanilla's climate point has seven; CityWorld natively
     * models temperature and humidity, derives continentalness from the terrain it already knows, and
     * generates erosion and weirdness as their own noise fields. Those last two exist purely for this —
     * see {@code CityWorldGenerator.getErosion}. Deriving them from elevation instead would have put
     * several axes on one scale, and any modded biome wanting a combination we could not express would
     * simply never be picked.
     *
     * <p>Temperature and humidity are CityWorld's {@code 0..1}; vanilla's parameters are {@code -1..1}.
     * Getting that mapping wrong does not fail loudly — it quietly confines every lookup to one corner
     * of the climate space, which looks like "the mod only added three biomes".
     */
    private static @Nullable Holder<Biome> moddedBiome(CityWorldBiomes source, CityWorldGenerator context,
            Column column, int blockX, int blockZ) {
        if (!(source instanceof CityWorldClimateBiomeSource climate))
            return null;
        TerraBlenderBridge bridge = climate.terraBlender();
        if (bridge == null)
            return null;
        float temperature = (float) (column.temperature * 2.0 - 1.0);
        float humidity = (float) (column.humidity * 2.0 - 1.0);
        float continentalness = (float) column.continentalness;
        float erosion = (float) context.getErosion(blockX, blockZ);
        float weirdness = (float) context.getWeirdness(blockX, blockZ);
        // Surface lookup: vanilla's depth is ~0 at the surface and grows downward, and this path only
        // runs above sea level, so a surface point is the honest query.
        Holder<Biome> hit = bridge.find(Climate.target(temperature, humidity, continentalness, erosion,
                0.0F, weirdness));
        if (hit == null)
            return null;

        // ⚠ Take the answer ONLY when it is actually a modded biome. TerraBlender's regions carry the
        // vanilla biomes too, so accepting every hit did not *add* to CityWorld's pool, it *replaced*
        // it: measured over 8km, swamp fell 11.6% -> 2.2%, desert rose 3.6% -> 8.7%, meadow left the
        // top twenty, and river appeared at 4.3% despite never being in CityWorld's palette at all.
        // Falling through on a vanilla hit keeps CityWorld's own tuned matrix in charge of vanilla
        // ground and lets the mod contribute only what CityWorld could not have chosen itself.
        boolean modded = hit.unwrapKey()
                .map(k -> !k.identifier().toString().startsWith("minecraft:")).orElse(false);
        return modded ? hit : null;
    }

    // --- the per-column cache -------------------------------------------------------------------

    /** The per-column inputs to {@code classify} — everything that does not vary with Y. */
    private static final class Column {
        int terrainY;
        double temperature;
        double humidity;
        /** Cached because it is nine noise samples now — see {@code CityWorldGenerator.regionalHeight}. */
        double continentalness;
    }

    /**
     * A small direct-mapped cache of {@link Column}s.
     *
     * <p><b>Thread-local by design, not by accident.</b> A shared cache would need the key and the
     * three values to be published atomically together; a per-thread one is correct by construction,
     * needs no synchronisation, and matches how the chunk pipeline works anyway (a worker stays with
     * one chunk at a time). Direct-mapped and fixed-size, so it cannot grow without bound as a player
     * explores.
     *
     * <p>It carries the context it was filled for and clears itself if asked about a different one —
     * a server can have two CityWorld dimensions with different seeds, and the same worker thread will
     * generate for both.
     */
    private static final class ColumnCache {
        private static final int SIZE = 1024; // power of two
        private static final int MASK = SIZE - 1;

        private final long[] keys = new long[SIZE];
        private final boolean[] filled = new boolean[SIZE];
        private final Column[] columns = new Column[SIZE];
        private @Nullable CityWorldGenerator owner;

        Column at(CityWorldGenerator context, int blockX, int blockZ) {
            if (owner != context) {
                owner = context;
                Arrays.fill(filled, false);
            }
            long key = ((long) blockX << 32) | (blockZ & 0xFFFFFFFFL);
            int slot = (int) ((key * 0x9E3779B97F4A7C15L) >>> 48) & MASK;

            Column column = columns[slot];
            if (column == null)
                columns[slot] = column = new Column();
            else if (filled[slot] && keys[slot] == key)
                return column;

            column.terrainY = context.getFarBlockY(blockX, blockZ);
            column.temperature = context.getTemperature(blockX, blockZ);
            column.humidity = context.getHumidity(blockX, blockZ);
            column.continentalness = context.getContinentalness(blockX, blockZ);
            keys[slot] = key;
            filled[slot] = true;
            return column;
        }
    }

    private static final ThreadLocal<ColumnCache> COLUMNS = ThreadLocal.withInitial(ColumnCache::new);
}
