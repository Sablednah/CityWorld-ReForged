# Cobblemon in CityWorld — what it is, what already works, and a plan

*Survey and plan, 2026-11-12, for GitHub issue #6 (Pokémon Centers and gyms in the city). Grounded in Cobblemon
**1.8.4** read from its GitLab source at tag `1.8.4` (`common/src/main/resources/data/cobblemon/` and the Kotlin under
`common/src/main/kotlin/com/cobblemon/mod/common/`) and in CityWorld master. Nothing here was taken from a wiki.
Cobblemon ships for **1.21.1** (NeoForge 1.8.4, needs Kotlin for Forge) and for **1.20.1** only up to **1.5.2**
(2024); there is no 1.21.11 or 26.x build, so the code side of this lives on `mc1.21.1` (and `mc1.20.1` where its
older data allows), while data entries are harmless on every line.*

## 0. What Cobblemon adds, measured

| Thing | What the source says |
|---|---|
| **Structures** | 67, every one `minecraft:jigsaw` (no custom structure types): 31 *habitats* (berry patch, flowerbed clearings, ponds, bug mound, peat bogs, thermal vents …, step `local_modifications`, biome-tagged), 29 *ruins* (henges, crypts, towers, arches; mostly `underground_structures`, `bury`/`beard_box`), 6 *Gimmighoul towers* (`surface_structures`, `beard_thin`, size 7), 3 fishing boats, 3 shipwreck coves. Ten structure sets, all `random_spread`: `habitats` (spacing 30), `ruins/avoid_villages` (30), `avoid_jungle_temples` (30), `avoid_trial_chambers` (15), `gimmi_towers` (40), `avoid_witch_huts` / `avoid_woodland_mansions` (25), `avoid_ocean_monuments` (60), `fishing_boats` (16), `shipwreck_coves` (100). |
| **Village Pokémon Centers** | Yes — but only as **village jigsaw houses**: `CobblemonStructures.registerJigsaws` adds `cobblemon:village_<biome>/village_<biome>_pokecenter` (weight 35) and two berry farms (weight 1) to vanilla's five `village/<biome>/houses` pools at server start, plus 17 more biome variants under `structure/villages/` for other village mods. Footprints 10×14 to 13×15, 11–17 high, each with **one `cobblemon:healing_machine`**, an apricorn door, and a chest on `cobblemon:villages/village_pokecenters` (bread, XS exp candy, remedies, status heals, oran berries). **No gym exists anywhere in the mod.** |
| **The nurse** | A villager profession `cobblemon:nurse` whose job site is the healing machine (`CobblemonPoiTypes.NURSE_KEY`, 1 ticket); a villager named Joy (or its translations) gets the Nurse Joy texture. |
| **NPCs** | Trainer-style NPCs are `cobblemon:npc` entities built from datapack *NPC classes* (`data/<any namespace>/npcs/*.json`, loaded from every namespace) with presets, variations, a party and a battle configuration; saved with `NPCClass`, `AppliedAspects`, `Interaction`. Only test classes ship (`standard`, `sacchi`, `kitchen_sink`, `ai_test`). NPCs heal at healing machines by AI (`HealUsingHealingMachineTask`). |
| **Blocks** | 497 constants: 13 evolution-stone ores (stone + deepslate + nether/dripstone/terracotta hosts), apricorn and saccharine wood sets (logs, planks, stairs, slabs, doors, fences, signs …), 7 apricorn colours + saplings, 70 berry bushes (+ potted), tumblestone (3 kinds, budding clusters, polished/smooth/brick families), 18 type-gem blocks + clusters, 10 evolution-stone blocks, 7 campfire pots, gilded chests (7 colours) + the Gimmighoul chest, 16 plaques, ~120 held-item display blocks, **machines**: `healing_machine` (1 block, facing), `pc` (2 high, `part` top/bottom, facing), `pasture` (2 high, `part`), `fossil_analyzer` + `restoration_tank` + `monitor` (a multiblock set), `tm_machine`, `lectern`, `disc_shelf`, `display_case`, mints, medicinal leek, big/energy root, revival herb, grains, tatami. |
| **Worldgen features** | Added in code (NeoForge `biome_modifier/coded.json`), VEGETAL step: apricorn trees, saccharine trees, mints, medicinal leek everywhere; big roots and **berry groves** in `#minecraft:is_overworld`; revival herbs, swamp/plains grains, galarica nuts by tag. UNDERGROUND_ORES: the evolution-stone ores and type gems. Plus 20 LOCAL_MODIFICATIONS "habitat" features (tidepools, geodes, wellsprings …). |
| **Biome mod support** | Entirely by **biome tags**: 68 `#cobblemon:is_*` tags naming vanilla biomes plus Wythers (237 entries), Terralith (157), Biomes O' Plenty (96), Clifftree, BWG, Blooming Biosphere, Darker Depths. CityWorld registers **no biomes of its own** (its climate source picks vanilla ones), so every CityWorld chunk is in whatever Cobblemon tag its vanilla biome is in. |
| **Spawning** | 1,591 spawn files, 5,178 spawn entries, all conditioned on `biomes` tags; 729 on `structures`, 401 on `neededNearbyBlocks`, 586 on `minY`. Presets: `natural` (base block in `#cobblemon:natural`: dirt, sand, stone, grass, snow, terracotta, ores …, never farmland), **`urban`** (`#cobblemon:concrete_blocks` within 4 blocks horizontally / 2 vertically, *not* in a village or trail ruins), `wild` (no concrete, no village), `water`, `treetop`, `derelict` (dark, enclosed, unnatural floor). Structure checks read the chunk's **real structure starts** (`StructureManager.startsForStructure`). |
| **Loot** | Poké Balls etc. reach vanilla chests through an **internal, fixed** injector (`LootInjector`: mineshaft, ancient city, bastions, village houses, strongholds, mansions …). Nothing of CityWorld's. |

