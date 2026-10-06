package me.daddychurchill.CityWorld.Support;

import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Loot;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.WrittenNote;
import net.minecraft.world.Container;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

/**
 * The rarest thing behind a waterfall (about one fall's hollow in 160, {@code RiverNetwork.findFor}): a chest of
 * vanilla's buried treasure, and in it a note from the kids who hid it. Owner, 2026-10-05: "chest of goodies really
 * rare — needs a goonies-esque note in there." The note is our own words, not the film's.
 */
public final class HiddenTreasure {

    private HiddenTreasure() {
    }

    private static final String TABLE = "minecraft:chests/buried_treasure";

    private static final List<String> NOTE = List.of(
            "If you are reading this, you looked where nobody ever looks: behind the falls. That makes you one of us.",
            "We hid what we found down here, where the river sings. Take it. You earned it. Just never tell the "
                    + "grown-ups where the water is hollow.",
            "Cross our hearts: we never give up, and we never leave a friend behind.\n\n- the Riverbank Kids");

    public static void place(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z, Odds odds) {
        chunk.setBlock(x, y, z, Material.CHEST, BlockFace.NORTH);
        var block = chunk.getActualBlock(x, y, z);
        // the region, not the chunk: a block placed while generating is a stub until the region makes it real
        var entity = block.getLevel().getBlockEntity(block.getPos());
        if (entity == null)
            return;
        if (!(entity instanceof Container container) || !(block.getLevel() instanceof WorldGenLevel level))
            return;
        // rolled now, not left as a table for the first opening: a chest still holding an unrolled table saves no
        // items, so the note put in beside it was lost on the first save
        int middle = container.getContainerSize() / 2;
        container.setItem(middle, WrittenNote.book("Behind the Water", "the Riverbank Kids", NOTE));
        LootTable table = Loot.table(level, Loot.parse(TABLE));
        if (table == null)
            return;
        var params = new LootParams.Builder(level.getLevel())
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(block.getPos()))
                .create(LootContextParamSets.CHEST);
        for (var stack : Loot.roll(table, params, odds.getRandomLong())) {
            for (int tries = 0; tries < 8; tries++) {
                int slot = odds.getRandomInt(container.getContainerSize());
                if (container.getItem(slot).isEmpty()) {
                    container.setItem(slot, stack);
                    break;
                }
            }
        }
    }
}
