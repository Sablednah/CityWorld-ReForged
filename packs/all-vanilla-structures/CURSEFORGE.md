# CityWorld: All Vanilla Structures

**Every vanilla structure in your CityWorld worlds, switched on with one jar.**

[CityWorld](https://www.curseforge.com/minecraft/mc-mods/cityworld-reforged) keeps most of vanilla's structures out, so its cities own the land: by default it places
strongholds, trial chambers, ancient cities, nether fortresses, bastions and end cities. This add-on switches the
rest on: villages, desert and jungle temples, igloos, swamp huts, pillager outposts, woodland mansions, ocean
monuments, shipwrecks, ocean and trail ruins, ruined portals, buried treasure, mineshafts and nether fossils.

CityWorld blends in whatever it places: buildings keep off a structure's footprint and the ground is shaped to meet
it.

Made for servers and modpacks that want every structure on by default without anyone visiting the Customize screen.
In single player, CityWorld's **Customize → Structures** page already lets you tick any structure set, vanilla's or a
mod's, for one world. A world's own choice there wins over this add-on.

## Install

1. **CityWorld 5.17.0 or later.**
2. Drop the jar in `mods` beside it. It also works as a world datapack: the same jar is a valid datapack zip for a
   world's `datapacks` folder.

One jar works on every Minecraft version CityWorld ships for: 1.20.1 (Forge) and 1.21.1, 1.21.11, 26.1, 26.2, 26.3
(NeoForge); a structure a version doesn't have (trial chambers on 1.20.1) is simply skipped. Only **newly generated
chunks** get structures; explored land stays as it was.

## For modpack makers: an example

The whole add-on is one file, `data/cityworld/tags/worldgen/structure_set/allowed.json`, which adds to CityWorld's
`#cityworld:allowed` tag:

```json
{
  "replace": false,
  "values": [
    { "id": "minecraft:villages", "required": false },
    { "id": "minecraft:desert_pyramids", "required": false }
  ]
}
```

`"replace": false` adds to CityWorld's list, and `"required": false` lets one file load on every version. Add a
mod's structures the same way, with `{ "id": "modid:set_name", "required": false }`: a structure set's id is its file
name under `data/<mod>/worldgen/structure_set/` in the mod's jar. To allow fewer, delete lines.

## Licence

GPL-3.0-only, like CityWorld. Source: [GitHub](https://github.com/Sablednah/CityWorld-ReForged/tree/master/packs/all-vanilla-structures).
