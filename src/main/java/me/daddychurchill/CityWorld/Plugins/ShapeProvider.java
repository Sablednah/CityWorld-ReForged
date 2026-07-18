package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Context.RoadContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.TraditionalCachedYs;
import me.daddychurchill.CityWorld.compat.Biome;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.noise.NoiseGenerator;
import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;

public abstract class ShapeProvider extends Provider {

	public abstract int getWorldHeight();

	public abstract int getStreetLevel();

	public abstract int getSeaLevel();

	public abstract int getLandRange();

	public abstract int getSeaRange();

	public abstract int getConstuctMin();

	public abstract int getConstuctRange();

	public abstract double findPerciseY(CityWorldGenerator generator, int blockX, int blockZ);

	public abstract void preGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk,
			BiomeGrid biomes, AbstractCachedYs blockYs);

	public abstract void postGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk,
			AbstractCachedYs blockYs);

	public abstract void preGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs);

	public abstract void postGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs);

	protected abstract Biome remapBiome(CityWorldGenerator generator, PlatLot lot, Biome biome);

	protected abstract void allocateContexts(CityWorldGenerator generator);

	public abstract String getCollectionName();

	protected abstract void validateLots(CityWorldGenerator generator, PlatMap platmap);

	public abstract DataContext getContext(int originX, int originZ);

	protected abstract DataContext getContext(PlatMap platmap);

	public AbstractCachedYs getCachedYs(CityWorldGenerator generator, int chunkX, int chunkZ) {
		return new TraditionalCachedYs(generator, chunkX, chunkZ);
	}

	public void populateLots(CityWorldGenerator generator, PlatMap platmap) {
		try {
			allocateContexts(generator);

			// assume everything is natural for the moment
			platmap.context = natureContext;
			natureContext.populateMap(generator, platmap);
			natureContext.validateMap(generator, platmap);

			// place and validate the roads
			if (generator.getSettings().includeRoads) {
				platmap.context = getContext(platmap);
				platmap.populateRoads(); // this will see the platmap's context as natural since it hasn't been re-set
				// yet, see below
				platmap.validateRoads();

				// place the buildings
				if (generator.getSettings().includeBuildings) {

					// recalculate the context based on the "natural-ness" of the platmap
//					platmap.context = getContext(platmap);
					platmap.context.populateMap(generator, platmap);
					platmap.context.validateMap(generator, platmap);
				}

				// one last check
				validateLots(generator, platmap);
			}
		} catch (Exception e) {
			generator.reportException("ShapeProvider.populateLots FAILED", e);

		}
	}

	/**
	 * Guards the one-time context allocation.
	 *
	 * <p>Volatile, and {@code allocateContexts} is synchronized, because {@code populateLots} runs
	 * inside the platmap cache's {@code computeIfAbsent} — and different platmaps hash to different
	 * bins, so they plan on several threads at once. Unsynchronized, a thread could see this flag
	 * set before seeing the context fields it guards, and read a null context.
	 *
	 * <p>It cannot simply be allocated eagerly with the rest of the provider stack: the contexts'
	 * constructors read {@code generator.height} and {@code generator.streetLevel}, which are derived
	 * from this provider and so do not exist until after it is built. That is why upstream defers it
	 * to first use, and why the deferral has to be made thread-safe rather than removed.
	 */
	volatile boolean contextInitialized = false;
	DataContext natureContext;
	RoadContext roadContext;

	private final SimplexNoiseGenerator macroShape;
	private final SimplexNoiseGenerator microShape;
	final Odds odds;

	public int getStructureLevel() {
		return getStreetLevel();
	}

	public int findBlockY(CityWorldGenerator generator, int blockX, int blockZ) {
		return NoiseGenerator.floor(findPerciseY(generator, blockX, blockZ));
	}

	public int findGroundY(CityWorldGenerator generator, int blockX, int blockZ) {
		return findBlockY(generator, blockX, blockZ);
	}

	public double findPerciseFloodY(CityWorldGenerator generator, int blockX, int blockZ) {
		return getSeaLevel();
	}

	public int findFloodY(CityWorldGenerator generator, int blockX, int blockZ) {
		return getSeaLevel();
	}

	public int findHighestFloodY(CityWorldGenerator generator) {
		return getSeaLevel();
	}

	public int findLowestFloodY(CityWorldGenerator generator) {
		return getSeaLevel();
	}

//	public byte findAtmosphereIdAt(WorldGenerator generator, int blockY) {
//		return BlackMagic.airId;
//	}

	public boolean clearAtmosphere(CityWorldGenerator generator) {
		return true;
	}

	public Material findAtmosphereMaterialAt(CityWorldGenerator generator, int blockY) {
		return Material.AIR;
	}

