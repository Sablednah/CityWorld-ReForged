package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A bedroom nook for apartment floors: a bed against the wall, a wardrobe or drawers beside it,
 * and a rug of carpet. High-rise living — office towers get flats now.
 */
public class BedNookRoom extends FilledRoom {

	private static final Material[] BEDS = { Material.WHITE_BED, Material.RED_BED, Material.BLUE_BED,
			Material.LIGHT_GRAY_BED, Material.CYAN_BED };

	private static final Material[] RUGS = { Material.WHITE_CARPET, Material.LIGHT_GRAY_CARPET,
			Material.CYAN_CARPET, Material.RED_CARPET };

	public BedNookRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		Material bed = odds.getRandomMaterial(BEDS);
		Material store = FurnitureTags.pick(FurnitureTags.WARDROBE, odds);
		if (store == null)
			store = FurnitureTags.pick(FurnitureTags.DRAWER, odds);
		switch (sideWithWall) {
		default:
		case NORTH:
			chunk.setBed(x, y, z, bed, BlockFace.NORTH); // head against the wall side
			if (store != null)
				chunk.setBlock(x + width - 1, y, z, store, FurnitureTags.facingFor(store, BlockFace.SOUTH));
			break;
		case SOUTH:
			chunk.setBed(x, y, z + depth - 2, bed, BlockFace.SOUTH);
			if (store != null)
				chunk.setBlock(x + width - 1, y, z + depth - 1, store,
						FurnitureTags.facingFor(store, BlockFace.NORTH));
			break;
		case WEST:
			chunk.setBed(x, y, z, bed, BlockFace.WEST);
			if (store != null)
				chunk.setBlock(x, y, z + depth - 1, store, FurnitureTags.facingFor(store, BlockFace.EAST));
			break;
		case EAST:
			chunk.setBed(x + width - 2, y, z, bed, BlockFace.EAST);
			if (store != null)
				chunk.setBlock(x + width - 1, y, z + depth - 1, store,
						FurnitureTags.facingFor(store, BlockFace.WEST));
			break;
		}
		int rx = x + width / 2, rz = z + depth / 2;
		if (chunk.isEmpty(rx, y, rz) && !chunk.isEmpty(rx, y - 1, rz))
			chunk.setBlock(rx, y, rz, odds.getRandomMaterial(RUGS));
	}
}
