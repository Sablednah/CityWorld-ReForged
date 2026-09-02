package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LoungeEllCouchRoom extends LoungeCouchRoom {

	public LoungeEllCouchRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material sofa = pooledSofa(odds);

		super.drawFixture(generator, chunk, odds, floor, x, y, z, width, height, depth, sideWithWall, materialWall,
				materialGlass);

		Material tableLeg = getTableLeg(odds);
		Material tableTop = getTableTop(odds);

		switch (sideWithWall) {
		default:
		case NORTH:
			for (int z1 = z + 1; z1 < z + depth; z1++)
				drawCouchSeat(chunk, sofa, x + width - 1, y, z1, BlockFace.EAST);
			drawTable(generator, chunk, odds, x, x + width - 2, y, z + depth - 1, z + depth);
			break;
		case SOUTH:
			for (int z1 = z; z1 < z + depth - 1; z1++)
				drawCouchSeat(chunk, sofa, x, y, z1, BlockFace.WEST);
			drawTable(generator, chunk, odds, x + 2, x + width, y, z, z + 1);
			break;
		case WEST:
			for (int x1 = x + 1; x1 < x + width; x1++)
				drawCouchSeat(chunk, sofa, x1, y, z, BlockFace.NORTH);
			drawTable(generator, chunk, odds, x + 2, x + width, y, z + depth - 1, z + depth);
			break;
		case EAST:
			for (int x1 = x; x1 < x + width - 1; x1++)
				drawCouchSeat(chunk, sofa, x1, y, z + depth - 1, BlockFace.SOUTH);
			drawTable(generator, chunk, odds, x, x + width - 2, y, z, z + 1);
			break;
		}
	}

}