//	public byte findGroundCoverIdAt(WorldGenerator generator, int blockY) {
//		return BlackMagic.airId;
//	}

	Material findGroundCoverMaterialAt(CityWorldGenerator generator, int blockY) {
		return Material.AIR;
	}

	public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return natureContext.createNaturalLot(generator, platmap, x, z);
	}

	public PlatLot createRoadLot(CityWorldGenerator generator, PlatMap platmap, int x, int z, boolean roundaboutPart,
			PlatLot oldLot) {
		return roadContext.createRoadLot(generator, platmap, x, z, roundaboutPart, oldLot);
	}

	public PlatLot createRoundaboutStatueLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return roadContext.createRoundaboutStatueLot(generator, platmap, x, z);
	}

	ShapeProvider(CityWorldGenerator generator, Odds odds) {
		super();
		this.odds = odds;
		long seed = generator.getWorldSeed();

		macroShape = new SimplexNoiseGenerator(seed + 2);
		microShape = new SimplexNoiseGenerator(seed + 3);

	}

	// Based on work contributed by drew-bahrue
	// (https://github.com/echurchill/CityWorld/pull/2)
	public static ShapeProvider loadProvider(CityWorldGenerator generator, Odds odds) {

		ShapeProvider provider = null;

		switch (generator.worldStyle) {

		// NATURE and METRO reshape Normal without dragging in a style-specific Context/Lot tree, so
		// they land here directly. The other six terrain styles (_Floating, _Flooded, _SandDunes,
		// _SnowDunes, _Astral, _Maze) each pull in a whole Context/<style> package and matching lots
		// (measured ~660–3,270 lines apiece); they land one complete style at a time behind this
		// same seam. Until each does, its case falls through to NORMAL rather than failing.
		case NATURE:
			provider = new ShapeProvider_Nature(generator, odds);
			break;
		case METRO:
			provider = new ShapeProvider_Metro(generator, odds);
			break;
		case FLOODED:
			provider = new ShapeProvider_Flooded(generator, odds);
			break;
		case FLOATING:
		case SANDDUNES:
		case SNOWDUNES:
		case ASTRAL:
		case MAZE:
		case DESTROYED:
		case SPARSE:
		case NORMAL:
			provider = new ShapeProvider_Normal(generator, odds);
			break;
		}

		return provider;
	}

	void actualGenerateStratas(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, int x, int z,
			Material substratumMaterial, Material stratumMaterial, int stratumY, Material subsurfaceMaterial,
			int subsurfaceY, Material surfaceMaterial, boolean surfaceCaves) {

		// Upstream hardcoded this to 0, the 1.14 world floor. It is now the level's real floor
		// (-64), so the strata reach all the way down and the world gains 64 blocks of underground
		// rather than opening onto a void. P4; see CityWorldGenerator.worldMinY.
		final int bottomOfWorld = generator.worldMinY;

		// compute the world block coordinates
		int blockX = chunk.sectionX * chunk.width + x;
		int blockZ = chunk.sectionZ * chunk.width + z;

		// make the base
		chunk.setBlock(x, bottomOfWorld, z, substratumMaterial);
		chunk.setBlock(x, bottomOfWorld + 1, z,
				generator.oreProvider.stratumMaterialAt(stratumMaterial, blockX, bottomOfWorld + 1, blockZ));

		// stony bits
		for (int y = bottomOfWorld + 2; y < stratumY; y++)
			if (lot.isValidStrataY(generator, blockX, y, blockZ)
					&& generator.shapeProvider.notACave(generator, blockX, y, blockZ))
				chunk.setBlock(x, y, z, generator.oreProvider.stratumMaterialAt(stratumMaterial, blockX, y, blockZ));
			else if (y <= generator.oreProvider.lavaFieldLevel && generator.getSettings().includeLavaFields)
				chunk.setBlock(x, y, z, Material.LAVA);

		// aggregate bits
		for (int y = stratumY; y < subsurfaceY - 1; y++)
			if (lot.isValidStrataY(generator, blockX, y, blockZ)
					&& (!surfaceCaves || generator.shapeProvider.notACave(generator, blockX, y, blockZ)))
				chunk.setBlock(x, y, z, subsurfaceMaterial);

		// icing for the cake
		if (!surfaceCaves || generator.shapeProvider.notACave(generator, blockX, subsurfaceY, blockZ)) {
			if (lot.isValidStrataY(generator, blockX, subsurfaceY - 1, blockZ))
				chunk.setBlock(x, subsurfaceY - 1, z, subsurfaceMaterial);
			if (lot.isValidStrataY(generator, blockX, subsurfaceY, blockZ))
				chunk.setBlock(x, subsurfaceY, z, surfaceMaterial);
		}
	}

	void generateStratas(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, int x, int z,
			Material substratumMaterial, Material stratumMaterial, int stratumY, Material subsurfaceMaterial,
			int subsurfaceY, Material surfaceMaterial, boolean surfaceCaves) {

		// a little crust please?
		actualGenerateStratas(generator, lot, chunk, x, z, substratumMaterial, stratumMaterial, stratumY,
				subsurfaceMaterial, subsurfaceY, surfaceMaterial, surfaceCaves);
	}

	void generateStratas(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, int x, int z,
			Material substratumMaterial, Material stratumMaterial, int stratumY, Material subsurfaceMaterial,
			int subsurfaceY, Material surfaceMaterial, int coverY, Material coverMaterial, boolean surfaceCaves) {

		// a little crust please?
		actualGenerateStratas(generator, lot, chunk, x, z, substratumMaterial, stratumMaterial, stratumY,
				subsurfaceMaterial, subsurfaceY, surfaceMaterial, surfaceCaves);

		// cover it up
		for (int y = subsurfaceY + 1; y <= coverY; y++)
			chunk.setBlock(x, y, z, coverMaterial);
	}

	// TODO refactor these over to UndergroundProvider (which should include
	// PlatLot's mines generator code)
	// TODO rename these to ifSoAndSo
	public abstract boolean isHorizontalNSShaft(int chunkX, int chunkY, int chunkZ);

	public abstract boolean isHorizontalWEShaft(int chunkX, int chunkY, int chunkZ);

	public abstract boolean isVerticalShaft(int chunkX, int chunkY, int chunkZ);

	// TODO refactor this so that it is a positive (maybe ifCave) instead of a
	// negative
	protected abstract boolean notACave(CityWorldGenerator generator, int blockX, int blockY, int blockZ);

	// macro slots
	private final static int macroRandomGeneratorSlot = 0;
	private final static int macroNSBridgeSlot = 1;

	// micro slots
	private final static int microRandomGeneratorSlot = 0;
	private final static int microRoundaboutSlot = 1;
	private final static int microSurfaceCaveSlot = 2;
	private final static int microIsolatedLotSlot = 3;
	private final static int microIsolatedConstructSlot = 4;

	private final double macroScale = 1.0 / 384.0;
	private final double microScale = 2.0;

	private double getMicroNoiseAt(double x, double z, int a) {
		return microShape.noise(x * microScale, z * microScale, a);
	}

	private double getMacroNoiseAt(double x, double z, int a) {
		return macroShape.noise(x * macroScale, z * macroScale, a);
	}

