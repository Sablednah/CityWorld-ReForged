# Building datapacks for CityWorld — a plan

*Read-only design, 2026-11-08. Grounded in master (1.21.11). File paths are under
`src/main/java/me/daddychurchill/CityWorld/` unless absolute.*

## 0. What exists, in one paragraph

A platmap (10×10 chunks, `Support/PlatMap`) gets a `Context` from the nature ladder
(`Plugins/ShapeProvider_Normal.getContext`; vanilla land uses distance from the city centre in
`ShapeProvider_Vanilla.getContext`). `UrbanContext.populateMap` runs, in order: the subway station claim,
`populateSchematics` (the `Clipboard` family for that district, odds per clip, footprint scan in
`PlatMap.placeSpecificClip`), then backfill: `getPark`/`getBackfillLot` → `getBuilding`, a hard-coded weighted
switch per context (`UrbanContext.getBuilding` 12-way, `MunicipalContext.getBuilding` 3-way,
`IndustrialContext.getBuilding`), flood-filled into 2×2+ runs. A multi-chunk campus is a pre-pass that claims
chunks before backfill: `MunicipalContext.placeHospitalCampus` → `HospitalLot(sizeX, sizeZ, offX, offZ,
floors, main)` (each chunk draws its slice from a world-coordinate footprint mask); `Plats/Urban/Mall.place`
nominates ONE platmap per 4×4-platmap region (`Mall.nominated`, macro odds) and claims wings + car parks.
A tower is `FinishedBuildingLot`: materials rolled in `loadMaterials` → per-part `Palettes.pick`
(28 parts, tag `cityworld:palette/<style>/<part>`), shape in `calculateOptions` (insets, roof style/feature,
corner style, interior style, glass), rooms per floor from a `RoomProvider` (`Rooms/Populators/*`, 40 of them:
`CivicWithOffices`, `OfficeWithCubicles`, `StoreWithRegisters`, `HouseKitchens`, …) chosen by the subclass
(`OfficeBuildingLot.roomProviderForFloor`, `GovernmentBuildingLot` picks a civic flavour). Data-driven today:
palettes/pools (tags), substitutes (data map), `cityworld:schematic_fitout`, `cityworld:vehicle`,
`cityworld:zoo/*`, `cityworld:world_settings`, all bound at level load in `CityWorldServerEvents` (lines 58–60)
by the `ZooCatalog.bind` pattern (sorted by id, mod-gated, logged count). Schematics are files, not data:
`Clipboard/SchematicLibrary` reads the jar's `cityworld/schematics/<Family>/index.txt` and
`config/cityworld/schematics/<Family>/`; a pack jar cannot contribute schematics today.

## 1. The spectrum, and where the line sits

| | Rule data: a *building kind* | A single schematic | A piece set (kit) |
|---|---|---|---|
| Good for | Anything CityWorld already draws procedurally and must fit the grid, the style, the neighbours' heights and the decay/overgrowth passes: towers, civic blocks, shops, a fire station, a police station, a **cyberpunk tower**. Infinite variety from a few numbers. | A hero set piece with a hand-made interior: a Pokémon Center, the Winchester, a hangar with a specific IV plane decor. Exact look, zero variety, fixed footprint, random quarter-turn. | Things made of repeated modules on the chunk grid: runways, terminals, malls, hospitals, a **Rapture tunnel ring**. The module is 16×16×N; the "connector" is the chunk edge and a role. |
| Controls | footprint range, floors (count/height), palette parts, roof/window/corner choices, interior room mix by name, odds, districts, styles, vehicles/spawn hooks, title, loot | sidecar keys (odds, ground level, flips, decay, loot) + fit-outs | pieces by role (`corner`, `edge`, `inner`, `end`, `mid`, `gate`), axis, length range, the same envelope as a kind |
| Cannot control | the drawing primitives (how a wall, stair, door, atrium is drawn), terrain levelling, roads | everything procedural: palettes don't touch it, no neighbour awareness, no vehicles | free-form topology (no T-junctions you did not draw a piece for) |

