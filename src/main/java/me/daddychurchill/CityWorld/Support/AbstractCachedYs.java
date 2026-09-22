package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.noise.NoiseGenerator;

public abstract class AbstractCachedYs extends AbstractYs {

	// extremes
	int segmentWidth;

	final double[][] blockYs = new double[width][width];

	AbstractCachedYs(CityWorldGenerator generator, int chunkX, int chunkZ) {

		// compute offset to start of chunk
		int originX = chunkX * width;
		int originZ = chunkZ * width;
		double sumHeight = 0.0;

		// calculate the Ys for this chunk
		for (int x = 0; x < width; x++) {
			for (int z = 0; z < width; z++) {

				// how high are we?
				blockYs[x][z] = generator.shapeProvider.findPerciseY(generator, originX + x, originZ + z);
				sumHeight = sumHeight + blockYs[x][z];

				// keep the tally going
				calcMinMax(x, NoiseGenerator.floor(blockYs[x][z]), z);
			}
		}

		//noinspection SuspiciousNameCombination
		calcState(generator, NoiseGenerator.floor(sumHeight), width * width);
	}

	public int getMaxYWithin(int x1, int x2, int z1, int z2) {
		assert (x1 >= 0 && x2 <= 15 && z1 >= 0 && z2 <= 15);
		int maxY = Integer.MIN_VALUE;
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++) {
				int y = getBlockY(x, z);
				if (y > maxY)
					maxY = y;
			}
		return maxY;
	}

	public int getBlockY(int x, int z) {
		return NoiseGenerator.floor(blockYs[x][z]);
	}

	public double getPerciseY(int x, int z) {
		return blockYs[x][z];
	}

	/**
	 * Whether the structure pad has already adjusted this chunk's planned heights. One-shot: the pad
	 * must never run twice over the same chunk, or it would blend against ground it had itself moved.
	 */
	private boolean padded;

	public boolean isPadded() {
		return padded;
	}

	public void markPadded() {
		padded = true;
	}

	/**
	 * Adjust one column's PLANNED height.
	 *
	 * <p>This is the seam the structure pad needs. {@code blockYs} is {@code final}, but only the
	 * reference is — the contents are writable, and terrain is drawn from this array
	 * ({@code PlatLot.generateChunk} hands it to {@code preGenerateChunk}/{@code postGenerateChunk})
	 * while {@code PlatLot.generateSurface} hands the same array to the surface provider at decoration
	 * time. Changing it here therefore moves terrain and surface together.
	 *
	 * <p>That single source of truth is the whole point: the previous pad rewrote BLOCKS at the terrain
	 * stage and left these heights alone, so decoration painted grass and snow at the old level and left
	 * a floating lid over a cavity — 28.5% of columns in one village footprint (2026-09-21).
	 *
	 * <p><b>Call {@link #recompute} once after any run of these.</b>
	 */
	public void setPerciseY(int x, int z, double y) {
		blockYs[x][z] = y;
	}

	/**
	 * Recompute the derived state after {@link #setPerciseY}.
	 *
	 * <p>⚠ {@code calcMinMax} only ever WIDENS the extremes — it cannot lower a minimum — so the
	 * extremes must be reset before re-walking, or a column that was shaved down would leave a stale
	 * {@code minHeight} behind. That matters well beyond cosmetics: {@code isShaftableLevel} and the
	 * mine level loops key off {@code getMinHeight()}, and {@code calcState} decides whether this chunk
	 * counts as sea, buildable or peak.
	 */
	public void recompute(CityWorldGenerator generator) {
		minHeight = Integer.MAX_VALUE;
		maxHeight = Integer.MIN_VALUE;
		double sumHeight = 0.0;
		for (int x = 0; x < width; x++)
			for (int z = 0; z < width; z++) {
				sumHeight += blockYs[x][z];
				calcMinMax(x, NoiseGenerator.floor(blockYs[x][z]), z);
			}
		calcState(generator, NoiseGenerator.floor(sumHeight), width * width);
	}

	public Point getHighPoint() {
		return new Point(maxHeightX, maxHeight, maxHeightZ);
	}

	public Point getLowPoint() {
		return new Point(minHeightX, minHeight, minHeightZ);
	}

	public int getSegment(int x, int z) {
		return 0;
	}

	@Override
	public int getMinHeight() {
		return minHeight;
	}

	@Override
	public int getMaxHeight() {
		return maxHeight;
	}

	@Override
	public int getAverageHeight() {
		return averageHeight;
	}

	public int getSegmentWidth() {
		return segmentWidth;
	}
}
