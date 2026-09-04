package me.daddychurchill.CityWorld.Support;

import java.util.List;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Furniture by <b>role</b> — "something to sit on", "something to eat at" — rather than by block name.
 *
 * <p>CityWorld furnishes rooms out of these tags, so a furniture mod joins by being tagged and needs no
 * code at all. The two large furniture mods ship ~1,200 blocks between them on a regular
 * {@code <material>_<kind>} naming scheme; {@code scripts/gen_furniture_tags.py} derives the tags from
 * an installed mod rather than anyone hand-writing them, and every entry is {@code "required": false}
 * so the tags cost nothing when the mod is absent.
 *
 * <p><b>Orientation cannot be inferred, and that is the whole reason the data map exists.</b> Measured
 * from model geometry: Macaw's chair uses {@code facing} as the direction its occupant looks, Macaw's
 * <em>sofa</em> is 90° off that, and Refurbished uses {@code facing} for the direction the backrest
 * points. Three conventions across two mods — and one mod disagreeing with itself — so "seat or back?"
 * is not a rich enough question. {@code cityworld:furniture} carries a rotation offset per block, in
 * degrees, and {@link #facingFor} applies it.
 */
public final class FurnitureTags {

    private FurnitureTags() {}

    public static final TagKey<Block> CHAIR = key("chair");
    public static final TagKey<Block> TABLE = key("table");
    public static final TagKey<Block> SOFA = key("sofa");
    public static final TagKey<Block> DESK = key("desk");
    public static final TagKey<Block> COUNTER = key("counter");
    public static final TagKey<Block> CABINET = key("cabinet");
    public static final TagKey<Block> DRAWER = key("drawer");
    public static final TagKey<Block> WARDROBE = key("wardrobe");
    public static final TagKey<Block> BOOKSHELF = key("bookshelf");
    public static final TagKey<Block> SINK = key("sink");
    public static final TagKey<Block> TOILET = key("toilet");
    public static final TagKey<Block> BATH = key("bath");
    public static final TagKey<Block> LAMP = key("lamp");

    // the appliance round: Refurbished's household pieces. The fridge "double block" is a fridge
    // with a separate freezer block stacked on top.
    public static final TagKey<Block> CEILING_FAN = key("ceiling_fan");
    public static final TagKey<Block> TV = key("tv");
    public static final TagKey<Block> FRIDGE = key("fridge");
    public static final TagKey<Block> FREEZER = key("freezer");
    public static final TagKey<Block> STOVE = key("stove");
    public static final TagKey<Block> MICROWAVE = key("microwave");
    public static final TagKey<Block> TOASTER = key("toaster");
    public static final TagKey<Block> CUTTING_BOARD = key("cutting_board");
    public static final TagKey<Block> BIN = key("bin");
    public static final TagKey<Block> COMPUTER = key("computer");
    public static final TagKey<Block> CRATE = key("crate");
    public static final TagKey<Block> WORKBENCH = key("workbench");

    // the Fantasy's Furniture round: beds join a pool (vanilla seeds + modded singles and 2×2
    // doubles), freestanding floor lamps, and wall shelves that something can stand on
    public static final TagKey<Block> BED = key("bed");
    public static final TagKey<Block> FLOOR_LAMP = key("floor_lamp");
    public static final TagKey<Block> SHELF = key("shelf");

    /**
     * The three decoration pools — placement classes, not furniture roles. {@code FLOOR_DECOR}
     * stands on the ground, {@code SURFACE_DECOR} belongs on a tabletop (the placer puts a table
     * underneath), {@code WALL_DECOR} mounts on a wall. Modded table lamps are generated into
     * {@code surface}, which is the whole reason the split exists: Refurbished lamps are y 0–14
     * with no facing — table lamps — and read wrong standing on the floor.
     */
    public static final TagKey<Block> FLOOR_DECOR = decorKey("floor");
    /** Interior lighting hung below ceilings — ceilings are the next floor's floor, so light
     *  blocks can't be set INTO them; these hang in the air cell beneath. */
    public static final TagKey<Block> HANGING_LIGHT = decorKey("hanging_light");
    public static final TagKey<Block> SURFACE_DECOR = decorKey("surface");
    public static final TagKey<Block> WALL_DECOR = decorKey("wall");
    /** The carpets a rug is cut from — vanilla seeds plus every furniture set's own carpet. */
    public static final TagKey<Block> RUG_DECOR = decorKey("rug");

    private static TagKey<Block> key(String role) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cityworld", "furniture/" + role));
    }

    private static TagKey<Block> decorKey(String pool) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cityworld", "decor/" + pool));
    }

    /**
     * A random piece for this role, or {@code null} if no mod supplies one.
     *
     * <p>{@code null} is the ordinary case — most worlds have no furniture mod — so every caller falls
     * back to the vanilla-block furniture CityWorld has always built. Nothing here is required.
     */
    public static Material pick(TagKey<Block> role, Odds odds) {
        return pick(role, odds, 1, 1, 2);
    }

    /**
     * A random piece for this role that fits in {@code width} cells across, {@code depth} cells
     * back and {@code height} cells up — the room a caller actually has for it. The plain
     * {@link #pick(TagKey, Odds)} allows one cell across and back and two up, because every
     * existing placer checks exactly that (the cell and the one above it clear), and a Fantasy's
     * Furniture chair is two blocks tall; wider pieces (2×2 double beds, 2-wide desks and dressers,
     * 2×3 wardrobes) only come out when a caller says it has the space.
     */
    public static Material pick(TagKey<Block> role, Odds odds, int width, int depth, int height) {
        List<Material> pool = MaterialTags.resolve(role);
        if (pool.isEmpty())
            return null;
        List<Material> fitting = new java.util.ArrayList<>(pool.size());
        for (Material piece : pool)
            if (footprint(piece).fits(width, depth, height))
                fitting.add(piece);
        return fitting.isEmpty() ? null : fitting.get(odds.getRandomInt(fitting.size()));
    }

    /** Whether any mod supplies this role. */
    public static boolean has(TagKey<Block> role) {
        return !MaterialTags.resolve(role).isEmpty();
    }

    /** The cells a piece takes along its own axes (1×1×1 for anything undeclared). */
    public static me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.Footprint footprint(Material piece) {
        return me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.footprintFor(piece);
    }

    /**
     * Whether a run of this piece may be re-derived with {@link SupportBlocks#reconnect} after
     * placement. Declared per block, because it is only safe when the mod's own connection logic
     * reads the same {@code facing} we wrote: a Macaw's couch run reconnected into corner shapes
     * (its facing is offset 270 from ours), while Fantasy's sofas and shelves connect correctly.
     */
    public static boolean reconnects(Material piece) {
        return me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.furnitureFor(piece).reconnect();
    }

    /**
     * The {@code facing} value to place this piece with so its occupant looks {@code look}.
     *
     * <p>Applies the block's own declared offset. With no declaration the offset is zero — the piece is
     * placed facing the way we want the occupant to look, which is right for some mods and visibly
     * wrong for others. Visibly wrong is the intended failure: a chair facing a wall gets reported and
     * fixed with one data-map line, whereas a subtle error would ship forever.
     */
    public static BlockFace facingFor(Material piece, BlockFace look) {
        int offset = me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.facingOffsetFor(piece);
        BlockFace facing = look;
        for (int turned = 0; turned < (offset % 360 + 360) % 360; turned += 90)
            facing = clockwise(facing);
        return facing;
    }

    private static BlockFace clockwise(BlockFace face) {
        return switch (face) {
            case NORTH -> BlockFace.EAST;
            case EAST -> BlockFace.SOUTH;
            case SOUTH -> BlockFace.WEST;
            case WEST -> BlockFace.NORTH;
            default -> face;
        };
    }
}
