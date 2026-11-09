package me.daddychurchill.CityWorld.Support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Plugins.LootProvider_LootTable;
import me.daddychurchill.CityWorld.compat.Loot;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
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
 * decoration, walks the chunk's block entities, and gives each untouched, empty container the table the
 * lot names for that spot ({@link PlatLot#lootTableAt} — a schematic's own, a mall shop's) or else the
 * lot's {@link PlatLot#defaultLoot() default table} at that height's tier — a warehouse's crates roll the
 * warehouse table, a hospital's cabinets the hospital's, a deep vault floor its floor's, and every table
 * ends in an {@code _extra} hook a pack can own.
 *
 * <p><b>Three kinds of container, three ways in.</b>
 * <ol>
 *   <li>A randomizable container (vanilla chests, barrels, shulkers; Macaw's storage extends the
 *       same base) takes a deferred table the vanilla way: nothing is rolled until a player opens it.</li>
 *   <li>A plain {@link Container} block entity (vanilla shelves, chiseled bookshelves) is filled now
 *       from the table, seeded from the lot's odds.</li>
 *   <li>A mod inventory that is neither (Fantasy's Furniture: an apexcore {@code InventoryBlockEntity}
 *       exposing only a NeoForge item handler) is filled now through that capability — see
 *       {@link #fillViaCapability}.</li>
 * </ol>
 *
 * <p>Everything that differs by version or loader (how a table is keyed and looked up, the item-handler
 * capability) is in the per-branch {@link Loot}, so this file is the same on every branch.
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

    /**
     * Containers that hold ONE thing being prepared, not storage: a cutting board, a skillet. They get a single
     * item of food from the item tag {@code #cityworld:kitchen/food} (two in three; the rest stay bare) instead of
     * the lot's loot table — a house's table put a painting on the chopping board (owner, 2026-10-02).
     */
    public static final TagKey<Block> FOOD = MaterialTags.key("cityworld:loot/food");
    public static final TagKey<net.minecraft.world.item.Item> KITCHEN_FOOD = TagKey.create(
            net.minecraft.core.registries.Registries.ITEM,
            NEVER.location().withPath("kitchen/food"));

    private static final AtomicInteger DEFERRED = new AtomicInteger(), FILLED = new AtomicInteger(),
            CAPABILITY = new AtomicInteger(), NEVER_TAGGED = new AtomicInteger(), HAS_TABLE = new AtomicInteger(),
            HAS_ITEMS = new AtomicInteger(), NOT_A_CONTAINER = new AtomicInteger(), OWN_TABLE = new AtomicInteger(),
            SCATTERED = new AtomicInteger(), RESTOCKED = new AtomicInteger(), OVERFILLED = new AtomicInteger();

    /**
     * For the self-test: how many block entities each path has handled since startup. {@code hasTable} is
     * every chest CityWorld placed on purpose (already tabled by {@code setChest}); {@code notAContainer}
     * is signs, beds, skulls and the like; the three live counts are what this pass actually did.
     */
    public static String summary() {
        return "deferred=" + DEFERRED.get() + " filled=" + FILLED.get() + " capability=" + CAPABILITY.get()
                + " scattered=" + SCATTERED.get()
                + " hasTable=" + HAS_TABLE.get() + " hasItems=" + HAS_ITEMS.get() + " never=" + NEVER_TAGGED.get()
                + " notAContainer=" + NOT_A_CONTAINER.get() + " lotsWithOwnTable=" + OWN_TABLE.get()
                + " restocked=" + RESTOCKED.get() + " overfilled=" + OVERFILLED.get();
    }

    /** The end-of-lot pass: every untouched empty container in this chunk gets the table the lot names for its
     *  position ({@link PlatLot#lootTableAt}, when that table exists), else the lot's default at its tier there. */
    public static void apply(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk, Odds odds) {
        LootLocation loot = lot.defaultLoot();
        if (loot == null || loot == LootLocation.EMPTY || loot == LootLocation.RANDOM)
            return;
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return;
            ChunkAccess access = level.getChunk(chunk.sectionX, chunk.sectionZ);
            java.util.Map<String, java.util.Optional<Loot.Ref>> resolved = new java.util.HashMap<>();
            boolean counted = false;
            // ⚠ Ask the REGION for each entity, not the chunk. A block placed during generation leaves only a
            // "DUMMY" NBT stub in the proto-chunk's pending map; WorldGenRegion.getBlockEntity materialises
            // the real block entity from that stub on demand, ChunkAccess.getBlockEntity answers null for it.
            // The first version asked the chunk and so found only the chests setChest had already
            // materialised — 848 "skipped", zero handled, with three furniture mods installed. Copy the
            // key set: materialising moves an entry from the pending map into the live one.
            // ⚠ The chunk's positions are world coordinates; on vanilla land the lot draws through a ShiftedRegion
            // that LIFTS every position it is given by the city's shift, so a chunk position handed straight to it
            // asked (and fed) the cell above the container by the lift — every vanilla-land container this pass
            // alone would have filled stayed empty. Everything below works in the lot's own (unlifted) frame.
            int lift = me.daddychurchill.CityWorld.worldgen.ShiftedRegion.shiftOf(level);
            for (BlockPos at : List.copyOf(access.getBlockEntitiesPos())) {
                BlockPos pos = lift == 0 ? at : at.below(lift);
                BlockEntity entity = level.getBlockEntity(pos);
                if (entity == null)
                    continue;
                String id = lot.lootTableAt(pos.getX(), pos.getY(), pos.getZ());
                Loot.Ref own = id == null ? null
                        : resolved.computeIfAbsent(id, k -> java.util.Optional.ofNullable(ownTableOrNull(level, k))).orElse(null);
                if (own != null && !counted) {
                    OWN_TABLE.incrementAndGet();
                    counted = true;
                }
                long seed = odds.getRandomLong();
                try {
                    // a store: a chest a room builder already gave the ordinary table becomes stock too
                    if (own != null && lot.ownLootReplacesTabled() && restock(entity, own, seed))
                        continue;
                    assign(level, pos, entity,
                            own != null ? own : Loot.ref(LootProvider_LootTable.keyFor(generator, loot, lot.lootTierAt(pos.getY()), seed)),
                            seed);
                } catch (RuntimeException e) {
                    // One container's own code must not cost the rest of the chunk its loot: Refurbished's cutting
                    // board takes the item, then may read its level, which is null during generation (NPE).
                    me.daddychurchill.CityWorld.CityWorldMod.LOGGER.debug("CityWorld: loot for {} at {} failed: {}",
                            entity.getType(), pos, e.toString());
                }
            }
        } catch (Throwable t) {
            // loot must never take a chunk down
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: container loot pass failed at {},{}: {}",
                    chunk.sectionX, chunk.sectionZ, t.toString());
        }
    }

    /** Swaps the table of an unopened chest that already has one. False (and nothing done) for anything else. */
    private static boolean restock(BlockEntity entity, Loot.Ref key, long seed) {
        BlockState state = entity.getBlockState();
        if (state.is(NEVER) || state.is(FOOD) || isStation(state) || !Loot.isRandomizable(entity)
                || !Loot.hasTable(entity))
            return false;
        Loot.setTable(entity, key, seed);
        RESTOCKED.incrementAndGet();
        return true;
    }

    /**
     * As {@link #assignTableAt}, but a chest that already has a table gets this one instead — the barrel beside
     * a shop counter, placed with the generic shop table, takes its trade's.
     */
    public static boolean restockAt(RealBlocks chunk, int x, int y, int z, String table, Odds odds) {
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return false;
            BlockPos pos = new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z);
            BlockEntity entity = level.getBlockEntity(pos);
            var key = ownTableOrNull(level, table);
            return entity != null && key != null && restock(entity, key, odds.getRandomLong());
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * A specific table onto one placed block — for a builder that knows what a container holds (the
     * vault's ammunition shelves) rather than the lot default. No-op when nothing at the cell can hold loot.
     */
    public static boolean assignAt(RealBlocks chunk, int x, int y, int z, LootLocation loot, Odds odds) {
        return assignAt(chunk, x, y, z, loot, odds, 0);
    }

    /** As above on loot tier {@code tier} (the vault's deeper floors). */
    public static boolean assignAt(RealBlocks chunk, int x, int y, int z, LootLocation loot, Odds odds, int tier) {
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return false;
            BlockPos pos = new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z);
            BlockEntity entity = level.getBlockEntity(pos);
            return entity != null && assign(level, pos, entity, Loot.ref(LootProvider_LootTable.keyFor(loot, tier)), odds.getRandomLong());
        } catch (Throwable t) {
            return false;
        }
    }

    /** A named table (e.g. a mall shop's) onto one placed container, if the table exists. */
    public static boolean assignTableAt(RealBlocks chunk, int x, int y, int z, String table, Odds odds) {
        try {
            if (!(chunk.getServerLevel() instanceof WorldGenLevel level))
                return false;
            BlockPos pos = new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z);
            BlockEntity entity = level.getBlockEntity(pos);
            var key = ownTableOrNull(level, table);
            return entity != null && key != null && assign(level, pos, entity, key, odds.getRandomLong());
        } catch (Throwable t) {
            return false;
        }
    }

    private static boolean assign(WorldGenLevel level, BlockPos pos, BlockEntity entity, Loot.Ref key,
            long seed) {
        BlockState state = entity.getBlockState();
        if (state.is(NEVER) || isStation(state)) {
            NEVER_TAGGED.incrementAndGet();
            return false;
        }
        if (state.is(FOOD))
            return serveFood(level, pos, entity, state, seed);
        if (Loot.isRandomizable(entity)) {
            if (Loot.hasTable(entity)) {
                HAS_TABLE.incrementAndGet();
                return false;
            }
            if (entity instanceof Container c && !c.isEmpty()) {
                HAS_ITEMS.incrementAndGet();
                return false;
            }
            Loot.setTable(entity, key, seed);
            DEFERRED.incrementAndGet();
            return true;
        }
        if (entity instanceof Container container) {
            if (!container.isEmpty()) {
                HAS_ITEMS.incrementAndGet();
                return false;
            }
            LootTable table = Loot.table(level, key);
            if (table == null)
                return false;
            fillSized(container, table, params(level, pos), seed);
            FILLED.incrementAndGet();
            return true;
        }
        boolean done = fillViaCapability(level, pos, entity, state, key, seed);
        if (!done)
            NOT_A_CONTAINER.incrementAndGet();
        return done;
    }

    /**
     * Vanilla's {@code LootTable.fill}, sized to the container: the same roll and the same scatter over the empty
     * slots, but same-item stacks are merged first and what still does not fit is counted ({@code overfilled}) and
     * dropped quietly. The building/nightstand/food tables are sized for a 27-slot chest; a furniture mod's drawer
     * or cabinet has 6–9, and vanilla's fill logged "Tried to over-fill a container" for every one — 151 lines in
     * one ZARP session, whose loot modifiers add stacks to the same tables (2026-11-02).
     */
    private static void fillSized(Container container, LootTable table, net.minecraft.world.level.storage.loot.LootParams params,
            long seed) {
        java.util.List<net.minecraft.world.item.ItemStack> rolled = new java.util.ArrayList<>(Loot.roll(table, params, seed));
        // merge: a second stack of the same item joins the first while it fits
        java.util.List<net.minecraft.world.item.ItemStack> merged = new java.util.ArrayList<>();
        for (var stack : rolled) {
            if (stack.isEmpty())
                continue;
            boolean joined = false;
            for (var have : merged)
                if (net.minecraft.world.item.ItemStack.isSameItemSameComponents(have, stack)
                        && have.getCount() + stack.getCount() <= have.getMaxStackSize()) {
                    have.grow(stack.getCount());
                    joined = true;
                    break;
                }
            if (!joined)
                merged.add(stack.copy());
        }
        java.util.List<Integer> empty = new java.util.ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++)
            if (container.getItem(i).isEmpty())
                empty.add(i);
        java.util.Random random = new java.util.Random(seed);
        java.util.Collections.shuffle(empty, random);
        // vanilla spreads stacks over the empty slots by splitting the big ones; keep that look while there is room
        if (merged.size() < empty.size())
            merged = split(merged, empty.size(), random);
        int placed = 0;
        for (var stack : merged) {
            if (placed >= empty.size())
                break;
            container.setItem(empty.get(placed++), stack);
        }
        if (merged.size() > empty.size())
            OVERFILLED.incrementAndGet();
    }

    /** Vanilla's shuffleAndSplitItems, in short: split the splittable stacks until the slots are used or nothing splits. */
    private static java.util.List<net.minecraft.world.item.ItemStack> split(java.util.List<net.minecraft.world.item.ItemStack> stacks,
            int slots, java.util.Random random) {
        java.util.List<net.minecraft.world.item.ItemStack> out = new java.util.ArrayList<>(stacks);
        int guard = 0;
        while (out.size() < slots && guard++ < 64) {
            net.minecraft.world.item.ItemStack biggest = null;
            for (var s : out)
                if (s.getCount() > 1 && (biggest == null || s.getCount() > biggest.getCount()))
                    biggest = s;
            if (biggest == null)
                break;
            int half = 1 + random.nextInt(biggest.getCount() / 2 + 1);
            var part = biggest.split(Math.min(half, biggest.getCount() - 1));
            if (part.isEmpty())
                break;
            out.add(part);
        }
        java.util.Collections.shuffle(out, random);
        return out;
    }

    /** One item of {@link #KITCHEN_FOOD} into the first slot of a board or a pan, two times in three. */
    private static boolean serveFood(WorldGenLevel level, BlockPos pos, BlockEntity entity, BlockState state,
            long seed) {
        java.util.Random random = new java.util.Random(seed);
        if (random.nextInt(3) == 0)
            return false;
        List<net.minecraft.world.item.Item> foods = new java.util.ArrayList<>(
                Armoury.pool(KITCHEN_FOOD, new net.minecraft.world.item.Item[] { net.minecraft.world.item.Items.BREAD }));
        // by id: a tag's own order is not promised to be the same from one load to the next
        foods.sort(java.util.Comparator.comparing(
                item -> net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(item).toString()));
        var stack = new net.minecraft.world.item.ItemStack(foods.get(random.nextInt(foods.size())));
        if (entity instanceof Container container) {
            if (!container.isEmpty())
                return false;
            container.setItem(0, stack);
            return true;
        }
        Loot.Inventory inventory = Loot.inventory(level, pos, state, entity);
        if (inventory == null || inventory.size() == 0 || !inventory.isEmpty(0))
            return false;
        return inventory.insert(0, stack) > 0; // a pan refuses what cannot be cooked: it stays empty
    }

    /** A furniture crafting station is an inventory, not a store. Recognised by id, since every set has one. */
    private static boolean isStation(BlockState state) {
        var key = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock());
        return key != null && key.getPath().endsWith("furniture_station");
    }

    /**
     * The table named by {@code own} if it exists — a schematic's {@code chests/schematic/<name>} or its
     * sidecar's {@code Loot:}, a mall shop's, or one handed to {@code assignTableAt} — else null.
     */
    private static Loot.Ref ownTableOrNull(WorldGenLevel level, String own) {
        try {
            Loot.Ref ref = Loot.parse(own);
            return Loot.table(level, ref) == null ? null : ref;
        } catch (Throwable t) {
            return null;
        }
    }

    private static LootParams params(WorldGenLevel level, BlockPos pos) {
        return new LootParams.Builder(level.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST);
    }

    /**
     * A mod block entity that exposes its inventory only through the loader's item handler ({@link Loot#inventory}).
     * The table is rolled now and each stack put in a random empty slot, the way a vanilla chest scatters its loot
     * (inserting in order packed every roll into the top-left corner).
     */
    private static boolean fillViaCapability(WorldGenLevel level, BlockPos pos, BlockEntity entity, BlockState state,
            Loot.Ref key, long seed) {
        Loot.Inventory inventory = Loot.inventory(level, pos, state, entity);
        if (inventory == null)
            return false;
        // already holds something: leave it
        java.util.List<Integer> empty = new java.util.ArrayList<>();
        for (int i = 0; i < inventory.size(); i++) {
            if (!inventory.isEmpty(i)) {
                HAS_ITEMS.incrementAndGet();
                return true;
            }
            empty.add(i);
        }
        LootTable table = Loot.table(level, key);
        if (table == null)
            return false;
        var stacks = Loot.roll(table, params(level, pos), seed);
        java.util.Collections.shuffle(empty, new java.util.Random(seed));
        int inserted = 0, used = 0, highest = -1;
        boolean overflow = false;
        for (var stack : stacks) {
            if (stack.isEmpty())
                continue;
            var left = stack.copy();
            for (var slots = empty.iterator(); slots.hasNext() && !left.isEmpty();) {
                int slot = slots.next();
                int in = inventory.insert(slot, left);
                if (in > 0) {
                    slots.remove();
                    used++;
                    highest = Math.max(highest, slot);
                    left.shrink(in);
                    inserted += in;
                }
            }
            if (!left.isEmpty())
                overflow = true; // the table rolled more than this inventory holds: dropped quietly, counted
        }
        CAPABILITY.incrementAndGet();
        if (highest >= used) // not just the first slots, in order: the self-test's proof that loot is scattered
            SCATTERED.incrementAndGet();
        if (overflow)
            OVERFILLED.incrementAndGet();
        return inserted > 0 || stacks.isEmpty();
    }
}
