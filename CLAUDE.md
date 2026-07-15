# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

This repo is a fork of **CityWorld**, originally a Bukkit/Spigot 1.14 plugin that procedurally
generates worlds full of cities, roads, buildings, mines, sewers, farms and nature. It is being
**ported to a modern NeoForge mod**. The port is built in place at the repo root; the original
Bukkit project has been removed from the working tree but remains the reference implementation in
git history and on the upstream fork.

**Read `PORTING.md` first** — it is the living plan (phases, decisions, coupling inventory, risks)
and the source of truth for what's done and what's next.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | NeoForge 21.11.42 |
| Java | 21 |
| Build | Gradle + ModDevGradle (`net.neoforged.moddev`) |

## Build & run

Requires a JDK 21. There is no system Java on this machine; a bundled JDK21 lives at
`../MobHealth-Forge/tools/jdk21` — export it as `JAVA_HOME` for Gradle:

```bash
export JAVA_HOME=/mnt/d/Repos/sable/MobHealth-Forge/tools/jdk21
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew build        # -> build/libs/cityworld-<version>.jar
./gradlew runClient    # dev client
./gradlew runServer    # dev dedicated server
./gradlew runData      # data generators -> src/generated/resources
```

- All versions/metadata live in `gradle.properties` and are expanded into
  `src/main/templates/META-INF/neoforge.mods.toml` at build time — **edit the template and
  gradle.properties, never a generated `mods.toml`**.
- Mod id is `cityworld`; the `@Mod` entrypoint is
  `me.daddychurchill.CityWorld.CityWorldMod`.

## Conventions specific to this port

- **Package tree is preserved**: ported code keeps the original `me.daddychurchill.CityWorld.*`
  packages (minimizes churn across ~300 files, keeps attribution). Note the Gradle group id is
  lowercase `me.daddychurchill.cityworld` — that's fine, it need not match the package.
- **Reading the reference source**: the Bukkit original is not in the working tree. Retrieve any
  file from history, e.g. `git show HEAD~1:src/me/daddychurchill/CityWorld/Support/AbstractBlocks.java`.
- Keep loader-agnostic generation logic free of NeoForge/Minecraft-specific glue where practical
  (the MobHealth port follows the same `core` vs. loader-glue split — see `../MobHealth-Forge`).

## Architecture being ported (the generation "brain")

The original isolates almost all Bukkit block coupling behind one seam, which is what makes the
port tractable. Key structures (ported largely intact):

- **`AbstractBlocks` → `InitialBlocks`/`RealBlocks`/`SupportBlocks`** — the block-writing layer.
  Reimplementing this family against modern `ChunkAccess`/`BlockState` (Phase 1) isolates the
  ~300 algorithm files from the Bukkit block API. This is the linchpin.
- **`PlatMap`** — a 10×10 grid of chunks; the unit of city planning. Seed-deterministic (important
  for the multithreaded modern chunk pipeline).
- **`PlatLot`** subclasses (`RoadLot`, `BuildingLot`, `ConnectedLot`, `NatureLot`, …) — one chunk's
  worth of "what goes here" and how to generate it.
- **`Context/`** (`DataContext` subclasses) — decide which lots populate a PlatMap.
- **Provider pattern** (`Plugins/`) — pluggable strategies (`ShapeProvider`, `CoverProvider`,
  `OreProvider`, `TreeProvider`, `LootProvider`, `MaterialProvider`, `PasteProvider`, …), selected
  per world style/environment.
- **`Support/Odds`** — the deterministic RNG wrapper; prefer it over raw randomness.

In modern Minecraft this all gets wrapped in a codec-registered custom `ChunkGenerator`, exposed
as both a custom dimension (`cityworld:city`) and a world preset. The old Bukkit two-phase model
(`generateChunkData` + `BlockPopulator`) maps onto the staged chunk pipeline; see `PORTING.md`
Phases 3 and 5 for how, and for the neighbor-access and threading caveats.

## Reference port

`../MobHealth-Forge` is a completed NeoForge port of another Bukkit plugin by the same author. Its
Gradle setup, `mods.toml` template, config (`ModConfigSpec`), Brigadier commands, and permission
handling are the patterns this project copies.
