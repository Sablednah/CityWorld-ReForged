package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.Biome;
import me.daddychurchill.CityWorld.compat.BlockFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.block.state.properties.SlabType;
import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

public abstract class PlatLot {

	// extremes
	protected final int chunkX;
	protected final int chunkZ;
	protected final AbstractCachedYs blockYs;

	//	protected Odds platmapOdds;
	protected Odds chunkOdds;

	// styling!
	public enum LotStyle {
		NATURE, STRUCTURE, ROAD, ROUNDABOUT
	}

	public LotStyle style;
	public boolean trulyIsolated;
	protected final boolean inACity;

	final Material pavementSidewalk;
	final Material dirtroadSidewalk;

	PlatLot(PlatMap platmap, int chunkX, int chunkZ) {
		super();
		this.chunkX = chunkX;
		this.chunkZ = chunkZ;
		this.style = LotStyle.NATURE;
		this.trulyIsolated = false;
		this.inACity = platmap.generator.getSettings().inCityRange(chunkX, chunkZ);

		// pavement is 0, read in RoadLot
		// lines is 1, read in RoadLot
		pavementSidewalk = platmap.generator.materialProvider.itemsMaterialListFor_Roads.getNthMaterial(2,
				Material.STONE_SLAB);
		// dirt is 3, read in RoadLot
		dirtroadSidewalk = platmap.generator.materialProvider.itemsMaterialListFor_Roads.getNthMaterial(4,
				Material.GRASS_PATH);

		initializeDice(platmap, chunkX, chunkZ);

		// precalc the Ys
		blockYs = platmap.generator.shapeProvider.getCachedYs(platmap.generator, chunkX, chunkZ);
	}

	protected abstract long getConnectedKey();

	public abstract boolean makeConnected(PlatLot relative);

	public abstract boolean isConnectable(PlatLot relative);

	public abstract boolean isConnected(PlatLot relative);

	public abstract PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ);

	protected abstract void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ);

	protected abstract void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ);

	/** Highest terrain block in this lot (from the planned column heights) — used to spot peak lots. */
	public int getMaxTerrainY() {
		return blockYs.getMaxHeight();
	}

	public int getChunkX() {
		return chunkX;
	}

	public int getChunkZ() {
		return chunkZ;
	}

	public Biome getChunkBiome() {
		return Biome.PLAINS;
	}

	/**
	 * This lot's shop classification, or {@code null} if it is not a shop. Set seed-deterministically
	 * at plan time by shop-bearing lots (see {@code StoreBuildingLot}); read by {@code /cityinfo} and
	 * the public {@link me.daddychurchill.CityWorld.api.CityWorldShops} lookup. Default: not a shop.
	 */
	public me.daddychurchill.CityWorld.api.ShopType getShopType() {
		return null;
	}

	public boolean isPlaceableAt(CityWorldGenerator generator, int chunkX, int chunkZ) {
		return generator.getSettings().inCityRange(chunkX, chunkZ);
	}

	/**
	 * MODERN only: may vanilla wild decoration (biome trees/grass/flowers) run over this lot? True for
	 * genuine wilderness (the default). Earthworks that carve or reshape the ground during the
	 * decoration pass override this to false — the vanilla heightmap is computed before they dig, so
	 * letting vanilla decorate leaves grass and flowers floating over pits and platforms.
	 */
	public boolean allowsWildDecoration() {
		return true;
	}

	public PlatLot validateLot(PlatMap platmap, int platX, int platZ) {
		return null; // assume that we don't do anything
	}

	public RoadLot repaveLot(CityWorldGenerator generator, PlatMap platmap) {
		return null; // same here
	}

	private void initializeDice(PlatMap platmap, int chunkX, int chunkZ) {

		// reset and pick up the dice
//		platmapOdds = platmap.getOddsGenerator();
		chunkOdds = platmap.getChunkOddsGenerator(chunkX, chunkZ);
	}

	protected int getSidewalkLevel(CityWorldGenerator generator) {
		int result = generator.streetLevel;
		if (inACity)
			return result + 1;
		else
			return result;
	}

	protected Material getSidewalkMaterial() {
		if (inACity)
			return pavementSidewalk;
		else
			return dirtroadSidewalk;
	}

	protected int getBlockY(int x, int z) {
		return blockYs == null ? 0 : blockYs.getBlockY(x, z);
	}

	//	public double getAverageY() {
//		return blockYs == null ? 0 : blockYs.averageHeight;
//	}
//	
//	protected double getPerciseY(int x, int z) {
//		return blockYs == null ? 0 : blockYs.getPerciseY(x, z);
//	}
//	
	protected int getSurfaceAtY(int x, int z) {
		return getSurfaceAtY(x, 15 - x, z, 15 - z);
	}

	private int getSurfaceAtY(int x1, int x2, int z1, int z2) {
		int surfaceY = Math.min(getBlockY(x1, z1), getBlockY(x2, z1));
		surfaceY = Math.min(surfaceY, getBlockY(x1, z2));
		surfaceY = Math.min(surfaceY, getBlockY(x2, z2));
		return surfaceY;
	}

	public abstract int getBottomY(CityWorldGenerator generator);

	public abstract int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z);

	// TODO: It seems that Spigot is generating the real blocks twice
	// (generateBlocks) for each time the blocks are initialized (generateChunk)
