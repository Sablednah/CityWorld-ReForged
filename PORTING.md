# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here (next task)

**CityWorld generates cities, and they're inhabited.** Terrain, roads with named street signs,
buildings with furnished interiors, parks, roundabouts, farms, civic districts, trees and ground
cover — all verified by reading blocks back out of a real world, deterministically, no exceptions.

**Three jars are stashed in `builds/`** (git-ignored) for comparing progress by hand:
`cityworld-p5-trees-and-streets.jar`, `cityworld-p5-plus-farms-and-civic.jar`, then
`cityworld-p5-mobs-and-loot.jar`. Remember: **new world each time** — existing chunks never
regenerate.

**The cities are inhabited and the chests have things in them** (2026-07). Villagers with names,
animals in the fields, fish in the sea, spawners in the mines and sewers, and 13 loot tables that
vanilla rolls on first open. See "Closed: mobs and loot" below — both were *smaller* than this
document predicted, because two of the risks recorded here rested on unchecked assumptions.

**What's left is breadth, not architecture.** The two most valuable next steps, in order:

1. **The industrial family** — the last ladder arm that has a lot family waiting
   (`FactoryBuildingLot` 703 + `BunkerLot` 1037). Measured: ~90 compiler fixes, vs ~4 for farm.
   Its `itemsEntities_Bunker` list and `BUNKER`/`STORAGE_SHED`/`WOODWORKS`/`STONEWORKS` loot tables
   are already ported and waiting — they light up for free when the lots land.
2. **Outland + the nature set-pieces** — bunkers, radio towers, oil platforms, flying saucers.
   The last ladder arm, and the only one with no setting to hide behind.

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
generation. Invisible in play (a chunk ticks when a player is near enough to look at it), and the
scheduled tick survives in the chunk's tick container until then. If it ever proves not enough, the
fallback is to fill the channel statically in `RoadLot` and stop depending on flow at all.

**Two generalisations worth more than the bug:**
- **`setDoPhysics(true)` had exactly one caller in the whole tree** — this water. A seam with one
  user, silently doing nothing. Grep for lone callers of a compat flag; they are where the
  assumptions hide.
- **The dangerous ports are the faithful ones.** A line that reads identically to upstream and
  compiles clean can still be wrong, because upstream's line depended on *when* and *where* it ran.
  Anything relying on ticks, physics, neighbours or a live world is suspect at decoration time.

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

The context ladder in `ShapeProvider_Normal.getContext(PlatMap)` is real and eight of its ten arms
are live (park, highrise, construction, midrise, lowrise, neighborhood, municipal, farm). Two remain:

| arm | needs | how it's held back |
|---|---|---|
| industrial | `FactoryBuildingLot` (703), `WarehouseBuildingLot` (99), `StorageLot` (121), and `BunkerLot` (1037) under them | `includeIndustrialSectors = false` |
| outland | `CampgroundLot`, `GravelworksLot`, `WoodworksLot`, `MineEntranceLot`, … | **no setting guards this one** — its band falls through to nature, marked in `getContext` |

**Using the setting for industrial is deliberate, not a hack.** Upstream already guards that arm
with exactly that flag, so switching it off makes the band fall through to the next precisely as it
would for a player who disabled the feature. Restoring it is: port its lots, allocate its context in
`allocateContexts`, flip the flag. The context field is already declared.

