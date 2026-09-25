# Changelog

All notable changes to the modern port of CityWorld — NeoForge, and MinecraftForge on 1.20.1.

Settings and terrain changes only affect **newly generated chunks** — existing chunks never
regenerate, so a fresh world (or unexplored land) is needed to see worldgen fixes.

## Unreleased

### Added

- **Alex's Caves joins the cave pool** (1.20.1, where the mod exists). Five of its biomes — magnetic,
  primordial, toxic, forlorn hollows and candy cavity — now appear as CityWorld's underground patches,
  in bigger, rarer cells than the vanilla types, with the whole band painted in the biome's own rock
  (galena, limestone, radrock, guanostone, chocolate) the way the mod's surface rules do, and its cave
  shapes — the ferrocave, the dino bowl, the forlorn canyon, the cake cave — allowed through as the
  structures they are. Its abyssal chasm is deliberately left out: it is a sea-floor biome whose
  trench carves down from the sea bed, which under dry land would be a pit open to the sky.
- **Cave biomes keep more of their character.** The cave-decoration pass now reads every generation
  step but ores, not three. Alex's Caves puts its look everywhere (acid lakes, magnetic ruins, the
  caveman house — in the strongholds step); on the old three steps magnetic caves would have kept one
  feature. Still safe on a city chunk: only features no surface biome can place survive, and every
  survivor is biome-checked at its position.
- **Fruit orchards.** A farm's orchard used to be six hand-drawn vanilla trees. It now draws from a
  fruit-tree pool per climate (`#cityworld:orchard/temperate`, `cold`, `dry`, `tropical`, on the
  configured-feature registry), grown through the tree's own feature so a mod's fruit hangs where the
  mod put it. Pam's HarvestCraft 2 Trees ships forty-odd of them, split by climate (apples and walnuts
  in temperate country, olives and dates on a savanna farm, bananas and mangoes in the jungle), and
  vanilla's cherry is in the temperate pool on every version. Two orchards in three draw from the pool;
  the classic oak and birch orchards keep a third.
- **Pam's HarvestCraft 2 Crops in the fields.** Thirty of its field crops — corn, tomatoes, lettuce,
  onions, garlic, cucumbers, peppers, brassicas, strawberries, peas, beans, grains, roots, squash,
  cotton, flax, rice — join `#cityworld:farm/crops`, so a tilled field grows them at a random stage.
- **`lockCustomize` for modpacks.** With `lockedWorldPreset` set, `lockCustomize = true` in
  `config/cityworld-startup.toml` removes the Customize button for that preset, so a new world always
  gets the preset's `world_settings` datapack entry unchanged. A pack that replaces that entry (schematics,
  shops, landmark announcements, its own street and villager names) is then authoritative; nothing a
  player can click switches it off.
- **Every chest table has an `_extra` hook now.** The thirteen tables that had none — building, bunker,
  warehouse, storage shed, sewer, mine, food, and the three works' input and output tables — end with a
  reference to an empty `cityworld:chests/<name>_extra` at weight 15, the same shape the vault rooms,
  hospital, shop, pond and nightstand have had since 5.9.0. A pack replaces just that file to seed its
  own items (quest parts in the industrial warehouse, say) without touching CityWorld's contents.
- **Large caverns** (`largeCaverns`, on by default for MODERN and APOCALYPSE, a free toggle for any
  style). Rare, big rooms astride the deepslate line, roughly y -50 to +25, shelved and rough inside
  rather than a blob: terraces every six blocks with a vertical face, rough walls, a roof and a floor
  always closed over. Room for a cave biome to be something, whether vanilla's lush and dripstone or a
  mod's. Tune without a rebuild with `-Dcityworld.caverns.region` (rarity) and `-Dcityworld.caverns.room`
  (size).
- **Dungeon Crawl is reserved.** Its dungeon is declared underground, so the planner ignored it and its
  entrance tower came up through a highrise roof. A structure fit can now say `reserve: true`, and a
  buried structure that does reserves only the chunks its surfacing pieces touch, plus a one-chunk
  doorstep, not its whole underground box. Shipped for `dungeoncrawl:dungeon`.
- **Every container gets a loot table.** A new end-of-lot pass walks each chunk's block entities and
  gives every empty, untouched container the lot's default table: pooled storage furniture (Macaw's
  cabinets and drawers, Fantasy's chests and lockboxes), the chests inside a pasted schematic, a modded
  crate in a warehouse. Warehouses roll the warehouse table, farms the farmworks one, hospitals,
  shops and the vault their own, everything else the building table, and every table ends in an
  `_extra` hook. Vanilla containers take a deferred table the vanilla way; a mod inventory that exposes
  only an item handler is filled at generation. Machines are excluded by `#cityworld:loot/never`
  (furnaces, hoppers, brewing stands, jukeboxes, lecterns…), and a container that already holds
  anything is left alone.
