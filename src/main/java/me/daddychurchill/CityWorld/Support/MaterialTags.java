package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
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
    public static final TagKey<Block> BUILD_CHEMICALS = key("cityworld:build/chemicals");
    public static final TagKey<Block> BUILD_STAINED_GLASS = key("cityworld:build/stained_glass");
    public static final TagKey<Block> BUILD_MODERN_STONES = key("cityworld:build/modern_stones");
    /** The End's own building blocks, blended into the build pools of a CityWorld End; see {@code MaterialProvider}. */
    public static final TagKey<Block> BUILD_END_STONES = key("cityworld:build/end_stones");

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

    /**
     * Fittings — the joinery a build is finished with: doors, trapdoors, windows, fences, roofs.
     *
     * <p>These exist for Macaw's (doors, trapdoors, windows, fences, roofs — and its Biomes O' Plenty
     * add-on, which pours BoP woods into the same family tags), but each is a plain block tag, so any
     * mod or datapack joins the same way. Every pool ships seeded with vanilla where vanilla has the
     * thing (wooden doors, wooden trapdoors, wooden fences); the ones vanilla cannot fill — sloped
     * roof blocks, framed windows — ship EMPTY, and every caller falls back to what it always built
     * (full-block or stair roofs, plain glass). An empty fittings pool is therefore not a fault and
     * is logged at INFO like the furniture roles, not WARN like a build palette.
     *
     * <p>The four door pools are by <em>use</em>, not by look: a house or office front door, an
     * interior door between rooms, a shop front (glass, sliding), and the metal doors of factories,
     * warehouses and bunkers. Macaw's ships a family tag per style (cottage, barn, modern, metal…) and
     * the pools reference those tags, so a Macaw's update that adds a wood joins by itself.
     */
    public static final TagKey<Block> FITTINGS_DOOR = key("cityworld:fittings/door");
    public static final TagKey<Block> FITTINGS_INTERIOR_DOOR = key("cityworld:fittings/interior_door");
    public static final TagKey<Block> FITTINGS_STORE_DOOR = key("cityworld:fittings/store_door");
    public static final TagKey<Block> FITTINGS_INDUSTRIAL_DOOR = key("cityworld:fittings/industrial_door");
    public static final TagKey<Block> FITTINGS_TRAPDOOR = key("cityworld:fittings/trapdoor");
    /** Framed window blocks that stand in for the glass of a house or an office wall (thin, centred,
     *  oriented along the wall by {@code Material.withFaces}). Ships empty: vanilla glass is the fallback. */
    public static final TagKey<Block> FITTINGS_WINDOW = key("cityworld:fittings/window");
    /**
     * Fences, split three ways by where they stand — the owner's call after one look at barbed wire round
     * a park and a stockade round a paddock: {@code fence} is the garden fence of a house railing or a park
     * edge (picket, hedge), {@code farm_fence} the paddock and barn-pen fence (horse, stockade),
     * {@code site_fence} the tall one round a construction site or a factory yard (iron bars, barbed wire).
     * A park or a farm picks ONE fence for the whole platmap (the macro odds), not one per chunk — a
     * four-chunk park had a different fence on each side.
     */
    public static final TagKey<Block> FITTINGS_FENCE = key("cityworld:fittings/fence");
    public static final TagKey<Block> FITTINGS_FARM_FENCE = key("cityworld:fittings/farm_fence");
    public static final TagKey<Block> FITTINGS_SITE_FENCE = key("cityworld:fittings/site_fence");
    /** The balcony rail of the rare building whose "glass" is bars: iron bars + Macaw's metal fences. */
    public static final TagKey<Block> FITTINGS_RAILING = key("cityworld:fittings/railing");
    /** One-block stair treads for a house's staircase (Macaw's compact and terrace stairs: a full riser per
     *  block, nothing needed underneath). The matching railing, platform and balcony are found by name from
     *  the tread ({@code oak_compact_stairs} → {@code oak_railing}, {@code oak_platform}, {@code oak_balcony}).
     *  Ships empty: the vanilla stair run with its under-steps is the fallback. */
    public static final TagKey<Block> FITTINGS_STAIRS = key("cityworld:fittings/stairs");
    /** The gate in a paddock, pen or zoo fence — vanilla fence gates + Macaw's single-block gates (its
     *  two-tall double gates are left out). Picked with the same odds as the fence, so the pair agrees. */
    public static final TagKey<Block> FITTINGS_GATE = key("cityworld:fittings/gate");
    /** A garage door for a factory or warehouse bay: one block id, a column of {@code part=middle} under a
     *  {@code part=top}, three columns wide. Ships empty: the metal door is the fallback. */
    public static final TagKey<Block> FITTINGS_GARAGE_DOOR = key("cityworld:fittings/garage_door");
    /** Stair-shaped sloped roof blocks (Macaw's {@code *_roof}); the house roof pass matches one to the
     *  roof material by name, else picks at random, else uses the vanilla stairs of that material. */
    public static final TagKey<Block> FITTINGS_ROOF = key("cityworld:fittings/roof");

    /** Stackable street-lamp posts: one block id placed four high, the mod deriving base/middle/top
     *  from the stack ({@code RoadLot.generateLightPost}). Ships empty: the fence-and-glowstone post is
     *  the fallback. */
    public static final TagKey<Block> LIGHT_STREET_LAMP = key("cityworld:light/street_lamp");
    /** Two-tall tiki torches round a campfire (stacked, the mod deriving bottom/top). Ships empty. */
    public static final TagKey<Block> LIGHT_TIKI = key("cityworld:light/tiki");
    /** One-block garden lights on the gate posts of a park entrance. Ships empty. */
    public static final TagKey<Block> LIGHT_GARDEN = key("cityworld:light/garden");

    /** A block tag key from a namespaced id, e.g. {@code "minecraft:planks"} or {@code "c:stones"}. */
    public static TagKey<Block> key(String id) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.parse(id));
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
        return CACHE.computeIfAbsent(tag, MaterialTags::load);
    }

    /**
     * One block from {@code tag}, chosen by {@code odds}, or {@code fallback} when nothing supplies
     * the tag. The way every fittings pool is consumed: a caller that always built a birch door keeps
     * building one until a datapack or a mod says otherwise, and the choice is as deterministic as
     * the pool order (sorted by id) and the caller's {@link Odds}.
     */
    public static Material pick(TagKey<Block> tag, Odds odds, Material fallback) {
        List<Material> pool = resolve(tag);
        return pool.isEmpty() ? fallback : pool.get(odds.getRandomInt(pool.size()));
    }

    /**
     * The pool entry whose registry path is {@code path} (any namespace), or {@code null} — how a
     * roof block is matched to the wall it caps ({@code oak_planks} → {@code mcwroofs:oak_planks_roof}).
     */
    public static Material named(TagKey<Block> tag, String path) {
        for (Material material : resolve(tag))
            if (BuiltInRegistries.BLOCK.getKey(material.getBlock()).getPath().equals(path))
                return material;
        return null;
    }

    /**
     * The sibling of a pooled stair tread — {@code oak_compact_stairs} → {@code oak_railing} for
     * {@code "railing"}, or {@code oak_platform}, {@code oak_balcony} — found by name in the tread's namespace, or
     * null when the mod has none. How one pick from {@code fittings/stairs} brings its whole kit.
     */
    public static Material stairPart(Material tread, String kind) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(tread.getBlock());
        String path = id.getPath();
        int cut = path.indexOf("_compact_stairs") >= 0 ? path.indexOf("_compact_stairs")
                : path.indexOf("_terrace_stairs") >= 0 ? path.indexOf("_terrace_stairs") : path.lastIndexOf("_stairs");
        if (cut < 0)
            return null;
        Material part = Material.of(id.getNamespace() + ":" + path.substring(0, cut) + "_" + kind);
        return part == Material.AIR ? null : part;
    }

    /**
     * Resolved pools, one per tag. Tags bind once per datapack load and the block registry is fixed,
     * so a pool cannot change between {@link #invalidate()} calls — and it is asked for constantly:
     * every furniture {@code pick} on every chunk used to re-walk the registry, re-merge the runtime
     * sets and re-sort, then (on a world with no furniture mod, which is most of them) log a WARN
     * for the empty result. One client session produced 94,000 of those lines. Now each pool is
     * built once and an empty one is mentioned once.
     */
    private static final java.util.concurrent.ConcurrentHashMap<TagKey<Block>, List<Material>> CACHE =
            new java.util.concurrent.ConcurrentHashMap<>();

    /** Forget every resolved pool — called when tags are (re)bound, so a datapack change is honoured. */
    public static void invalidate() {
        CACHE.clear();
    }

    private static List<Material> load(TagKey<Block> tag) {
        List<Block> blocks = new ArrayList<>();
        for (Holder<Block> holder : BuiltInRegistries.BLOCK.getTagOrEmpty(tag))
            blocks.add(holder.value());
        // furniture sets recognised at runtime (no tag file needed) join the same pool, once each
        for (Block block : FurnitureSets.extra(tag))
            if (!blocks.contains(block))
                blocks.add(block);

        if (blocks.isEmpty()) {
            String path = tag.location().getPath();
            if (path.startsWith("furniture/") || path.startsWith("fittings/") || path.startsWith("light/")) {
                // Expected on any world without a furniture mod: every furniture role is optional and
                // every caller falls back to the vanilla-block furniture it always built. Not a warning.
                CityWorldMod.LOGGER.info("CityWorld: no mod supplies #{} — the vanilla fallback will be used for it",
                        tag.location());
            } else {
                // A build or farm palette with nothing in it IS a fault worth a WARN: the tag file is
                // missing, or a required reference emptied the whole tag (see PORTING.md).
                CityWorldMod.LOGGER.warn("CityWorld: block tag #{} is empty or unbound; "
                        + "palettes using it will fall back to their defaults", tag.location());
            }
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
