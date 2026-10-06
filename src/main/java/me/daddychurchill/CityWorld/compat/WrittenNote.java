package me.daddychurchill.CityWorld.compat;

import java.util.List;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * A signed book with a few pages of plain text — a note someone left. How a book holds its pages differs by version
 * (this copy is 1.20.1: the item's NBT, each page a JSON text component; 1.21+ uses the written-book data
 * component), so this is per-branch, like {@link Loot}.
 */
public final class WrittenNote {

    private WrittenNote() {
    }

    public static ItemStack book(String title, String author, List<String> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        CompoundTag tag = stack.getOrCreateTag();
        tag.putString("title", title);
        tag.putString("author", author);
        ListTag list = new ListTag();
        for (String page : pages)
            list.add(StringTag.valueOf(Component.Serializer.toJson(Component.literal(page))));
        tag.put("pages", list);
        tag.putBoolean("resolved", true);
        return stack;
    }
}
