package me.daddychurchill.CityWorld.Plats.Vanilla;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.CitySites;

/**
 * Where a city meets its river (a vanilla-terrain world, {@code worldgen.CitySites}): the chunk beside a river
 * channel. Owner, 2026-10-03, after the first river city: buildings ran up to and over the water; instead the city
 * should "back away", and where it does meet the water it should be "clearly meant to be a mooring point / loading
 * area" — hard stone banks and wooden boardwalks — while some of the bank is left alone.
 *
 * <p>Three kinds, by the chunk's odds: a <b>promenade</b> (a stone quay with a parapet, lanterns, benches and
 * planters), a <b>mooring</b> (the quay with a slip cut into it, a spruce boardwalk round the slip at the
 * waterline on pilings, bollards and steps down), and a <b>loading quay</b> (an open quay edge, a timber derrick
 * over the water, barrels, logs and hay). The bank left alone is not a kind of this lot: about half the chunks
 * beside the river are left to nature instead, and the natural bank eases up to the street there (owner,
 * 2026-10-04). A mooring or loading quay faces the side with the most river, and the chunk generator dredges the
 * bank in front of it out to the water ({@link #berthsOn}).
 *
 * <p>Drawn at CityWorld's street level and lifted with the rest of the city (the block seam does it), so the water
 * is drawn at the river's real surface less the city's lift ({@link #waterTop}). Which sides face the water is
 * asked of the sites when the lot is made; the channel itself is the chunk generator's.
 */
public class ShorelineLot extends IsolatedLot {

	/**
	 * The shared waterside kinds ({@code Plats.River.Waterside}), as this lot can draw them on a whole-chunk bank:
	 * moorings are drawn as the slip (a jetty would stand in the channel chunk, which is the river's), a beach as the
	 * promenade.
	 */
	private enum Kind {
		PROMENADE, MOORING, LOADING, RUSTIC
	}

	private static Kind of(me.daddychurchill.CityWorld.Plats.River.Waterside.Kind shared) {
		return switch (shared) {
		case MOORING, SLIP -> Kind.MOORING;
		case LOADING -> Kind.LOADING;
		case RUSTIC, NATURAL -> Kind.RUSTIC;
		default -> Kind.PROMENADE;
		};
	}

	/** {north, south, west, east}: whether that neighbour is river channel. */
	private final boolean[] water = new boolean[4];
	private final Kind kind;

	private final me.daddychurchill.CityWorld.Plats.River.Waterside.Kind shared;

	public ShorelineLot(PlatMap platmap, int chunkX, int chunkZ, me.daddychurchill.CityWorld.Plats.River.Waterside.Kind shared) {
		super(platmap, chunkX, chunkZ);
		style = LotStyle.STRUCTURE;
		trulyIsolated = false;
		CitySites sites = platmap.generator.citySites;
		if (sites != null) {
			water[0] = sites.isChannelChunk(chunkX, chunkZ - 1);
			water[1] = sites.isChannelChunk(chunkX, chunkZ + 1);
			water[2] = sites.isChannelChunk(chunkX - 1, chunkZ);
			water[3] = sites.isChannelChunk(chunkX + 1, chunkZ);
		}
		this.shared = shared;
		kind = of(shared);
		// the slip and the loading edge face the side with the most river beyond it
		int best = -1, most = 0;
		if (sites != null)
			for (int side = 0; side < 4; side++) {
				if (!water[side])
					continue;
				int cells = sites.riverCells(chunkX + (side == 2 ? -1 : side == 3 ? 1 : 0),
						chunkZ + (side == 0 ? -1 : side == 1 ? 1 : 0));
				if (cells > most) {
					most = cells;
					best = side;
				}
			}
		mainSide = best;
	}

	/** {@code 0..3} north, south, west, east: the side a mooring's slip or a loading quay's open edge faces. */
	private final int mainSide;

	/** Whether the river chunk on this side should be dredged out to the water in front of this quay. */
	public boolean berthsOn(int side) {
		return side == mainSide && (kind == Kind.MOORING || kind == Kind.LOADING);
	}

	/** Whether this chunk should be a shoreline lot: city ground beside a river channel. */
	public static boolean belongsAt(CitySites sites, int chunkX, int chunkZ) {
		return sites != null && !sites.isChannelChunk(chunkX, chunkZ) && sites.isCityChunk(chunkX, chunkZ)
				&& (sites.isChannelChunk(chunkX, chunkZ - 1) || sites.isChannelChunk(chunkX, chunkZ + 1)
						|| sites.isChannelChunk(chunkX - 1, chunkZ) || sites.isChannelChunk(chunkX + 1, chunkZ));
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new ShorelineLot(platmap, chunkX, chunkZ, shared);
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return waterTop(generator) - BED_DEPTH;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return generator.streetLevel + 8;
	}

