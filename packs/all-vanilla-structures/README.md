# CityWorld: All Vanilla Structures

A sample datapack, packaged as a jar, that lets **every vanilla structure** generate in CityWorld worlds:
villages, desert and jungle temples, igloos, swamp huts, pillager outposts, woodland mansions, ocean monuments,
shipwrecks, ocean and trail ruins, ruined portals, buried treasure, mineshafts, ancient cities, trial chambers,
strongholds, nether fortresses and bastions, nether fossils and end cities.

It is also meant as an **example for modpack makers**: the whole pack is one file.

## Install

- **As a mod:** drop `cityworld-all-vanilla-structures-1.0.0.jar` into `mods/` beside CityWorld (5.17 or later).
  One jar works on every CityWorld line: Minecraft 1.20.1 (Forge), 1.21.1, 1.21.11, 26.1, 26.2, 26.3 (NeoForge).
- **As a datapack:** the same jar is a valid datapack zip. Put it in a world's `datapacks/` folder, or in
  your pack's global datapacks.

Only newly generated chunks get structures; explored land stays as it was.

## How it works

CityWorld places only the structure sets in the structure-set tag `#cityworld:allowed`. CityWorld ships
strongholds, trial chambers, ancient cities, nether fortresses/bastions and end cities, plus a few mods' sets.
This pack adds to that tag. It is one file:

    data/cityworld/tags/worldgen/structure_set/allowed.json

```json
{
  "replace": false,
  "values": [
    { "id": "minecraft:villages", "required": false },
    { "id": "minecraft:desert_pyramids", "required": false },
    ...
  ]
}
```

- `"replace": false` adds to CityWorld's own list rather than replacing it.
- `"required": false` lets the same file load on every version: a set a version doesn't have (trial chambers
  on 1.20.1) is skipped instead of failing the whole tag.
- Modded structure sets work the same way: add `{ "id": "modid:set_name", "required": false }`. A structure
  set's id is the file name under `data/<mod>/worldgen/structure_set/` in that mod's jar.
- To allow fewer structures, delete lines. To remove one CityWorld allows by default, use `"replace": true` and
  list everything you want.

CityWorld blends what it places: buildings keep off a structure's footprint and the ground is shaped to meet it.

Per world, the Customize screen's **Structures** page can still switch any set on or off; that choice is
stored with the world and wins over this tag.
