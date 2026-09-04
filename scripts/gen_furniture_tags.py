#!/usr/bin/env python3
"""Generate the furniture role tags from installed furniture mods.

CityWorld furnishes rooms from ROLE tags — "something to sit on", "something to eat at" — rather than
from block names, so a furniture mod joins by being tagged and needs no code. The two big mods ship
around 1,200 furniture blocks between them on a regular `<material>_<kind>` naming scheme, which is
far too many to hand-write and exactly regular enough to derive.

    python3 scripts/gen_furniture_tags.py [mods_dir]

Writes data/cityworld/tags/block/furniture/<role>.json. Every entry is `"required": false`, so the
tags cost nothing when the mod is absent — the same contract the palettes use.

⚠ Re-run this when a furniture mod updates; it adds blocks. Nothing here is hand-edited, in the same
way Material.java is generated rather than maintained.

Three families of mod are understood:

  * Macaw's / Refurbished — `<material>_<kind>` names, classified by kind SUFFIX (ROLES), oriented
    by a per-family measured FACING_OFFSET.
  * Fantasy's Furniture SETS (Nordic, Necrolord, …, every `fantasyfurniture_<set>` mod) — one fixed
    vocabulary of 39 block names per set (`chair`, `wardrobe`, `bed_double`…), most of them
    MULTI-BLOCK (a chair is two blocks tall, a wardrobe two wide and three tall). The vocabulary is
    fixed by the base mod's FurnitureUtil, so the tables below cover every set that exists and every
    set the author adds later; a set that grows a new block name is reported, not guessed at.
  * Fantasy's Furniture DECORATIONS — tabletop and wall scatter (books, bottles, food, candles,
    mirrors), classified per block into the decoration pools with the properties worth randomising.
"""
import json
import os
import re
import sys
import zipfile
from collections import defaultdict

MODS = sys.argv[1] if len(sys.argv) > 1 else \
    "/mnt/c/Users/darre/curseforge/minecraft/Instances/26.2/mods"
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "src", "main", "resources", "data",
                    "cityworld")
OUT = os.path.join(ROOT, "tags", "block", "furniture")
DATAMAP = os.path.join(ROOT, "data_maps", "block", "furniture.json")

