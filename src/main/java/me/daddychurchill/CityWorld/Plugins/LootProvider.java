package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.Block;

/**
 * Stub of the original {@code LootProvider}.
 *
 * <p><b>Phase 1 placeholder.</b> {@code SupportBlocks} needs this type only to name it in the
 * {@code setChest}/{@code setDoubleChest} signatures — it calls {@link #setLoot} and nothing else.
 * Keeping the shape and dropping the body is what lets the block seam compile ahead of the loot
 * layer.
 *
 * <p>Real loot is Phase 5, and it will not look like this: the original filled chest inventories
 * imperatively with Bukkit {@code ItemStack}s (and could hand off to Minecraft loot tables when
 * {@code useMinecraftLootTables} was set), whereas the port will migrate the bundled loot tables to
 * the 1.21 format and let vanilla populate the container. The {@code worldPrefix} argument exists
 * because the original scoped its loot tables per world name.
 */
public abstract class LootProvider extends Provider {

    public enum LootLocation {
        EMPTY, RANDOM, SEWER, MINE, BUNKER, BUILDING, WAREHOUSE, FOOD, STORAGE_SHED, FARMWORKS, FARMWORKS_OUTPUT,
        WOODWORKS, WOODWORKS_OUTPUT, STONEWORKS, STONEWORKS_OUTPUT
    }

    public abstract void setLoot(CityWorldGenerator generator, Odds odds, String worldPrefix,
            LootLocation chestLocation, Block block);

    public abstract void saveLoots();
}
