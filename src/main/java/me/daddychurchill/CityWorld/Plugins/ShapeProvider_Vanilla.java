package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Context.Vanilla.VanillaNatureContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.worldgen.CitySites;

/**
 * A vanilla world with cities in it ({@code "terrain": "vanilla"}): vanilla's terrain everywhere, and a city only
 * on the patches {@link CitySites} picks.
 *
 * <p><b>This provider shapes nothing.</b> Every chunk is filled by a real vanilla overworld generator before
 * CityWorld is asked (see {@code CityWorldChunkGenerator.fillFromNoise}), and the chunk generator itself brings
 * the ground of a city patch to the city's level and eases the ring around it back into the hills. What this does
 * is tell the planner where it may build, and the planner's one rule does the rest: a chunk is buildable only
 * where every sampled column stands exactly at street level. So it answers "street level" for a chunk of a city
 * patch and "one above" for everything else, and never looks at the terrain at all — which is also why planning
 * here costs nothing that an ordinary CityWorld does not pay.
 *
 * <p>The answer is always CityWorld's ONE street level, whatever height the city really stands at: the plan is
 * drawn there and lifted to the site's own level at the block seam ({@code InitialBlocks.yShift},
 * {@code worldgen.ShiftedRegion}).
 *
 * <p>Underground the world is vanilla's — its caves, ores and aquifers — so the shafts, caves and mines CityWorld
 * would dig for itself are all answered "no". Basements and sewers are the lots' own and stay.
 */
public class ShapeProvider_Vanilla extends ShapeProvider_Normal {

	private final CityWorldGenerator generator;

	public ShapeProvider_Vanilla(CityWorldGenerator generator, Odds odds) {
		super(generator, odds);
		this.generator = generator;
	}

	private static CitySites sites(CityWorldGenerator generator) {
		CitySites sites = generator.citySites;
		// Loud, because platmaps are cached for the life of the world: planning one against "no cities" would
		// silently give that region none forever.
		if (sites == null)
			throw new IllegalStateException("CityWorld: a vanilla-terrain world was asked for a plan before its city sites were bound");
		return sites;
	}

	@Override
	public String getCollectionName() {
		return "Vanilla";
	}

	@Override
	protected synchronized void allocateContexts(CityWorldGenerator generator) {
		if (!contextInitialized) {
			super.allocateContexts(generator);
			natureContext = new VanillaNatureContext(generator);
		}
	}

	/**
	 * Street level inside a city patch, one above it everywhere else: "build here" and "do not". A chunk the river
	 * runs through is reported at the water's surface, in the city's own frame of reference (the city is drawn at
	 * street level and lifted; the river is not), so the planner reads it as sea: no lot, and a road reaches the
	 * far bank as one of CityWorld's bridges with its piers sunk to the bed.
	 */
	@Override
	public double findPerciseY(CityWorldGenerator generator, int blockX, int blockZ) {
		CitySites sites = sites(generator);
		int chunkX = blockX >> 4, chunkZ = blockZ >> 4;
		if (!sites.isCityChunk(chunkX, chunkZ))
			return getStreetLevel() + 1;
		if (sites.isRiverChunk(chunkX, chunkZ)) {
			CitySites.Site site = sites.siteAt(blockX, blockZ);
			return sites.seaLevel() - 1 - (site == null ? 0 : sites.shift(site));
		}
		return getStreetLevel();
	}

	/**
	 * Which district a platmap is, by how far it lies from the middle of its city: towers in the centre, then the
	 * working town, then houses, and fields at the rim. The overworld's ladder grades by how much of the platmap
	 * is wild, which here would only measure how the disc happens to cut the platmap grid.
	 */
	@Override
	public DataContext getContext(PlatMap platmap) {
		int centreX = (platmap.originX + PlatMap.Width / 2) * 16, centreZ = (platmap.originZ + PlatMap.Width / 2) * 16;
		CitySites.Site site = sites(platmap.generator).siteAt(centreX, centreZ);
		if (site == null)
			return natureContext;
		double dx = centreX - site.centreX(), dz = centreZ - site.centreZ();
		double out = Math.sqrt(dx * dx + dz * dz) / site.radius();
		Odds odds = platmap.getOddsGenerator();
		var settings = platmap.generator.getSettings();
		// the platmap the centre falls in is always downtown, however the disc sits on the platmap grid
		boolean holdsCentre = Math.abs(dx) <= PlatMap.Width * 8 && Math.abs(dz) <= PlatMap.Width * 8;
		if (holdsCentre || out < 0.35)
			return odds.playOdds(Odds.oddsUnlikely) ? parkContext : highriseContext;
		if (out < 0.80) {
			double roll = odds.getRandomDouble();
			if (roll < 0.35)
				return midriseContext;
			if (roll < 0.50 && settings.includeMunicipalities)
				return municipalContext;
			if (roll < 0.62)
				return constructionContext;
			if (roll < 0.78 && settings.includeIndustrialSectors)
				return industrialContext;
			return lowriseContext;
		}
		if (out < 1.15) {
			double roll = odds.getRandomDouble();
			if (roll < 0.55)
				return neighborhoodContext;
			if (roll < 0.80 && settings.includeFarms)
				return farmContext;
			return lowriseContext;
		}
		return settings.includeFarms ? farmContext : neighborhoodContext;
	}

