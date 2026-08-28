package me.daddychurchill.CityWorld.worldgen;

import java.util.Arrays;

import me.daddychurchill.CityWorld.CityWorldGenerator;

import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;

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

        return source.classify(context, column.terrainY, column.temperature, column.humidity,
                context.getSettings().includeDecayedNature);
    }

    // --- the per-column cache -------------------------------------------------------------------

    /** The per-column inputs to {@code classify} — everything that does not vary with Y. */
    private static final class Column {
        int terrainY;
        double temperature;
        double humidity;
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
            keys[slot] = key;
            filled[slot] = true;
            return column;
        }
    }

    private static final ThreadLocal<ColumnCache> COLUMNS = ThreadLocal.withInitial(ColumnCache::new);
}
