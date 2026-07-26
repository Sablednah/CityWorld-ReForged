package me.daddychurchill.CityWorld.worldgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Drops a copy-and-edit settings example on first run, next to the schematics drop-in folder:
 * {@code config/cityworld/settings-example/}. It holds a complete, valid world datapack whose
 * {@code default.json} spells out every knob at its default, plus {@code settings-reference.txt}
 * explaining what each does and the sensible range. A server op copies the folder into
 * {@code <world>/datapacks/} and edits — the "download the defaults and see everything that can
 * change" ask (P7).
 *
 * <p>Deliberately <em>not</em> a datapack the game auto-loads: it sits under {@code config/}, so it is
 * inert until copied into a world. Written only if absent, so a hand-edited copy is never clobbered.
 */
public final class SettingsExample {

    private SettingsExample() {}

    private static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(CityWorldMod.MODID).resolve("settings-example");
    }

    /** Create the example pack + reference on first run. Best-effort: a failure only logs. */
    public static void ensureExampleFolder() {
        try {
            Path root = folder();
            if (Files.exists(root))
                return;
            SettingsDatapack.writePack(root, SettingsDatapack.DEFAULT_ENTRY, CityWorldSettingsData.DEFAULT,
                    "CityWorld settings example — copy into <world>/datapacks and edit");
            Files.writeString(root.resolve("settings-reference.txt"), REFERENCE);
            CityWorldMod.LOGGER.info("CityWorld: wrote settings example to {}", root);
        } catch (IOException | RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld: could not write settings example", e);
        }
    }

    // Kept in-code (not a resource) so it ships in the jar with no extra packaging, mirroring
    // SchematicLibrary's README. Wraps at ~100 cols to read well in a plain editor.
    private static final String REFERENCE = """
            CityWorld — per-world settings reference
            ========================================

            HOW THIS WORKS
            --------------
            CityWorld's settings are per-WORLD, delivered as a datapack (NeoForge's own config is
            per-instance, which can't say "this world crazy, that world plain"). This folder is a
            ready-to-use example pack:

              settings-example/
                pack.mcmeta
                data/cityworld/cityworld/world_settings/default.json
                settings-reference.txt   (this file)

            To use it:
              1. Copy the whole folder into your world:  <world>/datapacks/my-cityworld-settings/
              2. Edit default.json.
              3. Start (or /reload — but see "WHEN CHANGES APPLY") the world.

            "default.json" is named to override the built-in cityworld:default, which every CityWorld
            world preset and the /cityworld dimension reference. So editing it changes that world. To
            keep several named profiles instead, rename the file (e.g. ruined.json) and point a
            dimension's generator at it with  "settings": "cityworld:ruined".

            Every field is OPTIONAL: list only what you want to change; anything omitted keeps its
            default. A file of just {} is a stock world.

            WHEN CHANGES APPLY
            ------------------
            Settings are read when the world LOADS, and only NEW chunks are affected — existing chunks
            never regenerate. Spawn odds, names, treasure odds and decay bite as you explore into fresh
            land; terrain-shaping knobs (seas, mountains, caves, radius) want a brand-new world to be
            seen properly. Tip: trial settings in single-player via the Customize button, then export
            the world you like with  /cityexport <name>  to get a matching datapack.

            VALUE TYPES
            -----------
            true/false      a toggle.
            0.0 .. 1.0      an "odds" chance (0 = never, 0.5 = half the time, 1.0 = always).
            whole number    a count or a chunk distance (16 blocks per chunk).
            "NAME"          a named choice; the allowed names are listed with each such setting.

            ============================================================================
            features  — what the planner is allowed to build
            ============================================================================
            includeRoads              true   Road network. Off = no streets (and no sewers).
            includeRoundabouts        true   Roundabouts at some intersections.
            includeSewers             true   Sewers beneath the roads.
            includeCisterns           true   Water cisterns under some buildings.
            includeBasements          true   Building basements.
            includeMines              true   Abandoned mine networks underground.
            includeBunkers            true   Buried bunkers under midlands/highlands and factories.
            includeBuildings          true   Buildings at all. Off = roads/nature only.
            includeHouses             true   Houses in neighborhoods (a subset of buildings).
            includeFarms              true   Farm districts.
            includeMunicipalities     true   Civic district (town halls, libraries, museums, ...).
            includeIndustrialSectors  true   Factories, warehouses, storage yards.
            includeAirborneStructures true   Balloons/blimps over the fields.
            includeBuildingInteriors  true   Furnish building interiors. Off = empty shells (faster).
            includeSchematics         false  Drop the bundled classic building schematics into cities.
            includeNamedRoads         true   Street-name signs.

            ============================================================================
            terrain  — the land, its fluids, and decay
            ============================================================================
            includeCaves              true   Carve caves.
            includeLavaFields         true   Underground lava fields.
            includeSeas               true   Oceans/lakes. Off = a dry, land-only world.
            includeMountains          true   Mountainous terrain. Off = flatter.
            includeOres               true   Ore deposits in the strata.
            includeBones              true   Fossils/bone deposits.
            includeFires              true   Lit campfires/fire pits, and burning demolition debris.
            includeAbovegroundFluids  true   Surface water/lava placement.
            includeUndergroundFluids  true   Underground water/lava placement.
            includeWorkingLights      true   Lit lamps/lights in builds. Off = a darker world.
            includeDecayedRoads       false  Break roads up with rubble.
            includeDecayedBuildings   false  Chew ruin-holes into buildings — the "apocalypse" switch.
            includeDecayedNature      false  Drain the seas + desert the world (whole-world ruin mood).
                                             Leave false for ruins on a normal wet, green world.
            oddsOfPristineBuilding    0.0001 When decay is on, the chance a building/schematic is spared
                                             and stays intact (a rare find in a ruined world). 0.0001 =
                                             0.01%. A schematic's .yml can override this per-building
                                             with  PristineChance: <0..1>  (and Decayable: false means
                                             never decays). Range 0.0 .. 1.0.

            ============================================================================
            overgrowth  — nature reclaiming the built world (runs after decay)
            ============================================================================
            enabled     false  Drape buildings/roads in moss, vines, leaf litter, azalea and small
                               reclaim trees, + dripstone in mines and basements. Works with or
                               without decay.
            intensity   1.0    Density x-multiplier: 1.0 = default, 2-3 = heavy (more/denser vines
                               and plants). Vine-string length varies 1/4..full of each wall on its own.
            capVines    false  Finish each outer wall-vine string with a glow lichen so live vine
                               growth can't lengthen it over time (and it reads as a lit tip).

            ============================================================================
            spawns  — who turns up, and how often   (odds 0.0 .. 1.0)
            ============================================================================
            spawnBeings         0.5      Villagers/witches appearing in populated spots.
            spawnBaddies        0.0476   Hostile mobs at the surface. Raise for a rougher world.
            spawnAnimals        0.6667   Farm/wild animals.
            spawnVagrants       0.2      Stray animals/people wandering the streets.
            nameVillagers       true     Give villagers generated names (see [naming]).
            showVillagersNames  true     Show those names as floating nametags.

            ============================================================================
            treasures  — chests and spawners   (flags true/false, odds 0.0 .. 1.0)
            ============================================================================
            treasuresInMines          true   Loot chests in mines.
            spawnersInMines           true   Mob spawners in mines.
            treasuresInBunkers        true   Loot chests in bunkers.
            spawnersInBunkers         true   Mob spawners in bunkers.
            treasuresInSewers         true   Loot chests in sewers.
            spawnersInSewers          true   Mob spawners in sewers.
            treasuresInBuildings      true   Loot chests in buildings.
            oddsOfTreasureInMines     0.5    Chance a given mine spot gets a chest.
            oddsOfTreasureInBunkers   0.5    Chance a given bunker spot gets a chest.
            oddsOfTreasureInSewers    0.5    Chance a given sewer spot gets a chest.
            oddsOfTreasureInBuildings 0.5    Chance a given building spot gets a chest.
            oddsOfAlcoveInMines       0.5    Chance a mine tunnel opens into a side alcove.

            ============================================================================
            world  — trees, ground cover, floating subsurface, ruralness
            ============================================================================
            treeStyle        "NORMAL"   Tree family. One of: NORMAL, SPOOKY, CRYSTAL.
            spawnTrees       0.5        How densely trees grow (odds 0.0 .. 1.0).
            subSurfaceStyle  "LAND"     What fills space under a FLOATING world's land. One of:
                                        NONE, LAND, CLOUD, LAVA. Only the FLOATING style reads this.
            ruralnessLevel   0.0        Skews the world more rural (more nature, fewer cities).
                                        0.0 = normal; up toward 1.0 = increasingly rural.
            maxBuildingFloors 20        Tallest a building may rise, in floors (4 blocks each) above
                                        street level. 20 = the classic 1.8 look (~24 blocks over a
                                        3-storey base); the MODERN style ships taller. The build Y
                                        ceiling follows this, clamped to the world's ceiling (319), so
                                        very high values give skyscrapers that use the full headroom.
                                        Sensible range 8..60.

            ============================================================================
            radius  — where cities may appear   (distances in CHUNKS; 16 blocks each)
            ============================================================================
            Leave these at their defaults for "cities everywhere". They let you confine cities to a
            region — the SPARSE world style uses them. The default 1875000 is effectively "no limit"
            (the world border in chunks).
            centerPointOfChunkRadiusX          0         Centre the radius here (X, in chunks).
            centerPointOfChunkRadiusZ          0         Centre the radius here (Z, in chunks).
            constructChunkRadius               1875000   Max distance for quarries/gravel constructs.
            roadChunkRadius                    1875000   Max distance for the road network.
            cityChunkRadius                    1875000   Max distance for cities/buildings/farms.
            buildOutsideRadius                 false     true = build the ring OUTSIDE the radius
                                                         instead of the disc inside it.
            minInbetweenChunkDistanceOfCities  0         >0 tiles cities on a grid this many chunks
                                                         apart (SPARSE uses 100).

            ============================================================================
            naming  — your own villager & street names   (lists of text)
            ============================================================================
            Each list is EMPTY by default, which means "keep CityWorld's built-in hundreds". List your
            own to REPLACE a list entirely. Names are still assembled by the same generator, so even a
            short custom list yields varied streets. Example — make every villager a "<given> <sur>":

              "naming": {
                "villagerGivenNames": ["Ada", "Bob", "Cleo"],
                "villagerSurnames":   ["Vance", "Okafor"]
              }

            Set "append": true to ADD your names to the built-in lists instead of replacing them —
            handy for sprinkling a few friends' names in among the stock ones:

              "naming": {
                "append": true,
                "villagerGivenNames": ["Sablednah", "Darren"]
              }

            villagerGivenNames   Given names for villagers.
            villagerSurnames     Surnames for villagers.
            streetTerms          Cardinal/central words: [declaration, N, Main, S, W, Central, E].
            streetPrefixes       Optional street prefixes ("Old", "New", "Fort", ...).
            streetStarts         Start syllables of invented street names ("Elm", "Oak", ...).
            streetEnds           End syllables ("wood", "ville", "field", ...).
            streetSuffixes       Road types ("Street", "Avenue", "Boulevard", ...).
            fossilPrefixes       Start of museum fossil names ("Tyranno", "Dino", ...).
            fossilSuffixes       End of fossil names ("saurus", "raptor", ...).

            ============================================================================
            mobs  — your own creature lists   (lists of entity ids, WEIGHTED BY REPETITION)
            ============================================================================
            Each list is EMPTY by default = "keep the built-in bag". List entity ids to REPLACE a bag.
            Repetition is weight: listing minecraft:chicken six times and minecraft:wolf once means
            "mostly chickens". Unknown ids are logged and skipped, not guessed. Example:

              "mobs": {
                "sewers": ["minecraft:zombie", "minecraft:zombie", "minecraft:cave_spider"]
              }

            Set "append": true to ADD your entries to the built-in bags instead of replacing them:

              "mobs": {
                "append": true,
                "sewers": ["minecraft:cave_spider", "minecraft:cave_spider"]
              }

            goodies      Friendly beings in populated spots (villagers, ...).
            baddies      Surface hostiles.
            animals      Farm/wild animals.
            seaAnimals   Fish and sea life.
            vagrants     Wandering strays in the streets.
            sewers       What lurks in the sewers.
            mine         What lurks in the mines.
            bunker       What is sealed in the bunkers.
            waterPit     What is dropped in water pits/cisterns.
            lavaPit      What is dropped in lava pits.
            """;
}
