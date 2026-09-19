# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A fork of **CityWorld** — originally a Bukkit/Spigot 1.14 plugin that procedurally generates worlds
full of cities, roads, buildings, mines, sewers, farms and nature — being **ported to a modern
NeoForge mod**. The port is built in place at the repo root; the original Bukkit project was removed
from the working tree and lives on in git history as the reference implementation.

**Read `PORTING.md` first.** It is the living plan and the source of truth for decisions, progress,
verified API notes, and what to do next. Start at its "Resume here" section.

| | |
|---|---|
| Minecraft | 1.21.11 |
| Loader | NeoForge 21.11.42 |
| Java | 21 |
| Build | Gradle + ModDevGradle (`net.neoforged.moddev`) |
| Licence | **GPL-3.0-only** (see below — non-negotiable) |
| Branch | work happens on `master` (the `neoforge-port` branch was merged into it and deleted) |

## Licence — important

Upstream CityWorld is **GPL-3**, so this port is a derivative work and **must stay GPL-3**.
GPL-3 → MIT is not permitted. Don't "helpfully" relabel it. (An early `mod_license=MIT` was an
unverified assumption and has been corrected.)

A useful consequence: because we're GPL-3 and Bukkit's API is GPL-3, we **may vendor** Bukkit's
`SimplexNoiseGenerator`/`SimplexOctaveGenerator` (with attribution) to preserve CityWorld's exact
terrain shape. See `PORTING.md`.

## Build & run

Requires a JDK 21. There is **no system Java**; a JDK lives at `./tools/jdk21` (git-ignored):

```bash
export JAVA_HOME="$PWD/tools/jdk21"
export PATH="$JAVA_HOME/bin:$PATH"

./gradlew compileJava   # fast inner loop while porting
./gradlew build         # -> build/libs/cityworld-<version>.jar
./gradlew runServer     # dev dedicated server
./gradlew runClient     # dev client (needs a display)
```

- `./deploy.sh` builds and copies the jar into the CurseForge test instance
  (`CityWork-ReForged`); it sets `JAVA_HOME` itself.
- The dev server's `run/server.properties` is already set to `level-type=cityworld\:city`, so
  `runServer` generates using our generator. **Delete `run/world` to force regeneration.**
- Gradle can't forward piped stdin to the server console — to verify in-world behaviour, register a
  temporary `ServerStartedEvent` listener that logs what you need, rather than piping commands.
- **Build the version branches in their worktrees, don't switch branches here.** `master` is 1.21.11;
  `mc26.1`, `mc26.2` and `mc1.21.1` are checked out permanently at
  `../CityWorld-ReForged-worktrees/{mc26.1,mc26.2,mc1.21.1}`, each with a `tools` symlink back to this
  checkout's JDKs (`tools/` is git-ignored, so a worktree has none of its own). Build with an explicit
  `JAVA_HOME` — **the 1.21 lines need JDK 21, 26.1+ needs JDK 25**. `compat/Material.java` is generated per
  branch: a cherry-pick that conflicts on it is resolved by taking the branch's copy and re-running
  `scripts/gen_material.py` there (the template edits live in the script):

  ```bash
  cd ../CityWorld-ReForged-worktrees/mc26.2
  JAVA_HOME="$PWD/tools/jdk25" PATH="$PWD/tools/jdk25/bin:$PATH" ./gradlew build
  ```

  **Why, and it is not just tidiness:** the owner often has a second Claude session working in this
  repo (the sablecraft.co.uk website one). Switching branches in the shared checkout rewrites its
  files underneath it (when `WEBSITE.md` was tracked on master only, a switch tried to delete it and
  aborted — or worse, landed mid-edit). `WEBSITE.md` is now **git-ignored and local-only**: it is the
  note-passing file between this session and the website one, so read and write it freely, never
  commit it. Worktrees keep `master`'s tree still. They share
  the Gradle cache, so a build there is no slower after the first.
