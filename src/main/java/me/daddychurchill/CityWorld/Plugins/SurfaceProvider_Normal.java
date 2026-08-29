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

	// how far below the snow line MODERN begins icing the peaks — makes bare frozen tips less rare
	private final static int MODERN_ICE_DROP = 5;

	/**
	 * How much higher a <em>warm</em> region has to be before MODERN ice caps it.
	 *
	 * <p>The icecap used to be purely elevation-based, so <b>every</b> mountain iced over — including
	 * ones standing in a desert. Across a dozen worlds that read as a world far colder than its climate
	 * map actually was, and it is the reason ice peaks and snowy biomes seemed to be everywhere at once.
	 * Lifting the ice line with temperature means cold regions cap as before while hot ones simply never
	 * get high enough, so the caps become a feature of cold places rather than of all high places.
	 */
	private final static int MODERN_ICE_WARM_LIFT = 40;

	// share of icecap cells that become a flush powder-snow pocket (a real fall-in hazard, findable)
	private final static double POWDER_SNOW_ODDS = 0.15;

	// ice grading by blocks above the snow line (reachable heights — see generateModernIcecap):
	// snowy slopes below UPPER, then packed-ice-veined slopes, frozen peaks, glacier-blue tips.
	private final static int ICE_UPPER_ABOVE = 6;
	private final static int ICE_FROZEN_ABOVE = 12;
	private final static int ICE_BLUE_ABOVE = 18;

	public SurfaceProvider_Normal(Odds odds) {
		super(odds);
		// TODO Auto-generated constructor stub
	}

	/** Whether CityWorld's own cover belongs on this lot — always, except wild land in VANILLA mode. */
	private static boolean cityworldPlants(CityWorldGenerator generator, PlatLot lot) {
		return generator.getSettings().cityworldDecoratesWild()
				|| lot == null || lot.style != PlatLot.LotStyle.NATURE;
	}

	@Override
	protected void generateSurfacePoint(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
			CoverProvider foliage, int x, double perciseY, int z, boolean includeTrees) {
		OreProvider ores = generator.oreProvider;
		int y = NoiseGenerator.floor(perciseY);

		// roll the dice
		double primary = odds.getRandomDouble();
		double secondary = odds.getRandomDouble();

		// top of the world? MODERN starts the ice a touch lower than the snow line so bare peaks aren't
		// so rare (playtest: the caps looked great but too seldom).
		boolean modern = generator.isModernStyle();
		int iceStart = generator.snowLevel;
		if (modern) {
			// Ice where it is cold, not merely where it is high — see MODERN_ICE_WARM_LIFT.
			double warmth = generator.getTemperature(chunk.getOriginX() + x, chunk.getOriginZ() + z);
			iceStart = generator.snowLevel - MODERN_ICE_DROP + (int) Math.round(warmth * MODERN_ICE_WARM_LIFT);
		}
		if (y >= iceStart) {
			if (modern)
				// MODERN: ice the peaks properly with stable full blocks (snow / packed / blue ice /
				// powder snow) instead of a loose snow layer sitting on ice — which is an illegal state
				// that cascades away the moment anything touches it.
				generateModernIcecap(generator, chunk, x, y, z, perciseY);
			else
				ores.dropSnow(generator, chunk, x, y, z,
						(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));

			// are on a plantable spot?
			// wildDecoration=VANILLA hands wild land to vanilla (and to any biome mod): CityWorld places
			// no plants, cactus or reeds there. Deliberately gated here rather than by skipping the whole
			// surface pass, because the icecap above must still run — dropping it left the peaks bare.
		} else if (cityworldPlants(generator, lot) && foliage.isPlantable(generator, chunk, x, y, z)) {

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

		// Grade by ABSOLUTE height above the snow line, not a fraction of the theoretical peak ceiling
		// (seaLevel+landRange) — real terrain tops out well short of that, so the fraction never reached
		// the frozen-peak/blue-ice tiers (probe: max peak only ~0.6 of the way up). These offsets are
		// reachable, so the caps actually appear on tall peaks.
		int above = topY - generator.snowLevel;

		double roll = odds.getRandomDouble();
		// a separate roll for powder snow so it doesn't correlate with the cap choice — placed FLUSH with
		// the surface (a real walk-in pocket you fall into), not as a camouflaged bump on top.
		boolean powder = odds.getRandomDouble() < POWDER_SNOW_ODDS;

		if (above >= ICE_BLUE_ABOVE) {
			// the very tips — glacier-blue ice, powder nestled in
			chunk.setBlock(x, topY, z, powder ? Material.POWDER_SNOW : Material.BLUE_ICE);
		} else if (above >= ICE_FROZEN_ABOVE) {
			// frozen peaks — packed ice with the odd snow-block patch and powder pockets
			chunk.setBlock(x, topY, z,
					powder ? Material.POWDER_SNOW : roll < 0.2 ? Material.SNOW_BLOCK : Material.PACKED_ICE);
		} else if (above >= ICE_UPPER_ABOVE) {
			// upper slopes — snow blocks veined with packed ice and powder-snow pockets
			if (powder)
				chunk.setBlock(x, topY, z, Material.POWDER_SNOW);
			else {
				Material cap = roll < 0.3 ? Material.PACKED_ICE : Material.SNOW_BLOCK;
				chunk.setBlock(x, topY, z, cap);
				if (cap == Material.SNOW_BLOCK && roll > 0.5)
					ores.dropSnow(generator, chunk, x, emptyY, z,
							(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));
			}
		} else {
			// snowy slopes — mostly snow, findable powder-snow patches, the odd deeper drift
			if (powder)
				chunk.setBlock(x, topY, z, Material.POWDER_SNOW);
			else {
				chunk.setBlock(x, topY, z, Material.SNOW_BLOCK);
				if (roll > 0.6)
					ores.dropSnow(generator, chunk, x, emptyY, z,
							(byte) NoiseGenerator.floor((perciseY - Math.floor(perciseY)) * 8.0));
			}
		}
	}
}
