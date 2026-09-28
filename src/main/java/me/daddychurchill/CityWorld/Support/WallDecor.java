package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * Things hung on a wall from a cell in front of it: an item frame showing a chosen item, a 1x1 painting, or a
 * shelf (where the version or a mod has a shelf block) filled from a loot table so its items show. Each needs
 * air at the cell and a real wall behind it ({@link SupportBlocks#isWallBacking}), both inside this chunk, and
 * answers false otherwise so a caller can move on. Entities are constructed directly and added through the
 * region — never {@code Painting.create}/{@code survives()}, which query the real level from the worldgen
 * thread and hang on unloaded chunks (see {@link Furniture}).
 *
 * @see Armoury the vault's weapon frames, the same idiom with an item pool
 */
public final class WallDecor {

    private WallDecor() {
    }

    /** The in-chunk wall cell behind (x, z) toward {@code wallSide}, if there is air here and a wall there. */
    private static boolean backed(RealBlocks chunk, int x, int y, int z, BlockFace wallSide) {
        if (x < 0 || x > 15 || z < 0 || z > 15 || !chunk.isEmpty(x, y, z))
            return false;
        int bx = x + wallSide.getModX(), bz = z + wallSide.getModZ();
        return bx >= 0 && bx <= 15 && bz >= 0 && bz <= 15 && chunk.isWallBacking(bx, y, bz, wallSide.getOppositeFace());
    }

    /** An item frame showing {@code item}, on the wall toward {@code wallSide}. */
    public static boolean frame(RealBlocks chunk, int x, int y, int z, BlockFace wallSide, Item item) {
        if (item == null || !backed(chunk, x, y, z, wallSide))
            return false;
        ServerLevelAccessor server = chunk.getServerLevel();
        Direction out = wallSide.getOppositeFace().toDirection();
        if (server == null || out == null)
            return false;
        ItemFrame frame = new ItemFrame(server.getLevel(), new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z), out);
        frame.setSilent(true); // setItem would otherwise play a sound on the real level mid-worldgen
        frame.setItem(new ItemStack(item), false);
        server.addFreshEntityWithPassengers(frame);
        return true;
    }

    /** A 1x1 painting (the only size whose space this checks), on the wall toward {@code wallSide}. */
    public static boolean painting(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide) {
        if (!backed(chunk, x, y, z, wallSide))
            return false;
        ServerLevelAccessor server = chunk.getServerLevel();
        Direction out = wallSide.getOppositeFace().toDirection();
        if (server == null || out == null)
            return false;
        var variants = new java.util.ArrayList<net.minecraft.core.Holder<net.minecraft.world.entity.decoration.PaintingVariant>>();
        server.getLevel().registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.PAINTING_VARIANT)
                .getTagOrEmpty(net.minecraft.tags.PaintingVariantTags.PLACEABLE)
                .forEach(holder -> {
                    if (holder.value().getWidth() == 16 && holder.value().getHeight() == 16)
                        variants.add(holder);
                });
        if (variants.isEmpty())
            return false;
        var painting = new net.minecraft.world.entity.decoration.Painting(server.getLevel(),
                new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z), out,
                variants.get(odds.getRandomInt(variants.size())));
        server.addFreshEntityWithPassengers(painting);
        return true;
    }

    /**
     * A shelf from {@code #cityworld:furniture/shelf} against the wall toward {@code wallSide}, filled now from
     * loot table {@code table} so it shows its goods. False where no shelf block exists (older versions, no mod).
     */
    public static boolean shelf(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide, String table) {
        return shelf(chunk, odds, x, y, z, wallSide, table, FurnitureTags.pick(FurnitureTags.SHELF, odds));
    }

    /** As above with the shelf block chosen by the caller — one per shop, so a shop's shelves match. */
    public static boolean shelf(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide, String table,
            Material shelf) {
        if (!backed(chunk, x, y, z, wallSide))
            return false;
        if (shelf == null)
            return false;
        if (!chunk.setFurniture(x, y, z, shelf, FurnitureTags.facingFor(shelf, wallSide.getOppositeFace())))
            return false;
        ContainerLoot.assignTableAt(chunk, x, y, z, table, odds);
        return true;
    }
}
