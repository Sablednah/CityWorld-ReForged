package me.daddychurchill.CityWorld.Support;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Block;

/**
 * One chunk's worth of live world, addressed chunk-relative (x and z in 0..15).
 *
 * <p>This is the decoration-side counterpart of {@link InitialBlocks} and the type
 * {@code ShapeProvider} names in its signatures, which is what made the whole seam a prerequisite
 * for the generation brain.
 *
 * <p>The original wrapped a Bukkit {@code Chunk} — a live handle that knew its own world and could
 * hand out blocks by chunk-relative coordinate. Modern Minecraft splits those apart, so this takes
 * the level plus the {@link ChunkPos} and does the origin arithmetic itself (via
 * {@code AbstractBlocks.getOriginX/Z}, which the {@code sectionX}/{@code sectionZ} below feed).
 *
 * <p>Note {@link #isSurroundedByEmpty} and {@link #isByWater} deliberately answer {@code false} at
 * the chunk edge rather than peer into the neighbour — the constraint that separates this from
 * {@link RelativeBlocks}, and one that matters more now than it did under Bukkit: the modern chunk
 * pipeline restricts neighbour access during generation.
 */
public final class RealBlocks extends SupportBlocks {

	public RealBlocks(CityWorldGenerator generator, LevelAccessor world, ChunkPos pos) {
		super(generator, world);

		sectionX = pos.x;
		sectionZ = pos.z;
	}

	@Override
	public Block getActualBlock(int x, int y, int z) {
		return new Block(world, getOriginX() + x, y, getOriginZ() + z);
	}

	@Override
	public boolean isSurroundedByEmpty(int x, int y, int z) {
		return (x > 0 && x < 15 && z > 0 && z < 15)
				&& (isEmpty(x - 1, y, z) && isEmpty(x + 1, y, z) && isEmpty(x, y, z - 1) && isEmpty(x, y, z + 1));
	}

	@Override
	public boolean isByWater(int x, int y, int z) {
		return (x > 0 && x < 15 && z > 0 && z < 15)
				&& (isWater(x - 1, y, z) || isWater(x + 1, y, z) || isWater(x, y, z - 1) || isWater(x, y, z + 1));
	}
}