**Vanilla jigsaw inside a lot: no, not for v1.** `JigsawPlacement.generateJigsaw` needs a
`Structure.GenerationContext` (RandomState, StructureTemplateManager, a `ChunkGenerator`, a seed-derived
`WorldgenRandom`) and runs at structure-*start* time; CityWorld plans a platmap as a pure function on any
thread (`Planners` pool, no level), and a lot is drawn per chunk. Wrapping vanilla's assembler would mean
stubbing a context and then clipping the output to chunk slices, and its output is not a function of the two
numbers a platmap has (origin + seed) unless every input is pinned. Also every one of the six lines has its own
`JigsawPlacement` signature (the 26.3 density/`Climate.Sampler` drift already cost a port). The chunk grid IS
CityWorld's connector system: `HospitalLot`/`MallLot` prove it. So the line is: **a kind describes a
building; a kit describes a building made of chunk-sized pieces by role; a schematic is one piece.** A kind
may *use* a schematic as its ground floor or its piece — later.

Examples placed on the spectrum:
- **Cyberpunk tower** → a kind (`type: tower`): floors 12–40, `floor_height` 4–5, roof `features: [antenna, helipad, billboard]`, windows `palette/cyberpunk/building/window`, interiors `office_cubicles | apartments | arcade(new)`, `lights: cyberpunk` pool. Plus a style (section 4).
- **Rapture district** → a style (flooded terrain, art-deco palettes, glass skybridges) + a kit for the tunnel ring; not a kind.
- **Fire station** → a kind: 2–3 floors, 6-high ground floor with garage bays (`interior: engine_bay`), hose tower roof feature, `vehicles: fire_engine`, infill in Municipal/Lowrise/Neighborhood.
- **Police station** → a 1×2 campus kind: the station (civic palettes, `interior: [civic_offices, cells]`) + a yard chunk (`parking`, `vehicles: police_car`).
- **Airport** → a kit, `placement: outland`: runway (1×8–14 chunks), apron, terminal, hangars, control tower (a 1-chunk tower kind), outside cities on flat land, rare.

## 2. The first datapack format

**Registry:** `cityworld:building`, files `data/<ns>/cityworld/building/<name>.json`, codec a record like
`ZooCatalog.Animal`, registered in `CityWorldRegistries.onNewDataPackRegistry`, bound in
`CityWorldServerEvents` next to `ZooCatalog.bind` into `worldgen/BuildingCatalog` (sorted by id, mod-gated,
`weight 0` removes a shipped one — the zoo rule). Everything is decided at plan time from `platmap`/`chunk`
`Odds` forks keyed by the kind id (`Odds.fork`, the 6.9.0 rule), so the same seed builds the same city.

```json
// data/cityworld/cityworld/building/fire_station.json
{
  "type": "cityworld:tower",
  "title": "Fire station",
  "weight": 3,
  "districts": ["municipal", "lowrise", "neighborhood"],
  "styles": ["modern", "apocalypse"],
  "placement": { "kind": "infill", "footprint": { "x": [1, 2], "z": [1, 1] }, "beside_road": true },
  "floors": { "above": [2, 3], "below": [0, 0] },
  "ground_floor": {
    "height": 6,
    "interior": "cityworld:engine_bay",
    "street_doors": "garage",
    "vehicles": [{ "kind": "fire_engine", "count": [1, 2], "facing": "street" }]
  },
  "interior": ["cityworld:civic_offices", "cityworld:lounges"],
  "palette": {
    "wall": "#cityworld:palette/fire_station/wall",
    "roof": ["minecraft:red_concrete"],
    "window": ["minecraft:glass_pane"],
    "column": ["minecraft:white_concrete"]
  },
  "roof": { "styles": ["flat"], "features": ["hose_tower"] },
  "walls": { "inset": [1, 1], "rounded": false, "corners": ["filled"] },
  "loot": "cityworld:chests/fire_station",
  "name": "{place} Fire Station",
  "requires_mod": null
}
```

Field semantics, and what each maps to in Java:
- `type` picks the lot class: `cityworld:tower` → new `Plats/Urban/DataBuildingLot extends FinishedBuildingLot`
  (overrides `loadMaterials`/`calculateOptions`/`roomProviderForFloor`/`newLike`/`getInteriorDescription` from
  the record); `cityworld:campus` → new `DataCampusLot extends IsolatedLot` (the `HospitalLot` shape: size,
  offsets, a footprint mask, each chunk draws its slice, sub-lots by role); `cityworld:kit` → `KitLot` (phase 2);
  `cityworld:schematic` → the existing `ClipboardLot` with the clip named by `schematic` (phase 2, makes the
  sidecar optional and lets a pack *jar* ship schematics under `data/<ns>/cityworld/schematics/`).
