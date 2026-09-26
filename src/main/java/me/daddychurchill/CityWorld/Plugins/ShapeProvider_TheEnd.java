package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Context.TheEnd.EndNatureContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;
import me.daddychurchill.CityWorld.worldgen.EndTerrain;

/**
 * The End: vanilla's islands, with the city laid on the flat of them.
 *
 * <p><b>This provider shapes nothing.</b> Every End chunk is filled by a real vanilla End generator before
 * CityWorld is asked (see {@code CityWorldChunkGenerator.fillFromNoise}), so the islands — outline, underside,
 * void between them — are vanilla's own. What this does is <em>report</em> that terrain to the planner
 * ({@link EndTerrain}, which reads the same noise without generating anything), and the planner's one rule does
 * the rest: a chunk is buildable only where every sampled column stands exactly at street level. Roads, and so
 * cities, therefore exist only on island tops; everywhere else is an {@code EndNatureLot}, which draws nothing.
 *
 * <p><b>The terrace.</b> Vanilla's outer islands are strikingly flat — surveyed over 10,000 chunks (2026-09-17),
 * 57% of fully solid chunks top out within y 57..60 and 76% vary by under 6 blocks inside the chunk — but "flat" is
 * not "exactly street level", which is what the planner needs. So island tops within {@link #TERRACE} blocks of
 * street level are planed to it, and the next {@link #BLEND} blocks ease back to the natural height so the step is
 * a slope rather than a cliff. It is a rule of the terrain, not of a lot: it applies to every column of every
 * chunk alike, which is why no chunk-square edge can appear — the first End built the city on its own slab, and
 * chunk-square slabs are exactly what that looked like. Street level 59 with +/-4 fits 66% of solid chunks.
 *
 * <p>The first End was the overworld's twin and inherited its underground: mines, sewers and caves hanging out of
 * the bottom of islands a few dozen blocks thick. {@code CityWorldSettings.applyEndRealm} turns those off; this
 * answers "no" to every shaft and cave question besides.
 */
public class ShapeProvider_TheEnd extends ShapeProvider_Normal {

	@Override
	public boolean supportsSubways() {
		return false; // see ShapeProvider.supportsSubways
	}

	/** The top block of buildable ground. Vanilla's islands cluster at y 57..60 (top block). */
	public final static int STREET_LEVEL = 59;
	/** Island tops within this many blocks of street level are planed flat to it. */
	private final static int TERRACE = 4;
	/** ...and the next this-many blocks ease from the terrace back to the natural height. */
	private final static int BLEND = 4;

	/**
	 * Where the End is settled at all. A slow noise field splits the outer islands into city country and wild
	 * country (owner, 2026-09-17: "a bit TOO much... it can be as much as half what's covered now" — and a biome
	 * mod's End biomes had nowhere to show). By region rather than by thinning every city, so a district is still a district and the wild stretches
	 * are whole islands' worth, untouched down to the terrace: outside city country the ground is vanilla's exactly.
	 */
	private final SimplexNoiseGenerator settledShape;
	/**
	 * Regions a few platmaps across. At 1/384 the patches were smaller than the road grid could use: roads need
	 * buildable intersections five chunks apart with a way out of the platmap, so they died off and left
	 * buildings with no streets (measured: 88 road lots to 630 buildings, 12% coverage instead of ~21%).
	 */
	private final static double SETTLED_SCALE = 1.0 / 1100.0;
	/**
	 * City country is where the field is above this. Measured over 200x200 chunks (seed 8675309), share of island
	 * chunks built on, with island roads kept: everything settled 28%, -0.25 16%, 0.0 9% — so -0.2 is about half
	 * of what an unthinned End builds. {@code -Dcityworld.end.settled=<n>} overrides it for a tuning run with survey:end.
	 */
	private final static double SETTLED_THRESHOLD = Double.parseDouble(System.getProperty("cityworld.end.settled", "-0.2"));
	/** ...and the terrace fades in over this much of the field, so its edge is a slope and not a line. */
	private final static double SETTLED_FADE = 0.12;

	public ShapeProvider_TheEnd(CityWorldGenerator generator, Odds odds) {
		super(generator, odds);
		settledShape = new SimplexNoiseGenerator(generator.getWorldSeed() + 5959);
	}

	/** 0 in wild country, 1 in city country, between across the fade. */
	private double settled(int blockX, int blockZ) {
		double field = settledShape.noise(blockX * SETTLED_SCALE, blockZ * SETTLED_SCALE);
		return Math.max(0.0, Math.min(1.0, (field - SETTLED_THRESHOLD) / SETTLED_FADE));
	}

	@Override
	protected synchronized void allocateContexts(CityWorldGenerator generator) {
		if (!contextInitialized) {
			super.allocateContexts(generator);
			natureContext = new EndNatureContext(generator);
		}
	}

	@Override
	public String getCollectionName() {
		return "TheEnd";
	}

