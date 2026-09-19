package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.FactoryBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.GasometerLot;
import me.daddychurchill.CityWorld.Plats.Urban.SiloLot;
import me.daddychurchill.CityWorld.Plats.Urban.StorageLot;
import me.daddychurchill.CityWorld.Plats.Urban.WarehouseBuildingLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

public class IndustrialContext extends UrbanContext {

	public IndustrialContext(CityWorldGenerator generator) {
		super(generator);

		oddsOfParks = Odds.oddsUnlikely;
		oddsOfIsolatedLots = Odds.oddsPrettyUnlikely;
		oddsOfIdenticalBuildingHeights = Odds.oddsAlwaysGoingToHappen;
		oddsOfSimilarBuildingHeights = Odds.oddsExtremelyLikely;
		oddsOfSimilarBuildingRounding = Odds.oddsNeverGoingToHappen;
		oddsOfUnfinishedBuildings = Odds.oddsNeverGoingToHappen;
		oddsOfOnlyUnfinishedBasements = Odds.oddsNeverGoingToHappen;
		// oddsOfMissingRoad = oddsNeverGoingToHappen;
		oddsOfRoundAbouts = Odds.oddsUnlikely;

		oddsOfStairWallMaterialIsWallMaterial = Odds.oddsExtremelyLikely;
		oddsOfBuildingWallInset = Odds.oddsExtremelyLikely;
		oddsOfFlatWalledBuildings = Odds.oddsExtremelyLikely;
		oddsOfSimilarInsetBuildings = Odds.oddsExtremelyLikely;
		rangeOfWallInset = 2;

		setSchematicFamily(SchematicFamily.INDUSTRIAL, 6); // factories/warehouses run large

		maximumFloorsAbove = 2;
		maximumFloorsBelow = 1;
	}

	@Override
	protected PlatLot getPark(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ,
			int waterDepth) {
		if (odds.playOdds(Odds.oddsLikely))
			return new StorageLot(platmap, chunkX, chunkZ);
		else
			return new FactoryBuildingLot(platmap, chunkX, chunkZ);
	}

	@Override
	protected PlatLot getBuilding(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ) {
		// silos took over from the silo schematics (owner, 2026-09-19); the backfill's flood-fill
		// makes batteries of them just as it makes bigger factories
		if (odds.playOdds(Odds.oddsVeryUnlikely)) // was one in five: "waaay too common" (owner)
			return new SiloLot(platmap, chunkX, chunkZ);
		else if (odds.playOdds(Odds.oddsSomewhatLikely))
			return new WarehouseBuildingLot(platmap, chunkX, chunkZ);
		else
			return new FactoryBuildingLot(platmap, chunkX, chunkZ);
	}

	/** A gasometer in one industrial platmap in four — a rare large lot (owner, 2026-09-19), claimed
	 *  before the backfill the way the park context claims its big zoos and domes. */
	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		Odds odds = platmap.getOddsGenerator();
		if (generator.getSettings().includeBuildings && odds.playOdds(Odds.oddsSomewhatUnlikely + Odds.oddsPrettyUnlikely)) {
			int size = odds.playOdds(Odds.oddsSomewhatLikely) ? 3 : 2;
			int frameHeight = size == 3 ? 30 + odds.getRandomInt(19) : 24 + odds.getRandomInt(13);
			int fill = odds.getRandomInt(frameHeight); // the bell stands anywhere from empty to full
			int frameStyle = odds.getRandomInt(4);
			long paveSeed = odds.getRandomLong();
			for (int tries = 0; tries < 20; tries++) {
				int px = odds.getRandomInt(PlatMap.Width - size + 1);
				int pz = odds.getRandomInt(PlatMap.Width - size + 1);
				if (platmap.isEmptyLots(px, pz, size, size)) {
					for (int x = 0; x < size; x++)
						for (int z = 0; z < size; z++)
							platmap.setLot(px + x, pz + z, new GasometerLot(platmap, platmap.originX + px + x,
									platmap.originZ + pz + z, size, x, z, frameHeight, fill, frameStyle, paveSeed));
					break;
				}
			}
		}
		super.populateMap(generator, platmap);
	}
}
