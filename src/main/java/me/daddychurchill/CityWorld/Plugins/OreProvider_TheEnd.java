package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.world.level.block.Blocks;

/**
 * The End's ground (upstream's {@code OreProvider_TheEnd}, modernised): end stone all the way down, and
 * <b>no fluids at all</b> — where the overworld would fill a sea, the End has void.
 *
 * <p>Upstream filled those with packed ice, which was a 1.14 stand-in for "not water"; here the fluid
 * materials are air, so CityWorld's seas simply become the gaps between islands. The "ores" are the End's own
 * accents (obsidian, purpur, glowstone) rather than overworld veins — there is no iron in end stone.
 */
public class OreProvider_TheEnd extends OreProvider {

	public OreProvider_TheEnd(CityWorldGenerator generator) {
		super(generator);

		Material endStone = Material.of(Blocks.END_STONE);
		surfaceMaterial = endStone;
		subsurfaceMaterial = endStone;
		stratumMaterial = endStone;
		// No bedrock floor: the End is void underneath, and falling off an island should mean falling, not
		// landing (measured: 708 bedrock blocks under a 9-chunk sample, 2026-09-16).
		substratumMaterial = Material.AIR;
		deepstratumMaterial = endStone;

		// No water, no lava: the End's "sea" is empty space.
		fluidMaterial = Material.AIR;
		fluidFluidMaterial = Material.AIR;
		fluidSurfaceMaterial = endStone;
		fluidSubsurfaceMaterial = endStone;
		fluidFrozenMaterial = endStone;

		ore_types.clear();
		ore_types.add(endStone);
		ore_types.add(endStone);
		ore_types.add(Material.of(Blocks.END_STONE_BRICKS));
		ore_types.add(Material.of(Blocks.PURPUR_BLOCK));
		ore_types.add(Material.GLOWSTONE);
		ore_types.add(Material.of(Blocks.PURPUR_PILLAR));
		ore_types.add(Material.of(Blocks.END_STONE_BRICKS));
		ore_types.add(Material.OBSIDIAN);
		ore_types.add(Material.GLOWSTONE);
		ore_types.add(Material.OBSIDIAN);
	}

	/** End stone stays end stone: no deepslate band down here. */
	@Override
	public Material stratumMaterialAt(Material stratum, int blockX, int blockY, int blockZ) {
		return stratum;
	}

	@Override
	public void sprinkleSnow(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int x1, int x2, int y,
			int z1, int z2) {
	}

	@Override
	public void dropSnow(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {
	}

	@Override
	public void dropSnow(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z, double level) {
	}
}