	@Override
	public boolean allowsWildDecoration() {
		return kind == Kind.RUSTIC;
	}

	/** How deep the channel is kept, and so how far down the quay wall goes. */
	public static final int BED_DEPTH = 4;

	/** The river's top water block, in the drawing frame (the river is not lifted; the city is). */
	private int waterTop(CityWorldGenerator generator) {
		CitySites sites = generator.citySites;
		if (sites == null)
			return generator.seaLevel - 1;
		CitySites.Site site = sites.siteAt(getChunkX() * 16 + 8, getChunkZ() * 16 + 8);
		return sites.seaLevel() - 1 - (site == null ? 0 : sites.shift(site));
	}

	/** (u along the waterside edge, v away from the water) to chunk x for that side. */
	private static int xOf(int side, int u, int v) {
		return switch (side) {
		case 2 -> v;
		case 3 -> 15 - v;
		default -> u;
		};
	}

	private static int zOf(int side, int u, int v) {
		return switch (side) {
		case 0 -> v;
		case 1 -> 15 - v;
		default -> u;
		};
	}

	private static void set(AbstractBlocks chunk, int side, int u, int v, int y1, int y2, Material material) {
		int x = xOf(side, u, v), z = zOf(side, u, v);
		chunk.setBlocks(x, x + 1, y1, y2, z, z + 1, material);
	}

	private static void clear(AbstractBlocks chunk, int side, int u, int v, int y1, int y2) {
		int x = xOf(side, u, v), z = zOf(side, u, v);
		chunk.clearBlocks(x, x + 1, y1, y2, z, z + 1);
	}

	private int mainSide() {
		return mainSide;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		if (kind == Kind.RUSTIC)
			return; // the natural bank, as the land made it
		int deck = generator.streetLevel + 1; // the quay's walking surface
		int top = waterTop(generator), bed = top - BED_DEPTH;

		// clear above, firm below, paved at street level
		chunk.airoutLayer(generator, deck, 8);
		chunk.setBlocks(0, 16, deck - 4, deck, 0, 16, Material.STONE);
		chunk.setBlocks(0, 16, deck, deck + 1, 0, 16, Material.SMOOTH_STONE);

		for (int side = 0; side < 4; side++) {
			if (!water[side])
				continue;
			// the quay wall: two blocks of stone brick from the river bed to the deck, a coping, a parapet
			for (int u = 0; u < 16; u++) {
				set(chunk, side, u, 0, bed, deck, Material.STONE_BRICKS);
				set(chunk, side, u, 1, bed, deck, Material.STONE_BRICKS);
				set(chunk, side, u, 0, deck, deck + 1, Material.CHISELED_STONE_BRICKS);
			}
			boolean open = kind == Kind.LOADING && side == mainSide();
			for (int u = 0; u < 16; u++)
				if (!(open && u >= 5 && u <= 10))
					set(chunk, side, u, 0, deck + 1, deck + 2, Material.STONE_BRICKS);
		}

		int side = mainSide();
		if (kind == Kind.MOORING && side >= 0)
			drawSlip(chunk, side, deck, top, bed);
	}

