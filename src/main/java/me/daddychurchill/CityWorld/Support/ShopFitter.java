package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.api.ShopTrade;
import me.daddychurchill.CityWorld.api.ShopType;
import me.daddychurchill.CityWorld.Plugins.LootProvider;

/**
 * Drops a villager job-site block on a classified shop's ground floor, so a store <em>reads</em> as its
 * trade (a cartography table = the map seller, a fletching table = the fletcher) and a villager can
 * claim the profession. MODERN dressing, gated by the {@code shops} setting; runs as a post-decoration
 * pass like {@link Overgrowth}, after the lot's own interior is drawn, so it lands in genuinely open
 * floor rather than fighting the room populator.
 *
 * <p>Placement is guaranteed-but-tidy: it scans the ground-floor interior for open cells standing on a
 * solid floor, prefers one against a wall or shelf (so the counter reads as "placed", not marooned),
 * faces the block inward, and lays a trade-coloured mat in front. One counter per chunk of the shop —
 * a multi-chunk store simply gets a few. All writes stay in-chunk.
 */
public final class ShopFitter {

    private ShopFitter() {}

    public static void apply(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk, Odds odds) {
        ShopType shop = lot.getShopType();
        if (shop == null)
            return;
        Material jobBlock = jobBlockFor(shop.trade());
        if (jobBlock == null)
            return;

        // Find the ground-floor interior cells (open, standing on a solid floor), splitting those that
        // back onto something solid from those in the open — we prefer the former.
        List<int[]> againstWall = new ArrayList<>();
        List<int[]> inTheOpen = new ArrayList<>();
        for (int x = 3; x <= 12; x++) {
            for (int z = 3; z <= 12; z++) {
                int fy = floorY(chunk, x, z, generator.streetLevel);
                if (fy < 0)
                    continue;
                if (solid(chunk, x + 1, fy, z) || solid(chunk, x - 1, fy, z)
                        || solid(chunk, x, fy, z + 1) || solid(chunk, x, fy, z - 1))
                    againstWall.add(new int[] { x, fy, z });
                else
                    inTheOpen.add(new int[] { x, fy, z });
            }
        }
        List<int[]> spots = !againstWall.isEmpty() ? againstWall : inTheOpen;
        if (spots.isEmpty())
            return;

        int[] c = spots.get(odds.getRandomInt(spots.size()));
        int x = c[0], y = c[1], z = c[2];

        // Face the counter inward (toward the room's middle), so it presents to a customer.
        BlockFace facing = inward(x, z);
        chunk.setBlock(x, y, z, jobBlock, facing);

        // A trade-coloured mat in front of the counter, where a shopper would stand.
        Material mat = matFor(shop.trade());
        int fx = x + facing.getModX(), fz = z + facing.getModZ();
        if (mat != null && inChunk(fx, fz) && chunk.isEmpty(fx, y, fz) && solid(chunk, fx, y - 1, fz))
            chunk.setBlock(fx, y, fz, mat);

        // and the shopkeeper — an employed villager of the trade, tending the counter
        int wx = inChunk(fx, fz) ? fx : x, wz = inChunk(fx, fz) ? fz : z;
        generator.spawnProvider.spawnWorker(generator, chunk, odds, wx, y, wz, shop.trade().profession());

        // storage/wares flanking the counter (both perpendicular sides): a barrel one side, and a
        // decorated pot or a second barrel the other, so it reads as a stocked counter
        int sx = x - facing.getModZ(), sz = z + facing.getModX();
        if (inChunk(sx, sz) && chunk.isEmpty(sx, y, sz) && solid(chunk, sx, y - 1, sz))
            chunk.setChest(generator, sx, y, sz, facing, odds, generator.lootProvider,
                    LootProvider.LootLocation.SHOP, Material.BARREL);
        int sx2 = x + facing.getModZ(), sz2 = z - facing.getModX();
        if (inChunk(sx2, sz2) && chunk.isEmpty(sx2, y, sz2) && solid(chunk, sx2, y - 1, sz2))
            chunk.setBlock(sx2, y, sz2, odds.flipCoin() ? Material.DECORATED_POT : Material.BARREL, facing);

        // the shop's name (one name, used on both signs)
        String[] name = generator.odonymProvider.generateShopName(generator, odds, shop.trade().displayName());

        // an interior hanging sign over the counter, hung from the ceiling
        if (chunk.isEmpty(x, y + 1, z) && chunk.isEmpty(x, y + 2, z) && solid(chunk, x, y + 3, z))
            chunk.setSignPost(x, y + 2, z, Material.OAK_HANGING_SIGN, facing, name);

        // and, if the shop has a front door, a hanging shopfront sign outside above it
        exteriorSign(chunk, y, name);
    }

