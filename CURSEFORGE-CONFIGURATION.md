# Configuring CityWorld

CityWorld has around 100 tunable settings — which features generate, spawn/treasure odds, terrain
toggles, city radius, decay intensity, even the villager-name and mob lists. Settings are **per-world**
(not a global instance config), delivered as a **datapack**, so different worlds — and different
dimensions on the same server — can each have their own city.

## Three ways to set them

**1. Just play — the defaults are tuned already.** Pick a world type (`cityworld:city`, or one of the
style presets like `cityworld:apocalypse`) and go. Nothing below is required.

**2. The Customize screen (single-player, easiest).** On the create-world screen, choose **CityWorld**
as the world type and press **Customize**; the style (Modern, Apocalypse, Classic …) is picked inside it.
The everyday settings are there with a widget, grouped the same way as below; the rarer ones (radius,
naming, mob lists, caves, decay detail, landmark announcements, the biome-mod fine print) are datapack-only.
Settings your style forces (Apocalypse always decays, for example) grey out with a tooltip saying why.
**Presets** saves everything Customize holds (style, Nether and End, every setting, which structures
generate) under a name, in `config/cityworld/presets/<name>.json`, and loads it for the next world. The
structures page has **All on** / **All off**.

⚠ **A world made with Customize keeps its settings inside the world** (in `level.dat`). That is what lets
it need no datapack — and it means a settings datapack added later never changes that world.

