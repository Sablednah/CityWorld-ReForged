package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

public class NatureLot extends IsolatedLot {

	public NatureLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

		style = LotStyle.NATURE;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new NatureLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return 0;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return blockYs.getBlockY(x, z);// + generator.landRange;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		// In MODERN, vanilla decorates the wild (biome-appropriate trees/flowers), so don't also plant
		// CityWorld's own trees here — that doubling was making forests read too dense. Other styles keep
		// placing them. The grass/snow surface is still laid down either way (vanilla needs it to plant on).
		// ...and never on a chunk reserved for a structure. CityWorld's own decoration runs at the TOP of
		// applyBiomeDecoration, well before the structure step further down, so a tree planted here is one
		// a village house then stamps through. A reserved chunk is meant to be bare ground for the
		// structure to land on, and for the pad to smooth.
		//
		// ⚠ Only the TREES are suppressed, never generateSurface itself — see the note below: skipping the
		// whole pass was the first version of that change and it left the MODERN peaks bare.
		//
		// On MODERN this is already false (vanilla decorates the wild, and it places SURFACE_STRUCTURES
		// before VEGETAL_DECORATION, so its own trees land after the structure). This therefore matters on
		// CLASSIC and the other styles that still plant CityWorld's trees.
		boolean cityworldTrees = !generator.isModernStyle() && !structureReserved;
		// Always run the surface pass — wildDecoration=VANILLA suppresses CityWorld's *plants* inside it
		// (see SurfaceProvider_Normal) rather than skipping the pass, so the MODERN icecap survives.
		// Skipping the whole pass was the first version and it left the peaks bare.
		generateSurface(generator, chunk, cityworldTrees);

		// Biome-signature ground (sand deserts, badlands, mycelium, snowy plains) is applied by the base
		// PlatLot.generateBlocks after this returns — so it covers farms/houses too, not just the wild.

		// MODERN swamps/mangroves read swampy but had no water. Rebuild the swamp floor as muddy ground
		// shot through with water pools — only the low, flat interior is brought flush to the water table;
		// rises just get muddied in place (never flattened) and edges feather to a dry bank. Vanilla then
		// adds lily pads, mangrove roots and blue orchids.
		if (generator.isModernStyle() && !generator.isSiteCore(getChunkX(), getChunkZ())
				&& chunk.isSwampBiome(8, getBlockY(8, 8), 8))
			generateSwampSurface(generator, chunk);

		// a rope bridge over a ravine, from a bridge mod's pool (nothing without one)
		drawRopeBridge(generator, chunk);

