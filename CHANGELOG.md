# Changelog

All notable changes to the NeoForge port of CityWorld.

Settings and terrain changes only affect **newly generated chunks** — existing chunks never
regenerate, so a fresh world (or unexplored land) is needed to see worldgen fixes.

## Unreleased

### Added

- **Biome mods work in CityWorld worlds.** Biomes O' Plenty and most other modern biome mods register
  their biomes through **TerraBlender**, which CityWorld's own biome source never saw — so a CityWorld
  world with BoP installed got its 450 blocks and none of its 69 biomes. It now reads what those mods
  registered and folds them into the world. Measured with BoP installed: 113 biomes offered, 86 of them
  actually appearing. **On by default** — installing a biome mod is the intent; turning them off is the
  deliberate act (`world.useModdedBiomes`, or the Customize screen when such a mod is present). Costs
  nothing when you have no biome mods.
- **Biomes O' Plenty's cave biomes join the cave pool** — glowing grotto, crystalline chasm, spider
  nest and fungal jungle turn up underground alongside the vanilla four. Shipped inert, so it does
  nothing unless BoP is installed.

- **26.2's cinnabar and sulfur build with the rest.** Both new stone families join the MODERN and
  APOCALYPSE decorative palette, so cities on Minecraft 26.2 grow deep-red cinnabar and yellow sulfur
  buildings alongside the blackstone and copper ones. They are warm colours in a palette that was
  short of them. Stone buildings do not become any *more* common — the palette just has more stones in
  it. On earlier Minecraft versions the entries are simply absent.
- **Palettes document how to take a block out**, not just how to add one. NeoForge tags support a
  `remove` list, so a datapack can drop a single block — the sulfur, say — without replacing the whole
  palette. See PALETTES.md.

## 5.2.0

### Added

- **The End is reachable again — strongholds generate.** CityWorld suppressed every vanilla structure
  so it could own the chunk, and that quietly took strongholds with it: no stronghold means no End
  portal, so eyes of ender had nothing to find and a CityWorld world could not be finished by normal
  play. Strongholds are back, on vanilla's own ring placement, so `/locate` and eyes of ender agree
  with where they actually are.
- **Trial chambers and ancient cities generate too.** Which vanilla structures a world keeps is the
  block tag `#cityworld:allowed`, so a datapack can widen it — including to a structure from another
  mod — without touching the mod.
- **Real cave biomes underground: lush, dripstone and deep dark** — and **sulfur caves on Minecraft
  26.2**. They come in patches rather than filling the whole underground, so they stay a find. Each
  one decorates itself the way it does in a vanilla world: moss and glow berries, dripstone clusters,
  sculk, sulfur and cinnabar veins. Which biomes are in the pool is the tag `#cityworld:cave_pool`,
  so a modded cave biome can join it from a datapack.
- **Biome varies with depth, not just position.** The biome map is three-dimensional now, which is
  what lets cave biomes exist at all — and, as a side effect, what let ancient cities work, since
  vanilla only places them in deep dark.
- **The underground is tunable from a datapack** — a new `caves` settings group controls how big a
  cavern an ancient city gets, how far below the surface cave patches start, and the size, rarity and
  depth band of every cave type. Empty means the shipped defaults. See the configuration docs.

### Changed

- **Underground mob spawning differs where a cave biome landed.** Cave biomes carry their own spawn
  lists — thinner than open caves, and the deep dark carries none at all, plus wardens. That is the
  point of having real cave biomes rather than cave-shaped decoration, but it is a genuine gameplay
  change and not only a cosmetic one.
- **Vanilla structures can generate under a city.** A trial chamber may cut into a basement or a
  sewer. Ancient cities sit far below both (they top out around y −10, where cisterns bottom out at
  y 49), so those meet only the mine networks — which reads rather well, as though the miners downed
  tools when they broke through.

## 5.1.0

### Added

- **CityWorld runs on Minecraft 26.1 and 26.2** as well as 1.21.11. Minecraft moved to quarterly
  calendar releases, so the mod is now built and tested against several versions at once. Jars carry
  the version they are for — `cityworld-5.1.0+mc26.2.jar` — so there is no guessing which is which.
- **The mod has its icon and its own links in the mod list.** It previously showed no logo, and its
  home and issue links still pointed at the original author's pages — so bug reports would have gone
  to someone who cannot fix them. Issues now point at this fork's GitHub tracker.

### Fixed

- **26.2's block changes are absorbed without any visible difference.** 26.2 stopped declaring dyed
  blocks and copper as individual blocks — 145 of the blocks CityWorld builds with, including every
  colour of wool, terracotta, concrete, stained glass and beds, plus the whole copper family. Cities
  look exactly as they did; a given seed still produces the same city on every supported version.

## 5.0.3

### Added

