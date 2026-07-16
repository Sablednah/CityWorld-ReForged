package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Biome;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Partial port of the original {@code OreProvider} (227 lines).
 *
 * <p><b>Wave 1.</b> The palette below — which blocks a world's strata are made of — is ported
 * verbatim and is genuinely load-bearing: {@code ShapeProvider_Normal} reads these fields for every
 * column it shapes, so they decide what the terrain is actually built from.
 *
 * <p>What is <em>not</em> here yet is the ore <em>placement</em> half ({@code ore_types},
 * {@code sprinkleOres}, {@code sprinkleSnow}), which needs {@code MaterialProvider} and the
 * decoration pass. It arrives with P4 (which also adds the deepslate variants the 1.14 vocabulary
 * never had) and P5.
 */
public class OreProvider extends Provider {

    //	public final static int lavaFluidLevel = 24;
    public final static int lavaFieldLevel = 12;

    public Material surfaceMaterial;
    public Material subsurfaceMaterial;
    public Material stratumMaterial;
    public Material substratumMaterial;

    public Material fluidMaterial;
    public Material fluidFluidMaterial;
    public Material fluidSurfaceMaterial;
    public Material fluidSubsurfaceMaterial;
    public Material fluidFrozenMaterial;

    OreProvider(CityWorldGenerator generator) {
        super();

        surfaceMaterial = Material.GRASS_BLOCK;
        subsurfaceMaterial = Material.DIRT;
        stratumMaterial = Material.STONE;
        substratumMaterial = Material.BEDROCK;

        fluidMaterial = Material.WATER;
        fluidFluidMaterial = Material.WATER;
        fluidSurfaceMaterial = Material.SAND;
        fluidSubsurfaceMaterial = Material.GRAVEL;
        fluidFrozenMaterial = Material.PACKED_ICE;
    }

    // Based on work contributed by drew-bahrue
    // (https://github.com/echurchill/CityWorld/pull/2)
    public static OreProvider loadProvider(CityWorldGenerator generator) {

        // The original switches on worldStyle over 8 variants (_Astral, _Nether, _TheEnd,
        // _SandDunes, _SnowDunes, _Decayed, _Normal). Only the stock palette is wired up for
        // wave 1; the variants land with their matching ShapeProviders. See PORTING.md.
        return new OreProvider(generator);
    }

    /**
     * Lets a world style override the biome a column was going to get. The stock provider is the
     * identity — only the Nether/End/Astral variants actually remap.
     */
    public Biome remapBiome(Biome biome) {
        return biome;
    }
}
