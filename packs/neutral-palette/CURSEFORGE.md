# CityWorld: Neutral Palette

**Plainer cities, and no free iron.**

[CityWorld](https://www.curseforge.com/minecraft/mc-mods/cityworld-reforged) builds its cities from the whole block list, which looks great and hands a survival player a
fortune: iron and gold blocks in the walls, copper on the roofs, diamond and emerald trim. Neutral Palette builds
with natural materials instead.

- **No free resources in buildings.** Iron, gold, diamond, emerald, lapis, coal, amethyst and copper blocks (every
  weathering stage, waxed or not, and their stairs and slabs) are built as stone, deepslate or terracotta.
- **A natural palette.** Coloured wool becomes planks, concrete and glazed terracotta become plain terracotta of the
  same colour, stained glass becomes clear glass, and the decorative stones narrow to andesite, diorite, granite,
  deepslate, mud brick, stone brick, calcite and tuff.
- **Odd blocks out of overworld walls.** Netherrack, soul sand, nether brick, prismarine, purpur and end stone are
  swapped out in the overworld; the ruined Nether and the End keep their own.

It works on every world style, Apocalypse included.

**Left alone on purpose:** redstone blocks (they power the subway's rails), ores, iron bars and doors, lanterns,
chests, furniture, and schematic buildings.

## Install

1. **CityWorld 5.19.0 or later.**
2. Drop the Neutral Palette jar in `mods` beside it.

One jar works on every Minecraft version CityWorld ships for: 1.20.1 (Forge) and 1.21.1, 1.21.11, 26.1, 26.2, 26.3
(NeoForge). It changes how buildings are made, so it shows in **newly generated chunks**: start a new world, or head
for land you haven't explored.

## Change it

It's a plain datapack inside, so a pack author can change any swap. The swaps are in
`data/cityworld/data_maps/block/substitute.json`: `"with"` is the block used instead, and `"realms"` limits an
entry to some of `overworld`, `nether` and `end`. The narrowed palettes are the tags under `data/cityworld/tags/`.

## Licence

GPL-3.0-only, like CityWorld. Source: [GitHub](https://github.com/Sablednah/CityWorld-ReForged/tree/master/packs/neutral-palette).