- **The ship loop** (used ~30 times in the furniture arc, every trap below hit at least once):
  commit on master → `git cherry-pick <EXPLICIT SHA>` in the worktree (**never `$(git rev-parse
  HEAD)` in a chain — it resolves to the worktree's own HEAD**; hit six times) → **one commit per
  cherry-pick invocation** (multi-commit picks stall the sequencer) → **verify `git log` shows your
  sha before building** (a `| tail -1` once masked a conflict through three "successful" deploys) →
  PALETTES/PORTING/CHANGELOG conflicts resolve `--ours` (docs live on master) → build → deploy →
  `DEPLOYED-$(git log --format=%h -1)` stamp → selftest. F3 shows a jar mtime stamp in-game, so
  "is the game on the new jar" is checkable at a glance.
- **Debugging worldgen: measure, don't hypothesize.** `-Dcityworld.diagnostics=true` sweeps every
  tag pool at startup and shouts empties (the classic silent-fallback bug class);
  `-Dcityworld.probe=<chunkX>,<chunkZ>` (via `JAVA_TOOL_OPTIONS` on `runServer`, seed pinned in
  `run/server.properties`, `rm -rf run/world`) force-generates a region, dumps per-layer block
  tallies + furnishing traces, and halts. The probe solved in two runs what four patch-and-playtest
  rounds could not. Verify fixes by re-probe BEFORE deploying.
  **`-Dcityworld.watch=<x>,<y>,<z>`** (world coords, alongside the probe) logs every write to that cell
  with the CityWorld stack behind it — "who draws this block?" answered in one run (the line-of-blocks
  building was `drawInteriorColumns` through a CENTER stairwell; zero WATCH lines after the fix).
- **⚠ Read `PROBE: dimension <id> generator <class>` BEFORE believing any number from it.**
  Until 2026-09-17 the world presets defined **vanilla** realms (CityWorld's were swapped in by `CityWorldRealms`
  from the Customize toggles, which a dedicated server never goes through), so `runServer` gave a vanilla Nether
  and End — and with BoP installed the wrong world still shows crimson forest and withered abyss, so it looks
  convincingly right. Four probe runs measured the wrong dimension on 2026-09-16 and "proved" a fix using blocks
  vanilla's own surface rules had placed; it was committed, pushed and deployed before the generator line was
  read. The presets now ship CityWorld's Nether and End, so a **fresh** `run/world` has them — one made before
  that, or with its realms switched back, does not, and the line is still the first thing to read.
  `CityWorldChunkGenerator` = ours, `NoiseBasedChunkGenerator` = vanilla (PORTING.md has a
  `run/world/datapacks/` recipe for forcing either), and `-Dcityworld.probe=find:biome:<id>` checks a biome is present before you call its feature
  broken. Three more probe traps, each of which cost a run: the **server watchdog kills any sweep over 60s**
  (it runs as one long tick — set `max-tick-time=-1` in the gitignored `run/server.properties`); a roofed
  dimension's `WORLD_SURFACE` is the *ceiling*; and a small sweep sits in ONE biome, so zero there means
  "wrong place", not "broken".
- **A slice-and-join edit of a long doc can silently drop the rest of it.** `s = s[:start] + new + tail`
  with the wrong `tail` truncated PORTING.md from 4,297 lines to 203 for a day (2026-09-17; `git diff --stat`
  showed only the intended hunk because the drop was one big deletion). After any such edit, `wc -l` before and
  after, and `grep` for a heading you know sits near the end.
- **Shape questions need a picture, and there are two that need no client.** `-Dcityworld.probe=survey:end`
  (with `-Dcityworld.probe.dim=minecraft:the_end`) prints the End's *plan* as a chunk map — void, island, road,
  structure — for 10,000 chunks in 5 s without generating one; it found 40-chunk roads across the void and 685
  farms on end stone in its first two runs. `scripts/region_render.py <region dir> x0 x1 y0 y1 z0 z1 out.png`
  renders a generated box as a plan plus a side elevation (kill the server first so the region is flushed;
  1.21.11's End is `run/world/DIM1/region`, 26.x's `dimensions/minecraft/the_end/region`). Block tallies said the
  first End was fine; one screenshot said it was chunk-square slabs. And when "the map says city, the world says
  empty", `-Dcityworld.probe.radius=N` now prints `PLANvWORLD` — per swept chunk, the planned lot class and how
  many blocks stand above the street — which is the disagreement itself, chunk by chunk. The owner's F3 line
  (`context NeighborhoodContext` over `EndNatureLot nature 100%`) diagnosed the levelled-empties bug in one
  screenshot; ask for F3 before hypothesising.
- **Overriding another mod's datapack file takes two things, and each fails differently.**
  (1) `ordering="AFTER"` on an optional dependency in the `neoforge.mods.toml` template — a mod's pack only
  wins a file conflict if it sorts after the mod it overrides; without it the override is a **silent
  no-op** (measured identical block counts either way). (2) A
  `"neoforge:conditions": [{"type": "neoforge:mod_loaded", …}]` guard — the file references that mod's
  objects, and when the mod is absent the reference is unbound, which fails the **whole registry load** and
  **stops the server starting at all** (`Unbound values in registry …`). A dangling *feature* reference is
  fatal; a dangling *tag* entry is merely dropped. The self-test caught this on the two branches that have
  no BoP in `run/mods` — which is exactly why every branch gets tested, not one.
- **A quiet worldgen failure looks like scarcity, and the plan hash does not see it.** `ShapeProvider.populateLots`
  catches every exception and logs `populateLots FAILED`; the platmap then generates as nature. A coin-flip pool
  hook called twice (`garageDoorPool()`) handed `MaterialTags.pick` a null tag, and for half a day every
  industrial district was silently dropped while the plan — measured before population — hashed identical. Grep
  any probe or self-test log for `FAILED` before believing "there is little of X"; `MaterialTags.resolve(null)`
  is now an empty pool, and a hook that rolls odds must be called once.
- **Lot connection keys are positional, and a replaced lot's neighbours keep matching it.** `ConnectedLot`'s key
  comes from the chunk position; flood-filled copies take the source's key; `CivilizedContext.validateMap`
  swaps a `trulyIsolated` STRUCTURE with an isolated neighbour for a fresh backfill lot — which is born with
  the same positional key the copies carry, so a warehouse counted a silo battery as its own wing and drew no
  wall. `isConnected` now also requires the same kind of lot; a lot meant to cluster is not `trulyIsolated`.
- **⚠ Nothing in the shipped jar may be able to stop a server — not even behind a developer flag.**
  CurseForge **rejected 5.7.0 and 5.8.0**: "Please remove any function that shuts the Minecraft server
  down." The self-test harness and the chunk probe each ended in `server.halt(false)`, dormant unless
  `-Dcityworld.selftest=true` / `-Dcityworld.probe=` was set — but a reviewer greps the shipped bytecode,
  not the flag guarding it, and they are right to. `v5.8.1` removed both: ending a headless run now
  belongs to the script that starts it (the harness logs `SELFTEST: complete`, `scripts/selftest.sh`
  waits for it and kills the run's **process group** — job control, never a `pkill` pattern, and never
  the gradle pid alone or you orphan a server that holds port 25599 into the *next* run).
  Every tag from 5.5.0 carried the same two calls and passed review: that is **volunteer moderators with
  differing thoroughness, not a rule that changed**, so *a past approval is never evidence that something
  is allowed*. And verify a claim like this **against bytecode with a detector proved on a
  known-positive first** (`javap -p -c`, grep `\.halt:|System\.exit:`; a real 5.8.0 jar shows 4 hits —
  **`build/libs/cityworld-5.8.0+mc26.1.2.jar` from Sep 14; the 1.21.11 "5.8.0" jar there was rebuilt after
  the fix and reads 0, so it is NOT a control** — 5.8.1+ none; scan a whole jar with one `javap -cp . <all
  classes>`, not one javap per class, which takes over ten minutes). A `strings`-based check reported 0 for everything — including methods that were certainly
  there — because constant-pool entries sit adjacent. **Never trust a zero from a detector that has never
  produced a positive.**
- **Never compile in a checkout whose dev server or self-test is running.** `runSelfTest`/`runServer`
  run off that checkout's `build/classes`; a `compileJava` mid-run replaces class files under the JVM
  and the harness dies with `NoClassDefFoundError: …CityWorldSelfTest$1`. That is a race, not a code
  fault. Master and each worktree have separate build dirs, so build *another* checkout meanwhile.
- **Furniture mods are three families** (see `PALETTES.md`): Macaw's + Refurbished (tags/data map
  generated by `scripts/gen_furniture_tags.py` — re-run it when THOSE mods update) and Fantasy's
  Furniture sets, which share one vocabulary (`src/main/resources/cityworld/furniture_vocabulary/`)
  and are recognised from the block registry at runtime by `Support/FurnitureSets` — a new set needs
  NO regen or rebuild. Multi-block pieces place through `SupportBlocks.setFurniture` only; both plain
  `setBlock` overloads route declared furniture there, so never bypass it for pool furniture.