## 1. What already works with no change to CityWorld (to be confirmed by the spike below)

- **Every Cobblemon structure is an ordinary vanilla jigsaw** the structure forecast understands, biome-gated on
  vanilla biomes, which CityWorld uses. **But none is placed today** (§6): on CityWorld land a structure set is
  off unless it is in `#cityworld:allowed` or the player ticks it on the Structures page, and Cobblemon's ten sets
  are in neither. One tag edit (Stage 1) turns them on.
- **Apricorn trees, berry groves, mints, leeks, saccharine trees, roots** decorate **wild** land: CityWorld hands
  wild chunks to `super.applyBiomeDecoration` (the biome's full feature list, which includes NeoForge biome
  modifiers), so anything Cobblemon adds to a vanilla biome appears there.
- **Evolution-stone ores and type gems** under everything: CityWorld runs vanilla's `UNDERGROUND_ORES` step under
  cities and roads too (`placeUndergroundOres`).
- **Pokémon spawn** wherever the biome says, including on CityWorld land. The **`urban` entries** (Meowth,
  Growlithe, Grimer, Voltorb, Koffing, Chansey, Mr. Mime, Eevee, Snubbull, Lillipup, Trubbish, Minccino, Scraggy
  and ~100 more) need concrete nearby and a *natural* floor: CityWorld's buildings use concrete, so these should
  fire on grass verges, parks and bare ground in a city, but **not on a stone-slab sidewalk or an asphalt road**
  (not in `#cobblemon:natural`). How much of a city qualifies is a measurement, not a guess.
- **Apricorn and saccharine wood** already reach CityWorld's wood pools that draw on vanilla tags
  (`#minecraft:planks`, doors, stairs), so some buildings already use it.

## 2. The gaps

1. **The village-dwelling Pokémon never spawn in a CityWorld city.** 199 spawn entries (the same ~100 species as
   `urban`, their second entry each) require `#cobblemon:spawns/is_civilization` = `#minecraft:village` + a few
   village mods, checked against real structure starts. A CityWorld city is not a structure.
2. **No Pokémon Center in the city, no gym at all.** Cobblemon's Centers are village houses (wood and cobble,
   village-scale). The issue asks for both in cities.
3. **CityWorld's chests hold no Cobblemon loot** (the injector is internal and closed).
4. **Farms grow no berries, orchards grow no apricorns.** Berries are crops on farmland (`#cobblemon:berry_soil`
   = farmland; age 0–5, fruit at 5) and apricorn trees are configured features `cobblemon:<colour>_apricorn_tree`,
   so both fit CityWorld's existing pools.
5. **Cobblemon's own structures may land inside a city** (a Gimmighoul tower is a surface structure; ruins
   are buried with a `bury` adaptation) — the same question Cataclysm/Dungeon Crawl had, answered by
   `structure_fit` and `wilderness_only` entries.
6. **Indoor Pokémon.** The `derelict` preset (dark, enclosed, unnatural floor) is exactly an Apocalypse ruin, a
   sewer or a subway tunnel; those should already fire (Ghost/Poison types). Worth confirming, not building.

## 3. The plan — cheapest proof first

### Stage 0 — measure (this session) — DONE, see §6

