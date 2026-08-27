# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here (next task)

**Publishing is DONE — `v5.0.2` shipped everywhere** (GitHub release with jar, the owner's CurseForge
upload, and `sablecraft.co.uk/cityworld-reforged/` live). The port branch is merged: **work happens on
`master`**. All content arcs are done, verified and deployed.

**The cross-version arc: 1.21.11, 26.1 and 26.2 all build, run and generate cities.** Minecraft moved
to calendar versioning with quarterly drops, so this is a treadmill, not a one-off port. Stages 1 and
2 are done; **stage 3 — choosing the steady state — is what remains.**

1. **Harden on 1.21.11 first — data-driven palettes.** ✅ **DONE (2026-08, `5.0.3`)** — see the dated
   block below. This turned out to matter more than expected: because palettes resolve from block
   tags, 26.2 deleting 144 dyed block fields did not touch them at all.
2. **Port to 26.1 and 26.2 on branches, and measure.** ✅ **DONE (2026-08-16/17)** — branches
   **`mc26.1`** and **`mc26.2`**. See "The measured 26.1 delta" and "The measured 26.2 delta" below.
3. **▶▶ NEXT: pick the steady state.** Two deltas are now in hand and they point in opposite
   directions — 26.1 cost 12 lines, 26.2 cost a block-model rewrite. The recommendation and the
   evidence for it are in "Stage 3: what the two deltas say" below. **Nothing is committed to yet.**

**Where the versions live.** One branch per version for now: `master` = 1.21.11, `mc26.1` = 26.1.2,
`mc26.2` = 26.2. Jars carry their target (`cityworld-5.0.3+mc26.2.jar`); the version inside
`neoforge.mods.toml` stays a plain `5.0.3`. Documentation (this file) is maintained on `master` and
the version branches carry code only, so the write-up does not have to be merged three ways.

**`compat/Material.java` was the predicted fragility, and the prediction was right — but the defence
held.** 691 constants bound at compile time to vanilla `Blocks`/`Items` fields, feeding 3,096 call
sites. 26.1 touched none of them. 26.2 broke **145**. Because the file is *generated*, the entire
repair was teaching `scripts/gen_material.py` new resolution rules; not one of the 3,096 call sites
changed. That is the strongest argument in the whole arc for keeping generated code generated.

## ▶ Next up (queued 2026-08-17)

In rough priority order. **#1 and #2 are DONE (2026-08-27) and the biome half of #3 with them — see
"Caves, structures and 3D biomes" below.** #3's decorative half, #4 and #5 remain.

### 1. ~~⚠ Vanilla structures never generate — including strongholds~~ **DONE (2026-08-27)**

`CityWorldChunkGenerator.createState` returns `ChunkGeneratorStructureState.createForFlat(…,
Stream.empty())`, so **no vanilla structure is ever placed**. That is deliberate for villages and
mineshafts — CityWorld builds its own — but it takes strongholds with it, and **no stronghold means
no End portal**. Eyes of ender have nothing to find, so as far as I can tell a CityWorld world cannot
reach the End by normal play. *Read from the code, not yet confirmed in-game — verify before acting
on it.*

Worth deciding as a gameplay question, not just a feature: does a CityWorld world want to be
completable? If yes, the minimum is placing strongholds somewhere sensible and making
`findNearestMapStructure` agree with where they went, or eyes will point at nothing.

### 2. ~~"Put a structure here" — ancient cities, trial chambers, strongholds~~ **DONE (2026-08-27)**

**Researched 2026-08-17 against the 26.2 data — the answers are more encouraging than expected.**

**Suppression can be selective, easily.** `createState` passes `Stream.empty()` to
`ChunkGeneratorStructureState.createForFlat`, and that argument is a stream of `StructureSet`
holders — pass a *filtered* stream and exactly those come back. Vanilla even does the hard part:
both factory methods drop any set whose biomes the biome source cannot produce
(`hasBiomesForStructureSet`), so unavailable structures exclude themselves with no special-casing.

**The placement conditions, from `data/minecraft/worldgen/structure_set/` in 26.2:**

| | Placement | Biome requirement (`tags/worldgen/biome/has_structure/`) |
|---|---|---|
| Stronghold | `concentric_rings` — count 128, distance 32, spread 3 | `#minecraft:is_overworld` — *any* overworld biome |
| Trial chambers | `random_spread` — spacing 34, separation 12 | a long list of ordinary **surface** biomes |
| Ancient city | `random_spread` — spacing 24, separation 8 | **`minecraft:deep_dark` only** |

So **strongholds and trial chambers need no cave biome and would work today** — the surface biomes
CityWorld emits already satisfy both. **Ancient cities cannot**, and will be silently filtered out
until the biome source can emit `deep_dark` (see #3).

**Geodes are not structures.** `amethyst_geode` is a *placed feature* (underground decoration), which
is why they already appear in CityWorld worlds while strongholds do not: we run vanilla decoration on
nature chunks but zero the structure state. Features and structures are separate pipelines — ores
arrive the same way geodes do. Worth keeping straight, since "I can see geodes" naturally reads as
"structures work".

**⚠ The wrinkle, so this doesn't look like a five-minute change.** `createForFlat` passes `0L` as the
concentric-rings seed where `createForNormal` passes the level seed — and that seed is precisely what
positions strongholds. Re-enabling them through the current call would put every CityWorld world's
strongholds in *identical* places. The constructor taking both seeds is **private**, so the options
are `createForNormal` (enables everything the biome source supports, not a chosen subset) or an
access transformer. The selective part is trivial; the correctly-seeded part is the actual work.

The owner's idea, and it stacks on top of #1: let the planner deliberately place vanilla underground
structures, the way it already places its own vaults and mines. Ancient City and Trial Chambers are
jigsaw structures, so this means driving the jigsaw generator at a chosen position rather than
copying a template.

The eye-of-ender caveat the owner raised is real and bigger than it looks: vanilla places strongholds
on a fixed ring pattern and the eye asks the *structure placement system* where they are. A
hand-placed stronghold that the placement system does not know about is a stronghold the eye cannot
find.

### 3. Decorative caves as a pool, not just lush — **half done (2026-08-27)**

**The blocker described below is gone: the biome source is 3D and emits real cave biomes.** What
remains is the *decoration* half — running vanilla's `UNDERGROUND_DECORATION` step so those biomes
actually grow moss and sculk, and moving `CaveRegions`' pool into a datapack so a modded cave biome
slots in without a code change. See the dated block below for why the decoration half is now nearly
free. **The analysis below is kept for its research value but its conclusion is superseded.**

Generalise the existing lush-cave patch mechanic into **one decorative cave type drawn from a pool**:
lush, **sulfur** (26.2's sulfur caves, with sulfur dripstone), **deep-dark / sculk** (ancient-city
flavour without necessarily the city), and room for modded and future types. The owner explicitly
wants this data-driven enough that a new cave type — vanilla or modded — can slot in without a code
change. Alex's Caves is the obvious modded case.

**⚠ The blocker under this, #1 and #2 alike: the biome source emits no cave biomes and cannot.**
Verified: the biomes it can produce are all *surface* biomes — no `DEEP_DARK`, no `LUSH_CAVES`, no
`DRIPSTONE_CAVES`. And `createBiomes` classifies **per column (2D)** — "classify once per (quartX,
quartZ) and reuse down the column" — so biome cannot vary with depth at all. Today's lush-cave
patches are decorative *block placement*, not the lush caves biome.

Consequences, and they are the crux of this whole group of ideas:

