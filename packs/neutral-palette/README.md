# CityWorld: Neutral Palette

A data-only add-on for CityWorld (no code) for players who want plainer cities, or a survival pack
that should not hand out valuable blocks.

- **No free resources in buildings:** iron, gold, diamond, emerald, lapis, coal, amethyst and copper
  blocks (every weathering stage, waxed or not, stairs and slabs too) are built as stone, deepslate or
  terracotta instead.
- **A natural palette:** coloured wool becomes planks, concrete and glazed terracotta become plain
  terracotta of the same colour, stained glass becomes clear glass, and the decorative "modern" stones
  are narrowed to andesite, diorite, granite, deepslate, mud brick, stone brick, calcite and tuff.
- **Overworld only for the odd pieces:** netherrack, soul sand, nether brick, prismarine, purpur and end
  stone are swapped out of overworld walls; the ruined Nether and the End keep their own.

Left alone on purpose: redstone blocks (they power the subway's rails), ores, iron bars and doors,
lanterns and chests, furniture, and schematics.

Install: drop the jar in `mods` beside CityWorld. The block swaps need **CityWorld 5.19.0 or later**;
on 5.18 only the narrowed palettes apply. Change any of it by editing
`data/cityworld/data_maps/block/substitute.json` (`"with"` is the block used instead; `"realms"`
limits an entry to some of `overworld`, `nether`, `end`) and the tags under `data/cityworld/tags/`.

GPL-3.0-only, like CityWorld.
