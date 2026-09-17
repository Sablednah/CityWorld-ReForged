package me.daddychurchill.CityWorld.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.NoiseSettings;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * Where vanilla's End terrain is, asked of the noise — without generating a chunk.
 *
 * <p><b>Why it exists.</b> The CityWorld End keeps vanilla's islands (every chunk is filled by a real vanilla End
 * generator) and lays the city on top of them, so the planner has to know how high an island stands at a column
 * long before that chunk exists: {@code HeightInfo} asks about chunks five lots away to route a road. Vanilla's own
 * answer, {@code getBaseHeight}, measured <b>1.6 ms a column</b> (2026-09-17, 50,000 calls) — it rebuilds a
 * {@code NoiseChunk} per call — which is 40 s for one platmap's column heights.
 *
 * <p><b>How it stays exact.</b> This reproduces what {@code NoiseChunk} does rather than approximating it: the
 * function wrapped by the router's {@code interpolated} marker is evaluated at the noise cell corners (8 x 4 x 8
 * blocks in the End) and interpolated trilinearly between them, and a block is solid where that is above zero — the
 * {@code * 0.64} and {@code squeeze} outside the marker keep the sign, and the End has no aquifers, carvers or
 * beards to disagree. {@code -Dcityworld.probe=survey:end} checks it against {@code getBaseHeight} column by
 * column; a mismatch there means a Minecraft version reshaped the End's router and this needs another look.
 *
 * <p>The end-islands function is 2D but costs ~600 simplex lookups, and vanilla only caches it inside a
 * {@code NoiseChunk}; here every 2D cache marker (and the islands function itself) is swapped for a one-column
 * memo, since corners are evaluated a column at a time. That memo is why each thread has its own function tree.
 */
public final class EndTerrain {

    /** Above this no End terrain exists: the top slide has driven every density negative well before it. */
    private static final int SCAN_TOP = 96;
    private static final int CACHED_CHUNKS = 4096;

