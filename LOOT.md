# Loot tables

Every container CityWorld places, from a house's chest to a furniture mod's cupboard or a schematic's barrels, is
filled from a loot table in CityWorld's own namespace, `cityworld:chests/...`. This page explains how to change
what they hold, and lists every table.

> **Why other loot mods don't reach CityWorld's chests.** Most loot mods add their items to *vanilla's* tables
> (`minecraft:chests/abandoned_mineshaft`, `minecraft:chests/end_city_treasure`, ...). CityWorld's containers use
> their own tables, so those additions never show up there. To get a mod's items into CityWorld's chests, add them
> to CityWorld's tables with one of the methods below. Vanilla structures that generate in a CityWorld world
> (strongholds, trial chambers, ancient cities, and any you turn back on) keep vanilla's tables, so loot mods do
> reach those.

> **On the "junk".** The everyday tables are deliberately ordinary. Most places in a city are homes, offices and
> shops, and a drawer in an office holds paper, candles, a clock or some bread. Since 6.10.0, Modern and
> Apocalypse worlds have their own everyday tables (see below), and about one container in twelve also holds
> something worth having: an emerald, a diamond, an enchanted book. Classic worlds keep the original CityWorld's.
> The good stuff is in the places you go looking for it: the Apocalypse vault (better the deeper you go, best on
> the bottom floor), bunkers, mines, the hospital and the radio tower hut. Shops and mall stores hold their trade's
> stock (a butcher has meat, a bookshop has books). If your pack wants richer cities, the `_extra` hooks below are
> the easy way to add to all of it.

## Three ways to change the loot

All three are ordinary datapack or modpack techniques. Put a datapack in the world's `datapacks` folder, or ship it
in a mod; KubeJS-style tools work too. Loot tables load with the datapack, so `/reload` picks up a change for
containers that have not been opened yet. Containers already opened keep what they rolled.

### 1. Add items with an `_extra` hook (recommended)

Nearly every table ends by rolling an **empty** table named after it plus `_extra`: `chests/building` rolls
`chests/building_extra`, `chests/vault_floor3` rolls `chests/vault_floor3_extra`, and so on. CityWorld ships those
empty. Replace one in your datapack and your items join that table's rolls, without copying or replacing
CityWorld's own contents. A CityWorld update that changes its tables won't clash with yours.

