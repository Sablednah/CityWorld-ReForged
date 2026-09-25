#!/usr/bin/env python3
"""Build a drop-in datapack jar that admits vanilla structure sets to #cityworld:allowed.

    scripts/gen_allow_structures_pack.py <forge|neoforge> <pack_format> <out.jar> [set ...]

With no sets named, every surface/underground vanilla set is admitted (villages, outposts, pyramids,
temples, huts, igloos, mansions, monuments, ruins, shipwrecks, ruined portals, mineshafts, buried
treasure, trail ruins, nether fossils). For playtesting how the forecast, reservation and pad handle
each of vanilla's own structures — not for shipping: CityWorld builds its own villages and mines.
pack_format: 15 for 1.20.1, 48 for 1.21.1, 61+ for 1.21.11 (any recent value loads with a warning).
"""
import json, sys, zipfile
loader, fmt, out = sys.argv[1], int(sys.argv[2]), sys.argv[3]
sets = sys.argv[4:] or ["villages", "pillager_outposts", "desert_pyramids", "jungle_temples", "swamp_huts",
    "igloos", "woodland_mansions", "ocean_monuments", "ocean_ruins", "shipwrecks", "ruined_portals",
    "mineshafts", "buried_treasures", "trail_ruins", "nether_fossils"]
tag = {"replace": False, "values": [{"id": "minecraft:" + s, "required": False} for s in sets]}
if loader == "forge":
    toml = '''modLoader="lowcodefml"
loaderVersion="[47,)"
license="GPL-3.0-only"
[[mods]]
modId="cityworld_allstructures"
version="1.0"
displayName="CityWorld: all vanilla structures (test)"
description="Admits vanilla structure sets to #cityworld:allowed. Playtest pack."
[[dependencies.cityworld_allstructures]]
    modId="cityworld"
    mandatory=false
    versionRange="[5.11,)"
    ordering="AFTER"
    side="BOTH"
'''
    tomlname = "META-INF/mods.toml"
else:
    toml = '''modLoader="lowcodefml"
loaderVersion="[1,)"
license="GPL-3.0-only"
[[mods]]
modId="cityworld_allstructures"
version="1.0"
displayName="CityWorld: all vanilla structures (test)"
description="Admits vanilla structure sets to #cityworld:allowed. Playtest pack."
[[dependencies.cityworld_allstructures]]
    modId="cityworld"
    type="optional"
    versionRange="[5.11,)"
    ordering="AFTER"
    side="BOTH"
'''
    tomlname = "META-INF/neoforge.mods.toml"
with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("pack.mcmeta", json.dumps({"pack": {"description": "CityWorld: all vanilla structures (test)", "pack_format": fmt}}, indent=2))
    z.writestr(tomlname, toml)
    z.writestr("data/cityworld/tags/worldgen/structure_set/allowed.json", json.dumps(tag, indent=2))
print("wrote", out, "with", len(sets), "sets")
