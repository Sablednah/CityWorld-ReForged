package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Colors.ColorSet;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.world.level.block.Blocks;

/**
 * The ruined-city Nether's plant cover (upstream's {@code CoverProvider_Nether}, modernised). Sparse, like the
 * decayed cover it extends. Whatever the overworld would have grown becomes what the ground under it can hold:
 * roots, sprouts and fungus on crimson/warped nylium (the biome ground map lays the nylium), the odd soul fire
 * on soul soil, nothing on bare netherrack; crops become netherwart.
 *
 * <p><b>Trees become Nether trees.</b> Every tree a park, yard or avenue asks for is drawn from the
 * {@code #cityworld:nether_trees} configured-feature tag — vanilla's huge crimson and warped fungi and huge
 * red and brown mushrooms, plus Biomes O' Plenty's hellbark trees when it is installed — placed as the real
 * vanilla/mod feature on the live level. Huge fungi only grow on their own nylium, so that is laid under the
 * spot first (a mushroom accepts either). A tree that will not place falls back to a sprout, so a crowded
 * spot still gets something.
 */
public class CoverProvider_Nether extends CoverProvider_Decayed {

	private static final Material CRIMSON_ROOTS = Material.of(Blocks.CRIMSON_ROOTS);
	private static final Material WARPED_ROOTS = Material.of(Blocks.WARPED_ROOTS);
	private static final Material NETHER_SPROUTS = Material.of(Blocks.NETHER_SPROUTS);
	private static final Material SOUL_SOIL = Material.of(Blocks.SOUL_SOIL);
	private static final Material SOUL_FIRE = Material.of(Blocks.SOUL_FIRE);

	public CoverProvider_Nether(Odds odds) {
		super(odds);
	}

	@Override
	public ColorSet getColorSet() {
		return ColorSet.NETHER;
	}

	@Override
	void setCoverage(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			CoverageType coverageType) {
		switch (coverageType) {
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case NETHERWART:
		case FIRE:
			super.setCoverage(generator, chunk, x, y, z, coverageType);
			return;
		default:
			break;
		}
		// By name, so the mapping survives the enum gaining or losing a plant.
		String name = coverageType.name();
		if (name.equals("NOTHING") || name.equals("CACTUS") || name.equals("REED") || name.endsWith("CORAL")
				|| name.equals("KELP") || name.equals("SEAGRASS"))
			return;
		if (name.equals("WHEAT") || name.equals("CARROTS") || name.equals("POTATO") || name.equals("BEETROOT")
				|| name.equals("MELON") || name.equals("PUMPKIN")) {
			super.setCoverage(generator, chunk, x, y, z, CoverageType.NETHERWART);
			return;
		}
		boolean tree = name.contains("TREE") || name.contains("TRUNK");
		if (tree && hugeTree(chunk, x, y, z))
			return;
		flora(chunk, x, y, z, tree || name.contains("SAPLING"));
	}

	private static final net.minecraft.tags.TagKey<net.minecraft.world.level.levelgen.feature.ConfiguredFeature<?, ?>> NETHER_TREES =
			net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE,
					new net.minecraft.resources.ResourceLocation("cityworld", "nether_trees"));

	/** Grows a tree from {@link #NETHER_TREES} at (x, y, z) on the live level; false if it could not. */
	private boolean hugeTree(SupportBlocks chunk, int x, int y, int z) {
		if (!(chunk instanceof me.daddychurchill.CityWorld.Support.RealBlocks real)
				|| !(real.getServerLevel() instanceof net.minecraft.world.level.WorldGenLevel level))
			return false;
		var pool = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.CONFIGURED_FEATURE)
				.get(NETHER_TREES);
		if (pool.isEmpty() || pool.get().size() == 0)
			return false;
		var pick = pool.get().get(odds.getRandomInt(pool.get().size()));
		net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(
				me.daddychurchill.CityWorld.Support.AbstractBlocks.getBlockX(chunk.sectionX, x), y,
				me.daddychurchill.CityWorld.Support.AbstractBlocks.getBlockZ(chunk.sectionZ, z));
		// Huge fungi insist on their own nylium; mushrooms (and hellbark) take either.
		boolean warped = pick.unwrapKey().map(k -> k.location().getPath().contains("warped")).orElse(false);
		level.setBlock(pos.below(), (warped ? net.minecraft.world.level.block.Blocks.WARPED_NYLIUM
				: net.minecraft.world.level.block.Blocks.CRIMSON_NYLIUM).defaultBlockState(), 2);
		return pick.value().place(level, level.getLevel().getChunkSource().getGenerator(),
				net.minecraft.util.RandomSource.create(odds.getRandomLong()), pos);
	}

	private void flora(SupportBlocks chunk, int x, int y, int z, boolean big) {
		if (chunk.isOfTypes(x, y - 1, z, Material.CRIMSON_NYLIUM))
			chunk.setBlock(x, y, z, big ? Material.CRIMSON_FUNGUS : CRIMSON_ROOTS);
		else if (chunk.isOfTypes(x, y - 1, z, Material.WARPED_NYLIUM))
			chunk.setBlock(x, y, z, big ? Material.WARPED_FUNGUS : odds.flipCoin() ? WARPED_ROOTS : NETHER_SPROUTS);
		else if (chunk.isOfTypes(x, y - 1, z, SOUL_SOIL, Material.SOUL_SAND) && odds.playOdds(Odds.oddsSomewhatUnlikely))
			chunk.setBlock(x, y, z, SOUL_FIRE);
	}
}
