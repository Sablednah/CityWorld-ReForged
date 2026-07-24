package me.daddychurchill.CityWorld.Plugins;

import java.util.ArrayList;
import java.util.List;

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
 * <p><b>CLASSIC ores.</b> {@link #sprinkleOres} is now ported (it was a stub — MODERN reuses
 * vanilla's own {@code UNDERGROUND_ORES} step, so CityWorld's own vein pass is the CLASSIC path).
 * {@link me.daddychurchill.CityWorld.Plats.PlatLot#generateOres} gates MODERN off so the two never
 * stack. Upstream's 1.14 ore tables are Y-values in a 0-based world; the world floor is now -64, so
 * the primary placement is shifted down by {@code worldMinY} — a clean 1:1 remap of the old 0..128
 * column onto the new -64..64 underground that lands the deep ores (diamond, redstone) near the new
 * bedrock. Ores landing in the deepslate stratum (below {@link #deepslateTopY}) become their
 * deepslate variant so they read right; see {@link #deepVariant}. Snow placement
 * ({@link #sprinkleSnow}) stays a no-op on the stock palette (the surface pass handles it).
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

    private final static double oreSprinkleOdds = Odds.oddsLessLikely;

    /** Which materials each strata vein is made of — populated in the constructor. */
    List<Material> ore_types = new ArrayList<>();

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

        // Upstream populated ore_types from MaterialProvider.itemsMaterialListFor_NormalOres, whose
        // getNthMaterial(n, default) just returns the default when unconfigured; the port has no such
        // list, so the defaults go in directly. Every original style extended OreProvider_Normal and
        // so shared this table — the port's dune/astral variants extend this base for the same reason.
        ore_types.add(Material.WATER);
        ore_types.add(Material.LAVA);
        ore_types.add(Material.GRAVEL);
        ore_types.add(Material.COAL_ORE);
        ore_types.add(Material.IRON_ORE);
        ore_types.add(Material.GOLD_ORE);
        ore_types.add(Material.LAPIS_ORE);
        ore_types.add(Material.REDSTONE_ORE);
        ore_types.add(Material.DIAMOND_ORE);
        ore_types.add(Material.EMERALD_ORE);
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

        // The original switches on worldStyle over several variants (_Astral, _Nether, _TheEnd,
        // _SandDunes, _SnowDunes, _Decayed, _Normal). The style variants land with their matching
        // ShapeProviders at P8; the Nether/End branches are unreachable (overworld only), and the
        // _Decayed branch stays on the stock palette until OreProvider_Decayed is ported. The base
        // OreProvider is the port's Normal palette.
        switch (generator.worldStyle) {
        case SANDDUNES:
            return new OreProvider_SandDunes(generator);
        case SNOWDUNES:
            return new OreProvider_SnowDunes(generator);
        case ASTRAL:
            return new OreProvider_Astral(generator);
        default:
            return new OreProvider(generator);
        }
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
     * Populates the world with ores.
     *
     * <p>original authors Nightgunner5, Markus Persson; modified by simplex; wildly modified by
     * daddychurchill. Ported for the CLASSIC path — MODERN uses vanilla's own ore veins instead.
     */

    //                                                  WATER LAVA GRAV COAL IRON GOLD LAPIS REDST DIAM EMER
    private static final int[] ore_iterations = new int[] {  8,   6,  40,  30,  12,   4,    2,    4,   2,  10 };
    private static final int[] ore_amountToDo = new int[] {  1,   1,  12,   8,   8,   3,    3,   10,   3,   1 };
    private static final int[] ore_maxY       = new int[] {128,  32, 111, 128,  61,  29,   25,   16,  15,  32 };
    private static final int[] ore_minY       = new int[] { 32,   2,  40,  16,  10,   8,    8,    6,   2,   2 };
    private static final boolean[] ore_upper  = new boolean[] { true, false, false, true, true, true, true, true, false, false };
    private static final boolean[] ore_liquid = new boolean[] { true, true, false, false, false, false, false, false, false, false };

    public void sprinkleOres(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
            AbstractCachedYs blockYs, Odds odds) {

        // do it... maybe!
        int oreCount = Math.min(ore_types.size(), ore_iterations.length);
        for (int typeNdx = 0; typeNdx < oreCount; typeNdx++) {
            sprinkleOre(generator, lot, chunk, blockYs, odds, ore_types.get(typeNdx), ore_maxY[typeNdx],
                    ore_minY[typeNdx], ore_iterations[typeNdx], ore_amountToDo[typeNdx], ore_upper[typeNdx],
                    ore_liquid[typeNdx]);
        }
    }

    private void sprinkleOre(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, AbstractCachedYs blockYs,
            Odds odds, Material material, int maxY, int minY, int iterations, int amount, boolean mirror,
            boolean liquid) {

        // do we do this one?
        if ((liquid && generator.getSettings().includeUndergroundFluids)
                || (!liquid && generator.getSettings().includeOres)) {
            if (material != stratumMaterial) {

                // sprinkle it around!
                int range = maxY - minY;
                for (int iter = 0; iter < iterations; iter++) {
                    int x = odds.getRandomInt(16);
                    // The table is a 0-based-world Y; the floor is now worldMinY, so shift the primary
                    // band down by it — a 1:1 remap of the old 0..128 column onto -64..64.
                    int y = odds.getRandomInt(range) + minY + generator.worldMinY;
                    int z = odds.getRandomInt(16);
                    if (y < blockYs.getBlockY(x, z))
                        growVein(generator, lot, chunk, blockYs, odds, x, y, z, amount, material);
                    if (mirror) {
                        // The upper reflection lands ore in mountain cores, which sit in surface
                        // coordinates (unchanged by the floor drop) — so this half is NOT shifted.
                        y = (generator.seaLevel + generator.landRange) - minY - odds.getRandomInt(range);
                        if (y < blockYs.getBlockY(x, z))
                            growVein(generator, lot, chunk, blockYs, odds, x, y, z, amount, material);
                    }
                }
            }
        }
    }

    private void growVein(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, AbstractCachedYs blockYs,
            Odds odds, int originX, int originY, int originZ, int amountToDo, Material material) {
        int trysLeft = amountToDo * 2;
        int oresDone = 0;
        if (lot.isValidStrataY(generator, originX, originY, originZ)
                && blockYs.getBlockY(originX, originZ) > originY + amountToDo / 4) {
            while (oresDone < amountToDo && trysLeft > 0) {

                // shimmy
                int x = chunk.clampXZ(originX + odds.getRandomInt(Math.max(1, amountToDo / 2)) - amountToDo / 4);
                int y = chunk.clampY(originY + odds.getRandomInt(Math.max(1, amountToDo / 4)) - amountToDo / 8);
                int z = chunk.clampXZ(originZ + odds.getRandomInt(Math.max(1, amountToDo / 2)) - amountToDo / 4);

                // ore it is
                oresDone += placeOre(generator, chunk, odds, x, y, z, amountToDo - oresDone, material);

                // one less try to try
                trysLeft--;
            }
        }
    }

    private int placeOre(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int centerX, int centerY,
            int centerZ, int oresToDo, Material material) {
        int count = 0;
        // Upstream guarded centerY > 0 (the 1.14 world floor); the floor is now minY.
        if (centerY > chunk.minY && centerY < chunk.height) {
            if (placeBlock(chunk, odds, centerX, centerY, centerZ, material)) {
                count++;
                if (count < oresToDo && centerX < 15
                        && placeBlock(chunk, odds, centerX + 1, centerY, centerZ, material))
                    count++;
                if (count < oresToDo && centerX > 0 && placeBlock(chunk, odds, centerX - 1, centerY, centerZ, material))
                    count++;
                if (count < oresToDo && centerZ < 15
                        && placeBlock(chunk, odds, centerX, centerY, centerZ + 1, material))
                    count++;
                if (count < oresToDo && centerZ > 0 && placeBlock(chunk, odds, centerX, centerY, centerZ - 1, material))
                    count++;
            }
        }
        return count;
    }

    private boolean placeBlock(SupportBlocks chunk, Odds odds, int x, int y, int z, Material material) {
        if (odds.playOdds(oreSprinkleOdds)) {
            // Replace this world's ordinary stone with the ore. Below the deepslate line the stratum is
            // deepslate (see stratumMaterialAt) — 1.14 had no such block, so upstream never met this;
            // swap in the ore's deepslate variant there so it reads right and actually places at all.
            if (chunk.isType(x, y, z, stratumMaterial)) {
                chunk.setBlock(x, y, z, material);
                return true;
            }
            if (chunk.isType(x, y, z, deepstratumMaterial)) {
                chunk.setBlock(x, y, z, deepVariant(material));
                return true;
            }
        }
        return false;
    }

    /** The deepslate form of an ore, for veins that land in the deep stratum. Non-ores pass through. */
    private static Material deepVariant(Material material) {
        if (material == Material.COAL_ORE)
            return Material.DEEPSLATE_COAL_ORE;
        if (material == Material.IRON_ORE)
            return Material.DEEPSLATE_IRON_ORE;
        if (material == Material.GOLD_ORE)
            return Material.DEEPSLATE_GOLD_ORE;
        if (material == Material.LAPIS_ORE)
            return Material.DEEPSLATE_LAPIS_ORE;
        if (material == Material.REDSTONE_ORE)
            return Material.DEEPSLATE_REDSTONE_ORE;
        if (material == Material.DIAMOND_ORE)
            return Material.DEEPSLATE_DIAMOND_ORE;
        if (material == Material.EMERALD_ORE)
            return Material.DEEPSLATE_EMERALD_ORE;
        // gravel and the fluids have no deepslate form — they sit in deepslate as-is
        return material;
    }

    /**
     * Lays a patch of snow over a region. The stock palette does nothing here (snow is placed via the
     * surface pass); the snow/astral styles override it. Kept as the seam upstream declared so those
     * overrides bind.
     */
    public void sprinkleSnow(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
    }
}
