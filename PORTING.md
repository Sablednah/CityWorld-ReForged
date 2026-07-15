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
- [ ] **P2 — Material mapping.** `Material` name → `BlockState` behind `MaterialProvider`; most
      1.14 names map 1:1 to `minecraft:<name>`; special-case removed/renamed blocks and state
      variants. Unblocks compilation of the Material-using files.
- [ ] **P3 — Custom `ChunkGenerator` + registration (vertical slice).** Codec-registered
      `CityWorldChunkGenerator` driving `PlatMap` via `fillFromNoise`; `buildSurface`,
      `getBaseHeight`/`getBaseColumn`; custom `BiomeSource`. Register the dimension (datapack JSON)
      and world preset. **Gate: teleport into `cityworld:city` and see terrain.**
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

## Top risks

1. **Threading/determinism** in the multithreaded chunk pipeline (mutable caches → concurrent or
   per-chunk-recomputed). Biggest one.
2. **Neighbor access** during decoration for connected roads/parks.
3. **Performance** — the original disabled several styles for perf even on Bukkit.
4. **Per-world config** doesn't match NeoForge's per-instance config model.
