# Permissions and access

**Status: accurate as of 5.3.2, and hand-written** — like Factions' `NODES.md` and unlike Standards',
which is generated from its source because it has 72 nodes to generate from.

## CityWorld declares no permission nodes

Worth stating plainly, because a file called `NODES.md` implies a table of nodes: `grep -r
PermissionNode src/` returns nothing. CityWorld does not call NeoForge's `PermissionAPI` at all.

Every command gates on **vanilla operator level** instead, through
`Commands.hasPermission(Commands.LEVEL_GAMEMASTERS)` — op level 2, the same level vanilla requires
for `/gamemode` and `/tp`.

That means **a permissions manager cannot currently grant or deny any CityWorld command**. Standards'
`/rank`, LuckPerms and everything else are out of the loop; the only lever a server owner has is
whether somebody is an operator. See "The gap" below, because this is probably not what you want now
that Standards has a permission manager.

## The commands

| Command | Who | What it does |
|---|---|---|
| `/cityinfo` | **anybody** | Reports the world's style, seed-derived settings and the lot under your feet. Read-only. |
| `/cityworld` | op level 2 | Teleport into the CityWorld dimension. `/cityworld leave` returns you to the overworld. |
| `/cityfind <name>` | op level 2 | Find the nearest named landmark or schematic; `/cityfind tp <name>` teleports. |
| `/cityfind lots` | op level 2 | List the lot kinds you can search for. |
| `/cityfind lot <kind>` | op level 2 | Find the nearest lot of a kind; `/cityfind lot tp <kind>` teleports. |
| `/cwlocate <biome>` | op level 2 | Find the nearest matching biome; `/cwlocate <biome> tp` teleports. |
| `/cityschem list` | op level 2 | List the loaded schematics. |
| `/cityschem <name>` | op level 2 | Paste a schematic at your position. **Writes blocks.** |
| `/cityexport [name]` | op level 2 | Write this world's settings out as a datapack JSON. **Writes a file.** |

`/cityinfo` is the only ungated command, deliberately: it reads, it teleports nobody, and it writes
nothing. Everything else either moves a player or changes the world.

## The gap: none of this is grantable

Three of these are ordinary player-facing conveniences that a server might reasonably want to hand
out without making somebody an operator — `/cityinfo` already is, but `/cityfind` and `/cwlocate` are
exactly the sort of thing an adventure server gives its players. There is no way to do that today
short of op, which also hands them `/cityschem` and `/cityexport`.

Wiring these to `PermissionAPI` would be a small, contained change — the reference pattern is already
in the family, in `MobHealth-Forge`'s `MobHealthPermissions.java`: declare the nodes, register them
on `PermissionGatherEvent.Nodes`, and give each a default resolver that falls back to the current op
level. Nothing changes for a server with no permissions manager installed, and a server with one gets
per-command control.

The likely node names, if and when that happens:

| Node | Would default to | For |
|---|---|---|
| `cityworld.info` | everyone | `/cityinfo` |
| `cityworld.teleport` | ops | `/cityworld`, `/cityworld leave` |
| `cityworld.find` | ops | `/cityfind`, `/cwlocate` |
| `cityworld.schematic` | ops | `/cityschem` |
| `cityworld.export` | ops | `/cityexport` |

**This table is a proposal, not a description — do not configure against it.** Those nodes do not
exist yet. When they do, this section becomes the node table and the "declares no permission nodes"
heading above comes out.
