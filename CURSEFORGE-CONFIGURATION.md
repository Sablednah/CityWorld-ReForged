# Configuring CityWorld

CityWorld has around 100 tunable settings — which features generate, spawn/treasure odds, terrain
toggles, city radius, decay intensity, even the villager-name and mob lists. Settings are **per-world**
(not a global instance config), delivered as a **datapack**, so different worlds — and different
dimensions on the same server — can each have their own city.

## Three ways to set them

**1. Just play — the defaults are tuned already.** Pick a world type (`cityworld:city`, or one of the
style presets like `cityworld:apocalypse`) and go. Nothing below is required.

**2. The Customize screen (single-player, easiest).** On the create-world screen, choose CityWorld as
the world type and hit **Customize**. Every setting is there with a widget, grouped the same way as
below. Locked settings for your chosen style (e.g. APOCALYPSE forcing decay on) grey out with a tooltip
explaining why, so it's always clear what you can and can't change.

**3. Hand-edit a datapack (servers, or version-controlled configs).** On first run, a full example is
written to:

```
config/cityworld/settings-example/
├── pack.mcmeta
├── data/cityworld/cityworld/world_settings/default.json
└── settings-reference.txt        ← everything below, as plain text
```

Copy that whole folder into `<world>/datapacks/`, edit `default.json`, and start (or `/reload`) the
world. Every field is optional — list only what you want to change; anything you omit keeps its
default. A file of just `{}` is a stock world.

> **Tip:** the fastest workflow is to shape a world you like in single-player via Customize, then run
> `/cityexport <name>` to bottle its exact settings into a datapack you can hand to a server.

### When changes apply

Settings are read when the world **loads**, and only affect **new chunks** — existing chunks never
regenerate. Spawn odds, names, treasure odds and decay bite as you explore into fresh land;
terrain-shaping knobs (seas, mountains, caves, radius) want a brand-new world to be seen properly.

### Naming several profiles

`default.json` overrides the built-in `cityworld:default`, which every CityWorld world preset and the
`/cityworld` dimension reference — so editing it changes that world. To keep several named profiles
instead, rename the file (e.g. `ruined.json`) and point a dimension's generator at it with
`"settings": "cityworld:ruined"`.

---

## Recipes

A few common asks, as complete `default.json` files.

**A gentler apocalypse — ruined buildings and roads, but no fire, on a normal green world:**

```json
{
  "terrain": {
    "includeDecayedRoads": true,
    "includeDecayedBuildings": true,
    "includeDecayedNature": false
  },
  "decay": {
    "buildingIntensity": 0.5,
    "roadIntensity": 0.4,
    "oddsOfDecayFire": 0.0,
    "oddsOfPristineRoad": 0.3
  }
}
```

**Cities far apart, tiled on a grid (SPARSE-style), in a normal-style world:**

```json
{
  "radius": {
    "minInbetweenChunkDistanceOfCities": 100
  }
}
```

**Your own villager names, sprinkled in among the built-in hundreds:**

```json
{
  "naming": {
    "append": true,
    "villagerGivenNames": ["Sablednah", "Darren"]
  }
}
```

**A tougher, more hostile sewer system:**

```json
{
  "mobs": {
    "sewers": ["minecraft:zombie", "minecraft:zombie", "minecraft:drowned", "minecraft:cave_spider"]
  }
}
```

**Skyscraper city — taller buildings than the MODERN default:**

```json
{
  "world": {
    "maxBuildingFloors": 60
  }
}
```

**Turn off caves' winding tunnels (back to the classic noise-blob caves) on a MODERN world:**

```json
{
  "terrain": {
    "windingCaves": false
  }
}
```

---

## Full settings reference

### `features` — what the planner is allowed to build

| Setting | Default | What it does |
|---|---|---|
| `includeRoads` | `true` | Road network. Off = no streets (and no sewers). |
| `includeRoundabouts` | `true` | Roundabouts at some intersections. |
| `includeSewers` | `true` | Sewers beneath the roads. |
| `includeCisterns` | `true` | Water cisterns under some buildings. |
| `includeBasements` | `true` | Building basements. |
| `includeMines` | `true` | Abandoned mine networks underground. |
| `includeBunkers` | `true` | Buried bunkers under midlands/highlands and factories. |
| `includeBuildings` | `true` | Buildings at all. Off = roads/nature only. |
| `includeHouses` | `true` | Houses in neighborhoods (a subset of buildings). |
| `includeFarms` | `true` | Farm districts. |
| `includeMunicipalities` | `true` | Civic district (town halls, libraries, museums, ...). |
| `includeIndustrialSectors` | `true` | Factories, warehouses, storage yards. |
| `includeAirborneStructures` | `true` | Balloons/blimps over the fields. |
| `includeBuildingInteriors` | `true` | Furnish building interiors. Off = empty shells (faster). |
| `includeSchematics` | `false` | Drop the bundled classic building schematics into cities. |
| `includeNamedRoads` | `true` | Street-name signs. |

