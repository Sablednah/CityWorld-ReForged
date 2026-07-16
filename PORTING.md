# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here (next task)

**Wave 1 of the brain is done and runtime-verified: `ShapeProvider` + `ShapeProvider_Normal` are
ported and produce real, deterministic CityWorld terrain heights.** The next task is to **wire that
terrain into `CityWorldChunkGenerator.fillFromNoise`**, replacing the placeholder flat fill — that
is the remaining half of the P3 gate ("teleport in and see terrain").

### Wiring it in — what's already true, and the one decision to make first

The call shape is short, because the pieces exist:

```java
CityWorldGenerator context = context();                 // already there, lazy + thread-safe
PlatLot lot = new PlatLot(context, chunkX, chunkZ);      // precalcs its own cached Ys
InitialBlocks blocks = new InitialBlocks(context, chunk, chunkX, chunkZ);
context.shapeProvider.preGenerateChunk(context, lot, blocks, biomes, lot.blockYs);
context.shapeProvider.postGenerateChunk(context, lot, blocks, lot.blockYs);
```

Two things to settle before writing it:

1. **The Y offset — decide this first, it's the whole shape of P4.** The ported shaper thinks in
   upstream's `0..255` world (`ShapeProvider.bottomOfWorld = 0`, and `CityWorldChunkGenerator`
   currently feeds it `UPSTREAM_MAX_HEIGHT = 256` / `UPSTREAM_SEA_LEVEL = 63` deliberately, to
   reproduce upstream's terrain exactly). Wired naively into a `-64..319` world, terrain lands at
   `y = 0..253` with a void gap below it. Either accept that as a visible-but-wrong-looking gate and
   fix it at P4, or do the height modernization first. Not a bug — a sequencing choice.
2. **`BiomeGrid`** needs an implementation to receive the per-column biomes (a no-op one is enough
   to see terrain; see `compat/BiomeGrid` for why the real answer is a `BiomeSource`).

### ⚠ Open question: is upstream's sea level 63 or 64? (found 2026-07, unresolved)

We pass **63**. Upstream's own commented-out reference line in `initializeWorldInfo` reads:

> `seabed = 35 deepsea = 50 sea = 64 sidewalk = 65 tree = 110 evergreen = 156 snow = 202 top = 249`

Recomputing that line both ways: `seabed = 35` and `top = 249` come out right **either way** (the
`landRange` formula absorbs the difference), but `sea`/`sidewalk`/`tree`/`evergreen`/`snow` only
reproduce **exactly** at `seaLevel = 64` — at 63 the port gives `63/64/109/155/201`, uniformly one
low. (`deepsea` matches neither, so that line is at least partly stale.) Bukkit's `World.getSeaLevel()`
javadoc is no help — it claims "often half of getMaxHeight()", i.e. 128, which is plainly not what
the comment shows. CityWorld predates 1.14 by years, so the comment may record an older Bukkit where
sea level was 64.

**Why it matters:** the whole point of vendoring Bukkit's noise was to reproduce upstream's terrain
*exactly*; a one-block datum shift quietly undercuts that. **Why it isn't urgent:** it shifts every
level uniformly by one, and P4 re-does this Y mapping anyway. Settle it against a real 1.14
CraftBukkit `getSeaLevel()` before declaring terrain parity at P8.

### What wave 1 actually built (verified in a live world, 2026-07)

A `ServerStartedEvent` probe (since deleted) drove the ported provider directly. Results, seed 12345,
`256`/`63`:

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

**Still unexercised**: `preGenerateChunk` → `generateStratas` → `chunk.setBlock`, i.e. the path that
actually *writes* terrain. It needs a real `ChunkAccess`, so it gets proven by the wiring above —
that is the first thing to check once fillFromNoise drives it.

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
  (not a 1:1 copy of the 1.14 `0..255` layout).
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
- [ ] **P4 — Height modernization.** `ShapeProvider` Y math for `-64..319`; deepslate strata;
      sea/tree/snow bands; `OreProvider` deepslate variants; `SurfaceProvider`; modern carvers.
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
   per-chunk-recomputed). Biggest one.
2. **Neighbor access** during decoration for connected roads/parks.
3. **Performance** — the original disabled several styles for perf even on Bukkit.
4. **Per-world config** doesn't match NeoForge's per-instance config model.

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

- **Harvest vanilla structure placement points as city anchors.** Right now the generator
  *suppresses* all vanilla structure sets (villages, mineshafts, trial chambers, …) so CityWorld
  owns the chunk. Distant-future idea: instead of only discarding them, read where vanilla *would*
  have placed structures and use those points as anchors to seed CityWorld content — e.g. drop a
  landmark building, a plaza, an underground vault, or a themed district at a would-be village /
  trial-chamber location. The placement machinery already computes good spots; we'd be repurposing
  them as hints rather than throwing them away.
