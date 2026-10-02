#!/usr/bin/env python3
"""Write the street shops' stock tables: data/cityworld/loot_table/chests/shop_<trade>.json, one per ShopTrade.

    python3 scripts/gen_shop_loot.py          # in the checkout whose tables to write

A shop's containers roll its TRADE's table (StoreBuildingLot / CornerShopLot.ownLootTable, and the barrel
beside the counter), where they used to roll one generic `shop` table — or, in a store building, the ordinary
building table: an armourer's chests held paper and paintings (owner, 2026-10-02).

Generated, because the loot format is not the same on every line and fourteen hand-kept tables times three
dialects is where a typo hides (a table in the wrong dialect stops a 26.3 server at registry load):

    1.21.1, 1.21.11, 26.1, 26.2   "type": "item", "functions": [set_count], hook id under "value"
    1.20.1                        the same, in loot_tables/, hook id under "name"
    26.3                          typed everything: "minecraft:item", typed rolls, "modifier" for set_count

The dialect is read off the checkout (a loot_tables/ folder; minecraft_version in gradle.properties). Every item
here exists on all six versions — nothing newer than 1.20.1, nothing renamed since (no chain, no scute). Each
table ends in the usual `_extra` hook at weight 15, with an empty companion a pack can replace.
"""
import json
import os
import re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
DATA = os.path.join(ROOT, "src", "main", "resources", "data", "cityworld")

# trade (the ShopTrade enum name, lower case) -> [(item, weight, min, max)]; min/max 1 = a single item
STOCK = {
    "newsagent": [("paper", 10, 2, 8), ("book", 6, 1, 3), ("writable_book", 4, 1, 1), ("map", 4, 1, 2),
                  ("ink_sac", 4, 1, 3), ("feather", 4, 1, 3), ("item_frame", 3, 1, 2), ("cookie", 5, 1, 4),
                  ("sugar", 4, 1, 4), ("name_tag", 1, 1, 1), ("emerald", 4, 1, 2)],
    "greengrocer": [("carrot", 9, 2, 6), ("potato", 9, 2, 6), ("beetroot", 7, 2, 5), ("apple", 8, 1, 4),
                    ("melon_slice", 6, 2, 6), ("pumpkin", 4, 1, 2), ("wheat", 6, 2, 6), ("sweet_berries", 5, 2, 6),
                    ("brown_mushroom", 4, 1, 3), ("wheat_seeds", 4, 2, 6), ("beetroot_seeds", 3, 2, 4),
                    ("bread", 5, 1, 3), ("emerald", 4, 1, 2)],
    "fishmonger": [("cod", 10, 1, 4), ("salmon", 9, 1, 4), ("tropical_fish", 4, 1, 2), ("pufferfish", 2, 1, 1),
                   ("cooked_cod", 5, 1, 3), ("cooked_salmon", 5, 1, 3), ("kelp", 5, 2, 6), ("dried_kelp", 5, 2, 6),
                   ("ink_sac", 3, 1, 3), ("fishing_rod", 3, 1, 1), ("bucket", 2, 1, 1), ("emerald", 4, 1, 2)],
    "butcher": [("beef", 9, 1, 4), ("porkchop", 9, 1, 4), ("chicken", 8, 1, 4), ("mutton", 7, 1, 4),
                ("rabbit", 4, 1, 2), ("cooked_beef", 4, 1, 2), ("cooked_porkchop", 4, 1, 2), ("bone", 6, 1, 4),
                ("leather", 4, 1, 3), ("rabbit_hide", 3, 1, 2), ("coal", 3, 1, 4), ("emerald", 4, 1, 2)],
    "apothecary": [("glass_bottle", 10, 1, 4), ("nether_wart", 5, 1, 4), ("sugar", 6, 1, 4), ("redstone", 5, 1, 4),
                   ("glowstone_dust", 4, 1, 3), ("spider_eye", 4, 1, 2), ("fermented_spider_eye", 2, 1, 1),
                   ("honey_bottle", 4, 1, 2), ("glistering_melon_slice", 2, 1, 1), ("rabbit_foot", 1, 1, 1),
                   ("brown_mushroom", 4, 1, 3), ("red_mushroom", 4, 1, 3), ("emerald", 4, 1, 2)],
    "bookshop": [("book", 12, 1, 4), ("paper", 8, 2, 8), ("writable_book", 5, 1, 1), ("bookshelf", 3, 1, 1),
                 ("ink_sac", 4, 1, 3), ("feather", 4, 1, 3), ("leather", 4, 1, 3), ("map", 2, 1, 1),
                 ("name_tag", 1, 1, 1), ("emerald", 4, 1, 2)],
    "cartographer": [("map", 10, 1, 3), ("paper", 9, 2, 8), ("compass", 5, 1, 1), ("clock", 2, 1, 1),
                     ("spyglass", 2, 1, 1), ("item_frame", 4, 1, 2), ("glass_pane", 4, 1, 4), ("feather", 4, 1, 3),
                     ("ink_sac", 4, 1, 3), ("emerald", 4, 1, 2)],
    "fletcher": [("arrow", 12, 4, 16), ("bow", 4, 1, 1), ("crossbow", 2, 1, 1), ("flint", 7, 1, 6),
                 ("feather", 7, 1, 6), ("stick", 7, 2, 8), ("string", 6, 1, 4), ("tripwire_hook", 2, 1, 2),
                 ("target", 2, 1, 1), ("emerald", 4, 1, 2)],
    "builders_merchant": [("bricks", 6, 2, 8), ("stone_bricks", 6, 2, 8), ("cobblestone", 7, 4, 12),
                          ("oak_planks", 7, 4, 12), ("glass", 5, 2, 8), ("sand", 5, 2, 8), ("gravel", 5, 2, 8),
                          ("clay_ball", 5, 2, 8), ("brick", 5, 2, 8), ("ladder", 5, 2, 8), ("scaffolding", 4, 2, 8),
                          ("torch", 5, 4, 12), ("emerald", 3, 1, 2)],
    "armourer": [("iron_helmet", 4, 1, 1), ("iron_chestplate", 3, 1, 1), ("iron_leggings", 3, 1, 1),
                 ("iron_boots", 4, 1, 1), ("chainmail_helmet", 3, 1, 1), ("chainmail_chestplate", 2, 1, 1),
                 ("chainmail_leggings", 2, 1, 1), ("chainmail_boots", 3, 1, 1), ("leather_helmet", 3, 1, 1),
                 ("leather_chestplate", 3, 1, 1), ("shield", 4, 1, 1), ("iron_ingot", 6, 1, 4),
                 ("iron_nugget", 6, 3, 9), ("coal", 5, 1, 4), ("diamond", 1, 1, 1), ("emerald", 3, 1, 2)],
    "toolsmith": [("iron_pickaxe", 4, 1, 1), ("iron_axe", 4, 1, 1), ("iron_shovel", 4, 1, 1), ("iron_hoe", 3, 1, 1),
                  ("stone_pickaxe", 4, 1, 1), ("stone_axe", 4, 1, 1), ("shears", 4, 1, 1), ("flint_and_steel", 3, 1, 1),
                  ("bucket", 4, 1, 1), ("lantern", 4, 1, 2), ("iron_bars", 4, 2, 8), ("iron_ingot", 6, 1, 4),
                  ("iron_nugget", 6, 3, 9), ("coal", 5, 1, 4), ("emerald", 3, 1, 2)],
    "weaponsmith": [("iron_sword", 5, 1, 1), ("stone_sword", 5, 1, 1), ("iron_axe", 4, 1, 1), ("bow", 3, 1, 1),
                    ("crossbow", 2, 1, 1), ("arrow", 6, 4, 12), ("shield", 3, 1, 1), ("flint", 4, 1, 4),
                    ("iron_ingot", 6, 1, 4), ("iron_nugget", 6, 3, 9), ("coal", 5, 1, 4), ("diamond", 1, 1, 1),
                    ("emerald", 3, 1, 2)],
    "cobbler": [("leather_boots", 8, 1, 1), ("leather", 9, 1, 4), ("leather_leggings", 3, 1, 1),
                ("rabbit_hide", 5, 1, 4), ("string", 6, 1, 4), ("lead", 3, 1, 2), ("saddle", 1, 1, 1),
                ("leather_horse_armor", 1, 1, 1), ("shears", 2, 1, 1), ("emerald", 4, 1, 2)],
    "draper": [("white_wool", 7, 1, 4), ("red_wool", 4, 1, 4), ("blue_wool", 4, 1, 4), ("green_wool", 4, 1, 4),
               ("yellow_wool", 4, 1, 4), ("black_wool", 4, 1, 4), ("white_carpet", 4, 2, 6), ("red_carpet", 4, 2, 6),
               ("string", 6, 1, 4), ("shears", 3, 1, 1), ("red_dye", 3, 1, 3), ("blue_dye", 3, 1, 3),
               ("yellow_dye", 3, 1, 3), ("white_banner", 2, 1, 1), ("painting", 2, 1, 1), ("emerald", 4, 1, 2)],
}


