package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.BiomeSurface;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

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

		// MODERN: give the wild its biome-signature ground (sand deserts, banded badlands, mycelial
		// mushroom isles, snowy low plains, gravelly hills) — CityWorld lays grass everywhere otherwise.
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN)
			applyBiomeGround(generator, chunk);

		// MODERN swamps/mangroves sat on dry lowland a block above the waterline, so they read swampy but
		// had no water. Drop the whole swamp to sea level and rebuild it as muddy ground shot through
		// with water pools, so it's flush with the water table — vanilla then adds lily pads, mangrove
		// roots and blue orchids.
		if (generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN
				&& chunk.isSwampBiome(8, getBlockY(8, 8), 8))
			generateSwampSurface(generator, chunk);

		generateEntities(generator, chunk);
	}

	private void applyBiomeGround(CityWorldGenerator generator, RealBlocks chunk) {
		int iceLine = generator.snowLevel - 5; // the icecap pass owns columns at/above this
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int top = getBlockY(x, z);
				if (top < generator.seaLevel || top >= iceLine)
					continue; // underwater, or up in the icecap's territory
				// only reshape natural grassy ground — never a placed feature, water or a swamp pool
				if (!chunk.isOfTypes(x, top, z, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT,
						Material.PODZOL))
					continue;
				ResourceKey<Biome> biome = chunk.getBiomeKey(x, top, z);
				if (biome == null)
					continue;

				Material surf = BiomeSurface.surface(biome);
				boolean snow = BiomeSurface.snowy(biome);
				if (surf == null && !snow)
					continue; // a grass biome — leave its grass and foliage alone

				// The surface pass planted grass/ferns/flowers on the (grass) ground; clear all of it —
				// including 2-tall plants and non-replaceable flowers — so nothing floats over the new
				// sand/snow/etc. (These are wild lots: nothing but that vegetation sits above ground here.)
				clearVegetation(chunk, x, top + 1, z);
				clearVegetation(chunk, x, top + 2, z);

				if (surf != null) {
					chunk.setBlock(x, top, z, surf);
					Material sub = BiomeSurface.subsurface(biome);
					if (sub != null)
						chunk.setBlocks(x, top - 3, top, z, sub);
				}
				if (snow)
					chunk.setBlock(x, top + 1, z, Material.SNOW, chunkOdds.getRandomInt(1, 2));
			}
	}

	/** Clear a bit of surface vegetation (grass/ferns/flowers/tall plants) — anything non-air that
	 *  doesn't block motion — leaving solid blocks be. */
	private void clearVegetation(RealBlocks chunk, int x, int y, int z) {
		if (!chunk.isEmpty(x, y, z) && !chunk.getActualBlock(x, y, z).getBlockData().blocksMotion())
			chunk.setBlock(x, y, z, Material.AIR);
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
