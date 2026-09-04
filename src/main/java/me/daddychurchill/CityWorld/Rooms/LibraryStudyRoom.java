package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LibraryStudyRoom extends LibraryRoom {

	public LibraryStudyRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material tableLeg = getTableLeg(odds);
		Material tableTop = getTableTop(odds);
		Material shelf = me.daddychurchill.CityWorld.Support.Furniture.shelfMaterial(generator, odds);

		switch (sideWithWall) {
		default:
		case NORTH:
			chunk.setBlocks(x, x + width, y, y + height, z, z + 1, shelf);
			drawSeat(chunk, odds, x, y, z + 2, BlockFace.WEST);
			drawTable(generator, chunk, odds, x + 1, y, z + 2);
			break;
		case SOUTH:
			chunk.setBlocks(x, x + width, y, y + height, z + depth - 1, z + depth, shelf);
			drawSeat(chunk, odds, x, y, z, BlockFace.WEST);
			drawTable(generator, chunk, odds, x + 1, y, z);
			drawSeat(chunk, odds, x + 2, y, z, BlockFace.EAST);
			break;
		case WEST:
			chunk.setBlocks(x, x + 1, y, y + height, z, z + depth, shelf);
			drawSeat(chunk, odds, x + 2, y, z, BlockFace.NORTH);
			drawTable(generator, chunk, odds, x + 2, y, z + 1);
			drawSeat(chunk, odds, x + 2, y, z + 2, BlockFace.SOUTH);
			break;
		case EAST:
			chunk.setBlocks(x + width - 1, x + width, y, y + height, z, z + depth, shelf);
			drawSeat(chunk, odds, x, y, z, BlockFace.NORTH);
			drawTable(generator, chunk, odds, x, y, z + 1);
			drawSeat(chunk, odds, x, y, z + 2, BlockFace.SOUTH);
			break;
		}
	}

}
