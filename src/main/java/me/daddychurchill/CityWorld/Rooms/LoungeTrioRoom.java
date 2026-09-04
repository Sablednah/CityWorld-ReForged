package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LoungeTrioRoom extends LoungeRoom {

	public LoungeTrioRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material sofa = pooledSofa(odds);
		Material tableLeg = getTableLeg(odds);
		Material tableTop = getTableTop(odds);

		switch (sideWithWall) {
		default:
		case NORTH:
			drawCouchSeat(chunk, sofa, x + 1, y, z, BlockFace.NORTH);
			drawCouchSeat(chunk, sofa, x, y, z + 1, BlockFace.WEST);
			drawCouchSeat(chunk, sofa, x + 2, y, z + 1, BlockFace.EAST);

			drawTable(generator, chunk, odds, x, y, z);
			drawTable(generator, chunk, odds, x + 2, y, z);
			break;
		case SOUTH:
			drawCouchSeat(chunk, sofa, x + 1, y, z + 2, BlockFace.SOUTH);
			drawCouchSeat(chunk, sofa, x, y, z + 1, BlockFace.WEST);
			drawCouchSeat(chunk, sofa, x + 2, y, z + 1, BlockFace.EAST);

			drawTable(generator, chunk, odds, x, y, z + 2);
			drawTable(generator, chunk, odds, x + 2, y, z + 2);
			break;
		case WEST:
			drawCouchSeat(chunk, sofa, x + 1, y, z, BlockFace.NORTH);
			drawCouchSeat(chunk, sofa, x + 1, y, z + 2, BlockFace.SOUTH);
			drawCouchSeat(chunk, sofa, x, y, z + 1, BlockFace.WEST);

			drawTable(generator, chunk, odds, x, y, z);
			drawTable(generator, chunk, odds, x, y, z + 2);
			break;
		case EAST:
			drawCouchSeat(chunk, sofa, x + 1, y, z, BlockFace.NORTH);
			drawCouchSeat(chunk, sofa, x + 1, y, z + 2, BlockFace.SOUTH);
			drawCouchSeat(chunk, sofa, x + 2, y, z + 1, BlockFace.EAST);

			drawTable(generator, chunk, odds, x + 2, y, z);
			drawTable(generator, chunk, odds, x + 2, y, z + 2);
			break;
		}

	}

}
