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

## ▶ Where this is, and what's next (2026-10-15)

**▶ Resume here (2026-10-30, overnight: 6.10.0 release in flight, 6.11 biome-mod work on master).**
- **v6.11.0 is released** (2026-10-30 evening) on all six lines — tag `v6.11.0` at the bump `7411f2b4`, CurseForge
  9377104–9377109, fleet `DEPLOYED-v6.11.0` (ZARP opts out), wiki pushed: **your biome mods, in the city too.**
  PORTING.md "▶ Resume here — biome mods on both lands" has every measurement. (1) TerraBlender biomes never reached
  CityWorld's own land on 1.21.1/1.20.1 (`instanceof Registry` false there; server-registry fallback). (2) foreign =
  not a vanilla `Biomes` constant (pale garden places); `terrablender:deferred_placeholder` excluded. (3)
  `world.packPreset` (Terralith, Grand Terrain inline biomes, both lands). (4) `LithostitchedBridge` (Regions
  Unexplored, vanilla land; surface-rule pass once per settings object). (5) `world.moddedBiomeGrain` 64: the foreign
  decision per warped cell — patches ≤2 chunks 38%→4%. (6) rivers freeze by the bank biome; quays freeze whenever
  vanilla won't decorate them (`PlatLot.vanillaDecoratesHere`, the one gate). (7) `structures.wildernessOnly` +
  `#cityworld:wilderness_only` judged by a DRY plan without structure reservations (terrain share is NOT a proxy:
  bunkers on a mountain plan as lowrise); strongholds never filtered. (8) 1.21.1/1.20.1 only: CycleButton
  `withValues` before `withInitialValue` (Style button ping-ponged). Test bed `../CityWorld-ReForged-worktrees/exp-1.21.1`
  (mods in `run/mods`, `scratchpad/tools/modprobe.sh`, `patch_sizes.py`). Gates: six self-tests PASS + `--compare`,
  CI 41126015138 green, halt/exit 0 after the positive, 1.20.1 SRG names. Owner playtested CityWorld and vanilla land.
  Not measured: the dry-plan rule in game. **The owner pastes `CURSEFORGE.md`** (6.11.0 headline).
- **v6.10.0 is released** (2026-10-30 01:12) on all six lines — tag `v6.10.0` at the bump `ae5104a0`, CurseForge
  9367760–9367765, fleet `DEPLOYED-v6.10.0` on 12 (ZARP opts out), wiki regenerated and pushed. Gates: six self-tests
  PASS + `--compare`, CI 40982728581 green, halt/exit 0 after the synthetic positive, 1.20.1 SRG names present.
  **The owner pastes `CURSEFORGE.md`** (6.10.0 headline) into the CurseForge page.
- Open items:
- **ZARP** runs the test jar `cityworld-6.8.0-test-a2ee521f+mc26.2.jar` (the CurseForge 6.8.0 jar and older ones sit in
  `LegendQuest ZARP/cityworld-released-backup/`). The owner moves it to the CurseForge 6.8.1 in the app, then deletes the
  test jar and the backup folder. Its instance skips the fleet (`.sablecraft-no-deploy`). Test jars in ZARP are the
  owner's call; ZARP's own session (Remote Control, another machine) owns Threadwork/the pack — pass pack-side bugs
  to it rather than editing its repo. Its world_settings set `spawns.wildAnimals` and `spawns.cityAnimals` true.
- **The owner posts the reply to F4L70** (CurseForge comment, 1.21.1 End freeze with Forgified Fabric API — fixed 6.8.0).
- **The owner should paste `CURSEFORGE.md` into the CurseForge page** (6.8.0, 6.7.0, 6.6.0 headlines; 6.8.2 and 6.8.1
  head "Other recent changes").
- **The owner posts the C2ME reply** to the same player (6.8.2 fixes the End with C2ME).
- **6.10.0 is built on every line, release in progress (2026-10-29/30).** Four things: (1) **Modern/Apocalypse everyday
  loot** — `chests/modern|apocalypse/{building,nightstand,food}` + `_find` twins, chosen at placement by
  `treasures.oddsOfFinds` (1/13, "Finds in drawers"; `LootProvider_LootTable.keyFor(generator, loc, tier, seed)`),
  written per loot dialect by `scripts/gen_style_loot.py` (RUN IT in each checkout after a change; 26.3 differs); the
  vault mess hall takes `modern/food` everywhere. Owner: "loot is looking good". (2) **ATMs** — `Support/Atms` from
  `#cityworld:fittings/atm` (+ `atm_base`) in civic/shop/mall at `shops.oddsOfAtmIn*`; the rules (base block, indoors,
  3-high wall) came from the owner's 1.20.1 look with Modernlife. (3) **Paved reserved sites** —
  `CityWorldAPI.reservePlaza`, `Plats/PlazaLot`, `Site.margin/paved`, roads cross a paved ring
  (`isReservedFromRoads`); self-test `api.reservePlaza`. (4) **1.20.1 only:** `Support/ModernlifeBank` reserves an
  80-block square at spawn (`shops.bankPlazaRadius` 2) on `LevelEvent.CreateSpawnPosition` when `modernlife:atm`
  exists; Modernlife's bank lands two blocks from the player, so the square must hold its flat-land check's corners
  (43 blocks out) — radius 1 put a corner on a road's lamp post and the bank walked 24 blocks (overhead shots).
  **Modernlife (no space, CF 1623799, 1.20.1 Forge, needs Architectury + Automobility) ≠ Modern Life (space,
  Modrinth `modern-life`, furniture, owner's to-do); both are mod id `modernlife`.** The owner's `1.20.1  Forge`
  instance carries `modernlife-1.1.1-forge.jar` and `automobility-0.4.2+1.20.1-forge.jar` for this test.
- **TODO (owner, 2026-10-27): make (almost) every constant a setting.** *"Any time we pick a number - any constant
  - should be configurable - we're just picking the default ... only where it would break or be unworkable we keep
  things fixed."* A standing to-do, not started: sweep the gameplay constants (odds, rates, counts, sizes, depths,
  heights) into world settings with today's value as the default, a CURSEFORGE-CONFIGURATION.md row each, and a
  Customize widget whose picker contains the default. Mind the 16-field codec cap (new groups, not squeezed ones).
  The first one is `treasures.oddsOfFinds` (6.10.0). New features add their numbers as settings from the start.
