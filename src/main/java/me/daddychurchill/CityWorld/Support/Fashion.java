package me.daddychurchill.CityWorld.Support;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.DyeableLeatherItem;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * This season's look for a mall fashion shop (owner, 2026-09-28: "the latest fashions"): leather armour dyed a
 * shop colour and trimmed with a random pattern in a random material. Only the display pieces go through here
 * (item frames); the stock in the chests gets the same from the loot tables ({@code scripts/gen_mall_tables.py}).
 * Anything that is not leather armour comes back plain, and any failure (a pack that removed a trim) leaves the
 * piece plain rather than missing.
 */
public final class Fashion {

    private Fashion() {
    }

    /** The trim patterns and materials every version from 1.20 has. */
    public static final String[] PATTERNS = { "coast", "dune", "eye", "host", "raiser", "rib", "sentry", "shaper", "silence",
            "snout", "spire", "tide", "vex", "ward", "wayfinder", "wild" };
    public static final String[] MATERIALS = { "amethyst", "copper", "diamond", "emerald", "gold", "iron", "lapis", "netherite",
            "quartz", "redstone" };
    /** The season's colours: blush, sky, mint, lilac, mustard, coral, teal, cream, charcoal, burgundy. */
    public static final int[] COLOURS = { 0xF4A6B7, 0x87CEEB, 0x98E0C0, 0xC8A2C8, 0xE1AD01, 0xFF7F50, 0x2A9D8F, 0xF5F0DC,
            0x36454F, 0x800020 };

    public static boolean isLeather(Item item) {
        return item == Items.LEATHER_HELMET || item == Items.LEATHER_CHESTPLATE || item == Items.LEATHER_LEGGINGS
                || item == Items.LEATHER_BOOTS;
    }

    /** {@code item}, dressed if it is leather armour: a colour, and a trim. */
    public static ItemStack dress(ServerLevelAccessor level, Item item, Odds odds) {
        ItemStack stack = new ItemStack(item);
        if (!isLeather(item))
            return stack;
        try {
            // 1.20.1 has no item components: the dye goes through the leather item, the trim into the stack's tag
            if (item instanceof DyeableLeatherItem dyeable)
                dyeable.setColor(stack, COLOURS[odds.getRandomInt(COLOURS.length)]);
            var access = level.registryAccess();
            var material = access.registryOrThrow(Registries.TRIM_MATERIAL).getHolder(ResourceKey.create(Registries.TRIM_MATERIAL,
                    new ResourceLocation("minecraft", MATERIALS[odds.getRandomInt(MATERIALS.length)])));
            var pattern = access.registryOrThrow(Registries.TRIM_PATTERN).getHolder(ResourceKey.create(Registries.TRIM_PATTERN,
                    new ResourceLocation("minecraft", PATTERNS[odds.getRandomInt(PATTERNS.length)])));
            if (material.isPresent() && pattern.isPresent())
                ArmorTrim.setTrim(access, stack, new ArmorTrim(material.get(), pattern.get()));
        } catch (Throwable t) {
            // a plain piece beats no piece
        }
        return stack;
    }
}
