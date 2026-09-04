![CityWorld — reforged for NeoForge](https://media.forgecdn.net/attachments/description/1648913/description_c782ad1e-4ae0-4f12-a2b3-11b8862c5669.png)

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
  cave tunnels like vanilla's, and basalt-lined lava pools instead of a flat lava sea. Underground you
  will find real **cave biomes** in patches — lush (moss, glow-berry vines, dripleaf, pools with
  axolotls/frogs/tropical fish, surface azaleas), dripstone, deep dark, and sulfur caves on Minecraft
  26.2 — each decorated the way vanilla decorates it.
- **Vanilla structures where they belong** — **strongholds** (so eyes of ender work and the End is
  reachable), **trial chambers**, and **ancient cities** in the deep dark. Villages and mineshafts stay
  off: CityWorld builds its own. A datapack tag can widen the list, including to another mod's
  structures.
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

### Your other mods' blocks build cities too

CityWorld's building palettes are **block tags**, not a fixed list. A mod that tags its blocks the
normal way — planks in `#minecraft:planks`, stone in `#c:stones` — starts appearing in cities the
moment you install it, with no patch and no compatibility pack. The odds stay put as the palette
widens, so a heavily modded world gets *more variety*, not more wooden houses. For mods that don't tag
their blocks, or to put a mod's blocks somewhere they wouldn't naturally go, a small datapack extends
any palette directly.

### And your biome mod's biomes

Install **Biomes O' Plenty** — or anything else built on **TerraBlender** — and its biomes generate in
your CityWorld world. Not a handful of them: with BoP installed, **all 59 of its overworld biomes**
appear, over about a third of the ground, with CityWorld still naming the rest. Its cave biomes go
underground where they belong, and biomes whose look is their *ground* — gravel beaches, volcanic
plains, salt wastes — get their real blocks rather than a grass stand-in.

On by default, because installing a biome mod is itself the request. `world.moddedBiomeShare` sets how
much ground a mod may own, `world.useModdedBiomes` turns it off entirely, and both cost nothing when
you have no such mod.

### Farms grow your mods' crops

Crops and flowers come from tags too, so **Farmer's Delight** cabbages or a biome mod's wildflowers
grow in CityWorld's fields. Two-block crops work — Biomes O' Plenty's barley stands full height — and
a plant that can't survive where it was sown is quietly swapped for one that can, so a field is never
left bare.

## Commands

`/cityinfo` tells you what's under your feet; `/cityfind`/`/cityfind lot`/`/cwlocate` track down a
specific building, landmark or biome; `/cityschem` pastes catalog buildings by hand; `/cityexport`
bottles a world's settings to hand to a server.

Every command is a **permission node** (`cityworld.info`, `.teleport`, `.find`, `.schematic`,
`.export`), so LuckPerms — or any NeoForge permissions manager — can hand out finding and
world-jumping without handing out schematic pasting. With no permissions manager installed nothing
changes: the defaults are the operator levels the commands always used.

**→ Every command with usage examples: [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**

## Requirements

CityWorld runs on **three Minecraft versions**. Download the file for yours — the Minecraft version is
in the filename, so there is no guessing which is which.

| Minecraft | NeoForge | Java | File |
|---|---|---|---|
| 1.21.11 | 21.11.42+ | 21 | `cityworld-5.4.0+mc1.21.11.jar` |
| 26.1.2 | 26.1.2.95+ | 25 | `cityworld-5.4.0+mc26.1.2.jar` |
| 26.2 | 26.2.0.59+ | 25 | `cityworld-5.4.0+mc26.2.jar` |

**A given seed builds the same city on all three.** The layout — terrain, roads, districts, which
building stands where — is identical across versions; only the materials shift slightly, because newer
Minecraft versions bring new blocks into the building palettes. Every version is verified automatically
before release, generating a real world and checking the cities, signs and biomes come out right.

## Credits and licence

CityWorld is licensed under **GPL-3.0-only**.

- Original **CityWorld** Bukkit plugin by **DaddyChurchill** — the original author knows about this port
  and has approved it.
- This NeoForge port by **Sablednah**, continuing under GPL-3 as a derivative work.
- Terrain noise vendored from Bukkit (GPL-3), in turn derived from Stefan Gustavson's public-domain
  simplex work.

Full docs, screenshots and guides: **[sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**
Source, issue tracker and full port history: see the GitHub repository.