- Cobblemon 1.8.4 + Kotlin for Forge 5.12.0 in `mc1.21.1/run/mods` (jars in the session scratchpad; **not** in git).
- Self-test on 1.21.1 with it installed: does the server start, do all checks still pass, are there
  `populateLots FAILED` lines or Cobblemon errors.
- `scripts/region_tally.py run/world/region cobblemon:` after the self-test: which Cobblemon blocks landed, where
  (wild vs city). Expected: apricorn logs/leaves, berry bushes, mints, ores, type gems, a habitat or two.
- The owner's look with `/checkspawns` (Cobblemon's own command: what can spawn where you stand) on a city
  street, in a park, in a sewer and in an Apocalypse ruin — that is the spawn measurement.

### Stage 1 — data only, ships in CityWorld's jar, every entry `required: false` / `mod_loaded`

| Entry | Where | Effect |
|---|---|---|
| `cobblemon:<colour>_apricorn_tree` ×7 | `#cityworld:orchard/temperate` (and cold/tropical by colour) | apricorn orchards on farms |
| `cobblemon:oran_berry`, `cheri_berry`, `pecha_berry`, `rawst_berry`, `sitrus_berry`, `leppa_berry` … | `#cityworld:farm/crops` | berry fields on tilled farmland. A bush placed with `generated=true` and `age` ≥ 4 fills its own fruit in its block entity's constructor (`BerryBlockEntity`; the property is named `generated` in the saved state), so `FarmLot.plantField` needs one generic touch: set a `generated` property true when the crop has one (otherwise the bush stands at age 5 with nothing to pick) |
| `cobblemon:pc` | `#cityworld:furniture/computer` + a `cityworld:furniture` data-map layout (2 high, `part`: bottom/top, facing) | PCs on office desks and in homes |
| `cobblemon:display_case` | `#cityworld:furniture/shelf` or the museum exhibit stands | display cases in museums |
| `cobblemon:tatami_mat` | a floor/rug pool | — |
| `cobblemon:gilded_chest` (colours) | the vault / bank container pool | Gimmighoul-style treasure |
| Cobblemon's ten structure sets (`habitats/habitats`, `ruins/avoid_*` ×6, `ruins/gimmi_towers`, `fishing_boats`, `shipwreck_coves`) | `#cityworld:allowed` (`required: false`) | **the fix for §6's zero**: they generate on CityWorld land at Cobblemon's own spacing, and show on the Structures page |
| the same ten sets | `#cityworld:wilderness_only` | keep Cobblemon's ruins and clearings out of the city's platmaps (they are nature set pieces) |
| `cobblemon:ruins/*_gimmi_tower` | `structure_fit` `{ "beard": true, "clearance": 3 }` | the one surface structure stands on built-up ground |
| CityWorld's `chests/modern/*` + `apocalypse/*` | a pool referencing `cobblemon:injection/chests/village_house` / `simple_dungeon` guarded by `mod_loaded` | Poké Balls, potions and berries in city loot (the generated tables: `scripts/gen_style_loot.py`) |

Each is one PALETTES.md row (and the "Everything a datapack can touch" table where a pool is new), a
CURSEFORGE-CONFIGURATION.md note, and a diagnostics pass (`-Dcityworld.diagnostics=true` shouts an empty pool).

### Stage 2 — code, loaded only when Cobblemon is present (`mc1.21.1` first)

1. **`PokecenterLot`** — a CityWorld-drawn modern Pokémon Center (red roof, white walls, the "P" in concrete
   over the door) in Lowrise and Neighborhood districts at `shops.oddsOfPokecenter` (a setting, default rare):
   healing machines on a counter (`fittings/healing_machine`, a new pool so a pack can swap it), PCs along a wall,
   a pasture out the back, chairs, chests on `cobblemon:villages/village_pokecenters` + an `_extra` hook, and a
   **nurse**: `SpawnProvider.employ(level, villager, cobblemon:nurse)` already takes any profession id, and a
   villager named Joy gets the texture. A `Findable` "Pokémon Center" for the Compass and `/cityfind`.
   The owner's alternative is a shipped schematic in `cityworld/schematics/Lowrise/` (the issue reply's recipe),
   which costs no code but gets no palette, no neighbour awareness and no nurse unless the spawn registry adds one.
2. **`GymLot`** — a municipal-district civic building: a battle hall (an open two-storey floor, a marked arena
   in type-gem blocks and concrete, bleachers), the type picked by district or biome (industrial → Poison/Steel,
   park → Grass, harbour → Water, mountain → Rock, snow → Ice), a **gym leader** as a Cobblemon NPC spawned
   through the existing `cityworld:spawn` registry (`cobblemon:npc{NPCClass:"cityworld:gym_leader_<type>"}`)
   from NPC classes CityWorld ships under `data/cityworld/npcs/` with a typed party, and a plaque wall. If NPC
   parties prove awkward, a villager leader first (MCA users convert villagers to trainers anyway, per the issue).