    /**
     * Hang a shopfront sign on the outside wall just above a ground-floor door. The door is taken to face
     * the street (perimeter doors point away from the building centre), and the sign attaches to the wall
     * above it, facing out. First door that yields a clear spot wins; skips quietly if none does.
     */
    private static void exteriorSign(RealBlocks chunk, int y, String[] name) {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                if (!isDoor(chunk, x, y, z))
                    continue;
                BlockFace out = outward(x, z);
                int ox = x + out.getModX(), oz = z + out.getModZ();
                if (inChunk(ox, oz) && solid(chunk, x, y + 2, z)
                        && chunk.isEmpty(ox, y + 1, oz) && chunk.isEmpty(ox, y + 2, oz)) {
                    // a wall hanging sign's bar runs ALONG its facing axis and pins into the wall at its end,
                    // so the facing must be across the wall's outward direction: the board then hangs out
                    // over the door like a pub sign. Facing outward left the bar floating in the air and the
                    // board flat against nothing (owner, 2026-09-18: "need rotating 90 deg").
                    chunk.setWallSign(ox, y + 2, oz, Material.OAK_WALL_HANGING_SIGN,
                            BlockFace.fromDirection(out.toDirection().getClockWise()), name);
                    return;
                }
            }
    }

    /** Any DoorBlock — the shop fronts come from a pool now, and a fixed list of six wood doors lost every
     *  shop its sign the day Macaw's doors arrived (owner, 2026-09-18). */
    private static boolean isDoor(RealBlocks chunk, int x, int y, int z) {
        return chunk.isDoor(x, y, z);
    }

    /** The dominant horizontal direction from (x,z) away from the chunk centre — a perimeter door's street side. */
    private static BlockFace outward(int x, int z) {
        int dx = x - 8, dz = z - 8;
        if (Math.abs(dx) >= Math.abs(dz))
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** Lowest floor of the ground storey at (x,z): open here and above, solid underfoot. -1 if none. */
    private static int floorY(RealBlocks chunk, int x, int z, int streetLevel) {
        for (int y = streetLevel + 1; y < streetLevel + 4; y++)
            if (chunk.isEmpty(x, y, z) && chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y - 1, z))
                return y;
        return -1;
    }

    private static boolean solid(RealBlocks chunk, int x, int y, int z) {
        return inChunk(x, z) && !chunk.isEmpty(x, y, z);
    }

    private static boolean inChunk(int x, int z) {
        return x >= 0 && x < 16 && z >= 0 && z < 16;
    }

    /** The dominant horizontal direction from (x,z) toward the chunk centre (8,8). */
    private static BlockFace inward(int x, int z) {
        int dx = 8 - x, dz = 8 - z;
        if (Math.abs(dx) >= Math.abs(dz))
            return dx >= 0 ? BlockFace.EAST : BlockFace.WEST;
        return dz >= 0 ? BlockFace.SOUTH : BlockFace.NORTH;
    }

    /** The vanilla job-site block that marks each trade (all present in {@code Material}). */
    private static Material jobBlockFor(ShopTrade trade) {
        return switch (trade) {
            case NEWSAGENT, BOOKSHOP -> Material.LECTERN;
            case GREENGROCER -> Material.COMPOSTER;
            case FISHMONGER -> Material.BARREL;
            case BUTCHER -> Material.SMOKER;
            case APOTHECARY -> Material.BREWING_STAND;
            case CARTOGRAPHER -> Material.CARTOGRAPHY_TABLE;
            case FLETCHER -> Material.FLETCHING_TABLE;
            case BUILDERS_MERCHANT -> Material.STONECUTTER;
            case ARMOURER -> Material.BLAST_FURNACE;
            case TOOLSMITH -> Material.SMITHING_TABLE;
            case WEAPONSMITH -> Material.GRINDSTONE;
            case COBBLER -> Material.CAULDRON;
            case DRAPER -> Material.LOOM;
        };
    }

    /** A shop-front mat colour that hints at the trade. */
    private static Material matFor(ShopTrade trade) {
        return switch (trade) {
            case BUTCHER -> Material.RED_CARPET;
            case FISHMONGER -> Material.LIGHT_BLUE_CARPET;
            case GREENGROCER, FLETCHER -> Material.LIME_CARPET;
            case APOTHECARY -> Material.MAGENTA_CARPET;
            case CARTOGRAPHER, COBBLER -> Material.BROWN_CARPET;
            case BUILDERS_MERCHANT, WEAPONSMITH -> Material.GRAY_CARPET;
            case ARMOURER -> Material.LIGHT_GRAY_CARPET;
            case TOOLSMITH -> Material.BLACK_CARPET;
            case DRAPER -> Material.PURPLE_CARPET;
            case BOOKSHOP -> Material.BLUE_CARPET;
            case NEWSAGENT -> Material.WHITE_CARPET;
        };
    }
}
