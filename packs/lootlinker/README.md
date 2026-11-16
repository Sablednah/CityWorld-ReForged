# CityWorld: LootLinker

A data-only add-on for CityWorld (no code) that mixes vanilla's chest loot into the city's containers, **and every
mod's additions to that loot with it**. Most loot mods add their items to vanilla's tables (dungeons, mineshafts,
village chests), and CityWorld's containers use their own tables, so without this those items never reach a city.

| CityWorld containers | Get a roll of | Chance | Up to |
|---|---|---|---|
| Vault, entry level | simple dungeon | 35% | 2 stacks |
| Vault, first tier down | abandoned mineshaft | 40% | 2 |
| Vault, second tier | stronghold corridor | 45% | 3 |
| Vault, bottom tier | ancient city | 50% | 3 |
| Vault armouries, bunkers | pillager outpost, and a weaponsmith's and an armourer's chest | 35%, 30%, 30% | 2 each |
| Mines | abandoned mineshaft | 40% | 3 |
| Sewers | simple dungeon, small underwater ruin | 30%, 25% | 2 each |
| Warehouses | shipwreck supply | 40% | 3 |
| Each street shop and mall store with a village trade | that trade's village chest (toolsmith, butcher, fletcher, cartographer, ...) | 50% | 2 |
| Homes: drawers, nightstands, kitchens | a plains or taiga village house | 6%, 4% | 1 |

The radio tower hut is left alone (a modpack's quest may own that chest).

**Install:** drop the jar in `mods` beside CityWorld. It needs **CityWorld 7.2.0 or later**, which adds the
`cityworld:linked_table` loot modifier it is made of. One jar serves every Minecraft version CityWorld ships for.

**Change it:** each link is one file in `data/cityworld_lootlinker/loot_modifiers/`:

```json
{ "type": "cityworld:linked_table", "conditions": [],
  "targets": [ "cityworld:chests/mine" ],
  "table": "minecraft:chests/abandoned_mineshaft",
  "chance": 0.4, "max_stacks": 3 }
```

`targets` are the CityWorld tables it adds to (CityWorld's wiki, Loot page, lists them all), `table` the one it
rolls, `chance` how often, `max_stacks` the most it keeps. To drop a link in your own datapack, replace its file with
one whose `targets` is a table nothing uses, or remove it from `data/neoforge/loot_modifiers/global_loot_modifiers.json`
(1.21) / `data/forge/loot_modifiers/global_loot_modifiers.json` (1.20.1). The files are written by `gen.py`.

GPL-3.0-only, like CityWorld.
