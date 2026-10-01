package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Monuments;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A monument on a hill overlooking the city — Ed's "TODO statue overlooking the city?". Placed by
 * {@code NatureContext.placeHillStatue} on a district's highest natural chunk. A stepped stone plinth on the highest
 * ground under it, its footing built down to the ground where the hill falls away, and one of the
 * {@link Monuments} on it.
 */
public class HillStatueLot extends ConstructLot {

	public HillStatueLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		trulyIsolated = true;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new HillStatueLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return blockYs.getMaxHeight();
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		generateSurface(generator, chunk, false);
		boolean turned = chunkOdds.flipCoin(); // drawn along z instead of x
		Monuments.Design design = Monuments.pick(chunkOdds);

		// the plinth stands on the highest ground under it, its footing filled down to the ground elsewhere
		int top = Integer.MIN_VALUE;
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++)
				top = Math.max(top, blockYs.getBlockY(x, z));
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++) {
				chunk.setBlocks(x, blockYs.getBlockY(x, z) + 1, top + 1, z, Material.STONE_BRICKS);
				chunk.clearBlocks(x, top + 1, top + 16, z);
			}
		chunk.setBlocks(4, 12, top + 1, 4, 12, Material.of(net.minecraft.world.level.block.Blocks.STONE_BRICK_SLAB));
		chunk.setBlocks(5, 11, top + 1, 5, 11, Material.CHISELED_STONE_BRICKS);
		int inset = (16 - design.footprint()) / 2;
		chunk.setBlocks(inset, 16 - inset, top + 2, inset, 16 - inset, Material.POLISHED_ANDESITE);

		Monuments.draw((a, y, c, material) -> {
			if (turned)
				chunk.setBlock(c, y, a, material);
			else
				chunk.setBlock(a, y, c, material);
		}, design, top + 3, Monuments.pickStone(chunkOdds), chunkOdds);
		generator.reportLocation("statue", design.title, chunk);
	}
}
