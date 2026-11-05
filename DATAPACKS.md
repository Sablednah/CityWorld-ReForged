# A beginner's guide to datapacks with CityWorld

Almost everything CityWorld builds with — the blocks in its buildings, what its chests hold, which
structures and biomes it uses, its settings — comes from data files you can change **without touching the
mod**. You change them with a **datapack**. This page is the shortest path from "I've never made one" to
"my city is built from the blocks I chose".

## What a datapack is

A datapack is a folder (or a `.zip` of that folder) with two things in it:

```
my-city-pack/
├── pack.mcmeta          ← says "this is a datapack"
└── data/                ← the files, in exact folders
    └── cityworld/
        └── tags/
            └── block/
                └── build/
                    └── planks.json
```

Minecraft reads the files by **path**: the folders spell out what each file is for. A file in the wrong
folder — one wrong letter — is silently ignored. There is no error message. That is the cause of most
"nothing happened".

### `pack.mcmeta`

Copy this exactly. It works on every Minecraft version CityWorld supports (1.20.1 to 26.x):

```json
{
  "pack": {
    "description": "My CityWorld changes",
    "pack_format": 15,
    "supported_formats": [15, 999],
    "min_format": 15,
    "max_format": 999
  }
}
```

## Where it goes

A datapack belongs to **one world**, not to the game.

| You play… | Put the pack in… |
|---|---|
| **Single player, new world** | On **Create New World**, open the **More** tab → **Data Packs** → drag your folder or zip into the window, and move it to the *Selected* side. It is in place before the first chunk is made — the best way. |
| **Single player, existing world** | `.minecraft/saves/<world name>/datapacks/` (in the CurseForge app: right-click the instance → *Open Folder* → `saves`). Then quit to the title screen and reopen the world. |
| **A server** | `world/datapacks/` — the folder named by `level-name` in `server.properties`, next to `level.dat`. Then restart the server. |
| **Every new world, automatically** | A global-datapack mod such as **OpenLoader** (`config/openloader/data/`), or package the pack as a mod jar the way the [Neutral Palette](https://github.com/Sablednah/CityWorld-ReForged/releases/tag/neutral-palette-1.0.0) add-on does. |

**Check it loaded:** run `/datapack list`. Your pack should be listed as enabled (shown as
`[file/my-city-pack]`). If it isn't there, Minecraft hasn't read it, and nothing in it will do anything.

## The three rules that catch everyone

**1. Only new chunks change — and only after the world restarts.** CityWorld reads its palettes,
substitutes and settings when the world starts. After adding or editing a pack, restart the world
(quit to title and reopen, or restart the server); `/reload` is not enough. Chunks that already exist
**never regenerate**, so changes appear only in land nobody has visited yet. The surest test is a fresh
world with the pack already in it.

**2. The folder names differ by Minecraft version.**

| | 1.21 and newer | 1.20.1 |
|---|---|---|
| Block tags | `data/cityworld/tags/block/…` | `data/cityworld/tags/blocks/…` |
| Item tags | `data/cityworld/tags/item/…` | `data/cityworld/tags/items/…` |
| Loot tables | `data/cityworld/loot_table/…` | `data/cityworld/loot_tables/…` |

A pack meant for both can carry both folders — the Neutral Palette does.

**3. Add, don't replace — and make other mods' blocks optional.** In a tag file,
`"replace": false` adds your blocks to what is there; `true` throws away everything CityWorld and vanilla
put in it. And if one entry names a block that doesn't exist (a typo, or a mod that isn't installed), the
**whole tag** is discarded. Mark entries from other mods `"required": false` and a missing one is simply
skipped.

To find a block's exact id, look at it with **F3** open — the id is under *Targeted Block* on the right,
e.g. `chipped:boxed_oak_planks`.

## Which file does what

There are two ways to change what the cities are built from, and they do different things.

### Palettes: "also build with these blocks"

CityWorld's own buildings pick their walls, floors and roofs from **palettes** — block tags. Adding a block
to a palette makes it one more choice:

`data/cityworld/tags/block/build/planks.json` (1.20.1: `tags/blocks/`)

```json
{
  "replace": false,
  "values": [
    { "id": "chipped:boxed_oak_planks", "required": false },
    { "id": "chipped:basket_woven_oak_planks", "required": false }
  ]
}
```

