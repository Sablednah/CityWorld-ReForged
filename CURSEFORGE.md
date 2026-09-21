![CityWorld — reforged for NeoForge](https://media.forgecdn.net/attachments/description/1648913/description_c782ad1e-4ae0-4f12-a2b3-11b8862c5669.png)

# CityWorld — reforged for NeoForge

**Drop into an endless, hand-crafted-feeling city** — procedurally generated, seed-deterministic, and
packed with detail: named streets, furnished buildings, roundabouts, factories, farms, mines, sewers,
and wild nature in between. This is a full **NeoForge port** (and, on Minecraft 1.20.1, a **Forge**
build) of the classic Bukkit/Spigot plugin
[CityWorld](https://www.spigotmc.org/resources/cityworld.2250/), rebuilt from the ground up for modern
Minecraft — same generator brain, same GPL-3 license, now a world type you can pick from the create-world
screen.

Battle-tested across **135 worlds and 3.2 million generated chunks** — over **830 km²** of procedurally
generated city, more ground than New York City covers.

**New in 5.11.0: Minecraft 1.20.1, on Forge.** The first CityWorld build that is not NeoForge — asked
for here in the comments, because 1.20.1 is where a great many mods still live. Nothing is cut down for
it: every world style, the ruined Nether and End cities, strongholds, interiors, schematics, the
Customize screen, and the furniture and map mods all work as they do on the newer versions, and a seed
builds the same city there as everywhere else. The other five versions are unchanged.

**New in 5.10.1: Minecraft 26.3.** A build for the 26.3 line, where campgrounds pitch their tents from
wool stairs the way 26.3's own abandoned camps do, the new **dappled forest** joins the climate, and
**poplar** turns up in buildings by itself. The other four versions are unchanged and stay on 5.10.0.

**New in 5.10.0: Macaw's fits the city out** — doors, windows, fences, roofs, staircases and lights from
the Macaw's mods (see below), pitched roofs on modern houses, silos and gasometers in industry, and a
**Minecraft 1.21.1** build alongside 1.21.11, 26.1 and 26.2.

**New in 5.8.0: every street has a name you can use** — `/cityinfo` and the map name the road you are
on, JourneyMap labels the streets of cities you have not reached yet, and `/cityfind street` finds one.
Plus a proper **two-chunk airship** drifting over the wild.

**New in 5.7.0: JourneyMap support**, see below — the map draws the city plan over ground nobody has
explored, and names what is planned in any chunk you point at.

---

## What you get

Pick **CityWorld** as your world type and you land in a living city:

- **Roads and infrastructure** — named streets with real street signs (and the name on `/cityinfo`,
  F3 and the map), sidewalks, roundabouts (with
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
  26.2 and later — each decorated the way vanilla decorates it.
- **Vanilla structures where they belong** — **strongholds** (so eyes of ender work and the End is
  reachable), **trial chambers**, and **ancient cities** in the deep dark. Villages and mineshafts stay
  off: CityWorld builds its own. A datapack tag can widen the list, including to another mod's
  structures.
- **Inhabitants** — named villagers employed at their shop's actual trade, animals in the fields, fish in
  the sea, hostiles lurking in mines, sewers and the dark.
- **Set-pieces** — castles, radio towers, oil platforms, flying saucers, hot-air balloons, two-chunk
  airships with a crewed control car, campgrounds —
  scattered rare landmarks, findable with `/cityfind`.
- **Custom schematics** — a bundled catalog of classic buildings, plus drop your own
  (`.schematic`/`.schem`/`.litematic`/`.nbt`) into a config folder and turn them loose in the city.

## The map knows what the city is — with JourneyMap

**New in 5.7.0.** Install **JourneyMap** and the map gains what only CityWorld can tell it. A map mod
draws what you have *seen*; CityWorld decided where every road and district goes before anyone
arrived — so the map shows **the plan**, over ground nobody has explored:

- **Districts tinted by what they are** — highrise, municipal, industrial, park, farm — with the
  street grid over them, and the roads running out through the countryside. It stays drawn behind you
  as you travel.
- **Point at any chunk and it tells you what is planned there** — the district, the kind of lot, the
  schematic's name, the shop, and what is inside the building: *"Highrise · office building · Office
  cubicles"*, *"Farm · Potato field"*. Places nobody has visited included.
- **Streets are labelled on the map** once you zoom in (new in 5.8.0), cities you have never visited
  included, and pointing at a road names it.
- **Rare landmarks become waypoints as they generate**, and `/cityfind`, `/cityfind lot` and
  `/cwlocate` drop a marker on whatever they find — in two separate groups, so the world's own
  discoveries and your searches can be shown or hidden apart.
- **Controls where you would expect them**: a *City plan* switch in JourneyMap's options, a button on
  its fullscreen map, and `/citymap` for servers. `/citymap keep <n>` sets how much of the plan your
  client holds, because that cost is yours, not the server's.

It is a soft dependency in the proper sense: no JourneyMap, no difference — CityWorld does not touch
a single one of its classes unless it is installed.

## Beyond the overworld — the ruined Nether and the End

**New in 5.9.0.** A CityWorld world type now comes with its own Nether and End (switch either back to
Vanilla under Customize → Realms):

- **The ruined-city Nether** is the overworld's own city — same seed, same streets, same buildings — burnt
  and collapsed, in Nether biomes (and Biomes O' Plenty's, when installed), on netherrack and blackstone with
  lava seas. **Portals link 1:1**, so the ruin you arrive in stands where its overworld building does. Full
  height, no bedrock roof; fortresses and bastions still generate, each bastion in its own cavern with a
  ruined shaft down to it from the street.
- **The End is vanilla's throughout** — the central island, the dragon fight, the void ring, the outer
  islands with their chorus forests and end cities — with CityWorld's cities on the flat tops of the outer
  islands: clumps of city and clumps of wild End, streets lit by end rods, buildings blended with purpur and
  end stone brick, never ruined (the dragon kept everyone out). Biome mods' End biomes generate in the wild
  stretches. Nothing digs through the islands; basements go only as deep as the rock allows.
- **`/cityworld`** visits the city before the fall — the overworld's own plan, pristine.
- Modpacks can lock any of it: `lockedWorldPreset`, `ruinedNether` and `cityworldEnd` in
  `config/cityworld-startup.toml`.

## Furnished — and furniture mods make it more so

**New in 5.5.0:** every building has an interior. Offices and apartment towers, schools and
courthouses inside the government buildings, museum exhibits, factory control rooms, stocked shops
and warehouses — lit, labelled (check F3), and furnished down to the basements. Vacant buildings
hang FOR SALE / TO LET signs and stay dark.

Install **Macaw's Furniture**, **MrCrayfish's Refurbished Furniture** or **Fantasy's Furniture** (any
of its sets — Nordic, Necrolord, Dunmer, Bone — plus the Decorations add-on) and the whole city
upgrades: real kitchens with fridges and stoves, two-block baths, sofas, desks with computers,
ceiling fans, two-tall chairs and double beds, stocked shelves, tables cluttered with books, bottles
and tankards — over a thousand modded pieces, every one facing the right way. **New in 5.6.0:** a
Fantasy's Furniture set released *after* this version is recognised at startup and furnished on the
spot, and APOCALYPSE draws on a grim set of skulls, cobwebs and bone piles. Without furniture mods,
rooms keep a classic vanilla look. Mod authors: adding your own furniture takes two data files in
your jar — see `PALETTES.md` in the repo.

### Macaw's fits it out

**New in 5.10.0.** Install any of **Macaw's Doors, Windows, Fences and Walls, Roofs, Stairs, Trapdoors or
Lights** (Macaw's Furniture already furnishes the rooms, see above) (and the Biomes O' Plenty add-on, which joins by itself with every BoP wood) and the
city is built with them. Every house, shop, office and factory picks its street door by what it is for —
cottage and modern doors on homes, glass shop fronts, metal doors and **garage doors** three to seven high
on industry — and its interior doors to match. Houses get framed windows joined into runs, a trapdoor
hatch, and **pitched roofs** in Macaw's roof blocks, matched to the wall wood, with eaves, ridges, valleys
and gable ends; their stairs become Macaw's compact and terrace treads with railings, landings and balcony
rails, and office stairwells take the same treads and platforms. Gardens and parks are fenced in picket,
hedge, ornamental metal and stone walls, paddocks and barn pens in farm fences with a gate, construction
sites and factory yards in industrial mesh and panelled metal (barbed wire now and then), and every park
or yard sticks to one fence. Streets are lit by Macaw's lamp posts, park gates by garden lights,
campgrounds by tiki torches, and interiors by its wall lanterns, sconces, chandeliers, ceiling lights and
lamps. Without the mods nothing changes — each pool falls back to what CityWorld always built.

Industry itself grew: **silos** — a metal tank on a red steel frame with a hopper chute, a stepped cone
roof and a caged spiral stair up to a catwalk on the roof, often in batteries — and rare **gasometers**,
two or three chunks square, a telescoping gas holder in a lattice frame standing in a water trough, its
bell at a different height on every one. The old nether-brick silo schematics are gone.

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

`/cityinfo` tells you what's under your feet, street name included; `/cityfind`/`/cityfind lot`/
`/cityfind street`/`/cwlocate` track down a specific building, landmark, street or biome; `/cityschem` pastes catalog buildings by hand; `/cityexport`
bottles a world's settings to hand to a server.

Every command is a **permission node** (`cityworld.info`, `.teleport`, `.find`, `.schematic`,
`.export`), so LuckPerms — or any NeoForge permissions manager — can hand out finding and
world-jumping without handing out schematic pasting. With no permissions manager installed nothing
changes: the defaults are the operator levels the commands always used.

**→ Every command with usage examples: [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/)**

## Requirements

CityWorld runs on **six Minecraft versions**, across two loaders. Download the file for yours — the
Minecraft version is in the filename, so there is no guessing which is which.

| Minecraft | Loader | Java | File |
|---|---|---|---|
| 1.20.1 | **Forge** 47+ | 17 | `cityworld-5.11.0+mc1.20.1.jar` |
| 1.21.1 | NeoForge 21.1.251+ | 21 | `cityworld-5.10.0+mc1.21.1.jar` |
| 1.21.11 | NeoForge 21.11.42+ | 21 | `cityworld-5.10.0+mc1.21.11.jar` |
| 26.1.2 | NeoForge 26.1.2.95+ | 25 | `cityworld-5.10.0+mc26.1.2.jar` |
| 26.2 | NeoForge 26.2.0.59+ | 25 | `cityworld-5.10.0+mc26.2.jar` |
| 26.3 | NeoForge 26.3.0.3+ (beta) | 25 | `cityworld-5.10.1+mc26.3.jar` |

**A given seed builds the same city on all six.** The layout — terrain, roads, districts, which
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
