package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.CoverProvider.CoverageType;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.ClimateZone;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.Material;

public abstract class ConstructLot extends IsolatedLot {

	protected ConstructLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
	}

	@Override
	public boolean isPlaceableAt(CityWorldGenerator generator, int chunkX, int chunkZ) {
		return generator.getSettings().inConstructRange(chunkX, chunkZ);
	}

	// Construct lots (gravelworks/mines/oil platforms/campgrounds/etc.) carve or build during the
	// decoration pass, after the vanilla heightmap is fixed — so vanilla wild cover would float over
	// their pits and platforms. Keep MODERN's hybrid decoration off them; blendModernCover softens the
	// bare edges instead (below).
	@Override
	public boolean allowsWildDecoration() {
		return false;
	}

	@Override
	public void generateBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk, DataContext context,
			int platX, int platZ) {
		super.generateBlocks(generator, platmap, chunk, context, platX, platZ);
		blendModernCover(generator, chunk);
	}

	// natural ground a light plant tuft is allowed to sit on (kept off the industrial cobble so warmer
	// lots don't sprout grass out of the stonework)
	private final static Material[] blendableGround = { Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
			Material.PODZOL };

	private final static CoverageType[] blendTemperate = { CoverageType.GRASS, CoverageType.FERN,
			CoverageType.TALL_GRASS, CoverageType.DANDELION };
	private final static CoverageType[] blendWet = { CoverageType.FERN, CoverageType.GRASS, CoverageType.TALL_GRASS };
	private final static CoverageType[] blendDrySavanna = { CoverageType.GRASS, CoverageType.DEAD_BUSH };

	/**
	 * MODERN edge-blend for construct lots. Vanilla wild cover is kept off these (it floats over their
	 * pits), which left them ending in a hard bare line against the decorated wild next door. This lays
	 * a light, biome-appropriate cover on whatever the <em>real</em> post-construction surface turns out
	 * to be — probed live from the finished blocks, so nothing floats — to soften that seam: a snow
	 * blanket in the cold (which also settles onto the gravel piles, just like the surrounding snowy
	 * ground), a scatter of grass/ferns in temperate and wet zones, sparse dead bush on the savanna, and
	 * bare sand left bare in the desert. Deep pit floors (nothing solid near street level) are skipped.
	 */
	protected void blendModernCover(CityWorldGenerator generator, RealBlocks chunk) {
		if (generator.worldStyle != CityWorldGenerator.WorldStyle.MODERN)
			return;

		ClimateZone zone = ClimateZone.at(generator, chunkX, chunkZ);

		// Scan from just above the lot's tallest terrain (covers built platforms — radio towers, oil rigs
		// — a few blocks above the ground) down to each column's own terrain. This works at any elevation,
		// unlike the old street-level window that missed mountain lots. Anything found well below the
		// planned surface is a dug pit/shaft floor and is skipped (surfaceY >= ground - 3).
		int scanTop = getMaxTerrainY() + DataContext.FloorHeight;
		for (int x = 0; x < chunk.width; x++)
			for (int z = 0; z < chunk.width; z++) {
				int ground = getBlockY(x, z);
				int emptyY = chunk.findLastEmptyBelow(x, scanTop + 1, z, ground - 3);
				int surfaceY = emptyY - 1;
				if (emptyY <= ground - 3 || !chunk.isEmpty(x, emptyY, z))
					continue; // deep pit/shaft floor, or nothing solid — leave it
				blendCoverAt(generator, chunk, zone, x, emptyY, z, surfaceY);
			}
	}

	private void blendCoverAt(CityWorldGenerator generator, RealBlocks chunk, ClimateZone zone, int x, int y, int z,
			int surfaceY) {
		// Snow where vanilla's elevation-aware climate says it's cold enough — the same rule the
		// surrounding ground follows (snowy biomes at any height; plain cold biomes like taiga only up
		// high). That stops the lot reading as a snowy island in a snowless taiga. Snow onto the first
		// real solid block (replacing a plant, never on ice/water where a layer would be illegal).
		if (chunk.coldEnoughToSnow(x, surfaceY + 1, z)) {
			int groundY = isSnowableGround(chunk, x, surfaceY, z) ? surfaceY
					: isSnowableGround(chunk, x, surfaceY - 1, z) ? surfaceY - 1 : Integer.MIN_VALUE;
			if (groundY != Integer.MIN_VALUE && chunkOdds.playOdds(Odds.oddsExtremelyLikely))
				chunk.setBlock(x, groundY + 1, z, Material.SNOW, chunkOdds.getRandomInt(1, 2));
			return;
		}

		// desert stays bare
		if (zone.hot() && zone.dry())
			return;

		// warmer zones: only tuft on natural ground, lightly, so the edge greens up without carpeting
		if (!chunk.isOfTypes(x, surfaceY, z, blendableGround))
			return;
		CoverageType[] tufts = zone.wet() ? blendWet : (zone.temp == ClimateZone.Temp.WARM ? blendDrySavanna
				: blendTemperate);
		if (chunkOdds.playOdds(Odds.oddsSomewhatLikely))
			generator.coverProvider.generateRandomCoverage(generator, chunk, x, y, z, tufts);
	}

	// A block a snow layer can legally rest on: a solid, non-replaceable cube that isn't ice (snow on
	// ice is the illegal, cascading state). canBeReplaced() is true for plants/water/snow, so this also
	// keeps snow from landing on the grass and ferns the surface pass scattered.
	private boolean isSnowableGround(RealBlocks chunk, int x, int y, int z) {
		if (chunk.isEmpty(x, y, z) || chunk.getActualBlock(x, y, z).getBlockData().canBeReplaced())
			return false;
		return !chunk.isOfTypes(x, y, z, Material.ICE, Material.PACKED_ICE, Material.BLUE_ICE);
	}

	@Override
	public PlatLot validateLot(PlatMap platmap, int platX, int platZ) {
		return null;
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		// TODO Auto-generated method stub
		return 0;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return blockYs.getBlockY(x, z);
//		return generator.streetLevel;
	}
}
