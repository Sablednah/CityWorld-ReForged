# CityWorld — reforged for NeoForge

**Drop into an endless, hand-crafted-feeling city** — procedurally generated, seed-deterministic, and
packed with detail: named streets, furnished buildings, roundabouts, factories, farms, mines, sewers,
and wild nature in between. This is a full **NeoForge port** of the classic Bukkit/Spigot plugin
[CityWorld](https://www.spigotmc.org/resources/cityworld.2250/), rebuilt from the ground up for modern
Minecraft — same generator brain, same GPL-3 license, now a world type you can pick from the create-world
screen.

Battle-tested across **135 worlds and 3.2 million generated chunks** — over **830 km²** of procedurally
generated city, more ground than New York City covers.

---

## What you get

Pick **CityWorld** as your world type and you land in a living city:

- **Roads and infrastructure** — named streets with real street signs, sidewalks, roundabouts (with
  statues), bridges, tunnels through mountains, and the odd hidden lift shaft in a 4-way crossing below.
- **Buildings, furnished** — houses through highrises, all fully furnished inside: kitchens, living
  rooms, bedrooms, libraries with chiseled bookshelves, offices, shops with the right job-site block for
  their trade (cartography tables, fletching benches, looms, smokers…) and a hanging sign out front.
- **Districts** — municipal civic centres, industrial factories/warehouses (with bunkers underneath),
  farms with animals and crops, and parks with **zoos and glass biodomes**.
- **Underground** — mine networks with copper-age fittings, ore veins that get richer with depth,
  cave-spider nests, hanging lanterns, and vertical lift shafts; wet sewers; bunkers and basements with
  loot and mob spawners.
- **Nature and caves** — mountains, seas with beaches, and (in MODERN/APOCALYPSE) wandering, branching
  cave tunnels like vanilla's, rare **lush cave patches** (moss, glow-berry vines, dripleaf, pools with
  axolotls/frogs/tropical fish, surface azaleas), and basalt-lined lava pools instead of a flat lava sea.
- **Inhabitants** — named villagers employed at their shop's actual trade, animals in the fields, fish in
  the sea, hostiles lurking in mines, sewers and the dark.
- **Set-pieces** — castles, radio towers, oil platforms, flying saucers, hot-air balloons, campgrounds —
  scattered rare landmarks, findable with `/cityfind`.
- **Custom schematics** — a bundled catalog of classic buildings, plus drop your own
  (`.schematic`/`.schem`/`.litematic`/`.nbt`) into a config folder and turn them loose in the city.

## World styles

Thirteen selectable styles, each its own world type (`cityworld:<style>`) or a click away on the
**Customize** screen:

| Style | What it is |
|---|---|
| **Modern** | The default — full modern Minecraft: tall builds, modern blocks/ores/trees/ice, winding caves, lush cave patches, shop trades, employed villagers. |
| **Classic** | The faithful 1.8-era CityWorld look — the original style this port is based on. |
| **Apocalypse** | A Modern city gone to ruin — buildings slowly decaying, nature reclaiming, a rare hidden Fallout-style vault complex behind a blast door. |
| **Destroyed** | Heavier war-zone damage, with fires. |
| **Metro** | Wall-to-wall city, no gaps. |
| **Nature** | All wild, no cities. |
| **Sparse** | Cities, but far apart. |
| **Flooded** | A drowned world. |
| **Sand Dunes** | Buried in shifting desert. |
| **Snow Dunes** | Buried in snowdrifts. |
| **Floating** | Low terrain with houses and whole cities hovering in the air. |
| **Maze** | A labyrinth of roads. |
| **Astral** | Alien mushroom terrain. |

## Configure everything

CityWorld has around 100 tunable settings — which features generate, spawn/treasure odds, terrain
toggles, city radius, decay intensity, even the villager-name and mob lists. Because settings are
per-world, they ship as a **datapack**, not a global config: edit them by hand, or use the in-game
**Customize** screen and export what you like with `/cityexport`.

**→ Full settings reference, example datapacks, and guides (a gentler apocalypse, sparse cities,
custom villager names, taller skyscrapers, and more): [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**

## Commands

`/cityinfo` tells you what's under your feet; `/cityfind`/`/cityfind lot`/`/cwlocate` track down a
specific building, landmark or biome; `/cityschem` pastes catalog buildings by hand; `/cityexport`
bottles a world's settings to hand to a server.

**→ Every command with usage examples: [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**

## Requirements

Minecraft **1.21.11**, NeoForge **21.11.42**.

## Credits and licence

CityWorld is licensed under **GPL-3.0-only**.

- Original **CityWorld** Bukkit plugin by **DaddyChurchill** — the original author knows about this port
  and has approved it.
- This NeoForge port by **Sablednah**, continuing under GPL-3 as a derivative work.
- Terrain noise vendored from Bukkit (GPL-3), in turn derived from Stefan Gustavson's public-domain
  simplex work.

Full docs, screenshots and guides: **[sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**
Source, issue tracker and full port history: see the GitHub repository.