- `districts` = the `SchematicFamily` names, lowercase (`highrise … outland`, `nature`); `styles` = the Customize
  names used by `Palettes` (`modern`, `apocalypse`, …), empty = all.
- `placement.kind`: `infill` (offered to `getBuilding`), `campus` (pre-pass claim, like the hospital),
  `outland` (region-nominated, like the mall; phase 2). `footprint` is in chunks, `beside_road` uses
  `platmap.isExistingRoad`. `infill` respects the context's flood-fill as any lot (`newLike`).
- `floors.above/below` clamp to `DataContext.maximumFloorsAbove/Below`; `height` of a floor is `FloorHeight`
  (4) unless given; a 6-high ground floor is `firstFloorHeight` (already a field).
- `interior` names are the `Rooms/Populators` classes as ids: a small `RoomProviders` map
  (`cityworld:office_cubicles` → `OfficeWithCubicles`, …); a `/citybuildings interiors` command and a generated
  `config/cityworld/buildings-example/README` list them (the `SettingsExample` pattern), since the vocabulary is
  the thing a pack author cannot guess.
- `palette.<part>`: either a tag (`#…`) or an inline list; inline lists become an anonymous pool sorted by id;
  a tag is read through `Palettes`/`MaterialTags` exactly as the 28 parts are. Omitted parts fall back to the
  district's default roll, so the hospital-white problem ("fixed looks") is avoided.
- `roof.styles/features`, `walls.inset/rounded/corners` are the enums in `FinishedBuildingLot`
  (`RoofStyle`, `RoofFeature`, `CornerWallStyle`, `InsetStyle`) by lowercase name; `hose_tower`, `billboard`
  are new features, Java once each.
- `vehicles` → `worldgen/Vehicles.park(level, kind, x, y, z, heading, fuel, odds)` as the mall bays use it;
  `facing: street` is resolved from the lot's road side at draw time. A new `kind` is a pack's own vehicle file
  (`"kind": "police_car"`, `"paints": ["_police"]`) — no Java.
- `name` uses `Support/PlaceNames` tokens (`{place}`, `{name}`, `{n}`) for the sign and the `Findable` title.
- `loot`: the lot's `ownLootTable()`; `ContainerLoot` fills every container with it (existing seam).
- Entity spawns are NOT declared here: the `cityworld:spawn` registry targets `lot: "cityworld:fire_station"`
  (the kind id is exposed through `LotInfo`/`Findable`), so a spawn pack can add firefighters to someone else's
  fire station. Vehicles stay in the building file because they are geometry (which bay, which heading).

**What it cannot control, by design:** road layout, terrain levelling (`isLevellableLots`, the city's one
street level), decay/overgrowth passes (they run after, as for every lot), how a stair/door/window is drawn,
the district ladder thresholds, the flood-fill behaviour, loot filling rules. **What stays Java:** the lot
classes, room populators, roof features, the drawing primitives, `Vehicles` NBT, site nomination.

**Determinism and performance.** The catalogue is bound once per level before any plan (the zoo rule).
`getBuilding` draws ONE extra roll per lot from the context's `Odds` when any data kind is eligible (not per
kind), so a world with no packs still plans identically to today *only if* shipped kinds are `weight 0` — a
shipped fire station changes every modern-family plan: accept it as a release note (`--compare` will flag it,
as every planner change does). Per-lot cost is one list lookup; a campus footprint search is `findRun`'s
40 darts (hospital) — no full scans except for `outland` kits, which use `placeSpecificClip`'s bounded scan.

## 3. Fire station, police station, airport — end to end

**Fire station (phase 0).** Pack author writes the JSON above and (optionally) a loot table
`chests/fire_station` and a `cityworld:vehicle` file with `kind: fire_engine` for their IV pack. CityWorld adds
once: `BuildingCatalog`, `DataBuildingLot`, `UrbanContext.getBuilding` → `BuildingCatalog.infill(context,
odds, platmap, x, z)` consulted first (null → the existing switch); `Rooms/Populators/CivicWithEngineBay`
(bays 4 wide × 6 high along the street wall, garage doors from `#cityworld:fittings/garage_door` through the
existing `garageDoorPool()`/`garageDoorHeight` seam in `FinishedBuildingLot.calculateOptions`, hose reels,
lockers = pooled furniture); `RoofFeature.HOSE_TOWER` (a 3×3 column 2 floors up with a ladder, next to
`drawAntenna`/`drawHelipad`); `Findable`/`/cityfind fire_station` via the kind id. Vehicles: the bay's floor
cells and the street heading are known at `generateActualBlocks` time → `Vehicles.park(kind, …)`, one per bay
at the kind's count; `roads.vehicles` gates it as the car parks are gated.

