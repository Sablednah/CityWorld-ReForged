# CityWorld (NeoForge)

A modern **NeoForge** rewrite of the classic [CityWorld](https://www.spigotmc.org/resources/cityworld.2250/)
Bukkit plugin — it procedurally generates endless cities, roads, buildings, mines, sewers, farms
and nature.

| | |
|---|---|
| **Minecraft** | 1.21.11 |
| **Loader** | NeoForge 21.11.42 |
| **Java** | 21 |
| **Status** | 🚧 Early port — Phase 0 (scaffold) |

> This is a work-in-progress port. The generation logic is being ported from the original Bukkit
> plugin (kept as the reference implementation in the sibling `CityWorld-ReForged` repo). See
> that repo's `PORTING.md` for the phased plan and progress.

## Delivery

CityWorld will be reachable two ways (both on one shared chunk generator):

- a custom **dimension** (`cityworld:city`), entered/left with `/cityworld`; and
- a **world preset** ("world type") for whole-world city generation at creation.

## Building from source

Requires a JDK 21.

```bash
./gradlew build
# output: build/libs/cityworld-<version>.jar
```

Standard [NeoForge ModDevGradle](https://github.com/neoforged/ModDevGradle) setup. Use
`./gradlew runClient` or `./gradlew runServer` for a dev instance.
