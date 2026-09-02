package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class MeetingForFourRoom extends MeetingRoom {

	public MeetingForFourRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {

		Material tableLeg = getTableLeg(odds);
		Material tableTop = getTableTop(odds);

		switch (sideWithWall) {
		default:
		case NORTH:
		case SOUTH:
			drawSeat(chunk, odds, x, y, z, BlockFace.WEST);
			drawSeat(chunk, odds, x, y, z + 2, BlockFace.WEST);

			drawTable(generator, chunk, odds, x + 1, x + 2, y, z, z + 3);

			drawSeat(chunk, odds, x + 2, y, z, BlockFace.EAST);
			drawSeat(chunk, odds, x + 2, y, z + 2, BlockFace.EAST);
			break;
		case WEST:
		case EAST:
			drawSeat(chunk, odds, x, y, z, BlockFace.NORTH);
			drawSeat(chunk, odds, x + 2, y, z, BlockFace.NORTH);

			drawTable(generator, chunk, odds, x, x + 3, y, z + 1, z + 2);

			drawSeat(chunk, odds, x, y, z + 2, BlockFace.SOUTH);
			drawSeat(chunk, odds, x + 2, y, z + 2, BlockFace.SOUTH);
			break;
		}
	}

}