# ⚠ How a block's `facing` relates to the way its FRONT points, in degrees clockwise. MEASURED from
# each model's geometry (backrest, door, handle, tap, cistern) against its blockstate rotation table
# — not guessed, because the conventions vary not just per mod but per FAMILY within Macaw's:
# the classic chair is 0 while modern_chair is 180, so keys are (namespace, family suffix), longest
# suffix wins per block.
#
#   mcw chair/striped_chair : back at +X, unrotated facing=west  -> facing IS the front       -> 0
#   mcw modern_chair        : back at -X, unrotated facing=west  -> facing is the BACK        -> 180
#   mcw couch               : back at +Z, unrotated facing=west  -> 90 degrees off            -> 270
#   mcw counters + sinks    : working front at -Z, unrotated facing=south -> facing is BACK   -> 180
#   mcw kitchen cabinets    : doors at -X, unrotated facing=east -> facing is the BACK        -> 180
#   mcw drawers/wardrobes/bookshelves : fronts at -X, unrotated facing=south -> like the sofa -> 270
#   refurbished (ALL)       : facing is uniformly the BACK (six families measured, one shared
#                             FurnitureHorizontalBlock base class)                            -> 180
#   fantasyfurniture (ALL)  : facing is uniformly the FRONT — every block places with
#                             `context.getHorizontalDirection().getOpposite()` (one shared apexcore
#                             base class), and the geometry agrees: at the unrotated facing=north
#                             variant the chair backrest, sofa backrest, painting canvas and wall
#                             light all sit at +Z, i.e. behind a viewer standing to the north -> 0
#
# mcw desks (desk/covered_desk/modern_desk) are deliberately absent: their facing property is a
# custom two-value axis (north|east) our placement cannot set, and the pieces read the same from
# front and back, so no offset applies.
#
# Anything unmeasured defaults to no entry (offset 0) and will be visibly wrong rather than subtly
# wrong, which is the right failure: a piece facing the wall gets reported, one 15 degrees off does
# not exist.
FACING_OFFSET = {
    ("mcwfurnitures", "chair"): 0,
    ("mcwfurnitures", "striped_chair"): 0,
    # modern_chair: 180 was measured from a TRUNCATED element dump and the playtest falsified it —
    # the tall (y18) backrest posts sit at +X like the classic chair. All mcw chairs are 0.
    ("mcwfurnitures", "modern_chair"): 0,
    # ⚠ keys are FAMILY SUFFIXES (block-name endings), not role names. This entry was once keyed
    # "sofa" (the role) and silently matched nothing — F3 in playtest showed couches placed with
    # no offset at all, which is what the round-2 "couch corners" really were.
    ("mcwfurnitures", "couch"): 270,
    ("mcwfurnitures", "chaise"): 270,     # same family shape (chaises carry no facing; harmless)
    ("mcwfurnitures", "counter"): 180,
    ("mcwfurnitures", "drawer_counter"): 180,
    ("mcwfurnitures", "double_drawer_counter"): 180,
    ("mcwfurnitures", "cupboard_counter"): 180,
    ("mcwfurnitures", "kitchen_cabinet"): 180,
    ("mcwfurnitures", "double_kitchen_cabinet"): 180,
    ("mcwfurnitures", "glass_kitchen_cabinet"): 180,
    ("mcwfurnitures", "kitchen_sink"): 180,
    ("mcwfurnitures", "drawer"): 270,
    ("mcwfurnitures", "double_drawer"): 270,
    ("mcwfurnitures", "triple_drawer"): 270,
    ("mcwfurnitures", "large_drawer"): 270,
    ("mcwfurnitures", "lower_triple_drawer"): 270,
    ("mcwfurnitures", "bookshelf_drawer"): 270,
    ("mcwfurnitures", "lower_bookshelf_drawer"): 270,
    ("mcwfurnitures", "wardrobe"): 270,
    ("mcwfurnitures", "modern_wardrobe"): 270,
    ("mcwfurnitures", "double_wardrobe"): 270,
    ("mcwfurnitures", "bookshelf"): 270,
    ("mcwfurnitures", "bookshelf_cupboard"): 270,
}

# Refurbished is uniform: every oriented block uses facing for its back. Applied to any
# refurbished block whose blockstate carries a facing, instead of listing every family.
MOD_DEFAULT_OFFSET = {"refurbished_furniture": 180}

# Two-block bed-like furniture: type=bottom at the anchor, type=head one cell toward `facing`,
# both halves sharing the facing value (measured from BathBlock.setPlacedBy — it is exactly the
# vanilla bed contract). The refurbished baths are the ONLY multi-block furniture in either mod.
PARTS = {("refurbished_furniture", "bath"): 2}

# The three decoration pools — the owner's design call: things that stand on the floor, things
# that belong ON a surface (a table gets placed underneath them), and things mounted on a wall.
# Vanilla seeds live here so the whole pool stays generated; furniture-mod lamps are appended to
# `surface` automatically (Refurbished lamps are y 0-14 with no facing — table lamps, not floor
# lamps, which is why they read wrong standing on the ground).
DECOR = {
    "floor": ["minecraft:decorated_pot", "minecraft:flower_pot", "minecraft:potted_fern",
              "minecraft:potted_azalea_bush", "minecraft:potted_bamboo"],
    "surface": ["minecraft:candle", "minecraft:white_candle", "minecraft:orange_candle",
                "minecraft:light_gray_candle", "minecraft:red_candle", "minecraft:lantern",
                "minecraft:soul_lantern", "minecraft:potted_fern", "minecraft:decorated_pot",
                "minecraft:amethyst_cluster"],  # owner: "good decor for a hippy's table, not the floor"
    "wall": ["minecraft:wall_torch", "minecraft:soul_wall_torch", "minecraft:glow_lichen"],
    # interior lighting hung below ceilings (a ceiling block IS the next floor's floor, so light
    # blocks can't go in it) — mods add their hanging lights here
    "hanging_light": ["minecraft:lantern", "minecraft:soul_lantern"],
    # rugs: the carpets a bedroom/hallway rug is cut from (the old hard-coded RUGS array)
    "rug": ["minecraft:white_carpet", "minecraft:light_gray_carpet", "minecraft:cyan_carpet",
            "minecraft:red_carpet", "minecraft:moss_carpet"],
}

