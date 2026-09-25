package me.daddychurchill.CityWorld.Support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Plugins.LootProvider_LootTable;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * Every container a lot leaves behind gets a loot table — the end-of-lot pass behind "anywhere there is
 * a container it should have a loot table, even if it defaults to empty" (owner, 2026-09-25, after a
 * warehouse full of empty modded crates).
 *
 * <p>Chests and barrels that CityWorld places on purpose already carry a table ({@code setChest} →
 * {@link me.daddychurchill.CityWorld.Plugins.LootProvider}). Everything else did not: the storage
 * furniture the pools draw (Macaw's cabinets and drawers, Fantasy's chests and lockboxes), the chests
 * inside a pasted schematic, a modded crate in a warehouse. This runs once per lot after every other
 * decoration, walks the chunk's block entities, and gives each untouched, empty container the lot's
 * {@link PlatLot#defaultLoot() default table} — a warehouse's crates roll the warehouse table, a
 * hospital's cabinets the hospital's, and every table ends in an {@code _extra} hook a pack can own.
 *
 * <p><b>Three kinds of container, three ways in.</b>
 * <ol>
 *   <li>A {@link RandomizableContainer} (vanilla chests, barrels, shulkers; Macaw's storage extends the
 *       same base) takes a deferred table the vanilla way: nothing is rolled until a player opens it.</li>
 *   <li>A plain {@link Container} block entity (vanilla shelves, chiseled bookshelves) is filled now
 *       from the table, seeded from the lot's odds.</li>
 *   <li>A mod inventory that is neither (Fantasy's Furniture: an apexcore {@code InventoryBlockEntity}
 *       exposing only a NeoForge item handler) is filled now through that capability — see
 *       {@link #fillViaCapability}, the one loader-specific method here.</li>
 * </ol>
 *
 * <p>Two guards keep this from being a nuisance: {@code #cityworld:loot/never} lists the block entities
 * that are containers but not storage (furnaces, hoppers, brewing stands, jukeboxes, lecterns…), and a
 * container that already holds anything, or already carries a table, is left exactly as it is.
 */
public final class ContainerLoot {

    private ContainerLoot() {
    }

    /** Containers that must never be given loot: machines, not storage. */
    public static final TagKey<Block> NEVER = MaterialTags.key("cityworld:loot/never");

    private static final AtomicInteger DEFERRED = new AtomicInteger(), FILLED = new AtomicInteger(),
            CAPABILITY = new AtomicInteger(), NEVER_TAGGED = new AtomicInteger(), HAS_TABLE = new AtomicInteger(),
            HAS_ITEMS = new AtomicInteger(), NOT_A_CONTAINER = new AtomicInteger(), OWN_TABLE = new AtomicInteger();

    /**
     * For the self-test: how many block entities each path has handled since startup. {@code hasTable} is
     * every chest CityWorld placed on purpose (already tabled by {@code setChest}); {@code notAContainer}
     * is signs, beds, skulls and the like; the three live counts are what this pass actually did.
     */
    public static String summary() {
        return "deferred=" + DEFERRED.get() + " filled=" + FILLED.get() + " capability=" + CAPABILITY.get()
                + " hasTable=" + HAS_TABLE.get() + " hasItems=" + HAS_ITEMS.get() + " never=" + NEVER_TAGGED.get()
                + " notAContainer=" + NOT_A_CONTAINER.get() + " lotsWithOwnTable=" + OWN_TABLE.get();
    }

    /** The end-of-lot pass: every untouched empty container in this chunk gets the lot's default table. */
    public static void apply(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk, Odds odds) {
        LootLocation loot = lot.defaultLoot();
        if (loot == null || loot == LootLocation.EMPTY || loot == LootLocation.RANDOM)
            return;
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return;
            ChunkAccess access = level.getChunk(chunk.sectionX, chunk.sectionZ);
            ResourceKey<LootTable> key = ownTableOrNull(level, lot);
            if (key == null)
                key = LootProvider_LootTable.keyFor(loot);
            else
                OWN_TABLE.incrementAndGet();
            // ⚠ Ask the REGION for each entity, not the chunk. A block placed during generation leaves only a
            // "DUMMY" NBT stub in the proto-chunk's pending map; WorldGenRegion.getBlockEntity materialises
            // the real block entity from that stub on demand, ChunkAccess.getBlockEntity answers null for it.
            // The first version asked the chunk and so found only the chests setChest had already
            // materialised — 848 "skipped", zero handled, with three furniture mods installed. Copy the
            // key set: materialising moves an entry from the pending map into the live one.
            for (BlockPos pos : List.copyOf(access.getBlockEntitiesPos())) {
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity != null)
                    assign(level, pos, entity, key, odds.getRandomLong());
            }
        } catch (Throwable t) {
            // loot must never take a chunk down
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: container loot pass failed at {},{}: {}",
                    chunk.sectionX, chunk.sectionZ, t.toString());
        }
    }

    /**
     * A specific table onto one placed block — for a builder that knows what a container holds (the
     * vault's ammunition shelves) rather than the lot default. No-op when nothing at the cell can hold loot.
     */
    public static boolean assignAt(RealBlocks chunk, int x, int y, int z, LootLocation loot, Odds odds) {
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return false;
            BlockPos pos = new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z);
            BlockEntity entity = level.getBlockEntity(pos);
            return entity != null && assign(level, pos, entity, LootProvider_LootTable.keyFor(loot), odds.getRandomLong());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean assign(WorldGenLevel level, BlockPos pos, BlockEntity entity, ResourceKey<LootTable> key,
            long seed) {
        BlockState state = entity.getBlockState();
        if (state.is(NEVER) || isStation(state)) {
            NEVER_TAGGED.incrementAndGet();
            return false;
        }
        if (entity instanceof RandomizableContainer randomizable) {
            if (randomizable.getLootTable() != null) {
                HAS_TABLE.incrementAndGet();
                return false;
            }
            if (entity instanceof Container c && !c.isEmpty()) {
                HAS_ITEMS.incrementAndGet();
                return false;
            }
            randomizable.setLootTable(key, seed);
            DEFERRED.incrementAndGet();
            return true;
        }
        if (entity instanceof Container container) {
            if (!container.isEmpty()) {
                HAS_ITEMS.incrementAndGet();
                return false;
            }
            LootTable table = tableFor(level, key);
            if (table == null)
                return false;
            table.fill(container, params(level, pos), seed);
            FILLED.incrementAndGet();
            return true;
        }
        boolean done = fillViaCapability(level, pos, entity, state, key, seed);
        if (!done)
            NOT_A_CONTAINER.incrementAndGet();
        return done;
    }

    /** A furniture crafting station is an inventory, not a store. Recognised by id, since every set has one. */
    private static boolean isStation(BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && key.getPath().endsWith("furniture_station");
    }

    /**
     * The lot's own table ({@link PlatLot#ownLootTable()}) if it names one that exists — a schematic's
     * {@code chests/schematic/<name>}, or its sidecar's {@code Loot:} — else null for the lot default.
     */
    private static ResourceKey<LootTable> ownTableOrNull(WorldGenLevel level, PlatLot lot) {
        String own = lot.ownLootTable();
        if (own == null)
            return null;
        try {
            var id = net.minecraft.resources.Identifier.tryParse(own);
            if (id == null)
                return null;
            ResourceKey<LootTable> key = ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, id);
            return tableFor(level, key) == null ? null : key;
        } catch (Throwable t) {
            return null;
        }
    }

    private static LootTable tableFor(WorldGenLevel level, ResourceKey<LootTable> key) {
        var server = level.getServer();
        if (server == null)
            return null;
        LootTable table = server.reloadableRegistries().getLootTable(key);
        return table == LootTable.EMPTY ? null : table;
    }

    private static LootParams params(WorldGenLevel level, BlockPos pos) {
        return new LootParams.Builder(level.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
    }

    /**
     * The loader-specific path: a mod block entity that exposes its inventory only as a NeoForge item
     * handler. The table is rolled now and the stacks inserted through the handler. Each version branch
     * adapts this one method (the transfer API on 21.11 and 26.x, {@code IItemHandler} on 1.21.1, Forge's
     * {@code ForgeCapabilities.ITEM_HANDLER} on 1.20.1); nothing else here is loader-specific.
     */
    private static boolean fillViaCapability(WorldGenLevel level, BlockPos pos, BlockEntity entity, BlockState state,
            ResourceKey<LootTable> key, long seed) {
        var handler = level.getLevel().getCapability(net.neoforged.neoforge.capabilities.Capabilities.Item.BLOCK,
                pos, state, entity, null);
        if (handler == null)
            return false;
        // already holds something: leave it
        for (int i = 0; i < handler.size(); i++)
            if (handler.getAmountAsInt(i) > 0) {
                HAS_ITEMS.incrementAndGet();
                return true;
            }
        LootTable table = tableFor(level, key);
        if (table == null)
            return false;
        var stacks = table.getRandomItems(params(level, pos), net.minecraft.util.RandomSource.create(seed));
        int inserted = 0;
        try (var tx = net.neoforged.neoforge.transfer.transaction.Transaction.openRoot()) {
            for (var stack : stacks)
                if (!stack.isEmpty())
                    inserted += handler.insert(net.neoforged.neoforge.transfer.item.ItemResource.of(stack),
                            stack.getCount(), tx);
            tx.commit();
        }
        CAPABILITY.incrementAndGet();
        return inserted > 0 || stacks.isEmpty();
    }
}
