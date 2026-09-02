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
    ("mcwfurnitures", "modern_chair"): 180,
    ("mcwfurnitures", "sofa"): 270,       # couch family maps to the sofa role
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
                "minecraft:soul_lantern", "minecraft:potted_fern", "minecraft:decorated_pot"],
    "wall": ["minecraft:wall_torch", "minecraft:soul_wall_torch", "minecraft:glow_lichen"],
}

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
}

# Blocks whose name matches a role but which are not the thing itself.
EXCLUDE = re.compile(r"(item|slab|stair|door|trapdoor|wall|fence|pane|_top$|_bottom$|light_)")


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


def main():
    found = defaultdict(list)   # role -> [(id, has_facing)]
    jars = [f for f in sorted(os.listdir(MODS)) if f.endswith(".jar")]
    for jar in jars:
        try:
            z = zipfile.ZipFile(os.path.join(MODS, jar))
        except Exception:
            continue
        with z:
            blocks = defaultdict(list)
            for entry in z.namelist():
                m = re.match(r"assets/([a-z0-9_]+)/blockstates/([a-z0-9_/]+)\.json$", entry)
                if m:
                    blocks[m.group(1)].append((m.group(2), entry))
            for ns, paths in blocks.items():
                if ns in ("minecraft",):
                    continue
                hits = 0
                for path, entry in paths:
                    name = path.split("/")[-1]
                    if EXCLUDE.search(name):
                        continue
                    role = role_of(name)
                    if not role:
                        continue
                    # An offset only makes sense on a block that actually carries a facing —
                    # read it off the blockstate rather than assuming per role (mcw chaises
                    # and coffee tables have none, mcw desks have a 2-value axis).
                    try:
                        state = json.loads(z.read(entry))
                        has_facing = any("facing=" in k for k in state.get("variants", {}))
                    except Exception:
                        has_facing = False
                    found[role].append((f"{ns}:{path}", has_facing))
                    hits += 1
                if hits:
                    print(f"  {jar}: {ns} -> {hits} furniture blocks")

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(DATAMAP), exist_ok=True)

    # The facing/parts data map, covering EVERY oriented role. Offsets are measured per family
    # (see FACING_OFFSET); a block with no facing property gets no entry, and a family with no
    # measurement gets no entry rather than a guess.
    values = {}
    for role, entries in sorted(found.items()):
        for block_id, has_facing in sorted(set(entries)):
            ns, name = block_id.split(":")
            name = name.split("/")[-1]
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
                values[block_id] = {
                    "neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": ns}],
                    "value": value,
                }
    with open(DATAMAP, "w", encoding="utf-8") as fh:
        json.dump({"replace": False, "values": values}, fh, indent=2)
        fh.write("\n")
    two_part = sum(1 for v in values.values() if v["value"].get("parts"))
    print(f"  wrote furniture.json data map ({len(values)} entries, {two_part} two-part)")

    for role, entries in sorted(found.items()):
        ids = sorted(set(block_id for block_id, _ in entries))
        payload = {
            "replace": False,
            "values": [{"id": i, "required": False} for i in ids],
        }
        with open(os.path.join(OUT, f"{role}.json"), "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
        print(f"  wrote {role}.json ({len(ids)})")

    # The decoration pools: vanilla seeds plus every modded lamp in `surface` (they are table
    # lamps — the floor/surface split is the point of the pools).
    decor_dir = os.path.join(ROOT, "tags", "block", "decor")
    os.makedirs(decor_dir, exist_ok=True)
    surface_extra = sorted(set(block_id for block_id, _ in found.get("lamp", [])))
    for pool, seeds in DECOR.items():
        ids = list(seeds) + (surface_extra if pool == "surface" else [])
        payload = {
            "replace": False,
            "values": [{"id": i, "required": False} for i in ids],
        }
        with open(os.path.join(decor_dir, f"{pool}.json"), "w", encoding="utf-8") as fh:
            json.dump(payload, fh, indent=2)
            fh.write("\n")
        print(f"  wrote decor/{pool}.json ({len(ids)})")

    if not found:
        print("  no furniture mods found — nothing written")


if __name__ == "__main__":
    main()
