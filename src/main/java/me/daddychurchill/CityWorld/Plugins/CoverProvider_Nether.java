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
 * on soul soil, nothing on bare netherrack; crops become netherwart. Trees are left to vanilla's own Nether
 * decoration on wild land (huge fungi, basalt columns), which runs on the modern styles.
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
		boolean big = name.contains("TREE") || name.contains("TRUNK") || name.contains("SAPLING");
		flora(chunk, x, y, z, big);
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
