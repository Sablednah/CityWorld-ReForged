package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.noise.NoiseGenerator;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.CoverProvider.CoverageSets;
import me.daddychurchill.CityWorld.Plugins.CoverProvider.CoverageType;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

public class SurfaceProvider_Normal extends SurfaceProvider {

	public SurfaceProvider_Normal(Odds odds) {
		super(odds);
		// TODO Auto-generated constructor stub
	}

	@Override
	protected void generateSurfacePoint(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
			CoverProvider foliage, int x, double perciseY, int z, boolean includeTrees) {
		OreProvider ores = generator.oreProvider;
		int y = NoiseGenerator.floor(perciseY);

		// roll the dice
		double primary = odds.getRandomDouble();
		double secondary = odds.getRandomDouble();

		// top of the world?
		if (y >= generator.snowLevel) {
			if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN)
				// MODERN: ice the peaks properly with stable full blocks (snow / packed / blue ice /
				// powder snow) instead of a loose snow layer sitting on ice — which is an illegal state
				// that cascades away the moment anything touches it.
				generateModernIcecap(generator, chunk, x, y, z, perciseY);
			else
				ores.dropSnow(generator, chunk, x, y, z,
						(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));

			// are on a plantable spot?
		} else if (foliage.isPlantable(generator, chunk, x, y, z)) {

			// below sea level and plantable.. then cactus?
			if (y <= generator.seaLevel) {

				// trees? but only if we are not too close to the edge
				if (includeTrees) {
					if (generator.getSettings().includeAbovegroundFluids) {
						if (primary < reedOdds) {
							if (chunk.isType(x, y, z, Material.SAND)) {
								foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.REED);
							}
						}
					} else {
						if (primary < cactusOdds && x % 2 == 0 && z % 2 != 0) {
							if (chunk.isSurroundedByEmpty(x, y + 1, z))
								foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.CACTUS);
						}
					}
				}

				// regular trees, grass and flowers only
			} else if (y < generator.treeLevel) {

				// trees? but only if we are not too close to the edge of the chunk
				if (includeTrees && primary < treeOdds && inTreeRange(x, z)) {
					if (secondary < treeAltTallOdds && inBigTreeRange(x, z))
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.DARK_OAK_TREE);
					else if (secondary < treeAltOdds)
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.BIRCH_TREE);
					else
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.OAK_TREE);

					// foliage?
				} else if (primary < foliageOdds) {

					// what to pepper about
					foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageSets.PRARIE_PLANTS);
				}

				// regular trees, grass and some evergreen trees... no flowers
			} else if (y < generator.evergreenLevel) {

				// trees?
				if (includeTrees && primary < treeOdds && inTreeRange(x, z)) {

					// range change?
					if (secondary > ((double) (y - generator.treeLevel) / (double) generator.deciduousRange))
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.OAK_TREE);
					else
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.PINE_TREE);

					// foliage?
				} else if (primary < foliageOdds) {

					// range change?
					if (secondary > ((double) (y - generator.treeLevel) / (double) generator.deciduousRange))
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageSets.SHORT_PLANTS);
					else
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageSets.ALL_FERNS);
				}

				// evergreen and some grass and fallen snow, no regular trees or flowers
			} else if (y < generator.snowLevel) {

				// trees?
				if (includeTrees && primary < treeOdds && x % 2 == 0 && z % 2 != 0) {
					if (secondary < treeTallOdds)
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.PINE_TREE);
					else
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.TALL_PINE_TREE);

					// foliage?
				} else if (primary < foliageOdds) {

					// range change?
					if (secondary > ((double) (y - generator.evergreenLevel) / (double) generator.evergreenRange)) {
						if (odds.playOdds(flowerFernOdds))
							foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.FERN);
					} else {
						generator.oreProvider.dropSnow(generator, chunk, x, y + 5, z);
					}
				}
			}

			// can't plant, maybe there is something else I can do
		} else {

			// below sea level?
			if (y < generator.seaLevel) {
				if (generator.getSettings().includeAbovegroundFluids) {

					// trees? but only if we are not too close to the edge
					if (includeTrees && primary < treeOdds && x % 2 == 0 && z % 2 != 0) {
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageSets.SEA_CORALS);
					} else {
						foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageSets.SEA_PLANTS);
					}
				} else
					foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.DEAD_BUSH);