	/** No streets, no city: a platmap whose roads were all reclaimed keeps no buildings either. */
	@Override
	protected void validateLots(CityWorldGenerator generator, PlatMap platmap) {
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++)
				if (platmap.isExistingRoad(x, z))
					return;
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++)
				if (!platmap.isEmptyLot(x, z) && !platmap.isNaturalLot(x, z))
					platmap.recycleLot(x, z);
	}

	@Override
	public int getStreetLevel() {
		return STREET_LEVEL;
	}

	@Override
	public int getSeaLevel() {
		return STREET_LEVEL - 1;
	}

	private static EndTerrain terrain(CityWorldGenerator generator) {
		EndTerrain terrain = generator.endTerrain;
		// Loud, because platmaps are cached for the life of the world: planning one against "all void" would
		// silently give that region no city forever.
		if (terrain == null)
			throw new IllegalStateException("CityWorld: the End was asked for terrain before its vanilla noise was bound");
		return terrain;
	}

	/** The planned top block for a column whose vanilla top block is {@code top} (0 = void: {@link #VOID_FLOOR}). */
	static int terrace(int top) {
		if (top <= 0)
			return VOID_FLOOR;
		int delta = top - STREET_LEVEL, size = Math.abs(delta);
		if (size <= TERRACE)
			return STREET_LEVEL;
		if (size >= TERRACE + BLEND)
			return top;
		return STREET_LEVEL + Integer.signum(delta) * ((size - TERRACE) * (TERRACE + BLEND) / BLEND);
	}

	/**
	 * The dragon's zone: vanilla's 1,024-block central radius plus a chunk of margin. The chunk generator leaves
	 * it to vanilla entirely, so the planner must never see it as buildable — and the central island is flat at
	 * just the wrong height (the first probe of this provider planned a road across the exit podium).
	 */
	private static boolean inDragonZone(int blockX, int blockZ) {
		return (long) blockX * blockX + (long) blockZ * blockZ <= 1040L * 1040L;
	}

	/**
	 * What the planner is told a void column's height is. Just under "sea level", so the gap between two islands
	 * reads as a strait: a road that has land within reach on both sides crosses it as one of CityWorld's bridges
	 * — deck and rails; {@code RoadLot.placeBridgeColumn} gives it pylons only where real ground lies below — and
	 * nothing else is ever drawn there.
	 */
	private final static int VOID_FLOOR = STREET_LEVEL - 4;

	/** Rock left under the lowest basement floor, so a cellar never shows through the island's underside. */
	private final static int BASEMENT_COVER = 4;

	/**
	 * Basements go wherever the island is thick enough to hold them (owner, 2026-09-17: "I think there's space
	 * for basements in buildings, so let those in"). Judged on the thinnest column of the chunk, so a building at
	 * the rim — where the island tapers to nothing — gets a shallower cellar or none, never a box hanging out of
	 * the bottom.
	 */
	@Override
	public int getMaxBasementFloors(CityWorldGenerator generator, int chunkX, int chunkZ, int floorHeight) {
		int highestUnderside = 0;
		short[] tops = terrain(generator).chunkTops(chunkX, chunkZ);
		short[] undersides = terrain(generator).chunkUndersides(chunkX, chunkZ);
		for (int i = 0; i < 256; i++) {
			if (tops[i] <= 0)
				return 0; // part of this chunk is void
			highestUnderside = Math.max(highestUnderside, undersides[i]);
		}
		// the basement's bottom plate sits one below its lowest floor
		return Math.max(0, (STREET_LEVEL - 1 - BASEMENT_COVER - highestUnderside) / floorHeight);
	}

	@Override
	public boolean keepsIsolatedRoads() {
		return true;
	}

	/** Two road-grid steps. Bridges hop between neighbouring islands; they do not set out across the void. */
	@Override
	public int getMaxBridgeReach() {
		return 10;
	}

	/**
	 * The overworld's ladder grades a platmap by how much of it is wild — but here most "wild" is void, which
	 * says nothing about the island, and it graded nearly every island as farmland (685 farm lots in a 100x100
	 * chunk survey). So: grade by how much of the <em>island</em> is left wild, and keep to the city — wheat
	 * fields and sawmills on end stone, with no water to be had, are not an echo of anything.
	 */
	@Override
	public DataContext getContext(PlatMap platmap) {
		EndTerrain terrain = terrain(platmap.generator);
		int island = 0, wild = 0;
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++) {
				boolean land = false;
				for (short top : terrain.chunkTops(platmap.originX + x, platmap.originZ + z))
					if (top > 0) {
						land = true;
						break;
					}
				if (land) {
					island++;
					// Not isNaturalLot: that counts a lot nobody has claimed yet, and at this point in planning
					// that is every buildable one — which graded every island as untouched wilderness.
					if (!platmap.isEmptyLot(x, z) && platmap.isNaturalLot(x, z))
						wild++;
				}
			}
		if (island == 0)
			return natureContext;
		double nature = wild / (double) island;
		if (nature < 0.10)
			return platmap.getOddsGenerator().playOdds(Odds.oddsUnlikely) ? parkContext : highriseContext;
		else if (nature < 0.20)
			return constructionContext;
		else if (nature < 0.30 && platmap.generator.getSettings().includeMunicipalities)
			return municipalContext;
		else if (nature < 0.45)
			return midriseContext;
		else if (nature < 0.55 && platmap.generator.getSettings().includeIndustrialSectors)
			return industrialContext;
		else if (nature < 0.70)
			return lowriseContext;
		else if (nature < 0.90)
			return neighborhoodContext;
		else
			return natureContext;
	}

	/**
	 * The top block the ground at a column is actually brought to: the terrace in city country, vanilla's own
	 * height in wild country and the dragon's zone, and a blend of the two across the fade between them.
	 */
	private int groundTop(int blockX, int blockZ, int top) {
		if (top <= 0 || inDragonZone(blockX, blockZ))
			return top;
		double settled = settled(blockX, blockZ);
		if (settled <= 0.0)
			return top;
		return top + (int) Math.round((terrace(top) - top) * settled);
	}

	/**
	 * What the planner is told. The ground as it will be — except that outside city country a column that
	 * happens to stand at street level (vanilla's islands often do) is reported one higher, because that height
	 * is the one thing the planner reads as "build here".
	 */
	@Override
	public double findPerciseY(CityWorldGenerator generator, int blockX, int blockZ) {
		int top = terrain(generator).topAt(blockX, blockZ);
		if (top <= 0)
			return VOID_FLOOR;
		int ground = groundTop(blockX, blockZ, top);
		boolean cityCountry = !inDragonZone(blockX, blockZ) && settled(blockX, blockZ) >= 1.0;
		return ground == STREET_LEVEL && !cityCountry ? ground + 1 : ground;
	}

	/** How far, in blocks, the levelled ground of a built lot eases back into the natural island beside it. */
	private final static int APRON = 12;

	/**
	 * The ground is only ever levelled FOR something. The planner is told the terrace everywhere in city country
	 * (it has to be: what is buildable cannot depend on what gets built), but the blocks are moved only under a
	 * built lot and across a short apron around it. Levelling all of city country left plains of planed end stone
	 * wherever the plan then built nothing (owner, 2026-09-17: "whatever levels the island levels these empty
	 * regions too") — an island nobody built on must be vanilla's, untouched.
	 */
	@Override
	public void preGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, BiomeGrid biomes,
			AbstractCachedYs blockYs) {
		boolean built = lot.style != PlatLot.LotStyle.NATURE;
		boolean[][] builtBeside = new boolean[3][3];
		boolean any = built;
		if (!built)
			for (int dx = -1; dx <= 1; dx++)
				for (int dz = -1; dz <= 1; dz++) {
					int cx = chunk.sectionX + dx, cz = chunk.sectionZ + dz;
					PlatLot beside = generator.getPlatMap(cx, cz).getMapLot(cx, cz);
					builtBeside[dx + 1][dz + 1] = beside != null && beside.style != PlatLot.LotStyle.NATURE;
					any |= builtBeside[dx + 1][dz + 1];
				}
		if (!any)
			return; // wild, and nothing built beside it: the island stays exactly as vanilla made it

		short[] tops = terrain(generator).chunkTops(chunk.sectionX, chunk.sectionZ);
		Material ground = generator.oreProvider.surfaceMaterial;
		for (int x = 0; x < chunk.width; x++)
			for (int z = 0; z < chunk.width; z++) {
				int top = tops[x << 4 | z];
				if (top <= 0)
					continue; // void stays void; its "height" is only a story for the planner
				int planned = groundTop(chunk.getBlockX(x), chunk.getBlockZ(z), top);
				if (!built) {
					// ease off with distance from the nearest built chunk's edge
					int nearest = Integer.MAX_VALUE;
					for (int dx = -1; dx <= 1; dx++)
						for (int dz = -1; dz <= 1; dz++)
							if (builtBeside[dx + 1][dz + 1]) {
								int gapX = dx < 0 ? x + 1 : dx > 0 ? 16 - x : 0, gapZ = dz < 0 ? z + 1 : dz > 0 ? 16 - z : 0;
								nearest = Math.min(nearest, Math.max(gapX, gapZ));
							}
					double apron = Math.max(0.0, 1.0 - (nearest - 1) / (double) APRON);
					planned = top + (int) Math.round((planned - top) * apron);
				}
				if (planned > top)
					chunk.setBlocks(x, top + 1, planned + 1, z, ground);
				else if (planned < top)
					chunk.setBlocks(x, planned + 1, top + 1, z, Material.AIR);
			}
	}

	@Override
	public void postGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk,
			AbstractCachedYs blockYs) {
	}

	@Override
	public void preGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs) {
	}

	@Override
	public void postGenerateBlocks(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk,
			AbstractCachedYs blockYs) {
	}

	@Override
	public boolean isHorizontalNSShaft(int chunkX, int chunkY, int chunkZ) {
		return false;
	}

	@Override
	public boolean isHorizontalWEShaft(int chunkX, int chunkY, int chunkZ) {
		return false;
	}

	@Override
	public boolean isVerticalShaft(int chunkX, int chunkY, int chunkZ) {
		return false;
	}

	@Override
	public boolean notACave(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		return true;
	}

	@Override
	public boolean lavaFillAt(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		return false;
	}
}
