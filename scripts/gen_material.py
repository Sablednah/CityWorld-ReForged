#!/usr/bin/env python3
"""
Phase 2 codegen: generate me.daddychurchill.CityWorld.compat.Material.

Partitions every `Material.X` constant referenced by the original Bukkit source into:
  - block constants   -> of(Blocks.X)      (name exists as a modern Blocks field)
  - legacy/renamed    -> of(Blocks.<new>)  (hand-mapped table below)
  - item-only         -> ofItem(Items.X)   (name exists as a modern Items field only)

Run:  python3 gen_material.py
"""
import glob
import pathlib
import re
import subprocess
import sys
import tempfile
import zipfile

# Repo root (this script lives in <repo>/scripts/).
REPO = pathlib.Path(__file__).resolve().parent.parent
# Bootstrap cache lives outside the repo so it is never committed.
SCRATCH = pathlib.Path(tempfile.gettempdir()) / "cityworld-portgen"
REF = SCRATCH / "bukkit-ref/src"
MCSRC = SCRATCH / "mcsrc"
OUT = REPO / "src/main/java/me/daddychurchill/CityWorld/compat/Material.java"

# A file that only ever existed in the Bukkit tree — used to locate the pre-port commit.
SENTINEL = "src/me/daddychurchill/CityWorld/Support/AbstractBlocks.java"
MC_FILES = ["net/minecraft/world/level/block/Blocks.java", "net/minecraft/world/item/Items.java"]


def git(*args):
    return subprocess.run(["git", "-C", str(REPO), *args],
                          capture_output=True, text=True, check=True).stdout.strip()


def ensure_bukkit_ref():
    """Restore the original Bukkit source from git history (it's not in the working tree)."""
    if REF.is_dir():
        return
    # The commit that DELETED the Bukkit tree; its parent still has it.
    deleted_in = git("log", "--diff-filter=D", "--format=%H", "-1", "--", SENTINEL)
    if not deleted_in:
        sys.exit(f"Could not find the commit that removed {SENTINEL}")
    ref = f"{deleted_in}~1"
    REF.parent.mkdir(parents=True, exist_ok=True)
    print(f"bootstrapping Bukkit reference tree from {ref[:12]}")
    archive = subprocess.run(["git", "-C", str(REPO), "archive", ref, "src"],
                             capture_output=True, check=True).stdout
    tar = SCRATCH / "_ref.tar"
    tar.write_bytes(archive)
    subprocess.run(["tar", "-xf", str(tar), "-C", str(REF.parent)], check=True)
    tar.unlink()


def ensure_mc_sources():
    """Extract the decompiled Blocks/Items sources from the NeoForm cache."""
    if all((MCSRC / f).exists() for f in MC_FILES):
        return
    jars = glob.glob(str(pathlib.Path.home() /
                         ".gradle/caches/neoformruntime/intermediate_results/"
                         "sourcesAndCompiledWithNeoForge_*_output.jar"))
    if not jars:
        sys.exit("NeoForm sources jar not found — run ./gradlew build first.")
    MCSRC.mkdir(parents=True, exist_ok=True)
    print(f"bootstrapping MC sources from {pathlib.Path(jars[0]).name}")
    with zipfile.ZipFile(jars[0]) as z:
        for f in MC_FILES:
            z.extract(f, MCSRC)

# 1.14 Bukkit name -> modern Blocks field. Names that no longer exist in either registry.
# (Most of these survive only in commented-out code; mapped anyway so they can't bite later.)
LEGACY = {
    "BED_BLOCK":          ("WHITE_BED",         "generic bed -> a concrete colour"),
    "CARPET":             ("WHITE_CARPET",      "generic carpet -> a concrete colour"),
    "DOUBLE_STEP":        ("SMOOTH_STONE",      "legacy double stone slab -> the full block"),
    "ENCHANTMENT_TABLE":  ("ENCHANTING_TABLE",  "renamed"),
    "ENDER_PORTAL_FRAME": ("END_PORTAL_FRAME",  "renamed"),
    "GRASS":              ("SHORT_GRASS",       "1.14 GRASS was the plant; renamed in 1.20.3"),
    "GRASS_PATH":         ("DIRT_PATH",         "renamed"),
    "IRON_DOOR_BLOCK":    ("IRON_DOOR",         "legacy block name"),
    "LONG_GRASS":         ("TALL_GRASS",        "legacy tall-grass name"),
    "QUARTZ_ORE":         ("NETHER_QUARTZ_ORE", "renamed"),
    "SIGN":               ("OAK_SIGN",          "generic sign -> oak"),
    "TRAP_DOOR":          ("OAK_TRAPDOOR",      "legacy generic trapdoor -> oak"),
    "WOOD_DOOR":          ("OAK_DOOR",          "legacy generic wooden door -> oak"),
    "WOOD_STEP":          ("OAK_SLAB",          "legacy generic wooden slab -> oak"),
}

