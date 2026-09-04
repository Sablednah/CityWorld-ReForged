package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * Shop-floor shelving: two runs of display units with a browsing aisle between, both fronting the
 * aisle. Store floors used to be registers plus EmptyRoom — counters, then nothing (playtested).
 * Draws pooled drawers/bookshelf units when a furniture mod supplies them, else vanilla shelving.
 */
public class ShelfRoom extends FilledRoom {

	private static final Material[] VANILLA_SHELVES = { Material.BOOKSHELF, Material.CHISELED_BOOKSHELF,
			Material.BARREL };

	public ShelfRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		// one unit type per room, so an aisle reads as a run of the same shelving
		Material unit = FurnitureTags.pick(FurnitureTags.DRAWER, odds);
		if (unit == null)
			unit = FurnitureTags.pick(FurnitureTags.BOOKSHELF, odds);
		if (unit == null)
			unit = odds.getRandomMaterial(VANILLA_SHELVES);

		boolean alongX = sideWithWall == BlockFace.NORTH || sideWithWall == BlockFace.SOUTH;
		if (alongX) {
			for (int cx = x; cx < x + width; cx++) {
				chunk.setBlock(cx, y, z, unit, FurnitureTags.facingFor(unit, BlockFace.SOUTH));
				chunk.setBlock(cx, y, z + depth - 1, unit, FurnitureTags.facingFor(unit, BlockFace.NORTH));
			}
		} else {
			for (int cz = z; cz < z + depth; cz++) {
				chunk.setBlock(x, y, cz, unit, FurnitureTags.facingFor(unit, BlockFace.EAST));
				chunk.setBlock(x + width - 1, y, cz, unit, FurnitureTags.facingFor(unit, BlockFace.WEST));
			}
		}
	}
}