	/**
	 * No streets, no city: a platmap whose roads were all reclaimed keeps no buildings either. Otherwise its riverside
	 * — every city chunk beside the river, kept from the buildings by {@code VanillaNatureContext} — becomes what
	 * {@code Plats.River.Waterside} chooses for its district, as on CityWorld's own land.
	 */
	@Override
	protected void validateLots(CityWorldGenerator generator, PlatMap platmap) {
		boolean streets = false;
		for (int x = 0; x < PlatMap.Width && !streets; x++)
			for (int z = 0; z < PlatMap.Width && !streets; z++)
				streets = platmap.isExistingRoad(x, z);
		if (streets) {
			for (int x = 0; x < PlatMap.Width; x++)
				for (int z = 0; z < PlatMap.Width; z++) {
					int cx = platmap.originX + x, cz = platmap.originZ + z;
					if (!(platmap.getLot(x, z) instanceof me.daddychurchill.CityWorld.Plats.NatureLot)
							|| !me.daddychurchill.CityWorld.Plats.Vanilla.ShorelineLot.belongsAt(generator.citySites, cx, cz))
						continue;
					var district = cityBeside(platmap, x, z) ? platmap.context : natureContext;
					var kind = me.daddychurchill.CityWorld.Plats.River.Waterside.choose(district, false,
							getMicroOddsGeneratorAt(cx, cz));
					if (kind != me.daddychurchill.CityWorld.Plats.River.Waterside.Kind.NATURAL)
						platmap.setLot(x, z, new me.daddychurchill.CityWorld.Plats.Vanilla.ShorelineLot(platmap, cx, cz, kind));
				}
			return;
		}
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++)
				if (!platmap.isEmptyLot(x, z) && !platmap.isNaturalLot(x, z))
					platmap.recycleLot(x, z);
	}

	/** A river channel holds water and bridges, nothing else (see {@code ShapeProvider.refusesLotAt}). */
	@Override
	public boolean refusesLotAt(int chunkX, int chunkZ) {
		return sites(generator).isChannelChunk(chunkX, chunkZ);
	}

	/** The wild between cities is never planned: a platmap no city's disc or ring can reach stays all-null. */
	@Override
	public boolean plansNothingAt(int originX, int originZ) {
		return !sites(generator).touches(originX * 16, originZ * 16, PlatMap.Width * 16);
	}

	/** A city's streets end at its edge; the next city is a long walk away. */
	@Override
	public boolean keepsIsolatedRoads() {
		return true;
	}

	/**
	 * Two road-grid steps (five chunks each): a river between two intersections, or one running over an
	 * intersection. Beyond the patch the land is never reported as water, so no bridge sets out into the wild.
	 */
	@Override
	public int getMaxBridgeReach() {
		return 10;
	}

	/**
	 * Which way a bridge may run here: across the river. Upstream picks it from a noise, which over the sea is
	 * as good as anything but here forbids half of all crossings (a north-south river wants east-west bridges).
	 * The river's direction is read from where its biome lies within {@link #RIVER_LOOK} blocks: spread wider
	 * east-west than north-south means it runs east-west, and bridges go north-south ({@code true}). No river
	 * near: upstream's noise, which nothing here will ever ask about.
	 */
	@Override
	public boolean getBridgePolarityAt(double blockX, double blockZ) {
		CitySites sites = sites(generator);
		int x0 = (int) blockX, z0 = (int) blockZ, n = 0;
		double sumX = 0, sumZ = 0, sumXX = 0, sumZZ = 0;
		for (int dx = -RIVER_LOOK; dx <= RIVER_LOOK; dx += 16)
			for (int dz = -RIVER_LOOK; dz <= RIVER_LOOK; dz += 16)
				if (sites.isRiverColumn(x0 + dx, z0 + dz)) {
					n++;
					sumX += dx;
					sumZ += dz;
					sumXX += (double) dx * dx;
					sumZZ += (double) dz * dz;
				}
		if (n < 3)
			return super.getBridgePolarityAt(blockX, blockZ);
		double varX = sumXX / n - (sumX / n) * (sumX / n), varZ = sumZZ / n - (sumZ / n) * (sumZ / n);
		return varX >= varZ;
	}

	private static final int RIVER_LOOK = 96;

	@Override
	public boolean supportsSubways() {
		return false; // tunnels run under nature lots too, and here those draw nothing
	}

	/**
	 * The ground under a built lot gets CityWorld's own surface: the chunk generator has already brought vanilla's
	 * stone to the city's level, and vanilla's surface rules are not run on a built chunk (they would turf every
	 * stone roof). A nature lot is left exactly as vanilla and the blend made it.
	 */
	@Override
	public void preGenerateChunk(CityWorldGenerator generator, PlatLot lot, InitialBlocks chunk, BiomeGrid biomes,
			AbstractCachedYs blockYs) {
		if (lot.style == PlatLot.LotStyle.NATURE)
			return;
		// a bridge over the river: the river below is vanilla's, and a bed laid here hangs over the water
		if (sites(generator).isRiverChunk(chunk.sectionX, chunk.sectionZ))
			return;
		OreProvider ores = generator.oreProvider;
		int street = getStreetLevel();
		for (int x = 0; x < chunk.width; x++)
			for (int z = 0; z < chunk.width; z++) {
				chunk.setBlocks(x, street - 3, street, z, ores.subsurfaceMaterial);
				chunk.setBlock(x, street, z, ores.surfaceMaterial);
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
