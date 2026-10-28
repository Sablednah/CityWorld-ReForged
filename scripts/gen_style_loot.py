#!/usr/bin/env python3
"""Write the everyday tables for the modern and ruined worlds: data/cityworld/loot_table/chests/<style>/<name>.json.

    python3 scripts/gen_style_loot.py          # in the checkout whose tables to write

The shared tables (chests/building, nightstand, food) are upstream CityWorld's: every item at the same weight, so a
drawer was as likely to hold rotten flesh or a white banner as a book. A MODERN world rolls chests/modern/<name>
instead and a ruined APOCALYPSE world chests/apocalypse/<name> (LootProvider_LootTable.keyFor); CLASSIC and every
other style keep the shared ones. Each style ships two tables:

    <name>        what a drawer in that world holds, weighted to read right (paper and books in an office; scrap,
                  string and bone in a ruin), ending in the SHARED <name>_extra hook, so a pack that fills
                  chests/building_extra reaches every style
    <name>_find   <name>, plus one find: something worth having (an emerald, a diamond, an enchanted book)

Which one a container gets is decided when it is placed, by the world setting treasures.oddsOfFinds (default 1 in
13, CityWorld's "very unlikely"; a loot table cannot read a setting, so the roll is made at generation from the
container's own seed). Ordinary on purpose: the vault, bunkers and mines are where the good loot is.

Generated for the same reason as gen_shop_loot.py, and in the same three dialects (read off the checkout):

    1.21.1, 1.21.11, 26.1, 26.2   "type": "item", "functions": [...], hook id under "value"
    1.20.1                        the same, in loot_tables/, hook id under "name"
    26.3                          typed: "minecraft:item", typed rolls, "modifier"

Every item exists on all six versions (nothing newer than 1.20.1, nothing renamed since).
"""
import json
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "cityworld")