# Modern blocks the 1.14 vocabulary never had, so they appear nowhere in the Bukkit source and
# referenced_names() cannot find them — but the port needs them anyway. Added here rather than by
# hand-editing Material.java (which is generated). Each must exist as a modern Blocks field.
EXTRAS = {
    "DEEPSLATE": "P4: the deep stratum below y=0; 1.14's world bottomed out at stone",
    "BLUE_ICE": "P8 MODERN: glacier-blue ice for the highest peaks; 1.14 had none",
    "POWDER_SNOW": "P8 MODERN: powder-snow pockets on snowy slopes; a 1.17 block",
    "MUD": "P8 MODERN: muddy bottoms for carved swamp/mangrove pools; a 1.19 block",
    "RED_SAND": "P8 MODERN: badlands surface; the 1.14 Bukkit source never referenced it",
    "WALL_TORCH": "P9: wall-mounted torches for mine corridors; the Bukkit source only used standing TORCH",
    # P9 mine dress-up: deepslate ore variants, copper, ancient debris, chains, lanterns
    "DEEPSLATE_COAL_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_IRON_ORE": "P9: deepslate ore variant for deep mines",
    "COPPER_ORE": "P9: copper ore in mine walls",
    "DEEPSLATE_COPPER_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_GOLD_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_REDSTONE_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_LAPIS_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_DIAMOND_ORE": "P9: deepslate ore variant for deep mines",
    "DEEPSLATE_EMERALD_ORE": "P9: deepslate ore variant for deep mines",
    "ANCIENT_DEBRIS": "P9: rare prize in the deepest mines (the reason they dug so far)",
    "IRON_CHAIN": "P9: chains in mine lift shafts (1.21 renamed CHAIN -> IRON_CHAIN)",
    "LANTERN": "P9: mine lighting",
    # P9 copper-theme mine (the 1.21.9 "copper age" set). Weathers by depth: fresh near the
    # surface -> exposed -> weathered -> oxidized in the deepest shafts. These are plain Block
    # fields; the chain/bars families are WeatheringCopperBlocks records and live in EXTRAS_EXPR.
    "COPPER_TORCH": "P9 copper mine: standing copper torch (green flame)",
    "COPPER_WALL_TORCH": "P9 copper mine: wall-mounted copper torch",
    "CUT_COPPER": "P9 copper mine: worked-metal support beam (fresh)",
    "EXPOSED_CUT_COPPER": "P9 copper mine: worked-metal support beam (exposed)",
    "WEATHERED_CUT_COPPER": "P9 copper mine: worked-metal support beam (weathered)",
    "OXIDIZED_CUT_COPPER": "P9 copper mine: worked-metal support beam (oxidized, deepest)",
    "COPPER_GRATE": "P9 copper mine: cage/machinery grate (fresh)",
    "EXPOSED_COPPER_GRATE": "P9 copper mine: cage/machinery grate (exposed)",
    "WEATHERED_COPPER_GRATE": "P9 copper mine: cage/machinery grate (weathered)",
    "OXIDIZED_COPPER_GRATE": "P9 copper mine: cage/machinery grate (oxidized, deepest)",
    "COPPER_CHEST": "P9 copper mine: loot chest, copper-age flavour (fresh)",
    "EXPOSED_COPPER_CHEST": "P9 copper mine: loot chest (exposed)",
    "WEATHERED_COPPER_CHEST": "P9 copper mine: loot chest (weathered)",
    "OXIDIZED_COPPER_CHEST": "P9 copper mine: loot chest (oxidized, deepest)",
    "GLOW_LICHEN": "P9 mine: glow lichen creeping the abandoned walls, thicker with depth",
    # P9 mine props — miners' camp clutter dropped along the corridor ledge opposite the track
    "BLAST_FURNACE": "P9 mine prop: miners' camp",
    "SMOKER": "P9 mine prop: miners' camp",
    "STONECUTTER": "P9 mine prop: miners' camp",
    "SMITHING_TABLE": "P9 mine prop: miners' camp",
    "BARREL": "P9 mine prop: miners' camp storage",
    "GRINDSTONE": "P9 mine prop: miners' camp",
    "ANVIL": "P9 mine prop: miners' camp",
    "CARTOGRAPHY_TABLE": "P9 mine prop: miners' camp (drawing up plans)",
    "DECORATED_POT": "P9 mine prop: miners' camp clutter",
    "SOUL_CAMPFIRE": "P9 mine prop: miners' camp fire (soul)",
    "BELL": "P9 mine prop: miners' signal bell",
    "SOUL_LANTERN": "P9 mine prop/light: soul lantern",
    "OAK_HANGING_SIGN": "P9 mine entrance: the named-mine headframe sign",
    # P10 overgrowth: nature reclaiming buildings/roads (a MODERN "overgrown" pass that runs AFTER
    # decay so the plant life isn't itself decayed). Post-1.8 blocks that read as reclamation.
    "MOSS_BLOCK": "P10 overgrowth: moss cushion on ground/floors",
    "MOSS_CARPET": "P10 overgrowth: moss creeping across floors and roofs",
    "PALE_MOSS_BLOCK": "P10 overgrowth: pale (grey) moss cushion",
    "PALE_MOSS_CARPET": "P10 overgrowth: pale moss carpet",
    "PALE_HANGING_MOSS": "P10 overgrowth: pale moss hanging from overhangs",
    "LEAF_LITTER": "P10 overgrowth: fallen leaves littering floors (1.21.4 multiface)",
    "MOSSY_STONE_BRICKS": "P10 overgrowth: mossy swap for stone-brick walls",
    "AZALEA": "P10 overgrowth: azalea bush reclaiming corners",
    "FLOWERING_AZALEA": "P10 overgrowth: flowering azalea bush",
    "AZALEA_LEAVES": "P10 overgrowth: azalea foliage for small reclaim bushes/trees",
    "HANGING_ROOTS": "P10 overgrowth: roots dangling from ceilings/overhangs",
    "SPORE_BLOSSOM": "P10 overgrowth: spore blossom on ceilings",
    "SMALL_DRIPLEAF": "P10 overgrowth: dripleaf in damp corners",
    "BIG_DRIPLEAF": "P10 overgrowth: big dripleaf",
    "CAVE_VINES": "P10 overgrowth: glow-berry vines hanging from ledges",
    "PINK_PETALS": "P10 overgrowth: petals scattered on the ground",
    "SHORT_GRASS": "P10 overgrowth: grass tufts pushing through",
    "DRIPSTONE_BLOCK": "P10 overgrowth: dripstone clumps underground",
    "POINTED_DRIPSTONE": "P10 overgrowth: stalactites/stalagmites in mines and caves",
    # P11 shops: villager job-site blocks that mark a themed shop's trade (MODERN). Most job blocks
    # (blast_furnace, smoker, stonecutter, cartography_table, smithing_table, barrel, grindstone,
    # cauldron, brewing_stand) already live above as mine props; these four are the stragglers.
    "FLETCHING_TABLE": "P11 shop: fletcher's job block",
    "LOOM": "P11 shop: draper/tailor's job block",
    "COMPOSTER": "P11 shop/farm: greengrocer/farmer job block",
    "LECTERN": "P11 shop: newsagent/bookshop job block",
    "LILY_PAD": "P11 farm: lily pads on the rare fish-pond farm variant",
    # P12 interiors: modern furniture/decoration vocabulary (MODERN only). Clever-block-trick accents
    # plus the post-1.8 decorative palette woven into furnished rooms.
    "CHISELED_BOOKSHELF": "P12 interior: studies/libraries",
    "CANDLE": "P12 interior: tabletop/shelf candle accent",
    "WHITE_CANDLE": "P12 interior: candle accent (white)",
    "ORANGE_CANDLE": "P14 interior: candle accent (warm colour)",
    "LIGHT_GRAY_CANDLE": "P14 interior: candle accent (neutral colour)",
    "RED_CANDLE": "P14 interior: candle accent (warm colour)",
    "CANDLE_CAKE": "P14 interior: lit-cake accent on a table",
    "LIGHTNING_ROD": "P14 rooftop: lightning rod on MODERN building roofs",
    "COPPER_BULB": "P12 interior: warm modern light source",
    "CHISELED_COPPER": "P12 interior: decorative modern wall/counter block",
    "CHISELED_TUFF": "P12 interior: decorative modern stone block",
    "AMETHYST_CLUSTER": "P12 interior: sparkle accent",
    "POTTED_FERN": "P12 interior: potted plant accent",
    "POTTED_AZALEA": "P12 interior: potted plant accent",
    "POTTED_BAMBOO": "P12 interior: potted plant accent",
    "SMOOTH_QUARTZ": "P12 interior: clean modern counter/surface",
    "SMOOTH_QUARTZ_SLAB": "P12 interior: modern counter/coffee-table top",
    "OAK_WALL_HANGING_SIGN": "P12 shop: exterior shopfront sign above the door",
    # P12 MODERN building stones — plain STONE gets peppered by vanilla's ore pass on build chunks, so
    # MODERN builds swap it for these decorative stones (none are in the ore-replaceables tag).
    "BLACKSTONE": "P12 MODERN build stone",
    "POLISHED_BLACKSTONE": "P12 MODERN build stone",
    "POLISHED_BLACKSTONE_BRICKS": "P12 MODERN build stone",
    "CHISELED_POLISHED_BLACKSTONE": "P12 MODERN build stone",
    "CRACKED_POLISHED_BLACKSTONE_BRICKS": "P12 MODERN build stone",
    "GILDED_BLACKSTONE": "P12 MODERN build stone",
    "POLISHED_ANDESITE": "P12 MODERN build stone",
    "POLISHED_DIORITE": "P12 MODERN build stone",
    "POLISHED_GRANITE": "P12 MODERN build stone",
    "MUD_BRICKS": "P12 MODERN build stone",
    "RESIN_BRICKS": "P12 MODERN build stone",
    "BASALT": "P12 MODERN build stone",
    "POLISHED_BASALT": "P12 MODERN build stone",
    "SMOOTH_BASALT": "P12 MODERN build stone",
    "COPPER_BLOCK": "P12 MODERN build stone (weathers over time)",
    "CUT_COPPER": "P12 MODERN build stone (weathers over time)",
    "EXPOSED_CUT_COPPER": "P12 MODERN build stone",
    "WEATHERED_CUT_COPPER": "P12 MODERN build stone",
    "OXIDIZED_CUT_COPPER": "P12 MODERN build stone",
    # P13 zoo + biodome: themed enclosure ground and biome-fill blocks
    "BAMBOO": "P13 zoo: bamboo for the panda enclosure / jungle biodome",
    "BAMBOO_BLOCK": "P13 zoo: bamboo trunk",
    "BEE_NEST": "P13 zoo: bee enclosure / flower-forest biodome",
    "COCOA": "P13 biodome: jungle cocoa pods",
    "MANGROVE_LOG": "P13 biodome: swamp",
    "MANGROVE_LEAVES": "P13 biodome: swamp",
    "MANGROVE_ROOTS": "P13 biodome: swamp",
    "MUDDY_MANGROVE_ROOTS": "P13 biodome: swamp",
    "PALE_OAK_LOG": "P13 biodome: pale garden",
    "PALE_OAK_WOOD": "P13 biodome: pale garden",
    "PALE_OAK_LEAVES": "P13 biodome: pale garden",
    "CREAKING_HEART": "P13 biodome: pale garden",
    "OPEN_EYEBLOSSOM": "P13 biodome: pale garden flower",
    "CRIMSON_STEM": "P13 biodome: nether",
    "WARPED_STEM": "P13 biodome: nether",
    "NETHER_WART_BLOCK": "P13 biodome: nether",
    "WARPED_WART_BLOCK": "P13 biodome: nether",
    "CRIMSON_FUNGUS": "P13 biodome: nether",
    "WARPED_FUNGUS": "P13 biodome: nether",
    "CRIMSON_NYLIUM": "P13 biodome: nether ground",
    "WARPED_NYLIUM": "P13 biodome: nether ground",
    "SHROOMLIGHT": "P13 biodome: nether light",
    "CHORUS_PLANT": "P13 biodome: end",
    "CHORUS_FLOWER": "P13 biodome: end",
    "FLOWERING_AZALEA_LEAVES": "P13 biodome: flower forest / jungle canopy",
    # P12 interior furniture: the toilet flush handle (a wall button on the cistern)
    "BIRCH_BUTTON": "P12 interior: toilet flush button on the cistern",
    "STONE_BUTTON": "P15 vault: the surface-hut iron-door opener (industrial button)",
}

