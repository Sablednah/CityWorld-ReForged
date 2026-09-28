package me.daddychurchill.CityWorld.Plats.Urban;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;

/**
 * A shopping mall (owner, 2026-09-27): rare, big, at the edge of town, with car parks all round.
 *
 * <p><b>Where.</b> The world is cut into regions of 4x4 platmaps; each region nominates ONE platmap
 * ({@link #nominated}), and that platmap builds a mall only if it is an edge-of-town district
 * (neighbourhood, farm or outland — the caller's context), the style is modern-family, and the central
 * 4x4 block its ring roads enclose (plat 3..6 both ways) is empty, flat and unreserved. So malls are
 * spread out without any platmap ever looking at another (planning inside planning is the stall's cousin).
 * The building takes 4x3 or 4x4 of that block; the rest of the block and every empty lot across the ring
 * road from it becomes a {@link ParkingLot}, so every car park fronts a road.
 *
 * <p><b>What.</b> One {@code Mall} object is the whole plan, built once in the placer and shared by every
 * lot it claims. Everything is laid out in the mall's own coordinates — {@code a} along the long axis,
 * {@code c} across it — and drawn through a {@link Canvas} that maps to world columns and clips to the
 * chunk being decorated, so each {@link MallLot} simply runs the whole drawing and keeps its own slice.
 * The layout, long axis first:
 * <pre>
 *   apron | anchor store | shop units (both sides) round the atrium | anchor store | apron
 * </pre>
 * and across: {@code apron | shop units | walkway | ATRIUM | walkway | shop units | apron}. The atrium runs
 * the length of the middle, open to a glass roof through every floor; the walkways ring it and become
 * balconies upstairs, railed in glass. A mall entrance corridor cuts through the ground-floor units on
 * both long sides. Two glass lifts stand at opposite atrium corners, escalators climb beside the
 * balconies, and the atrium floor carries a fountain, planters with trees, benches, sculptures and food
 * kiosks. Each unit has a {@link Kind}, a name on its fascia, its kind's fittings, and a loot table
 * {@code cityworld:chests/mall_<kind>} (with an {@code _extra} hook) that the container pass assigns by
 * position ({@link MallLot#lootTableAt}).
 */
public final class Mall {

    // ---- the kinds of shop ---------------------------------------------------------------------

    /** A kind of shop: its loot table suffix, fascia colour, floor, and the words its names are made of. */
    public enum Kind {
        MUSIC("music", Material.BLACK_CONCRETE, Material.BLACK_CONCRETE, new String[] { "Records", "Music", "Sounds", "Vinyl", "Tunes" },
                new String[] { "Groove", "Sonic", "Echo", "Harmony", "Rhythm", "Blue Note" }),
        FLORIST("florist", Material.LIME_CONCRETE, Material.SMOOTH_STONE, new String[] { "Flowers", "Florist", "Blooms", "Petals", "Garden" },
                new String[] { "Rose", "Daisy", "Lily", "Bluebell", "Poppy", "Wildflower" }),
        FASHION("fashion", Material.PINK_CONCRETE, Material.WHITE_CONCRETE, new String[] { "Boutique", "Fashion", "Style", "Threads", "Wardrobe" },
                new String[] { "Velvet", "Silk", "Urban", "Chic", "Mode", "Tailored" }),
        HARDWARE("hardware", Material.ORANGE_CONCRETE, Material.GRAY_CONCRETE, new String[] { "Hardware", "Tools", "DIY", "Supplies", "Workshop" },
                new String[] { "Handy", "Ironside", "Toolbox", "Hammer", "Anvil", "Builder's" }),
        GROCER("grocer", Material.GREEN_CONCRETE, Material.WHITE_CONCRETE, new String[] { "Grocer", "Market", "Foods", "Pantry", "Fresh" },
                new String[] { "Harvest", "Orchard", "Farmhouse", "Green", "Daily", "Village" }),
        BAKERY("bakery", Material.YELLOW_CONCRETE, Material.WHITE_TERRACOTTA, new String[] { "Bakery", "Bakes", "Patisserie", "Bread Co.", "Ovens" },
                new String[] { "Golden", "Crusty", "Sweet", "Warm", "Sugar", "Hearth" }),
        CAFE("cafe", Material.BROWN_CONCRETE, Material.SPRUCE_PLANKS, new String[] { "Cafe", "Coffee", "Espresso", "Tea Room", "Beans" },
                new String[] { "Roast", "Brew", "Mocha", "Latte", "Morning", "Corner" }),
        BOOKS("books", Material.RED_CONCRETE, Material.OAK_PLANKS, new String[] { "Books", "Bookshop", "Pages", "Chapters", "Reads" },
                new String[] { "Quill", "Paperback", "Owl", "Library", "Ink", "Story" }),
        TOYS("toys", Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, new String[] { "Toys", "Games", "Toy Box", "Playtime", "Fun" },
                new String[] { "Rocket", "Teddy", "Marble", "Jester", "Kite", "Puzzle" }),
        PETS("pets", Material.LIGHT_BLUE_CONCRETE, Material.SMOOTH_STONE, new String[] { "Pets", "Pet Shop", "Aquatics", "Paws", "Critters" },
                new String[] { "Happy", "Waggy", "Whiskers", "Goldfish", "Furry", "Feathered" }),
        JEWELLER("jeweller", Material.PURPLE_CONCRETE, Material.POLISHED_DIORITE, new String[] { "Jewellers", "Gems", "Diamonds", "Gold", "Sparkle" },
                new String[] { "Crown", "Sterling", "Emerald", "Gilded", "Regal", "Amethyst" }),
        ELECTRONICS("electronics", Material.CYAN_CONCRETE, Material.LIGHT_GRAY_CONCRETE, new String[] { "Electronics", "Tech", "Gadgets", "Circuits", "Digital" },
                new String[] { "Redstone", "Volt", "Pixel", "Quantum", "Spark", "Signal" }),
        SPORTS("sports", Material.BLUE_CONCRETE, Material.GRAY_CONCRETE, new String[] { "Sports", "Outdoors", "Athletic", "Gear", "Active" },
                new String[] { "Summit", "Archer", "Sprint", "Trail", "Victory", "Angler's" }),
        PHARMACY("pharmacy", Material.WHITE_CONCRETE, Material.WHITE_CONCRETE, new String[] { "Pharmacy", "Chemist", "Remedies", "Health", "Apothecary" },
                new String[] { "Well", "Cure", "Care", "Herbal", "Family", "Green Cross" }),
        FURNITURE("furniture", Material.LIGHT_GRAY_CONCRETE, Material.OAK_PLANKS, new String[] { "Furniture", "Home", "Interiors", "Living", "Rooms" },
                new String[] { "Oak", "Cosy", "Nest", "Hearth", "Modern", "Cottage" }),
        GIFTS("gifts", Material.RED_TERRACOTTA, Material.SMOOTH_STONE, new String[] { "Gifts", "Candles", "Treasures", "Presents", "Curios" },
                new String[] { "Twinkle", "Lantern", "Keepsake", "Wick", "Charm", "Ribbon" }),
        ART("art", Material.ORANGE_TERRACOTTA, Material.WHITE_CONCRETE, new String[] { "Gallery", "Art", "Studio", "Prints", "Frames" },
                new String[] { "Canvas", "Easel", "Palette", "Brush", "Colour", "Sketch" }),
        DEPARTMENT("department", Material.BLACK_CONCRETE, Material.POLISHED_DIORITE, new String[] { "& Co.", "Department Store", "Emporium", "Stores", "Brothers" },
                new String[] { "Grand", "Royal", "Metro", "Central", "Park", "Empire" }),
        KIOSK("food", Material.RED_CONCRETE, Material.SMOOTH_STONE, new String[] { "Pretzels", "Hot Dogs", "Smoothies", "Doughnuts", "Popcorn", "Ice Cream", "Noodles", "Crepes" },
                new String[] { "Twisty", "Top", "Tasty", "Happy", "Quick", "Street" });

        public final String table;
        final Material fascia, floor;
        final String[] nouns, adjectives;

        Kind(String table, Material fascia, Material floor, String[] nouns, String[] adjectives) {
            this.table = table;
            this.fascia = fascia;
            this.floor = floor;
            this.nouns = nouns;
            this.adjectives = adjectives;
        }

        /** The loot table id every container in a unit of this kind rolls. */
        public String lootTable() {
            return "cityworld:chests/mall_" + table;
        }
    }

    /** What each kind of shop shows in its item frames: its own goods, vanilla items that exist from 1.20.1 on. */
    static final java.util.Map<Kind, net.minecraft.world.item.Item[]> GOODS = goods();

    private static net.minecraft.world.item.Item[] of(net.minecraft.world.item.Item... items) {
        return items;
    }

    private static java.util.Map<Kind, net.minecraft.world.item.Item[]> goods() {
        java.util.EnumMap<Kind, net.minecraft.world.item.Item[]> m = new java.util.EnumMap<>(Kind.class);
        m.put(Kind.MUSIC, of(Items.MUSIC_DISC_CAT, Items.MUSIC_DISC_13, Items.MUSIC_DISC_BLOCKS, Items.MUSIC_DISC_MALL,
                Items.MUSIC_DISC_CHIRP, Items.MUSIC_DISC_FAR, Items.NOTE_BLOCK, Items.GOAT_HORN));
        m.put(Kind.FLORIST, of(Items.POPPY, Items.DANDELION, Items.CORNFLOWER, Items.ALLIUM, Items.OXEYE_DAISY, Items.RED_TULIP,
                Items.AZURE_BLUET, Items.LILY_OF_THE_VALLEY));
        m.put(Kind.FASHION, of(Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS, Items.LEATHER_BOOTS,
                Items.GOLDEN_HELMET, Items.WOOL.pick(net.minecraft.world.item.DyeColor.PINK), Items.BANNER.pick(net.minecraft.world.item.DyeColor.WHITE)));
        m.put(Kind.HARDWARE, of(Items.IRON_PICKAXE, Items.IRON_AXE, Items.IRON_SHOVEL, Items.IRON_HOE, Items.SHEARS, Items.BUCKET,
                Items.FLINT_AND_STEEL, Items.LANTERN));
        m.put(Kind.GROCER, of(Items.APPLE, Items.BREAD, Items.CARROT, Items.POTATO, Items.MELON_SLICE, Items.BEETROOT,
                Items.SWEET_BERRIES, Items.EGG));
        m.put(Kind.BAKERY, of(Items.BREAD, Items.CAKE, Items.COOKIE, Items.PUMPKIN_PIE, Items.WHEAT));
        m.put(Kind.CAFE, of(Items.COOKIE, Items.HONEY_BOTTLE, Items.MILK_BUCKET, Items.COCOA_BEANS, Items.PUMPKIN_PIE));
        m.put(Kind.BOOKS, of(Items.BOOK, Items.WRITABLE_BOOK, Items.MAP, Items.FEATHER, Items.PAPER, Items.BOOKSHELF));
        m.put(Kind.TOYS, of(Items.FIREWORK_ROCKET, Items.SNOWBALL, Items.SLIME_BALL, Items.CARROT_ON_A_STICK, Items.SPYGLASS,
                Items.NAME_TAG, Items.BELL));
        m.put(Kind.PETS, of(Items.LEAD, Items.NAME_TAG, Items.BONE, Items.COD, Items.TROPICAL_FISH_BUCKET, Items.SADDLE,
                Items.WHEAT_SEEDS));
        m.put(Kind.JEWELLER, of(Items.GOLD_INGOT, Items.EMERALD, Items.DIAMOND, Items.AMETHYST_SHARD, Items.CLOCK,
                Items.GOLDEN_APPLE, Items.GOLD_NUGGET));
        m.put(Kind.ELECTRONICS, of(Items.REDSTONE, Items.REPEATER, Items.COMPARATOR, Items.CLOCK, Items.COMPASS, Items.SPYGLASS,
                Items.REDSTONE_TORCH));
        m.put(Kind.SPORTS, of(Items.BOW, Items.ARROW, Items.FISHING_ROD, Items.CROSSBOW, Items.SHIELD, Items.SADDLE));
        m.put(Kind.PHARMACY, of(Items.GLASS_BOTTLE, Items.GLISTERING_MELON_SLICE, Items.GOLDEN_CARROT, Items.HONEY_BOTTLE,
                Items.SPIDER_EYE, Items.SUGAR));
        m.put(Kind.FURNITURE, of(Items.PAINTING, Items.ITEM_FRAME, Items.LANTERN, Items.BED.pick(net.minecraft.world.item.DyeColor.WHITE), Items.FLOWER_POT, Items.CANDLE));
        m.put(Kind.GIFTS, of(Items.CANDLE, Items.DECORATED_POT, Items.FLOWER_POT, Items.FIREWORK_ROCKET, Items.NAME_TAG,
                Items.DYE.pick(net.minecraft.world.item.DyeColor.PINK)));
        m.put(Kind.ART, of(Items.DYE.pick(net.minecraft.world.item.DyeColor.RED), Items.DYE.pick(net.minecraft.world.item.DyeColor.BLUE), Items.DYE.pick(net.minecraft.world.item.DyeColor.YELLOW), Items.DYE.pick(net.minecraft.world.item.DyeColor.GREEN), Items.BRUSH, Items.PAINTING,
                Items.INK_SAC));
        m.put(Kind.DEPARTMENT, of(Items.LEATHER_CHESTPLATE, Items.CLOCK, Items.COMPASS, Items.LANTERN, Items.BOOK, Items.CANDLE,
                Items.GOLDEN_HELMET));
        m.put(Kind.KIOSK, of(Items.COOKIE, Items.BREAD));
        return m;
    }

