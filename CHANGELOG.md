# Changelog

All notable changes to the NeoForge port of CityWorld.

Settings and terrain changes only affect **newly generated chunks** — existing chunks never
regenerate, so a fresh world (or unexplored land) is needed to see worldgen fixes.

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
