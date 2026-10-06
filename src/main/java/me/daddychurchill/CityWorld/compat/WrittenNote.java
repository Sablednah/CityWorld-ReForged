package me.daddychurchill.CityWorld.compat;

import java.util.List;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.network.Filterable;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.WrittenBookContent;

/**
 * A signed book with a few pages of plain text — a note someone left. How a book holds its pages differs by version
 * (this copy is 1.21.x and 26.x: the written-book data component; 1.20.1 keeps them in the item's NBT), so this is
 * per-branch, like {@link Loot}.
 */
public final class WrittenNote {

    private WrittenNote() {
    }

    public static ItemStack book(String title, String author, List<String> pages) {
        ItemStack stack = new ItemStack(Items.WRITTEN_BOOK);
        stack.set(DataComponents.WRITTEN_BOOK_CONTENT, new WrittenBookContent(Filterable.passThrough(title), author, 0,
                pages.stream().map(p -> Filterable.<Component>passThrough(Component.literal(p))).toList(), true));
        return stack;
    }
}
