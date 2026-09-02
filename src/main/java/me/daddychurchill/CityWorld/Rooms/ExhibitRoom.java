package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A museum exhibit cell: quartz pedestals with artifacts on top. Museums stood entirely empty
 * before the civic-interiors round.
 */
public class ExhibitRoom extends FilledRoom {

	private static final Material[] ARTIFACTS = { Material.DECORATED_POT, Material.BONE_BLOCK,
			Material.AMETHYST_CLUSTER, Material.POTTED_AZALEA, Material.GOLD_BLOCK, Material.DECORATED_POT,
			Material.FLOWER_POT, Material.CANDLE };

	public ExhibitRoom() {
	}

	@Override
	public void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y, int z,
			int width, int height, int depth, BlockFace sideWithWall, Material materialWall, Material materialGlass) {
		// centre pedestal always (the bone block reads as the fossil exhibit); a second in a corner half the time
		pedestal(chunk, odds, x + width / 2, y, z + depth / 2);
		if (odds.flipCoin())
			pedestal(chunk, odds, x, y, z);
	}

	private void pedestal(RealBlocks chunk, Odds odds, int x, int y, int z) {
		chunk.setBlock(x, y, z, Material.QUARTZ_PILLAR);
		Material artifact = odds.getRandomMaterial(ARTIFACTS);
		if (artifact == Material.AMETHYST_CLUSTER)
			chunk.setBlock(x, y + 1, z, artifact, BlockFace.UP);
		else
			chunk.setBlock(x, y + 1, z, artifact);
	}
}
