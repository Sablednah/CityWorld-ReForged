# Biome mods and biome datapacks with CityWorld

CityWorld replaces the overworld's biome source with its own. That is what lets it put a city's biome where the city
is, a river's where the river runs and a cave biome underground — but it also means a biome mod's usual route into the
world does not exist here, and every route has to be bridged. Since 6.11.0 all three routes are, and this page says
what works where, what to expect, and how to steer it from a datapack.

Everything here only affects **newly generated chunks**. Settings live in the world's `cityworld:world_settings` entry
— see [Configuring CityWorld](Configuring-CityWorld) for the three ways to set them.

## Which land are you on?

CityWorld makes two kinds of overworld, chosen on the Customize screen's **Land** row (`cities.vanillaTerrain`):

- **CityWorld's own land** (the default): CityWorld's terrain and its own climate-driven biome palette, with cities
  everywhere. Mod biomes are *added* to that palette through the bridges below.
- **Vanilla land** ("Vanilla, cities apart"): vanilla's terrain and vanilla's biome source, with cities dropped in
  far apart. Here CityWorld runs the biome mods' own set-up for you, so anything that works in a vanilla world should
  work, and the biome mod decides the layout.

## The three roads a biome mod takes — and how far each one reaches

| How the mod adds its biomes | Examples | CityWorld's land | Vanilla land |
|---|---|---|---|
| **TerraBlender regions** (the mod registers climate points through TerraBlender) | Biomes O' Plenty, Oh The Biomes We've Gone, Nyctophobia, VanillaBackport's pale garden, Terralith's companion mods | ✅ harvested into CityWorld's climate lookup (`TerraBlenderBridge`) | ✅ TerraBlender is initialised for the vanilla generator |
| **A datapack world preset** (the mod rewrites `minecraft:normal` with its own biome list and noise) | Terralith, Dreamwoods + Grand Terrain, most pure-datapack biome packs | ✅ the preset's climate points join the same lookup (`world.packPreset`) | ✅ the preset's terrain and biome source are used outright |
| **Lithostitched injectors** (density-function layouts over vanilla's biomes) | Regions Unexplored | ❌ nothing to harvest — the layout is not climate points | ✅ Lithostitched's passes run on the vanilla generator |
| **A mixin on vanilla's `MultiNoiseBiomeSource`** | Alex's Caves (cave biomes) | ✅ via `#cityworld:cave_pool` | ✅ |

Which road a mod uses is in its jar: `terrablender` in its dependencies, `data/minecraft/worldgen/world_preset/normal.json`
in its data, or `lithostitched` in its dependencies.

### What works well

- **TerraBlender mods** and **datapack-preset mods**, on either land. Their biomes appear with their own trees,
  plants and ground blocks; CityWorld's cities sit in them like in any other biome; rivers through a snowy mod biome
  freeze. A mod that declares its biomes' climate well (distinct temperature/humidity ranges) gets the most natural
  placement.
- **Mods that only add structures, features or blocks** to existing biomes — nothing to do; they decorate wild
  chunks the way they would anywhere (MODERN and APOCALYPSE worlds; see `world.wildDecoration`).

### What struggles, and why

- **Lithostitched layouts on CityWorld's land** (Regions Unexplored). CityWorld's land is drawn from a climate
  lookup; a density-function layout has no climate points to feed it. Play these on **vanilla land**, where they work
  in full.
- **Biomes whose look is their ground.** CityWorld never runs a biome's surface rules — its ground comes from block
  tags and the `cityworld:ground` data map. A gravel beach, volcanic plain or salt flat whose character is its top
  block reads as grass until it is given one; the self-test reports these (`biome.groundDefault`) and the shipped
  data map covers Biomes O' Plenty's. See "Give a biome its ground" below.
- **Biomes with a climate box CityWorld never produces** (a bog that wants humidity 0.95 on land CityWorld keeps at
  0.6). The regional share (`moddedBiomeShare`) hands such biomes whole regions anyway, and a datapack can force a
  patch with `#cityworld:surface_pool` (see below).
- **Mods that mix into vanilla's chunk generator rather than its biome source** (terrain shapers, cave carvers,
  surface-rule injectors). They do nothing on CityWorld's land, which has its own terrain; on vanilla land they
  usually work because CityWorld builds a real vanilla generator there.