- **Building palettes are block tags now, so new blocks join them on their own.** The palettes used
  to be runs of hand-written constants, which is why CityWorld was still building houses out of the
  six 1.14 wood types. Eight of them — planks, wool, terracotta, glazed terracotta, concrete,
  concrete powder, stained glass and the MODERN decorative stones — now resolve from
  `cityworld:build/*` block tags at world creation. Planks went from six woods to **twelve** the day
  this landed (pale oak, cherry, mangrove, bamboo, crimson and warped join the palette), and wool
  from ten colours to all sixteen.
- **Modded blocks arrive the same way.** A mod that tags its blocks conventionally — planks in
  `#minecraft:planks`, stone in `#c:stones` — starts showing up in cities as soon as it is installed,
  with no patch and no compatibility pack. For mods that don't tag their blocks, or for putting a
  mod's blocks somewhere they wouldn't naturally go, a datapack can extend any palette directly. See
  the new `PALETTES.md`.

### Changed

- **Palette odds are unchanged despite the wider contents.** Each tag occupies exactly the number of
  slots the constants it replaced did, so wooden houses are no more common than in 5.0.2 — only
  *which* wood varies. Without this a twelve-block planks tag would have doubled wood's share of
  every wall in the world, and a modpack with thirty wood types would have drowned the palette
  entirely.
- Palettes that are a curated look rather than "all of a family" are deliberately left as fixed
  lists: the muted greyscale of unfinished buildings, the pale civic palette of government offices,
  and the ordered road and maze lists.

> Because the palettes are wider, a seed generates slightly different **materials** than it did on
> 5.0.2. The terrain and city layout are unchanged, and existing chunks never regenerate.

## 5.0.2

### Changed (from a full review of the announce feature before release)

- **Announcements are per-world and curated.** Chat goes to the players in the world the landmark
  generated in (was: the whole server, with coordinates meaningless in other dimensions). And only
  the genuine rares announce by default — vaults, zoos, biodomes, hospitals, airships, saucers and
  flagged schematics; a new `world.announcedLandmarks` datapack list lets a server widen or narrow
  the set (castle, oil platform, radio tower, mine entrance, campground, fish pond and more are
  available keys). Previously every `reportLocation` call site would have hit chat: fish ponds,
  shacks, campgrounds, and every hospital department separately.
- **Vault road tunnels no longer announce.** Each road chunk through a vault was announcing its own
  hash as a different "Vault N" — several contradictory lines for one vault, none matching the number
  in the lobby. The entrance chunk's announcement is the only one now.
- **`/cityfind` understands titles.** It now matches and displays the `.yml` `Title`, so the name a
  player reads in chat ("The Statue of Liberty") is findable — previously only the raw file name
  ("liberty") matched, and results printed the file name.
- **Sidecar `.yml` values may be quoted anywhere.** Quoting was only honoured on `Title`, so ordinary
  YAML habits like `Decayable: "true"` silently parsed as *false*. Quoting (and inline `#` comments)
  now work on every key.
- **`.nbt` drop-ins no longer stamp recorded air.** A structure-block export records explicit air for
  its whole bounding box; placing one carved an air cuboid into the surrounding terrain, unlike every
  other format. Recorded air is now stripped unless the sidecar sets `KeepAir: true`, matching
  `.schematic`/`.schem`/`.litematic`.
- **Customize screen fixes.** Cycling the Style no longer silently resets the groups the screen has
  no widgets for (city radius, naming lists, mob bags) back to defaults — a datapack's custom values
  survive the cycle. The floors picker now always shows exactly the value that will save (an
  out-of-range datapack value used to display snapped but save raw), and its button no longer reads
  "…floors: 24 floors".

### Added

- **Landmark announcements.** A new `world.broadcastSpecialPlaces` setting (off by default) announces
  landmarks in chat as they generate — "Castle generated near 1520, -340" — to everyone on the
  server. Upstream had this and the port had never implemented it. The same events are always
  written to the debug log either way, so turning it off loses nothing but the chat.
- **Schematics can announce themselves.** The `.yml` sidecar's `BroadcastLocation` key was being
  parsed but never acted on; it now works, and fires once per building rather than once per chunk of
  its footprint. It needs the world's `broadcastSpecialPlaces` on as well.
- **`Title:` in schematic `.yml`.** Announcements previously could only use a schematic's filename,
  so `winchester.schematic` announced as "winchester". A title lets it read "The Winchester Tavern".
  Defaults to the filename, so a catalog already named nicely needs no change.
- **Seven bundled landmarks now announce themselves**, with proper names: the Statue of Liberty,
  Midwich, the Spiritwind Monument, the Dragon Statue, the Old Water Tower, the Hedge Maze and the
  Great Pagoda. Previously exactly one schematic was flagged to announce and it had no title, so it
  would have said "liberty". Only distinctive landmarks were picked — the common flavour buildings
  stay quiet.
- **The Statue of Liberty is copper now.** The build predates copper, so its skin was 13,563 light
  blue wool blocks. Converted to a vanilla `.nbt` structure with the wool remapped to weathered
  copper; every other block carries over unchanged.
