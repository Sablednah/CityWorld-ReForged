# sablecraft.co.uk — what the site needs from this repo

The docs site lives at **<https://sablecraft.co.uk/cityworld-reforged/>** and is maintained by a
separate session, so this file is the handover: what the site must keep in sync with the mod, where
the source text lives, and what is currently outstanding.

## Where the site's facts come from

| Site content | Source in this repo |
|---|---|
| Requirements / versions | `CURSEFORGE.md` → "Requirements" (kept current; copy from there) |
| Feature overview | `CURSEFORGE.md` |
| Settings reference | `CURSEFORGE-CONFIGURATION.md` |
| Commands reference | `CURSEFORGE-COMMANDS.md` |
| Modded-block / palette support | `PALETTES.md` |
| Screenshots | `screengrabs/` |

**Version facts should only ever appear on the landing page.** The `/commands/`, `/settings/` and
`/styles/` child pages are deliberately version-agnostic — that is why a release does not invalidate
them, and it is worth preserving.

## Outstanding as of 5.1.0 (2026-08-17)

Reviewed the landing page and all three child pages. The child pages need **no changes**. Two items
on the landing page:

### 1. Version statements are stale — the important one

The page still says "Minecraft 1.21.11", "NeoForge 21.11.42" and "Requires Minecraft 1.21.11 and
NeoForge 21.11.42." CityWorld now ships for **three** Minecraft versions, so anyone on 26.1 or 26.2
currently reads that and concludes the mod does not support their game. Replacement:

> **Requirements**
>
> CityWorld runs on three Minecraft versions. Download the file for yours — the Minecraft version is
> in the filename.
>
> | Minecraft | NeoForge | Java |
> |---|---|---|
> | 1.21.11 | 21.11.42+ | 21 |
> | 26.1.2 | 26.1.2.95+ | 25 |
> | 26.2 | 26.2.0.59+ | 25 |
>
> A given seed builds the same city on all three — terrain, roads and districts are identical; only
> the materials shift slightly, because newer Minecraft versions bring new blocks into the building
> palettes.

The Java split is real and worth keeping: the 1.21 line runs on Java 21, the 26.x line needs Java 25.
Jars are named `cityworld-5.1.0+mc26.2.jar`, so the Minecraft version is always in the filename.

### 2. The palettes feature is missing from the whole site

Nothing on the site mentions that **a mod you install turns up in the cities on its own** — its
planks in the houses, its stone in the walls, with no patch and no compatibility pack. Palettes are
block tags, so any mod tagging its blocks conventionally (planks in `#minecraft:planks`, stone in
`#c:stones`) joins them on install, and the *odds* do not shift as the palette widens — a heavily
modded world gets more variety, not more wooden houses.

This shipped in 5.0.3 and is probably the most interesting thing on the site to a modpack author.
It would sit naturally as a short section on `/settings/`, with `PALETTES.md` as the source.

### Verified correct, no action needed

- The CurseForge and GitHub links both resolve and are right
- `/styles/` lists all 13 world styles
- Licence stated as GPL-3.0-only, correct

## Checklist for future releases

1. Update the requirements block **only if** the supported version matrix changed.
2. Add anything genuinely new and player-visible from `CHANGELOG.md`.
3. Leave the child pages alone unless a command, setting or style actually changed.
