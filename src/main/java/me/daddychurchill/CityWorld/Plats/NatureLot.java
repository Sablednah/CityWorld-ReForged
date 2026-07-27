package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
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
		boolean cityworldTrees = !generator.isModernStyle();
		generateSurface(generator, chunk, cityworldTrees);

		// Biome-signature ground (sand deserts, badlands, mycelium, snowy plains) is applied by the base
		// PlatLot.generateBlocks after this returns — so it covers farms/houses too, not just the wild.

		// MODERN swamps/mangroves read swampy but had no water. Rebuild the swamp floor as muddy ground
		// shot through with water pools — only the low, flat interior is brought flush to the water table;
		// rises just get muddied in place (never flattened) and edges feather to a dry bank. Vanilla then
		// adds lily pads, mangrove roots and blue orchids.
		if (generator.isModernStyle()
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
				// a chunk can straddle a biome boundary; only reshape columns that are really swamp
				if (!chunk.isSwampBiome(x, top, z))
					continue;

				clearVegetation(chunk, x, top + 1, z);
				clearVegetation(chunk, x, top + 2, z);

				boolean bank = swampBank(chunk, x, top, z); // touches non-swamp — feather to a dry bank
				int drop = top - sea;

				if (bank || drop > 2) {
					// Follow the terrain — never flatten a rise (that carved sheer walls into hills). Muddy
					// ground; away from the banks, the odd shallow water pocket dug just 1 deep in place.
					if (!bank && drop <= 6 && chunkOdds.playOdds(Odds.oddsSomewhatUnlikely)) {
						chunk.setBlock(x, top, z, Material.WATER);
						chunk.setBlock(x, top - 1, z, Material.MUD);
					} else {
						chunk.setBlock(x, top, z, chunkOdds.getRandomDouble() < 0.65 ? Material.MUD
								: Material.GRASS_BLOCK);
					}
				} else {
					// Interior and already at the water table: strip the thin dry cap and lay a watery,
					// muddy pond floor flush with the sea.
					chunk.setBlocks(x, sea + 1, top + 2, z, Material.AIR);
					double roll = chunkOdds.getRandomDouble();
					if (roll < 0.5) {
						chunk.setBlock(x, sea, z, Material.WATER);
						chunk.setBlock(x, sea - 1, z, Material.MUD);
					} else if (roll < 0.9) {
						chunk.setBlock(x, sea, z, Material.MUD);
					} else {
						chunk.setBlock(x, sea, z, Material.GRASS_BLOCK);
					}
				}
			}
	}

	/** A swamp column that borders non-swamp terrain (including the neighbouring chunk) — feathered as a
	 *  dry muddy bank so the swamp doesn't meet the wild in a hard step. */
	private boolean swampBank(RealBlocks chunk, int x, int y, int z) {
		return !chunk.isSwampBiome(x - 1, y, z) || !chunk.isSwampBiome(x + 1, y, z)
				|| !chunk.isSwampBiome(x, y, z - 1) || !chunk.isSwampBiome(x, y, z + 1);
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
