package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A small vocabulary of MODERN interior decoration — the "clever block trick" furniture and the
 * post-1.8 decorative palette woven into furnished rooms. Kept as reusable builders so any room can
 * dress itself without repeating the coordinate fiddling, mirroring {@code Overgrowth}/{@code ShopFitter}.
 *
 * <p>Everything here is MODERN-only and writes in-chunk. The centrepiece is {@link #accentRoom}, a light
 * touch called on every furnished room cell: it finds a clear patch of floor and adds a tasteful accent
 * (a potted plant, a little lamp, a decorated pot), so offices, lounges and shops all read as lived-in
 * rather than empty boxes. CLASSIC (1.8-era) rooms are left exactly as they were.
 */
public final class Furniture {

    private Furniture() {}

    private static final Material[] PLANTS = { Material.POTTED_FERN, Material.POTTED_AZALEA, Material.POTTED_BAMBOO,
            Material.FLOWER_POT };

    /** True on a MODERN world — the gate every decoration here honours. */
    public static boolean modern(CityWorldGenerator generator) {
        return generator.worldStyle == CityWorldGenerator.WorldStyle.MODERN;
    }

    /** A bookshelf material — MODERN mixes in chiseled bookshelves (per-call, so a wall reads as a blend). */
    public static Material shelfMaterial(CityWorldGenerator generator, Odds odds) {
        return modern(generator) && odds.flipCoin() ? Material.CHISELED_BOOKSHELF : Material.BOOKSHELF;
    }

    /**
     * MODERN: drop one tasteful floor accent into a furnished room cell, preferring a corner so it never
     * blocks a walkway. No-op on CLASSIC, on a full cell, or (most of the time) by the density roll — so
     * a floor gets a scattering, not clutter.
     */
    public static void accentRoom(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z,
            int width, int depth) {
        if (!modern(generator) || !odds.playOdds(0.4))
            return;

        List<int[]> corners = new ArrayList<>();
        List<int[]> open = new ArrayList<>();
        for (int cx = x; cx < x + width; cx++)
            for (int cz = z; cz < z + depth; cz++) {
                if (!clearFloor(chunk, cx, y, cz))
                    continue;
                (backsSomething(chunk, cx, y, cz) ? corners : open).add(new int[] { cx, cz });
            }
        List<int[]> pick = !corners.isEmpty() ? corners : open;
        if (pick.isEmpty())
            return;
        int[] c = pick.get(odds.getRandomInt(pick.size()));
        placeAccent(chunk, odds, c[0], y, c[1]);
    }

    private static void placeAccent(RealBlocks chunk, Odds odds, int x, int y, int z) {
        switch (odds.getRandomInt(6)) {
        case 0:
        case 1:
            pottedPlant(chunk, odds, x, y, z);
            break;
        case 2:
        case 3:
            floorLamp(chunk, odds, x, y, z);
            break;
        case 4:
            chunk.setBlock(x, y, z, Material.DECORATED_POT);
            break;
        default:
            // amethyst sparkle on the floor
            chunk.setBlock(x, y, z, Material.AMETHYST_CLUSTER, BlockFace.UP);
            break;
        }
    }

    /** A potted plant on the floor — instant "someone lives here". */
    public static void pottedPlant(RealBlocks chunk, Odds odds, int x, int y, int z) {
        chunk.setBlock(x, y, z, PLANTS[odds.getRandomInt(PLANTS.length)]);
    }

    /** A little standing lamp: a fence post topped with a lantern that actually casts light. */
    public static void floorLamp(RealBlocks chunk, Odds odds, int x, int y, int z) {
        chunk.setBlock(x, y, z, Material.OAK_FENCE);
        chunk.setBlock(x, y + 1, z, odds.flipCoin() ? Material.LANTERN : Material.SOUL_LANTERN);
    }

    /**
     * A low coffee/side table with something on top — a slab surface on a fence leg, a candle or plant
     * set on it. Placed at (x,y,z) if that cell and the one above are clear.
     */
    public static void sideTable(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (!clearFloor(chunk, x, y, z))
            return;
        chunk.setBlock(x, y, z, Material.OAK_FENCE);
        chunk.setBlock(x, y + 1, z, odds.flipCoin() ? Material.SMOOTH_QUARTZ_SLAB : Material.OAK_SLAB);
        if (odds.flipCoin())
            chunk.setBlock(x, y + 2, z, odds.flipCoin() ? Material.CANDLE : Material.POTTED_FERN);
    }

    // --- house rooms (called from the colonial house's per-room styling) -----------------------

    private static final Material[] BEDS = { Material.WHITE_BED, Material.RED_BED, Material.BLUE_BED };

    /** A kitchen counter along the north interior wall: cabinet, sink, stove (a smoker on MODERN). */
    public static void kitchen(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        int z = z1 + 1;
        placeIfClear(chunk, x1 + 1, y, z, Material.BARREL, BlockFace.SOUTH);
        if (clearFloor(chunk, x1 + 2, y, z))
            chunk.setCauldron(x1 + 2, y, z, odds);
        if (x1 + 3 <= x2 - 1)
            placeIfClear(chunk, x1 + 3, y, z, modern(generator) ? Material.SMOKER : Material.FURNACE, BlockFace.SOUTH);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /** A dining table with a chair either side, in the middle of the room. */
    public static void dining(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        if (clearFloor(chunk, cx, y, cz)) {
            chunk.setBlock(cx, y, cz, Material.OAK_FENCE);
            chunk.setBlock(cx, y + 1, cz, modern(generator) ? Material.SMOOTH_QUARTZ_SLAB : Material.WHITE_CARPET);
        }
        placeIfClear(chunk, cx - 1, y, cz, Material.OAK_STAIRS, BlockFace.EAST);
        placeIfClear(chunk, cx + 1, y, cz, Material.OAK_STAIRS, BlockFace.WEST);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /** A couch (with a coffee table in front) against whichever interior wall has room for it. */
    public static void living(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        // try the south wall (couch faces it), then east, then north, then west — first that fits wins
        if (couchAlongX(chunk, x1, x2, y, z2 - 1, BlockFace.SOUTH))
            sideTable(chunk, odds, cx, y, z2 - 2);
        else if (couchAlongZ(chunk, x2 - 1, y, z1, z2, BlockFace.EAST))
            sideTable(chunk, odds, x2 - 2, y, cz);
        else if (couchAlongX(chunk, x1, x2, y, z1 + 1, BlockFace.NORTH))
            sideTable(chunk, odds, cx, y, z1 + 2);
        else if (couchAlongZ(chunk, x1 + 1, y, z1, z2, BlockFace.WEST))
            sideTable(chunk, odds, x1 + 2, y, cz);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /** A row of couch stairs along an interior x-run at fixed z; true if at least two seats landed. */
    private static boolean couchAlongX(RealBlocks chunk, int x1, int x2, int y, int z, BlockFace facing) {
        int placed = 0;
        for (int x = x1 + 1; x <= x2 - 1 && placed < 3; x++)
            if (placeIfClear(chunk, x, y, z, Material.SPRUCE_STAIRS, facing))
                placed++;
        return placed >= 2;
    }

    /** A run of couch stairs along an interior z-run at fixed x; true if at least two seats landed. */
    private static boolean couchAlongZ(RealBlocks chunk, int x, int y, int z1, int z2, BlockFace facing) {
        int placed = 0;
        for (int z = z1 + 1; z <= z2 - 1 && placed < 3; z++)
            if (placeIfClear(chunk, x, y, z, Material.SPRUCE_STAIRS, facing))
                placed++;
        return placed >= 2;
    }

    /** A bed in the corner with a bedside barrel, and (MODERN) a lamp. */
    public static void bedroom(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        Material bed = BEDS[odds.getRandomInt(BEDS.length)];
        // bed head to the north wall, foot toward room — needs two clear cells
        if (clearFloor(chunk, x1 + 1, y, z1 + 1) && clearFloor(chunk, x1 + 1, y, z1 + 2))
            chunk.setBed(x1 + 1, y, z1 + 1, bed, BlockFace.SOUTH);
        placeIfClear(chunk, x1 + 2, y, z1 + 1, Material.BARREL, BlockFace.SOUTH); // nightstand
        if (modern(generator) && x2 - 1 > x1 + 2)
            floorLampIfClear(chunk, odds, x2 - 1, y, z2 - 1);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    private static boolean placeIfClear(RealBlocks chunk, int x, int y, int z, Material mat, BlockFace facing) {
        if (!clearFloor(chunk, x, y, z))
            return false;
        chunk.setBlock(x, y, z, mat, facing);
        return true;
    }

    private static void floorLampIfClear(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (clearFloor(chunk, x, y, z))
            floorLamp(chunk, odds, x, y, z);
    }

    // --- checks --------------------------------------------------------------------------------

    /** Air at (x,y,z) and above, solid underfoot — a genuinely empty spot to stand a piece on. */
    private static boolean clearFloor(RealBlocks chunk, int x, int y, int z) {
        return chunk.isEmpty(x, y, z) && chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y - 1, z);
    }

    /** Whether a horizontal neighbour is solid — a piece here backs onto a wall/shelf, not marooned. */
    private static boolean backsSomething(RealBlocks chunk, int x, int y, int z) {
        return solid(chunk, x + 1, y, z) || solid(chunk, x - 1, y, z)
                || solid(chunk, x, y, z + 1) || solid(chunk, x, y, z - 1);
    }

    private static boolean solid(RealBlocks chunk, int x, int y, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16 && !chunk.isEmpty(x, y, z);
    }
}