- **Two placement rules that each cost a playtest round:** (1) anything drawn between `claimStairs`
  and `drawStairs` (columns, `lightInterior`) must consult `isStairClaimed` — the stairs cut later;
  (2) a "wall" for art/sconces/shelves is `isWallBacking` (full cube, sturdy, not glass, not pooled),
  checked behind EVERY cell of a wide piece, and blocks go before entities (a painting is invisible to
  `isEmpty`, so a chandelier chain went through one).
- **Fleet deploy: `scripts/deploy-fleet.sh`** (`--dry-run` first). Ten CurseForge instances carry a
  CityWorld jar — five on 1.21.11 (`CityWork-ReForged`, `MobHealth - Forge`, `Neoforge 1.21.11 - sci
  fi/wasteland`, `Standards`), `26.1.2`, three on 26.2 (`26.2`, `26.2.test`, `BoP+Cityworld`) and `1.21.1`. The
  fleet is whatever `Instances/*/mods` already holds a `cityworld-*.jar` or `DEPLOYED-*` stamp; the
  script reads each instance's Minecraft version from `minecraftinstance.json`, picks the newest
  built jar for it across master + the three worktrees (`--version X.Y.Z` for a release, `--build` to
  build them all first), and stamps `DEPLOYED-<sha|vX.Y.Z>` (vX.Y.Z when the jar's Build-Commit is
  the tag or a "Bump to X.Y.Z" commit). A running game locks its jar ("Permission denied") — the
  script reports SKIPPED and carries on; rerun for that one after the game is closed.
