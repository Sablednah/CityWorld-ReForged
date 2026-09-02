package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class DeskCornerRoom extends DeskRoom {

	public DeskCornerRoom() {
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
			drawDesk(generator, chunk, odds, x, x + 3, y, z, z + 1, sideWithWall.getOppositeFace());
			chunk.setBlocks(x, x + 1, y, z + 1, z + 3, Material.BOOKSHELF);
			chunk.setBlocks(x, x + 1, y + 1, z + 1, z + 3, tableTop);
			drawSeat(chunk, odds, x + 1, y, z + 1, BlockFace.SOUTH);
			break;
		case SOUTH:
			drawDesk(generator, chunk, odds, x, x + 3, y, z + 2, z + 3, sideWithWall.getOppositeFace());
			chunk.setBlocks(x + 2, x + 3, y, z, z + 2, Material.BOOKSHELF);
			chunk.setBlocks(x + 2, x + 3, y + 1, z, z + 2, tableTop);
			drawSeat(chunk, odds, x + 1, y, z + 1, BlockFace.NORTH);
			break;
		case WEST:
			drawDesk(generator, chunk, odds, x, x + 1, y, z, z + 3, sideWithWall.getOppositeFace());
			chunk.setBlocks(x + 1, x + 3, y, z + 2, z + 3, Material.BOOKSHELF);
			chunk.setBlocks(x + 1, x + 3, y + 1, z + 2, z + 3, tableTop);
			drawSeat(chunk, odds, x + 1, y, z + 1, BlockFace.EAST);
			break;
		case EAST:
			drawDesk(generator, chunk, odds, x + 2, x + 3, y, z, z + 3, sideWithWall.getOppositeFace());
			chunk.setBlocks(x, x + 2, y, z, z + 1, Material.BOOKSHELF);
			chunk.setBlocks(x, x + 2, y + 1, z, z + 1, tableTop);
			drawSeat(chunk, odds, x + 1, y, z + 1, BlockFace.WEST);
			break;
		}
	}

}
