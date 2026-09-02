package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class LoungeTVRoom extends LoungeRoom {

	public LoungeTVRoom() {
		// TODO Auto-generated constructor stub
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material sofa = pooledSofa(odds);
		switch (sideWithWall) {
		default:
		case NORTH:
			drawCouchSeat(chunk, sofa, x, y, z + depth - 1, BlockFace.SOUTH);
			drawCouchSeat(chunk, sofa, x + 1, y, z + depth - 1, BlockFace.SOUTH);
			drawCouchSeat(chunk, sofa, x + 2, y, z + depth - 1, BlockFace.SOUTH);

			chunk.setBlocks(x, x + 3, y, y + height, z - 1, z, materialWall);
			television(chunk, odds, x + 1, y, z, BlockFace.SOUTH);
			break;
		case SOUTH:
			drawCouchSeat(chunk, sofa, x, y, z, BlockFace.NORTH);
			drawCouchSeat(chunk, sofa, x + 1, y, z, BlockFace.NORTH);
			drawCouchSeat(chunk, sofa, x + 2, y, z, BlockFace.NORTH);

			chunk.setBlocks(x, x + 3, y, y + height, z + depth, z + depth + 1, materialWall);
			television(chunk, odds, x + 1, y, z + depth - 1, BlockFace.NORTH);
			break;
		case WEST:
			drawCouchSeat(chunk, sofa, x + width - 1, y, z, BlockFace.EAST);
			drawCouchSeat(chunk, sofa, x + width - 1, y, z + 1, BlockFace.EAST);
			drawCouchSeat(chunk, sofa, x + width - 1, y, z + 2, BlockFace.EAST);

			chunk.setBlocks(x - 1, x, y, y + height, z, z + 3, materialWall);
			television(chunk, odds, x, y, z + 1, BlockFace.EAST);
			break;
		case EAST:
			drawCouchSeat(chunk, sofa, x, y, z, BlockFace.WEST);
			drawCouchSeat(chunk, sofa, x, y, z + 1, BlockFace.WEST);
			drawCouchSeat(chunk, sofa, x, y, z + 2, BlockFace.WEST);

			chunk.setBlocks(x + width, x + width + 1, y, y + height, z, z + 3, materialWall);
			television(chunk, odds, x + width - 1, y, z + 1, BlockFace.WEST);
			break;
		}
	}

	/** The upstream author's 12-year-old TODO said "add picture to wall" here — a television on a
	 *  stand, screen to the couch, is surely what they meant. No-op without a furniture mod. */
	private void television(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace front) {
		Material tv = me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.TV, odds);
		if (tv == null || !chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z))
			return;
		Material stand = me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.DRAWER, odds);
		chunk.setBlock(x, y, z, stand != null ? stand : Material.SMOOTH_QUARTZ,
				me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(stand, front));
		chunk.setBlock(x, y + 1, z, tv,
				me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(tv, front));
	}

}
