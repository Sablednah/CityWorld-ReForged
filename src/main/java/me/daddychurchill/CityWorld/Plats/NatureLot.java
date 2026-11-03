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
	 * Rope bridges (Macaw's Bridges, {@code #cityworld:fittings/rope_bridge}): a find, not a fixture — at most one in a
	 * region of {@code roads.wildBridgeRegion} chunks square, and one region in {@code roads.oddsOfWildBridge} has
	 * one. The region's seed orders its lines (chunk rows, then columns, or the other way about, each through the
	 * chunks' middle), and the bridge is the first span along the first line that has one: from a ridge crest, the
	 * deck one block above it, across a valley at least {@code roads.wildBridgeDepth} below, to the first column where
	 * the ground comes back up to the deck, so it lands on the far side and never needs a support (the owner: "if it
	 * had carried on two more blocks it would have reached the mountain"). At least a chunk and at most
	 * {@code roads.wildBridgeSpan} long, wholly inside the region. The search reads the natural terrain, the same from
	 * any chunk, once per region (memo); each chunk on the span then checks it against the PLAN it will draw (a city
	 * levels its nature lots) — every column of it and its crest test on plain wild land (a {@code NatureLot}
	 * exactly, unreserved) with the same heights — and draws its own part. Empty pool: nothing drawn.
	 */
	private void drawRopeBridge(CityWorldGenerator generator, RealBlocks chunk) {
		me.daddychurchill.CityWorld.CityWorldSettings settings = generator.getSettings();
		if (!settings.includeWildBridges || !takesRopeBridge(this))
			return;
		int region = Math.max(4, settings.wildBridgeRegion);
		int rx = Math.floorDiv(chunkX, region), rz = Math.floorDiv(chunkZ, region);
		int[] found = regionBridge(generator, settings, region, rx, rz);
		if (found == NO_REGION_BRIDGE)
			return;
		boolean alongX = found[0] == 1;
		int row = found[1], s = found[2], e = found[3];
		int o = (alongX ? chunkX : chunkZ) << 4;
		if ((alongX ? chunkZ : chunkX) != row >> 4 || e < o || s > o + 15)
			return;
		Material rope = me.daddychurchill.CityWorld.Support.MaterialTags.pick(
				me.daddychurchill.CityWorld.Support.MaterialTags.FITTINGS_ROPE_BRIDGE,
				new Odds(regionSeed(generator, rx, rz) ^ 0x524F5045L), null);
		if (rope == null)
			return;
		java.util.function.IntUnaryOperator plan = i -> alongX ? plannedGround(generator, i, row) : plannedGround(generator, row, i);
		if (!crest(plan, s, generator.seaLevel) || spanEnd(plan, s, settings.wildBridgeDepth, settings.wildBridgeSpan) != e)
			return; // the plan draws something else here: no bridge, in every chunk alike
		drawSpan(chunk, alongX, s, e, plan.applyAsInt(s) + 1, rope);
	}

	private static long regionSeed(CityWorldGenerator generator, int rx, int rz) {
		return generator.getWorldSeed() ^ (rx * 0x9E3779B97F4A7C15L) ^ (rz * 0xC2B2AE3D27D4EB4FL);
	}

	/** A region with no rope bridge (memo value; a memo cannot hold null). */
	private static final int[] NO_REGION_BRIDGE = new int[0];

	/** {@code region -> {alongX, line's fixed block coordinate, start, end}}, per world (weak keys). */
	private static final java.util.Map<CityWorldGenerator, java.util.concurrent.ConcurrentHashMap<Long, int[]>> REGION_BRIDGES =
			java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** The region's one span, from the natural terrain: worked out once (outside any map lock) and remembered. */
	private static int[] regionBridge(CityWorldGenerator generator, me.daddychurchill.CityWorld.CityWorldSettings settings,
			int region, int rx, int rz) {
		java.util.concurrent.ConcurrentHashMap<Long, int[]> memo = REGION_BRIDGES.computeIfAbsent(generator,
				g -> new java.util.concurrent.ConcurrentHashMap<>());
		long key = ((long) rx << 32) ^ (rz & 0xffffffffL);
		int[] known = memo.get(key);
		if (known != null)
			return known;
		int[] found = NO_REGION_BRIDGE;
		Odds dice = new Odds(regionSeed(generator, rx, rz));
		if (dice.playOdds(settings.oddsOfWildBridge)) {
			int[] order = new int[region];
			for (int k = 0; k < region; k++)
				order[k] = k;
			for (int k = region - 1; k > 0; k--) { // the region's own order of its lines
				int m = dice.getRandomInt(k + 1), t = order[k];
				order[k] = order[m];
				order[m] = t;
			}
			boolean firstAlongX = dice.flipCoin();
			search: for (int pass = 0; pass < 2; pass++) {
				boolean alongX = pass == 0 ? firstAlongX : !firstAlongX;
				for (int line : order) {
					int row = ((alongX ? rz : rx) * region + line) * 16 + 7;
					int[] span = firstSpan(generator, settings, alongX, row, (alongX ? rx : rz) * region * 16, region * 16);
					if (span != null) {
						found = new int[] { alongX ? 1 : 0, row, span[0], span[1] };
						break search;
					}
				}
			}
		}
		int[] raced = memo.putIfAbsent(key, found);
		return raced != null ? raced : found;
	}

	/** The first span along a line of the natural terrain that lies wholly within {@code first..first+length-1}. */
	private static int[] firstSpan(CityWorldGenerator generator, me.daddychurchill.CityWorld.CityWorldSettings settings,
			boolean alongX, int row, int first, int length) {
		int last = first + length - 1, lo = first - CREST_REACH;
		int[] natural = new int[length + 2 * CREST_REACH + 2];
		java.util.Arrays.fill(natural, Integer.MIN_VALUE);
		java.util.function.IntUnaryOperator nat = i -> {
			if (i < lo || i - lo >= natural.length)
				return NO_BRIDGE; // beyond the region: a wall, so a span never leaves it
			int k = i - lo;
			if (natural[k] == Integer.MIN_VALUE)
				natural[k] = alongX ? generator.getFarBlockY(i, row) : generator.getFarBlockY(row, i);
			return natural[k];
		};
		for (int c = first; c <= last - LEAST_SPAN; c++)
			if (crest(nat, c, generator.seaLevel)) {
				int far = spanEnd(nat, c, settings.wildBridgeDepth, settings.wildBridgeSpan);
				if (far != NO_SPAN && far <= last)
					return new int[] { c, far };
			}
		return null;
	}

	private static final int CREST_REACH = 8, LEAST_SPAN = 16;

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

	/**
	 * No span. Never -1: a block coordinate is negative west and north of the origin, and a -1 sentinel there passed
	 * the "ends before this chunk" test, drew bogus spans ending at block -1 and read past the height cache.
	 */
	private static final int NO_SPAN = Integer.MIN_VALUE;

	/**
	 * The far end of a span from crest {@code s} (the deck one above it), or {@link #NO_SPAN}: the first column where
	 * the ground comes back up to the deck's foot, at least a chunk on, past a valley at least {@code depth} below the
	 * crest. That column may stand up to two above the crest: its top blocks give way to the bridge's end and its
	 * headroom, a notch into the hillside a step up from the deck at most; any higher is a cliff.
	 */
	private static int spanEnd(java.util.function.IntUnaryOperator y, int s, int depth, int span) {
		int h0 = y.applyAsInt(s), low = h0;
		for (int e = s + 1; e <= s + span; e++) {
			int he = y.applyAsInt(e);
			if (he >= h0)
				return e - s >= LEAST_SPAN && h0 - low >= depth && he <= h0 + 2 ? e : NO_SPAN;
			low = Math.min(low, he);
		}
		return NO_SPAN;
	}

	/** This chunk's blocks of the span {@code s..e} at {@code deckY}: the mod's pieces, the headroom over them. */
	private void drawSpan(RealBlocks chunk, boolean alongX, int s, int e, int deckY, Material rope) {
		int o = (alongX ? chunkX : chunkZ) << 4;
		net.minecraft.world.level.block.state.BlockState facing = rope.withFacing(alongX ? BlockFace.EAST : BlockFace.SOUTH);
		// the mod names a piece by where its rails are (middle_ns = rails north and south = walking east-west) and an
		// end by the way the bridge continues from it (middle_end_e = the west end), read from its models
		String middle = alongX ? "middle_ns" : "middle_ew";
		String startPiece = alongX ? "middle_end_e" : "middle_end_s", endPiece = alongX ? "middle_end_w" : "middle_end_n";
		for (int i = Math.max(s, o); i <= Math.min(e, o + 15); i++) {
			int x = alongX ? i - o : 7, z = alongX ? 7 : i - o;
			String piece = i == s ? startPiece : i == e ? endPiece : middle;
			chunk.setBlockState(x, deckY, z, Material.withProperty(facing, "connection", piece));
			chunk.setBlocks(x, deckY + 1, deckY + 3, z, Material.AIR);
		}
	}
}
