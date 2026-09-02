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
        switch (odds.getRandomInt(12)) {
        case 0:
        case 1:
            pottedPlant(chunk, odds, x, y, z);
            break;
        case 2:
        case 3: {
            // a lamp — a modded one is a TABLE lamp (single block, no facing), so it gets an end
            // table stood under it; the vanilla fence-and-lantern is genuinely freestanding
            Material lamp = FurnitureTags.pick(FurnitureTags.LAMP, odds);
            if (lamp == null || !tableLamp(chunk, odds, x, y, z, lamp))
                floorLamp(chunk, odds, x, y, z);
            break;
        }
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
        case 8:
            // something on the wall this cell backs onto — art (a painting or a framed keepsake)
            // or a sconce; open cells get a plant instead
            if (odds.flipCoin() && wallArt(chunk, odds, x, y, z))
                break;
            if (wallSconce(chunk, odds, x, y, z))
                break;
            pottedPlant(chunk, odds, x, y, z);
            break;
        case 9:
            // something that belongs ON a surface, with the surface put underneath it
            surfacePiece(chunk, odds, x, y, z);
            break;
        default:
            // amethyst moved from the floor to the surface pool (owner: table decor, not floor
            // decor) — so the remaining default is another surface piece or a plant
            if (odds.flipCoin())
                surfacePiece(chunk, odds, x, y, z);
            else
                pottedPlant(chunk, odds, x, y, z);
            break;
        }
    }

    /**
     * A table lamp on an end table — the {@code surface} half of the floor/surface split. Uses a
     * modded table from the pool when one exists (it auto-connects politely), else a flush-top
     * vanilla block. Returns false if the cell isn't free so the caller can fall back.
     */
    private static boolean tableLamp(RealBlocks chunk, Odds odds, int x, int y, int z, Material lamp) {
        if (!clearFloor(chunk, x, y, z))
            return false;
        Material table = FurnitureTags.pick(FurnitureTags.TABLE, odds);
        chunk.setBlock(x, y, z, table != null ? table : TABLE_TOPS[odds.getRandomInt(TABLE_TOPS.length)]);
        chunk.reconnect(x, y, z);
        if (chunk.isEmpty(x, y, z))
            return false; // the table did not take (fell, broke) — never leave a floating topper
        chunk.setBlock(x, y + 1, z, lamp);
        return true;
    }

    /** A piece from the surface pool (candles, lanterns, table lamps…) stood on a table put under it. */
    private static void surfacePiece(RealBlocks chunk, Odds odds, int x, int y, int z) {
        Material piece = FurnitureTags.pick(FurnitureTags.SURFACE_DECOR, odds);
        if (piece == null) {
            pottedPlant(chunk, odds, x, y, z);
            return;
        }
        Material table = FurnitureTags.pick(FurnitureTags.TABLE, odds);
        chunk.setBlock(x, y, z, table != null ? table : TABLE_TOPS[odds.getRandomInt(TABLE_TOPS.length)]);
        chunk.reconnect(x, y, z);
        if (chunk.isEmpty(x, y, z))
            return; // never leave a floating topper
        chunk.setBlock(x, y + 1, z, piece);
    }

    /**
     * One piece of wall decoration for a room — art (painting/item frame) or a sconce on a random
     * wall-backed cell. This is the direct pass the rooms call; the accent switch also rolls it,
     * but at 1-in-12-of-40% nothing was ever visible in playtest.
     */
    public static void wallDecor(RealBlocks chunk, Odds odds, int x1, int x2, int y, int z1, int z2) {
        if (!odds.playOdds(0.6))
            return;
        List<int[]> cells = new ArrayList<>();
        for (int cx = x1 + 1; cx <= x2 - 1; cx++)
            for (int cz = z1 + 1; cz <= z2 - 1; cz++)
                if (chunk.isEmpty(cx, y + 2, cz) && wallwardOrNull(chunk, cx, y + 2, cz) != null)
                    cells.add(new int[] { cx, cz });
        if (cells.isEmpty())
            return;
        int[] c = cells.get(odds.getRandomInt(cells.size()));
        if (!wallArt(chunk, odds, c[0], y, c[1]))
            wallSconce(chunk, odds, c[0], y, c[1]);
    }

    /** What ends up inside an item frame — the "nice things" of the owner's brief. */
    private static final net.minecraft.world.item.Item[] FRAMED = { net.minecraft.world.item.Items.CLOCK,
            net.minecraft.world.item.Items.COMPASS, net.minecraft.world.item.Items.AMETHYST_SHARD,
            net.minecraft.world.item.Items.BOOK, net.minecraft.world.item.Items.SUNFLOWER,
            net.minecraft.world.item.Items.GOLDEN_APPLE };

    /**
     * A painting or an item frame hung at eye height on the wall this cell backs onto.
     *
     * <p>These are ENTITIES, not blocks — the same worldgen-region spawn path the villagers use
     * ({@code addFreshEntityWithPassengers} on the {@code ServerLevelAccessor}). Two cautions,
     * both learned elsewhere in this codebase: the position must stay inside this chunk, because
     * {@code WorldGenRegion.addFreshEntity} throws rather than declines outside its cache (room
     * coordinates are in-chunk, so that holds); and {@code Painting.create} + {@code survives()}
     * do the fitting — the painting picks a variant that fits the wall, and anything that cannot
     * hang simply is not spawned.
     */
    private static boolean wallArt(RealBlocks chunk, Odds odds, int x, int y, int z) {
        BlockFace out = wallwardOrNull(chunk, x, y + 2, z);
        if (out == null || !chunk.isEmpty(x, y + 2, z))
            return false;
        // the backing must be a sturdy full face — art hung on window glass pops off on first tick
        if (!chunk.isSturdyFace(x - out.getModX(), y + 2, z - out.getModZ(), out))
            return false;
        me.daddychurchill.CityWorld.compat.Location at = chunk.getBlockLocation(x, y + 2, z);
        if (!(at.getLevel() instanceof net.minecraft.world.level.ServerLevelAccessor server))
            return false;
        net.minecraft.core.Direction dir = out.toDirection();
        if (dir == null)
            return false;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(at.getBlockX(), at.getBlockY(),
                at.getBlockZ());
        // ⚠ Never call Painting.create or survives() here: both run collision and entity queries
        // against the real ServerLevel, and from the worldgen thread that blocks on unloaded
        // chunks until the watchdog kills the server (observed, not hypothetical). We verified
        // the wall and the air cell ourselves; construct directly and only ever hang 1x1 pieces,
        // which is exactly the space we checked.
        if (odds.flipCoin()) {
            var variants = new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.entity.decoration.painting.PaintingVariant>>();
            server.getLevel().registryAccess()
                    .lookupOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT)
                    .getTagOrEmpty(net.minecraft.tags.PaintingVariantTags.PLACEABLE)
                    .forEach(holder -> {
                        if (holder.value().width() == 1 && holder.value().height() == 1)
                            variants.add(holder);
                    });
            if (variants.isEmpty())
                return false;
            var painting = new net.minecraft.world.entity.decoration.painting.Painting(server.getLevel(), pos, dir,
                    variants.get(odds.getRandomInt(variants.size())));
            server.addFreshEntityWithPassengers(painting);
            return true;
        }
        var frame = new net.minecraft.world.entity.decoration.ItemFrame(server.getLevel(), pos, dir);
        frame.setSilent(true); // setItem would otherwise playSound on the real level mid-worldgen
        frame.setItem(new net.minecraft.world.item.ItemStack(FRAMED[odds.getRandomInt(FRAMED.length)]), false);
        server.addFreshEntityWithPassengers(frame);
        return true;
    }

    /**
     * A piece from the wall pool mounted at eye height on whichever wall this cell backs onto.
     * Torch-like blocks face away from the wall; face-attached blocks (glow lichen) attach toward
     * it — {@code hasFaces()} distinguishes, since the two families mean opposite things by the
     * same parameter.
     */
    private static boolean wallSconce(RealBlocks chunk, Odds odds, int x, int y, int z) {
        Material piece = FurnitureTags.pick(FurnitureTags.WALL_DECOR, odds);
        if (piece == null || !chunk.isEmpty(x, y + 2, z))
            return false;
        BlockFace out = wallwardOrNull(chunk, x, y + 2, z);
        if (out == null)
            return false;
        chunk.setBlock(x, y + 2, z, piece, piece.hasFaces() ? out.getOppositeFace() : out);
        return true;
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
        BlockFace out = wallwardOrNull(chunk, x, y, z);
        return out != null ? out : BlockFace.NORTH;
    }

    /** As {@link #wallward}, but {@code null} when no wall backs this cell — for wall-mounted pieces
     *  that must not be placed floating. */
    private static BlockFace wallwardOrNull(RealBlocks chunk, int x, int y, int z) {
        if (solid(chunk, x - 1, y, z)) return BlockFace.EAST;
        if (solid(chunk, x + 1, y, z)) return BlockFace.WEST;
        if (solid(chunk, x, y, z - 1)) return BlockFace.SOUTH;
        if (solid(chunk, x, y, z + 1)) return BlockFace.NORTH;
        return null;
    }

    /** A potted plant (or anything else from the floor pool) — instant "someone lives here". */
    public static void pottedPlant(RealBlocks chunk, Odds odds, int x, int y, int z) {
        Material piece = FurnitureTags.pick(FurnitureTags.FLOOR_DECOR, odds);
        chunk.setBlock(x, y, z, piece != null ? piece : PLANTS[odds.getRandomInt(PLANTS.length)]);
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
    // no SCAFFOLDING here: it collapses without support, leaving whatever stood on it floating —
    // the free-floating candle of playtest round 4
    private static final Material[] TABLE_TOPS = { Material.COPPER_GRATE, Material.EXPOSED_COPPER_GRATE,
            Material.SMOOTH_QUARTZ, Material.OAK_PLANKS };

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
            // Appliances land FIRST, at the end of the wall, or the counter run swallows their
            // cells — the old smoker was silently lost to exactly that. Stove at the end; fridge
            // beside it with the freezer stacked on top (Refurbished's "tall fridge" is two
            // separate blocks).
            Material stove = FurnitureTags.pick(FurnitureTags.STOVE, odds);
            if (x1 + 3 <= x2 - 1) {
                Material cooker = stove != null ? stove
                        : modern(generator) ? Material.SMOKER : Material.FURNACE;
                placeFacing(chunk, x2 - 1, y, z, cooker, BlockFace.SOUTH);
            }
            Material fridge = FurnitureTags.pick(FurnitureTags.FRIDGE, odds);
            if (fridge != null && x1 + 4 <= x2 - 2 && placeFacing(chunk, x2 - 2, y, z, fridge, BlockFace.SOUTH)) {
                Material freezer = FurnitureTags.pick(FurnitureTags.FREEZER, odds);
                if (freezer != null && chunk.isEmpty(x2 - 2, y + 1, z))
                    chunk.setBlock(x2 - 2, y + 1, z, freezer,
                            FurnitureTags.facingFor(freezer, BlockFace.SOUTH));
            }
            int mid = (x1 + x2) / 2;
            for (int cx = x1 + 1; cx <= x2 - 1; cx++) {
                Material piece = cx == mid && sink != null ? sink : counter;
                if (cx == x1 + 1 && cabinet != null)
                    piece = cabinet;
                // fronts open SOUTH into the room — placeFacing applies each mod's own idea of
                // what `facing` means, which is how the doors stopped facing the wall
                if (placeFacing(chunk, cx, y, z, piece, BlockFace.SOUTH))
                    chunk.reconnect(cx, y, z); // counters join into a run (verified good in playtest)
            }
            // counter-top clutter: microwave / toaster / cutting board stood ON the counter run —
            // but never on the SINK cell (a toaster in the sink, playtested, "unless one grows
            // weary of this mortal coil")
            Material topper = odds.flipCoin() ? FurnitureTags.pick(FurnitureTags.MICROWAVE, odds)
                    : odds.flipCoin() ? FurnitureTags.pick(FurnitureTags.TOASTER, odds)
                            : FurnitureTags.pick(FurnitureTags.CUTTING_BOARD, odds);
            if (topper != null)
                for (int tx : new int[] { x1 + 2, x1 + 3, mid + 1 })
                    if (tx != mid && tx <= x2 - 1 && chunk.isEmpty(tx, y + 1, z)
                            && !chunk.isEmpty(tx, y, z)) {
                        chunk.setBlock(tx, y + 1, z, topper, FurnitureTags.facingFor(topper, BlockFace.SOUTH));
                        break;
                    }
            wallDecor(chunk, odds, x1, x2, y, z1, z2);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
            return;
        }
        chunk.setChest(generator, x1 + 1, y, z, BlockFace.SOUTH, odds, generator.lootProvider,
                LootProvider.LootLocation.NIGHTSTAND, Material.BARREL);
        if (clearFloor(chunk, x1 + 2, y, z))
            chunk.setCauldron(x1 + 2, y, z, odds);
        if (x1 + 3 <= x2 - 1)
            placeIfClear(chunk, x1 + 3, y, z, modern(generator) ? Material.SMOKER : Material.FURNACE, BlockFace.SOUTH);
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
            if (clearFloor(chunk, cx, y, cz)) {
                chunk.setBlock(cx, y, cz, table);
                chunk.reconnect(cx, y, cz);
            }
            if (wide && clearFloor(chunk, cx + 1, y, cz)) {
                chunk.setBlock(cx + 1, y, cz, table);
                chunk.reconnect(cx + 1, y, cz);
            }
            if (chair != null) {
                placeFacing(chunk, cx, y, cz - 1, chair, BlockFace.SOUTH);   // north of the table, looking south
                placeFacing(chunk, cx, y, cz + 1, chair, BlockFace.NORTH);   // south of it, looking north
                if (wide) {
                    placeFacing(chunk, cx + 1, y, cz - 1, chair, BlockFace.SOUTH);
                    placeFacing(chunk, cx + 1, y, cz + 1, chair, BlockFace.NORTH);
                }
            }
            ceilingPiece(chunk, odds, cx, y, cz); // a fan or lantern over the dining table
            wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
                placed |= placeFacing(chunk, sx, y, z1 + 1, sofa, BlockFace.SOUTH);
            if (placed && table != null && z1 + 3 <= z2 - 1)
                placeFacing(chunk, cx, y, z1 + 3, table, BlockFace.SOUTH);
            if (lamp != null)
                // modded lamps are TABLE lamps (single block, no facing) — stand one on an end
                // table in the corner rather than on the floor
                tableLamp(chunk, odds, x2 - 1, y, z2 - 1, lamp);
            // a television on a stand against the far wall, screen facing the sofa
            Material tv = FurnitureTags.pick(FurnitureTags.TV, odds);
            if (tv != null && z1 + 4 <= z2 - 1) {
                Material stand = FurnitureTags.pick(FurnitureTags.DRAWER, odds);
                if (stand == null)
                    stand = TABLE_TOPS[odds.getRandomInt(TABLE_TOPS.length)];
                if (placeFacing(chunk, cx, y, z2 - 1, stand, BlockFace.NORTH)
                        && chunk.isEmpty(cx, y + 1, z2 - 1))
                    chunk.setBlock(cx, y + 1, z2 - 1, tv, FurnitureTags.facingFor(tv, BlockFace.NORTH));
            }
            ceilingPiece(chunk, odds, cx, y, cz);
            if (placed) {
                wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
        // Try walls nearest the chunk edge first, but fall through to the others — now that
        // furnishing runs after the doors are cut, clearFloor refuses doorway approaches, and a
        // bed refused its first wall must get another rather than not existing (or, worse, the
        // old behaviour: parking in front of the door).
        record Wall(int dist, int bx, int bz, BlockFace facing) {}
        java.util.List<Wall> tries = new ArrayList<>(List.of(
                new Wall(dN, midX, z1 + 1, BlockFace.SOUTH), new Wall(dS, midX, z2 - 2, BlockFace.NORTH),
                new Wall(dW, x1 + 1, midZ, BlockFace.EAST), new Wall(dE, x2 - 2, midZ, BlockFace.WEST)));
        tries.sort(java.util.Comparator.comparingInt(Wall::dist));
        for (Wall w : tries)
            if (placeBed(chunk, bed, w.bx(), y, w.bz(), w.facing()))
                break;

        chunk.setChest(generator, x1 + 1, y, z1 + 1, BlockFace.SOUTH, odds, generator.lootProvider,
                LootProvider.LootLocation.NIGHTSTAND, Material.BARREL); // a nightstand where it fits

        // Playtest: bedrooms were "mostly bed + barrel". Wardrobe and drawers flank the bed on ITS
        // wall — the bed backs onto the wall nearest the chunk edge (exterior), and interior walls
        // get doors cut AFTER furnishing, so pieces there would block doorways. Corners of the bed
        // wall are the safe storage spots. The rug and lamp stay MODERN-only so CLASSIC keeps its
        // 1.8 look; the modded picks are null without a furniture mod either way.
        Material wardrobe = FurnitureTags.pick(FurnitureTags.WARDROBE, odds);
        Material drawer = FurnitureTags.pick(FurnitureTags.DRAWER, odds);
        int[][] corners; // the two corners of the bed wall
        BlockFace front;
        if (best == dN || best == dS) {
            int wz = best == dN ? z1 + 1 : z2 - 1;
            front = best == dN ? BlockFace.SOUTH : BlockFace.NORTH;
            corners = new int[][] { { x1 + 1, wz }, { x2 - 1, wz } };
        } else {
            int wx = best == dW ? x1 + 1 : x2 - 1;
            front = best == dW ? BlockFace.EAST : BlockFace.WEST;
            corners = new int[][] { { wx, z1 + 1 }, { wx, z2 - 1 } };
        }
        // first free corner each (the barrel nightstand may hold one of them)
        for (Material piece : new Material[] { wardrobe, drawer })
            if (piece != null)
                for (int[] c : corners)
                    if (placeFacing(chunk, c[0], y, c[1], piece, front))
                        break;
        if (modern(generator)) {
            rug(chunk, odds, midX, y, midZ);
            // a bedside lamp most of the time now, not the rare treat it was
            Material lamp = FurnitureTags.pick(FurnitureTags.LAMP, odds);
            if (lamp == null || !tableLamp(chunk, odds, x2 - 1, y, z2 - 1, lamp))
                floorLampIfClear(chunk, odds, x2 - 1, y, z2 - 1);
        }
        ceilingPiece(chunk, odds, midX, y, midZ);
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    private static final Material[] RUGS = { Material.WHITE_CARPET, Material.LIGHT_GRAY_CARPET,
            Material.CYAN_CARPET, Material.RED_CARPET, Material.MOSS_CARPET };

    /** A small rug: a carpet plus-shape centred on (x,z), skipping cells that aren't clear floor. */
    private static void rug(RealBlocks chunk, Odds odds, int x, int y, int z) {
        Material carpet = RUGS[odds.getRandomInt(RUGS.length)];
        for (int[] c : new int[][] { { x, z }, { x + 1, z }, { x - 1, z }, { x, z + 1 }, { x, z - 1 } })
            if (clearFloor(chunk, c[0], y, c[1]))
                chunk.setBlock(c[0], y, c[1], carpet);
    }

    /**
     * An upstairs landing — the stairwell room on floors above ground, which playtested as bare.
     * A console piece (drawers, else a cabinet, else a side table) against the exterior wall, a
     * runner rug, and the accent pass for wall/surface decoration. Deliberately light: the room
     * contains the stairwell opening and its ladder, and {@code clearFloor} keeps everything off
     * the hole. Interior walls get doors cut after furnishing, so the console keeps to the wall
     * nearest the chunk edge, same trick as the bed and the bath.
     */
    public static void hallway(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y,
            int z1, int z2) {
        Material console = FurnitureTags.pick(FurnitureTags.DRAWER, odds);
        if (console == null)
            console = FurnitureTags.pick(FurnitureTags.CABINET, odds);
        int dN = z1, dS = 15 - z2, dW = x1, dE = 15 - x2;
        int best = Math.min(Math.min(dN, dS), Math.min(dW, dE));
        int cx = (x1 + x2) / 2, cz = (z1 + z2) / 2;
        boolean placed = false;
        if (console != null) {
            if (best == dN)
                placed = placeFacing(chunk, cx, y, z1 + 1, console, BlockFace.SOUTH);
            else if (best == dS)
                placed = placeFacing(chunk, cx, y, z2 - 1, console, BlockFace.NORTH);
            else if (best == dW)
                placed = placeFacing(chunk, x1 + 1, y, cz, console, BlockFace.EAST);
            else
                placed = placeFacing(chunk, x2 - 1, y, cz, console, BlockFace.WEST);
        }
        if (!placed && modern(generator))
            sideTable(chunk, odds, cx, y, cz);
        // a bin tucked beside the console now and then
        Material bin = FurnitureTags.pick(FurnitureTags.BIN, odds);
        if (placed && bin != null && odds.flipCoin()) {
            if (best == dN)
                placeFacing(chunk, cx + 1, y, z1 + 1, bin, BlockFace.SOUTH);
            else if (best == dS)
                placeFacing(chunk, cx + 1, y, z2 - 1, bin, BlockFace.NORTH);
            else if (best == dW)
                placeFacing(chunk, x1 + 1, y, cz + 1, bin, BlockFace.EAST);
            else
                placeFacing(chunk, x2 - 1, y, cz + 1, bin, BlockFace.WEST);
        }
        if (modern(generator))
            rug(chunk, odds, cx, y, cz);
        ceilingPiece(chunk, odds, cx, y, cz);
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /**
     * A ceiling fan (Refurbished, hung with {@code facing=down}) or a hanging lantern under the
     * ceiling above (x,z). Fans stay dark and still — the mod's power system is deliberately
     * unwired — but they read as ceiling furniture all the same. Quiet no-op when there is no
     * ceiling within reach or the cell is taken.
     */
    private static void ceilingPiece(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (!odds.playOdds(0.35))
            return;
        int ceil = -1;
        for (int cy = y + 3; cy <= y + 5; cy++)
            if (!chunk.isEmpty(x, cy, z)) {
                ceil = cy;
                break;
            }
        if (ceil < 0 || !chunk.isEmpty(x, ceil - 1, z))
            return;
        Material fan = FurnitureTags.pick(FurnitureTags.CEILING_FAN, odds);
        if (fan != null && odds.playOdds(0.7))
            chunk.setBlock(x, ceil - 1, z, fan, BlockFace.DOWN);
        else
            chunk.setHangingLantern(x, ceil - 1, z, odds.flipCoin() ? Material.LANTERN : Material.SOUL_LANTERN);
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
            // A plumbed bathroom, spread around the room — everything on one wall read as a
            // showroom, not a bathroom (playtested). The bath keeps the exterior (window) wall;
            // the basin takes the next wall around with its working space in FRONT guaranteed
            // clear; the toilet takes the wall opposite the bath. And the toilet places FIRST:
            // it is the one piece a bathroom may not lack, and when it was last in a single-wall
            // run, small rooms ran out of wall and had none (playtested).
            int dN = z1, dS = 15 - z2, dW = x1, dE = 15 - x2;
            int best = Math.min(Math.min(dN, dS), Math.min(dW, dE));
            int bathWall = best == dN ? 0 : best == dE ? 1 : best == dS ? 2 : 3;
            if (toilet != null) {
                // preferred spot: the wall OPPOSITE THE DOOR (visible, reachable, never in the
                // way — the owner's read of the rooms), then opposite the bath, then whatever
                // else isn't the bath wall
                int door = doorWall(chunk, y, x1, x2, z1, z2);
                int[] walls = { door >= 0 ? (door + 2) % 4 : -1, (bathWall + 2) % 4,
                        (bathWall + 1) % 4, (bathWall + 3) % 4 };
                placing: for (int w : walls) {
                    if (w < 0 || w == bathWall)
                        continue;
                    int len = wallLen(w, x1, x2, z1, z2);
                    for (int t : new int[] { len - 1, 1, len / 2 })
                        if (t >= 1 && t <= len - 1
                                && placeOnWall(chunk, y, toilet, w, t, x1, x2, z1, z2))
                            break placing;
                }
            }
            if (bath != null) {
                int[] s = wallSpot(bathWall, x1, x2, z1, z2, 1);
                placePiece(chunk, s[0], y, s[1], bath, intoRoom(bathWall),
                        bathWall == 0 || bathWall == 2 ? BlockFace.EAST : BlockFace.SOUTH);
            }
            if (basin != null) {
                int w = (bathWall + 1) % 4, len = wallLen(w, x1, x2, z1, z2);
                for (int t : new int[] { len / 2, len / 2 + 1, len - 1 })
                    if (t >= 1 && t <= len - 1
                            && placeOnWall(chunk, y, basin, w, t, x1, x2, z1, z2))
                        break;
            }
            int bx = (x1 + x2) / 2, bz = (z1 + z2) / 2;
            if (clearFloor(chunk, bx, y, bz))
                chunk.setBlock(bx, y, bz, odds.flipCoin() ? Material.WHITE_CARPET : Material.LIGHT_BLUE_CARPET);
            wallDecor(chunk, odds, x1, x2, y, z1, z2);
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
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

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
        // (Macaw's desks are axis pieces — facing has only north|east — so the front request is a
        // no-op there and the X-run default happens to be right; Refurbished desks rotate properly.)
        if (placeFacing(chunk, cx, y, z1 + 1, desk, BlockFace.SOUTH))
            chunk.reconnect(cx, y, z1 + 1);
        if (cx + 1 <= x2 - 1 && placeFacing(chunk, cx + 1, y, z1 + 1, desk, BlockFace.SOUTH))
            chunk.reconnect(cx + 1, y, z1 + 1);
        Material chair = FurnitureTags.pick(FurnitureTags.CHAIR, odds);
        if (chair != null && z1 + 2 <= z2 - 1)
            placeFacing(chunk, cx, y, z1 + 2, chair, BlockFace.NORTH);
        Material shelf = FurnitureTags.pick(FurnitureTags.BOOKSHELF, odds);
        if (shelf != null)
            placeFacing(chunk, x1 + 1, y, z1 + 1, shelf, BlockFace.SOUTH);
        wallDecor(chunk, odds, x1, x2, y, z1, z2);
        accentRoom(generator, chunk, odds, x1 + 1, y, z1 + 1, x2 - x1 - 1, z2 - z1 - 1);
    }

    /**
     * Places a furniture piece so its <em>front</em> points {@code front} — for a seat, the way the
     * occupant looks; for a counter or toilet, the way it opens into the room.
     *
     * <p>The caller never touches the block's own {@code facing} — {@link FurnitureTags#facingFor}
     * converts, because the mods disagree about what {@code facing} means and Macaw's disagrees with
     * itself between families. Vanilla pieces pass through unchanged (no declared offset).
     *
     * <p>⚠ Deliberately does NOT {@link SupportBlocks#reconnect}. Reconnect recomputes connections
     * using the MOD's own idea of what {@code facing} means, and for pieces whose facing we
     * offset-rotated for the correct look (Macaw's couches) the two conventions disagree — a
     * straight sofa run reconnected into corner shapes in playtest. Callers that place runs of
     * connection-safe pieces (tables, desks, kitchen counters) reconnect explicitly.
     */
    private static boolean placeFacing(RealBlocks chunk, int x, int y, int z, Material piece, BlockFace front) {
        return placeIfClear(chunk, x, y, z, piece, FurnitureTags.facingFor(piece, front));
    }

    /**
     * Places a piece that may be declared two-block (the Refurbished baths — bed-like, bottom plus
     * head along {@code extendDir}). Falls back to a single-block placement when the piece isn't.
     */
    private static boolean placePiece(RealBlocks chunk, int x, int y, int z, Material piece, BlockFace front,
            BlockFace extendDir) {
        if (me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.partsFor(piece) == 2) {
            int hx = x + (extendDir == BlockFace.EAST ? 1 : extendDir == BlockFace.WEST ? -1 : 0);
            int hz = z + (extendDir == BlockFace.SOUTH ? 1 : extendDir == BlockFace.NORTH ? -1 : 0);
            if (!clearFloor(chunk, x, y, z) || !clearFloor(chunk, hx, y, hz))
                return false;
            chunk.setTwoPartFurniture(x, y, z, piece, extendDir);
            return true;
        }
        return placeFacing(chunk, x, y, z, piece, front);
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

    // --- wall geometry (walls indexed 0=N, 1=E, 2=S, 3=W) --------------------------------------

    /** The direction a piece on this wall fronts — into the room. */
    private static BlockFace intoRoom(int wall) {
        return switch (wall) {
        case 0 -> BlockFace.SOUTH;
        case 1 -> BlockFace.WEST;
        case 2 -> BlockFace.NORTH;
        default -> BlockFace.EAST;
        };
    }

    private static int wallLen(int wall, int x1, int x2, int z1, int z2) {
        return wall == 0 || wall == 2 ? x2 - x1 : z2 - z1;
    }

    /** The floor cell {@code t} steps along this wall's interior row, from its low corner. */
    private static int[] wallSpot(int wall, int x1, int x2, int z1, int z2, int t) {
        return switch (wall) {
        case 0 -> new int[] { x1 + t, z1 + 1 };
        case 2 -> new int[] { x1 + t, z2 - 1 };
        case 1 -> new int[] { x2 - 1, z1 + t };
        default -> new int[] { x1 + 1, z1 + t };
        };
    }

    /**
     * Place a piece against a wall, fronting into the room, only if its working space — the cell
     * in FRONT of it — is clear too. Nobody can use a sink they cannot stand at.
     */
    private static boolean placeOnWall(RealBlocks chunk, int y, Material piece, int wall, int t, int x1, int x2,
            int z1, int z2) {
        int[] s = wallSpot(wall, x1, x2, z1, z2, t);
        BlockFace front = intoRoom(wall);
        if (!clearFloor(chunk, s[0] + front.getModX(), y, s[1] + front.getModZ()))
            return false;
        return placeFacing(chunk, s[0], y, s[1], piece, front);
    }

    /** Which wall carries a door, or -1 — askable because furnishing runs after the doors are cut. */
    private static int doorWall(RealBlocks chunk, int y, int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x++) {
            if (chunk.isDoor(x, y, z1))
                return 0;
            if (chunk.isDoor(x, y, z2))
                return 2;
        }
        for (int z = z1; z <= z2; z++) {
            if (chunk.isDoor(x1, y, z))
                return 3;
            if (chunk.isDoor(x2, y, z))
                return 1;
        }
        return -1;
    }

    // --- checks --------------------------------------------------------------------------------

    /**
     * Air at (x,y,z) and above, solid underfoot, and not the approach to a door — a genuinely
     * usable spot. The door check is why furnishing runs after the walls are drawn: a bed or a
     * wardrobe parked in front of a doorway blocked it (playtested, twice).
     */
    private static boolean clearFloor(RealBlocks chunk, int x, int y, int z) {
        return chunk.isEmpty(x, y, z) && chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y - 1, z)
                && !chunk.isBesideDoor(x, y, z);
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
