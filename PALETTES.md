# Building palettes

CityWorld decides what a wall, floor or roof is made of by drawing from a **palette** — a weighted
list of blocks. Most of those palettes are now defined by **block tags**, which means you can change
what your cities are built from with a datapack, without touching the mod.

This is also how a *"CityWorld × some other mod"* compatibility pack works.

## The short version

Most of the time you don't need to do anything.

CityWorld's palettes are built on top of the ordinary vanilla and common tags — so a mod that tags its
blocks the way mods are supposed to **already shows up in CityWorld cities the moment you install it**:
its planks (`#minecraft:planks`), wool and terracotta, and on NeoForge its concrete and glazed terracotta
(`#c:concretes`, `#c:glazed_terracottas`). No pack required. **Stone never joins by itself** — there is no
stone palette built on `#c:stones` — so a mod's bricks go into a palette by hand (see below).

> New to datapacks? Start with the [Beginner's guide to datapacks](Beginners-Guide-to-Datapacks): where a
> pack goes, the folder names per Minecraft version, and a first pack that proves yours is being read.
>
> **On 1.20.1 every `tags/block/` path on this page is `tags/blocks/`** (and `tags/item/` is `tags/items/`).

To choose the blocks for one **part** of a building — office walls, house roofs, the road surface — use a
[per-part palette](#per-part-palettes-from-614) (from 6.14).

A compatibility pack is for the other cases:

- the mod doesn't tag its blocks, so nothing knows they're planks
- the mod tags them correctly but you only want *some* of them in your cities
- you want a mod's blocks in a palette they don't naturally belong to — a castle-brick highrise,
  say — which no amount of correct tagging will do for you

## The palettes

Each of these is a block tag CityWorld reads when the world starts. Add to one and the blocks you add
start appearing in that palette, in chunks generated after the next restart.

| Tag | Used for | Ships as |
|---|---|---|
| `cityworld:build/planks` | house and building walls, floors, ceilings, roofs; shacks and sheds | `#minecraft:planks` |
| `cityworld:build/wool` | building walls, house floors | `#minecraft:wool` |
| `cityworld:build/terracotta` | house walls, water towers, bunkers, factory and bunker tanks | `#minecraft:terracotta` |
| `cityworld:build/glazed_terracotta` | house floors | `#c:glazed_terracottas` |
| `cityworld:build/concrete` | house walls, factories, bunkers, oil platforms, water towers | `#c:concretes` |
| `cityworld:build/concrete_powder` | factory and bunker tanks | `#minecraft:concrete_powder` |
| `cityworld:build/stained_glass` | factory and bunker tanks | the sixteen dyed glasses |
| `cityworld:build/chemicals` | what industrial tanks and silos hold when fluids are enabled | concrete powders, water, lava, slime — plus 18 Mekanism fluids (brine, acids, lithium, uranium hexafluoride…) verified against Mekanism-1.21.1-10.7.19.85 and marked optional; add your own mod's fluid blocks the same way |
| `cityworld:build/modern_stones` | the decorative stone palette used by the MODERN and APOCALYPSE styles | 32 curated blocks (8 of them Minecraft 26.2+) |
| `cityworld:build/end_stones` | blended into every building, house, government and factory pool **in the CityWorld End only** — about one pick in three | purpur block and pillar, end stone bricks, obsidian, amethyst |

### Per-part palettes (from 6.14)

The palettes above are by **kind of block** — planks, wool, concrete — and each one feeds many parts of many
buildings. A **per-part palette** says which blocks one *part* is made of, and only that part: office walls,
house roofs, the road surface. They all **ship empty**, and an empty one changes nothing. Fill one and that part
is drawn from your blocks alone; every other choice in the building — its shape, floors, windows, furniture —
stays exactly as the seed would have made it.

| Tag | The part |
|---|---|
| `cityworld:palette/building/wall` | office blocks, shops, libraries, museums and warehouses: the outside walls (inner walls follow them) |
| `cityworld:palette/building/foundation` | their foundations and basements |
| `cityworld:palette/building/floor` | the floor and ceiling slabs between their storeys |
| `cityworld:palette/building/roof` | their roofs |
| `cityworld:palette/building/column` | their corner columns and trim |
| `cityworld:palette/building/window` | their windows (a pane or pane-like block is drawn thin, a full block fills the opening) |
| `cityworld:palette/government/wall`, `…/foundation`, `…/floor`, `…/roof`, `…/column` | city halls and other civic buildings (windows follow `building/window`) |
| `cityworld:palette/house/wall`, `…/floor`, `…/ceiling`, `…/roof`, `…/window` | houses, the modern houses among them (`house/window` reaches shacks too) |
| `cityworld:palette/shack/wall`, `…/floor`, `…/roof` | rural shacks and cabins |
| `cityworld:palette/shed/wall`, `…/roof` | garden and farm sheds |
| `cityworld:palette/factory/wall`, `…/office`, `…/support`, `…/smokestack` | a factory hall's inner walls, its office block, its supports and its chimney |
| `cityworld:palette/street/surface` | paved roads — **one pick for the whole world**, so every road matches. Filling it also sets aside a road mod's asphalt and markings (Macaw's Roads); to keep those, point `#cityworld:fittings/road` instead |
| `cityworld:palette/street/lines` | the lines painted on them (one pick per world) |
| `cityworld:palette/street/sidewalk` | pavements (one pick per world); placed as given, and the default is a **slab**, so a full block raises the pavement half a step |

Stairs, slabs and doors that CityWorld derives from a building's wall follow a replaced wall where the block has
them. Walls and floors that would fall (sand, gravel, concrete powder) are still drawn as smooth stone, as before.

**One style only.** Put the same path under the style's name and it applies only to that style:
`cityworld:palette/apocalypse/building/wall` is read for Apocalypse worlds, and wins over
`cityworld:palette/building/wall` there. The style names are the ones in the Customize screen, lowercase:
`modern`, `apocalypse`, `classic`, `destroyed`, `astral`, `maze`, `floating`, `flooded`, `sanddunes`, `snowdunes`,
`nature`, `metro`, `sparse`.

An example — Apocalypse office blocks in mossy and cracked bricks, Chipped's where installed, and asphalt-dark
roads in every style:

`data/cityworld/tags/block/palette/apocalypse/building/wall.json`
```json
{
  "values": [
    "minecraft:mossy_stone_bricks",
    "minecraft:cracked_stone_bricks",
    { "id": "chipped:etched_stone_bricks", "required": false }
  ]
}
```

`data/cityworld/tags/block/palette/street/surface.json`
```json
{ "values": ["minecraft:blackstone"] }
```

How it picks: once per building chunk for buildings (a building's chunks then agree, as they always have), once
per house, shack or shed, once per world for streets — each pick on dice seeded from the world seed, the part and
the place, so the same seed builds the same city. A tag has no weights; to make one block more common than
another in a part, add a weighted [substitute](#block-substitutes) on top.

**Not covered yet:** malls, hospitals, the subway, the vault, bunkers, mines and the smaller industrial lots
(warehouses, silos, gasometers) keep their own fixed looks. Block substitutes reach all of those.

### Biome pools

These biome tags decide which biomes CityWorld places by a route other than its own climate lookup. They
ship with Biomes O' Plenty ids marked optional, so they cost nothing without it — and any biome mod can be
added the same way.

| Tag | What it does |
|---|---|
| `cityworld:cave_pool` | Biomes placed **underground**, as patches, with their own decoration. |
| `cityworld:surface_pool` | Biomes given a share of surface cells **outright**, for biomes the climate lookup would never choose. |
| `cityworld:shore_pool` | Beach variants that may **stand in for CityWorld's own beach** where the terrain says shore. |
| `cityworld:ocean_pool` | Ocean variants that may stand in for CityWorld's own ocean and deep ocean. Ships empty (BoP adds no overworld oceans). |
| `cityworld:nether_pool` | The biomes of the ruined-city Nether. Ships as `#minecraft:is_nether` and `#c:is_nether`, so a mod's Nether biomes join by themselves. |
| `cityworld:ground/<kind>` | Which biomes get which ground: `gravel`, `sand`, `red_sand`, `stone`, `podzol`, `coarse_dirt`, `terracotta`, `basalt`, `mud`. Checked before the built-in answers. For any other block, use the `ground` data map ([below](#biome-ground)). |

The surface and shore pools exist because some biomes cannot win a climate lookup at all: they overlap
CityWorld's climate on every axis and still never sit nearest, so no amount of widening or
`moddedBiomeShare` reaches them. Listing one hands it ground directly. Patches still respect the
temperature and humidity that biome declared for itself, so a bog will not appear in a desert.

### Furniture

CityWorld furnishes rooms from **role tags** — "something to sit on", "something to eat at" — so a
furniture mod appears in kitchens, dining rooms, lounges, bathrooms and studies without CityWorld
knowing anything about it.

| Tag | Used for |
|---|---|
| `cityworld:furniture/chair` | dining chairs, desk chairs, stools |
| `cityworld:furniture/table` | dining tables, coffee tables |
| `cityworld:furniture/sofa` | lounge seating runs |
| `cityworld:furniture/desk` | studies |
| `cityworld:furniture/counter` | kitchen counter runs |
| `cityworld:furniture/cabinet` `…/drawer` `…/wardrobe` | storage |
| `cityworld:furniture/sink` | kitchen sinks, bathroom basins |
| `cityworld:furniture/bath` `…/toilet` | bathrooms |
| `cityworld:furniture/bookshelf` `…/lamp` | studies and lounges |
| `cityworld:furniture/bed` | bedrooms — ships the sixteen vanilla beds, so modded singles and doubles join one pool |
| `cityworld:furniture/shelf` | wall shelves at waist height — ships the vanilla `*_shelf` blocks, which are containers and get **stocked** with keepsakes (books, a clock, bottles…); a modded shelf gets something from the surface pool stood on it, and a top-half slab stands in when nothing resolves |
| `cityworld:furniture/floor_lamp` | freestanding (two-tall) floor lamps — the fence-and-lantern is the fallback |
| `cityworld:furniture/crate` `…/stove` `…/fridge` … | warehouses, workshops, kitchens (the appliance and interiors roles) |

Doors, windows, fences, roofs and street lamps are not furniture; they have their own pools under
**Fittings**, below.

**Fantasy's Furniture is supported as a family, at runtime.** Every one of its sets (Nordic,
Necrolord, and any set its author adds later) registers the same 39 block names, so CityWorld
carries one vocabulary — `chair`, `wardrobe`, `bed_double`, `oven`, with their multi-block layouts —
in `cityworld/furniture_vocabulary/fantasyfurniture.json` inside the jar. At startup CityWorld scans
the block registry for any namespace holding that vocabulary and pools its pieces on the spot, so
**a set CityWorld was never built against still furnishes the city**, a jar carrying two sets is two
namespaces and needs nothing special, and a datapack entry always overrides a derived one. The same
JSON drives `scripts/gen_furniture_tags.py`, which additionally bakes tag files for the sets present
at build time and reports any block name the vocabulary does not know. A set that reshapes a piece
(its `multi_block_index` no longer matching the layout) is skipped with a log line, never placed as a
row of origins; the self-test reports `furniture.setsDetected` and `furniture.setNotes`. Sets do not link to each other in-game (a Nordic sofa will not join a
Necrolord one — the mod's connection logic requires the same block), which is fine: CityWorld picks
one piece per role per room, so a run is always one set. The sets' planks and wool join the building
palettes by themselves (they are tagged `#minecraft:planks` / `#minecraft:wool`); Necrolord's bricks
are only in `#c:stones`, so they are listed in `build/modern_stones` by hand. The Decorations add-on's
tabletop, floor and wall scatter is classified into the decoration pools, with stack heights and
colours randomised per placement.

#### Mod authors: supporting CityWorld from your side

**You do not need us to add your mod.** Ship these two files in your own jar and CityWorld picks your
furniture up the moment both mods are installed. (On **1.20.1** ship only the tag: there a data-map file
in your jar would replace CityWorld's whole `furniture` map — see [Data maps](#data-maps-swapping-blocks-unlit-lights-biome-ground).) Neither file does anything without CityWorld, so
there is no dependency and no harm in shipping them always.

**1. Say what your blocks are.** `data/cityworld/tags/block/furniture/chair.json` in *your* jar:

```json
{
  "replace": false,
  "values": [
    "yourmod:oak_dining_chair",
    "yourmod:birch_dining_chair"
  ]
}
```

`"replace": false` matters — it merges with everyone else's rather than replacing them. Your own block
ids always exist when your mod is loaded, so they need no `"required": false`.

#### 2. Say which way they face

⚠ **This is the part worth reading**, because getting it wrong seats everybody with their back to the
table, and the furniture mods disagree about it — Macaw's disagrees with *itself* between its chairs
and its sofas, Refurbished's facing is the back, Fantasy's is the front.

CityWorld tells a seat **which way its occupant should look**. Your block's `facing` may mean something
different, so declare the difference in `data/cityworld/data_maps/block/furniture.json`:

```json
{
  "replace": false,
  "values": {
    "yourmod:oak_dining_chair": { "facingOffset": 0 }
  }
}
```

`facingOffset` is **how far clockwise to turn the front direction to get the value you want in
`facing`**, in degrees — one of `0`, `90`, `180`, `270`.

- `0` — your `facing` already *is* the direction the piece fronts (the way a sitter looks, the way a
  cabinet's doors open).
- `180` — your `facing` points at the back (very common: backrests, cistern side, the wall side of a
  counter).
- `90` / `270` — your model was authored on a different axis to its blockstate.

To work it out without guessing: find the variant in your blockstate with **no `y` rotation**, look at
where the identifying geometry sits in that model — backrest, door and handle, taps, cistern — and the
front is the opposite side. The offset is the clockwise turn from that front direction to that
variant's `facing` value.

**Every oriented block wants an offset**, not just seats — counters, cabinets, sinks, toilets,
drawers, wardrobes and bookshelves are all placed fronting into the room. Measure per *family*, not
per mod: one of the big mods uses `0` for its classic chair and `180` for its modern chair. Tables
that auto-connect (fence-style booleans) and blocks with no `facing` at all need nothing.

**Two-block furniture** (bed-like — a bath): declare `"parts": 2` in the same entry. CityWorld then
places `type=bottom` at the anchor and `type=head` one cell along `facing`, both halves sharing the
facing value — the vanilla bed contract, with the `type` property found by name so your own
bottom/head enum works.

```json
"yourmod:oak_bath": { "facingOffset": 180, "parts": 2 }
```

**Multi-block furniture of any shape** — a two-tall chair, a 2×3 wardrobe, a 2×2 double bed — is
declared as a `layout`: one entry per cell, relative to the piece's origin and to its **own `facing`
value**. Stand where `facing` points and look back at the piece: `right` is on your right, `back` is
away from you, `up` is up. The list index is the value written into the piece's index property
(`multi_block_index` by default; name yours with `indexProperty`), and a cell can carry `props` of its
own — the way a bed's head differs from its foot. CityWorld places the whole piece or none of it:
every cell must be inside the chunk and empty, and a floor-standing piece never overhangs a hole.

```json
"yourmod:wardrobe": { "layout": [ {}, {"right": 1}, {"up": 1}, {"right": 1, "up": 1},
                                  {"up": 2}, {"right": 1, "up": 2} ] },
"yourmod:double_bed": { "facingOffset": 180, "layout": [
    {"props": {"part": "foot"}}, {"back": -1, "props": {"part": "head"}},
    {"back": -1, "right": -1, "props": {"part": "head"}}, {"right": -1, "props": {"part": "foot"}} ] }
```

Three more optional keys, all per block:

- `props` — property values to set on every placement, by name: a sofa whose *default* state is the
  armless middle piece wants `{"connection": "single"}`.
- `vary` — property names to randomise per placement (deterministically, by position): `["count"]`
  makes a stack of books sometimes one and sometimes three; `["count", "color"]` gives a row of potion
  bottles more than one colour.
- `reconnect: true` — CityWorld may re-derive a run's connections after placing it. Only say so when
  your connection logic reads `facing` the way CityWorld writes it (offset 0): the one mod whose
  couches are offset 270 reconnected into corner shapes.

#### 3. Decoration pools (optional)

Loose decoration is drawn from three placement pools, and you can add to them the same way:

| Tag | Meaning |
|---|---|
| `cityworld:decor/floor` | stands on the ground (potted plants, big vases) |
| `cityworld:decor/surface` | belongs ON a table — CityWorld puts one underneath (table lamps, candles, crockery) |
| `cityworld:decor/wall` | wall-mounted at eye height (sconces, wall lights, paintings, mirrors — two-wide pieces declare a `layout` and run along the wall) |
| `cityworld:decor/hanging_light` | hung in the air cell below a ceiling (lanterns, chandeliers) |
| `cityworld:decor/rug` | the carpets a bedroom or hallway rug is cut from |
| `cityworld:decor/feast` | a dish on a dining table (one table in two), and pies on a mall bakery's counter — ships empty; Farmer's Delight's feasts and pies fill it |
| `cityworld:decor/stove_top` | a pot or pan stood on the kitchen cooker (two in three) — ships empty; Farmer's Delight's `cooking_pot` and `skillet` fill it |
| `cityworld:decor/desk` | what sits on an office desk that got no computer — paper stacks, books, a mug |
| `cityworld:decor/grim_floor` `…/grim_surface` `…/grim_wall` | **APOCALYPSE only** — half of that style's floor, table and wall decoration draws from these instead: vanilla skulls and cobwebs seed them, Fantasy's Decorations adds bone piles, gravestones (floor only), spider webs (walls), soul gems and potion bottles. A pack can add to them the same way, and other styles never see them. |

The floor/surface split is why a table lamp never ends up standing on the carpet: if your lamp is a
table lamp, tag it `surface`, and if it is a freestanding floor lamp, tag it `floor`.

### Fittings

The joinery a build is finished with — doors, trapdoors, windows, fences and roofs — comes from
these pools. They exist for **Macaw's** (Doors, Trapdoors, Windows, Fences, Roofs, and its Biomes O'
Plenty add-on, which pours the BoP woods into the same family tags), but each is a plain block tag,
so any mod or datapack joins the same way. Where vanilla has the thing the pool ships seeded with it;
the pools vanilla cannot fill — sloped roof blocks, framed windows — ship **empty**, and every caller
falls back to what it always built (stepped or stair roofs, plain glass). An empty fittings pool is
therefore normal, not a fault.

| Tag | Used for | Ships as |
|---|---|---|
| `cityworld:fittings/door` | the street door of a house, an office, a shed | vanilla wooden doors + Macaw's cottage, classic, four-panel, beach, western, swamp, tropical, waffle, whispering, mystic, nether, barn, stable, modern, glass and bamboo families |
| `cityworld:fittings/interior_door` | doors between rooms | vanilla wooden doors + Macaw's four-panel, classic, paper, shoji, glass, modern, waffle |
| `cityworld:fittings/store_door` | shop fronts | vanilla wooden doors + Macaw's store door, sliding glass door, glass and modern families |
| `cityworld:fittings/industrial_door` | factories and warehouses | the iron door + Macaw's metal doors |
| `cityworld:fittings/trapdoor` | the attic hatch of a house | `#minecraft:wooden_trapdoors` (Macaw's trapdoors tag themselves into it) |
| `cityworld:fittings/window` | the window band of every house wall, and the pane half of an office wall | **empty** — Macaw's single, hinged and four-pane windows when installed; glass otherwise |
| `cityworld:fittings/fence` | the garden fence: house railings and park edges | the vanilla wooden fences by id + Macaw's picket fences, hedges, its ornamental metal fences (acorn, ornate, gothic, cathedral…) and its stone walls (pillar, railing, modern, grass-topped) |
| `cityworld:fittings/railing` | the balcony rails of the rare building whose windows are bars | iron bars + Macaw's metal fences |
| `cityworld:fittings/farm_fence` | paddocks, barn pens, a factory's wooden yard fence | the vanilla wooden fences by id + Macaw's horse and stockade fences |
| `cityworld:fittings/site_fence` | the tall fence round a construction site or a factory's metal yard — one per platmap; the panelled metal fence six picks in twenty where a mod has one, iron bars five, this pool five, barbed wire four | iron bars + Macaw's industrial metal fences (mesh, panelled, bastion…) |
| `cityworld:fittings/site_wire` | the barbed-wire quarter of the site fences | Macaw's wired fences |
| `cityworld:fittings/roof` | the pitched roofs of MODERN houses | **empty** — Macaw's `*_roof` blocks (generated by `scripts/gen_furniture_tags.py`); the roof material's vanilla stairs otherwise |
| `cityworld:build/plaza` | a paved town square (a site another mod reserved as paved, e.g. the square at spawn for Modernlife's bank): two picks per square, the paving and its lines | smooth stone, polished andesite, polished diorite, stone bricks, light grey concrete, cut sandstone |
| `cityworld:fittings/atm` | a cash machine against an inside wall, screen to the room: the ground floor of city halls and courthouses, shops and malls, at the `shops.oddsOfAtmIn...` odds | **empty** — Modernlife's ATM (`modernlife:atm`, 1.20.1, optional); none otherwise, and a world without one draws nothing for it |
| `cityworld:fittings/shop_sign` | the hanging sign of half the shops, inside and (its `wall_` form, found by name) over the door | **empty** — Farmer's Delight's canvas signs, one per dye (optional) |
| `cityworld:fittings/gate` | the gate in a paddock, a barn pen or a factory's wooden yard — one per platmap | the vanilla fence gates by id + Macaw's single-block highley and pyramid gates |
| `cityworld:fittings/garage_door` | the street bay of half the factories and warehouses (three wide, three to seven high) | **empty** — Macaw's garage doors when installed; the door and its frame otherwise |
| `cityworld:fittings/stairs` | the staircase of a MODERN house, and every building's stairwell | **empty** — Macaw's compact and terrace stairs when installed (their matching railing, platform and balcony are found by name); the vanilla stair run otherwise |
| `cityworld:light/street_lamp` | the lamp posts along every street | **empty** — Macaw's street lamps when installed; the fence-and-glowstone post otherwise |
| `cityworld:fittings/path` | the walkways of a park (the cross and ring paths, the maze approaches): one paving per platmap, like the fence, so connected park chunks share it. A thin one (Macaw's `*_paving` plates, half a pixel) is laid OVER the ground like a carpet, a full block (its flagstone, weave, bond, crystal floor) IS the ground: told apart by the block's collision shape, so mix them freely | **empty** — Macaw's Paths and Pavings' stone pavings (flagstone, running bond, crystal floor, windmill weave and the six `*_paving` patterns of each stone, 120 blocks) when installed; the grass path otherwise |
| `cityworld:fittings/track` | a country track: the strip between fields, the farmyard's path to the well | **empty** — Macaw's Paths' gravel, dirt, podzol and sand path blocks when installed; the grass path otherwise |
| `cityworld:fittings/road` | the surface of every city street, **one pick per world**; from its id the mod's matching pieces are found by name — `<surface>_broken_line` (the dashed centre line), `<surface>_crosswalk` (the zebra on every junction arm), `<surface>_manhole` (one stretch in three), the namespace's `sidewalk_step` (kerbs along every pavement edge) and `roadsign_rod` + signs, each on the driver's side of its traffic (`roads.driveOnLeft`): `stop` at a T, `dead_end`, a pair of speed limits back to back where town (`roads.speedLimitUrban`, 50) meets country (`roads.speedLimitRural`, 30) with repeats in the slower zone (`roads.oddsOfSpeedRepeat`), and `city_1..5_sign` where a road leaves town. A missing piece is simply not drawn | **empty** — Macaw's Roads and Streets' `asphalt` when installed; the style's own pavement, quartz lines and fence-post signs otherwise |
| `cityworld:fittings/skybridge` | with `roads.skybridges` on (the Apocalypse default): a two-wide deck between an upper floor of two neighbouring towers of different buildings (both at least `roads.skybridgeMinFloors` floors, a floor at the same height), one pair in `roads.oddsOfSkybridge`, a doorway cut where it meets each wall; half the pairs get a rope bridge instead | **empty** — Macaw's Bridges' iron, glass, stone brick, deepslate, blackstone and prismarine bridges when installed; nothing otherwise |
| `cityworld:fittings/rope_bridge` | a one-wide rope bridge: in the wild, from a ridge across a valley at least `roads.wildBridgeDepth` (8) deep to where the far side meets the deck, up to `roads.wildBridgeSpan` (48) long, at most one per `roads.wildBridgeRegion` (32-chunk) region, half the regions (`roads.oddsOfWildBridge`); and the rope skybridges, and the rare rope across a street (`roads.oddsOfStreetRope`, one per district at most) | **empty** — Macaw's Bridges' rope bridges, one per wood, when installed; nothing otherwise |
| `cityworld:light/tiki` | two-tall torches flanking a campground's fire | **empty** — Macaw's tiki torches when installed; nothing otherwise |
| `cityworld:light/garden` | a light on the inner post of each park entrance | **empty** — Macaw's garden lights when installed; nothing otherwise |

Three things the pools do that a plain tag would not:

- **Doors are pooled by use, not by look.** A building draws its street door from the pool for what it
  *is* — a shop takes a shop front, a factory a metal door — and every house, shed and office picks one
  door and one interior door per lot, so the rooms match.
- **Windows turn themselves.** A framed window is thin and centred, so it has a facing; CityWorld places
  it the way it would connect a pane — along the wall — and turns it across that run. A house's window
  band is then *reconnected* so the frames read each other and join into one run; an office wall,
  drawn at the generation stage where no neighbour logic exists, gets a grid of single frames.
- **Roofs are matched by name.** A MODERN house whose roof is `oak_planks` looks for `oak_planks_roof`
  in the pool, takes any pool entry if there is no namesake (a cobblestone house under a terracotta
  roof), and falls back to the vanilla stairs of the roof material — so pitched roofs exist without a
  roof mod, in stairs. The stepped layers become stairs facing the ridge, starting with an eave on the
  wall tops; corners and ridge caps (`*_top_roof`) are shaped by the roof block's own neighbour
  logic, exactly as if a player had built it; a gable's end walls, and the walls where one section's
  roof runs against another's, are built in the block the roof is made of (`redwood_roof` →
  redwood logs, `nether_bricks_roof` → nether bricks). CLASSIC keeps its stepped roof.

A park or a farm picks **one** fence for its whole platmap, so every side of a park agrees; a house
picks its own.

- **Stairs are a kit.** A tread from the stairs pool brings its family with it: `oak_compact_stairs`
  → `oak_railing`, `oak_platform`, `oak_balcony`, looked up by name in the same namespace. The house
  then builds what its owner built by hand: a platform at the corner landing, a perpendicular first
  step, three treads rising a block a cell with a railing in the cell beside each (its `toggle` set so
  the banister sits on the edge it shares with the tread), a platform at the top, and balcony rails
  along the upper floor's opening. One rail style (classic, harp, smooth) per house. Nothing goes
  under the treads — the mod's stairs are full risers.

A stacked metal fence gets its `bottom`/`top` parts from a sweep after the chunk is drawn
(`InitialBlocks.stackFenceParts`) — the one thing the mod's own neighbour logic would have set that
the generation stage cannot ask it for.

**Left out on purpose:** Macaw's two-tall *double gates* (the fence pools list the vanilla fences by
id rather than `#minecraft:wooden_fences`, where Macaw's puts its gates too); portcullises; Macaw's
loft, skyline and bulk stairs (a ramp, and shapes that spill into the next cell).

**Macaw's Lights** are furniture as far as CityWorld is concerned and go through the furniture pools
above: wall lanterns, wall lamps and wall candle holders into `decor/wall` (their `facing` is the way
the light looks, away from its wall — offset 0, measured), chandeliers, lanterns and ceiling lights
into `decor/hanging_light`, candle holders, paper lamps and the lava lamp onto tables, the standing
lamps into `furniture/floor_lamp` as a declared two-tall stack (`part=bottom` under `part=top`), and
the ceiling fan lights into `furniture/ceiling_fan`. Street lamps are the one exception, above: one
block id placed four high, and the mod reads the stack into a base, a shaft and a lit head.

### Farms

Farm fields draw from these too, so a mod's crops and flowers grow on CityWorld's farmland.

| Tag | Used for | Ships as |
|---|---|---|
| `cityworld:farm/crops` | tilled fields — one crop is drawn per field, so a field reads as *a field of something* | the four vanilla crops, plus Farmer's Delight and Biomes O' Plenty entries marked optional |
| `cityworld:farm/flowers` | flower fields and mixed meadows | `#minecraft:small_flowers` + `#cityworld:farm/tall_flowers`, minus BoP's `waterlily` |
| `cityworld:farm/tall_flowers` | the tall flower fields (sunflower, lilac and friends) | the five vanilla tall flowers, plus five BoP ones marked optional |
| `cityworld:farm/paddy` | crops that stand **in water** — a field of one is flooded: mud, standing water, the crop, every fourth row an open channel | empty; Farmer's Delight's `rice` (optional) |
| `cityworld:farm/soil` | the tilled soil of one field in three, instead of farmland | empty; Farmer's Delight's `rich_soil_farmland` (optional) |
| `cityworld:farm/compost` | heaped beside a farm's composter | empty; Farmer's Delight's `organic_compost` (optional) |
| `cityworld:farm/produce` | crates and sacks of harvest: beside the composter, in barn stalls, at the greengrocer, on a mall grocer's display | empty; Farmer's Delight's six produce crates and its rice bag (optional) |
| `cityworld:farm/bales` | what half the haystack fields and barn lofts are stacked with, instead of hay | empty; Farmer's Delight's `straw_bale` and `rice_bale` (optional) |

**The five mod-only farm pools are empty in a vanilla install, on purpose**, and every use checks for that
before it rolls any odds — so adding one of these mods changes farms, and not having one leaves every field
exactly as it was. A paddy crop is read off its block: it must be its own water (Farmer's Delight's rice is),
an integer `age` is set, and when a block named `<id>_panicles` exists it is stood on a ripe stem (with
`supporting` set beneath). Half the sugar-cane fields and one tilled field in five become paddies.

**Two-block plants work.** A crop or flower that places as a `half=lower`/`half=upper` pair — BoP's
barley, or any modded tall crop — is detected and both halves are placed. You do not need to declare
anything for that.

**A plant that cannot live there is skipped**, and the vanilla one grows instead. CityWorld asks the
block itself (`canSurvive`) before planting, which is why an End or Nether bloom inherited from
`#minecraft:small_flowers` does not leave a bald patch.

Palettes that are a **deliberate look** rather than "all of a family" are not tags and are not meant
to be extended: the muted greyscale of unfinished buildings, the pale civic palette of government
offices, and the ordered road and maze lists.

### Zoos

What lives in CityWorld's zoos is data too — two **datapack registries**, so a pack or a zoo mod can add animals
(any mod's mobs, or vanilla's) and their enclosures without touching CityWorld. A zoo pen picks its resident by
weight from every animal registered; CityWorld's own ten (pandas, camels, frogs, polar bears, foxes, goats,
parrots, bees, axolotls, turtles) are ordinary entries in `data/cityworld/cityworld/zoo/`.

**An animal** — `data/<namespace>/cityworld/zoo/animal/<name>.json`:

```json
{
  "entity": "examplezoo:giraffe",
  "name": "Giraffes",
  "weight": 2,
  "count": { "min": 2, "max": 4 },
  "baby_chance": 0.25,
  "size": "tall",
  "habitat": "examplezoo:savanna",
  "requires_mod": "examplezoo",
  "finalize": true
}
```

| Field | Default | Meaning |
|---|---|---|
| `entity` | — | the mob's id |
| `name` | — | the name on the pen's sign |
| `weight` | `1` | how often it is picked against the others; `0` removes it |
| `count` | `2`–`4` | how many in a pen |
| `baby_chance` | `0` | the chance each one is a baby |
| `size` | `small` | `small` fits any pen; `large` only a big multi-chunk one; `tall` any pen, with a four-high fence |
| `habitat` | — | the id of a habitat (below) |
| `requires_mod` | — | skip this animal unless that mod is installed |
| `finalize` | `true` | `false` skips the mob's spawn setup — for a modded mob that reads the world while it is set up, which is not safe while a world is generating |

An animal whose entity is not registered, whose `requires_mod` is missing or whose habitat does not exist is
skipped quietly, so a pack can carry entries for mods that may not be installed. To take one of CityWorld's own
out, ship the same file (`data/cityworld/cityworld/zoo/animal/panda.json`) with `"weight": 0`. Zoo animals never
despawn. In a decayed world (APOCALYPSE) the pens are broken open and empty: the animals are long gone.

**A habitat** — `data/<namespace>/cityworld/zoo/habitat/<name>.json`:

```json
{
  "ground": "minecraft:coarse_dirt",
  "scatter": [ { "block": "minecraft:grass_block", "chance": 0.3 } ],
  "water": "pool",
  "pool_blocks": [ { "block": "minecraft:lily_pad", "count": 2 } ],
  "plants": [ { "block": "minecraft:short_grass", "count": 6 },
              { "block": "minecraft:sugar_cane", "count": 1, "height": { "min": 1, "max": 3 } } ],
  "trees": [ { "log": "minecraft:acacia_log", "leaves": "minecraft:acacia_leaves", "count": 1 } ],
  "rocks": [ { "block": "minecraft:granite", "count": 1, "height": { "min": 1, "max": 2 } } ],
  "fence": "minecraft:dark_oak_fence",
  "gate": "minecraft:dark_oak_fence_gate"
}
```

`ground` covers the pen and `scatter` replaces it here and there; `water` is `none` or a `pool` in the middle,
with `pool_blocks` floating on it (`"surface": true` puts one *in* the water's top, like ice); `plants` stand on the
dry floor as a column `height` high; `trees` are short trees with an optional `hive` in the leaves; `rocks` are 3×3
crags to climb. Counts are per pen, or per chunk of a big pen. `fence` must be something that joins up like a
fence — a fence, iron bars, a glass pane. The sign, the gate's place and the pen's shape are CityWorld's.

### Schematic fit-outs

A pack can change what is inside a schematic building without shipping a new schematic: swap blocks and hang item
frames. Each file is `data/<ns>/cityworld/schematic_fitout/<name>.json` and names the `schematic` it fits out — the
file name without its extension, so `winchester` covers every family's copy. This is ZARP's Winchester, with
Phoenix's slot machine in place of the fruit machine and a loaded shotgun over the bar:

```json
{
  "schematic": "winchester",
  "blocks": [
    { "pos": [5, 11, 10], "block": "phoenix:phoenix[facing=east]", "requires_mod": "phoenix" },
    { "pos": [5, 12, 10], "block": "minecraft:air",                 "requires_mod": "phoenix" }
  ],
  "frames": [
    { "pos": [2, 14, 6], "facing": "east", "item": "gbg:shotgun[...]", "requires_mod": "gbg" }
  ]
}
```

Positions are in the schematic's own coordinates, the way it is stored (x, y up from its bottom layer, z), and so
are facings: CityWorld turns the building to fit its lot and turns the fit-out with it.

- **`blocks`** — `block` is a block state in `/setblock` syntax. It replaces whatever the schematic places at
  `pos`, before decay, so a ruined building's slot machine can be broken like the rest of it. A block two tall in
  its model but one block in the world (Phoenix's slot machine) takes one line for its cell and an air line for the
  cell above. A schematic saved without air (`KeepAir: false`) places nothing at an air cell, so a line there does
  nothing.
- **`frames`** — an item frame (`"glow": true` for a glow frame) in the cell at `pos`, hung on the wall behind it,
  `facing` into the room. `item` is in `/give` syntax for the Minecraft version, so item data such as a gun's
  ammunition is written the way `/give` takes it — components (`id[...]`) from 1.20.5 on, NBT (`id{...}`) on 1.20.1.
  Frames go up after decay, and only where the cell is still clear and the wall still stands.
- **A block's own data** goes in `/setblock`'s braces and is handed to its block entity as the building is placed:
  `mts:fuelpump{packID:"mtsofficialpack",systemName:"fuelpump",subName:"",anglesy:270.0d,tank:{...}}`. Where a mod
  keeps a block's facing in that data rather than in its state (Immersive Vehicles' decors keep a compass heading,
  `anglesy`: 0 south, 90 east, 180 north, 270 west), name it in **`heading`** (`"heading": "anglesy"`) and it turns
  and mirrors with the building like a facing does. CityWorld's own `iv_gas_stop` and `iv_g45station` put Immersive
  Vehicles' fuel pumps (full of lava, IV's stock fuel) on the gas stations' islands when its Official Content Pack
  is installed (IV's own pump without it: `iv_core_*`). A pump's `anglesy` is the way its front faces, away from
  its island. To find a cell in a schematic, read it with `scripts/region_dump.py`'s `read_nbt` (the old format
  keeps `Blocks` in y, z, x order: index `(y * Length + z) * Width + x`) or paste it in game with WorldEdit and
  count from its origin.
- **`requires_mod`** on the file or on any line skips it when that mod is not installed. A block or item that
  does not parse is skipped and named once in the log. Files apply in id order; for a cell two files both swap, the
  later file wins.

### Parked vehicles (Immersive Vehicles)

With [Immersive Vehicles](https://www.curseforge.com/minecraft/mc-mods/minecraft-transport-simulator) and a vehicle
pack, the bays of a mall's car park hold real vehicles instead of block cars (a ruin keeps its wrecks). They come
from the registry `cityworld:vehicle`, one file per vehicle at `data/<ns>/cityworld/vehicle/<name>.json`. CityWorld
ships the Official Content Pack's and the Official Automobile Pack's road vehicles (written from their jars by
`scripts/gen_iv_vehicles.py`); a pack adds its own the same way:

```json
{
  "requires_mod": "oamp",
  "vehicle": "oamp:camaro",
  "kind": "car",
  "paints": ["_white", "_red", "_blue"],
  "centre": 1.562,
  "parts": [
    { "slot": 0, "part": "oamp:wheelvintage", "subs": ["_sinty", "_mint"] },
    { "slot": 4, "part": "oamp:enginecamarov8" },
    { "slot": 5, "part": "oamp:sportseat", "subs": ["_red", "_brown"] }
  ]
}
```

- **`vehicle`** is the pack's mod id and the vehicle's file name under `assets/<pack>/jsondefs/vehicles/` in the
  pack's jar (`oamp:camaro` is `assets/oamp/jsondefs/vehicles/camaro.json`).
- **`kind`** is what a lot asks for: `car` fills car parks. (`truck`, `quad` and `fire_engine` ship for lots to come.)
- **`paints`** are the vehicle's sub names, its file's `definitions[].subName`; one is picked per vehicle.
- **`centre`** is how far forward of the vehicle's origin its wheelbase's middle is, so it stands centred in a bay.
- **`parts`** by slot, the slot's index (from 0) in the vehicle file's `parts` list. A vehicle spawned from data gets
  only the parts it is given (a player's spawn adds the defaults), so name the wheels, seats and engine: a wheel
  whose height (its file's `ground.height`) is within the slot's `minValue`..`maxValue`, a seat for each `seat`
  slot, and each slot's `defaultPart`. `subs` are the part's own sub names (leave it out for a part with none). **A
  part or sub name that does not exist makes Immersive Vehicles throw the whole vehicle away** when it loads ("Did a
  pack change?" in the log), so check them against the pack.
- **`fuel_capacity`** holds `roads.vehicles.fuel` to the vehicle's tank (its `motorized.fuelCapacity`).
- A parked car's doors are shut (a crafted one comes with them open).
- **The generator does all this for you:** `python3 -I scripts/gen_iv_vehicles.py --list <pack jar> <OCP jar>`
  lists a pack's vehicles; `--kind <pack>:<vehicle>=car` (once per vehicle) and `--out
  <your pack>/data/<ns>/cityworld/vehicle` write their files, wheels and seats picked by size from the pack itself
  and then from the Official Content Pack.
- A vehicle parks with the brake on, a charged battery and a random `roads.vehicles.fuelMin`–`fuel` millibuckets of
  lava in the tank (0–1000 by default; `fuel: 0` parks them dry — the gas stations' pumps are full). Every parked vehicle costs the client frames, so they are
  few by default: a car-park chunk holds up to `carParkVehicles` (1) in `oddsOfParkedVehicle`, and a district parks
  up to `streetVehicles` (1) at the kerb of a straight street in `oddsOfStreetVehicle` (settings group
  `roads.vehicles`, CURSEFORGE-CONFIGURATION.md).

### Ultimate Car Mod (from 7.0)

[Ultimate Car Mod](https://modrinth.com/mod/ultimate-car-mod) (mod id `car`) is the all-in-one: cars, asphalt and
slabs, road paint and signs, fuel. CityWorld uses all of it, without a vehicle pack:

- **Roads.** `car:asphalt` is in `#cityworld:fittings/road`, and **it wins the pick whenever it is in the pool**
  (with Macaw's Roads installed too, the streets are still Ultimate Car Mod's; a pack that wants Macaw's removes
  `car:asphalt` from the tag). The streets are asphalt with the mod's paint one block over the road:
  `car:line_middle` dashes down a straight street's centre, `car:arrow_zebras` bands across each crossing arm.
  Its road signs are **text** on `car:sign_post`s: `STOP`, `DEAD END`, `SPEED LIMIT 50`, `Welcome to <town>` and
  `Thanks for visiting`, `END OF SPEED LIMIT 50`, each on both faces of a `car:sign` — but **with Macaw's Roads
  installed as well, Macaw's picture signs stand wherever it has one** (STOP, DEAD END, the limits and their ends,
  on Macaw's rod), and the text signs carry only the town's name, coming and going. The mod has no manhole or kerb
  lip, so there are none; the highway's own signs (MOTORWAY, TUNNEL, STEEP ASCENT) still want Macaw's Roads or an
  Immersive Vehicles sign pack.
- **Cars.** Four vehicle files ship (`ucm_sport`, `ucm_suv`, `ucm_wood`, kind `car`; `ucm_transporter`, kind
  `truck`) and park wherever Immersive Vehicles' cars do — car parks, kerbs, `cityworld:spawn` files — under the
  same `roads.vehicles` numbers. A car is its parts, so a file lists them by slot; `{paint}` in a part's id takes
  one of the file's `paints` (`car:{paint}_sport_body` with `red`, `blue`…; the wooden cars' paints are their
  woods), and a part's `subs` are appended to its id (`car:engine_` with `3_cylinder`, `6_cylinder`). It parks with
  a full battery (the mod reads a missing one as flat), not started, a number plate, and a random
  `roads.vehicles.fuelMin`–`fuel` millibuckets of bio diesel (0–1000 by default).

```json
{
  "requires_mod": "car",
  "vehicle": "car:car",
  "kind": "car",
  "paints": ["white", "red", "blue", "black"],
  "fuel_capacity": 1000,
  "parts": [
    { "slot": 0, "part": "car:{paint}_sport_body" },
    { "slot": 1, "part": "car:iron_license_plate_holder" },
    { "slot": 2, "part": "car:medium_tank" },
    { "slot": 3, "part": "car:engine_", "subs": ["3_cylinder", "6_cylinder"] },
    { "slot": 4, "part": "car:wheel" }, { "slot": 5, "part": "car:wheel" },
    { "slot": 6, "part": "car:wheel" }, { "slot": 7, "part": "car:wheel" }
  ]
}
```

- **Gas stations.** The three shipped gas stations' pump islands get `car:gas_station`s (with their tops), full of
  bio diesel and free, through fit-outs `ucm_gas_stop`, `ucm_polar_gas`, `ucm_g45station` in
  `cityworld:schematic_fitout` — the same positions as the Immersive Vehicles pumps; with both mods installed the
  UCM ones win there (fit-outs apply in id order).

### Road signs on Immersive Vehicles' poles (from 7.0)

With Immersive Vehicles and its Official Signs pack (`osip`) or the Official Content Pack, a city's road signs stand
on IV poles, and those that carry text carry it: the **town's name** on its boards, the limit on a speed sign, and on
a highway an **overhead gantry** naming the city ahead. Each sign pack is one file in
`data/<your namespace>/cityworld/pole_signs/<name>.json`
(CityWorld ships `osip`, priority 20, and `official_content_pack`, 10); a sign no installed set has falls back to
Macaw's Roads if that is installed. The signs:

| CityWorld's sign | Where | osip | OCP |
|---|---|---|---|
| `stop` | the stem of a T | `sign_stop` | `sign_stop` |
| `dead_end` | a street's last chunk | `sign_informational` `_deadend` | `sign_deadend` |
| `town`, `town_end` | where a street leaves for the wild or a highway: the town's name, coming and going | `sign_town` `_entrance` / `_exit` | |
| `speed_limit`, `speed_limit_end` | where the limit changes (`roads.speedLimitUrban`/`Rural`) | `sign_allowed` `_speedlimit` / `_speedlimitend` | `sign_speedlimit` |
| `motorway`, `motorway_end` | a highway, leaving and coming into a city | `sign_informational` `_highway` / `_highwayend` | `sign_highway` / `sign_highwayend` |
| `tunnel`, `steep_ascent`, `steep_descent` | before a tunnel, a long climb or drop | `sign_informational` `_tunnel`, `sign_warn` `_ascent` / `_descent` | |
| `gantry` | over a highway's lanes, 24 samples out: the city it leads to | `signhighway` `_exitbblank` | |

A set for another sign pack:

```json
{
  "requires_mod": "mysigns",
  "pack": "mysigns",
  "core": "pole_core",
  "priority": 30,
  "signs": {
    "town": { "sign": "sign_welcome", "sub": "_blue", "text": "Town Name", "max": 20 },
    "stop": { "sign": "sign_stop" }
  }
}
```

`pack` is the sign pack's **mod id** — a sign pack Immersive Vehicles loads that is not also a mod (no
`mods.toml`) is not seen; `core` its pole core (`jsondefs/poles/<core>.json`); each sign names the pole
component (`sign`, a file in `jsondefs/poles/`), its `sub` (a `definitions[].subName`) and, for one with text, the
`text` field's `fieldName` and `maxLength` (`rendering.textObjects` in the same file). A higher `priority` wins; a set
with only some signs leaves the rest to the next; `town` and `town_end` (and the limit and its end) are separate
entries. **A component or sub name the pack does not have makes Immersive Vehicles remove the pole** on its first
tick ("Did a pack change?" in the log), so check each against the pack's jar. Town names come from `naming` (see
CURSEFORGE-CONFIGURATION.md).

### Putting anything in the city: spawns (from 7.0)

"How do I make X appear?" One file per thing in `data/<your namespace>/cityworld/spawn/<name>.json` puts a mob, a
mod's NPC, a parked vehicle or a block with its data (a vending machine, a workstation) in the lots, districts,
places and styles you choose. CityWorld ships none, so a world without your pack draws exactly what it drew.

```json
{
  "requires_mod": "guardvillagers",
  "entity": "guardvillagers:guard{PersistenceRequired:1b}",
  "lots": ["structure"],
  "districts": ["municipal", "highrise"],
  "styles": ["modern"],
  "at": "indoors",
  "odds": 0.25,
  "count": 1,
  "count_max": 2
}
```

| Field | Default | What it does |
|---|---|---|
| `entity` | | An entity in `/summon` form: its id, then its data (`"minecraft:villager{VillagerData:{profession:\"minecraft:librarian\"}}"`). |
| `vehicle` | | Or a parked vehicle of that [kind](#parked-vehicles-immersive-vehicles): `car`, `truck`, `quad`, `fire_engine`, or your own. |
| `block` | | Or a block in `/setblock` form, its data included (`"mymod:vending_machine[facing=north]{Stock:[...]}"`). |
| `requires_mod` | | The file is ignored unless this mod is installed. An entity whose namespace is not installed is ignored too. |
| `lots` | any | Lot class names, exactly (a subclass does not match its parent): shops `StoreBuildingLot`, `CornerShopLot`, `MallLot`; offices and towers `OfficeBuildingLot`, `FinishedBuildingLot`; `HospitalLot`, `MuseumBuildingLot`, `PlazaLot`, `ParkingLot`, `SubwayStationLot`, `RoadLot`, `HouseLot`, `FarmLot`, `ParkLot`, ... — F3 shows the one you stand in. Or the lot kinds `structure` (any building), `road`, `roundabout`, `nature`. |
| `schematics` | any | Schematic names (the file name without its extension: `Condo`, `chayats-bank`, your own). Only a schematic lot matches. |
| `districts` | any | The district's building family: `highrise`, `midrise`, `lowrise`, `industrial`, `municipal`, `construction`, `neighborhood`, `farm`, `park`, `roundabout`, `nature`, `outland`, `astral`. |
| `styles` | any | World styles: `modern`, `apocalypse`, `classic`, `destroyed`, ... |
| `places` | any | Place names ([naming](CURSEFORGE-CONFIGURATION.md): a vanilla-land city's name, or a CityWorld district's; F3 shows it. `"District 12"` needs `naming.placeFormat` `"District {n}"`). |
| `environments` | any | `normal`, `nether`, `the_end`. |
| `land` | `any` | `cityworld` (CityWorld's own land) or `vanilla` (cities in a vanilla world). |
| `at` | `outdoors` | `outdoors`: on the top of the ground, a roof or a road. `indoors`: on a floor with anything over it within 16 blocks (rooms, basements, car park decks — and the ground under a tree). `anywhere`. Feet and head clear, a solid top underfoot, from 48 below the street to 160 above it (deep vault levels are out of reach). `driveway`: on a schematic's driveway (its `Driveway:` cells and the paving out to the road), facing the road — a car on the condo's forecourt. |
| `odds` | `1.0` | The chance per chunk that matches. |
| `count`, `count_max` | `1`, `0` | How many when it does: `count`, or `count` to `count_max`. |
| `persistent` | `true` | Entities never despawn (`PersistenceRequired`), unless `false` or their data says otherwise. |

- Things are written into the chunk as it generates, the way a saved chunk holds them: an entity is loaded from
  its data, not spawned, so a mob's random looks (a villager's biome, equipment) are only what its data says — give
  a guard its gear in the data (`HandItems`, `ArmorItems` on 1.20.1/1.21.1; `equipment` from 1.21.5) — and mods that
  act on a spawn (Minecraft Comes Alive converting villagers) do not see it.
- `odds` is per matching chunk: a district has a hundred, so `0.25` on `structure` lots is dozens. A block keeps the
  `facing` you write; it is not turned to a wall.
- A car outside a particular build: `{ "vehicle": "car", "schematics": ["Condo"], "at": "driveway" }` — one per
  condo, on its forecourt, nose to the road.
- Each file rolls on its own stream from the world seed, the chunk and the file's id: adding or removing a file
  never moves anything else, and the same seed puts the same things in the same places.
- The log names every file it read and every one it skipped and why (`CityWorld spawns:`).

## Data maps: swapping blocks, unlit lights, biome ground

Tags say *which* blocks belong to a pool. **Data maps** attach a value to a block or biome instead. Three
are for packs; the furniture one is described under [Furniture](#furniture).

> **On 1.20.1 only one pack's copy of each data-map file is read** — the one highest in the pack order —
> and `"replace"` and `remove` are ignored. A pack shipping `furniture.json`, `ground.json` or `unlit.json`
> there replaces CityWorld's whole map rather than adding to it. Keep all your swaps in one file. The
> NeoForge lines merge every pack's file as usual.

### Block substitutes

`data/cityworld/data_maps/block/substitute.json` (the same path on every version) swaps one block for
another **everywhere CityWorld places it** — its generated buildings, roads and decoration, and its
schematics. CityWorld ships it empty; the
[Neutral Palette](https://github.com/Sablednah/CityWorld-ReForged/releases/tag/neutral-palette-1.0.0)
add-on is a complete example.

```json
{
  "replace": false,
  "values": {
    "minecraft:iron_block": { "with": "minecraft:smooth_stone" },
    "minecraft:cut_copper_stairs": { "with": "minecraft:polished_granite_stairs" },
    "minecraft:netherrack": { "with": "minecraft:granite", "realms": ["overworld"] }
  }
}
```

- **`with`** — the block to place instead. Every state property the two blocks share is kept, so stairs
  swapped for stairs keep their facing, half and shape.
- **A mix** (from 6.14) — `with` may instead be a list of `{"block": …, "weight": …}`, and each block
  CityWorld places picks one of them, weighted. The pick is fixed by the block's position, so the same seed
  always builds the same mix. A weight defaults to 1; the list must not be empty. To keep some of the
  original, list the original block too:

  ```json
  "minecraft:yellow_concrete": { "with": [
    { "block": "minecraft:yellow_concrete", "weight": 50 },
    { "block": "chipped:smooth_yellow_concrete", "weight": 30 },
    { "block": "chipped:yellow_concrete_tiles", "weight": 20 }
  ] }
  ```

- **Shorthand** (from 6.14) — when an entry needs no `realms`, the value may be just the block id
  (`"minecraft:iron_block": "minecraft:smooth_stone"`) or just the weighted list. 6.13 and earlier accept
  only the `{ "with": … }` object; an entry they cannot read is skipped.
- **`realms`** (optional) — any of `overworld`, `nether`, `end`; absent means all three. The `cityworld:city`
  dimension counts as overworld. A misspelt realm silently matches nothing.
- Matching is by block, never by state, and it is one pass: A→B plus B→C does not turn A into C.
- It does **not** touch vanilla's own features and structures, or the overgrowth and lush-cave passes.
- It is read as each block is placed, so `/reload` reaches new chunks.
- A block from another mod should carry a condition, so the file still loads without it:
  `"examplemod:thing": { "neoforge:conditions": [{ "type": "neoforge:mod_loaded", "modid": "examplemod" }], "value": { "with": "examplemod:other" } }`.

### Unlit lights

`data/cityworld/data_maps/block/unlit.json` decides what a light becomes when the world's **Working lights**
setting is off (`features.includeWorkingLights: false` — the Apocalypse default). An entry may give `with`
(another block) and/or `properties` (`{"name": "value"}`); afterwards any `lit` property is set false.
CityWorld ships torches → redstone torches, lanterns → chains, glowstone, sea lanterns and froglights →
redstone lamps. The vault is exempt: it keeps its own power.

### Biome ground

CityWorld never runs biome surface rules. A biome's top block comes from the `cityworld:ground/<kind>` biome
tags (above), then from the data map `data/cityworld/data_maps/worldgen/biome/ground.json`:

```json
{ "values": { "biomesoplenty:volcanic_plains": { "surface": "biomesoplenty:black_sand", "subsurface": "biomesoplenty:black_sandstone" } } }
```

`subsurface` is optional. See [Biome Mods](Biome-Mods) for when you need it.

## Writing a compatibility pack

A pack is just tag files. To add a mod's wood to every wooden thing CityWorld builds, create:

`data/cityworld/tags/block/build/planks.json` (on 1.20.1: `tags/blocks/build/planks.json`)

```json
{
  "values": [
    { "id": "examplemod:ashwood_planks", "required": false },
    { "id": "examplemod:silverwood_planks", "required": false }
  ]
}
```

Three things to know:

- **Don't set `"replace": true`** unless you mean it. Leaving it out (the default) *adds* to the
  palette; setting it throws away everything CityWorld and vanilla put there.
- **Use `"required": false`.** If the player removes that mod, an entry marked required breaks the
  whole tag and the palette falls back to nothing. Marked optional, it's simply skipped. This is
  also what lets one pack work across several Minecraft versions.
- **You can reference whole tags**, not just blocks — `{ "id": "#examplemod:planks", "required":
  false }` picks up everything that mod tags, including blocks it adds later.

### Taking one block *out* of a palette

Don't like something CityWorld builds with? NeoForge tags support a `remove` list, so you can subtract
a single block without `"replace": true` and re-listing everything else. To keep the cinnabar but drop
the sulfur from the MODERN stone palette:

`data/cityworld/tags/block/build/modern_stones.json`

```json
{
  "values": [],
  "remove": [
    "minecraft:sulfur",
    "minecraft:sulfur_bricks",
    "minecraft:polished_sulfur",
    "minecraft:chiseled_sulfur"
  ]
}
```

`values` still has to be present, even when empty. `remove` works on every CityWorld line (NeoForge, and
Forge on 1.20.1); removing a block that isn't there is ignored.

Drop the pack in the world's `datapacks/` folder, or in `config/openloader/data/` if you use OpenLoader.
The building palettes are read when the world starts: restart the world (or the server) and **new**
chunks use them — `/reload` is not enough for these, though the fittings, furniture and decoration pools
and the data maps do follow it. Chunks already generated never change, so a fresh world gives the
cleanest look.

## How weighting works, and why your blocks might feel rare

A palette isn't a flat list. Each tag occupies a fixed number of **slots**, and that number doesn't
change when the tag grows.

Classic house walls, for example, give planks 6 slots out of 53. That was six slots when there were six
wood types in the game and it's still six now that there are twelve — so wooden houses are exactly as
common as they always were, and *which* wood gets used is what widened. In the Modern and Apocalypse
styles, plain stone is swapped for `cityworld:build/modern_stones`, which takes 24 slots (76 in all) and
already holds about 30 blocks; the CityWorld End blends in `build/end_stones` on top.

The practical consequence: adding ten modded wood types to `cityworld:build/planks` does **not** make
wooden buildings ten times more likely. It splits the existing wooden share across more woods. That's
deliberate — it stops a heavily modded world from turning every city into a timber yard — but it does
mean that in a pack with lots of wood, any one specific plank becomes uncommon.

**If you want a block to be genuinely prominent, don't add it to a palette — substitute it** for a
block CityWorld already uses a lot ([Block substitutes](#block-substitutes)). Or replace a small palette
outright (`"replace": true`) with just the blocks you want, accepting that you lose the rest.

## Determinism

Palette contents are sorted by block id before anything picks from them, so the same seed always
builds the same city. Two consequences worth knowing:

- Installing or removing a mod that contributes to these tags **will change what a given seed
  generates**. The terrain stays put; the materials shift.
- Already-generated chunks never change. Palette edits show up in new land only.
