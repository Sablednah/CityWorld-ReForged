package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.BiodomeLot;
import me.daddychurchill.CityWorld.Plats.Urban.ZooLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

public class ParkContext extends UrbanContext {

	public ParkContext(CityWorldGenerator generator) {
		super(generator);

		oddsOfParks = Odds.oddsAlwaysGoingToHappen;
		oddsOfIsolatedLots = Odds.oddsAlwaysGoingToHappen;
		oddsOfIdenticalBuildingHeights = Odds.oddsNeverGoingToHappen;
		oddsOfSimilarBuildingHeights = Odds.oddsNeverGoingToHappen;
		oddsOfSimilarBuildingRounding = Odds.oddsNeverGoingToHappen;
		oddsOfUnfinishedBuildings = Odds.oddsNeverGoingToHappen;
		oddsOfOnlyUnfinishedBasements = Odds.oddsNeverGoingToHappen;
		// oddsOfMissingRoad = oddsNeverGoingToHappen;
		oddsOfRoundAbouts = Odds.oddsNeverGoingToHappen;

		oddsOfStairWallMaterialIsWallMaterial = Odds.oddsNeverGoingToHappen;
		oddsOfBuildingWallInset = Odds.oddsNeverGoingToHappen;
		oddsOfFlatWalledBuildings = Odds.oddsNeverGoingToHappen;
		oddsOfSimilarInsetBuildings = Odds.oddsNeverGoingToHappen;
		rangeOfWallInset = 1;

		setSchematicFamily(SchematicFamily.PARK);
	}

	/**
	 * Park districts are CityWorld's home for the two "catch-all" attractions — a themed zoo enclosure and a
	 * biome-filled biodome — so a park zone reads as a zoo / botanical park rather than plain lawn. MODERN
	 * only: both lean on modern blocks (mangrove, pale garden, the Nether/End palettes). Roughly a third of
	 * a park district's lots become one of the two; the rest stay ordinary parks so they still cluster.
	 */
	@Override
	protected PlatLot getPark(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ,
			int waterDepth) {
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN) {
			int roll = odds.getRandomInt(6);
			if (roll == 0)
				return new ZooLot(platmap, chunkX, chunkZ);
			if (roll == 1)
				return new BiodomeLot(platmap, chunkX, chunkZ);
		}
		return super.getPark(generator, platmap, odds, chunkX, chunkZ, waterDepth);
	}
}
