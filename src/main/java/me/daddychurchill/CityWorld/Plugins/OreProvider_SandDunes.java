package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;

class OreProvider_SandDunes extends OreProvider {

	public OreProvider_SandDunes(CityWorldGenerator generator) {
		super(generator);

		fluidMaterial = Material.SAND;
		fluidFluidMaterial = Material.SAND;
		fluidSurfaceMaterial = Material.SAND;
		fluidSubsurfaceMaterial = Material.SANDSTONE;
		fluidFrozenMaterial = Material.SANDSTONE;
	}
}