- **Kill the previous `runServer` before starting another.** A backgrounded one keeps the dev port —
  **25599**, not 25565 (`run/server.properties`; the self-test moved off 25565 because it collided
  with another mod's dev server, override with `CITYWORLD_SELFTEST_PORT`). The second run then fails
  with `bind(..) failed: Address already in use` → `Failed to initialize server` → a crash report and
  an NPE in `overworld()` on shutdown. That reads like a code fault and isn't one. Kill it **by PID**
  and confirm the process is gone — `pkill -f "gradlew runServer"` returns success without killing
  the JVM, which then holds `run/world/session.lock` and the next run dies with
  `DirectoryLock$LockException: already locked` instead. Never match a `pkill`/`pgrep` pattern that
  appears in your own command line: it kills your own shell.
- Versions/metadata live in `gradle.properties` and expand into
  `src/main/templates/META-INF/neoforge.mods.toml` at build time — **edit the template and
  gradle.properties, never a generated `mods.toml`**.
- Mod id `cityworld`; `@Mod` entrypoint `me.daddychurchill.CityWorld.CityWorldMod`.

## Reading the original Bukkit source

It is **not in the working tree**. Two ways in:

```bash
# One file, from the last pre-port commit (stable sha; 9827bcf removed the tree):
git show 251078e:src/me/daddychurchill/CityWorld/Support/AbstractBlocks.java

# Or restore the whole reference tree (self-bootstrapping; finds the commit itself):
python3 scripts/gen_material.py     # -> /tmp/cityworld-portgen/bukkit-ref/src/...
```

That script also extracts decompiled Minecraft sources to `/tmp/cityworld-portgen/mcsrc/`. Grep them
to **verify API signatures instead of guessing** — 1.21.11 renamed and reshaped things (see the
"1.21.11 API notes" in `PORTING.md`; e.g. `ResourceLocation` is now `Identifier`).

## Conventions specific to this port

- **Package tree is preserved**: ported code keeps the original `me.daddychurchill.CityWorld.*`
  packages (minimizes churn across ~300 files, keeps attribution). The Gradle group id is lowercase
  `me.daddychurchill.cityworld` — that's fine, it needn't match.
- **Port by mechanical transform, not retyping.** Large files (e.g. the 768-line `AbstractBlocks`)
  were ported by scripting the import/type swaps and then fixing residuals against the compiler.
  Preserve the original logic and comments; keep diffs reviewable.
- **`compat/` holds the Bukkit shims** — the trick that makes this tractable:
  - `Material` — interned wrapper over `Block`/`BlockState` **and** `Item`. Its constant block is
    **generated by `scripts/gen_material.py` — do not hand-edit**; change the generator and re-run.
  - `BlockFace` → vanilla `Direction`; `WoodSpecies` → Bukkit `TreeSpecies`.
  - `Block` → Bukkit's live positioned block (`LevelAccessor` + `BlockPos`).
  - Mappings worth remembering: Bukkit `BlockData` ≡ modern `BlockState`; Bukkit "apply physics" ≡
    vanilla update flags (`UPDATE_ALL` vs `UPDATE_CLIENTS`).

## Architecture being ported

The original funnels nearly all Bukkit block coupling through one seam, which is what makes this
possible:

- **`AbstractBlocks`** → **`InitialBlocks`** (generation side, on `ChunkAccess`) — **done**.
- **`SupportBlocks`** → `RealBlocks`/`RelativeBlocks`/`WorldBlocks`/`CornerBlocks` (decoration side,
  on `LevelAccessor`) — **done**. Rests on one abstract method, `getActualBlock(x,y,z)`.
- **`PlatMap`** — a 10×10 grid of chunks; the unit of city planning. Seed-deterministic (important
  for the multithreaded modern chunk pipeline).
- **`PlatLot`** subclasses (`RoadLot`, `BuildingLot`, `ConnectedLot`, `NatureLot`, …) — one chunk's
  worth of "what goes here".
- **`Context/`** (`DataContext` subclasses) — decide which lots populate a PlatMap.
- **Provider pattern** (`Plugins/`) — `ShapeProvider`, `CoverProvider`, `OreProvider`, `TreeProvider`,
  `LootProvider`, `MaterialProvider`, `PasteProvider`, … selected per world style/environment.
- **`Support/Odds`** — deterministic RNG wrapper; prefer it over raw randomness.

**⚠ The brain is one mutually-recursive cycle** (`ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔
Plugins ↔ Rooms ↔ Clipboard`) — measured: every seed yields the same 316-file / ~40k-line closure.
**There is no "terrain-only" slice.** Port it as a mass transform in waves plus shims. The cycle's
edges are often thin (single method signatures), so they can be stubbed to break it.

Modern worldgen wraps this in a codec-registered `ChunkGenerator` (`worldgen/CityWorldChunkGenerator`,
registered `cityworld:city`), exposed as both a dimension and a world preset. **The port is complete
and the brain is wired in** — it generates real CityWorld terrain, cities, interiors, mines, caves and
decoration across 13 world styles, on four Minecraft versions (1.21.1, 1.21.11, 26.1, 26.2). It suppresses *most* vanilla
structures/decoration/carvers so CityWorld owns the chunk, with deliberate exceptions: strongholds,
trial chambers and ancient cities are placed (see PORTING.md), and vanilla biome features may decorate
wild land depending on `world.wildDecoration`.

**The other realms (5.9.0) invert that.** The ruined Nether is the overworld's twin (`twin_of`, same plan,
`environment: nether`, decayed harder) on CityWorld terrain. **The End is vanilla's terrain everywhere** —
`vanillaEnd()` (a real `NoiseBasedChunkGenerator` inside ours) fills every chunk, and CityWorld only *reports*
those heights to the planner (`worldgen/EndTerrain`, an exact re-implementation of `NoiseChunk`'s
interpolation, proved column-for-column against `getBaseHeight` by the self-test) so upstream's "buildable =
flat at street level" rule puts cities on island tops. `ShapeProvider_TheEnd` shapes nothing: it terraces the
ground only under built lots plus a 12-block apron, keeps island roads (`keepsIsolatedRoads`), thins by a
regional noise (`settled`; retune with `-Dcityworld.end.settled=<n>` + `survey:end`), and answers no to every
shaft/cave. Its biomes come from the real `TheEndBiomeSource` through `EndTerrain.sampler()` — that is the
only way TerraBlender/BoP End biomes exist, because TerraBlender mixes into that class. **BoP + TerraBlender
are installed only in the `mc26.2` worktree's `run/mods`** — verify any mod interaction there. All 13 world
presets ship both realms by default; the Customize toggles switch either back to vanilla.

**Verify changes with `scripts/selftest.sh`** — a headless dedicated server on a fixed seed that
checks ~88 things and writes a JSON report per Minecraft version. It exists because "looks right in
game" repeatedly disagreed with what the code did; several bugs in this project were a feature
silently doing nothing while looking exactly like working software.

## Reference port

`../MobHealth-Forge` is a completed NeoForge port of another Bukkit plugin by the same author. Its
Gradle setup, `mods.toml` template, config (`ModConfigSpec`), Brigadier commands, and permission
handling are the patterns this project copies. (It's a simple event-driven mod, so it has no
worldgen or registration examples.)
