package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.EmptyBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.LibraryBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.OfficeBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.ParkLot;
import me.daddychurchill.CityWorld.Plats.Urban.StoreBuildingLot;
import me.daddychurchill.CityWorld.Plats.Urban.UnfinishedBuildingLot;
import me.daddychurchill.CityWorld.Plugins.ShapeProvider;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

public abstract class UrbanContext extends CivilizedContext {

	double oddsOfFloodFill = Odds.oddsVeryLikely;
	double oddsOfFloodDecay = Odds.oddsLikely;
	int minSizeOfBuilding = 1;

	UrbanContext(CityWorldGenerator generator) {
		super(generator);

		maximumFloorsAbove = 2;
		maximumFloorsBelow = 2;
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {

		// the subway station claims its chunk first: one per urban district, beside a road, so the
		// tunnels between districts have somewhere to go (Support/Subway)
		me.daddychurchill.CityWorld.Support.Subway.placeStation(generator, platmap);

		// let the user add their stuff first, then plug any remaining holes with our
		// stuff
		populateSchematics(generator, platmap);

		// random fluff!
		Odds platmapOdds = platmap.getOddsGenerator();
		ShapeProvider shapeProvider = generator.shapeProvider;
		int waterDepth = ParkLot.getWaterDepth(platmapOdds);

		// backfill with buildings and parks
		for (int x = 0; x < PlatMap.Width; x++) {
			for (int z = 0; z < PlatMap.Width; z++) {
				PlatLot current = platmap.getLot(x, z);
				if (current == null) {

					// TODO I need to come up with a more elegant way of doing this!
					if (generator.getSettings().includeBuildings) {

						// what to build?
						boolean buildPark = platmapOdds.playOdds(oddsOfParks);
						if (buildPark)
							current = getPark(generator, platmap, platmapOdds, platmap.originX + x, platmap.originZ + z,
									waterDepth);
						else
							current = getBackfillLot(generator, platmap, platmapOdds, platmap.originX + x,
									platmap.originZ + z);

						// see if the previous chunk is the same type
						PlatLot previous = null;
						if (x > 0 && current.isConnectable(platmap.getLot(x - 1, z))) {
							previous = platmap.getLot(x - 1, z);
						} else if (z > 0 && current.isConnectable(platmap.getLot(x, z - 1))) {
							previous = platmap.getLot(x, z - 1);
						}

						// if there was a similar previous one then copy it... maybe
						if (previous != null && !shapeProvider.isIsolatedLotAt(platmap.originX + x, platmap.originZ + z,
								oddsOfIsolatedLots)) {
							current.makeConnected(previous);

							// 2 by 2 at a minimum if at all possible
						} else if (!buildPark && x < PlatMap.Width - 1 && z < PlatMap.Width - 1) {
							if (minSizeOfBuilding == 1) {
								fillOutBuilding(generator, platmap, platmapOdds, oddsOfFloodFill, current, x, z + 1);
								fillOutBuilding(generator, platmap, platmapOdds, oddsOfFloodFill, current, x + 1, z);
							} else if (platmap.isEmptyLots(x, z, minSizeOfBuilding, minSizeOfBuilding)) {
								boolean madeOne = false;
								int newZ = z;
								while (platmap.inBounds(x, newZ) && platmap.isEmptyLot(x, newZ)) {
									if (fillOutBuilding(generator, platmap, platmapOdds, oddsOfFloodFill, current, x,
											newZ))
										madeOne = true;
									newZ++;
								}

								// did it, so lets not do it again
								if (madeOne)
									current = null;
							}
						}
					}

					// remember what we did
					if (current != null)
						platmap.setLot(x, z, current);
				}
			}
		}

		// validate each lot
		for (int x = 0; x < PlatMap.Width; x++) {
			for (int z = 0; z < PlatMap.Width; z++) {
				PlatLot current = platmap.getLot(x, z);
				if (current != null) {
					PlatLot replacement = current.validateLot(platmap, x, z);
					if (replacement != null)
						platmap.setLot(x, z, replacement);
				}
			}
		}
	}

	/**
	 * Extends a building into the lot at {@code x,z}; {@code true} if it took.
	 *
	 * <p>Asks about the reservation BEFORE building the lot: {@code newLike} constructs a full
	 * {@code PlatLot}, and that costs 256 columns of octave noise ({@code getCachedYs}) for a lot
	 * that {@code setLot} would then refuse.
	 */
	private boolean addToBigBuilding(CityWorldGenerator generator, PlatMap platmap, PlatLot source, int x, int z) {
		if (generator.isStructureReserved(platmap.originX + x, platmap.originZ + z))
			return false;
		PlatLot destination = source.newLike(platmap, platmap.originX + x, platmap.originZ + z);
		destination.makeConnected(source);
		return platmap.setLot(x, z, destination);
	}

	private boolean fillOutBuilding(CityWorldGenerator generator, PlatMap platmap, Odds odds, double theOdds,
			PlatLot source, int x, int z) {
		if (odds.playOdds(oddsOfFloodFill) && platmap.inBounds(x, z) && platmap.isEmptyLot(x, z)) {
			// ⚠ The flood STOPS where the lot cannot be placed. It used to ignore the result and recurse
			// anyway, and a refused lot stays empty -- so every monotone path through a refused region
			// re-entered the same chunks, each visit constructing a fresh lot (256 columns of octave
			// noise). Through a structure reservation the size of an acropolis's clearance that is tens
			// of thousands of visits: the "minute-long stall" (71.5 s on the owner's machine, 37.7 s
			// here, on the same seed's platmap 0,160), found with the stack-dumping watchdog after two
			// wrong diagnoses from the code. Reservation and carve were each under 2% of it.
			if (!addToBigBuilding(generator, platmap, source, x, z))
				return false;
			return fillOutBuilding(generator, platmap, odds, theOdds * oddsOfFloodDecay, source, x + 1, z)
					|| fillOutBuilding(generator, platmap, odds, theOdds * oddsOfFloodDecay, source, x, z + 1);
		} else
			return false;
	}

	@Override
	protected PlatLot getBackfillLot(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ) {
		if (odds.playOdds(oddsOfUnfinishedBuildings))
			return getUnfinishedBuilding(generator, platmap, odds, chunkX, chunkZ);
		else
			return getBuilding(generator, platmap, odds, chunkX, chunkZ);
	}

	protected PlatLot getPark(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ,
			int waterDepth) {
		return new ParkLot(platmap, chunkX, chunkZ, generator.connectedKeyForParks, waterDepth);
	}

	protected PlatLot getUnfinishedBuilding(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX,
			int chunkZ) {
		return new UnfinishedBuildingLot(platmap, chunkX, chunkZ);
	}

	protected PlatLot getBuilding(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ) {
		// Rebalanced (owner, 2026-09-03) from the old 6-way pick (office 50%, empty/store/library
		// 17% each): more stores, fewer libraries and empties, and apartment towers dealt out
		// directly — the upstream author's commented-out ApartmentBuildingLot, finally real.
		switch (odds.getRandomInt(12)) {
		case 1:
			return new EmptyBuildingLot(platmap, chunkX, chunkZ);
		case 2:
		case 3:
		case 4:
			return new StoreBuildingLot(platmap, chunkX, chunkZ);
		case 5:
			return new LibraryBuildingLot(platmap, chunkX, chunkZ);
		case 6:
		case 7:
			return new OfficeBuildingLot(platmap, chunkX, chunkZ, true); // apartments
		default:
			return new OfficeBuildingLot(platmap, chunkX, chunkZ);
		}
	}
}
