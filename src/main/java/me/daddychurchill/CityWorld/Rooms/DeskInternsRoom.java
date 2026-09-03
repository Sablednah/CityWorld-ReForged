package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class DeskInternsRoom extends DeskRoom {

	public DeskInternsRoom() {
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
			drawDesk(generator, chunk, odds, x, y, z, BlockFace.SOUTH);
			chunk.setBlock(x + 1, y, z, Material.BOOKSHELF);
			chunk.setBlock(x + 1, y + 1, z, tableTop);
			drawDesk(generator, chunk, odds, x + 2, y, z, BlockFace.SOUTH);
			drawSeat(chunk, odds, x, y, z + 1, BlockFace.SOUTH);
			drawSeat(chunk, odds, x + 2, y, z + 1, BlockFace.SOUTH);
			break;
		case SOUTH:
			drawDesk(generator, chunk, odds, x, y, z + 2, BlockFace.NORTH);
			chunk.setBlock(x + 1, y, z + 2, Material.BOOKSHELF);
			chunk.setBlock(x + 1, y + 1, z + 2, tableTop);
			drawDesk(generator, chunk, odds, x + 2, y, z + 2, BlockFace.NORTH);
			drawSeat(chunk, odds, x, y, z + 1, BlockFace.NORTH);
			drawSeat(chunk, odds, x + 2, y, z + 1, BlockFace.NORTH);
			break;
		case WEST:
			drawDesk(generator, chunk, odds, x, y, z, BlockFace.EAST);
			chunk.setBlock(x, y, z + 1, Material.BOOKSHELF);
			chunk.setBlock(x, y + 1, z + 1, tableTop);
			drawDesk(generator, chunk, odds, x, y, z + 2, BlockFace.EAST);
			drawSeat(chunk, odds, x + 1, y, z, BlockFace.EAST);
			drawSeat(chunk, odds, x + 1, y, z + 2, BlockFace.EAST);
			break;
		case EAST:
			drawDesk(generator, chunk, odds, x + 2, y, z, BlockFace.WEST);
			chunk.setBlock(x + 2, y, z + 1, Material.BOOKSHELF);
			chunk.setBlock(x + 2, y + 1, z + 1, tableTop);
			drawDesk(generator, chunk, odds, x + 2, y, z + 2, BlockFace.WEST);
			drawSeat(chunk, odds, x + 1, y, z, BlockFace.WEST);
			drawSeat(chunk, odds, x + 1, y, z + 2, BlockFace.WEST);
			break;
		}
	}

}
