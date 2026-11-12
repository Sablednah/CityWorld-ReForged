# Examples: changing CityWorld from a datapack or a mod

Somewhere to start. Each example is complete — the file, where it goes, and how to see that it worked — and each
does one thing. Pick the one nearest what you want and change the ids. For what a datapack is and where it goes,
read the [Beginner's guide to datapacks](Beginners-Guide-to-Datapacks) first; for every setting, tag and registry,
[Configuring CityWorld](Configuring-CityWorld) and [Building Palettes](Building-Palettes).

**Three things every example relies on:**

1. **Only new chunks change, and only after the world restarts.** Quit to the title and reopen (or restart the
   server); `/reload` is not enough for palettes, settings and schematics. The cleanest test is a fresh world with
   the pack already in `datapacks/`.
2. **Folder names differ by Minecraft version.** Below, `tags/block/`, `tags/item/` and `loot_table/` are the
   1.21+ names; on **1.20.1** they are `tags/blocks/`, `tags/items/` and `loot_tables/`. A pack for both carries
   both. Data maps and `cityworld/…` registries use the same path everywhere.
3. **Other mods' blocks go in as optional.** `{ "id": "mod:block", "required": false }` in a tag, or a
   `neoforge:mod_loaded` condition in a data map or registry file. One required id that does not exist throws the
   **whole tag** away.

A block's exact id is under *Targeted Block* with F3 open. A mod jar ships the same files under its own
`data/` folder and needs no dependency on CityWorld: the files do nothing without it.

---

## 1. Add a block to the buildings

Make a mod's planks one of the woods CityWorld builds with. The pools are block tags; adding to one adds a choice.

`data/cityworld/tags/block/build/planks.json`

```json
{
  "replace": false,
  "values": [
    { "id": "biomesoplenty:redwood_planks", "required": false },
    { "id": "biomesoplenty:mahogany_planks", "required": false }
  ]
}
```

Every `build/*` pool is listed under [The palettes](Building-Palettes#the-palettes): `wool`, `terracotta`,
`concrete`, `modern_stones`, `chemicals` (what the tanks hold)… A tag can also name another tag:
`{ "id": "#biomesoplenty:planks", "required": false }` takes everything the mod tags, now and later.

**Expect it to be rare.** A pool has a fixed share of the city, so the blocks you add split that share. To make a
block *common*, use a substitute (example 3) or a per-part palette (example 4).

**Check:** new chunks, F3 on a house wall.

## 2. Take a block out (a blacklist)

Keep everything else and drop one block, without `"replace": true`:

`data/cityworld/tags/block/build/modern_stones.json`

```json
{
  "values": [],
  "remove": [
    "minecraft:sulfur",
    "minecraft:sulfur_bricks",
    "minecraft:polished_sulfur",
    "minecraft:chiseled_sulfur"
  ]
}
```

`values` must be present even when empty. `remove` works on every line, Forge 1.20.1 included; removing a block that
is not there is ignored. The same works on any CityWorld tag: a furniture role, a fittings pool, a biome pool.

To stop CityWorld using a **vanilla block entirely** (no iron blocks anywhere), swap it instead — example 3; the
[Neutral Palette](https://github.com/Sablednah/CityWorld-ReForged/releases/tag/neutral-palette-1.0.0) add-on is a
complete pack of exactly that.

## 3. Replace a block everywhere — or make a mix of it

A **substitute** swaps a block wherever CityWorld places it: generated buildings, roads, decoration and schematics.

`data/cityworld/data_maps/block/substitute.json` (same path on every version)

```json
{
  "replace": false,
  "values": {
    "minecraft:iron_block": "minecraft:smooth_stone",
    "minecraft:stone_bricks": [
      { "block": "minecraft:stone_bricks", "weight": 60 },
      { "block": "minecraft:mossy_stone_bricks", "weight": 25 },
      { "block": "minecraft:cracked_stone_bricks", "weight": 15 }
    ],
    "minecraft:netherrack": { "with": "minecraft:granite", "realms": ["overworld"] }
  }
}
```

- A plain id swaps one for one; a weighted list places a mix, fixed by position so the same seed builds the same
  mix; `realms` limits an entry to `overworld`, `nether` or `end`.
- A block from another mod needs a condition so the file still loads without it:

  ```json
  "examplemod:thing": {
    "neoforge:conditions": [ { "type": "neoforge:mod_loaded", "modid": "examplemod" } ],
    "value": { "with": "examplemod:other" }
  }
  ```

- Stairs and slabs keep their facing and shape when swapped for stairs and slabs.

**Check:** `/reload` reaches this one, so the next new chunk shows it; F3 on a block that should have been swapped.

## 4. Set what one part of a building is made of

A **per-part palette** says what one part is made from, and only that part: office walls, house roofs, the road.
They ship empty; filling one draws that part from your blocks alone.

Apocalypse office walls in weathered brick, every style's roads in blackstone:

`data/cityworld/tags/block/palette/apocalypse/building/wall.json`

```json
{
  "values": [
    "minecraft:mossy_stone_bricks",
    "minecraft:cracked_stone_bricks",
    { "id": "chipped:etched_stone_bricks", "required": false }
  ]
}
```

`data/cityworld/tags/block/palette/street/surface.json`

```json
{ "values": [ "minecraft:blackstone" ] }
```

The 28 parts (`building/wall`, `house/roof`, `shack/floor`, `factory/smokestack`, `street/sidewalk`…) are listed
under [Per-part palettes](Building-Palettes#per-part-palettes-from-614). A tag has no weights: for "mostly A, some
B" add a weighted substitute (example 3) on top.

## 5. Add a building of your own

Build it, save it with WorldEdit (`//copy`, `//schem save mybank`), and drop the file in the family folder for the
district it belongs in. This is a file in the instance's config, not a datapack:

```
config/cityworld/schematics/
└── Lowrise/
    ├── mybank.schem
    └── mybank.schem.yml
```

`mybank.schem.yml`

```yaml
Title: "First National"
OddsOfAppearance: 0.15
GroundLevelY: 1
Decayable: true
Driveway: south 4-9
```

- The **folder** decides the district (`Highrise`, `Midrise`, `Lowrise`, `Municipal`, `Industrial`,
  `Neighborhood`, `Park`, `Farm`, `Nature`…) and the biggest footprint it may have (4×4 chunks for most, 9×9 for
  `Municipal` and `Nature`). A copy in two folders appears in both.
- `GroundLevelY` is how many bottom layers sit **below** ground. In a city family the ground is the pavement, one
  above the road, so a forecourt that should meet the road flush wants `1`.
- `Driveway: south 4-9` says its own ground meets its south edge at blocks 4–9: CityWorld turns and sites the
  build so that edge faces a road, drops the kerb there and paves out to the road.
- `OddsOfAppearance` is per district square (10×10 chunks). Keep it modest; `0.3` is common, `0.01` a landmark.

Every key is on [Custom Schematics](Custom-Schematics#the-sidecar-odds-depth-and-a-title). **Check:** the log lists
every schematic it indexed at startup (`SchematicLibrary: indexed …`); `/cityschem mybank` pastes it by hand;
`/cityfind "First National"` finds a placed one.

## 6. Change what is inside a building you did not make

A **fit-out** swaps blocks and hangs item frames inside any schematic, yours or CityWorld's, without a new file.
Put a mod's slot machine in the Winchester and a shotgun over the bar:

`data/mypack/cityworld/schematic_fitout/winchester.json`

```json
{
  "schematic": "winchester",
  "blocks": [
    { "pos": [5, 11, 10], "block": "phoenix:phoenix[facing=east]", "requires_mod": "phoenix" },
    { "pos": [5, 12, 10], "block": "minecraft:air",                 "requires_mod": "phoenix" }
  ],
  "frames": [
    { "pos": [2, 14, 6], "facing": "east", "item": "gbg:shotgun", "requires_mod": "gbg" }
  ]
}
```

Positions are the schematic's own (x, y up from its bottom layer, z) and are turned with the building. A block's
NBT goes in `/setblock` braces; an item's data in `/give` syntax for the version. [Schematic
fit-outs](Building-Palettes#schematic-fit-outs) has the rest, including a mod block whose facing lives in its data.

**Check:** the log's `CityWorld fit-outs: N file(s) for M schematic(s)` at startup, then `/cityschem winchester`.

## 7. Spawn something in a lot, a district or a schematic

The `cityworld:spawn` registry puts an entity, a parked vehicle or a block with its data wherever you say.
CityWorld ships none. Three files, one thing each:

**Guards inside civic and office buildings** — `data/mypack/cityworld/spawn/guards.json`

```json
{
  "requires_mod": "guardvillagers",
  "entity": "guardvillagers:guard{PersistenceRequired:1b}",
  "lots": ["structure"],
  "districts": ["municipal", "highrise"],
  "at": "indoors",
  "odds": 0.25,
  "count": 1,
  "count_max": 2
}
```

**A car on every condo's forecourt** — `data/mypack/cityworld/spawn/condo_car.json`

```json
{
  "vehicle": "car",
  "schematics": ["Condo"],
  "at": "driveway",
  "odds": 1.0
}
```

**A vending machine in the shops** — `data/mypack/cityworld/spawn/vending.json`

```json
{
  "requires_mod": "vending",
  "block": "vending:vending_machine[facing=north]",
  "lots": ["StoreBuildingLot", "CornerShopLot", "MallLot"],
  "at": "indoors",
  "odds": 0.3
}
```

- `lots` are lot class names exactly as F3 shows them, or the kinds `structure`, `road`, `roundabout`, `nature`;
  `districts` are the building families; `schematics` are file names without the extension; `places` are the
  town or district names from `naming`; `styles`, `environments` and `land` narrow further.
- `at` is `outdoors` (ground, roof or road), `indoors` (a floor with something over it), `anywhere`, or `driveway`.
- `odds` is **per matching chunk** — a district has a hundred — so start low.
- Entities are written into the chunk as saved data, not spawned: give a mob its gear in the data, and mods that
  react to spawns do not see it.

Every field: [Spawns](Building-Palettes#putting-anything-in-the-city-spawns-from-70). **Check:** the log names every
file read and every one skipped and why (`CityWorld spawns:`).

## 8. Add an item to the loot

Every table ends by rolling an empty `_extra` table named after it. Replace the hook and your items join that
table's rolls without touching CityWorld's own list:

`data/cityworld/loot_table/chests/building_extra.json` (1.20.1: `loot_tables/`)

```json
{
  "type": "minecraft:chest",
  "pools": [
    {
      "rolls": 1,
      "entries": [
        { "type": "minecraft:item", "name": "minecraft:emerald", "weight": 3 },
        { "type": "minecraft:item", "name": "mymod:canned_beans", "weight": 10 }
      ]
    }
  ]
}
```

- The hook is one entry among the parent's, so it lands in **some** chests (about one in five for `building`). For
  every chest, replace the parent table instead, or use a loot modifier.
- The hooks: `building_extra`, `nightstand_extra`, `food_extra`, `shop_<trade>_extra`, `mall_<shop>_extra`,
  `hospital_extra`, `vault_floor3_extra`… every table is on [Loot Tables](Loot-Tables).
- **26.3 has a different loot format** (typed rolls); copy a vanilla table from that version as the shape.

**Your own building's chests:** either `Loot: mypack:chests/mybank` in its sidecar, or a table at
`data/cityworld/loot_table/chests/schematic/mybank.json` (the file name in lower case).

**A mod block that must stay empty** (a machine, not a chest) goes in `data/cityworld/tags/block/loot/never.json`;
one that holds a single food item (a cutting board) in `loot/food.json`.

**Check:** `/loot give @s loot cityworld:chests/building_extra` rolls the hook itself, so you can see your items
without finding a chest.

## 9. Add an animal to the zoos

Two files: the animal, and the pen it lives in.

`data/mypack/cityworld/zoo/animal/giraffe.json`

```json
{
  "entity": "examplezoo:giraffe",
  "name": "Giraffes",
  "weight": 2,
  "count": { "min": 2, "max": 4 },
  "size": "tall",
  "habitat": "mypack:savanna",
  "requires_mod": "examplezoo"
}
```

`data/mypack/cityworld/zoo/habitat/savanna.json`

```json
{
  "ground": "minecraft:coarse_dirt",
  "scatter": [ { "block": "minecraft:grass_block", "chance": 0.3 } ],
  "water": "pool",
  "plants": [ { "block": "minecraft:short_grass", "count": 6 } ],
  "trees": [ { "log": "minecraft:acacia_log", "leaves": "minecraft:acacia_leaves", "count": 1 } ],
  "fence": "minecraft:dark_oak_fence",
  "gate": "minecraft:dark_oak_fence_gate"
}
```

To take one of CityWorld's own out, ship `data/cityworld/cityworld/zoo/animal/panda.json` with `"weight": 0`. The
fields are under [Zoos](Building-Palettes#zoos). **Check:** `/cityfind lot zoo tp`.

## 10. Structures: turn villages on, keep a mod's dungeons out of town

**Villages (and any vanilla or mod structure):** `data/cityworld/tags/worldgen/structure_set/allowed.json`

```json
{
  "replace": false,
  "values": [
    "minecraft:villages",
    { "id": "dungeons_arise:small_blimp", "required": false }
  ]
}
```

The Structures page of Customize does the same by clicking. CityWorld reserves the structure's real footprint and
shapes the ground to meet it.

**Keep a set out of the city, in the wild only:** `data/cityworld/tags/worldgen/structure_set/wilderness_only.json`

```json
{ "replace": false, "values": [ { "id": "arboria:lake_common", "required": false } ] }
```

**Tell CityWorld how the ground should meet your structure:** `data/cityworld/data_maps/worldgen/structure/structure_fit.json`

```json
{
  "values": {
    "mymod:watchtower": { "clearance": 6, "beard": true }
  }
}
```

`beard` builds the ground up to the structure's floor, `shave` cuts it down to its box, `clearance` keeps that
many blocks of flat ground round it, and `reserve` keeps the city off the chunks an underground structure's
entrance comes up through.

**Check:** `/cwlocate structure minecraft:village` in a new world.

## 11. Change a setting for a world type

Settings live in `data/cityworld/cityworld/world_settings/<entry>.json`, one entry per world type: `modern` for the
Modern city, `apocalypse` for Apocalypse, `default` for the rest. **Your file replaces the shipped entry**, so copy
the shipped one from `config/cityworld/settings-example/` and change what you need. Rarer car parks and no
oceans, in `modern.json`:

```json
{
  "...": "everything else as shipped",
  "roads": { "carParks": { "enabled": true, "oddsOfSurface": 0.005, "oddsOfMultiStorey": 0.005 } },
  "cities": { "oceans": { "enabled": false } }
}
```

Every setting and its default: [Configuring CityWorld](Configuring-CityWorld). A world made with Customize keeps its
settings inside the world, so a settings pack added later does not change it.

## 12. Make your mod's furniture appear (mod authors)

Two files in your jar, no dependency: a tag saying what your blocks are, and a data map saying which way they face.

`data/cityworld/tags/block/furniture/chair.json`

```json
{ "replace": false, "values": [ "yourmod:oak_dining_chair", "yourmod:birch_dining_chair" ] }
```

`data/cityworld/data_maps/block/furniture.json` (not on 1.20.1, where it would replace CityWorld's map: ship the tag only)

```json
{
  "values": {
    "yourmod:oak_dining_chair": { "facingOffset": 180 }
  }
}
```

The facing rule, multi-block layouts (sofas, double beds) and the vocabulary route for a whole family of sets are
under [Furniture](Building-Palettes#furniture). **Check:** the log's furniture pool counts at startup, then any
dining room.

## 13. Vehicles, sign packs, biomes

Pointers, because each has its own worked example:

- **A parked vehicle** from an Immersive Vehicles pack or Ultimate Car Mod: one file in `cityworld/vehicle/`, or
  let `scripts/gen_iv_vehicles.py` write it from the pack's jar —
  [Parked vehicles](Building-Palettes#parked-vehicles-immersive-vehicles).
- **Road signs** on IV poles from another sign pack: one file in `cityworld/pole_signs/` —
  [Road signs](Building-Palettes#road-signs-on-immersive-vehicles-poles-from-70).
- **Force a biome** CityWorld's climate never picks, **give a biome its ground**, or point CityWorld at a biome
  datapack's world preset — [Biome Mods](Biome-Mods#datapack-recipes).
- **A whole look** — plainer cities, no free iron — is the Neutral Palette add-on, a pack of substitutes you can
  copy and change.

## Checking that a pack is being read

- **The log** at world start: `CityWorld spawns:`, `CityWorld fit-outs:`, `SchematicLibrary: indexed` and the
  furniture pool counts. A file that did not parse is named there once.
- **`/loot give @s loot cityworld:chests/<table>`** rolls a loot table on the spot.
- **`/cwlocate structure <id>`** says whether a structure set is in the world at all.
- **`/cityschem <name>`** pastes a schematic, fit-out included, where you stand.
- **F3** shows the lot class, district and place under you, and a block's id under *Targeted Block*.
- **`-Dcityworld.diagnostics=true`** on the JVM lists every pool and says which are empty.
- **A tag that vanished** is almost always one required id that does not exist: mark other mods' blocks
  `"required": false`.
