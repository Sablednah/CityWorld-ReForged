package me.daddychurchill.CityWorld.Plats.Vanilla;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.Floating.FloatingNatureLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * Wild land in a vanilla-terrain world: a lot that draws nothing at all. The ground is vanilla's, filled before
 * CityWorld is asked, and so are its trees, lakes, animals and structures (the chunk generator hands a nature
 * chunk to vanilla for every later stage too).
 */
public class VanillaNatureLot extends FloatingNatureLot {

	public VanillaNatureLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new VanillaNatureLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return blockYs.getBlockY(x, z);
	}
}
