package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Block;

/**
 * Another section's coordinate space, but free to reach outside it.
 *
 * <p>Same origin as the {@link SupportBlocks} it is built from, and addressed the same
 * chunk-relative way — the difference is that it goes straight to the world instead of through a
 * chunk, so an x or z outside 0..15 simply lands in the neighbouring chunk rather than being
 * refused. {@link RealBlocks} clamps at the edge; this is what the generator reaches for when it
 * needs to draw across one (roads and parks joining up, for instance).
 *
 * <p>It borrows the level from the section it is relative to, so nothing extra is needed to build
 * one.
 */
public final class RelativeBlocks extends SupportBlocks {

	private final int originX;
	private final int originZ;

	public RelativeBlocks(CityWorldGenerator generator, SupportBlocks relative) {
		super(generator, relative.world);

		this.originX = relative.getOriginX();
		this.originZ = relative.getOriginZ();
	}

	@Override
	public Block getActualBlock(int x, int y, int z) {
		return new Block(world, originX + x, y, originZ + z);
	}

	@Override
	public boolean isSurroundedByEmpty(int x, int y, int z) {
		return isEmpty(x - 1, y, z) && isEmpty(x + 1, y, z) && isEmpty(x, y, z - 1) && isEmpty(x, y, z + 1);
	}

	@Override
	public boolean isByWater(int x, int y, int z) {
		return isWater(x - 1, y, z) || isWater(x + 1, y, z) || isWater(x, y, z - 1) || isWater(x, y, z + 1);
	}
}
