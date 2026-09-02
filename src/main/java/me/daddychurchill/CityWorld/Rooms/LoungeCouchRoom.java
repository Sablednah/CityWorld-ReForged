package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Furniture;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LoungeCouchRoom extends LoungeRoom {

	public LoungeCouchRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material sofa = pooledSofa(odds);
		int tx = x + width / 2, tz = z + depth / 2; // a coffee table in the middle of the room
		switch (sideWithWall) {
		default:
		case NORTH:
			for (int x1 = x; x1 < x + width; x1++)
				drawCouchSeat(chunk, sofa, x1, y, z, BlockFace.NORTH);
			tz = z + 1;
			break;
		case SOUTH:
			for (int x1 = x; x1 < x + width; x1++)
				drawCouchSeat(chunk, sofa, x1, y, z + depth - 1, BlockFace.SOUTH);
			tz = z + depth - 2;
			break;
		case WEST:
			for (int z1 = z; z1 < z + depth; z1++)
				drawCouchSeat(chunk, sofa, x, y, z1, BlockFace.WEST);
			tx = x + 1;
			break;
		case EAST:
			for (int z1 = z; z1 < z + depth; z1++)
				drawCouchSeat(chunk, sofa, x + width - 1, y, z1, BlockFace.EAST);
			tx = x + width - 2;
			break;
		}
		// MODERN: a little coffee table in front of the couch
		if (Furniture.modern(generator))
			Furniture.sideTable(chunk, odds, tx, y, tz);
	}

}
