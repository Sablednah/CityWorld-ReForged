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
  `mc26.1` and `mc26.2` are checked out permanently at
  `../CityWorld-ReForged-worktrees/mc26.{1,2}`, each with a `tools` symlink back to this checkout's
  JDKs (`tools/` is git-ignored, so a worktree has none of its own). Build with an explicit
  `JAVA_HOME` — **1.21.11 needs JDK 21, 26.1+ needs JDK 25**:

  ```bash
  cd ../CityWorld-ReForged-worktrees/mc26.2
  JAVA_HOME="$PWD/tools/jdk25" PATH="$PWD/tools/jdk25/bin:$PATH" ./gradlew build
  ```

  **Why, and it is not just tidiness:** the owner often has a second Claude session working in this
  repo (the sablecraft.co.uk website one). Switching branches in the shared checkout rewrites its
  files underneath it — `WEBSITE.md` does not exist on the version branches at all, so a switch tries
  to delete it and aborts (or worse, lands mid-edit). Worktrees keep `master`'s tree still. They share
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
- **Kill the previous `runServer` before starting another.** A backgrounded one keeps port 25565, and
  the second run fails with `bind(..) failed: Address already in use` → `Failed to initialize server`
  → a crash report and an NPE in `overworld()` on shutdown. That reads like a code fault and isn't
  one. `pkill -f "gradlew runServer"`, then check the port is actually free before rerunning.
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
decoration across 13 world styles, on three Minecraft versions. It suppresses *most* vanilla
structures/decoration/carvers so CityWorld owns the chunk, with deliberate exceptions: strongholds,
trial chambers and ancient cities are placed (see PORTING.md), and vanilla biome features may decorate
wild land depending on `world.wildDecoration`.

**Verify changes with `scripts/selftest.sh`** — a headless dedicated server on a fixed seed that
checks ~88 things and writes a JSON report per Minecraft version. It exists because "looks right in
game" repeatedly disagreed with what the code did; several bugs in this project were a feature
silently doing nothing while looking exactly like working software.

## Reference port

`../MobHealth-Forge` is a completed NeoForge port of another Bukkit plugin by the same author. Its
Gradle setup, `mods.toml` template, config (`ModConfigSpec`), Brigadier commands, and permission
handling are the patterns this project copies. (It's a simple event-driven mod, so it has no
worldgen or registration examples.)