# Vanilla beds seed the `bed` role, so a bedroom draws vanilla and modded beds from ONE pool. A
# vanilla bed is a two-block piece whose `facing` runs foot->head, i.e. the OPPOSITE of the way the
# sleeper looks: offset 180 from CityWorld's "front", and the head one cell along facing (back -1).
VANILLA_BEDS = ["minecraft:%s_bed" % c for c in (
    "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink", "gray", "light_gray",
    "cyan", "purple", "blue", "brown", "green", "red", "black")]
BED_LAYOUT = [{"props": {"part": "foot"}}, {"back": -1, "props": {"part": "head"}}]

# Vanilla's own wall shelves (1.21.9+) seed the `shelf` role; `facing` is the front there too
# (ShelfBlock.getStateForPlacement uses the player's opposite direction), so no offset. Ids a
# Minecraft version lacks are skipped by the tag loader — every entry is optional.
VANILLA_SHELVES = ["minecraft:%s_shelf" % w for w in (
    "oak", "spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
    "bamboo", "crimson", "warped")]

# Kind suffix -> CityWorld role. Longest suffix wins, so `kitchen_sink` beats `sink` and
# `kitchen_storage_cabinet` beats `cabinet`. A block matching nothing here is simply not furniture we
# know how to place, which is a better outcome than guessing a role for it.
ROLES = {
    "chair": "chair", "stool": "chair",
    "table": "table", "desk": "desk",
    "couch": "sofa", "sofa": "sofa", "chaise": "sofa",
    "counter": "counter",
    "cabinet": "cabinet", "cupboard": "cabinet", "cabinetry": "cabinet",
    "storage_cabinet": "cabinet", "kitchen_storage_cabinet": "cabinet", "kitchen_cabinetry": "cabinet",
    "bookshelf": "bookshelf",
    "sink": "sink", "basin": "sink", "kitchen_sink": "sink",
    "toilet": "toilet", "bath": "bath",
    "lamp": "lamp",
    "drawer": "drawer", "kitchen_drawer": "drawer",
    "wardrobe": "wardrobe",
    # the appliance round (owner-scouted, Refurbished): the fridge "double block" is really a
    # fridge with a freezer stacked on top — two separate blocks, placed as a stack
    "ceiling_fan": "ceiling_fan",
    "television": "tv",
    "fridge": "fridge", "freezer": "freezer",
    "stove": "stove", "microwave": "microwave", "toaster": "toaster",
    "cutting_board": "cutting_board",
    "recycle_bin": "bin",
    # the interiors round: offices, warehouses and workshops draw these
    "computer": "computer",
    "crate": "crate",
    "workbench": "workbench",
}

# Blocks whose name matches a role but which are not the thing itself. ⚠ This used to contain a
# bare `light_`, which silently ate every `light_fridge`/`light_stove`/`light_ceiling_fan` — half
# of Refurbished's appliance palette. Only the lightswitch itself needs excluding.
EXCLUDE = re.compile(r"(item|slab|stair|door|trapdoor|wall|fence|pane|_top$|_bottom$|lightswitch)")