	/**
	 * A slip cut into the quay, six wide and six deep, open to the river: water to the river's surface, a spruce
	 * boardwalk round it at the waterline (or five below the deck, whichever is higher) on log pilings to the bed,
	 * bollards at the mouth, and steps from the deck down to the boardwalk.
	 */
	private void drawSlip(InitialBlocks chunk, int side, int deck, int top, int bed) {
		int walk = Math.max(top + 1, deck - 5);
		// the slip and the boardwalk's footprint: dug out to the bed
		for (int u = 4; u <= 11; u++)
			for (int v = 0; v <= 6; v++) {
				clear(chunk, side, u, v, bed, deck + 2);
				set(chunk, side, u, v, bed - 1, bed, Material.GRAVEL);
				set(chunk, side, u, v, bed, top + 1, Material.WATER);
			}
		// the boardwalk: the two sides of the slip and its head
		for (int v = 0; v <= 6; v++) {
			set(chunk, side, 4, v, walk, walk + 1, Material.SPRUCE_PLANKS);
			set(chunk, side, 11, v, walk, walk + 1, Material.SPRUCE_PLANKS);
		}
		for (int u = 4; u <= 11; u++)
			set(chunk, side, u, 6, walk, walk + 1, Material.SPRUCE_PLANKS);
		// pilings under its corners and midpoints
		for (int[] p : new int[][] { { 4, 0 }, { 11, 0 }, { 4, 3 }, { 11, 3 }, { 4, 6 }, { 11, 6 }, { 7, 6 } })
			set(chunk, side, p[0], p[1], bed, walk, Material.SPRUCE_LOG);
		// bollards at the slip's mouth
		set(chunk, side, 4, 0, walk + 1, walk + 2, Material.SPRUCE_FENCE);
		set(chunk, side, 11, 0, walk + 1, walk + 2, Material.SPRUCE_FENCE);
		// steps from the boardwalk's head up to the deck, cut into the quay behind it
		int rise = deck - walk;
		for (int i = 1; i <= rise; i++)
			for (int u = 7; u <= 8; u++) {
				clear(chunk, side, u, 6 + i, walk + i + 1, deck + 2);
				set(chunk, side, u, 6 + i, walk, walk + i + 1, Material.STONE_BRICKS);
			}
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		dress(generator, chunk);
		if (!allowsWildDecoration())
			me.daddychurchill.CityWorld.Plats.River.Waterside.freeze(chunk, generator.streetLevel + 10, generator.seaLevel - 40);
	}

	private void dress(CityWorldGenerator generator, RealBlocks chunk) {
		if (kind == Kind.RUSTIC) {
			me.daddychurchill.CityWorld.Plats.River.Waterside.rusticJetty(chunk, waterTop(generator), generator.streetLevel,
					getChunkX() * 341873128712L ^ getChunkZ() * 132897987541L);
			return;
		}
		int deck = generator.streetLevel + 1;
		int side = mainSide();
		Material lantern = Material.of(net.minecraft.world.level.block.Blocks.LANTERN);
		switch (kind) {
		case PROMENADE -> {
			// lanterns along the parapet, benches facing the water, a planter or two
			for (int s = 0; s < 4; s++)
				if (water[s])
					for (int u = 2; u < 16; u += 5)
						set(chunk, s, u, 0, deck + 2, deck + 3, lantern);
			if (side >= 0) {
				for (int u : new int[] { 3, 4, 11, 12 })
					set(chunk, side, u, 3, deck + 1, deck + 2, Material.SMOOTH_STONE_SLAB);
				for (int u : new int[] { 7, 8 }) {
					set(chunk, side, u, 9, deck, deck + 1, Material.GRASS_BLOCK);
					set(chunk, side, u, 9, deck + 1, deck + 2, Material.FLOWERING_AZALEA);
				}
			}
		}
		case MOORING -> {
			for (int s = 0; s < 4; s++)
				if (water[s] && s != side)
					for (int u = 2; u < 16; u += 5)
						set(chunk, s, u, 0, deck + 2, deck + 3, lantern);
			if (side >= 0) {
				set(chunk, side, 2, 0, deck + 2, deck + 3, lantern);
				set(chunk, side, 13, 0, deck + 2, deck + 3, lantern);
			}
		}
		case LOADING -> {
			if (side < 0)
				break;
			// a timber derrick at the open edge, its arm out over the quay's lip
			set(chunk, side, 4, 2, deck + 1, deck + 7, Material.SPRUCE_LOG);
			for (int v = 0; v <= 2; v++)
				set(chunk, side, 4, v, deck + 7, deck + 8, Material.SPRUCE_PLANKS);
			set(chunk, side, 4, 0, deck + 6, deck + 7, Material.SPRUCE_FENCE);
			set(chunk, side, 4, 0, deck + 5, deck + 6, lantern);
			// cargo waiting on the quay: barrels (they get the chunk's loot), logs, hay
			Odds odds = chunkOdds;
			for (int u = 6; u <= 13; u++)
				for (int v = 4; v <= 7; v++) {
					if (!odds.playOdds(0.55))
						continue;
					double what = odds.getRandomDouble();
					Material cargo = what < 0.5 ? Material.BARREL : what < 0.8 ? Material.OAK_LOG : Material.HAY_BLOCK;
					int stack = 1 + odds.getRandomInt(2);
					set(chunk, side, u, v, deck + 1, deck + 1 + stack, cargo);
				}
		}
		}
	}
}