- **The vault armoury has an identity.** A weapon rack of item frames along one wall (items from the
  item tag `#cityworld:armoury/weapons`, so a gun mod adds guns), armour stands wearing one to three
  pieces of a set along another (`#cityworld:armoury/armour`), ammunition crates with a shelf over
  each along the third (rolling the new `cityworld:chests/vault_ammo`, with its `_extra` hook for a
  mod's ammunition), and the two armoury chests. It was a row of barrels.
- **Dungeon Crawl and Battle Towers are placed** when installed: their structure sets are in the
  shipped allow-list, the land tower is declared a beard so the ground rises to its floor, and the
  forecast reserves the towers' real footprints. Cataclysm's sets are in the shipped list too, so the
  separate `cataclysm-cityworld-compat` jar is no longer needed.

## 5.13.0

Released for all six Minecraft versions. One theme: CityWorld now knows exactly where every
structure will be before it plans, and shapes the land to it properly. Also the minute-long stall
next to a large modded structure is gone.

### Changed

- **CityWorld now knows exactly where a structure will be before it plans the city around it.** It
  makes the same placement call Minecraft will make later, ahead of any chunk, so the ground it keeps
  clear is the structure's real footprint plus one chunk, not a square guess. Measured on one
  2,809-chunk sweep: chunks reserved fell from 1,160 to 279, and the 804 chunks that used to be kept
  empty for a structure that never came are gone. The self-test now proves the forecast against what
  the chunk really stores on every version.
- **The ground under a large modded structure is shaped on every side.** The blend used to reach only
  as far as the chunk pipeline could read, which left the far side of a 174-block prison as a vertical
  wall. It now reads the forecast instead, at any distance. Beside a multi-storey wall the ground also
  aims at the building's base rather than the average of every storey, which had raised a 30-block
  mound.
- **The ground under a tall structure now rises as a hill, not a stepped pyramid.** The blend's run
  is sized from its rise (two and a half blocks of run per block of climb, and never less than
  sixteen) and its contours are wobbled by seeded noise, so a prison forty blocks above its plain
  gets a hundred-block slope instead of a terraced ramp.
- **The city keeps off the whole slope, not just the structure.** The ground reserved around a
  structure now reaches as far as its blend does, sized from its rise, so no house is planned where
  the hill will rise around it and no road runs into the hill's wall. A flat village still reserves
  one chunk past its footprint.
- **A structure that sinks itself below the surface no longer sits in a one-block trench** at a
  sea-level plain: the ground under it may now settle to sea level itself, which is a dry beach in
  CityWorld, rather than stopping one block above it.
- **A structure that cuts its own volume out of a hill can be declared `shave`** in
  `cityworld:structure_fit`: the plan is lowered to the storey the hill cuts into inside its box and
  feathered back to natural ground outside it, only ever lowering and never below the waterline. Set
  for Cataclysm's acropolis.

### Fixed

- **The minute-long stall next to a large modded structure is gone.** Planning a city block that a
  structure reservation cuts through (Cataclysm's acropolis keeps the city 12 chunks away) could take
  over a minute and freeze the server with it. A multi-chunk building's flood fill kept re-entering
  chunks the reservation had refused, planning a fresh lot each time — exponentially many times. It now
  stops at the reservation. On the seed it was reported from, that block plans in a second instead of
  38 to 72. Planning one block also no longer blocks other blocks from being planned at the same time.

## 5.12.0

Released for every supported Minecraft version — 1.20.1, 1.21.1, 1.21.11, 26.1, 26.2 and 26.3 — which
brings the five older lines up from 5.10.0/5.10.1.

A worldgen release, and all of it about the seam where a structure meets CityWorld's own terrain.
Structures from other mods — Cataclysm in particular — now sit in the land instead of on top of it.

### Fixed

- **Village houses no longer hang in the air, and the ground they sit on reaches a chunk further.**
  A structure that asks Minecraft to bend the terrain under it (`terrain_adaptation: beard_thin`, which
  is what villages, pillager outposts and many modded structures use) was only getting half of that
  contract: CityWorld lays its own terrain and never ran vanilla's beard. Measured on a fixed seed,
  745 of 745 columns now sit exactly on the block their piece declares as its floor, with none buried.
  The blend also now reaches one chunk past a structure's own footprint, which is what removed the
  sheer drops at its edge.
- **No more moats.** The ground under a structure could be cut below sea level, and CityWorld floods
  anything it plans under water — so a buried structure left a water-filled trench beside it. One was
  8 blocks wide with 14 blocks of standing water in it. Terrain is never lowered below the waterline
  now; cutting into a dune above it still works.
- **A tall building no longer sits on a mountain.** Where a structure has storeys stacked over the
  same footprint, the ground was aimed at the *top* storey's floor rather than the base, so a 97-piece
  tower built terrain up to its roof — a snow mound climbing 65 blocks with a vertical face. It aims
  at the base the building stands on.
- **Floating structures are left alone.** A structure that hangs above the ground was variously
  filled with a column of strata, dug out from underneath, drained of the water it stood in, or had a
  rectangular bite taken out of a neighbouring hillside. None of that happens: terrain stays where it
  is and the structure's lower parts sit embedded in it.
- **No more half-built schematics.** A CityWorld landmark whose footprint straddled the ground
  reserved for a structure placed the chunks outside the reservation and silently dropped the rest.
  A building is not a per-chunk decision — the whole site is now taken or skipped.
- **Buried structures still get their cavern**, and only they do. The 10-block clearance that keeps an
  ancient city reading as one hall rather than a row of boxes no longer applies to anything standing in
  the open, where it just ate the landscape.

### Notes

- Terrain fixes only affect **newly generated chunks**. A fresh world, or unexplored land, is needed
  to see any of this.
- Per-structure tuning lives in the `cityworld:structure_fit` data map, so a pack can declare how much
  room a modded structure needs and whether its ground should be shaped at all. The Cataclysm entries
  ship with the mod and are inert if Cataclysm is not installed.
- A structure much larger than vanilla's own 128-block bound (Cataclysm's frosted prison is ~174) can
  still be unblended along one far edge. Minecraft only lets a chunk see structures whose origin is
  within 8 chunks, and vanilla has the same limit; a different mechanism is needed and is planned.

## 5.11.0

Released for Minecraft 1.20.1 only — the other versions are unchanged and stay on 5.10.1 (26.3) and
5.10.0 (1.21.1, 1.21.11, 26.1, 26.2).

### Added

- **Minecraft 1.20.1, on MinecraftForge.** A sixth jar, `cityworld-<version>+mc1.20.1.jar`, for
  Forge 47 — the first CityWorld build that is not NeoForge. It was asked for on CurseForge, because
  1.20.1 is where a great many mods still live. Nothing is cut down for it: all thirteen world
  styles, the ruined Nether and the End cities, strongholds, mines, sewers, interiors, schematics
  and the Customize screen all work as they do everywhere else, and **the plan of a world is
  identical to the other five versions** — same seed, same city, checked by the self-test's plan
  hashes rather than by eye.
- **The furniture and map mods come too.** Macaw's doors, trapdoors, windows, fences, roofs, lights
  and stairs, Fantasy's Furniture, Biomes O' Plenty and JourneyMap are all supported on 1.20.1 on
  the same terms as on the newer versions.

## 5.10.1

Released for Minecraft 26.3 only — the other versions are unchanged and stay on 5.10.0.

### Added

- **Minecraft 26.3.** A fifth jar, `cityworld-<version>+mc26.3.jar`, for NeoForge 26.3 (beta). Same
  cities, same seeds — the plan of a world is identical to the other four versions. 26.3's new **dappled
  forest** joins the MODERN climate as the damper edge of plains country, and **poplar** wood turns up in
  buildings by itself through the plank and leaf tags.
- **Tents pitched from wool stairs on 26.3.** Campgrounds now pitch their tents the way 26.3's own
  abandoned camps do — a roof of wool stairs meeting at the ridge, open sides, fence posts under the eaves,
  straw beds inside — in the campground's own colour. On earlier Minecraft versions the tent is the stepped
  wool one it always was.

## 5.10.0

### Added

- **Macaw's doors, trapdoors, windows, fences, roofs and lights furnish the city.** Nine new block-tag
  pools under `cityworld:fittings/` and `cityworld:light/` (see PALETTES.md): every house, shed, office,
  shop and factory now draws its street door and its interior doors from a pool by what the building is
  for (shop fronts for shops, metal doors for industry, cottage-to-modern styles for homes), houses get
  framed windows (joined into runs) and a trapdoor hatch from the pools, office walls take framed windows
  where they took panes, railings, paddocks, park edges, barn pens and factory yards draw their fences,
  and streets are lit by Macaw's lamp posts. All of it comes from the mods' own family tags, so
  **Macaw's Biomes O' Plenty add-on** joins by itself with every BoP wood. Nothing changes in a world
  without the mods: each pool falls back to what CityWorld always built.
- **Macaw's staircases** where Macaw's Stairs is installed: MODERN houses get compact or terrace
  treads with a railing beside each, platforms at the landings and balcony rails along the opening
  above — the layout the mod's own players build — and every building's stairwell takes the treads
  and platforms. Without the mod the stairs are as they were.
- **MODERN houses have pitched roofs.** The stepped roof layers are now slopes — Macaw's roof blocks
  matched to the roof material by name (a willow-plank house under a willow-plank roof, with the ridge
  cap), any roof block from the pool otherwise, and the roof material's vanilla stairs when no roof mod
  is installed. The slope starts with an eave on the wall tops; corners and ridges are shaped by the
  roof block's own logic; gable ends are built in the roof's own wood. CLASSIC keeps its stepped roof.
- **The rest of Macaw's**: ornamental metal fences round gardens and parks (the panelled metal fence leads the
  construction-site mix), Macaw's gates on paddocks
  and barn pens, garage doors on half the factories and warehouses, tiki torches round campfires, and
  garden lights on park gates.
