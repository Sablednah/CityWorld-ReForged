package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.GovernmentBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.GovernmentMonumentLot;
import me.daddychurchill.CityWorld.Plats.Urban.HospitalLot;
import me.daddychurchill.CityWorld.Plats.Urban.MuseumBuildingLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

public class MunicipalContext extends UrbanContext {

	public MunicipalContext(CityWorldGenerator generator) {
		super(generator);

		oddsOfParks = Odds.oddsVeryUnlikely;
		oddsOfIsolatedLots = Odds.oddsVeryLikely;
		oddsOfIdenticalBuildingHeights = Odds.oddsAlwaysGoingToHappen;
		oddsOfSimilarBuildingHeights = Odds.oddsAlwaysGoingToHappen;
		oddsOfSimilarBuildingRounding = Odds.oddsAlwaysGoingToHappen;
		oddsOfUnfinishedBuildings = Odds.oddsExtremelyUnlikely;
		oddsOfOnlyUnfinishedBasements = Odds.oddsUnlikely;
		// oddsOfMissingRoad = oddsNeverGoingToHappen;
		oddsOfRoundAbouts = Odds.oddsVeryLikely;

		oddsOfStairWallMaterialIsWallMaterial = Odds.oddsAlwaysGoingToHappen;
		oddsOfFlatWalledBuildings = Odds.oddsAlwaysGoingToHappen;
		oddsOfSimilarInsetBuildings = Odds.oddsAlwaysGoingToHappen;
		oddsOfBuildingWallInset = Odds.oddsAlwaysGoingToHappen;
		rangeOfWallInset = 1;

		setSchematicFamily(SchematicFamily.MUNICIPAL, 9); // civic landmarks (cathedrals) run large

		maximumFloorsAbove = 5;
		maximumFloorsBelow = 2;
		minSizeOfBuilding = 3;

		oddsOfFloodFill = Odds.oddsAlwaysGoingToHappen;
		oddsOfFloodDecay = Odds.oddsAlwaysGoingToHappen;
	}

	@Override
	protected PlatLot getBuilding(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ) {
		double roll = odds.getRandomDouble();
		if (roll < Odds.oddsVeryUnlikely)
			return new GovernmentMonumentLot(platmap, chunkX, chunkZ);
		else if (roll < Odds.oddsSomewhatUnlikely)
			return new MuseumBuildingLot(platmap, chunkX, chunkZ);
		else
			return new GovernmentBuildingLot(platmap, chunkX, chunkZ);
	}

	@Override
	protected PlatLot getPark(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ,
			int waterDepth) {
		return getBuilding(generator, platmap, odds, chunkX, chunkZ);
	}

	private static final int[][] MAIN_SIZES = { { 2, 2 }, { 2, 3 }, { 3, 2 }, { 3, 3 } };

	/**
	 * A MODERN civic district occasionally becomes a hospital campus: one big sprawling multi-chunk main
	 * hospital plus a few smaller ancillary departments ("grown over the years"), claimed as a pre-pass
	 * (mirroring {@code PlatMap.placeSpecificClip}) before the normal backfill fills the rest of the
	 * district. Rare, so most civic districts are the usual government/museum buildings.
	 */
	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		if (generator.isModernStyle()) {
			Odds odds = platmap.getOddsGenerator();
			if (odds.playOdds(Odds.oddsSomewhatUnlikely))
				placeHospitalCampus(platmap, odds);
		}
		super.populateMap(generator, platmap);
	}

	private void placeHospitalCampus(PlatMap platmap, Odds odds) {
		int[] m = MAIN_SIZES[odds.getRandomInt(MAIN_SIZES.length)];
		int[] spot = findRun(platmap, m[0], m[1], odds);
		if (spot == null)
			return;
		claim(platmap, spot[0], spot[1], m[0], m[1], 3 + odds.getRandomInt(3), true); // 3-5 storey main

		int depts = 2 + odds.getRandomInt(3); // 2-4 ancillary departments in the same district
		for (int i = 0; i < depts; i++) {
			int dsx = 1, dsz = odds.flipCoin() ? 1 : 2;
			if (odds.flipCoin()) {
				int t = dsx;
				dsx = dsz;
				dsz = t;
			}
			int[] d = findRun(platmap, dsx, dsz, odds);
			if (d != null)
				claim(platmap, d[0], d[1], dsx, dsz, 1 + odds.getRandomInt(3), false); // 1-3 storey dept
		}
	}

	private void claim(PlatMap platmap, int px, int pz, int sx, int sz, int floors, boolean main) {
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++)
				platmap.setLot(px + x, pz + z, new HospitalLot(platmap, platmap.originX + px + x,
						platmap.originZ + pz + z, sx, sz, x, z, floors, main));
	}

	private int[] findRun(PlatMap platmap, int sx, int sz, Odds odds) {
		for (int tries = 0; tries < 40; tries++) {
			int px = odds.getRandomInt(PlatMap.Width - sx + 1);
			int pz = odds.getRandomInt(PlatMap.Width - sz + 1);
			if (platmap.isEmptyLots(px, pz, sx, sz))
				return new int[] { px, pz };
		}
		return null;
	}

}
