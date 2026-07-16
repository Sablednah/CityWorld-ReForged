# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here (next task)

**The block seam (P1) is done and runtime-verified — start the brain: `Plugins/ShapeProvider`.**
Its signatures take `RealBlocks`, which is why the seam had to come first; that dependency is now
satisfied. Nothing is left blocking it.

Expect this to be the hard part, and **not** an incremental one — see "There is no terrain-only
slice" below. `ShapeProvider` sits in the mutually recursive cycle (`ShapeProvider ↔ PlatMap ↔
PlatLot ↔ Context ↔ Plugins ↔ Rooms ↔ Clipboard`), so the plan of record is a scripted **mass
transform in waves plus shims**, the way `AbstractBlocks`/`CornerBlocks` were done — not
file-by-file.

### Recon already done (2026-07 — measured, don't re-derive)

**Only two files are needed for the P3 gate, not the whole family.** `ShapeProvider` has 10
variants totalling 2,376 lines, but the gate ("teleport in and see terrain") only needs **NORMAL**:

| | |
|---|---|
| `Plugins/ShapeProvider` (abstract base) | 366 lines |
| `Plugins/ShapeProvider_Normal` | 477 lines |
| the other 8 variants (`_Astral` 558, `_Floating` 262, `_SnowDunes` 265, `_SandDunes` 173, `_Flooded` 119, `_Maze` 86, `_Metro` 51, `_Nature` 19) | ~1,533 lines — **defer**; `loadProvider` switches on `worldStyle`, so stub the arms |

So the first wave is **~843 lines**, not 2,376.

**`ShapeProvider`'s direct unported dependencies** (from its own imports):

| | |
|---|---|
| `Support/PlatMap` | 548 — the 10×10 chunk grid, seed-deterministic |
| `Plats/PlatLot` | 612 — one chunk's "what goes here" |
| `Context/DataContext` | 165 — **currently a stub** (`torchMat` + `FloorHeight` only) |
| `Context/RoadContext` | 70 |
| `Support/AbstractCachedYs` | 88 — plus `TraditionalCachedYs` (11) |
| already done | `Material`, `InitialBlocks`, `RealBlocks`, `Odds`, `CityWorldGenerator` (skeleton) |

**Biomes: deferrable, despite being listed as "design needed".** The 56 in the coupling table is a
*use* count and reads scarier than it is — it lands in only **12 files**, concentrated in the
`ShapeProvider_*` variants (`_Normal` 13, `_Astral` 6), `PlatLot` (6) and `SpawnProvider` (5). On the
abstract base the entire surface is **two signatures**: `generateChunk(..., BiomeGrid biomes,
AbstractCachedYs blockYs)` and `remapBiome(generator, PlatLot lot, Biome biome)`. So put a thin
`compat/Biome` + `BiomeGrid` shim behind those two, port the brain, and hook a real `BiomeSource` at
P3/P4 when there is terrain to look at. The architectural question (CityWorld writes biomes per
column; modern gen assigns them via a `BiomeSource`) is still real — it just isn't a gate.

**Noise is already decided**: vendor Bukkit's `SimplexNoiseGenerator`/`SimplexOctaveGenerator`
(GPL-3 permits it — see the licence section) to keep CityWorld's exact terrain shape.
`NoiseGenerator.floor` is just `Mth.floor`; no vendoring needed for that one.

### Then

1. **Grow the stubs as you go.** `CityWorldGenerator` is a skeleton (`height`, `streetLevel`,
   `reportFormatted`, `clearAtmosphere`, `findAtmosphereMaterialAt`); the real `streetLevel` comes
   from `shapeProvider.getStreetLevel()` (`= seaLevel + 1`). `DataContext` is a stub;
   `LootProvider`/`Provider` are signature-only. **Watch the threading rule**: the original hung
   per-world state off the generator (`generator.getWorld()`); a modern `ChunkGenerator` is shared
   and immutable, so that state gets passed in instead — that is exactly why `RealBlocks` now takes
   a `LevelAccessor` + `ChunkPos`. Same trap will recur across the brain (top risk #1).
2. **Don't rebuild the block layer.** `SupportBlocks` owns the `BlockData → BlockState` translation
   (`with`, `withDirection`, `withHalf`, `withRailShape`, `withScaledLevel`, `stateOf`);
   `compat/Material` has `withFacing`/`withFaces`/`asSlab`/`asDoorHalf`/`hasFaces`/`isOccluding`.
   No `block.data.*` shims are needed — they all map onto vanilla property enums.
3. Then P3 replaces the placeholder fill in `CityWorldChunkGenerator` with the real brain.

**Verify behaviour, don't just compile.** Both waves of the seam were proven with a temporary
subclass + a `ServerStartedEvent` listener that places blocks and logs the resulting `BlockState`s
(Gradle can't pipe stdin to the server console). That caught two real bugs and a coordinate check
that compiling never could. Delete the probe before committing.

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
| `ChunkGenerator.BiomeGrid` + `block.Biome` | 56 uses, but only **12 files** | **Deferrable, not a blocker** (re-measured 2026-07). CityWorld writes biomes per column; modern gen assigns via `BiomeSource` — still a real architectural change, but it is concentrated in the `ShapeProvider_*` variants, `PlatLot` and `SpawnProvider`, and the abstract `ShapeProvider` exposes just **2 signatures** (`generateChunk(…, BiomeGrid, …)`, `remapBiome(…)`). Shim those two, port the brain, hook a real `BiomeSource` at P3/P4. |
| `util.noise.*` (`NoiseGenerator`, `SimplexNoiseGenerator`, `SimplexOctaveGenerator`) | 25 | **Vendor Bukkit's noise classes** — cleared now that we're GPL-3 (see licence section). Preserves CityWorld's exact terrain shape. |
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
    - [ ] Replace the placeholder fill with the real terrain brain (needs P2 + `ShapeProvider`/
          `PlatMap`/contexts ported); add the `/cityworld` teleport into the `cityworld:city`
          dimension.
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