# Copper "weathering" families are declared as `public static final WeatheringCopperBlocks NAME`
# — a record bundling the four weather stages — NOT as individual `Block` fields, so the stage
# blocks are reached through accessors (Blocks.COPPER_CHAIN.exposed()) rather than a bare field.
# Emit those via raw expressions instead of the of(Blocks.NAME) shortcut EXTRAS uses.
EXTRAS_EXPR = {
    "COPPER_CHAIN":           ("Blocks.COPPER_CHAIN.unaffected()", "P9 copper mine: lift-shaft chain (fresh)"),
    "EXPOSED_COPPER_CHAIN":   ("Blocks.COPPER_CHAIN.exposed()",    "P9 copper mine: lift-shaft chain (exposed)"),
    "WEATHERED_COPPER_CHAIN": ("Blocks.COPPER_CHAIN.weathered()",  "P9 copper mine: lift-shaft chain (weathered)"),
    "OXIDIZED_COPPER_CHAIN":  ("Blocks.COPPER_CHAIN.oxidized()",   "P9 copper mine: lift-shaft chain (oxidized, deepest)"),
    "COPPER_BARS":            ("Blocks.COPPER_BARS.unaffected()",  "P9 copper mine: cage bars (fresh)"),
    "EXPOSED_COPPER_BARS":    ("Blocks.COPPER_BARS.exposed()",     "P9 copper mine: cage bars (exposed)"),
    "WEATHERED_COPPER_BARS":  ("Blocks.COPPER_BARS.weathered()",   "P9 copper mine: cage bars (weathered)"),
    "OXIDIZED_COPPER_BARS":   ("Blocks.COPPER_BARS.oxidized()",    "P9 copper mine: cage bars (oxidized, deepest)"),
    "COPPER_LANTERN":           ("Blocks.COPPER_LANTERN.unaffected()", "P9 mine prop/light: copper lantern (fresh)"),
    "EXPOSED_COPPER_LANTERN":   ("Blocks.COPPER_LANTERN.exposed()",    "P9 mine prop/light: copper lantern (exposed)"),
    "WEATHERED_COPPER_LANTERN": ("Blocks.COPPER_LANTERN.weathered()",  "P9 mine prop/light: copper lantern (weathered)"),
    "OXIDIZED_COPPER_LANTERN":  ("Blocks.COPPER_LANTERN.oxidized()",   "P9 mine prop/light: copper lantern (oxidized)"),
}


