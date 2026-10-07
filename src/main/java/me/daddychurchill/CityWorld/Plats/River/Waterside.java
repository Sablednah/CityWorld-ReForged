package me.daddychurchill.CityWorld.Plats.River;

import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Context.IndustrialContext;
import me.daddychurchill.CityWorld.Context.UrbanContext;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * What a city does where it meets water, the same on CityWorld's own land ({@link QuaysideLot}) and on vanilla land
 * ({@code Plats.Vanilla.ShorelineLot}): one list of kinds, one choice by district, and the shared pieces.
 *
 * <p>The district decides (owner, 2026-10-07): "cities tend to wind down to rural near the sea — to then suddenly
 * have big chunks of stone is out of place. Need it to do it more where industrial meets, and built up city meets
 * rivers, but not when rural housing or farms especially meet it — maybe the odd simple rustic wooden jetty for
 * those." So built-up districts ({@link UrbanContext}) get quays, industrial ones mostly loading quays; rural ones
 * keep their natural bank, now and then with a rustic jetty.
 */
public final class Waterside {

	private Waterside() {
	}

	public enum Kind {
		/** A paved quay, its wall along the water, lanterns on the parapet. */
		PROMENADE,
		/** The quay with timber jetties out over the water on pilings. */
		MOORING,
		/** The quay with a slip cut into it: water up to a boardwalk, steps down from the deck. */
		SLIP,
		/** The quay with a derrick over the water and cargo waiting. */
		LOADING,
		/** The coast only: the sand as it is, a boardwalk, umbrellas, a lifeguard tower. */
		BEACH,
		/** The natural bank, with one plain plank jetty on posts. */
		RUSTIC,
		/** The natural bank, as it is. */
		NATURAL
	}

	public static boolean builtUp(DataContext context) {
		return context instanceof UrbanContext;
	}

	/** What this stretch of waterside is, by its district; {@code coast} is a chunk where the land meets the sea. */
	public static Kind choose(DataContext context, boolean coast, Odds odds) {
		if (!builtUp(context))
			return odds.playOdds(0.3) ? Kind.RUSTIC : Kind.NATURAL;
		if (!odds.playOdds(0.85))
			return Kind.NATURAL;
		double roll = odds.getRandomDouble();
		if (context instanceof IndustrialContext)
			return roll < 0.45 ? Kind.LOADING : roll < 0.7 ? Kind.MOORING : roll < 0.9 ? Kind.SLIP : Kind.PROMENADE;
		if (coast)
			return roll < 0.25 ? Kind.PROMENADE : roll < 0.5 ? Kind.MOORING : roll < 0.65 ? Kind.SLIP
					: roll < 0.75 ? Kind.LOADING : Kind.BEACH;
		return roll < 0.4 ? Kind.PROMENADE : roll < 0.65 ? Kind.MOORING : roll < 0.85 ? Kind.SLIP : Kind.LOADING;
	}

	private static final int[][] AROUND = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	/** The topmost block of a column that is not air or a plant, scanning down from {@code from}; or -1. */
	private static int top(SupportBlocks chunk, int x, int z, int from, int to) {
		for (int y = from; y >= to; y--)
			if (!chunk.isEmpty(x, y, z))
				return y;
		return -1;
	}