//	private int macroValueAt(double chunkX, double chunkZ, int slot, int scale) {
//		return NoiseGenerator.floor(macroScaleAt(chunkX, chunkZ, slot) * scale);
//	}
//
//	private int microValueAt(double chunkX, double chunkZ, int slot, int scale) {
//		return NoiseGenerator.floor(microScaleAt(chunkX, chunkZ, slot) * scale);
//	}
//
//	private double macroScaleAt(double chunkX, double chunkZ, int slot) {
//		return (getMacroNoiseAt(chunkX, chunkZ, slot) + 1.0) / 2.0;
//	}

	private double microScaleAt(double chunkX, double chunkZ, int slot) {
		return (getMicroNoiseAt(chunkX, chunkZ, slot) + 1.0) / 2.0;
	}

	private boolean macroBooleanAt(double chunkX, double chunkZ, int slot) {
		return getMacroNoiseAt(chunkX, chunkZ, slot) >= 0.0;
	}

	private boolean microBooleanAt(double chunkX, double chunkZ, int slot) {
		return getMicroNoiseAt(chunkX, chunkZ, slot) >= 0.0;
	}

	public Odds getMicroOddsGeneratorAt(int x, int z) {
		return new Odds((long) (getMicroNoiseAt(x, z, microRandomGeneratorSlot) * Long.MAX_VALUE));
	}

	public Odds getMacroOddsGeneratorAt(int x, int z) {
		return new Odds((long) (getMacroNoiseAt(x, z, macroRandomGeneratorSlot) * Long.MAX_VALUE));
	}

	public boolean getBridgePolarityAt(double chunkX, double chunkZ) {
		return macroBooleanAt(chunkX, chunkZ, macroNSBridgeSlot);
	}

	boolean isSurfaceCaveAt(double chunkX, double chunkZ) {
		return microBooleanAt(chunkX, chunkZ, microSurfaceCaveSlot);
	}

	public boolean isRoundaboutAt(double chunkX, double chunkZ, double oddsOfRoundabouts) {
		return microScaleAt(chunkX, chunkZ, microRoundaboutSlot) < oddsOfRoundabouts;
	}

	public boolean isIsolatedConstructAt(double chunkX, double chunkZ, double oddsOfIsolatedConstruct) {
		return microScaleAt(chunkX, chunkZ, microIsolatedConstructSlot) < oddsOfIsolatedConstruct;
	}

	public boolean isIsolatedLotAt(double chunkX, double chunkZ, double oddsOfIsolatedLots) {
		return microScaleAt(chunkX, chunkZ, microIsolatedLotSlot) < oddsOfIsolatedLots;
	}

}