`data/cityworld/loot_table/chests/building_extra.json` (on **1.20.1** the folder is `loot_tables`, plural):

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:diamond", "weight": 1 },
        { "type": "minecraft:item", "name": "minecraft:emerald", "weight": 3 },
        { "type": "minecraft:item", "name": "mymod:canned_beans", "weight": 10 }
      ]
    }
  ]
}
```

The `_extra` table is rolled as **one entry** among the parent table's (weight 15 in `building`), so each roll has
a chance of landing on your hook; within it, your own pools and weights apply. That form works on every Minecraft
version CityWorld supports. If you set counts or other functions, write them the way your Minecraft version
expects. 26.3 changed the loot format, so compare with a vanilla table from the same version.

### 2. Replace a whole table

Put a file at the same path as CityWorld's (for example `data/cityworld/loot_table/chests/hospital.json`) and
yours is used instead. For `building`, `nightstand` and `food`, a Modern world reads `chests/modern/<name>` and a
ruined Apocalypse world `chests/apocalypse/<name>`, so replace the one your world uses (the `_extra` hooks are
shared, so method 1 needs no such care). That gives full control, but you take over the whole table, including keeping its
`_extra` reference if you want packs layered on top of yours to keep working.

### 3. A loot modifier, or your pack's loot tool

Loot modifiers can target CityWorld's tables by id like any other table. With NeoForge's global loot modifiers,
use the `neoforge:loot_table_id` condition (`forge:loot_table_id` on 1.20.1 Forge) with an id such as
`cityworld:chests/radio_tower`. LootJS and similar tools take the same ids. This is how a mod adds a guaranteed
quest item to one kind of chest without touching anyone else's.

If you use a loot mod that has a config list of tables to modify, add the `cityworld:chests/...` ids from the
list below to it.

### Turning loot off

The world settings `treasuresInBuildings`, `treasuresInBunkers`, `treasuresInMines` and `treasuresInSewers`
(and their `oddsOfTreasureIn...` chances) control CityWorld's dedicated loot chests: closets, storerooms and
kitchenettes in buildings, and the treasure chests in bunkers, mines and sewers. See
[Configuring CityWorld](Configuring-CityWorld). They do not stop furniture and other containers getting a table.
To empty a kind of container completely, replace its table with an empty one (`"pools": []`).

### Schematic buildings

A pasted schematic's containers take `cityworld:chests/schematic/<name>` when such a table exists (lower-case
name, spaces as underscores), or the table named by its `.yml` sidecar's `Loot:` key; otherwise the default for
where it stands. So a datapack can give one building its own loot without editing the schematic.

### Lootr

CityWorld's containers are standard loot-table containers, so Lootr (per-player loot) works with them.

## Every table

All ids are `cityworld:chests/<name>`. "Hook" is whether `<name>_extra` exists for you to fill.

### Buildings, homes and shops

`building`, `nightstand` and `food` come in three versions. A **Modern** world (and an Apocalypse world's pristine
`/cityworld` twin) rolls `chests/modern/<name>`: an office's paper, books and candles, a kitchen's bread and
fruit. A ruined **Apocalypse** world rolls `chests/apocalypse/<name>`: scrap, string, bone, arrows, spoiled food.
Every other style rolls the shared `chests/<name>`, the original CityWorld's. Each version has a second pool, one
roll that is nearly always nothing and about one time in twelve a find, and all three end in the same
`<name>_extra` hook.

| Table | Where | Hook |
|---|---|---|
| `building` (+ `modern/building`, `apocalypse/building`) | The default for a building's containers: closets, storerooms, and furniture-mod storage in houses, offices and apartments | yes |
| `nightstand` (+ `modern/`, `apocalypse/`) | Bedside drawers, and the chests and barrels furnished rooms keep against a wall | yes |
| `food` (+ `modern/`, `apocalypse/`) | Kitchenettes, and the vault's food barrels | yes (shared) |
| `shop` | A shop's stock when the shop has no trade of its own | yes |
| `shop_apothecary` · `shop_armourer` · `shop_bookshop` · `shop_builders_merchant` · `shop_butcher` · `shop_cartographer` · `shop_cobbler` · `shop_draper` · `shop_fishmonger` · `shop_fletcher` · `shop_greengrocer` · `shop_newsagent` · `shop_toolsmith` · `shop_weaponsmith` | Street shops, by trade: their counters, shelves and storerooms | yes |
| `mall_art` · `mall_bakery` · `mall_books` · `mall_cafe` · `mall_department` · `mall_electronics` · `mall_fashion` · `mall_florist` · `mall_food` · `mall_furniture` · `mall_gifts` · `mall_grocer` · `mall_hardware` · `mall_jeweller` · `mall_music` · `mall_pets` · `mall_pharmacy` · `mall_sports` · `mall_toys` | Shopping-mall stores, by kind | yes |
| `hospital` | The hospital campus: medicine and supplies | yes |
| `warehouse` | Industrial buildings and warehouses, and the yards of storage lots | yes |
| `storage_shed` | The lock-up units on storage lots | yes |
| `radio_tower` | The radio tower hut's chest (copper, redstone, spares) | yes |
| `schematic/winchester` | The Winchester schematic (an example of a per-schematic table) | — |

### Underground

| Table | Where | Hook |
|---|---|---|
| `sewer` | Sewer treasure chests | yes |
| `mine` | Mine treasure chests | yes |
| `bunker` | Bunkers under the hills | yes |

### The Apocalypse vault

The vault is four to eight levels deep, and its loot gets better floor by floor. Each room kind has a table, and a
deeper floor's table is that room's table **plus** a depth bonus, `vault_floor1` to `vault_floor3` (the bottom
floor always gets `vault_floor3`).

| Table | Where | Hook |
|---|---|---|
| `vault_quarters` · `vault_office` · `vault_armoury` · `vault_ammo` | The vault's rooms at entry depth (ammo: the armoury's shelves and crates) | yes |
| `vault_floor1` · `vault_floor2` · `vault_floor3` | The depth bonus added on each deeper floor; fill these hooks to add to a whole floor at once | yes |
| `<room>_floor1` to `<room>_floor3` (e.g. `vault_quarters_floor2`) | A room on a deeper floor: the room's table plus that floor's bonus | — (use the room's or the floor's hook) |

### The outlands and farms

| Table | Where | Hook |
|---|---|---|
| `farmworks` · `farmworks_output` | Farms: supplies, and the barns' produce | yes |
| `woodworks` · `woodworks_output` | Woodworks: supplies, and the cut timber | yes |
| `stoneworks` · `stoneworks_output` | Stoneworks and gravel works: supplies, and the cut stone | yes |
| `pond` | The fish pond's barrel | yes |

Every table's contents can be read in the mod jar (`data/cityworld/loot_table/chests/`) or on
[GitHub](https://github.com/Sablednah/CityWorld-ReForged/tree/master/src/main/resources/data/cityworld/loot_table/chests).
