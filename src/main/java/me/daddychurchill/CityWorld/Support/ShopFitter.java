package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.api.ShopTrade;
import me.daddychurchill.CityWorld.api.ShopType;

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
