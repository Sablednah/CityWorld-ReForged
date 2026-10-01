package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.FinishedBuildingLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Support.Colors;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.Surroundings;

public class MuseumBuildingLot extends FinishedBuildingLot {

	public MuseumBuildingLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

		firstFloorHeight = firstFloorHeight * 5;
		height = 1;
		depth = 0;
		rounded = false;
		roofFeature = roofFeature == RoofFeature.ANTENNAS ? RoofFeature.CONDITIONERS : roofFeature;
		interiorStyle = InteriorStyle.COLUMNS_OFFICES;
	}

	@Override
	public String getInteriorDescription() {
		return "Museum exhibits";
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new MuseumBuildingLot(platmap, chunkX, chunkZ);
	}

	@Override
	public boolean makeConnected(PlatLot relative) {
		boolean result = super.makeConnected(relative);

//		// other bits
//		if (result && relative instanceof WarehouseBuildingLot) {
//			MuseumBuildingLot relativebuilding = (MuseumBuildingLot) relative;
//
//			// any other bits
//			contentStyle = relativebuilding.contentStyle;
//		}

		return result;
	}

	@Override
	protected void calculateOptions(DataContext context) {
		super.calculateOptions(context);

		// how do the walls inset?
		insetWallWE = 1;
		insetWallNS = 1;

		// what about the ceiling?
		insetCeilingWE = insetWallWE;
		insetCeilingNS = insetWallNS;

		// nudge in a bit more as we go up
		insetInsetMidAt = 1;
		insetInsetHighAt = 1;
		insetStyle = InsetStyle.STRAIGHT;
	}

	/**
	 * A museum is one tall hall: this replaces the whole interior pass (no rooms, walls or stairs are drawn), and
	 * what stands in the hall is the fossil and {@link #drawExhibits}. An exhibit-room populator once sat on this
	 * class and was never reached for that reason.
	 */
	@Override
	protected void drawInteriorParts(CityWorldGenerator generator, RealBlocks chunk, DataContext context,
			RoomProvider rooms, int floor, int floorAt, int floorHeight, int insetNS, int insetWE, boolean allowRounded,
			Material materialWall, Material materialGlass, StairWell stairLocation, Material materialStair,
			Material materialStairWall, Material materialPlatform, boolean drawStairWall, boolean drawStairs,
			boolean topFloor, boolean singleFloor, Surroundings heights) {

		// outside
		drawExteriorDoors(generator, chunk, context, floor, floorAt, floorHeight, insetNS, insetWE, allowRounded,
				materialWall, materialGlass, stairLocation, heights);

		if (singleFloor && generator.getSettings().includeBones) {

			// calculate if we should do it
			boolean placeBones = false;
			if (allowRounded) {

				// do the sides (yea this could be done tighter but it doesn't get called much)
				if (heights.toSouth()) {
					if (heights.toWest()) {
						placeBones = false;
					} else if (heights.toEast()) {
						placeBones = false;
					}
				} else if (heights.toNorth()) {
					if (heights.toWest()) {
						placeBones = false;
					} else if (heights.toEast()) {
						placeBones = false;
					}
				}
			} else
				placeBones = true;

			// ok... then do it
			if (placeBones) {
				int sidewalkLevel = getSidewalkLevel(generator);
				Colors colors = new Colors(chunkOdds);
				chunk.setBlocks(3, 13, sidewalkLevel, 3, 13, colors.getConcrete());
				generator.reportLocation("museum", "Museum", chunk);
				me.daddychurchill.CityWorld.Support.Fossils.Species species = me.daddychurchill.CityWorld.Support.Fossils
						.draw(chunk, me.daddychurchill.CityWorld.Support.Fossils.speciesAt(getChunkX(), getChunkZ()),
								sidewalkLevel + 1);

				// it looked so nice for a moment... but the moment has passed
				if (buildingsDecay(generator)) {
					destroyLot(generator, sidewalkLevel, sidewalkLevel + firstFloorHeight);

				} else {
					// the name plaque under the skull, two wide like the skeleton, and lights let into the floor
					chunk.setBlocks(7, 9, sidewalkLevel + 1, sidewalkLevel + 3, 4, 5, Material.SMOOTH_STONE);
					String[] name = generator.odonymProvider.generateFossilOdonym(generator, chunkOdds);
					chunk.setWallSign(7, sidewalkLevel + 2, 3, BlockFace.NORTH, name);
					chunk.setWallSign(8, sidewalkLevel + 2, 3, BlockFace.NORTH, "", species.title);
					for (int[] corner : new int[][] { { 3, 3 }, { 12, 3 }, { 3, 12 }, { 12, 12 } })
						chunk.setBlock(corner[0], sidewalkLevel, corner[1], Material.SEA_LANTERN);
				}
				drawExhibits(chunk, sidewalkLevel);
			}
		}
	}

	/** Where an exhibit may stand: beside the fossil's floor and along the walls, clear of the middle of each
	 *  side, where the doors are. {x, z, the way it faces}. */
	private static final Object[][] EXHIBIT_SPOTS = { { 3, 4, BlockFace.EAST }, { 3, 11, BlockFace.EAST },
			{ 12, 4, BlockFace.WEST }, { 12, 11, BlockFace.WEST }, { 4, 2, BlockFace.SOUTH },
			{ 11, 2, BlockFace.SOUTH }, { 4, 13, BlockFace.NORTH }, { 11, 13, BlockFace.NORTH } };

	/**
	 * The exhibits round the hall (owner, 2026-10-01): about two spots in three hold a podium with an artifact, an
	 * armour stand, or a shelf of artifacts (a podium where this version has no shelf block). Drawn after any
	 * decay, and only where the floor is still there and the spot is clear, so a ruined museum keeps what survived.
	 */
	private void drawExhibits(RealBlocks chunk, int sidewalkLevel) {
		int y = sidewalkLevel + 1;
		for (Object[] spot : EXHIBIT_SPOTS) {
			int x = (Integer) spot[0], z = (Integer) spot[1];
			BlockFace facing = (BlockFace) spot[2];
			if (!chunkOdds.playOdds(Odds.oddsVeryLikely))
				continue;
			int kind = chunkOdds.getRandomInt(20);
			if (chunk.isEmpty(x, sidewalkLevel, z))
				continue; // the floor went with the ruin
			if (kind < 5) {
				me.daddychurchill.CityWorld.Support.Exhibits.armour(chunk, chunkOdds, x, y, z, facing);
			} else if (kind >= 11 || !me.daddychurchill.CityWorld.Support.Exhibits.shelf(chunk, chunkOdds, x, y, z, facing)) {
				me.daddychurchill.CityWorld.Support.Exhibits.podium(chunk, chunkOdds, x, y, z);
			}
		}
	}

}
