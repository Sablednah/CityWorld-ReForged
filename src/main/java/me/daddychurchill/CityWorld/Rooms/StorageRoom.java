package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.Support.RealBlocks;

abstract class StorageRoom extends FilledRoom {

	StorageRoom() {
		// TODO Auto-generated constructor stub
	}

	void drawNSEmptyShelve(RealBlocks chunk, int x, int y, int z, int height, int run) {
		for (int y1 = 0; y1 < height; y1++) {
			chunk.setBlock(x, y + y1, z, Material.BIRCH_STAIRS, BlockFace.NORTH, Half.TOP);
			chunk.setBlocks(x, x + 1, y + y1, z + 1, z + run - 1, Material.BIRCH_SLAB, SlabType.TOP);
			chunk.setBlock(x, y + y1, z + run - 1, Material.BIRCH_STAIRS, BlockFace.SOUTH, Half.TOP);
		}
	}

	void drawWEEmptyShelve(RealBlocks chunk, int x, int y, int z, int height, int run) {
		for (int y1 = 0; y1 < height; y1++) {
			chunk.setBlock(x, y + y1, z, Material.BIRCH_STAIRS, BlockFace.WEST, Half.TOP);
			chunk.setBlocks(x + 1, x + run - 1, y + y1, z, z + 1, Material.BIRCH_SLAB, SlabType.TOP);
			chunk.setBlock(x + run - 1, y + y1, z, Material.BIRCH_STAIRS, BlockFace.EAST, Half.TOP);
		}
	}
}
