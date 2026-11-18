# CityWorld: LootLinker

**Vanilla's chest loot, and everything your loot mods add to it, in CityWorld's cities.**

[CityWorld](https://www.curseforge.com/minecraft/mc-mods/cityworld-reforged)'s chests use their own loot tables, which is why the items loot mods add to vanilla's chests
(dungeons, mineshafts, village houses, shipwrecks) never turned up in a city. LootLinker links those tables in. When a
city chest is first opened, it may also roll the vanilla chest that fits it, and that roll brings every mod's additions
with it.

| In the city | Gets a roll of | How often | Up to |
|---|---|---|---|
| Vault, entry level | a dungeon chest | 35% | 2 stacks |
| Vault, first floor down | an abandoned mineshaft chest | 40% | 2 |
| Vault, second floor | a stronghold corridor chest | 45% | 3 |
| Vault, bottom floor | an ancient city chest | 50% | 3 |
| Vault armouries, bunkers | a pillager outpost's, a weaponsmith's and an armourer's chest | 35%, 30%, 30% | 2 each |
| Mines | an abandoned mineshaft chest | 40% | 3 |
| Sewers | a dungeon chest, a small underwater ruin | 30%, 25% | 2 each |
| Warehouses | a shipwreck's supplies | 40% | 3 |
| Street shops and mall stores | their trade's village chest (toolsmith, butcher, fletcher, cartographer, ...) | 50% | 2 |
| Homes: drawers, nightstands, kitchens | a plains or taiga village house | 6%, 4% | 1 |

The deeper vault floors are the better ones, as in CityWorld itself, and homes only rarely get anything, so houses
don't fill up with dungeon loot. The radio tower hut is left alone, since a modpack's quest may own that chest.

**Your mods come with it.** Whatever a loot mod adds to those vanilla chests arrives too: in testing, Railcraft's
crowbars, spike mauls and steel gear turned up in mine and vault chests, and Farmer's Delight's food in the dungeon rolls.

## Install

1. **CityWorld 7.2.0 or later.**
2. Drop the LootLinker jar in `mods` beside it.

One jar works on every Minecraft version CityWorld ships for: 1.20.1 (Forge) and 1.21.1, 1.21.11, 26.1, 26.2, 26.3
(NeoForge). Chests in land you've already explored but haven't opened yet use the links too, since a city chest rolls
its loot the first time it's opened.

## Change it

LootLinker is data, not code. Each link is one small JSON file in `data/cityworld_lootlinker/loot_modifiers/`:

```json
{ "type": "cityworld:linked_table", "conditions": [],
  "targets": [ "cityworld:chests/mine" ],
  "table": "minecraft:chests/abandoned_mineshaft",
  "chance": 0.4, "max_stacks": 3 }
```

`targets` are the CityWorld tables it adds to, `table` is the one it rolls, `chance` is how often, and
`max_stacks` is the most it keeps. A pack can change the numbers, link a modded chest in, or switch a link off. The
CityWorld wiki's **Loot Tables** page lists every city table.

## Licence

GPL-3.0-only, like CityWorld. Source: [GitHub](https://github.com/Sablednah/CityWorld-ReForged/tree/master/packs/lootlinker).