### `terrain` — the land, its fluids, and decay

| Setting | Default | What it does |
|---|---|---|
| `includeCaves` | `true` | Carve caves. |
| `windingCaves` | `false`* | Winding "noodle" cave tunnels that wander and branch (vanilla-like) instead of the classic noise blobs. *On by default for MODERN/APOCALYPSE; a free toggle for any style. Needs `includeCaves`. Never style-locked. |
| `includeLavaFields` | `true` | Underground lava fields (basalt-lined pools in MODERN/APOCALYPSE). |
| `includeSeas` | `true` | Oceans/lakes. Off = a dry, land-only world. |
| `includeMountains` | `true` | Mountainous terrain. Off = flatter. |
| `includeOres` | `true` | Ore deposits in the strata. |
| `includeBones` | `true` | Fossils/bone deposits. |
| `includeFires` | `true` | Lit campfires/fire pits, and burning demolition debris. |
| `includeAbovegroundFluids` | `true` | Surface water/lava placement. |
| `includeUndergroundFluids` | `true` | Underground water/lava placement. |
| `includeWorkingLights` | `true` | Lit lamps/lights in builds. Off = a darker world. |
| `includeDecayedRoads` | `false` | Break roads up with rubble. |
| `includeDecayedBuildings` | `false` | Chew ruin-holes into buildings — the "apocalypse" switch. |
| `includeDecayedNature` | `false` | Drain the seas + desert the world (whole-world ruin mood). Leave `false` for ruins on a normal wet, green world. |
| `oddsOfPristineBuilding` | `0.0001` | When decay is on, the chance a building/schematic is spared and stays intact. A schematic's `.yml` can override this per-building with `PristineChance: <0..1>` (`Decayable: false` = never decays). |

### `overgrowth` — nature reclaiming the built world (runs after decay)

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `false` | Drape buildings/roads in moss, vines, leaf litter, azalea and small reclaim trees, plus dripstone in mines and basements. Works with or without decay. |
| `intensity` | `1.0` | Density multiplier: 1.0 = default, 2–3 = heavy (more/denser vines and plants). |
| `capVines` | `false` | Finish each outer wall-vine string with a glow lichen tip, so live vine growth can't lengthen it over time. |

### `decay` — fine control of demolition (pairs with the `includeDecayed*` toggles above)

| Setting | Default | What it does |
|---|---|---|
| `buildingIntensity` | `1.0` | How ruined a decayed **building** is — scales both the number and size of collapse holes. Below 1 = light weathering, above 1 = heavy rubble (much above ~1.3 shreds buildings past recognition). APOCALYPSE uses `0.5`, DESTROYED `1.1`. |
| `roadIntensity` | `1.0` | The same, for decayed **roads**. APOCALYPSE `0.4`, DESTROYED `1.1`. |
| `oddsOfDecayFire` | `0.20` | Fraction of collapse rubble that catches fire. Only bites when `includeFires` **and** `includeDecayedFires` are both on. DESTROYED uses `0.07` — even 0.2+ reads as a carpet of flame. |
| `oddsOfPristineRoad` | `0.0` | Chance a road chunk is spared and stays intact, so a ruined grid keeps walkable stretches. APOCALYPSE uses `0.3`. |

### `shops` — themed retail with villager job blocks (MODERN dressing)

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `false` | Villager job-site blocks so a store/farm reads as its trade (cartography table = map seller, fletching table = fletcher, smoker = butcher, ...), a composter at farm edges, and a rare fish-pond farm. The shop classification itself is always computed for `/cityinfo`; this only governs block placement. |

### `spawns` — who turns up, and how often (odds `0.0`–`1.0`)

| Setting | Default | What it does |
|---|---|---|
| `spawnBeings` | `0.5` | Villagers/witches appearing in populated spots. |
| `spawnBaddies` | `0.0476` | Hostile mobs at the surface. Raise for a rougher world. |
| `spawnAnimals` | `0.667` | Farm/wild animals. |
| `spawnVagrants` | `0.2` | Stray animals/people wandering the streets. |
| `nameVillagers` | `true` | Give villagers generated names (see `naming`). |
| `showVillagersNames` | `true` | Show those names as floating nametags. |

### `treasures` — chests and spawners

