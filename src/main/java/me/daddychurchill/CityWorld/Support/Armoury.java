package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * The pieces that give a vault armoury its identity, each driven by data a gun mod can extend:
 * weapons in item frames from {@code #cityworld:armoury/weapons}, armour on stands from
 * {@code #cityworld:armoury/armour}, ammunition shelves rolling {@code chests/vault_ammo}.
 *
 * <p>Entities are constructed directly and added through the region, never via {@code create}/
 * {@code survives} — the same rule as {@link Furniture}'s paintings: anything that queries the real
 * level from the worldgen thread blocks on unloaded chunks until the watchdog fires.
 */
public final class Armoury {

    private Armoury() {
    }

    public static final TagKey<Item> WEAPONS = TagKey.create(Registries.ITEM,
            new ResourceLocation("cityworld", "armoury/weapons"));
    public static final TagKey<Item> ARMOUR = TagKey.create(Registries.ITEM,
            new ResourceLocation("cityworld", "armoury/armour"));

    /** What the shipped tags say without a datapack, and the fallback if a pack empties them. */
    private static final Item[] WEAPON_FALLBACK = { Items.IRON_SWORD, Items.IRON_AXE, Items.BOW, Items.CROSSBOW,
            Items.SHIELD, Items.TRIDENT };
    private static final Item[] ARMOUR_FALLBACK = { Items.IRON_HELMET, Items.IRON_CHESTPLATE, Items.IRON_LEGGINGS,
            Items.IRON_BOOTS, Items.CHAINMAIL_HELMET, Items.CHAINMAIL_CHESTPLATE, Items.CHAINMAIL_LEGGINGS,
            Items.CHAINMAIL_BOOTS, Items.LEATHER_HELMET, Items.LEATHER_CHESTPLATE, Items.LEATHER_LEGGINGS,
            Items.LEATHER_BOOTS };

    /** The tag resolved, or the fallback when it is missing or empty. */
    static List<Item> pool(TagKey<Item> tag, Item[] fallback) {
        List<Item> out = new ArrayList<>();
        try {
            BuiltInRegistries.ITEM.getTag(tag).ifPresent(set -> set.forEach(h -> out.add(h.value())));
        } catch (Throwable ignored) {
        }
        if (out.isEmpty())
            out.addAll(List.of(fallback));
        return out;
    }

    /**
     * A weapon in an item frame on the wall behind (x, y, z), facing {@code out} into the room. Needs a
     * sturdy wall in the cell behind and air here; false otherwise, so a caller can try another wall.
     */
    public static boolean weaponFrame(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide) {
        if (!chunk.isEmpty(x, y, z))
            return false;
        int bx = x + wallSide.getModX(), bz = z + wallSide.getModZ();
        if (bx < 0 || bx > 15 || bz < 0 || bz > 15 || !chunk.isWallBacking(bx, y, bz, wallSide.getOppositeFace()))
            return false;
        ServerLevelAccessor server = chunk.getServerLevel();
        Direction out = wallSide.getOppositeFace().toDirection();
        if (server == null || out == null)
            return false;
        List<Item> weapons = pool(WEAPONS, WEAPON_FALLBACK);
        BlockPos pos = new BlockPos(chunk.getOriginX() + x, y, chunk.getOriginZ() + z);
        ItemFrame frame = new ItemFrame(server.getLevel(), pos, out);
        frame.setSilent(true);
        frame.setItem(new ItemStack(weapons.get(odds.getRandomInt(weapons.size()))), false);
        frame.setInvulnerable(false);
        server.addFreshEntityWithPassengers(frame);
        return true;
    }

    /**
     * An armour stand wearing one to three pieces from the armour pool, facing {@code facing}. The
     * slot comes from the item itself, so a mod's helmet sits on the head without being special-cased;
     * anything the pool holds that is not wearable is skipped.
     */
    public static boolean armourStand(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace facing) {
        ServerLevelAccessor server = chunk.getServerLevel();
        if (server == null)
            return false;
        ArmorStand stand = new ArmorStand(server.getLevel(), chunk.getOriginX() + x + 0.5, y,
                chunk.getOriginZ() + z + 0.5);
        Direction dir = facing.toDirection();
        stand.setYRot(dir == null ? 0 : dir.toYRot());
        stand.setSilent(true);
        List<Item> armour = pool(ARMOUR, ARMOUR_FALLBACK);
        int pieces = 1 + odds.getRandomInt(3);
        for (int i = 0; i < pieces && !armour.isEmpty(); i++) {
            ItemStack stack = new ItemStack(armour.get(odds.getRandomInt(armour.size())));
            EquipmentSlot slot = net.minecraft.world.entity.Mob.getEquipmentSlotForItem(stack);
            if (slot.getType() != EquipmentSlot.Type.ARMOR || !stand.getItemBySlot(slot).isEmpty())
                continue;
            stand.setItemSlot(slot, stack);
        }
        server.addFreshEntityWithPassengers(stand);
        return true;
    }

    /**
     * A shelf block from the shelf pool mounted on the wall behind (x, y, z), rolling the ammunition
     * table — a vanilla shelf is a container, so the table fills it now. Silently nothing on a version
     * or install with no shelf block, or where the cell is not free.
     */
    public static boolean ammoShelf(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide) {
        return ammoShelf(chunk, odds, x, y, z, wallSide, 0);
    }

    /** As above on loot tier {@code tier} (the vault's deeper floors). */
    public static boolean ammoShelf(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace wallSide, int tier) {
        if (!chunk.isEmpty(x, y, z))
            return false;
        int bx = x + wallSide.getModX(), bz = z + wallSide.getModZ();
        if (bx < 0 || bx > 15 || bz < 0 || bz > 15 || !chunk.isWallBacking(bx, y, bz, wallSide.getOppositeFace()))
            return false;
        Material shelf = FurnitureTags.pick(FurnitureTags.SHELF, odds);
        if (shelf == null)
            return false;
        BlockFace facing = FurnitureTags.facingFor(shelf, wallSide.getOppositeFace());
        if (!chunk.setFurniture(x, y, z, shelf, facing))
            return false;
        ContainerLoot.assignAt(chunk, x, y, z, LootLocation.VAULT_AMMO, odds, tier);
        return true;
    }
}
