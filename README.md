# CityWorld (NeoForge)

A **NeoForge** port of the classic [CityWorld](https://www.spigotmc.org/resources/cityworld.2250/)
Bukkit plugin — it procedurally generates endless cities, roads, buildings, mines, sewers, farms
and nature.

| | |
|---|---|
| **Minecraft** | 1.21.11 |
| **Loader** | NeoForge 21.11.42 |
| **Java** | 21 |
| **Licence** | GPL-3.0-only |
| **Status** | 🚧 Work in progress — the generator pipeline works end-to-end, but terrain is still a placeholder |

> **Not yet playable as CityWorld.** The custom chunk generator, dimension and world preset are
> wired up and proven, but the generation "brain" (the city/terrain algorithms) is still being
> ported, so worlds currently generate a flat placeholder. See **`PORTING.md`** for the plan and
> current progress.

## Delivery

CityWorld is reachable two ways, both on one shared chunk generator:

- a **world preset** — pick **CityWorld** as the world type when creating a world; and
- a custom **dimension** (`cityworld:city`) — the `/cityworld` teleport command lands here (command
  still to come).

## Building from source

Requires a JDK 21.

```bash
./gradlew build
# output: build/libs/cityworld-<version>.jar
```

Standard [NeoForge ModDevGradle](https://github.com/neoforged/ModDevGradle) setup. Use
`./gradlew runClient` or `./gradlew runServer` for a dev instance, or `./deploy.sh` to copy the jar
into a NeoForge test instance.

## Licence and credits

CityWorld is licensed under the **GNU General Public License v3** — see [`LICENSE`](LICENSE).

- Original **CityWorld** Bukkit plugin by **DaddyChurchill**
  ([echurchill/CityWorld](https://github.com/echurchill/CityWorld)), released under GPL-3.
- This **NeoForge port** by **Sablednah**, continuing under GPL-3 as a derivative work.

Because this is a derivative of GPL-3 code, the port and any redistribution must remain GPL-3.
