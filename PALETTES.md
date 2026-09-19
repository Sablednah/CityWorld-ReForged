# Building palettes

CityWorld decides what a wall, floor or roof is made of by drawing from a **palette** — a weighted
list of blocks. Most of those palettes are now defined by **block tags**, which means you can change
what your cities are built from with a datapack, without touching the mod.

This is also how a *"CityWorld × some other mod"* compatibility pack works.

## The short version

Most of the time you don't need to do anything.

CityWorld's palettes are built on top of the ordinary vanilla and NeoForge common tags — so a mod
that tags its blocks the way mods are supposed to (its planks in `#minecraft:planks`, its stone in
`#c:stones`) **already shows up in CityWorld cities the moment you install it.** No pack required.

A compatibility pack is for the other cases:

- the mod doesn't tag its blocks, so nothing knows they're planks
- the mod tags them correctly but you only want *some* of them in your cities
- you want a mod's blocks in a palette they don't naturally belong to — a castle-brick highrise,
  say — which no amount of correct tagging will do for you

## The palettes

Each of these is a block tag CityWorld resolves when a world is created. Add to one and the blocks
you add start appearing in that palette.

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

### Biome pools

Three biome tags decide which biomes CityWorld places by a route other than climate. All ship with
Biomes O' Plenty ids marked optional, so they cost nothing without it — and any biome mod can be added
the same way.

| Tag | What it does |
|---|---|
| `cityworld:cave_pool` | Biomes placed **underground**, as patches, with their own decoration. |
| `cityworld:surface_pool` | Biomes given a share of surface cells **outright**, for biomes the climate lookup would never choose. |
| `cityworld:shore_pool` | Beach variants that may **stand in for CityWorld's own beach** where the terrain says shore. |

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
furniture up the moment both mods are installed. Neither file does anything without CityWorld, so
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
| `cityworld:fittings/site_fence` | the tall fence round a construction site or a factory yard — one per platmap | iron bars + Macaw's wired (barbed-wire) fences and its industrial metal fences (mesh, panelled, bastion…) |
| `cityworld:fittings/roof` | the pitched roofs of MODERN houses | **empty** — Macaw's `*_roof` blocks (generated by `scripts/gen_furniture_tags.py`); the roof material's vanilla stairs otherwise |
| `cityworld:fittings/gate` | the gate in a paddock or barn pen | the vanilla fence gates by id + Macaw's single-block highley and pyramid gates |
| `cityworld:fittings/garage_door` | the street bay of half the factories and warehouses (three wide, three to seven high) | **empty** — Macaw's garage doors when installed; the door and its frame otherwise |
| `cityworld:fittings/stairs` | the staircase of a MODERN house, and every building's stairwell | **empty** — Macaw's compact and terrace stairs when installed (their matching railing, platform and balcony are found by name); the vanilla stair run otherwise |
| `cityworld:light/street_lamp` | the lamp posts along every street | **empty** — Macaw's street lamps when installed; the fence-and-glowstone post otherwise |
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

**Two-block plants work.** A crop or flower that places as a `half=lower`/`half=upper` pair — BoP's
barley, or any modded tall crop — is detected and both halves are placed. You do not need to declare
anything for that.

**A plant that cannot live there is skipped**, and the vanilla one grows instead. CityWorld asks the
block itself (`canSurvive`) before planting, which is why an End or Nether bloom inherited from
`#minecraft:small_flowers` does not leave a bald patch.

Palettes that are a **deliberate look** rather than "all of a family" are not tags and are not meant
to be extended: the muted greyscale of unfinished buildings, the pale civic palette of government
offices, and the ordered road and maze lists.

## Writing a compatibility pack

A pack is just tag files. To add a mod's wood to every wooden thing CityWorld builds, create:

`data/cityworld/tags/block/build/planks.json`

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

`values` still has to be present, even when empty. This is a NeoForge extension — it does nothing on
other loaders, where the only options are add or wholesale replace.

Drop the pack in the world's `datapacks/` folder, or in `config/openloader/data/` if you use
OpenLoader, and **create a new world** — palettes are resolved once, when a world is first
generated.

## How weighting works, and why your blocks might feel rare

A palette isn't a flat list. Each tag occupies a fixed number of **slots**, and that number doesn't
change when the tag grows.

House walls, for example, give planks 6 slots out of 53. That was six slots when there were six wood
types in the game and it's still six now that there are twelve — so wooden houses are exactly as
common as they always were, and *which* wood gets used is what widened.

The practical consequence: adding ten modded wood types to `cityworld:build/planks` does **not** make
wooden buildings ten times more likely. It splits the existing wooden share across more woods. That's
deliberate — it stops a heavily modded world from turning every city into a timber yard — but it does
mean that in a pack with lots of wood, any one specific plank becomes uncommon.

If you want a mod's blocks to be genuinely prominent, add them to a palette with fewer competitors
(`cityworld:build/modern_stones` has 32) rather than to the biggest one.

## Determinism

Palette contents are sorted by block id before anything picks from them, so the same seed always
builds the same city. Two consequences worth knowing:

- Installing or removing a mod that contributes to these tags **will change what a given seed
  generates**. The terrain stays put; the materials shift.
- Already-generated chunks never change. Palette edits show up in new land only.