		generateEntities(generator, chunk);
	}

	private void generateSwampSurface(CityWorldGenerator generator, RealBlocks chunk) {
		int sea = generator.seaLevel;
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int top = getBlockY(x, z);
				if (top < sea)
					continue; // already open water at the swamp's edge — leave it
				// a chunk can straddle a biome boundary; only reshape columns that are really swamp
				if (!chunk.isSwampBiome(x, top, z))
					continue;

				clearVegetation(chunk, x, top + 1, z);
				clearVegetation(chunk, x, top + 2, z);

				boolean bank = swampBank(chunk, x, top, z); // touches non-swamp — feather to a dry bank
				int drop = top - sea;

				if (bank || drop > 2) {
					// Follow the terrain — never flatten a rise (that carved sheer walls into hills). Muddy
					// ground; away from the banks, the odd shallow water pocket dug just 1 deep in place.
					if (!bank && drop <= 6 && chunkOdds.playOdds(Odds.oddsSomewhatUnlikely)) {
						chunk.setBlock(x, top, z, Material.WATER);
						chunk.setBlock(x, top - 1, z, Material.MUD);
					} else {
						chunk.setBlock(x, top, z, chunkOdds.getRandomDouble() < 0.65 ? Material.MUD
								: Material.GRASS_BLOCK);
					}
				} else {
					// Interior and already at the water table: strip the thin dry cap and lay a watery,
					// muddy pond floor flush with the sea.
					chunk.setBlocks(x, sea + 1, top + 2, z, Material.AIR);
					double roll = chunkOdds.getRandomDouble();
					if (roll < 0.5) {
						chunk.setBlock(x, sea, z, Material.WATER);
						chunk.setBlock(x, sea - 1, z, Material.MUD);
					} else if (roll < 0.9) {
						chunk.setBlock(x, sea, z, Material.MUD);
					} else {
						chunk.setBlock(x, sea, z, Material.GRASS_BLOCK);
					}
				}
			}
	}

	/** A swamp column that borders non-swamp terrain (including the neighbouring chunk) — feathered as a
	 *  dry muddy bank so the swamp doesn't meet the wild in a hard step. */
	private boolean swampBank(RealBlocks chunk, int x, int y, int z) {
		return !chunk.isSwampBiome(x - 1, y, z) || !chunk.isSwampBiome(x + 1, y, z)
				|| !chunk.isSwampBiome(x, y, z - 1) || !chunk.isSwampBiome(x, y, z + 1);
	}

	private final static int magicSeaSpawnY = 62;

	protected void generateEntities(CityWorldGenerator generator, RealBlocks chunk) {
		int x = chunkOdds.getRandomInt(1, 14);
		int z = chunkOdds.getRandomInt(1, 14);
		int y = getBlockY(x, z);

		// in the water?
		if (y < magicSeaSpawnY) {
			generator.spawnProvider.spawnSeaAnimals(generator, chunk, chunkOdds, x, magicSeaSpawnY, z);
//			chunk.setBlock(x, 100, z, Material.LAPIS_BLOCK);
		} else {
//			int origY = getBlockY(x, z);
//			int topY = getTopY(generator);
//			int y = chunk.findFirstEmptyAbove(x, origY, z, topY);
//			chunk.setSignPost(x, 101, z, BlockFace.NORTH, "Y = " + y, "origY = " + origY, "TopY = " + topY);
			if (!chunk.isWater(x, y - 1, z)) {
				generator.spawnProvider.spawnVagrants(generator, chunk, chunkOdds, x, y, z);
//				chunk.setBlock(x, 100, z, Material.IRON_BLOCK);
//			} else {
//				chunk.setBlock(x, 100, z, Material.DIAMOND_BLOCK);
			}
		}
	}


	/**
	 * Rope bridges (Macaw's Bridges, {@code #cityworld:fittings/rope_bridge}) across a valley: from a ridge crest on
	 * this chunk's middle row (or column) — or one up to {@code bridges.wildBridgeSpan} back along it — to the first
	 * column at least a chunk on whose ground stands within {@code bridges.wildBridgeStep} of the crest, with the
	 * valley between at least {@code bridges.wildBridgeDepth} below both ends and nothing between standing as high as
	 * the deck. Read from the PLAN alone — each column's ground as the lot planned there will draw it — so every
	 * chunk the span crosses finds the same bridge and draws its own blocks of it; one valley in
	 * {@code bridges.oddsOfWildBridge} gets one, decided from the crest's own seed, which also picks the wood. Every
	 * chunk the span and its crest test touch must be plain wild land that draws this pass (a {@code NatureLot}
	 * exactly, not reserved for a structure): anywhere else reads as a wall, so a bridge never runs into a city
	 * or stops dead at a lot that would not draw its part. (The natural terrain is not the plan: a city levels its
	 * nature lots, and a bridge read from {@code getFarBlockY} stood on flat ground at spawn.) The lower end is
	 * built up to the deck with a bank of its own ground, and the headroom over the deck is cleared. Empty pool:
	 * nothing drawn.
	 */
	private void drawRopeBridge(CityWorldGenerator generator, RealBlocks chunk) {
		me.daddychurchill.CityWorld.CityWorldSettings settings = generator.getSettings();
		if (!settings.includeWildBridges || !takesRopeBridge(this))
			return;
		drawRopeBridges(generator, chunk, settings, true);
		drawRopeBridges(generator, chunk, settings, false);
	}

	private static final int CREST_REACH = 8, LEAST_SPAN = 16;

	private void drawRopeBridges(CityWorldGenerator generator, RealBlocks chunk,
			me.daddychurchill.CityWorld.CityWorldSettings settings, boolean alongX) {
		int row = alongX ? (chunkZ << 4) + 7 : (chunkX << 4) + 7; // the world coordinate of the fixed axis
		int o = alongX ? (chunkX << 4) : (chunkZ << 4); // this chunk's start along the run
		int depth = settings.wildBridgeDepth, step = settings.wildBridgeStep, span = Math.max(LEAST_SPAN, settings.wildBridgeSpan);
		// the row's heights, read once each: a crest test looks 8 either way and a span up to wildBridgeSpan on
		int first = o - span - CREST_REACH;
		int[] heights = new int[span + 16 + span + 2 * CREST_REACH];
		java.util.Arrays.fill(heights, Integer.MIN_VALUE);
		java.util.function.IntUnaryOperator y = i -> {
			int k = i - first;
			if (heights[k] == Integer.MIN_VALUE)
				heights[k] = alongX ? plannedGround(generator, i, row) : plannedGround(generator, row, i);
			return heights[k];
		};
		for (int s = o - span; s <= o + 15; s++) {
			if (!crest(y, s, generator.seaLevel))
				continue;
			int e = spanEnd(y, s, depth, step, span, generator.seaLevel);
			if (e < o)
				continue; // no bridge from this crest, or one that ends before this chunk
			Odds dice = new Odds(generator.getWorldSeed() ^ ((long) s << 21) ^ ((long) row << 1) ^ (alongX ? 1L : 0L));
			if (!dice.playOdds(settings.oddsOfWildBridge))
				continue;
			Material rope = me.daddychurchill.CityWorld.Support.MaterialTags.pick(
					me.daddychurchill.CityWorld.Support.MaterialTags.FITTINGS_ROPE_BRIDGE, dice, null);
			if (rope == null)
				return;
			drawSpan(generator, chunk, alongX, s, e, y.applyAsInt(s), y.applyAsInt(e), rope);
		}
	}

	/** Plain wild land, unreserved: the only lot that draws this pass and stands on its own planned ground. */
	private static boolean takesRopeBridge(PlatLot lot) {
		return lot != null && lot.getClass() == NatureLot.class && !lot.structureReserved;
	}

	/** A wall no deck clears: a column on any lot that does not take rope bridges. */
	private static final int NO_BRIDGE = 10_000;

	/** The ground the plan will draw in a column, or {@link #NO_BRIDGE} off wild land. */
	private static int plannedGround(CityWorldGenerator generator, int blockX, int blockZ) {
		int cx = blockX >> 4, cz = blockZ >> 4;
		PlatLot lot;
		try {
			lot = generator.getPlatMap(cx, cz).getMapLot(cx, cz);
		} catch (IndexOutOfBoundsException e) {
			return NO_BRIDGE;
		}
		return takesRopeBridge(lot) ? ((NatureLot) lot).getBlockY(blockX & 15, blockZ & 15) : NO_BRIDGE;
	}

	/** A column at least as high as every column within reach of it, and above its eastern (southern) neighbour. */
	private static boolean crest(java.util.function.IntUnaryOperator y, int s, int sea) {
		int h = y.applyAsInt(s);
		if (h <= sea || h >= NO_BRIDGE || y.applyAsInt(s + 1) >= h)
			return false;
		for (int d = 1; d <= CREST_REACH; d++)
			if (y.applyAsInt(s - d) > h || y.applyAsInt(s + d) > h)
				return false;
		return true;
	}

	/** The far end of a span from crest {@code s}, or -1: the first far column within {@code step} of the crest, a valley at least {@code depth} below both between, nothing between above the deck. */
	private static int spanEnd(java.util.function.IntUnaryOperator y, int s, int depth, int step, int span, int sea) {
		int h0 = y.applyAsInt(s), low = h0, high = h0;
		for (int e = s + 1; e <= s + span; e++) {
			int he = y.applyAsInt(e);
			if (he > h0 + step)
				return -1; // a wall higher than any deck could be
			if (e - s >= LEAST_SPAN && Math.abs(he - h0) <= step && he > sea && Math.min(h0, he) - low >= depth
					&& high <= Math.max(h0, he))
				return e;
			low = Math.min(low, he);
			high = Math.max(high, he);
		}
		return -1;
	}

	/** This chunk's blocks of the span {@code s..e}: deck pieces, the banks under a low end, the headroom. */
	private void drawSpan(CityWorldGenerator generator, RealBlocks chunk, boolean alongX, int s, int e, int hs, int he,
			Material rope) {
		int o = alongX ? (chunkX << 4) : (chunkZ << 4);
		int deckY = Math.max(hs, he) + 1;
		net.minecraft.world.level.block.state.BlockState facing = rope.withFacing(alongX ? BlockFace.EAST : BlockFace.SOUTH);
		// the mod names a piece by where its rails are (middle_ns = rails north and south = walking east-west) and an
		// end by the way the bridge continues from it (middle_end_e = the west end), read from its models
		String middle = alongX ? "middle_ns" : "middle_ew";
		String startPiece = alongX ? "middle_end_e" : "middle_end_s", endPiece = alongX ? "middle_end_w" : "middle_end_n";
		for (int i = Math.max(s, o); i <= Math.min(e, o + 15); i++) {
			int x = alongX ? i - (chunkX << 4) : 7, z = alongX ? 7 : i - (chunkZ << 4);
			String piece = i == s ? startPiece : i == e ? endPiece : middle;
			chunk.setBlockState(x, deckY, z, Material.withProperty(facing, "connection", piece));
			chunk.setBlocks(x, deckY + 1, deckY + 3, z, Material.AIR);
		}
		// the lower end's bank: three wide and two long, of each cell's own ground, up to the deck
		int lowEnd = hs < he ? s : he < hs ? e : -1;
		if (lowEnd >= 0 && Math.min(hs, he) < deckY - 1) {
			int near = lowEnd == s ? s : e - 1;
			for (int i = near; i <= near + 1; i++)
				for (int j = 6; j <= 8; j++) {
					int x = alongX ? i - (chunkX << 4) : j, z = alongX ? j : i - (chunkZ << 4);
					if (x < 0 || x > 15 || z < 0 || z > 15)
						continue;
					int ground = getBlockY(x, z);
					if (ground < deckY - 1)
						chunk.setBlocks(x, ground + 1, deckY, z, chunk.getActualBlock(x, ground, z).getType());
				}
		}
	}
}
