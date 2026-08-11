# Commands

All CityWorld commands tab-complete their arguments. `/cityinfo` works for anyone; everything else
needs op (permission level 2 — "gamemaster").

## `/cityinfo`

Reports the plan under your feet: **anyone** can run it, read-only, no side effects.

```
> /cityinfo
at: chunk -14, 22
context: NeighborhoodContext (URBAN)
lot: HouseLot (STRUCTURE)
shop: CORNER_SHOP · FLETCHER
schematic: Colonial Cottage
roads: 2
nature: 4%
```

`shop`/`schematic` only appear when relevant (a shop lot, or a pasted schematic building). Handy for
figuring out *why* something generated the way it did, or confirming which world style/settings a
server is actually running.

## `/cityworld` / `/cityworld leave`

Teleport into (and back out of) the dedicated `cityworld:city` dimension — a separate, always-on
CityWorld world independent of whatever your overworld generator is set to. Drops you on the surface at
your current X/Z. Useful on a server whose main world uses a different generator, or for admins who want
a always-available CityWorld to demo/test in without touching the main map.

```
/cityworld          # teleport in
/cityworld leave     # back to the overworld
```

## `/cityschem <name>` / `/cityschem list`

Paste one of the bundled classic-building schematics directly, at your feet — independent of whether
`includeSchematics` is on for natural generation. Good for hand-placing a specific building, or testing
a schematic you just dropped into `config/cityworld/schematics/`.

```
/cityschem list
> 67 classic schematics: Colonial Cottage, Tudor Manor, Fire Station, Lighthouse, ...

/cityschem Lighthouse
> Pasted 'Lighthouse' [MARITIME] (9x24x9) at 120, 71, -340
```

The name argument tab-completes and is a *greedy string*, so multi-word names (`Fire Station`) work
without quoting.

## `/cityfind <name>` / `/cityfind tp <name>`

Hunt down the nearest instance of a specific schematic building by name (substring match, case
insensitive). Add `tp` to jump straight there.

```
/cityfind lighthouse
> Searching for 'lighthouse'...
> Nearest 'Lighthouse' [MARITIME] at x=1520 z=-340  (1683 blocks NE)

/cityfind tp lighthouse
> Teleported to the nearest 'Lighthouse'.
```

The search runs **off the main thread** in an expanding ring around you (planning is cheap but not
free), so the server keeps ticking while it looks. A search can take a little while for something rare
or far away — it gives up after ~9,600 blocks or two minutes, whichever comes first, and tells you how
far it actually got if it comes up empty. Repeated searches from the same area get faster, since plans
are cached.

## `/cityfind lot <kind>` / `/cityfind lot tp <kind>` / `/cityfind lots`

The same ring-search, but for **procedurally-generated landmark lots** rather than pasted schematics —
the rare set-pieces that don't have a fixed "name" (a zoo isn't a schematic, it's a whole generated
enclosure). Matches on the lot's type as a substring, so anything works (`office`, `warehouse`), but
`/cityfind lots` lists the well-known, worth-hunting-for kinds:

```
/cityfind lots
> Findable lot kinds: zoo, biodome, saucer, balloon, blimp, fishpond, cornershop, castle,
  oilplatform, radiotower, watertower, monument, library, museum, campground, mineentrance,
  bunker, farm, park

/cityfind lot tp castle
> Teleported to the nearest Old Castle.
```

## `/cityexport [name]`

Bottles the **current world's actual effective settings** — however you got there, whether that's a
style preset, the Customize screen, or a hand-edited datapack — into a ready-to-use datapack:

```
/cityexport my-server-config
> Exported this world's settings to config/cityworld/exports/my-server-config
  — copy that folder into <world>/datapacks/ to use it.
```

This is the recommended way to move a world you like from single-player to a server: shape it with
Customize, `/cityexport` it, then hand that folder to whoever manages the server. See the
**Configuration** page for what's inside.

## `/cwlocate <biome> [tp]`

CityWorld runs its own biome map (matched to the same terrain the city planner uses), so vanilla's
`/locate biome` can't see it properly. `/cwlocate` is the CityWorld-aware equivalent — searches outward
in chunk rings for the nearest matching biome, with an optional `tp`:

```
/cwlocate taiga
> Nearest 'taiga' at x=-560 z=880  (912 blocks SW)

/cwlocate desert tp
> Teleported to the nearest 'desert'.
```

Only works on worlds using CityWorld's own climate biome source (MODERN and related styles) — CLASSIC
uses a fixed elevation palette instead and isn't searchable this way.

---

*Every search command (`/cityfind`, `/cwlocate`) plans chunks off-thread and caches results, so the
server stays responsive even while hunting something genuinely rare or far away.*
