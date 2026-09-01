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

# ⚠ How a block's `facing` relates to the way its occupant looks, in degrees clockwise. MEASURED from
# each model's geometry (where the backrest sits) against its blockstate rotation table — not guessed,
# because the two mods disagree and Macaw's own sofa disagrees with Macaw's own chair:
#
#   mcwfurnitures  chair : base model back at +X, unrotated variant facing=west  -> facing IS the look
#   mcwfurnitures  sofa  : base model back at +Z, unrotated variant facing=west  -> 90 degrees off
#   refurbished    chair : base model back at -Z, unrotated variant facing=north -> facing is the BACK
#   refurbished    sofa  : base model back at -Z, unrotated variant facing=north -> facing is the BACK
#
# Anything unmeasured defaults to 0 and will be visibly wrong rather than subtly wrong, which is the
# right failure: a chair facing the wall gets reported, a chair 15 degrees off does not exist.
FACING_OFFSET = {
    ("mcwfurnitures", "chair"): 0,
    ("mcwfurnitures", "sofa"): 270,
    ("refurbished_furniture", "chair"): 180,
    ("refurbished_furniture", "sofa"): 180,
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


def main():
    found = defaultdict(list)
    jars = [f for f in sorted(os.listdir(MODS)) if f.endswith(".jar")]
    for jar in jars:
        try:
            with zipfile.ZipFile(os.path.join(MODS, jar)) as z:
                names = z.namelist()
        except Exception:
            continue
        blocks = defaultdict(list)
        for entry in names:
            m = re.match(r"assets/([a-z0-9_]+)/blockstates/([a-z0-9_/]+)\.json$", entry)
            if m:
                blocks[m.group(1)].append(m.group(2))
        for ns, paths in blocks.items():
            if ns in ("minecraft",):
                continue
            hits = 0
            for path in paths:
                name = path.split("/")[-1]
                if EXCLUDE.search(name):
                    continue
                role = role_of(name)
                if role:
                    found[role].append(f"{ns}:{path}")
                    hits += 1
            if hits:
                print(f"  {jar}: {ns} -> {hits} furniture blocks")

    os.makedirs(OUT, exist_ok=True)
    os.makedirs(os.path.dirname(DATAMAP), exist_ok=True)

    # The facing data map: only the roles CityWorld orients (seats). Tables, counters and cabinets
    # either auto-connect or read the same from any side, so declaring an offset for them would be
    # inventing precision we have not measured.
    values = {}
    for role, ids in sorted(found.items()):
        if role not in ("chair", "sofa"):
            continue
        for i in sorted(set(ids)):
            ns = i.split(":")[0]
            off = FACING_OFFSET.get((ns, role))
            if off is None:
                continue
            values[i] = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": ns}],
                         "value": {"facingOffset": off}}
    with open(DATAMAP, "w", encoding="utf-8") as fh:
        json.dump({"replace": False, "values": values}, fh, indent=2)
        fh.write("\n")
    print(f"  wrote furniture.json data map ({len(values)} seats)")
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
    if not found:
        print("  no furniture mods found — nothing written")


if __name__ == "__main__":
    main()
