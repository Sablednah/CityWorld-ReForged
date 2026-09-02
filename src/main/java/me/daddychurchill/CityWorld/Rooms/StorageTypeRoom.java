package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

abstract class StorageTypeRoom extends StorageRoom {

	private final Material materialType;

	StorageTypeRoom(Material type) {
		super();
		materialType = type;
	}

	void setStorageBlocks(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int x, int y1, int y2,
			int z) {
		// a third of the stacks become crates when a furniture mod supplies them — warehouse
		// shelving full of actual crates instead of raw material blocks
		Material crate = odds.playOdds(0.33)
				? me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
						me.daddychurchill.CityWorld.Support.FurnitureTags.CRATE, odds)
				: null;
		if (crate != null) {
			chunk.setBlocks(x, x + 1, y1, y2, z, z + 1, crate);
		} else if (materialType == Material.PISTON) {
			chunk.setBlocks(x, x + 1, y1, y2, z, z + 1, materialType, BlockFace.UP);
		} else {
			chunk.setBlocks(x, x + 1, y1, y2, z, z + 1, materialType);
		}
	}

}