//				// trees? but only if we are not too close to the edge
//				if (includeTrees) {
//					if (generator.settings.includeAbovegroundFluids) {
//						if (primary < reedOdds) {
//							if (chunk.isType(x, y, z, Material.SAND)) {
//								foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.REED);
//							}
//						}
//					} else {
//						
//					}
//				}

				// regular trees, grass and flowers only
			} else if (y < generator.treeLevel) {

				// regular trees, grass and some evergreen trees... no flowers
			} else if (y < generator.evergreenLevel) {

				// evergreen and some grass and fallen snow, no regular trees or flowers
			} else if (y < generator.snowLevel) {

				if (primary < foliageOdds) {

					// range change?
					if (secondary > ((double) (y - generator.evergreenLevel) / (double) generator.evergreenRange)) {
						if (odds.playOdds(0.10) && foliage.isPlantable(generator, chunk, x, y, z))
							foliage.generateCoverage(generator, chunk, x, y + 1, z, CoverageType.FERN);
					} else {
						ores.dropSnow(generator, chunk, x, y, z);
					}
				}
			}
		}
	}

	/**
	 * MODERN mountaintop surfacing. Grades the cap by how high the peak stands above the snow line and
	 * lays only stable, full-cube blocks so nothing breaks on contact:
	 * <ul>
	 *   <li>snowy slopes (low) — a solid snow-block surface topped by a legal snow layer, the odd
	 *       powder-snow pocket;</li>
	 *   <li>upper slopes (mid) — snow blocks veined with packed ice and powder-snow pockets;</li>
	 *   <li>frozen peaks (high) — packed ice, glacier-blue ice at the very top, occasional snow caps.</li>
	 * </ul>
	 * Snow <em>layers</em> only ever go on snow blocks (never on ice), which is what fixes the
	 * cascading-snow bug.
	 */
	private void generateModernIcecap(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			double perciseY) {
		OreProvider ores = generator.oreProvider;

		// probe for the real solid top (mirrors dropSnow) — the empty cell above it is where a cap sits
		int emptyY = chunk.findLastEmptyBelow(x, y + 1, z, y - 6);
		int topY = emptyY - 1;
		if (topY < generator.snowLevel)
			return; // not actually standing above the snow line here

		int peakTop = generator.seaLevel + generator.landRange;
		double f = (double) (topY - generator.snowLevel) / Math.max(1, peakTop - generator.snowLevel);
		if (f < 0.0)
			f = 0.0;
		if (f > 1.0)
			f = 1.0;

		double roll = odds.getRandomDouble();

		if (f >= 0.7) {
			// frozen peaks
			Material cap = f >= 0.9 ? Material.BLUE_ICE : roll < 0.15 ? Material.SNOW_BLOCK : Material.PACKED_ICE;
			chunk.setBlock(x, topY, z, cap);
			if (roll > 0.92)
				chunk.setBlock(x, emptyY, z, Material.POWDER_SNOW);
		} else if (f >= 0.35) {
			// upper slopes
			Material cap = roll < 0.25 ? Material.PACKED_ICE : roll > 0.9 ? Material.POWDER_SNOW : Material.SNOW_BLOCK;
			chunk.setBlock(x, topY, z, cap);
			if (cap == Material.SNOW_BLOCK && roll > 0.5)
				ores.dropSnow(generator, chunk, x, emptyY, z,
						(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));
		} else {
			// snowy slopes
			chunk.setBlock(x, topY, z, Material.SNOW_BLOCK);
			if (roll > 0.94)
				chunk.setBlock(x, emptyY, z, Material.POWDER_SNOW);
			else
				ores.dropSnow(generator, chunk, x, emptyY, z,
						(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));
		}
	}
}