# ---------------------------------------------------------------------------------------------------
# Fantasy's Furniture sets.
#
# Every set mod (`fantasyfurniture_nordic`, `fantasyfurniture_necrolord`, and whatever comes next)
# registers the SAME 39 block names through the base mod's FurnitureUtil, so one table keyed by block
# name covers all of them. Most pieces are apexcore MULTI-BLOCKS: one block id, a `multi_block_index`
# property, and a fixed list of local positions the mod rotates by `facing`. Read from the decompiled
# apexcore MultiBlockProperties/MultiBlock (26.2.3):
#
#   sized(sx, sy, sz) lists positions y-outer, x, z-inner; facing=WEST is the unrotated frame, where
#   +z = facing.getCounterClockWise() (a viewer standing where `facing` points sees it on their
#   RIGHT), +x = facing.getOpposite() (BACK, away from that viewer), +y = UP.
#
# So a layout is written viewer-relative as (right, up, back) and the Java rotates it by the placed
# `facing`. The bed_double is a custom shape: origin + (-1,0,0) + (-1,0,-1) + (0,0,-1), head = indices
# 1 and 2 (BedMultiBlock.isHead). The bed_single is a plain vanilla-contract bed (part=foot/head).
# ---------------------------------------------------------------------------------------------------
def _sized(sx, sy, sz):
    """apexcore MultiBlockProperty.Builder.sized, expressed as (right, up, back) parts."""
    parts = []
    for y in range(sy):
        for x in range(sx):
            for z in range(sz):
                part = {}
                if z:
                    part["right"] = z
                if y:
                    part["up"] = y
                if x:
                    part["back"] = x
                parts.append(part)
    return parts


MB_1x1x2 = _sized(1, 1, 2)   # two wide (bench, desk, dresser, chest, painting_wide)
MB_1x2x1 = _sized(1, 2, 1)   # two tall (chair, floor_light)
MB_1x2x2 = _sized(1, 2, 2)   # bookshelf
MB_1x3x2 = _sized(1, 3, 2)   # wardrobe

# block name -> (role or ("decor", pool), layout, props, vary, reconnect)
SET = {
    "chair":          dict(role="chair", layout=MB_1x2x1),
    "stool":          dict(role="chair"),
    "cushion":        dict(role="chair"),
    "bench":          dict(role="chair", layout=MB_1x1x2),
    # the sofa's DEFAULT state is connection=center (a backless middle piece) — placed alone it must
    # be `single`; runs are re-derived by reconnect, which is safe here because the mod's own
    # connection logic reads the same facing we write (offset 0)
    "sofa":           dict(role="sofa", props={"connection": "single"}, reconnect=True),
    "table":          dict(role="table", reconnect=True),
    "desk_left":      dict(role="desk", layout=MB_1x1x2),
    "desk_right":     dict(role="desk", layout=MB_1x1x2),
    "counter":        dict(role="counter", reconnect=True),
    "drawer":         dict(role="drawer"),
    "dresser":        dict(role="drawer", layout=MB_1x1x2),
    "wardrobe":       dict(role="wardrobe", layout=MB_1x3x2),
    "bookshelf":      dict(role="bookshelf", layout=MB_1x2x2),
    "chest":          dict(role="crate", layout=MB_1x1x2),
    "oven":           dict(role="stove"),
    "bed_single":     dict(role="bed", offset=180, layout=BED_LAYOUT),
    "bed_double":     dict(role="bed", offset=180, layout=[
        {"props": {"part": "foot"}},
        {"back": -1, "props": {"part": "head"}},
        {"back": -1, "right": -1, "props": {"part": "head"}},
        {"right": -1, "props": {"part": "foot"}},
    ]),
    "floor_light":    dict(role="floor_lamp", layout=MB_1x2x1),
    "lockbox":        dict(decor="surface"),
    "chandelier":     dict(decor="hanging_light"),
    "wall_light":     dict(decor="wall"),
    "painting_small": dict(decor="wall"),
    "painting_wide":  dict(decor="wall", layout=MB_1x1x2),
    # a wall shelf: two pixels at the top of its cell, so a piece placed in the cell above stands
    # ON it — its own role, for the wall pass to mount at waist height with something on top
    "shelf":          dict(role="shelf", props={"connection": "single"}, reconnect=True),
    "carpet":         dict(decor="rug"),
}
# Set blocks that are building material or fittings, not furniture. Planks and wool join the
# building palettes by themselves through #minecraft:planks / #minecraft:wool (the sets tag them);
# a set's stone (necrolord bricks, in #c:stones) is added to build/modern_stones by hand.
SET_NOT_FURNITURE = {"planks", "wool", "bricks", "slab", "stairs", "fence", "fence_gate", "trapdoor",
                     "pressure_plate", "door_single", "door_double", "sign", "wall_sign",
                     "hanging_sign", "wall_hanging_sign", "furniture_station"}

