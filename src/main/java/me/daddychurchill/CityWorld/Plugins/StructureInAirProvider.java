package me.daddychurchill.CityWorld.Plugins;

import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.Colors;
import me.daddychurchill.CityWorld.Support.Colors.ColorSet;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The things floating above the world: hot-air balloons and blimps moored to rooftops, free-floating
 * balloons/saucers over the wild, and (via {@link #generateBlimp}) the rare sky airship. Ported from
 * upstream's {@code StructureInAirProvider}; drawn in the decoration pass, in-chunk only.
 */
public class StructureInAirProvider extends Provider {

    public final static int hotairBalloonHeight = 30;

    public static StructureInAirProvider loadProvider(CityWorldGenerator generator) {
        return new StructureInAirProvider();
    }

    /** A small balloon moored to a roof: a string up from (attachX, attachY, attachZ) to a colour-blocked
     *  teardrop envelope. No-op if there's nothing solid at the attach point to tie to. */
    public void generateBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachX,
            int attachY, int attachZ, Odds odds) {
        int bx = attachX, bz = attachZ;
        int by1 = attachY + 5 + odds.getRandomInt(10);
        int by2 = by1 + 8 + odds.getRandomInt(3);

        if (!attachString(chunk, bx, attachY, by1, bz))
            return;

        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material primary = colors.getConcrete();
        Material secondary = colors.getConcrete();

        chunk.setBlocks(bx, bx + 1, by1, by1 + 2, bz, bz + 1, primary);

        chunk.setBlocks(bx - 1, bx + 2, by1 + 2, by1 + 4, bz - 1, bz + 2, primary);
        chunk.setBlock(bx - 1, by1 + 2, bz - 1, Material.AIR);
        chunk.setBlock(bx - 1, by1 + 2, bz + 1, Material.AIR);
        chunk.setBlock(bx + 1, by1 + 2, bz - 1, Material.AIR);
        chunk.setBlock(bx + 1, by1 + 2, bz + 1, Material.AIR);

        chunk.setBlocks(bx - 2, bx + 3, by1 + 4, by1 + 6, bz - 2, bz + 3, primary);
        chunk.setBlock(bx - 2, by1 + 4, bz - 2, Material.AIR);
        chunk.setBlock(bx - 2, by1 + 4, bz + 2, Material.AIR);
        chunk.setBlock(bx + 2, by1 + 4, bz - 2, Material.AIR);
        chunk.setBlock(bx + 2, by1 + 4, bz + 2, Material.AIR);

        chunk.setBlocks(bx - 2, bx + 3, by1 + 6, by2 - 1, bz - 2, bz + 3, secondary);
        chunk.setBlocks(bx - 2, bx + 3, by2 - 1, by2, bz - 2, bz + 3, primary);
        chunk.setBlocks(bx - 1, bx + 2, by2, by2 + 1, bz - 1, bz + 2, primary);
    }

    /** A hot-air balloon floating at {@code bottomY}: a concrete basket with an occupant, four strings up
     *  to a hollow envelope with a fire burner. This is the free-floating one (its own basket is the
     *  anchor), used by the nature/farm airborne lots. */
    public void generateHotairBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context,
            int bottomY, Odds odds) {
        int balloonY1 = bottomY + 6;
        int balloonY2 = balloonY1 + 20;

        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material basket = colors.getConcrete();
        chunk.setBlocks(6, 10, bottomY, 6, 10, basket);
        chunk.setWalls(5, 11, bottomY + 1, 5, 11, basket);
        chunk.setWalls(5, 11, bottomY + 2, 5, 11, basket);

        generator.spawnProvider.spawnBeing(generator, chunk, odds, 7, bottomY + 1, 7);

        attachString(chunk, 5, bottomY + 2, balloonY1, 5);
        attachString(chunk, 5, bottomY + 2, balloonY1, 10);
        attachString(chunk, 10, bottomY + 2, balloonY1, 5);
        attachString(chunk, 10, bottomY + 2, balloonY1, 10);

        drawBigBalloon(generator, chunk, balloonY1, balloonY2, odds, true);
    }

    /** A big balloon moored to a roof (the standalone envelope), if it can be strung to the roof. */
    public void generateBigBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachY,
            Odds odds) {
        int balloonY1 = attachY + 4 + odds.getRandomInt(4);
        int balloonY2 = balloonY1 + 15 + odds.getRandomInt(15);

        boolean strung = attachString(chunk, 7 + odds.getRandomInt(2), attachY, balloonY1 + 5, 4);
        strung = attachString(chunk, 7 + odds.getRandomInt(2), attachY, balloonY1 + 5, 11) || strung;
        strung = attachString(chunk, 4, attachY, balloonY1 + 5, 7 + odds.getRandomInt(2)) || strung;
        strung = attachString(chunk, 11, attachY, balloonY1 + 5, 7 + odds.getRandomInt(2)) || strung;

        if (strung)
            drawBigBalloon(generator, chunk, balloonY1, balloonY2, odds, false);
    }

    /** A free-floating blimp/airship centred at {@code y}: a solid elongated envelope with a small gondola
     *  slung underneath. Its own gondola is the anchor, so it needs nothing below it. */
    public void generateBlimp(CityWorldGenerator generator, SupportBlocks chunk, int y, Odds odds) {
        int gondolaY = y;
        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material hull = colors.getConcrete();

        // a little gondola cabin
        chunk.setBlocks(6, 10, gondolaY, 6, 10, hull);
        chunk.setWalls(6, 10, gondolaY + 1, 6, 10, hull);
        chunk.setBlocks(6, 10, gondolaY + 2, 6, 10, hull);
        generator.spawnProvider.spawnBeing(generator, chunk, odds, 7, gondolaY + 1, 7);

        // strings up to the envelope
        int envY1 = gondolaY + 6;
        for (int[] p : new int[][] { { 7, 6 }, { 8, 6 }, { 7, 9 }, { 8, 9 } })
            chunk.setBlocks(p[0], gondolaY + 2, envY1, p[1], Material.IRON_BARS);

        drawBigBalloon(generator, chunk, envY1, envY1 + 12 + odds.getRandomInt(6), odds, false);
    }

    /** The saucer a bunker occasionally keeps (called with legs off from the air lots, on from the bunker). */
    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int y, boolean drawLegs) {
        generateSaucer(generator, chunk, 7, y, 7, drawLegs);
    }

    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
            boolean drawLegs) {
        if (drawLegs) {
            drawSaucer(chunk, x, y + 2, z);
            chunk.setBlocks(x - 3, y, y + 2, z - 3, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x + 2, y, y + 2, z - 3, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x - 3, y, y + 2, z + 2, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x + 2, y, y + 2, z + 2, Material.QUARTZ_BLOCK);
        } else
            drawSaucer(chunk, x, y, z);
    }

    // ---------------------------------------------------------------------------------------------

    private void drawBigBalloon(CityWorldGenerator generator, SupportBlocks chunk, int balloonY1, int balloonY2,
            Odds odds, boolean hollow) {
        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material primary = colors.getConcrete();
        Material secondary = hollow ? colors.getGlass() : colors.getConcrete();

        // bottom taper
        chunk.setCircle(8, 8, 3, balloonY1 - 1, primary, false);
        chunk.setCircle(8, 8, 4, balloonY1, balloonY1 + 4, primary, true);
        chunk.setCircle(8, 8, 5, balloonY1 + 4, balloonY1 + 7, primary, true);
        chunk.setCircle(8, 8, 6, balloonY1 + 7, primary, true);
        if (hollow) {
            chunk.setCircle(8, 8, 3, balloonY1, balloonY1 + 4, Material.AIR, true);
            chunk.setCircle(8, 8, 4, balloonY1 + 4, balloonY1 + 7, Material.AIR, true);
            chunk.setCircle(8, 8, 5, balloonY1 + 7, Material.AIR, true);
        }

        // striped middle
        int step = 2 + odds.getRandomInt(4);
        int y = balloonY1 + 8;
        do {
            chunk.setCircle(8, 8, 6, y, y % step != 0 ? secondary : primary, true);
            if (hollow)
                chunk.setCircle(8, 8, 5, y, Material.AIR, true);
            y++;
        } while (y < balloonY2 - 3);

        // top taper
        chunk.setCircle(8, 8, 6, balloonY2 - 3, primary, true);
        chunk.setCircle(8, 8, 5, balloonY2 - 2, balloonY2 - 1, primary, true);
        chunk.setCircle(8, 8, 4, balloonY2 - 1, balloonY2, primary, true);
        chunk.setCircle(8, 8, 3, balloonY2, secondary, true);
        if (hollow) {
            chunk.setCircle(8, 8, 5, balloonY2 - 3, Material.AIR, true);
            chunk.setCircle(8, 8, 4, balloonY2 - 2, balloonY2 - 1, Material.AIR, true);
            chunk.setCircle(8, 8, 3, balloonY2 - 1, balloonY2, Material.AIR, true);
        }

        // a fire burner under the hollow envelope
        if (hollow) {
            chunk.setBlocks(7, 9, balloonY1 - 2, 7, 9, Material.STONE_SLAB, SlabType.TOP);
            chunk.setBlocks(7, 9, balloonY1 - 1, 7, 9, Material.NETHERRACK);
            chunk.setBlocks(7, 9, balloonY1, 7, 9, Material.FIRE);
            chunk.setBlocks(7, 8, balloonY1 - 1, 5, 7, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
            chunk.setBlocks(8, 9, balloonY1 - 1, 9, 11, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
            chunk.setBlocks(9, 11, balloonY1 - 1, 7, 8, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
            chunk.setBlocks(5, 7, balloonY1 - 1, 8, 9, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
        }
    }

    private void drawSaucer(SupportBlocks chunk, int x, int y, int z) {
        chunk.setCircle(x, z, 4, y, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 1, y, Material.GLASS, true);
        chunk.setCircle(x, z, 5, y + 1, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 2, y + 1, Material.GLASS, true);
        chunk.setCircle(x, z, 4, y + 2, Material.REDSTONE_BLOCK, true);
        chunk.setCircle(x, z, 2, y + 3, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 1, y + 4, Material.GLASS, true);
    }

    private boolean attachString(AbstractBlocks chunk, int x, int y1, int y2, int z) {
        boolean anchored = !chunk.isEmpty(x, y1 - 1, z);
        if (anchored)
            chunk.setBlocks(x, y1, y2, z, Material.IRON_BARS);
        return anchored;
    }
}