- **Furniture compat to look at: Modern Life (WITH a space, Modrinth `modern-life`, mod id `modernlife`, 1.20.1
  Forge + 1.21.1 NeoForge)** has a lot of good furniture (owner, 2026-10-29). Not the same mod as **Modernlife**
  (no space, CurseForge 1623799, by Radinsaeb12, 1.20.1 only: the economy mod whose `modernlife:atm` 6.10 places).
  ⚠ Both use mod id `modernlife`, so a pack can't install both, and a tag entry under `modernlife:` must name a
  block only one of them has (`atm` is Modernlife's alone).
- **6.9.0 confirmed on the full ZARP pack** (ZARP session, 2026-10-26: seed 20261025, apocalypse, C2ME on): the vault
  swaps are gone, ~20 man-made blocks differ (single contested cells), 900 chunks in ~27 s with C2ME. The owner moves
  ZARP to 6.9.0 through the app.
- **CityWorld Compass**: a hardening commit (search frees the player in the worker + on logout/server stop) is pushed on
  all six branches, unreleased — ship it with the Compass's next real change as 1.0.1.
- **JourneyMap 6.0.10 for 26.3 works on NeoForge 26.3.0.58** (it needs .45+; checked 2026-10-18: CityWorld's map plugin
  initialises, self-test PASS). It replaced 6.0.8 in `mc26.3/run/mods`, so 26.3 self-tests no longer set JourneyMap aside.
  The owner's `26.3` instance still has 6.0.10 renamed `.jar.disabled`; it can go back on.
- **CityWorld Compass 1.0.0 is released** (2026-10-18; `../CityWorld-Compass`, https://github.com/Sablednah/CityWorld-Compass,
  CurseForge project 1752214 https://www.curseforge.com/minecraft/mc-mods/cityworld-urban-compass, files 9240462–9240467; its own CLAUDE.md). It requires CityWorld `[6.6.0,)` and uses only
  `CityWorldAPI.findables`/`find`/`vaultEntrance`: change those with care, and message nobody — this session owns both.
- **Confirm 6.4.1 in the `1.20.1  Forge` instance log:** no "could not read TerraBlender's End biomes" lines. The dev
  1.20.1 runtime has no TerraBlender to show it.
- **The owner's 26.2 world-creation hang is fixed in 6.4.0** (`worldgen/Waiting`). It was a race the dev server never
  reproduced, so if a world creation hangs again, take a jstack before anything else.
- **The Zoo mod** (`../CityWorld-Zoo`, its own session "zoo") builds against 6.4.0's `ZooCatalog`. Message that session
  if the zoo schema moves.
- **Better biome blending** — the owner's to-do, not scoped (PORTING.md, rivers section).
- **The ruined Nether twin's rivers** — owner hasn't chosen lava / dry ravines / none; it has none today.
- **Vanilla land's coasts** could take the coastal quays/beaches (needs the lift-aware drawing ShorelineLot has);
  vanilla moorings draw as the slip (a jetty would stand in the river's own chunk).
- **The self-test does not cover vanilla land or rivers.** Verify them by probe on a seed: vanilla land by setting
  `cities.vanillaTerrain: true` in `world_settings/modern.json` for the run (revert after), rivers with the river
  checks below and `-Dcityworld.rivers.off=true` as the control.
- Tools from this arc: probes `survey:rivers`, `survey:treasure`, `survey:harbour`, `find:QuaysideLot`,
  `find:ShorelineLot`; `-Dcityworld.rivers.quay=<KIND>`, `-Dcityworld.rivers.find=<FIND_*>`; `/cityfind river [tp]`;
  `scripts/river_{spill,pillars,depth,hollows}.py` (region checks), `scripts/count_worlds.py` (promo stat).
- The owner's test seeds: CityWorld land 5670300453504745606 (their mountain, the falls, both mouths), vanilla land
  1688298913692546204 (the bridge at x 704–751 z −1000; quays at 661,−824 and 712,−1095).

**v6.9.0 is released** (2026-10-26) on all six lines — tag `v6.9.0` (`5d1fc61e`), CurseForge 9331893–9331907, fleet
`DEPLOYED-v6.9.0` on 11 (ZARP opts out): **the same seed builds the same city.** ZARP found two same-seed runs built
different cities. A lot decorated from ONE random stream, and many steps roll only for a cell they find free; a
neighbour chunk's decoration (timing) filling one such cell re-rolled everything after it (traced with a temporary
counting Random + per-draw stacks: `Furniture.tableLamp` -> `clearFloor`, then the roof picked at draw 169 vs 181).
Fix: `Odds.fork()` (child seeded from ONE parent draw), `PlatLot.phaseDice` (each pass after the lot's own drawing
on its own seeded stream), `withForkedDice` around house rooms, office interiors and the bare-floor sweep, the ore
pass, each mine level and each of its steps; the house roof pick is forked before the rooms; mine slab coins are
tossed for every cell; Overgrowth forks per column/nook/wall/sapling/dripstone try, LushCaves per column;
SchematicLibrary sorts the custom folder. **Rule: a roll that only happens when a cell is free goes on a fork, or is
rolled unconditionally.** Measured as CityWorld-placed blocks differing between two same-seed runs: ZARP's seed
(apocalypse, 26x26) 1964 -> 97, dev seed (modern, 30x30) 5047 -> 2546; the rest is single contested cells and mod
decoration. New chunks differ in detail from 6.8.x on the same seed; plans are unchanged (`--compare` agrees). Gates:
six self-tests PASS, CI green on the bump heads, halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated.

**v6.8.2 is released** (2026-10-25) on all six lines — tag `v6.8.2` (`608e7768`), CurseForge 9324321–9324326:
**C2ME compatibility.** The End generated nothing with C2ME (a player's 1.21.1 report, mclo.gs Ao9ZkhN): C2ME's DFC
compiles the router's density functions, and a compiled function's `mapAll` casts each visited cache node to its own
`IFastCacheLike`, so `EndTerrain`'s `ColumnMemo` threw `ClassCastException` at the biome stage. `EndTerrain
.seeThroughCompiled` wraps the visitor in a Proxy that also implements C2ME's `IBlendingAwareVisitor` (by name), and C2ME
then maps the vanilla tree it keeps behind each compiled function; the proxy matches the visitor method by SIGNATURE,
since 1.20.1's runtime name is SRG. 26.3's End (unbaked router + `createClimateSampler`) was unaffected. Also: the loot
pass is caught per container (Refurbished's cutting board NPEs in `setItem` with a null level and emptied the rest of
its chunk). Measured on 26.2 (WideLoad 30x30): 71–76 s without C2ME, 24–32 s with; the block diff with/without is the
same size and kind as two runs without (vanilla decoration noise). Every line's self-test passes with its own C2ME in
`run/mods` (none exists for Forge 1.20.1; jars are on Modrinth `c2me-neoforge`, removed from dev mods afterwards). The
ZARP session is to trial C2ME on 26.2 (owner's call). The C2ME OpenCL module only replaces vanilla's noise/biome stages
(nothing for CityWorld land), is experimental and needs Java 25: not pursued.

**v6.8.1 is released** (2026-10-25) on all six lines — tag `v6.8.1` (`d3fe5434`), CurseForge 9316111–9316116, fleet
`DEPLOYED-v6.8.1` on 11 (ZARP opts out; it holds `cityworld-6.8.0-test-a2ee521f+mc26.2.jar`, CurseForge 6.8.0 in
`cityworld-released-backup/`): **twins take their state from their own settings entry** — a twin plans with its
source's settings (street for street, names and all) but a dimension that names a `settings` entry now takes working
lights, overgrowth and the mobs lists from it (`CityWorldSettingsData.withStateOf`). ZARP's pre-apocalypse
`cityworld:city` (→ `cityworld:modern`) had the ruin's lights off. Presets' Nether and End name no entry, so they follow
the overworld (owner: right), and a pack can give them one. Probe of `cityworld:city` under an Apocalypse overworld:
0 lit / 51 lamps → 174 lit. Gates: six self-tests PASS, CI green, halt/exit 0 after a synthetic positive, 1.20.1
reobfuscated.

**v6.8.0 is released** (2026-10-22) on all six lines — tag `v6.8.0` (`1c61b819`), CurseForge 9285277–9285282, fleet
`DEPLOYED-v6.8.0` on 11 (ZARP opts out; it holds the test jar `cityworld-6.7.0-test-f432d5be+mc26.2.jar`, owner
tested animals there and updates it via the CurseForge app): **vanilla's generation-time animals on CityWorld land**
(`spawnOriginalMobs`: `spawns.wildAnimals` default on for non-built chunks, `spawns.cityAnimals` default off for built
chunks with no grass column > street+4; NaturalSpawner called directly, NEVER via `vanillaOverworld()` on CityWorld land
— that hands the biome source to vanilla; 26.3's spawner takes a BlockPos; measured 4 → 171 animals on a Nature+decayed
seed; city animals are sparse because vanilla animals need grass) and **the End froze with Forgified Fabric API** plus a
mod adding End biomes through it: `EndTerrain` built its Climate.Sampler by hand, and Fabric's TheEndBiomeSource pick
needs the seed its mixin hangs on RandomState samplers ("MultiNoiseSampler doesn't have a seed set"); `carryFabricSeed`
copies it by method name (reproduced on 1.21.1 + FFAPI 0.116.15+2.3.6 on NeoForge 21.1.264 with a reflective test
highlands biome + an End probe at chunk 100,0: 3 errors → 0). FFAPI alone registers no End biomes, so it does NOT
reproduce without one. 26.3 uses `createClimateSampler` and is unaffected. Gates: six self-tests PASS, CI green, halt/exit
0 after a synthetic positive, 1.20.1 reobfuscated.

**v6.7.0 is released** (2026-10-21) on all six lines — tag `v6.7.0` (`ce041437`), CurseForge 9279847–9279854, fleet
`DEPLOYED-v6.7.0` on 11 (ZARP opts out; it holds the pre-release test jar `cityworld-6.6.0-test-c988b3c7+mc26.2.jar`,
the CurseForge 6.6.0 jar in `cityworld-released-backup/` — the owner updates it through the CurseForge app):
**Customize presets** (`client/CityWorldPresets` + `CityWorldPresetsScreen`, `config/cityworld/presets/<name>.json`:
style, realms, the settings in datapack form; a pack's style lock keeps its style), **All on / All off** on the
Structures page, **a style change in Customize loads that style's world-preset settings** (`CityWorldClient.styleBases`
reads every `cityworld:` world preset; `CityWorldSettings.styleSettings` applies the style), **Apocalypse defaults
airborne structures and working lights off** (soft: `styleDefaults` + `world_settings/apocalypse.json`), **road trees on
a slab deck replace the slab** (owner's ZARP bridge), and **nothing carries into the next world in the same game**
(audit after ZARP's Threadwork bug: bunker materials and the subway link memo were statics, now weak per-generator maps
from the seed; the map-plan choice resets on logout/server stop). Gates: six self-tests PASS, CI green on the bump heads,
halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated; presets/All on/style switch driven on Vivo clients (26.2,
1.20.1). The Compass got matching hardening (search frees the player in the worker; pushed, unreleased: a "busy forever"
bug was suspected but a Vivo world-A/world-B test could not produce it).

**v6.6.0 is released** (2026-10-18) on all six lines — tag `v6.6.0` (`394e84df`), CurseForge 9240305–9240312, fleet
`DEPLOYED-v6.6.0` on 11 (ZARP opts out): **the find-anything API** (`CityWorldAPI.findables` → `Findable(id, group,
title)`: city, 33 places, 14 shop trades, 11 districts, every schematic by Title; `find(level, from, id, radius, limit,
budget)`; `findLots(…, Predicate<LotInfo>, …)`; `nearestCity`) for the CityWorld Compass, with each search ring's
platmaps planned side by side (`planAll`, a 2–4 thread daemon pool; hospital at ~1000 blocks 9.1 s → 2.9 s), and
**deep vault loot**: containers below level 3 had named `vault_<room>_floor4..7`, which do not exist (empty chests,
Lootr chat spam, ZARP's report). `keyFor` clamps to `DEEPEST_LOOT_TIER`, and `VaultLot.lootTier(k, levels)` spreads the
levels over the four tiers, entry included (owner's spec: 8 levels = entry, entry, 1, 1, 2, 2, 3, 3). Also 26.2/26.3
drop `logoFile` (NeoForge 26.2+ stopped at a warning screen for it). Self-test `checkFindables`,
`checkVaultLootTiers` (proved failing without the clamp). Gates: six self-tests PASS, CI green on the bump heads,
halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated.

**v6.5.0 is released** (2026-10-16) on all six lines — tag `v6.5.0` (`c47698ab`), CurseForge 9206957–9206962, fleet
`DEPLOYED-v6.5.0` on 11 (ZARP opts out): **schematic fit-outs** (`worldgen/SchematicFitOuts`, registry
`cityworld:schematic_fitout`; PALETTES.md "Schematic fit-outs"). Block swaps (`/setblock` syntax) go through a paste
processor keyed on the processor's stored `raw.pos()`, so vanilla turns them with the building. Item frames (`/give`
syntax) are hung by `Clipboard.hangFrames` after decay, only where the cell is clear and the wall stands. Probe
`find:schematic:<name>`; self-test `checkFitOuts`, whose sample item string differs per line. Per-line drift: 26.x
`ItemParser.parse` → `ItemInput`; 26.2+ processor is an interface (`process(…, template)` + `codec()`); 26.3
`worldRegistry`; 1.20.1 `parseForItem` → NBT, and its processor override ships as SRG `m_7382_` (checked). It also
carries the TerraBlender quieting to the other five lines. Gates: six self-tests PASS, CI green on the bump heads,
halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated.

**v6.4.1 is released for 1.20.1 only** (2026-10-15, tag `v6.4.1` at the master docs commit `7041988b`, bump
`aa250e4b`, CurseForge 9193319, `1.20.1  Forge` instance `DEPLOYED-v6.4.1`): TerraBlender for 1.20.1 (3.0.1) has no
`EndBiomeRegistry`, and `TerraBlenderBridge.endBiomes` logged a stack trace for every End biome source built (840
lines in one log) — a `ClassNotFoundException` there is now silent. The fix (`1da2a5ad`) is on every branch and
pushed; the other lines ship it with their next release.

**v6.4.0 is released** (2026-10-15) on all six lines — tag `v6.4.0` (`e6c6686b`), CurseForge 9184970–9184975, fleet
`DEPLOYED-v6.4.0` on 11 (ZARP opts out): **zoos are data** (`worldgen/ZooCatalog`: datapack registries
`cityworld:zoo/animal` + `cityworld:zoo/habitat`, bound at level load, sorted by id; the ten residents ship as data;
format in PALETTES.md "Zoos") — the consumer is the owner's **CityWorld-Zoo** mod (`../CityWorld-Zoo`, mod id
`cityworld_zoo`, its own Claude session "zoo": message it if the schema moves) — and **a world-creation hang fixed**:
`CompletableFuture.join()` on a ForkJoin worker runs other queued tasks, so a worker building a river region ran a
neighbour chunk's biome task that waited for that same region (the owner's 26.2 world, jstack at "Preparing spawn
area", since 6.0.0). **Every build-once wait goes through `worldgen/Waiting.join`** (parks via `managedBlock`) —
never a bare `join()` in worldgen. The dev server never hit the race in four fresh worlds; a standalone one-worker
demo deadlocks with `join()` every time and never with `Waiting.join`. Gates: six self-tests PASS, CI green on the
bump heads, halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated.

**v6.3.0 is released** (2026-10-12) on all six lines — tag `v6.3.0` (`1b243627`), CurseForge 9146620–9146626, fleet
`DEPLOYED-v6.3.0` on 11 (ZARP has a hand-copied pre-release test jar of the same code, `915a2f5b`, and its 6.0.0 jar in
`LegendQuest ZARP/cityworld-released-backup/`): **radio towers have a hut you can walk into** (`RadioTowerLot`: 4x4
room three high, door by the ladder, pooled chair + table, lantern, one chest on `cityworld:chests/radio_tower` + the
`_extra` hook — the ONLY container on that table, and decay runs before the fit-out, because ZARP's quest "The
Signal" adds guaranteed loot to it by an `add_table` loot modifier), and **hospital medicine chests keep their
medicine** — a chest with a loot table AND real items saves no items in vanilla, and Lootr's swap
(`setBlock` flag 2) spills them (153 potions + 51 paper on one hospital's floors, 0 after). Gates: six self-tests
PASS on the code heads, CI green on the bump heads, halt/exit 0 after a synthetic positive, 1.20.1 reobfuscated
(489 SRG methods). **NeoForge 26.3 left beta at 26.3.0.76**; the 26.3 line compiles against it (ships on .58-beta,
range `[26.3.0.20-beta,26.4)` admits it).

**v6.2.0 is released** (2026-10-10) on all six lines — tag `v6.2.0` (`87005e1f`), CurseForge 9123060–9123068, fleet
`DEPLOYED-v6.2.0` on 11: **Working lights off puts out every light CityWorld draws** (all three block seams —
`InitialBlocks.put`, `compat/Block.setBlockData`, schematics — run `CityWorldDataMaps.substitute` with lights off:
the new `cityworld:unlit` data map, then any `lit` property false), **the vault keeps its own power**
(`CityWorldDataMaps.ownPower(Runnable)`, a thread-local scope around vault drawing), and **vaults are 4–8 levels**
(`VaultLot.levelsFor`, per vault by seed and platmap, fewer where the world floor is near) with lights fading
bright → dim → dead by floor (`lightKind`: sea lantern / lit oxidized copper bulb, light 4, low enough for spawners
/ unpowered redstone lamp; 1.20.1 has no copper bulb, its dim light is magma), wear and spawners +2/10 and +15% per
level. Self-test records `vault.levels`. Gates: six self-tests PASS (1.21.1 once died on the vanilla hang watchdog
with two tests running, passed alone), CI green on the bump heads, halt/exit 0 on all six after a synthetic positive,
1.20.1 reobfuscated (488 SRG methods; devlibs 0). Newest NeoForge compiles fine on every line except 26.2.0.90,
which fails in NeoForge's own Minecraft recompile (as .88 did) — 26.2 stays on .59.

**v6.1.1 is released for 26.3 only** (2026-10-08 evening, tag `v6.1.1` at `b952aaa0`, CurseForge 9101834, `26.3` instance `DEPLOYED-v6.1.1`): the owner's users asked for NeoForge 26.3.0.58; the 26.3
line now builds on .58-beta (`02166746`, bump `28e49aac`), range unchanged `[26.3.0.20-beta,26.4)`, self-test PASS on
.58. ⚠ **JourneyMap 6.0.8 for 26.3 does not load on .58** (FML 12 removed `ModConfig.Type.SERVER`; CityWorld
registers only STARTUP) — the dev `run/mods` copy had to be set aside for the self-test, and users on .58 will hit
it too. Single-line release, the 5.15.2 pattern: tag at a master docs commit, CI dispatched by hand.

**v6.1.0 is released** (2026-10-08) on all six lines — tag `v6.1.0` (`af9d1f1d`), CurseForge 9100379–9100385, fleet
`DEPLOYED-v6.1.0` on 11: **warm mountains are no longer snow-capped.** A player's "cold next to tropical with no
transition" was the biome matrix, not the climate noise: warm highland was windswept hills/forest, which vanilla
snows above ~y 120, directly above sparse jungle and savanna plateau hills. `CityWorldClimateBiomeSource.highland`/
`peak` now follow vanilla's `OverworldBiomeBuilder` (windswept for cold/temperate only; warm → windswept savanna /
forest / sparse jungle; hot → eroded badlands / windswept savanna / jungle; peaks stony above warm). Measured on seed
2884287148936112549 over 8 km: snow-to-hot borders 1,207 → 126, patch sizes unchanged, 3.3% of chunks change.
Owner tested in game ("much better"). **The offline loop:** `-Dcityworld.probe=survey:biomes` (biome + temperature
maps, grid CSV), `scripts/biome_seams.py`, `scripts/biome_matrix_sim.py` (a Python copy of the matrix scored over
the CSV; its `current` must match the probe pixel for pixel, then the Java must match `candidate` the same way),
`scripts/biome_map_render.py` (the before/after sheet). ⚠ `selftest.sh --compare` reads only master's
`build/selftest/*.json`: copy each worktree's fresh report in first, or it compares yesterday's. Gates: six
self-tests PASS and `--compare` on fresh reports, CI 37810746511 green, halt/exit 0 on all six (synthetic positive
read 2), 1.20.1 reobfuscated (62 SRG names), 26.3 compiles against NeoForge 26.3.0.57-beta (ships on .33).

**v6.0.0 was released** (2026-10-07) on all six lines — tag `v6.0.0` (`5dad4495`), CurseForge 9092756–9092761:
rivers on CityWorld's own land (falls, hollows and finds), the waterside by district on both lands (quays, slips,
loading quays, harbours, beaches, lighthouses, rustic jetties), harbour districts, vanilla-land bridge and quay
fixes. Major version on the owner's call: cities in vanilla Minecraft plus rivers is a new way to play, and
5.19 vanilla-land worlds lay out new chunks differently. Gates: six self-tests PASS and `--compare` agrees on the
pre-bump heads, CI green on the bump heads, halt/exit 0 on all six (synthetic positive first), 1.20.1 reobfuscated.
Sample add-on `all-vanilla-structures-1.0.0` is its own GitHub release.

**Rivers on CityWorld's own land are committed on all six lines (2026-10-06), unreleased** — owner signed off
the look; PORTING.md "▶ Resume here — rivers on CityWorld's own land" has the design, the traps (per-column
rules make pillars; never touch columns below sea level) and the tools. Quays, harbours, lighthouses, city
beaches and flat street crossings followed overnight (2026-10-07), then a day of fixes from his walks (waterside
by district on both lands, harbour districts, vanilla-land bridges and quays, mossy waterfall hollows) — he signed
it off as "looking good". All on six lines, unreleased; master pushed, the five branches not. Next: a release (5.20.0).

**v5.19.1 is released** (2026-10-05) on all six lines — tag `v5.19.1` (`1853f507`), CurseForge 9064267–9064273: vanilla
land failed every chunk with TerraBlender (BoP) or Alex's Caves, because both hook vanilla's `MultiNoiseBiomeSource`
and set it up only for real dimension stems — the inner source answered null. Now `TerraBlenderBridge
.initializeOverworld` and `ModdedBiomeSources.prepareOverworld` (Alex's Caves: key map, cave biomes, seed) prepare it,
the handover falls back to CityWorld's biome on a null, and the river test takes null as no river. Neither mod runs
in the 1.20.1 dev runtime: the owner's instance was the test (both mods' biomes now appear in vanilla land).

**v5.19.0 was released** (2026-10-04) on all six lines — tag `v5.19.0` (`653bff89`), GitHub release with six jars,
CurseForge files 9061385–9061390, fleet `DEPLOYED-v5.19.0` on 10 (`MobHealth - Forge` was running: rerun
`scripts/deploy-fleet.sh --version 5.19.0 --only "MobHealth - Forge"`; ZARP opts out). It is: **vanilla land**
(Customize's Land row, settings group `cities`: cities like villages in a vanilla world, each on its own level,
lifted at the block seam by `InitialBlocks.yShift` + `worldgen/ShiftedRegion`; rivers kept vanilla's and navigable,
`ShorelineLot` quays and natural banks; owner's playtest on his seed passed) and the **block substitutes** data map
(`cityworld:substitute`, both seams + schematics). The **Neutral Palette add-on** is its own GitHub release
(`neutral-palette-1.0.0`, `packs/neutral-palette/`) and **never goes on CurseForge** (CF would serve it as the
latest CityWorld) — `curseforge.yml` now runs only for `v*` tags, and `gh release edit v5.19.0 --latest` puts
CityWorld back as GitHub's latest after an add-on release. Gates: six self-tests PASS, `--compare` agrees, a
vanilla-land city generated on every line (entities lifted on 1.20.1 and 26.3), the add-on measured on 1.21.11,
1.20.1 and 26.3, CI green, halt/exit 0 on all six after a synthetic positive, 1.20.1 reobfuscated. Per-line drift
for the port is in PORTING.md.

**v5.18.0 is released** (2026-10-02) on all six lines — tag `v5.18.0` (`44bb0d4b`), GitHub release with six jars,
CurseForge files 9039342–9039349, fleet `DEPLOYED-v5.18.0` on all 11 deployable instances (ZARP opts out). It is:
**Farmer's Delight support** (kitchens from its cabinets/stove/pots/boards, feasts on dining tables, rice
paddies, rich soil, compost, crates and bales on farms, canvas shop signs — all tag pools, empty and odds-free
without the mod; the mod exists for 1.20.1 and 1.21.1 only, and **its 1.21.1 jar lives in `mc1.21.1/run/mods`**,
so that line's self-test runs with it); **street shops stock their trade** (`chests/shop_<trade>` x14, written
per loot dialect by `scripts/gen_shop_loot.py` — re-run it on `mc1.20.1` and `mc26.3` after picking a change to
it); **a trade per shop chunk** (`StoreBuildingLot.pickShopType`, by position; it was one per building); and
cutting boards hold food instead of the building's loot. Gates: six self-tests PASS and `--compare` agrees on
the pre-bump heads (0 loot tables failing to parse on every line), CI green on the bump heads, halt/exit 0 on
all six (synthetic positive first), 1.20.1 jar reobfuscated, 26.3 compiled against NeoForge 26.3.0.41-beta, and
three owner playtest rounds on 1.20.1. PORTING.md "▶ Resume here — Farmer's Delight". **`scripts/
gen_furniture_tags.py` must only be run with `--static-only`** (a full run drops pools: the scanned instance no
longer has every mod). The owner pasted the 5.18.0 CURSEFORGE.md into the CurseForge page (2026-10-02).

**Open items, none started (2026-10-02) — the owner's playtest is the queue:**
- **ZARP `checkSite`**: a dry-run "would `reserveSite` work here" API was offered (two seeds got no camp: no
  vault within 1500 blocks); the owner has not said yes or no.
- **Astral nexus / map rooms** (Ed's TODOs): parked by the owner "for another one".
- **Farmer's Delight leftovers**: its loot modifiers only reach vanilla tables (CityWorld's `_extra` hooks are
  the seam); rope, safety net, rope fence and baskets unused; skillets stay empty (they refuse an insert through
  the item handler); mall grocer/bakery produce and pies are coded but no mall fell in the measured area.
- **`checkDirectLoad` covers one museum district on MODERN.** A wider one-off (40x40 chunks, MODERN and
  APOCALYPSE, on mc26.2 with its mods) passed on 2026-10-01; widening the permanent check was not done.
- **`mc1.20.1`'s copy of `gen_furniture_tags.py` writes to `tags/block/`** (that line uses `tags/blocks/`): its
  tags arrive by cherry-pick. Do not run the script there.
- The owner's `1.20.1  Forge` instance has two test jars renamed `.jar.disabled` at his request
  (`allstructures-cityworld-test`, `cataclysm-cityworld-compat`). **Leave them off** (his word, 2026-10-02) —
  not an open item, and not something to "restore".

**Tools this arc added to `scripts/`** (each was a scratchpad helper first, and each paid for itself):
`selftest-one.sh <checkout> <jdk> <port>` (one self-test + kill the leftover server by its cwd; two at a time at
most), `scan_jar.sh <jar>…` (the shutdown/Timings/debug gate — prove it on a positive first; it now errors on a
jar it cannot open instead of printing zeros), `wideload.sh` + `debug/WideLoad.java` (FORCED tickets on a block of
chunks at once: the way to reproduce a decoration deadlock and to generate an area for measuring),
`region_tally.py <region dir> <id prefix>` (exact block counts by palette decode), `gen_shop_loot.py` (the
per-trade shop tables, in the checkout's loot dialect).

**v5.17.0 was released** (2026-10-01 night) on all six lines — tag `v5.17.0` (`96b0c1be`), GitHub release with six
jars, CurseForge files 9031937–9031942, fleet `DEPLOYED-v5.17.0` on all 11 deployable instances (ZARP opts out).
It is four things: the **Structures page** (Customize screen → every installed structure set, vanilla's and
mods', on/off; stored as settings `structures: {allow, deny}` over `#cityworld:allowed`; twins follow the
overworld), the **MCA fix** (villagers spawn as `STRUCTURE` residents, so Minecraft Comes Alive converts them),
the **museum deadlock fix** (5.16.0 could hang world creation at "Preparing spawn area" — see "A hang at
Preparing spawn area" below; the self-test's `checkDirectLoad` is the new detector), and on `mc1.20.1` only the
**pack lock config read at startup by hand** (Forge read it before loading it: silent defaults in a shipped jar).
Gates: six self-tests PASS and `--compare` agrees on the pre-bump heads, CI green on the bump heads, halt/exit 0
on all six (detector proved on a synthetic positive), 1.20.1 jar reobfuscated, 26.3 compiled against NeoForge
26.3.0.40-beta, the screen driven on real clients (Vivo) on 1.21.11, 1.20.1, 26.2 and 1.21.1, and the owner's
1.20.1 playtest (world creates, ticked structures appear). His test jars `allstructures-cityworld-test` and
`cataclysm-cityworld-compat` in the `1.20.1  Forge` instance are renamed `.jar.disabled` at his request.
PORTING.md "▶ Resume here — the Structures page and MCA".

**v5.16.0 was released** (2026-10-01) on all six lines — tag `v5.16.0` (`dbfc155b`), GitHub release with six jars,
CurseForge files 9028088–9028093, fleet `DEPLOYED-v5.16.0` on all 11 deployable instances (ZARP opts out and
takes the CurseForge jar). It is the round built from Ed Churchill's own upstream TODO comments — missing roads
(`oddsOfMissingRoad`, dead since 2012), lane lines, ruined tunnels, office atria, helipads, split-level and modern
houses, park gazebos/ponds/benches, cocoa plantations, farmyard wells, climbable bunker towers, monuments on the
hills and in the civic squares — plus museums (four drawn fossil skeletons, exhibits that are worn loot from three
item tags), the reserveSite ring slope for ZARP, scattered loot in furniture-mod storage, and 26.3's red shrub and
shelf mushroom in the overgrowth. Gates: six local self-tests PASS and `--compare` agrees (before the comment
sweep and bump), CI green on the bump head, halt/exit 0 on all six (detector proved on a synthetic positive),
1.20.1 jar reobfuscated, 26.3 compiled against NeoForge 26.3.0.39-beta (the FML `ModConfig.Type` rename does not
touch us: we register only `STARTUP`). PORTING.md "▶ Resume here — Ed's TODOs, museums and monuments" has the
design of each and what each cost. Parked by the owner: the Astral nexus rooms; the boat (his schematics cover it).

**v5.15.3 is released** (2026-09-29) on all six lines: `reserveSite` levels to `y` (5.15.1/5.15.2 gave `y + 1`;
old site files load as ground `y + 1`, and Threadwork passes `y - 1` below 5.15.3), mall cafe stair seats face
their tables, and structure_fit entries are guarded by `mod_loaded`. Tag `v5.15.3` (`6a7b2f91`), CurseForge
9012676–9012681, fleet deployed (branch instances stamped with a sha because the mall fix landed after the bump).
26.2 did not compile against NeoForge 26.2.0.88, but the failure was in NeoForge's own `createMinecraftArtifacts`
recompile, not ours, and no class was removed between .59 and .88; 26.2 still builds on .59.

**v5.15.2 is released for 26.3 only** (2026-09-29): NeoForge 26.3.0.20 renamed the datapack-registry event, so
earlier 26.3 jars crash on current NeoForge; the 26.3 line now builds on .33 and requires .20+. CurseForge 9008253.

**v5.15.1 is released** (2026-09-29): API-only, from the ZARP ask. `CityWorldAPI.findLot(s)`,
`findVaultEntrances`/`vaultEntrance` and `reserveSite` (a persisted, levelled, vault-safe camp site,
`worldgen/ReservedSites`). CurseForge 9007687–9007694, fleet `DEPLOYED-v5.15.1`. PORTING.md "the camp-site API".

**v5.15.0 was released** on all six lines — tag `v5.15.0` (`6dcc8e8d`), GitHub release with six jars, CurseForge
files 9003156–9003161, fleet `DEPLOYED-v5.15.0` on all 11 deployable instances (ZARP opts out). It is the
subway (stations, long links and loops, interchanges, the sloped loop joining an interchange's dead ends),
shopping malls (wings, car parks, lit and dressed shops, per-position loot), and the vault by depth. Gates: six
self-tests PASS, `--compare` agrees, CI green on the bump head, halt/exit 0 on all six (detector proved on a
synthetic positive), 1.20.1 jar reobfuscated (SRG names present).

**⚠ Modrinth no longer publishes on release.** Modrinth rejected the projects under its rule 6.1 (AI-generated
logos/descriptions); the owner won't fight it. `modrinth.yml`'s `release:` trigger is commented out
(`workflow_dispatch` remains). Do not suggest resubmitting.

**NEXT: nothing queued** — the owner's playtest is the queue. PORTING.md "▶ Resume here" sections carry the
subway, mall and vault designs and the playtest rounds that shaped them.

## ▶ The subway, in one breath (shipped in 5.15.0)

A `SubwayStationLot` per urban platmap (claimed first, beside a road, off the road lines 2/7), and every tunnel
chunk a pure function of two stations (`Subway.at`) — east-west lines 24 under the street, north-south 32, so
lines never junction and a station on both is an interchange; an interchange with one line each way joins its
dead ends in a three-chunk sloped loop (`Subway.Ramp`; sloped rails only on a bend's straight arms). Links reach
the first station up to `REACH` (6) platmaps away. **Planning never looks at a neighbour platmap; drawing does**
(`stationNear` → `getPlatMap` at decoration time only). Modern-family only; its own `subways` settings group
because `Features` is at the 16-field cap. `scripts/region_slice.py` (plan / x-section / z-section of a saved
region as characters) is how its shape was read back — and how the owner's hand fixes in his save are diffed
against a regenerated chunk of the same seed.

## ▶ Before that

**v5.14.0 is released** (2026-09-25 evening) on all six lines — tag `v5.14.0` (`1513c89c`), GitHub release
with six jars, CurseForge files 8974359–8974364, Modrinth `XouovttV gH5dPP55 RE5TKg5L QFtsFBcV SS1UebU3
SDDtTAEJ`, fleet `DEPLOYED-v5.14.0` on all 12. Gates: six self-tests PASS, `--compare` agrees, CI green on
every bump head, halt/exit scan 0 on all six — **proved on a synthetic positive, because no real
known-positive jar exists any more** (both 5.8.0 jars on disk were rebuilt after the fix). The ZARP pack
(LegendQuest ZARP, 26.2; its own Claude session) takes only released CurseForge jars and was told.

**NEXT: nothing queued.** The owner's playtest is the queue, as always. Cavern density: confirmed good
(2026-09-30). `ContainerLoot` is now ONE source on every branch; what differs per loader (table key and lookup,
the item-handler capability) is the per-branch `compat/Loot` — keep the branch's copy on a conflict, like
`compat/Material`. Unbuilt ideas from Ed's own upstream TODOs (lane lines, tunnel decay, helipad, park
pond/gazebo, lowland statue, dead `oddsOfMissingRoad`, ...) were swept 2026-09-30 and offered; none asked for.

**What 5.14.0 is** (2026-09-24 evening → 25th; PORTING.md "the owner's next four mods" has the measurements):
- **Four mods from his 1.20.1 instance, read from their jars with `unzip`/`javap`, never wikis.** Alex's Caves
  reaches the overworld only through a mixin on `MultiNoiseBiomeSource`, so CityWorld's biome source never
  had it: five of its biomes now join `#cityworld:cave_pool` with bands `-64..40` (its cave SHAPES are
  `raw_generation` structures gated on the biome at their origin, y -48..59), painted SOLID in its rock
  (`CaveRegions.SOLID_ROCK`: its surface rules replace every stone, and its ores target that rock), and the
  cave-pool decoration pass reads every step but ores (`CAVE_POOL_STEPS`; the mod puts its look in LAKES,
  SURFACE_STRUCTURES, even STRONGHOLDS). Pam's crops are tag entries; Pam's trees needed a seam —
  `#cityworld:orchard/{temperate,cold,dry,tropical}` configured-feature pools grown through the tree's own
  feature (vanilla cherry sits in temperate so the self-test proves the path everywhere). Battle Towers,
  Dungeon Crawl, Cataclysm and Alex's structure sets ship in `#cityworld:allowed` as optional entries.
- **`structure_fit` `reserve: true`** for an underground-step structure with a surfacing part (Dungeon
  Crawl's entrance tower came up through a highrise roof): reserves only the chunks its pieces reaching the
  raw ground touch, plus a one-chunk doorstep — never its buried box.
- **Every container gets a loot table** (`Support/ContainerLoot`, end of `PlatLot.generateBlocks`; the lot's
  `defaultLoot()`, or `ownLootTable()` when that table exists — a schematic's `chests/schematic/<name>` or
  its sidecar's `Loot:`). Three tiers: `RandomizableContainer` deferred, plain `Container` filled now, a
  capability-only mod inventory filled through the handler. **⚠ Ask the REGION for a block entity, never
  the chunk:** a block placed during generation is a `DUMMY` NBT stub in the proto-chunk until
  `WorldGenRegion.getBlockEntity` materialises it; `ChunkAccess.getBlockEntity` answers null, and the first
  version found only the chests `setChest` had already touched — 848 skipped, 0 handled, with three
  furniture mods installed. Every table ends in an `_extra` hook a pack can own (`scripts/add_loot_extras.py`).
- **The armoury** (`Support/Armoury`): item frames from item tag `#cityworld:armoury/weapons`, armour stands
  from `#cityworld:armoury/armour` (`getEquipmentSlotForItem` picks the slot), ammo shelves rolling
  `chests/vault_ammo`. Entities built directly and added through the region — never `create`/`survives`.
- **Large caverns** (`largeCaverns`, `ShapeProvider_Normal.inCavern`): region × room × texture noises, a
  sawtooth on the threshold for shelves, y -52..26, on for MODERN/APOCALYPSE. Owner: *"just the right
  shapes."* `scripts/CavernScan.java <seed> <x> <z> <r>` lists them with `/tp` points from the noise alone.
- **For modpacks:** `worldCreation.lockCustomize` removes the Customize button for a locked preset, so a
  pack's replaced `world_settings` entry is authoritative (Customize is what bakes an inline copy).

**Traps this arc paid for, each once:**
- **A probe that prints the same picture three times has not run.** `scripts/probe.sh` deletes the stale
  `latest.log` first: a leftover `PROBE complete` line made the wait loop kill the new server at once.
- **`modern.json`/`apocalypse.json` name every cave toggle explicitly**, so a style default in
  `styleDefaults` never reaches a preset world; a new Terrain toggle goes in both files. Terrain's codec
  is at its 16-field cap.
- **26.3 has its own loot dialect** (typed rolls, `modifier` not `functions`); a table cherry-picked in the
  1.21 form stops its server at registry load. **1.20.1 keeps `loot_tables/`, `tags/blocks/`, `tags/items/`
  and the `name` key**, and git's rename detection carries master's `value` key straight into them: grep
  after any loot pick.
- **A conflict resolver that asserts before writing leaves the markers in place**, and `git add -A` then
  commits them. Grep for `<<<<` after every resolved pick.

**5.13.0 (2026-09-24) was one idea: the planner knows exactly where every structure will be.** `StructureForecast`
makes vanilla's own `createStructures` call from the planner, for any chunk, before it exists, and the
self-test proves it against the stored start on every version (`checkForecast`: compare footprint, floor
and piece count — never maxY, which a chunk reload rebuilds for a terrain-matching piece). Everything
else follows from it. PORTING.md "▶ Resume here — v5.13.0" has the measurements. The rules, each paid for in a
playtest round:

- **Reserve the real footprint, plus as far as the blend will reach.** Reservation = a forecast start's
  box + `reserveMarginChunks(start)`: one chunk for anything the pad leaves alone, otherwise the same
  taper arithmetic the pad uses. One 2,809-chunk sweep: 1160 → 279 chunks reserved, 804 → 0 reserved for
  a structure that never came. A city planned inside the taper gets lifted around (houses in pits, a road
  into a wall) — so the reservation and the blend must be sized by the SAME number.
- **The blend is sized by the RISE and wobbled by noise.** `PAD_SLOPE` 2.5 blocks of run per block of
  climb, never under 16, capped at 120 (the forecast path gathers 8 chunks out; the region fallback keeps
  the pipeline's one-chunk ceiling). A 40-block rise through a 16-block taper is a terraced pyramid.
- **Blend toward the plane the pad can REACH, never the box bottom.** Outside a piece the ground never
  goes below `seaLevel + 1`; a box bottom can be underground chambers (the cursed pyramid: ~35 blocks
  down), and aiming at it sized an 88-block taper and sank the whole ring to the beach line. Under a
  piece the ground may settle to `seaLevel` (a dry beach in CityWorld), which is what closes the trench
  under a structure that sinks itself below the heightmap (Cataclysm's desert village, `start_height -3`).
- **A beard aims at a FLOOR, a shave at a box BOTTOM.** `box.minY() + groundLevelDelta - 1` is a rigid
  jigsaw's single ground plane — every acropolis piece reported y199 while the hill it cuts sits at y60,
  and the shave shaved nothing. A structure that cuts removes everything from its box bottom up, so a
  shave lowers the plan to the LOWEST box bottom below natural ground and feathers outside; only ever
  lowering. Beards: under a piece the LOWEST floor wins (the base, not the roof); outside a footprint
  only GROUND pieces pull (pieces with no lower-floored piece under them in X/Z), weighted by the square
  of inverse-square so the piece beside you owns the edge (no ridge).
- **Every structure is beard, carve, shave or neither.** Carve = a cavern for something BURIED, halo only
  then. Neither = anything that levels itself (`ScatteredFeaturePiece`) or genuinely floats.
- **Two rules keep the forecast a pure function:** `getBaseHeight` answers RAW terrain (vanilla asks it
  while placing; answering the plan would make a real chicken-and-egg), and no reservation answer is
  memoised before the forecast has bound to its level (a timing-dependent plan). Fill the forecast memo
  OUTSIDE any map lock — a jigsaw assembly inside `computeIfAbsent` is the stall below in another coat.
- **Left as Cataclysm's own:** the acropolis pillar bases one block into the water (its absolute start
  height; identical in a vanilla world). Not worth tricking the heightmap answer, which would float
  every village by one.

**The stall (fixed in the same release).** It was never random: the owner's logs put every 60-80 s
`plan.build` on the SAME platmap of the same seed. `UrbanContext.fillOutBuilding` ignored `setLot`'s
refusal of a reserved chunk, so every monotone path through the reservation re-entered it, building a
fresh lot (256 noise columns) per visit — exponential in the reservation's width. 37,669 ms → 1,040 ms.
And `getPlatMap` no longer plans inside `ConcurrentHashMap`'s bin lock (a resize locks every bin, so one
slow plan froze unrelated workers and the server thread). **A stall that looks random is a platmap: ask
which chunk, and whether the same chunk stalls twice, before reasoning.** Two code diagnoses were wrong;
the restored `Support/Timings` watchdog (at `4671737d^`, strip before shipping) named it in one run.

**Reproducing a modded structure locally.** Cataclysm's jar (like every code mod in the owner's
instance) carries SRG-named mixins that cannot apply in a named dev runtime. What works: a datapack-only
stand-in jar with its `structure_set` JSONs + `*_avoid` tags verbatim, its template pools with
`processors` rewritten to `minecraft:empty`, its NBTs, biome tags stripped to vanilla entries, and a
named transcription of any custom placement type registered under the mod's namespace from a debug
class (deleted before committing — `find me -path '*debug*'` in the jar must read 0). Its jigsaw
assembler is NOT vanilla's, so the pieces are an approximation: good for exercising a mechanism, never
for the real shape — that is the owner's playtest. Recipe in PORTING.md "The stall, found and fixed".

**⚠ Cherry-picking the pad to a version branch:** the branches never received master's `fitIndex`
refactor and a hunk-level merge mixed two generations of that code. Splice master's whole fit-index
and `padPlanForStructures` regions between the two comment anchors that exist verbatim on every branch,
then let the compiler name the API drift (26.x: `ChunkPos` is a record, `pack()`/`x()`; 26.3:
`getStructureTemplateManager`, a `Climate.Sampler` on `Structure.generate`, two-arg
`getStartForStructure`; 1.20.1/1.21.1: ten-arg `generate`, `location()`, Forge's server hooks).

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
- **⚠ A hang at "Preparing spawn area" is a DEADLOCK until a thread dump says otherwise — and no sweep can
  reproduce it.** 5.16.0's museum podium called `ItemFrame.setRotation(int)`, which notifies comparators through
  the frame's level — the real `ServerLevel` — from a generation worker: `hasChunkAt` → `getBlockState` →
  `getChunk` joins the server thread, which is waiting for that chunk. `hasChunkAt` is true only when the chunk
  already holds a FULL ticket while it decorates (spawn area, teleport, login, forced chunk); a chunk that
  decorates as its neighbour's ring answers false, and every probe, sweep and self-test loads chunks one after
  another, so each had already decorated as a neighbour. The self-test's `checkDirectLoad` (FORCED tickets on a
  whole museum district at once, via `updateChunkForced` — `setChunkForced` loads synchronously and hangs the
  harness itself) is the only check that sees this class. **An entity built during decoration holds the real
  level: read any vanilla method you call on it, and take the overload that does not notify.** To dump the
  owner's hung game from WSL: `tasklist.exe` for the big `javaw.exe` PID, then
  `/mnt/c/Users/darre/.gradle/jdks/eclipse_adoptium-21-amd64-windows.2/bin/jstack.exe <pid>`.
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
- **⚠ Every release updates the wiki: `scripts/gen-wiki.sh --push`** (owner, 2026-10-26: *"fold 'update the wiki'
  into the rollout process"*). The wiki is GENERATED from `CURSEFORGE-CONFIGURATION.md`, `CURSEFORGE-COMMANDS.md`,
  `PALETTES.md`, `LOOT.md` and `NODES.md`. It had gone a month and fifteen releases stale, with no loot page and
  none of 5.19–6.8's settings, when a user was about to be pointed at it. So the release recipe is: user-facing
  changes go into those docs in the release's docs commit (a new setting -> its row in CURSEFORGE-CONFIGURATION.md;
  a new or changed loot table -> LOOT.md, and `scripts/gen_style_loot.py`/`gen_shop_loot.py` if generated), then
  after the tag `scripts/gen-wiki.sh` (dry run: read the diff) and `--push`, and tell the website session (WEBSITE.md)
  what changed. A release with "nothing to publish" from gen-wiki is fine; a release that added a setting is not.
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