    private final RandomState random;
    private final int cellWidth, cellHeight, minY, levels;
    private final ThreadLocal<Functions> functions = ThreadLocal.withInitial(this::wire);
    /** Per chunk: [0] the top block of each column, [1] the lowest block of the solid run that top belongs to. */
    private final Map<Long, short[][]> tops = new LinkedHashMap<>(256, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, short[][]> eldest) {
            return size() > CACHED_CHUNKS;
        }
    };

    public EndTerrain(RandomState random, NoiseSettings noise) {
        this.random = random;
        this.cellWidth = noise.getCellWidth();
        this.cellHeight = noise.getCellHeight();
        this.minY = noise.minY();
        this.levels = (Math.min(SCAN_TOP, noise.minY() + noise.height()) - noise.minY()) / cellHeight + 1;
        if (16 % cellWidth != 0)
            throw new IllegalStateException("CityWorld: End noise cells are " + cellWidth + " wide; EndTerrain "
                    + "assumes they tile a chunk");
    }

    /** The density inside the router's {@code interpolated} marker, and the 2D field the End's biomes read. */
    private record Functions(DensityFunction density, DensityFunction erosion) {}

    private Functions wire() {
        DensityFunction[] interpolated = new DensityFunction[1];
        DensityFunction.Visitor visitor = function -> {
            if (function instanceof DensityFunctions.MarkerOrMarked marker) {
                // Marker.Type is package-private; its constant names are what we can see of it.
                String type = String.valueOf((Object) marker.type());
                if (type.equals("Interpolated") && interpolated[0] == null)
                    interpolated[0] = marker.wrapped();
                if (type.equals("Cache2D") || type.equals("FlatCache"))
                    return new ColumnMemo(marker.wrapped());
            } else if (function.getClass().getSimpleName().equals("EndIslandDensityFunction"))
                return new ColumnMemo(function);
            return function;
        };
        DensityFunction whole = random.router().finalDensity().mapAll(visitor);
        return new Functions(interpolated[0] != null ? interpolated[0] : whole,
                random.router().erosion().mapAll(visitor));
    }

    /** Vanilla's End biome field at a block column — what {@code TheEndBiomeSource} reads as erosion. */
    public double erosionAt(int blockX, int blockZ) {
        return functions.get().erosion().compute(new DensityFunction.SinglePointContext(blockX, 0, blockZ));
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
        DensityFunction density = functions.get().density();
        int cells = 16 / cellWidth, corners = cells + 1;
        // Corner densities, [cornerX][cornerZ][level] — a column at a time, which is what the 2D memo wants.
        double[][][] corner = new double[corners][corners][levels];
        for (int i = 0; i < corners; i++)
            for (int j = 0; j < corners; j++)
                for (int level = 0; level < levels; level++)
                    corner[i][j][level] = density.compute(new DensityFunction.SinglePointContext(
                            chunkX * 16 + i * cellWidth, minY + level * cellHeight, chunkZ * 16 + j * cellWidth));

        short[] result = new short[256], underside = new short[256];
        boolean[] closed = new boolean[256]; // the run under the top has ended
        for (int i = 0; i < cells; i++)
            for (int j = 0; j < cells; j++) {
                double[] c00 = corner[i][j], c10 = corner[i + 1][j], c01 = corner[i][j + 1], c11 = corner[i + 1][j + 1];
                for (int level = levels - 2; level >= 0; level--) {
                    // A cell whose eight corners are all empty interpolates to empty everywhere.
                    if (c00[level] <= 0 && c10[level] <= 0 && c01[level] <= 0 && c11[level] <= 0 && c00[level + 1] <= 0
                            && c10[level + 1] <= 0 && c01[level + 1] <= 0 && c11[level + 1] <= 0) {
                        for (int dx = 0; dx < cellWidth; dx++)
                            for (int dz = 0; dz < cellWidth; dz++)
                                if (result[(i * cellWidth + dx) << 4 | (j * cellWidth + dz)] != 0)
                                    closed[(i * cellWidth + dx) << 4 | (j * cellWidth + dz)] = true;
                        continue;
                    }
                    for (int dy = cellHeight - 1; dy >= 0; dy--) {
                        double fy = dy / (double) cellHeight;
                        double y00 = lerp(fy, c00[level], c00[level + 1]), y10 = lerp(fy, c10[level], c10[level + 1]);
                        double y01 = lerp(fy, c01[level], c01[level + 1]), y11 = lerp(fy, c11[level], c11[level + 1]);
                        for (int dx = 0; dx < cellWidth; dx++) {
                            double fx = dx / (double) cellWidth;
                            double x0 = lerp(fx, y00, y10), x1 = lerp(fx, y01, y11);
                            for (int dz = 0; dz < cellWidth; dz++) {
                                int index = (i * cellWidth + dx) << 4 | (j * cellWidth + dz);
                                if (closed[index])
                                    continue;
                                short y = (short) (minY + level * cellHeight + dy);
                                if (lerp(dz / (double) cellWidth, x0, x1) > 0) {
                                    if (result[index] == 0)
                                        result[index] = y;
                                    underside[index] = y;
                                } else if (result[index] != 0)
                                    closed[index] = true;
                            }
                        }
                    }
                }
            }
        return new short[][] { result, underside };
    }

    private static double lerp(double t, double a, double b) {
        return a + t * (b - a);
    }

    /** A 2D function remembered for the last column asked — corners are evaluated a column at a time. */
    private static final class ColumnMemo implements DensityFunction.SimpleFunction {
        private final DensityFunction wrapped;
        private int lastX = Integer.MIN_VALUE, lastZ = Integer.MIN_VALUE;
        private double last;

        ColumnMemo(DensityFunction wrapped) {
            this.wrapped = wrapped;
        }

        @Override
        public double compute(DensityFunction.FunctionContext context) {
            if (context.blockX() != lastX || context.blockZ() != lastZ) {
                last = wrapped.compute(context);
                lastX = context.blockX();
                lastZ = context.blockZ();
            }
            return last;
        }

        @Override
        public double minValue() {
            return wrapped.minValue();
        }

        @Override
        public double maxValue() {
            return wrapped.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec() {
            throw new UnsupportedOperationException("EndTerrain's column memo is never serialised");
        }
    }
}
