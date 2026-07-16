package me.daddychurchill.CityWorld.Plugins;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Context.NatureContext;
import me.daddychurchill.CityWorld.Context.RoadContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.Biome;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;
import me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator;

public class ShapeProvider_Normal extends ShapeProvider {

	private static final Logger LOGGER = LogUtils.getLogger();

	private final SimplexOctaveGenerator landShape1;
	private final SimplexOctaveGenerator landShape2;
	private final SimplexOctaveGenerator seaShape;
	private final SimplexOctaveGenerator noiseShape;
	private final SimplexOctaveGenerator featureShape;
	private final SimplexNoiseGenerator caveShape;
	private final SimplexNoiseGenerator mineShape;

	final int height;
	final int seaLevel;
	private final int landRange;
	private final int seaRange;
	int constructMin;
	int constructRange;

	private final static int landFlattening = 32;
	private final static int seaFlattening = 4;
	private final static int landFactor1to2 = 3;
	final static int noiseVerticalScale = 3;
	private final static int featureVerticalScale = 10;
	private final static int fudgeVerticalScale = noiseVerticalScale * landFactor1to2
			+ featureVerticalScale * landFactor1to2;

	private final static double landFrequency1 = 1.50;
	private final static double landAmplitude1 = 20.0;
	private final static double landHorizontalScale1 = 1.0 / 2048.0;
	private final static double landFrequency2 = 1.0;
	private final static double landAmplitude2 = landAmplitude1 / landFactor1to2;
	private final static double landHorizontalScale2 = landHorizontalScale1 * landFactor1to2;

	private final static double seaFrequency = 1.00;
	private final static double seaAmplitude = 2.00;
	private final static double seaHorizontalScale = 1.0 / 384.0;

	private final static double noiseFrequency = 1.50;
	private final static double noiseAmplitude = 0.70;
	private final static double noiseHorizontalScale = 1.0 / 32.0;

	private final static double featureFrequency = 1.50;
	private final static double featureAmplitude = 0.75;
	private final static double featureHorizontalScale = 1.0 / 64.0;

	private final static double caveScale = 1.0 / 64.0;
	private final static double caveScaleY = caveScale * 2;
	private final static double caveThreshold = 0.75; // smaller the number the more larger the caves will be

	private final static double mineScale = 1.0 / 4.0;
	public final static double mineScaleY = mineScale;

	public ShapeProvider_Normal(CityWorldGenerator generator, Odds odds) {
		super(generator, odds);
		long seed = generator.getWorldSeed();

		landShape1 = new SimplexOctaveGenerator(seed, 4);
		landShape1.setScale(landHorizontalScale1);
		landShape2 = new SimplexOctaveGenerator(seed, 6);
		landShape2.setScale(landHorizontalScale2);
		seaShape = new SimplexOctaveGenerator(seed + 2, 8);
		seaShape.setScale(seaHorizontalScale);
		noiseShape = new SimplexOctaveGenerator(seed + 3, 16);
		noiseShape.setScale(noiseHorizontalScale);
		featureShape = new SimplexOctaveGenerator(seed + 4, 2);
		featureShape.setScale(featureHorizontalScale);

		caveShape = new SimplexNoiseGenerator(seed);
		mineShape = new SimplexNoiseGenerator(seed + 1);

		// get ranges
		// Upstream read these off the Bukkit World (world.getMaxHeight()/getSeaLevel()). A modern
		// ChunkGenerator has no World to ask and cannot hold per-world state, so the context is
		// handed them at construction instead — see CityWorldGenerator (PORTING.md, top risk #1).
		height = generator.getWorldMaxHeight();
		seaLevel = generator.getWorldSeaLevel();
		landRange = height - seaLevel - fudgeVerticalScale + landFlattening;
		seaRange = seaLevel - fudgeVerticalScale + seaFlattening;
		constructMin = seaLevel;
		constructRange = height - constructMin;
	}

	@Override
	protected void validateLots(CityWorldGenerator generator, PlatMap platmap) {
		// nothing to do in this one
	}

	@Override
	protected void allocateContexts(CityWorldGenerator generator) {
		if (!contextInitialized) {
			natureContext = new NatureContext(generator);
			roadContext = new RoadContext(generator);

			// Upstream also allocates the ten urban contexts here (park, highrise, construction,
			// midrise, municipal, industrial, lowrise, neighborhood, farm, outland) — the city
			// planning half of the brain. They arrive in wave 2 with the lots they build; see
			// getContext(PlatMap) below and PORTING.md.

			contextInitialized = true;
		}
	}

	/**
	 * Upstream believed this overload was dead code and said so, loudly, rather than deleting it.
	 * Kept — including the plea — because if it ever fires it means something reached for a context
	 * by origin instead of by platmap, which is worth noticing.
	 */
	@Override
	public DataContext getContext(int originX, int originZ) {
		LOGGER.info("IF YOU SEE THIS MESSAGE PLEASE SEND ME EMAIL AT eddie@virtualchurchill.com, THANKS");
		return null;
	}

