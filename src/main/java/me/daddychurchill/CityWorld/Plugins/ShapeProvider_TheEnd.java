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

	/** The top block of buildable ground. Vanilla's islands cluster at y 57..60 (top block). */
	public final static int STREET_LEVEL = 59;
	/** Island tops within this many blocks of street level are planed flat to it. */
	private final static int TERRACE = 4;
	/** ...and the next this-many blocks ease from the terrace back to the natural height. */
	private final static int BLEND = 4;

	public ShapeProvider_TheEnd(CityWorldGenerator generator, Odds odds) {
		super(generator, odds);
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
	 * — deck, rails and stub pylons, since the pylons stop at this height — and nothing else is ever drawn there.
	 */
	private final static int VOID_FLOOR = STREET_LEVEL - 4;

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

	/** The planned top block at a column: the terrace outside the dragon's zone, vanilla's own height inside. */
	private static int plannedTop(int blockX, int blockZ, int top) {
		if (!inDragonZone(blockX, blockZ))
			return terrace(top);
		// Untouched — and nudged off street level, the one height the planner reads as "build here".
		return top == STREET_LEVEL ? top + 1 : top;
	}

	@Override
	public double findPerciseY(CityWorldGenerator generator, int blockX, int blockZ) {
		return plannedTop(blockX, blockZ, terrain(generator).topAt(blockX, blockZ));
	}

	@Override
	public void preGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, BiomeGrid biomes,
			AbstractCachedYs blockYs) {
		// The island is already here. All that is left is the terrace: bring each column to its planned height.
		short[] tops = terrain(generator).chunkTops(chunk.sectionX, chunk.sectionZ);
		Material ground = generator.oreProvider.surfaceMaterial;
		for (int x = 0; x < chunk.width; x++)
			for (int z = 0; z < chunk.width; z++) {
				int top = tops[x << 4 | z];
				if (top <= 0)
					continue; // void stays void; its "height" is only a story for the planner
				int planned = inDragonZone(chunk.getBlockX(x), chunk.getBlockZ(z)) ? top : terrace(top);
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
