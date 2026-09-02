package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LoungeTableRoom extends LoungeRoom {

	public LoungeTableRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material tableLeg = getTableLeg(odds);
		Material tableTop = getTableTop(odds);

		int offset;
		switch (sideWithWall) {
		default:
		case NORTH:
		case SOUTH:
			offset = odds.getRandomInt(width);
			drawTable(generator, chunk, odds, x + offset, x + 1 + offset, y, z, z + depth);
			break;
		case WEST:
		case EAST:
			offset = odds.getRandomInt(depth);
			drawTable(generator, chunk, odds, x, x + width, y, z + offset, z + 1 + offset);
			break;
		}
	}

}
