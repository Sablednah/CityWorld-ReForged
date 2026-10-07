package me.daddychurchill.CityWorld.Plats.River;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.RiverNetwork;

/**
 * Where a river runs through a city on CityWorld's own land ({@code worldgen/RiverNetwork}): the chunk the water
 * crosses. The river itself is left exactly as the network drew it; the dry bank in the chunk becomes the city's
 * edge — a paved quay at street level, a stone quay wall wherever it meets the water, a parapet along it — in one
 * of three kinds, as vanilla land's {@code ShorelineLot}: a <b>promenade</b> (lanterns on the parapet), a
 * <b>mooring</b> (short jetties on pilings out over the water, bollards at their ends) and a <b>loading quay</b>
 * (a timber derrick over the water, cargo on the quay). About a third of a city's river chunks get no lot at all
 * and keep their natural bank ({@code ShapeProvider_Normal.validateLots}).
 *
 * <p>Unlike vanilla land, the water here is a free curve through the chunk, not a whole chunk: which columns are
 * water, and which dry columns touch it, is asked of the network column by column, across the chunk's edges too.
 *
 * <p><b>The coast</b> is the same lot with the sea's water counted too ({@code coast}): a city's chunk where the
 * land meets the sea gets its quay wall along the real shore, longer jetties out over the sea (mostly moorings and
 * loading quays — a harbour), and now and then a lighthouse on the quay; or a city <b>beach</b>, its sand left as
 * it is, with a boardwalk where the street meets it, striped umbrellas and towels, and a lifeguard tower. A harbour lot that built on whole sea
 * chunks put its quay wall along a chunk edge out in the water, with nothing reaching the beach (2026-10-07).
 */
public class QuaysideLot extends IsolatedLot {

	public enum Kind {
		PROMENADE, MOORING, LOADING,
		/** The coast only: the sand left as it is, a boardwalk where the street meets it, umbrellas, a lifeguard tower. */
		BEACH
	}

	private final Kind kind;
	private final boolean coast, lighthouse;

	public QuaysideLot(PlatMap platmap, int chunkX, int chunkZ, boolean coast) {
		super(platmap, chunkX, chunkZ);
		style = LotStyle.STRUCTURE;
		trulyIsolated = false;
		this.coast = coast;
		double roll = chunkOdds.getRandomDouble();
		Kind chosen = coast ? (roll < 0.1 ? Kind.PROMENADE : roll < 0.45 ? Kind.MOORING : roll < 0.7 ? Kind.LOADING : Kind.BEACH)
				: (roll < 0.4 ? Kind.PROMENADE : roll < 0.75 ? Kind.MOORING : Kind.LOADING);
		String forced = System.getProperty("cityworld.rivers.quay"); // a probe's way to see a kind it would rarely meet
		kind = forced != null && (coast || !forced.equals("BEACH")) ? Kind.valueOf(forced) : chosen;
		lighthouse = coast && kind != Kind.BEACH && chunkOdds.playOdds(0.15);
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new QuaysideLot(platmap, chunkX, chunkZ, coast);
	}

	/** A river chunk refuses ordinary lots; this one is made for it. */
	@Override
	public boolean isPlaceableAt(CityWorldGenerator generator, int chunkX, int chunkZ) {
		return true;
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return generator.seaLevel - 8;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return generator.streetLevel + (lighthouse ? TOWER + 6 : 8);
	}

	@Override
	public boolean allowsWildDecoration() {
		return false;
	}

	/** The terrain's own ground and water stay under it: no foundation pad (that filled the water in to the street). */
	@Override
	public boolean generatesNaturalStrata() {
		return true;
	}

