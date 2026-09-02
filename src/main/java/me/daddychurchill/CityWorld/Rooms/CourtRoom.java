package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A courtroom cell: the judge's bench against the wall with a lectern before it (the witness
 * stand), and a row of public seating facing the bench. New with the civic-interiors round.
 */
public class CourtRoom extends FilledRoom {

	public CourtRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		int mid = x + width / 2, midZ = z + depth / 2;
		switch (sideWithWall) {
		default:
		case NORTH:
			chunk.setBlocks(x, x + width, y, y + height, z, z + 1, materialWall);
			drawDesk(generator, chunk, odds, x, x + width, y, z + 1, z + 2, BlockFace.SOUTH); // the bench
			for (int cx = x; cx < x + width; cx++)
				if (cx == mid)
					chunk.setBlock(cx, y, z + 2, Material.LECTERN, BlockFace.SOUTH); // witness stand
				else
					drawSeat(chunk, odds, cx, y, z + 2, BlockFace.SOUTH); // gallery, facing the bench
			break;
		case SOUTH:
			chunk.setBlocks(x, x + width, y, y + height, z + depth - 1, z + depth, materialWall);
			drawDesk(generator, chunk, odds, x, x + width, y, z + depth - 2, z + depth - 1, BlockFace.NORTH);
			for (int cx = x; cx < x + width; cx++)
				if (cx == mid)
					chunk.setBlock(cx, y, z + depth - 3, Material.LECTERN, BlockFace.NORTH);
				else
					drawSeat(chunk, odds, cx, y, z + depth - 3, BlockFace.NORTH);
			break;
		case WEST:
			chunk.setBlocks(x, x + 1, y, y + height, z, z + depth, materialWall);
			drawDesk(generator, chunk, odds, x + 1, x + 2, y, z, z + depth, BlockFace.EAST);
			for (int cz = z; cz < z + depth; cz++)
				if (cz == midZ)
					chunk.setBlock(x + 2, y, cz, Material.LECTERN, BlockFace.EAST);
				else
					drawSeat(chunk, odds, x + 2, y, cz, BlockFace.EAST);
			break;
		case EAST:
			chunk.setBlocks(x + width - 1, x + width, y, y + height, z, z + depth, materialWall);
			drawDesk(generator, chunk, odds, x + width - 2, x + width - 1, y, z, z + depth, BlockFace.WEST);
			for (int cz = z; cz < z + depth; cz++)
				if (cz == midZ)
					chunk.setBlock(x + width - 3, y, cz, Material.LECTERN, BlockFace.WEST);
				else
					drawSeat(chunk, odds, x + width - 3, y, cz, BlockFace.WEST);
			break;
		}
	}
}
