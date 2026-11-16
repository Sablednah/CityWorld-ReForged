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
| **Slash Rails** (`slashrails`) | 1.20.1 Forge, 1.21.1, 1.21.11, 26.1.2, 26.2, 26.3 | A *Track Smoother* item a player uses on a rail: it walks the flat run (≤ 512 rails, stops at slopes) and renders/drives the cart along a smooth curve through a staircase of short rails. A single 90° corner "can only be rounded a little". A rail is *tight* when a colliding block sits within ±1 of it at rail height or +1. Nothing is automatic. | **Bends are curves now** (§1): every subway bend is a staircase arc on both tracks through a round-walled tunnel, clear at cart height around every rail, and each is smoothed through the mod's own `RunService.smooth` the first time it generates (`subways.smoothBends`) — unless Choo Choo is installed too (§6). |
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

**Smoothed for the player (2026-11-15, owner: "default on when slash rails is installed").** `Support/RailSmoothing`:
the chunk-load event for a chunk just generated (`isNewChunk`, never one read from disk) queues the middle rail of each
of a plain bend's two curves; a second later, once the chunk is in the world, it calls Slash Rails'
`RunService.smooth(ServerLevel, BlockPos)` by reflection — the method the Track Smoother calls, unchanged in name and
signature through all three releases (v0.1.0 2026-09-19, v0.2.0, v0.3.0 2026-10-05; the file's four commits never
touched it). The mod walks the run itself and stops at unloaded chunks and at rails of other runs, so each bend is its
own run of a few hundred rails at most, well under the 512 cap (and the cap truncates, it does not fail). Runs live in
the mod's saved data (`slashrails_runs`), so this happens once per bend. Setting `subways.smoothBends` (default true).
The mod refuses a run that is too short ("Too few connected rails to smooth"), which a bend generated before the
track either side of it always is, so a chunk's generation also re-queues its four loaded neighbours' bends that are
not yet in a run. Measured (1.21.11, 30-chunk probe): every bend queued was smoothed, 50–288 rails a run, none too
short. Self-test `subway.bend.smoothed`: every rail of the checked bend is in one of the mod's runs (read from
`SmoothRunRegistry` by reflection) when Slash Rails is loaded; the check holds the bend and its four neighbours on
forced tickets first (a chunk read with no ticket had unloaded before the queue ran, and every try read "not
loaded").

## 2. Track pools (Railcraft)

`Support/Railway`: one block per pool per world (the first by id). `track(ruined)` = `fittings/rail_ruined` for a
ruin, then `fittings/rail`, else vanilla rail; `booster()` = `fittings/rail_booster`, else powered rail (always over
its redstone block); `bufferStop()` = `fittings/rail_buffer_stop` at a dead end in place of the iron block, turned by
`reversed` (Railcraft's model: unreversed north-south has its bumper north, east-west east); `platformStop()` =
`fittings/rail_stop` at each track's stop cell when Improvement Minecarts is not marking stops. Measured on
1.21.1 with Railcraft 1.2.17: reinforced track throughout, booster track over every redstone block, the delayed
locking track at along 8 (along 3 at a terminus), the buffer stop at a west dead end `reversed=true` (bumper to the
wall); Slash Rails smooths Railcraft's track through a bend the same as vanilla's (160/192 rails). Shapes go through the
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
On 1.21.1 (NeoForge 21.1.266, with Railcraft and Improvement Minecarts too) a north-south train would not couple
("3.0 apart": its nearest ends were its centres) until each carriage was given Choo Choo's `PassengerNose` yaw
(degrees of `atan2(z, x)`, 0 = +x; the default 0 lies east-west whatever the track) — and with a nose but no
`TwoSeatVersion: 1` Choo Choo takes the carriage for an old one-seat save and swaps it for its `single_cart`. Both
are now written; every train in that run coupled as passenger carts.

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

**Improvement Minecarts on 1.21.1:** its 1.21–1.21.4 jar logs two function parse errors there (`minecraft:iron_chain`,
an item 1.21.1 does not have, in its cart-chaining functions); stations, dwell and announcements are separate
functions. Its own matter, not CityWorld's — but its predicate `holding_iron_chain` fails the self-test's
datapack-parse gate, so the jar stays out of `mc1.21.1/run/mods` for a self-test (the probes ran with it).

## 5. Not done, and why

- **Railcraft steam locomotives** — they do nothing without fuel and water; a parked one would be scenery that looks
  broken. The **electric** one is done (§7): the line is electrified and the city charges it.
- **"The metro should always run"** (the issue's Improvement Minecarts wish) — a running train is a live cart that
  must exist at generation time and keep a loaded chunk; CityWorld places worlds, it does not run them. A pack can put
  a furnace cart at each terminus by the spawn datapack.
- **Choo Choo heading after coupling** — the locomotive's front anchor reads toward its carriages once coupled (Choo
  Choo re-ends the consist); which way it drives is the player's starter button. Not seen in a client.
- **Create / MTR** — not looked at.
- **An interchange station's dead end** (seen once, 1.21.1 station −27,−15): one track's buffer stop and its stop sign
  were missing. That station is in a lush cave region, and the cell under its parked locomotive read clay: CityWorld's
  own `LushCaves` pass took the hall's air above its rails for a cave and laid moss/clay over 35% of such floors.
  Fixed (§6); the missing pieces were most likely the same pass.

## 6. All four together (2026-11-15, owner: "someone's gonna install all 4 and go 'why's it not working'")

Each pair, read from the jars and then ridden on 1.21.1 (NeoForge 21.1.266, all four mods, seed 8675309, the bend at
chunk −11,−5, 8 blocks of straight before it; probe `rideBend`, a scratchpad debug listener):

| Pair | Finding | What CityWorld does |
|---|---|---|
| Slash Rails + Railcraft | Railcraft's track is `BaseRailBlock`; the mod smooths it like vanilla's (160/192 rails). | nothing needed |
| Slash Rails + Improvement Minecarts | A fuelled furnace cart rides a smoothed bend and out at speed. | nothing needed |
| Slash Rails + Choo Choo | Slash Rails' `CartTickMixin` wraps `AbstractMinecart.tick`'s `moveAlongTrack` and, on a smoothed run, moves the cart itself without calling it (`CurveRide.stepOnRail`, no check of the cart's type). Choo Choo's `LocomotiveEntity` overrides `moveAlongTrack`: its traction. **A running locomotive (coupled to a carriage, engine RUNNING) stood still for 60 s on a smoothed run; unsmoothed, with Slash Rails installed and without, it pulled away and round the bend.** Unpowered Choo Choo carriages with a rider ride a smoothed bend fine. | `RailSmoothing.heldBack()`: no auto-smoothing while `choochoo` is loaded (logged once). Not ours to fix; worth a report to Slash Rails. |
| Improvement Minecarts + Railcraft | Every predicate and function keys on `#improvement_minecarts:rails` (vanilla's four), so on Railcraft track its speed control, signals and cart chaining do not run; its stops still worked because CityWorld lays a vanilla rail over the lapis. | `data/improvement_minecarts/tags/block/rails.json` adds CityWorld's five rail pools (optional entries): the tag reads true on Railcraft track, and the furnace cart then ran 37 blocks in four seconds where it ran 20. |
| Improvement Minecarts + Choo Choo | Its `#carts` tag is vanilla's seven minecart types: it neither manages nor stops a Choo Choo train. | documented only (Choo Choo trains are driven) |
| Railcraft + Choo Choo | Choo Choo's vehicles extend vanilla `Minecart`; a running train drove on Railcraft's track, boosters included. | nothing needed |

**Starting a Choo Choo locomotive with no player:** open the firebox hatch (`engine.toggleServiceHatch`), `addCoal`
while `canAddCoal`, `ignite` (FIRE_LIT), close the hatch (that calls `startFromLiveFire`: STARTING, then RUNNING
after about three seconds). It drives toward its `ForwardX/ForwardZ`. An empty minecart dies by friction within a
second or two (0.96 a tick unridden, vanilla's), so a pushed empty cart says nothing about a bend; give it a rider.

**Found on the way: `LushCaves` carpeted the subway.** It decorated any air cell above a solid block below the street
(`isFloor`), so in a lush region it laid moss and clay over the rails (and hung vines in the halls); the WATCH at
−420,41,−234 read rail → moss_block from `LushCaves.decorateFloor`. It now decorates only floors and ceilings of rock or
soil (`#base_stone_overworld`, `#dirt`, gravel, clay); the cell reads booster track again.

## 7. The electric line (2026-11-16, owner: "have a try at the electric line over night - answer your queries with tests - and then build the best result. If theres a way to recharge we can built into the station somehow")

Read from Railcraft Reborn 1.21.1-1.2.17 with `javap`, then measured on 1.21.1 (seed 8675309, Railcraft + Slash Rails in
`run/mods`; probe listener `scratchpad trains/TrainProbe-1.21.1-electric.java`, never committed).

**Built.** With `#cityworld:fittings/rail_electric` naming a block (Railcraft's `electric_track`) and `subways.electric`
on (default), a line that is not a ruin is drawn inside `Railway.electrified(...)`, and every track picker answers from
the electric pools: `rail_electric`, `rail_electric_booster`, `rail_electric_buffer_stop`, `rail_electric_stop`. A piece
the electric pools lack is plain electric track (a stop: none), never a plain rail, which would split the network
(1.20.1's Railcraft has no electric locking track). Improvement Minecarts' stop rail is the line's own electric track
over the lapis (its rails tag carries the four electric pools). Each station's own hall has two `fittings/rail_battery`
(Railcraft's `nickel_zinc_battery`, rechargeable, 150,000) at the foot of its middle pillars (along 7, across 7 and 8,
rail height), one against each track. `Support/Substations` reads the three pillar columns of every loaded chunk of an
electric world for batteries (no planning), keeps them per level while loaded, and every 20 ticks gives each
`subways.stationCharge` × 20 (default 20 a tick) through the block energy capability: `IEnergyStorage` on 1.21.1,
`Capabilities.Energy` + a transaction on 1.21.11 and 26.x, Railcraft's own `Charge.network(level).access(pos).storage()`
by reflection on 1.20.1 Forge (no block capability there). A parked train on an electric line may be an
`#cityworld:subway/electric_locomotive` (Railcraft's `electric_locomotive`, alone; with Choo Choo's locomotive in its pool
too, a coin per station). Self-test `subway.electric` (track, batteries, the supply knows and feeds them, the track then
has charge on its network).

**The questions, and what answered them:**

| Question | Answer (measured unless it says "read") |
|---|---|
| Do blocks placed at generation join the network? | Yes, with nothing extra. `ChargeNetworkImpl.access(pos)` registers an unknown node from its block state and `addNode` floods through every loaded connected neighbour (read), so the first capability call on a battery, or the first time a locomotive draws, builds the whole line. Generated station: one grid of 226 nodes (both tracks, both batteries, the tunnels either side), capacity 300,000. |
| How does a battery connect? | Beside a rail at rail height: yes (it joined the track's grid). Under a rail: no (a one-node grid of its own). Hence the pillar feet. Each battery touches its track and the other battery, so both tracks are one grid. |
| Does the city's recharge work? | Yes. `receiveEnergy` / `insert` puts charge in (50,000 accepted at once); fed at 20 a tick, each battery rose 11,392 → 15,177 in 200 ticks. Any FE generator a player connects charges them as well (Railcraft's batteries are the standard capability). |
| Does an electric locomotive run on it? | Yes: started (SHUTDOWN → RUNNING) it filled its own 5,000 from the grid, ran at 0.30 a tick (6 blocks a second) through the station and down the tunnel, and drew about 80 per 8 ticks after. It rode a **smoothed** bend (Slash Rails) and out the other side, so no hold-back as for Choo Choo. Parked ones (6 of 8 stations at `oddsOfTrain` 1/2) started and drove 35–36 blocks in 6 s out of the station's open end, on boosters or not. |
| Does the third rail shock? | No. The track's `entityInside` → `CollisionHandler.ELECTRIC` → `Charge.Access.zap(entity, TRACK, 2)` needs 2,000 *available* charge in a tick and a killable entity (living, attackable, not riding a minecart; read). Measured: a pig and a zombie held on charged rail for 60 s, and `entityInside` called directly on a pig, a zombie and a villager with the grid part-charged (39k) and full (300k): no damage, though all three test killable. No setting needed. |
| Can one be parked from NBT? | Yes, and it saves mode SHUTDOWN. **Its heading is a minecart's yaw**: traction is `(cos yaw, 0, sin yaw)` × horsepower (read), so 0 is east, 90 south. Saved with a mob's yaw (what Choo Choo's parking writes) it stood across its track: yaw 90 on an east-west line, and 0/180 on a north-south one, never moved (yaw −90 on east-west snaps to 0 and runs east, which hid it at first). `parkTrain` writes `atan2(dz, dx)` for an electric locomotive. |
| Does it survive a restart? | Yes. The batteries' charge is in Railcraft's saved data (`ChargeSavedData`); the supply found all six batteries again on load, the 226-node grid rebuilt on first use, and a fresh locomotive with an empty store filled to 5,000 from it and ran 24 blocks. |

**Not done.** Railcraft's delayed locking track does not hold a running locomotive (measured: it ran straight
through the station's), so an electric train does not stop at stations by itself; Improvement Minecarts manages
vanilla minecarts only. A timetable would be redstone (Railcraft's locomotive/throttle tracks), which is the player's.
1.20.1 is unmeasured: Railcraft 1.20.1's mixins are SRG-named and do not load in the named dev runtime; its charge API is
the same calls (read from 1.20.1-1.1.11).

**A probe trap, paid for twice:** a level with no players stops ticking entities 300 ticks after it empties (1.21.1;
1.21.2+ adds `pause-when-empty-seconds` on top). A wait in a headless probe must call `level.resetEmptyTime()` or every
locomotive "does not move" — that, not their NBT or the boosters, was why the first parked locomotives stood still.