	private static final int[][] AROUND = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };

	private boolean wet(CityWorldGenerator generator, int x, int z) {
		int wx = getChunkX() * 16 + x, wz = getChunkZ() * 16 + z;
		if (generator.shapeProvider.riverWaterAt(wx, wz) != RiverNetwork.NONE)
			return true;
		if (!coast)
			return false;
		int ground = x >= 0 && x < 16 && z >= 0 && z < 16 ? blockYs.getBlockY(x, z)
				: generator.shapeProvider.findBlockY(generator, wx, wz);
		return ground < generator.seaLevel;
	}

	/** The water beside a dry column ({dx, dz}), or null when it has none. */
	private int[] waterBeside(CityWorldGenerator generator, int x, int z) {
		for (int[] o : AROUND)
			if (wet(generator, x + o[0], z + o[1]))
				return o;
		return null;
	}

	/** Whether this dry column is low enough to be quay: banks up in the hills are left as they are. */
	private boolean quay(CityWorldGenerator generator, int x, int z) {
		return !wet(generator, x, z) && blockYs.getBlockY(x, z) <= generator.streetLevel + 2;
	}

	/** Where a jetty or the derrick stands: a few edge columns, spread out, chosen by position (both passes agree). */
	private boolean feature(CityWorldGenerator generator, int x, int z) {
		long h = (getChunkX() * 341873128712L) ^ (getChunkZ() * 132897987541L) ^ (x * 31 + z);
		h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
		return Math.floorMod(h >>> 7, 19) == 0 && x >= 2 && x <= 13 && z >= 2 && z <= 13;
	}

	/** Where the lighthouse stands, {x, z} of its middle, or null: on the quay, all nine of its columns dry, near the sea. */
	private int[] lighthouseAt(CityWorldGenerator generator) {
		if (!lighthouse)
			return null;
		for (int x = 2; x <= 13; x++)
			for (int z = 2; z <= 13; z++) {
				boolean fits = true, nearSea = false;
				for (int a = -1; a <= 1 && fits; a++)
					for (int b = -1; b <= 1 && fits; b++)
						fits = quay(generator, x + a, z + b) && waterBeside(generator, x + a, z + b) == null;
				for (int a = -2; a <= 2 && fits && !nearSea; a++)
					for (int b = -2; b <= 2 && !nearSea; b++)
						nearSea = wet(generator, x + a, z + b);
				if (fits && nearSea)
					return new int[] { x, z };
			}
		return null;
	}

	private static final int TOWER = 12;

	/** Whether a column beside this one (in the chunk) is beach: sand at the sea's level. */
	private boolean besideSand(CityWorldGenerator generator, int x, int z) {
		for (int a = -1; a <= 1; a++)
			for (int b = -1; b <= 1; b++) {
				int nx = x + a, nz = z + b;
				if (nx >= 0 && nz >= 0 && nx < 16 && nz < 16 && blockYs.getBlockY(nx, nz) == generator.seaLevel)
					return true;
			}
		return false;
	}

	/** A beach column: dry sand at the sea's level, at least two blocks from the water. */
	private boolean sand(CityWorldGenerator generator, int x, int z) {
		if (x < 1 || z < 1 || x > 14 || z > 14 || blockYs.getBlockY(x, z) != generator.seaLevel)
			return false;
		for (int a = -2; a <= 2; a++)
			for (int b = -2; b <= 2; b++)
				if (wet(generator, x + a, z + b))
					return false;
		return true;
	}

	private static final Material[][] STRIPES = { { Material.RED_WOOL, Material.WHITE_WOOL },
			{ Material.BLUE_WOOL, Material.WHITE_WOOL }, { Material.YELLOW_WOOL, Material.ORANGE_WOOL },
			{ Material.LIME_WOOL, Material.WHITE_WOOL } };
	private static final Material[] TOWELS = { Material.RED_CARPET, Material.BLUE_CARPET, Material.YELLOW_CARPET,
			Material.LIGHT_BLUE_CARPET, Material.PINK_CARPET };

	private void dressBeach(CityWorldGenerator generator, RealBlocks chunk) {
		int floor = generator.seaLevel;
		// a lifeguard tower: four legs, a platform, a rail, a red roof; on the first sand near the water that fits
		boolean tower = false;
		for (int x = 2; x <= 12 && !tower; x++)
			for (int z = 2; z <= 12 && !tower; z++) {
				boolean fits = true, nearWater = false;
				for (int a = 0; a <= 2 && fits; a++)
					for (int b = 0; b <= 2 && fits; b++)
						fits = blockYs.getBlockY(x + a, z + b) == floor && !wet(generator, x + a, z + b);
				for (int a = -2; a <= 4 && fits && !nearWater; a++)
					for (int b = -2; b <= 4 && !nearWater; b++)
						nearWater = wet(generator, x + a, z + b);
				if (!fits || !nearWater)
					continue;
				tower = true;
				for (int[] leg : new int[][] { { 0, 0 }, { 2, 0 }, { 0, 2 }, { 2, 2 } })
					chunk.setBlocks(x + leg[0], x + leg[0] + 1, floor + 1, floor + 4, z + leg[1], z + leg[1] + 1,
							Material.SPRUCE_LOG);
				chunk.setBlocks(x, x + 3, floor + 4, floor + 5, z, z + 3, Material.SPRUCE_PLANKS);
				for (int a = 0; a <= 2; a++)
					for (int b = 0; b <= 2; b++)
						if (a != 1 || b != 1)
							chunk.setBlock(x + a, floor + 5, z + b, Material.SPRUCE_FENCE);
				chunk.setBlocks(x, x + 3, floor + 7, floor + 8, z, z + 3, Material.RED_WOOL);
				for (int[] post : new int[][] { { 0, 0 }, { 2, 0 }, { 0, 2 }, { 2, 2 } })
					chunk.setBlock(x + post[0], floor + 6, z + post[1], Material.SPRUCE_FENCE);
			}
		// umbrellas with towels, here and there on the sand
		for (int x = 1; x <= 14; x++)
			for (int z = 1; z <= 14; z++) {
				if (!sand(generator, x, z) || !chunk.isEmpty(x, floor + 1, z))
					continue;
				long h = (getChunkX() * 73856093L) ^ (getChunkZ() * 19349663L) ^ (x * 83492791L + z);
				h = (h ^ (h >>> 29)) * 0xBF58476D1CE4E5B9L;
				if (Math.floorMod(h >>> 9, 23) != 0)
					continue;
				Material[] stripe = STRIPES[(int) Math.floorMod(h >>> 20, (long) STRIPES.length)];
				chunk.setBlocks(x, x + 1, floor + 1, floor + 3, z, z + 1, Material.SPRUCE_FENCE);
				for (int a = -1; a <= 1; a++)
					for (int b = -1; b <= 1; b++) {
						int ux = x + a, uz = z + b;
						if (ux >= 0 && uz >= 0 && ux < 16 && uz < 16 && chunk.isEmpty(ux, floor + 3, uz))
							chunk.setBlock(ux, floor + 3, uz, stripe[(a + b + 2) % 2]);
					}
				int tx = x + 1;
				if (tx < 16 && chunk.isEmpty(tx, floor + 1, z) && blockYs.getBlockY(tx, z) == floor)
					chunk.setBlock(tx, floor + 1, z, TOWELS[(int) Math.floorMod(h >>> 30, (long) TOWELS.length)]);
			}
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		int deck = generator.streetLevel;
		if (kind == Kind.BEACH) {
			// a boardwalk along the top of the sand, where the street-level ground meets the beach
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++)
					if (blockYs.getBlockY(x, z) == deck && besideSand(generator, x, z))
						chunk.setBlock(x, deck, z, Material.SPRUCE_PLANKS);
			return;
		}
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				if (!quay(generator, x, z))
					continue;
				int ground = blockYs.getBlockY(x, z);
				chunk.clearBlocks(x, x + 1, deck + 1, deck + 8, z, z + 1);
				int[] water = waterBeside(generator, x, z);
				if (water != null) {
					// the quay wall, down into the bed it stands on; a coping, and a parapet unless something
					// leaves the quay from here
					chunk.setBlocks(x, x + 1, Math.min(ground, generator.seaLevel) - 3, deck, z, z + 1, Material.STONE_BRICKS);
					chunk.setBlocks(x, x + 1, deck, deck + 1, z, z + 1, Material.CHISELED_STONE_BRICKS);
					if (!(kind != Kind.PROMENADE && feature(generator, x, z)))
						chunk.setBlocks(x, x + 1, deck + 1, deck + 2, z, z + 1, Material.STONE_BRICKS);
				} else {
					// the quay itself: firm ground up to a paved deck
					if (ground < deck)
						chunk.setBlocks(x, x + 1, ground + 1, deck, z, z + 1, Material.STONE);
					chunk.setBlocks(x, x + 1, deck, deck + 1, z, z + 1, Material.SMOOTH_STONE);
				}
			}
		// a lighthouse on the quay: white and red bands, a roof, a door
		int[] light = lighthouseAt(generator);
		if (light != null) {
			for (int y = deck + 1; y <= deck + TOWER; y++) {
				Material band = ((y - deck - 1) / 3) % 2 == 0 ? Material.WHITE_CONCRETE : Material.RED_CONCRETE;
				for (int a = -1; a <= 1; a++)
					for (int b = -1; b <= 1; b++)
						if (a != 0 || b != 0)
							chunk.setBlock(light[0] + a, y, light[1] + b, band);
			}
			chunk.setBlocks(light[0] - 1, light[0] + 2, deck + TOWER + 1, deck + TOWER + 2, light[1] - 1, light[1] + 2,
					Material.SMOOTH_STONE);
			chunk.setBlocks(light[0] - 1, light[0] + 2, deck + TOWER + 5, deck + TOWER + 6, light[1] - 1, light[1] + 2,
					Material.SMOOTH_STONE);
			chunk.setBlock(light[0], deck + TOWER + 2, light[1], Material.SEA_LANTERN);
			chunk.clearBlocks(light[0], light[0] + 1, deck + 1, deck + 3, light[1] - 1, light[1]);
		}
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		int deck = generator.streetLevel;
		Material lantern = Material.of(net.minecraft.world.level.block.Blocks.LANTERN);
		Odds odds = chunkOdds;
		if (kind == Kind.BEACH) {
			dressBeach(generator, chunk);
			return;
		}
		// the lighthouse's lamp room: glass round the light
		int[] light = lighthouseAt(generator);
		if (light != null)
			for (int y = deck + TOWER + 2; y <= deck + TOWER + 4; y++)
				for (int a = -1; a <= 1; a++)
					for (int b = -1; b <= 1; b++)
						if (a != 0 || b != 0)
							chunk.setBlock(light[0] + a, y, light[1] + b, Material.GLASS);
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				if (!quay(generator, x, z))
					continue;
				int[] water = waterBeside(generator, x, z);
				if (water == null) {
					// cargo waiting on a loading quay, back from the edge
					if (kind == Kind.LOADING && odds.playOdds(0.08) && chunk.isEmpty(x, deck + 1, z)) {
						double what = odds.getRandomDouble();
						Material cargo = what < 0.5 ? Material.BARREL : what < 0.8 ? Material.OAK_LOG : Material.HAY_BLOCK;
						chunk.setBlocks(x, x + 1, deck + 1, deck + 2 + odds.getRandomInt(2), z, z + 1, cargo);
					}
					continue;
				}
				// lanterns along the parapet
				if (Math.floorMod(x * 3 + z * 5, 11) == 0 && !feature(generator, x, z))
					chunk.setBlock(x, deck + 2, z, lantern);
				if (!feature(generator, x, z))
					continue;
				int dx = water[0], dz = water[1];
				if (kind == Kind.MOORING) {
					// a jetty: planks out over the water at the deck, pilings at its end, a bollard
					int len = 0;
					for (int i = 1; i <= (coast ? 10 : 4); i++) {
						int jx = x + dx * i, jz = z + dz * i;
						if (jx < 0 || jz < 0 || jx > 15 || jz > 15 || !wet(generator, jx, jz))
							break;
						chunk.setBlock(jx, deck, jz, Material.SPRUCE_PLANKS);
						len = i;
					}
					if (len > 0) {
						int ex = x + dx * len, ez = z + dz * len;
						for (int y = generator.seaLevel - 6; y < deck; y++)
							if (chunk.isWaterAt(ex, y, ez) || chunk.isEmpty(ex, y, ez))
								chunk.setBlock(ex, y, ez, Material.SPRUCE_LOG);
						chunk.setBlock(ex, deck + 1, ez, Material.SPRUCE_FENCE);
					}
				} else if (kind == Kind.LOADING) {
					// a timber derrick at the edge, its arm out over the water
					chunk.setBlocks(x, x + 1, deck + 1, deck + 7, z, z + 1, Material.SPRUCE_LOG);
					for (int i = 0; i <= 2; i++) {
						int ax = x + dx * i, az = z + dz * i;
						if (ax >= 0 && az >= 0 && ax <= 15 && az <= 15)
							chunk.setBlock(ax, deck + 7, az, Material.SPRUCE_PLANKS);
					}
					int hx = x + dx * 2, hz = z + dz * 2;
					if (hx >= 0 && hz >= 0 && hx <= 15 && hz <= 15) {
						chunk.setBlock(hx, deck + 6, hz, Material.SPRUCE_FENCE);
						chunk.setBlock(hx, deck + 5, hz, lantern);
					}
				}
			}
	}
}
