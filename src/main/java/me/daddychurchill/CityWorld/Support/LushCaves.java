package me.daddychurchill.CityWorld.Support;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A lush-cave decoration pass (MODERN): a patch of caves gets reclaimed by a lush biome — moss- and
 * clay-lined floors, glow-berry cave vines and spore blossoms on the ceilings, dripleaf, little water
 * pools stocked with axolotls, frogs and tropical fish, and — over a nature lot — the tell-tale surface
 * azalea on rooted dirt. Runs during decoration on the already-carved caves; a subset of columns (a
 * seed-coherent "lush region") is decorated so lush caves cluster in patches rather than being everywhere.
 */
public final class LushCaves {

    private LushCaves() {}

    private static final int REGION = 80; // lush-cave patch size in blocks (big patches)
    private static final int REGION_PCT = 5; // ~this % of the coarse regions are lush (rare -> well spaced)

    public static void apply(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, Odds odds) {
        if (!(chunk instanceof RealBlocks real))
            return;
        ServerLevelAccessor level = real.getServerLevel();
        if (level == null)
            return;

        int oX = real.getOriginX(), oZ = real.getOriginZ();
        int top = generator.seaLevel - 3; // caves below the surface
        int bottom = real.minY + 6; // above bedrock

        boolean anyLush = false;
        for (int x = 0; x < real.width; x++)
            for (int z = 0; z < real.width; z++) {
                int wx = oX + x, wz = oZ + z;
                if (!lushRegion(generator, wx, wz))
                    continue;
                anyLush = true;
                decorateColumn(generator, real, level, x, z, wx, wz, top, bottom, odds);
            }

        // the surface tell — an azalea on rooted dirt above a lush cave, on a nature lot
        if (anyLush && lot.style == LotStyle.NATURE)
            surfaceAzalea(generator, real, oX, oZ, odds);
    }

    /** Coarse, seed-stable region test: ~{@link #REGION_PCT}% of {@link #REGION}-block cells are lush. */
    private static boolean lushRegion(CityWorldGenerator generator, int wx, int wz) {
        long h = (long) Math.floorDiv(wx, REGION) * 341873128712L
                + (long) Math.floorDiv(wz, REGION) * 132897987541L;
        return Math.floorMod(generator.getWorldSeed() + h, 100) < REGION_PCT;
    }

    private static void decorateColumn(CityWorldGenerator generator, RealBlocks real, ServerLevelAccessor level,
            int x, int z, int wx, int wz, int top, int bottom, Odds odds) {
        for (int y = top; y > bottom; y--) {
            if (!real.isEmpty(x, y, z))
                continue; // only carved cave air
            if (isFloor(real, x, y, z))
                decorateFloor(generator, real, x, z, y, odds);
            if (isCeiling(real, x, y, z))
                decorateCeiling(real, level, x, z, wx, y, wz, odds);
        }
    }

    private static boolean isFloor(RealBlocks real, int x, int y, int z) {
        return !real.isEmpty(x, y - 1, z) && !real.isWater(x, y - 1, z);
    }

    private static boolean isCeiling(RealBlocks real, int x, int y, int z) {
        // require a broad, thick backing so we're decorating real ceiling, not a small floating remnant the
        // cave-carving noise leaves behind (a single detached lump reads as a moss block hanging in mid-air
        // once coloured — two-thick alone wasn't enough, it just needed to be a *wide* lump too)
        if (real.isEmpty(x, y + 1, z) || real.isWater(x, y + 1, z) || real.isEmpty(x, y + 2, z))
            return false;
        int solidSides = 0;
        if (!real.isEmpty(x + 1, y + 1, z))
            solidSides++;
        if (!real.isEmpty(x - 1, y + 1, z))
            solidSides++;
        if (!real.isEmpty(x, y + 1, z + 1))
            solidSides++;
        if (!real.isEmpty(x, y + 1, z - 1))
            solidSides++;
        return solidSides >= 3;
    }

    private static void decorateFloor(CityWorldGenerator generator, RealBlocks real, int x, int z, int y, Odds odds) {
        // line the floor with moss (and the odd clay patch)
        if (odds.playOdds(0.35))
            real.setBlock(x, y - 1, z, odds.playOdds(0.22) ? Material.CLAY : Material.MOSS_BLOCK);
        // low growth / a rare pool
        double r = odds.getRandomDouble();
        if (r < 0.10)
            real.setBlock(x, y, z, Material.MOSS_CARPET);
        else if (r < 0.14)
            real.setBlock(x, y, z, Material.SMALL_DRIPLEAF);
        else if (r < 0.155)
            makePool(generator, real, x, z, y, odds);
    }