//	private static int totalNumberOfLotsOverGenerated = 0;
//	private static int totalNumberOfGeneratedChunks = 0;
	private int generateBlocksCallCountForThisLot = 0;

	protected void flattenLot(CityWorldGenerator generator, AbstractBlocks chunk, int maxLayersToDo) {
		if (blockYs.getMaxHeight() > generator.streetLevel && blockYs.getMaxHeight() <= generator.streetLevel + maxLayersToDo) {
			chunk.airoutLayer(generator, generator.streetLevel + 1,
					Math.min(blockYs.getMaxHeight() - generator.streetLevel + 1, maxLayersToDo));
		}
	}

	public void generateChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk, BiomeGrid biomes,
			DataContext context, int platX, int platZ) {
//		if (chunk.sectionX != chunkX || chunk.sectionZ != chunkZ)
//			generator.reportFormatted("!!!!!2! Wrong chunk [%d, %d] for Platlot [%d, %d]", chunk.sectionX, chunk.sectionZ, chunkX, chunkZ);
//		
//		burp(generator, 1, false);
		initializeDice(platmap, chunk.sectionX, chunk.sectionZ);

		// what do we need to first?
//		burp(generator, 2, false);
		generator.shapeProvider.preGenerateChunk(generator, this, chunk, biomes, blockYs);

		// let the specialized platlot do it's thing
//		burp(generator, 3, false);
		generateActualChunk(generator, platmap, chunk, biomes, context, platX, platZ);
		generateBlocksCallCountForThisLot = 0;
//		totalNumberOfGeneratedChunks++;

		// polish things off
//		burp(generator, 4, false);
		generator.shapeProvider.postGenerateChunk(generator, this, chunk, blockYs);
//		burp(generator, 5, false);
	}

	public void generateBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk, DataContext context,
			int platX, int platZ) {

		// TODO: This code makes sure that there is a single generateBlocks for each
		// generateChunk... and occasionally reports how often the problem occurred.
		generateBlocksCallCountForThisLot++;
		if (generateBlocksCallCountForThisLot > 1) {
//			totalNumberOfLotsOverGenerated++;
//			if (totalNumberOfLotsOverGenerated % 100 == 0)
//				generator.reportMessage(String.format("OVERGEN: At least %3.1f percentage of the lots have been over generated", 
//						((double)totalNumberOfLotsOverGenerated / (double)totalNumberOfGeneratedChunks) * 100));
			return;
		}

		initializeDice(platmap, chunk.sectionX, chunk.sectionZ);

		// what do we need to first?
		generator.shapeProvider.preGenerateBlocks(generator, this, chunk, blockYs);

		// let the specialized platlot do it's thing
		generateActualBlocks(generator, platmap, chunk, context, platX, platZ);

		// MODERN: give the exposed natural ground of every lot its biome-signature block (sand deserts,
		// banded badlands, mycelium isles, snowy plains) — so a house yard or farm edge in a desert reads
		// sandy instead of a green island. Guarded to grass/dirt, so it never touches builds, farmland,
		// roads or swamp pools. Roads opt out (they keep their own snow blend).
		if (generator.isModernStyle() && wantsBiomeGround())
			applyBiomeGround(generator, chunk);

		// Overgrowth: let nature reclaim built things — moss, vines, leaf litter, small trees. Runs
		// here, AFTER the lot's own decoration and any decay above, so the greenery isn't itself
		// decayed. Buildings/roads/roundabouts/schematics only (nature lots grow their own cover).
		if (generator.getSettings().includeOvergrowth
				&& (style == LotStyle.STRUCTURE || style == LotStyle.ROAD || style == LotStyle.ROUNDABOUT))
			me.daddychurchill.CityWorld.Support.Overgrowth.apply(generator, this, chunk, chunkOdds);

		// Apocalypse: hide zombie spawners in the ruins' cellars — some buried in a sealed pocket UNDER
		// the basement floor so they can't be seen. Building lots only (they have the cellars); sewers and
		// caves get their own zombie spawners through the enabled spawner bags.
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.APOCALYPSE && style == LotStyle.STRUCTURE)
			me.daddychurchill.CityWorld.Support.ApocalypseSpawners.apply(generator, this, chunk, chunkOdds);

		// MODERN: crown the tall buildings with a rooftop lightning rod (highrises only — it self-limits
		// to roofs well above street, the skyline that actually takes the strikes).
		if (generator.isModernStyle() && style == LotStyle.STRUCTURE)
			me.daddychurchill.CityWorld.Support.Furniture.rooftopLightningRod(generator, chunk, chunkOdds);

		// Shops: a classified shop gets its trade's villager job block dropped on the ground floor, so a
		// store reads as its trade. MODERN dressing (gated); runs after the interior is drawn so the
		// counter lands in open floor. getShopType() is only non-null for shop lots.
		if (generator.getSettings().includeShops && getShopType() != null)
			me.daddychurchill.CityWorld.Support.ShopFitter.apply(generator, this, chunk, chunkOdds);

		// polish things off
		generator.shapeProvider.postGenerateBlocks(generator, this, chunk, blockYs);
	}

	/** MODERN: whether the base biome-ground pass runs on this lot's exposed grass. True by default. */
	protected boolean wantsBiomeGround() {
		return true;
	}

	/** MODERN: whether the biome-ground pass also lays snow on cold-biome grass. True by default; roads
	 *  return false so they still get biome soil (sand/badlands/etc.) but keep their own graded
	 *  tunnel-roof snow (the traceable line) instead of a full blanket. */
	protected boolean biomeGroundSnows() {
		return true;
	}

	/** MODERN biome-aware ground: swap the exposed natural grass/dirt of this lot's columns to the
	 *  assigned biome's signature block (and snow the cold ones), clearing any surface vegetation first
	 *  so nothing floats. Only touches grass/dirt near the planned surface, below the icecap line. */
	protected void applyBiomeGround(CityWorldGenerator generator, RealBlocks chunk) {
		int iceLine = generator.snowLevel - 5; // the icecap pass owns columns at/above this
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int top = getBlockY(x, z);
				if (top < generator.seaLevel || top >= iceLine)
					continue;
				if (!chunk.isOfTypes(x, top, z, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
						Material.PODZOL))
					continue; // only natural grassy ground — never a build, farmland, road or swamp pool
				var biome = chunk.getBiomeKey(x, top, z);
				if (biome == null)
					continue;

				// Badlands: red sand cap over striped terracotta bands (elevation-indexed, gently
				// wandering) instead of the flat sand/terracotta a plain swap would give.
				if (me.daddychurchill.CityWorld.Support.BiomeSurface.isBadlands(biome)) {
					clearVegetation(chunk, x, top + 1, z);
					clearVegetation(chunk, x, top + 2, z);
					layBadlands(generator, chunk, x, top, z);
					continue;
				}

				Material surf = me.daddychurchill.CityWorld.Support.BiomeSurface.surface(biome);
				boolean doSnow = me.daddychurchill.CityWorld.Support.BiomeSurface.snowy(biome) && biomeGroundSnows();
				if (surf == null && !doSnow)
					continue; // grass biome, or a road that owns its own snow — leave it

				clearVegetation(chunk, x, top + 1, z);
				clearVegetation(chunk, x, top + 2, z);

				if (surf != null) {
					chunk.setBlock(x, top, z, surf);
					Material sub = me.daddychurchill.CityWorld.Support.BiomeSurface.subsurface(biome);
					if (sub != null)
						chunk.setBlocks(x, top - 3, top, z, sub);
				}
				// Only snow onto genuinely open ground. getBlockY here is the *natural* terrain height, so
				// on a lot that builds up from the ground (a schematic, a house on natural terrain) top+1 is
				// the build's floor, not open air — snowing it would replace the floor. Skip if it's solid.
				if (doSnow && chunk.isEmpty(x, top + 1, z))
					chunk.setBlock(x, top + 1, z, Material.SNOW, chunkOdds.getRandomInt(1, 2));
			}
	}

	/** Lay a badlands column: a red-sand cap, then striped terracotta bands down the exposed face. Colour
	 *  comes from the generator's seeded, elevation-indexed band table so stripes line up across columns
	 *  and cliff faces. */
	private void layBadlands(CityWorldGenerator generator, RealBlocks chunk, int x, int top, int z) {
		int wx = chunkX * SupportBlocks.sectionBlockWidth + x;
		int wz = chunkZ * SupportBlocks.sectionBlockWidth + z;
		// The surface itself carries the band colour (so a smooth slope shows its stripe as it rises,
		// rather than a red-sand cap hiding everything). A red-sand skim only lands on the near-flat tops
		// via a surface roll, the way vanilla mesas keep red sand up top but bare terracotta on the faces.
		chunk.setBlock(x, top, z, generator.badlandsSurfaceAt(wx, top, wz));
		int floor = top - 24; // band the top ~24 blocks (the visible face); stone below
		for (int y = top - 1; y >= floor; y--)
			chunk.setBlock(x, y, z, generator.badlandsBandAt(wx, y, wz));
	}

	/** Clear a bit of surface vegetation (grass/ferns/flowers/tall plants) — anything non-air that
	 *  doesn't block motion — leaving solid blocks be. */
	protected void clearVegetation(RealBlocks chunk, int x, int y, int z) {
		if (!chunk.isEmpty(x, y, z) && !chunk.getActualBlock(x, y, z).getBlockData().blocksMotion())
			chunk.setBlock(x, y, z, Material.AIR);
	}

	protected void destroyLot(CityWorldGenerator generator, int y1, int y2) {
		int x1 = chunkX * SupportBlocks.sectionBlockWidth;
		int z1 = chunkZ * SupportBlocks.sectionBlockWidth;
		generator.destroyWithin(x1, x1 + SupportBlocks.sectionBlockWidth, y1, y2, z1,
				z1 + SupportBlocks.sectionBlockWidth);
	}

	protected void destroyBuilding(CityWorldGenerator generator, int y, int floors) {
		destroyLot(generator, y, y + DataContext.FloorHeight * (floors + 1));
	}

	// Mines now run deep — down into the deepslate — so there's room for a real depth gradient (coal/iron
	// up top, diamond and the odd scrap of ancient debris at the bottom, the reason they dug so far).
	private final static int lowestMineSegment = -48;

	public void generateMines(CityWorldGenerator generator, InitialBlocks chunk) {

		// get shafted! (this builds down to keep the support poles happy)
		if (generator.getSettings().includeMines)
			for (int y = (blockYs.getMinHeight() / 16 - 1) * 16; y >= lowestMineSegment; y -= 16) {
				if (isShaftableLevel(generator, y))
					generateHorizontalMineLevel(generator, chunk, y);
			}
	}

	protected int findHighestShaftableLevel(CityWorldGenerator generator, DataContext context, SupportBlocks chunk) {

		// keep going down until we find what we are looking for
		for (int y = (blockYs.getMinHeight() / 16 - 1) * 16; y >= lowestMineSegment; y -= 16) {
			if (isShaftableLevel(generator, y)
					&& generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ))
				return y + 7;
		}

		// nothing found
		return 0;
	}

	protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
		return blockY >= lowestMineSegment && blockY < blockYs.getMinHeight() && blockYs.getMinHeight() > generator.seaLevel;
	}

	private void generateHorizontalMineLevel(CityWorldGenerator generator, InitialBlocks chunk, int y) {
		int y1 = y + 6;
		int y2 = y1 + 1;

		// draw the shafts/walkways
		boolean pathFound = false;
		if (generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ)) {
			generateMineShaftSpace(generator, chunk, 6, 10, y1, y1 + 4, 0, 6);
			generateMineNSSupport(chunk, 6, y2, 1);
			generateMineNSSupport(chunk, 6, y2, 4);
			generateMineShaftSpace(generator, chunk, 6, 10, y1, y1 + 4, 10, 16);
			generateMineNSSupport(chunk, 6, y2, 11);
			generateMineNSSupport(chunk, 6, y2, 14);
			pathFound = true;
		}
		if (generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ)) {
			generateMineShaftSpace(generator, chunk, 0, 6, y1, y1 + 4, 6, 10);
			generateMineWESupport(chunk, 1, y2, 6);
			generateMineWESupport(chunk, 4, y2, 6);
			generateMineShaftSpace(generator, chunk, 10, 16, y1, y1 + 4, 6, 10);
			generateMineWESupport(chunk, 11, y2, 6);
			generateMineWESupport(chunk, 14, y2, 6);
			pathFound = true;
		}

		// draw the center bit
		if (pathFound)
			generateMineShaftSpace(generator, chunk, 6, 10, y1, y1 + 4, 6, 10);
	}

	private final static Material shaftBridge = Material.OAK_PLANKS;
	private final static Material shaftSupport = Material.OAK_FENCE;
	private final static Material shaftBeam = Material.OAK_PLANKS;

	private void generateMineShaftSpace(CityWorldGenerator generator, InitialBlocks chunk, int x1, int x2, int y1,
			int y2, int z1, int z2) {
		chunk.setEmptyBlocks(x1, x2, y1, z1, z2, shaftBridge);
		chunk.airoutBlocks(generator, x1, x2, y1 + 1, y2, z1, z2);
	}

	private void generateMineNSSupport(InitialBlocks chunk, int x, int y, int z) {

		// on a bridge
		if (chunk.isType(x, y - 1, z, shaftBridge) && chunk.isType(x + 3, y - 1, z, shaftBridge)) {

			// place supports
			generateMineSupport(chunk, x, y - 1, z);
			generateMineSupport(chunk, x + 3, y - 1, z);

			// in a tunnel
		} else {
			chunk.setBlock(x, y, z, shaftSupport);
			chunk.setBlock(x, y + 1, z, shaftSupport);
			chunk.setBlock(x + 3, y, z, shaftSupport);
			chunk.setBlock(x + 3, y + 1, z, shaftSupport);
			chunk.setBlocks(x, x + 4, y + 2, z, z + 1, shaftBeam);
		}
	}

	private void generateMineWESupport(InitialBlocks chunk, int x, int y, int z) {
		// on a bridge
		if (chunk.isType(x, y - 1, z, shaftBridge) && chunk.isType(x, y - 1, z + 3, shaftBridge)) {

			// place supports
			generateMineSupport(chunk, x, y - 1, z);
			generateMineSupport(chunk, x, y - 1, z + 3);

			// in a tunnel
		} else {
			chunk.setBlock(x, y, z, shaftSupport);
			chunk.setBlock(x, y + 1, z, shaftSupport);
			chunk.setBlock(x, y, z + 3, shaftSupport);
			chunk.setBlock(x, y + 1, z + 3, shaftSupport);
			chunk.setBlocks(x, x + 1, y + 2, z, z + 4, shaftBeam);
		}
	}

	private void generateMineSupport(InitialBlocks chunk, int x, int y, int z) {
		int aboveSupport = chunk.findLastEmptyAbove(x, y, z, blockYs.getMaxHeight());
		if (aboveSupport < blockYs.getMaxHeight())
			chunk.setBlocks(x, y + 1, aboveSupport + 1, z, shaftSupport);
	}

	public void generateMines(CityWorldGenerator generator, SupportBlocks chunk) {

		// get shafted!
		if (generator.getSettings().includeMines)
			for (int y = lowestMineSegment; y + 16 < blockYs.getMinHeight(); y += 16) {
				if (isShaftableLevel(generator, y))
					generateVerticalMineLevel(generator, chunk, y);
			}
	}

	private void generateVerticalMineLevel(CityWorldGenerator generator, SupportBlocks chunk, int y) {
		int y1 = y + 6;
		boolean stairsFound = false;

		// going down?
		if (isShaftableLevel(generator, y - 16)) {
			if (generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ)
					&& generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y - 16, chunk.sectionZ)) {

				// draw the going down bit
				placeMineStairBase(chunk, 10, y1, 15);
				placeMineStairStep(chunk, 10, y1, 14, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 1, 13, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 2, 12, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 3, 11, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 4, 10, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 5, 9, BlockFace.SOUTH, BlockFace.NORTH);
				placeMineStairStep(chunk, 10, y1 - 6, 8, BlockFace.SOUTH, BlockFace.NORTH);
				stairsFound = true;
			}

			if (!stairsFound && generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ)
					&& generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y - 16, chunk.sectionZ)) {

				// draw the going down bit
				placeMineStairBase(chunk, 15, y1, 10);
				placeMineStairStep(chunk, 14, y1, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 13, y1 - 1, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 12, y1 - 2, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 11, y1 - 3, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 10, y1 - 4, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 9, y1 - 5, 10, BlockFace.EAST, BlockFace.WEST);
				placeMineStairStep(chunk, 8, y1 - 6, 10, BlockFace.EAST, BlockFace.WEST);
			}
		}

		// reset the stairs flag
		stairsFound = false;

		// going up?
		if (isShaftableLevel(generator, y + 32)) {
			if (generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ)
					&& generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y + 16, chunk.sectionZ)) {

				// draw the going up bit
				placeMineStairBase(chunk, 5, y1, 15);
				placeMineStairStep(chunk, 5, y1 + 1, 14, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 2, 13, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 3, 12, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 4, 11, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 5, 10, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 6, 9, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 7, 8, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairStep(chunk, 5, y1 + 8, 7, BlockFace.NORTH, BlockFace.SOUTH);
				placeMineStairBase(chunk, 5, y1 + 8, 6);
				placeMineStairBase(chunk, 6, y1 + 8, 6);
				placeMineStairBase(chunk, 7, y1 + 8, 6);
				placeMineStairBase(chunk, 8, y1 + 8, 6);
				placeMineStairBase(chunk, 9, y1 + 8, 6);
				placeMineStairBase(chunk, 10, y1 + 8, 6);
				placeMineStairStep(chunk, 10, y1 + 9, 7, BlockFace.SOUTH, BlockFace.NORTH);

				generateMineSupport(chunk, 6, y1 + 7, 7);
				generateMineSupport(chunk, 9, y1 + 7, 7);

				stairsFound = true;
			}

			if (!stairsFound && generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ)
					&& generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y + 16, chunk.sectionZ)) {

				// draw the going up bit
				placeMineStairBase(chunk, 15, y1, 5);
				placeMineStairStep(chunk, 14, y1 + 1, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 13, y1 + 2, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 12, y1 + 3, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 11, y1 + 4, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 10, y1 + 5, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 9, y1 + 6, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 8, y1 + 7, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairStep(chunk, 7, y1 + 8, 5, BlockFace.WEST, BlockFace.EAST);
				placeMineStairBase(chunk, 6, y1 + 8, 5);
				placeMineStairBase(chunk, 6, y1 + 8, 6);
				placeMineStairBase(chunk, 6, y1 + 8, 7);
				placeMineStairBase(chunk, 6, y1 + 8, 8);
				placeMineStairBase(chunk, 6, y1 + 8, 9);
				placeMineStairBase(chunk, 6, y1 + 8, 10);
				placeMineStairStep(chunk, 7, y1 + 9, 10, BlockFace.EAST, BlockFace.WEST);

				generateMineSupport(chunk, 7, y1 + 7, 6);
				generateMineSupport(chunk, 7, y1 + 7, 9);
			}
		}

		// make the ceiling pretty
		boolean pathFound = false;
		if (generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ)) {
			generateMineCeiling(chunk, 6, 10, y1 + 3, 0, 6);
			generateMineCeiling(chunk, 6, 10, y1 + 3, 10, 16);

			generateMineAlcove(generator, chunk, 4, y1, 2, 4, 2);
			generateMineAlcove(generator, chunk, 10, y1, 2, 11, 3);

			pathFound = true;
		}
		if (generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ)) {
			generateMineCeiling(chunk, 0, 6, y1 + 3, 6, 10);
			generateMineCeiling(chunk, 10, 16, y1 + 3, 6, 10);

			generateMineAlcove(generator, chunk, 2, y1, 4, 2, 4);
			generateMineAlcove(generator, chunk, 2, y1, 10, 3, 11);

			pathFound = true;
		}

		// draw the center bit
		if (pathFound)
			generateMineCeiling(chunk, 6, 10, y1 + 3, 6, 10);

		// vanilla-style dressing: minecart rails down the corridors, cobwebs and the odd torch
		dressMineCorridors(generator, chunk, y);

		// the occasional copper lift shaft at a crossing (placed last so its frame wins any overlap)
		generateMineLift(generator, chunk, y);
	}

	// Dress the carved corridors like a vanilla mineshaft: a rail line down the centre (with occasional
	// gaps for old broken track), cobwebs strung about, and a torch or two.
	private void dressMineCorridors(CityWorldGenerator generator, SupportBlocks chunk, int y) {
		int floorY = y + 6;
		int railY = floorY + 1;
		boolean ns = generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ);
		boolean we = generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ);
		if (!ns && !we)
			return;

		// rails down the centre, wall torches on the corridor walls at intervals
		int torchY = railY + 1; // up the wall a little
		if (ns)
			for (int z = 0; z < 16; z++) {
				layMineRail(chunk, 8, floorY, railY, z, RailShape.NORTH_SOUTH);
				if (z % 5 == 2) {
					// mount on whichever side wall is solid: west (x5) facing east, else east (x10) facing west
					if (!chunk.isEmpty(5, torchY, z) && chunk.isEmpty(6, torchY, z))
						chunk.setBlock(6, torchY, z, Material.COPPER_WALL_TORCH, BlockFace.EAST);
					else if (!chunk.isEmpty(10, torchY, z) && chunk.isEmpty(9, torchY, z))
						chunk.setBlock(9, torchY, z, Material.COPPER_WALL_TORCH, BlockFace.WEST);
				}
			}
		if (we)
			for (int x = 0; x < 16; x++) {
				layMineRail(chunk, x, floorY, railY, 8, RailShape.EAST_WEST);
				if (x % 5 == 2) {
					if (!chunk.isEmpty(x, torchY, 5) && chunk.isEmpty(x, torchY, 6))
						chunk.setBlock(x, torchY, 6, Material.COPPER_WALL_TORCH, BlockFace.SOUTH);
					else if (!chunk.isEmpty(x, torchY, 10) && chunk.isEmpty(x, torchY, 9))
						chunk.setBlock(x, torchY, 9, Material.COPPER_WALL_TORCH, BlockFace.NORTH);
				}
			}

		// cobwebs strung through the corridor volume — many more the deeper (older/more abandoned) it is
		int webCount = 6 + Math.max(0, (24 - floorY) / 5);
		for (int i = 0; i < webCount; i++) {
			int wx = chunkOdds.getRandomInt(1, 14);
			int wz = chunkOdds.getRandomInt(1, 14);
			int wy = railY + chunkOdds.getRandomInt(0, 2);
			if (chunk.isEmpty(wx, wy, wz) && chunkOdds.playOdds(Odds.oddsSomewhatLikely))
				chunk.setBlock(wx, wy, wz, Material.COBWEB);
		}

		// overgrown worlds: dripstone reclaiming the abandoned shaft (stalactites/stalagmites/clumps)
		if (generator.getSettings().includeOvergrowth)
			me.daddychurchill.CityWorld.Support.Overgrowth.dripstoneMine(chunk, floorY, chunkOdds);

		// copper-age shoring: cut-copper support frames with a dangling chain cable, patinated by depth
		dressCopperSupports(chunk, floorY, ns, we);

		// abandoned decay + creeping glow lichen (both intensify with depth), and recolour the vanilla
		// oak-fence supports to copper so nothing wooden clashes with the copper frames
		weatherAndLichen(chunk, floorY, ns, we);

		// the odd cave-spider nest right in the corridor — more the deeper you go
		maybeCaveSpiderNest(generator, chunk, floorY, railY, ns, we);

		// miners' camp clutter — furnaces, stonecutters and the like on the ledge opposite the track
		scatterMineProps(generator, chunk, floorY, ns, we);

		// lanterns hung from the corridor ceiling so the drift isn't pitch black
		scatterMineLanterns(chunk, floorY, ns, we);

		// ore veins exposed in the walls (depth-graded), gravel fall-in hazards, the odd cave-in rubble
		veinAndHazard(generator, chunk, floorY, ns, we);
	}

	// Copper-age shoring: at intervals, frame the corridor with cut-copper posts embedded in the side
	// walls, a lintel across the ceiling with a copper grate set into it (an old ventilation/hoist port),
	// and a copper-chain cable dangling from it — the deeper the shaft, the more oxidised it all is.
	private void dressCopperSupports(SupportBlocks chunk, int floorY, boolean ns, boolean we) {
		int ceilY = floorY + 3; // the carved corridor ceiling sits at floorY+3 (floorY+1/+2 are the air gap)
		Material beam = copperCut(floorY);
		Material grate = copperGrate(floorY);
		Material chain = copperChain(floorY);
		if (ns)
			for (int z = 2; z < 16; z += 6)
				buildCopperFrame(chunk, floorY, ceilY, beam, grate, chain, 5, 10, z, z, true);
		if (we)
			for (int x = 2; x < 16; x += 6)
				buildCopperFrame(chunk, floorY, ceilY, beam, grate, chain, x, x, 5, 10, false);
	}

	// One support frame spanning a corridor. (loX,loZ)/(hiX,hiZ) are the two side-wall columns; the
	// lintel and hanging cable run between them. Only ever recolours blocks that are already solid so a
	// frame at a junction/opening doesn't wall the corridor off.
	private void buildCopperFrame(SupportBlocks chunk, int floorY, int ceilY, Material beam, Material grate,
			Material chain, int loX, int hiX, int loZ, int hiZ, boolean ns) {
		// posts up both side walls
		for (int level = floorY; level < ceilY; level++) {
			if (!chunk.isEmpty(loX, level, loZ))
				chunk.setBlock(loX, level, loZ, beam);
			if (!chunk.isEmpty(hiX, level, hiZ))
				chunk.setBlock(hiX, level, hiZ, beam);
		}
		// solid cut-copper lintel across the ceiling, with a copper-grate vent panel spanning the full
		// corridor width (the 6..9 opening; walls at loX/hiX stay cut copper) and a chain hung beneath it
		if (ns) {
			for (int x = loX; x <= hiX; x++)
				if (!chunk.isEmpty(x, ceilY, loZ))
					chunk.setBlock(x, ceilY, loZ, x >= 6 && x <= 9 ? grate : beam);
			if (chunk.isEmpty(8, ceilY - 1, loZ))
				chunk.setBlock(8, ceilY - 1, loZ, chain);
		} else {
			for (int z = loZ; z <= hiZ; z++)
				if (!chunk.isEmpty(loX, ceilY, z))
					chunk.setBlock(loX, ceilY, z, z >= 6 && z <= 9 ? grate : beam);
			if (chunk.isEmpty(loX, ceilY - 1, 8))
				chunk.setBlock(loX, ceilY - 1, 8, chain);
		}
	}

	// Copper patinas the deeper (older) the shaft: 0 fresh near the surface .. 3 fully oxidised at the
	// bottom. floorY runs from about +60 down to -42, so the bands are spaced across that range.
	private int copperWeatherStage(int floorY) {
		if (floorY >= 24)
			return 0;
		if (floorY >= 4)
			return 1;
		if (floorY >= -20)
			return 2;
		return 3;
	}

	private Material copperCut(int floorY) {
		switch (copperWeatherStage(floorY)) {
		case 0:
			return Material.CUT_COPPER;
		case 1:
			return Material.EXPOSED_CUT_COPPER;
		case 2:
			return Material.WEATHERED_CUT_COPPER;
		default:
			return Material.OXIDIZED_CUT_COPPER;
		}
	}

	private Material copperBars(int floorY) {
		switch (copperWeatherStage(floorY)) {
		case 0:
			return Material.COPPER_BARS;
		case 1:
			return Material.EXPOSED_COPPER_BARS;
		case 2:
			return Material.WEATHERED_COPPER_BARS;
		default:
			return Material.OXIDIZED_COPPER_BARS;
		}
	}

	private Material copperGrate(int floorY) {
		switch (copperWeatherStage(floorY)) {
		case 0:
			return Material.COPPER_GRATE;
		case 1:
			return Material.EXPOSED_COPPER_GRATE;
		case 2:
			return Material.WEATHERED_COPPER_GRATE;
		default:
			return Material.OXIDIZED_COPPER_GRATE;
		}
	}

	private Material copperChest(int floorY) {
		switch (copperWeatherStage(floorY)) {
		case 0:
			return Material.COPPER_CHEST;
		case 1:
			return Material.EXPOSED_COPPER_CHEST;
		case 2:
			return Material.WEATHERED_COPPER_CHEST;
		default:
			return Material.OXIDIZED_COPPER_CHEST;
		}
	}

	// Recolour the vanilla oak-fence supports to (weathered) copper bars so nothing wooden clashes with
	// the copper frames, then creep glow lichen over the walls and ceiling and moss up the cobble — all
	// heavier the deeper (older, more abandoned) the shaft.
	private void weatherAndLichen(SupportBlocks chunk, int floorY, boolean ns, boolean we) {
		Material bars = copperBars(floorY);
		int stage = copperWeatherStage(floorY); // 0 shallow .. 3 deepest

		// oak-fence supports -> copper bars, anywhere in this corridor slice
		for (int x = 1; x < 15; x++)
			for (int z = 1; z < 15; z++)
				for (int yy = floorY; yy <= floorY + 3; yy++)
					if (chunk.isType(x, yy, z, Material.OAK_FENCE))
						chunk.setBlock(x, yy, z, bars);

		// glow lichen creeping the corridor walls + ceiling; more patches the deeper it is
		int patches = 4 + stage * 5;
		for (int i = 0; i < patches; i++) {
			int wy = floorY + 1 + chunkOdds.getRandomInt(0, 1); // the air gap
			if (ns) {
				int z = chunkOdds.getRandomInt(1, 14);
				creepLichen(chunk, 6, wy, z, BlockFace.WEST); // onto the x5 wall
				creepLichen(chunk, 9, wy, z, BlockFace.EAST); // onto the x10 wall
			}
			if (we) {
				int x = chunkOdds.getRandomInt(1, 14);
				creepLichen(chunk, x, wy, 6, BlockFace.NORTH); // onto the z5 wall
				creepLichen(chunk, x, wy, 9, BlockFace.SOUTH); // onto the z10 wall
			}
			// the odd patch clinging to the ceiling
			int cx = chunkOdds.getRandomInt(6, 9), cz = chunkOdds.getRandomInt(6, 9);
			creepLichen(chunk, cx, floorY + 2, cz, BlockFace.UP);
		}

		// mossy decay in the cobble ceiling, spreading with depth
		if (stage > 0)
			for (int i = 0; i < stage * 3; i++) {
				int mx = chunkOdds.getRandomInt(1, 14), mz = chunkOdds.getRandomInt(1, 14);
				if (chunk.isType(mx, floorY + 3, mz, Material.COBBLESTONE))
					chunk.setBlock(mx, floorY + 3, mz, Material.MOSSY_COBBLESTONE);
			}
	}

	// Place a glow-lichen patch in an air cell, attached to the solid neighbour on the given face.
	private void creepLichen(SupportBlocks chunk, int x, int y, int z, BlockFace attachTo) {
		if (!chunk.isEmpty(x, y, z))
			return;
		if (chunk.isEmpty(x + attachTo.getModX(), y + attachTo.getModY(), z + attachTo.getModZ()))
			return; // nothing solid to cling to
		if (chunkOdds.playOdds(Odds.oddsLikely))
			chunk.setBlock(x, y, z, Material.GLOW_LICHEN, new BlockFace[] { attachTo });
	}

	// A proper cave-spider nest strung right across the corridor: a cave-spider spawner on the centreline
	// with a dense web tangle 2-3 blocks deep around it. Rare up top, common in the deep levels.
	private void maybeCaveSpiderNest(CityWorldGenerator generator, SupportBlocks chunk, int floorY, int railY,
			boolean ns, boolean we) {
		if (!generator.getSettings().spawnersInMines)
			return;
		double nestOdds;
		switch (copperWeatherStage(floorY)) { // 0 shallow .. 3 deepest
		case 0:
			nestOdds = Odds.oddsVeryUnlikely;
			break;
		case 1:
			nestOdds = Odds.oddsUnlikely;
			break;
		case 2:
			nestOdds = Odds.oddsSomewhatLikely;
			break;
		default:
			nestOdds = Odds.oddsLikely;
			break;
		}
		if (!chunkOdds.playOdds(nestOdds))
			return;

		// on the corridor centreline, back from the chunk edges
		int sx, sy = railY, sz;
		if (ns) {
			sx = 8;
			sz = chunkOdds.getRandomInt(4, 11);
		} else {
			sx = chunkOdds.getRandomInt(4, 11);
			sz = 8;
		}
		if (!chunk.isEmpty(sx, sy, sz))
			return;

		generator.spawnProvider.setSpawner(generator, chunk, chunkOdds, sx, sy, sz, EntityType.CAVE_SPIDER, true);

		// dense web core around the spawner, thinning to wisps 2-3 blocks out
		for (int dx = -2; dx <= 2; dx++)
			for (int dz = -2; dz <= 2; dz++)
				for (int dy = -1; dy <= 2; dy++)
					if (!(dx == 0 && dz == 0 && dy == 0)) {
						int wx = sx + dx, wy = sy + dy, wz = sz + dz;
						if (!chunk.isEmpty(wx, wy, wz))
							continue;
						int dist = Math.abs(dx) + Math.abs(dz) + Math.abs(dy);
						double webOdds = dist <= 1 ? Odds.oddsExtremelyLikely
								: dist <= 2 ? Odds.oddsVeryLikely
										: dist <= 3 ? Odds.oddsSomewhatLikely : Odds.oddsUnlikely;
						if (chunkOdds.playOdds(webOdds))
							chunk.setBlock(wx, wy, wz, Material.COBWEB);
					}
	}

	// Miners' camp clutter dropped along the corridor. A mix of workstations, as if the crew downed
	// tools and walked off — some fitting (furnace, stonecutter, smithing table, grindstone, anvil,
	// barrel), some just village workshop odds and ends.
	private final static Material[] mineProps = {
			// workstations
			Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER, Material.STONECUTTER, Material.SMITHING_TABLE,
			Material.GRINDSTONE, Material.ANVIL, Material.BARREL, Material.CRAFTING_TABLE, Material.CAULDRON,
			Material.CARTOGRAPHY_TABLE,
			// lighting (three types)
			Material.LANTERN, Material.SOUL_LANTERN, Material.COPPER_LANTERN,
			// camp
			Material.CHEST, Material.DECORATED_POT, Material.CAMPFIRE, Material.SOUL_CAMPFIRE, Material.BELL,
			// getting about — weighted up (scaffolding most, ladders a little), listed several times
			Material.SCAFFOLDING, Material.SCAFFOLDING, Material.SCAFFOLDING, Material.SCAFFOLDING,
			Material.LADDER, Material.LADDER };

	private final static double oddsOfMineProp = Odds.oddsVeryUnlikely;

	// Scatter the camp clutter along the ledge opposite the rail (x6 on a N/S run, z6 on a W/E run) so it
	// never sits on the track, each piece facing into the corridor.
	private void scatterMineProps(CityWorldGenerator generator, SupportBlocks chunk, int floorY, boolean ns,
			boolean we) {
		int propY = floorY + 1;
		if (ns)
			for (int z = 2; z < 15; z++)
				if (chunkOdds.playOdds(oddsOfMineProp))
					placeMineProp(generator, chunk, 6, propY, z, BlockFace.EAST);
		if (we)
			for (int x = 2; x < 15; x++)
				if (chunkOdds.playOdds(oddsOfMineProp))
					placeMineProp(generator, chunk, x, propY, 6, BlockFace.SOUTH);
	}

	private void placeMineProp(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			BlockFace facing) {
		if (!chunk.isEmpty(x, y, z) || chunk.isEmpty(x, y - 1, z))
			return; // want an empty spot standing on a solid floor
		Material prop = mineProps[chunkOdds.getRandomInt(mineProps.length)];
		if (prop.is(Material.CHEST)) {
			// a supply crate, actually worth opening
			chunk.setChest(generator, x, y, z, facing, chunkOdds, generator.lootProvider, LootLocation.MINE);
		} else if (prop.is(Material.LADDER) || prop.is(Material.SCAFFOLDING)) {
			// a climb up the wall — fill the corridor headroom
			for (int yy = y; yy <= y + 1; yy++)
				if (chunk.isEmpty(x, yy, z))
					chunk.setBlock(x, yy, z, prop, facing);
		} else {
			chunk.setBlock(x, y, z, prop, facing);
		}
	}

	// Lanterns hung from the corridor ceiling (at floorY+2, dangling under the floorY+3 rock) so the drift
	// reads as a worked, lit mine rather than a black hole — a soft chain of light along the ledge.
	private final static Material[] mineLanterns = { Material.LANTERN, Material.SOUL_LANTERN,
			Material.COPPER_LANTERN };
	private final static double oddsOfMineLantern = Odds.oddsUnlikely;

	private void scatterMineLanterns(SupportBlocks chunk, int floorY, boolean ns, boolean we) {
		int y = floorY + 2; // just under the carved ceiling
		if (ns)
			for (int z = 2; z < 15; z++)
				hangMineLantern(chunk, 6, y, z);
		if (we)
			for (int x = 2; x < 15; x++)
				hangMineLantern(chunk, x, y, 6);
	}

	private void hangMineLantern(SupportBlocks chunk, int x, int y, int z) {
		if (!chunkOdds.playOdds(oddsOfMineLantern))
			return;
		if (!chunk.isEmpty(x, y, z) || chunk.isEmpty(x, y + 1, z))
			return; // an empty cell with a solid ceiling above to hang from
		chunk.setHangingLantern(x, y, z, mineLanterns[chunkOdds.getRandomInt(mineLanterns.length)]);
	}

	private final static double oddsOfMineLift = Odds.oddsLikely;

	// A vertical lift shaft at a corridor crossing: four cut-copper corner posts frame a 5x5 mouth, a
	// copper-grate winch housing caps the ceiling, and the inner 3x3 is a hollow shaft you can drop
	// straight down — a chain cable runs the centre, and a 3x3 grate landing at the bottom catches the
	// fall onto the level below. The stairs still do the safe traversal; this is the shortcut for the
	// brave. Sited only at 4-way crossings so the frame never walls off a corridor.
	private void generateMineLift(CityWorldGenerator generator, SupportBlocks chunk, int y) {
		if (!isShaftableLevel(generator, y - 16))
			return; // need a level directly below to drop the cable into
		boolean ns = generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y, chunk.sectionZ);
		boolean we = generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y, chunk.sectionZ);
		if (!(ns && we))
			return;
		// the level below must have a corridor at the centre too, so the shaft bottoms into open corridor
		// rather than solid rock ((8,8) is corridor air for a shaft of either direction)
		if (!generator.shapeProvider.isHorizontalNSShaft(chunk.sectionX, y - 16, chunk.sectionZ)
				&& !generator.shapeProvider.isHorizontalWEShaft(chunk.sectionX, y - 16, chunk.sectionZ))
			return;
		if (!chunkOdds.playOdds(oddsOfMineLift))
			return;

		int y1 = y + 6; // this level's floor
		int lower = y1 - 16; // the level below's floor
		int ceil = y1 + 3; // this junction's ceiling
		Material post = copperCut(y1);
		Material chain = copperChain(y1);
		Material grate = copperGrate(y1);

		// four cut-copper corner posts framing a 5x5 mouth (corners at x6/x10, z6/z10)
		for (int level = y1; level <= ceil; level++) {
			chunk.setBlock(6, level, 6, post);
			chunk.setBlock(10, level, 6, post);
			chunk.setBlock(6, level, 10, post);
			chunk.setBlock(10, level, 10, post);
		}
		// copper-grate winch housing over the shaft, at the ceiling
		for (int gx = 7; gx <= 9; gx++)
			for (int gz = 7; gz <= 9; gz++)
				chunk.setBlock(gx, ceil, gz, grate);

		// hollow the inner 3x3 into an open fall shaft, from the junction headroom straight down to the
		// level below — clearing the floor and any rails/cobwebs the corridor pass laid across the crossing
		for (int level = lower + 1; level <= y1 + 2; level++)
			for (int vx = 7; vx <= 9; vx++)
				for (int vz = 7; vz <= 9; vz++)
					chunk.setBlock(vx, level, vz, Material.AIR);
		// a chain cable down the centre, a ladder up one corner (climb or fall, your choice), and a 3x3
		// grate landing at the bottom
		for (int level = lower + 1; level < ceil; level++)
			chunk.setBlock(8, level, 8, chain);
		for (int level = lower + 1; level <= y1; level++)
			chunk.setBlock(7, level, 7, Material.LADDER, BlockFace.SOUTH);
		for (int gx = 7; gx <= 9; gx++)
			for (int gz = 7; gz <= 9; gz++)
				chunk.setBlock(gx, lower, gz, grate);
	}

	private Material copperChain(int floorY) {
		switch (copperWeatherStage(floorY)) {
		case 0:
			return Material.COPPER_CHAIN;
		case 1:
			return Material.EXPOSED_COPPER_CHAIN;
		case 2:
			return Material.WEATHERED_COPPER_CHAIN;
		default:
			return Material.OXIDIZED_COPPER_CHAIN;
		}
	}

	private void layMineRail(SupportBlocks chunk, int x, int floorY, int railY, int z, RailShape shape) {
		if (!chunk.isEmpty(x, floorY, z) && chunk.isEmpty(x, railY, z)
				&& chunkOdds.playOdds(Odds.oddsExtremelyLikely)) // occasional gaps = old broken track
			chunk.setBlock(x, railY, z, Material.RAIL, shape, false);
	}

	private final static double oreVeinOdds = Odds.oddsSomewhatUnlikely;

	// Expose ore in the corridor walls (worth mining), sprinkle gravel in the ceiling (a fall-in hazard),
	// and drop the odd cave-in rubble pile on the floor — all depth-graded via oreForDepth.
	private void veinAndHazard(CityWorldGenerator generator, SupportBlocks chunk, int floorY, boolean ns, boolean we) {
		if (ns)
			for (int z = 0; z < 16; z++) {
				veinWall(chunk, 5, floorY, z);
				veinWall(chunk, 10, floorY, z);
			}
		if (we)
			for (int x = 0; x < 16; x++) {
				veinWall(chunk, x, floorY, 5);
				veinWall(chunk, x, floorY, 10);
			}

		int ceilY = floorY + 3; // the carved corridor ceiling
		int hazards = 5 + copperWeatherStage(floorY) * 4; // riskier the deeper you dig
		for (int i = 0; i < hazards; i++) {
			// suspended gravel in the ceiling — falls when disturbed
			int gx = chunkOdds.getRandomInt(1, 14), gz = chunkOdds.getRandomInt(1, 14);
			if (!chunk.isEmpty(gx, ceilY, gz) && chunk.isEmpty(gx, ceilY - 1, gz)
					&& chunkOdds.playOdds(Odds.oddsLikely))
				chunk.setBlock(gx, ceilY, gz, Material.GRAVEL);
			// cave-in rubble on the floor
			int rx = chunkOdds.getRandomInt(1, 14), rz = chunkOdds.getRandomInt(1, 14);
			if (chunk.isType(rx, floorY, rz, shaftBridge) && chunk.isEmpty(rx, floorY + 1, rz)
					&& chunkOdds.playOdds(Odds.oddsUnlikely))
				chunk.setBlock(rx, floorY + 1, rz, chunkOdds.flipCoin() ? Material.GRAVEL : Material.COBBLESTONE);
		}
	}

	private void veinWall(SupportBlocks chunk, int x, int floorY, int z) {
		for (int level = floorY; level <= floorY + 3; level++)
			if ((chunk.isType(x, level, z, Material.STONE) || chunk.isType(x, level, z, Material.DEEPSLATE))
					&& chunkOdds.playOdds(oreVeinOdds))
				chunk.setBlock(x, level, z, oreForDepth(level));
	}

	// Ore by depth: coal/iron/copper up top, gold/redstone/lapis/diamond as you go deep, and a rare scrap
	// of ancient debris right at the bottom — the reason they mined so far. Deepslate variants below y0.
	private Material oreForDepth(int y) {
		boolean deep = y < 0;
		if (y < -32 && chunkOdds.playOdds(Odds.oddsExtremelyUnlikely))
			return Material.ANCIENT_DEBRIS;
		double r = chunkOdds.getRandomDouble();
		if (deep) {
			if (r < 0.06)
				return Material.DEEPSLATE_DIAMOND_ORE;
			if (r < 0.14)
				return Material.DEEPSLATE_REDSTONE_ORE;
			if (r < 0.20)
				return Material.DEEPSLATE_LAPIS_ORE;
			if (r < 0.27)
				return Material.DEEPSLATE_GOLD_ORE;
			if (r < 0.30)
				return Material.DEEPSLATE_EMERALD_ORE;
			if (r < 0.55)
				return Material.DEEPSLATE_IRON_ORE;
			if (r < 0.74)
				return Material.DEEPSLATE_COPPER_ORE;
			return Material.DEEPSLATE_COAL_ORE;
		}
		if (r < 0.03)
			return Material.GOLD_ORE;
		if (r < 0.07)
			return Material.REDSTONE_ORE;
		if (r < 0.11)
			return Material.LAPIS_ORE;
		if (r < 0.45)
			return Material.IRON_ORE;
		if (r < 0.66)
			return Material.COPPER_ORE;
		return Material.COAL_ORE;
	}

	private void generateMineAlcove(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z, int prizeX,
			int prizeZ) {
		if (chunkOdds.playOdds(generator.getSettings().oddsOfAlcoveInMines)) {
			if (!chunk.isEmpty(x, y, z) && !chunk.isEmpty(x + 1, y, z) && !chunk.isEmpty(x, y, z + 1)
					&& !chunk.isEmpty(x + 1, y, z + 1)) {
				chunk.setBlocks(x, x + 2, y + 1, y + 4, z, z + 2, Material.AIR);
				generateMineCeiling(chunk, x, x + 2, y + 3, z, z + 2);
				if (chunkOdds.flipCoin())
					generateMineTrick(generator, chunk, prizeX, y + 1, prizeZ);
				else
					generateMineTreat(generator, chunk, prizeX, y + 1, prizeZ);
			}
		}
	}

	protected void generateMineCeiling(SupportBlocks chunk, int x1, int x2, int y, int z1, int z2) {
		for (int x = x1; x < x2; x++) {
			for (int z = z1; z < z2; z++) {
				if (!chunk.isEmpty(x, y + 1, z) && chunk.isEmpty(x, y, z))
					if (chunkOdds.flipCoin())
						chunk.setBlock(x, y, z, Material.COBBLESTONE_SLAB, SlabType.TOP);
					else
						chunk.setBlock(x, y, z, Material.COBBLESTONE);
			}
		}
	}

	protected void generateMineFloor(SupportBlocks chunk, int x1, int x2, int y, int z1, int z2) {
		for (int x = x1; x < x2; x++) {
			for (int z = z1; z < z2; z++) {
				if (!chunk.isEmpty(x, y, z))
					if (chunkOdds.flipCoin())
						chunk.setBlock(x, y, z, Material.COBBLESTONE_SLAB, SlabType.BOTTOM);
					else
						chunk.setBlock(x, y, z, Material.COBBLESTONE);
			}
		}
	}

	private void generateMineSupport(SupportBlocks chunk, int x, int y, int z) {
		int aboveSupport = chunk.findLastEmptyAbove(x, y, z, blockYs.getMaxHeight());
		if (aboveSupport < blockYs.getMaxHeight())
			chunk.setBlocks(x, y + 1, aboveSupport + 1, z, Material.OAK_FENCE);
	}

	private void placeMineStairBase(SupportBlocks chunk, int x, int y, int z) {
		chunk.setBlocks(x, y + 1, y + 4, z, Material.AIR);
		chunk.setEmptyBlock(x, y, z, Material.OAK_PLANKS);
	}

	private void placeMineStairStep(SupportBlocks chunk, int x, int y, int z, BlockFace direction,
			BlockFace flipDirection) {
		chunk.setBlocks(x, y + 1, y + 4, z, Material.AIR);
		chunk.setBlock(x, y, z, Material.OAK_STAIRS, direction);
		if (chunk.isEmpty(x, y - 1, z))
			chunk.setBlock(x, y - 1, z, Material.OAK_STAIRS, flipDirection, Half.TOP);
	}

	private void generateMineTreat(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {

		// cool stuff?
		if (generator.getSettings().treasuresInMines && chunkOdds.playOdds(generator.getSettings().oddsOfTreasureInMines)) {
			chunk.setChest(generator, x, y, z, chunkOdds, generator.lootProvider, LootLocation.MINE, copperChest(y));
		}
	}

	private void generateMineTrick(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {
		// not so cool stuff?
		generator.spawnProvider.setSpawnOrSpawner(generator, chunk, chunkOdds, x, y, z,
				generator.getSettings().spawnersInMines, generator.spawnProvider.itemsEntities_Mine);

		// cave-spider style: a proper nest — a dense web core around the spawner fading to wisps ~2 out
		if (generator.getSettings().spawnersInMines)
			for (int dx = -2; dx <= 2; dx++)
				for (int dz = -2; dz <= 2; dz++)
					for (int dy = 0; dy <= 2; dy++)
						if (!(dx == 0 && dz == 0 && dy == 0) && chunk.isEmpty(x + dx, y + dy, z + dz)) {
							int dist = Math.abs(dx) + Math.abs(dz) + dy;
							double webOdds = dist <= 1 ? Odds.oddsExtremelyLikely
									: dist <= 2 ? Odds.oddsVeryLikely : Odds.oddsSomewhatLikely;
							if (chunkOdds.playOdds(webOdds))
								chunk.setBlock(x + dx, y + dy, z + dz, Material.COBWEB);
						}
	}

	public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		return true;
	}

	/**
	 * Whether this lot wants the natural terrain (seabed + water, hill, whatever the noise says) under
	 * it rather than the flat foundation pad the terrain phase lays for a {@code STRUCTURE} lot. Default
	 * is no — a placed building normally stands on a levelled pad. An <em>ocean</em> schematic overrides
	 * this: it floats at the waterline and must sit over the real deep seabed, or the pad raises the sea
	 * floor to just under its hull and it reads as beached on a flat mound instead of afloat.
	 */
	public boolean generatesNaturalStrata() {
		return false;
	}

	/**
	 * For a {@code STRUCTURE}/{@code ROUNDABOUT} lot, whether this particular column should get the flat
	 * foundation pad (dirt up to street level) the terrain phase lays for buildings. Default is the
	 * whole lot. A schematic smaller than its chunk footprint overrides this so only the columns under
	 * the build itself get the pad; the leftover margin falls through to natural terrain and keeps its
	 * biome-correct surface (grass/sand/…) instead of showing a bare dirt apron around the build.
	 */
	public boolean isFoundationColumnAt(CityWorldGenerator generator, int blockX, int blockZ) {
		return true;
	}

	/** Cached per-lot pristine roll: null until first asked. */
	private Boolean decayThisLot;

	/**
	 * Whether this building lot should wear its apocalypse decay — {@code false} when decayed buildings
	 * are off, and also for the rare lot that rolls <em>pristine</em> and stands untouched even in a
	 * ruined world. Building lots test this instead of {@code includeDecayedBuildings} directly, so the
	 * decay-as-probability feature ({@code oddsOfPristineBuilding}, default tiny) applies to ordinary
	 * buildings the way it already does to placed schematics.
	 *
	 * <p>Rolled once per lot and cached, seeded from the building's {@link #getConnectedKey() connection
	 * key} (falling back to the lot position for isolated lots) so every chunk of a multi-chunk building
	 * agrees — it is wholly pristine or wholly decayed, never half-and-half.
	 */
	public boolean buildingsDecay(CityWorldGenerator generator) {
		if (!generator.getSettings().includeDecayedBuildings)
			return false;
		if (decayThisLot == null) {
			double pristine = generator.getSettings().oddsOfPristineBuilding;
			long key = getConnectedKey();
			if (key == -1L)
				key = chunkX * 341873128712L + chunkZ * 132897987541L;
			decayThisLot = pristine <= 0.0 || !new Odds(generator.getWorldSeed() + key).playOdds(pristine);
		}
		return decayThisLot;
	}

	protected boolean isValidWithBones() {
		return true;
	}

	public void generateBones(CityWorldGenerator generator, SupportBlocks chunk) {

		// fossils?
		if (isValidWithBones() && generator.getSettings().includeBones && chunkOdds.playOdds(Odds.oddsTremendouslyUnlikely))
			generator.thingProvider.generateBones(generator, this, chunk, blockYs, chunkOdds);
	}

	public void generateOres(CityWorldGenerator generator, SupportBlocks chunk) {

		// MODERN gets vanilla's own ore veins (CityWorldChunkGenerator.placeUndergroundOres); CityWorld's
		// own sprinkle pass is the CLASSIC path only, else the two would stack in the same stone.
		if (generator.isModernStyle())
			return;

		// shape the world
		if (generator.getSettings().includeOres || generator.getSettings().includeUndergroundFluids)
			generator.oreProvider.sprinkleOres(generator, this, chunk, blockYs, chunkOdds);
	}

	// TODO move this logic to SurroundingLots, add to it the ability to produce
	// SurroundingHeights and SurroundingDepths
	public PlatLot[][] getNeighborPlatLots(PlatMap platmap, int platX, int platZ, boolean onlyConnectedNeighbors) {
		PlatLot[][] miniPlatMap = new PlatLot[3][3];

		// populate the results
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {

				// which platchunk are we looking at?
				int atX = platX + x - 1;
				int atZ = platZ + z - 1;

				// is it in bounds?
				if (!(atX < 0 || atX > PlatMap.Width - 1 || atZ < 0 || atZ > PlatMap.Width - 1)) {
					PlatLot relative = platmap.getLot(atX, atZ);

					if (!onlyConnectedNeighbors || isConnected(relative)) {
						miniPlatMap[x][z] = relative;
					}
				}
			}
		}

		return miniPlatMap;
	}

	public void generateSurface(CityWorldGenerator generator, SupportBlocks chunk, boolean includeTrees) {
		generateSurface(generator, chunk, 0, includeTrees);
	}

	protected void generateSurface(CityWorldGenerator generator, SupportBlocks chunk, int addTo, boolean includeTrees) {

		// plant grass or snow... sometimes we want the sprinker to start a little
		// higher
		generator.surfaceProvider.generateSurface(generator, this, chunk, blockYs, addTo, includeTrees);
	}

	protected boolean clearAir(CityWorldGenerator generator) {
		return generator.shapeProvider.clearAtmosphere(generator);
	}

//	protected Material getAirMaterial(CityWorldGenerator generator, int y) {
//		if (getTopY(generator) <= y)
//			return Material.AIR;
//		else
//			return generator.shapeProvider.findAtmosphereMaterialAt(generator, y);
//	}
}