- **Macaw's Lights** in interiors: wall lanterns, wall lamps and candle sconces on the walls,
  chandeliers, lanterns and ceiling lights under the ceilings, candle holders and paper lamps on tables,
  its standing lamps as two-tall floor lamps, and its ceiling fan lights.
- **Silos.** A metal storage silo on a red steel frame — a hopper chute underneath, a stepped cone roof with
  a hatch, and a caged spiral stair tower beside it with a catwalk onto the roof — takes a building lot in
  industrial districts, often several side by side sharing a height and a paint. The old nether-brick and
  brick silo schematics are retired, and the slab silos with them (they were swamping industry); the G45 station stays.
- **A Minecraft 1.21.1 build** (`cityworld-5.10.0+mc1.21.1.jar`, NeoForge 21.1) joins 1.21.11, 26.1 and 26.2 —
  the same city on every version; blocks that version lacks stand in as their nearest older cousin.
- **Gasometers.** A rare large industrial lot, two or three chunks square: a telescoping gas holder in a
  lattice guide frame standing in a water trough, with a ladder up one column to the walkway on the top
  ring. The bell stands at a different height on every one — nearly empty to full, its lifts rising out of
  the trough as it fills.

### Fixed

- Factories had no way in: a walled factory drew no street door at all, and a fenced yard's gaps were left
  to a coin flip per side. Walled factories now open onto the street like every other building (metal doors
  where a mod supplies them), every fenced yard has at least one opening per street chunk, and wooden yards
  get a gate from the gate pool. A factory's yard also uses one fence, not one per chunk.
- A storage mod's filing cabinet, framing table and decoration table were being classified as
  furniture whenever those mods shared a folder with the furniture ones at tag-generation time; the
  furniture roles are now derived from the furniture mods only.

## 5.9.0

### Added

- **Modpacks can lock the world type.** Set `lockedWorldPreset = "cityworld:apocalypse"` (any preset id)
  in `config/cityworld-startup.toml` and every new single-player world is made with it: the create-world
  screen opens on it, the World Type button is greyed out, and Customize still works with the style held.
  Empty (the default) changes nothing. A dedicated server keeps using `level-type` in `server.properties`.
- **A CityWorld End: cities on vanilla's islands.** The End is vanilla's throughout — the central island, the
  obsidian pillars, the dragon fight, the void ring, and beyond it the outer islands exactly as vanilla grows
  them, with their chorus forests, biomes and **end cities**. CityWorld adds the city on top:
  - **Cities stand on the flat tops of the outer islands and nowhere else.** Island tops within four blocks of
    street level (y 59) are levelled to take the streets — but only under a building or road and across a short
    apron beside one, easing back into the island; an island nobody built on is exactly as vanilla made it.
    Short bridges hop between neighbouring islands, standing on obsidian footings where an island passes
    beneath and spanning open void without pylons.
  - **Settled by region.** A slow field splits the outer islands into city country and wild country — clumps of
    city, clumps of nature, about half of what an unthinned End would build — so there is room for the wild
    End, and for **biome mods' End biomes**: Biomes O' Plenty's end wilds, end flats, end reef and end
    corruption (anything a mod registers with TerraBlender for the End) generate with their own ground and
    plants. Districts follow the overworld's style and settings, graded by how much of each island is
    buildable — no farms or outland, there is no water out there — and CityWorld steps aside wherever an end
    city stands.
  - **Nothing digs through** — no mines, sewers, cisterns or caves, because an island is only a few dozen
    blocks thick — but buildings keep their basements wherever the island under them is thick enough,
    shallower or absent towards the rim and never showing from below.
  - **Never ruined**, even under an APOCALYPSE or DESTROYED overworld: the dragon kept everyone out. Built
    from the overworld's palette with the End's own blocks blended in (about one wall, roof or floor in three:
    purpur, end stone bricks, obsidian, amethyst — the new `#cityworld:build/end_stones` tag, yours to widen or
    empty with a datapack), and lit by end rods.
  - Customize has **Realms → End** (*Cities on the islands* / *Vanilla*), and modpacks can lock it with
    `cityworldEnd` in `config/cityworld-startup.toml`.
- **CityWorld's Nether and End are now the default.** Every CityWorld world type creates the ruined-city
  Nether and the CityWorld End unless you switch them back to Vanilla under Customize → Realms — and a
  dedicated server started with `level-type=cityworld:…` now gets them too, which it never could before.
  Existing worlds keep the realms they were created with.
- **A ruined-city Nether.** Customize has a new **Realms → Nether** choice. *Ruined city*
  replaces the Nether with the overworld's own city — same seed, same streets, same buildings — burnt and
  collapsed, in Nether biomes (crimson, warped, soul sand, basalt, wastes, and any mod's Nether biomes such as
  Biomes O' Plenty's, spread evenly by climate), on netherrack and blackstone with lava seas and Nether ores.
  **Portals link 1:1**, so the ruin you arrive in stands exactly where its overworld building does. It is full
  height with no bedrock roof, and Nether fortresses and bastions still generate, so blaze rods and the road to
  the End are intact. Modpacks can lock it with `ruinedNether = "cityworld"` in `config/cityworld-startup.toml`.
  Trees in its parks, yards and avenues grow as huge crimson and warped fungi and giant red and brown
  mushrooms — and Biomes O' Plenty's hellbark trees when it is installed (a datapack can add any mod's trees
  to `#cityworld:nether_trees`).
- **Bastions in the ruined-city Nether get a cavern and a way down.** A bastion always generates at y 33, which
  in a full-height ruined Nether is deep under the streets. It now sits in its own carved cavern, and a ruined
  blackstone shaft — a broken collar with a soul campfire at street level, a ladder down — leads onto its roof.
  A datapack can give any other buried structure the same treatment with `#cityworld:carve_cavern`.
