package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LibraryDoubleRoom extends LibraryRoom {

	public LibraryDoubleRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {

		Material shelf = me.daddychurchill.CityWorld.Support.Furniture.shelfMaterial(generator, odds);
		switch (sideWithWall) {
		default:
		case NORTH:
			drawNSBookshelves(chunk, x, y, z, width, height, depth, 0, shelf);
			break;
		case SOUTH:
			drawNSBookshelves(chunk, x, y, z, width, height, depth, depth - 1, shelf);
			break;
		case EAST:
			drawWEBookshelves(chunk, x, y, z, width, height, depth, width - 1, shelf);
			break;
		case WEST:
			drawWEBookshelves(chunk, x, y, z, width, height, depth, 0, shelf);
			break;
		}
	}

	private void drawNSBookshelves(RealBlocks chunk, int x, int y, int z, int width, int height, int depth, int i,
			Material shelf) {
		for (int offset = 0; offset < width; offset += 2) {
			chunk.setBlocks(x + offset, x + 1 + offset, y, y + height, z, z + depth, shelf);
			if (offset < width - 1)
				chunk.setBlock(x + offset + 1, y, z + i, shelf);
		}
	}

	private void drawWEBookshelves(RealBlocks chunk, int x, int y, int z, int width, int height, int depth, int i,
			Material shelf) {
		for (int offset = 0; offset < depth; offset += 2) {
			chunk.setBlocks(x, x + width, y, y + height, z + offset, z + 1 + offset, shelf);
			if (offset < depth - 1)
				chunk.setBlock(x + i, y, z + offset + 1, shelf);
		}
	}
}
