package me.daddychurchill.CityWorld.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.densityfunction.DensityFunction;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.densityfunction.DensityBufferPool;
import net.minecraft.world.level.levelgen.densityfunction.DensitySampler;
import net.minecraft.world.level.levelgen.densityfunction.DensityVolume;
import net.minecraft.world.level.levelgen.densityfunction.SamplerContext;
import net.minecraft.world.level.levelgen.densityfunction.ScopedDensityBuffer;

/**
 * Where vanilla's End terrain is, asked of the noise — without generating a chunk.
 *
 * <p><b>Why it exists.</b> The CityWorld End keeps vanilla's islands (every chunk is filled by a real vanilla End
 * generator) and lays the city on top of them, so the planner has to know how high an island stands at a column
 * long before that chunk exists: {@code HeightInfo} asks about chunks five lots away to route a road. Vanilla's own
 * answer, {@code getBaseHeight}, builds a {@code NoiseChunk} per column, and on 1.21.11 measured <b>1.6 ms a
 * column</b> (2026-09-17, 50,000 calls) — 40 s for one platmap's column heights.
 *
 * <p><b>How it stays exact — the 26.3 shape.</b> Before 26.3 this class re-implemented {@code NoiseChunk}'s cell
 * interpolation by hand, because vanilla only interpolated inside a chunk fill. 26.3 rewrote the density engine:
 * a function compiles to a {@code DensitySampler}, the router's {@code interpolated} marker carries its own cell
 * size and interpolates inside {@code sampleVolume}, and the 2D caches are the sampler context's. So this now does
 * exactly what {@code NoiseBasedChunkGenerator.doFill} does — sample {@code final_density} over the chunk's
 * volume through a caching context — and reads the sign: a block is solid where the density is above zero, and
 * the End has no aquifers, carvers or beards to disagree. {@code -Dcityworld.probe=survey:end} still checks it
 * against {@code getBaseHeight} column by column; a mismatch there means the End's router or the engine changed
 * shape again and this needs another look.
 *
 * <p>The climate sampler the biome source reads is per thread, because a caching {@code SamplerContext} is
 * stateful; the engine's own cache is what memoises the ~600-simplex-lookup end-islands field per column.
 */
public final class EndTerrain {

    /** Above this no End terrain exists: the top slide has driven every density negative well before it. */
    private static final int SCAN_TOP = 96;
    private static final int CACHED_CHUNKS = 4096;

    private final RandomState random;
    private final DensityFunction finalDensity;
    private final int minY, height;
    private final ThreadLocal<Climate.Sampler> climate;
    /** Per chunk: [0] the top block of each column, [1] the lowest block of the solid run that top belongs to. */
    private final Map<Long, short[][]> tops = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, short[][]> eldest) {
            return size() > CACHED_CHUNKS;
        }
    };

    public EndTerrain(RandomState random, NoiseGeneratorSettings settings) {
        this.random = random;
        this.finalDensity = settings.noiseRouter().finalDensity();
        this.climate = ThreadLocal.withInitial(
                () -> random.createClimateSampler(SamplerContext.builder().enableCaches().build()));
        var noise = settings.noiseSettings();
        this.minY = noise.minY();
        this.height = Math.max(1, Math.min(SCAN_TOP, noise.minY() + noise.height()) - noise.minY());
    }

    /**
     * Vanilla's End climate for this world — erosion is the end-islands field, the other five axes zero — which is
     * what a real {@code TheEndBiomeSource} needs to answer under a generator that is not noise-based. Per thread.
     */
    public Climate.Sampler sampler() {
        return climate.get();
    }

    /** Vanilla's End biome field at a block column — what {@code TheEndBiomeSource} reads as erosion. */
    public double erosionAt(int blockX, int blockZ) {
        return climate.get().erosion().sampleValue(blockX, 0, blockZ);
    }

    /** The highest solid block of vanilla's terrain in this column, or 0 where the column is void. */
    public int topAt(int blockX, int blockZ) {
        return chunkTops(blockX >> 4, blockZ >> 4)[(blockX & 15) << 4 | (blockZ & 15)];
    }

    /** {@link #topAt} for a whole chunk, indexed {@code x << 4 | z}. Cached; do not modify. */
    public short[] chunkTops(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ)[0];
    }

    /**
     * How deep the island runs under each column of a chunk: the lowest block of the unbroken run of rock that
     * ends at {@link #chunkTops} (0 where the column is void). What a basement may be dug into — an island is a
     * few dozen blocks thick in the middle and nothing at all at its rim.
     */
    public short[] chunkUndersides(int chunkX, int chunkZ) {
        return chunk(chunkX, chunkZ)[1];
    }

    private short[][] chunk(int chunkX, int chunkZ) {
        long key = (long) chunkX << 32 | (chunkZ & 0xFFFFFFFFL);
        synchronized (tops) {
            short[][] cached = tops.get(key);
            if (cached != null)
                return cached;
        }
        short[][] computed = compute(chunkX, chunkZ);
        synchronized (tops) {
            tops.put(key, computed);
        }
        return computed;
    }

    private short[][] compute(int chunkX, int chunkZ) {
        short[] result = new short[256], underside = new short[256];
        DensityVolume volume = new DensityVolume(16, height, 16, chunkX * 16, minY, chunkZ * 16);
        // As NoiseChunk does it: a pooled buffer arena and a caching context, per fill.
        DensityBufferPool pool = random.acquireDensityBufferPool();
        try {
            SamplerContext context = SamplerContext.builder().enableCaches().useBufferArena(pool).build();
            DensitySampler.Bound density = random.samplersWithContext(context).get(finalDensity);
            try (ScopedDensityBuffer buffer = density.sampleVolume(volume)) {
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        int index = x << 4 | z;
                        for (int y = height - 1; y >= 0; y--) {
                            if (buffer.get(volume.indexUnchecked(x, y, z)) > 0) {
                                if (result[index] == 0)
                                    result[index] = (short) (minY + y);
                                underside[index] = (short) (minY + y);
                            } else if (result[index] != 0)
                                break; // the run under the top has ended
                        }
                    }
            }
        } finally {
            random.releaseDensityBufferPool(pool);
        }
        return new short[][] { result, underside };
    }
}