# (item, weight, min, max[, "enchant"]); min/max 1 = a single item. "empty" is an item name meaning nothing.
EVERYDAY = {
    "modern": {
        "building": ((1, 4), [
            ("paper", 12, 1, 6), ("book", 8, 1, 2), ("writable_book", 4, 1, 1), ("clock", 3, 1, 1),
            ("compass", 3, 1, 1), ("candle", 6, 1, 3), ("string", 6, 1, 3), ("glass_bottle", 6, 1, 2),
            ("torch", 6, 1, 4), ("lantern", 2, 1, 1), ("flower_pot", 3, 1, 1), ("item_frame", 3, 1, 1),
            ("painting", 2, 1, 1), ("bowl", 4, 1, 2), ("bucket", 2, 1, 1), ("shears", 3, 1, 1),
            ("flint_and_steel", 2, 1, 1), ("feather", 4, 1, 3), ("ink_sac", 3, 1, 2), ("lead", 2, 1, 1),
            ("copper_ingot", 4, 1, 3), ("iron_nugget", 4, 1, 6), ("cookie", 4, 1, 3), ("apple", 4, 1, 2),
            ("empty", 10, 1, 1)]),
        "nightstand": ((1, 2), [
            ("book", 10, 1, 1), ("paper", 10, 1, 4), ("writable_book", 4, 1, 1), ("clock", 3, 1, 1),
            ("candle", 8, 1, 2), ("apple", 6, 1, 1), ("cookie", 6, 1, 3), ("glass_bottle", 5, 1, 1),
            ("string", 4, 1, 2), ("feather", 4, 1, 2), ("ink_sac", 3, 1, 1), ("name_tag", 1, 1, 1),
            ("empty", 8, 1, 1)]),
        "food": ((1, 4), [
            ("bread", 10, 1, 3), ("apple", 8, 1, 3), ("cookie", 8, 2, 6), ("carrot", 6, 1, 4),
            ("potato", 6, 1, 4), ("baked_potato", 6, 1, 3), ("beetroot", 4, 1, 3), ("melon_slice", 6, 1, 4),
            ("sweet_berries", 5, 2, 6), ("sugar", 5, 1, 4), ("egg", 5, 1, 3), ("honey_bottle", 3, 1, 1),
            ("pumpkin_pie", 4, 1, 1), ("cooked_chicken", 5, 1, 2), ("cooked_beef", 4, 1, 2), ("cooked_cod", 4, 1, 2),
            ("mushroom_stew", 2, 1, 1), ("dried_kelp", 3, 2, 6), ("empty", 8, 1, 1)]),
    },
    "apocalypse": {
        "building": ((1, 4), [
            ("rotten_flesh", 10, 1, 3), ("bone", 8, 1, 3), ("string", 8, 1, 4), ("paper", 8, 1, 4),
            ("stick", 6, 1, 4), ("iron_nugget", 8, 2, 8), ("gold_nugget", 3, 1, 4), ("leather", 4, 1, 2),
            ("arrow", 5, 2, 8), ("gunpowder", 4, 1, 3), ("glass_bottle", 5, 1, 2), ("spider_eye", 3, 1, 1),
            ("torch", 5, 1, 4), ("candle", 4, 1, 2), ("flint", 4, 1, 3), ("coal", 5, 1, 4), ("dried_kelp", 4, 1, 4),
            ("bread", 2, 1, 1), ("cobweb", 2, 1, 1), ("tripwire_hook", 2, 1, 1), ("empty", 12, 1, 1)]),
        "nightstand": ((1, 2), [
            ("paper", 8, 1, 3), ("candle", 6, 1, 2), ("string", 6, 1, 3), ("bone", 6, 1, 2), ("arrow", 5, 1, 6),
            ("iron_nugget", 6, 1, 6), ("rotten_flesh", 6, 1, 2), ("book", 4, 1, 1), ("glass_bottle", 4, 1, 1),
            ("spider_eye", 3, 1, 1), ("gunpowder", 3, 1, 2), ("empty", 10, 1, 1)]),
        "food": ((1, 4), [
            ("rotten_flesh", 12, 1, 4), ("dried_kelp", 8, 2, 6), ("poisonous_potato", 6, 1, 3), ("potato", 5, 1, 3),
            ("beetroot", 5, 1, 3), ("carrot", 4, 1, 2), ("spider_eye", 4, 1, 2), ("bread", 3, 1, 1),
            ("apple", 3, 1, 1), ("cookie", 2, 1, 2), ("sugar", 3, 1, 2), ("bowl", 4, 1, 2), ("glass_bottle", 4, 1, 1),
            ("mushroom_stew", 1, 1, 1), ("red_mushroom", 3, 1, 2), ("brown_mushroom", 3, 1, 2), ("empty", 12, 1, 1)]),
    },
}

# the find in a <name>_find table, by weight (how often it holds anything is the oddsOfFinds setting)
FINDS = {
    "modern": {
        "building": [("emerald", 3, 1, 3), ("iron_ingot", 3, 1, 3), ("gold_ingot", 2, 1, 2),
                     ("experience_bottle", 2, 1, 3), ("book", 2, 1, 1, "enchant"), ("diamond", 1, 1, 1),
                     ("golden_apple", 1, 1, 1), ("spyglass", 1, 1, 1), ("name_tag", 1, 1, 1)],
        "nightstand": [("emerald", 4, 1, 2), ("gold_ingot", 3, 1, 2), ("experience_bottle", 3, 1, 3),
                       ("book", 3, 1, 1, "enchant"), ("diamond", 1, 1, 1), ("music_disc_cat", 1, 1, 1),
                       ("golden_apple", 1, 1, 1)],
        "food": [("golden_carrot", 6, 1, 3), ("cake", 4, 1, 1), ("emerald", 4, 1, 2), ("golden_apple", 2, 1, 1)],
    },
    "apocalypse": {
        "building": [("iron_ingot", 4, 1, 3), ("emerald", 2, 1, 2), ("experience_bottle", 2, 1, 3),
                     ("book", 2, 1, 1, "enchant"), ("diamond", 1, 1, 1), ("golden_apple", 1, 1, 1),
                     ("ender_pearl", 1, 1, 1), ("crossbow", 1, 1, 1), ("iron_sword", 1, 1, 1), ("name_tag", 1, 1, 1)],
        "nightstand": [("iron_ingot", 4, 1, 2), ("emerald", 3, 1, 2), ("experience_bottle", 3, 1, 3),
                       ("book", 3, 1, 1, "enchant"), ("diamond", 1, 1, 1), ("ender_pearl", 1, 1, 1),
                       ("golden_apple", 1, 1, 1)],
        "food": [("golden_carrot", 4, 1, 3), ("cooked_beef", 4, 1, 3), ("bread", 3, 2, 4), ("honey_bottle", 3, 1, 2),
                 ("golden_apple", 2, 1, 1)],
    },
}


