package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.Block;

/**
 * What goes in the chests.
 *
 * <p>Upstream offered three implementations — a PhatLoots integration (already commented out by
 * 1.14), {@code LootProvider_Normal}, which filled inventories imperatively with random picks from
 * {@code MaterialProvider}'s per-location lists, and {@code LootProvider_LootTable}, which handed the
 * job to Minecraft's own loot tables. The last was upstream's default
 * ({@code useMinecraftLootTables = true}) and is the only one ported: it is the one that already
 * behaves like modern Minecraft, and the imperative path would need a dozen
 * {@code itemsRandomMaterials_*Chests} lists rebuilt to say something vanilla now says better.
 *
 * <p><b>The tables ship with the mod.</b> Upstream extracted its bundled datapack into
 * {@code <world>/datapacks/cityworld/} at world-load and called {@code Bukkit.reloadData()}; a
 * NeoForge mod jar <em>is</em> a datapack, so the 13 tables simply sit in
 * {@code data/cityworld/loot_tables/chests/} and are found. That retires two pieces of upstream's
 * signature:
 *
 * <ul>
 * <li>{@code saveLoots()} — a vestige of the PhatLoots API that both upstream implementations
 * already left empty.
 * <li>{@code worldPrefix} — passed at every call site and read by neither implementation. Its intent
 * was per-world loot customisation, keying each table {@code <worldname>_<location>}, which is what
 * the extraction step was really for. The port has no per-world anything (the dimension is always
 * {@code city}), so the prefix could only ever spell one name. It returns with the settings layer at
 * P7, if it returns at all.
 * </ul>
 */
public abstract class LootProvider extends Provider {

    /**
     * Where a chest is, which is to say what should be in it.
     *
     * <p>The order is load-bearing: {@link LootLocation#EMPTY} and {@link LootLocation#RANDOM} are
     * the two non-places, and everything from index 2 on is a real one whose name matches a table in
     * {@code data/cityworld/loot_tables/chests/}. Upstream leant on the same split with
     * {@code Arrays.copyOfRange(values(), 2, …)}.
     */
    public enum LootLocation {
        EMPTY, RANDOM, SEWER, MINE, BUNKER, BUILDING, WAREHOUSE, FOOD, STORAGE_SHED, FARMWORKS, FARMWORKS_OUTPUT,
        WOODWORKS, WOODWORKS_OUTPUT, STONEWORKS, STONEWORKS_OUTPUT,
        // Containers that used to be placed bare. Each name maps to cityworld:chests/<lowercase>, and
        // each of those tables ends with a reference to an EMPTY cityworld:chests/<name>_extra that we
        // also ship — a mod or pack replaces just that file to drop its own items in without touching
        // (or clashing with) CityWorld's own contents. Ammo in nightstands, guns in the armoury.
        HOSPITAL, NIGHTSTAND, VAULT_QUARTERS, VAULT_OFFICE, VAULT_ARMOURY, SHOP, POND,
        // The armoury's ammunition shelves and crates: arrows and powder, and the hook a gun mod fills.
        VAULT_AMMO
    }

    public abstract void setLoot(CityWorldGenerator generator, Odds odds, LootLocation lootLocation, Block block);

    /**
     * As {@link #setLoot(CityWorldGenerator, Odds, LootLocation, Block)}, for a container on a deeper
     * {@code tier} of a place that worsens (and richens) with depth: the vault's floors. Tier 0 is the
     * plain table; tier {@code k} names {@code cityworld:chests/<name>_floor<k>}, which the shipped data
     * composes as the room's own table plus {@code chests/vault_floor<k>} (the depth bonus and the
     * per-floor {@code _extra} hook a pack fills). The default ignores the tier.
     */
    public void setLoot(CityWorldGenerator generator, Odds odds, LootLocation lootLocation, Block block, int tier) {
        setLoot(generator, odds, lootLocation, block);
    }

    public static LootProvider loadProvider(CityWorldGenerator generator) {
        return new LootProvider_LootTable();
    }
}
