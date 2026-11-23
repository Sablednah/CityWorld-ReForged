# CityWorld: Biome Palettes

A data-only add-on for CityWorld (no code): **cities built to suit the biome they stand in.**

| Theme | Where | Built of |
|---|---|---|
| `desert` | deserts, dunes, oases, arid and dry land | sandstone, cut and smooth sandstone, pale terracotta |
| `badlands` | badlands, mesas, canyons, outback | red sandstone and banded terracotta |
| `savanna` | savannas, shrubland, scrub, steppe | acacia, mud brick, packed mud, orange terracotta |
| `tropical` | jungles, rainforest, tropics, bamboo | mossy stone brick, jungle wood and bamboo |
| `swamp` | swamps, bayous, marshes, bogs, mangroves | mangrove and dark oak, mud brick, mossy cobble |
| `boreal` | taiga, pine, spruce, fir and redwood forest, snowy woods | spruce, stone brick, cobble, deepslate |
| `frozen` | snowy plains, tundra, ice, glaciers | spruce, calcite, diorite, white and pale blue, packed ice |
| `coastal` | beaches, shores, coves | whitewash, birch, sandstone, prismarine |
| `mountain` | peaks, cliffs, highlands, windswept hills, volcanoes | stone brick, andesite, deepslate, spruce |
| `blossom` | cherry and sakura groves, wisteria, jacaranda | cherry wood, pink and white terracotta, calcite |

Plains, forests, birch woods and meadows keep CityWorld's own look.

Each theme fills the walls, roofs, columns, foundations and floors of office blocks, shops, civic buildings and
houses, the factory's office block, and the woods of shacks and sheds. Windows, streets, interiors and schematic
buildings are left as the world has them. A building is always one theme: where a biome ends mid-city, the
buildings on each side differ, never one building.

**Biome mods are covered** by word and by tag, not by a list that goes stale: any biome with `desert` or `dune`
in its name is a desert here, whatever mod it comes from, and so on for each theme (checked against Biomes O'
Plenty, Terralith, Regions Unexplored and Dreamwoods). On vanilla land (CityWorld's cities in a vanilla world) a
city takes the theme of the biome at its centre.

Install: drop the jar in `mods` beside **CityWorld 7.3.0 or later**. One jar for every Minecraft version
CityWorld ships for. It shows in newly generated chunks.

## Changing it

It is a plain datapack inside.

- **Which biomes a theme covers:** `data/cityworld_biome_palettes/cityworld/palette_theme/<theme>.json`. To add a
  biome from your own pack without replacing the file, add a theme file of another name with
  `"theme": "desert"` in it.
- **What a theme builds with:** the block tags under `data/cityworld/tags/block/palette/biome/<theme>/`
  (`tags/blocks/` on 1.20.1), one per building part.
- **A theme of your own** is the same two things under a new name.

The format is in CityWorld's wiki, [Palettes by biome](https://github.com/Sablednah/CityWorld-ReForged/wiki/Building-Palettes#palettes-by-biome-from-73).
To see the theme each installed biome gets, start the game with `-Dcityworld.diagnostics=true` and read the
`CityWorld palette themes:` lines in the log; F3 shows the theme where you stand.

For the pack's maintainer: the files are written by `gen.py` from the tables at its top (`python3 gen.py`), and
`python3 gen.py --check biomes.json` prints the theme each biome in a harvest gets. `./build.sh` makes the jar.

GPL-3.0-only, like CityWorld.