def dialect():
    if os.path.isdir(os.path.join(DATA, "loot_tables")):
        return "1.20.1"
    props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
    version = re.search(r"^minecraft_version=(\S+)", props, re.M).group(1)
    return "26.3" if version.startswith("26.3") else "standard"


def entry(spec, kind):
    item, weight, low, high = spec[:4]
    enchant = len(spec) > 4 and spec[4] == "enchant"
    typed = kind == "26.3"
    if item == "empty":
        return {"type": "minecraft:empty" if typed else "empty", "weight": weight}
    out = {"type": "minecraft:item" if typed else "item", "name": "minecraft:" + item, "weight": weight}
    functions = []
    if high > 1:
        functions.append(("set_count", {"count": {"type": "minecraft:uniform", "min": low, "max": high}
                                        if typed else {"min": low, "max": high}}))
    if enchant:
        functions.append(("enchant_randomly", {}))
    if typed:
        if len(functions) == 1:
            out["modifier"] = dict(type=functions[0][0], **functions[0][1])
        elif functions:
            # no shipped 26.3 table chains two modifiers, so the form is unproven: give a find a count OR an enchant
            raise ValueError("%s: two modifiers in the 26.3 dialect" % item)
    elif functions:
        out["functions"] = [dict(function=f, **args) for f, args in functions]
    return out


def rolls(low, high, kind):
    if low == high:
        return low
    return {"type": "minecraft:uniform", "min": low, "max": high} if kind == "26.3" else {"min": low, "max": high}


def table(style, name, kind):
    (low, high), everyday = EVERYDAY[style][name]
    entries = [entry(s, kind) for s in everyday]
    entries.append({"type": "minecraft:loot_table", "name" if kind == "1.20.1" else "value":
                    "cityworld:chests/%s_extra" % name, "weight": 15})
    return {"type": "minecraft:chest", "pools": [{"rolls": rolls(low, high, kind), "entries": entries}]}


def find_table(style, name, kind):
    everyday = {"type": "minecraft:loot_table", "name" if kind == "1.20.1" else "value":
                "cityworld:chests/%s/%s" % (style, name)}
    return {"type": "minecraft:chest",
            "pools": [{"rolls": 1, "entries": [everyday]},
                      {"rolls": 1, "entries": [entry(s, kind) for s in FINDS[style][name]]}]}


def main():
    kind = dialect()
    base = os.path.join(DATA, "loot_tables" if kind == "1.20.1" else "loot_table", "chests")
    count = 0
    for style in EVERYDAY:
        os.makedirs(os.path.join(base, style), exist_ok=True)
        for name in EVERYDAY[style]:
            for file, body in ((name, table(style, name, kind)), (name + "_find", find_table(style, name, kind))):
                with open(os.path.join(base, style, file + ".json"), "w", encoding="utf-8") as fh:
                    json.dump(body, fh, indent=2)
                    fh.write("\n")
                count += 1
    print("wrote %d style tables in the %s dialect to %s" % (count, kind, base))


if __name__ == "__main__":
    main()