**Why industrial and not the others** (measured, don't re-derive): transformed together, the three
families needed wildly different amounts of fixing against the compiler — municipal **~0**, farm
**4**, industrial **90** (`FactoryBuildingLot` 69, `BunkerLot` 21). Factory reaches for
`InteriorStyle`, `insetWallNS/WE`, `firstFloorHeight` and `itemsSelectMaterial_FactoryInsides`;
Bunker wants `RoadThroughBunkerLot` and `itemsSelectMaterial_BunkerPlatforms`.

### P5: what's deliberately not done

Trees, ground cover, street names, statues, fossils, **mobs and loot** are ported.

Also stubbed, each documented at its site: `StructureOnGroundProvider` (1158 — water towers, sheds,
campgrounds; a park lays out correctly but has no water tower in it), `StructureInAirProvider` (207
— balloons and blimps).

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

Still unreached, because their lots aren't ported: `BUNKER`, `STORAGE_SHED`, `WOODWORKS(_OUTPUT)`,
`STONEWORKS(_OUTPUT)` chests, and the `itemsEntities_Bunker` list.

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
    - [ ] Add the `/cityworld` teleport into the `cityworld:city` dimension.
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
    - [ ] `OreProvider` deepslate **ore** variants (`deepslate_coal_ore`, …) — needed once ores are
          actually placed (P5). Add them via `EXTRAS` in `gen_material.py`, as `DEEPSLATE` was.
    - [ ] `SurfaceProvider`; modern carvers; revisit `DataContext.buildingMaximumY` (still capped at
          the 256 terrain ceiling, though the world now allows 319).
- [ ] **P5 — Decoration (old `BlockPopulator`).** Loot chests, spawners, furnished `Rooms`,
      neighbor-aware roads/parks → feature placement / post-gen, respecting neighbor limits (the
      seed-deterministic `PlatMap` makes per-chunk regeneration viable). Migrate datapack loot
      tables to 1.21 format and bundle them in mod resources (drop the runtime extraction).
      `SpawnProvider` → modern `EntityType`.
- [ ] **P6 — Schematics.** Reimplement `PasteProvider`/`Clipboard` via `StructureTemplate`; convert
      `schematics/` assets to `.nbt` (or read the existing format).
- [ ] **P7 — Config + commands.** `CityWorldSettings` YAML → `ModConfigSpec` TOML (per-world
      settings need a datapack/world-saved-data approach — NeoForge config is per-instance).
      `/cityworld`, `/citychunk`, `/cityinfo` → Brigadier; NeoForge permissions.
- [ ] **P8 — Parity & polish.** Re-enable world styles (`validateStyle` currently forces `NORMAL`);
      GameTest coverage; README/docs.

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
4. **Per-world config** doesn't match NeoForge's per-instance config model. Note the *name and mob
   lists* are a separable slice of this and need not wait for it — see "Let players write their own
   villager names…" in the parking lot.

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

- **Let players write their own villager names, street names and mob lists** (asked for by the owner,
  2026-07). Personalising your own city is the point — "Christine Johnson on Elm Street" should be
  able to be *your* names.

  **This is re-attaching a reader, not new design.** Upstream already had it, and the port kept the
  seams and dropped only the plumbing, because the plumbing was Bukkit YAML:
    - `OdonymProvider_Normal` holds **nine** lists, each already paired with its config tag —
      `VillagerGivenNames`, `VillagerSurnames`, `StreetTerms`, `StreetPrefixes`, `StreetStarts`,
      `StreetEnds`, `StreetSuffixes`, `FossilPrefixes`, `FossilSuffixes`. The `tag*` fields are
      **still in the port and currently unused**; upstream's `read` did
      `getNames(section, tag, defaults)` — take the list if configured, else keep the hardcoded one.
      That fallback shape is exactly right and should survive.
    - `AbstractEntityList` had the same arrangement for the mob lists (`Entities_For_Goodies`,
      `_Baddies`, `_Animals`, `_SeaAnimals`, `_Vagrants`, `_Sewers`, `_Mine`, `_Bunker`,
      `_WaterPit`, `_LavaPit`). Its `read`/`write` were dropped in the mobs port for want of a
      reader; the `listName` is still carried and `getListName()` is there for whoever adds one.
      Note upstream's reader also validated names and reported unknown/nonliving ones — worth
      keeping, since a typo'd entity name is the obvious failure mode.

  **The open question is the mechanism, and it is the same one as top risk #4**: CityWorld's settings
  are per-world, NeoForge's `ModConfigSpec` is per-instance. A datapack fits these particular lists
  unusually well — they are pure data, they want to differ per world, and players already know how to
  edit one; loot tables landed exactly this way in P5 and cost nothing. A custom registry or plain
  JSON under `data/cityworld/` would both work. Whatever P7 picks for settings generally, **these
  lists probably shouldn't wait for it** — they have no dependency on the rest of the settings layer.

- **Harvest vanilla structure placement points as city anchors.** Right now the generator
  *suppresses* all vanilla structure sets (villages, mineshafts, trial chambers, …) so CityWorld
  owns the chunk. Distant-future idea: instead of only discarding them, read where vanilla *would*
  have placed structures and use those points as anchors to seed CityWorld content — e.g. drop a
  landmark building, a plaza, an underground vault, or a themed district at a would-be village /
  trial-chamber location. The placement machinery already computes good spots; we'd be repurposing
  them as hints rather than throwing them away.
