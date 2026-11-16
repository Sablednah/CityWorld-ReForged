#!/usr/bin/env python3
"""Write LootLinker's loot modifiers from the table below: packs/lootlinker/gen.py (then build.sh).

Each link adds a roll of a vanilla chest table, rolled as itself so every mod's loot modifiers on it run, to the
CityWorld tables named in `targets`, at `chance`, keeping at most `max_stacks` of what it gave. The format is
CityWorld's `cityworld:linked_table` (CityWorld 7.2.0+), one file per link, the same on every loader."""
import json, os, shutil

C = "cityworld:chests/"
V = "minecraft:chests/"
VILLAGE = V + "village/village_"
ROOMS = ["ammo", "armoury", "office", "quarters"]

def tier(n):  # the vault's tables at one depth tier: entry (0) has no suffix
    s = "" if n == 0 else f"_floor{n}"
    return ([C + f"vault_floor{n}"] if n else []) + [C + f"vault_{r}{s}" for r in ROOMS]

ARMOURY = [C + "vault_armoury"] + [C + f"vault_armoury_floor{n}" for n in (1, 2, 3)] + [C + "bunker"]
HOMES = [C + t for t in ["building", "nightstand", "food"]] + \
        [C + f"{style}/{t}{find}" for style in ("modern", "apocalypse") for t in ("building", "nightstand", "food") for find in ("", "_find")]
SHOPS = {  # a CityWorld shop or mall store: its village counterpart
    "shop_apothecary": "temple", "shop_armourer": "armorer", "shop_bookshop": "cartographer",
    "shop_builders_merchant": "mason", "shop_butcher": "butcher", "shop_cartographer": "cartographer",
    "shop_cobbler": "tannery", "shop_draper": "shepherd", "shop_fishmonger": "fisher", "shop_fletcher": "fletcher",
    "shop_greengrocer": "plains_house", "shop_newsagent": "cartographer", "shop_toolsmith": "toolsmith",
    "shop_weaponsmith": "weaponsmith",
    "mall_hardware": "toolsmith", "mall_fashion": "shepherd", "mall_pharmacy": "temple", "mall_books": "cartographer",
    "mall_sports": "fletcher", "mall_grocer": "plains_house", "mall_bakery": "plains_house",
}

LINKS = [  # name, targets, vanilla table, chance, max stacks
    ("vault_entry", tier(0), V + "simple_dungeon", 0.35, 2),        # the vault gets better the deeper you go
    ("vault_floor1", tier(1), V + "abandoned_mineshaft", 0.4, 2),
    ("vault_floor2", tier(2), V + "stronghold_corridor", 0.45, 3),
    ("vault_floor3", tier(3), V + "ancient_city", 0.5, 3),
    ("armoury_outpost", ARMOURY, V + "pillager_outpost", 0.35, 2),  # vault armouries and bunkers
    ("armoury_weaponsmith", ARMOURY, VILLAGE + "weaponsmith", 0.3, 2),
    ("armoury_armorer", ARMOURY, VILLAGE + "armorer", 0.3, 2),
    ("mine", [C + "mine"], V + "abandoned_mineshaft", 0.4, 3),
    ("sewer_dungeon", [C + "sewer"], V + "simple_dungeon", 0.3, 2),
    ("sewer_ruin", [C + "sewer"], V + "underwater_ruin_small", 0.25, 2),
    ("warehouse", [C + "warehouse"], V + "shipwreck_supply", 0.4, 3),
    ("homes_plains", HOMES, VILLAGE + "plains_house", 0.06, 1),     # rare: a home's drawer stays a home's drawer
    ("homes_taiga", HOMES, VILLAGE + "taiga_house", 0.04, 1),
]
for village in sorted(set(SHOPS.values())):
    LINKS.append((f"shops_{village}", [C + s for s in sorted(SHOPS) if SHOPS[s] == village], VILLAGE + village, 0.5, 2))

here = os.path.dirname(os.path.abspath(__file__))
data = os.path.join(here, "src", "data")
shutil.rmtree(data, ignore_errors=True)
mods = os.path.join(data, "cityworld_lootlinker", "loot_modifiers")
os.makedirs(mods)
for name, targets, table, chance, stacks in LINKS:
    with open(os.path.join(mods, name + ".json"), "w") as f:
        json.dump({"type": "cityworld:linked_table", "conditions": [], "targets": targets, "table": table,
                   "chance": chance, "max_stacks": stacks}, f, indent=2)
        f.write("\n")
# 1.21 NeoForge and 1.20.1 Forge load only the modifiers a list names; 26.x loads every file in the folder and
# would read these lists as modifiers, so they carry a condition only 26.x reads, and it says never.
for loader in ("neoforge", "forge"):
    d = os.path.join(data, loader, "loot_modifiers")
    os.makedirs(d)
    with open(os.path.join(d, "global_loot_modifiers.json"), "w") as f:
        json.dump({"neoforge:conditions": [{"type": "neoforge:never"}], "replace": False,
                   "entries": [f"cityworld_lootlinker:{n}" for n, *_ in LINKS]}, f, indent=2)
        f.write("\n")
print(f"{len(LINKS)} links")