# Fantasy's Furniture - Decorations: block name -> pool + what to randomise per placement. Every block
# is a SimpleHorizontalDirectionalBlock (facing = front, offset 0) with no support check. Left out on
# purpose: the macabre (bone piles, gravestone, skull blossoms, spider webs — an APOCALYPSE idea),
# the block-entity-backed (plushie, cookie jar's contents are fine but the jar itself is a BE menu),
# seasonal snowballs, and the bronze chain.
DECORATIONS = {
    # on a table
    "beetroot_soup_bowl": dict(decor="surface"), "mushroom_stew_bowl": dict(decor="surface"),
    "bowl": dict(decor="surface"),
    "berry_basket": dict(decor="surface"), "blueberry_basket": dict(decor="surface"),
    "strawberry_basket": dict(decor="surface"), "sweetberry_basket": dict(decor="surface"),
    "boiled_creme_treats": dict(decor="surface", vary=["count"]),
    "book_stack_0": dict(decor="surface", vary=["count"]),
    "book_stack_1": dict(decor="surface", vary=["count"]),
    "candelabra_0": dict(decor="surface", props={"lit": "true"}),
    "candelabra_1": dict(decor="surface", props={"lit": "true"}),
    "candles_0": dict(decor="surface", props={"lit": "true"}),
    "candles_1": dict(decor="surface", props={"lit": "true"}),
    "chalices_0": dict(decor="surface", vary=["count"]),
    "chalices_1": dict(decor="surface", vary=["count"]),
    "chalices_2": dict(decor="surface", vary=["count"]),
    "chalices_3": dict(decor="surface", vary=["count", "color"]),
    "cookie_jar": dict(decor="surface", vary=["fullness"]),
    "copper_coin_stack": dict(decor="surface"), "iron_coin_stack": dict(decor="surface"),
    "golden_coin_stack": dict(decor="surface"),
    "crown": dict(decor="surface"), "cushioned_crown": dict(decor="surface"),
    "floating_tomes": dict(decor="surface", vary=["count", "color"]),
    "food_0": dict(decor="surface"), "food_1": dict(decor="surface"),
    "food_2": dict(decor="surface"), "food_3": dict(decor="surface"),
    "mead_bottles": dict(decor="surface", vary=["count"]),
    "muffins_blueberry": dict(decor="surface", vary=["count"]),
    "muffins_chocolate": dict(decor="surface", vary=["count"]),
    "muffins_sweetberry": dict(decor="surface", vary=["count"]),
    "paper_stack": dict(decor="surface"),
    "platter_0": dict(decor="surface", vary=["count"]),
    "platter_1": dict(decor="surface", vary=["count"]),
    "potion_bottles": dict(decor="surface", vary=["count", "color"]),
    "soul_gems_dark": dict(decor="surface"), "soul_gems_light": dict(decor="surface"),
    "sweetrolls": dict(decor="surface", vary=["count"]),
    "tankards": dict(decor="surface", vary=["count"]),
    "tankards_honeymead": dict(decor="surface", vary=["count"]),
    "tankards_milk": dict(decor="surface", vary=["count"]),
    "tankards_sweetberry": dict(decor="surface", vary=["count"]),
    "tea_cups": dict(decor="surface", vary=["count"]),
    "tea_set": dict(decor="surface", layout=MB_1x1x2),
    # on the floor
    "bolts_of_cloth": dict(decor="floor"),
    "brewing_cauldron": dict(decor="floor", vary=["color"]),
    "presents": dict(decor="floor", vary=["count", "color"]),
    "stackable_pumpkins": dict(decor="floor", vary=["count"]),
    "pottery_0": dict(decor="floor"), "pottery_1": dict(decor="floor"),
    "mushrooms_brown": dict(decor="floor", vary=["count"]),
    "mushrooms_red": dict(decor="floor", vary=["count"]),
    "floor_cushion": dict(role="chair", vary=["color"]),
    # on the wall
    "banner": dict(decor="wall", layout=MB_1x2x1),
    "fairy_lights": dict(decor="wall", vary=["color"]),
    "stocking": dict(decor="wall", vary=["color"]),
    "hanging_herbs": dict(decor="wall"),
    "wall_mirror_small": dict(decor="wall"),
    "wall_mirror_large": dict(decor="wall", layout=MB_1x2x1),
}