	/**
	 * Picks the context for a platmap from how natural it still is.
	 *
	 * <p><b>Wave 1 stub — this is the city-planning ladder, and it is deferred, not lost.</b>
	 * Upstream grades {@code platmap.getNaturePercent()} into ten urban contexts, and the exact
	 * thresholds are the knobs that decide a world's character, so they are recorded here verbatim
	 * to be restored with the contexts in wave 2:
	 *
	 * <pre>
	 *   0.00        -> park (odds of a central park) else highrise
	 *   &lt; 0.05      -> highrise
	 *   &lt; 0.10      -> construction
	 *   &lt; 0.15      -> municipal      (if includeMunicipalities)
	 *   &lt; 0.25      -> midrise
	 *   &lt; 0.30      -> industrial     (if includeIndustrialSectors)
	 *   &lt; 0.40      -> lowrise
	 *   &lt; 0.55      -> neighborhood
	 *   &lt; 0.70      -> farm           (if includeFarms)
	 *   &lt; 0.75      -> outland
	 *   otherwise   -> nature (keep what we have)
	 * </pre>
	 *
	 * Returning nature until then is what upstream itself does for a fully-natural platmap, which
	 * is every platmap in wave 1 — so the terrain path runs unchanged and worlds simply have no
	 * cities in them yet.
	 */
	@Override
	public DataContext getContext(PlatMap platmap) {
		return natureContext;
	}

	@Override
	public String getCollectionName() {
		return "Normal";
	}

	@Override
	protected Biome remapBiome(CityWorldGenerator generator, PlatLot lot, Biome biome) {
		return generator.oreProvider.remapBiome(biome);
	}

