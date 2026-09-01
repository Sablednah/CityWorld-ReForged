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

    private static TagKey<Block> key(String role) {
        return TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cityworld", "furniture/" + role));
    }

    /**
     * A random piece for this role, or {@code null} if no mod supplies one.
     *
     * <p>{@code null} is the ordinary case — most worlds have no furniture mod — so every caller falls
     * back to the vanilla-block furniture CityWorld has always built. Nothing here is required.
     */
    public static Material pick(TagKey<Block> role, Odds odds) {
        List<Material> pool = MaterialTags.resolve(role);
        return pool.isEmpty() ? null : pool.get(odds.getRandomInt(pool.size()));
    }

    /** Whether any mod supplies this role. */
    public static boolean has(TagKey<Block> role) {
        return !MaterialTags.resolve(role).isEmpty();
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