def role_of(name: str):
    """The role for a block path, longest suffix first."""
    for suffix in sorted(ROLES, key=len, reverse=True):
        if name == suffix or name.endswith("_" + suffix):
            return ROLES[suffix]
    return None


def suffix_lookup(table, ns, name):
    """The table value for (ns, family suffix), longest suffix first, or None."""
    best = None
    for (tns, suffix), value in table.items():
        if tns != ns:
            continue
        if name == suffix or name.endswith("_" + suffix):
            if best is None or len(suffix) > len(best[0]):
                best = (suffix, value)
    return best[1] if best else None


def depends_on_fantasyfurniture(z: zipfile.ZipFile) -> bool:
    """Whether this jar declares the Fantasy's Furniture base mod as a dependency — the durable way
    to recognise a set, since a set's modid is the author's convention rather than a contract."""
    try:
        toml = z.read("META-INF/neoforge.mods.toml").decode("utf-8", "replace")
    except KeyError:
        return False
    # only a DEPENDENCY on the base mod counts — the base jar's own `modId = "fantasyfurniture"`
    # header must not make the base mod (which ships one crafting station) look like a set
    return re.search(r'\[\[dependencies\.[a-z0-9_]+\]\][^\[]*?modId\s*=\s*"fantasyfurniture"', toml) is not None


def index_values(state: dict) -> int:
    """How many multi_block_index values a blockstate declares (0 when it has none)."""
    values = set()
    for key in state.get("variants", {}):
        for kv in key.split(","):
            if kv.startswith("multi_block_index="):
                values.add(kv.split("=", 1)[1])
    return len(values)


