package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;

/**
 * A cheap sample of a chunk's terrain heights, used to ask "is this flat enough to build on?"
 *
 * <p>Where {@link AbstractCachedYs} computes all 256 columns of a chunk, this samples 5 or 9 points
 * — which is what makes it affordable to ask about chunks that aren't being generated. That is
 * exactly what {@code PlatMap.placeIntersection} does when it probes 5 chunks out in each direction
 * to decide where roads go.
 */
public final class HeightInfo extends AbstractYs {

	public static HeightInfo getHeightsFaster(CityWorldGenerator generator, int blockX, int blockZ) {
		HeightInfo heights = new HeightInfo();

		int sumHeight = 0;

		sumHeight += heights.add(generator, blockX + 8, blockZ + 8); // center
		sumHeight += heights.add(generator, blockX, blockZ); // corners
		sumHeight += heights.add(generator, blockX + 15, blockZ);
		sumHeight += heights.add(generator, blockX, blockZ + 15);
		sumHeight += heights.add(generator, blockX + 15, blockZ + 15);

		heights.calcState(generator, sumHeight, 5);
		return heights;
	}

	public static HeightInfo getHeightsFast(CityWorldGenerator generator, int blockX, int blockZ) {
		HeightInfo heights = new HeightInfo();

		int sumHeight = 0;

		sumHeight += heights.add(generator, blockX + 8, blockZ + 8); // center
		sumHeight += heights.add(generator, blockX, blockZ); // corners
		sumHeight += heights.add(generator, blockX + 15, blockZ);

		sumHeight += heights.add(generator, blockX, blockZ + 15);
		sumHeight += heights.add(generator, blockX + 15, blockZ + 15);
		sumHeight += heights.add(generator, blockX, blockZ + 8); // edges

		sumHeight += heights.add(generator, blockX + 15, blockZ + 8);
		sumHeight += heights.add(generator, blockX + 8, blockZ + 15);
		sumHeight += heights.add(generator, blockX + 8, blockZ + 15);

		heights.calcState(generator, sumHeight, 9);
		return heights;
	}

	/**
	 * Whether a chunk is flat, at street level, and therefore worth putting a city on. This single
	 * predicate is what decides where CityWorld's roads — and so its cities — can exist at all.
	 */
	public static boolean isBuildableAt(CityWorldGenerator generator, int blockX, int blockZ) {
		return getHeightsFaster(generator, blockX, blockZ).getState() == HeightState.BUILDING;
	}

	public static boolean isBuildableToNorth(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX(), chunk.getOriginZ() - chunk.width);
	}

	public static boolean isBuildableToSouth(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX(), chunk.getOriginZ() + chunk.width);
	}

	public static boolean isBuildableToWest(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() - chunk.width, chunk.getOriginZ());
	}

	public static boolean isBuildableToEast(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() + chunk.width, chunk.getOriginZ());
	}

	public static boolean isBuildableToNorthWest(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() - chunk.width, chunk.getOriginZ() - chunk.width);
	}

	public static boolean isBuildableToSouthWest(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() - chunk.width, chunk.getOriginZ() + chunk.width);
	}

	public static boolean isBuildableToNorthEast(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() + chunk.width, chunk.getOriginZ() - chunk.width);
	}

	public static boolean isBuildableToSouthEast(CityWorldGenerator generator, AbstractBlocks chunk) {
		return isBuildableAt(generator, chunk.getOriginX() + chunk.width, chunk.getOriginZ() + chunk.width);
	}

	public boolean anyEmpties = false;

	private int add(CityWorldGenerator generator, int x, int z) {
		// we will need to get the Y the hard way
		int value = generator.getFarBlockY(x, z);
		anyEmpties = anyEmpties || value == 0;
		calcMinMax(x, value, z);
		return value;
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
}
