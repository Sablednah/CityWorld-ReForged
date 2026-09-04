package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A school classroom cell: a blackboard on the wall, a row of desks facing it, a row of seats
 * behind them. New with the civic-interiors round — schools never had rooms of their own.
 */
public class ClassRoom extends FilledRoom {

	public ClassRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		switch (sideWithWall) {
		default:
		case NORTH:
			chunk.setBlocks(x, x + width, y, y + height, z, z + 1, materialWall);
			chunk.setBlocks(x, x + width, y + 1, y + 3, z, z + 1, Material.BLACK_CONCRETE); // the board
			drawDesk(generator, chunk, odds, x, x + width, y, z + 1, z + 2, BlockFace.SOUTH);
			for (int cx = x; cx < x + width; cx++)
				drawSeat(chunk, odds, cx, y, z + 2, BlockFace.SOUTH);
			break;
		case SOUTH:
			chunk.setBlocks(x, x + width, y, y + height, z + depth - 1, z + depth, materialWall);
			chunk.setBlocks(x, x + width, y + 1, y + 3, z + depth - 1, z + depth, Material.BLACK_CONCRETE);
			drawDesk(generator, chunk, odds, x, x + width, y, z + depth - 2, z + depth - 1, BlockFace.NORTH);
			for (int cx = x; cx < x + width; cx++)
				drawSeat(chunk, odds, cx, y, z + depth - 3, BlockFace.NORTH);
			break;
		case WEST:
			chunk.setBlocks(x, x + 1, y, y + height, z, z + depth, materialWall);
			chunk.setBlocks(x, x + 1, y + 1, y + 3, z, z + depth, Material.BLACK_CONCRETE);
			drawDesk(generator, chunk, odds, x + 1, x + 2, y, z, z + depth, BlockFace.EAST);
			for (int cz = z; cz < z + depth; cz++)
				drawSeat(chunk, odds, x + 2, y, cz, BlockFace.EAST);
			break;
		case EAST:
			chunk.setBlocks(x + width - 1, x + width, y, y + height, z, z + depth, materialWall);
			chunk.setBlocks(x + width - 1, x + width, y + 1, y + 3, z, z + depth, Material.BLACK_CONCRETE);
			drawDesk(generator, chunk, odds, x + width - 2, x + width - 1, y, z, z + depth, BlockFace.WEST);
			for (int cz = z; cz < z + depth; cz++)
				drawSeat(chunk, odds, x + width - 3, y, cz, BlockFace.WEST);
			break;
		}
	}
}
