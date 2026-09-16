package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Colors.ColorSet;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.world.level.block.Blocks;

/**
 * The End's plant cover (upstream's {@code CoverProvider_TheEnd}, modernised). Almost nothing grows on end
 * stone: the overworld's flowers, crops and grass become bare ground, and the occasional chorus flower stands
 * in for a plant. Full chorus <em>trees</em> are vanilla's own decoration on wild land (the
 * {@code chorus_plant} placed feature of end highlands/midlands), so this does not try to grow them itself.
 */
public class CoverProvider_TheEnd extends CoverProvider_Normal {

	private static final Material CHORUS_FLOWER = Material.of(Blocks.CHORUS_FLOWER);
	private static final Material END_STONE = Material.of(Blocks.END_STONE);

	public CoverProvider_TheEnd(Odds odds) {
		super(odds);
	}

	@Override
	public ColorSet getColorSet() {
		return ColorSet.THEEND;
	}

	@Override
	void setCoverage(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
			CoverageType coverageType) {
		String name = coverageType.name();
		// Mushrooms and fire behave as they do anywhere; everything else is either a chorus flower or nothing.
		switch (coverageType) {
		case BROWN_MUSHROOM:
		case RED_MUSHROOM:
		case FIRE:
			super.setCoverage(generator, chunk, x, y, z, coverageType);
			return;
		default:
			break;
		}
		if (name.equals("NOTHING") || name.contains("TREE") || name.contains("TRUNK") || name.contains("SAPLING")
				|| name.equals("CACTUS") || name.equals("REED") || name.endsWith("CORAL") || name.equals("KELP")
				|| name.equals("SEAGRASS"))
			return;
		if (chunk.isOfTypes(x, y - 1, z, END_STONE) && odds.playOdds(Odds.oddsSomewhatUnlikely))
			chunk.setBlock(x, y, z, CHORUS_FLOWER);
	}
}
