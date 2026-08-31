# Permission nodes

**Status: accurate as of 5.3.2, and hand-written** — like Factions' `NODES.md` and unlike Standards',
which is generated because it has 72 nodes to generate from. Five is a table you can read.

CityWorld asks NeoForge's `PermissionAPI` for every one of these, so they work with LuckPerms, with
Standards' own handler (`/rank`), or with nothing installed at all — in which case the **Default**
column is the whole answer.

| Default | Means |
|---|---|
| `everyone` | Every player has it unless a permissions mod takes it away. |
| `ops` | Operators (level 2, the vanilla `gamemaster` level) have it; anybody else needs it granted. |

## The nodes

| Node | Default | What it allows |
|---|---|---|
| `cityworld.info` | `everyone` | `/cityinfo` — read the plan under your feet: world style, context, lot, nature percentage, and the name of the schematic you are standing in. Reads only. |
| `cityworld.teleport` | `ops` | `/cityworld` and `/cityworld leave` — jump in and out of the `cityworld:city` dimension. |
| `cityworld.find` | `ops` | `/cityfind`, `/cityfind lot`, `/cityfind lots` and `/cwlocate` — search for a landmark, a lot kind or a biome. **Includes the `tp` forms**, so this grants travelling to what was found. |
| `cityworld.schematic` | `ops` | `/cityschem list` and `/cityschem <name>` — list and paste schematics. **Writes blocks into the world.** |
| `cityworld.export` | `ops` | `/cityexport [name]` — write this world's settings out as a datapack JSON. **Writes a file to the server.** |

## Notes worth having before you configure

**`cityworld.find` is the one most worth delegating.** Finding a hospital, a vault or a biome is a
guide's tool rather than an administrator's, and it is the obvious candidate to grant to moderators or
to everybody on an exploration server. Be deliberate about it though: the `tp` forms ride on the same
node, because a search you cannot travel to is of little use. Granting `find` grants fast travel to
anything CityWorld can name.

**`cityworld.info` is open by default** because it reads, moves nobody and writes nothing. Revoke it
if you would rather players not see how the world is put together.

**`schematic` and `export` are separate from everything else on purpose.** One writes blocks and the
other writes files; neither is in the same category as looking something up. Splitting them is most of
the reason these nodes exist — before them, handing somebody `/cityfind` meant making them an
operator, which handed them both of these too.

**The console, command blocks and functions are unaffected.** `PermissionAPI` answers about a player,
and those have no player to ask about, so they fall back to the vanilla operator-level check. A server
console can always run every CityWorld command.

**With no permissions manager installed nothing changes.** Each node's default resolver returns
exactly what the old op-level gate returned, so an unmanaged server behaves as it did before nodes
existed. These are a new lever, not a new policy.

## Where they live

Declared in `src/main/java/me/daddychurchill/CityWorld/CityWorldPermissions.java` and registered from
`CityWorldServerEvents` on `PermissionGatherEvent.Nodes`. The self-test asserts all five are
registered with the active handler — `PermissionAPI.getPermission` throws for an unregistered node, so
a dropped registration would break every command for players while leaving the console working.