    private static final Kind[] SHOP_KINDS = { Kind.MUSIC, Kind.FLORIST, Kind.FASHION, Kind.FASHION, Kind.HARDWARE,
            Kind.GROCER, Kind.BAKERY, Kind.CAFE, Kind.CAFE, Kind.BOOKS, Kind.TOYS, Kind.PETS, Kind.JEWELLER,
            Kind.ELECTRONICS, Kind.SPORTS, Kind.PHARMACY, Kind.FURNITURE, Kind.GIFTS, Kind.ART };

    private static final String[] SURNAMES = { "Harper", "Walker", "Bennett", "Carter", "Hughes", "Fletcher", "Mason",
            "Turner", "Price", "Reed", "Ellis", "Brooks", "Hayes", "Morgan", "Porter", "Sutton", "Wells", "Lloyd",
            "Marsh", "Fox", "Holt", "Pryce", "Quinn", "Abbott" };
    private static final String[] PLACES = { "Riverside", "Oakwood", "Meadowbrook", "Westgate", "Northfield", "Crystal",
            "Silverburn", "Parkview", "Lakeside", "Kingsgate", "Millbrook", "Harbour", "Sunset", "Highland",
            "Brookfield", "Elmwood", "Greenway", "Stonebridge" };
    private static final String[] MALL_WORDS = { "Mall", "Shopping Centre", "Galleria", "Plaza", "Centre", "Arcade" };

    // ---- the plan ------------------------------------------------------------------------------

    /** One shop unit: a floor, a side (0 low c, 1 high c), and its interior span along the mall. */
    record Unit(int floor, int side, int a0, int a1, Kind kind, String name, long seed) {
    }

    /** A food kiosk on the atrium floor, centred at (a, c). */
    record Kiosk(int a, int c, String name, Material awning) {
    }

    static final int E = 2; // the apron: the wall stands this far in from the lot edge
    static final int H = 6; // floor to floor
    static final int WALK = 4; // walkway / balcony width

    final int floors;
    final int street;
    final String name;
    final String shape;
    final Material wall, band, trim, tile1, tile2;
    final long seed;
    final boolean ruined; // APOCALYPSE: most of the lights are dead
    final CityWorldGenerator generator;
    final List<Wing> wings = new ArrayList<>();

    private Mall(CityWorldGenerator generator, String shape, Odds odds) {
        this.shape = shape;
        this.street = generator.streetLevel;
        this.ruined = generator.isApocalypseStyle();
        this.generator = generator;
        this.seed = odds.getRandomLong();
        this.floors = 2 + odds.getRandomInt(2);
        this.name = PLACES[odds.getRandomInt(PLACES.length)] + " " + MALL_WORDS[odds.getRandomInt(MALL_WORDS.length)];
        Material[][] palettes = {
                { Material.WHITE_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.LIGHT_GRAY_CONCRETE },
                { Material.of(Blocks.SMOOTH_SANDSTONE), Material.ORANGE_TERRACOTTA, Material.of(Blocks.CUT_SANDSTONE) },
                { Material.LIGHT_GRAY_CONCRETE, Material.RED_CONCRETE, Material.WHITE_CONCRETE },
                { Material.WHITE_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.SMOOTH_STONE },
                { Material.BRICKS, Material.WHITE_CONCRETE, Material.STONE_BRICKS } };
        Material[] pal = palettes[odds.getRandomInt(palettes.length)];
        this.wall = pal[0];
        this.band = pal[1];
        this.trim = pal[2];
        this.tile1 = Material.POLISHED_DIORITE;
        this.tile2 = odds.flipCoin() ? Material.POLISHED_ANDESITE : Material.SMOOTH_STONE;
    }

