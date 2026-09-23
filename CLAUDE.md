# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

A fork of **CityWorld** — originally a Bukkit/Spigot 1.14 plugin that procedurally generates worlds
full of cities, roads, buildings, mines, sewers, farms and nature — ported to a modern **NeoForge**
mod, and since v5.11.0 to **MinecraftForge on 1.20.1** as well. The port is built in place at the
repo root; the original Bukkit project was removed from the working tree and lives on in git history
as the reference implementation.

**Read `PORTING.md` first.** It is the living plan and the source of truth for decisions, progress,
verified API notes, and what to do next. Start at its "Resume here" section.

| | |
|---|---|
| Minecraft | **this checkout** 1.21.11 — six lines ship: 1.20.1, 1.21.1, 1.21.11, 26.1, 26.2, 26.3 |
| Loader | NeoForge 21.11.42 here; **MinecraftForge 47 on the 1.20.1 line only** |
| Java | 21 here — **17 on 1.20.x, 21 on the 1.21 lines, 25 on 26.1+** |
| Build | Gradle + ModDevGradle (`net.neoforged.moddev`; the 1.20.1 line adds its `legacyforge` addon) |
| Licence | **GPL-3.0-only** (see below — non-negotiable) |
| Branch | work happens on `master` (the `neoforge-port` branch was merged into it and deleted) |

## ▶ Where this is, and what's next (2026-09-23)

**v5.12.0 is released** — tagged, on GitHub, CurseForge (files 8957340–8957345) and Modrinth, and
deployed to all 11 fleet instances (`DEPLOYED-v5.12.0`). All six lines are on the same version for the
first time since 5.9.0. All six self-tests pass and `selftest.sh --compare` agrees on plan hashes.

**The release was one arc:** a structure from another mod now sits IN the land instead of on it. Read
PORTING.md's "Resume here" for the detail. The rules that came out of it, each paid for in a playtest
round:

- Every structure is **beard**, **carve**, or **neither**. Beard = fill up to the piece floor, for
  something standing on the ground. Carve = clear the box plus a halo, for something genuinely
  BURIED. Neither = for anything that levels itself (`ScatteredFeaturePiece`) or floats.
- **Never dig.** The pad never lowers ground below `seaLevel + 1`, and a standing structure's carve
  starts above `max(ground, sea)`. CityWorld floods whatever it plans under sea level, so a "hollow"
  becomes a moat and a cleared ocean column becomes a dry hole.
- **Aim at a building's BASE, not its roof.** Under overlapping pieces take the LOWEST floor. Taking
  the highest built terrain up to the roof of a 97-piece tower.
- **The blend reaches one chunk past the bounding box and no further** — that is the chunk pipeline's
  ceiling, not a preference. See `BLEND_CHUNKS`.
- **A halo is for a cavern.** Around something standing in the open it eats the landscape.

