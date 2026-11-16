package me.daddychurchill.CityWorld.Support;

import java.util.List;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.Util;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/**
 * {@code cityworld:linked_table}: a global loot modifier that rolls another loot table into the loot being made —
 * the way to put vanilla's chest loot, <i>and every mod's additions to it</i>, into CityWorld's containers (the
 * LootLinker add-on is a set of these; LOOT.md "Linking vanilla loot").
 *
 * <p>Why not a {@code minecraft:loot_table} entry in a CityWorld table, or NeoForge's {@code neoforge:add_table}: both
 * roll the other table <i>raw</i>, and a mod adds its items to vanilla's tables with loot modifiers, which run only
 * when a table is rolled as itself (Farmer's Delight, Alex's Caves, Pam's, Railcraft, Useful Food: every loot mod in
 * the owner's instances, 2026-11-16). This rolls it as itself, so its modifiers run under its own id.</p>
 *
 * <pre>
 * { "type": "cityworld:linked_table",
 *   "conditions": [],
 *   "targets": [ "cityworld:chests/mine" ],   // the tables it adds to (empty: any the conditions allow)
 *   "table": "minecraft:chests/abandoned_mineshaft",
 *   "chance": 0.4,          // optional, default 1: how often the linked table is rolled at all
 *   "max_stacks": 2 }       // optional, default 2: at most this many of its stacks, picked at random (a drawer is small)
 * </pre>
 *
 * Only a container's roll (one with an origin) is linked. A linked roll never links again, so a pack cannot loop.
 * {@code targets} is there so one file reads the same on every loader: the condition that names a table is
 * {@code forge:loot_table_id} on 1.20.1, {@code neoforge:loot_table_id} after it, and 26.3 writes a condition's
 * key as {@code "type"} instead of {@code "condition"}, but this list is CityWorld's own.
 */
public final class LinkedTableLoot extends LootModifier {

    public static final Codec<LinkedTableLoot> CODEC = RecordCodecBuilder.create(i -> codecStart(i).and(i.group(
            ResourceLocation.CODEC.listOf().optionalFieldOf("targets", List.of()).forGetter(m -> m.targets),
            ResourceLocation.CODEC.fieldOf("table").forGetter(m -> m.table),
            Codec.floatRange(0f, 1f).optionalFieldOf("chance", 1f).forGetter(m -> m.chance),
            Codec.intRange(1, 64).optionalFieldOf("max_stacks", 2).forGetter(m -> m.maxStacks)))
            .apply(i, LinkedTableLoot::new));

    private static final ThreadLocal<Boolean> LINKING = ThreadLocal.withInitial(() -> false);

    private final List<ResourceLocation> targets;
    private final ResourceLocation table;
    private final float chance;
    private final int maxStacks;

    public LinkedTableLoot(LootItemCondition[] conditions, List<ResourceLocation> targets, ResourceLocation table,
            float chance, int maxStacks) {
        super(conditions);
        this.targets = List.copyOf(targets);
        this.table = table;
        this.chance = chance;
        this.maxStacks = maxStacks;
    }

    @Override
    protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        if (LINKING.get() || !context.hasParam(LootContextParams.ORIGIN)
                || (!targets.isEmpty() && !targets.contains(context.getQueriedLootTableId()))
                || context.getRandom().nextFloat() >= chance)
            return loot;
        LootTable linked = context.getResolver().getLootTable(table);
        if (linked == LootTable.EMPTY)
            return loot; // a table from a mod that is not installed: nothing to add
        ObjectArrayList<ItemStack> extra = roll(linked, context);
        Util.shuffle(extra, context.getRandom());
        for (int k = 0; k < extra.size() && k < maxStacks; k++)
            loot.add(extra.get(k));
        return loot;
    }

    /** {@code table} rolled as itself (its own loot modifiers run) for the container {@code context} is filling. */
    public static ObjectArrayList<ItemStack> roll(LootTable table, LootContext context) {
        Vec3 origin = context.getParam(LootContextParams.ORIGIN);
        Entity opener = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        LootParams params = new LootParams.Builder(context.getLevel())
                .withParameter(LootContextParams.ORIGIN, origin)
                .withOptionalParameter(LootContextParams.THIS_ENTITY, opener)
                .withLuck(context.getLuck())
                .create(LootContextParamSets.CHEST);
        LINKING.set(true);
        try {
            // 1.20.1 has no roll that takes the caller's random: seed this one from it
            return table.getRandomItems(params, context.getRandom().nextLong());
        } finally {
            LINKING.set(false);
        }
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return CODEC;
    }
}