| Setting | Default | What it does |
|---|---|---|
| `treasuresInMines` / `spawnersInMines` | `true` | Loot chests / mob spawners in mines. |
| `treasuresInBunkers` / `spawnersInBunkers` | `true` | Loot chests / mob spawners in bunkers. |
| `treasuresInSewers` / `spawnersInSewers` | `true` | Loot chests / mob spawners in sewers. |
| `treasuresInBuildings` | `true` | Loot chests in buildings. |
| `oddsOfTreasureInMines/Bunkers/Sewers/Buildings` | `0.5` | Chance a given spot gets a chest. |
| `oddsOfAlcoveInMines` | `0.5` | Chance a mine tunnel opens into a side alcove. |

### `world` — trees, ground cover, floating subsurface, ruralness

| Setting | Default | What it does |
|---|---|---|
| `treeStyle` | `"NORMAL"` | Tree family: `NORMAL`, `SPOOKY`, `CRYSTAL`. |
| `spawnTrees` | `0.5` | How densely trees grow. |
| `subSurfaceStyle` | `"LAND"` | What fills space under a FLOATING world's land: `NONE`, `LAND`, `CLOUD`, `LAVA`. Only the FLOATING style reads this. |
| `ruralnessLevel` | `0.0` | Skews the world more rural (more nature, fewer cities) — up toward `1.0`. |
| `maxBuildingFloors` | `20` | Tallest a building may rise, in floors (4 blocks each) above street level. 20 = the classic 1.8 look; MODERN ships taller. Sensible range 8–60. |

### `radius` — where cities may appear (distances in chunks; 16 blocks each)

Leave these at their defaults for "cities everywhere" — the SPARSE style uses them to confine cities to
a region.

| Setting | Default | What it does |
|---|---|---|
| `centerPointOfChunkRadiusX/Z` | `0` | Centre the radius here. |
| `constructChunkRadius` | `1875000` (no limit) | Max distance for quarries/gravel constructs. |
| `roadChunkRadius` | `1875000` | Max distance for the road network. |
| `cityChunkRadius` | `1875000` | Max distance for cities/buildings/farms. |
| `buildOutsideRadius` | `false` | `true` = build the ring **outside** the radius instead of the disc inside it. |
| `minInbetweenChunkDistanceOfCities` | `0` | `>0` tiles cities on a grid this many chunks apart (SPARSE uses 100). |

### `naming` — your own villager and street names

Each list is empty by default (= keep the built-in hundreds). List your own to **replace** a list, or
add `"append": true` to **add** to it instead:

```json
{
  "naming": {
    "villagerGivenNames": ["Ada", "Bob", "Cleo"],
    "villagerSurnames": ["Vance", "Okafor"]
  }
}
```

| List | What it's for |
|---|---|
| `villagerGivenNames` / `villagerSurnames` | Villager names. |
| `streetTerms` | Cardinal/central words (`declaration`, `N`, `Main`, `S`, `W`, `Central`, `E`). |
| `streetPrefixes` | Optional street prefixes (`Old`, `New`, `Fort`, ...). |
| `streetStarts` / `streetEnds` | Invented street name syllables (`Elm` + `wood`, `Oak` + `ville`, ...). |
| `streetSuffixes` | Road types (`Street`, `Avenue`, `Boulevard`, ...). |
| `fossilPrefixes` / `fossilSuffixes` | Museum fossil names (`Tyranno` + `saurus`, ...). |
| `professionNames` | Occupational surnames for employed shop/farm workers, as `"profession:Surname"` (e.g. `"fisherman:Angler"`). A worker becomes "*given name* *surname for its trade*". Profession ids: `farmer`, `fisherman`, `fletcher`, `cartographer`, `mason`, `armorer`, `weaponsmith`, `toolsmith`, `butcher`, `leatherworker`, `cleric`, `shepherd`, `librarian`. |

### `mobs` — your own creature lists (weighted by repetition)

Each list is empty by default (= keep the built-in bag). List entity ids to **replace** a bag, or add
`"append": true` to **add** to it. Repetition is weight — listing `minecraft:chicken` six times and
`minecraft:wolf` once means "mostly chickens". Unknown ids are logged and skipped, not guessed.

| List | What it's for |
|---|---|
| `goodies` | Friendly beings in populated spots (villagers, ...). |
| `baddies` | Surface hostiles. |
| `animals` | Farm/wild animals. |
| `seaAnimals` | Fish and sea life. |
| `vagrants` | Wandering strays in the streets. |
| `sewers` / `mine` / `bunker` | What lurks in each. |
| `waterPit` / `lavaPit` | What's dropped in water pits/cisterns, and lava pits. |

---

*This page mirrors `settings-reference.txt`, generated alongside the example datapack on first run —
if the two ever disagree, the file in your `config/` folder is the authority for the version you're
running.*