def referenced_names():
    out = subprocess.run(
        ["grep", "-rhoE", r"Material\.[A-Z][A-Z_0-9]*", str(REF)],
        capture_output=True, text=True, check=True).stdout
    return sorted({line.split(".", 1)[1] for line in out.splitlines() if line.strip()})


def fields(java_file, decl):
    text = (MCSRC / java_file).read_text()
    return set(re.findall(r"public static final %s ([A-Z][A-Z_0-9]*)" % decl, text))


def main():
    ensure_bukkit_ref()
    ensure_mc_sources()

    names = referenced_names()
    blocks = fields("net/minecraft/world/level/block/Blocks.java", "Block")
    weathering = fields("net/minecraft/world/level/block/Blocks.java", "WeatheringCopperBlocks")
    items = fields("net/minecraft/world/item/Items.java", "Item")

    block_names, item_names, legacy_names, unknown = [], [], [], []
    for n in names:
        if n in LEGACY:
            legacy_names.append(n)
        elif n in blocks:
            block_names.append(n)
        elif n in items:
            item_names.append(n)
        else:
            unknown.append(n)

    # Fail loudly rather than silently emitting something that won't compile.
    if unknown:
        sys.exit("Unmapped names (add to LEGACY): %s" % ", ".join(unknown))
    bad = [f"{k}->{v[0]}" for k, v in LEGACY.items() if v[0] not in blocks]
    if bad:
        sys.exit("LEGACY targets missing from Blocks: %s" % ", ".join(bad))
    missing_extras = [n for n in EXTRAS if n not in blocks]
    if missing_extras:
        sys.exit("EXTRAS missing from Blocks: %s" % ", ".join(missing_extras))
    # An EXTRA that the Bukkit source turns out to reference is just a normal block constant, and
    # emitting both would not compile.
    dupe_extras = [n for n in EXTRAS if n in block_names]
    if dupe_extras:
        sys.exit("EXTRAS already emitted as block constants (drop them): %s" % ", ".join(dupe_extras))
    # EXTRAS_EXPR: validate the WeatheringCopperBlocks base field each accessor hangs off exists,
    # and that no name collides with another emitted constant.
    missing_expr = [n for n, (e, _) in EXTRAS_EXPR.items()
                    if e.split("Blocks.", 1)[1].split(".", 1)[0] not in weathering]
    if missing_expr:
        sys.exit("EXTRAS_EXPR base not a WeatheringCopperBlocks field: %s" % ", ".join(missing_expr))
    dupe_expr = [n for n in EXTRAS_EXPR if n in block_names or n in item_names
                 or n in legacy_names or n in EXTRAS]
    if dupe_expr:
        sys.exit("EXTRAS_EXPR collides with another constant (drop them): %s" % ", ".join(dupe_expr))

    lines = []
    lines.append("    // ---- Blocks (%d) — 1.14 names that map 1:1 onto a modern block --------------"
                 % len(block_names))
    for n in block_names:
        lines.append(f"    public static final Material {n} = of(Blocks.{n});")
    lines.append("")
    lines.append("    // ---- Legacy / renamed (%d) — 1.14 names with no modern equivalent ----------"
                 % len(legacy_names))
    for n in legacy_names:
        target, why = LEGACY[n]
        lines.append(f"    public static final Material {n} = of(Blocks.{target}); // {why}")
    lines.append("")
    lines.append("    // ---- Modern extras (%d) — blocks the 1.14 vocabulary never had -------------"
                 % len(EXTRAS))
    for n in sorted(EXTRAS):
        lines.append(f"    public static final Material {n} = of(Blocks.{n}); // {EXTRAS[n]}")
    lines.append("")
    lines.append("    // ---- Copper weathering stages (%d) — reached via WeatheringCopperBlocks accessors -"
                 % len(EXTRAS_EXPR))
    for n in sorted(EXTRAS_EXPR):
        expr, why = EXTRAS_EXPR[n]
        lines.append(f"    public static final Material {n} = of({expr}); // {why}")
    lines.append("")
    lines.append("    // ---- Items (%d) — Bukkit's Material spanned blocks AND items; these are ----"
                 % len(item_names))
    lines.append("    // ---- item-only (loot/chest contents). They carry no block state. -----------")
    for n in item_names:
        lines.append(f"    public static final Material {n} = ofItem(Items.{n});")

    constants = "\n".join(lines)
    OUT.write_text(TEMPLATE.replace("//__CONSTANTS__", constants))

    print(f"blocks={len(block_names)} legacy={len(legacy_names)} items={len(item_names)} "
          f"total={len(block_names)+len(legacy_names)+len(item_names)} (referenced={len(names)})")
    print(f"wrote {OUT}")