    /** SplitMix64: neighbouring seeds give java.util.Random correlated first draws (11 of 25 sites failed a 75% roll). */
    static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }

    static String shopName(Kind kind, Odds odds) {
        String noun = kind.nouns[odds.getRandomInt(kind.nouns.length)];
        String adj = kind.adjectives[odds.getRandomInt(kind.adjectives.length)];
        return switch (odds.getRandomInt(3)) {
        case 0 -> SURNAMES[odds.getRandomInt(SURNAMES.length)] + "'s " + noun;
        case 1 -> adj + " " + noun;
        default -> "The " + adj + " " + noun;
        };
    }

    public String name() {
        return name;
    }

    public String shape() {
        return shape;
    }

    public int floors() {
        return floors;
    }

    public int unitCount() {
        int n = 0;
        for (Wing w : wings)
            n += w.units.size();
        return n;
    }

    int floorY(int f) {
        return street + f * H;
    }

    int roofY() {
        return street + floors * H;
    }

    /** The loot table for a container at world (x, y, z): the wing it stands in decides; null for none. */
    String lootTableAt(int wx, int y, int wz) {
        for (Wing w : wings)
            if (w.contains(wx, wz))
                return w.lootTableAt(wx, y, wz);
        return null;
    }

    /** Every wing's share of {@code chunk}; each clips to its own rectangle and to the chunk. */
    void draw(CityWorldGenerator generator, RealBlocks chunk, Odds odds) {
        for (Wing w : wings)
            w.draw(generator, chunk, odds);
        // panes and bars join only when told; the row either side of this chunk too, since the neighbour's edge
        // panes were placed before these existed and never looked again
        chunk.reconnect(-1, 17, street + 1, roofY() + 3, -1, 17);
    }

    // ---- where malls go, and what shape ------------------------------------------------------

    static final int REGION = 4; // platmaps per region side
    static final int MAX_RISE = 8; // how far above the street the ground under a mall may rise before it is cut level

    /** Whether this platmap is its region's nominee. */
    static boolean nominated(CityWorldGenerator generator, PlatMap platmap) {
        int px = Math.floorDiv(platmap.originX, PlatMap.Width), pz = Math.floorDiv(platmap.originZ, PlatMap.Width);
        int rx = Math.floorDiv(px, REGION), rz = Math.floorDiv(pz, REGION);
        Odds odds = generator.shapeProvider.getMacroOddsGeneratorAt(rx * REGION * PlatMap.Width + 3, rz * REGION * PlatMap.Width + 7);
        return Math.floorMod(px, REGION) == odds.getRandomInt(REGION) && Math.floorMod(pz, REGION) == odds.getRandomInt(REGION);
    }

    /** Why sites were turned down, for the probe and the self-test: nominated / dice / no fit / roads / built. */
    private static final java.util.concurrent.atomic.AtomicIntegerArray SITES = new java.util.concurrent.atomic.AtomicIntegerArray(5);
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.concurrent.atomic.AtomicInteger> SHAPES = new java.util.concurrent.ConcurrentHashMap<>();

    public static String sites() {
        return "nominated=" + SITES.get(0) + " dice=" + SITES.get(1) + " noFit=" + SITES.get(2) + " roads=" + SITES.get(3)
                + " built=" + SITES.get(4) + " shapes=" + new java.util.TreeMap<>(SHAPES);
    }

    /**
     * A wing in plat coordinates: {@code x, z} its NW lot, {@code along} its length in lots, 3 lots wide,
     * {@code alongX} its axis, and which of its ends are open (joined to another wing's flank).
     */
    private record WingSpec(int x, int z, int along, boolean alongX, boolean open0, boolean open1) {
        int sizeX() {
            return alongX ? along : 3;
        }

        int sizeZ() {
            return alongX ? 3 : along;
        }
    }

    /**
     * A plan in plat coordinates, before anything is claimed: a main wing and the wings joined to its flanks.
     * The shapes are the ones UK shopping centres grow into — a bar, an L, a T, a U, a Z, a cross — the main
     * wing 5 to 8 lots long, each side wing 2 to 4 lots out from its flank, all three lots wide.
     */
    private static List<WingSpec> shapeSpec(String shape, int len, int arm1, int arm2, boolean alongX, int x0, int z0) {
        List<WingSpec> out = new ArrayList<>();
        // lay it out as if the main wing runs along x at z = 0..2 from x = 0, arms to +z (side 1) or -z (side 0)
        record Arm(int at, int side, int len) {
        }
        List<Arm> arms = new ArrayList<>();
        int mid = (len - 3) / 2, end = len - 3;
        switch (shape) {
        case "L" -> arms.add(new Arm(end, 1, arm1));
        case "T" -> arms.add(new Arm(mid, 1, arm1));
        case "U" -> {
            arms.add(new Arm(0, 1, arm1));
            arms.add(new Arm(end, 1, arm2));
        }
        case "Z" -> {
            arms.add(new Arm(0, 0, arm1));
            arms.add(new Arm(end, 1, arm2));
        }
        case "cross" -> {
            arms.add(new Arm(mid, 0, arm1));
            arms.add(new Arm(mid, 1, arm2));
        }
        default -> {
        }
        }
        int minZ = 0;
        for (Arm a : arms)
            if (a.side == 0)
                minZ = Math.min(minZ, -a.len);
        // main wing, then arms; the coordinates are shifted so the whole shape starts at (x0, z0)
        List<int[]> raw = new ArrayList<>(); // {x, z, along, axisX(1/0), open0, open1}
        raw.add(new int[] { 0, 0, len, 1, 0, 0 });
        for (Arm a : arms)
            raw.add(a.side == 1 ? new int[] { a.at, 3, a.len, 0, 1, 0 } : new int[] { a.at, -a.len, a.len, 0, 0, 1 });
        for (int[] r : raw) {
            int rx = r[0], rz = r[1] - minZ;
            boolean ax = r[3] == 1;
            // transpose the whole shape when the main wing runs along z
            out.add(alongX ? new WingSpec(x0 + rx, z0 + rz, r[2], ax, r[4] == 1, r[5] == 1)
                    : new WingSpec(x0 + rz, z0 + rx, r[2], !ax, r[4] == 1, r[5] == 1));
        }
        return out;
    }

    private static final String[] SHAPE_NAMES = { "bar", "L", "L", "T", "T", "U", "Z", "cross" };

    /**
     * Claim a mall in this platmap if it is the region's nominee and a shape fits. Called first thing from the
     * edge-of-town contexts' {@code populateMap}; own dice, so nothing else in the platmap rolls differently.
     * A mall may cover the platmap's own roads (their stubs dead-end at its walls) but never a building, a
     * roundabout or a structure reservation, and stays inside plat 1..8 so the platmap's edge keeps its roads
     * out to the neighbours.
     */
    public static void place(CityWorldGenerator generator, PlatMap platmap) {
        if (!generator.isModernStyle() || !generator.getSettings().includeBuildings || !generator.getSettings().includeMalls
                || !nominated(generator, platmap))
            return;
        SITES.incrementAndGet(0);
        Odds odds = new Odds(mix(platmap.originX * 734287L + platmap.originZ * 912367L + 51L));
        if (!odds.playOdds(0.75)) {
            SITES.incrementAndGet(1);
            return;
        }
        List<WingSpec> plan = null;
        String shape = null;
        for (int tries = 0; tries < 40 && plan == null; tries++) {
            String s = SHAPE_NAMES[odds.getRandomInt(SHAPE_NAMES.length)];
            int len = s.equals("bar") ? 5 + odds.getRandomInt(4) : 5 + odds.getRandomInt(3);
            int arm1 = 2 + odds.getRandomInt(3), arm2 = 2 + odds.getRandomInt(3);
            if (s.equals("cross") || s.equals("Z")) { // two arms either side of a three-wide wing: keep it in 8
                arm1 = 2 + odds.getRandomInt(2);
                arm2 = Math.min(arm2, 5 - arm1);
            }
            boolean alongX = odds.flipCoin();
            List<WingSpec> spec = shapeSpec(s, len, arm1, arm2, alongX, 0, 0);
            int maxX = 0, maxZ = 0;
            for (WingSpec w : spec) {
                maxX = Math.max(maxX, w.x + w.sizeX());
                maxZ = Math.max(maxZ, w.z + w.sizeZ());
            }
            if (maxX > 8 || maxZ > 8)
                continue;
            int x0 = 1 + odds.getRandomInt(8 - maxX + 1), z0 = 1 + odds.getRandomInt(8 - maxZ + 1);
            List<WingSpec> placed = shapeSpec(s, len, arm1, arm2, alongX, x0, z0);
            if (fits(generator, platmap, placed)) {
                plan = placed;
                shape = s;
            }
        }
        if (plan == null) {
            SITES.incrementAndGet(2);
            return;
        }
        // at least two roads must reach the building, or nobody can drive to it
        boolean[][] foot = footprint(plan);
        int roads = 0;
        for (int x = 0; x < PlatMap.Width; x++)
            for (int z = 0; z < PlatMap.Width; z++)
                if (!foot[x][z] && platmap.isExistingRoad(x, z) && touches(foot, x, z))
                    roads++;
        if (roads < 2) {
            SITES.incrementAndGet(3);
            return;
        }
        SITES.incrementAndGet(4);
        SHAPES.computeIfAbsent(shape, k -> new java.util.concurrent.atomic.AtomicInteger()).incrementAndGet();

        // build the wings; the main wing's joined flanks lose their aprons, and it gets a passage to each arm
        Mall mall = new Mall(generator, shape, odds);
        WingSpec main = plan.get(0);
        boolean flank0 = false, flank1 = false;
        for (int i = 1; i < plan.size(); i++) {
            WingSpec a = plan.get(i);
            boolean low = main.alongX ? a.z < main.z : a.x < main.x;
            if (low)
                flank0 = true;
            else
                flank1 = true;
        }
        Wing mw = mall.new Wing(platmap.originX + main.x, platmap.originZ + main.z, main.along, 3, main.alongX, false, false,
                flank0, flank1);
        mall.wings.add(mw);
        for (int i = 1; i < plan.size(); i++) {
            WingSpec a = plan.get(i);
            Wing aw = mall.new Wing(platmap.originX + a.x, platmap.originZ + a.z, a.along, 3, a.alongX, a.open0, a.open1, false,
                    false);
            mall.wings.add(aw);
            // the passage on the main wing: the arm's walkways and atrium, in the main wing's coordinates
            int endA = a.open0 ? 0 : aw.L - 1;
            int[] p0 = mw.local(aw.worldX(endA, aw.cF1 + 1), aw.worldZ(endA, aw.cF1 + 1));
            int[] p1 = mw.local(aw.worldX(endA, aw.cF2 - 1), aw.worldZ(endA, aw.cF2 - 1));
            boolean low = main.alongX ? a.z < main.z : a.x < main.x;
            mw.link(low ? 0 : 1, Math.min(p0[0], p1[0]), Math.max(p0[0], p1[0]));
        }
        for (Wing w : mall.wings)
            w.plan(odds);

        boolean origin = true;
        for (int x = 0; x < PlatMap.Width; x++)
            for (int z = 0; z < PlatMap.Width; z++)
                if (foot[x][z]) {
                    platmap.setLot(x, z, new MallLot(platmap, platmap.originX + x, platmap.originZ + z, mall, origin));
                    origin = false;
                }
        // car parks: wild, levellable lots next to the building, then next to those, kept only where joined to a road
        boolean[][] park = new boolean[PlatMap.Width][PlatMap.Width];
        for (int ring = 0; ring < 2; ring++) {
            boolean[][] next = new boolean[PlatMap.Width][PlatMap.Width];
            for (int x = 0; x < PlatMap.Width; x++)
                for (int z = 0; z < PlatMap.Width; z++)
                    if (!foot[x][z] && !park[x][z] && (touches(foot, x, z) || touches(park, x, z)) && platmap.isNaturalLot(x, z)
                            && platmap.isLevellableLots(x, z, 1, 1, MAX_RISE)
                            && !generator.isStructureReserved(platmap.originX + x, platmap.originZ + z))
                        next[x][z] = true;
            for (int x = 0; x < PlatMap.Width; x++)
                for (int z = 0; z < PlatMap.Width; z++)
                    park[x][z] |= next[x][z];
        }
        boolean[][] reached = new boolean[PlatMap.Width][PlatMap.Width];
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int x = 0; x < PlatMap.Width; x++)
                for (int z = 0; z < PlatMap.Width; z++)
                    if (park[x][z] && !reached[x][z] && (roadNext(platmap, x, z) || touches(reached, x, z))) {
                        reached[x][z] = true;
                        grew = true;
                    }
        }
        for (int x = 0; x < PlatMap.Width; x++)
            for (int z = 0; z < PlatMap.Width; z++)
                if (reached[x][z])
                    platmap.setLot(x, z, new ParkingLot(platmap, platmap.originX + x, platmap.originZ + z, mall));
    }

    private static boolean[][] footprint(List<WingSpec> plan) {
        boolean[][] foot = new boolean[PlatMap.Width][PlatMap.Width];
        for (WingSpec w : plan)
            for (int x = w.x; x < w.x + w.sizeX(); x++)
                for (int z = w.z; z < w.z + w.sizeZ(); z++)
                    if (x >= 0 && x < PlatMap.Width && z >= 0 && z < PlatMap.Width)
                        foot[x][z] = true;
        return foot;
    }

    /** Every lot of the footprint is wild or road (never a building or roundabout), unreserved and levellable. */
    private static boolean fits(CityWorldGenerator generator, PlatMap platmap, List<WingSpec> plan) {
        boolean[][] foot = footprint(plan);
        int n = 0;
        for (WingSpec w : plan)
            n += w.sizeX() * w.sizeZ();
        int count = 0;
        for (int x = 0; x < PlatMap.Width; x++)
            for (int z = 0; z < PlatMap.Width; z++) {
                if (!foot[x][z])
                    continue;
                count++;
                if (x < 1 || x > 8 || z < 1 || z > 8)
                    return false;
                PlatLot lot = platmap.getLot(x, z);
                if (lot != null && lot.style != PlatLot.LotStyle.NATURE && lot.style != PlatLot.LotStyle.ROAD)
                    return false;
                if (generator.isStructureReserved(platmap.originX + x, platmap.originZ + z)
                        || !platmap.isLevellableLots(x, z, 1, 1, MAX_RISE))
                    return false;
            }
        return count == n; // no two wings overlap
    }

    private static boolean touches(boolean[][] set, int x, int z) {
        for (int[] d : new int[][] { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } }) {
            int nx = x + d[0], nz = z + d[1];
            if (nx >= 0 && nx < PlatMap.Width && nz >= 0 && nz < PlatMap.Width && set[nx][nz])
                return true;
        }
        return false;
    }

    private static boolean roadNext(PlatMap platmap, int x, int z) {
        return platmap.isExistingRoad(x + 1, z) || platmap.isExistingRoad(x - 1, z) || platmap.isExistingRoad(x, z + 1)
                || platmap.isExistingRoad(x, z - 1);
    }

    static String[] splitName(String name) {
        if (name.length() <= 15)
            return new String[] { "", name };
        int cut = name.lastIndexOf(' ', 15);
        if (cut < 0)
            cut = 15;
        return new String[] { name.substring(0, cut).trim(), name.substring(cut).trim() };
    }

    // ---- one wing: a rectangle of the mall, the whole of it for a plain bar ------------------------

    /**
     * One rectangular wing. Its coordinates are its own: {@code a} along it from {@code a = 0}, {@code c}
     * across it. A closed end has an apron and an anchor store; an OPEN end ({@code open0}/{@code open1})
     * runs to the rectangle's edge, walkways and atrium open, where it meets the flank of the wing it joins —
     * that wing cuts a passage through its shop band ({@code links}) to meet it. A flank that another wing
     * joins has no apron ({@code eC0}/{@code eC1} = 0), so the two walls stand back to back.
     */
    final class Wing {
        final CityWorldGenerator generator = Mall.this.generator;
        final int chunkX0, chunkZ0, lenChunks, widthChunks;
        final boolean alongX, open0, open1;
        final int L, C, eA0, eA1, eC0, eC1;
        final int anchorDepth, aFront1, aFront2, shopDepth, cF1, cF2, aA0, aA1, cA0, cA1, corridorA0, corridorA1;
        final boolean[] corridorSide = new boolean[2];
        final boolean hasLifts, hasEscalators, hasFountain;
        final List<int[]> links = new ArrayList<>(); // {side, a0, a1}
        final List<Unit> units = new ArrayList<>();
        final List<Kiosk> kiosks = new ArrayList<>();
        final List<int[]> planters = new ArrayList<>(), benches = new ArrayList<>(), sculptures = new ArrayList<>();
        int fountainR, fountainA; // fountainA is MIN_VALUE when there was no room for one
        final Kind[] anchors = new Kind[2];
        final String[] anchorNames = new String[2];

        Wing(int chunkX0, int chunkZ0, int lenChunks, int widthChunks, boolean alongX, boolean open0, boolean open1,
                boolean flank0Joined, boolean flank1Joined) {
            this.chunkX0 = chunkX0;
            this.chunkZ0 = chunkZ0;
            this.lenChunks = lenChunks;
            this.widthChunks = widthChunks;
            this.alongX = alongX;
            this.open0 = open0;
            this.open1 = open1;
            this.L = lenChunks * 16;
            this.C = widthChunks * 16;
            this.eA0 = open0 ? 0 : E;
            this.eA1 = open1 ? 0 : E;
            this.eC0 = flank0Joined ? 0 : E;
            this.eC1 = flank1Joined ? 0 : E;
            this.anchorDepth = lenChunks >= 4 ? 13 : 11;
            this.aFront1 = open0 ? -1 : eA0 + 1 + anchorDepth;
            this.aFront2 = open1 ? L : L - 2 - eA1 - anchorDepth;
            this.shopDepth = C >= 64 ? 15 : 11;
            this.cF1 = eC0 + shopDepth + 1;
            this.cF2 = C - 1 - eC1 - shopDepth - 1;
            this.cA0 = cF1 + WALK + 1;
            this.cA1 = cF2 - WALK - 1;
            this.aA0 = aFront1 + WALK + 1;
            this.aA1 = aFront2 - WALK - 1;
            int mid = (aFront1 + aFront2) / 2;
            this.corridorA0 = mid - 3;
            this.corridorA1 = mid + 2;
            this.corridorSide[0] = eC0 > 0;
            this.corridorSide[1] = eC1 > 0;
            int len = aA1 - aA0 + 1;
            this.hasLifts = len >= 12;
            this.hasEscalators = len >= 14;
            this.hasFountain = len >= 16;
        }

        /** A passage on flank {@code side} over {@code a0..a1}, to a wing joined there. Call before {@link #plan}. */
        void link(int side, int a0, int a1) {
            links.add(new int[] { side, a0, a1 });
        }

        /** Name the anchors, cut the shop rows into units round the passages and corridors, lay out the atrium. */
        void plan(Odds odds) {
            for (int e = 0; e < 2; e++) {
                if (e == 0 ? open0 : open1)
                    continue;
                anchors[e] = odds.getRandomInt(3) == 0 ? Kind.GROCER : odds.getRandomInt(4) == 0 ? Kind.FURNITURE : Kind.DEPARTMENT;
                anchorNames[e] = anchors[e] == Kind.DEPARTMENT
                        ? SURNAMES[odds.getRandomInt(SURNAMES.length)] + " " + anchors[e].nouns[odds.getRandomInt(anchors[e].nouns.length)]
                        : shopName(anchors[e], odds);
            }
            for (int f = 0; f < floors; f++)
                for (int side = 0; side < 2; side++) {
                    List<int[]> gaps = new ArrayList<>();
                    for (int[] l : links)
                        if (l[0] == side)
                            gaps.add(new int[] { l[1], l[2] });
                    if (f == 0 && corridorSide[side])
                        gaps.add(new int[] { corridorA0, corridorA1 });
                    gaps.sort((x, y) -> Integer.compare(x[0], y[0]));
                    int from = aFront1 + 1;
                    for (int[] g : gaps) {
                        partition(f, side, from, g[0] - 2, odds);
                        from = g[1] + 2;
                    }
                    partition(f, side, from, aFront2 - 1, odds);
                }
            layoutAtrium(odds);
        }

        /**
         * The atrium floor, on a reservation grid so nothing lands on anything else: the fixed pieces first
         * (lifts at opposite corners, escalators beside the long balconies, the fountain in the middle — each
         * only where the atrium is long enough for it), then kiosks, planters with benches and sculptures
         * wherever the grid is still free, with room to walk round them.
         */
        private void layoutAtrium(Odds odds) {
            res = new boolean[Math.max(1, aA1 - aA0 + 1)][Math.max(1, cA1 - cA0 + 1)];
            if (hasLifts) {
                reserve(aA0, aA0 + 4, cA0, cA0 + 4);
                reserve(aA1 - 4, aA1, cA1 - 4, cA1);
            }
            if (hasEscalators) {
                reserve(aA0 + 4, aA0 + 8 + H, cA1 - 2, cA1);
                if (floors >= 3)
                    reserve(aA1 - 9 - H, aA1 - 4, cA0, cA0 + 2);
            }
            // the rim (r + 1 either side of a centre between two cells) must fit between the escalators, which
            // hold the three cells along each long side
            fountainR = Math.max(2, Math.min(3, (cA1 - cA0 - 7) / 2));
            int am = (aA0 + aA1) / 2, cm = (cA0 + cA1) / 2;
            fountainA = Integer.MIN_VALUE;
            for (int d = 0; hasFountain && d <= (aA1 - aA0) / 2 && fountainA == Integer.MIN_VALUE; d++)
                for (int a : new int[] { am - d, am + d })
                    if (fountainA == Integer.MIN_VALUE
                            && free(a - fountainR - 1, a + fountainR + 2, cm - fountainR, cm + fountainR + 1)) {
                        fountainA = a;
                        reserve(a - fountainR - 1, a + fountainR + 2, cm - fountainR - 1, cm + fountainR + 2);
                    }
            Material[] awnings = { Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL,
                    Material.ORANGE_WOOL, Material.PINK_WOOL };
            for (int end = 0; end < 2; end++)
                search: for (int i = 0; i <= aA1 - aA0; i++) {
                    int a = end == 0 ? aA0 + 2 + i : aA1 - 2 - i;
                    for (int c = cA0 + 3; c <= cA1 - 2; c++)
                        if (free(a - 2, a + 2, c - 3, c + 2)) {
                            kiosks.add(new Kiosk(a, c, shopName(Kind.KIOSK, odds), awnings[odds.getRandomInt(awnings.length)]));
                            reserve(a - 2, a + 2, c - 3, c + 2);
                            break search;
                        }
                }
            for (int a = aA0 + 1; a <= aA1 - 1; a++)
                for (int c = cA0 + 1; c <= cA1 - 1; c++)
                    if (planters.size() < 6 && free(a - 1, a + 1, c - 1, c + 1) && free(Math.max(aA0, a - 2), Math.min(aA1, a + 2),
                            Math.max(cA0, c - 2), Math.min(cA1, c + 2))) {
                        planters.add(new int[] { a, c });
                        reserve(a - 1, a + 1, c - 1, c + 1);
                        for (int d : new int[] { 2, -2 })
                            if (c + d >= cA0 && c + d <= cA1 && free(a, a, c + d, c + d)) {
                                benches.add(new int[] { a, c + d, d > 0 ? 1 : 0 });
                                break;
                            }
                        reserve(a - 2, a + 2, c - 2, c + 2);
                    }
            for (int a = aA0 + 2; a <= aA1 - 2 && sculptures.size() < 2; a++)
                for (int c = cA0 + 2; c <= cA1 - 2; c++)
                    if (sculptures.size() < 2 && free(a - 2, a + 2, c - 2, c + 2)) {
                        sculptures.add(new int[] { a, c, sculptures.size() });
                        reserve(a - 2, a + 2, c - 2, c + 2);
                    }
        }

        /** Whether world column (wx, wz) lies in this wing's rectangle. */
        boolean contains(int wx, int wz) {
            int[] ac = local(wx, wz);
            return ac[0] >= 0 && ac[0] < L && ac[1] >= 0 && ac[1] < C;
        }

        private boolean[][] res;

        private void reserve(int a0, int a1, int c0, int c1) {
            for (int a = Math.max(a0, aA0); a <= Math.min(a1, aA1); a++)
                for (int c = Math.max(c0, cA0); c <= Math.min(c1, cA1); c++)
                    res[a - aA0][c - cA0] = true;
        }

        private boolean free(int a0, int a1, int c0, int c1) {
            if (a0 < aA0 || a1 > aA1 || c0 < cA0 || c1 > cA1)
                return false;
            for (int a = a0; a <= a1; a++)
                for (int c = c0; c <= c1; c++)
                    if (res[a - aA0][c - cA0])
                        return false;
            return true;
        }

        /** Cut {@code [a0, a1]} into shop units separated by one-block walls. */
        private void partition(int floor, int side, int a0, int a1, Odds odds) {
            int[] widths = { 5, 7, 7, 9, 11, 15 };
            int pos = a0;
            while (pos <= a1) {
                int w = widths[odds.getRandomInt(widths.length)];
                if (a1 - (pos + w - 1) < 6)
                    w = a1 - pos + 1;
                Kind kind = SHOP_KINDS[odds.getRandomInt(SHOP_KINDS.length)];
                units.add(new Unit(floor, side, pos, pos + w - 1, kind, shopName(kind, odds), odds.getRandomLong()));
                pos += w + 1;
            }
        }

        // ---- coordinates ---------------------------------------------------------------------------

        int worldX(int a, int c) {
            return chunkX0 * 16 + (alongX ? a : c);
        }

        int worldZ(int a, int c) {
            return chunkZ0 * 16 + (alongX ? c : a);
        }

        /** Mall coordinates of a world column: {a, c}. */
        int[] local(int wx, int wz) {
            int rx = wx - chunkX0 * 16, rz = wz - chunkZ0 * 16;
            return alongX ? new int[] { rx, rz } : new int[] { rz, rx };
        }

        /** The world face for a mall direction: +a, -a, +c, -c. */
        BlockFace plusA() {
            return alongX ? BlockFace.EAST : BlockFace.SOUTH;
        }

        BlockFace minusA() {
            return plusA().getOppositeFace();
        }

        BlockFace plusC() {
            return alongX ? BlockFace.SOUTH : BlockFace.EAST;
        }

        BlockFace minusC() {
            return plusC().getOppositeFace();
        }

        /** The unit a mall cell belongs to, on floor {@code f}, or null. */
        Unit unitAt(int f, int a, int c) {
            int side = c < cF1 ? 0 : c > cF2 ? 1 : -1;
            if (side < 0)
                return null;
            for (Unit u : units)
                if (u.floor == f && u.side == side && a >= u.a0 && a <= u.a1)
                    return u;
            return null;
        }

        /** Which floor a world height falls on. */
        int floorOf(int y) {
            return Math.max(0, Math.min(floors - 1, Math.floorDiv(y - street, H)));
        }

        /** The loot table for a container at world (x, y, z), by the unit or anchor it stands in; null for none. */
        String lootTableAt(int wx, int y, int wz) {
            int[] ac = local(wx, wz);
            int a = ac[0], c = ac[1], f = floorOf(y);
            if (a < aFront1 && anchors[0] != null)
                return anchors[0].lootTable();
            if (a > aFront2 && anchors[1] != null)
                return anchors[1].lootTable();
            Unit u = unitAt(f, a, c);
            if (u != null)
                return u.kind.lootTable();
            return Kind.KIOSK.lootTable();
        }

        // ---- drawing -------------------------------------------------------------------------------

        /** Draws in mall coordinates onto one chunk, clipping everything outside it. */
        final class Canvas {
            final RealBlocks chunk;
            final int ox, oz;

            Canvas(RealBlocks chunk) {
                this.chunk = chunk;
                this.ox = chunk.getOriginX();
                this.oz = chunk.getOriginZ();
            }

            /** Chunk-local x/z of a mall cell, or -1 if it is not in this chunk. */
            int lx(int a, int c) {
                int x = worldX(a, c) - ox;
                return x >= 0 && x < 16 ? x : -1;
            }

            int lz(int a, int c) {
                int z = worldZ(a, c) - oz;
                return z >= 0 && z < 16 ? z : -1;
            }

            boolean in(int a, int c) {
                return a >= 0 && a < L && c >= 0 && c < C && lx(a, c) >= 0 && lz(a, c) >= 0;
            }

            void set(int a, int c, int y, Material m) {
                if (in(a, c))
                    chunk.setBlock(lx(a, c), y, lz(a, c), m);
            }

            void set(int a, int c, int y, Material m, BlockFace facing) {
                if (in(a, c))
                    chunk.setBlock(lx(a, c), y, lz(a, c), m, facing);
            }

            void stair(int a, int c, int y, Material m, BlockFace facing) {
                if (in(a, c))
                    chunk.setStair(lx(a, c), y, lz(a, c), m, facing);
            }

            boolean empty(int a, int c, int y) {
                return in(a, c) && chunk.isEmpty(lx(a, c), y, lz(a, c));
            }

            /** Fill the box a0..a1 x y0..y1 x c0..c1 (inclusive), clipped to the chunk. */
            void fill(int a0, int a1, int y0, int y1, int c0, int c1, Material m) {
                for (int a = Math.min(a0, a1); a <= Math.max(a0, a1); a++)
                    for (int c = Math.min(c0, c1); c <= Math.max(c0, c1); c++) {
                        if (!in(a, c))
                            continue;
                        int x = lx(a, c), z = lz(a, c);
                        for (int y = y0; y <= y1; y++)
                            chunk.setBlock(x, y, z, m);
                    }
            }

            /** A lantern hanging from whatever is above it (a chain). */
            void hanging(int a, int c, int y) {
                if (in(a, c))
                    chunk.setHangingLantern(lx(a, c), y, lz(a, c), Material.LANTERN);
            }

            /** Whether the box a0..a1 x c0..c1 lies wholly in this chunk — for water, which must not straddle one. */
            boolean wholly(int a0, int a1, int c0, int c1) {
                return in(a0, c0) && in(a1, c0) && in(a0, c1) && in(a1, c1);
            }

            void sign(int a, int c, int y, BlockFace facing, String... lines) {
                if (in(a, c))
                    chunk.setWallSign(lx(a, c), y, lz(a, c), facing, lines);
            }

            void furniture(int a, int c, int y, Material pooled, Material fallback,
                    BlockFace look) {
                if (!in(a, c) || !chunk.isEmpty(lx(a, c), y, lz(a, c)))
                    return;
                if (pooled != null && chunk.setFurniture(lx(a, c), y, lz(a, c), pooled, FurnitureTags.facingFor(pooled, look)))
                    return;
                if (fallback != null)
                    chunk.setBlock(lx(a, c), y, lz(a, c), fallback, look);
            }
        }

        /** Everything this wing has in {@code chunk}. */
        void draw(CityWorldGenerator generator, RealBlocks chunk, Odds odds) {
            Canvas k = new Canvas(chunk);
            shell(k);
            for (int f = 0; f < floors; f++)
                floor(generator, k, f);
            links(k);
            atrium(generator, k, odds);
            lifts(k);
            escalators(k);
            for (Unit u : units)
                unit(generator, k, u);
            for (int e = 0; e < 2; e++)
                if (anchors[e] != null)
                    anchor(generator, k, e);
            for (Kiosk q : kiosks)
                kiosk(k, q);
            exterior(k);
            lighting(k);
            walls(k);
        }

        /**
         * Ceiling lights, drawn after everything else. A floor's ceiling is the next floor's slab, which is
         * drawn after the floor below it, so lights set into it were paved over on every floor but the top —
         * the "dark shops", and the lights that showed only where a wall happened to stand above. On the top
         * floor a light sits flush in the roof; below that it is a lantern hung under the slab.
         */
        private void lighting(Canvas k) {
            int stairC = C - 3 - eC1;
            for (int f = 0; f < floors; f++) {
                for (int a = aIn0(); a <= aIn1(); a++)
                    for (int c = eC0 + 1; c <= C - 2 - eC1; c++) {
                        boolean atrium = a >= aA0 && a <= aA1 && c >= cA0 && c <= cA1;
                        boolean walk = !atrium && a > aFront1 && a < aFront2 && c > cF1 && c < cF2;
                        if (walk && (a + 2 * c) % 6 == 0)
                            light(k, a, c, f);
                    }
                for (Unit u : units) {
                    if (u.floor != f)
                        continue;
                    int front = u.side == 0 ? cF1 : cF2, back = u.side == 0 ? eC0 + 1 : C - 2 - eC1;
                    int dir = u.side == 0 ? -1 : 1, depth = Math.abs(back - front);
                    for (int a = u.a0 + 1; a <= u.a1 - 1; a += 3)
                        for (int v = 2; v < depth; v += 4)
                            light(k, a, front + dir * v, f);
                }
                for (int e = 0; e < 2; e++) {
                    if (anchors[e] == null)
                        continue;
                    int front = e == 0 ? aFront1 : aFront2, outer = e == 0 ? eA0 : L - 1 - eA1;
                    for (int a = Math.min(front, outer) + 2; a <= Math.max(front, outer) - 2; a += 4)
                        for (int c = eC0 + 3; c < stairC - 1; c += 4)
                            light(k, a, c, f);
                }
                for (int[] l : links) {
                    int c0 = l[0] == 0 ? 0 : cF2, c1 = l[0] == 0 ? cF1 : C - 1;
                    for (int c = c0 + 2; c <= c1 - 2; c += 3)
                        light(k, (l[1] + l[2]) / 2, c, f);
                }
            }
        }

        /**
         * One ceiling light. In a ruined world three in four are dead, which the owner asked for because a dark
         * mall is a mall the zombies spawn in: a dead bulb in the roof, or a bare chain (or nothing) where a
         * lantern hung.
         */
        private void light(Canvas k, int a, int c, int f) {
            int h = (worldX(a, c) * 73856093) ^ (worldZ(a, c) * 19349663) ^ (f * 83492791);
            boolean dead = ruined && Math.floorMod(h, 4) != 0;
            if (f == floors - 1) {
                k.set(a, c, roofY(), dead ? Material.COPPER_BULB : Material.SEA_LANTERN);
                return;
            }
            int y = floorY(f) + H - 1;
            if (!k.empty(a, c, y) || k.empty(a, c, y + 1))
                return;
            if (!dead)
                k.hanging(a, c, y);
            else if (Math.floorMod(h >> 3, 2) == 0)
                k.set(a, c, y, Material.IRON_CHAIN);
        }

        /**
         * The walls: in every shop, its goods in item frames at eye height along both side walls and the back
         * wall, every other cell a shelf of its stock where the version has a shelf block; the art shop hangs
         * paintings as well. Paintings along the link corridors, paintings and frames on the anchor stores' outer
         * walls. Entities go last, after every block they could collide with.
         */
        private void walls(Canvas k) {
            for (Unit u : units) {
                int y = floorY(u.floor) + 3; // eye height, above the shelving
                int front = u.side == 0 ? cF1 : cF2, back = u.side == 0 ? eC0 + 1 : C - 2 - eC1;
                int dir = u.side == 0 ? -1 : 1, depth = Math.abs(back - front);
                Odds odds = new Odds(u.seed ^ 0x5eedL);
                for (int v = 2; v <= depth - 3; v += 2) {
                    decor(k, u, u.a0, front + dir * v, y, minusA(), odds, v);
                    decor(k, u, u.a1, front + dir * v, y, plusA(), odds, v + 1);
                }
                for (int a = u.a0 + 1; a <= u.a1 - 1; a += 2)
                    decor(k, u, a, front + dir * depth, y, dir < 0 ? minusC() : plusC(), odds, a);
            }
            for (int f = 0; f < floors; f++) {
                int y = floorY(f) + 3;
                Odds odds = new Odds(seed ^ (f * 7919L));
                for (int[] l : links) {
                    int c0 = l[0] == 0 ? 1 : cF2 + 1, c1 = l[0] == 0 ? cF1 - 1 : C - 2;
                    for (int c = c0 + 1; c <= c1 - 1; c += 3) {
                        paint(k, l[1], c, y, minusA(), odds);
                        paint(k, l[2], c, y, plusA(), odds);
                    }
                }
                for (int e = 0; e < 2; e++) {
                    if (anchors[e] == null)
                        continue;
                    int outer = e == 0 ? eA0 : L - 1 - eA1, in = e == 0 ? 1 : -1;
                    BlockFace wallSide = e == 0 ? minusA() : plusA();
                    net.minecraft.world.item.Item[] goods = GOODS.get(anchors[e]);
                    for (int c = eC0 + 3; c <= C - 6 - eC1; c += 3)
                        if (odds.flipCoin())
                            paint(k, outer + in, c, y, wallSide, odds);
                        else if (goods != null)
                            frame(k, outer + in, c, y, wallSide, goods[odds.getRandomInt(goods.length)]);
                }
            }
        }

        private void decor(Canvas k, Unit u, int a, int c, int y, BlockFace wallSide, Odds odds, int i) {
            if (!k.in(a, c))
                return;
            if (u.kind == Kind.ART && i % 2 == 0) {
                paint(k, a, c, y, wallSide, odds);
                return;
            }
            Material shelf = FurnitureTags.pick(FurnitureTags.SHELF, new Odds(u.seed)); // one wood per shop
            if (i % 2 == 1 && shelf != null && me.daddychurchill.CityWorld.Support.WallDecor.shelf(k.chunk, odds, k.lx(a, c), y,
                    k.lz(a, c), wallSide, u.kind.lootTable(), shelf))
                return;
            net.minecraft.world.item.Item[] goods = GOODS.get(u.kind);
            if (goods != null)
                frame(k, a, c, y, wallSide, goods[odds.getRandomInt(goods.length)]);
        }

        private void paint(Canvas k, int a, int c, int y, BlockFace wallSide, Odds odds) {
            if (k.in(a, c))
                me.daddychurchill.CityWorld.Support.WallDecor.painting(k.chunk, odds, k.lx(a, c), y, k.lz(a, c), wallSide);
        }

        private void frame(Canvas k, int a, int c, int y, BlockFace wallSide, net.minecraft.world.item.Item item) {
            if (!k.in(a, c))
                return;
            // leather in the frames wears this season's colours and trims (Support/Fashion)
            if (me.daddychurchill.CityWorld.Support.Fashion.isLeather(item) && k.chunk.getServerLevel() != null)
                me.daddychurchill.CityWorld.Support.WallDecor.frame(k.chunk, k.lx(a, c), y, k.lz(a, c), wallSide,
                        me.daddychurchill.CityWorld.Support.Fashion.dress(k.chunk.getServerLevel(), item,
                                new Odds(worldX(a, c) * 31L + worldZ(a, c) * 17L + y)));
            else
                me.daddychurchill.CityWorld.Support.WallDecor.frame(k.chunk, k.lx(a, c), y, k.lz(a, c), wallSide, item);
        }

        /** The first and last column of the interior along the wing: an open end runs to the rectangle's edge. */
        int aIn0() {
            return open0 ? 0 : eA0 + 1;
        }

        int aIn1() {
            return open1 ? L - 1 : L - 2 - eA1;
        }

        /** Whether (a, c) is on this wing's outer wall. An open end has wall only across its shop bands. */
        boolean isWall(int a, int c) {
            int aW0 = open0 ? 0 : eA0, aW1 = open1 ? L - 1 : L - 1 - eA1;
            if (a < aW0 || a > aW1 || c < eC0 || c > C - 1 - eC1)
                return false;
            if (c == eC0 || c == C - 1 - eC1)
                return true;
            boolean shopBand = c <= cF1 || c >= cF2;
            if (a == aW0)
                return !open0 || shopBand;
            if (a == aW1)
                return !open1 || shopBand;
            return false;
        }

        /** The box: aprons, outer walls with a band at every floor, the roof and its parapet. */
        private void shell(Canvas k) {
            int top = roofY();
            k.fill(0, L - 1, street, street, 0, C - 1, Material.SMOOTH_STONE); // apron and ground slab
            for (int a = 0; a < L; a++)
                for (int c = eC0; c <= C - 1 - eC1; c++) {
                    if (!isWall(a, c))
                        continue;
                    k.fill(a, a, street + 1, top, c, c, wall);
                    for (int f = 1; f <= floors; f++)
                        k.set(a, c, floorY(f) - 1, band);
                    k.set(a, c, top + 1, trim); // parapet
                    // tall narrow windows between the bands, every fourth block, on walls that face the open air
                    boolean sideWall = c == eC0 || c == C - 1 - eC1;
                    boolean outside = sideWall ? (c == eC0 ? eC0 : eC1) > 0 : !(a == 0 && open0 || a == L - 1 && open1);
                    if (outside && (sideWall ? a : c) % 4 == 2)
                        for (int f = 0; f < floors; f++)
                            k.fill(a, a, floorY(f) + 2, floorY(f) + 3, c, c, Material.GLASS);
                }
            // the roof, with the atrium's glass skylight raised one block on a frame
            k.fill(aIn0(), aIn1(), top, top, eC0 + 1, C - 2 - eC1, Material.SMOOTH_STONE);
            k.fill(aA0, aA1, top, top, cA0, cA1, Material.AIR);
            for (int a = aA0 - 1; a <= aA1 + 1; a++)
                for (int c = cA0 - 1; c <= cA1 + 1; c++) {
                    boolean rim = a == aA0 - 1 || a == aA1 + 1 || c == cA0 - 1 || c == cA1 + 1;
                    k.set(a, c, top + 1, rim ? trim : (a - aA0) % 5 == 0 ? trim : Material.GLASS);
                }
            // rooftop plant: a few air handlers over the anchors
            for (int a = eA0 + 4; a < aFront1 - 2; a += 5)
                k.fill(a, a + 1, top + 1, top + 2, eC0 + 4, eC0 + 5, Material.IRON_BLOCK);
            for (int a = aFront2 + 3; a < L - eA1 - 4; a += 5)
                k.fill(a, a + 1, top + 1, top + 2, C - eC1 - 6, C - eC1 - 5, Material.IRON_BLOCK);
        }

        /** One floor: its slab (upper floors leave the atrium open), the balcony rail, the ceiling lights. */
        private void floor(CityWorldGenerator generator, Canvas k, int f) {
            int y = floorY(f);
            for (int a = aIn0(); a <= aIn1(); a++)
                for (int c = eC0 + 1; c <= C - 2 - eC1; c++) {
                    if (isWall(a, c))
                        continue;
                    boolean atrium = a >= aA0 && a <= aA1 && c >= cA0 && c <= cA1;
                    if (f > 0 && atrium) {
                        k.fill(a, a, y, y + H - 1, c, c, Material.AIR);
                        continue;
                    }
                    boolean walk = !atrium && a > aFront1 && a < aFront2 && c > cF1 && c < cF2;
                    k.set(a, c, y, walk || atrium ? ((a + c) % 2 == 0 ? tile1 : tile2) : Material.SMOOTH_STONE);
                    k.fill(a, a, y + 1, y + H - 1, c, c, Material.AIR);
                    // the balcony rail: glass on the walkway cells that border the open atrium
                    if (f > 0 && walk && (a == aA0 - 1 || a == aA1 + 1 || c == cA0 - 1 || c == cA1 + 1)
                            && a >= aA0 - 1 && a <= aA1 + 1 && c >= cA0 - 1 && c <= cA1 + 1)
                        k.set(a, c, y + 1, Material.GLASS_PANE);
                }
            // the mall entrance corridors on the ground floor, through the shop band, on the outward sides
            if (f == 0)
                for (int side = 0; side < 2; side++) {
                    if (!corridorSide[side])
                        continue;
                    int c0 = side == 0 ? eC0 : cF2, c1 = side == 0 ? cF1 : C - 1 - eC1;
                    k.fill(corridorA0, corridorA1, y, y, c0, c1, tile1);
                    k.fill(corridorA0, corridorA1, y + 1, y + 4, c0, c1, Material.AIR);
                    k.fill(corridorA0 - 1, corridorA0 - 1, y + 1, y + H - 1, c0 + 1, c1 - 1, trim);
                    k.fill(corridorA1 + 1, corridorA1 + 1, y + 1, y + H - 1, c0 + 1, c1 - 1, trim);
                    int outer = side == 0 ? eC0 : C - 1 - eC1;
                    k.fill(corridorA0, corridorA1, y + 1, y + 4, outer, outer, Material.GLASS_PANE);
                    k.fill(corridorA0 + 2, corridorA1 - 2, y + 1, y + 3, outer, outer, Material.AIR); // the doors
                    BlockFace out = side == 0 ? minusC() : plusC();
                    int signC = side == 0 ? outer - 1 : outer + 1;
                    k.sign((corridorA0 + corridorA1) / 2, signC, y + 5, out, name);
                    k.sign((corridorA0 + corridorA1) / 2 + 1, signC, y + 5, out, "Entrance");
                    for (int a = corridorA0; a <= corridorA1; a += 2)
                        k.set(a, (c0 + c1) / 2, y + H - 1, Material.SEA_LANTERN);
                }
        }

        /**
         * The passages to the wings joined onto this one's flanks: on every floor, a hall the width of the joined
         * wing's walkways and atrium, cut from this wing's walkway out through its shop band and wall to the
         * rectangle's edge, where the other wing's open end meets it. Floored on every level (a bridge upstairs),
         * walled either side, lit.
         */
        private void links(Canvas k) {
            int top = roofY();
            for (int[] l : links) {
                int side = l[0], a0 = l[1], a1 = l[2];
                int c0 = side == 0 ? 0 : cF2, c1 = side == 0 ? cF1 : C - 1;
                for (int f = 0; f < floors; f++) {
                    int y = floorY(f);
                    k.fill(a0, a1, y, y, c0, c1, (f + a0) % 2 == 0 ? tile1 : tile2);
                    k.fill(a0, a1, y + 1, (f == floors - 1 ? top : floorY(f + 1)) - 1, c0, c1, Material.AIR);
                    k.fill(a0 - 1, a0 - 1, y + 1, y + H - 1, c0, c1, trim);
                    k.fill(a1 + 1, a1 + 1, y + 1, y + H - 1, c0, c1, trim);
                }
                k.fill(a0 - 1, a1 + 1, top, top, c0, c1, Material.SMOOTH_STONE);
            }
        }

        /** The atrium floor: the fountain, planters with trees, benches, sculptures, and lanterns overhead. */
        private void atrium(CityWorldGenerator generator, Canvas k, Odds odds) {
            int y = street, stand = y + 1;
            int am = fountainA, cm = (cA0 + cA1) / 2;
            boolean hasFountain = fountainA != Integer.MIN_VALUE;
            double fa = am + 0.5, fc = cm + 0.5; // the fountain centre, between four cells
            int r = fountainR;
            for (int a = aA0; a <= aA1 && hasFountain; a++)
                for (int c = cA0; c <= cA1; c++) {
                    double d = Math.hypot(a + 0.5 - fa - 0.5, c + 0.5 - fc - 0.5);
                    if (d <= r) {
                        k.set(a, c, y, Material.PRISMARINE_BRICKS);
                        k.set(a, c, stand, Material.WATER);
                    } else if (d <= r + 1) {
                        k.set(a, c, stand, Material.SMOOTH_QUARTZ);
                    }
                }
            if (hasFountain) {
                k.fill(am, am, stand, stand + 2, cm, cm, Material.PRISMARINE); // the spout
                k.set(am, cm, stand + 3, Material.SEA_LANTERN);
            }
            for (int[] p : planters)
                planter(k, p[0], p[1], stand);
            for (int[] b : benches)
                k.stair(b[0], b[1], stand, Material.of(Blocks.SMOOTH_QUARTZ_STAIRS), b[2] == 1 ? plusC() : minusC());
            for (int[] s : sculptures)
                sculpture(k, s[0], s[1], stand, s[2]);
            // lanterns hung from the skylight down the atrium's centre line
            int top = roofY();
            for (int a = aA0 + 2; a <= aA1 - 2; a += 4) {
                k.fill(a, a, top - 3, top, cm, cm, Material.IRON_CHAIN);
                if (!ruined || Math.floorMod(worldX(a, cm) * 31 + worldZ(a, cm) * 17, 2) == 0)
                    k.hanging(a, cm, top - 4);
            }
        }

        private void planter(Canvas k, int a0, int c0, int stand) {
            for (int a = a0 - 1; a <= a0 + 1; a++)
                for (int c = c0 - 1; c <= c0 + 1; c++)
                    k.set(a, c, stand, a == a0 && c == c0 ? Material.DIRT : Material.STONE_BRICKS);
            k.set(a0, c0, stand, Material.MOSS_BLOCK);
            k.fill(a0, a0, stand + 1, stand + 3, c0, c0, Material.of(Blocks.STRIPPED_BIRCH_LOG));
            for (int a = a0 - 1; a <= a0 + 1; a++)
                for (int c = c0 - 1; c <= c0 + 1; c++)
                    for (int y = stand + 3; y <= stand + 4; y++)
                        if ((a != a0 || c != c0 || y == stand + 4) && k.in(a, c))
                            k.chunk.setLeaves(k.lx(a, c), y, k.lz(a, c), Material.FLOWERING_AZALEA_LEAVES);
        }

        private void sculpture(Canvas k, int a, int c, int stand, int which) {
            k.fill(a - 1, a + 1, stand, stand, c - 1, c + 1, Material.POLISHED_BLACKSTONE);
            switch (which) {
            case 0 -> { // a twisting copper column
                for (int i = 0; i < 4; i++) {
                    int da = i % 2 == 0 ? 0 : (i == 1 ? 1 : -1);
                    k.set(a + da, c, stand + 1 + i, i % 2 == 0 ? Material.CUT_COPPER : Material.COPPER_BLOCK);
                }
                k.set(a, c, stand + 5, Material.LIGHTNING_ROD);
            }
            default -> { // a quartz obelisk with an amethyst cap
                k.fill(a, a, stand + 1, stand + 3, c, c, Material.QUARTZ_PILLAR);
                k.set(a, c, stand + 4, Material.of(Blocks.AMETHYST_BLOCK));
                k.set(a, c, stand + 5, Material.AMETHYST_CLUSTER);
            }
            }
        }

        /**
         * Two glass lifts at opposite corners of the atrium, full height. Each is a 4x4 glass tower with a
         * solid core on its atrium side and a ladder up its face, a doorway onto the walkway at every floor
         * with a threshold to step on, and a lamp in its cap. (Vanilla has no lift car; the ladder is how
         * you ride it.)
         */
        private void lifts(Canvas k) {
            if (!hasLifts)
                return;
            lift(k, aA0, cA0, true);
            lift(k, aA1 - 3, cA1 - 3, false);
        }

        private void lift(Canvas k, int a0, int c0, boolean doorLowC) {
            int top = roofY();
            int doorC = doorLowC ? c0 : c0 + 3, ladderC = doorLowC ? c0 + 1 : c0 + 2, coreC = doorLowC ? c0 + 2 : c0 + 1,
                    backC = doorLowC ? c0 + 3 : c0;
            for (int a = a0; a <= a0 + 3; a++)
                for (int c = c0; c <= c0 + 3; c++) {
                    boolean ring = a == a0 || a == a0 + 3 || c == c0 || c == c0 + 3;
                    Material m = !ring ? (c == coreC ? Material.IRON_BLOCK : Material.AIR)
                            : c == backC ? Material.QUARTZ_BLOCK : Material.GLASS_PANE;
                    k.fill(a, a, street + 1, top, c, c, m);
                }
            BlockFace ladderFace = doorLowC ? minusC() : plusC(); // facing out of the core, toward the door
            for (int a = a0 + 1; a <= a0 + 2; a++)
                if (k.in(a, ladderC))
                    k.chunk.setLadder(k.lx(a, ladderC), street + 1, top, k.lz(a, ladderC), ladderFace);
            for (int f = 0; f < floors; f++) {
                int y = floorY(f);
                k.fill(a0 + 1, a0 + 2, y + 1, y + 3, doorC, doorC, Material.AIR);
                if (f > 0) {
                    k.fill(a0 + 1, a0 + 2, y, y, doorC, doorC, Material.IRON_BLOCK); // threshold
                    int railC = doorLowC ? c0 - 1 : c0 + 4;
                    k.fill(a0 + 1, a0 + 2, y + 1, y + 1, railC, railC, Material.AIR); // gap in the balcony rail
                }
            }
            k.fill(a0, a0 + 3, top, top, c0, c0 + 3, Material.IRON_BLOCK);
            k.set(a0 + 1, coreC, top, Material.SEA_LANTERN);
        }

        /**
         * Escalators: a two-wide flight in the atrium beside each long balcony, climbing one floor along the
         * mall. The first rises from the ground to the first floor on the high side; the next, if there is a
         * third floor, from the first to the second on the low side, standing free in the atrium air.
         */
        private void escalators(Canvas k) {
            if (!hasEscalators)
                return;
            flight(k, aA0 + 5, cA1 - 1, 0, cA1 + 1);
            if (floors >= 3)
                flight(k, aA1 - 5 - H, cA0, 1, cA0 - 1);
        }

        /**
         * One escalator: a two-wide flight in the atrium climbing one floor along {@code +a}, beside the balcony at
         * {@code railC}. A two-cell landing at the top, level with the balcony (and, for a flight that stands free
         * of the ground, one at the bottom too), with the balcony rail opened along each landing. The handrail on
         * the open side is two bars high at every step, so each step's bars meet the next step's and the rail
         * reads as one line, and it carries on round the landings.
         */
        private void flight(Canvas k, int a0, int c0, int f, int railC) {
            int y = floorY(f), top = floorY(f + 1);
            int handC = c0 + (railC > c0 ? -1 : 2); // the open (atrium) side
            for (int i = 0; i < H; i++) {
                for (int c = c0; c <= c0 + 1; c++) {
                    k.stair(a0 + i, c, y + 1 + i, Material.POLISHED_ANDESITE_STAIRS, plusA()); // facing the climb
                    if (i < H - 1)
                        k.fill(a0 + i, a0 + i, y + 2 + i, y + 4 + i, c, c, Material.AIR);
                }
                k.fill(a0 + i, a0 + i, y + 2 + i, y + 3 + i, handC, handC, Material.IRON_BARS);
            }
            // the top landing, level with the balcony floor, railed on its open side and at its end
            k.fill(a0 + H, a0 + H + 1, top, top, c0, c0 + 1, tile1);
            k.fill(a0 + H, a0 + H + 1, top + 1, top + 3, c0, c0 + 1, Material.AIR);
            k.fill(a0 + H, a0 + H + 1, top + 1, top + 1, handC, handC, Material.IRON_BARS);
            k.fill(a0 + H + 2, a0 + H + 2, top + 1, top + 1, Math.min(c0, handC), Math.max(c0 + 1, handC), Material.IRON_BARS);
            k.fill(a0 + H - 1, a0 + H + 1, top + 1, top + 1, railC, railC, Material.AIR); // step off onto the balcony
            if (f > 0) {
                // the bottom landing, level with this floor's balcony
                k.fill(a0 - 2, a0 - 1, y, y, c0, c0 + 1, tile1);
                k.fill(a0 - 2, a0 - 1, y + 1, y + 3, c0, c0 + 1, Material.AIR);
                k.fill(a0 - 2, a0 - 1, y + 1, y + 2, handC, handC, Material.IRON_BARS);
                k.fill(a0 - 3, a0 - 3, y + 1, y + 1, Math.min(c0, handC), Math.max(c0 + 1, handC), Material.IRON_BARS);
                k.fill(a0 - 2, a0, y + 1, y + 1, railC, railC, Material.AIR);
            }
        }

        /** A shop unit: its dividing walls, glass front with a door and fascia sign, and its fittings. */
        private void unit(CityWorldGenerator generator, Canvas k, Unit u) {
            int y = floorY(u.floor), stand = y + 1;
            int front = u.side == 0 ? cF1 : cF2, back = u.side == 0 ? eC0 + 1 : C - 2 - eC1;
            int dir = u.side == 0 ? -1 : 1; // from the front into the shop
            BlockFace outward = u.side == 0 ? plusC() : minusC();
            int depth = Math.abs(back - front);
            // floor
            for (int a = u.a0; a <= u.a1; a++)
                for (int v = 1; v <= depth; v++)
                    k.set(a, front + dir * v, y, u.kind.floor);
            // walls either side (shared with the neighbours) and the front
            for (int a : new int[] { u.a0 - 1, u.a1 + 1 })
                for (int v = 0; v <= depth; v++)
                    k.fill(a, a, stand, y + H - 1, front + dir * v, front + dir * v, trim);
            for (int a = u.a0; a <= u.a1; a++) {
                k.fill(a, a, stand, stand + 2, front, front, Material.GLASS_PANE);
                k.fill(a, a, stand + 3, y + H - 1, front, front, u.kind.fascia);
            }
            int door = (u.a0 + u.a1) / 2;
            k.fill(door, door + (u.a1 - u.a0 >= 4 ? 1 : 0), stand, stand + 2, front, front, Material.AIR);
            // the name on the fascia, facing the walkway; a second board for the kind on wider fronts
            int signC = front - dir;
            String[] lines = splitName(u.name);
            k.sign(door, signC, stand + 3, outward, lines);
            if (u.a1 - u.a0 >= 6) {
                k.sign(u.a0 + 1, signC, stand + 3, outward, lines);
                k.sign(u.a1 - 1, signC, stand + 3, outward, lines);
            }
            fit(generator, k, u, stand, front, dir, depth);
        }

        /**
         * The fittings of a unit by kind: wall shelving down both sides, a counter across the back with the
         * till and a stock chest behind it, and display islands on the shop floor. Each kind supplies its own
         * shelf, display and counter blocks; containers are placed bare and take the kind's table in the
         * end-of-lot container pass.
         */
        private void fit(CityWorldGenerator generator, Canvas k, Unit u, int stand, int front, int dir, int depth) {
            Odds odds = new Odds(u.seed);
            int w = u.a1 - u.a0 + 1;
            BlockFace toFront = u.side == 0 ? plusC() : minusC();
            Fittings fx = fittings(u.kind);
            // side shelving
            for (int v = 2; v <= depth - 3; v++)
                for (int a : new int[] { u.a0, u.a1 }) {
                    int c = front + dir * v;
                    k.set(a, c, stand, fx.shelf[(v + a) % fx.shelf.length]);
                    if (fx.shelfTop != null && (v % 2 == 0))
                        k.set(a, c, stand + 1, fx.shelfTop[(v + a) % fx.shelfTop.length]);
                }
            // the counter across the back, the till on it, stock behind
            int counterV = depth - 2;
            for (int a = u.a0 + 1; a <= u.a1 - 1; a++) {
                if (a == u.a0 + 1 && w > 3)
                    continue; // the way behind
                k.furniture(a, front + dir * counterV, stand, FurnitureTags.pick(FurnitureTags.COUNTER, odds), fx.counter, toFront);
            }
            k.set(u.a0 + w / 2, front + dir * counterV, stand + 1, fx.till);
            k.set(u.a1 - 1, front + dir * depth, stand, Material.CHEST);
            k.set(u.a0 + 1, front + dir * depth, stand, Material.BARREL);
            if (w > 5)
                k.set(u.a0 + 2, front + dir * depth, stand, Material.BARREL);
            // display islands
            for (int v = 3; v <= depth - 5; v += 3)
                for (int a = u.a0 + 2; a <= u.a1 - 2; a += 3) {
                    int c = front + dir * v;
                    if (fx.special != null) {
                        fx.special.draw(this, k, a, c, stand, odds);
                        continue;
                    }
                    k.set(a, c, stand, fx.display);
                    if (fx.displayTop != null)
                        k.set(a, c, stand + 1, fx.displayTop[odds.getRandomInt(fx.displayTop.length)]);
                }
            // a shopkeeper, in the chunk that holds the till
            int ta = u.a0 + w / 2, tc = front + dir * (counterV + 1);
            if (k.in(ta, tc) && u.kind != Kind.PETS)
                generator.spawnProvider.spawnBeing(generator, k.chunk, odds, k.lx(ta, tc), stand, k.lz(ta, tc));
        }

        /** An anchor store: the end of the mall, all floors, open wide onto the atrium walkway, doors outside. */
        private void anchor(CityWorldGenerator generator, Canvas k, int e) {
            Kind kind = anchors[e];
            int front = e == 0 ? aFront1 : aFront2, outer = e == 0 ? eA0 : L - 1 - eA1;
            int in = e == 0 ? 1 : -1; // from the outer wall into the store
            BlockFace toWalk = e == 0 ? plusA() : minusA();
            int aMin = Math.min(front, outer) + 1, aMax = Math.max(front, outer) - 1;
            int stairC = C - 3 - eC1; // the stairs between floors, against the high side wall
            String[] lines = splitName(anchorNames[e]);
            Fittings fx = fittings(kind);
            for (int f = 0; f < floors; f++) {
                int y = floorY(f), stand = y + 1;
                Odds odds = new Odds(seed ^ (e * 31L + f * 977L));
                // the front onto the walkway: fascia over a wide opening, the name along it
                for (int c = eC0 + 1; c <= C - 2 - eC1; c++) {
                    boolean open = c > cF1 + 1 && c < cF2 - 1;
                    k.fill(front, front, stand, stand + 2, c, c, open ? Material.AIR : Material.GLASS_PANE);
                    k.fill(front, front, stand + 3, y + H - 1, c, c, kind.fascia);
                }
                for (int c = cF1 + 3; c <= cF2 - 3; c += 5)
                    k.sign(front + in, c, stand + 3, toWalk, lines); // on the walkway side of the fascia
                // floor and lights
                for (int a = aMin; a <= aMax; a++)
                    for (int c = eC0 + 1; c <= C - 2 - eC1; c++) {
                        k.set(a, c, y, kind == Kind.DEPARTMENT ? ((a + c) % 2 == 0 ? Material.POLISHED_DIORITE : Material.WHITE_CONCRETE)
                                : kind.floor);
                    }
                // display islands, clear of the stairs
                for (int a = aMin + 2; a <= aMax - 2; a += 3)
                    for (int c = eC0 + 3; c <= stairC - 3; c += 3) {
                        if (fx.special != null) {
                            fx.special.draw(this, k, a, c, stand, odds);
                            continue;
                        }
                        k.set(a, c, stand, kind == Kind.GROCER ? Material.BARREL : fx.display);
                        if (fx.displayTop != null)
                            k.set(a, c, stand + 1, fx.displayTop[odds.getRandomInt(fx.displayTop.length)]);
                    }
                // stock against the outer wall
                for (int c = eC0 + 2; c <= stairC - 3; c += 4)
                    k.set(outer + in, c, stand, (c / 4) % 2 == 0 ? Material.CHEST : Material.BARREL);
                int ma = (aMin + aMax) / 2, mc = (cA0 + cA1) / 2;
                if (k.in(ma, mc))
                    generator.spawnProvider.spawnBeing(generator, k.chunk, odds, k.lx(ma, mc), stand, k.lz(ma, mc));
            }
            // stairs between the store's floors, drawn after every floor so no floor closes them again
            for (int f = 0; f < floors - 1; f++) {
                int stand = floorY(f) + 1;
                for (int i = 0; i < H; i++) {
                    int a = outer + in * (2 + i);
                    for (int c = stairC - 1; c <= stairC; c++) {
                        k.stair(a, c, stand + i, Material.POLISHED_ANDESITE_STAIRS, e == 0 ? plusA() : minusA());
                        k.fill(a, a, stand + i + 1, stand + i + 3, c, c, Material.AIR);
                    }
                }
            }
            // the store's own entrance from outside, and its name over it
            int stand = street + 1, cm = C / 2;
            k.fill(outer, outer, stand, stand + 2, cm - 2, cm + 1, Material.AIR);
            k.fill(outer, outer, stand + 3, stand + 4, cm - 3, cm + 2, kind.fascia);
            BlockFace out = e == 0 ? minusA() : plusA();
            k.sign(outer - in, cm - 1, stand + 3, out, lines);
            k.sign(outer - in, cm, stand + 3, out, lines);
        }

        /** A food kiosk: a counter ring under a striped wool awning on posts, a smoker and a barrel inside. */
        private void kiosk(Canvas k, Kiosk q) {
            int stand = street + 1;
            for (int a = q.a - 1; a <= q.a + 1; a++)
                for (int c = q.c - 2; c <= q.c + 1; c++) {
                    boolean ring = a == q.a - 1 || a == q.a + 1 || c == q.c - 2 || c == q.c + 1;
                    if (ring)
                        k.set(a, c, stand, Material.SMOOTH_QUARTZ);
                    k.set(a, c, stand + 3, (a + c) % 2 == 0 ? q.awning : Material.WHITE_WOOL);
                }
            for (int[] p : new int[][] { { q.a - 1, q.c - 2 }, { q.a + 1, q.c - 2 }, { q.a - 1, q.c + 1 }, { q.a + 1, q.c + 1 } })
                k.fill(p[0], p[0], stand + 1, stand + 2, p[1], p[1], Material.SPRUCE_FENCE);
            k.set(q.a, q.c - 1, stand, Material.SMOKER);
            k.set(q.a, q.c, stand, Material.BARREL);
            k.sign(q.a - 2, q.c, stand + 3, minusA(), splitName(q.name));
            k.sign(q.a + 2, q.c - 1, stand + 3, plusA(), splitName(q.name));
        }

        /** Outside: planters on the side aprons, and the mall's name high on every closed end wall. */
        private void exterior(Canvas k) {
            int stand = street + 1;
            for (int a = 3; a < L - 3; a += 8)
                for (int side = 0; side < 2; side++) {
                    if ((side == 0 ? eC0 : eC1) == 0 || corridorSide[side] && a >= corridorA0 - 2 && a <= corridorA1 + 2)
                        continue;
                    int c = side == 0 ? 0 : C - 1;
                    k.set(a, c, stand, Material.MOSS_BLOCK);
                    k.set(a, c, stand + 1, Material.AZALEA);
                }
            int top = roofY();
            for (int e = 0; e < 2; e++) {
                if (anchors[e] == null)
                    continue;
                int a = e == 0 ? eA0 - 1 : L - eA1;
                BlockFace out = e == 0 ? minusA() : plusA();
                for (int c = C / 2 - 2; c <= C / 2 + 1; c++)
                    k.set(a + (e == 0 ? 1 : -1), c, top - 2, band);
                k.sign(a, C / 2 - 1, top - 2, out, "", name);
                k.sign(a, C / 2, top - 2, out, "", "Shopping");
            }
        }
    }

    interface Special {
        void draw(Wing w, Wing.Canvas k, int a, int c, int stand, Odds odds);
    }

    record Fittings(Material[] shelf, Material[] shelfTop, Material display, Material[] displayTop, Material counter,
            Material till, Special special) {
    }

    private static final Material NOTE_BLOCK = Material.of(Blocks.NOTE_BLOCK), JUKEBOX = Material.of(Blocks.JUKEBOX),
            TARGET = Material.of(Blocks.TARGET), OBSERVER = Material.of(Blocks.OBSERVER),
            DAYLIGHT = Material.of(Blocks.DAYLIGHT_DETECTOR), HONEY = Material.of(Blocks.HONEY_BLOCK),
            STRIPPED_OAK = Material.of(Blocks.STRIPPED_OAK_LOG), POT_TULIP = Material.of(Blocks.POTTED_RED_TULIP),
            POT_POPPY = Material.of(Blocks.POTTED_POPPY), POT_AZALEA = Material.of(Blocks.POTTED_FLOWERING_AZALEA),
            POT_CACTUS = Material.of(Blocks.POTTED_CACTUS), POT_CORNFLOWER = Material.of(Blocks.POTTED_CORNFLOWER),
            CORNFLOWER = Material.of(Blocks.CORNFLOWER), PEARL_LIGHT = Material.of(Blocks.PEARLESCENT_FROGLIGHT);

    private static Fittings fittings(Kind kind) {
        return switch (kind) {
        case MUSIC -> new Fittings(new Material[] { NOTE_BLOCK, Material.BLACK_CONCRETE }, new Material[] { NOTE_BLOCK },
                JUKEBOX, null, Material.BLACK_CONCRETE, JUKEBOX, null);
        case FLORIST -> new Fittings(new Material[] { Material.MOSS_BLOCK }, new Material[] { POT_TULIP, POT_POPPY, POT_AZALEA, POT_CORNFLOWER },
                Material.MOSS_BLOCK, new Material[] { Material.POPPY, Material.DANDELION, CORNFLOWER, Material.ALLIUM, Material.AZALEA },
                Material.SPRUCE_PLANKS, POT_AZALEA, null);
        case FASHION -> new Fittings(new Material[] { Material.WHITE_WOOL, Material.PINK_WOOL, Material.LIGHT_BLUE_WOOL, Material.BLACK_WOOL },
                null, Material.WHITE_CONCRETE, new Material[] { Material.PINK_CARPET, Material.WHITE_CARPET, Material.LIGHT_BLUE_CARPET },
                Material.WHITE_CONCRETE, Material.LECTERN, Mall::mannequin);
        case HARDWARE -> new Fittings(new Material[] { Material.BARREL, Material.IRON_BLOCK }, new Material[] { Material.LANTERN },
                Material.SMOOTH_STONE, new Material[] { Material.ANVIL, Material.GRINDSTONE, Material.CAULDRON, Material.IRON_CHAIN },
                Material.SPRUCE_PLANKS, Material.SMITHING_TABLE, null);
        case GROCER -> new Fittings(new Material[] { Material.BARREL, Material.COMPOSTER }, null, Material.HAY_BLOCK,
                new Material[] { Material.MELON, Material.PUMPKIN }, Material.WHITE_CONCRETE, Material.BARREL, null);
        case BAKERY -> new Fittings(new Material[] { Material.SMOKER, Material.FURNACE, Material.BARREL }, null, Material.SMOOTH_QUARTZ,
                new Material[] { Material.CAKE }, Material.SMOOTH_QUARTZ, Material.CAKE, null);
        case CAFE -> new Fittings(new Material[] { Material.BARREL, Material.SPRUCE_PLANKS }, new Material[] { Material.LANTERN },
                Material.SPRUCE_PLANKS, null, Material.SPRUCE_PLANKS, Material.BREWING_STAND, Mall::cafeTable);
        case BOOKS -> new Fittings(new Material[] { Material.BOOKSHELF, Material.CHISELED_BOOKSHELF }, null, Material.BOOKSHELF,
                new Material[] { Material.LECTERN }, Material.OAK_PLANKS, Material.LECTERN, null);
        case TOYS -> new Fittings(new Material[] { Material.RED_WOOL, Material.YELLOW_WOOL, Material.LIME_WOOL, Material.LIGHT_BLUE_WOOL },
                null, Material.WHITE_CONCRETE, new Material[] { TARGET, Material.SLIME_BLOCK, HONEY, Material.of(Blocks.BELL) },
                Material.MAGENTA_CONCRETE, Material.of(Blocks.JACK_O_LANTERN), null);
        case PETS -> new Fittings(new Material[] { Material.BARREL, Material.HAY_BLOCK }, null, Material.GLASS, null,
                Material.LIGHT_BLUE_CONCRETE, Material.BARREL, Mall::fishTank);
        case JEWELLER -> new Fittings(new Material[] { Material.SMOOTH_QUARTZ }, new Material[] { Material.GOLD_BLOCK, Material.EMERALD_BLOCK },
                Material.SMOOTH_QUARTZ, new Material[] { Material.AMETHYST_CLUSTER, Material.AMETHYST_CLUSTER, Material.GOLD_BLOCK,
                        Material.GOLD_BLOCK, Material.EMERALD_BLOCK, Material.EMERALD_BLOCK, Material.CANDLE, Material.CANDLE,
                        Material.AMETHYST_CLUSTER, Material.GOLD_BLOCK, Material.CANDLE, Material.DIAMOND_BLOCK }, // the rare one
                Material.SMOOTH_QUARTZ, Material.GOLD_BLOCK, null);
        case ELECTRONICS -> new Fittings(new Material[] { Material.REDSTONE_LAMP, OBSERVER }, new Material[] { DAYLIGHT },
                Material.LIGHT_GRAY_CONCRETE, new Material[] { Material.REDSTONE_LAMP, OBSERVER, DAYLIGHT, TARGET },
                Material.LIGHT_GRAY_CONCRETE, Material.REDSTONE_LAMP, null);
        case SPORTS -> new Fittings(new Material[] { Material.BARREL, TARGET }, null, Material.HAY_BLOCK,
                new Material[] { TARGET, Material.FLETCHING_TABLE }, Material.SPRUCE_PLANKS, Material.FLETCHING_TABLE, null);
        case PHARMACY -> new Fittings(new Material[] { Material.WHITE_CONCRETE, Material.BARREL }, new Material[] { Material.BREWING_STAND },
                Material.WHITE_CONCRETE, new Material[] { Material.BREWING_STAND, Material.CAULDRON }, Material.WHITE_CONCRETE,
                Material.BREWING_STAND, null);
        case FURNITURE -> new Fittings(new Material[] { Material.OAK_PLANKS, Material.BOOKSHELF }, null, Material.OAK_PLANKS, null,
                Material.OAK_PLANKS, Material.LECTERN, Mall::showroom);
        case GIFTS -> new Fittings(new Material[] { Material.SPRUCE_PLANKS }, new Material[] { Material.CANDLE, Material.of(Blocks.DECORATED_POT) },
                Material.SPRUCE_PLANKS, new Material[] { Material.CANDLE, POT_CACTUS, Material.of(Blocks.DECORATED_POT) },
                Material.SPRUCE_PLANKS, Material.CANDLE, null);
        case ART -> new Fittings(new Material[] { Material.WHITE_CONCRETE }, null, Material.WHITE_CONCRETE,
                new Material[] { Material.ORANGE_GLAZED_TERRACOTTA, Material.MAGENTA_GLAZED_TERRACOTTA,
                        Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.YELLOW_GLAZED_TERRACOTTA },
                Material.WHITE_CONCRETE, Material.LOOM, null);
        case DEPARTMENT, KIOSK -> new Fittings(new Material[] { Material.WHITE_WOOL, Material.BOOKSHELF, Material.BARREL }, null,
                Material.POLISHED_DIORITE, new Material[] { Material.WHITE_CARPET, Material.LANTERN, Material.REDSTONE_LAMP, PEARL_LIGHT },
                Material.SMOOTH_QUARTZ, Material.LECTERN, null);
        };
    }

    /** Fashion: a clothes rail — fence posts with a carpet "garment" row, or a wool stack. */
    private static void mannequin(Wing m, Wing.Canvas k, int a, int c, int stand, Odds odds) {
        k.set(a, c, stand, Material.SPRUCE_FENCE);
        k.set(a, c, stand + 1, odds.flipCoin() ? Material.PINK_WOOL : Material.LIGHT_BLUE_WOOL);
        k.set(a + 1, c, stand, Material.SPRUCE_FENCE);
        k.set(a + 1, c, stand + 1, Material.WHITE_WOOL);
    }

    /** Cafe: a table with two chairs. */
    private static void cafeTable(Wing m, Wing.Canvas k, int a, int c, int stand, Odds odds) {
        k.furniture(a, c, stand, FurnitureTags.pick(FurnitureTags.TABLE, odds), Material.SPRUCE_FENCE, m.plusA());
        if (k.in(a, c) && k.chunk.isEmpty(k.lx(a, c), stand + 1, k.lz(a, c)) && !k.empty(a, c, stand))
            k.set(a, c, stand + 1, Material.SPRUCE_PRESSURE_PLATE);
        k.furniture(a - 1, c, stand, FurnitureTags.pick(FurnitureTags.CHAIR, odds), Material.SPRUCE_STAIRS, m.plusA());
        k.furniture(a + 1, c, stand, FurnitureTags.pick(FurnitureTags.CHAIR, odds), Material.SPRUCE_STAIRS, m.minusA());
    }

    /** Pets: a two-block fish tank. */
    private static void fishTank(Wing m, Wing.Canvas k, int a, int c, int stand, Odds odds) {
        // a sealed tank: glass all round the water and over it, on a stand. A tank that would straddle two chunks
        // is a pet-food display instead: its water could run before the other half's glass exists.
        if (!k.wholly(a - 1, a + 2, c - 1, c + 1)) {
            if (k.in(a, c)) {
                k.set(a, c, stand, Material.BARREL);
                k.set(a + 1, c, stand, Material.HAY_BLOCK);
            }
            return;
        }
        k.fill(a - 1, a + 2, stand, stand, c - 1, c + 1, Material.LIGHT_BLUE_CONCRETE);
        k.fill(a - 1, a + 2, stand + 1, stand + 2, c - 1, c + 1, Material.GLASS);
        k.fill(a, a + 1, stand + 1, stand + 2, c, c, Material.WATER);
        k.fill(a - 1, a + 2, stand + 3, stand + 3, c - 1, c + 1, Material.GLASS);
        // and fish in it: one or two, kept for good
        if (m.generator != null && k.in(a, c)) {
            int n = 1 + odds.getRandomInt(2);
            for (int i = 0; i < n; i++)
                m.generator.spawnProvider.spawnAquariumFish(m.generator, k.chunk, odds, k.lx(a + i, c), stand + 1, k.lz(a + i, c));
        }
    }

    /** Furniture: a showroom set from the furniture pools, vanilla beds and stairs where there are none. */
    private static void showroom(Wing m, Wing.Canvas k, int a, int c, int stand, Odds odds) {
        switch (odds.getRandomInt(3)) {
        case 0 -> {
            k.furniture(a, c, stand, FurnitureTags.pick(FurnitureTags.SOFA, odds), Material.OAK_STAIRS, m.plusC());
            k.furniture(a + 1, c, stand, FurnitureTags.pick(FurnitureTags.SOFA, odds), Material.OAK_STAIRS, m.plusC());
        }
        case 1 -> {
            k.furniture(a, c, stand, FurnitureTags.pick(FurnitureTags.TABLE, odds), Material.OAK_FENCE, m.plusA());
            k.furniture(a + 1, c, stand, FurnitureTags.pick(FurnitureTags.CHAIR, odds), Material.OAK_STAIRS, m.minusA());
        }
        default -> {
            k.furniture(a, c, stand, FurnitureTags.pick(FurnitureTags.FLOOR_LAMP, odds), Material.LANTERN, m.plusA());
            k.furniture(a + 1, c, stand, FurnitureTags.pick(FurnitureTags.CABINET, odds), Material.BARREL, m.plusA());
        }
        }
    }

}