- **No more "experimental settings" warning for CityWorld worlds.** Vanilla asked about every CityWorld world
  it created, because CityWorld adds a fourth dimension and vanilla only trusts the three it ships. CityWorld
  now answers it for you when its own dimensions are the only reason — anything else experimental (another
  mod's dimension, an experimental datapack) still asks.

### Changed

- **`/cityworld` now visits the city before the fall.** The `cityworld:city` dimension is the overworld's
  twin: it takes the overworld's own style and settings (including anything set in Customize), so it is the
  same city street for street — but with no decay, no overgrowth, no hidden zombie spawners and no grim
  interiors. On an Apocalypse world that is the city as it was. It used to be a ruined Modern city with its
  own settings, which did not match the overworld's plan. **Worlds that already have the dimension keep
  what they had.**

### Fixed

- **The Nether's caves and city ground were missing their biome's character.** Underneath a crimson or warped
  forest — and anywhere that wasn't wild, open countryside — CityWorld skipped the decoration step that carries
  a Nether biome's own touches: glowstone and magma, and with Biomes O' Plenty installed its orpiment buds and
  fumaroles, blackstone spines, rose quartz, flesh tendons and eyebulbs. Those now appear throughout, roughly
  twice as much of them as before (measured over a 49x49-chunk sweep: rose quartz buds 333 -> 759, orpiment
  7,436 -> 9,271, pus bubbles 2 -> 80, eyebulbs 2 -> 24).
- **Biomes O' Plenty's Withered Abyss and Visceral Heap generated on plain netherrack**, instead of blackstone
  and flesh. Its other Nether biomes were already right: their character comes from what grows on the ground
  rather than the ground itself.
- **The Nether's crimson forests, warped forests and soul sand valleys had no ground of their own.** They
  generated as bare netherrack — the right biome in every other way (fog, mobs, name), which is why they read
  as plain Nether Wastes. They now get their nylium and soul soil, basalt deltas get basalt, and the grass that
  parks and farms laid in the Nether becomes the biome's own ground.
- **The bastion shaft stopped short of the surface.** It climbed to the city's planned street height rather
  than the real ground above that spot, so it could end underground. It now follows the actual surface.
- **The ruined-city Nether could freeze the whole world.** Chunks stopped generating (a black void) and quitting
  hung on "Saving world". A decaying sidewalk kept looking for an uncovered spot to crumble and never gave up
  when the whole sidewalk was covered — sunk under the Nether's lava sea, or buried in rubble. It now gives up
  after a fair number of tries. A world that froze this way is fine to reopen: the stuck chunk was never saved.
- The CityWorld: Apocalypse world type showed its raw name (`generator.cityworld.apocalypse`) on the
  create-world screen.
- A dimension marked `"decayed": false` was still ruined when its style was Apocalypse or Destroyed — the
  style switched decay back on after the override.

## 5.8.1

### Fixed

- **Removed the two calls that could shut a server down.** CurseForge rejected 5.7.0 and 5.8.0 with
  "Please remove any function that shuts the Minecraft server down", and they were right to: the headless
  self-test harness and the chunk probe each called `server.halt(...)` when they finished. Both only ever
  ran behind a developer flag (`-Dcityworld.selftest=true`, `-Dcityworld.probe=`) that no player sets, and
  neither could fire in normal play — but the code shipped inside the jar, and a mod that *can* stop your
  server has no business being installed on it. There is now no `halt` or `System.exit` anywhere in the
  published jar. Both tools still work; the scripts that start them now stop them.
- No worldgen, content or behaviour changes: 5.8.1 is 5.8.0 with that code removed.

## 5.8.0

### Added

- **The airship is a real airship now, two chunks long.** It used to be drawn with the hot-air balloon's
  upright envelope, so the landmark announced as "Airship" looked exactly like a balloon. It is now a
  32-block rigid airship: a cigar-shaped envelope in one of eight two-colour liveries, four tail fins, a
  glazed control car slung underneath with a rounded bow and stern, seats, a helm and a railed observation
  deck, and an engine with a propeller either side. The car is built in a random wood, modded woods
  included, and with a furniture mod installed its seats are real chairs — one style for the passengers,
  another for the crew at the helm. Each one has a name, which the announcement carries ("Airship Endeavour"). Still
  MODERN and APOCALYPSE only, and just as rare. `/cityfind lot airship` finds one.
- **A few new names in the default pools.** Villagers can be called Sable, SableDnah, Cara, Cara Samara
  or Bil, and carry the surnames Douglas or Houiellebecq. Streets can be named Sable, SableDnah, Cara,
  Cara Samara, Ruth or Bil ("East Lower Cara Samara Gardens"). **Existing worlds keep their street
  names:** a street's name is worked out again in every chunk it crosses, so simply growing the list would
  have renamed nearly every street at the edge of explored ground. The new names are drawn from a
  separate roll instead, so only about one street in forty changes. A datapack that replaces the street
  names replaces these too.
- **Streets are named everywhere, not just on the signs.** Stand on a road and `/cityinfo` says which
  street it is ("street: North 5th Street", or both at a junction), F3 shows it too, and hovering a road
  on the JourneyMap map names it. With JourneyMap the city plan also **labels the streets on the map**,
  once you zoom in: each street is named once per city block it crosses, including in cities you have
  not been to yet. Names match the signs exactly; they come from the same plan.
- **`/cityfind street <name>` finds a street** by any part of its name, ignoring case
  (`/cityfind street cara samara`, `/cityfind street 5th`), and reports the nearest road on it;
  `/cityfind street tp <name>` takes you there. Tab completion offers the streets around you.
- **For mod authors: schematic buildings can be pasted turned, and read.** `Clipboard` gains a whole-building
  `paste(level, nwX, groundY, nwZ, rotation, mirror, random)` (the turned footprint's north-west corner
  lands on `nwX, nwZ`) and `saveTemplate()`, which returns the building as vanilla structure NBT — a copy,
  for things like a placement preview. Both are new since 5.7.1, so look them up and catch `LinkageError`
  if you also support older jars. (StoryTeller's structure placement uses them.)

### Fixed

- **The log no longer fills with "block tag #cityworld:furniture/… is empty or unbound".** On a world
  with no furniture mod installed, every piece of furniture in every chunk re-resolved its (legitimately
  empty) pool and warned about it — one client session logged 94,000 of those lines. Pools are now
  resolved once per tag and rebuilt only when tags reload (a datapack change is still honoured), and
  an empty furniture pool is mentioned once, at INFO, as "no mod supplies #…". Empty *build* and
  *farm* palettes still warn, once, because those are real faults. Also a small worldgen saving: the
  registry walk and sort behind each furniture pick happen once instead of per piece.

## 5.7.1

### Added

- **Every jar says which build it is, and so does the log.** The startup line now reads
  `CityWorld 5.7.1+mc1.21.11 (build a1b2c3d4 on master, 2026-09-11T…Z)`, and the same stamp is on the
  jar's manifest (`unzip -p <jar> META-INF/MANIFEST.MF | grep Build-`) for checking a jar without
  loading it. A version number answers "which release"; when a jar has been copied between instances
  or rebuilt mid-session, "which bytes" is a different question — and the log line is the one that
  says what actually *ran* when something is reported. A `-dirty` suffix means it was built from
  uncommitted changes. The F3 line carries it too, replacing the jar-timestamp stamp it used to show.

  Shared format with the other mods in this family, so a support question gets the same answer
  whichever one is being asked about.

### Fixed

- Nothing user-facing. This release exists so the build that ships alongside StoryTeller's can be
  identified exactly.

## 5.7.0

### Added

- **JourneyMap integration — the map now knows what CityWorld is building.** Install JourneyMap and
  the map gains five things:
  - **The city plan, drawn over ground nobody has explored.** Districts are tinted by what they are
    — highrise, municipal, industrial, park, farm — with the street grid over them, and the streets
    of the countryside included. JourneyMap can only map what a player has *seen*; CityWorld decided
    where those roads go before anyone arrived, so the map shows the shape of a city you are still
    walking towards. It stays drawn behind you as you travel.
  - **Hover any chunk and it tells you what is planned there** — district, lot kind, schematic name,
    shop, and what the building holds inside ("Highrise · office building · Office cubicles",
    "Farm · Potato field"). Again, including chunks nobody has visited.
  - **Rare landmarks become waypoints as they generate**, from the same curated
    `announcedLandmarks` list the chat announces use, and whether or not chat announces are on.
  - **`/cityfind`, `/cityfind lot` and `/cwlocate` drop a marker** on whatever they find, so you can
    walk to it without copying coordinates down. Landmarks and search results go into two separate
    waypoint groups so they can be shown or hidden apart.
  - **Controls where you would look for them**: a **City plan** switch in JourneyMap's own options
    screen, a toggle button on its fullscreen map, and `/citymap [on|off]` for servers.
    `/citymap keep <n>` (or the same setting in the options screen) sets how much of the plan your
    client holds — the cost of keeping it drawn is entirely client-side, so the number is yours to
    pick.

  It is a **soft dependency**: the JourneyMap API is compile-only, no JourneyMap class is touched
  unless JourneyMap is installed, and every callback is wrapped so a fault of CityWorld's can never
  take the map or the game down. Other map mods can hook the same seam
  (`me.daddychurchill.CityWorld.api.MapMarkers`) without the generator knowing about them.

### Changed

- **Landmark markers sit on the building, not its corner.** Landmarks announce from their
  footprint's north-west chunk, which put a pin in the car park of anything large; schematics
  (rotation-aware) and hospital departments now report from the middle of what they built.
- **Farms say what they are growing** — "Potato field", "Pasture", "Vineyard", "Oak orchard",
  "Paddock, livestock" — in `/cityinfo` and on the map. Three lot kinds that reported their code
  names now read plainly: *schematic*, *open ground* and *paved lot*.
- The **Cara Sutra** schematic is now **Cara Samara**, in its filename, title and announcement.

## 5.6.0

### Added

- **Fantasy's Furniture furnishes the city** — every set (Nordic, Necrolord, Dunmer, both Bone sets,
  and any set its author releases later) plus the Decorations add-on. The sets share one block
  vocabulary and CityWorld recognises it in the block registry at startup, so **a set released after
  this version is furnished on the spot**, two sets in one jar included, and a set that reshapes a
  piece (Dunmer's two-wide oven) is placed by the shape the block itself reports. Its pieces are
  **multi-block**: two-tall chairs, two-wide desks, dressers and benches, 2×2 bookcases, 2×3
  wardrobes and **2×2 double beds** place whole or not at all. Beds of every kind — vanilla, modded
  singles, doubles — come from one pool.
- **Tabletop and wall scatter with variety** — the Decorations add-on's books, bottles, food, candles,
  coins, tankards, platters, mirrors, banners and fairy lights join the decoration pools; stack
  heights, colours and fill levels vary per placement. Freestanding floor lamps, a rug pool, and
  Necrolord bricks in the modern stone palette.
- **Shelves.** A third of wall decoration is now a shelf at waist height: a vanilla `*_shelf`
  **stocked with keepsakes** (books, a clock, bottles — bones and skulls on APOCALYPSE), or a
  Fantasy's shelf or top-half slab with something stood on it.
- **Offices, shops and flats get the wall pass** (art, sconces, shelves) that only house rooms had.
  Two-tall pieces (large mirrors, banners) and two-wide paintings hang properly, with wall behind
  every cell.
- **Desk clutter.** Office desks without a computer get paper stacks, books or a mug from a new
  `decor/desk` pool.
- **Grim decoration on APOCALYPSE.** Skulls, cobwebs, bone piles, gravestones, spider webs, soul gems
  and potion bottles fill half of the ruined world's floor, table and wall decoration, from three
  new `decor/grim_*` pools; the other styles never see them.
- For mod authors: the furniture data map grew `layout`, `props`, `vary`, `indexProperty` and
  `reconnect` (see `PALETTES.md`); `parts: 2` still works. Picks are footprint-aware, so a wide
  piece is only chosen where a room has the space for it.

### Fixed

- Paintings and sconces no longer hang on windows, chandeliers or the inside of stairwells: a wall
  mount now needs a real wall behind it, and blocks are placed before the art.
- The bedroom nightstand could replace part of the bed in a narrow room; it now only goes where the
  floor is clear.
- Interior columns and hanging lights no longer cross a CENTER stairwell (the row of wall blocks
  across the stair head on every floor, and the chandelier over the stairs).

## 5.5.0

### Added

- **Furniture mods furnish the city.** Install Macaw's Furniture and/or MrCrayfish's Refurbished
  Furniture and CityWorld builds with them: kitchens with counter runs, sinks, stoves and a fridge
  with its freezer stacked on top; dining sets and lounge suites; two-block baths, toilets and
  basins spread around real bathrooms; wardrobes and bedside lamps; desks with computers. Nearly
  1,000 modded pieces across 16 role tags — and every piece is placed **facing the right way**, via
  a per-block data map that absorbs each mod's own facing conventions. No furniture mods? Every room
  keeps its classic vanilla look.
- **Every building has an interior now.** Offices, cubicle floors and meeting rooms; **apartment
  towers** (about one tower in seven — lobby at street level, flats above); **schools, courthouses
  and city halls** inside government buildings; museum exhibit floors; factory workshops and
  control rooms with console banks; warehouse racks of crates and barrels; shop floors with
  browsing aisles between the counters. Basements of occupied buildings hold storage; **vacant
  buildings hang FOR SALE / TO LET signs** by the door and stay dark and empty inside.
- **Interiors are lit.** Hanging lights below every furnished ceiling (a datapack-extendable pool),
  plus ceiling fans, wall art — paintings and item frames with clocks and keepsakes — and sconces.
  Dark interiors now *mean* derelict.
- **Industrial chemicals.** Tanks, vats and pits hold a `#cityworld:build/chemicals` palette —
  concrete powders, water, lava, slime — and **18 Mekanism fluids are pre-wired** (verified against
  the Mekanism jar) for whenever it reaches this Minecraft line. Water towers are actually full.
- **F3 tells you more.** The interior type ("Courthouse", "Apartments", "Vacant"…) shows in F3 and
  `/cityinfo`, plus a build stamp line so you always know which jar generated what.
- **A rebalanced city.** More shops, fewer libraries, fewer empty shells; hospitals and the vault
  get pool furniture at every desk.
- For mod authors: `PALETTES.md` documents how to tag your own furniture, lights, decor and fluids
  into CityWorld from your own jar — no dependency in either direction.

### Fixed

- Farm fields no longer come up empty or single-species; flower fields include every vanilla and
  Biomes O' Plenty flower that can actually survive there.
- Modded ocean and shore biomes place correctly (gravel beaches at the shoreline, not across open
  sea), and modded biomes get their proper ground blocks.

## 5.4.0

### Added

- **Biome mods work in CityWorld worlds.** Biomes O' Plenty and most other modern biome mods register
  their biomes through **TerraBlender**, which CityWorld's own biome source never saw — so a CityWorld
  world with BoP installed got its 450 blocks and none of its 69 biomes. It now reads what those mods
  registered and folds them into the world. Measured with BoP installed: 113 biomes offered, 86 of them
  actually appearing. **On by default** — installing a biome mod is the intent; turning them off is the
  deliberate act (`world.useModdedBiomes`, or the Customize screen when such a mod is present). Costs
  nothing when you have no biome mods.

- **Modded biomes get ground they can win.** Two separate problems were measured, and both are fixed.
  Continentalness was scaled against extremes CityWorld's terrain never reaches, so it only ever
  emitted `-0.78..0.54` where every other axis spans `-1..1`; it now measures its own ends and uses the
  full range. That alone was not enough — most unreachable biomes overlapped our ranges on *every* axis
  and still lost, because the climate lookup picks the nearest point and TerraBlender's regions field
  vanilla's biomes as competitors. So `world.moddedBiomeShare` (default `0.35`, on the Customize
  screen) reserves a share of the map where the lookup runs with vanilla's points removed. Measured
  with BoP: **34 → 52 distinct modded biomes, 12.4% → 36.1% of ground**, with CityWorld still naming
  about two thirds. `0.0` restores the previous behaviour exactly.

- **The last few biome-mod biomes get a route to the ground.** With Biomes O' Plenty installed, 54 of
  its 59 biomes already generated. Of the rest, `spider_nest` was fine (it is a cave-pool biome), and
  four never appeared for two different reasons. `bog`, `fungal_jungle` and `snowblossom_grove` lose
  the climate lookup everywhere — against vanilla, and against other modded biomes even at
  `moddedBiomeShare = 1.0` — so there is no gap to widen and no share that reaches them;
  **`#cityworld:surface_pool`** now hands a listed biome a share of cells outright. `gravel_beach` was
  never *asked* about: shores are decided from terrain, and the modded lookup is deliberately limited
  to land above the waterline, so **`#cityworld:shore_pool`** stands a variant in on ground already
  ruled a shore. Patches respect the temperature and humidity the biome declared for itself, so a bog
  will not turn up in a desert. Both tags are datapack-driven, so the next biome mod needs no code.

- **Biomes look like themselves.** CityWorld lays its own surface and never runs a biome's surface
  rules, so a biome whose identity *is* its ground generated as ordinary grass or sand — BoP's gravel
  beaches were sand, its volcanoes grass. Ground is now data-driven two ways: biome tags
  (`#cityworld:ground/gravel`, `/sand`, `/podzol`, `/coarse_dirt`, `/terracotta`, `/basalt`, `/mud`,
  `/stone`) for vanilla materials, and a **`cityworld:ground` data map** for anything else — which is
  what lets BoP's `lush_desert` get its own orange sand, `wasteland` its dried salt and
  `origin_valley` its own grass, rather than a vanilla stand-in. Mappings came from BoP's own surface
  rules, not guesswork. A pack can add or override any of it.

- **Three vanilla biomes were wrong too**, in every world, mods or not: `old_growth_pine_taiga` and
  `old_growth_spruce_taiga` now get their **podzol** floor, `mangrove_swamp` its **mud**, and `grove`
  its snow dusting — a grove is a snowy forest at ordinary height, so it fell between the biome list
  and the elevation-based snow cap and got neither.

- **Biomes O' Plenty's cave biomes join the cave pool** — glowing grotto, crystalline chasm and
  spider nest turn up underground alongside the vanilla four. Shipped inert, so it does nothing unless
  BoP is installed. (Fungal jungle was in this list and has been taken out: it is a *surface* biome —
  its features are placed on the heightmap — so underground it generated bare stone.)

- **Bigger biome regions by default.** `world.biomeScale` now defaults to `1.5` rather than `1.0` —
  regions you can walk across rather than change three times on the way to the shops. The Customize
  labels moved with it, so "Default" names the step the setting actually starts on.

- **Worlds are warmer, and ice caps mean something.** Across a dozen worlds the world read cold — snowy
  biomes *and* iced peaks everywhere, and little desert. Two causes: the temperature field sat neutral,
  and **the MODERN ice cap was purely a matter of height**, so every mountain iced over even standing in
  a desert. The ice line now rises with temperature, so caps belong to cold places rather than to all
  high places; and `world.climateWarmth` (default `0.25`, on the Customize screen too) leans the whole
  field warm — about 17% — without narrowing its range, so frozen peaks and deserts both still happen.

- **Farm fields are a tag, so mods can grow in them.** Crops come from `#cityworld:farm/crops` and
  flowers from `#cityworld:farm/flowers` (with `#cityworld:farm/tall_flowers` for the tall fields),
  each drawn per field so a field still reads as *a field of something*. Ships Farmer's Delight and
  Biomes O' Plenty entries marked optional, so they cost nothing until those mods are installed.
  **Two-block crops work**: BoP's barley is a `half=lower`/`half=upper` plant rather than an aged crop,
  so the planter checks for that and places both halves — which means any modded tall crop works, not
  just barley. Every vanilla flower is in the pool now, tall ones included.

- **Farms look like farms.** The crop mix was an even split, which put tilled fields — the thing a farm
  is *for* — at 11.5% of farm lots, behind trees, flowers, ground crops, pasture and grass, while
  fallow and ferns held ground that reads as empty from the air. The MODERN pools are now weighted:
  **tilled 27.3%, pasture 22.7%, ground crops 14.1%, trees 12.7%, flowers 10.2%, grass 8.4%, fallow
  4.7%** — measured over 3,074 farm lots, not eyeballed. Each climate keeps its character: potatoes
  and beets in the cold, cane and melons in the jungle, cactus in the desert.

- **Every bare container has themed loot**, with a modder seam. Hospital, nightstand, shop, pond and
  the three vault rooms each get their own table, and each has an empty `_extra` companion table that
  a datapack can fill without touching CityWorld's own — so a gun mod can drop ammo into nightstands
  and rifles into the vault armoury.

- **Permission nodes for every command.** CityWorld gated purely on operator level, so a permissions
  manager could not grant or deny any of it — and op is all-or-nothing: handing a moderator
  `/cityfind` also handed them `/cityschem`, which writes blocks, and `/cityexport`, which writes
  files. Five nodes now split them up: `cityworld.info` (default everyone), `cityworld.teleport`,
  `cityworld.find`, `cityworld.schematic` and `cityworld.export` (default operators). Works with
  LuckPerms, SableCraft Standards' `/rank`, or nothing at all — **with no permissions manager
  installed the behaviour is exactly as before**, since each node's default is the old op check. The
  console keeps working regardless. See [`NODES.md`](NODES.md).

- **You choose who decorates the wild.** On MODERN/APOCALYPSE, wild land was getting vanilla's biome
  features *and* CityWorld's cover, with no way to change it — which is why wild forests read lush.
  `world.wildDecoration` (and a "Wild plants" picker on the Customize screen) now takes `BOTH` (the
  default, unchanged), `CITYWORLD` for CityWorld's cover alone, or `VANILLA` to hand the wild over
  entirely — which is the interesting one if you have a biome mod installed, since it lets that mod's
  own plants and trees stand on their own.

- **26.2's cinnabar and sulfur build with the rest.** Both new stone families join the MODERN and
  APOCALYPSE decorative palette, so cities on Minecraft 26.2 grow deep-red cinnabar and yellow sulfur
  buildings alongside the blackstone and copper ones. They are warm colours in a palette that was
  short of them. Stone buildings do not become any *more* common — the palette just has more stones in
  it. On earlier Minecraft versions the entries are simply absent.

- **Palettes document how to take a block out**, not just how to add one. NeoForge tags support a
  `remove` list, so a datapack can drop a single block — the sulfur, say — without replacing the whole
  palette. See PALETTES.md.

### Fixed

- **`/cwlocate` finds every biome the world has.** It searched CityWorld's own biome matrix rather
  than the biome source, so biome-mod biomes, the surface/shore pools and cave biomes were invisible
  to it — vanilla's `/locate biome` could find them and CityWorld's could not. It now asks the same
  lookup the world generates from (and unlike vanilla's, it teleports).

- **Wild plants are vanilla's on MODERN and APOCALYPSE.** Running CityWorld's cover *and* vanilla's
  biome features doubled the planting and read as unnaturally lush. Vanilla's pass is also the half a
  biome mod extends, so on the modern styles it is the one worth keeping. Still switchable to `BOTH`
  or `CITYWORLD` per world.

- **The F3 readout fits on screen.** The level line was running past the edge; it is two lines now.

## 5.2.0

### Added

- **The End is reachable again — strongholds generate.** CityWorld suppressed every vanilla structure
  so it could own the chunk, and that quietly took strongholds with it: no stronghold means no End
  portal, so eyes of ender had nothing to find and a CityWorld world could not be finished by normal
  play. Strongholds are back, on vanilla's own ring placement, so `/locate` and eyes of ender agree
  with where they actually are.
- **Trial chambers and ancient cities generate too.** Which vanilla structures a world keeps is the
  block tag `#cityworld:allowed`, so a datapack can widen it — including to a structure from another
  mod — without touching the mod.
- **Real cave biomes underground: lush, dripstone and deep dark** — and **sulfur caves on Minecraft
  26.2**. They come in patches rather than filling the whole underground, so they stay a find. Each
  one decorates itself the way it does in a vanilla world: moss and glow berries, dripstone clusters,
  sculk, sulfur and cinnabar veins. Which biomes are in the pool is the tag `#cityworld:cave_pool`,
  so a modded cave biome can join it from a datapack.
- **Biome varies with depth, not just position.** The biome map is three-dimensional now, which is
  what lets cave biomes exist at all — and, as a side effect, what let ancient cities work, since
  vanilla only places them in deep dark.
- **The underground is tunable from a datapack** — a new `caves` settings group controls how big a
  cavern an ancient city gets, how far below the surface cave patches start, and the size, rarity and
  depth band of every cave type. Empty means the shipped defaults. See the configuration docs.

### Changed

- **Underground mob spawning differs where a cave biome landed.** Cave biomes carry their own spawn
  lists — thinner than open caves, and the deep dark carries none at all, plus wardens. That is the
  point of having real cave biomes rather than cave-shaped decoration, but it is a genuine gameplay
  change and not only a cosmetic one.
- **Vanilla structures can generate under a city.** A trial chamber may cut into a basement or a
  sewer. Ancient cities sit far below both (they top out around y −10, where cisterns bottom out at
  y 49), so those meet only the mine networks — which reads rather well, as though the miners downed
  tools when they broke through.

## 5.1.0

### Added

- **CityWorld runs on Minecraft 26.1 and 26.2** as well as 1.21.11. Minecraft moved to quarterly
  calendar releases, so the mod is now built and tested against several versions at once. Jars carry
  the version they are for — `cityworld-5.1.0+mc26.2.jar` — so there is no guessing which is which.
- **The mod has its icon and its own links in the mod list.** It previously showed no logo, and its
  home and issue links still pointed at the original author's pages — so bug reports would have gone
  to someone who cannot fix them. Issues now point at this fork's GitHub tracker.

### Fixed

- **26.2's block changes are absorbed without any visible difference.** 26.2 stopped declaring dyed
  blocks and copper as individual blocks — 145 of the blocks CityWorld builds with, including every
  colour of wool, terracotta, concrete, stained glass and beds, plus the whole copper family. Cities
  look exactly as they did; a given seed still produces the same city on every supported version.

## 5.0.3

### Added

- **Building palettes are block tags now, so new blocks join them on their own.** The palettes used
  to be runs of hand-written constants, which is why CityWorld was still building houses out of the
  six 1.14 wood types. Eight of them — planks, wool, terracotta, glazed terracotta, concrete,
  concrete powder, stained glass and the MODERN decorative stones — now resolve from
  `cityworld:build/*` block tags at world creation. Planks went from six woods to **twelve** the day
  this landed (pale oak, cherry, mangrove, bamboo, crimson and warped join the palette), and wool
  from ten colours to all sixteen.
- **Modded blocks arrive the same way.** A mod that tags its blocks conventionally — planks in
  `#minecraft:planks`, stone in `#c:stones` — starts showing up in cities as soon as it is installed,
  with no patch and no compatibility pack. For mods that don't tag their blocks, or for putting a
  mod's blocks somewhere they wouldn't naturally go, a datapack can extend any palette directly. See
  the new `PALETTES.md`.

### Changed

- **Palette odds are unchanged despite the wider contents.** Each tag occupies exactly the number of
  slots the constants it replaced did, so wooden houses are no more common than in 5.0.2 — only
  *which* wood varies. Without this a twelve-block planks tag would have doubled wood's share of
  every wall in the world, and a modpack with thirty wood types would have drowned the palette
  entirely.
- Palettes that are a curated look rather than "all of a family" are deliberately left as fixed
  lists: the muted greyscale of unfinished buildings, the pale civic palette of government offices,
  and the ordered road and maze lists.

> Because the palettes are wider, a seed generates slightly different **materials** than it did on
> 5.0.2. The terrain and city layout are unchanged, and existing chunks never regenerate.

## 5.0.2

### Changed (from a full review of the announce feature before release)

- **Announcements are per-world and curated.** Chat goes to the players in the world the landmark
  generated in (was: the whole server, with coordinates meaningless in other dimensions). And only
  the genuine rares announce by default — vaults, zoos, biodomes, hospitals, airships, saucers and
  flagged schematics; a new `world.announcedLandmarks` datapack list lets a server widen or narrow
  the set (castle, oil platform, radio tower, mine entrance, campground, fish pond and more are
  available keys). Previously every `reportLocation` call site would have hit chat: fish ponds,
  shacks, campgrounds, and every hospital department separately.
- **Vault road tunnels no longer announce.** Each road chunk through a vault was announcing its own
  hash as a different "Vault N" — several contradictory lines for one vault, none matching the number
  in the lobby. The entrance chunk's announcement is the only one now.
- **`/cityfind` understands titles.** It now matches and displays the `.yml` `Title`, so the name a
  player reads in chat ("The Statue of Liberty") is findable — previously only the raw file name
  ("liberty") matched, and results printed the file name.
- **Sidecar `.yml` values may be quoted anywhere.** Quoting was only honoured on `Title`, so ordinary
  YAML habits like `Decayable: "true"` silently parsed as *false*. Quoting (and inline `#` comments)
  now work on every key.
- **`.nbt` drop-ins no longer stamp recorded air.** A structure-block export records explicit air for
  its whole bounding box; placing one carved an air cuboid into the surrounding terrain, unlike every
  other format. Recorded air is now stripped unless the sidecar sets `KeepAir: true`, matching
  `.schematic`/`.schem`/`.litematic`.
- **Customize screen fixes.** Cycling the Style no longer silently resets the groups the screen has
  no widgets for (city radius, naming lists, mob bags) back to defaults — a datapack's custom values
  survive the cycle. The floors picker now always shows exactly the value that will save (an
  out-of-range datapack value used to display snapped but save raw), and its button no longer reads
  "…floors: 24 floors".

### Added

- **Landmark announcements.** A new `world.broadcastSpecialPlaces` setting (off by default) announces
  landmarks in chat as they generate — "Castle generated near 1520, -340" — to everyone on the
  server. Upstream had this and the port had never implemented it. The same events are always
  written to the debug log either way, so turning it off loses nothing but the chat.
- **Schematics can announce themselves.** The `.yml` sidecar's `BroadcastLocation` key was being
  parsed but never acted on; it now works, and fires once per building rather than once per chunk of
  its footprint. It needs the world's `broadcastSpecialPlaces` on as well.
- **`Title:` in schematic `.yml`.** Announcements previously could only use a schematic's filename,
  so `winchester.schematic` announced as "winchester". A title lets it read "The Winchester Tavern".
  Defaults to the filename, so a catalog already named nicely needs no change.
- **Seven bundled landmarks now announce themselves**, with proper names: the Statue of Liberty,
  Midwich, the Spiritwind Monument, the Dragon Statue, the Old Water Tower, the Hedge Maze and the
  Great Pagoda. Previously exactly one schematic was flagged to announce and it had no title, so it
  would have said "liberty". Only distinctive landmarks were picked — the common flavour buildings
  stay quiet.
- **The Statue of Liberty is copper now.** The build predates copper, so its skin was 13,563 light
  blue wool blocks. Converted to a vanilla `.nbt` structure with the wool remapped to weathered
  copper; every other block carries over unchanged.
- **Bundled schematics may now ship in any supported format.** The bundled index only accepted legacy
  `.schematic`, even though drop-in schematics could be `.schem`, `.litematic` or `.nbt` — which
  meant no bundled build could use a block newer than 1.12.
- **A Max building floors picker** in the single-player Customize screen (World section). The setting
  existed and worked from a datapack, but there was no UI control for it. Offers 8–60 floors; a
  hand-edited value in between displays as the nearest and is only overwritten if you turn the dial.

### Changed

- **The vault lobby got its Fallout glow-up.** The blast door is now a proper cog: a 10-block toothed
  steel gear — gray plate body, copper cross-spokes and hub, iron rim, eight gear teeth — with a
  hazard-striped doorway "rolled open" through the bottom, striped threshold, chain-and-grate winch
  machinery overhead, and VAULT-number wall signs flanking the opening. The old door was also centred
  half a block off the corridor (odd-width design against a 2-wide corridor); the gear is even-width
  and lines up exactly. The lobby itself gains an overseer's control console, a locker row (barrels
  and an anvil), and copper air vents let into the walls.

### Fixed

- **The vault's surface hut stood metres above the hillside.** Its floor height came from the cached
  base-terrain height plus a fudge, which overshoots on slopes — leaving a concrete stalk with a door
  in the air. It now scans the actual placed terrain at the door column and sits at real grade, with
  a deeper foundation skirt for the downhill side.
- **Lightning rods stood on moss carpet.** The rooftop-rod pass ran after overgrowth and stopped its
  downward scan at the first non-empty block — which, on a reclaimed roof, was the moss carpet or
  leaf litter lying on it, so the rod sat a notch above the roof looking like it floated. The pass
  now runs before overgrowth (the rod is part of the building, and the greenery creeps around it),
  and its scan requires a solid, load-bearing top face, so loose cover can't be mistaken for a roof
  either way.

## 5.0.1

### Fixed

- **Overgrowth debris was all identical.** Leaf litter and pink petals are segmented blocks whose
  default state is always one segment facing north, so every scrap of fallen-leaf debris was the same
  tile in the same orientation. Segment count and facing are now rolled per placement, so a littered
  floor reads as scattered rather than tiled.
- **Azaleas grew in thickets.** Two separate causes, both fixed:
  - The lush-cave surface tell ran per chunk with 6–10 attempts in each, but a lush region spans
    roughly 25 chunks — so a single patch could stack up hundreds of azaleas. Most lush chunks now
    get none, and the rest get one or two.
  - Azalea and flowering azalea together made up 10% of the overgrowth ground-cover pool. Their
    silhouette is loud and distinctive, so one in ten reclaimed surfaces wearing one read as a
    thicket (most visible on APOCALYPSE with overgrowth on).

### Changed

- **Overgrowth shrubs now match the local woodland.** The slot the azaleas held is filled with a
  persistent leaf block of the biome's own tree species — spruce in taiga and groves, birch in birch
  forest, jungle, acacia in savanna, dark oak, pale oak in the pale garden, mangrove, cherry in a
  cherry grove, oak everywhere else. Being `persistent`, they never decay away after placing.
  Azalea foliage is kept for flower forest and meadow, where a flowering shrub actually belongs.
- **More variety in the ground clutter.** Modern vegetation joins the pool: wildflowers, plain
  bushes, feral sweet-berry bushes (grown, not bare sprigs), dry grass, and — at about 1% — a
  firefly bush, for a rare glow in the ruins. Roads pick up wildflowers, dry grass and dead bushes
  alongside the existing light dusting.

### Housekeeping

- Version bumped to **5.0.1** and the `-beta` tag dropped.
- 21 screenshots added to the README.
- CurseForge description now links to
  [sablecraft.co.uk/cityworld-reforged](https://sablecraft.co.uk/cityworld-reforged/) for the
  configuration and command deep dives.

## 5.0.0

First public release of the NeoForge port — a full port of the original Bukkit/Spigot CityWorld
plugin to Minecraft 1.21.11 / NeoForge 21.11.42.

- Procedural cities: roads with street-name signs, sidewalks, roundabouts, bridges, tunnels,
  furnished buildings from houses to highrises, municipal/industrial/farm districts, parks with zoos
  and biodomes.
- Underground: mine networks with lift shafts, sewers, bunkers, basements, cisterns, loot and
  spawners; an APOCALYPSE-only Fallout-style vault.
- Nature: CityWorld's own vendored terrain noise extended to the `-64..319` world, winding cave
  tunnels, lush cave patches, basalt-lined lava pools, a CityWorld-aware biome source.
- Inhabitants: named villagers employed at their shop's trade, animals, sea life, hostiles.
- 13 world styles, ~100 per-world settings delivered as a datapack, an in-game Customize screen, and
  the `/cityinfo`, `/cityworld`, `/cityschem`, `/cityfind`, `/cityexport` and `/cwlocate` commands.
- Custom schematics in `.schematic`, `.schem`, `.litematic` and `.nbt`.
