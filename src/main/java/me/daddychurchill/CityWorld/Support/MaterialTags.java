package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/**
 * Resolves block tags into CityWorld {@link Material} palettes.
 *
 * <p><b>Why tags at all.</b> The palettes used to be runs of hand-written {@code Material} constants,
 * which meant every block Mojang shipped after 1.14 was invisible to the generator until someone
 * edited Java and cut a release — CityWorld was still building houses out of the six 1.14 wood types
 * long after there were twelve. A tag is a *name for a set*, resolved from data at world time, so
 * {@code #minecraft:planks} means "whatever counts as planks in this game" whether that is 1.21.11,
 * 26.2, or 1.21.11-plus-a-mod-pack. New vanilla blocks arrive for free; modded ones arrive because
 * mods tag their blocks by convention.
 *
 * <p><b>Determinism is the trap here.</b> Tag contents come from datapack load and their iteration
 * order is not guaranteed stable between loads, versions or packs — but every material choice in
 * CityWorld is made by indexing a list with a seeded {@link Odds}. An unstable order would mean the
 * same seed grew a different city each time the world was loaded, which is precisely the bug this
 * generator exists to not have. So every resolved pool is <b>sorted by registry id</b> before it is
 * ever indexed. Two worlds with the same seed and the same loaded tags always agree.
 *
 * <p><b>Resolution timing.</b> Tags bind to {@link BuiltInRegistries} during the datapack reload that
 * precedes world load, and the per-world {@code CityWorldGenerator} is built lazily on first chunk
 * generation — so by the time anything here runs, tags are bound. The pools are resolved once and
 * cached for the life of the world context, which does mean a {@code /reload} does not retune a
 * running world's palettes. That matches how the rest of the per-world settings behave.
 */
public final class MaterialTags {

    private MaterialTags() {
    }

    /** CityWorld's own palette tags — the indirection modpack compatibility packs hook into. */
    public static final TagKey<Block> BUILD_PLANKS = key("cityworld:build/planks");
    public static final TagKey<Block> BUILD_WOOL = key("cityworld:build/wool");
    public static final TagKey<Block> BUILD_TERRACOTTA = key("cityworld:build/terracotta");
    public static final TagKey<Block> BUILD_GLAZED_TERRACOTTA = key("cityworld:build/glazed_terracotta");
    public static final TagKey<Block> BUILD_CONCRETE = key("cityworld:build/concrete");
    public static final TagKey<Block> BUILD_CONCRETE_POWDER = key("cityworld:build/concrete_powder");

    /** What industrial tanks and silos hold: concrete powders, water, lava — and whatever fluids a
     *  mod tags in (see PALETTES.md; Mekanism ids to be added once verified against its jar). */
    public static final TagKey<Block> BUILD_CHEMICALS = key("build/chemicals");
    public static final TagKey<Block> BUILD_STAINED_GLASS = key("cityworld:build/stained_glass");
    public static final TagKey<Block> BUILD_MODERN_STONES = key("cityworld:build/modern_stones");

    /**
     * What a farm field can be planted with, and what a flower bed can draw from.
     *
     * <p>The crop pool is the seam Farmer's Delight and friends were always going to need: farm fields
     * used to be a fixed {@code CropType} enum feeding a switch, so a mod's crops could never appear
     * however well it tagged them. Vanilla has no "is a crop" tag to borrow — {@code #minecraft:crops}
     * does not exist — so this is ours.
     *
     * <p>Ships with the four vanilla crops plus a few Farmer's Delight ids marked
     * {@code "required": false}, which cost nothing while that mod has no build for our versions.
     */
    public static final TagKey<Block> FARM_CROPS = key("cityworld:farm/crops");
    public static final TagKey<Block> FARM_FLOWERS = key("cityworld:farm/flowers");
    public static final TagKey<Block> FARM_TALL_FLOWERS = key("cityworld:farm/tall_flowers");

    /** A block tag key from a namespaced id, e.g. {@code "minecraft:planks"} or {@code "c:stones"}. */
    public static TagKey<Block> key(String id) {
        return TagKey.create(Registries.BLOCK, Identifier.parse(id));
    }

    /**
     * The blocks in {@code tag}, as materials, <b>sorted by registry id</b> so the order is stable
     * across loads (see the class note on determinism).
     *
     * <p>An unbound or empty tag yields an empty list rather than throwing: a palette that loses its
     * contents should fall back to whatever the call site defaults to, not stop the world generating.
     * That tolerance is also what lets one palette definition serve several Minecraft versions — a
     * block that does not exist yet simply isn't in the tag.
     */
    public static List<Material> resolve(TagKey<Block> tag) {
        List<Block> blocks = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag))
            blocks.add(holder.value());

        if (blocks.isEmpty()) {
            CityWorldMod.LOGGER.warn("CityWorld: block tag #{} is empty or unbound; "
                    + "palettes using it will fall back to their defaults", tag.location());
            return List.of();
        }

        // Sort by id, not by tag order — see the class note. Comparing the string form keeps
        // namespaced ids (a mod's blocks) grouped and ordered predictably alongside vanilla's.
        blocks.sort(Comparator.comparing(block -> BuiltInRegistries.BLOCK.getKey(block).toString()));

        List<Material> materials = new ArrayList<>(blocks.size());
        for (Block block : blocks)
            materials.add(Material.of(block));
        return materials;
    }
}
