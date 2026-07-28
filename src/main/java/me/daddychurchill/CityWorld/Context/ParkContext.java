package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.BigBiodomeLot;
import me.daddychurchill.CityWorld.Plats.Urban.BigZooLot;
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
	 * MODERN park districts occasionally become a rare "attraction district": a clustered mix of big
	 * (multi-chunk) and small zoos and biodomes, with the rest of the district left as ordinary parkland.
	 * Claimed as a pre-pass — mirroring {@code PlatMap.placeSpecificClip} — <em>before</em> the normal
	 * backfill, which then skips the STRUCTURE chunks we took ({@code UrbanContext.populateMap} only fills
	 * empty lots). So zoos/domes are uncommon overall, but packed together into a proper zoo/botanical park
	 * when they do turn up, rather than one lonely enclosure per park.
	 */
	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		if (generator.isModernStyle()) {
			Odds odds = platmap.getOddsGenerator();
			if (odds.playOdds(Odds.oddsSomewhatUnlikely))
				placeAttractions(platmap, odds);
		}
		super.populateMap(generator, platmap);
	}

	private static final int[][] ZOO_SIZES = { { 1, 2 }, { 2, 2 }, { 2, 3 } };

	private void placeAttractions(PlatMap platmap, Odds odds) {
		int attempts = 6 + odds.getRandomInt(6); // several, clustered in this one district
		for (int i = 0; i < attempts; i++) {
			int kind = odds.getRandomInt(4); // 0 big dome, 1 big zoo, 2 small dome, 3 small zoo
			int sx, sz;
			switch (kind) {
			case 0:
				sx = sz = odds.flipCoin() ? 2 : 3; // square dome
				break;
			case 1:
				int[] d = ZOO_SIZES[odds.getRandomInt(ZOO_SIZES.length)];
				sx = d[0];
				sz = d[1];
				if (odds.flipCoin()) { // rotate the rectangle
					int t = sx;
					sx = sz;
					sz = t;
				}
				break;
			default:
				sx = sz = 1;
			}
			int[] spot = findRun(platmap, sx, sz, odds);
			if (spot != null)
				claim(platmap, spot[0], spot[1], sx, sz, kind, odds);
		}
	}

	/** A random empty sx-by-sz run of lots in the 10x10 platmap, or null. */
	private int[] findRun(PlatMap platmap, int sx, int sz, Odds odds) {
		for (int tries = 0; tries < 40; tries++) {
			int px = odds.getRandomInt(PlatMap.Width - sx + 1);
			int pz = odds.getRandomInt(PlatMap.Width - sz + 1);
			if (platmap.isEmptyLots(px, pz, sx, sz))
				return new int[] { px, pz };
		}
		return null;
	}

	/** Claim the run: one lot per chunk, each carrying its offset (x,z) within the footprint so it can draw
	 *  its own slice of the shared structure (the {@code ClipboardLot} pattern). */
	private void claim(PlatMap platmap, int px, int pz, int sx, int sz, int kind, Odds odds) {
		int biome = odds.getRandomInt(BigBiodomeLot.BIOMES);
		int theme = odds.getRandomInt(BigZooLot.THEMES);
		boolean sunken = odds.flipCoin();
		for (int x = 0; x < sx; x++)
			for (int z = 0; z < sz; z++) {
				int cx = platmap.originX + px + x, cz = platmap.originZ + pz + z;
				PlatLot lot = switch (kind) {
				case 0 -> new BigBiodomeLot(platmap, cx, cz, sx, sz, x, z, biome);
				case 1 -> new BigZooLot(platmap, cx, cz, sx, sz, x, z, theme, sunken);
				case 2 -> new BiodomeLot(platmap, cx, cz);
				default -> new ZooLot(platmap, cx, cz);
				};
				platmap.setLot(px + x, pz + z, lot);
			}
	}
}