**3. A datapack (servers, modpacks, or settings Customize doesn't show).** World settings are datapack
files at `data/cityworld/cityworld/world_settings/<entry>.json`. Each world type reads one entry, and your
file must have **that entry's name**:

| World type | Reads entry | Your file |
|---|---|---|
| CityWorld (the Modern city), and the `/cityworld` dimension | `cityworld:modern` | `modern.json` |
| CityWorld: Apocalypse | `cityworld:apocalypse` | `apocalypse.json` |
| Every other CityWorld world type (Classic, Metro, Sparse, Nature, Destroyed, Flooded …) | `cityworld:default` | `default.json` |

**Your file replaces the shipped entry — it does not merge with it.** Any field you leave out takes the
built-in default from the reference below, *not* the style's: a `modern.json` holding only one setting
loses Modern's taller buildings, schematics, shops and rivers. So start from the shipped file and change
what you need. The shipped entries are in the mod jar (a jar is a zip) at the same path, and on
[GitHub](https://github.com/Sablednah/CityWorld-ReForged/tree/master/src/main/resources/data/cityworld/cityworld/world_settings).

CityWorld also writes an example pack to `config/cityworld/settings-example/`: the three shipped entries
(`modern.json`, `apocalypse.json`, `default.json`) with every setting spelled out, and a `README.txt`. Copy
the folder into your world, keep the file for your world type, and edit it. From 6.13.1 the folder is
rewritten whenever CityWorld updates, so copy it rather than editing it in place. (6.13.0 and earlier wrote it
once, with only `default.json` and a `settings-reference.txt` that had fallen out of date — delete an old
folder to get the new one.)

Put the pack in the world's `datapacks/` folder **before the world is first created**, or restart the
world after adding it. New to datapacks? See the [Beginner's guide to datapacks](Beginners-Guide-to-Datapacks).

> **Tip:** to move a world you like from single-player to a server, shape it with Customize, then run
> `/cityexport <name>`: it writes every setting into the entry the world type reads and tells you which
> `level-type` to give the server. See [Commands](Commands#cityexport-name) — including what to do with a
> pack exported before 6.13.1.

### When changes apply

Settings are read when the world **starts** — restart the server, or quit to the title screen and reopen
the world. `/reload` does not apply them. And only **new chunks** change: existing chunks never
regenerate. Spawn odds, names, treasure odds and decay bite as you explore into fresh land;
terrain-shaping settings (seas, mountains, caves, radius, rivers, oceans) want a brand-new world to be seen
properly.

### Your style forces some values

A few styles hold settings fixed whatever a datapack says. **Apocalypse** always decays roads and
buildings (building intensity `0.5`, road `0.4`, a pristine road in three), never decays nature, has no
burning debris, runs overgrowth at `3.0`, raises surface hostiles to 1 in 3 and puts spawners in mines,
sewers and bunkers. **Destroyed** decays at `1.1` with fire odds `0.07`. **Sparse** sets the city radii
and the city grid itself. Customize greys these out; in a datapack they are simply overridden.

### Naming several profiles

To keep several profiles, give your file a new name (e.g. `ruined.json`) and point a dimension's generator
at it with `"settings": "cityworld:ruined"`.

---

## Recipes

A few common asks. Each shows only the part to change: put these keys into your copy of the world type's
settings file (see the table above), leaving the rest of it as shipped.

**No deep oceans (or rarer ones).** A world made before 7.0 needs nothing: it never has them. For a new world,
switch **Oceans** off in Customize, or in a settings datapack:

```json
{
  "cities": { "oceans": { "enabled": false } }
}
```

`"rarity": 0.62` instead keeps them but makes them about a third as common. Decide before you explore: changed
under a world already played, an ocean stops dead at the chunks already made.

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

**Cities far apart, tiled on a grid (SPARSE-style), in a normal-style world** — the grid needs the radii
too, or every chunk is still in range of a city:

```json
{
  "radius": {
    "minInbetweenChunkDistanceOfCities": 100,
    "cityChunkRadius": 50,
    "roadChunkRadius": 150,
    "constructChunkRadius": 150
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

**Villages — and any other vanilla structure:**

CityWorld builds its own cities, so it places only the vanilla and mod structures that are switched on.

**The easy way (5.17.0+): the Structures page.** On the create-world screen pick a CityWorld world type,
press **Customize**, then **Vanilla & mod structures**. Every structure set the install has is listed,
grouped by mod, with an on/off each — the ones CityWorld places by default start on. Switch on
**Villages** and create the world. Hover a button to see the structures in the set and which realm they
belong to.

**For a server or a pack (5.17.0+): the `structures` settings group** — two lists in the world settings
(see [`structures`](#structures--which-vanilla-and-mod-structures-this-world-places) below):

```json
{ "structures": { "allow": ["minecraft:villages"] } }
```

**The datapack way (any version)** still works, and is what sets the *default* for every world. Two
files, in the world's own datapack folder:

```
<world>/datapacks/cityworld-villages/
├── pack.mcmeta
└── data/cityworld/tags/worldgen/structure_set/allowed.json
```

`allowed.json` — one set covers all five village variants (plains, desert, savanna, snowy, taiga):

```json
{
  "replace": false,
  "values": [ "minecraft:villages" ]
}
```

`pack.mcmeta` — the simplest one that works on every Minecraft version CityWorld supports:

```json
{
  "pack": {
    "description": "Villages in CityWorld",
    "pack_format": 15,
    "supported_formats": [15, 999],
    "min_format": 15,
    "max_format": 999
  }
}
```

Then create the world with it in place (or restart the world). `/reload` is not enough: the list is read
once, when the world starts.

**`"replace": false` is the one thing not to get wrong.** With `true` your file *replaces* CityWorld's
list instead of adding to it, which drops `minecraft:strongholds` — and no stronghold means no End
portal and nothing for an eye of ender to find.

The other sets you can name the same way: `minecraft:pillager_outposts`, `minecraft:woodland_mansions`,
`minecraft:mineshafts`, `minecraft:ocean_monuments`, `minecraft:desert_pyramids`,
`minecraft:swamp_huts`, `minecraft:igloos`, `minecraft:jungle_temples`, `minecraft:shipwrecks`,
`minecraft:ocean_ruins`, `minecraft:buried_treasures`, `minecraft:ruined_portals`,
`minecraft:trail_ruins`, `minecraft:nether_fossils`.

**Archaeology (suspicious sand and gravel) comes only through vanilla's structures**: CityWorld never places those
blocks itself. On CityWorld's own land the default sets have none, so allow `minecraft:desert_pyramids`,
`minecraft:trail_ruins` and `minecraft:ocean_ruins` to get them; a world with deep oceans (7.0) already has the
ocean ruins, a vanilla-land world has every set, and a desert well (a vanilla feature, placed when **Wild plants**
lets vanilla decorate the wild) carries its own.

**What to expect.** An allowed structure is not simply dropped on top of the city. CityWorld reserves
ground around it so streets and buildings plan *around* it, and bends the terrain under each piece so
it sits in the land — measured on a fixed seed at 745 of 745 columns resting exactly on the block their
piece asks for, none buried, with the ground easing back to natural terrain a chunk past the structure's
footprint.

**Telling whether it worked:** `/cwlocate structure minecraft:village_plains` (add `tp` to teleport
there). In a CityWorld world that command only finds what is switched on for that world, so "not found"
means the choice has not taken — which distinguishes a settings or datapack problem from an unlucky seed.
(When the `structures` settings name anything, the server log also says what it read: `CityWorld: structure
settings (overworld) allow [...] deny [...] -> N structure sets placed`.) See
[`caves`](#caves--the-underground-cave-biomes-and-structure-caverns) below for the three
look-alike outcomes and the two silent failures, and remember **nothing retro-fits into chunks that
already exist** — test in a new world or in land you have never visited.

## For modpacks: locking the create-world screen

`config/cityworld-startup.toml` (per instance, read on the client's create-world screen; a server uses `level-type`
in `server.properties` instead):

| Key | Default | What it does |
|---|---|---|
| `lockedWorldPreset` | `""` | Locks every new single-player world to a world type, e.g. `"cityworld:apocalypse"`. The World Type button greys out and Customize keeps the preset's style. Empty = no lock. |
| `ruinedNether` | `""` | `"cityworld"` (the ruined-city Nether) or `"vanilla"` locks the Nether choice. Empty = the player chooses. |
| `cityworldEnd` | `""` | `"cityworld"` (cities on the End's outer islands) or `"vanilla"` locks the End choice. Empty = the player chooses. |
| `lockCustomize` | `false` | With `lockedWorldPreset` set: removes the Customize button, so every new world gets the preset's own `world_settings` entry unchanged. That makes a pack's replacement of that entry authoritative. |

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
| `includeAirborneStructures` | `true` | Balloons/blimps over the fields. Off in the Apocalypse preset. |
| `includeBuildingInteriors` | `true` | Furnish building interiors. Off = empty shells (faster). |
| `includeSchematics` | `false` | Drop the bundled classic building schematics into cities. On in the Modern and Apocalypse presets. |
| `includeNamedRoads` | `true` | Street-name signs. |

### `terrain` — the land, its fluids, and decay

| Setting | Default | What it does |
|---|---|---|
| `includeCaves` | `true` | Carve caves. |
| `windingCaves` | `false`* | Winding "noodle" cave tunnels that wander and branch (vanilla-like) instead of the classic noise blobs. *On by default for MODERN/APOCALYPSE; a free toggle for any style. Needs `includeCaves`. Never style-locked. |
| `largeCaverns` | `false`* | Huge open caverns deep underground (y −52 to 26), with ledges and shelves. *On in the MODERN and APOCALYPSE world presets. |
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
| `buildingIntensity` | `1.0` | How ruined a decayed **building** is — scales both the number and size of collapse holes. Below 1 = light weathering, above 1 = heavy rubble (much above ~1.3 shreds buildings past recognition). APOCALYPSE forces `0.5`, DESTROYED `1.1`. |
| `roadIntensity` | `1.0` | The same, for decayed **roads**. APOCALYPSE forces `0.4`, DESTROYED `1.1`. |
| `oddsOfDecayFire` | `0.20` | Fraction of collapse rubble that catches fire. Only bites when `includeFires` is on and the style keeps burning debris (APOCALYPSE never does). DESTROYED forces `0.07` — even 0.2+ reads as a carpet of flame. |
| `oddsOfPristineRoad` | `0.0` | Chance a road chunk is spared and stays intact, so a ruined grid keeps walkable stretches. APOCALYPSE forces `0.3`. |

### `caves` — the underground: cave biomes and structure caverns

Two things here are **datapack tags, not settings**, because they are lists of ids rather than numbers:

| Tag | What it decides |
|---|---|
| `#cityworld:allowed` (structure sets) | Which vanilla and mod structures generate. Ships as strongholds, trial chambers, ancient cities, Nether fortresses and bastions, and End cities, plus optional entries for Cataclysm, Dungeon Crawl, Battle Towers and Alex's Caves (used only when those mods are installed). Add another mod's structure set here and it will generate; **an absent or empty tag means none**, so a stripped datapack falls back to CityWorld-only worldgen rather than letting villages loose. |
| `#cityworld:cave_pool` (biomes) | Which cave biomes the underground draws from. Ships as deep dark, lush, dripstone, and sulfur caves (Minecraft 26.2+, marked optional so it is simply absent on older versions), plus optional Biomes O' Plenty and Alex's Caves cave biomes. Add a modded cave biome here and it will appear. |

> **Adding another mod's structures — and telling whether it worked.**
>
> Put `data/cityworld/tags/worldgen/structure_set/allowed.json` either in a world datapack
> (`<world>/datapacks/<name>/`, that world only) or in a data-only mod jar in `mods/` (every world).
> Keep `"replace": false`, and write each entry as `{ "id": "themod:their_set", "required": false }` —
> **one unknown *required* entry discards the entire tag**, which silently removes strongholds and with
> them the End portal. List the mod's *structure **sets*** (`worldgen/structure_set/`), not its structures.
>
> Three different things look alike here, and confusing them costs an afternoon:
>
> | What you see | What it actually proves |
> |---|---|
> | `/locate structure <id>` offers the name in **autocomplete** | Only that the **mod** is installed — that list comes from the structure registry, which the mod fills on its own. It looks identical whether or not your tag took. |
> | The pack appears in the **Mods list** (or `/datapack list`) | Your **file loaded**. Still not that the tag merged. |
> | `/locate structure <id>` **returns a position** | Placement works — the tag took. This is the only one that answers the question. |
>
> Two silent failures worth knowing. A structure whose biomes CityWorld's biome source cannot produce is
> **dropped even when allowed** (the game discards any structure set it cannot host), so check the mod's
> `tags/worldgen/biome/has_structure/` against what CityWorld actually generates. And nothing retro-fits
> into existing chunks — **test in a new world**, or at least in terrain that has never been generated.

The `caves` settings group is the numbers those tags cannot express:

| Setting | Default | What it does |
|---|---|---|
| `structureCarveHalo` | `10` | How far, horizontally, terrain is cleared past the pieces of a structure that expects a cavern around it — an ancient city, in practice. Too small and the city reads as a row of boxes instead of one hall; larger gives a roomier, emptier space. |
| `structureCarveHaloUp` | `6` | The same, upward — headroom, not a chimney. Nothing is ever carved *below* a structure, because the game piles material there to hold it up. |
| `surfaceMargin` | `8` | How far below the ground a cave patch begins, so a patch can't bleed into surface grass and foliage colour on a hillside. |
| `patches` | `[]` | Per-biome patch shaping. **Empty means the shipped defaults**; listing any entry replaces the lot. |

Each `patches` entry takes `biome` (required), plus `cell`, `percent`, `minY` and `maxY`. `cell` is the
patch grid size in blocks (bigger = bigger, further-apart patches), `percent` roughly what share of
cells are that type, and `minY`/`maxY` the depth band it applies in.

**Order matters.** The first entry whose cell matches owns the whole column — one cave type per column,
never stacked — so list the rarest and deepest first. A biome must still be in `#cityworld:cave_pool` to
appear at all; `patches` only shapes it. That is also how you give a **modded** cave biome proper
geometry instead of the generic fallback it gets from the tag alone.

The shipped defaults, if you want to start from them:

```json
"caves": {
  "patches": [
    { "biome": "minecraft:deep_dark",       "cell": 176, "percent": 4, "minY": -64, "maxY": -24 },
    { "biome": "minecraft:sulfur_caves",    "cell": 112, "percent": 5, "minY": -64, "maxY":  -8 },
    { "biome": "minecraft:lush_caves",      "cell":  80, "percent": 5, "minY": -40, "maxY":  40 },
    { "biome": "minecraft:dripstone_caves", "cell":  96, "percent": 6, "minY": -60, "maxY":  20 }
  ]
}
```

> **Careful with `deep_dark`'s `maxY`.** It stops at `-24` because that is where ancient cities
> generate. Raise it and you advertise the biome above the depth anything can use it — and you start
> eating the clearance that keeps ancient cities from opening into your sewers and cisterns.

Cave biomes bring their own mobs. The deep dark carries none at all, plus wardens.

### `shops` — themed retail with villager job blocks (MODERN dressing)

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `false` | Villager job-site blocks so a store/farm reads as its trade (cartography table = map seller, fletching table = fletcher, smoker = butcher, ...), a composter at farm edges, and a rare fish-pond farm. The shop classification itself is always computed for `/cityinfo`; this only governs block placement. |
| `malls` | `true` | Shopping malls (MODERN and APOCALYPSE): rare, at the edge of town, two or three storeys round a glass-roofed atrium, built in wings — a bar, L, T, U, Z or cross, up to eight chunks each way — with dozens of named shops, each with its own loot table (`chests/mall_<kind>`, each ending in an `_extra` hook), and car parks out to the road. Announced as the landmark kind `mall`. Independent of `enabled`. |
| `oddsOfAtmInCivic` | `0.333` (1 in 3) | **ATMs: civic**: the chance each ground-floor chunk of a city hall or courthouse has a cash machine against an inside wall, from `#cityworld:fittings/atm`. Only when a mod supplies one ([Modernlife](https://www.curseforge.com/minecraft/mc-mods/modernlife)'s ATM is listed); none otherwise. |
| `oddsOfAtmInShops` | `0.125` (1 in 8) | **ATMs: shops**: the same, per ground-floor chunk of a shop. |
| `oddsOfAtmInMalls` | `0.2` (1 in 5) | **ATMs: malls**: the same, per chunk of a shopping mall's ground floor. |
| `bankPlazaRadius` | `2` | **Bank plaza (Modernlife)**, *1.20.1 only, the one version Modernlife ships for*: with Modernlife installed, a new world's spawn sits on a paved town square this many chunks out (2 = 80 blocks: paving, street lamps, benches, planters) so the bank Modernlife builds beside a new player lands on the square instead of on top of a shop. 2 is the smallest at which Modernlife's own flat-land check passes where the player lands. The city's roads run up to its kerb. `0` for none. Decided when the world is created. |

### `structures` — which vanilla and mod structures this world places

Two lists of structure-**set** ids laid over the tag `#cityworld:allowed` (CityWorld's defaults: strongholds,
trial chambers, ancient cities, the Nether's fortresses and bastions, end cities, and several mods' sets). This is
what the Structures page of the Customize screen writes.

| Setting | Default | What it does |
|---|---|---|
| `allow` | `[]` | Structure sets to place as well: `["minecraft:villages", "minecraft:woodland_mansions"]`. |
| `deny` | `[]` | Structure sets to stop placing: `["minecraft:trial_chambers"]`. |
| `wildernessOnly` | `false` | Keep every placed set to the wild: a structure never starts in a platmap the city plans to build (a district of any kind, farms and outland included) — so a pond or ruin structure cannot cut a city in two. Strongholds are never affected: the eyes of ender point at their ring whether or not one stands. Set by set instead, a datapack tags `#cityworld:wilderness_only` (structure sets; Arboria's lakes ship in it). |

Ids are sets (`data/<namespace>/worldgen/structure_set/`), not single structures. An id whose mod is not installed
is ignored. A structure still only appears where its biomes exist. The Nether and the End follow the overworld's
lists. Cities keep clear of whatever is placed.

### `subways` — a metro under the city (MODERN and APOCALYPSE; forced off elsewhere)

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | A station in every urban district — a ticket hall beside a road, switchback stairs down to twin-track platforms 24 blocks under the street — and tunnels to the first station along its row or column up to six districts away, so lines cross the countryside between city clusters. East-west lines run on the upper level, north-south lines eight blocks lower, so lines never junction; a station on both is an interchange with stairs between its halls, and one with a single line each way joins them in a sloped loop instead of two dead ends. Lit, ringed in brick, powered rails so a cart runs the line. Stations announce as the landmark kind `subway` (off by default). |
| `spawners` | `true` | In a ruined (APOCALYPSE) world, sewer-mob spawners in niches in the tunnel walls. |

### `roads` — road signs, bridges and parked vehicles (Macaw's Roads and Streets, Macaw's Bridges, Immersive Vehicles; nothing without them)

Every pool here is empty unless the road and bridge mods fill it (`#cityworld:fittings/road`, `#cityworld:fittings/skybridge`, `#cityworld:fittings/rope_bridge`), so without them these settings change nothing.

| Setting | Default | What it does |
|---|---|---|
| `driveOnLeft` | `false` | **Drive on** in Customize. The side of the road traffic keeps to: `false` the right (the US, most of the world), `true` the left (the UK). Every road sign stands on the driver's side of the traffic it is for and faces it. |
| `speedLimitUrban` | `50` | A town's speed limit, on the mod's signs (10 to 120 in tens; another number rounds to the nearest). |
| `speedLimitRural` | `30` | The limit in a district that is mostly nature: neighbourhoods, farms, the outskirts. Where a road crosses from one limit to another the signs come in a pair, back to back across the road: the new limit for the traffic going in, the old one for the traffic coming out. Where a road leaves a town for the wild, the town's name board and its "leaving" face, then a chunk in, the limit and its end. |
| `oddsOfSpeedRepeat` | `0.125` (1 in 8) | The chance a straight stretch in the slower zone repeats its limit, as a pair. |
| `skybridges` | `false` (Apocalypse `true`) | **Skybridges** in Customize. A deck between an upper floor of two neighbouring towers of different buildings, with a doorway cut into each wall: survivors' ways about a ruined city, so Apocalypse has them by default and a working city does not. As often a one-wide rope bridge as a two-wide solid deck, each from its own pool (`#cityworld:fittings/skybridge`, `#cityworld:fittings/rope_bridge`). Each half runs from its chunk edge until it meets the building. |
| `oddsOfSkybridge` | `0.333` (1 in 3) | The chance each qualifying pair of towers gets a deck. |
| `skybridgeMinFloors` | `6` | Both towers must have at least this many floors; the deck lands on the highest floor they share, never the second floor or lower. |
| `oddsOfStreetRope` | `0.333` (1 in 3) | With skybridges on, the chance a district gets one rope bridge across a street, between two towers that face each other over a road. Never more than one per district. |
| `wildBridges` | `true` | **Wild rope bridges** in Customize. A one-wide rope bridge from a ridge crest in the wild across a valley to where the far side's ground comes back up to the deck, so it always lands on the hill and needs no support. A find, not a fixture: see `wildBridgeRegion`. |
| `wildBridgeDepth` | `8` | How far the valley floor must lie below the crest for a rope bridge to be worth building. |
| `wildBridgeSpan` | `48` | The longest bridge, in blocks; a valley wider than this gets none. The shortest is a chunk. |
| `wildBridgeRegion` | `32` | The size of a bridge region, in chunks square. A region has at most one wild rope bridge, wholly inside it: its seed orders its chunk rows and columns, and the bridge is the first span along the first of them that has one. |
| `oddsOfWildBridge` | `0.5` | The chance a region has its bridge (1 in 2). At the defaults a mountain range shows one, maybe two. |
| `vehicles` | see below | Immersive Vehicles' and Ultimate Car Mod's parked vehicles (7.0). **Parked vehicles** in Customize. |
| `carParks` | see below | Car parks as buildings of their own (7.0). **Car parks** in Customize. |

`vehicles` is a group of its own. Every parked vehicle costs the client frames (a car park with a bay in two filled
ran at 1 fps), so by default they are few: about one in two mall car-park chunks holds one, and one district in two
has one at a kerb.

```json
"roads": { "vehicles": { "enabled": true, "carParkVehicles": 1, "oddsOfParkedVehicle": 0.5, "streetVehicles": 1, "oddsOfStreetVehicle": 0.5, "fuel": 1000, "fuelMin": 0 } }
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | With Immersive Vehicles and a vehicle pack, park real vehicles: in mall car parks (the bays hold block cars otherwise; a ruin keeps its block wrecks) and at the kerbs of straight streets. The vehicles are data: see PALETTES.md, "Parked vehicles". Nothing is placed without the mod. |
| `carParkVehicles` | `1` | The most real vehicles a mall car-park chunk holds (it has eight bays). The other bays stay empty. |
| `oddsOfParkedVehicle` | `0.5` | The chance each of those is there. |
| `streetVehicles` | `1` | The most vehicles a district (a 10×10-chunk platmap) parks at its kerbs, each on a different straight street, mid-chunk, in the kerb lane, nose along the traffic on its side (`driveOnLeft`). Never in a ruin, a tunnel or on a bridge. |
| `oddsOfStreetVehicle` | `0.5` | The chance a district has its street vehicles at all. |
| `fuel` | `1000` | The most fuel a parked vehicle has in its tank, in millibuckets: lava for Immersive Vehicles (its stock fuel), bio diesel for Ultimate Car Mod. Each vehicle gets a random amount between `fuelMin` and this. `0` parks them all dry (the gas stations' pumps are full). |
| `fuelMin` | `0` | The least fuel a parked vehicle has. |

`carParks` is a group of its own: car parks in place of a building, in the modern family's highrise, midrise and
lowrise districts (the mall keeps its own whatever these say).

```json
"roads": { "carParks": { "enabled": true, "oddsOfSurface": 0.0147, "oddsOfMultiStorey": 0.0147, "minLevels": 2, "maxLevels": 4 } }
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true` | Car parks of their own: a surface car park (the mall's lot: bays, lamp posts, verges, open to every road it fronts) or a multi-storey one (decks of bays joined by slab ramps, open sides behind a parapet, a ladder up the stairwell). Each grows into its neighbours like a building, and holds parked vehicles by the `vehicles` rules. **Car parks** in Customize. |
| `oddsOfSurface` | `0.0147` | The chance each building pick is a surface car park instead (one in sixty-eight). |
| `oddsOfMultiStorey` | `0.0147` | The chance each building pick is a multi-storey car park instead (rolled first, one in sixty-eight). |
| `minLevels`, `maxLevels` | `2`, `4` | How many decks a multi-storey car park has, picked per car park. Each deck is five blocks. |

### `cities` — cities in a vanilla world, highways, rivers and oceans

| Setting | Default | What it does |
|---|---|---|
| `vanillaTerrain` | `false` | **Land: vanilla** in Customize. The land is vanilla's throughout (its biomes, mountains, rivers and structures), and CityWorld builds cities on it like villages: one per `spacing`-block cell, each on its own level, with quays and natural banks where a river runs through. Modern-family and Classic styles; a style that is its own terrain (floating, flooded, dunes, astral, maze, nature, metro) ignores it. |
| `spacing` | `2048` | Blocks between cities in a vanilla world (one city per cell this wide). `1024`–`16384`. The whole city and its blend ring into the land must fit in its cell, so the spacing caps the size: `1024` fits 4 districts across, `1536` 6, `2048` 9, and a city *d* across needs about 179 × *d* + 284 blocks. |
| `districts` | `3` | How many districts across a vanilla-world city is. `1`–`8`. In a world made with 7.0 or later a size too big for the spacing is built as the biggest that fits (Customize's **City size** shows "(N fit)" in gold); before 7.0 that city was not built at all. |
| `districtsVariance` | `1` | How much each city's size varies from `districts`, plus or minus. `0`–`3`. |
| `levelRange` | `40` | How far above CityWorld's street level (y 64) a vanilla-world city may stand, to sit on the land around it. `0`–`120`. |
| `maxWater` | `0.35` | The share of a candidate site that may be under the sea. Datapack only. |
| `minNear` | `0.75` | The share of a candidate site that must already lie within sixteen blocks of the city's level. Datapack only. |
| `rivers` | `false`* | Rivers on CityWorld's own land: springs in the hills, waterfalls, rivers widening to the sea, bridges where roads cross, and the waterside by district (quays, slips, harbours, beaches, lighthouses). *On in the world presets; off in a world saved before rivers existed, so an old world never grows a river that stops at the chunks it already has. |
| `highways` | see below | Roads between the cities of a vanilla world (7.0). **Highways** in Customize. |
| `oceans` | see below | Deep oceans on CityWorld's own land (7.0). **Oceans** and **Ocean share** in Customize. |

`highways` is a group of its own:

```json
"cities": { "highways": { "enabled": true, "width": 9, "grade": 0.1, "fillMax": 8, "clearance": 5, "tunnel": 12, "speedLimit": 70, "reach": 3, "surface": "minecraft:cobbled_deepslate" } }
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true`* | **Vanilla land only.** A road from each city to the nearest city east and the nearest south (see `reach`). It leaves each city along one of the city's own streets (the city paves that street out to its edge for it), at the city's level, then winds round hills and along valleys rather than over them, and climbs no steeper than `grade`. Where the ground is higher it is cut through, where lower it is banked up, over water and deep drops it is a bridge (railings, piers), and under a ridge a tunnel (lit). Its surface is the city's street: a road mod's asphalt (Macaw's Roads) where the city has it. *On for a world made with 7.0 or later; **a world made before 7.0 never has them**, so explored land never meets a road cut off at its edge. A city with no city within `reach` that way gets no road that way. |
| `width` | `9` | The road's width in blocks, plus a kerb each side. `3`–`15`. |
| `grade` | `0.1` | The steepest climb: blocks up per block along (`0.1` is one in ten). The steps are half slabs where the surface has a slab. `0.02`–`0.5`. |
| `fillMax` | `8` | The deepest a road is banked up on the ground; deeper than this it is a bridge. `0`–`32`. |
| `clearance` | `5` | How high a bridge deck stands above the sea when it crosses water. `1`–`32`. |
| `tunnel` | `12` | How far the ground must stand above the road for a tunnel; less is a cut with sloped sides. `8`–`128`. |
| `speedLimit` | `70` | The limit signed where a city's street becomes the highway (with a road mod's signs: Macaw's Roads), beside the town's board. `10`–`120`, in tens. |
| `reach` | `3` | How many cells (each `spacing` blocks) a highway may run past empty ones to the next city. A city takes the next cell's city when there is one, otherwise the nearest that way up to this many cells off whose straight line passes no other city. `1` joins next-door cities only. `1`–`6`. |
| `surface` | `minecraft:cobbled_deepslate` | The deck's block where no road mod gives the street one: a block that has a slab, so the half-block climbs match it (`minecraft:polished_deepslate`, `minecraft:smooth_stone`, `minecraft:deepslate_tiles` all do). The city's street keeps its own surface, so the deck changes where the highway begins. With Macaw's Roads the deck is its asphalt whatever this says; a pack's `street/surface` palette also wins. A name that is not a block falls back to the street's surface. |

`oceans` is a group of its own:

```json
"cities": { "oceans": { "enabled": true, "size": 6000, "rarity": 0.5, "depth": 30, "shelf": 0.1 } }
```

| Setting | Default | What it does |
|---|---|---|
| `enabled` | `true`* | Now and then the land runs down into a wide, deep ocean, with vanilla's ocean biomes (deep, cold, frozen, lukewarm, warm), and the structures that live there: ocean monuments, ocean ruins, shipwrecks and buried treasure, and any mod's that ask for ocean biomes (Cataclysm's Sunken City and Acropolis, Battle Towers' ocean tower). Cities keep off it. In the ruined Nether twin it is a lava sea. *On for a world made with 7.0 or later (the world's generator carries `"made_with": 7`); **a world made before 7.0 never has them**, whatever this says, so explored land never meets an ocean cut off at its edge. Modern, Apocalypse, Classic and Sparse, with seas on; Destroyed (decayed nature) and the styles that are their own terrain ignore it, and vanilla land has vanilla's oceans. |
| `size` | `6000` | How broad the ocean pattern is, in blocks: larger means fewer, bigger oceans. `1024`–`32768`. |
| `rarity` | `0.5` | How much of the land stays land: higher is rarer. **Ocean share** in Customize: `0.62` about 2%, `0.56` about 4%, `0.5` about 6%, `0.44` about 9%, `0.38` about 13% (measured over 80 km on three seeds; it varies by seed). At the default an ocean is about 1–5 km across. `0`–`0.95`. |
| `depth` | `30` | How far below sea level the ocean floor lies (vanilla's deep oceans are about this deep). `8`–`56`. |
| `shelf` | `0.1` | How wide the slope from the shore to the deep is, as a share of the pattern: larger is a gentler, wider shelf. `0.01`–`0.5`. |
| `rigs` | `0.0625` | The share of the deep-water sites inside these oceans that get an oil rig (a site comes up in about one platmap in two). `1` puts a rig on every one; `0` none. The old, patchy deep sea outside them keeps every rig. `0`–`1`. |

> Every city in a vanilla world is worked out from the seed and these numbers, so **change them only before
> the world is made**: changed under an existing world, cities move or change level under chunks already built.

### `spawns` — who turns up, and how often (odds `0.0`–`1.0`)

| Setting | Default | What it does |
|---|---|---|
| `spawnBeings` | `0.5` | Villagers/witches appearing in populated spots. |
| `spawnBaddies` | `0.0476` | Hostile mobs at the surface. Raise for a rougher world. |
| `spawnAnimals` | `0.667` | Farm/wild animals. |
| `spawnVagrants` | `0.2` | **Wanderers** in Customize: strays out of town, on wild land and dirt roads. Hostile on a Baddies roll, or always in a ruined world. (The key keeps its old name.) |
| `nameVillagers` | `true` | Give villagers generated names (see `naming`). |
| `showVillagersNames` | `true` | Show those names as floating nametags. |
| `wildAnimals` | `true` | **Wild animals**: vanilla's herds for each biome as the wild land is made (sheep on the plains, wolves in the woods, any animals a biome mod adds), the way a vanilla world gets them. Overworld only. |
| `cityAnimals` | `false` | **Animals in cities**: the same herds in a city's open ground, never on a roof. Vanilla's animals need grass, so in practice they come to parks and gardens. |

### `treasures` — chests and spawners

| Setting | Default | What it does |
|---|---|---|
| `treasuresInMines` / `spawnersInMines` | `true` | Loot chests / mob spawners in mines. |
| `treasuresInBunkers` / `spawnersInBunkers` | `true` | Loot chests / mob spawners in bunkers. |
| `treasuresInSewers` / `spawnersInSewers` | `true` | Loot chests / mob spawners in sewers. |
| `treasuresInBuildings` | `true` | Loot chests in buildings. |
| `oddsOfTreasureInMines/Bunkers/Sewers/Buildings` | `0.5` | Chance a given spot gets a chest. |
| `oddsOfAlcoveInMines` | `0.5` | Chance a mine tunnel opens into a side alcove. |
| `oddsOfFinds` | `0.077` (1 in 13) | **Finds in drawers**: the chance an everyday container (a building's closet or furniture, a nightstand, a kitchen store) in a Modern or Apocalypse world also holds something worth having: an emerald, a diamond, an enchanted book. `0` for none, `1` for every one. See [Loot Tables](Loot-Tables). |

These control CityWorld's dedicated loot chests (closets, storerooms and kitchenettes in buildings, and the
treasure chests underground). What any container holds, and how a modpack adds its own items, is on
**[Loot Tables](Loot-Tables)**.

### `world` — trees, ground cover, floating subsurface, ruralness

| Setting | Default | What it does |
|---|---|---|
| `treeStyle` | `"NORMAL"` | Tree family: `NORMAL`, `SPOOKY`, `CRYSTAL`. |
| `spawnTrees` | `0.5` | How densely trees grow. |
| `subSurfaceStyle` | `"LAND"` | What fills space under a FLOATING world's land: `NONE`, `LAND`, `CLOUD`, `LAVA`. Only the FLOATING style reads this. |
| `ruralnessLevel` | `0.0` | Skews the world more rural (more nature, fewer cities) — up toward `1.0`. |
| `maxBuildingFloors` | `20` | Tallest a building may rise, in floors (4 blocks each) above street level. 20 = the classic 1.8 look; the Modern and Apocalypse presets ship `40`. Sensible range 8–60. Also on the Customize screen. |
| `broadcastSpecialPlaces` | `false` | Announce landmarks in chat as they generate ("Vault 42 generated near 1520, -340"), to the players in the world it happened in. Off by default — chunks generate wherever anyone explores. The same events always go to the debug log regardless. Schematics join in only if their `.yml` also sets `BroadcastLocation: true`. |
| `announcedLandmarks` | the rares | Which landmark kinds may chat-announce (with the toggle above on). Default `["airship","saucer","vault","zoo","biodome","hospital","schematic","mall"]`; a server can add `castle`, `oilplatform`, `radiotower`, `bunker`, `museum`, `mineentrance`, `campground`, `shack`, `balloon`, `fishpond`, `vaultroad`, `hospitaldept`, `subway` — or trim the list down. **With JourneyMap installed this list also decides which landmarks get a map waypoint**, and that happens whether or not chat announces are on. |

#### Modded biomes

| Setting | Default | What it does |
|---|---|---|
| `wildDecoration` | `BOTH` (`VANILLA` in the Modern and Apocalypse presets) | **Wild plants** in Customize: who decorates **wild (nature) land** in MODERN/APOCALYPSE. `BOTH` runs CityWorld's cover *and* vanilla's biome features — the default, and the densest. `CITYWORLD` skips vanilla's, leaving CityWorld's own cover alone. `VANILLA` skips CityWorld's entirely, so vanilla — **and any biome mod you have installed** — is the sole decorator; note that also drops CityWorld's cactus and its MODERN blue-ice peaks, which get vanilla's snow instead. City, road and structure land is always CityWorld's alone, whatever this is set to. |
| `climateWarmth` | `0.25` | Leans the temperature field warm. `0.0` is the raw field; higher values shift the average up without narrowing the range (0 stays 0, 1 stays 1), so you keep frozen peaks and deserts but see more warm ground between them. The default lifts the mean by about 14%, which is there because worlds read persistently cold. |
| `useModdedBiomes` | `true` | Let installed biome mods (Biomes O' Plenty and anything else built on **TerraBlender**) contribute biomes to the world. On by default, because installing a biome mod is itself the request — set it `false` to keep CityWorld's own biome selection. Does nothing at all if you have no such mod. Oceans, shores and peaks always stay CityWorld's, since those follow the terrain exactly. |
| `moddedBiomeShare` | `0.35` | How much contested ground a biome mod may own outright. A modded biome is normally picked only where it beats every vanilla biome in the climate lookup, and many mod biomes overlap CityWorld's climate on every axis yet never quite win — measured with Biomes O' Plenty, 25 of them. On this share of the map the lookup runs with vanilla's biomes removed, so those compete only with each other. The share is regional, not scattered, so a mod gets areas you can walk across. `0.0` means "only where they outright win"; `1.0` hands every contested column to the mod. Needs `useModdedBiomes`. |
| `packPreset` | `"minecraft:normal"` | The world preset CityWorld reads the pack's overworld from. **Vanilla land** (`cities.vanillaTerrain`) takes its terrain noise and biome source from it; **CityWorld's land** offers the foreign biomes it declares through `moddedBiomeShare`, exactly as TerraBlender's. Datapack biome mods — Terralith, Dreamwoods' Grand Terrain — rewrite `minecraft:normal` and write their biomes inline in it, nowhere else, so with this they just work; a pack that gives vanilla another preset names it here. Blank reads no preset (vanilla's registry entries, as before 6.11). |
| `moddedBiomeGrain` | `64` | The cell, in blocks, a mod's biome is decided for: the climate is read at the cell's centre (nudged by a low-frequency warp, so edges are not a grid) and the answer stands for the whole cell. Per column, a mod biome's narrow climate box wins for a few blocks at its edges — haunted forests a few blocks across, inside the city. `1` is every column for itself (the pre-6.11 look); larger values make bigger patches. |
| `biomeScale` | `1.5` | How large biome regions are, as a multiplier — it divides the frequency of the temperature, humidity, erosion and weirdness fields together, and widens the continentalness smoothing to match. `1.0` is the older, tighter default; `3.0` gives continent-sized regions. Scaling all the axes together changes how *big* regions are rather than which biome you get. |

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

### `naming` — your own villager, street and place names

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
| `streetTerms` | The direction and centre words, **by position**: the first entry is ignored, then North, Main, South, West, Central, East. Give all seven to change them; `append` has no effect on this list. |
| `streetPrefixes` | Optional street prefixes (`Old`, `New`, `Fort`, ...). |
| `streetStarts` / `streetEnds` | Invented street name syllables (`Elm` + `wood`, `Oak` + `ville`, ...). |
| `streetSuffixes` | Road types (`Street`, `Avenue`, `Boulevard`, ...). |
| `fossilPrefixes` / `fossilSuffixes` | Museum fossil names (`Tyranno` + `saurus`, ...). |
| `placeNames` | Whole place names (`Midtown`, `Soho`, `The Heights`, ...). Every city in a vanilla world has a name, and CityWorld's own land (one great city) is named district by district. These lists replace the built-in ones whatever `append` says. |
| `placeStarts` / `placeEnds` | Invented place names (`Ash` + `ford`, `River` + `side`). A district is as often a whole name as an invented one, a city of its own mostly invented. |
| `placeFormat` | `"{name}"` by default. `{name}` is a name from the lists, `{n}` a number from 1 to 99: `"City {n}"`, `"District {n}"`, `"New {name}"`. |
| `districtSize` | `4`: how many platmaps (of ten chunks) square a named district of CityWorld's own land is, so 640 blocks. |
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
| `vagrants` | The Wanderers: strays out of town (see `spawnVagrants`). |
| `sewers` / `mine` / `bunker` | What lurks in each. |
| `waterPit` / `lavaPit` | What's dropped in water pits/cisterns, and lava pits. |

---

*This page is the reference for every setting; the example pack in `config/cityworld/settings-example/`
holds the values, and its README points here.*