Good to know:
- A palette has a fixed share of the building, so the blocks you add are spread across that share: add
  ten planks and each becomes **rare**. To make a block common, use a substitute (below) instead.
- Palettes change CityWorld's **generated** buildings, not the pasted schematic buildings.
- Every palette, and the other tags (furniture, fittings, farms, biomes), is on
  [Building Palettes](Building-Palettes).

### Substitutes: "use block X wherever CityWorld would put block Y"

The **substitute** data map swaps one block for another everywhere CityWorld places it — its own
buildings *and* schematics — keeping the block's shape and facing where the new block has them:

`data/cityworld/data_maps/block/substitute.json` (the same path on every version)

```json
{
  "replace": false,
  "values": {
    "minecraft:stone_bricks": { "with": "chipped:etched_stone_bricks" },
    "minecraft:gold_block": { "with": "minecraft:yellow_terracotta" }
  }
}
```

This is usually what people mean by "make the city use my blocks". **Each value is an object with a
`with` key** — on 6.13 and earlier a bare id or a list is not understood and that entry does nothing.
From 6.14, `with` can also be a weighted mix, so a wall comes out speckled rather than all one block
(list the original block too, if you want to keep some of it):

```json
"minecraft:yellow_concrete": { "with": [
  { "block": "minecraft:yellow_concrete", "weight": 50 },
  { "block": "chipped:smooth_yellow_concrete", "weight": 30 },
  { "block": "chipped:yellow_concrete_tiles", "weight": 20 }
] }
```

From 6.14 you may also write just `"minecraft:stone_bricks": "chipped:etched_stone_bricks"`. An entry can be
limited to some realms with `"realms": ["overworld"]` (or `"nether"`, `"end"`). On **1.20.1** only one pack's
`substitute.json` is read, so keep all your swaps in one file. Details on
[Building Palettes](Building-Palettes#block-substitutes).

### Per-part palettes: "office walls from these blocks, house roofs from those"

From 6.14, each part of a building or street has a tag of its own, shipped empty: fill
`data/cityworld/tags/block/palette/building/wall.json` and office blocks' walls come only from your blocks, while
everything else about them stays as it was. Put the path under a style's name
(`palette/apocalypse/building/wall.json`) to change one style only. The full list of parts is on
[Building Palettes](Building-Palettes#per-part-palettes-from-614).

### Everything else

| To change… | Read |
|---|---|
| World settings (city size, decay, spawns, roads …) | [Configuring CityWorld](Configuring-CityWorld) — note the file must be named for your world type |
| Which vanilla and mod structures generate (villages!) | [Configuring CityWorld → Villages](Configuring-CityWorld#recipes) |
| What the chests hold | [Loot Tables](Loot-Tables) |
| Biome mods and biome datapacks | [Biome Mods](Biome-Mods) |

## Your first pack: prove it works

Before changing anything subtle, make a pack that does something **impossible to miss**, so you know the
pack is being read at all. Every street lamp CityWorld builds stands on a smooth stone block; turn those
gold:

```
first-test/
├── pack.mcmeta                                   ← the one above
└── data/cityworld/data_maps/block/substitute.json
```

```json
{
  "replace": false,
  "values": {
    "minecraft:smooth_stone": { "with": "minecraft:gold_block" }
  }
}
```

Create a new CityWorld world with it selected and walk down a street. Gold under the lamps: your pack
works, and you can swap in the change you really wanted. No gold: run `/datapack list`, then check the
folder names letter by letter against this page.

## Easiest of all: copy a pack that works

The [Neutral Palette](https://github.com/Sablednah/CityWorld-ReForged/releases/tag/neutral-palette-1.0.0)
add-on is a complete, working CityWorld pack: palette tags for both 1.20.1 and 1.21+, a substitute map, and
a `pack.mcmeta` that loads everywhere. It is a `.jar`, but a jar is a zip — rename it to `.zip`, open it,
copy its `data/` folder and `pack.mcmeta` into a folder of your own, and change the contents.

## Still stuck?

Open an [issue](https://github.com/Sablednah/CityWorld-ReForged/issues) with:
- your Minecraft version and mod loader (NeoForge or Forge)
- single player or a server
- a screenshot of your pack's folder tree, opened all the way down to the `.json` files
- what `/datapack list` shows