	@Override
	public void preGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, BiomeGrid biomes,
			AbstractCachedYs blockYs) {
		Biome biome = lot.getChunkBiome();
		OreProvider ores = generator.oreProvider;
		boolean surfaceCaves = isSurfaceCaveAt(chunk.sectionX, chunk.sectionZ);

		// shape the world
		for (int x = 0; x < chunk.width; x++) {
			for (int z = 0; z < chunk.width; z++) {
				int y = blockYs.getBlockY(x, z);

				// buildable?
				if (lot.style == LotStyle.STRUCTURE || lot.style == LotStyle.ROUNDABOUT) {
					generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
							generator.streetLevel - 2, ores.subsurfaceMaterial, generator.streetLevel,
							ores.subsurfaceMaterial, false);

					// possibly buildable?
				} else if (y == generator.streetLevel) {
					generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial, y - 3,
							ores.subsurfaceMaterial, y, ores.surfaceMaterial, generator.getSettings().includeDecayedNature);

					// won't likely have a building
				} else {

					// on the beach
					if (y == generator.seaLevel) {
						generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
								y - 2, ores.fluidSubsurfaceMaterial, y, ores.fluidSurfaceMaterial,
								generator.getSettings().includeDecayedNature);
						biome = Biome.BEACH;

						// we are in the water! ...or are we?
					} else if (y < generator.seaLevel) {
						biome = Biome.DESERT;
						if (generator.getSettings().includeDecayedNature)
							if (generator.getSettings().includeAbovegroundFluids && y < generator.deepseaLevel)
								generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial,
										ores.stratumMaterial, y - 2, ores.fluidSubsurfaceMaterial, y,
										ores.fluidSurfaceMaterial, generator.deepseaLevel, ores.fluidMaterial, false);
							else
								generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial,
										ores.stratumMaterial, y - 2, ores.fluidSubsurfaceMaterial, y,
										ores.fluidSurfaceMaterial, true);
						else if (generator.getSettings().includeAbovegroundFluids) {
							generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
									y - 2, ores.fluidSubsurfaceMaterial, y, ores.fluidSurfaceMaterial,
									generator.seaLevel, ores.fluidMaterial, false);
							biome = Biome.OCEAN;
						} else
							generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
									y - 2, ores.fluidSubsurfaceMaterial, y, ores.fluidSurfaceMaterial, false);

						// we are in the mountains
					} else {

						// regular trees only
						if (y < generator.treeLevel) {
							generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
									y - 3, ores.subsurfaceMaterial, y, ores.surfaceMaterial,
									generator.getSettings().includeDecayedNature);
							biome = Biome.FOREST;

							// regular trees and some evergreen trees
						} else if (y < generator.evergreenLevel) {
							generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
									y - 2, ores.subsurfaceMaterial, y, ores.surfaceMaterial, surfaceCaves);
							biome = Biome.BIRCH_FOREST_HILLS;

							// evergreen and some of fallen snow
						} else if (y < generator.snowLevel) {
							generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial, ores.stratumMaterial,
									y - 1, ores.subsurfaceMaterial, y, ores.surfaceMaterial, surfaceCaves);
							biome = Biome.TAIGA_HILLS;

							// only snow up here!
						} else {
							if (generator.getSettings().includeAbovegroundFluids && y > generator.snowLevel + 2)
								generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial,
										ores.stratumMaterial, y - 1, ores.stratumMaterial, y, ores.fluidFrozenMaterial,
										surfaceCaves);
							else
								generateStratas(generator, lot, chunk, x, z, ores.substratumMaterial,
										ores.stratumMaterial, y - 1, ores.stratumMaterial, y, ores.stratumMaterial,
										surfaceCaves);
							biome = Biome.SNOWY_MOUNTAINS;
						}
					}
				}

				// set biome for block
				if (generator.getSettings().includeDecayedNature)
					biome = Biome.DESERT;
				biomes.setBiome(x, z, remapBiome(generator, lot, biome));
			}
		}
	}

	@Override
	public void postGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk,
			AbstractCachedYs blockYs) {

		// mines please
		lot.generateMines(generator, chunk);
	}

	@Override
	public void preGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs) {

		// put bones in?
		lot.generateBones(generator, chunk);
	}

	@Override
	public void postGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs) {

		// put ores in?
		lot.generateOres(generator, chunk);

		// do we do it or not?
		lot.generateMines(generator, chunk);
	}

	@Override
	public int getWorldHeight() {
		return height;
	}

	@Override
	public int getStreetLevel() {
		return seaLevel + 1;
	}

	@Override
	public int getSeaLevel() {
		return seaLevel;
	}

	@Override
	public int getLandRange() {
		return landRange;
	}

	@Override
	public int getSeaRange() {
		return seaRange;
	}

	@Override
	public int getConstuctMin() {
		return constructMin;
	}

	@Override
	public int getConstuctRange() {
		return constructRange;
	}

	@Override
	public double findPerciseY(CityWorldGenerator generator, int blockX, int blockZ) {
		double y = 0;

		// shape the noise
		double noise = noiseShape.noise(blockX, blockZ, noiseFrequency, noiseAmplitude, true);
		double feature = featureShape.noise(blockX, blockZ, featureFrequency, featureAmplitude, true);

		double land1 = seaLevel + (landShape1.noise(blockX, blockZ, landFrequency1, landAmplitude1, true) * landRange)
				+ (noise * noiseVerticalScale * landFactor1to2 + feature * featureVerticalScale * landFactor1to2)
				- landFlattening;
		double land2 = seaLevel
				+ (landShape2.noise(blockX, blockZ, landFrequency2, landAmplitude2, true)
				* (landRange / (double) landFactor1to2))
				+ (noise * noiseVerticalScale + feature * featureVerticalScale) - landFlattening;

		double landY = Math.max(land1, land2);
		double sea = seaShape.noise(blockX, blockZ, seaFrequency, seaAmplitude, true);

		// calculate the Ys
		double seaY = seaLevel + (sea * seaRange) + (noise * noiseVerticalScale) + seaFlattening;

		// land is below the sea
		if (landY <= seaLevel) {

			// if seabed is too high... then we might be buildable
			if (seaY >= seaLevel) {
				y = seaLevel + 1;

				// if we are too near the sea then we must be on the beach
				if (seaY <= seaLevel + 1) {
					y = seaLevel;
				}

				// if land is higher than the seabed use land to smooth
				// out under water base of the mountains
			} else if (landY >= seaY) {
				y = Math.min(seaLevel, landY + 1);

				// otherwise just take the sea bed as is
			} else {
				y = Math.min(seaLevel, seaY);
			}

			// must be a mountain then
		} else {
			y = Math.max(seaLevel, landY + 1);
		}

		// for real?
		if (!generator.getSettings().includeMountains)
			y = Math.min(seaLevel + 1, y);
		if (!generator.getSettings().includeSeas)
			y = Math.max(seaLevel + 1, y);

		// range validation
		return Math.min(height - 3, Math.max(y, 3));
	}

	@Override
	public boolean isHorizontalNSShaft(int chunkX, int chunkY, int chunkZ) {
		return mineShape.noise(chunkX * mineScale, chunkY * mineScale, chunkZ * mineScale + 0.5) > 0.0;
	}

	@Override
	public boolean isHorizontalWEShaft(int chunkX, int chunkY, int chunkZ) {
		return mineShape.noise(chunkX * mineScale + 0.5, chunkY * mineScale, chunkZ * mineScale) > 0.0;
	}

	@Override
	public boolean isVerticalShaft(int chunkX, int chunkY, int chunkZ) {
		return mineShape.noise(chunkX * mineScale, chunkY * mineScale + 0.5, chunkZ * mineScale) > 0.0;
	}

	@Override
	public boolean notACave(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		if (generator.getSettings().includeCaves) {
			double cave = caveShape.noise(blockX * caveScale, blockY * caveScaleY, blockZ * caveScale);
			return !(cave > caveThreshold || cave < -caveThreshold);
		} else
			return true;
	}

}