def dialect():
    if os.path.isdir(os.path.join(DATA, "loot_tables")):
        return "1.20.1"
    props = open(os.path.join(ROOT, "gradle.properties"), encoding="utf-8").read()
    version = re.search(r"^minecraft_version=(\S+)", props, re.M).group(1)
    return "26.3" if version.startswith("26.3") else "standard"


def table(trade, stock, kind):
    typed = kind == "26.3"
    entries = []
    for item, weight, low, high in stock:
        entry = {"type": "minecraft:item" if typed else "item", "name": "minecraft:" + item, "weight": weight}
        if high > 1:
            if typed:
                entry["modifier"] = {"type": "set_count",
                                     "count": {"type": "minecraft:uniform", "min": low, "max": high}}
            else:
                entry["functions"] = [{"function": "set_count", "count": {"min": low, "max": high}}]
        entries.append(entry)
    entries.append({"type": "minecraft:empty" if typed else "empty", "weight": 8})
    entries.append({"type": "minecraft:loot_table", "name" if kind == "1.20.1" else "value":
                    "cityworld:chests/shop_%s_extra" % trade, "weight": 15})
    rolls = {"type": "minecraft:uniform", "min": 2, "max": 5} if typed else {"min": 2, "max": 5}
    return {"type": "minecraft:chest", "pools": [{"rolls": rolls, "entries": entries}]}


def main():
    kind = dialect()
    out = os.path.join(DATA, "loot_tables" if kind == "1.20.1" else "loot_table", "chests")
    for trade, stock in sorted(STOCK.items()):
        for name, body in (("shop_%s" % trade, table(trade, stock, kind)),
                           ("shop_%s_extra" % trade, {"type": "minecraft:chest", "pools": []})):
            with open(os.path.join(out, name + ".json"), "w", encoding="utf-8") as fh:
                json.dump(body, fh, indent=2)
                fh.write("\n")
    print("wrote %d shop tables (+ their _extra hooks) in the %s dialect to %s" % (len(STOCK), kind, out))


if __name__ == "__main__":
    main()
