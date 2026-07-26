# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here (next task)

**▶▶ NEXT: interiors + furniture + villager job blocks + shop-themed lowrise/midrise (owner, 2026-07).**
The overgrowth arc (below) is done and shipped; the owner's queued next feature is making rooms feel
lived-in. Three parts, in the owner's words: (1) **interiors/furniture** — research real MC
interior-design/furniture build conventions (stairs+signs = chairs, barrel+trapdoor nightstands, item
frames, etc.) and give the room-fitting a small furniture vocabulary; (2) **villager job blocks** so
bundled/spawned villagers claim professions — e.g. composters on the farms, lecterns for librarians;
(3) **theme lowrise/midrise buildings as shops** and drop the matching job block so a room reads as
that trade — map seller = cartography table, fletcher = fletching table, cleric = brewing stand,
armorer = blast furnace, butcher = smoker, mason = stonecutter, etc. (one theme per building/section).
The shop/job-block half is concrete; the "furniture from build guides" half wants a design pass. See
[[cityworld-interiors-decoration-ideas]] in memory. **MODERN-gate additions** (CLASSIC stays 1.8-era).
Placement runs through the decoration seam (`RealBlocks` / room fitting), not terrain gen. A good model
to copy: the `Support/Overgrowth` pass added this session (post-decoration, per-lot, block-state aware).

**The shop *classification layer* is now built (the foundation interiors keys off).** A store no longer
just "is a `StoreBuildingLot`" — it carries a seed-deterministic `ShopType(ShopScale, ShopTrade)`, a
two-axis taxonomy under family→lot: **scale** (`CORNER_SHOP` for rural/residential families vs
`HIGH_STREET` for the commercial cores — decided by `DataContext.shopScale()`, overridden on
`RuralContext`) × **trade** (~14 values: `CARTOGRAPHER`, `FLETCHER`, `BUILDERS_MERCHANT`, `ARMOURER`,
`APOTHECARY`, `BUTCHER`, `NEWSAGENT`, … each carrying its vanilla profession + job-block `Identifier`).
Set in `StoreBuildingLot`'s ctor (rolled from `chunkOdds`, shared across a connected building via
`makeConnected`), exposed as `PlatLot.getShopType()` (null = not a shop). Because it's decided at plan
time it's readable with no block generation: `/cityinfo` and the F3 overlay print a `shop:` line, and a
new **public `me.daddychurchill.CityWorld.api` package** — `CityWorldShops.shopAt(level,pos)` /
`shopsNear(...)`, query-only, no persistence — lets other mods react to shops. Verified by a plan-only
probe: 148 high-street shops in 121×121 chunks around origin, well-spread across trades, `shopAt` in
lockstep with the sweep. **Interiors is now: (a) wire `roomProviderForFloor` / job-block placement off
`lot.getShopType()`, MODERN-gated; (b) add corner-shop *placement* in residential (the `CORNER_SHOP`
scale is ready but no rural lot sets a `ShopType` yet); (c) the furniture-vocabulary design pass.**

