package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A statue on a hill overlooking the city — Ed's "TODO statue overlooking the city?" in the lowland band of
 * {@code NatureContext}, where only balloons were ever placed. A plinth of stone bricks at the top of the rise, its
 * footing built down to the ground, and the monument statue ({@code ThingProvider.generateStatue}) on it.
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

		// the plinth stands on the highest ground under it, its footing filled down to the ground elsewhere — only
		// on a hilltop, not a mountainside: first measured, a steep chunk built a stone-brick tower past y100
		int top = Integer.MIN_VALUE, bottom = Integer.MAX_VALUE;
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++) {
				top = Math.max(top, blockYs.getBlockY(x, z));
				bottom = Math.min(bottom, blockYs.getBlockY(x, z));
			}
		if (top - bottom > 4)
			return;
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++) {
				chunk.setBlocks(x, blockYs.getBlockY(x, z) + 1, top + 1, z, Material.STONE_BRICKS);
				chunk.clearBlocks(x, top + 1, top + 12, z);
			}
		chunk.setBlocks(4, 12, top + 1, 4, 12, Material.of(net.minecraft.world.level.block.Blocks.STONE_BRICK_SLAB));
		chunk.setBlocks(5, 11, top + 1, 5, 11, Material.CHISELED_STONE_BRICKS);
		chunk.setBlocks(6, 10, top + 2, 6, 10, Material.POLISHED_ANDESITE);

		Material stone = chunkOdds.flipCoin() ? Material.QUARTZ_BLOCK : Material.of(net.minecraft.world.level.block.Blocks.WEATHERED_COPPER);
		generator.thingProvider.generateStatue(chunk, chunkOdds, 8, top + 3, 8, stone);
		generator.reportLocation("statue", "Hilltop Statue", chunk);
	}
}
