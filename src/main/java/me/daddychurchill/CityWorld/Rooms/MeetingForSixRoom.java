package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class MeetingForSixRoom extends MeetingForFourRoom {

	public MeetingForSixRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {

		super.drawFixture(generator, chunk, odds, floor, x, y, z, width, height, depth, sideWithWall, materialWall,
				materialGlass);

		switch (sideWithWall) {
		default:
		case NORTH:
		case SOUTH:
			drawSeat(chunk, odds, x, y, z + 1, BlockFace.WEST);
			drawSeat(chunk, odds, x + 2, y, z + 1, BlockFace.EAST);
			break;
		case WEST:
		case EAST:
			drawSeat(chunk, odds, x + 1, y, z, BlockFace.NORTH);
			drawSeat(chunk, odds, x + 1, y, z + 2, BlockFace.SOUTH);
			break;
		}
	}

}
