# Train mods in CityWorld's subway: survey, what was built, what is left

*Survey and build, 2026-11-14/15, for GitHub issue #7 ("Request for Compatibility for Train mods", TheRealADOBO).
Each mod was read from its own jar (`unzip` + `javap`) or source, never a wiki: Slash Rails 0.3.0 (GitHub
slashdaemon/SlashRails, CC-BY-4.0), Improvement Minecarts 1.3.0 (Modrinth `improvement-minecarts`, mod jar and
datapack), Railcraft Reborn 1.21.1-1.2.17, Choo Choo 1.2.0 (CurseForge 1747661, All Rights Reserved: read, never
copied). The reporter says the first two already work with CityWorld: "I have a station that notifies on arrival,
the train waits a bit then heads to the next stop. I edited some of the corners with SlashRail".*

## 0. The four mods

| Mod | Lines it ships for | What it does | What CityWorld does for it |
|---|---|---|---|
| **Slash Rails** (`slashrails`) | 1.20.1 Forge, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 | A *Track Smoother* item a player uses on a rail: it walks the flat run (≤ 512 rails, stops at slopes) and renders/drives the cart along a smooth curve through a staircase of short rails. A single 90° corner "can only be rounded a little". A rail is *tight* when a colliding block sits within ±1 of it at rail height or +1. Nothing is automatic. | **Bends are curves now** (§1): every subway bend is a staircase arc on both tracks through a round-walled tunnel, clear at cart height around every rail. The player still smooths it; there is nothing to call. |
| **Improvement Minecarts** (`improvement_minecarts`) | mod jar + datapack, 1.21–26.2 | Manages carts of `#improvement_minecarts:carts` on vanilla rails (`#rails`: rail, powered, detector, activator). A **station** is a rail over a lapis block (`#station_block`; emerald/amethyst are the config's alternatives): a train stops there, dwells (`#dwell` 200 ticks), then leaves. A **gold block** is a terminus: wait, reverse, depart. A redstone lamp under a rail is a signal. A sign directly beside the station rail is announced from its first line. | **Each platform track gets its stop** when the mod is there (as a mod or as a datapack): lapis under a vanilla rail mid-platform, gold near the buffers of a dead-end station, and a wall sign on the pillar between the tracks whose first line is the station's name (the road it stands on). Setting `subways.stops`. |
| **Railcraft Reborn** (`railcraft`) | 1.20.1, 1.21.1, 1.21.10, 26.1.2 | Its own track family, each a `BaseRailBlock` with its own shape property: `reinforced_track`, `reinforced_booster_track` (its own `powered`), `abandoned_track` (`grass`), `reinforced_buffer_stop_track` (`reversed`), `reinforced_delayed_locking_track` (holds a cart a while; a block entity). Steam/electric locomotives need fuel or power. | **Track pools** (§2): the subway lays its plain, powered, ruined, buffer-stop and platform-stop rails from five `#cityworld:fittings/rail*` tags, which ship Railcraft's five as optional entries. Locomotives: not parked (they need fuel, a boiler, or an electric line to do anything). |
| **Choo Choo** (`choochoo`) | 1.20.1 Forge, 1.21.1 (NeoForge 21.1.256+), 1.21.8, 1.21.11 (21.11.45+), 26.1.2 | Locomotives and carriages as entities (`choochoo:locomotive`, `passenger_cart`, `single_cart`, `flatbed_cart`, `storage_cart`) coupled with a `coupling_chain`; couplings live in level saved data (`CouplingData`, keyed by entity ids). No blocks. | **Parked trains** (§3): a locomotive and carriages standing at a platform of some stations (`subways.oddsOfTrain` 1/2, `subways.trainCarriages` 2), from entity tags `#cityworld:subway/locomotive` and `#cityworld:subway/carriage`, coupled once they are in the world. |

Create and MTR are mentioned in the issue as the heavy options; not looked at.

## 1. Curved bends (every line, every world)

`Subway.tunnel` on a bend: `arc(mask, line)` walks each track (across 6 and 9) round a quarter circle about the
chunk corner the two open sides share (radius `15.5 - line`, greedy toward the cell nearer the circle, then
mirrored for the bend's orientation), giving each cell the rail shape of its two neighbours; `bendProfile` cuts an
annulus round both tracks instead of the old square profile, plus every cell within one of a rail up to three
above the floor (Slash Rails' tight test); `bendLamps` hangs three crown lights round the arc. Ramps keep the old
profile and corner. Decoration only: the plan hash is unchanged.

Measured (1.21.11, seed 8675309, bend at chunk −11,−5, 126 bends in 60 chunks): Slash Rails smooths the two runs
through the bend, "Smoothed 160 rails" and "Smoothed 192 rails", none tight; the old corner reads the same counts
but each turn is the one corner rail. Self-test `subway.bend`: every rail in a plain bend leads at both ends to a
rail or out of the chunk, with no colliding block above it (fails on a broken or roofed curve; a ruin reports only).

## 2. Track pools (Railcraft)

`Support/Railway`: one block per pool per world (the first by id). `track(ruined)` = `fittings/rail_ruined` for a
ruin, then `fittings/rail`, else vanilla rail; `booster()` = `fittings/rail_booster`, else powered rail (always over
its redstone block); `bufferStop()` = `fittings/rail_buffer_stop` at a dead end in place of the iron block, turned by
`reversed` (Railcraft's model: unreversed north-south has its bumper north, east-west east); `platformStop()` =
`fittings/rail_stop` at each track's stop cell when Improvement Minecarts is not marking stops. Shapes go through the
block's own shape property (`SupportBlocks.withRailShape`: `BaseRailBlock.getShapeProperty()`), and `powered` by name
when the block declares its own.

## 3. Parked trains (Choo Choo)

`Subway.hall` for a station's own hall, last of all its drawing and only while `#cityworld:subway/locomotive` holds a
type (so a world without one takes no new draw): roll `oddsOfTrain`; pick a track; the locomotive at along 12 facing
an open +along end (else at 3 facing −along), carriages behind it `Railway.SPACING` (3.0, measured: they settle about
0.3 closer when coupled) apart while track remains. Written as NBT into the proto-chunk (the `worldgen/Vehicles`
pattern): position, yaw, `ForwardX/ForwardZ` (the locomotive's own heading), a fixed id from the odds, and on each
carriage a tag `cityworld.couple:<id of the vehicle ahead>`.

`Support/TrainCouplings`: the entity-join event queues a tagged carriage; the server tick, once a second for half a
minute, looks both vehicles up by id and, when both are in the world, calls Choo Choo's `CouplingData.connect` at
their two nearest ends (by `CoupledMinecart.anchor`) with a coupling chain stack, then drops the tag. All by
reflection: nothing loads without the mod. Measured (1.21.11 + Choo Choo on NeoForge 21.11.48 by
`ORG_GRADLE_PROJECT_neo_version`): three stations with trains, each a component of 3 after coupling, tags gone.

**Traps this cost:** a vehicle that has just joined is not found by id (`ServerLevel.getEntity`) until its chunk is
in the world proper, so `server.execute` from the join event found nothing every time; and **a dedicated server
with no player stops ticking after `pause-when-empty-seconds` (60 s, 1.21.2+)**, so a headless probe's queued work
never ran until `run/server.properties` set it to −1. Per-line: `MinecraftServer.schedule(TickTask)` on 1.21.11+
is `tell` on 1.21.1/1.20.1.

## 4. Stops (Improvement Minecarts)

`Railway.marksStops`: `subways.stops` on and the mod present by mod id or by its `improvement_minecarts:tick`
function (the datapack form), answered once per server. `Subway.stops` per station hall: a through hall stops at
along 8 (beside the middle pillar), a terminus (one end closed) at 13 or 3 beside the end pillar, on gold. Measured
(1.21.11 + the mod jar): lapis at 8,6 and 8,9 with signs "Main Street" etc. on the pillar; a fuelled furnace cart set
rolling at the platform's end stopped on the lapis, stood ten seconds (the 200-tick dwell), and left at speed.

## 5. Not done, and why

- **Railcraft locomotives** — they do nothing without fuel and water (steam) or an electrified line; a parked one
  would be scenery that looks broken. Its carts could go in `#cityworld:subway/carriage` by a pack.
- **"The metro should always run"** (the issue's Improvement Minecarts wish) — a running train is a live cart that
  must exist at generation time and keep a loaded chunk; CityWorld places worlds, it does not run them. A pack can put
  a furnace cart at each terminus by the spawn datapack.
- **Choo Choo heading after coupling** — the locomotive's front anchor reads toward its carriages once coupled (Choo
  Choo re-ends the consist); which way it drives is the player's starter button. Not seen in a client.
- **Create / MTR** — not looked at.