- **Bundled schematics may now ship in any supported format.** The bundled index only accepted legacy
  `.schematic`, even though drop-in schematics could be `.schem`, `.litematic` or `.nbt` — which
  meant no bundled build could use a block newer than 1.12.
- **A Max building floors picker** in the single-player Customize screen (World section). The setting
  existed and worked from a datapack, but there was no UI control for it. Offers 8–60 floors; a
  hand-edited value in between displays as the nearest and is only overwritten if you turn the dial.

### Changed

- **The vault lobby got its Fallout glow-up.** The blast door is now a proper cog: a 10-block toothed
  steel gear — gray plate body, copper cross-spokes and hub, iron rim, eight gear teeth — with a
  hazard-striped doorway "rolled open" through the bottom, striped threshold, chain-and-grate winch
  machinery overhead, and VAULT-number wall signs flanking the opening. The old door was also centred
  half a block off the corridor (odd-width design against a 2-wide corridor); the gear is even-width
  and lines up exactly. The lobby itself gains an overseer's control console, a locker row (barrels
  and an anvil), and copper air vents let into the walls.

### Fixed

- **The vault's surface hut stood metres above the hillside.** Its floor height came from the cached
  base-terrain height plus a fudge, which overshoots on slopes — leaving a concrete stalk with a door
  in the air. It now scans the actual placed terrain at the door column and sits at real grade, with
  a deeper foundation skirt for the downhill side.
- **Lightning rods stood on moss carpet.** The rooftop-rod pass ran after overgrowth and stopped its
  downward scan at the first non-empty block — which, on a reclaimed roof, was the moss carpet or
  leaf litter lying on it, so the rod sat a notch above the roof looking like it floated. The pass
  now runs before overgrowth (the rod is part of the building, and the greenery creeps around it),
  and its scan requires a solid, load-bearing top face, so loose cover can't be mistaken for a roof
  either way.

## 5.0.1

### Fixed

- **Overgrowth debris was all identical.** Leaf litter and pink petals are segmented blocks whose
  default state is always one segment facing north, so every scrap of fallen-leaf debris was the same
  tile in the same orientation. Segment count and facing are now rolled per placement, so a littered
  floor reads as scattered rather than tiled.
- **Azaleas grew in thickets.** Two separate causes, both fixed:
  - The lush-cave surface tell ran per chunk with 6–10 attempts in each, but a lush region spans
    roughly 25 chunks — so a single patch could stack up hundreds of azaleas. Most lush chunks now
    get none, and the rest get one or two.
  - Azalea and flowering azalea together made up 10% of the overgrowth ground-cover pool. Their
    silhouette is loud and distinctive, so one in ten reclaimed surfaces wearing one read as a
    thicket (most visible on APOCALYPSE with overgrowth on).

### Changed

- **Overgrowth shrubs now match the local woodland.** The slot the azaleas held is filled with a
  persistent leaf block of the biome's own tree species — spruce in taiga and groves, birch in birch
  forest, jungle, acacia in savanna, dark oak, pale oak in the pale garden, mangrove, cherry in a
  cherry grove, oak everywhere else. Being `persistent`, they never decay away after placing.
  Azalea foliage is kept for flower forest and meadow, where a flowering shrub actually belongs.
- **More variety in the ground clutter.** Modern vegetation joins the pool: wildflowers, plain
  bushes, feral sweet-berry bushes (grown, not bare sprigs), dry grass, and — at about 1% — a
  firefly bush, for a rare glow in the ruins. Roads pick up wildflowers, dry grass and dead bushes
  alongside the existing light dusting.

### Housekeeping

- Version bumped to **5.0.1** and the `-beta` tag dropped.
- 21 screenshots added to the README.
- CurseForge description now links to
  [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/) for the
  configuration and command deep dives.

## 5.0.0

First public release of the NeoForge port — a full port of the original Bukkit/Spigot CityWorld
plugin to Minecraft 1.21.11 / NeoForge 21.11.42.

- Procedural cities: roads with street-name signs, sidewalks, roundabouts, bridges, tunnels,
  furnished buildings from houses to highrises, municipal/industrial/farm districts, parks with zoos
  and biodomes.
- Underground: mine networks with lift shafts, sewers, bunkers, basements, cisterns, loot and
  spawners; an APOCALYPSE-only Fallout-style vault.
- Nature: CityWorld's own vendored terrain noise extended to the `-64..319` world, winding cave
  tunnels, lush cave patches, basalt-lined lava pools, a CityWorld-aware biome source.
- Inhabitants: named villagers employed at their shop's trade, animals, sea life, hostiles.
- 13 world styles, ~100 per-world settings delivered as a datapack, an in-game Customize screen, and
  the `/cityinfo`, `/cityworld`, `/cityschem`, `/cityfind`, `/cityexport` and `/cwlocate` commands.
- Custom schematics in `.schematic`, `.schem`, `.litematic` and `.nbt`.
