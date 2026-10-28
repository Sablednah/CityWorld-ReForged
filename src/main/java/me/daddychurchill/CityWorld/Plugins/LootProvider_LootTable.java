package me.daddychurchill.CityWorld.Plugins;

import java.util.Locale;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.loot.LootTable;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.Block;

/**
 * Tags a chest with one of our loot tables and lets vanilla fill it, on first open.
 *
 * <p>This is the whole of the loot layer. It writes no items: a container carrying a loot table
 * rolls it lazily, which is why the chest can be tagged during generation, where there is no
 * inventory to fill and no player to fill it for. Upstream's {@code chest.setLootTable(key)} maps
 * almost exactly, and this is vanilla's own worldgen idiom — {@code StructurePiece.createChest}
 * does the same three steps against a {@code ServerLevelAccessor}, which is precisely what
 * decoration hands us.
 *
 * <p>Note that unlike {@code SupportBlocks.setSignText}, this needs no {@code setLevel} guard:
 * {@code setLootTable} is a plain field write on the block entity and never notifies its level, so
 * the levelless block entity that a {@code WorldGenRegion} hands back over a {@code ProtoChunk} is
 * fine to write to as-is.
 */
public final class LootProvider_LootTable extends LootProvider {

    /** Everything from index 2 on — the {@code LootLocation}s that name a real table. */
    private static final LootLocation[] realLocations =
            java.util.Arrays.copyOfRange(LootLocation.values(), 2, LootLocation.values().length);

    @Override
    public void setLoot(CityWorldGenerator generator, Odds odds, LootLocation lootLocation, Block block) {
        setLoot(generator, odds, lootLocation, block, 0);
    }

    @Override
    public void setLoot(CityWorldGenerator generator, Odds odds, LootLocation lootLocation, Block block, int tier) {

        // An empty chest is one with no table on it, so there is nothing to do. Upstream instead
        // assigned minecraft:empty, which rolls nothing — same chest, one more table lookup.
        if (lootLocation == LootLocation.EMPTY)
            return;

        // Anything at all. Upstream re-rolled with getRandomInt(values().length - 1) + 1, which
        // indexed a 13-element array with as much as 14 and threw; and it could land on RANDOM
        // again and recurse. Rolling within the real locations is what it meant to say.
        if (lootLocation == LootLocation.RANDOM)
            lootLocation = realLocations[odds.getRandomInt(realLocations.length)];

        BlockEntity entity = block.getState();
        if (!(entity instanceof RandomizableContainer container))
            return;

        long seed = odds.getRandomLong();
        container.setLootTable(keyFor(generator, lootLocation, tier, seed), seed);
    }

    /** The everyday tables a MODERN or ruined APOCALYPSE world rolls in its own version (see {@link #keyFor(CityWorldGenerator, LootLocation, int)}). */
    private static final java.util.Set<LootLocation> STYLED =
            java.util.EnumSet.of(LootLocation.BUILDING, LootLocation.NIGHTSTAND, LootLocation.FOOD);

    /**
     * As {@link #keyFor(LootLocation, int)}, in this world's style: a MODERN world (and an APOCALYPSE world's pristine
     * twin) rolls {@code chests/modern/<name>} for the everyday tables, a ruined APOCALYPSE world
     * {@code chests/apocalypse/<name>}, and one container in {@code 1 / oddsOfFinds} (decided here, from the
     * container's own loot {@code seed}: a loot table cannot read a world setting) the {@code <name>_find} version,
     * which adds a find. Every other style, and every other table, is the shared one, upstream's. The style tables end
     * in the shared {@code <name>_extra} hook, so a pack filling it reaches every style; written by
     * {@code scripts/gen_style_loot.py}.
     */
    public static ResourceKey<LootTable> keyFor(CityWorldGenerator generator, LootLocation lootLocation, int tier,
            long seed) {
        if (tier == 0 && generator != null && STYLED.contains(lootLocation)) {
            String style = generator.isApocalypseStyle() ? "apocalypse" : generator.isModernStyle() ? "modern" : null;
            if (style != null)
                return styled(style, lootLocation, seed, generator.getSettings().oddsOfFinds);
        }
        return keyFor(lootLocation, tier);
    }

    /** {@code chests/<style>/<name>}, or its {@code _find} version for one container in {@code 1 / findOdds}. */
    public static ResourceKey<LootTable> styled(String style, LootLocation lootLocation, long seed, double findOdds) {
        boolean find = new java.util.Random(seed ^ 0x46494E44L).nextDouble() < findOdds; // "FIND"
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("cityworld",
                "chests/" + style + "/" + lootLocation.name().toLowerCase(Locale.ROOT) + (find ? "_find" : "")));
    }

    /** The table a location names: {@code cityworld:chests/<lowercase name>}. */
    public static ResourceKey<LootTable> keyFor(LootLocation lootLocation) {
        return keyFor(lootLocation, 0);
    }

    /**
     * The table a location names on depth {@code tier}: the plain table for tier 0, else
     * {@code cityworld:chests/<lowercase name>_floor<tier>} — shipped for every vault room table, and
     * composed there as the room's table plus that floor's bonus and hook (see {@code
     * scripts/gen_vault_floor_tables.py}).
     */
    public static ResourceKey<LootTable> keyFor(LootLocation lootLocation, int tier) {
        String name = "chests/" + lootLocation.name().toLowerCase(Locale.ROOT);
        // never past the deepest tier shipped: vaults go to eight levels (6.2.0) and a level-7 footlocker named
        // vault_quarters_floor7, which does not exist, so the chest was empty and Lootr said so in chat
        tier = Math.min(tier, me.daddychurchill.CityWorld.Plats.Nature.VaultLot.DEEPEST_LOOT_TIER);
        if (tier > 0)
            name += "_floor" + tier;
        return ResourceKey.create(Registries.LOOT_TABLE, Identifier.fromNamespaceAndPath("cityworld", name));
    }
}
