# CityWorld — Bukkit → NeoForge port plan

## ▶ Resume here — the End was rebuilt on vanilla's islands (2026-09-17); it wants the owner's eyes

**Status 2026-09-17.** The End was redone from scratch and CityWorld's Nether + End are now the **default realms**
in all 13 world presets. Self-tested on all three branches (1.21.11 128 checks, 26.1 128, 26.2 151; the new
`checkEnd` reads 0 terrain mismatches, 472 built chunks, 0 central lots on each; plan hashes identical) and
deployed to the 9-instance fleet for playtest. The basements + pylons follow-up was re-tested the same way and
deployed: master `5bb3ebf2` (code; stamp `dc0533a4`), `mc26.1 21d9804d`, `mc26.2 46664b60`.
**Committed, not pushed.** **Not yet seen in game by the owner**; the section below ("The End, second build") has the design, every
measurement, and the dials to turn once they have.

**What the owner asked for (2026-09-17), after playing the first End:** it "just didn't look right at all" —
square-edged slabs with solid chunk boundaries, and the overworld's mines hanging out of the bottom. The middle was
perfect (dragon spawned and was fightable, fountain formed, portal home, gateways led to the city). So: *keep
the End islands as vanilla generates them, and add the city on top of where an island is — no foundation fills.*
The owner is happy for the End to plan differently from the overworld and Nether; "some echo-semblance would be nice, but
getting something that works is priority".

**Open, in the order I would take them:**

1. **Owner playtest of the new End** — the look of the terrace edge (4-block planing + 4-block blend), bridges
   between islands (pylons only over real ground, on obsidian; max total span 10 chunks), how dense the city feels, what an end city
   inside a district looks like, and gateways landing in a city. All dials are constants at the top of
   `ShapeProvider_TheEnd`.
2. **The dragon fight has now been fought end to end by the owner on the first build** (2026-09-16) and the centre
   is untouched by the rebuild (same delegation), so that open question is closed.
3. **End-flavoured palette / decay.** The End city is the overworld's twin in style, materials and decay. Nothing
   End-specific (purpur, end stone bricks, end rods as street lights, shulker-box loot) has been tried; the owner
   has not asked, and should see the plain version first.
4. **The plan does not know about end cities.** `endStructureHere` stops CityWorld drawing in a chunk an end city
   occupies, but the lot is still planned (F3/JourneyMap say "RoadLot"). Connected buildings beside the gap keep
   their party walls open. Cosmetic; measured 2 of 7 end cities start inside a lot.

## The ZARP realms arc (2026-09-16: everything below is shipped and pushed)

**Shipped on all three branches, each self-tested (1.21.11 122 checks, 26.1 122, 26.2 127) and pushed:**
the modpack world-type lock, `/cityworld` as the pre-apocalypse twin, the ruined-city 1:1 Nether (biomes,
ground, ores, lava seas, fortresses+bastions, heavier ruin), the Customize **Realms → Nether** toggle and its
`ruinedNether` pack lock, Nether trees (`#cityworld:nether_trees`), the experimental-settings warning skip,
the **world-freeze fix** (`RoadLot.decaySidewalk`) and **bastion caverns + ruined shafts**. Master jar
redeployed to `CityWork-ReForged`. Two follow-ups worth knowing: the Vivo look at a bastion shaft was
**inconclusive** (unlit Nether on llvmpipe — the measured campfire/halo counts are the evidence), and the
End is **not started**.

**Owner's in-game report on the first shipped build (2026-09-16), both fixed and pushed:**

