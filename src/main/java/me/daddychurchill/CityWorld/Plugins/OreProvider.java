package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Biome;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;

/**
 * Partial port of the original {@code OreProvider} (227 lines).
 *
 * <p><b>Wave 1.</b> The palette below — which blocks a world's strata are made of — is ported
 * verbatim and is genuinely load-bearing: {@code ShapeProvider_Normal} reads these fields for every
 * column it shapes, so they decide what the terrain is actually built from.
 *
 * <p>P4 has since added the deep stratum on top of that palette: {@link #stratumMaterialAt} blends
 * stone into deepslate down the new -64..0 underground, which 1.14 had no concept of.
 *
 * <p>What is <em>not</em> here yet is the ore <em>placement</em> half ({@code ore_types},
 * {@code sprinkleOres}, {@code sprinkleSnow}), which needs {@code MaterialProvider} and the
 * decoration pass — P5. That is also when the deepslate <em>ore</em> variants
 * ({@code deepslate_coal_ore} and friends) are needed, so that ores placed in the deep stratum look
 * right; only plain {@code DEEPSLATE} exists so far.
 */
public class OreProvider extends Provider {

    //	public final static int lavaFluidLevel = 24;

    /**
     * How far above bedrock the lava fields reach.
     *
     * <p>Upstream this was an <em>absolute</em> Y of 12 — which was 12 blocks above bedrock, since
     * the 1.14 world floor was 0. With the floor now at -64 an absolute 12 would flood 76 blocks
     * instead of 12, so what it always meant — a depth above the floor — is now stated as one.
     * See {@link #lavaFieldLevel}.
     */
    public final static int lavaFieldDepth = 12;

    /** The absolute Y that {@link #lavaFieldDepth} works out to for this world. */
    public final int lavaFieldLevel;

    public Material surfaceMaterial;
    public Material subsurfaceMaterial;
    public Material stratumMaterial;
    public Material substratumMaterial;
    /** The deep stratum, below {@link #deepslateTopY}. P4; 1.14 had no such block. */
    public Material deepstratumMaterial;

    public Material fluidMaterial;
    public Material fluidFluidMaterial;
    public Material fluidSurfaceMaterial;
    public Material fluidSubsurfaceMaterial;
    public Material fluidFrozenMaterial;

    /**
     * The stone→deepslate transition band, matching vanilla: solid deepslate at or below
     * {@link #deepslateBottomY}, solid stone at or above {@link #deepslateTopY}, and a ragged
     * noise-blended mix between.
     */
    private final static int deepslateTopY = 0;
    private final static int deepslateBottomY = -8;

    /**
     * Noise for the deepslate blend.
     *
     * <p>Seeded off the world seed at a fixed offset rather than through
     * {@code CityWorldGenerator.getRelatedSeed()}: that counter advances per call, so drawing from
     * it here would shift every provider constructed afterwards and change the whole world. Offsets
     * 0–4 are already taken by the ShapeProviders' noise fields, hence 5.
     *
     * <p>Sampled per block, which is safe because the vendored generator offsets its coordinates by
     * a random amount, so integer inputs never land on the noise lattice. Being seed-derived, it is
     * deterministic — required, since chunks generate on many threads.
     */
    private final SimplexNoiseGenerator deepslateShape;

    OreProvider(CityWorldGenerator generator) {
        super();

        surfaceMaterial = Material.GRASS_BLOCK;
        subsurfaceMaterial = Material.DIRT;
        stratumMaterial = Material.STONE;
        substratumMaterial = Material.BEDROCK;
        deepstratumMaterial = Material.DEEPSLATE;

        fluidMaterial = Material.WATER;
        fluidFluidMaterial = Material.WATER;
        fluidSurfaceMaterial = Material.SAND;
        fluidSubsurfaceMaterial = Material.GRAVEL;
        fluidFrozenMaterial = Material.PACKED_ICE;

        lavaFieldLevel = generator.worldMinY + lavaFieldDepth;
        deepslateShape = new SimplexNoiseGenerator(generator.getWorldSeed() + 5);
    }

    /**
     * The stratum block to use at a given depth — deepslate deep down, blending up into stone.
     *
     * <p>Takes the stratum the caller was <em>going</em> to place and only substitutes when it is
     * this world's ordinary stone. That keeps the deepslate band out of world styles whose strata
     * are something else entirely (the Nether/End/Astral providers), where a deepslate seam would
     * make no sense.
     *
     * @param stratum the stratum material the caller intended to place
     * @param blockX  world X — the blend is 3D, so it needs real coordinates, not chunk-local ones
     * @param blockY  world Y
     * @param blockZ  world Z
     */
    public Material stratumMaterialAt(Material stratum, int blockX, int blockY, int blockZ) {
        if (stratum != stratumMaterial || blockY >= deepslateTopY)
            return stratum;
        if (blockY <= deepslateBottomY)
            return deepstratumMaterial;

        // Between the two: the chance of deepslate ramps from 0 at the top of the band to 1 at the
        // bottom, so the seam comes out ragged rather than flat.
        double deepness = (double) (deepslateTopY - blockY) / (deepslateTopY - deepslateBottomY);
        double noise = (deepslateShape.noise(blockX, blockY, blockZ) + 1.0) / 2.0;
        return noise < deepness ? deepstratumMaterial : stratum;
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

    public void dropSnow(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {
        dropSnow(generator, chunk, x, y, z, 0);
    }

    /** Lays a snow layer on whatever it lands on. Ported verbatim — it is plain block work. */
    public void dropSnow(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z, double level) {
        y = chunk.findLastEmptyBelow(x, y + 1, z, y - 6);
        if (!chunk.isOfTypes(x, y - 1, z, Material.AIR, Material.SNOW))
            chunk.setBlock(x, y, z, Material.SNOW, level);
    }

    /**
     * Scatters ore veins and fluid pockets through a chunk's strata.
     *
     * <p>P5, with the rest of the ore-placement half — it runs in the decoration pass, which is not
     * driven yet, and wants the deepslate ore variants that only exist from 1.17 on.
     */
    public void sprinkleOres(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
            AbstractCachedYs blockYs, Odds odds) {
    }
}
