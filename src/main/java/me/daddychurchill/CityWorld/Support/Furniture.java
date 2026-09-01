package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.Plugins.LootProvider;

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

    private static final Material[] CANDLES = { Material.CANDLE, Material.WHITE_CANDLE, Material.ORANGE_CANDLE,
            Material.LIGHT_GRAY_CANDLE, Material.RED_CANDLE };

    /** True on a MODERN world — the gate every decoration here honours. */
    public static boolean modern(CityWorldGenerator generator) {
        return generator.isModernStyle();
    }

    /**
     * MODERN: a lightning rod on the roof of a tall building. Scans a near-centre column down from above
     * the tallest builds to the first solid block (the roof); if that roof sits well above street level —
     * a genuine highrise — plants a rod on top. Short buildings and open lots get nothing, so rods only
     * ever crown the skyline (where lightning would strike anyway). Called from the decoration seam per
     * STRUCTURE lot, so a multi-chunk tower can carry a couple.
     */
    public static void rooftopLightningRod(CityWorldGenerator generator, SupportBlocks chunk, Odds odds) {
        if (!modern(generator) || !odds.playOdds(0.30))
            return;
        int x = 7 + odds.getRandomInt(2), z = 7 + odds.getRandomInt(2); // near the lot centre
        int street = generator.streetLevel;
        int roofY = -1;
        for (int y = street + 200; y > street + 16; y--) // only tall builds (roof >16 above the street)
            // the roof proper, not whatever is lying on it: overgrowth drapes moss carpet and leaf litter
            // across roofs, and those are "not empty" but can't carry a rod — a rod stood on one looks
            // like it's floating a notch above the roof
            if (!chunk.isEmpty(x, y, z) && chunk.isSturdyTop(x, y, z)) {
                roofY = y;
                break;
            }
        if (roofY >= 0 && chunk.isEmpty(x, roofY + 1, z))
            chunk.setBlock(x, roofY + 1, z, Material.LIGHTNING_ROD);
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
        placeAccent(generator, chunk, odds, c[0], y, c[1]);
    }

    private static void placeAccent(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z) {
        switch (odds.getRandomInt(10)) {
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
        case 5:
            candleCluster(chunk, odds, x, y, z);
            break;
        case 6:
            // a copper storage chest tucked against the wall (accentRoom prefers wall-backed cells)
            chunk.setChest(generator, x, y, z, wallward(chunk, x, y, z), odds, generator.lootProvider,
                    LootProvider.LootLocation.NIGHTSTAND, Material.COPPER_CHEST);
            break;
        case 7:
            // a chandelier — a chain dropping from the ceiling with a lantern hung on the end, if there's
            // a ceiling close enough above to hang it from; otherwise fall through to a floor piece
            if (chandelier(chunk, odds, x, y, z))
                break;
            pottedPlant(chunk, odds, x, y, z);
            break;
        default:
            // amethyst sparkle on the floor
            chunk.setBlock(x, y, z, Material.AMETHYST_CLUSTER, BlockFace.UP);
            break;
        }
    }

    /** One to three candles clustered on the floor — the odd one a lit birthday cake. */
    private static void candleCluster(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (odds.playOdds(0.15))
            chunk.setBlock(x, y, z, Material.CANDLE_CAKE);
        else
            chunk.setBlock(x, y, z, CANDLES[odds.getRandomInt(CANDLES.length)]);
    }

    /** A chain-and-lantern chandelier hung from a ceiling within ~4 blocks above the floor cell. Returns
     *  false if no ceiling is close enough (a tall/open room) so the caller can place a floor piece instead. */
    private static boolean chandelier(RealBlocks chunk, Odds odds, int x, int y, int z) {
        int ceil = -1;
        for (int cy = y + 3; cy <= y + 5; cy++) // look for a solid ceiling 3..5 above the floor
            if (!chunk.isEmpty(x, cy, z)) {
                ceil = cy;
                break;
            }
        if (ceil < 0)
            return false;
        int chainBottom = Math.max(y + 2, ceil - 2); // at most a couple of links, so the lantern hangs high
        for (int cy = ceil - 1; cy >= chainBottom; cy--)
            chunk.setBlock(x, cy, z, Material.IRON_CHAIN);
        int lanternY = Math.max(y + 1, chainBottom - 1); // lantern on the end, never down at the floor
        chunk.setHangingLantern(x, lanternY, z, odds.flipCoin() ? Material.LANTERN : Material.SOUL_LANTERN);
        return true;
    }

    /** The face of a solid horizontal neighbour, so a chest/cabinet sits back against a wall facing out. */
    private static BlockFace wallward(RealBlocks chunk, int x, int y, int z) {
        if (solid(chunk, x - 1, y, z)) return BlockFace.EAST;
        if (solid(chunk, x + 1, y, z)) return BlockFace.WEST;
        if (solid(chunk, x, y, z - 1)) return BlockFace.SOUTH;
        return BlockFace.NORTH;
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
    /** Full-block "table top" surfaces — an item set on top of these sits flush, unlike a bottom slab which
     *  leaves the candle/plant floating half a block above it (the reason coffee-table clutter looked
     *  levitated). Copper grate / scaffolding read as light modern side tables. */
    private static final Material[] TABLE_TOPS = { Material.COPPER_GRATE, Material.EXPOSED_COPPER_GRATE,
            Material.SCAFFOLDING, Material.SMOOTH_QUARTZ, Material.OAK_PLANKS };

    public static void sideTable(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (!clearFloor(chunk, x, y, z))
            return;
        // a one-block table with a flush top, so anything set on it sits ON the surface
        chunk.setBlock(x, y, z, TABLE_TOPS[odds.getRandomInt(TABLE_TOPS.length)]);
        if (odds.flipCoin())
            chunk.setBlock(x, y + 1, z, odds.flipCoin() ? CANDLES[odds.getRandomInt(CANDLES.length)]
                    : Material.POTTED_FERN);
    }

    // --- house rooms (called from the colonial house's per-room styling) -----------------------

    private static final Material[] BEDS = { Material.WHITE_BED, Material.RED_BED, Material.BLUE_BED };

    /** A kitchen counter along the north interior wall: cabinet, sink, stove (a smoker on MODERN). */
    public static void kitchen(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        int z = z1 + 1;
        Material counter = FurnitureTags.pick(FurnitureTags.COUNTER, odds);
        if (counter != null) {
            // A run of counter along the back wall, with a sink in it and cabinets either side — the
            // shape a real kitchen has, instead of a barrel and a furnace in a row.
            Material sink = FurnitureTags.pick(FurnitureTags.SINK, odds);
            Material cabinet = FurnitureTags.pick(FurnitureTags.CABINET, odds);
            int mid = (x1 + x2) / 2;
            for (int cx = x1 + 1; cx <= x2 - 1; cx++) {
                Material piece = cx == mid && sink != null ? sink : counter;
                if (cx == x1 + 1 && cabinet != null)
                    piece = cabinet;
                placeIfClear(chunk, cx, y, z, piece, BlockFace.SOUTH);
            }
            if (x1 + 3 <= x2 - 1)
                placeIfClear(chunk, x2 - 1, y, z, modern(generator) ? Material.SMOKER : Material.FURNACE,
                        BlockFace.SOUTH);
            accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
            return;
        }
        chunk.setChest(generator, x1 + 1, y, z, BlockFace.SOUTH, odds, generator.lootProvider,
                LootProvider.LootLocation.NIGHTSTAND, Material.BARREL);
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
        Material table = FurnitureTags.pick(FurnitureTags.TABLE, odds);
        Material chair = FurnitureTags.pick(FurnitureTags.CHAIR, odds);
        if (table != null) {
            // A real dining set. Furniture-mod tables auto-connect like fences, so a 2x1 top needs no
            // orientation — and the chairs are told which way to LOOK, with FurnitureTags applying
            // whatever rotation that mod's model wants.
            boolean wide = cx + 1 <= x2 - 1;
            if (clearFloor(chunk, cx, y, cz))
                chunk.setBlock(cx, y, cz, table);
            if (wide && clearFloor(chunk, cx + 1, y, cz))
                chunk.setBlock(cx + 1, y, cz, table);
            if (chair != null) {
                seat(chunk, cx, y, cz - 1, chair, BlockFace.SOUTH);   // north of the table, looking south
                seat(chunk, cx, y, cz + 1, chair, BlockFace.NORTH);   // south of it, looking north
                if (wide) {
                    seat(chunk, cx + 1, y, cz - 1, chair, BlockFace.SOUTH);
                    seat(chunk, cx + 1, y, cz + 1, chair, BlockFace.NORTH);
                }
            }
            accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
            return;
        }
        if (clearFloor(chunk, cx, y, cz)) {
            chunk.setBlock(cx, y, cz, Material.OAK_FENCE);
            chunk.setBlock(cx, y + 1, cz, modern(generator) ? Material.SMOOTH_QUARTZ_SLAB : Material.WHITE_CARPET);
        }
        // a chair's stair FACING is its backrest side, so the sitter faces the opposite way — point the
        // backrests AWAY from the table so the diners face it
        placeIfClear(chunk, cx - 1, y, cz, Material.OAK_STAIRS, BlockFace.WEST);
        placeIfClear(chunk, cx + 1, y, cz, Material.OAK_STAIRS, BlockFace.EAST);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /** A couch (with a coffee table in front) against whichever interior wall has room for it. */
    public static void living(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        Material sofa = FurnitureTags.pick(FurnitureTags.SOFA, odds);
        if (sofa != null) {
            // A sofa run against the north wall looking into the room, a table in front of it, and a
            // lamp in the corner. The sofa is told where its occupant looks, like any other seat.
            Material lamp = FurnitureTags.pick(FurnitureTags.LAMP, odds);
            Material table = FurnitureTags.pick(FurnitureTags.TABLE, odds);
            boolean placed = false;
            for (int sx = x1 + 1; sx <= x2 - 1; sx++)
                placed |= seat(chunk, sx, y, z1 + 1, sofa, BlockFace.SOUTH);
            if (placed && table != null && z1 + 3 <= z2 - 1)
                placeIfClear(chunk, cx, y, z1 + 3, table, BlockFace.SOUTH);
            if (lamp != null)
                placeIfClear(chunk, x2 - 1, y, z2 - 1, lamp, BlockFace.WEST);
            if (placed) {
                accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
                return;
            }
        }
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
        int midX = (x1 + x2) / 2, midZ = (z1 + z2) / 2;
        // Back the bed onto whichever wall sits nearest a chunk edge. Exterior walls (with windows) run
        // toward x/z 0..15; interior partitions (which carry the doors) sit inward — so this keeps the bed
        // off the door walls and stops it landing in a doorway (the old fixed NW corner did that a lot).
        int dN = z1, dS = 15 - z2, dW = x1, dE = 15 - x2;
        int best = Math.min(Math.min(dN, dS), Math.min(dW, dE));
        if (best == dN)
            placeBed(chunk, bed, midX, y, z1 + 1, BlockFace.SOUTH); // head to north wall
        else if (best == dS)
            placeBed(chunk, bed, midX, y, z2 - 2, BlockFace.NORTH); // head to south wall
        else if (best == dW)
            placeBed(chunk, bed, x1 + 1, y, midZ, BlockFace.EAST); // head to west wall
        else
            placeBed(chunk, bed, x2 - 2, y, midZ, BlockFace.WEST); // head to east wall

        chunk.setChest(generator, x1 + 1, y, z1 + 1, BlockFace.SOUTH, odds, generator.lootProvider,
                LootProvider.LootLocation.NIGHTSTAND, Material.BARREL); // a nightstand where it fits
        if (modern(generator))
            floorLampIfClear(chunk, odds, x2 - 1, y, z2 - 1);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /** Place a bed's two cells (anchor + the partner in {@code facing}), only if both are clear floor. */
    private static boolean placeBed(RealBlocks chunk, Material bed, int x, int y, int z, BlockFace facing) {
        int px = x, pz = z;
        switch (facing) {
        case NORTH, SOUTH -> pz = z + 1;
        case EAST -> px = x + 1;
        case WEST -> px = x - 1;
        default -> {
        }
        }
        if (!clearFloor(chunk, x, y, z) || !clearFloor(chunk, px, y, pz))
            return false;
        chunk.setBed(x, y, z, bed, facing);
        return true;
    }

    /** A little bathroom: a cauldron sink/bath, a quartz "toilet", and a tiled mat. */
    public static void bathroom(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        Material bath = FurnitureTags.pick(FurnitureTags.BATH, odds);
        Material toilet = FurnitureTags.pick(FurnitureTags.TOILET, odds);
        Material basin = FurnitureTags.pick(FurnitureTags.SINK, odds);
        if (bath != null || toilet != null || basin != null) {
            // A plumbed bathroom: bath in the far corner, basin beside it, toilet against the near wall.
            // Each is optional on its own, so one mod supplying only some of them still improves things.
            if (bath != null)
                placeIfClear(chunk, x1 + 1, y, z1 + 1, bath, BlockFace.SOUTH);
            if (basin != null && x1 + 2 <= x2 - 1)
                placeIfClear(chunk, x1 + 2, y, z1 + 1, basin, BlockFace.SOUTH);
            if (toilet != null && z1 + 2 <= z2 - 1)
                placeIfClear(chunk, x2 - 1, y, z1 + 2, toilet, BlockFace.WEST);
            int bx = (x1 + x2) / 2, bz = (z1 + z2) / 2;
            if (clearFloor(chunk, bx, y, bz))
                chunk.setBlock(bx, y, bz, odds.flipCoin() ? Material.WHITE_CARPET : Material.LIGHT_BLUE_CARPET);
            accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
            return;
        }
        if (clearFloor(chunk, x1 + 1, y, z1 + 1))
            chunk.setCauldron(x1 + 1, y, z1 + 1, odds); // sink/bath
        int tx = x2 - 1;
        if (z1 + 2 <= z2 - 1 && clearFloor(chunk, tx, y, z1 + 2)) {
            // matches the community build: a 2-tall quartz cistern against the wall, a quartz bowl in front
            // with a flat (closed) birch-trapdoor seat on top, and a birch-button flush on the side of the tank
            // the two base blocks are upside-down stairs facing each other (solid sides meeting), so the
            // pedestal narrows to a central foot with the bowl/tank overhanging — the classic toilet base
            chunk.setStairUpsideDown(tx, y, z1 + 1, Material.QUARTZ_STAIRS, BlockFace.SOUTH); // cistern base
            chunk.setBlock(tx, y + 1, z1 + 1, Material.QUARTZ_BLOCK); // cistern (upper — stands above the seat)
            chunk.setStairUpsideDown(tx, y, z1 + 2, Material.QUARTZ_STAIRS, BlockFace.NORTH); // bowl base
            chunk.setBlock(tx, y + 1, z1 + 2, Material.BIRCH_TRAPDOOR); // flat seat (default state = closed, bottom)
            if (tx - 1 >= x1 && chunk.isEmpty(tx - 1, y + 1, z1 + 1))
                chunk.setWallButton(tx - 1, y + 1, z1 + 1, Material.BIRCH_BUTTON, BlockFace.WEST); // flush handle
        } else if (clearFloor(chunk, tx, y, z1 + 1)) {
            // tight room: bowl straight against the wall with a flat seat, no separate cistern
            chunk.setStairUpsideDown(tx, y, z1 + 1, Material.QUARTZ_STAIRS, BlockFace.NORTH);
            chunk.setBlock(tx, y + 1, z1 + 1, Material.BIRCH_TRAPDOOR);
        }
        int mx = (x1 + x2) / 2, mz = (z1 + z2) / 2; // a tiled bathmat
        if (clearFloor(chunk, mx, y, mz))
            chunk.setBlock(mx, y, mz, odds.flipCoin() ? Material.WHITE_CARPET : Material.LIGHT_BLUE_CARPET);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /**
     * Places a seat so its occupant looks {@code look}.
     *
     * <p>The caller says which way the sitter should face and never touches the block's own
     * {@code facing} — {@link FurnitureTags#facingFor} converts, because the mods disagree about what
     * {@code facing} means and one of them disagrees with itself between chairs and sofas.
     */
    /**
     * A study: a desk against the wall, a chair pulled up to it, and a bookshelf beside it.
     *
     * <p>New with the furniture mods, because CityWorld had no vanilla blocks that read as a desk — a
     * room type the mods make possible rather than one they merely redecorate. Does nothing at all
     * without a desk to place, so an unmodded world is unchanged.
     */
    public static void study(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        Material desk = FurnitureTags.pick(FurnitureTags.DESK, odds);
        if (desk == null)
            return;
        int cx = (x1 + x2) / 2;
        // Desk against the north wall; the chair sits south of it looking north, into the desk.
        placeIfClear(chunk, cx, y, z1 + 1, desk, BlockFace.SOUTH);
        if (cx + 1 <= x2 - 1)
            placeIfClear(chunk, cx + 1, y, z1 + 1, desk, BlockFace.SOUTH);
        Material chair = FurnitureTags.pick(FurnitureTags.CHAIR, odds);
        if (chair != null && z1 + 2 <= z2 - 1)
            seat(chunk, cx, y, z1 + 2, chair, BlockFace.NORTH);
        Material shelf = FurnitureTags.pick(FurnitureTags.BOOKSHELF, odds);
        if (shelf != null)
            placeIfClear(chunk, x1 + 1, y, z1 + 1, shelf, BlockFace.SOUTH);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    private static boolean seat(RealBlocks chunk, int x, int y, int z, Material piece, BlockFace look) {
        return placeIfClear(chunk, x, y, z, piece, FurnitureTags.facingFor(piece, look));
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