- **Crimson/warped/soul-sand-valley had no ground** — `applyBiomeGround` only swapped grass/dirt/coarse
  dirt/podzol, so in the Nether (ground = the ore provider's netherrack) it never fired; its height gates are
  overworld datums (sea level, icecap) and are skipped there; grass counts as swappable there too, because
  parks and farms were laying lawns. Measured over a radius-6 sweep: soul sand valley 99 soul soil / 25
  netherrack, warped forest 30 nylium / 23, basalt deltas 13 basalt, nether wastes 804 netherrack, stray
  grass 60 → 8 and 12 → 2. ⚠ Basalt deltas had *looked* right all along because `LavaLakes` lines pools with
  basalt — a biome can pass a glance on someone else's blocks.
- **The bastion shaft stopped short** — it built to `streetLevel`, a planned datum. The vault hut's upward
  scan (`VaultLot.groundedHutFloor`) does not transfer: that column is solid rock, while a bastion sits in its
  own carved cavern, so the scan found air at once and campfires landed at y 50 under a y 76 surface.
  `Heightmap.Types.WORLD_SURFACE` answers it directly — deltas now −1/−1/−1/−8 (the −8 has a build overhead),
  and no shaft at all when the surface is not meaningfully above the roof.

**Confirmed working in game by the owner:** 1:1 portals, Nether trees in parks, no experimental warning,
nether wastes and basalt deltas.

### The End, second build — vanilla's islands, the city on the flat of them (2026-09-17)

**Shape of it.** Every End chunk is filled by a real vanilla End generator (`vanillaEnd()` —
`NoiseBasedChunkGenerator(TheEndBiomeSource, NoiseGeneratorSettings.END)` with its own `RandomState`), so the islands,
their undersides and the void are vanilla's everywhere, not only in the centre. CityWorld then runs **on top**:
`ShapeProvider_TheEnd` (chosen by `environment`, outranking the style, like the ore and cover providers) shapes
nothing; it *reports* vanilla's terrain to the planner, and the planner's one upstream rule does the rest — a chunk
is buildable only where all five `HeightInfo` samples stand exactly at street level. So roads and buildings exist
only on island tops; every other chunk is an `EndNatureLot`, which draws nothing, and gets vanilla's own biome
decoration (chorus). The centre (1,024 blocks) is still vanilla's alone — `inEndCentre` — and
`ShapeProvider_TheEnd.inDragonZone` keeps the planner out of it (the first probe planned a road over the podium: the
central island is flat at exactly the wrong height).

- **`worldgen/EndTerrain`** answers "how high is vanilla's island here" without generating. Vanilla's
  `getBaseHeight` measured **1.6–1.9 ms per column** (it rebuilds a `NoiseChunk` each call; 50,000 calls) — 40 s
  per platmap. `EndTerrain` reproduces `NoiseChunk` instead: the function inside the router's `interpolated` marker,
  evaluated at noise-cell corners (8×4×8) and interpolated trilinearly, solid where > 0; the 2D end-islands field
  (≈600 simplex lookups) memoised per column via a `DensityFunction.Visitor`. **0 of 2,880 columns wrong against
  vanilla, 0.42 ms per *chunk*** (≈1,100× per column). The self-test re-proves exactness on every branch
  (`end.terrain.wrong`), because a Minecraft version that reshapes the End router would break it silently.
- **The terrace.** Survey of vanilla's outer islands (10,000 chunks, seed 8675309): 37% of chunks are solid at all
  five samples; of those **57% top out within y 57..60 (top block)** and 76% vary by < 6 blocks in the chunk.
  Street level → share of solid chunks wholly within ±4: **59 → 66%**, 61 → 58%, 57 → 50%; within ±6: 80%. So
  `STREET_LEVEL = 59`, tops within ±4 (`TERRACE`) are planed to it and the next 4 (`BLEND`) ease back to natural
  height. It is a rule of the *terrain*, applied to every column of every chunk alike — that is why no
  chunk-square edge can appear (the first End's squares were the city's own slab).
- **The void is a strait.** A void column reports `VOID_FLOOR` (street − 4, below "sea level" 58), so a gap reads
  as sea and `RoadLot` crosses it with its ordinary bridge; the pylons stop at that height (stubs, not columns to
  y 0). Upstream's bridge search had no maximum length (its own TODO) and drew roads 40 chunks across the void;
  `ShapeProvider.getMaxBridgeReach()` (End: 10 chunks, everyone else unlimited → overworld plans unchanged) caps
  the **total** span — a per-side cap left the middle of a too-long gap paving itself, an orphan stretch of bridge.
- **Districts are graded by island, not by platmap.** The overworld ladder grades a platmap by how much of it is
  wild; here most "wild" is void, which graded nearly every island as farmland (**685 farm lots** in the first
  survey). `getContext` now counts only chunks that touch an island, and keeps to the city — no farm or outland
  contexts. ⚠ `PlatMap.isNaturalLot` is true for a lot *nobody has claimed yet*; use `!isEmptyLot && isNaturalLot`
  at that point in planning or everything reads as wilderness.
- **Nothing digs.** `CityWorldSettings.applyEndRealm()` turns off mines, sewers, cisterns, bunkers,
  caves, lava, ores, bones and fluids whatever the overworld twin has on; the provider answers "no" to every
  shaft/cave question. Measured over 169 chunks: below y 55 the only blocks are end stone and chorus.
- **Owner's first look (2026-09-17): "looking much better."** The odd glitch where an end city meets its
  neighbouring chunks, "but nothing that wouldn't be explained by the builders sculpting to build the buildings".
  Two asks, both done the same day: **basements back in** — `ShapeProvider.getMaxBasementFloors` (unlimited
  everywhere but the End) caps each `BuildingLot.depth` by the island's thickness under *that chunk*
  (`EndTerrain.chunkUndersides`, 4 blocks of cover, none where any column is void); measured over 169 chunks:
  basements to y 48, and the lowest block of every column is still end stone. And **bridge pylons** "either need
  to vanish or rest on obsidian": `RoadLot.placeBridgeColumn` — in the End each pylon column looks for real ground
  beneath it and stands on an obsidian footing, or is not drawn at all over void.
- **Palette, lights, and a pristine End (owner's three answers, 2026-09-17).** *"Blend it in"*: the new
  `#cityworld:build/end_stones` tag (purpur block + pillar, end stone bricks, obsidian, amethyst) is folded into the
  building/house/government/factory/water-tower pools **in the End only**, weighted at half of what each pool already
  holds → about one pick in three (`MaterialProvider`, after the MODERN fold). ⚠ A tag pool picks evenly, so an
  "accent" is as common as the staple: crying obsidian came out at 824 blocks against purpur's 711 in the first
  probe and was dropped. *End rods for street lights*: `RoadLot.generateLightPost` stacks two where the lamp goes;
  the fence post stays because the street signs hang from its top block. *Pristine*: the End never decays, whatever
  it mirrors — lore: the dragon kept people away until it was killed, which is why ZARP's voidlings only appear
  afterwards. `decayed: false` in the presets and `CityWorldRealms`, **and** defaulted in `context()` so an End
  created before today is pristine too; self-test `end.pristine`.
- **Biomes are vanilla's.** `CityWorldEndBiomeSource` reads the same end-islands field through `EndTerrain`
  (`TheEndBiomeSource`'s own sample point and thresholds). ⚠ It is bound in **`createState`**, not at first chunk:
  structure placement asks for biomes before any chunk exists, and unbound it answered "barrens" — the probe's
  locate ground through 87,000 candidate cells in five minutes and found no end city. After: 7 of 7 found.
- **End cities.** Both gates (lowest of four columns ≥ y 60, highlands/midlands) pass in 8.5% of surveyed chunks.
  2 of 7 located starts fell inside a CityWorld lot, so `endStructureHere` makes CityWorld draw nothing in any chunk
  a structure piece overlaps (checked in `fillFromNoise` and decoration; the chunk decorates as vanilla).
- **Realms are the default now.** All 13 `world_preset/*.json` define the ruined Nether and the CityWorld End;
  the Customize toggles read the selected dimensions, so they open on "on" and can still go back to Vanilla. A
  dedicated server gets them too — **which retires the `run/world/datapacks` recipe below for the End and Nether**
  (the generator line is still the first thing to read in any probe).

**Tools added for this.** `-Dcityworld.probe=survey:end` (with `probe.dim=minecraft:the_end`): on a vanilla End it
surveys surface heights and checks `EndTerrain` against `getBaseHeight`; on CityWorld's it prints the **plan as a
chunk map** (`PLAN` lines: void, island edge, wild island, `#` road, `B` structure) plus the end-city gates —
planning only, no chunk generated, 5 s for 10,000 chunks. `-Dcityworld.probe.layers=<y1>..<y2>` tallies each layer
of a sweep. **`scripts/region_render.py`** renders a box of a saved world to a PNG (plan above, side elevation
below) with no dependencies — shape is what the End was about, and block tallies cannot show it. 1.21.11 saves the
End to `run/world/DIM1/region`; 26.x to `dimensions/minecraft/the_end/region`.

### The End, first build (2026-09-16) — superseded, kept for what it measured

> Replaced on 2026-09-17: CityWorld built its own FLOATING-shaped islands outside the centre, as the overworld's
> plain twin. In game it read as square-edged slabs with solid chunk boundaries and mines hanging underneath.

**Shape of it.** `cityworld:end` (`CityWorldEndBiomeSource`) + the generator's `environment: "the_end"`, on
**vanilla's `minecraft:the_end` dimension type** (1.21.11 gives the dragon fight only to that type; 26.x asks
the type's `has_ender_dragon_fight`, which vanilla's sets). Within **1,024 blocks of the origin — vanilla's own
radius, the `x²+z² <= 4096` sections test `TheEndBiomeSource` uses — the chunk is generated by a real vanilla
End generator** (`NoiseBasedChunkGenerator(TheEndBiomeSource.create(biomes), NoiseGeneratorSettings.END)`, its
own `RandomState`, built lazily via `ServerLifecycleHooks`): `fillFromNoise`, `buildSurface` and
`applyBiomeDecoration` all delegate, so the central island, the obsidian pillars (an `end_spike` feature of
`the_end`), the spawn platform and the void ring are vanilla's. Outside it CityWorld builds FLOATING-shaped
islands in end stone (`OreProvider_TheEnd`: end stone strata, **no fluids — its seas are the void**, End
accents for ores; `CoverProvider_TheEnd`: chorus flowers, nothing else). Biomes follow island height —
highlands ≥ sea+24, midlands ≥ sea+8, barrens ≥ sea-8, else small islands — and all five are in
`possibleBiomes`, which is what lets **`minecraft:end_cities`** (added to `#cityworld:allowed`) place at all.
Customize gains **Realms → End** (default vanilla) with a `cityworldEnd` pack lock.

**First probe (seed 8675309, end spike datapack).** Centre (0,0, r=4): End Stone 114,453 + **Obsidian 2,884**,
biome `the_end` across all 2,304 columns — vanilla's island and spikes. Outer (chunk 100,100, r=3): End Stone
149,173, city blocks on the islands, chorus plant/flower on highlands, biomes barrens 2,040 / midlands 264.
Three faults found and fixed: a **bedrock floor** (708 blocks — the End must be void below), **lush-cave
decoration** (moss, cave vines, dripleaf, spore blossoms) and **lava-lake basalt** (708) leaking in; the lush
and lava passes are now overworld/Nether-gated.

**Still to verify:** end city placement (needs ground ≥ y 60 in highlands/midlands — `find:structure:minecraft:end_city`),
gateways landing on end stone, and the dragon fight end to end on a real client.

