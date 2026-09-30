package me.daddychurchill.CityWorld.Plats.Rural;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A farmyard with a well in it — the "Wells" half of Ed's "TODO Barns and Wells" in {@code FarmContext} (the barns
 * were built long ago). A stone well over a water shaft, posts and a little roof with a chain hanging from it, a path
 * across the yard, and a haystack, barrels and a composter in its corners. A one-off per farm platmap, like the barn.
 */
public class FarmyardWellLot extends IsolatedLot {

	public FarmyardWellLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		style = LotStyle.STRUCTURE;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new FarmyardWellLot(platmap, chunkX, chunkZ);
	}

	@Override
	public boolean allowsWildDecoration() {
		return false;
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return generator.streetLevel;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return generator.streetLevel + 5;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		chunk.airoutLayer(generator, generator.streetLevel + 1, DataContext.FloorHeight * 2, 0, true);
		chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
		chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		int y = generator.streetLevel;

		// the path across the yard, under the well's approach
		boolean alongX = chunkOdds.flipCoin();
		for (int i = 0; i < 16; i++)
			if (alongX)
				chunk.setBlock(i, y, 7, Material.GRASS_PATH);
			else
				chunk.setBlock(7, y, i, Material.GRASS_PATH);

		// the well: a cobblestone ring two high round a water shaft
		chunk.setBlocks(6, 9, y - 6, y + 2, 6, 9, Material.COBBLESTONE);
		chunk.setBlocks(7, y - 5, y + 1, 7, Material.WATER);
		chunk.setBlock(7, y + 1, 7, Material.AIR);
		chunk.setBlock(6, y + 1, 6, Material.MOSSY_COBBLESTONE);
		chunk.setBlock(8, y + 1, 8, Material.MOSSY_COBBLESTONE);
		// posts, a slab roof and the chain the bucket hung from
		chunk.setBlocks(6, y + 2, y + 4, 7, Material.OAK_FENCE);
		chunk.setBlocks(8, y + 2, y + 4, 7, Material.OAK_FENCE);
		chunk.setBlocks(6, 9, y + 4, 6, 9, Material.SPRUCE_SLAB);
		chunk.setBlock(7, y + 3, 7, Material.IRON_CHAIN);

		// the yard's clutter
		chunk.setBlocks(11, 13, y + 1, 3, 5, Material.HAY_BLOCK);
		chunk.setBlock(11, y + 2, 3, Material.HAY_BLOCK);
		chunk.setBlock(3, y + 1, 11, Material.BARREL, BlockFace.UP);
		chunk.setBlock(4, y + 1, 11, Material.BARREL, BlockFace.UP);
		chunk.setBlock(3, y + 1, 12, Material.COMPOSTER);
		chunk.setBlock(12, y + 1, 12, Material.of(net.minecraft.world.level.block.Blocks.WATER_CAULDRON));

		generateSurface(generator, chunk, false);
		generator.reportLocation("well", "Farmyard Well", chunk);
	}
}
