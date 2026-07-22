package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.Material;

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

		// Biome-signature ground (sand deserts, badlands, mycelium, snowy plains) is applied by the base
		// PlatLot.generateBlocks after this returns — so it covers farms/houses too, not just the wild.

		// MODERN swamps/mangroves sat on dry lowland a block above the waterline, so they read swampy but
		// had no water. Drop the whole swamp to sea level and rebuild it as muddy ground shot through
		// with water pools, so it's flush with the water table — vanilla then adds lily pads, mangrove
		// roots and blue orchids.
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN
				&& chunk.isSwampBiome(8, getBlockY(8, 8), 8))
			generateSwampSurface(generator, chunk);

		generateEntities(generator, chunk);
	}

	private void generateSwampSurface(CityWorldGenerator generator, RealBlocks chunk) {
		int sea = generator.seaLevel;
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int top = getBlockY(x, z);
				if (top < sea)
					continue; // already open water at the swamp's edge — leave it

				// strip the swamp down to the water table: clear the block(s) and any vegetation above sea
				chunk.setBlocks(x, sea + 1, top + 2, z, Material.AIR);

				// keep water a block off the chunk edge so a source can't chase an unloaded neighbour
				boolean edge = x == 0 || x == 15 || z == 0 || z == 15;
				double roll = chunkOdds.getRandomDouble();
				if (!edge && roll < 0.45) {
					// a water pool sitting flush with the sea, muddy bottom
					chunk.setBlock(x, sea, z, Material.WATER);
					chunk.setBlock(x, sea - 1, z, Material.MUD);
				} else if (roll < 0.9) {
					chunk.setBlock(x, sea, z, Material.MUD); // muddy swamp ground
				} else {
					chunk.setBlock(x, sea, z, Material.GRASS_BLOCK); // the odd grassy tussock
				}
			}
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
