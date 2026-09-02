package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A workshop cell: a workbench (the pooled powered one, else a crafting table), an anvil or
 * smithing table, and a crate or two against the wall. For factories and industrial floors.
 */
public class WorkshopRoom extends FilledRoom {

	public WorkshopRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		BlockFace front = sideWithWall.getOppositeFace();
		Material bench = FurnitureTags.pick(FurnitureTags.WORKBENCH, odds);
		Material crate = FurnitureTags.pick(FurnitureTags.CRATE, odds);
		int bx, bz, dx, dz; // bench-row anchor and step along the wall
		switch (sideWithWall) {
		default:
		case NORTH:
			bx = x; bz = z; dx = 1; dz = 0;
			break;
		case SOUTH:
			bx = x; bz = z + depth - 1; dx = 1; dz = 0;
			break;
		case WEST:
			bx = x; bz = z; dx = 0; dz = 1;
			break;
		case EAST:
			bx = x + width - 1; bz = z; dx = 0; dz = 1;
			break;
		}
		// the work row: bench, anvil/smithing, crates — whatever the wall has room for
		Material[] row = { bench != null ? bench : Material.CRAFTING_TABLE,
				odds.flipCoin() ? Material.ANVIL : Material.SMITHING_TABLE,
				crate != null ? crate : Material.BARREL };
		int steps = Math.max(dx * width, dz * depth);
		for (int i = 0; i < Math.min(row.length, steps); i++) {
			int cx = bx + dx * i, cz = bz + dz * i;
			chunk.setBlock(cx, y, cz, row[i], FurnitureTags.facingFor(row[i], front));
			// crates sometimes stack two high, like someone is mid-unpacking
			if (i == 2 && crate != null && odds.flipCoin())
				chunk.setBlock(cx, y + 1, cz, crate);
		}
	}
}