- **Ancient City cannot place**, because it is gated on `deep_dark` (the owner spotted this).
- Anything biome-driven underground is unavailable: sculk spread, warden spawning, cave ambience.
- **Modded cave mods that define their own biomes (Alex's Caves) will not appear either**, for the
  same reason — so this is the same problem as the worldgen half of #5.

So "decorative cave pool" splits into two very different jobs: *decorative blocks* (cheap, extends
what already works) versus *real cave biomes* (needs the biome source to become 3D, which is a
genuine piece of architecture and would unlock ancient cities and modded caves at the same time).
Worth deciding which one is actually wanted before starting.

### 4. 26.2's new stone families are not in any palette

26.2 ships full **cinnabar** and **sulfur** families (`CINNABAR`, `CINNABAR_BRICKS`,
`CHISELED_CINNABAR`, `SULFUR`, `SULFUR_BRICKS`, `POLISHED_SULFUR`, `CHISELED_SULFUR`,
`POTENT_SULFUR`, plus slabs/stairs/walls) and CityWorld uses none of them. Two reasons, both worth
remembering:

- They are absent from `Material.java`, which is generated from names the 1.14 Bukkit source used
  plus a curated `EXTRAS` list. Post-1.14 blocks only exist there because someone added them.
- `cityworld:build/modern_stones` is 24 explicitly-listed blocks, **not** a reference to `#c:stones`.
  Deliberate — it is a curated look — but it means the tag layer widens *within* chosen families and
  cannot discover a new one.

**This is the honest limit of the 5.0.3 tag work**: six woods became twelve automatically, but a
brand-new stone family still needs a human. Both would suit MODERN (the palette is light on warm
colours) and sulfur is an obvious mine-wall accent. 26.2 branch only; `"required": false` keeps the
same tag file safe on older versions.

**⚠ 26.3 is reported to add wool and concrete slabs and stairs — check the tags when it lands.** Our
palettes assume every block in a tag is a **full cube**: they are used as walls, floors and ceilings.
If Mojang puts wool slabs into `#minecraft:wool` (rather than a separate `#minecraft:wool_slabs`),
CityWorld would start building *walls out of slabs* — holes in houses, wherever wool came up. Cheap
to check, unpleasant to discover from a bug report. The same caution applies to `#c:concretes`.

The upside is real too: slab and stair variants of wool and concrete are exactly what the builders
lack for edges, steps and roof trim, so they are worth wiring in deliberately — as their own shape
vocabulary, not by widening the wall palettes.

### 5. Per-mod compatibility datapacks

The owner is choosing mods. The useful split is by **what the mod needs from us**, because tags only
decide what a block is *made of* — anything needing placement semantics needs a new seam first:

- **Free or a trivial datapack** (blocks joining existing palettes): Twilight Forest, Biomes O' Plenty
  blocks, Apotheosis bookshelves.
- **Needs a new tag seam, then datapack-able**: Farmer's Delight crops (farm fields are not
  tag-driven yet), Apotheosis spawners, cave decoration (#3).
- **Needs real feature work**: Fantasy's Furniture. The owner's instinct is right — furniture carries
  orientation and semantics (a chair faces a table), which no block tag can express. It would extend
  the `Support/Furniture` vocabulary, not a palette.

Also worth checking before promising anything: mods that add **worldgen** (Alex's Caves, Biomes O'
Plenty biomes) may not appear at all, because CityWorld suppresses carvers and structures and drives
its own biome source. That is a compatibility question of a different kind from palettes, and
probably the first thing to test with a big content mod installed.

## Caves, structures and 3D biomes (2026-08-27) — waves A and B

Queued items #1 and #2 are done, and the blocker under #3 with them. **1.21.11 only so far; not yet
cherry-picked to `mc26.1`/`mc26.2`.**

### The three were one bug, and it was not the one that was queued

`Structure.isValidBiome` asks **`chunkGenerator.getBiomeSource().getNoiseBiome(qx, qy, qz, sampler)`**
— the *biome source*, at the `STRUCTURE_STARTS` chunk stage. CityWorld's `getNoiseBiome` was a stub
returning `PLAINS`, because the real classification lived in `createBiomes`, which runs later and is
invisible to the structure pipeline. So every structure was biome-gated against plains at every
height: ancient cities (gated on `deep_dark`) could never place, and trial chambers would have placed
uniformly everywhere.

**Fixing `getNoiseBiome` for real, in 3D, unlocked all three items from one change.** Vanilla's own
`createBiomes` is nothing but `fillBiomesFromNoise(biomeSource, sampler)`, so our override collapsed
to a `super` call — the hand-rolled resolver it replaced was producing correct chunks while leaving
the method vanilla actually consults a constant. **That is the shape of this bug worth remembering: a
seam that is right for the caller you were thinking about and stubbed for the one you weren't.**

### Two corrections to the queued research

- **No access transformer is needed.** The queued note called the correctly-seeded path "the actual
  work", because `createForFlat` hardcodes the concentric-rings seed to `0L` (which would put every
  world's strongholds in identical places) and the two-seed constructor is private. But
  `createForNormal` reads its `HolderLookup` through **`listElements()` and nothing else** — so a
  ~20-line filtering delegate gives selective sets *and* the level seed. Vanilla even ships
  `HolderLookup.RegistryLookup.filterElements(Predicate)`. What was billed as the hard half was the
  easy one.
- **There was a blocker nobody had spotted.** Structure **pieces** are placed inside
  `ChunkGenerator.applyBiomeDecoration` (interleaved with features, in the same step loop) — the
  method CityWorld overrides and only calls `super` on for MODERN nature lots. Re-enabling starts
  without addressing this would have produced strongholds *sliced to whichever chunks happened to be
  wild*: a bug that looks like corrupt worldgen and reads like a vanilla fault. `placeStructures` is
  now the structure-only slice of that method, exactly as `placeUndergroundOres` is the ore-only
  slice, and it mirrors vanilla's `setDecorationSeed`/`setFeatureSeed` sequence so a structure lands
  identically whether this placed it or `super` did on the wild chunk next door.

### What landed

| | |
|---|---|
| `worldgen/CaveRegions.java` | the cave pool — seed-stable 2D cells, each a biome + a Y band |
| `worldgen/CityWorldBiomeLookup.java` | the shared 3D `getNoiseBiome` body + per-thread column cache |
| both biome sources | real `getNoiseBiome`; cave pool in `possibleBiomes()`; context binding |
| `CityWorldChunkGenerator` | `createForNormal` + `onlyAllowed`; `placeStructures`; `createBiomes` → `super` |
| `data/cityworld/tags/worldgen/structure_set/allowed.json` | which vanilla structures a CityWorld world keeps |
| `data/cityworld/tags/worldgen/biome/cave_pool.json` | which cave biomes the pool draws from (sulfur optional) |

**Cave biomes are patches, not vanilla's 3D banding** (owner's call). Vanilla fills its whole
underground with cave biomes by noise, which would change mob spawning *everywhere* and make wardens
routine. Cells are picked from `(x, z)` and apply only within their own Y band, which gives vertical
variation without a 3D noise field and stacks the pool by depth for free. `deep_dark` sits below
`y = -24` because that is where ancient cities generate — a shallower band would advertise the biome
without ever being able to host the structure gated on it.

### The cave pool is a biome tag, and it had to be