def main():
    found = defaultdict(list)      # role -> [block ids]
    decor_extra = defaultdict(list)  # pool -> [block ids]
    entries = {}                   # block id -> data map value
    conditions = {}                # block id -> modid (for the loaded condition)
    jars = [f for f in sorted(os.listdir(MODS)) if f.endswith(".jar")]
    for jar in jars:
        try:
            z = zipfile.ZipFile(os.path.join(MODS, jar))
        except Exception:
            continue
        with z:
            is_set = depends_on_fantasyfurniture(z)
            blocks = defaultdict(list)
            for entry in z.namelist():
                m = re.match(r"assets/([a-z0-9_]+)/blockstates/([a-z0-9_/]+)\.json$", entry)
                if m:
                    blocks[m.group(1)].append((m.group(2), entry))
            for ns, paths in blocks.items():
                if ns in ("minecraft",):
                    continue
                if ns == "fantasyfurniture_decorations":
                    table = DECORATIONS
                elif is_set or ns.startswith("fantasyfurniture_"):
                    table = SET
                else:
                    table = None
                hits = 0
                unknown = []
                for path, entry in paths:
                    name = path.split("/")[-1]
                    try:
                        state = json.loads(z.read(entry))
                    except Exception:
                        state = {}
                    if table is not None:
                        spec = table.get(name)
                        if spec is None:
                            if table is SET and name not in SET_NOT_FURNITURE:
                                unknown.append(name)
                            continue
                        layout = spec.get("layout")
                        declared = index_values(state)
                        if declared and (layout is None or len(layout) != declared):
                            # the mod grew or reshaped a piece — say so, place nothing, never guess
                            print(f"  !! {ns}:{name} declares {declared} multi_block_index values but the "
                                  f"table says {len(layout) if layout else 1} — skipped, re-measure it")
                            continue
                        block_id = f"{ns}:{path}"
                        if "role" in spec:
                            found[spec["role"]].append(block_id)
                        else:
                            decor_extra[spec["decor"]].append(block_id)
                        value = {}
                        if spec.get("offset"):
                            value["facingOffset"] = spec["offset"]
                        if layout:
                            value["layout"] = layout
                        if spec.get("props"):
                            value["props"] = spec["props"]
                        if spec.get("vary"):
                            value["vary"] = spec["vary"]
                        if spec.get("reconnect"):
                            value["reconnect"] = True
                        if value:
                            entries[block_id] = value
                            conditions[block_id] = ns
                        hits += 1
                        continue
                    if EXCLUDE.search(name):
                        continue
                    role = role_of(name)
                    if not role:
                        continue
                    # An offset only makes sense on a block that actually carries a facing —
                    # read it off the blockstate rather than assuming per role (mcw chaises
                    # and coffee tables have none, mcw desks have a 2-value axis).
                    has_facing = any("facing=" in k for k in state.get("variants", {}))
                    block_id = f"{ns}:{path}"
                    found[role].append(block_id)
                    value = {}
                    if has_facing:
                        off = suffix_lookup(FACING_OFFSET, ns, name)
                        if off is None:
                            off = MOD_DEFAULT_OFFSET.get(ns)
                        if off:
                            value["facingOffset"] = off
                    parts = suffix_lookup(PARTS, ns, name)
                    if parts:
                        value["parts"] = parts
                    if value:
                        entries[block_id] = value
                        conditions[block_id] = ns
                    hits += 1
                if hits:
                    kind = "set" if table is SET else "decorations" if table is DECORATIONS else ""
                    print(f"  {jar}: {ns} -> {hits} furniture blocks {kind}".rstrip())
                if unknown:
                    print(f"  !! {ns} has block names the set table does not know: {sorted(unknown)} — "
                          f"a new piece in this set; classify it in SET (or SET_NOT_FURNITURE)")

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(DATAMAP), exist_ok=True)

    # Vanilla beds seed the bed pool, and carry their contract in the data map like any mod's.
    for bed in VANILLA_BEDS:
        found["bed"].append(bed)
        entries[bed] = {"facingOffset": 180, "layout": BED_LAYOUT}
    found["shelf"].extend(VANILLA_SHELVES)

    # The facing/parts/layout data map, covering EVERY oriented role. Offsets are measured per
    # family (see FACING_OFFSET); a block with no facing property gets no entry, and a family with
    # no measurement gets no entry rather than a guess.
    values = {}
    for block_id in sorted(entries):
        ns = block_id.split(":")[0]
        record = {"value": entries[block_id]}
        if block_id in conditions:
            record = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": conditions[block_id]}],
                      "value": entries[block_id]}
        values[block_id] = record
    with open(DATAMAP, "w", encoding="utf-8") as fh:
        json.dump({"replace": False, "values": values}, fh, indent=2)
        fh.write("\n")
    multi = sum(1 for v in values.values()
                if v["value"].get("parts") or len(v["value"].get("layout", [])) > 1)
    print(f"  wrote furniture.json data map ({len(values)} entries, {multi} multi-block)")

    for role, ids in sorted(found.items()):
        ids = sorted(set(ids))
        payload = {
            "replace": False,
            "values": [{"id": i, "required": False} for i in ids],
        }
        with open(os.path.join(OUT, f"{role}.json"), "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
        print(f"  wrote {role}.json ({len(ids)})")

    # The decoration pools: vanilla seeds plus every modded lamp in `surface` (they are table
    # lamps — the floor/surface split is the point of the pools), plus the classified scatter.
    decor_dir = os.path.join(ROOT, "tags", "block", "decor")
    os.makedirs(decor_dir, exist_ok=True)
    surface_extra = sorted(set(found.get("lamp", [])))
    for pool, seeds in DECOR.items():
        ids = list(seeds) + (surface_extra if pool == "surface" else [])
        ids += sorted(set(decor_extra.get(pool, [])))
        payload = {
            "replace": False,
            "values": [{"id": i, "required": False} for i in ids],
        }
        with open(os.path.join(decor_dir, f"{pool}.json"), "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
        print(f"  wrote decor/{pool}.json ({len(ids)})")
    for pool in decor_extra:
        if pool not in DECOR:
            print(f"  !! decor pool '{pool}' has no vanilla seeds in DECOR — add it there")

    if not found:
        print("  no furniture mods found — nothing written")


if __name__ == "__main__":
    main()
