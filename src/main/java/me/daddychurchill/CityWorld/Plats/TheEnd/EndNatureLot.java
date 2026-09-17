package me.daddychurchill.CityWorld.Plats.TheEnd;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.Floating.FloatingNatureLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * Wild End: a lot that draws nothing at all. The island under it is vanilla's, filled before CityWorld is asked,
 * and its chorus plants are vanilla's too (the chunk generator runs the biome's own decoration over nature lots).
 */
public class EndNatureLot extends FloatingNatureLot {

	public EndNatureLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new EndNatureLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return blockYs.getBlockY(x, z);
	}
}