**Police station (phase 1).** `type: cityworld:campus`, `footprint {x:[1,2], z:[2,2]}`, `parts`:
`[{ "role": "station", "cells": [[0,0],[1,0]], "tower": { floors [2,3], interior ["cityworld:civic_offices",
"cityworld:cells"], palette government/* } }, { "role": "yard", "cells": [[0,1],[1,1]], "lot": "cityworld:parking",
"vehicles": [{ "kind": "police_car", "count": [2, 5] }], "fence": "#cityworld:fittings/fence" }]`. CityWorld
adds once: `DataCampusLot` (claim via a generalised `MunicipalContext.placeHospitalCampus` → `BuildingCatalog
.placeCampuses(generator, platmap, context)` called at the top of `UrbanContext.populateMap` after the subway
claim; `findRun` + `claim` lifted into the catalogue), part roles `tower` (delegates to `DataBuildingLot` with
a shared `Odds` fork so the two chunks agree, like `makeConnected`) and `parking` (`Plats/Urban/ParkingLot`
exists: 167 lines — give it bays + `Vehicles.park`), `Rooms/Populators/CivicWithCells` (iron bars, iron doors,
a bed and a bucket per cell — one afternoon). Police paint left out of car parks: today's `Vehicles.bind`
groups by `kind`, so a `police_car` kind never fills `car` bays — already true.