	/**
	 * One plain plank jetty from the natural bank out over the water, on log posts, found from the blocks as they are
	 * (so it fits any bank, any land): a dry column at the water's edge, no more than two above it, with at least
	 * three blocks of open water in front within the chunk. The first such edge on the chunk's own hash wins.
	 * {@code seaLevel} is where the water is expected (on vanilla land, the river's surface in the lifted frame).
	 */
	public static void rusticJetty(SupportBlocks chunk, int seaLevel, int streetLevel, long hash) {
		int start = (int) Math.floorMod(hash, 256L);
		for (int n = 0; n < 256; n++) {
			int i = (start + n * 37) & 255, x = i >> 4, z = i & 15;
			int land = top(chunk, x, z, Math.max(streetLevel, seaLevel) + 6, seaLevel - 6);
			if (land < 0 || chunk.isWaterAt(x, land, z))
				continue;
			for (int[] o : AROUND) {
				int wx = x + o[0], wz = z + o[1];
				if (wx < 0 || wz < 0 || wx > 15 || wz > 15)
					continue;
				int water = top(chunk, wx, wz, Math.max(streetLevel, seaLevel) + 6, seaLevel - 6);
				if (water < 0 || !chunk.isWaterAt(wx, water, wz) || land > water + 2 || land < water)
					continue;
				int len = 0;
				for (int k = 1; k <= 7; k++) {
					int jx = x + o[0] * k, jz = z + o[1] * k;
					if (jx < 0 || jz < 0 || jx > 15 || jz > 15 || !chunk.isWaterAt(jx, water, jz)
							|| !chunk.isEmpty(jx, water + 1, jz))
						break;
					len = k;
				}
				if (len < 3)
					continue;
				int deck = water + 1;
				for (int k = 1; k <= len; k++) {
					int jx = x + o[0] * k, jz = z + o[1] * k;
					chunk.setBlock(jx, deck, jz, Material.OAK_PLANKS);
					if (k % 3 == 0 || k == len) {
						for (int y = deck - 1; y > seaLevel - 12 && (chunk.isWaterAt(jx, y, jz) || chunk.isEmpty(jx, y, jz)); y--)
							chunk.setBlock(jx, y, jz, Material.OAK_LOG);
					}
				}
				int ex = x + o[0] * len, ez = z + o[1] * len;
				chunk.setBlock(ex, deck + 1, ez, Material.OAK_FENCE);
				return;
			}
		}
	}

	/**
	 * What vanilla's top-layer freeze would have done here, for a quay its decoration does not reach (owner,
	 * 2026-10-07: frozen rivers thawed in square chunks at every quay): in a biome cold enough to snow, the water's
	 * surface turns to ice and the paving takes a layer of snow. Columns scanned down from {@code from} to {@code to}.
	 */
	public static void freeze(me.daddychurchill.CityWorld.Support.RealBlocks chunk, int from, int to) {
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int y = top(chunk, x, z, from, to);
				if (y < 0 || !chunk.coldEnoughToSnow(x, y + 1, z))
					continue;
				if (chunk.isWaterAt(x, y, z))
					chunk.setBlock(x, y, z, Material.ICE);
				else if (chunk.isOfTypes(x, y, z, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
						Material.SPRUCE_PLANKS, Material.OAK_PLANKS, Material.SAND, Material.STONE))
					chunk.setBlock(x, y + 1, z, Material.SNOW);
			}
	}

	/**
	 * A quay's timber jetty from an edge column ({@code x, z}) out over the water ({@code dx, dz}), its planks at the
	 * deck, up to {@code most} long within the chunk while {@code wet} holds; pilings to the floor at its end, and a
	 * bollard there. Returns how long it came out (0: no room).
	 */
	public static int jetty(SupportBlocks chunk, int x, int z, int dx, int dz, int deck, int most, int floorFrom,
			java.util.function.BiPredicate<Integer, Integer> wet) {
		int len = 0;
		for (int i = 1; i <= most; i++) {
			int jx = x + dx * i, jz = z + dz * i;
			if (jx < 0 || jz < 0 || jx > 15 || jz > 15 || !wet.test(jx, jz))
				break;
			chunk.setBlock(jx, deck, jz, Material.SPRUCE_PLANKS);
			len = i;
		}
		if (len > 0) {
			int ex = x + dx * len, ez = z + dz * len;
			for (int y = floorFrom; y < deck; y++)
				if (chunk.isWaterAt(ex, y, ez) || chunk.isEmpty(ex, y, ez))
					chunk.setBlock(ex, y, ez, Material.SPRUCE_LOG);
			chunk.setBlock(ex, deck + 1, ez, Material.SPRUCE_FENCE);
		}
		return len;
	}
}
