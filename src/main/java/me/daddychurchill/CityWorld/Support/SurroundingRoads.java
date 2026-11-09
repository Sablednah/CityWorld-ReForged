package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;

public final class SurroundingRoads extends Surroundings {

	private final boolean[][] roads;
	/** The sides a highway leaves this chunk from ({@code Highways.EXIT_*} bits): each counts as a road there. */
	public final int highwayExits;

	public SurroundingRoads(PlatMap platmap, int platX, int platZ) {
		this(platmap, platX, platZ, false);
	}

	/**
	 * With {@code highways}, a side a highway leaves from counts as a road: the street runs on to that edge (and is
	 * signed for it) where it would have ended. Only the road's own drawing asks: finding a city's gates judges its
	 * neighbours' sites, which a map or a command on the server thread must not wait for.
	 */
	public SurroundingRoads(PlatMap platmap, int platX, int platZ, boolean highways) {
		super();
		roads = new boolean[3][3];

		// calculate neighbors
		updateNeighbors(platmap, platX, platZ);

		highwayExits = !highways || platmap.generator == null ? 0
				: platmap.generator.highwayExitsAt(platmap.originX + platX, platmap.originZ + platZ);
		if ((highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_NORTH) != 0)
			roads[1][0] = true;
		if ((highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_SOUTH) != 0)
			roads[1][2] = true;
		if ((highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_WEST) != 0)
			roads[0][1] = true;
		if ((highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_EAST) != 0)
			roads[2][1] = true;
	}

	/** The side a highway leaves from, or null. */
	public me.daddychurchill.CityWorld.compat.BlockFace highwayExit() {
		return (highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_NORTH) != 0 ? me.daddychurchill.CityWorld.compat.BlockFace.NORTH
				: (highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_SOUTH) != 0 ? me.daddychurchill.CityWorld.compat.BlockFace.SOUTH
				: (highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_WEST) != 0 ? me.daddychurchill.CityWorld.compat.BlockFace.WEST
				: (highwayExits & me.daddychurchill.CityWorld.worldgen.Highways.EXIT_EAST) != 0 ? me.daddychurchill.CityWorld.compat.BlockFace.EAST : null;
	}

	private void updateNeighbors(PlatMap platmap, int platX, int platZ) {
		PlatLot platlot = platmap.getLot(platX, platZ);

		// get a list of qualified neighbors
		PlatLot[][] neighborChunks = platlot.getNeighborPlatLots(platmap, platX, platZ, false);
		for (int x = 0; x < 3; x++) {
			for (int z = 0; z < 3; z++) {
				PlatLot neighbor = neighborChunks[x][z];

				// beyond the edge
				if (neighbor == null) {
					roads[x][z] = platX == RoadLot.PlatMapRoadInset - 1 || platZ == RoadLot.PlatMapRoadInset - 1
							|| platX == PlatMap.Width - RoadLot.PlatMapRoadInset
							|| platZ == PlatMap.Width - RoadLot.PlatMapRoadInset;

					// is connected in some way?
				} else {
					roads[x][z] = platlot.isConnected(neighbor);
				}
			}
		}
	}

	// adjacent roads?
	public boolean adjacentRoads() {
		return adjacentNeighbors();
	}

	@Override
	public boolean toCenter() {
		return true;
	}

	@Override
	public boolean toNorth() {
		return roads[1][0];
	}

	@Override
	public boolean toSouth() {
		return roads[1][2];
	}

	@Override
	public boolean toWest() {
		return roads[0][1];
	}

	@Override
	public boolean toEast() {
		return roads[2][1];
	}

	@Override
	public boolean toNorthWest() {
		return true;
	}

	@Override
	public boolean toNorthEast() {
		return true;
	}

	@Override
	public boolean toSouthWest() {
		return true;
	}

	@Override
	public boolean toSouthEast() {
		return true;
	}
}