**Airport (phase 2–3).** `type: cityworld:kit`, `placement: { kind: "outland", "region": 8, "odds": 0.25,
"flat_rise": 4, "road_link": true }`, `axis: "random"`, `pieces`: `runway_end` (1×1 schematic or drawn),
`runway_mid` (1×1, repeated `length [8,14]`), `taxiway`, `apron` (2×2), `terminal` (`tower` rules: 2 floors,
`floor_height` 6, glass front, `interior: ["cityworld:departures"(new), "cityworld:lounges"]`), `hangar`
(1×2 schematic, `vehicles: plane`), `control_tower` (1×1 tower: 6 floors, glass top, `roof.features:
["antenna"]`). Layout: `"layout": "airfield"` is Java (runway along the axis, apron beside its middle, terminal
behind the apron, tower beside the terminal, hangars at the apron ends) — a kit declares pieces; the
*arrangement* is a named Java layout (`airfield`, `ring`, `bar`), because free-form layout is the jigsaw we
are not building. CityWorld adds once: `KitLot` (role + offset + rotation per chunk, drawing a drawn piece or
pasting a schematic piece with `Clipboard.pasteChunk`), region nomination (`Mall.nominated` generalised:
`region` platmaps a side, one nominee, dice, fit) in `NatureContext`/`FarmContext`/`OutlandContext.populateMap`
(after the mall's claim and before the terrain survey; `platmap.isLevellableLots(x, z, w, h, flat_rise)` for
the strip — the hospital's `MAX_RISE` idea), `road_link`: pave a one-lot-wide stub from the terminal's lot to
the nearest existing road line (`PlatMap.paveLot`, `nearestRoadLine`); on vanilla land (phase 3) a site between
`CitySites` cells judged flat by `CitySites.heights().top` variance along the strip, lifted by
`ShiftedRegion`, and an exit from `worldgen/Highways` (`exitsAt`) if a link passes. `find:airport` probe
and `/cityfind airport`. A plane kind is a pack's `cityworld:vehicle` with `kind: plane`; `Vehicles.park` is
geometry-agnostic already.

## 4. Styles: cyberpunk/neon and Rapture

A style pack today can ship: a world preset `cityworld:cyberpunk` (`worldgen/world_preset`) whose dimension
names a `world_settings` entry (copy of `modern.json`: `naming.placeFormat "Sector {n}"`, `mobs`, `spawns`,
lights, `maxBuildingFloors` 60, `roads.skybridges true`), palette tags, substitutes, fit-outs, loot, and now
building kinds with `styles: ["cyberpunk"]` + a `districts` mix. **What is missing to make one:**
1. **A style id as data.** `WorldStyle` is a Java enum; `Palettes` looks up `palette/<style>/<part>` by it, and
   `styleDefaults`/`isModernStyle()` branch on it. Add `world.styleId` (string, default = the enum name) to the
   settings so a pack's `palette/cyberpunk/*`, `light/cyberpunk/*`, kinds `styles: ["cyberpunk"]` resolve, while
   the *behavioural* style stays `MODERN`/`APOCALYPSE`/`FLOODED`. Settings `world` group: check its field count
   (the top-level codec and `terrain` are at the 16 cap; `world` may be too — if so it goes in `naming`, which
   is small and already holds `placeFormat`).
2. **District mix as data.** `cityworld:district/<style>/<district>.json`: `{ "infill": { "cityworld:office": 5,
   "mypack:neon_tower": 8, "cityworld:store": 3 }, "parks": 0.05 }` — weights over kind ids *including* the
   built-in lots exposed as ids (`cityworld:office`, `cityworld:store`, `cityworld:library`, …). Replaces the
   hard-coded switches when present; the ladder thresholds stay Java (a wholly different ladder is a new
   `ShapeProvider`, i.e. a Java style).
3. **Light pools per style** (`cityworld:light/<style>/street|interior|sign`: froglights, sea lanterns, copper
   bulbs, Macaw's lights) — `DataContext.lightMat`/`torchMat` are two fields today.
4. **New palette parts**: `skybridge/deck`, `street/lamp`, `sign/board`, `building/trim` (neon strips: a
   window-top course). Rapture needs `skybridge/deck = glass` and the skybridges on in Modern.
5. **Rapture terrain**: `Flooded` style exists (`ShapeProvider_Flooded`, water at street level) — Rapture is
   "flooded, sea level far above the roofs, buildings sealed, glass tunnels between towers, art-deco palettes".
   The sealing (no exterior doors, pressurised lobbies) is a kind flag `sealed: true`; the water column over the
   city is `terrain.seaLevel`/fluids settings; dry interiors need `KeepAir`-like fill rules — Java once.
6. **A pack jar shipping schematics** (`data/<ns>/cityworld/schematics/<Family>/…` read by `SchematicLibrary`
   alongside the jar index and the config folder) — one `getResource` loop.

## 5. Phased build order, risks, tests

- **Phase 0 (spike, 1–2 days): the registry + `tower` kind + infill + one shipped fire station.** Proves value
  end to end: a JSON turns into a building with engines in its bays. Java: `BuildingCatalog`, `DataBuildingLot`,
  `RoomProviders` name map, `CivicWithEngineBay`, `HOSE_TOWER`, `getBuilding` hook, `Findable`. Tests: self-test
  `checkBuildingCatalog` (binds, count ≥ 1, a `DataBuildingLot` found by `findables` in the sweep, a bay cell
  holds a garage door — a proved-failing variant with the hook disabled), `-Dcityworld.probe=find:lot:
  DataBuildingLot`, `-Dcityworld.diagnostics` sweeps the kind's palette lists for empties, `--compare` on the
  six lines (expect the plan change and record it).
- **Phase 1: `campus` kind + police station + parking with vehicles + `cells` populator.** Tests: the hospital
  claim refactor must leave `--compare` equal with the catalogue empty (do it first, measure, then add kinds).
- **Phase 2: `schematic` kind (pack-jar schematics) + `kit` kind + outland nomination + the airport on
  CityWorld land.** Tests: `survey:` style probe printing `sites()` counters (nominated/dice/noFit/built, the
  mall's), `region_render.py` of one airport (shape questions need a picture), `/cityfind airport`,
  `checkDirectLoad`-style forced-ticket load of a whole airport (a kit draws across ~20 chunks: the deadlock
  class). Watch the runaway-loop trap the skybridge deck hit (far-chunk `setBlock`).
- **Phase 3: vanilla-land airports** (flat strip between `CitySites` cells, lifted, highway exit). Tests on the
  owner's vanilla seed; `survey:highways` already exists.
- **Phase 4: style as data** (`styleId`, `cityworld:district`, light pools, new parts, pack-jar schematics),
  with a cyberpunk sample pack like `packs/neutral-palette` (its own GitHub release, never CurseForge).
  Rapture after, on `Flooded`.

**Risks.** *Determinism:* every pick on an `Odds.fork` keyed by kind id and position; catalogue sorted by id;
installing a pack changes plans (document, as palettes do). *Performance:* bind once; no per-chunk registry
reads; outland scans bounded; kits preload `StructureTemplate`s at bind. *Settings cap:* the registry is NOT a
settings field, so phase 0 adds nothing to the codec; the one toggle (`includeDataBuildings`) should ride on the
kinds' own `weight`/`styles` instead of a setting until a group has room. *Six lines:* the registry code
cherry-picks cleanly (zoo/vehicles did); drift is `Identifier`/`ResourceLocation` (already per-branch),
`tags/blocks` on 1.20.1 for any shipped palette tag, 26.3's loot dialect for `chests/fire_station`
(`gen_style_loot.py` route), `StructureTemplate` placement via the existing `Clipboard` abstraction only.
*Silent failure:* an unknown interior id, an empty palette list or a missing vehicle kind must log at bind
(`CityWorld buildings: 3 kinds (1 skipped: …)`) and the lot must still draw — a kind that fails must not fail
the platmap (`populateLots FAILED` turns a district to nature).

## 6. Walkthrough: a modpack author adds "a fire station with IV fire engines"

1. Reads the wiki page *Building Kinds* (to be written from a `BUILDINGS.md`, fed to `gen-wiki.sh`). Learns the
   folder: `data/mypack/cityworld/building/fire_station.json`. **Stuck 1:** the file lives in a *datapack*,
   while schematics live in `config/cityworld/schematics/` — two homes; the doc must say why (schematics are
   files, kinds are data) and that a pack's `overrides/` can carry both.
2. Copies the shipped example from `config/cityworld/buildings-example/fire_station.json` (generated per build,
   like `settings-example/`). Changes `weight` and `districts`. **Stuck 2:** which district names exist and what
   each means — the table from SCHEMATICS.md, reused.
3. Picks `interior` names. **Stuck 3:** the vocabulary. Needs `/citybuildings interiors` or the README list;
   without it they guess `"garage"` and get a quietly empty floor. The bind log must name the bad id.
4. Vehicles: runs `python3 -I scripts/gen_iv_vehicles.py --list <their pack jar> <OCP jar>`, finds the fire
   truck, writes `--kind pack:firetruck=fire_engine --out …/data/mypack/cityworld/vehicle`. **Stuck 4:** if their
   pack has no fire engine, the bays stay empty with no message — `Vehicles.bind` must log kinds a kind asked
   for and none provided. **Stuck 5:** does the engine *fit* the bay? The vehicle file has `centre` but no
   length/width; add `size: [w, l]` to `cityworld:vehicle` (the generator can read `collision` from the IV
   json) so a bay is sized or the vehicle refused with a log line.
5. Loot: writes `data/mypack/loot_table/chests/fire_station.json` — **Stuck 6:** `loot_tables/` on 1.20.1 and
   typed rolls on 26.3 (LOOT.md covers it; the kind doc must link it).
6. Tests: new world, Modern, `includeMunicipalities` on. **Stuck 7:** municipal is ~5% of platmaps and the kind
   is one roll among a dozen — they will not find one by walking. `/cityfind fire_station` (op) and the log
   line `CityWorld buildings: N kinds` are the proof; the doc must say "existing chunks never change" (the
   schematics lesson). **Stuck 8:** Apocalypse worlds — their station is ruined; `decayable: false` sidecar
   equivalent is a kind field (`"decay": false`).
7. Firefighters: a `cityworld:spawn` file targeting `lot: "mypack:fire_station"` — the id is the file name, so
   the doc must state the kind id rule and that `LotInfo.kind` carries it for `/cityinfo`.
8. 1.20.1 users: palette tags under `tags/blocks/`; the kind JSON is identical. One pack, two tag folders.

**Where this plan most likely bends on contact:** the bay geometry (a 16-block lot inset by 1 with a 6-high
ground floor is 14×14: two IV engines side by side is tight — measure the OCP fire truck's collision box
first), and the `getBuilding` roll changing every modern plan (ship the first kinds in a minor release with a
clear CHANGELOG line, as 6.13's roads did).