    /** A shallow 2x2 water pool dug into the floor, with a clay bed, and stocked with a couple of critters. */
    private static void makePool(CityWorldGenerator generator, RealBlocks real, int x, int z, int y, Odds odds) {
        if (x > 14 || z > 14)
            return;
        // only on a flat, open 2x2 patch
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++)
                if (!isFloor(real, x + dx, y, z + dz) || !real.isEmpty(x + dx, y, z + dz))
                    return;
        for (int dx = 0; dx <= 1; dx++)
            for (int dz = 0; dz <= 1; dz++) {
                real.setBlock(x + dx, y - 3, z + dz, Material.CLAY); // bed
                real.setBlock(x + dx, y - 2, z + dz, Material.WATER); // 2-deep so the fish sit in water
                real.setBlock(x + dx, y - 1, z + dz, Material.WATER);
            }
        // a big dripleaf on the rim, then stock the pool
        real.setBlock(x, y, z, Material.BIG_DRIPLEAF);
        generator.spawnProvider.spawnCritter(generator, real, odds, x + 1, y - 1, z, EntityType.AXOLOTL, true);
        generator.spawnProvider.spawnCritter(generator, real, odds, x, y - 1, z + 1, EntityType.TROPICAL_FISH, true);
        generator.spawnProvider.spawnCritter(generator, real, odds, x + 1, y, z + 1, EntityType.FROG, false);
    }

    private static void decorateCeiling(RealBlocks real, ServerLevelAccessor level, int x, int z, int wx, int y,
            int wz, Odds odds) {
        // mossy ceiling
        if (odds.playOdds(0.22))
            real.setBlock(x, y + 1, z, Material.MOSS_BLOCK);
        double r = odds.getRandomDouble();
        if (r < 0.12)
            hangGlowVine(real, level, x, z, wx, y, wz, odds);
        else if (r < 0.17)
            real.setBlock(x, y, z, Material.SPORE_BLOSSOM); // attaches under the solid ceiling above
    }

    /** A cave vine dangling from the ceiling — 1-4 segments while there's open air below, most bare and only
     *  the odd segment lit with a glow berry (a vine that's ALL berries reads as a string of lanterns). */
    private static void hangGlowVine(RealBlocks real, ServerLevelAccessor level, int x, int z, int wx, int y, int wz,
            Odds odds) {
        int len = 1 + odds.getRandomInt(4);
        for (int i = 0; i < len && real.isEmpty(x, y - i, z); i++)
            level.setBlock(new BlockPos(wx, y - i, wz),
                    Blocks.CAVE_VINES_PLANT.defaultBlockState().setValue(BlockStateProperties.BERRIES,
                            odds.playOdds(0.2)),
                    Block.UPDATE_CLIENTS);
    }

    /** On a nature-lot surface over a lush cave: an azalea (sometimes flowering) on rooted dirt, roots below. */
    private static void surfaceAzalea(CityWorldGenerator generator, RealBlocks real, int oX, int oZ, Odds odds) {
        // A lush REGION spans several chunks, and this runs per chunk — 6-10 azaleas in every one of them
        // stacked up into a thicket over the whole patch. Most chunks now get none, and the ones that do get
        // one or two, so a region ends up with a scattered handful: a tell you notice, not a shrubbery.
        if (!odds.playOdds(0.2))
            return;
        int tries = 1 + odds.getRandomInt(2);
        for (int i = 0; i < tries; i++) {
            int x = odds.getRandomInt(2, 13), z = odds.getRandomInt(2, 13);
            if (!lushRegion(generator, oX + x, oZ + z))
                continue; // keep the tell directly over a lush cave
            // the terrain surface at this column (any elevation — nature lots are often mountains, which the
            // old bounded scan sat above and always skipped)
            // the ACTUAL top block: first solid cell (with air above) scanning down — findBlockY is only the
            // base terrain height, a couple below the placed surface, so it never lined up.
            int surfaceY = Integer.MIN_VALUE;
            for (int y = generator.seaLevel + 140; y > generator.seaLevel; y--)
                if (!real.isEmpty(x, y, z) && real.isEmpty(x, y + 1, z)) {
                    surfaceY = y;
                    break;
                }
            // only onto actual grass — not tree canopy (floats) and not a building roof (mountain shacks);
            // grass block only occurs on real, load-bearing terrain
            if (surfaceY == Integer.MIN_VALUE || !real.isType(x, surfaceY, z, Material.GRASS_BLOCK))
                continue;
            real.setBlock(x, surfaceY, z, Material.ROOTED_DIRT);
            if (odds.playOdds(0.4))
                real.setBlock(x, surfaceY - 1, z, Material.HANGING_ROOTS); // roots dripping under the rooted dirt
            real.setBlock(x, surfaceY + 1, z, odds.playOdds(0.5) ? Material.FLOWERING_AZALEA : Material.AZALEA);
        }
    }
}
