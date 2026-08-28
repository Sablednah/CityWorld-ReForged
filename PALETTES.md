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
