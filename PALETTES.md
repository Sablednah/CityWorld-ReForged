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
| `cityworld:build/modern_stones` | the decorative stone palette used by the MODERN and APOCALYPSE styles | 32 curated blocks (8 of them Minecraft 26.2+) |

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
table, and the two big furniture mods disagree about it — one of them disagrees with *itself* between
its chairs and its sofas.

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

`facingOffset` is **how far clockwise to turn the look direction to get the value you want in
`facing`**, in degrees — one of `0`, `90`, `180`, `270`.

- `0` — your `facing` already *is* the direction the sitter looks.
- `180` — your `facing` points at the backrest (very common).
- `90` / `270` — your model was authored on a different axis to its blockstate.

To work it out without guessing: find the variant in your blockstate with **no `y` rotation**, look at
where the backrest sits in that model, and the occupant looks the opposite way. The offset is the turn
from that look direction to that variant's `facing` value.

Only seats need this. Tables, counters and cabinets either auto-connect or read the same from any
side, so leave them out.

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