**Overgrowth landed + a big schematics/decay polish pass (2026-07, this session).** Nature now reclaims
the built world behind the new `[overgrowth]` settings group (`enabled` / `intensity` / `capVines`; on
+ intensity 2.0 in MODERN by default). `Support.Overgrowth` runs post-decoration per built lot: moss /
leaf-litter / pale-moss carpets and grass/ferns/azalea/petals/dripleaf/mushrooms creep over
sturdy-topped surfaces (canSurvive-checked, none on slabs/fences); stone-brick/cobble/stone weather to
mossy — full blocks *and* their slab/stair/wall shapes; **vine strings hang down outer walls** (an
up-scan finds the roofline, reads the wall across the chunk seam so seam-flush faces aren't skipped,
length a random ¼..full of each wall, optionally capped with a glow-lichen tip); small reclaim trees
break through roads; and **dripstone** reclaims mines *and* basements (anchored only to real
floors/ceilings — never the cistern water — waterlogged if a tip dips in). Intensity multiplies density;
verified 3654→8730 vines/city-platmap 1.0→3.0. Also this session: the whole **schematics playtest
polish** — ocean builds keep the natural sea floor + `Anchor:` legs to the seabed + auto-waterlog;
`KeepAir:` yml (winchester's cellar stays hollow) + dead-air trims; big builds full-scan and a 9×5
cathedral may claim road lots (so it's rare-but-possible); the leftover footprint margin gets biome
surface not a dirt apron; `LegacyBlocks`/`LegacySchematic` gained ~17 block + a full item map (the
cathedral renders in its real materials, chests keep loot); water-edge/shallows builds pulled;
`/cityfind` range widened. And **decay-as-probability completed**: `PlatLot.buildingsDecay()` gives
ordinary buildings the rare-pristine roll schematics already had. All verified by read-back probes; see
the memory notes [[cityworld-overgrowth-done]], [[cityworld-p6-schematics-done]].

**CityWorld generates cities, and they're inhabited.** Terrain, roads with named street signs,
buildings with furnished interiors, parks, roundabouts, farms, civic districts, trees and ground
cover — all verified by reading blocks back out of a real world, deterministically, no exceptions.

**Three jars are stashed in `builds/`** (git-ignored) for comparing progress by hand:
`cityworld-p5-trees-and-streets.jar`, `cityworld-p5-plus-farms-and-civic.jar`, then
`cityworld-p5-mobs-and-loot.jar`. Remember: **new world each time** — existing chunks never
regenerate.

**The cities are inhabited, the chests have things in them, and the sewers run wet** (2026-07).
Villagers with names, animals in the fields, fish in the sea, spawners in the mines and sewers, and
13 loot tables that vanilla rolls on first open. **All confirmed in play, not just by probe** — named
villagers, sewer and barn chests with contents, flowing sewer water. See "Closed: mobs and loot"
below: both were *smaller* than this document predicted, because two of the risks recorded here
rested on unchecked assumptions.

**Two bugs that shipped in the same session are the more valuable record**, both found by the owner
playing rather than by any probe, and both written up above: the **dry sewers** (a verbatim port of a
line whose meaning depended on running in a live world) and the **worldgen deadlock** (a previous
fix's `setLevel` turning a load-bearing null into a hang). Read those two before the next wave.

**Industrial landed too** (2026-07) — factories, warehouses and storage yards, with bunkers under the
factories. Nine of the ladder's ten arms are now live. See "Closed: industrial" below; it was **less
than half** the predicted work, because this document's own estimate had gone stale.

**Outland landed** (2026-07) — `OutlandContext` and its six lots (`GravelMineLot`, `CampgroundLot`,
`WoodframeLot`, `MineEntranceLot`, `GravelworksLot`, `WoodworksLot`) ported and wired into the
ladder's tenth and last arm. A deterministic plan-sweep of 6,561 platmaps confirmed it: the outland
band selects `OutlandContext` (212 platmaps) and all six lot types generate without throwing —
`GravelworksLot` and `WoodworksLot` in bulk, the four singletons (`Campground`, `GravelMine`,
`MineEntrance`, `Woodframe`) at their intended rarity. **Every ladder arm is now live.** It was a
straight mechanical port: all its support (`StructureOnGroundProvider`, `GravelLot`'s hole/pile/
tailings helpers, the `WOODWORKS`/`STONEWORKS` loot) was already in place from earlier waves.

**Nature set-pieces landed** (2026-07) — `NatureContext.populateMap`'s full survey is restored, so
the wild parts of the world now get their landmarks. Eight lot families ported to feed it:
`MountainFlatLot` (parent) with `MountainShackLot`/`MountainTentLot`, the airborne `FlyingSaucerLot`/
`HotairBalloonLot` (over `StructureInAirProvider`, which was already ported), and the three medium
builds `OilPlatformLot`/`OldCastleLot`/`RadioTowerLot`. `BunkerLot` is now planned **as a lot** (not
just via its statics), and `HeightInfo`'s `HeightState` classification finally has consumers: bunkers
buried under midlands/highlands, and one "special" lot at the platmap's highest and lowest inner
points chosen by terrain — oil platforms in deep sea, saucers/balloons overhead, mine entrances in
midlands, radio towers on highlands, a castle on a peak. Added `includeBunkers` to settings, three
`materialProvider` selectors (oil-platform floor/column, castles), and a stubbed 7-arg `destroyWithin`
(since wired — see "Demolition landed" below; castles/decayed radio towers now spawn as ruins). A 25,921-platmap
plan-sweep confirmed all nine set-piece types generate without throwing (rarest: `OldCastleLot`=112,
`FlyingSaucerLot`=134).

**Demolition landed** (2026-07) — `destroyWithin`/`destroyArea` are no longer stubs. The full
`WorldBlocks` demolition machinery (sphere dispersal, debris sprinkle, fire) was already ported;
the gap was purely wiring the generator's three entry points to a `WorldBlocks` bound to the live
decoration level. Done with a **thread-local** `WorldBlocks` on the per-world generator, re-seeded
per chunk from the chunk position — *not* upstream's single shared `decayBlocks`/`Odds`, which would
race and go non-deterministic under the concurrent decoration workers (the same trap `RealBlocks` and
`getConnectionKey` already dodge). `CityWorldChunkGenerator.applyBiomeDecoration` binds it via
`beginDecoration`/`endDecoration` (in a `finally`, so a worker never carries a stale level forward).
Now the ruined styles actually chew holes: castles/radio towers/oil platforms/unfinished buildings
crumble even at default settings (they demolish unconditionally), and `includeDecayedBuildings`/
`includeDecayedRoads` finally bite. **Verified end-to-end**: with `includeDecayedBuildings` forced on,
a 41×41 (1,681-chunk) force-load generated with **0 chunk failures, 90 demolitions on live levels,
zero far-chunk write warnings, zero stacktraces, zero watchdog trips** — the debris radius (≤~10
blocks) stays inside the decorating chunk's writable region, so PORTING.md's top-risk #2 (neighbour
access) doesn't bite here. `includeDecayedBuildings=true` is the owner's "apocalypse server" preset,
so this path matters in play, not just in theory. **Confirmed in play** (2026-07): with the decay
config on (buildings + roads + nature, fires off) the owner walked a fresh world — ruined buildings,
rubbled roads, remote structures and oil rigs all reading right. City 17.

**⚠ Correction (2026-07): top-risk #2 DID bite once schematics decay too, and "≤~10 blocks stays
in-chunk" was the load-bearing assumption that failed.** With `includeSchematics` *and*
`includeDecayedBuildings` both on (a combo the new Customize screen makes one click away), a placed
schematic building being demolished — `ClipboardLot.generateActualBlocks` → `PlatLot.destroyLot` →
`WorldBlocks.destroyWithin` — threw `IllegalStateException: Requested chunk unavailable during world
generation` from `WorldBlocks.disperseLine` → `Block.isEmpty` → `level.getBlockState`, crashing chunk
generation (and then the world's teardown deadlocked, which is what "stuck on Saving world" was).
Schematic footprints are larger and can sit at a chunk edge, so the blast's *read* reached a chunk
outside the `WorldGenRegion`. Fix: `WorldBlocks` now guards every demolition read/drop with
`world.hasChunk(x>>4, z>>4)` and skips what it can't reach — the neighbour decays its own slice, the
same "don't cross the edge" rule `RealBlocks` already relies on. Verified: a 961-chunk force-load with
schematics + decayed buildings + decayed roads all on → **0 failures, 0 unavailable-chunk throws, 0
far-chunk write warnings**. **Found by the owner playing it, not a probe** — the earlier 1,681-chunk
decay test had no schematics on, so it never demolished a `ClipboardLot`. The generalisation holds:
an "it stays in-chunk" assumption is only as good as the widest thing that can be demolished.

**Mines got a full MODERN pass** (2026-07) — a long cosmetic + gameplay arc on the underground, all in
`PlatLot`'s mine methods (the `SupportBlocks`/decoration side) plus `MineEntranceLot`. Landed in order:

- **Density → "rare but big + rambling"**: mine shafts cluster into rare *fields* (a low-frequency
  `mineRegionShape` in `ShapeProvider_Normal.inMineField`, with a mountain bonus), instead of being
  dense everywhere. Mine *entrances* gate on `hasMineShaftBelow` (so they always connect to a real
  shaft) and appear at any land band over a field, common under mountains.
- **Copper-age theme, weathered by depth**: corridors get copper wall torches, cut-copper support
  frames (posts + a full-width copper-grate ceiling vent + a hung chain), and the vanilla oak-fence
  supports recolour to copper bars so nothing wooden clashes. Everything patinas with depth — fresh
  near the surface → exposed → weathered → oxidised in the deepest shafts (`copperWeatherStage`).
- **Deep + worthwhile + risky + abandoned**: `lowestMineSegment` dropped to −48; depth-graded ore
  veins in the walls (`oreForDepth`: coal/iron/copper up top → deepslate gold/redstone/lapis/diamond
  deep → a rare scrap of **ancient debris** at the bottom); gravel fall-in + cave-in rubble that scale
  with depth; glow lichen and mossy-cobble decay thickening downward; cobwebs scaling with depth.
- **Cave-spider nests**: dedicated cave-spider spawners on the corridor centreline in a dense web
  tangle, rare up top and common deep. The spawner is *forced* — `SpawnProvider.setSpawner` got a
  `force` overload that bypasses the `spawnBaddies` roll (~0.048), which was silently skipping the
  spawner ~95% of the time and leaving webs with nothing at their heart.
- **Miners' camp props**: a weighted pool (furnace/blast furnace/smoker/stonecutter/smithing table/
  grindstone/anvil/barrel/crafting table/cauldron/cartography table, three lantern types incl. the new
  copper lantern, chest/decorated pot/campfire/soul campfire/bell, and weighted-up scaffolding +
  ladders) scattered on the ledge *opposite the rail* so it never fouls the track.
- **Vertical lift shafts** at 4-way crossings: a 5×5 cut-copper frame around a hollow 3×3 you can drop
  straight down, a chain cable down the centre, a ladder up one corner, and a copper-grate landing at
  the bottom — carved through the crossing rails so nothing floats.
- **Named entrances**: each mine mouth gets a dark-oak gallows headframe with an `OAK_HANGING_SIGN`
  swinging beneath, procedurally named from a 20×20 prefix/noun table ("Sable's Gorge", "Widow's Lode",
  … "Est. 18xx"). Signs write **both faces** so they read front and back.

**⚠ Two `WeatheringCopperBlocks` gotchas worth remembering.** (1) The 1.21.9 "copper age" *is* in
1.21.11, but `COPPER_CHAIN`/`COPPER_BARS`/`COPPER_LANTERN` are declared as `WeatheringCopperBlocks`
*records* (a 4-stage bundle), **not** `public static final Block` fields — so `gen_material.py`'s
field-name scan can't see them and an early grep wrongly concluded they didn't exist. Reach the stages
via accessors (`Blocks.COPPER_CHAIN.exposed()`); the generator now emits those through a new
`EXTRAS_EXPR` table (plain copper Block fields still go in `EXTRAS`). Proof they exist: read them out
of a save's region NBT (`palette` strings). (2) **The two-sided sign crash** — writing the *back* text
via `SignBlockEntity.setText(text, false)` routes through `markUpdated()`, which dereferences the block
entity's `level`; that level is **null during decoration** (the load-bearing rule this port already
follows for `frontText`), so it NPEs and fails the chunk — and the world's teardown then hangs on
"Saving world". Fix: an access-transformer entry widening `backText` (mirroring the existing
`frontText` one), then a direct field write. **Same NPE-on-null-level / deadlock-on-real-level trap as
`frontText`** — any BE mutation during decoration must be a plain field write, never a setter that
notifies the level.

**What's left is breadth, not architecture.** The most valuable next steps:

1. ~~**P6 schematics polish** (rotation/mirroring, foundation dig)~~ **DONE (2026-07)** — rotation,
   mirroring, foundation dig/carve, centring, water-edge + ocean builds, biome surround, NATURE
   placement, and a big bundled-family cleanup all landed in a playtest pass; see "Schematics — the big
   playtest pass" below. **P7 config is done** (2026-07): per-world settings now come from a datapack
   registry (`cityworld:world_settings`), naming/mob lists included, verified end-to-end — see "P7 —
   Config + commands" below and top risk #4.

### ▶▶ Next up (planned 2026-07, after the P7 session) — read this first

A triage of "what's left" found that **most of it is design-gated on one big decision**, plus one
real gap. In priority order:

1. **⭐ The Modern vs Classic world-style split (owner's call, needs a greenlight).** This is the
   organizing decision. Today's `NORMAL` becomes `CLASSIC` (faithful 1.8 look); a new `MODERN` style
   (full modern MC — tall builds, modern blocks/ores/mobs/trees/ice, some vanilla structures) becomes
   the default. **Most of the loose polish below is really a facet of Modern**, so decide this first.
   Full write-up + the open sub-decisions in "Future ideas → Modern vs Classic". Suggested execution
   order once greenlit: (a) rename `NORMAL`→`CLASSIC` (+ its preset/lang), keeping behaviour identical;
   (b) add a `MODERN` `WorldStyle` cloning Classic, wire the `loadProvider` switches + a `world_preset`;
   (c) move per-style knobs onto it — building height (raise the `buildingMaximumY` cap for Modern
   only), tree style, cover/ice, ore distribution; (d) flip the codec default to Modern (or just make
   it the top preset — that's a sub-decision).

2. ~~**⚠ Ores are not placed (real gap, not polish).**~~ **MODERN done, then CLASSIC done (2026-07).**
   MODERN reuses vanilla's own ore distribution: `CityWorldChunkGenerator.placeUndergroundOres` runs
   just the `UNDERGROUND_ORES` decoration step of each chunk's biome on the non-wild chunks (the wild
   chunks already get it via the full `super.applyBiomeDecoration`). So the stone under
   cities/roads/structures mineralises with the exact vanilla veins (incl. deepslate variants), no
   lakes/springs/trees. Verified: city chunks ~342 ore blocks/chunk vs nature ~336.
   **CLASSIC now ported too:** `OreProvider.sprinkleOres` (+ `sprinkleOre`/`growVein`/`placeOre`/
   `placeBlock`) is a faithful port of upstream's vein algorithm, with two port-specific changes. (a)
   The 1.14 ore tables are Y-values in a 0-based world; the floor is now -64, so **primary placement
   shifts down by `worldMinY`** — a 1:1 remap of the old 0..128 column onto the new -64..64 underground
   that lands the deep ores near the new bedrock. The `mirror` (upper-terrain) half is *not* shifted:
   mountain cores sit in surface coordinates, unchanged by the floor drop. (b) 1.14 had no deepslate,
   so `placeBlock` now replaces **either** stone or deepslate, swapping in the ore's deepslate variant
   (`deepVariant`) below the deepslate line — without which the shifted-down veins wouldn't place at all
   (the stratum there isn't stone). `PlatLot.generateOres` gates MODERN off so the two paths never
   stack. **Verified by a place-and-read-back probe** on a fresh CLASSIC world (44 spawn chunks, 0 gen
   errors): coal ~107/chunk spanning y-7..55 in both variants, iron ~82/chunk, the deep ores all
   deepslate variants near bedrock (diamond -62..-50, redstone avg -53, gold/lapis/emerald deep). The
   probe caught nothing wrong with the maths — but a rebase like this is exactly where an off-by-64
   hides, so it was worth reading the blocks back rather than trusting the shift.

3. ~~**Schematic rotation/mirroring** (P6)~~ **DONE (2026-07).** Buildings now take a random quarter-turn
   (and an optional mirror on axes the `.yml` marks flippable). The "fiddly multi-chunk origin maths"
   turned out to be a solved problem in vanilla: `StructureTemplate.getZeroPositionWithTransform(target,
   mirror, rotation)` returns the placement offset that lands the transformed structure's **minimum
   corner** exactly on `target` (pivot left at `ZERO`) — the same pairing `FossilFeature` uses. So
   `Clipboard.pasteChunk` takes a `Rotation`/`Mirror`, and the reservation swaps the footprint's X/Z
   extents for the 90°/270° turns (`footprintChunkX/Z`, `swapsFootprint`) so a non-square building fits
   its reserved grid — the piece upstream skipped (it reserved `chunkX×chunkZ` regardless and would have
   spilled rotated non-square builds into neighbouring lots). Rotation is chosen **once** in
   `PlatMap.placeSpecificClip` (from the platmap's deterministic `Odds`) so every footprint chunk shares
   it; the roundabout-statue single path picks one too. **Verified with a place-and-read-back probe** (as
   this very line advised): a 2×1-chunk building placed in all four rotations — block count identical
   across all four (1558 — nothing clipped), block-box dims swap for 90°/270° and match for 180°, NW
   corner exactly on target every time → PASS.

### ▶▶ Schematics — the big playtest pass (2026-07), everything below landed

A long owner-driven playtest loop turned the schematic system from "places, mostly" into something
that reads right in the world. All committed + deployed; the drop-in folder `README.txt`
(`SchematicLibrary.README`) documents every `.yml` key. In rough order:

- **Foundation dig / air-carve** (`ClipboardLot.shapeFoundation`, was the last open scope item). A
  converted template omits air and carries no ground, so on any non-flat terrain it floated or got
  speared by a hillside. Now, in the decoration pass before the paste, it clears the build's whole
  vertical span (kills terrain poking in) and backfills a stone foundation down to solid ground.
  Verified: 256-chunk force-load, 0 gen failures, 0 floating columns under 28 building-chunks.
- **Centre in footprint.** The footprint is whole chunks (ceil of the size), so a smaller build hugged
  the NW corner. `buildNwX/buildNwZ` shift it by half the slack — deterministic, so every footprint
  chunk agrees and the slices still tile.
- **Placement terrain rules — three tiers** (all in `PlatMap.placeSpecificClip`, checked after
  `isEmptyLots`): normal → `footprintBuildable` (flat, buildable, at street level — keeps builds off
  mountains *and* water, the fix for the 40-block dirt scars and the buildings-in-the-ocean);
  **water-edge** → `footprintAtWaterline` (flat ground at the shore/shallows); **ocean** →
  `footprintDeepWater` (deep open sea). A 2601-platmap sweep confirmed 0 placements on non-buildable
  ground with NATURE still at ~1595.
- **Water-edge builds** (`Clipboard.waterEdge`, auto-detected: most of the footprint's outer ring is
  water — catches watertemple and moated castles). They may sit at the shore and get water pooled
  around them **at sea level** (63, a block under the land) so it reads flush with the ocean, not a
  raised puddle.
- **Ocean builds** (`Ocean: true` in the `.yml`) — rigs/ships/lighthouses. Place only in deep water,
  ride the surface (`surfaceLevel = seaLevel`), and get **no foundation** — `shapeFoundation` fills the
  below-waterline volume with water so the schematic's own legs/hull hold it up. Schematic-driven
  `OilPlatformLot`. Put them in `Nature/`; they self-segregate from land builds by terrain.
- **Biome-correct surround.** `isValidStrataY` was excluding the build's Y-span strata for the *whole*
  footprint chunk, so the strata pass skipped the leftover corners' grass and left a bare dirt apron.
  Made it footprint-aware (`buildNwX/Z`): clear strata only *under* the building, so the surround keeps
  its natural biome surface (grass/sand).
- **NATURE schematics enabled** (`NatureContext.populateMap` had `populateSchematics` commented out).
  Two traps: (1) `populateMap` runs **twice** for a nature platmap (pre-road survey, then the committed
  post-road pass) — placing in the survey let the road grid stamp over half each build and drop a
  duplicate behind it, so it's gated on `PlatMap.roadsPopulated` to place only after roads; (2)
  uncapped, 100 empty wild lots carpet the wilderness, so it's capped at 2 per platmap.
- **Bundled family cleanup** (`index.txt`). One demo build, `midwich` (a school), was listed in all 8
  families at `OddsOfAppearance: 1.0` and carpeted every platmap; several others sat in daft families.
  Re-homed midwich/winchester(pub)/IMCHospital/G45station/chayats-bank/eaglman to sensible families and
  odds, and deleted 17 orphan `.schematic` files left behind (present in resources but not in
  `index.txt`, so dead weight). Resources now balance: 70 entries = 70 files = 70 ymls, all ≤ their
  family footprint cap.
- **⚠ A real crash surfaced here, unrelated to schematics** — see "the RoadLot/BuildingLot cast" below.

The one open cosmetic follow-up: plant-decorate the (now grass) leftover corners of a non-square
footprint. `GroundLevelY` tuning is per-build and owner-driven (sea 63 vs land 64 means water builds
usually want their waterline layer at 63).

### ⚠ A latent upstream crash: RoadLot cast to BuildingLot (the "Saving world" hang)

**Found by the owner playing it** — a mid-game `ClassCastException` crashing chunk generation, which
then wedged teardown on "Saving world". `BuildingLot.getNeighboringBasementCounts`/`...FloorCounts`
cast every "connected" neighbour to `BuildingLot`. The connected-neighbour filter is *key*-based
(`isConnected` compares `connectedkey`), and the port derives a building's key from `worldSeed +
(chunkX<<32 ^ chunkZ)` while roads use a fixed `worldSeed + 101` — which **collide at chunk (0,101)**
(and parks' `+102` at `(0,102)`). So a road next to a building there slips through the filter and the
cast throws. Rare coordinate collision → intermittent. Upstream has the identical unchecked cast (it
relied on the filter); the port's determinism refactor of the key is what created the specific
collision. Fixed defensively with an `instanceof BuildingLot` guard at the cast (a non-building
neighbour contributes 0 floors/basement) — more robust than upstream.

4. **Smaller, orthogonal, lower-value:** loot tables → native 1.21 datapack format (they already work);
   GameTest/unit coverage (the `gameTestServer` run is already wired in `build.gradle`); furnished-Rooms
   polish; the huge-mushroom all-cap cosmetic gap.

Done in the P7 session and safe to build on: the datapack settings + Customize + export + example,
the two playtest bugfixes (schematic-decay crash, schematic filename spaces), command tab-complete,
and a rewritten README.

### ⚠ The single most important thing to know: CityWorld builds in the DECORATION pass

Not in the chunk generator. Upstream's `ChunkGenerator.generateChunkData` only ever shaped *terrain*;
a separate `BlockPopulator` drew the cities afterwards. `RoadLot.generateActualChunk` is **literally
empty**, commented "moved to other chunk generator" — all 1,600-odd lines of road live in
`generateActualBlocks`, which needs a **live level** rather than a raw chunk.

This was discovered the hard way: the planner reported 1,142 road lots while the world showed zero
road blocks. Planning and drawing are different passes.

So the two passes map onto modern worldgen as:

| upstream | port | writes to |
|---|---|---|
| `generateChunkData` → `platmap.generateChunk` | `CityWorldChunkGenerator.fillFromNoise` | `InitialBlocks` (raw `ChunkAccess`) — terrain only |
| `BlockPopulator` → `platmap.generateBlocks` | `CityWorldChunkGenerator.applyBiomeDecoration` | `RealBlocks` (live `WorldGenLevel`) — **the whole city** |

`applyBiomeDecoration` is the modern `BlockPopulator`: it runs at the decoration stage and hands over
a `WorldGenLevel`, which is exactly what `RealBlocks` was built to take. Not calling `super` there is
also what suppresses vanilla's own decoration. **Neighbour access is the live constraint** (top risk
#2): a `WorldGenRegion` only permits writes near the chunk being decorated, which is why
`RealBlocks` refusing to look past its chunk edge matters more now than it did under Bukkit.

### ⚠ Lesson: a "simplified" stub silently rewired the whole world

`NatureContext.populateMap` was stubbed to a no-op in wave 2 on the reasoning that its job was to
place nature *set-pieces*, which aren't ported. That reasoning missed its real job, and the result
shipped: **it surveys every chunk and hands the unbuildable ones to nature before anything else is
planned.** Without it,

- nothing is ever marked natural, so `PlatMap.getNaturePercent()` reads **0.00 for every platmap**
  and `getContext`'s ladder grades the entire world as downtown highrise — the other nine bands
  become unreachable (a `HouseLot` could not exist);
- mountains and seas are never excluded, so buildings get planned on them and flatten them;
- the only lots left natural are roads that `validateRoads` reclaims — each one a 16×16 column of
  untouched terrain standing in a flattened downtown. That is what "mountains mid-city" and "random
  columns of stone" actually were.

**How it was found:** not by a probe — by the owner playing it. And the first two diagnoses were
wrong. A data race in `allocateContexts` (real, latent, since fixed) looked like an excellent
suspect, and a probe built to reproduce it *appeared* to, until the probe turned out to be varying
the seed per attempt. Rebuilt to hold the seed fixed and compare concurrent planning against a
sequential reference, it reported **0 differences over 6 runs** — clearing the race and forcing the
search back to the actual cause. Correlating each lot's planned style against real terrain heights is
what exposed it: 625 chunks, `NATURE=0`.

Worth generalising: **stubs are behaviour, not absence.** Before stubbing something out, check what
the callers *read back* from having called it — here, `naturalPlats`, three lines away.

### ⚠ Never let a block entity have a real level during decoration (the deadlock)

**Symptom:** world creation wedged at "Preparing spawn area: 27%" forever. **Found by the owner
playing it**, again — and the fix for it was itself the cause of the previous fix.

An earlier pass found that placing a sign during generation NPE'd, because a block entity reached
through a `WorldGenRegion` over a `ProtoChunk` **has no level** — it is built on demand by
`newBlockEntity` and never told where it lives, and `SignBlockEntity.markUpdated` does
`this.level.sendBlockUpdated(…)`. That pass silenced the NPE with `sign.setLevel(server.getLevel())`.
That looked reasonable, shipped, and armed a deadlock:

```
applyBiomeDecoration                       ← on a chunk-generation worker
 └ setSignText → updateText → markUpdated
    └ setChanged()                          ← no longer a no-op: the BE has a level now
       └ Level.blockEntityChanged → getChunkAt
          └ ServerChunkCache.getChunk → CompletableFuture.join()   ← waits forever
```

A generation worker asking the chunk system for a chunk **synchronously, from inside chunk
generation**. The future needs a worker; the worker is blocked on the future. Every worker parked at
0% CPU, the server thread in `managedBlock`, nothing computing. `BlockEntity.setChanged()` is guarded
by `if (this.level != null)` — *the levelless block entity was already correct*, and giving it a level
is what broke it.

**The fix: don't give it one.** No notification is wanted during worldgen — there are no clients to
update, and the block entity is already held by the chunk (`WorldGenRegion.getBlockEntity` calls
`setBlockEntity` on the one it builds), so it saves without being marked. But every public way into a
sign (`updateText`/`setText` → `setFrontText`) ends at `markUpdated`, so the port writes
`SignBlockEntity.frontText` directly via an **access transformer**
(`src/main/resources/META-INF/accesstransformer.cfg`, wired in `build.gradle` — note adding it forces
a one-off re-run of the NeoForm decompile, which takes minutes). Verified: 124 signs, 124 with text,
0 blank; and the owner's exact hung seed (`-8325793622667797117`, pulled from `level.dat`) now
generates in 2.6s.

**The rules this leaves:**
- **A block entity touched during decoration must never hold a real `Level`.** Anything that
  notifies — `setChanged`, `sendBlockUpdated`, neighbour updates — can re-enter the chunk system from
  a worker and deadlock. Loot and spawners are safe precisely because `setLootTable` and
  `setEntityId` only write fields (see "Closed: mobs and loot").
- **An NPE is a symptom, not a diagnosis.** The null level was load-bearing. Silencing a null without
  asking *why it is null* replaced a loud crash with a silent hang — a far worse bug, and one that
  only showed up under someone else's seed.
- **A hang is not slowness.** `jstack` on the running process named the culprit in one shot after two
  wrong theories. For a client: `tasklist.exe` for the `javaw` pid, then the CurseForge runtime's
  `jstack.exe`. Workers at 0% CPU ⇒ deadlock, not work.

### ⚠ A faithful port of a physics-dependent line is still wrong: the dry sewers

**Found by the owner playing it** — sewer water had gaps. Not a transcription error; `RoadLot` is
verbatim upstream. The platform moved underneath it.

CityWorld fills a sewer channel in two halves: **static** water stubs at the four chunk edges
(physics off, commented *"prevent cross-chunk domino effect"*), and four **single source blocks**
inland placed with `setDoPhysics(true)`, expecting the water to *flow* and fill the ~12 blocks
between. Under Bukkit that worked, because a `BlockPopulator` ran on a live, ticking world: physics
fired immediately and the flow was baked into the chunk.

Decoration writes through a `WorldGenRegion` onto a `ProtoChunk`, and **`ProtoChunk.setBlockState`
never calls `onPlace` and never notifies neighbours** — it writes the section, heightmaps and light,
and stops. `WorldGenRegion.setBlock` only consults the update flags for one post-processing check.
So `UPDATE_ALL` is very nearly inert during generation, and `LiquidBlock.onPlace` — *the thing that
schedules a fluid's first tick* — never runs. Placed water is just a block that sits there.

Measured before the fix: **0 pending fluid ticks**, every water block a source, **zero** flowing, and
the channel between the stubs open air. That is exactly what "gaps" looked like.

**Vanilla hits the same wall and works around it explicitly**: `SpringFeature` and `LakeFeature`
follow their `setBlock` with a hand-written `scheduleTick(pos, fluid, …)` *for this very reason*.
So does `compat/Block.setBlockData` now — one seam, so any future fluid works too, and `RoadLot` is
untouched. After: **0 → 292** pending ticks (4 per sewer chunk = the four `setDoPhysics(true)`
calls), and they fire once chunks tick.

The one remaining difference from upstream: water flows **when the chunk ticks**, not during
generation. **Confirmed in play by the owner — the sewers have flowing water**, so the deferral is
invisible as predicted, and the fallback (fill the channel statically in `RoadLot` and stop depending
on flow) is *not* needed. Worth noting the probe could only ever prove the ticks were scheduled
(0 → 292); that flow actually fills the channel was settled by walking into one.

**Two generalisations worth more than the bug:**
- **`setDoPhysics(true)` had exactly one caller in the whole tree** — this water. A seam with one
  user, silently doing nothing. Grep for lone callers of a compat flag; they are where the
  assumptions hide.
- **The dangerous ports are the faithful ones.** A line that reads identically to upstream and
  compiles clean can still be wrong, because upstream's line depended on *when* and *where* it ran.
  Anything relying on ticks, physics, neighbours or a live world is suspect at decoration time.

**Same bug, second site: dry roundabout channels** (2026-07). **Found by the owner playing it.** A
roundabout's underground WATER pit has four half-pipe channels that should run water down into the
pit. Upstream never wets them from *this* chunk — it wets only the pool and leans on the neighbouring
sewer's flowing water spilling through the edge notches. But the neighbour caps its water at its own
edge (the same "prevent cross-chunk domino" static stubs) and cross-chunk flow never fires during
generation — so the channels read dry. **This is the dry-sewers bug at a different lot.** Fix
(`RoundaboutCenterLot.generateActualBlocks`, WATER pit): the roundabout seeds its own water, exactly
like `RoadLot` — static stubs at the four channel mouths (edge columns → physics auto-suppressed) plus
a flowing source one block inland at (8,1)/(8,14)/(1,8)/(14,8), inside a `setDoPhysics(true)` block so
the `compat/Block.setBlockData` seam schedules each fluid's tick. **Placement at 1/14 not 0/15 is
load-bearing**: `SupportBlocks.getDoPhysics` suppresses physics on edge columns (`onEdgeXZ`), so a
source at 0/15 would silently sit static — the same lone-caller trap.

**Follow-up (same playtest): the mouth step, then channel width.** Took three deploys, each fixing what
the previous one's screenshot exposed — a good example of "confirm fluids by walking them, not by
reasoning about coordinates":
- **Deploy 1** flowed the channels but left a 1-block dry gap at each mouth. That step down
  (`yPitPipes+1`→`yPitPipes`) is upstream-intentional: the sewer feeds in at the higher level and the
  channel floor is one lower. Neither source bridged the lip — the mouth stub sits at the top of the
  step but is static (edge column), and the channel source sits at the bottom and only flows *toward
  centre*, never climbing the step.
- **Deploy 2** added a flowing source at the *top* of the step (`yPitPipes+1`, inland) so it cascades
  down the lip. Fixed the gap, but seeded only a single column while the channel is **2 wide** (x7-8 /
  z7-8), so half the width filled by spill-flow and read misaligned.
- **Deploy 3** widened every seeded block to the full 2-wide channel with `setBlocks` (matching the
  mouth stubs, already 2-wide). **Confirmed perfect in play.**

Final shape: each WATER-pit channel seeds three tiers, all 2-wide — static mouth stub, a top-of-step
flowing source, and a channel-floor flowing source feeding the centre pour. Generalises the first
generalisation: **any lot that expected a fluid to arrive from a neighbour is suspect**, not just the
one that placed it — the neighbour's water stops at the shared edge by design.

### ⚠ Lesson: probe the whole world before shipping, not one feature at a time

A null sign line crashed **chunk generation** — `Component.literal(null)` throws where Bukkit's
`setLine` blanked the line, and `OdonymProvider`'s fossil names fill only line 1 of a `String[4]`.
The chunk failed outright, leaving holes wherever a museum tried to label a fossil.

It was latent in the block seam from the start and only became reachable when `MuseumBuildingLot` —
the only fossil sign in the game — landed. **Every probe in that session targeted one feature at a
time, and none of them placed a museum.** It was caught by one last end-to-end pass over the exact
jar about to be handed over, and that is the only reason it didn't ship.

So: a per-feature probe proves a feature. It does not prove a world. Do a whole-world pass over the
artefact you are actually shipping — the interesting bugs live where two features meet.

### Remaining families

The context ladder in `ShapeProvider_Normal.getContext(PlatMap)` is real and **nine of its ten arms
are live** (park, highrise, construction, midrise, lowrise, neighborhood, municipal, farm,
industrial). One remains:

| arm | needs | how it's held back |
|---|---|---|
| outland | `CampgroundLot`, `GravelworksLot`, `WoodworksLot`, `MineEntranceLot`, … | **no setting guards this one** — its band falls through to nature, marked in `getContext` |

### ✔ Closed: industrial (2026-07)

`IndustrialContext` (55), `IndustrialBuildingLot` (38), `FactoryBuildingLot` (703),
`WarehouseBuildingLot` (99), `StorageLot` (121), `BunkerLot` (1037) and `RoadThroughBunkerLot` (73) —
**2,126 lines, copied verbatim**, `includeIndustrialSectors` back on at upstream's default.

**The "90 compiler fixes" estimate was stale and this section told you not to re-derive it — derive it
anyway.** The real number was 200 before the import transform and **40 after**, and they collapsed
into three small buckets, because the things Factory was said to be blocked on had all quietly
arrived with later waves: `InteriorStyle`, `insetWallNS/WE`, `firstFloorHeight`, `RoofStyle`,
`RoofFeature`, `InsetStyle` were *already there*. What was actually missing:

- **Six `MaterialProvider` lists**, not the two named here: `itemsSelectMaterial_FactoryInsides`,
  `_FactoryTanks`, `_BunkerBuildings`, `_BunkerPlatforms`, `_BunkerBilge`, `_BunkerTanks`.
- **Three settings**: `treasuresInBunkers`, `spawnersInBunkers`, `oddsOfTreasureInBunkers`.
- **Two stub methods**: `StructureOnGroundProvider.generateShed` and
  `StructureInAirProvider.generateSaucer`. Both call sites are `void` and read nothing back — checked,
  because of the `NatureContext.populateMap` lesson — so no-op stubs were safe. (`generateShed` has
  since been ported for real; see "Closed: StructureOnGroundProvider".)

**`IndustrialBuildingLot` was missing from the list above** — the abstract parent of Factory and
Warehouse. `BunkerLot`'s role was also mis-stated: Factory needs only its **static** generators
(`generateRecallBunker`, `generateTankBunker`, `generateBallsyBunker`, `generateQuadBunker`,
`generateGrowingBunker`), not `BunkerLot` as a lot. `BunkerLot` as a *lot* is still unplanned —
that's `NatureContext`'s set-pieces, still outstanding.

**The transform is mechanical and worth reusing** (measured on already-ported lots, which differ from
upstream by 0–22 lines): `org.bukkit.Material` → `compat.Material`, `org.bukkit.block.BlockFace` →
`compat.BlockFace`, `org.bukkit.TreeSpecies` → `compat.WoodSpecies` (+ the type refs),
`ChunkGenerator.BiomeGrid` → `compat.BiomeGrid`, `Bisected.Half` →
`properties.Half`, `Stairs.Shape.X` → `StairsShape.X`. That is the whole port for a lot file.

**Verified by probing planning *and* drawing** (they are different passes — that is the 1,142-road-lots
lesson): 2 of 25 platmaps chose `IndustrialContext`; 77 `FactoryBuildingLot`, 26
`WarehouseBuildingLot`, 6 `StorageLot` planned; and in the world, **41 `chests/warehouse` and 13
`chests/bunker`** — both previously unreachable — plus a **blaze spawner**, i.e.
`itemsEntities_Bunker` reached for the first time. 625 chunks, 8s, no exceptions. Those bunker chests
and spawners are factories building bunkers beneath themselves via `generateTreat`/`generateTrick`,
which is exactly what the three new settings gate.

### P5: what's deliberately not done

Trees, ground cover, street names, statues, fossils, **mobs and loot** are ported.

Also stubbed, documented at its site: `StructureInAirProvider` (207 — balloons, blimps, saucers).

### ✔ Closed: StructureOnGroundProvider (2026-07) — and the houses were empty

Ported whole (1158 lines, copied verbatim), plus **nine** `MaterialProvider` lists
(`_HouseWalls/_HouseFloors/_HouseCeilings/_HouseRoofs`, `_ShackWalls/_ShackRoofs`,
`_ShedWalls/_ShedRoofs`, `_WaterTowers`) and one setting (`includeFires`). 26 compiler errors, all of
them those ten symbols. `org.bukkit.DyeColor` → `net.minecraft.world.item.DyeColor` joins the import
transform; no shim was needed because `Support/Colors` already used the vanilla enum.

**This was reordered ahead of outland, and the reason matters more than the port.** The plan had
outland next. Surveying it first showed two things that changed the order:

- **`HouseLot:56` keyed off `generateHouse`, which the stub returned `0` from** — so *every*
  `HouseLot` was a vacant plot. `NeighborhoodContext` is the most common civilized context (7 of 25
  platmaps in a sample) and the planner made 146 `HouseLot`s per 2,500. A large, populated-looking
  fraction of the world was bare ground, and the stub's own doc-comment described this as correct
  behaviour ("lays out its plots and leaves them vacant") rather than as a hole.
- **`CampgroundLot`'s entire body is `generateCampground(…)`**. Porting outland first would have
  shipped campgrounds that compile, plan, draw — and are bare terrain.

**Verified by asking the planner which chunks it made houses on, then measuring those exact chunks**:
27 of 27 sampled `HouseLot`s now carry ~552 blocks above street level; **0 vacant**. The first attempt
at this probe was worthless and worth recording as a method note — it counted "house tells" (doors,
stairs, bookshelves, glass panes) across the whole world, which offices and libraries also place, and
it flagged the *absence* of beds as suspicious when `generateHouse` carries upstream's own
`// TODO add bed`. **A probe that cannot distinguish the feature from its neighbours proves nothing.**

Still stubbed here on purpose, both `void` with nothing read back:
`StructureInAirProvider.generateSaucer` (bunkers have no saucer parked in them).

### ✔ Closed: mobs and loot (2026-07)

Both landed, and both were **smaller than this document estimated** — in each case because a risk
recorded here turned out to rest on an assumption nobody had checked. Worth reading before trusting
the other estimates.

- **Top risk #1 (`generator.getWorld()` does not exist) was never a real problem.** `SpawnProvider`
  was the one caller that supposedly needed a whole `World`, and it doesn't: Bukkit's `Location`
  carried its world, and so does `compat/Location`. Upstream had the level in its hand — `at` — at
  the moment it called `getWorld()`. The port reads `blocks.getBlockLocation(…).getLevel()` and the
  risk evaporates. It is struck from the list below.
- **The `BiomeGrid` problem is real, unfixable here, and doesn't matter.** There is no `setBiome` on
  `LevelAccessor`, `ChunkAccess` or `LevelChunk`; section biome containers are `PalettedContainerRO`
  (read-only) and `BIOMES` settles five chunk-statuses before `FEATURES`. Upstream used it for
  exactly two entries — wolf→`FOREST`, ocelot→`JUNGLE` — as cosmetic tidying before a spawn. Dropped
  and documented in `SpawnProvider`. Directly-placed mobs ignore natural spawn rules, so nothing
  depends on it. If it is ever wanted, it belongs to the `BiomeSource`.
- **The loot tables needed almost no migration.** The estimate here was "number-provider objects,
  `enchant_randomly` options, `pack_format` bump". Checked against the codecs instead of assumed:
  `NumberProviders.CODEC` is `Codec.withAlternative(TYPED_CODEC, UniformGenerator.CODEC)`, so bare
  `{"min":…,"max":…}` still reads as uniform; `enchant_randomly`'s `options` is an
  `optionalFieldOf`; and the top-level `type` is a `lenientOptionalFieldOf`. The 13 files needed
  **no content edits at all** — only the `loot_tables` → `loot_table` directory rename (the
  registry key is `loot_table`), and a `"type": "minecraft:chest"` added for explicitness. All 205
  item ids they name still exist in 1.21.11.
- **A mod jar is a datapack**, so upstream's extract-into-`<world>/datapacks/`-then-`reloadData()`
  machinery is gone; the tables just sit in `data/cityworld/loot_table/chests/`. That retired
  `saveLoots()` (already empty in both upstream implementations) and the `worldPrefix` argument
  (passed everywhere, read by neither — it keyed per-world tables, and the port has no per-world
  anything).
- **Only the loot-table implementation is ported**, not `LootProvider_Normal`. It was upstream's
  default (`useMinecraftLootTables = true`) and the imperative path would need a dozen
  `itemsRandomMaterials_*Chests` lists rebuilt to say what vanilla says better.

Two modern gotchas the code comments carry, worth knowing before touching this again:

- **`EntityType.spawn(...)` is unusable at decoration** — every overload demands a concrete
  `ServerLevel` and would add the mob to the live level, bypassing the region. Vanilla's own worldgen
  never calls it. The idiom (`OceanMonumentPieces`, `SwampHutPiece`, `MineshaftPieces`) is
  `create(level.getLevel(), reason)` → `snapTo` → `finalizeSpawn(region, …)` →
  `region.addFreshEntityWithPassengers(…)`: `getLevel()` only to *construct*, the accessor to place.
- **`WorldGenRegion.addFreshEntity` does not bounds-check like `setBlock` does.** It never consults
  `ensureCanWrite`; it resolves the chunk directly, and one outside the region's cache throws
  `ReportedException` — a server crash, not a declined write. Upstream's `insideXYZ`/`clampXZ` guards
  are load-bearing now, not politeness.
- **Use `EventHooks.finalizeMobSpawn`, not `Mob.finalizeSpawn`** — NeoForge marks the latter
  `@ApiStatus.OverrideOnly`. The hook fires `FinalizeSpawnEvent` so other mods get a say; a cancelled
  spawn needs no handling on our side, as `WorldGenRegion.addFreshEntity` drops marked mobs.
- **Chests need no `setLevel` guard**, unlike signs: `setLootTable` is a plain field write that never
  notifies its level. Spawners likewise — `SpawnerBlockEntity.setEntityId` passes its `@Nullable`
  level straight through and the trailing `setChanged()` guards on it.

**Verified by generating 625 chunks and reading them back** (`PopulationProbe`, since deleted), over
three seeds: 60–88 mobs across 15–20 kinds, 27–42 spawners across 7–8 kinds, 443–485 chests of which
**every single one carried a loot table**, villagers with names ("Hazel Simmons", "Christine
Johnson", "Curtis Nichols"), zero exceptions.

**And separately: all 13 tables resolved and rolled 20× each, every one yielding items** — including
the seven whose lots aren't ported, so they're known-good ahead of time. This was worth doing on its
own: a chest tagged with a table that doesn't parse is exactly as empty as one with no table at all,
and `setLootTable` only stores a key, so counting tagged chests proves nothing about the data. The
first attempt at this probe *itself* crashed on `Missing registry: minecraft:loot_table` — loot
tables are **not** in `level.registryAccess()`, they are a reloadable datapack registry reached via
`server.reloadableRegistries().getLootTable(key)`.

**Confirmed in play by the owner (2026-07): sewer and barn chests have contents, and villagers have
names.** That closes the gap the probe could not: it read chests *in memory during generation*, which
proves the table was attached but says nothing about the save/load round trip a player actually
meets. Barn chests are the nicer confirmation — `FARMWORKS` is a coin-flip inside a coin-flip
(`BarnLot.placeChest`) and never once turned up in a probe sample.

`BUNKER`, `WAREHOUSE` and `itemsEntities_Bunker` came alive with the industrial family — measured in
a world: 13 bunker chests, 41 warehouse chests, and a blaze spawner. `STORAGE_SHED` became reachable
with `StructureOnGroundProvider`. Still unreached: `WOODWORKS(_OUTPUT)` / `STONEWORKS(_OUTPUT)`, whose
outland lots aren't ported. `RANDOM` has no caller outside the Astral styles.

Also still outstanding:
- **`NatureContext.populateMap` still lacks its set-pieces** — upstream seeds bunkers, radio towers,
  oil platforms, flying saucers, hot air balloons and mine entrances by terrain type, and tracks the
  platmap's highest and lowest spots to place two "special" lots. `BunkerLot` alone is 1037 lines.
  Its `HeightState` switch is the only consumer of `HeightInfo`'s classification. **The survey
  itself is ported and is not optional** — see the warning below.
- **`PlatMap.placeSpecificClip`** and the schematic-backed roundabout centre — P6. A built
  roundabout always gets the generated `RoundaboutCenterLot`, never a player's schematic.

### ✔ Closed: the "sea level might be 64" scare was a stale comment (2026-07)

An earlier pass flagged that upstream's commented-out reference line in `initializeWorldInfo` —

> `seabed = 35 deepsea = 50 sea = 64 sidewalk = 65 tree = 110 evergreen = 156 snow = 202 top = 249`

— only reproduces at `seaLevel = 64`, while we pass 63, and worried this undercut terrain parity.
**It doesn't. That comment cannot be evidence about 1.14, because it references `sidewalkLevel`,
which is not a field on `CityWorldGenerator` and has not been one for years** — in the 1.14 source
it is a *local* in `PlatLot.getSidewalkLevel()` (`streetLevel + 1` inside a city). The commented line
would not even compile against the code it sits in. It is a fossil from an older CityWorld with
different formulas, which is also why its `deepsea = 50` matches no version of the maths. Two of its
numbers agreeing with a `seaLevel = 64` reading is a property of that dead version, not of 1.14.

**So 63 is right**, and it is what both 1.14 Bukkit and modern Minecraft use. The lesson is narrower
and worth keeping: *don't infer behaviour from upstream's commented-out code* — check whether it
still type-checks against the tree it lives in first. Terrain parity was independently confirmed by
eye anyway (above).

### Sea level: what the number means to vanilla vs to CityWorld

Related, and a real difference that has to be translated rather than "fixed":

- **CityWorld** fills water *through* its sea level, inclusive (`for (y = subsurfaceY + 1; y <= coverY; y++)`,
  `coverY = seaLevel`). With `seaLevel = 63` the topmost water block is **63** and the surface plane
  is **64.0**. Its beaches sit flush with that waterline (sand at 63, dry) — which is exactly what
  makes them read as beaches, so this is intentional and must not be "corrected".
- **Vanilla** means the opposite by the same word: `Aquifer.FluidStatus.at(y)` gives fluid only where
  `y < fluidLevel`, so sea level is *the first Y that is not water* — the top water block is
  `seaLevel - 1`.

The two conventions differ by one, so `CityWorldChunkGenerator.getSeaLevel()` returns
`UPSTREAM_SEA_LEVEL + 1` (64). Reporting 63 told vanilla our oceans were a block deeper than they
are. Found by standing on a beach and reading F3: player at Y=64, water at 63.

### Verified by reading blocks back out of a generated world (2026-07)

A `ServerStartedEvent` probe (since deleted) generated a real world and read it back:

- **Layout**: `minY=-64 maxY=319`, generator is ours. Bedrock at **-64**, and y=0 is *stone, not
  bedrock* — i.e. the old 1.14 floor is gone.
- **Deepslate blend**: 100% deepslate at y=-8 ramping smoothly to 100% stone at y=0, mixed on all 7
  rows between. Ragged like vanilla, not a flat seam.
- **Real terrain**: surface varies **64..211** across a ±512 grid; sample columns show continuous
  strata from bedrock through deepslate to stone to a grass surface at y=129/65/123, with scattered
  air pockets that are caves (5–29 per column, no long runs — so no void gaps).
- **Seas and beaches run**: ~52 of 289 sampled columns are water-topped, and 8 are sand — so the
  sea/beach/fluid branches of `preGenerateChunk` genuinely execute, not just the mountain branch.
- **`getBaseHeight` agrees with generated terrain on 288/289.** The one outlier is a surface cave,
  which `getBaseColumn` deliberately does not model.

**And confirmed by eye**: the project owner played a generated world and recognised it — "mountains,
flat areas with beaches and lots of caves — looks just like the old CityWorld terrain I remember".
That is the payoff of vendoring Bukkit's noise rather than approximating it with vanilla's, and it is
evidence no probe can produce. It does **not** settle the sea-level question below: a uniform
one-block shift is invisible to the eye.

**Confirmed again once the cities landed** (after the `NatureContext` fix): "bridges, tunnels,
mountains, buildings, roundabouts — it all feels right, like old CityWorld". Worth noting what that
covers that nothing here tested: **bridges and tunnels**. Several hundred lines of `RoadLot` — the
polarity search in `PlatMap.isBridgeTowards` that decides a crossing is worth building, then the
bridge caps, railings and tunnel linings — and no probe ever looked for them. They work.

**A bug this caught:** `getBaseHeight` first returned the *terrain* height, which disagreed with the
world on **63 of 289** columns — every sea column, because `WORLD_SURFACE` counts water as the
surface while `OCEAN_FLOOR` doesn't. Vanilla uses it to place spawn, so it would have dropped players
under the sea. Both it and `getBaseColumn` now model the fluid fill, and `getBaseHeight` is derived
*from* `getBaseColumn` via the heightmap's own predicate, exactly as vanilla does, so the two cannot
drift apart again.

### And earlier, driving the provider directly (seed 12345, `256`/`63`)

- **The datums derive correctly**: `height=256 seaLevel=63 streetLevel=64 landRange=186 seaRange=28`,
  `deepsea=54 tree=109 evergreen=155 snow=201`. `streetLevel = seaLevel + 1` as predicted.
- **Terrain varies and stays in range**: over a 2000×2000 sample, `minY=47 maxY=218` (bounds are
  `3..253`).
- **Deterministic** — required, since the modern pipeline generates chunks on many threads: repeated
  calls agree, and a *fresh generator on the same seed* agrees. A different seed moves **1222 of
  1521** sampled columns, so the seed really is plumbed through (the rest are flat areas clamped to
  sea/street level).
- **The cached-Ys path agrees with the provider** column-for-column, and `TraditionalCachedYs`
  classifies chunk (0,0) as `BUILDING` (it is flat at exactly `streetLevel`).
- **Caves carve**: `notACave` is false for ~4.6% of sampled blocks — so the strata loop's cave branch
  is live, not a no-op.

(The write path — `preGenerateChunk` → `generateStratas` → `chunk.setBlock` — was unexercised at that
point; wiring `fillFromNoise` is what proved it, above.)

### How wave 1 was cut (so wave 2 can be cut the same way)

The measured closure from `ShapeProvider` is **250 files / ~29k lines** — the earlier "~843 lines"
estimate counted only `ShapeProvider` + `_Normal` themselves and missed that their dependencies pull
the whole cycle (it also missed `AbstractYs`, `Point`, and the ~20 contexts). **There is no
terrain-only slice**; what makes it tractable is that the cycle's edges are thin, so wave 1 ported
the terrain spine for real and stubbed the city-planning side:

| ported for real | stubbed (wave 2) |
|---|---|
| `compat/noise/*` (5 classes, vendored verbatim) | `Plats/PlatLot` — only `style`/`blockYs`/`isValidStrataY`/`getChunkBiome`/`generate*` |
| `Plugins/ShapeProvider` + `_Normal` (terrain maths, strata, caves/mines noise) | `Support/PlatMap` — no lot grid |
| `Support/AbstractYs`, `AbstractCachedYs`, `TraditionalCachedYs`, `Point` | `Context/DataContext`, `NatureContext`, `RoadContext` |
| `CityWorldGenerator` (now the real per-world context) | `CityWorldSettings` — only the flags the shaper branches on, at upstream defaults |
| `OreProvider`'s strata palette | `OreProvider`'s ore *placement*; the other 8 `ShapeProvider` variants |

Two deferrals worth knowing about, both documented at their sites:
- **`ShapeProvider_Normal.getContext(PlatMap)`** — the ten-way nature-percent ladder that decides
  whether a platmap becomes downtown or farmland. Thresholds are recorded verbatim in its javadoc;
  it returns `natureContext` until the contexts land. This is *why* wave-1 worlds have terrain but
  no cities.
- **`loadProvider`** — all 10 style arms currently construct `_Normal` rather than failing.

**Verify behaviour, don't just compile.** Every wave so far was proven with a temporary
`ServerStartedEvent` probe (Gradle can't pipe stdin to the server console). It has caught real bugs
the compiler couldn't. Delete the probe before committing.

---

Living checklist for porting CityWorld from a Spigot 1.14 plugin to a modern **NeoForge** mod.

This repo is a fork of the original CityWorld; the NeoForge port is being built **in place at the
repo root**. The original Bukkit 1.14 project was removed from the working tree but remains the
**reference implementation**, recoverable from git history (e.g. `git show HEAD~1:src/me/...`) and
the upstream fork. Loot tables and schematics will be pulled back from history and converted at
Phases 5–6.

## Target

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | NeoForge 21.11.42 |
| Java | 21 |
| Build | Gradle + ModDevGradle (`net.neoforged.moddev`) |

## Decisions locked in

- **Delivery: both** a custom **dimension** (`cityworld:city`, entered via `/cityworld`) *and* a
  **world preset** (whole-world generation at creation). Both sit on one shared `ChunkGenerator`.
- **World layout: modernize now** — full `-64..319` height, deepslate strata, modern cave carvers
  (not a 1:1 copy of the 1.14 `0..255` layout). **Settled 2026-07 — "extend down, keep the shape":**
  the world runs `-64..319`, but **terrain still scales against a 256 ceiling**. These are two
  different numbers, and upstream conflated them in `world.getMaxHeight()`. `landRange` (which sets
  mountain amplitude) is derived from that ceiling, so feeding the shaper 384 would not make the
  world taller — it would make *mountains* half again as tall (peaks ~345, clipping the 319 ceiling)
  and discard the shape the noise vendoring exists to preserve. So modernization goes **downward**:
  64 blocks of new underground, deepslate below y=0, sky/building headroom to 319. Sea level is 63
  in both 1.14 and modern Minecraft, so the surface band already lines up.
- **Package tree preserved**: keep `me.daddychurchill.CityWorld.*` across all ported files to
  minimize churn and keep attribution. Gradle `mod_group_id = me.daddychurchill.cityworld`.

## Why this is a big port (vs. MobHealth)

MobHealth was event-driven (hook damage → draw a bar). CityWorld is a **world generator**, and
Bukkit's imperative `ChunkGenerator.generateChunkData()` + `BlockPopulator` model does not exist in
modern Minecraft. Modern worldgen is a **codec-registered `ChunkGenerator`** running in a **staged,
multithreaded** pipeline with **restricted neighbor access**, using `BlockState` (not Bukkit
`Material`) and a `-64..319` world.

## ⚠ There is no "terrain-only" slice (measured, 2026-07)

The original plan assumed we could port the terrain shaper first and add cities later. **We can't.**
Following *only* explicit imports, the transitive closure is **identical (316 files / ~39,780 lines)**
from every one of these seeds:

- `Plugins/ShapeProvider_Normal` (the terrain shaper)
- `Plugins/ShapeProvider` (the abstract base)
- `Support/PlatMap`

The brain is one mutually-recursive cycle — `ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔ Plugins
↔ Rooms ↔ Clipboard` — so touching any of it pulls in essentially the whole codebase. `ShapeProvider`
also references `RealBlocks` in its method signatures, so the **decoration-side block seam is a hard
prerequisite**, not a P5 concern.

**Revised strategy:** port the brain as a scripted **mass transform in waves** (as was done for
`AbstractBlocks`: rewrite imports/types mechanically, then fix residuals against the compiler),
backed by a **shim layer** for the remaining Bukkit surface. Not incremental feature-by-feature.

### Remaining Bukkit coupling (whole tree, by import count)

| Surface | Uses | Plan |
|---|---:|---|
| `Material` | 150 | ✅ done (`compat/Material`, all 557 constants) |
| `block.BlockFace` | 82 | ✅ done (`compat/BlockFace`) |
| `ChunkGenerator.BiomeGrid` + `block.Biome` | 56 uses, but only **12 files** | ✅ **shimmed** (`compat/Biome`, `compat/BiomeGrid`). The whole tree names only **12 biome constants**, and `BiomeGrid`'s entire used surface is `setBiome(x, z, biome)`. **4 of the 12 no longer exist** — the 1.18 rework deleted every `*_HILLS` variant plus `SNOWY_MOUNTAINS` — so they remap to the nearest survivor (`BIRCH_FOREST`, `TAIGA`, `DESERT`, `SNOWY_SLOPES`); costs colour/mob flavour, not terrain. The real change (CityWorld pushes biomes per column, modern gen pulls via `BiomeSource`) is still outstanding at P3/P4. |
| `util.noise.*` (`NoiseGenerator`, `SimplexNoiseGenerator`, `SimplexOctaveGenerator`) | 25 | ✅ **done** — vendored verbatim into `compat/noise` (GPL-3 permits it; see licence section), preserving CityWorld's exact terrain shape. 5 classes: the 3 above plus `PerlinNoiseGenerator` and `OctaveGenerator` (their base classes). Only change: the `org.bukkit.World` convenience ctors are dropped. `NoiseGenerator.floor` is just `Mth.floor`, but it comes along with the vendored base anyway. |
| `block.data.*` (`Bisected.Half`, `Slab.Type`, `Stairs`, `Rail.Shape`, `Bed`, `Door`, `Leaves`, `Snow`, `Chest`, …) | ~45 | ✅ done — **no shims needed**: each maps onto a vanilla property enum (`Half`, `SlabType`, `StairsShape`, `RailShape`, `BedPart`, `DoorHingeSide`, `ChestType`, …), and the `instanceof` chains became `hasProperty` guards in `SupportBlocks`. |
| `World`, `Chunk`, `Location`, `Bukkit`, `Environment` | ~30 | Decoration-side → `WorldGenLevel`/`ServerLevel`. |
| `entity.*` (`EntityType`, `Entity`, `Player`, `Item`) | ~15 | → modern `EntityType` (P5). |
| `configuration.*` | ~10 | → `ModConfigSpec` (P7). |
| `command.*`, `plugin.*`, `event.*` | ~15 | → Brigadier / drop plugin lifecycle (P7). |
| misc (`DyeColor`, `Axis`, `NamespacedKey`, `TreeType`, `ItemStack`, `Inventory`, `Sign`, `CreatureSpawner`, `MushroomBlockTexture`) | ~12 | Small shims, as encountered. `DyeColor` and `Axis` need none (vanilla has both); `Sign` and `Location` are done. |

## The key seam (why it's tractable)

The original author already funneled all block writing through one family:
`AbstractBlocks → InitialBlocks/RealBlocks/SupportBlocks`. Reimplementing that single layer against
modern `ChunkAccess`/`BlockState` isolates most Bukkit coupling from the ~300 algorithm files.

Coupling inventory (from the 1.14 source):
- `org.bukkit.Material` used in **150 files**, **557** `Material.X` references → needs a
  name → `BlockState` mapping table.
- `org.bukkit` touched in **199 files** (blocks, `BlockFace`, biomes, entities).
- WorldEdit coupling in only **3 files** (`PasteProvider`/`Clipboard`).

## Phases

- [x] **P0 — Scaffold.** ModDevGradle project at repo root, `@Mod` entrypoint (`CityWorldMod`),
      builds empty (`cityworld-<version>.jar`), `runClient`/`runServer` available. **Done.**
- [x] **P1 — Block seam. Done** (bar the mass import rewrite, which happens per-file as the brain is
      ported). `AbstractBlocks`/`InitialBlocks` (generation side, on `ChunkAccess`) and
      `SupportBlocks`/`RealBlocks`/`RelativeBlocks`/`WorldBlocks` (decoration side, on
      `LevelAccessor`) all reimplemented on `BlockState`; `BlockFace` → `Direction`; oriented
      placement (stairs, doors, facing, waterlogging) verified in a live world. **This unblocks
      `ShapeProvider`, and with it the whole generation brain.**
    - [x] Compat foundation compiling: `compat/BlockFace` (enum mirroring Bukkit values +
          `toDirection`/`getOppositeFace`) and `compat/Material` (interned `BlockState` wrapper +
          orientation helpers `withFacing`/`withFaces`/`asSlab`/`asDoorHalf` + id resolver +
          representative constant set). Strategy: `Material` is a value-vocabulary (never
          switched-on across the source), so it becomes interned wrappers, not an enum.
    - [x] Port `Support/Odds` (Material/BlockFace → shims; Vector → `Vec3`; TreeSpecies →
          `compat/WoodSpecies`).
    - [x] **Generation-side seam** compiling: `AbstractBlocks` (768 lines, surgically transformed —
          all `final` convenience methods intact), `InitialBlocks` (on `ChunkAccess`/`ProtoChunk`,
          world-coord mapping + heightmap-auto-update), `Factories/MaterialFactory`, and a minimal
          `CityWorldGenerator` skeleton exposing only what the block layer needs (grows in P3).
    - [x] **Decoration-side seam — done.** Was a **hard prerequisite for `ShapeProvider`** (its
          signatures take `RealBlocks`), not a P5 concern as first assumed. Binds to `LevelAccessor`
          (live world) rather than `ChunkAccess`.
        - [x] `compat/Block` — shim for Bukkit's live positioned block reference, pairing a
              `LevelAccessor` + `BlockPos`. This is the single primitive the whole decoration layer
              rests on (`SupportBlocks.getActualBlock(x,y,z)` is its *only* abstract method).
              Key mappings: Bukkit `BlockData` ≡ modern `BlockState`; "apply physics" ≡ update
              flags (`UPDATE_ALL` vs `UPDATE_CLIENTS`).
        - [x] **`SupportBlocks` (971) — done, and verified in a live world.** All 77 original
              methods ported. The Bukkit `instanceof` chains (`Directional`, `Bisected`, `Levelled`,
              `Ageable`, `Snow`, `Rail`, `Chest`, …) became `hasProperty` guards — that is what those
              interfaces were really testing — via one helper, `with(state, property, value)`, which
              leaves the state alone when the property is absent, exactly as the old `instanceof`
              fell through. The read-modify-write idiom (`setType` → `getBlockData` → mutate →
              `setBlockData`) collapsed into deriving the state first and writing **once**.
              Notes worth keeping:
            - `withScaledLevel` looks its property up **by name** (`age`/`level`/`layers`), because
              there is no single `AGE` property to reference — vanilla declares `AGE_1 … AGE_25`, and
              `LEVEL` alongside `LEVEL_CAULDRON`, each with its own range.
            - Signs: a block entity reached through a `WorldGenRegion` over a `ProtoChunk` **has no
              level**, and `SignBlockEntity.updateText` → `markUpdated` dereferences it. `setSignText`
              hands it one first; without that, placing a sign during generation NPEs.
            - `setCauldron` no longer fills the cauldron — the 1.13 flattening split levelled
              `CAULDRON` into `cauldron` + `water_cauldron`, and only the latter has a level. Left
              for P5, when the callers land.
            - Upstream's `setDoorBlock`/`setBedBlock` took a `doPhysics` flag **they never read**
              (both ended `setBlockData(data, getDoPhysics(x, z))`), so the `true` at those call
              sites never did anything. Parameter dropped; behaviour unchanged.
            - `Material.hasFaces()` (Bukkit's `MultipleFacing`) **excludes walls**: 1.16 moved them
              off boolean faces onto a `WallSide` enum, so they take the bulk-placement branch of
              `setWalls`/`fillBlocks`. Revisit at P5 if wall connections look wrong.
        - [x] Supporting work that fell out of it: `compat/Location`; `Material.isOccluding()`
              (→ `BlockState.canOcclude()`), `Material.hasFaces()`; ported `Support/Colors` (491,
              a pure leaf — vanilla has its own `DyeColor`, so it was a two-import swap); stubbed
              `Context/DataContext` (`torchMat`), `Plugins/LootProvider`/`LootLocation` + `Provider`;
              added `CityWorldGenerator.reportFormatted`. `NoiseGenerator.floor` → `Mth.floor`.
        - [x] **`RealBlocks` (41), `RelativeBlocks` (34), `WorldBlocks` (202) — done.** Each exists
              to define `getActualBlock`; all three verified in a live world (origin arithmetic incl.
              negative chunk coords, edge-crossing, writes landing at the right position).
              The original took `world` from `generator.getWorld()`; the port passes a
              `LevelAccessor` in at construction instead, because a modern `ChunkGenerator` is shared
              and immutable and cannot hold a per-world reference (top risk #1). So `RealBlocks` now
              takes `(generator, LevelAccessor, ChunkPos)` in place of a Bukkit `Chunk`, and
              `WorldBlocks` takes the level too; `RelativeBlocks` borrows both origin and level from
              the section it is relative to, so its signature is unchanged.
              The `RealBlocks`/`RelativeBlocks` split matters more now than it did under Bukkit:
              `RealBlocks` refuses to look past the chunk edge (returns false), `RelativeBlocks`
              crosses freely — and the modern pipeline restricts neighbour access during generation.
        - [x] **`CornerBlocks` (1222) — done**, two import swaps. Worth correcting the record: it is
              **not** a `SupportBlocks` subclass, it is a standalone corner-style table that takes an
              `AbstractBlocks` as a parameter and only calls `setBlock`/`setBlocks`/`setDoor` on it.
              A pure leaf.
    - [ ] Mass import rewrite across ported files: `org.bukkit.Material` →
          `me.daddychurchill.CityWorld.compat.Material`, `org.bukkit.block.BlockFace` → `compat.BlockFace`
          (done per-file as each is ported).
- [x] **P2 — Material mapping. Done.** All **557** referenced `Material.X` constants are mapped and
      compiling, generated by `scripts/gen_material.py` (self-bootstrapping: pulls the Bukkit source
      from git history and the `Blocks`/`Items` field lists from the NeoForm sources jar, so it
      re-runs from a clean machine). Breakdown:
    - **427** map 1:1 onto a modern `Blocks.X`.
    - **14** legacy/renamed hand-mapped in the generator's `LEGACY` table (e.g. `GRASS` →
      `SHORT_GRASS`, `GRASS_PATH` → `DIRT_PATH`, `QUARTZ_ORE` → `NETHER_QUARTZ_ORE`,
      `ENCHANTMENT_TABLE` → `ENCHANTING_TABLE`; most survive only in commented-out code).
    - **116** are **item-only** (loot/chest contents) — Bukkit's `Material` spanned blocks *and*
      items, so `Material` now carries an optional `Item` and `isBlock()`/`getBlockState()` return
      null for these. Proper item handling lands with loot in P5.
    - The constant block is **generated — do not hand-edit**; change the generator and re-run.
    - Note: modern blocks the 1.14 vocabulary never knew (deepslate, tuff, deepslate ores) are
      deliberately absent; P4 adds them as it needs them (or use `Material.of("deepslate")`).
- [x] **P1.5 — The brain, wave 1: the terrain spine. Done, runtime-verified.**
      `Plugins/ShapeProvider` + `ShapeProvider_Normal` ported and producing real, deterministic
      CityWorld heights; Bukkit's noise stack vendored verbatim into `compat/noise`; `compat/Biome`
      + `BiomeGrid` shimmed; the `Ys` family (`AbstractYs`, `AbstractCachedYs`,
      `TraditionalCachedYs`, `Point`) ported; `CityWorldGenerator` grown from a skeleton into the
      real per-world context. The city-planning side (`PlatMap`, `PlatLot`, the contexts) is stubbed
      at thin edges — see "How wave 1 was cut" above. **Terrain heights are real; nothing writes
      them into a chunk yet.**
- [ ] **P3 — Custom `ChunkGenerator` + registration (vertical slice).** Codec-registered
      `CityWorldChunkGenerator` driving `PlatMap` via `fillFromNoise`; `buildSurface`,
      `getBaseHeight`/`getBaseColumn`; custom `BiomeSource`. Register the dimension (datapack JSON)
      and world preset. **Gate: teleport into `cityworld:city` and see terrain.**
    - [x] **Infrastructure spike PROVEN.** `CityWorldChunkGenerator` (codec-registered under
          `cityworld:city` via `DeferredRegister` on `Registries.CHUNK_GENERATOR`) implements the
          full 11-method `ChunkGenerator` contract and writes a placeholder bedrock/stone/grass
          profile through the real `InitialBlocks`→`Material` seam. Datapacks: `dimension/city.json`,
          `worldgen/world_preset/city.json`, and a `#minecraft:normal` world-preset tag (dropdown).
          A headless `runServer` (level-type `cityworld:city`) confirmed the overworld generator is
          ours and blocks are exactly the placeholder profile (stone at y=0 where vanilla is
          deepslate) — no exceptions. Verified via a temporary `ServerStartedEvent` diagnostic
          (since removed). **This validates the whole Phase 1 seam plugs into modern worldgen.**
    - [ ] **Replace the placeholder fill with the real terrain shaper — this is the next task; see
          "Resume here".** No longer blocked: P2 and wave 1 (`ShapeProvider_Normal`, and enough of
          `PlatLot`/`PlatMap`) are done, and `CityWorldChunkGenerator` already builds the per-world
          context lazily and thread-safely. Needs a `BiomeGrid` implementation and a decision on the
          Y offset. Contexts/cities are *not* needed for the terrain gate — they are wave 2.
    - [x] Add the `/cityworld` teleport into the `cityworld:city` dimension. (Done — see P7 below.)
- [ ] **P4 — Height modernization.** Mostly done (2026-07); see "extend down, keep the shape" above.
    - [x] **`ShapeProvider` Y math for `-64..319`.** The key insight is that upstream's single
          `height` meant two things — the world's ceiling *and* the ceiling terrain scales against —
          and they now differ. `CityWorldGenerator` splits them: `getTerrainCeiling()` (256, feeds
          `landRange`) vs `worldMinY`/`worldMaxY` (-64/319, the real bounds).
          `ShapeProvider.bottomOfWorld` was a hardcoded `0` and now reads `generator.worldMinY`, so
          the strata reach the real floor instead of opening onto a void.
    - [x] **Deepslate strata.** `OreProvider.stratumMaterialAt` blends stone→deepslate across
          `y = 0..-8` like vanilla, ragged rather than a flat seam, off seed-derived noise (so it
          stays deterministic under the multithreaded pipeline). It only substitutes for *this*
          provider's stone, so the Nether/End/Astral strata are untouched when those land.
    - [x] **Y constants that were secretly floor-relative.** `OreProvider.lavaFieldLevel` was an
          absolute `12` that only meant "12 above bedrock" because bedrock was at 0; at -64 it would
          have flooded 76 blocks. Now `lavaFieldDepth` + `worldMinY`. `AbstractBlocks.insideY`/
          `clampY` were `0..height` and would have rejected/mis-clamped the new underground — fixed
          preventively (nothing calls them until wave 2's lots do).
    - [ ] **⚠ Ore veins are not placed at all yet — `OreProvider.sprinkleOres` is an empty stub**
          (found 2026-07). So generated worlds have the stone/deepslate strata but **no coal/iron/gold/
          diamond/… to mine** (vanilla ore features are suppressed, and CityWorld's own placement is a
          no-op). This is a real playability gap, not just polish. The port: upstream's `sprinkleOre`
          vein algorithm + the `ore_types`/`ore_minY`/`ore_maxY`/`ore_iterations`/… tables (~80 lines).
          **The catch is a parity decision, so surface it before doing it:** upstream's ore depths are
          **1.14-calibrated** (`minY`/`maxY` like 2/16/128 against a 0..255 world with bedrock at 0).
          In the modern `-64..319` world those must be re-based (the same floor-relative trap
          `lavaFieldLevel` hit in P4). And *what* distribution to use is itself a **Modern/Classic**
          choice — Classic wants the literal 1.14 depths/rarities, Modern wants the modern spread
          (diamonds deep in deepslate, etc.). So do this as a per-style facet, and add the deepslate
          **ore** variants (`deepslate_coal_ore`, … via `EXTRAS` in `gen_material.py`) as part of it,
          picking the variant by Y in `sprinkleOre`. Verify with a block-read probe (count veins by
          type and Y-band). See the Modern/Classic parking-lot entry.
    - [ ] `SurfaceProvider`; modern carvers; revisit `DataContext.buildingMaximumY` (still capped at
          the 256 terrain ceiling, though the world now allows 319).
- [ ] **P5 — Decoration (old `BlockPopulator`).** Loot chests, spawners, furnished `Rooms`,
      neighbor-aware roads/parks → feature placement / post-gen, respecting neighbor limits (the
      seed-deterministic `PlatMap` makes per-chunk regeneration viable). Migrate datapack loot
      tables to 1.21 format and bundle them in mod resources (drop the runtime extraction).
      `SpawnProvider` → modern `EntityType`.
- [~] **P6 — Schematics.** *Largely done: conversion, library, paste command, worldgen placement,
      multi-format loading, drop-in folder, data-fixing and block entities all landed and verified.
      Remaining: schematic rotation/mirroring (FlipableX/Z parsed but not applied).* Formats read:
      legacy `.schematic`, WorldEdit `.schem` (Sponge v2/v3), Litematica `.litematic`, and vanilla
      `.nbt` — each converted to a `StructureTemplate` and run through the vanilla structure data-fixer
      (`Templates.build`) so older files are upgraded rather than losing renamed blocks to air (a 2017
      DataVersion-1343 `.nbt` recovered 585→1273 non-air). Block entities (chests/signs/pots) are
      carried for every format. Players drop their own into `config/cityworld/schematics/<Family>/`
      (external scan alongside the bundled set; created with a README on first run). `/cityfind <name>`
      locates the nearest; `/cityinfo` names the one underfoot.
      The seam: `LegacySchematic` reads a legacy MCEdit `.schematic` (numeric ids +
      `Data`, `Width/Height/Length`) and converts it to a native `StructureTemplate` (the vanilla
      `.nbt` representation) via `LegacyBlocks` (legacy id+data → modern `BlockState`). `LegacyBlocks`
      now covers **every one of the 91 legacy ids** the classic catalog uses — verified: **all 86
      bundled schematics convert with zero unmapped ids and zero failures** (5.69M blocks). `Clipboard`
      wraps the template + parsed `.yml` metadata and pastes in one native call; `SchematicLibrary`
      indexes/loads/caches all 86 across 11 families (via a shipped `index.txt`, since jars can't list
      resource dirs). `/cityschem <name>` (op) pastes any classic at the player; `/cityschem list`
      enumerates them. **Decisions:** modern target = vanilla `.nbt` (native, no deps); WorldEdit
      `.schem` deferred; legacy→`.nbt` is one reusable conversion.
      **Bugfix (2026-07): bundled schematics with a space in the filename never loaded from the jar.**
      `Class.getResourceAsStream` builds an internal `jar:` URI, and a space is an illegal URI char
      (`URISyntaxException`), so the 3 copies of `IMC eaglman13 home entry.schematic` failed (caught →
      WARN → skipped). Only *bundled* resources hit it — external drop-ins load via a filesystem `Path`
      and were always fine. Fixed by renaming the 6 offending bundled files to underscores and updating
      `index.txt`; the surviving-with-spaces catalog is otherwise unaffected. Surfaced once
      `includeSchematics` became a one-click Customize toggle.

      **Worldgen auto-placement — WIRED (behind `includeSchematics`, default off; awaiting in-world
      visual check).** The full path is live: `DataContext.populateSchematics` (gated on the setting)
      → `getSchematics` builds a `ClipboardList` from `SchematicLibrary.family(...)`, footprint-filtered
      to the context's `schematicMax` → `ClipboardList.populate` rolls each clip's `oddsOfAppearance`
      on the platmap's own `Odds` → `PlatMap.placeSpecificClip` finds a run of empty `platLots` big
      enough (`isEmptyLots`, ≤16 tries) and fills it with one `ClipboardLot` per footprint chunk, each
      carrying its `(lotX, lotZ)` offset. `ClipboardLot.generateActualBlocks` reconstructs the whole
      building's NW origin (`chunk.getOriginX() − lotX*16`) and calls `Clipboard.pasteChunk`, which is
      `StructureTemplate.placeInWorld` with `StructurePlaceSettings.setBoundingBox(thisChunk)` — the box
      clips the write to the chunk being decorated (verified in the 1.21.11 source: `placeInWorld` skips
      any block whose pos is outside the box), so a multi-chunk building is placed legally within the
      `WorldGenRegion` radius as each overlapping chunk decorates its own slice. The seams line up
      because every chunk computes the same origin. Height = `streetLevel − groundLevelY`; decay reuses
      `destroyLot` when `includeDecayedBuildings`. `RealBlocks.getServerLevel()` hands `placeInWorld` the
      live `ServerLevelAccessor`.

      *Verified headlessly (plan-sweep probe, 1089 platmaps):* the gate works (0 `ClipboardLot`s with
      the setting off), and with it on **2,035 building cells** were planned across every urban/farm
      family (Highrise/Midrise/Lowrise/Industrial/Municipal/Neighborhood/Construction/Park/Farm) with
      no exceptions — the whole planning path is sound and deterministic. Block-writing itself
      (`placeInWorld`) was already proven by `/cityschem` (gas_stop matched block counts exactly), and
      `pasteChunk` only adds the source-verified bounding-box clip.

      **What still needs the owner's eyes in-world** (turn on `[schematics] includeSchematics` in the
      config, fresh world): buildings flush with streets (not floating/buried/half-clobbering roads),
      and the deliberate first-cut simplifications below.

      *First-cut simplifications (parity-first; refine after the visual check):*
      • No rotation yet — all buildings face the same way (`Rotation.NONE`); random facing swaps the
        footprint for 90/270° and complicates the origin maths, so deferred.
      • No foundation dig / air-carve — the converted template omits air, so a building sits cleanly on
        flat city ground but won't hollow a hillside or basement pocket (`groundLevelY > 0` schematics
        will want the upstream backfill in `generateActualChunk`, currently a no-op).

      *Confirmed in play (2026-07, owner):* schematics drop in, frequency reads right. Fixes that
      followed the feedback (verified via a live place-and-read-back probe on the Winchester pub):
      **sign text now carried** (legacy `Sign` `Text1..4` → modern `front_text.messages`; "The /
      Winchester / Tavern" reads back exactly), **double doors fixed** (both halves decoded together so
      the hinge — which lives on the upper block — is set; place-back shows both LEFT and RIGHT hinges),
      and **roundabout player-statues wired** (the `ROUNDABOUT` family — `sablednah`, `richard`, … — via
      `RoadContext.createRoundaboutStatueLot` → `getSingleSchematic`; sweep found 118 placed). New op
      commands: `/cityfind <name>` (async nearest-match search) and `/cityinfo` now names the schematic
      you're standing on.

      *Container contents now carried too* (2026-07): legacy `Chest`/`Furnace`/`Trap`(=old dispenser)
      tile-entity `Items` → a modern `Items` list via a tiny legacy item-id map (only six ids appear in
      the catalog: gold ingot/nugget, emerald, potion, golden helmet, slimeball). Only three buildings
      ship stock — **chayats-bank** (a vault, 108 gold stacks), **IMCHospital**, **winchester** (one
      emerald) — verified by place-and-read-back. Unknown item ids are logged once and skipped, never
      guessed. Decay currently leaves a stocked vault intact except where `destroyLot` happens to blow
      through it; thinning contents under `includeDecayedBuildings` is a possible later refinement.

      Also still to do: build-time (or cached-on-disk) `.schematic`→`.nbt` so the legacy parser isn't a
      runtime cost; refine orientation (door facing mapping and stairs facing may need a rotation tweak
      once eyeballed); building rotation and the foundation dig above.
      Assets recovered: `../Schematics for zarp.zip` (Era-3 backup, ~297 KB, 186 files) holds the
      building schematics grouped by style (`Lowrise/`, `Industrial/`, `Municipal/`, …). Each
      `.schematic` has a `.schematic.yml` sidecar with CityWorld placement metadata — port both.
      Format is the **old flat-array MCEdit `.schematic`** (2013, MC ~1.5): numeric block IDs in
      `Blocks`/`Data` byte arrays — not WorldEdit `.schem`, not vanilla `.nbt`. Conversion needs a
      numeric-ID → modern `BlockState` mapping pass on top of the NBT reshaping.
- [x] **P7 — Config + commands. Config done (2026-07): per-world settings via a datapack registry.**
      Commands: `/cityinfo` (anyone; reports context/lot/nature under the player — the modern
      Brigadier port of Sablednah's upstream PR #4) and `/cityworld` + `/cityworld leave` (op;
      teleport into/out of the `cityworld:city` dimension, landing on the surface at the player's
      X/Z) are **done** — `CityWorldCommands`, registered via `CityWorldServerEvents` on
      `RegisterCommandsEvent`. Command gating uses Brigadier permission levels (op for teleport),
      matching the reference port's command pattern. `/cityexport [name]` (op) bottles a world's
      effective settings into a ready datapack (see "Trial → export → ship" below). `/citychunk`
      deliberately **not** ported: its `regen` relied on Bukkit's `World.regenerateChunk`, which
      modern MC has no safe runtime equivalent for. Still open: a proper `NeoForge` permission-node
      layer if per-node control is wanted beyond op levels.

  - **The config problem and how it was solved.** CityWorld's ~100 settings were *per-world* (parsed
    from each world's YAML); a NeoForge `ModConfigSpec` is *per-instance* (top risk #4), so it cannot
    say "this world crazy, that world plain". **A datapack registry can, and that's what the port now
    uses.** `CityWorldSettingsData` (a codec'd record, `worldgen/`) is registered as the datapack
    registry `cityworld:world_settings` (via `DataPackRegistryEvent.NewRegistry`), so entries live at
    `data/<ns>/cityworld/world_settings/<name>.json`. The generator codec carries an optional
    `RegistryFixedCodec` holder field `settings` — **resolved at codec-decode time, which is the one
    place registry access is clean** (`fillFromNoise` never gets a `registryAccess()`). The bundled
    `cityworld:default` (spelled-out defaults, a copy-and-edit template) is referenced by
    `cityworld:city` and every world preset; a server op ships a datapack overriding `default.json`
    per save, or points a dimension at its own profile. `CityWorldSettings.applyData` copies the
    resolved data onto its fields *before* the world-style validation and the `decayed` override, so
    those still win (a style's "THIS MUST BE SET" invariants and the ruined-twin are unchanged).
    **This retired `CityWorldConfig` (the old per-instance `[decay]`/`[schematics]` `ModConfigSpec`) —
    the datapack is now the single source of truth.** Every field is `optionalFieldOf(default)`, so a
    JSON lists only what it overrides and a bare `{}` is a full-default world; the knobs are grouped
    (features / terrain / spawns / treasures / world / radius) to stay under RecordCodecBuilder's
    16-field ceiling.

  - **Villager / street names and mob lists fold in here too** (the parking-lot "let players write
    their own names" item). `CityWorldSettingsData` carries a `naming` group (nine word lists) and a
    `mobs` group (ten weighted entity-id bags). **Each list defaults to empty, meaning "keep the
    compiled hundreds"** — exactly upstream's "take the configured list, else the hardcoded one"
    fallback, so they stay out of `default.json` to keep it readable. `OdonymProvider_Normal` reads
    the nine (via its `CityWorldSettings`); `SpawnProvider` reads the ten through a new
    `AbstractEntityList.applyOverride` (the override list *is* the weighted bag — repetition is
    weight). Mob ids resolve via a new `EntityType.of(String)`; an unknown id is **logged once and
    skipped, never guessed** — the `tag*`/`getListName` seams the earlier waves kept are what made
    this a plug-in, not a redesign.

  - **Verified end-to-end** (temporary `ServerStartedEvent` probes, since deleted — the usual method;
    Gradle can't pipe stdin). Two runs: default, then the same world with a world datapack overriding
    `cityworld:default`. The value knobs flipped as written (`includeRoundabouts` true→false,
    `includeDecayedBuildings` false→true, `spawnBaddies` 0.0476→0.99, `oddsOfTreasureInMines`
    0.5→0.123) while *unspecified* fields kept their defaults — field- and group-level merge both
    work. Names came back `"Zorp Xyzzy"` / `"East New Quuxglorp Boulevard"` (a `streetPrefixes: []`
    correctly fell through to the compiled default), the sewer bag became `minecraft:allay` only
    (which isn't one of the 48 hardcoded constants — so `EntityType.of(String)` reaches the whole
    registry), and a bogus `minecraft:not_a_real_mob` was logged-and-skipped. Zero exceptions; the
    override is picked up on world *load* (the holder re-resolves each load), so no regen is needed to
    retune spawn odds / names / decay — only terrain-shaping knobs want a fresh world.

  - **Per-dimension decay override** (the "ruined twin"). The generator codec also carries an optional
    `decayed` boolean; when present it forces `includeDecayedBuildings`/`includeDecayedRoads` on/off
    for that dimension, winning over the datapack settings (absent = follow settings). The
    `cityworld:city` dimension ships with `decayed: true`, so — because both dimensions seed off the
    same world seed — `/cityworld` visits the *same city in ruins* while the overworld follows the
    settings. Scoped to buildings/roads, not `includeDecayedNature` (that drains the seas / deserts
    the world — a whole-world mood, not "this city is wrecked"), so the twin stays wet and green.
    Backward compatible: existing worlds lack the field → `Optional.empty()` → settings.

  - **Trial → export → ship, and a documented example** (2026-07, follow-up). Three additions turn the
    datapack layer into a usable workflow for server ops ("trial in single-player, then set the
    worlds"):
    - **The single-player Customize screen now edits every value knob**, not just the style —
      `CityWorldCustomizeScreen` became an `OptionsSubScreen` with scrollable, headed sections
      (Features / Terrain / Spawns / Treasures / World), booleans as on/off cycles, odds as a named
      `Chance` ladder, enums as cycles. On Done it bakes the edited settings **inline** into the
      generator. That needed the codec to move from `RegistryFixedCodec` (reference-only) to
      `RegistryFileCodec` (reference *or* inline) — so `"settings"` in a dimension JSON is now either
      `"cityworld:default"` or a full `{…}` object; both verified to decode, existing reference worlds
      unaffected. The screen carries the radius/naming/mob groups through untouched (those stay
      datapack-only — impractical as GUI widgets). **Compiled and wired, but not visually verified —
      no display in the port harness; it wants an in-world look on the owner's client** (like the
      schematics visual check).
    - **`/cityexport [name]`** snapshots the current world's *effective* settings
      (`CityWorldSettings.toData()` — post style-validation and decay override) into a ready datapack at
      `config/cityworld/exports/<name>/`. Drop it into another world's `datapacks/` and it applies.
    - **A first-run example** at `config/cityworld/settings-example/` (next to the schematics drop-in):
      a full datapack whose `default.json` spells out *every* knob at its default, plus
      `settings-reference.txt` documenting each setting, its type and sensible range — the "download
      the defaults and see everything that can change, with commentary" ask.
    - **One codec gotcha worth keeping:** `optionalFieldOf(name, default)` **omits** any field equal to
      its default on *encode* (a default world round-trips to `{}`). Correct for reading, useless as a
      human template — so the written datapacks use a hand-rolled full serializer (`SettingsDatapack.
      toFullJson`), not the codec. Also: 1.21.9+ changed `pack.mcmeta` to require `min_format`/
      `max_format` (each `[major, minor]`); the writer emits them from the running version, verified to
      load with no "incompatible" warning.
    - Verified headlessly: an edited *full* export pack dropped into a world loaded clean (no format
      warning), decoded every field, and applied (`includeRoundabouts`/`spawnBaddies` flipped).

  - **Still open on the settings layer** (small): the `darkEnvironment` flag is runtime-only (set by
    the alien/nether styles), deliberately not a datapack knob; the Customize screen omits the
    radius/naming/mob groups by design (datapack-authored). Nothing blocking.
- [x] **P8 — World styles (done 2026-07).** All 10 styles are live behind an optional `style` field
      on the generator codec (mirrors `decayed`): NORMAL, NATURE, METRO, SPARSE, DESTROYED (terrain =
      Normal, differ via `validateSettingsAgainstWorldStyle` — ported, with the city-radius maths and
      `SubSurfaceStyle`), plus the six that needed their own terrain closures — FLOODED, SANDDUNES,
      SNOWDUNES, MAZE, FLOATING, ASTRAL — each a full `ShapeProvider`/`SurfaceProvider`(/Ore/Cover)
      + `Context/<style>/*` + `Plats/<style>/*` port wired into the provider `loadProvider` switches.
      Exposure: a `world_preset/<style>.json` per style (`level-type=cityworld:<style>` on servers) +
      a single-player **Customize button** (`RegisterPresetEditorsEvent` → `CityWorldCustomizeScreen`
      style picker), and prettified `generator.cityworld.*` lang. Each style headless-verified (spawn
      100%, 0 exceptions). One known cosmetic gap: huge mushrooms render all-cap (the 1.12
      `MushroomBlockTexture` model is gone). See the `cityworld-next-p8-world-styles` memory.
- [ ] **P8 remainder — Parity & polish.** GameTest coverage; README/docs; furnished-Rooms polish;
      loot tables to native 1.21 datapack.

**Critical path to first playable slice:** P0 → P1 → P2 → P3 (terrain in `cityworld:city`, no
cities yet). Cities/decoration/loot layer on after.

## Licence: GPL-3 (settled)

Upstream CityWorld is **GPL-3** (confirmed by the project owner; the upstream tree carries no
`LICENSE` file and no `<licenses>` in `pom.xml`, which is why this was ambiguous at first). This
port is a **derivative work**, so it **must remain GPL-3** — GPL-3 → MIT is not permitted
(compatibility runs the other way only: MIT code may be absorbed into a GPL-3 work).

An earlier `mod_license=MIT` was an unverified assumption copied from the MobHealth template; it has
been corrected to `GPL-3.0-only`, and a verbatim GPL-3 `LICENSE` now lives at the repo root.

**The original author knows about this port and has approved it** (2026-07). GPL-3 already permitted
the fork, so his blessing was not required — it was asked for anyway, which seems the right way to
treat someone whose project this was. Worth recording because it is otherwise undocumented, and
because it means open questions about upstream's *intent* — as opposed to its behaviour, which the
code answers — now have somewhere to go. (Fittingly, his own plea is still in the tree: the log line
in `ShapeProvider_Normal.getContext(int, int)` asking whoever sees it to email him. It is preserved
verbatim.)

**Consequence — the noise question resolves the good way:** since we are GPL-3 and Bukkit's API is
GPL-3, we **may vendor** Bukkit's `SimplexNoiseGenerator`/`SimplexOctaveGenerator` (with notices and
attribution intact) and so **preserve CityWorld's exact terrain shape**, rather than approximating
it with vanilla noise and getting different terrain.

**Done** (2026-07): the five classes now live in `me.daddychurchill.CityWorld.compat.noise`
(`NoiseGenerator`, `PerlinNoiseGenerator`, `SimplexNoiseGenerator`, `OctaveGenerator`,
`SimplexOctaveGenerator`), each carrying an attribution header naming Bukkit as the source, the
GPL-3 basis for vendoring, and the only change made (dropping the `org.bukkit.World` ctors). Bukkit
ships no per-file licence headers — its licence is repo-level — which is precisely why the header we
add matters. Upstream Bukkit in turn derived them from Stefan Gustavson's public-domain simplex
paper; that credit is preserved too.

Known wrinkle (not blocking, owner's call): GPL + linking against proprietary Minecraft is a
long-standing grey area in the modding ecosystem; many GPL mods ship regardless.

## Top risks

1. **Threading/determinism** in the multithreaded chunk pipeline (mutable caches → concurrent or
   per-chunk-recomputed). Biggest one. **Hit in full at wave 2 (2026-07) and dealt with** — two
   separate bugs, both invisible to the compiler:
    - `getPlatMap` cached in a `Hashtable` with a **non-atomic get-then-put**, so concurrent chunks
      would each build their own PlatMap for the same origin. Now a `ConcurrentHashMap` with
      `computeIfAbsent`, which builds exactly once per key however many threads ask.
    - Far worse: `ConnectedLot` drew its identity from `connectionKeyGen.getRandomLong()` — **one
      shared, mutable RNG**, so a lot's key depended on how many lots had been built before it.
      Single-threaded Bukkit made that reproducible; concurrent, arbitrarily-ordered planning would
      have made **the same seed produce a different world every run**, and raced the RNG besides.
      Now derived from the lot's position (`CityWorldGenerator.getConnectionKey(chunkX, chunkZ)`) —
      only key *equality* is ever tested, so the meaning is identical and it is order-independent.
      Verified: same seed + fresh generator ⇒ identical plan across a 25-platmap sample.

   The pattern to watch for in the rest of the port: **anything whose value depends on call order**.
   `CityWorldGenerator.getRelatedSeed()` is the remaining one — it advances a counter per call, so
   the provider stack must keep being built in upstream's order, on one thread.
2. **Neighbor access** during decoration for connected roads/parks. **Now live** — the city is drawn
   in `applyBiomeDecoration` against a `WorldGenRegion`, which restricts how far a write may reach.
   `RealBlocks` refusing to cross its chunk edge is what keeps this legal. **Sharper than it looks
   for entities**: `WorldGenRegion.addFreshEntity` skips the `ensureCanWrite` check that `setBlock`
   gets, and resolving a chunk outside the region's cache *throws* rather than declining — so an
   out-of-chunk spawn crashes the server instead of being quietly dropped. See `SpawnProvider`.
3. **Performance** — the original disabled several styles for perf even on Bukkit.
4. ~~**Per-world config** doesn't match NeoForge's per-instance config model.~~ **Resolved (2026-07,
   P7):** settings are a *datapack registry* (`cityworld:world_settings`), not a `ModConfigSpec`, so
   they are genuinely per-world/per-dimension — a server op ships a pack per save. The generator
   references a settings holder resolved at codec-decode. The name and mob lists landed in the same
   pass. See "P7 — Config + commands". `ModConfigSpec` was retired. What a datapack registry does
   *not* give is per-node runtime editing — settings are authored, frozen at world load; that suits
   the use case (retuning wants a datapack edit + reload/restart, not a live command).

**Struck: the old risk #1, "`generator.getWorld()` does not exist".** It was only ever needed by
`SpawnProvider`, which doesn't need it either — `compat/Location` carries its level exactly as
Bukkit's `Location` carried its world. See "Closed: mobs and loot". The numbering above is kept as-is
so older notes referring to "top risk #2" still point at neighbour access.

## 1.21.11 API notes (verified against the decompiled NeoForge sources)

These bit us / would bite anyone porting; confirmed by grepping the neoform sources jar
(`~/.gradle/caches/neoformruntime/.../sourcesAndCompiledWithNeoForge_*.jar`):

- **`ResourceLocation` is renamed `net.minecraft.resources.Identifier`** in 1.21.11. Factories:
  `Identifier.withDefaultNamespace(path)`, `Identifier.fromNamespaceAndPath(ns, path)`,
  `Identifier.parse(str)`.
- **`ChunkAccess.setBlockState(BlockPos, BlockState, int flags)`** — the third arg is an
  `@Block.UpdateFlags int`, **not** the old `boolean isMoving`. A 2-arg convenience
  `setBlockState(pos, state)` defaults to flags `3`. `ProtoChunk` updates heightmaps automatically.
- **Bukkit's "apply physics = false" is `UPDATE_SKIP_ALL_SIDEEFFECTS | UPDATE_CLIENTS`, not
  `UPDATE_CLIENTS`.** This bit us and was only caught by placing blocks in a live world. Writing
  with `UPDATE_CLIENTS` alone still lets the block run `onPlace` — so a powered rail re-reads the
  redstone around it and **un-powers itself**, and a plant checks what it is standing on and
  **deletes itself** — quietly corrupting whatever the generator placed. `LevelChunk.setBlockState`
  gates the side effects on flags: `onPlace` unless `UPDATE_SKIP_ON_PLACE` (512), container drops
  unless `UPDATE_SKIP_BLOCK_ENTITY_SIDEEFFECTS` (256). `UPDATE_SKIP_ALL_SIDEEFFECTS` (816) is
  vanilla's name for the whole inert combination. Fixed in `compat/Block`.
- **`ResourceKey.location()` is now `identifier()`** — same rename family as `ResourceLocation` →
  `Identifier`. So a dimension's path is `level.dimension().identifier().getPath()`.
- **`SignBlockEntity`**: text goes in via `updateText(UnaryOperator<SignText>, true /* front */)`,
  and `SignText.setMessage(i, Component)` returns a **new** `SignText`. It notifies its level on
  change, and a block entity built on demand through a `WorldGenRegion`/`ProtoChunk` has none — see
  the `SupportBlocks` note above.
- **Registry lookups**: `BuiltInRegistries.BLOCK.getValue(Identifier)` returns a `@Nullable Block`;
  `.get(Identifier)` returns `Optional<Holder.Reference<Block>>`; `.getKey(block)` returns the
  `Identifier`.
- **State properties** live in `net.minecraft.world.level.block.state.properties.BlockStateProperties`:
  `FACING`/`HORIZONTAL_FACING` (`Direction`), `AXIS` (`Direction.Axis`), `SLAB_TYPE` (`SlabType`),
  `HALF` (`Half`), `DOUBLE_BLOCK_HALF` (`DoubleBlockHalf`), `WATERLOGGED`, and boolean connection
  faces `NORTH/EAST/SOUTH/WEST/UP/DOWN`. Apply with `state.setValue(prop, val)` /
  guard with `state.hasProperty(prop)`.

## Build notes

- No system Java; build with `JAVA_HOME=../MobHealth-Forge/tools/jdk21` (also copied to
  `./tools/jdk21`, git-ignored). `./gradlew compileJava` is the fast inner loop while porting.
- Decompiled MC/NeoForge sources (for checking signatures) extract from the neoform jar above.

## Future ideas (parking lot)

- ~~**Vary grass / soil / foliage / trees by BIOME (owner, 2026-07).**~~ **Done (2026-07) for the
  colour/terrain half.** `CityWorldBiomeSource` (registered `cityworld:terrain`) carries a configurable
  8-biome palette keyed to CityWorld's height bands, and `CityWorldChunkGenerator.createBiomes` fills
  each column by classifying its deterministic terrain height (the same bands the shaper used) — deep
  ocean / ocean / beach / low / mid / high / peak, plus a dry biome for nature-decayed worlds. So
  grass, water and foliage colour and biome mobs now follow the land (verified: ocean below sea, beach
  at the waterline, plains→forest→taiga→snowy up the height bands). The palette lives in the
  biome-source JSON, so CLASSIC and MODERN (and each style) can use different biomes for the same band.
  Wired into the classic preset; the other style presets still use a fixed biome (their terrain is
  unusual — do them per-style as needed).

  **MODERN climate biomes done (2026-07):** `CityWorldClimateBiomeSource` (`cityworld:climate`) crosses
  CityWorld's elevation with a slow temperature+humidity field (`CityWorldGenerator.getTemperature/
  getHumidity`, seeded off the world) and a matrix maps the pair to the **full overworld biome spread**
  — deserts/savannas warm-dry, jungle/swamp/mangrove warm-wet, snowy/ice-spikes cold, cherry-grove/
  dark-forest temperate hills, badlands/windswept/old-growth up high, warm→frozen oceans by latitude.
  Both sources share the elevation bands via the `CityWorldBiomes` interface, driven uniformly by
  `createBiomes` (CLASSIC ignores the climate). The `cityworld:city` preset + dimension + the Customize
  MODERN path use it; verified: **36 distinct biomes** in one 65×65 area, temp/humid span 0..1.
  **Cover — hybrid, trial (2026-07):** owner's call — CityWorld's own cover in the built areas, vanilla
  wild decoration on the nature lots. Since each chunk is one lot, `applyBiomeDecoration` calls
  {@code super.applyBiomeDecoration} (vanilla biome features) after CityWorld's pass, gated on
  {@code lot.style == NATURE} and {@code MODERN}. Verified: 841 chunks force-loaded, 0 failures, and
  the nature lots grow biome-appropriate vanilla vegetation — acacia/cherry/dark-oak/jungle/mangrove/
  spruce/birch trees, bamboo, a full coral reef + seagrass in warm oceans, mushrooms. **Open (owner to
  eyeball):** CityWorld's nature lots still place their own cover too, so wild forests may read *dense*
  (CityWorld trees + vanilla trees); if so, suppress CityWorld's nature cover on MODERN nature lots so
  vanilla is the sole wild decorator. Gaps vanilla can't place from terrain (mushroom fields, cave
  biomes, deep dark) → the "bio-dome" set-piece. City-area cover (CityWorld's) is unchanged.

- **"Zoo" / "Bio Dome" lot to cover biome-block gaps (owner, 2026-07).** If MODERN can't get *every*
  naturally-spawning vanilla block to appear in the wild, add a cheeky special lot — a zoo or botanical
  bio-dome — that showcases the stragglers (rare biome blocks, plants, spawn eggs' mobs) in one build.
  A fun catch-all and a landmark. Rides the NatureContext set-piece machinery.

- **MODERN "overgrown" look + full 1.21 palette (owner, 2026-07).** Use the whole modern block range in
  MODERN's providers — and for a decayed/overgrown MODERN, drape **moss carpet, vines, leaf litter,
  azalea, glow lichen** over ruins and nature (the CoverProvider + the decay path). Rides the
  per-style settings-profile pattern (`cityworld:modern`) and, ideally, the biome work above.

- ~~**Decay as a probability, not on/off — rare pristine buildings/schematics (owner, 2026-07).**~~
  **DONE (2026-07).** A building/schematic can survive intact even in an apocalypse world (a small
  global pristine chance, `[terrain] oddsOfPristineBuilding`, default tiny 0.0001), overridable
  per-schematic via a `PristineChance:` yml key. Schematics shipped first (rolled from the build's NW
  origin so a multi-chunk build agrees). **Regular buildings now too:** new `PlatLot.buildingsDecay()`
  helper — `includeDecayedBuildings && !pristine`, rolled once per lot and cached, seeded from the
  building's `getConnectedKey()` (position fallback for isolated lots) so every chunk of a multi-chunk
  building agrees. The scattered `includeDecayedBuildings` checks in the ordinary building lots
  (`FinishedBuildingLot`, `HouseLot`, `BarnLot`, `FactoryBuildingLot`, `StorageLot`, `MuseumBuildingLot`,
  `WaterTowerLot`) now test `buildingsDecay()` instead; the always-ruined nature set-pieces (castle,
  radio tower, oil platform), floating-style lots, the unfinished-building lot, and the spawner logic
  were left on the raw flag by design. Verified: at a 0.3 test setting, 29.6% of 29,605
  `FinishedBuildingLot`s came back pristine — the roll fires at the configured rate.

- **⭐ "Modern" vs "Classic" — a modernization world style, made default (owner's idea, 2026-07).**
  The big one. Rename today's `NORMAL` style to **`CLASSIC`** (it faithfully reproduces the 1.8-era
  look — old blocks, old height feel, no vanilla structures) and add a new **`MODERN`** style that
  becomes the *default*, using everything current Minecraft offers:
    - **Taller builds** — actually use the -64..319 headroom. This subsumes the existing
      `DataContext.buildingMaximumY` cap (still pinned at the 256 terrain ceiling) — but it should be
      **per-style**, not a blanket raise: Classic stays short, Modern goes tall. So the height cap
      wants to move onto the settings/style, not be globally bumped.
    - **Modern blocks** — deepslate + its ore variants (already half-wired, see P4), tuff, calcite,
      copper/oxidation, modern wood sets, glazed terracotta, etc. in the material providers.
    - **Modern mobs** — the newer entities in the spawn bags (allays, foxes/goats where apt, wardens
      only where deliberate). The mob lists are already datapack-overridable (P7), so Modern can ship a
      richer default `mobs` group while Classic keeps the 1.8 roster.
    - ~~**Modern ice/snow** — packed ice / blue ice / powder snow to *ice the mountaintops* properly
      (the cover/surface providers currently use plain snow); Snowdunes-style worlds especially.~~
      **Done (2026-07).** `SurfaceProvider_Normal.generateModernIcecap` grades MODERN peaks by height
      above the snow line: snow blocks on the slopes, packed ice higher, glacier-blue ice at the tips,
      powder-snow pockets throughout — all full cubes. Fixes the loose-snow-on-ice bug (a snow *layer*
      on ice is illegal and cascades on touch); layers now only ever sit on snow blocks. Added
      `BLUE_ICE`/`POWDER_SNOW` to the `gen_material.py` EXTRAS. Snowdunes still uses its own provider.
    - **New tree types** — cherry, mangrove, azalea, spruce/large variants via the tree provider
      (`TreeStyle` already exists but only `NORMAL` is wired; this is where `SPOOKY`/`CRYSTAL` and new
      ones land per style).
    - **Allow *some* vanilla structures** — instead of suppressing every structure set (see the
      "harvest vanilla structure points" idea below), let Modern permit a curated few (ancient cities
      deep down, trial chambers, the odd shipwreck/ruined portal) to blend with CityWorld's own.
  **Why this shape:** most of the piecemeal "what's left" polish (building-height cap, deepslate ores,
  new trees, modern cover) is really *facets of the Modern style*, so doing them under one style banner
  is cleaner than one-off global changes — and it keeps a pixel-faithful `CLASSIC` for people who want
  the original. Mechanically it rides the P8 style machinery already in place (a `WorldStyle` value +
  `validateSettingsAgainstWorldStyle` + provider `loadProvider` switches + a `world_preset`), plus the
  P7 datapack settings for the knobs. **Decisions to make with the owner:** does `MODERN` become the
  literal codec default (changes new-world behaviour) or just the top preset; how far to push vanilla
  structures; and whether "Modern" is one style or a family (Modern + Modern-Sparse, …).

- ~~**Let players write their own villager names, street names and mob lists.**~~ **Done (2026-07,
  P7).** It was re-attaching a reader, not new design — exactly as predicted: the nine `OdonymProvider`
  name lists and the ten `AbstractEntityList` mob bags now come from the `naming`/`mobs` groups of the
  `cityworld:world_settings` datapack, each defaulting to empty = "keep the compiled list" (upstream's
  `getNames` fallback). Mob-name validation (unknown → log-and-skip) came along via
  `EntityType.of(String)`. The datapack mechanism the note guessed at is exactly what shipped.

- **Harvest vanilla structure placement points as city anchors.** Right now the generator
  *suppresses* all vanilla structure sets (villages, mineshafts, trial chambers, …) so CityWorld
  owns the chunk. Distant-future idea: instead of only discarding them, read where vanilla *would*
  have placed structures and use those points as anchors to seed CityWorld content — e.g. drop a
  landmark building, a plaza, an underground vault, or a themed district at a would-be village /
  trial-chamber location. The placement machinery already computes good spots; we'd be repurposing
  them as hints rather than throwing them away.
