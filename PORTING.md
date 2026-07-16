# CityWorld — Bukkit → NeoForge port plan

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
| `ChunkGenerator.BiomeGrid` + `block.Biome` | 56 | **Design needed** — CityWorld writes biomes per column; modern gen assigns via `BiomeSource`. Architectural change. |
| `util.noise.*` (`NoiseGenerator`, `SimplexNoiseGenerator`, `SimplexOctaveGenerator`) | 25 | **Vendor Bukkit's noise classes** — cleared now that we're GPL-3 (see licence section). Preserves CityWorld's exact terrain shape. |
| `block.data.*` (`Bisected.Half`, `Slab.Type`, `Stairs`, `Rail.Shape`, `Bed`, `Door`, `Leaves`, `Snow`, `Chest`, …) | ~45 | Small shims → `BlockState` properties (the `Material` helpers already cover facing/slab/half). |
| `World`, `Chunk`, `Location`, `Bukkit`, `Environment` | ~30 | Decoration-side → `WorldGenLevel`/`ServerLevel`. |
| `entity.*` (`EntityType`, `Entity`, `Player`, `Item`) | ~15 | → modern `EntityType` (P5). |
| `configuration.*` | ~10 | → `ModConfigSpec` (P7). |
| `command.*`, `plugin.*`, `event.*` | ~15 | → Brigadier / drop plugin lifecycle (P7). |
| misc (`DyeColor`, `Axis`, `NamespacedKey`, `TreeType`, `ItemStack`, `Inventory`, `Sign`, `CreatureSpawner`, `MushroomBlockTexture`) | ~12 | Small shims, as encountered. |

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
- [ ] **P1 — Block seam.** Reimplement `AbstractBlocks`/`InitialBlocks`/`RealBlocks`/`SupportBlocks`
      on `ChunkAccess`/`BlockState`; `BlockFace` → `Direction`; oriented placement (stairs, doors,
      facing, waterlogging).
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
    - [ ] **Decoration-side seam** (reads the live world — needed by P5, not by the P3 terrain gate):
          `SupportBlocks` (971 lines), `RealBlocks`, `RelativeBlocks`, `WorldBlocks`, `CornerBlocks`
          (1222 lines). These bind to `WorldGenLevel`/`LevelAccessor` rather than `ChunkAccess`.
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
