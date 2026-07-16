package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The ground cover: grass, flowers, mushrooms, saplings, trees, crops, coral.
 *
 * <p><b>The vocabulary below is ported verbatim; the placement is not.</b> {@link CoverageType} and
 * {@link CoverageSets} are pure data and the callers name their members directly (e.g. {@code ParkLot}
 * picks its park trees from a {@code CoverageType[]}), so they belong here now.
 *
 * <p>What is deferred is the 900 lines that turn a {@code CoverageType} into blocks. It runs in the
 * decoration pass and leans on {@code TreeProvider} (631) to actually grow trees — P5. Until then a
 * park is laid out correctly but unplanted.
 */
public class CoverProvider extends Provider {

	public enum CoverageType {
		NOTHING, GRASS, FERN, /* DEAD_GRASS, */ DANDELION, DEAD_BUSH,

		POPPY, BLUE_ORCHID, ALLIUM, AZURE_BLUET, OXEYE_DAISY, RED_TULIP, ORANGE_TULIP, WHITE_TULIP, PINK_TULIP,

		SUNFLOWER, LILAC, TALL_GRASS, TALL_FERN, ROSE_BUSH, PEONY,

		CACTUS, REED, EMERALD_GREEN,

		OAK_SAPLING, DARK_OAK_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING, ACACIA_SAPLING,

		MINI_OAK_TREE, SHORT_OAK_TREE, OAK_TREE, DARK_OAK_TREE, MINI_PINE_TREE, SHORT_PINE_TREE, PINE_TREE,
		TALL_PINE_TREE, MINI_BIRCH_TREE, SHORT_BIRCH_TREE, BIRCH_TREE, TALL_BIRCH_TREE, MINI_JUNGLE_TREE,
		SHORT_JUNGLE_TREE, JUNGLE_TREE, TALL_JUNGLE_TREE, MINI_SWAMP_TREE, SWAMP_TREE, MINI_ACACIA_TREE, ACACIA_TREE,

		MINI_OAK_TRUNK, OAK_TRUNK, TALL_OAK_TRUNK, MINI_PINE_TRUNK, PINE_TRUNK, TALL_PINE_TRUNK, MINI_BIRCH_TRUNK,
		BIRCH_TRUNK, TALL_BIRCH_TRUNK, MINI_JUNGLE_TRUNK, JUNGLE_TRUNK, TALL_JUNGLE_TRUNK, MINI_SWAMP_TRUNK,
		SWAMP_TRUNK, TALL_SWAMP_TRUNK, MINI_ACACIA_TRUNK, ACACIA_TRUNK, TALL_ACACIA_TRUNK,

		WHEAT, CARROTS, POTATO, MELON, PUMPKIN, BEETROOT,

		//		TALL_BROWN_MUSHROOM, TALL_RED_MUSHROOM,
		BROWN_MUSHROOM, RED_MUSHROOM, NETHERWART,

		BRAIN_CORAL, BUBBLE_CORAL, FIRE_CORAL, HORN_CORAL, TUBE_CORAL, SEAGRASS, KELP,

		FIRE
	}

	public enum CoverageSets {
		SHORT_FLOWERS, TALL_FLOWERS, ALL_FERNS, ALL_FLOWERS, SHORT_PLANTS, TALL_PLANTS, ALL_PLANTS, GENERAL_SAPLINGS,
		ALL_SAPLINGS, OAK_TREES, PINE_TREES, BIRCH_TREES, JUNGLE_TREES, ACACIA_TREES, SWAMP_TREES, SHORT_TREES,
		MEDIUM_TREES, TALL_TREES, ALL_TREES, PRARIE_PLANTS, EDIBLE_PLANTS, SHORT_MUSHROOMS, NETHER_PLANTS, DECAY_PLANTS,
		SEA_PLANTS, SEA_CORALS
	}

	public static CoverProvider loadProvider(CityWorldGenerator generator, Odds odds) {
		return new CoverProvider();
	}

	/** P5: plants one of {@code types}, chosen at random, at the given spot. */
	public void generateRandomCoverage(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			CoverageType... types) {
	}

	/** P5: plants something from the named set at the given spot. */
	public void generateCoverage(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			CoverageSets coverageSet) {
	}

	/** P5: makes a block able to grow things (tills/waters it, as the cover needs). */
	public void makePlantable(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {
	}

	/**
	 * Whether something could be planted here. Ported verbatim — it is a plain question about the
	 * blocks, not decoration, and callers branch on it.
	 */
	public boolean isPlantable(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {

		// only if the spot is empty and the spot above is empty
		return chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y, z);
	}
}