3. **Spawn influence** — one class registered into `SpawningInfluenceDetector.detectors` when Cobblemon is
   loaded (a `@JvmStatic` public set). Its `detectFromInput(spawner, input)` asks
   `CityWorldAPI.lotAtIfPlanned` for the zone's chunks (never plans, never blocks) and returns an influence that:
   (a) marks `#cobblemon:spawns/is_civilization` as *found* for positions on city lots (the structure cache's
   `foundTags` set is public), so the 199 village-dwelling entries spawn in cities — but **not** `#minecraft:village`,
   which the `urban` preset forbids; (b) re-weights species by district from data,
   `data/cityworld/cityworld/pokemon/<district>.json` (industrial: Grimer, Koffing, Magnemite, Trubbish; park:
   Grass/Bug; municipal: Chansey, Audino; harbour: Water; sewers/subway: Rattata, Zubat, Grimer; vault: Ghost).
   This is where "Pokémon that belong in a city" comes from, and it is all public Cobblemon API.
4. **Poké Mart** — a 15th `ShopTrade` with a Cobblemon items table (`chests/shop_pokemart`), a canvas sign, a
   shopkeeper; only picked when Cobblemon is present.

### Stage 3 — later, if wanted

Berry farms as a field kind with the campfire pot, evolution-stone exhibits in museums, Gimmighoul chests in
the vault's deep levels, a Pokémon statue monument, pastures in parks.

## 4. What this does NOT need

- No Cobblemon dependency at compile time for Stage 1 (tags and data maps are strings).
- Stage 2 needs Cobblemon's API on the compile classpath of `mc1.21.1` only (`compileOnly`, the mod loaded
  reflectively/behind a `ModList.isLoaded("cobblemon")` guard, the same shape as the JourneyMap plugin).
- No change to Cobblemon: spawn pools, NPC classes and detectors are its documented extension points.

## 5. Per-line

| Line | Cobblemon | Plan |
|---|---|---|
| 1.21.1 | 1.8.4 (NeoForge, Kotlin for Forge) | everything above |
| 1.20.1 | 1.5.2 (Forge, 2024-05): 615 spawn files, 42 structures, the `urban` preset and village Pokémon Centers exist; **no** `SpawningInfluenceDetector`, **no** NPC classes, **no** nurse profession (checked against tag `1.5.2`) | Stage 1 data where the ids exist; of Stage 2 only the Center lot (with a plain villager) and the gym with a villager leader |
| 1.21.11, 26.x | none | data entries sit idle (`required: false`) |

## 6. Measured (2026-11-12, 1.21.1, Cobblemon 1.8.4 + Kotlin for Forge 5.12.0 in `mc1.21.1/run/mods`)

- **Self-test PASS** with Cobblemon installed (`scripts/selftest-one.sh ../CityWorld-ReForged-worktrees/mc1.21.1 jdk21 25599`),
  no `populateLots FAILED`, no Cobblemon error; the one warning is Cobblemon's own Adorn mixin target missing (benign).
- **Block tally of the self-test world** (`scripts/region_tally.py run/world/region cobblemon:`, 51 regions):
  - **29,768 apricorn planks, 6,854 saccharine planks, 44 of their doors inside CityWorld buildings** — the wood
    pools that draw on vanilla tags already hand Cobblemon's wood to the builders, no entry needed.
  - Wild land decorates as vanilla would: apricorn trees (25 logs, 290 leaves, 20 apricorns), berry bushes
    (cheri, nanab … with `generated=true`), mints, medicinal leeks, 92 big roots, a galarica nut bush.
  - Under everything: every evolution-stone ore (383 leaf stone, 192 dawn stone, 139 deepslate sun stone …) and
    type-gem geodes (normal, electric, dark, fairy, grass, poison, psychic, rock, steel — blocks, clusters and
    crystal cores); three relic coin pouches from a treasure-hoard feature. Full list: the session scratchpad's
    `tally-cobblemon.txt`.
  - Two `cobblemon:habitat_block`s → at least two habitat structures were placed by the forecast.
