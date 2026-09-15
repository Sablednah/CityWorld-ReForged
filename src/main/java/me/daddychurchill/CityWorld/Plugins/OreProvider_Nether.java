package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.world.level.block.Blocks;

/**
 * The ruined-city Nether's ground (upstream's {@code OreProvider_Nether}, 1.14, brought up to date):
 * netherrack strata over blackstone, lava where the overworld has water — its seas become lava seas on a
 * magma bed — and the Nether's ores in the slots upstream's table ranks by rarity (ancient debris takes the
 * rarest). No snow, ever.
 */
public class OreProvider_Nether extends OreProvider {

	public OreProvider_Nether(CityWorldGenerator generator) {
		super(generator);

		surfaceMaterial = Material.NETHERRACK;
		subsurfaceMaterial = Material.NETHERRACK;
		stratumMaterial = Material.NETHERRACK;
		substratumMaterial = Material.BEDROCK;
		deepstratumMaterial = Material.BLACKSTONE;

		fluidMaterial = Material.LAVA;
		fluidFluidMaterial = Material.LAVA;
		fluidSurfaceMaterial = Material.MAGMA_BLOCK;
		fluidSubsurfaceMaterial = Material.NETHERRACK;
		fluidFrozenMaterial = Material.OBSIDIAN;

		// Same ten slots as the overworld table, rarest last (upstream's order, modern blocks).
		ore_types.clear();
		ore_types.add(Material.LAVA);
		ore_types.add(Material.LAVA);
		ore_types.add(Material.SOUL_SAND);
		ore_types.add(Material.MAGMA_BLOCK);
		ore_types.add(Material.GLOWSTONE);
		ore_types.add(Material.GLOWSTONE);
		ore_types.add(Material.NETHER_QUARTZ_ORE);
		ore_types.add(Material.of(Blocks.NETHER_GOLD_ORE));
		ore_types.add(Material.GILDED_BLACKSTONE);
		ore_types.add(Material.ANCIENT_DEBRIS);
	}

	/** Blackstone below y 0 where the overworld mixes in deepslate. */
	@Override
	public Material stratumMaterialAt(Material stratum, int blockX, int blockY, int blockZ) {
		return stratum == stratumMaterial && blockY < 0 ? Material.BLACKSTONE : stratum;
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