`#cityworld:cave_pool` — `deep_dark`, `lush_caves`, `dripstone_caves`, and **`sulfur_caves` marked
`"required": false`**. 26.2 added sulfur caves and it is the *only* new biome in that drop (measured
by diffing the two jars' biome lists), so it must be in the pool on 26.2 and absent elsewhere, from
one shared source file. A `Biomes.SULFUR_CAVES` constant would exist on 26.2 alone and break the
cherry-pick.

**⚠ The obvious alternative does not work, and fails in a way that looks unrelated.** The first
attempt resolved pool biomes by `ResourceKey` and used `HolderGetter.get(key).isPresent()` to skip
ones this version lacks. That crashes world load with:

```
java.lang.IllegalStateException: Unbound values in registry
    ResourceKey[minecraft:root / minecraft:worldgen/biome]: [minecraft:sulfur_caves]
```

The `HolderGetter` handed out by `RegistryOps.retrieveGetter` during datapack decode **creates an
unbound promise for a key it does not have** — that is how forward references between datapack files
work — so *asking whether a biome exists is what makes it not exist*. There is no "does this key
exist" question you can safely ask that getter. **Tags are the mechanism built for this**, and
`"required": false` is exactly the feature. Same discipline as the block-palette tags.

It also buys the data-driven half of queued item #3 early: anything added to the tag becomes a cave
type, and an entry with no geometry in `CaveRegions.GEOMETRY` gets a sensible default — so a modded
cave biome (Alex's Caves) needs a datapack line and no code. Patch salts are derived from the biome
id, so a new type gets an independent cell grid without anyone inventing a constant.

**Tag resolution must be lazy.** Tags are not bound when the codec builds the biome source, so
resolving in a constructor caches an empty pool forever. `cavePool()` is double-checked-lazy, and
`collectPossibleBiomes()` is late enough because vanilla memoizes it on first call.

### Ancient cities cannot reach CityWorld's sewers or cisterns (measured)

The owner's worry was a deep-dark city opening into a sewer. It cannot happen:

| | y |
|---|---|
| ancient city, **measured** from two generated starts | **−64 … −10** |
| CityWorld cistern floor (`streetLevel - FloorHeight*4 + 1`) | 49 |
| CityWorld sewers | 57 … 62 |

59 blocks of clearance. Note `start_height` in the structure JSON is an *absolute* `y = -27` and is
only where the jigsaw starts — the real extent had to be read off a generated `StructureStart`'s
bounding box, which is what `checkAncientCityDepth` now does on every version. Mines *do* reach that
deep, and the owner explicitly wants that collision: it reads as the miners having downed tools when
they broke through.

**The allow-list is a tag, not a config field** — `#cityworld:allowed` on the structure-set registry,
so a datapack (or a mod's) can widen it with no code change, the same seam the block palettes use.
**An absent tag means no vanilla structures**, so a stripped datapack fails to the old behaviour
rather than letting villages and mineshafts loose in a world that builds its own.

### Verified, not assumed

`checkBiomeDepth` and `checkStructures` were added to the self-test, so CI checks these on every
version. Measured on 1.21.11, seed 8675309:

| | |
|---|---|
| columns whose biome varies with depth | 603 / 4225 |
| cave-pool samples hit | 1669 |
| `deep_dark`/`lush_caves`/`dripstone_caves` | in the deep set, **absent from the surface set** |
| cave pool resolved from the tag | `deep_dark, dripstone_caves, lush_caves` (sulfur absent, as it should be) |
| structure sets surviving selection | exactly `ancient_cities`, `strongholds`, `trial_chambers` |
| stronghold ring positions | 128, first at chunk `3,-145` |
| trial chamber read back | chunk `12,11`, **with its own block entities** |
| ancient city extent | `y -64 … -10` |
| MODERN plan hash | `28fc3789` — unchanged |

Each row is load-bearing. `ancient_cities` surviving selection is the only cheap proof that
`deep_dark` is genuinely reachable (a set is dropped if the biome source cannot produce its biomes).
The trial-chamber *block entities* are the only proof that pieces are placed rather than merely
started — the failure mode `placeStructures` exists to prevent would still pass a starts-only check.
And the unchanged plan hash confirms biomes do not touch city planning, so the cross-version compare
stays valid.

**⚠ The rough edges to playtest, both by design rather than defect:**

- **Vanilla structures now generate under cities.** `placeStructures` runs *after* CityWorld's build,
  mirroring where `super` sits in the wild branch — so both branches order the world identically and
  a structure wins where the two overlap. A trial chamber landing under a city core (one did, at
  chunk `12,11`) may be exactly what is wanted or may need a city-core exclusion.
- **Ancient cities will meet the mines** — wanted, per the owner. They cannot reach sewers or
  cisterns; see the measurement above.
- **Underground mob spawning has changed** wherever a patch landed: cave biomes carry thin spawn
  lists and `deep_dark` carries none at all (plus wardens). Owner has signed off on this.

### Multi-version: measured, not assumed

All four load-bearing classes — `ChunkGeneratorStructureState`, `BiomeSource`, `Structure`,
`ChunkGenerator` — were diffed across 1.21.11 / 26.1.2 / 26.2. **The differences are decompiler
parameter renames and brace reshuffling; zero API-shape change.** `StructureStart.placeInChunk` is
identical too. So waves A and B should cherry-pick clean.

One genuine 26.2 behaviour change worth knowing: `ChunkGenerator.findNearestMapStructure` now
early-returns when the world's "Generate Structures" option is off, so that world-creation checkbox
now actually gates `/locate` — it did not on 1.21.11.

## Releasing — GitHub, and CurseForge automatically

Publishing a GitHub release now publishes to CurseForge too, via
`.github/workflows/curseforge.yml`. It downloads every jar attached to the release, reads the
Minecraft version out of each filename (`cityworld-5.1.0+mc26.2.jar` → `26.2`), and uploads them
with the release body as the changelog.

**One-time setup (owner only — the token must never be pasted into a chat or committed):**

1. Create a token at <https://legacy.curseforge.com/account/api-tokens>.
2. Repo **Settings → Secrets and variables → Actions → Secrets**: add `CURSEFORGE_TOKEN`.
3. Same screen, **Variables** tab: add `CURSEFORGE_PROJECT_ID` — the numeric project ID shown on the
   CurseForge project page.

Until both exist the workflow **skips rather than fails**, so it will not put a red cross on a
release. `workflow_dispatch` re-uploads an existing tag by hand.

`scripts/curseforge-upload.sh` does the actual upload and can be run locally. CurseForge wants
numeric game-version IDs, and those change as versions are added, so it resolves them from
`/api/game/versions` on every run rather than hardcoding them — and fails with the list of names
CurseForge *does* know if a Minecraft version is not listed yet. **That is the expected failure
right after a Minecraft release**: CurseForge has to add the version before anything can be uploaded
against it.

**⚠ HTTP 200 means accepted, not published.** Moderation runs afterwards, and this is where the
confusing failures live:

- **CurseForge dedupes by file content.** The same jar cannot exist twice on a project, so
  re-uploading a release that is already up gets each file *rejected as a duplicate* — even though
  the API returned a file ID. Delete the old files first, or don't re-run.
- **Rejected files are hidden from the authors file list by default**, so they do not look rejected;
  they look like they never arrived. Everything appears to have silently done nothing.
- **Some files land in "Under Manual Review"** and stay off the public page for a while. Nothing is
  wrong; it clears on its own.

All three bit during the 5.1.0 upload. The authoritative view, always, is
`https://authors.curseforge.com/#/projects/<id>/files` — the public Files tab lags behind it.

## Verifying a version — `scripts/selftest.sh`

Hand-playtesting every supported version does not scale at four drops a year, so verification is
automated. `selftest/CityWorldSelfTest` generates a real world on a fixed seed and checks:

- the overworld is genuinely on `CityWorldChunkGenerator`/`CityWorldClimateBiomeSource` — **a silent
  fall back to vanilla worldgen is the scariest failure and looks like nothing at all**;
- the planner produces a full spread of contexts and lots across MODERN/APOCALYPSE/CLASSIC;
- decoration actually writes blocks;
- signs carry text **on both faces** — the canary for the `SignBlockEntity` access transformers.

It is dormant unless `-Dcityworld.selftest=true`. Run it with `./scripts/selftest.sh` (it picks the
right JDK from `minecraft_version`), then `./scripts/selftest.sh --compare` once several versions
have been run.

**It also runs in CI** — `.github/workflows/selftest.yml`, on every push to the three version
branches and on demand. A three-branch matrix runs the harness on each version, then a compare job
fails if any two disagree on the plan hash. Warm, that is **4–5 minutes per version in parallel**;
cold it has to let NeoForm decompile Minecraft, which is 10–15 minutes and is why the cache is keyed
on the NeoForge version. Docs-only pushes are ignored.

**It earned its keep immediately.** The first green-building CI run caught that on a *fresh
checkout* the server silently fell back to vanilla `NoiseBasedChunkGenerator`: `set_prop` had two
paths that disagreed about backslashes, and a developer's `run/` directory always took the working
one. The world generated, looked entirely normal, and was not CityWorld. That is exactly the failure
the generator-identity check exists for, and nothing but a clean environment would have surfaced it.

**The comparison is the clever half.** Planning never touches the block registry, so for a fixed seed
the plan is a pure function of the seed and must be *identical* on every Minecraft version. The
harness hashes it and `--compare` fails if two versions disagree — that catches a change that
silently alters worldgen on one version only. Materials are deliberately **excluded** from the hash,
because those legitimately widen as newer versions add blocks to the palette tags.

Measured 2026-08-17, all three passing and agreeing:

| version | plan hash | signs | front | back | distinct blocks | run |
|---|---|---|---|---|---|---|
| 1.21.11 | `28fc3789` | 67 | 65 | 30 | 136 | 233s |
| 26.1.2  | `28fc3789` | 67 | 65 | 30 | 134 | 232s |
| 26.2    | `28fc3789` | 67 | 65 | 30 | 136 | 261s |

The identical plan hashes are the point: same seed, same city, three Minecraft versions. The distinct
block counts differing by two is the expected material variance, not a fault.

**Only the plan hash is an invariant — the rest of the table is indicative.** Re-running an unchanged
1.21.11 build gave 66 signs and 64 fronts where it had given 67 and 65: decoration of chunks at the
edge of the surveyed block depends on which neighbours happen to be loaded, so counts wobble by one
or two between runs. The harness therefore asserts *presence* (signs exist, fronts exist, backs
exist, blocks were written), never exact counts. **Do not tighten those into equality assertions** —
it would produce a test that fails at random and teaches everyone to ignore it.

**⚠ A trap this harness fell into itself, worth not repeating.** Its first version located test
chunks by rebuilding a `CityWorldGenerator` and asking it for `RoadLot`s — and reported "no signs
found", which read exactly like a 26.2 regression. It was not: `PlatMap.getMapLot()` subtracts the
platmap's own origin, which is *not* aligned to a multiple of `PlatMap.Width`, so indexing `getLot()`
with a `floorMod` silently reads a different lot. **Survey the world that was generated; do not
predict it.** Reading `LevelChunk.getBlockEntities()` is both exact and far cheaper than scanning.

## The measured 26.1 delta (2026-08-16, branch `mc26.1`)

Stage 2's actual deliverable. Target: **Minecraft 26.1.2 / NeoForge 26.1.2.95** (the last patch of
the 26.1 line). Builds clean, runs, and generates cities. **The port cost was far lower than
predicted.**

**The entire source delta is one API change:**

- **`ChunkPos` became a record**, so the public `x`/`z` fields are now accessors — `pos.x` →
  `pos.x()`. That is **20 compiler errors across 12 lines in 6 files**, and it is *all* of them.
  `new ChunkPos(x, z)` still works (it is the canonical constructor); the predicted
  `new ChunkPos(BlockPos)` → `containing()` and `.asLong()` → `.pack()` renames exist in 26.1 but
  this codebase never used those forms.

**Predictions that did not bite:**

- **`compat/Material.java` — the "biggest single fragility" — cost nothing.** Re-running
  `gen_material.py` against 26.1 produced a file **byte-identical** to the 1.21.11 one. All 691
  `Blocks.X`/`Items.X` bindings survived. The generator already fails loudly on unmapped names,
  `EXTRAS`, and `EXTRAS_EXPR`, so this was a real check, not a silent pass.
- **The loot-registry `MapCodec` change misses us** — every `getType()` hit in the tree is our own
  method, not a vanilla loot type.
- **The rendering refactors miss us**, as predicted (0 `GuiGraphics` usages).
- **`new ItemStack(...)` still works** in `HospitalLot`; `ItemStackTemplate` was not forced on us.
- **The access transformer needed no change.** `SignBlockEntity.frontText`/`backText` still exist
  and the AT still widens them (verified in the transformed 26.1 sources, not just inferred from a
  green build).

**Toolchain changes** (`gradle.properties`, `build.gradle`, `deploy.sh`):

- **Java 21 → 25** (26.1 ships `java-runtime-epsilon`; JDK at `./tools/jdk25`, git-ignored like
  `jdk21`). `deploy.sh` now prefers `jdk25` and falls back to `jdk21`, honouring a preset `JAVA_HOME`.
- **ModDevGradle 2.0.141 → 2.0.144.** The Gradle wrapper was **already** 9.2.1, so the "Gradle 9.1+"
  requirement cost nothing.
- **Parchment stays disabled** — 26.1 dropped obfuscation entirely, so it would only add parameter
  names. The properties are already blank; nothing to do.
- **The jar now carries its Minecraft version** — `cityworld-5.0.3+mc26.1.2.jar`. Both lines share a
  mod version, and two files both called `cityworld-5.0.3.jar` are indistinguishable in a mods folder
  or on a releases page. The version *inside* `neoforge.mods.toml` stays a plain `5.0.3`.

**Three cross-version traps found in `scripts/gen_material.py`** — all silent-wrong-answer bugs that
only appear once a second version exists on the machine, now fixed:

1. It globbed `sourcesAndCompiledWithNeoForge_*` and took **`jars[0]` of an unsorted glob**. With one
   MC version cached that is always right; with two it regenerates `Material.java` against whichever
   happens to sort first.
2. **The NeoForm artifact was renamed** — 26.1 produces `mergeWithSources_*_output.jar`, so the old
   glob does not match it at all and would silently fall back to a stale 1.21.11 jar.
3. **The decompiled-source cache was shared across versions** (`/tmp/cityworld-portgen/mcsrc`), so
   after retargeting the build it would serve the *previous* version's `Blocks.java`.

   The fix picks the jar by fingerprint: read `minecraft_version` from `gradle.properties`, get that
   release's `world_version` from NeoForm's cached `minecraft_<ver>_client.jar` (`version.json`), and
   match it against `SharedConstants.WORLD_VERSION` in each candidate jar. No hand-maintained table,
   no network call, and it dies loudly rather than guessing. The cache is now per-version.

**Verified in-world**, not just compiled (temporary `Port261Probe`, since deleted):

- The overworld really is on our generator — asked the live server:
  `minecraft:overworld -> CityWorldChunkGenerator / CityWorldClimateBiomeSource`. **`level-type` still
  works on 26.1.**
- Planning sweep over 961 platmaps × MODERN/APOCALYPSE/CLASSIC: **11 contexts and 35–41 lot classes
  each**, no throws — the whole `ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔ Plugins` cycle runs.
  Hospitals, zoos, biodomes, airships, saucers and castles all appear.
- Decoration read-back: forced a `RoadLot` chunk to FULL and read it back — 25 distinct blocks
  including cyan terracotta, stone bricks, a birch door, a chest, glowstone and iron bars. **Block
  writing and block entities work.** Zero exceptions across the whole server run.

**Verified in a real client** (owner, 2026-08-16, a CurseForge `26.1.2` instance on
neoforge-26.1.2.95, CityWorld the only mod). This covers what `runServer` structurally cannot:

- **The Customize screen works** — the client-only world-creation UI was the single largest untested
  surface on 26.1 (the dedicated server never loads those `@OnlyIn(CLIENT)` classes), and it needed
  no changes.
- **Street-name signs render their text**, and a mine-entrance headframe read *"Gallows Adit / Est.
  1888" on both faces*. That is the strongest result of the whole port: it proves the `frontText`
  **and** `backText` access transformers still apply and that the direct field writes still survive
  decoration. Had either failed, the symptom would not have been a blank sign — the chunk would have
  failed outright and world teardown would have hung on "Saving world".
- **The 5.0.3 tag-backed palettes behave identically** — the owner's read was "like 5.0.3 with an
  expanded but weighted palette", i.e. wider materials with the original odds intact, exactly as on
  1.21.11. The tag layer needed no version-specific work.
- **Named villagers, overgrowth, biomes and schematics all work.** Each of these is a separate risky
  surface and all four came through unchanged: entity spawning during chunk generation
  (`EntitySpawnReason.CHUNK_GENERATION`), the post-decay overgrowth pass, the custom
  `CityWorldClimateBiomeSource`, and the whole schematic pipeline — multi-format load, data-fixing
  from 1.12-era files, block entities, rotation and mirroring.

**Net: the 26.1 port is complete and verified end to end.** Every major subsystem has now been seen
working on 26.1 — worldgen, decoration, signs, spawning, biomes, overgrowth, schematics, palettes and
the client UI. Nothing on the 26.1 line is outstanding.

**⚠ Two traps for the next port, both of which cost time here:**

- **`level.dat` no longer stores worldgen settings in 26.1.** There is no `WorldGenSettings` key. A
  world generated by our generator therefore looks identical to a vanilla one if you inspect
  `level.dat` — which briefly, and wrongly, looked like the port had regressed to vanilla worldgen.
  **Ask the running server** (`level.getChunkSource().getGenerator()`) instead.
- **`WorldData.worldGenOptions()` is gone.** The documented probe recipe uses it to fetch the seed;
  use `server.overworld().getSeed()` on 26.1. This does not affect the mod, only probes.

**What this says about stage 3.** The `compat/` seam did its job: 26.1's breakage was one record
conversion, not a port. But note the shape of it — `pos.x` vs `pos.x()` has **no syntax that compiles
on both versions**, so a genuinely single tree needs either a shim (`Compat.chunkX(pos)`) or a source
preprocessor. That was left out deliberately here so this branch measures the raw delta rather than
an abstraction built from a single data point. Do 26.2 next, then decide with two deltas in hand.

## The measured 26.2 delta (2026-08-17, branch `mc26.2`)

Target: **Minecraft 26.2 / NeoForge 26.2.0.59**. Java 25 and ModDevGradle 2.0.144 again, so the
toolchain cost nothing this time. **This is the drop where the treadmill stopped being free.**

**26.2 rewrote how Minecraft declares whole families of blocks.**

- **Dyed blocks are gone as fields.** `Blocks.BLACK_WOOL` and its siblings no longer exist — wool,
  carpet, concrete, concrete powder, terracotta, glazed terracotta, stained glass, stained glass
  panes and beds are each one `ColorCollection<Block>` indexed by `DyeColor`:
  `Blocks.WOOL.pick(DyeColor.BLACK)`. **145 of our constants** were affected. Note the naming
  exceptions — the dyed family takes a `DYED_` prefix where the undyed block keeps the plain name
  (`Blocks.TERRACOTTA` is undyed, so dyed terracotta is `Blocks.DYED_TERRACOTTA`), likewise
  `DYED_SHULKER_BOX`, `DYED_CANDLE`, `DYED_CANDLE_CAKE`.
- **Copper went the same way.** The `WeatheringCopperBlocks` record became
  `WeatheringCopperCollection`, whose stages hang off `weathering()` —
  `Blocks.CUT_COPPER.weathering().exposed()`. It also swallowed blocks that used to be plain fields:
  `CUT_COPPER`, `COPPER_GRATE`, `COPPER_CHEST`, `COPPER_BULB`, `CHISELED_COPPER`, `COPPER_BLOCK`
  and `LIGHTNING_ROD`.
- **`EntityType`'s constants moved to `EntityTypes`** (mirroring `BlockEntityType`/`BlockEntityTypes`).
  59 references, one mechanical rename.
- **`DripstoneThickness` is `SpeleothemThickness`**, and `BlockStateProperties.DRIPSTONE_THICKNESS`
  is `SPELEOTHEM_THICKNESS`. Same enum constants.
- **`Minecraft.setScreen` moved onto `Minecraft.gui`** — `this.minecraft.gui.setScreen(...)`.

**Almost all of it landed in generated code, which is the whole point of having generated it.**
`gen_material.py` now *derives* these expressions instead of naming fields, from rules rather than
tables: split a name at its longest dye-colour prefix and look for a matching `ColorCollection`
(trying `DYED_` too), or split a weathering-stage prefix and look for a `WeatheringCopperCollection`.
The same generator therefore emits flat fields on 1.21.11 and 26.1 and collection picks on 26.2, and
**regenerating on the two older versions produces byte-identical files — verified.** `EXTRAS_EXPR`'s
twelve hand-written copper expressions are now derived the same way and can no longer rot.

Only **three hand-written sites** needed touching: `LegacyBlocks` (its dye-ordered tables are now
derived via `DyeColor.byId`, which is *better* code — a legacy block's data value simply is the dye
id), `Overgrowth` (the speleothem rename) and `CityWorldCustomizeScreen` (the `gui.setScreen` move).

**What did not break:** the palettes. Because `5.0.3` moved them onto block tags, 144 vanished block
fields did not cost the palette layer a single line — the tags still resolve. Stage 1 paid for itself
here. The access transformer, the codec registration, the schematic pipeline and the biome source all
came through untouched as well.

**⚠ Two infrastructure traps, neither of them code faults:**

- **A zero-byte jar in the Gradle cache** (`error_prone_annotations-2.48.0.jar`, whose SHA-1 was
  `da39a3ee…` — the hash of an empty file) failed the build with hundreds of bogus
  "cannot access net.minecraft" errors. Maven Central served it fine; Gradle wrote it empty, twice.
  Fixed by dropping the real jar into the cache under its correct SHA-1 directory.
- **An OOM kill (exit 137)** mid-build while a dev client for another mod was running. It reads like
  a build failure and is not one.

## Stage 3: what the two deltas say

The two data points disagree, which is itself the finding:

| | 26.1 | 26.2 |
|---|---|---|
| hand-written source changes | 12 lines, 6 files | 3 files |
| generated source changes | none (byte-identical) | 145 constants, all derived |
| toolchain | Java 21 → 25, MDG bump | none |
| nature of the change | one record conversion | block-declaration model rewrite |

**A quarterly drop is not reliably cheap.** Planning for "12 lines every three months" would have
been the wrong lesson to take from 26.1.

**What actually carried the weight was not a clever build setup — it was two design decisions already
in place:** the `compat/` seam (only 59 of 386 files touch `net.minecraft`), and generating
`Material.java` instead of hand-writing it. 26.2's 145 broken constants cost *rules in one Python
file*, not 145 edits, and none of the 3,096 call sites moved.

**The open question for a single tree** is that the divergences have no syntax valid on both versions
— `pos.x` vs `pos.x()`, `Blocks.BLACK_WOOL` vs `Blocks.WOOL.pick(DyeColor.BLACK)`. So a single tree
needs either per-version source sets for a small compat shim, or a source preprocessor. The generated
file is *already* effectively per-version, which suggests the shim approach: keep one shared tree,
add `src/compat/<version>/java` holding only the handful of diverging methods, select it with a
Gradle property. The hand-written divergence across three versions is currently **four call sites**,
which is small enough to be worth doing and small enough that getting it wrong costs little.

**Recommended before committing to it:** keep branch-per-version for one more drop (26.3, due ~Sept
2026) to see whether the divergence set keeps shrinking or grows. Merging three branches into one
tree is cheap now and cheap later; guessing wrong about the mechanism is not.


Deploying: `./deploy.sh` targets the `CityWork-ReForged` instance;
`CITYWORLD_INSTANCE="/mnt/c/Users/darre/curseforge/minecraft/Instances/MobHealth - Forge" ./deploy.sh`
targets the second test instance (where LegendQuest/ZombieMod test reacting to city locations). Both
fail with "Permission denied" if that instance's Minecraft is open — close it and rerun.

- ~~Finer decay knobs~~ **DONE (2026-08)** — graduated per-category demolition control (intensity,
  fire density, pristine-road sparing), with APOCALYPSE/DESTROYED presets encoding the owner's two-tier
  vision (gentle reclaim vs heavy war damage). See [[cityworld-demolition-more-options-later]] and the
  dated block below.
- ~~A few interior deco blocks not yet woven in~~ **DONE (2026-07)** — chains (chandeliers), copper
  chests, richer candles + candle-cake, decorated pots all in the `Furniture` accent vocabulary; lightning
  rods on highrise roofs + radio-tower aerials; hanging lanterns in the mines. See the deco log below.
- ~~Building copper weathering~~ **CLOSED (owner: a-ok, leave as-is 2026-07)** — not doing it.

**Tag-backed building palettes (2026-08-16, most recent).** Stage 1 of the cross-version arc, and a
feature in its own right. Stacks under `## 5.0.3`; `mod_version` bumped. Not yet released.

- **Eight palettes moved from compiled constants to block tags** — `cityworld:build/{planks, wool,
  terracotta, glazed_terracotta, concrete, concrete_powder, stained_glass, modern_stones}`, defined in
  `src/main/resources/data/cityworld/tags/block/build/` and resolved by `Support/MaterialTags`.
  Verified in-world by a temporary `ServerStartedEvent` probe (since removed): planks **6 → 12**,
  wool **10 → 16**, terracotta 17, glazed 16, concrete 16, powder 16, stained glass 16, modern stones
  24; no unbound-tag warnings.
- **Weighting is preserved, and that is the whole trick.** `MaterialList` gained tag *pools*: a pool
  occupies exactly the number of slots the constants it replaced did, so the odds of "some plank" are
  unchanged and only *which* plank widens. Flattening the tag in instead would have doubled wood's
  share of every wall, and a modpack with thirty wood types would have drowned every palette. Two
  rolls per pick: one for the slot, one within the pool (reusing the slot offset would make a
  weight-1 pool always yield its first block).
- **Determinism trap, worth remembering:** tag iteration order is *not* stable across loads, versions
  or packs, and every material choice indexes a list with a seeded `Odds` — so an unsorted pool would
  mean the same seed grew a different city each load. `MaterialTags.resolve` **sorts by registry id**
  before anything indexes it. Probe-verified: two runs of the same seed produced identical sequences.
- **`c:` tags are the win for the families vanilla doesn't tag.** Vanilla ships 204 block tags but has
  no `concrete` tag (only `concrete_powder`) and no generic "stones"; NeoForge's 123 common tags
  supply `c:concretes`, `c:glazed_terracottas`, `c:stones`, `c:cobblestones`, `c:glass_blocks`,
  `c:villager_job_sites` (a direct fit for the shop layer, still unused) and more.
- **Modpack compatibility falls out for free** — a mod that tags conventionally appears in cities with
  no patch at all; anything else is a datapack adding to `cityworld:build/*`. Documented for players
  in the new **`PALETTES.md`** (also website source material), including the `"replace": false` /
  `"required": false` traps and why a modded plank feels rare (slot weighting).
- **Left as fixed lists on purpose:** unfinished buildings (muted greyscale), government offices (pale
  civic), roads and maze walls (order carries meaning). Widening those loses the intent.

**Announce feature + vault glow-up (2026-08-15).** Built on owner request after the release
wave below; all committed through `ae04ebe`, deployed to both instances, **not yet released** (stacks
under `## 5.0.2` in `CHANGELOG.md` — owner cuts releases when there's enough, and this batch is enough):

- **Landmark announcements** (`world.broadcastSpecialPlaces`, default off): chat lines as landmarks
  generate, sent to the players **in the generating world** (not server-wide). Every `reportLocation`
  call site now carries a stable kind key, gated through **`world.announcedLandmarks`** (a datapack
  list) — the owner's curated default: `airship, saucer, vault, zoo, biodome, hospital, schematic`.
  Everything else (fishpond, campground, shack, mineentrance, castle, oilplatform, radiotower, bunker,
  museum, balloon, vaultroad, hospitaldept) stays debug-log-only unless a server adds its key.
- **Schematic `.yml` grew `Title:`** ("The Statue of Liberty" instead of "liberty") — announcements and
  `/cityfind` both use it (find matches name OR title, displays title). Sidecar parsing now honours
  quoting and inline `#` comments on **every** key (was Title-only; `Decayable: "true"` parsed false).
- **Curation applied**: 7 bundled landmarks announce with proper titles (liberty→Statue of Liberty,
  midwich, spiritwind, dragon, water tower, hedge maze, pagoda). **Instance-side** (not in git!): 8
  drop-ins in CityWork-ReForged's config announce (Big Ben, Lighthouse, Cathedral, Arch de Minecraft,
  Freight Ship, Dredge, Cara Sutra, Sablednah); 5 titled-but-quiet; mini-castle odds fixed 0.1→0.02.
- **Liberty reskinned in weathered copper** (was 13,563 light-blue wool — predates copper). Legacy
  `.schematic` can't name post-1.12 blocks, so it's now a vanilla `.nbt` — which surfaced that the
  bundled index only accepted `.schematic`; `loadBundled` now takes any supported format.
- **A high-effort code review of this diff found 10 verified issues, all fixed** (commit `90b4191`):
  per-world+curated broadcast (above), vault road chunks each announcing a different hashed number,
  `.nbt` drop-ins stamping recorded air (now stripped unless `KeepAir`, matching other formats —
  `Templates.build` takes a `keepAir` param; `.schem`/`.litematic` readers pass `true` since they
  filter during tag construction), Customize style-cycle silently resetting radius/naming/mobs (now
  carried), floors picker display-vs-save divergence, and a duplicate extension matcher.
- **Vault glow-up** (owner screenshots): the lobby blast door is now a proper Fallout cog — 10-block
  toothed gear (gray plate, copper spokes/hub, iron rim, 8 teeth), hazard-striped doorway/threshold,
  winch machinery + chains, VAULT-number wall signs. **Root cause of the old "not lined up" look: an
  odd-width design centred on column 8 against the 2-wide 7/8 corridor — half a block off. Even-width
  designs centred on the 7.5 seam are the rule for anything framing a corridor.** Lobby furnished
  (console, lockers, vents) via the `setAt/mapX/mapZ` door-side mapper so props rotate with the door
  wall. The surface hut stood metres above slopes (`blockYs`+3 fudge) — now scans the live chunk at
  the door column for real grade (scanning UP so canopies can't fool it). **Gotcha: wall signs face
  AWAY from the block they hang on; `outwardFace(side)` points out through the wall, so signs need
  `getOppositeFace()`.** The cog was ASCII-rendered off-line before shipping — cheap geometry check.
- **Max building floors picker** in Customize (8–60, snaps at load so display == saved).

**Release wave + post-release polish (2026-08-12→15).** Documentation, first public release,
and the playtest fixes that followed it:

- **Docs corrected and expanded.** The world-style table said "10 styles" and listed a single "Normal";
  the enum actually has **13**, with MODERN and CLASSIC as separate styles (CLASSIC was formerly named
  NORMAL) and FLOATING missing entirely. Fixed in `README.md` and `CURSEFORGE.md`. Added `CURSEFORGE.md`
  (project description), `CURSEFORGE-CONFIGURATION.md` and `CURSEFORGE-COMMANDS.md` (deep dives, built by
  reading `SettingsExample.java` and `CityWorldCommands.java` rather than from memory — the latter
  documents `/cwlocate`, which had never appeared in any doc). 21 captioned screenshots added to the
  README under `screengrabs/`, JPEG-compressed 23.4MB → 2.9MB.
- **Promo stat, ground-truthed.** 135 saved worlds / 3,249,933 generated chunks / ~832 km², counted by
  parsing the 1024 3-byte offset entries in each `.mca` region header across the test instance's `saves/`
  — not estimated from file sizes.
- **`v5.0.1` released** on GitHub (tag + jar + notes), `CHANGELOG.md` started.
- **Overgrowth debris was all one tile.** `LEAF_LITTER` and `PINK_PETALS` are `SegmentableBlock`s whose
  default state is always 1 segment facing north, so every scrap looked identical even though the material
  pool was already mixed. `Overgrowth.scatter()` now rolls `SEGMENT_AMOUNT`, `HORIZONTAL_FACING` and
  `AGE_3` (berry bushes) per placement; blocks without those properties pass through.
- **Azaleas grew in thickets — two independent causes.** (1) `LushCaves.surfaceAzalea` ran per *chunk*
  with 6–10 tries, but a lush *region* spans ~25 chunks, so a patch could stack up hundreds; now 80% of
  lush chunks get none and the rest get 1–2. (2) Separately, `AZALEA`+`FLOWERING_AZALEA` were **10% of the
  `Overgrowth` ground-cover pool** — the loud silhouette read as a thicket on APOCALYPSE. That slot now
  takes a **`PERSISTENT` leaf block of the local biome's tree species** (spruce/birch/jungle/acacia/dark
  oak/pale oak/mangrove/cherry, oak fallback; azalea foliage kept only for flower forest and meadow).
  **Gotcha: fixing the visible pass first was wrong** — the two paths look identical in-world, and the
  owner was on APOCALYPSE with overgrowth on, i.e. the *other* one.
- **Richer nature clutter**: wildflowers, plain bushes, feral sweet-berry bushes, dry grass, dead bushes on
  roads, and a ~1% `FIREFLY_BUSH`. New constants added via `scripts/gen_material.py` (**never hand-edit
  `Material.java`**) and regenerated.
- **Lightning rods stood on moss carpet.** The rooftop-rod pass ran *after* overgrowth and stopped its
  downward scan at the first non-empty block — which on a reclaimed roof was the moss carpet, so the rod
  perched a notch high. Fixed at both ends: the pass now runs **before** overgrowth (overgrowth already
  skips non-sturdy tops, so it won't moss the rod), and the scan requires a solid top face via a new
  **`SupportBlocks.isSturdyTop()`**. **Gotcha worth remembering: `isEmpty()` counts carpets/plants/snow as
  filled**, so any "scan down to the surface" loop needs the sturdy test, not just `!isEmpty`.

**Playtest-polish wave landed (2026-08, earlier).** A long
run of the owner playing a MODERN/APOCALYPSE world and reporting back, each fix redeployed and re-tested
in-world (not just probed):

- **Vault lift shafts reworked.** The original `hasLift` distribution (`cx*7+cz*13 mod 70`) was
  mathematically stuck — only fires when `cz≡0 mod 7`, so most chunk-rows never got one — replaced with a
  bit-mixed hash giving a uniform ~1/24. Geometry reworked from owner feedback across several rounds: the
  per-level landing gantry (which fouled the moving car) is gone, the shaft is a clear vertical column with
  a central wall guide-rail + `IRON_CHAIN` cables, a ladder run flush against each level's door (no
  freestanding landing), a hanging `LIFT` sign over each door, `COPPER_GRATE` winch/anchor machinery above
  and below each chain segment, and a 4-tall open-interior car (bars/chains removed so it's walkable) whose
  floor and roof are `BIRCH_TRAPDOOR` hatches where the ladder passes through (floor hatch flush with the
  floor, not the block below). **Confirmed perfect by the owner** after this round. See
  [[cityworld-vault-feature]].
- **Finer decay knobs + APOCALYPSE/DESTROYED split** (see the DONE bullet above) — plus a fire-gating bug:
  `destroyArea`'s crater rubble only checked `includeFires`, not `includeDecayedFires` like `destroyWithin`
  did, so APOCALYPSE (fires off, decayed-fires off) still caught its crater debris alight. Both paths now
  gate identically. See [[cityworld-apocalypse-aesthetic]].
- **CLASSIC now replicates 1.8 CityWorld's biomes**: dropped the plains band 1.8 never had, added birch
  forest, and rebanded `CityWorldBiomeSource.classify` on the shaper's actual `treeLevel`/`evergreenLevel`/
  `snowLevel` (the exact Y-levels the original Bukkit `setBiome` push used) instead of approximate percent
  bands — forest-dominant cities, birch/taiga on hills, zero plains. Also filled in biome coverage that was
  stuck on placeholder `plains` for `nature`/`metro`/`sparse` (→ `cityworld:climate`) and `sanddunes`/
  `snowdunes`/`flooded` (→ desert/snowy_plains/ocean). See [[cityworld-biomes-and-outland]].
- **Customize screen overhaul.** Switching the Style dropdown used to leave every other toggle as-is, so
  settings from the previous style (e.g. Schematics, on by default) silently rode into styles that don't
  use them. Fixed: changing style now reloads that style's own defaults and rebuilds the screen. Locked
  settings (the ones `validateSettingsAgainstWorldStyle` forces) now grey out with a tooltip, detected
  generically (`CityWorldSettings.styleLocks` — validates all-on vs all-off and keeps the keys that agree,
  so it can't drift from the actual lock logic as styles evolve). A "soft default" pattern was introduced
  for style-appropriate-but-overridable settings (set in `styleDefaults`/the datapack, never in `validate`,
  so it stays toggleable and ungreyed) — used for `windingCaves` below. See
  [[cityworld-style-testing-fixes]].
- **Schematic covering fixed for FLOODED/SANDDUNES/SNOWDUNES**: pasted buildings left dry air pockets
  because the fill only used the flat `findHighestFloodY`. `ClipboardLot.finishStyleFill` now fills each
  column to its own `findFloodY` (following the dune surface, not a flat plateau) with the right material
  (water/sand/snow), plus a partial snow-layer cap at the true edge instead of a solid-block step. Astral
  mushroom stems were rendering with the cap-block texture (all-one-texture bug) — now use `MUSHROOM_STEM`.
  See [[cityworld-style-testing-fixes]].
- **Winding ("noodle") caves for MODERN** — a new `windingCaves` setting (default on for MODERN/
  APOCALYPSE, off elsewhere but freely toggleable by any style) replaces single-noise-blob caves with
  tunnels that wander and branch like vanilla, faked via the intersection of two simplex iso-surfaces
  (`|wormA|<eps AND |wormB|<eps`, plus a rare low-frequency "cheese" cavern field) — vanilla `WorldCarver`s
  aren't reachable from this generator (it extends the base `ChunkGenerator`, no `NoiseChunk`/
  `CarvingContext`), so the shape is faked in `ShapeProvider_Normal.notACave` rather than carved for real.
  Tuned over several owner-driven passes to a final set of knobs (`wormScale 1/112`, `wormScaleY 1/88`,
  `wormEps 0.095`, `cheeseScale 1/88`, `cheeseThreshold 0.865`).
- **Lush cave decoration pass** (`Support.LushCaves`, MODERN, gated on a seed-coherent ~5%-of-80-block
  region so patches are rare and well-spaced): moss- and clay-lined floors, moss carpet, small/big
  dripleaf, glow-berry cave vines and spore blossoms on the ceiling, rare 2×2 water pools stocked with
  axolotls/frogs/tropical fish, and — on nature lots above a lush patch — a surface azalea on rooted dirt.
  Two owner-reported bugs fixed after the first cut: (1) the surface azalea **never actually generated** —
  the scan used `findBlockY` (the base noise terrain height, not the real placed surface) and a broken
  empty-space search; fixed by scanning the real placed blocks top-down for the first solid-with-air-above
  cell; (2) azaleas were floating / landing on tree canopies and building roofs — fixed by requiring the
  surface block be an actual `GRASS_BLOCK`, not just "any solid block". Ceiling moss also read as floating
  green cubes detached in mid-air (the noodle-cave noise can leave small-but-thick isolated rock lumps in a
  cavern); fixed by requiring the ceiling to be both two-thick *and* backed on ≥3 of its 4 horizontal
  neighbours before decorating it, so only a genuinely broad slab qualifies. Cave vines were also all
  lit with glow berries (`BERRIES=true` on every segment); now only ~20% of segments carry a berry so a
  hanging vine reads as mostly bare with the odd lit spot, not a string of lanterns. See
  [[cityworld-winding-caves]] (also holds the lush + lava notes).
- **Lava reworked from a flat sea (and then floating 3D blobs) to flat-topped pools.**
  `ShapeProvider.lavaFillAt` is a per-block hook (base: flat field below `lavaFieldLevel`;
  `ShapeProvider_Normal` overrides it for MODERN) — the first MODERN attempt used 3D noise, which reads as
  floating blobs rather than pools. Fixed with a 2D lava-lake region field (noise sampled at `y=0`, i.e.
  flat) so a lake fills every void up to its level with a genuinely flat top. Then, because the pool's
  region boundary can cut straight across a winding-cave tunnel and leave an unnaturally flat vertical
  wall of raw stone, a new decoration pass (`Support.LavaLakes`) lines the pool's sides and floor (never
  the open top) with `BASALT` — but only where a lava cell has ≥2 lava neighbours, so a lone flowing drip
  stays untouched. See [[cityworld-winding-caves]].

**Two things this doc *used* to list as remaining are actually DONE** (verified 2026-07): schematic
**rotation is applied** (random 1-of-4 in `PlatMap.placeSpecificClip`; mirroring too, but opt-in per
schematic via `FlipableX/Z`, default off to protect signs/fronts), and the **outland context arm is wired
and live** (`ShapeProvider_Normal` returns it for the nature 0.70–0.75 band, so gravelworks / woodworks /
campgrounds generate in normal worlds). The stale claims below are corrected.

**MODERN-gate any new deco** (CLASSIC stays 1.8-era). Placement runs through the decoration seam
(`RealBlocks` / room fitting), not terrain gen; model new passes on `Support/Overgrowth` or
`Support/ApocalypseSpawners` (post-decoration, per-lot, block-state aware).

**Park zones + APOCALYPSE + landmark finder landed (2026-07, most recent).**
- **Park zones**: `ZooLot` (themed fenced enclosures + matching animal + name sign; 10 themes) and
  `BiodomeLot` (glass domes over a biome slice — jungle/swamp/flower-forest/pale-garden/cave, rarely
  End/Nether), placed in park districts via a `ParkContext.getPark` override (~⅓ of park lots),
  MODERN-family only. Both extend `IsolatedLot` (like the barn/fish-pond).
- **APOCALYPSE world style**: a MODERN city gone to ruin. A new `CityWorldGenerator.isModernStyle()`
  (MODERN ‖ APOCALYPSE) replaces the ~dozen `== MODERN` gameplay checks (behaviour-preserving for MODERN)
  so it inherits the modern look; `validateSettingsAgainstWorldStyle` forces road+building decay on /
  nature decay off / overgrowth @3.0 / `spawnBaddies` up / spawner toggles on; `Support.ApocalypseSpawners`
  buries zombie spawners in a sealed 2-tall pocket UNDER basement floors (hidden, floor caps it), plus
  zombie-heavy sewer/mine bags. **MODERN overgrowth now defaults OFF** (it's the apocalypse's signature).
  **Gotcha: a new `WorldStyle` needs a matching case in EVERY exhaustive `switch (generator.worldStyle)`
  provider loader (`ShapeProvider` + `SurfaceProvider`) or you get a null-provider NPE at gen time —
  `OreProvider`/`CoverProvider` have `default:` so they're safe. Proxy check: `grep "case DESTROYED"`.**
- **`/cityfind lot <kind> [tp]`**: locate rare landmark lots by type (zoo, biodome, saucer, balloon,
  castle, oilplatform, radiotower, …) — free substring match on the lot's class name, reusing the
  schematic finder's off-thread ring-search + budget. `/cityfind lots` lists the well-known kinds.

**Decoration blocks pass (2026-07).** The remaining "weave in the new deco blocks" list is cleared:
- **Interiors** (`Support.Furniture` accent vocabulary, MODERN): chandeliers (an `IRON_CHAIN` dropping
  from the ceiling with a hung lantern — new `SupportBlocks.setHangingLantern` sets the HANGING state),
  wall-backed **copper chests**, **candle clusters** (colour variety + the odd lit `CANDLE_CAKE`), on top
  of the existing plant/lamp/pot/amethyst. Note: the mines already place `COPPER_CHEST` loot, so verify
  interior copper chests **above street level** to avoid counting mine loot.
- **Lightning rods**: on **highrise roofs** (`Furniture.rooftopLightningRod`, from the decoration seam —
  self-limits to roofs >16 above street, so only the skyline gets them) and on **radio-tower aerials**
  (`RadioTowerLot` caps every antenna apex, the tallest crowned with an END_ROD beacon + a rod above it
  at y≈193 — the tallest thing for miles, so it genuinely draws the strike).
- **Hanging lanterns in the mines**: `PlatLot.scatterMineLanterns` hangs lanterns (plain/soul/copper)
  from the corridor ceiling so drifts read as worked and lit.
- New materials via `gen_material.py`: `LIGHTNING_ROD`, `ORANGE/LIGHT_GRAY/RED_CANDLE`, `CANDLE_CAKE`
  (`IRON_CHAIN`, `COPPER_CHEST`, `CANDLE`/`WHITE_CANDLE`, lanterns already existed). Verified by probe:
  office carried rooftop rod (y=118) + chains + candles + copper chest; radio tower 3 aerial rods (y=193);
  62–122 hanging mine lanterns. **Building copper weathering: closed — owner is a-ok leaving it as-is.**

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
lockstep with the sweep.

**The old Bukkit `CityWorldAPI` is resurrected too** (Sablednah wrote the original for upstream,
PR #4/#5). Modern `me.daddychurchill.CityWorld.api.CityWorldAPI.lotAt(level, pos) → Optional<LotInfo>`
is the typed successor to its `getFullInfo` — a read-only snapshot of the plan for a chunk (context
family + class, lot style + class, chunk pos, nature %, road count, schematic name, `ShopType`),
derived from the seed-deterministic plan so it needs no generated chunk. `getFullInfo(level, pos)` keeps
the original stringly-typed `Map<String,String>` shape (same keys) for continuity. `/cityinfo` and the
F3 overlay now **read through this API** rather than hand-rolling `getPlatMap→getMapLot`, so command and
API can't drift. `CityWorldShops` stays the focused shop lookup over the same plan.

**Shops now manifest in-world — job blocks landed (v1).** A new `shops` settings group (`enabled`; on
in MODERN, off in CLASSIC) drives `Support.ShopFitter`, a post-decoration pass (modelled on Overgrowth)
that drops the trade's villager job-site block on a classified shop's ground floor — cartography table =
map seller, fletching table = fletcher, smoker = butcher, loom = draper, stonecutter = builder's
merchant, lectern = bookshop, etc. It scans the ground storey for an open cell standing on solid floor,
prefers one backing a wall/shelf, faces it inward, and lays a trade-coloured mat in front; the store's
own registers/shelves are untouched (ShopFitter runs alongside the room populator, doesn't replace it).
Four job blocks (fletching_table, loom, composter, lectern) were added to `gen_material.py`. Verified by
force-load probe: **35/35 sampled shops carried the right job block, 0 misses, 0 exceptions.** The
`Customize` screen, `SettingsExample`, `SettingsDatapack` and `/cityexport` all learned the `shops` knob.

**Rural job-block dressing landed too (corner shops, farm composters, fish ponds).** With `shops` on:
a residential house is occasionally a **corner shop** (`CornerShopLot extends HouseLot`, carries a
`CORNER_SHOP` `ShopType`, so ShopFitter drops its trade's block for free — newsagent/greengrocer/
butcher/fishmonger/apothecary; ~1 in 13 Neighborhood houses); farms get a **composter** at a field edge
(~1 in 5 `FarmLot` chunks, the farmer's workstation, scanning for dry ground so it skips crop water
furrows); and a rare one-off **fish pond** farm variant (`FishPondLot`, like the barn/water tower) — a
dug water pool with lily pads, reeds, a bankside **barrel** (fisherman's workstation) and spawned fish.
Verified over a 341×341 plan-sweep + force-load: 165 corner shops across all five corner trades, 12/12
with a job block; composters present across farms; 40 ponds, all water+fish, most with the barrel (a
couple in odd wet terrain miss it — cosmetic, rare).

**Villagers are employed now (the big one landed).** `SpawnProvider.spawnWorker(...)` spawns a villager
already set to the workstation's trade — cartographer at the cartography table, fisherman at the pond
barrel, farmer at the composter, etc. — via `VillagerData.withProfession(registryAccess, key)` +
`refreshBrain` (verified safe at worldgen). Called from `ShopFitter`, `FarmLot` and `FishPondLot` right
where each job block is placed; rolls `spawnBeings` so keeper density tracks the world's populated-ness.
Each employed villager gets a **role-themed name** — a given name plus an occupational surname keyed to
the trade ("Frank Compass" cartographer, "Roberta Lamb" shepherd, "Bill Butcher"). The surname pools are
built-in and **datapack-overridable** via a new `naming.professionNames` list (`"profession:Surname"`
entries; replace or, with `append`, add — same as the other name lists). Verified: 21 villagers employed
across sampled shops, all `employed=true`, names themed correctly.

**Interior decoration pass — first slice landed.** New `Support.Furniture` is the MODERN furniture
vocabulary (clever-block-trick pieces + the post-1.8 deco palette). Its centrepiece `accentRoom(...)` is
hooked into `FinishedBuildingLot.drawInteriorRoom`, so every furnished room cell in a MODERN building has
a chance at a tasteful floor accent — a potted plant, a fence-and-lantern floor lamp, a decorated pot, an
amethyst sparkle — placed in a clear corner so it never blocks a walkway (CLASSIC untouched). Plus
targeted upgrades: library rooms blend in **chiseled bookshelves**, and lounge couches get a little
**coffee table** (slab-on-fence with a candle/plant). ~13 modern deco blocks added to `gen_material`.
Verified: 2082 accent blocks across 50 sampled MODERN building chunks, clearFound==placed (no silent
misses), 0 exceptions. **NOTE: worldgen entities/floor aren't final mid-construction — decoration that
needs the finished floor belongs in a post-pass (like `ShopFitter`), not mid-room; `accentRoom` works
because the room's own floor row is already solid when `drawInteriorRoom` runs.**

**Decoration wave 2 landed — houses, shop signage, hay (2026-07).** (1) **Houses are furnished**: the
colonial house's empty KITCHEN/DINING/LIVING/BED room styles now call `Furniture.kitchen/dining/living/
bedroom(...)` — a cauldron-sink + stove (smoker on MODERN) + barrel counter, a table with chairs, a couch
+ coffee table, a bed + bedside barrel + lamp, all clear-floor-guarded and MODERN-accented. (2) **Shop
signage**: `ShopFitter` hangs an `OAK_HANGING_SIGN` over the counter with a **random shop name** (new
`OdonymProvider.generateShopName(...)` → "Gibson & Sons — Armourer", "Ye Olde Map seller", "Anderson's Map
seller", drawn from the villager surname pool), plus a storage barrel beside the counter. No sign-NPE.
(3) **Hay**: paddock fields get a bale or two in the *centre* (clear of the fence so animals can't hop
out), and a new `FarmLot.CropType.HAYSTACK` renders a field of stacked bales in rows (in the normal +
MODERN crop pools). Verified: 40 houses richly furnished, 8 shop signs with names, 123 hay blocks / 60
farms, 0 exceptions.

**Decoration remainder — mostly cleared since:** themed shop counters/dressing and **bathrooms** both
landed (bathroom rooms in houses; per-trade counters + hanging signs). Still open/optional: more house
variety, starter trades on employed villagers, richer shop-name patterns. (The rare barrel-less fish pond
the owner explicitly said is fine.)

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
  the bottom — carved through the crossing rails so nothing floats. **Reworked 2026-08** — see the
  playtest-polish wave in "Resume here" above; the landing gantry and freestanding car interior described
  here are gone, replaced by hanging signs, in-frame door thresholds, and trapdoor-hatch floors/roofs.
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

### ✔ Closed: all context families (outland landed 2026-07)

The context ladder in `ShapeProvider_Normal.getContext(PlatMap)` is **fully live — all ten arms**: park,
highrise, construction, midrise, lowrise, neighborhood, municipal, farm, industrial, and **outland**.
The outland arm returns `outlandContext` for the `nature 0.70–0.75` band (`getContext`, ~5% of built
bands), so `OutlandContext` and its lots — `GravelMineLot`, `GravelworksLot`, `WoodworksLot`,
`CampgroundLot`, `MineEntranceLot` — generate in normal worlds. (An earlier draft of this section listed
outland as unwired; that was stale — see "Outland landed" in the Resume log above.)

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
with `StructureOnGroundProvider`. **`WOODWORKS(_OUTPUT)` / `STONEWORKS(_OUTPUT)` are reachable too**
(corrected 2026-08 — the outland lots ARE ported: `WoodworksLot` + `GravelworksLot`, wired into
`OutlandContext`). Probe-verified in a city world: 506 woodworks / 407 gravelworks lots, and across 20
sampled lots each, woodworks yielded 10 chests + crafting tables + furnaces and gravelworks 4 shed
chests. `RANDOM` has no caller outside the Astral styles.

Both former items here are now **DONE** (corrected 2026-08 — they were stale):
- ~~`NatureContext.populateMap` lacks its set-pieces~~ **DONE** — `populateMap` is implemented and, via
  the ported `HeightInfo` height survey, seeds bunkers (+ entrances), the APOCALYPSE vault, and the
  height-special lots (oil platforms, radio towers, flying saucers, hot-air balloons, mine entrances,
  castle). Confirmed by `/cityfind lot <kind>` locating each.
- ~~schematic-backed roundabout centre~~ **DONE** — `RoadContext.createRoundaboutStatueLot` pulls a
  single-chunk `ROUNDABOUT` schematic (a statue) and places it as a `ClipboardLot` (with a random
  rotation), falling back to the generated `RoundaboutCenterLot` only when none is available.
  `PlatMap.placeSpecificClip` is fully implemented (rotation + opt-in per-schematic mirror).

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
| `ChunkGenerator.BiomeGrid` + `block.Biome` | 56 uses, but only **12 files** | ✅ **shimmed** (`compat/Biome`, `compat/BiomeGrid`). The whole tree names only **12 biome constants**, and `BiomeGrid`'s entire used surface is `setBiome(x, z, biome)`. **4 of the 12 no longer exist** — the 1.18 rework deleted every `*_HILLS` variant plus `SNOWY_MOUNTAINS` — so they remap to the nearest survivor (`BIRCH_FOREST`, `TAIGA`, `DESERT`, `SNOWY_SLOPES`); costs colour/mob flavour, not terrain. The real change (CityWorld pushes biomes per column, modern gen pulls via `BiomeSource`) is **DONE** (2026-07/08): custom `cityworld:climate`/`cityworld:terrain` biome sources pull the same seed-deterministic terrain height and classify it, replacing the discarded push — see the biome entries near the end. |
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
- [x] **P6 — Schematics.** *Done: conversion, library, paste command, worldgen placement, multi-format
      loading, drop-in folder, data-fixing, block entities, and **rotation/mirroring** all landed and
      verified. Rotation IS applied — `PlatMap.placeSpecificClip` picks a random 1-of-4 orientation
      (`Clipboard.ROTATIONS`) for every placed building (and roundabout statues via `RoadContext`).
      Mirroring is applied too, but **opt-in per schematic**: it only fires when the `.yml` meta sets
      `FlipableX`/`FlipableZ` (both default false, deliberately — a mirrored building with signs or a
      deliberate front reads backwards). So "flip not used" is a per-schematic default, not a missing
      feature.* Formats read:
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
  Wired into the classic preset. **Extended 2026-08:** the `nature`, `metro` and `sparse` presets now
  point at `cityworld:climate` too (varied biomes verified: 12 distinct in a nature world), and the
  themed styles got their correct fixed biome (sanddunes→desert, snowdunes→snowy_plains, flooded→ocean)
  instead of the placeholder plains. Still on a fixed/placeholder biome by choice: `destroyed` (owner's
  active style — left untouched pending their call), `astral` (needs End biomes), `floating`, `maze`.

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