- **Structure starts in the same world: 0 of Cobblemon's, in 14,203 chunks** (a scratchpad `structure_starts.py`
  over the chunk NBT; vanilla's trial chambers 9, ancient cities 4, ocean sets 6 — the 7.0 deep oceans). The log
  says why: `structure settings (overworld) allow [ocean sets]` — Cobblemon's sets are not in `#cityworld:allowed`,
  so CityWorld's filtered structure-set view never offers them. Expected at its spacings over that area: ~15
  habitats, ~15 ruins, ~9 Gimmighoul towers. The two habitat blocks are from a LOCAL_MODIFICATIONS feature.
- **Not measured**: live Pokémon spawning (needs a player; the owner's `/checkspawns` walk), the Nether twin.
- The jars stay in `mc1.21.1/run/mods` (git-ignored) as this arc's test bed, like Farmer's Delight's.

## 7. Progress (2026-11-12 evening)

**Built (uncommitted at the time of writing, master → the five lines):** the `cityworld:placed_sets` data map on
structure sets (`CityWorldDataMaps.PlacedSet {allow, wild}`, file
`data_maps/worldgen/structure_set/placed_sets.json`, entries guarded by `neoforge:conditions mod_loaded cobblemon`),
read by `onlyAllowed` (allowed), the wilderness-only set and `StructureChoices.byDefault` (the Customize tick).
Ships **vanilla villages** (the owner's ask: the Pokémon Centers ride in them) **and Cobblemon's ten sets**, all
`wild: true`. Self-test `checkPlacedSets`: with Cobblemon in `run/mods` villages must be placed AND wild; without it
the entry must not have loaded. Replaces the plan's Stage 1 tag edit (a tag entry cannot be conditional, and
villages are vanilla's, so "absent id" could not stand in for "mod absent").

**The owner's `/checkspawns` walk (1.21.1 instance, 20:48–20:57, read from `logs/latest.log` — the client logs
chat):** five buckets per spot (common, alpha, ultra-rare, rare, uncommon).
- **Street:** common = Hawlucha 19.7%, Sigilyph 19.7%, Meditite 17.8%, Bronzor 16.6%, then river/swamp species.
  Those four are Cobblemon's *jungle-pyramid* / *desert-pyramid* presets: base block cobblestone or sandstone
  with `#cobblemon:jungle_pyramid_blocks` (chiseled stone bricks, mossy cobblestone) or
  `#cobblemon:desert_pyramid_blocks` (chiseled sandstone, orange terracotta) within reach — CityWorld's building
  walls (`MaterialProvider.itemsSelectMaterial_BuildingWalls`: cobblestone, sandstone, mossy cobblestone, chiseled
  stone bricks…) read to Cobblemon as a temple. **No `urban` species fired** (Meowth, Growlithe, Trubbish…): the
  preset needs a `#cobblemon:natural` base block within 4 blocks of concrete, and the street is terracotta paving
  on stone-slab sidewalks (`itemsMaterialListFor_Roads`), neither natural; the only urban-listed names present
  (Vivillon, Eevee) came through their biome entries. Rare = Ditto/Goomy/starters (biome); uncommon = Dewpider,
  Rattata (the cave entry, `minSkyLight 0–7`), Gastly.
- **Sewer:** same common list as the street (the 8-block zone reaches the surface), uncommon = Rattata 26.8%,
  Gastly 16.1%, Haunter, Gengar — the `derelict` preset (no sky, light 0, unnatural floor) works in CityWorld's
  sewers as-is.
- **Park (roundabout fountain, `/cityfind park`):** common = Sandshrew, Rattata, Phanpy, Patrat, Doduo, Blitzle,
  Spearow, Nymble, Ekans — savanna/plains `natural` spawns, right for a grass park; ultra-rare Kangaskhan/Rhydon/
  Cyclizar; rare Eevee/Jolteon/Scorbunny. Five "Nothing can spawn here right now" in between were taken standing
  somewhere with no spawnable floor in the zone (the fountain itself), then the lists came back after a re-teleport.

So in-city spawning is biome + building-block driven today, the sewer is already a derelict habitat, and the
street never reads as urban. Stage 2's spawn influence is still the fix for streets; a cheaper partial is a
**concrete kerb or planter beside grass verges** (a natural base within 4 blocks of `#cobblemon:concrete_blocks`
is all the urban preset asks), measurable with the same `/checkspawns common` walk.

**Measured (2026-11-12 night, all six lines):** six self-tests PASS. On 1.21.1 with Cobblemon in `run/mods`:
`placedSets.cobblemon = 10 Cobblemon sets declared, 10 in the structure state, villages placed and wild`; the
self-test world (12,837 chunks) went from **0 to 14 Cobblemon starts** (berry patches, ancient dais, crypt, bunker
ruins, fishing boats, icebergs, ice spikes, lightning rod). Villages: 4 attempts fell in those chunks — 2 failed
vanilla's own biome check (desert/savanna variants on plains), 2 plains attempts sat in platmaps with roads and
were kept out (INVALID). A positive needed wild platmaps: `scripts/structure_sites.py 8675309` predicts vanilla's
attempt chunks (linear spread, checked against the three recorded), and `-Dcityworld.probe=<cx>,<cz>
-Dcityworld.probe.radius=3 -Dcityworld.probe.forecast=true` at 14 of them gave **two full villages in all-Nature
platmaps** (plains at chunk -113,-79: 89 pieces, box 137×137; desert at -85,-94: 87 pieces) and INVALID at every
attempt inside an Office/Concrete/Park platmap. Without Cobblemon (the other five lines' beds) the map declares
nothing: `placedSets.declared = []`, 1.20.1's hand-rolled loader included. Deployed to the owner's `1.21.1`
instance as `DEPLOYED-89bfb660` (a 7.0.0-named jar). ⚠ `probe.sh` uses `run/server.properties`' port 25599 — the
same one `selftest-one.sh` takes by default — so a probe beside a self-test fails to bind and reads `chunks 0`
(7 of 8 runs, before the port was moved for the run).

## 8. Progress (2026-11-13 overnight) — Stage 1 and Stage 2 built on master and all five lines, unreleased

Owner at bedtime: *"if you can plow on - and do as much cobble mon support as you can overnight please."*

**Stage 1, data (`e8fcd55b`):** apricorn trees in the four orchard pools (`required: false`), Cobblemon's four
berries in `farm/crops` (a bush drawn with its `generated` property true fills its own fruit on load —
`SupportBlocks.asGenerated` sets any `generated` BooleanProperty in the levelled `setBlock`), `cobblemon:pc` and
`cobblemon:pasture` as two-high furniture layouts in `data_maps/block/furniture.json` (`mod_loaded`), the six
`cobblemon:ruins/*_gimmi_tower` in `structure_fit` (`clearance 3, beard true`). Files were re-appended in each
file's own one-entry-per-line style after a `json.dump` rewrite reformatted them (499-line diff → reverted).

**Stage 2a, the spawn bridge (`d39d3915`):** `worldgen/CobblemonBridge` — reflection + `Proxy` on Cobblemon's
`SpawningInfluenceDetector` set (installed on `ServerAboutToStartEvent`; every class looked up by name, a
`ClassNotFoundException` leaves the bridge off with one info line). `detectFromInput` answers one
`UnconditionalSpawningZoneInfluence` for a CityWorld `ServerLevel`; its `affectSpawnablePosition` reads
`lotAtIfPlanned` and adds **markers** (`cityworld:city/street/farm/wild` at the surface = y ≥ streetLevel−1;
`cityworld:underground`, `cityworld:sewer` under a road, `cityworld:subway`; always `cityworld:district/<family>`,
`cityworld:lot/<kind>`, `cityworld:shop/<trade>`) and, for a city surface position, loads the position's structure
cache if needed and adds `#cobblemon:spawns/is_civilization` to its found tags — so Cobblemon's 199 village-only
entries see a city as a village. Unknown default methods on the influence go to `invokeDefault`; non-default
booleans default true. Two spawn pools in `data/cityworld/spawn_pool_world/`: `cityworld_city.json` = every
Cobblemon **urban** entry (183 entries, 82 herds, 79 species) copied by `scripts/gen_cobblemon_city_spawns.py`
with presets `urban`+`natural` dropped and `condition.markers = ["cityworld:city"]` (a preset's condition is MERGED
into each entry, so the urban preset's concrete rule could not be OR'd away — hence the copy, keeping Cobblemon's
weights); `cityworld_districts.json` = 69 hand-kept entries by district/marker (industrial, park, municipal,
neighborhood, highrise, sewer, subway, vault, farm). Self-test `checkCobblemonSpawns`: 1.21.1 reads
`252 CityWorld entries of 5430 in the world pool; influence installed; street [city, district/highrise, lot/road,
street] / [district/highrise, lot/road, sewer, underground]`, zero Cobblemon errors about `cityworld-` entries.
**1.20.1:** Cobblemon 1.5.2 has neither `markers` nor the detector API; the bridge logs and stays off, and the two
pool files are deleted on that branch (1.5.2 would ignore the unknown `markers` and spawn every entry everywhere).

**Stage 2b, the buildings (`7f9c1c86`):**
- `Plats/Urban/PokecenterLot` (IsolatedLot, 1 chunk, 2 storeys): white concrete, red band, Poké Ball roof, double
  door + awning + sign to the fronting road (`Frame.of(platmap, x, z)`: the lot is drawn in a canonical frame with
  the street south and turned — cells, facings and yaw — to whichever neighbour is the road), reception counter,
  `fittings/healing_machine` behind it, a `cobblemon:nurse` villager via `spawnWorker`, a `fittings/pc` bank (two
  high via its furniture layout, so `setFurniture`), pooled chairs/table, planters, `fittings/pasture`, two chests
  on `LootLocation.POKECENTER`, ladder, four beds and two more PCs upstairs. `reportLocation("pokecenter")`.
- `Plats/Urban/GymLot` (IsolatedLot): type by position hash (fire/water/grass/electric/rock/ice/psychic/fighting),
  hall in the type's concrete with white bands, arena floor with its `cobblemon:<type>_gem_block` centre (accent
  concrete when the block is absent), bleachers, two chests on `LootLocation.GYM`, and the leader: a
  `cobblemon:npc` with `{NPCClass:"cityworld:gym_leader_<type>"}` written through the new
  `Spawns.placeEntity(chunk, id, data, x, y, z, yaw, persistent)` (the `cityworld:spawn` registry's tail,
  factored out) when the entity type exists, else a cleric villager. Eight NPC classes in `data/cityworld/npcs/`
  (four names each, `canChallenge`, `skill 4`, `autoHealParty`, a typed `pool` party of 3–5 from eight species each, all checked against Cobblemon 1.8.4's species list;
  `isStatic: false` because Cobblemon builds a static party only on its own spawn path (`initialize`), and
  `getPartyForChallenge` answers null for a static class loaded from NBT without one — a non-static party forms at
  challenge time at the NPC's own level, which is `Level` in its NBT (0/absent loads as 1), set from the new
  `shops.gymLeaderLevel` (25); a pool entry's `npcLevels` is a FILTER on that level, so ours is `1-100`) and `data/cityworld/dialogues/gym_leader.json`
  (modelled on Cobblemon's `npc-example`: greet, Challenge → `q.npc.start_battle(q.player, 'single')`, Not today).
- Planning: `UrbanContext.getBuilding` (lowrise/midrise/highrise) rolls `shops.oddsOfPokecenter` (default 1/55 — the new Customize rung "Scarce"; it was 1/21 and the district flood copied a Center into its neighbours, fourteen in a row on the owner's first look, so `PlatLot.floodsNeighbours()` is false for a Center and a gym; a
  Customize rung) only when `isModernStyle && platmap.frontsRoad && PokecenterLot.available()` — available = the
  healing-machine pool resolves — so a world without Cobblemon takes no draw and plans byte-for-byte as before;
  `MunicipalContext.getBuilding` the same for `GymLot` at `shops.oddsOfGym` (1/21, was 1/8). Settings `Shops` is 7 fields
  (8 on 1.20.1 with `bankPlazaRadius`), DEFAULT = field defaults (`checkSettingsDefaults`), Customize pickers
  "Pokémon Centers"/"Pokémon gyms", export included.
- `ShopTrade.POKEMART("Poké Mart", minecraft:cleric, cobblemon:pc)`: the constructor now parses a namespaced id;
  `available()` = the job block's mod is loaded (vanilla always), applied in `tradesFor` and the Findables, so an
  absent mod changes nothing. `ShopFitter`: job block = first of `fittings/pc`, carpet red.
- Loot: `scripts/gen_cobblemon_loot.py` writes `chests/{pokecenter,gym,shop_pokemart}` + `_extra` per dialect,
  each table under a top-level `neoforge:conditions` (`forge:conditions` on 1.20.1) `mod_loaded cobblemon`;
  run in master, mc26.3 (typed) and mc1.20.1 (`loot_tables/`, `name`).
- Findables `pokecenter`, `gym`; `DEFAULT_ANNOUNCED` += both; self-test `checkCobblemonLots` (with Cobblemon all
  three must appear in the plan; without it none may, and `available()` must equal "Cobblemon loaded").
- Drift met on the picks: 1.21.1 `Identifier` → `ResourceLocation` (ShopTrade conflicted on the constructor);
  1.20.1 `new ResourceLocation(ns, path)`, `net.minecraftforge.fml.ModList`,
  `net.minecraftforge.event.server.ServerAboutToStartEvent`, the Shops record keeps `bankPlazaRadius` between
  `oddsOfAtmInMalls` and the two new odds, `tags/blocks/` + `loot_tables/` by rename detection. 26.x clean.

**Not done / to measure:** the lots seen built (probe `find:PokecenterLot`, `find:GymLot` on 1.21.1), a client look
at the NPC leader (the NPC class fields were read from Cobblemon's source, not seen in game — `hitbox`,
`battleConfiguration.canChallenge`, `party.type pool` are 1.8's shape), the `/checkspawns` walk after the bridge
(urban species on a street is the claim), Stage 3.

**Measured (2026-11-13, 1.21.1 with Cobblemon 1.8.4):** self-test PASS on the lots head — `cobblemon.lots = 190
Pokémon Centers, 157 gyms {ELECTRIC 25, FIGHTING 23, FIRE 22, GRASS 20, ICE 12, PSYCHIC 24, ROCK 13, WATER 18}, 133
Poké Marts in 301² chunks`, `cobblemon.spawns` as above, no Cobblemon error about any `cityworld-` entry. Probes
(`scripts/probe.sh "-Dcityworld.probe=find:PokecenterLot -Dcityworld.probe.radius=1"` in the worktree, JAVA_HOME
exported — without it the script fails silently with no `latest.log`): the Center at chunk −9,−29 reads back from the
region (`region_slice.py plan 65`) with the counter, `cobblemon:healing_machine[facing=south]` behind it, four
`cobblemon:pc[facing=east,part=bottom]`, the red runner from the double door, three chairs (quartz stairs — the chair
pool is empty on this bed), table, ferns, chest, ladder, `cobblemon:pasture` — which came out **`waterlogged=true`**:
Cobblemon's `PastureBlock` never sets WATERLOGGED in its default state, so `stateDefinition.any()` answers the
property's first value, true (`PCBlock` sets it false). `SupportBlocks.setFurniture` now places every piece dry
(`cab5cf9a`). Upstairs by tally: 4 red + 4 white bed halves, 2 PCs, chest, sea-lantern ceiling, red band at y 71,
roof 32 black / 80 red / 84 white = 196, parapet. The nurse was not in that Center: `spawnWorker` rolls
`spawnBeings` (2/3) like every worker and the hospital's medic, and that Center lost the roll — left as it is, the
owner's beings setting governs. The gym at 14,−17 (GRASS): white floor with the arena's green lines pattern and
`cobblemon:grass_gem_block` ×4 at the centre, 16 quartz-stair bleachers facing in, two chests, dark oak double door
and sign to the road, lime awning, white band at y 68, roof 176 green / 4 lime / 4 lanterns / 12 white; the entity
region held **four `cobblemon:npc` with `NPCClass` grass/water/fighting/psychic and a `CustomName`** — Cobblemon
applied the class on load (the name is from the class's `names`). 26.1 and 26.3 without Cobblemon: PASS,
`cobblemon.lots = 0 / 0 / 0 (Cobblemon not installed)`, `cobblemon.spawns = n/a`.

**1.20.1 loot (2026-11-13, final-head self-test):** `FAIL — 3 datapack element(s) failed to parse`: Forge 1.20.1 has
no condition hook on loot tables (`ForgeHooks.loadLootTable` reads the JSON as it is; the `forge:conditions` key is
simply ignored), so `cobblemon:rare_candy` as an `item` entry failed the whole table's parse without the mod
(`Expected name to be an item, was unknown string`). `gen_cobblemon_loot.py` now writes **vanilla stand-ins** on that
line (bread, honey bottles, emeralds, experience bottles…) with no condition; a pack adds Cobblemon's items through
the `_extra` hooks. Master `ef4d4b7b`, 1.20.1 `8ac7fa18`. The other five lines keep the Cobblemon stock under
`neoforge:conditions`.

**Gates (2026-11-13 ~03:00):** six self-tests PASS on the final heads (master `80e1bb13`, 1.21.1 `8725c2cb`, 26.1
`ff247c54`, 26.2 `544e2861`, 26.3 `5d549ef6`, 1.20.1 `8ac7fa18`); `selftest.sh --compare` on fresh reports: CLASSIC
identical everywhere; MODERN and APOCALYPSE identical on the five lines WITHOUT Cobblemon and equal to the hashes the
1.21.1 bed gave at the bridge commit, before the lots existed (MODERN `c4b95b7c`, APOCALYPSE `fd6df8c0`) — so the
lots take no draw without the mod; 1.21.1 alone differs (`2df593e7` APOCALYPSE), because Cobblemon is installed there
and 190 Centers / 157 gyms / 133 Marts are in its plan. Deployed to the owner's `1.21.1` instance
`DEPLOYED-8725c2cb` (`cityworld-7.0.0+mc1.21.1.jar` rebuilt from that head). Nothing pushed, no release.