- **Structures a biome mod places in its biomes.** They are placed by vanilla's structure system and reserved for
  by CityWorld, but a pond or ruin structure landing in a city still cuts it in two unless it is kept to the wild —
  see "Structures only in the wild".

## The settings that steer it

All in the `world` group unless noted ([full reference](Configuring-CityWorld#full-settings-reference)):

| Setting | Default | Role |
|---|---|---|
| `useModdedBiomes` | `true` | The master switch for mod biomes on CityWorld's land. |
| `moddedBiomeShare` | `0.35` | How much contested ground a mod may own outright — the dial for "more of my mod's biomes". |
| `moddedBiomeGrain` | `64` | The cell, in blocks, a mod's biome is decided for. Bigger = larger, bolder patches; `1` = the old per-column look (slivers). |
| `packPreset` | `"minecraft:normal"` | The world preset CityWorld reads the pack's overworld from. Vanilla land takes its terrain and biomes from it; CityWorld's land offers its foreign biomes. Name another preset if your pack's is not `minecraft:normal`; blank reads none. |
| `cities.vanillaTerrain` | `false` | Vanilla land. |
| `structures.wildernessOnly` | `false` | Keep every placed structure set out of the built platmaps (`structures` group). |

## Datapack recipes

A datapack goes in the world's `datapacks/` folder (or a mod's `data/`). Paths below are inside `data/cityworld/`.

### Keep a mod's structures out of the city (set by set)

`tags/worldgen/structure_set/wilderness_only.json`:

```json
{ "replace": false, "values": [ { "id": "arboria:lake_common", "required": false } ] }
```

A set listed here never starts in a platmap the city plans to build, in either land. Arboria's five lake sets ship in
this tag already. (`structures.wildernessOnly: true` does it for every set at once; strongholds are never touched,
because the eyes of ender point at their ring whether or not one stands.)

### Force a biome CityWorld's climate never picks

`tags/worldgen/biome/surface_pool.json` (land), `shore_pool.json` (the beach course) or `ocean_pool.json`:

```json
{ "replace": false, "values": [ "biomesoplenty:bog" ] }
```

A pooled biome gets patches whether or not the climate lookup would choose it, kept inside the climate its own author
declared when CityWorld knows that (a TerraBlender or preset biome). Underground biomes go in `cave_pool.json`, with
their depth band in the `caves` settings group — see [Configuring CityWorld](Configuring-CityWorld#caves--the-underground-cave-biomes-and-structure-caverns).

### Give a biome its ground

CityWorld never runs surface rules. The data map `data_maps/worldgen/biome/ground.json` names a biome's top block:

```json
{ "values": { "biomesoplenty:volcanic_plains": { "surface": "biomesoplenty:black_sand", "subsurface": "biomesoplenty:black_sandstone" } } }
```

Details and the shipped entries are in [Building Palettes](Building-Palettes).

### Point CityWorld at a different world preset

If your pack's overworld is a preset other than `minecraft:normal` (a modpack's own `mypack:overworld`), set
`world.packPreset: "mypack:overworld"` in the world settings. Vanilla land then uses its terrain and biomes; CityWorld's
land offers its foreign biomes.

### Turn a mod's biomes off

`world.useModdedBiomes: false` — CityWorld's own palette only, on its own land. On vanilla land the biome mod owns the
map; remove the mod or its datapack instead.

## Checking it worked

- `/locate biome <id>` from spawn. A biome that cannot be found within a few thousand blocks is not in the world's
  pool; one that is found but tiny is a patch-size question (`moddedBiomeGrain`).
- F3 shows the biome under you and, on the right, the `[CityWorld]` lines: lot, context and style.
- The log at world load names what was bridged:
  `CityWorld: vanilla land follows world preset minecraft:normal: ... with N biomes`,
  `CityWorld: Lithostitched applied to vanilla land ...`,
  `CityWorld: N structure set(s) keep to the wild`.
- `-Dcityworld.diagnostics=true` on the JVM arguments sweeps every tag pool at startup and names the empty ones.

## Known limits (6.11.0)

- Lithostitched layouts: vanilla land only.
- Mod biomes never replace CityWorld's choices for oceans, shores and peaks — those are decided from the terrain.
- A biome's own surface rules never run; see "Give a biome its ground".
- Rivers on CityWorld's land are CityWorld's own; a mod's river biomes are not used for them.
