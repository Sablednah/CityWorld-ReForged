# Your own buildings: custom schematics

CityWorld drops ready-made buildings (**schematics**) into its cities between the ones it generates: the Statue
of Liberty, the Winchester Tavern, gas stations, a pagoda and about thirty more ship with the mod. You can add
your own. Build it in game, save it, drop the file in a folder named after the kind of district it belongs in, and
new cities start using it. You don't need a datapack, a mod or a restart of the world, just a restart of the game
or server.

A building can hold blocks from any mod you have installed. A Cobblemon Pokémon Center with a healing machine and
a PC, a Create workshop or a Farmer's Delight kitchen all work, because a schematic stores each block by its id.

## The short version

1. Build it. Save it with a **structure block** (`.nbt`), **WorldEdit** (`.schem`) or **Litematica** (`.litematic`).
2. Put the file in `config/cityworld/schematics/<Family>/`, where `<Family>` is one of the folders below.
3. Optionally, put a sidecar file beside it (`pokecenter.nbt.yml`) to set how often it appears.
4. Restart the game or server. Check that it loaded: `/cityschem list` names it, and `/cityschem pokecenter` pastes
   it at your feet.
5. Explore **new** land in a world with **Schematics** on. Chunks already generated never change.

## Where the folder is

| You play | The folder |
|---|---|
| Single player | `<your instance>/config/cityworld/schematics/` (CurseForge: right-click the profile → Open Folder) |
| A server | `config/cityworld/schematics/` **on the server**. Players don't need the files. |
| A modpack | ship `config/cityworld/schematics/` in the pack's overrides |

CityWorld creates the folder, with a `README.txt` in it, the first time it runs. The path is the same on every
Minecraft version and both loaders.

## The family folders: which district a building appears in

The folder name decides where the building can land. Capitals don't matter (`Lowrise`, `lowrise`). A building
may go in more than one folder: put a copy in each (the bundled gas station is in `Lowrise`, `Municipal` and
`Neighborhood`).

| Folder | Where it appears | Biggest footprint |
|---|---|---|
| `Highrise` | downtown: the skyscraper core | 4 × 4 chunks (64 × 64 blocks) |
| `Midrise` | the ring of mid-height blocks around downtown (and the Maze style) | 4 × 4 chunks |
| `Lowrise` | high streets: shops, diners, banks, small offices | 4 × 4 chunks |
| `Municipal` | civic districts: city halls, hospitals, churches, landmarks | 9 × 9 chunks |
| `Industrial` | factories, warehouses and yards | 6 × 6 chunks |
| `Construction` | building sites | 4 × 4 chunks |
| `Neighborhood` | the suburbs: houses and gardens | 4 × 4 chunks |
| `Park` | city parks | 4 × 4 chunks |
| `Roundabout` | the centre of a roundabout: a statue or fountain | **1 chunk** (16 × 16) |
| `Farm` | farmland outside the cities | 4 × 4 chunks |
| `Outland` | the rough edge where the wild begins: works, yards, camps | 4 × 4 chunks |
| `Nature` | the deep wild, and the open sea for `Ocean: true` builds (at most two per map square) | 9 × 9 chunks |

A file in any other folder name is ignored. So is a building wider than its family's limit. A footprint is
counted in whole chunks, so a 17-block-wide building takes two. Modern and Apocalypse worlds have every kind of
district. The other world styles have some of them.

**How a building is picked.** CityWorld plans its world in squares of 10 × 10 chunks (160 blocks), and each square
is one kind of district. In each square, every building in that district's folder rolls its own
`OddsOfAppearance`. A building that wins is placed on a clear, flat spot if one exists, and CityWorld generates
the rest of the square around it. The roll is seeded from the world seed, so a seed always builds the same city.
Many buildings with high odds in one folder can leave little room for CityWorld's own buildings. Keep the odds
modest.

## The sidecar: odds, depth and a title

The sidecar is a plain text file with the building's own name plus `.yml`: `pokecenter.nbt.yml`, or just
`pokecenter.yml`. Every key is optional, and a building without a sidecar takes the defaults.

