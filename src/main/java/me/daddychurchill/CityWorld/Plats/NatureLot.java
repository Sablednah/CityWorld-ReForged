package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class NatureLot extends IsolatedLot {

	public NatureLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

		style = LotStyle.NATURE;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new NatureLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return 0;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return blockYs.getBlockY(x, z);// + generator.landRange;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		// TODO Auto-generated method stub

	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		// In MODERN, vanilla decorates the wild (biome-appropriate trees/flowers), so don't also plant
		// CityWorld's own trees here — that doubling was making forests read too dense. Other styles keep
		// placing them. The grass/snow surface is still laid down either way (vanilla needs it to plant on).
		boolean cityworldTrees = generator.worldStyle != CityWorldGenerator.WorldStyle.MODERN;
		generateSurface(generator, chunk, cityworldTrees);

		// MODERN swamps/mangroves sit on dry lowland just above the waterline, so they read swampy but
		// have no water. Scoop a few shallow, muddy pools down to the water table so vanilla's swamp
		// decoration (lily pads, mangrove roots) has water to work with.
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN
				&& chunk.isSwampBiome(8, getBlockY(8, 8), 8))
			carveSwampPools(generator, chunk);

		generateEntities(generator, chunk);
	}

	private void carveSwampPools(CityWorldGenerator generator, RealBlocks chunk) {
		int sea = generator.seaLevel;
		int pools = chunkOdds.getRandomInt(2, 3); // 2-4 small pools per swamp chunk
		for (int i = 0; i < pools; i++) {
			int px = chunkOdds.getRandomInt(2, 12);
			int pz = chunkOdds.getRandomInt(2, 12);
			int depth = chunkOdds.getRandomInt(1, 2); // 1-2 deep
			carvePoolCell(chunk, px, pz, sea, depth);
			// a small blob rather than a single hole
			if (chunkOdds.flipCoin())
				carvePoolCell(chunk, px + 1, pz, sea, depth);
			if (chunkOdds.flipCoin())
				carvePoolCell(chunk, px - 1, pz, sea, depth);
			if (chunkOdds.flipCoin())
				carvePoolCell(chunk, px, pz + 1, sea, depth);
			if (chunkOdds.flipCoin())
				carvePoolCell(chunk, px, pz - 1, sea, depth);
		}
	}

	private void carvePoolCell(RealBlocks chunk, int x, int z, int sea, int depth) {
		if (x < 1 || x > 14 || z < 1 || z > 14)
			return; // keep a block off the chunk edge so pool water can't chase an unloaded neighbour
		int top = getBlockY(x, z);
		int waterTop = Math.min(top, sea); // the pool surface sits at the water table
		if (waterTop - depth < 1)
			return;
		if (top > waterTop)
			chunk.setBlocks(x, waterTop + 1, top + 1, z, me.daddychurchill.CityWorld.compat.Material.AIR);
		chunk.setBlocks(x, waterTop - depth + 1, waterTop + 1, z, me.daddychurchill.CityWorld.compat.Material.WATER);
		chunk.setBlock(x, waterTop - depth, z, me.daddychurchill.CityWorld.compat.Material.MUD);
	}

	private final static int magicSeaSpawnY = 62;

	protected void generateEntities(CityWorldGenerator generator, RealBlocks chunk) {
		int x = chunkOdds.getRandomInt(1, 14);
		int z = chunkOdds.getRandomInt(1, 14);
		int y = getBlockY(x, z);

		// in the water?
		if (y < magicSeaSpawnY) {
			generator.spawnProvider.spawnSeaAnimals(generator, chunk, chunkOdds, x, magicSeaSpawnY, z);
//			chunk.setBlock(x, 100, z, Material.LAPIS_BLOCK);
		} else {
//			int origY = getBlockY(x, z);
//			int topY = getTopY(generator);
//			int y = chunk.findFirstEmptyAbove(x, origY, z, topY);
//			chunk.setSignPost(x, 101, z, BlockFace.NORTH, "Y = " + y, "origY = " + origY, "TopY = " + topY);
			if (!chunk.isWater(x, y - 1, z)) {
				generator.spawnProvider.spawnVagrants(generator, chunk, chunkOdds, x, y, z);
//				chunk.setBlock(x, 100, z, Material.IRON_BLOCK);
//			} else {
//				chunk.setBlock(x, 100, z, Material.DIAMOND_BLOCK);
			}
		}
	}

}