TEMPLATE = '''package me.daddychurchill.CityWorld.compat;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Port shim for {@code org.bukkit.Material}.
 *
 * <p><b>Generated — do not hand-edit the constant block.</b> It is produced by
 * {@code gen_material.py} from the constants the original Bukkit source actually references,
 * cross-checked against the modern {@link Blocks}/{@link Items} fields.
 *
 * <p>The original generator passes Bukkit {@code Material} constants around as a vocabulary
 * (never switched-on anywhere in the source), so this replaces that vocabulary with interned
 * wrappers over vanilla types; ported call sites keep their {@code Material.STONE} shape and only
 * change an import.
 *
 * <p>Like Bukkit's enum, a {@code Material} may denote a <em>block</em> or an <em>item</em>:
 * block-backed materials expose a {@link BlockState}; item-only ones (loot/chest contents) do not
 * and return {@code null} from the state accessors — see {@link #isBlock()}.
 *
 * <p>Because Bukkit encoded orientation in separate {@code BlockFace}/slab/half arguments applied
 * at placement rather than baked into the constant, a material holds a block's <em>default
 * state</em> and derives oriented states via {@link #withFacing}, {@link #withFaces},
 * {@link #asSlab} and {@link #asDoorHalf}.
 *
 * <p>Instances are interned, so {@code ==}/{@link #equals} compare block (or item) identity,
 * ignoring state — matching how the old code compared {@code Material}s.
 */
public final class Material {

    private static final ConcurrentHashMap<Block, Material> BLOCK_INTERN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Item, Material> ITEM_INTERN = new ConcurrentHashMap<>();

    private final Block block;
    private final BlockState defaultState;
    private final Item item;

    private Material(Block block) {
        this.block = block;
        this.defaultState = block.defaultBlockState();
        this.item = block.asItem();
    }

    private Material(Item item) {
        this.block = null;
        this.defaultState = null;
        this.item = item;
    }

    /** Interned wrapper for a vanilla block. */
    public static Material of(Block block) {
        return BLOCK_INTERN.computeIfAbsent(block, Material::new);
    }

    /** Interned wrapper for an item-only material (no block form). */
    public static Material ofItem(Item item) {
        return ITEM_INTERN.computeIfAbsent(item, Material::new);
    }

    /**
     * Resolve a block by (namespaced or bare) id, e.g. {@code "stone"} or {@code "minecraft:stone"}.
     * Falls back to {@link #AIR} if unknown. Requires the block registry to be loaded (true during
     * world generation); prefer the typed constants for anything referenced at class-load time.
     */
    public static Material of(String id) {
        Identifier key = id.indexOf(':') >= 0 ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        Block resolved = BuiltInRegistries.BLOCK.getValue(key);
        return of(resolved == null ? Blocks.AIR : resolved);
    }

    /** Whether this material has a block form (false for item-only materials). */
    public boolean isBlock() {
        return block != null;
    }

    /** The vanilla block, or {@code null} for item-only materials. */
    public Block getBlock() {
        return block;
    }

    /** The block's default {@link BlockState}, or {@code null} for item-only materials. */
    public BlockState getBlockState() {
        return defaultState;
    }

    /** The item form (a block's own item for block-backed materials). */
    public Item getItem() {
        return item;
    }

    /** Approximates Bukkit {@code Material.name()} — the registry path, upper-cased. */
    public String name() {
        Identifier key = block != null
                ? BuiltInRegistries.BLOCK.getKey(block)
                : BuiltInRegistries.ITEM.getKey(item);
        return key.getPath().toUpperCase(Locale.ROOT);
    }

    public boolean is(Material other) {
        if (other == null) {
            return false;
        }
        return block != null ? other.block == block : other.item == item;
    }

    /**
     * Bukkit's {@code Material.isOccluding()} — whether this is a full, opaque cube. The generator
     * uses it to decide what may be stacked on (see {@code SupportBlocks.isNonstackableBlock}).
     * Item-only materials are not occluding.
     */
    public boolean isOccluding() {
        return defaultState != null && defaultState.canOcclude();
    }

    /**
     * Bukkit's {@code Material.hasGravity()} — whether this block falls if unsupported (sand,
     * gravel, anvils, concrete powder). The building code asks before using something as an outset
     * or ceiling, since a floating slab of sand would just drop.
     *
     * <p>Bukkit answered from a flag on the material; vanilla expresses it as a block class, so this
     * asks whether the block is a {@link FallingBlock}. Item-only materials never have gravity.
     */
    public boolean hasGravity() {
        return block instanceof FallingBlock;
    }

    // ---- Orientation derivation (mirrors the old InitialBlocks BlockData logic) --------------

    /**
     * Apply a single facing, matching the old {@code Directional / MultipleFacing / Orientable}
     * fallback chain: a facing property if present, else a single connection face, else an axis.
     */
    public BlockState withFacing(BlockFace facing) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        Direction dir = facing.toDirection();
        if (dir != null && state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, dir);
        }
        if (dir != null && dir.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        }
        Property<Boolean> faceProp = faceProperty(facing);
        if (faceProp != null && state.hasProperty(faceProp)) {
            return state.setValue(faceProp, true);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.setValue(BlockStateProperties.AXIS, axisFor(facing));
        }
        return state;
    }

    /** Set several connection faces true (fences, panes, glass, bars, vines). */
    public BlockState withFaces(BlockFace... faces) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        for (BlockFace face : faces) {
            Property<Boolean> faceProp = faceProperty(face);
            if (faceProp != null && state.hasProperty(faceProp)) {
                state = state.setValue(faceProp, true);
            }
        }
        return state;
    }

    /**
     * Whether this block connects face-by-face — Bukkit's {@code MultipleFacing}. The old code
     * tests this to choose between per-face and bulk placement (see {@code SupportBlocks.setWalls}).
     *
     * <p><b>Walls are deliberately excluded.</b> In 1.14 walls were {@code MultipleFacing} like
     * fences, but the 1.16 flattening moved them to a {@code WallSide} (none/low/tall) enum per
     * side, so they no longer carry the boolean face properties this reports on. They therefore
     * take the bulk-placement branch. Revisit when decoration lands (Phase 5) if wall connections
     * come out wrong.
     */
    public boolean hasFaces() {
        if (defaultState == null) {
            return false;
        }
        for (BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN }) {
            Property<Boolean> faceProp = faceProperty(face);
            if (faceProp != null && defaultState.hasProperty(faceProp)) {
                return true;
            }
        }
        return false;
    }

    /** The block as a slab of the given type (top / bottom / double), if it is a slab. */
    public BlockState asSlab(SlabType type) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        return state.hasProperty(BlockStateProperties.SLAB_TYPE)
                ? state.setValue(BlockStateProperties.SLAB_TYPE, type)
                : state;
    }

    /**
     * One half of a two-tall block (door), facing the given direction. Handles both the modern
     * {@code DOUBLE_BLOCK_HALF} (doors) and the {@code HALF} (stairs/trapdoors) properties.
     */
    public BlockState asDoorHalf(boolean top, BlockFace facing) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        Direction dir = facing.toDirection();
        if (dir != null && dir.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    top ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        } else if (state.hasProperty(BlockStateProperties.HALF)) {
            state = state.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }
        return state;
    }

    /** The boolean connection property for one face, or {@code null} for a face that has none. */
    public static Property<Boolean> faceProperty(BlockFace face) {
        switch (face) {
            case NORTH: return BlockStateProperties.NORTH;
            case EAST:  return BlockStateProperties.EAST;
            case SOUTH: return BlockStateProperties.SOUTH;
            case WEST:  return BlockStateProperties.WEST;
            case UP:    return BlockStateProperties.UP;
            case DOWN:  return BlockStateProperties.DOWN;
            default:    return null;
        }
    }

    private static Direction.Axis axisFor(BlockFace facing) {
        switch (facing) {
            case NORTH:
            case SOUTH:
                return Direction.Axis.Z;
            case EAST:
            case WEST:
                return Direction.Axis.X;
            default:
                return Direction.Axis.Y;
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof Material)) {
            return false;
        }
        Material other = (Material) o;
        return block != null ? other.block == block : other.item == item;
    }

    @Override
    public int hashCode() {
        return block != null ? block.hashCode() : item.hashCode();
    }

    @Override
    public String toString() {
        return "Material(" + name() + ")";
    }

//__CONSTANTS__
}
'''

if __name__ == "__main__":
    main()
