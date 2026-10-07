package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;

public abstract class AbstractYs {

	public enum HeightState {
		EMPTY, DEEPSEA, SEA, BUILDING, LOWLAND, MIDLAND, HIGHLAND, PEAK
	}

	private HeightState state;

	public HeightState getState() {
		return state;
	}

	public abstract int getMinHeight();

	public abstract int getMaxHeight();

	public abstract int getAverageHeight();

	final static int width = AbstractBlocks.sectionBlockWidth;
	int minHeight = Integer.MAX_VALUE;
	int minHeightX = 0;
	int minHeightZ = 0;
	int maxHeight = Integer.MIN_VALUE;
	int maxHeightX = 0;
	int maxHeightZ = 0;
	int averageHeight;

	private int getRange() {
		return maxHeight - minHeight;
	}

	public final boolean isSortaFlat() {
		return getRange() < 8;
	}

	public final boolean isAbsolutelyFlat() {
		return maxHeight == minHeight;
	}

	public final boolean isAbsolutelyEmpty() {
		return maxHeight == 0;
	}

	public final boolean isOnLevel(int value) {
		return value == maxHeight && value == minHeight;
	}

	public final boolean isSortaOnLevel(int value) {
		return minHeight <= value && value >= maxHeight;
	}

	public final boolean isBuildable() {
		return state == HeightState.BUILDING;
	}

	public final boolean isSea() {
		return state == HeightState.DEEPSEA || state == HeightState.SEA;
	}

	void calcMinMax(int x, int y, int z) {
		if (y < minHeight) {
			minHeight = y;
			minHeightX = x;
			minHeightZ = z;
		}
		if (y > maxHeight) {
			maxHeight = y;
			maxHeightX = x;
			maxHeightZ = z;
		}
	}

	/**
	 * A river covers at least half this flat chunk at street level (CityWorld's own rivers, {@code worldgen/RiverNetwork}): it is
	 * water, whatever its five or nine sample columns saw — a stream between them read as buildable, and the road
	 * laid over it dammed it. As water, a road bridges it. A river up in the hills leaves its chunk as it was: a
	 * road there tunnels, as upstream's does, rather than bridging at street level under the hill.
	 */
	final void riverCheck(CityWorldGenerator generator, int blockX, int blockZ) {
		if ((state == HeightState.BUILDING || state == HeightState.LOWLAND) && !generator.vanillaTerrain
				&& generator.shapeProvider.riverMostlyAt(blockX >> 4, blockZ >> 4))
			state = HeightState.SEA;
	}

	final void calcState(CityWorldGenerator generator, int sumHeight, int count) {
		averageHeight = sumHeight / count;
		if (maxHeight == 0)
			state = HeightState.EMPTY;
		else if (maxHeight <= generator.deepseaLevel)
			state = HeightState.DEEPSEA;
		else if (maxHeight <= generator.seaLevel)
			state = HeightState.SEA;
		else if (maxHeight == generator.structureLevel && minHeight == generator.structureLevel)
			state = HeightState.BUILDING;
		else {
			if (averageHeight <= generator.treeLevel)
				state = HeightState.LOWLAND;
			else if (averageHeight <= generator.evergreenLevel)
				state = HeightState.MIDLAND;
			else if (averageHeight >= generator.snowLevel)
				state = HeightState.PEAK;
			else
				state = HeightState.HIGHLAND;
		}
	}

}