**The stall is FOUND and FIXED (2026-09-23 evening, unreleased).** It was never random: the owner's
logs put every 60-80 s `plan.build` on the SAME platmap (0,160) of the same seed, so it was
reproduced here with the owner's seed and a placement-only stand-in for Cataclysm's structure sets
(the real jar's SRG mixins cannot load in a named dev runtime — PORTING.md "The stall, found"). The
restored watchdog's stack named it in one run: `UrbanContext.fillOutBuilding` ignored `setLot`'s
result, and `setLot` refuses a reserved chunk — so the chunk stayed empty and every monotone path
through the reservation re-entered it, constructing a fresh lot (256 columns of octave noise) per
visit. Exponential in the reservation's size. A/B on the same platmap: **37,669 ms → 1,040 ms**.
Second fix in the same commit: `getPlatMap` no longer plans inside `ConcurrentHashMap`'s bin lock
(a 71 s plan there held a worker wanting a DIFFERENT platmap for 62 s, and the server thread with
it). The instrument was stripped again before committing — `grep -r Timings src` reads 0.

**⚠ The lesson that outlives it: a stall that looks random is a platmap.** Ask which chunk, and
whether the same chunk stalls twice, before reasoning about causes. Two code diagnoses were wrong;
the instrument plus the owner's own log lines were right in one run each.

**NEXT: the forecast plan** — PORTING.md "▶ PLAN (2026-09-23 night)". `StructureForecast` (spike on
master) computes vanilla's exact `StructureStart` from the planner before any chunk exists, proved
5/5 against stored starts (compare footprint, floor and piece count, not maxY — reload drops a
terrain-matching piece's box growth). It answers all three of the owner's asks: reserve the real
footprint instead of a clearance square, pad past the 8-chunk pipeline limit, and shave the acropolis
in the plan. Superseded: reservation-driven levelling.** A structure larger than vanilla's 128-block bound (Cataclysm's
frosted prison is ~174) cannot be fully bearded: a chunk can only resolve a start whose ORIGIN chunk
is within 8, and vanilla has the same limit. `StructureReservations` already computes WHERE a
structure will be, deterministically, at any distance and with no chunk loading — levelling the
reserved area toward the structure's projected start height would blend the far edge too. Accepted
as-is for 5.12.0 (owner's call).

## Licence — important

Upstream CityWorld is **GPL-3**, so this port is a derivative work and **must stay GPL-3**.
GPL-3 → MIT is not permitted. Don't "helpfully" relabel it. (An early `mod_license=MIT` was an
unverified assumption and has been corrected.)

A useful consequence: because we're GPL-3 and Bukkit's API is GPL-3, we **may vendor** Bukkit's
`SimplexNoiseGenerator`/`SimplexOctaveGenerator` (with attribution) to preserve CityWorld's exact
terrain shape. See `PORTING.md`.

## Build & run

There is **no system Java**; the JDKs live in `./tools/` (git-ignored) — `jdk17`, `jdk21` and `jdk25`.
**This checkout (1.21.11) needs JDK 21**; the worktrees need their own (17 for 1.20.1, 25 for 26.x), and
they reach these same JDKs through a `tools` symlink:

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
  `mc1.20.1`, `mc1.21.1`, `mc26.1`, `mc26.2` and `mc26.3` are checked out permanently at
  `../CityWorld-ReForged-worktrees/{mc1.20.1,mc1.21.1,mc26.1,mc26.2,mc26.3}`, each with a `tools` symlink back to this
  checkout's JDKs (`tools/` is git-ignored, so a worktree has none of its own). Build with an explicit
  `JAVA_HOME` — **1.20.x needs JDK 17, the 1.21 lines need JDK 21, 26.1+ needs JDK 25**. A new Minecraft line is a worktree
  branched from the previous one plus `gradle.properties`/MDG bumps, then compile-fix against the
  decompiled sources; **26.3 was not a one-liner** (PORTING.md "26.3 port — what actually moved": the
  density engine, `ConfiguredFeature`, `buildTerrain`, concurrent registry loading, loot-table schema). `compat/Material.java` is generated per
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
- **⚠ The same caution applies to DIAGNOSTIC code, not just shutdown code.** `Support/Timings` was
  off unless `-Dcityworld.timing=true`, cost nothing when off, and only ever logged — and it was still
  stripped before release, on the owner's call: *"we got but by one before - and although its just
  timeing - no need to trigger people or bots looking for 'hidden' code."* A reviewer greps the shipped
  bytecode, not the flag guarding it. Restore it from history for a debugging session, strip it again
  before shipping, and verify with a detector **proved on a positive first** — the pre-strip jar reads
  1 `Timings` class, a stripped one reads 0.
- **⚠ A detector that has never fired is worth nothing, and a polling one can lie by timing.** The
  stall watchdog's first run produced **zero** dumps with nothing broken: it polled every 2 s while the
  phase it watched took ~800 ms locally, so every phase began and ended inside one poll gap. Only a
  tunable interval (25 ms) proved it worked. Same family as the `strings`-based halt check that read 0
  for everything.
- **⚠ "Measured in game" is not a measurement.** `StructureReservations`' memo javadoc claimed the memo
  fixed the minute-long stall because the symptom seemed to go away once. It had not: proper timing put
  reservation at 2125 ms across 492,863 calls against a single 71,557 ms `populateMap`. A false claim
  in a comment is worse than no comment. Always A/B against a control on the same seed.
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
- **Fleet deploy: `scripts/deploy-fleet.sh`** (`--dry-run` first). Twelve CurseForge instances carry a
  CityWorld jar — five on 1.21.11 (`CityWork-ReForged`, `MobHealth - Forge`, `Neoforge 1.21.11 - sci
  fi/wasteland`, `Standards`), `26.1.2`, three on 26.2 (`26.2`, `26.2.test`, `BoP+Cityworld`), `26.3`, `1.21.1`
  and **`1.20.1  Forge`** (⚠ TWO spaces in that folder name — `--only` matches the exact basename, so
  `--only 1.20.1` silently matches nothing and reports "no instances carry CityWorld"). The
  fleet is whatever `Instances/*/mods` already holds a `cityworld-*.jar` or `DEPLOYED-*` stamp, so a
  brand-new instance is invisible until one jar is copied in by hand; the
  script reads each instance's Minecraft version from `minecraftinstance.json`, picks the newest
  built jar for it across master + the five worktrees (`--version X.Y.Z` for a release, `--build` to
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
  gradle.properties, never a generated `mods.toml`**. On the **1.20.1 Forge** line that template is
  `META-INF/mods.toml` instead, and its schema differs: `mandatory=true/false`, not `type="required"`.
- Mod id `cityworld`; `@Mod` entrypoint `me.daddychurchill.CityWorld.CityWorldMod`.
- **⚠ The 1.20.1 Forge line breaks five rules the NeoForge lines let you assume** (full account in
  PORTING.md "1.20.1 **Forge**"; released as v5.11.0):
  1. **`pack.mcmeta` is MANDATORY.** Forge injects no pack metadata, so without that file Minecraft
     discards the **entire mod datapack** — every tag empty, no world preset, no dimension. NeoForge
     synthesises it, so no other branch ships one. One missing file, 21 self-test failures.
  2. **Ship `build/libs`, never `build/devlibs`.** legacyforge writes **same-named jars** to both and
     only `build/libs` is reobfuscated (check the SRG count, not the filename). The dev-mapped one
     installs and loads fine, then crashes at every access-transformed call site.
  3. **Verify an override of a vanilla method by its SRG name**, not the readable one —
     `getScrollbarPosition` ships as `m_5756_`. Grepping the readable name finds nothing and looks
     exactly like "the fix never made it into the build". Map it via
     `build/moddev/artifacts/intermediateToNamed.srg`, and `javap -p -cp .` — the `-cp .` is not optional.
  4. **`scripts/selftest.sh` writes `run/eula.txt` itself.** NeoForge's dev `runServer` accepts the
     EULA; the legacyforge path does not, and a clean checkout has no `run/` at all.
  5. **The `@Mod` class needs a no-arg constructor**; FML injects nothing. Get the bus and container
     from `FMLJavaModLoadingContext.get()` / `ModLoadingContext.get()`.
- **⚠ The workflows live ONLY on `master`, deliberately — so no push to a version branch ever runs CI.**
  Every branch used to carry its own copies, cut at different times and never synced, and they had
  drifted badly: `mc26.1` fired on its own pushes while `mc1.20.1` and `mc1.21.1` did not, `mc26.2` and
  `mc26.3` had no workflow at all, and the version branches' `curseforge.yml`/`modrinth.yml` were still
  the pre-fix copies that hardcoded the NeoForge loader. Deleted from every version branch on
  2026-09-21 (owner's call: *"cant drift if they dont exist"*).
  **The gate is a `master` push**, whose matrix checks out each branch's head (`actions/checkout` with
  `ref: ${{ matrix.branch }}`) — so coverage is unchanged. **Push the version branch FIRST, then
  master**, or the gate tests a tree you are not releasing. Note `paths-ignore: ['**.md', …]`: a
  docs-only commit deliberately fires nothing.
- **The publish scripts key their loader off the Minecraft version** (`1.20.* -> Forge/forge/Java 17`,
  else NeoForge). Both once hardcoded NeoForge, which was invisible until the first non-NeoForge jar.
  **Before publishing a jar of a new KIND** (new loader, Java, or channel), read the uploader for
  values it *assumes* rather than derives — and check the workflow log's
  `Minecraft <v> = <id>, <Loader> = <id>` line, because that lookup needs the API token and cannot be
  tested locally.

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
decoration across 13 world styles, on six Minecraft versions across two loaders (1.20.1 on
MinecraftForge; 1.21.1, 1.21.11, 26.1, 26.2, 26.3 on NeoForge). It suppresses *most* vanilla
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
