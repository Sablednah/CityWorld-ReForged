#!/usr/bin/env python3
"""Write the Cobblemon buildings' loot tables: chests/pokecenter, chests/gym and chests/shop_pokemart (+ _extra hooks).

    python3 scripts/gen_cobblemon_loot.py          # in the checkout whose tables to write

Every item is Cobblemon's, so each table is conditioned on the mod (a top-level `neoforge:conditions` mod_loaded):
without Cobblemon the file is skipped at load and nothing references it, since the lots that roll these are only
planned while Cobblemon's blocks are present. Dialects as scripts/gen_shop_loot.py:
    1.21.1, 1.21.11, 26.1, 26.2   "type": "item", "functions": [set_count], hook id under "value"
    26.3                          typed everything: "minecraft:item", typed rolls, "modifier" for set_count
    1.20.1                        in loot_tables/, hook id under "name" — and VANILLA stock only: Forge 1.20.1 has no
                                  condition hook on loot tables (ForgeHooks.loadLootTable takes the JSON as it is),
                                  so an unknown `cobblemon:` item fails the whole table's parse without the mod
                                  (self-test 2026-11-13: "Expected name to be an item, was unknown string"). A pack
                                  adds Cobblemon's items there through the `_extra` hooks.
Re-run it in each checkout after a change (26.3 and 1.20.1 differ).
"""
import json
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "cityworld")

# table -> [(item, weight, min, max)]; a bare name is cobblemon's, "minecraft:x" vanilla's
TABLES = {
    "pokecenter": [("potion", 10, 1, 3), ("super_potion", 5, 1, 2), ("revive", 4, 1, 1), ("antidote", 3, 1, 2),
                   ("paralyze_heal", 3, 1, 2), ("awakening", 3, 1, 2), ("burn_heal", 3, 1, 2), ("ice_heal", 3, 1, 2),
                   ("full_heal", 2, 1, 1), ("ether", 3, 1, 1), ("poke_ball", 6, 1, 4), ("great_ball", 3, 1, 2),
                   ("oran_berry", 5, 2, 5), ("sitrus_berry", 3, 1, 3), ("exp_candy_xs", 3, 1, 3), ("rare_candy", 1, 1, 1),
                   ("minecraft:bread", 4, 1, 3), ("minecraft:cake", 1, 1, 1)],
    "gym": [("rare_candy", 4, 1, 2), ("exp_candy_xs", 6, 2, 5), ("ultra_ball", 3, 1, 2), ("great_ball", 5, 1, 3),
            ("hyper_potion", 4, 1, 2), ("max_revive", 2, 1, 1), ("full_heal", 3, 1, 2), ("max_ether", 2, 1, 1),
            ("minecraft:emerald", 5, 1, 3), ("minecraft:gold_ingot", 3, 1, 2), ("minecraft:name_tag", 1, 1, 1)],
    "shop_pokemart": [("poke_ball", 12, 2, 6), ("great_ball", 6, 1, 3), ("ultra_ball", 2, 1, 1), ("potion", 10, 1, 3),
                      ("super_potion", 6, 1, 2), ("hyper_potion", 2, 1, 1), ("revive", 4, 1, 1), ("antidote", 4, 1, 2),
                      ("paralyze_heal", 4, 1, 2), ("awakening", 3, 1, 2), ("burn_heal", 3, 1, 2), ("ice_heal", 3, 1, 2),
                      ("full_heal", 2, 1, 1), ("ether", 3, 1, 1), ("max_ether", 1, 1, 1), ("exp_candy_xs", 4, 1, 3),
                      ("rare_candy", 1, 1, 1), ("minecraft:emerald", 4, 1, 2)],
}


# 1.20.1: what stands in for the Cobblemon stock (see the module doc)
VANILLA_TABLES = {
    "pokecenter": [("minecraft:bread", 6, 1, 3), ("minecraft:cake", 1, 1, 1), ("minecraft:golden_apple", 2, 1, 1),
                   ("minecraft:honey_bottle", 4, 1, 2), ("minecraft:paper", 3, 1, 4), ("minecraft:red_bed", 1, 1, 1)],
    "gym": [("minecraft:emerald", 5, 1, 3), ("minecraft:gold_ingot", 3, 1, 2), ("minecraft:name_tag", 1, 1, 1),
            ("minecraft:experience_bottle", 4, 2, 5), ("minecraft:golden_apple", 1, 1, 1)],
    "shop_pokemart": [("minecraft:emerald", 4, 1, 2), ("minecraft:paper", 4, 1, 4), ("minecraft:honey_bottle", 4, 1, 2),
                      ("minecraft:bread", 3, 1, 3), ("minecraft:glass_bottle", 3, 1, 3)],
}


def dialect():
    if os.path.isdir(os.path.join(DATA, "loot_tables")):
        return "1.20.1"
    props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
    version = re.search(r"^minecraft_version=(\S+)", props, re.M).group(1)
    return "26.3" if version.startswith("26.3") else "standard"


def table(name, stock, kind):
    typed = kind == "26.3"
    entries = []
    for item, weight, low, high in stock:
        item_id = item if ":" in item else "cobblemon:" + item
        entry = {"type": "minecraft:item" if typed else "item", "name": item_id, "weight": weight}
        if high > 1:
            if typed:
                entry["modifier"] = {"type": "set_count",
                                     "count": {"type": "minecraft:uniform", "min": low, "max": high}}
            else:
                entry["functions"] = [{"function": "set_count", "count": {"min": low, "max": high}}]
        entries.append(entry)
    entries.append({"type": "minecraft:empty" if typed else "empty", "weight": 6})
    entries.append({"type": "minecraft:loot_table", "name" if kind == "1.20.1" else "value":
                    "cityworld:chests/%s_extra" % name, "weight": 15})
    rolls = {"type": "minecraft:uniform", "min": 2, "max": 5} if typed else {"min": 2, "max": 5}
    body = {"type": "minecraft:chest", "pools": [{"rolls": rolls, "entries": entries}]}
    if kind == "1.20.1":  # vanilla stock, no condition (Forge 1.20.1 would not read one)
        return body
    return {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": "cobblemon"}], **body}


def main():
    kind = dialect()
    out = os.path.join(DATA, "loot_tables" if kind == "1.20.1" else "loot_table", "chests")
    for name, stock in sorted((VANILLA_TABLES if kind == "1.20.1" else TABLES).items()):
        for file, body in ((name, table(name, stock, kind)), (name + "_extra", {"type": "minecraft:chest", "pools": []})):
            with open(os.path.join(out, file + ".json"), "w", encoding="utf-8") as fh:
                json.dump(body, fh, indent=2)
                fh.write("\n")
    print("wrote %d Cobblemon tables (+ their _extra hooks) in the %s dialect to %s" % (len(TABLES), kind, out))


if __name__ == "__main__":
    main()
