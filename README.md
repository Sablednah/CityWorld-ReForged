# CityWorld (NeoForge)

A **NeoForge** port of the classic [CityWorld](https://www.spigotmc.org/resources/cityworld.2250/)
Bukkit plugin — a procedural world generator that fills the world with **cities, roads, buildings,
mines, sewers, farms and wild nature**, all seed-deterministic.

| | |
|---|---|
| **Minecraft** | 1.21.11 |
| **Loader** | NeoForge 21.11.42 |
| **Java** | 21 |
| **Licence** | GPL-3.0-only |
| **Status** | ✅ Playable — full cities generate, inhabited and furnished, across 13 world styles |

Drop the jar in your `mods/` folder and pick **CityWorld** as the world type (or one of its styles),
and you get endless procedurally-generated cities: named streets, furnished buildings, roundabouts
with statues, civic districts, factories and warehouses, farms with animals, parks, and mountainous
wild between them — with mines and sewers below, loot in the chests, and villagers (with names) in
the streets.

Battle-tested across 135 worlds and 3.2 million generated chunks — over 830 km² of procedurally
generated city, more ground than New York City covers.

## What generates

- **Terrain** faithful to the original — mountains, seas with beaches, caves — using CityWorld's own
  vendored noise, extended down to the modern `-64..319` world with deepslate strata. MODERN/APOCALYPSE
  add wandering, branching "noodle" cave tunnels (a toggle, off by default elsewhere), rare lush-biome
  cave patches (moss, glow-berry vines, dripleaf, pools with axolotls/frogs/tropical fish, surface
  azaleas), and basalt-lined lava pools instead of a flat lava sea.
- **Cities** — roads (with real street-name signs), sidewalks, roundabouts, bridges and tunnels;
  buildings from houses to highrises, furnished inside; municipal, industrial and farm districts.
- **Underground** — mine networks (with vertical lift shafts), wet sewers, bunkers, basements and
  cisterns, with loot chests and mob spawners. APOCALYPSE hides a rare Fallout-style vault complex —
  multiple furnished levels behind a blast door — off the road tunnels through big mountains.
- **Inhabitants** — villagers (named, employed at their shop's trade), animals in the fields, fish in
  the seas, hostiles in the dark.
- **Set-pieces** — oil platforms, castles, radio towers, flying saucers, hot-air balloons, campgrounds,
  zoos and biodomes.
- **Schematics** — a bundled catalog of classic buildings, plus any you drop in yourself.
- **Decay** — two ruined presets with independently tunable intensity, fire density and how much survives
  intact: **apocalypse** (buildings slowly decaying, nature reclaiming, no fires) and **destroyed**
  (heavier war-zone damage, fires on).

## World styles

Thirteen styles ship, each selectable as its own world type (`cityworld:<style>`) or via the
**Customize** button on the create-world screen:

`city`/**modern** (the default, full modern MC) · `classic` (the faithful 1.8-era look) · `apocalypse`
(modern gone to ruin) · `destroyed` (heavier war-zone damage) · `metro` (all city) · `nature` (all wild)
· `sparse` (cities far apart) · `flooded` · `sanddunes` · `snowdunes` · `floating` (houses/cities
hovering over low terrain) · `maze` · `astral` (alien).

## Configuring a world

CityWorld has ~100 settings (which features generate, spawn/treasure odds, terrain toggles, city
radius, and even the villager-name and mob lists). Because they are **per-world** and NeoForge config
is per-instance, they are delivered as a **datapack**:

- On first run, a copy-and-edit example is written to
  **`config/cityworld/settings-example/`** — a full `default.json` with every knob at its default,
  plus **`settings-reference.txt`** explaining what each does and its sensible range.
- Copy that folder into `<world>/datapacks/`, edit `default.json` (list only what you want to
  change), and start the world. Different worlds — and different dimensions — can differ.
- In single-player, the **Customize** screen edits everything visually, and **`/cityexport <name>`**
  bottles a world you like into a datapack you can drop on a server.

Settings apply to **newly generated chunks** — existing chunks never regenerate, so start a fresh
world to see terrain-shaping changes.

## Custom schematics

Drop your own building schematics into `config/cityworld/schematics/<Family>/` (legacy `.schematic`,
WorldEdit `.schem`, Litematica `.litematic`, or vanilla `.nbt`). Turn on `includeSchematics` and the
generator salts them into cities. Filenames may contain spaces.

## Commands

| Command | Who | What |
|---|---|---|
| `/cityinfo` | anyone | Reports the plan under you (context, lot, nature %); names the schematic underfoot. |
| `/cityworld` / `/cityworld leave` | op | Teleport into/out of the `cityworld:city` dimension. |
| `/cityschem <name>` / `/cityschem list` | op | Paste / list catalog schematics (tab-completes). |
| `/cityfind <name>` | op | Find the nearest matching schematic building (tab-completes). |
| `/cityexport [name]` | op | Export this world's settings as a datapack. |

## Building from source

Requires a JDK 21 (standard [NeoForge ModDevGradle](https://github.com/neoforged/ModDevGradle) setup):

```bash
./gradlew build          # -> build/libs/cityworld-<version>.jar
./gradlew runClient      # dev client
./gradlew runServer      # dev dedicated server
./deploy.sh              # build + copy the jar into a NeoForge test instance
```

See **`PORTING.md`** for the full port history, architecture notes, and the plan going forward.

## Licence and credits

CityWorld is licensed under the **GNU General Public License v3** — see [`LICENSE`](LICENSE).

- Original **CityWorld** Bukkit plugin by **DaddyChurchill**
  ([echurchill/CityWorld](https://github.com/echurchill/CityWorld)), released under GPL-3. The
  original author knows about this port and has approved it.
- This **NeoForge port** by **Sablednah**, continuing under GPL-3 as a derivative work.
- CityWorld's terrain noise is vendored from Bukkit (GPL-3), which in turn derives it from Stefan
  Gustavson's public-domain simplex work — attribution preserved in `compat/noise`.

Because this is a derivative of GPL-3 code, the port and any redistribution must remain GPL-3.