| Key | Default | What it does |
|---|---|---|
| `OddsOfAppearance` | `0.1` | The chance (0 to 1) it appears in each square of its district. `0.3` is common, `0.05` rare, `0.01` a landmark. |
| `GroundLevelY` | `0` | How many of the building's bottom layers are **below** ground: a basement or a buried foundation. `0` puts the bottom layer on the pavement. |
| `Title` | the file name | The name `/cityfind` and the landmark announcements use, and the [CityWorld Compass](https://www.curseforge.com/minecraft/mc-mods/cityworld-urban-compass) lists. |
| `FlipableX`, `FlipableZ` | `false` | Allow a mirrored copy across X or Z. Leave them off for anything with writing or a deliberate front. |
| `Decayable` | `true` | Whether Apocalypse and Destroyed worlds may ruin it. `false` keeps it whole. |
| `PristineChance` | world default | This building's own chance of being spared that ruin, 0 to 1. |
| `BroadcastLocation` | `false` | Announce it in chat when it generates (the world's `broadcastSpecialPlaces` setting must be on too). |
| `KeepAir` | `false` | Place the schematic's air too, so a cellar or hollow stays open instead of filling with ground. Trim dead air off the build first. |
| `Ocean` | `false` | An offshore build (a rig, a ship): placed only in deep open sea, floating at the waterline. Put it in `Nature`. `GroundLevelY` is then how far the hull or legs go under water. |
| `Anchor` | `false` | With `Ocean`: run each leg down to the sea floor. |
| `Loot` | — | A loot table for every container in it, e.g. `Loot: mypack:chests/pokecenter`. Without it, see [Loot](#loot-and-what-is-inside). |

```yaml
# pokecenter.nbt.yml
Title: "Pokémon Center"
OddsOfAppearance: 0.3
GroundLevelY: 0
Decayable: false
```

Lines starting with `#` are comments. Quotes around a value are optional.

## A worked example: a Pokémon Center and a gym

You want a Pokémon Center to turn up in most towns, and a gym now and then.

1. **Build the Center** with its healing machine and PC, on a floor one block thick. Make it no more than 48
   blocks each way, the most one structure block saves.
2. **Save it.** `/give @s minecraft:structure_block`, place it at one corner, set **Save** mode, name it
   `pokecenter`, size the box to the building (switch **Show bounding box** on), and press **Save**. The file is
   written to `<world>/generated/minecraft/structures/pokecenter.nbt`.
3. **Drop it in.** Copy it to `config/cityworld/schematics/Lowrise/pokecenter.nbt`, and the same file to
   `config/cityworld/schematics/Neighborhood/pokecenter.nbt` so the suburbs get one too.
4. **The sidecar.** Beside each copy, `pokecenter.nbt.yml`:

   ```yaml
   Title: "Pokémon Center"
   OddsOfAppearance: 0.3
   Decayable: false
   ```

5. **The gym**, the same way, as `config/cityworld/schematics/Municipal/gym.nbt` with a rarer sidecar:

   ```yaml
   Title: "Pokémon Gym"
   OddsOfAppearance: 0.05
   BroadcastLocation: true
   ```

6. **Restart, then test.** `/cityschem list` should name both, and `/cityschem pokecenter` pastes one at your feet
   to check it looks right. In a new world, `/cityfind pokecenter` (or `/cityfind tp gym`) finds the nearest one
   generated.

Odds of `0.3` in two districts give roughly one Center for every two or three city squares. If they come up too
often or too rarely, change the number. It only affects land that hasn't been generated yet.

## Good to know

- **Schematics must be switched on for the world.** They are on in the **Modern** (the default CityWorld
  type) and **Apocalypse** presets and off in the other styles. Turn them on with **Schematics** in the
  Customize screen when you create the world, or in a settings datapack: `"features": { "includeSchematics": true }`
  (see [Configuring CityWorld](Configuring-CityWorld)).
- **Only new chunks.** CityWorld reads the folder when the game starts and uses it for land generated after that.
  Explored land never changes.
- **A building may face any way.** Each one is turned a random quarter-turn when placed, so a front door can face
  away from the road. A door on two sides, or a building that looks right from every side, sits best.
- **Modded blocks need their mod.** A block from a mod that isn't installed places as air, so a Pokémon Center
  without Cobblemon is a room with gaps. Blocks with contents keep them: chests, signs, a PC, a healing machine.
- **Entities are not placed.** Mobs, villagers, armour stands, item frames and paintings in the file are skipped.
  Item frames can be hung by a [fit-out](Building-Palettes#schematic-fit-outs), and a mod that spawns its own
  NPCs (a nurse, a gym leader) has to do that itself.
- **Palettes don't change a schematic, substitutes do.** A [block substitute](Building-Palettes#block-substitutes)
  swaps a block in schematic buildings too. The per-part palettes are for CityWorld's own buildings only.
- **Old files are upgraded.** A schematic saved on an older Minecraft version is brought up to date as it loads.
  Very old MCEdit `.schematic` files can lose blocks that were renamed since (they place as air). A modern
  format (`.nbt`, `.schem`, `.litematic`) avoids that.
- **A broken file is skipped**, with a `SchematicLibrary: failed to load` line in the log naming it.
  `SchematicLibrary: +N custom schematics from …` at startup says how many were found.

## Loot, and what is inside

Every container in a schematic gets a loot table when it is placed:

1. the table named by the sidecar's `Loot:` key, if it has one;
2. otherwise `cityworld:chests/schematic/<name>`, if a datapack provides it. The name is the file name in lower
   case, with spaces as underscores: `data/cityworld/loot_table/chests/schematic/pokecenter.json`
   (`loot_tables` on 1.20.1);
3. otherwise the usual loot for the district it stands in.

A datapack can also change what is inside a building without a new schematic: swap blocks and hang item frames
with a [schematic fit-out](Building-Palettes#schematic-fit-outs). See [Loot Tables](Loot-Tables) for the tables.

## Commands

- `/cityschem list` lists every schematic, bundled and yours. `/cityschem <name>` pastes one with its corner at your
  feet, whether or not the world places schematics.
- `/cityfind <name>` and `/cityfind tp <name>` find the nearest one in the world, by file name or `Title`.
- `/cityinfo` names the schematic you are standing on.

`/cityschem` and `/cityfind` are for ops by default; `/cityinfo` is for everyone. See [Commands](Commands) and
[Permission Nodes](Permission-Nodes).
