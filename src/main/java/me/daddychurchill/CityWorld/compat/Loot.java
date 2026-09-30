package me.daddychurchill.CityWorld.compat;

import java.util.List;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.RandomizableContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;

/**
 * The loot-table and mod-inventory calls that differ by Minecraft version and loader, so that
 * {@code Support/ContainerLoot} is the same source on every branch. Like {@link Material}, this file is PER
 * BRANCH: a cherry-pick that conflicts here is resolved by keeping the branch's copy.
 *
 * <p>This copy is 1.21.1: tables keyed by {@code ResourceKey<LootTable>} (ids are {@code ResourceLocation}, not
 * yet {@code Identifier}), and a mod inventory reached through NeoForge's {@code IItemHandler}, before the transfer
 * API. 1.21.11 and 26.x use the transfer API; 1.20.1 keys tables by {@code ResourceLocation} and uses Forge's
 * item-handler capability.
 */
public final class Loot {

    private Loot() {
    }

    /** A loot table's key, whatever this version keys tables by. */
    public record Ref(ResourceKey<LootTable> key) {
    }

    public static Ref ref(ResourceKey<LootTable> key) {
        return key == null ? null : new Ref(key);
    }

    /** The table named {@code id} ("cityworld:chests/..."), or null if the id does not parse. */
    public static Ref parse(String id) {
        var parsed = id == null ? null : net.minecraft.resources.ResourceLocation.tryParse(id);
        return parsed == null ? null
                : new Ref(ResourceKey.create(net.minecraft.core.registries.Registries.LOOT_TABLE, parsed));
    }

    /** The loaded table, or null when it is missing or empty. */
    public static LootTable table(WorldGenLevel level, Ref ref) {
        var server = level.getServer();
        if (server == null || ref == null)
            return null;
        LootTable table = server.reloadableRegistries().getLootTable(ref.key());
        return table == LootTable.EMPTY ? null : table;
    }

    /** A container that takes a deferred table, rolled when a player first opens it. */
    public static boolean isRandomizable(BlockEntity entity) {
        return entity instanceof RandomizableContainer;
    }

    public static boolean hasTable(BlockEntity entity) {
        return entity instanceof RandomizableContainer randomizable && randomizable.getLootTable() != null;
    }

    public static void setTable(BlockEntity entity, Ref ref, long seed) {
        ((RandomizableContainer) entity).setLootTable(ref.key(), seed);
    }

    public static List<ItemStack> roll(LootTable table, LootParams params, long seed) {
        return table.getRandomItems(params, net.minecraft.util.RandomSource.create(seed));
    }

    /** A mod inventory reached only through the loader's item-handler capability. */
    public interface Inventory {
        int size();

        boolean isEmpty(int slot);

        /** Put {@code stack} in {@code slot}; how many went in. Never changes {@code stack}. */
        int insert(int slot, ItemStack stack);
    }

    /** The block's item-handler inventory, or null if it has none. */
    public static Inventory inventory(WorldGenLevel level, BlockPos pos, BlockState state, BlockEntity entity) {
        net.neoforged.neoforge.items.IItemHandler handler = level.getLevel().getCapability(
                net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK, pos, state, entity, null);
        if (handler == null)
            return null;
        return new Inventory() {
            @Override
            public int size() {
                return handler.getSlots();
            }

            @Override
            public boolean isEmpty(int slot) {
                return handler.getStackInSlot(slot).isEmpty();
            }

            @Override
            public int insert(int slot, ItemStack stack) {
                return stack.getCount() - handler.insertItem(slot, stack.copy(), false).getCount();
            }
        };
    }
}
