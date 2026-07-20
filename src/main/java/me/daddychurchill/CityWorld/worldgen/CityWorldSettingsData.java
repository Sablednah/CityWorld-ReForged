package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.Locale;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldSettings.SubSurfaceStyle;
import me.daddychurchill.CityWorld.Plugins.TreeProvider.TreeStyle;
import me.daddychurchill.CityWorld.Support.Odds;

/**
 * The per-world CityWorld settings, as a codec'd datapack-registry element.
 *
 * <p><b>This is the P7 answer to "per-world config in a per-instance mod" (PORTING.md top risk
 * #4).</b> Upstream {@code CityWorldSettings} parsed ~100 knobs out of each world's YAML; NeoForge's
 * {@code ModConfigSpec} is per-<em>instance</em>, so it cannot express "this world crazy, that world
 * plain". A datapack registry can: entries live at
 * {@code data/<namespace>/cityworld/world_settings/<name>.json}, they are pure data, and a server op
 * can ship a different set per save. The generator references one by holder (see
 * {@code CityWorldChunkGenerator}); different dimensions may point at different profiles.
 *
 * <p>Every field is {@code optionalFieldOf(default)}, so a JSON only lists what it overrides and a
 * bare <code>{}</code> is a full upstream-default world. The groups mirror upstream's YAML sections
 * — and keep each {@link RecordCodecBuilder} group under its 16-field ceiling. {@link #DEFAULT} is
 * the compiled upstream default, used when a generator carries no settings holder (existing worlds,
 * probes, tests).
 *
 * <p>The naming and mob lists (upstream's {@code VillagerGivenNames}, {@code Entities_For_*}, …) ride
 * in the {@link Naming} and {@link Mobs} groups. Each list defaults to <em>empty</em>, which means
 * "keep the compiled defaults" — the hundreds of hardcoded names in {@code OdonymProvider_Normal} and
 * the weighted bags in {@code SpawnProvider} — exactly upstream's "take the list if configured, else
 * keep the hardcoded one" fallback. So a server op writes only the lists they want to personalise
 * ("Christine Johnson on Elm Street" becomes <em>your</em> names), and they're kept out of the
 * bundled {@code default.json} to keep that readable.
 *
 * <p>{@link me.daddychurchill.CityWorld.CityWorldSettings#applyData} copies these onto its runtime
 * fields <em>before</em> the world-style validation and range-flag derivation, so a style's
 * "THIS MUST BE SET" invariants and the per-dimension {@code decayed} override still win — exactly
 * the ordering upstream used.
 */
public record CityWorldSettingsData(
        Features features,
        Terrain terrain,
        Spawns spawns,
        Treasures treasures,
        World world,
        Radius radius,
        Naming naming,
        Mobs mobs) {

    /** 1875000 chunks — the modern world-format radius ceiling (30,000,000 blocks / 16). */
    public static final int MAX_RADIUS = 30000000 / 16;

    public static final CityWorldSettingsData DEFAULT = new CityWorldSettingsData(
            Features.DEFAULT, Terrain.DEFAULT, Spawns.DEFAULT, Treasures.DEFAULT, World.DEFAULT, Radius.DEFAULT,
            Naming.DEFAULT, Mobs.DEFAULT);

    public static final Codec<CityWorldSettingsData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Features.CODEC.optionalFieldOf("features", Features.DEFAULT).forGetter(CityWorldSettingsData::features),
            Terrain.CODEC.optionalFieldOf("terrain", Terrain.DEFAULT).forGetter(CityWorldSettingsData::terrain),
            Spawns.CODEC.optionalFieldOf("spawns", Spawns.DEFAULT).forGetter(CityWorldSettingsData::spawns),
            Treasures.CODEC.optionalFieldOf("treasures", Treasures.DEFAULT).forGetter(CityWorldSettingsData::treasures),
            World.CODEC.optionalFieldOf("world", World.DEFAULT).forGetter(CityWorldSettingsData::world),
            Radius.CODEC.optionalFieldOf("radius", Radius.DEFAULT).forGetter(CityWorldSettingsData::radius),
            Naming.CODEC.optionalFieldOf("naming", Naming.DEFAULT).forGetter(CityWorldSettingsData::naming),
            Mobs.CODEC.optionalFieldOf("mobs", Mobs.DEFAULT).forGetter(CityWorldSettingsData::mobs)
    ).apply(i, CityWorldSettingsData::new));

    // --- what gets built ----------------------------------------------------------------------

    /** Which structures the planner is allowed to place. Mirrors upstream's first YAML block. */
    public record Features(
            boolean includeRoads,
            boolean includeRoundabouts,
            boolean includeSewers,
            boolean includeCisterns,
            boolean includeBasements,
            boolean includeMines,
            boolean includeBunkers,
            boolean includeBuildings,
            boolean includeHouses,
            boolean includeFarms,
            boolean includeMunicipalities,
            boolean includeIndustrialSectors,
            boolean includeAirborneStructures,
            boolean includeBuildingInteriors,
            boolean includeSchematics,
            boolean includeNamedRoads) {

        public static final Features DEFAULT = new Features(
                true, true, true, true, true, true, true, true, true, true, true, true, true, true, false, true);

        public static final Codec<Features> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("includeRoads", true).forGetter(Features::includeRoads),
                Codec.BOOL.optionalFieldOf("includeRoundabouts", true).forGetter(Features::includeRoundabouts),
                Codec.BOOL.optionalFieldOf("includeSewers", true).forGetter(Features::includeSewers),
                Codec.BOOL.optionalFieldOf("includeCisterns", true).forGetter(Features::includeCisterns),
                Codec.BOOL.optionalFieldOf("includeBasements", true).forGetter(Features::includeBasements),
                Codec.BOOL.optionalFieldOf("includeMines", true).forGetter(Features::includeMines),
                Codec.BOOL.optionalFieldOf("includeBunkers", true).forGetter(Features::includeBunkers),
                Codec.BOOL.optionalFieldOf("includeBuildings", true).forGetter(Features::includeBuildings),
                Codec.BOOL.optionalFieldOf("includeHouses", true).forGetter(Features::includeHouses),
                Codec.BOOL.optionalFieldOf("includeFarms", true).forGetter(Features::includeFarms),
                Codec.BOOL.optionalFieldOf("includeMunicipalities", true).forGetter(Features::includeMunicipalities),
                Codec.BOOL.optionalFieldOf("includeIndustrialSectors", true).forGetter(Features::includeIndustrialSectors),
                Codec.BOOL.optionalFieldOf("includeAirborneStructures", true).forGetter(Features::includeAirborneStructures),
                Codec.BOOL.optionalFieldOf("includeBuildingInteriors", true).forGetter(Features::includeBuildingInteriors),
                Codec.BOOL.optionalFieldOf("includeSchematics", false).forGetter(Features::includeSchematics),
                Codec.BOOL.optionalFieldOf("includeNamedRoads", true).forGetter(Features::includeNamedRoads)
        ).apply(i, Features::new));
    }

    // --- the land itself, its fluids and decay -------------------------------------------------

    public record Terrain(
            boolean includeCaves,
            boolean includeLavaFields,
            boolean includeSeas,
            boolean includeMountains,
            boolean includeOres,
            boolean includeBones,
            boolean includeFires,
            boolean includeAbovegroundFluids,
            boolean includeUndergroundFluids,
            boolean includeWorkingLights,
            boolean includeDecayedRoads,
            boolean includeDecayedBuildings,
            boolean includeDecayedNature,
            double oddsOfPristineBuilding) {

        /** Default pristine chance — tiny, so a spared building is a rare find even in a ruined world. */
        public static final double DEFAULT_ODDS_OF_PRISTINE = 0.0001; // 0.01%

        public static final Terrain DEFAULT = new Terrain(
                true, true, true, true, true, true, true, true, true, true, false, false, false,
                DEFAULT_ODDS_OF_PRISTINE);

        public static final Codec<Terrain> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("includeCaves", true).forGetter(Terrain::includeCaves),
                Codec.BOOL.optionalFieldOf("includeLavaFields", true).forGetter(Terrain::includeLavaFields),
                Codec.BOOL.optionalFieldOf("includeSeas", true).forGetter(Terrain::includeSeas),
                Codec.BOOL.optionalFieldOf("includeMountains", true).forGetter(Terrain::includeMountains),
                Codec.BOOL.optionalFieldOf("includeOres", true).forGetter(Terrain::includeOres),
                Codec.BOOL.optionalFieldOf("includeBones", true).forGetter(Terrain::includeBones),
                Codec.BOOL.optionalFieldOf("includeFires", true).forGetter(Terrain::includeFires),
                Codec.BOOL.optionalFieldOf("includeAbovegroundFluids", true).forGetter(Terrain::includeAbovegroundFluids),
                Codec.BOOL.optionalFieldOf("includeUndergroundFluids", true).forGetter(Terrain::includeUndergroundFluids),
                Codec.BOOL.optionalFieldOf("includeWorkingLights", true).forGetter(Terrain::includeWorkingLights),
                Codec.BOOL.optionalFieldOf("includeDecayedRoads", false).forGetter(Terrain::includeDecayedRoads),
                Codec.BOOL.optionalFieldOf("includeDecayedBuildings", false).forGetter(Terrain::includeDecayedBuildings),
                Codec.BOOL.optionalFieldOf("includeDecayedNature", false).forGetter(Terrain::includeDecayedNature),
                Codec.DOUBLE.optionalFieldOf("oddsOfPristineBuilding", DEFAULT_ODDS_OF_PRISTINE).forGetter(Terrain::oddsOfPristineBuilding)
        ).apply(i, Terrain::new));
    }

    // --- who turns up, and how often -----------------------------------------------------------

    public record Spawns(
            double spawnBeings,
            double spawnBaddies,
            double spawnAnimals,
            double spawnVagrants,
            boolean nameVillagers,
            boolean showVillagersNames) {

        public static final Spawns DEFAULT = new Spawns(
                Odds.oddsLikely, Odds.oddsPrettyUnlikely, Odds.oddsVeryLikely, Odds.oddsSomewhatUnlikely, true, true);

        public static final Codec<Spawns> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("spawnBeings", Odds.oddsLikely).forGetter(Spawns::spawnBeings),
                Codec.DOUBLE.optionalFieldOf("spawnBaddies", Odds.oddsPrettyUnlikely).forGetter(Spawns::spawnBaddies),
                Codec.DOUBLE.optionalFieldOf("spawnAnimals", Odds.oddsVeryLikely).forGetter(Spawns::spawnAnimals),
                Codec.DOUBLE.optionalFieldOf("spawnVagrants", Odds.oddsSomewhatUnlikely).forGetter(Spawns::spawnVagrants),
                Codec.BOOL.optionalFieldOf("nameVillagers", true).forGetter(Spawns::nameVillagers),
                Codec.BOOL.optionalFieldOf("showVillagersNames", true).forGetter(Spawns::showVillagersNames)
        ).apply(i, Spawns::new));
    }

    // --- chests and spawners -------------------------------------------------------------------

    public record Treasures(
            boolean treasuresInMines,
            boolean spawnersInMines,
            boolean treasuresInBunkers,
            boolean spawnersInBunkers,
            boolean treasuresInSewers,
            boolean spawnersInSewers,
            boolean treasuresInBuildings,
            double oddsOfTreasureInMines,
            double oddsOfTreasureInBunkers,
            double oddsOfTreasureInSewers,
            double oddsOfTreasureInBuildings,
            double oddsOfAlcoveInMines) {

        public static final Treasures DEFAULT = new Treasures(
                true, true, true, true, true, true, true,
                Odds.oddsLikely, Odds.oddsLikely, Odds.oddsLikely, Odds.oddsLikely, Odds.oddsLikely);

        public static final Codec<Treasures> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("treasuresInMines", true).forGetter(Treasures::treasuresInMines),
                Codec.BOOL.optionalFieldOf("spawnersInMines", true).forGetter(Treasures::spawnersInMines),
                Codec.BOOL.optionalFieldOf("treasuresInBunkers", true).forGetter(Treasures::treasuresInBunkers),
                Codec.BOOL.optionalFieldOf("spawnersInBunkers", true).forGetter(Treasures::spawnersInBunkers),
                Codec.BOOL.optionalFieldOf("treasuresInSewers", true).forGetter(Treasures::treasuresInSewers),
                Codec.BOOL.optionalFieldOf("spawnersInSewers", true).forGetter(Treasures::spawnersInSewers),
                Codec.BOOL.optionalFieldOf("treasuresInBuildings", true).forGetter(Treasures::treasuresInBuildings),
                Codec.DOUBLE.optionalFieldOf("oddsOfTreasureInMines", Odds.oddsLikely).forGetter(Treasures::oddsOfTreasureInMines),
                Codec.DOUBLE.optionalFieldOf("oddsOfTreasureInBunkers", Odds.oddsLikely).forGetter(Treasures::oddsOfTreasureInBunkers),
                Codec.DOUBLE.optionalFieldOf("oddsOfTreasureInSewers", Odds.oddsLikely).forGetter(Treasures::oddsOfTreasureInSewers),
                Codec.DOUBLE.optionalFieldOf("oddsOfTreasureInBuildings", Odds.oddsLikely).forGetter(Treasures::oddsOfTreasureInBuildings),
                Codec.DOUBLE.optionalFieldOf("oddsOfAlcoveInMines", Odds.oddsLikely).forGetter(Treasures::oddsOfAlcoveInMines)
        ).apply(i, Treasures::new));
    }

    // --- trees, ground cover, floating subsurface, ruralness, building height -------------------

    /**
     * @param maxBuildingFloors the tallest a building may rise, in floors above street level (4
     *        blocks each). 20 is CLASSIC's 1.14 default (~Y144); MODERN ships a taller default so
     *        highrises use the -64..319 headroom. The generator raises the building Y ceiling to fit
     *        this, clamped to the world's actual ceiling.
     */
    public record World(
            TreeStyle treeStyle,
            double spawnTrees,
            SubSurfaceStyle subSurfaceStyle,
            double ruralnessLevel,
            int maxBuildingFloors) {

        /** CLASSIC's default building-floor cap — the old hardcoded {@code absoluteAbsoluteMaximumFloorsAbove}. */
        public static final int DEFAULT_MAX_BUILDING_FLOORS = 20;

        public static final World DEFAULT = new World(TreeStyle.NORMAL, Odds.oddsLikely, SubSurfaceStyle.LAND, 0.0,
                DEFAULT_MAX_BUILDING_FLOORS);

        private static final Codec<TreeStyle> TREE_STYLE_CODEC = Codec.STRING.xmap(
                s -> parseEnum(TreeStyle.class, s, TreeStyle.NORMAL), TreeStyle::name);
        private static final Codec<SubSurfaceStyle> SUBSURFACE_CODEC = Codec.STRING.xmap(
                s -> parseEnum(SubSurfaceStyle.class, s, SubSurfaceStyle.LAND), SubSurfaceStyle::name);

        public static final Codec<World> CODEC = RecordCodecBuilder.create(i -> i.group(
                TREE_STYLE_CODEC.optionalFieldOf("treeStyle", TreeStyle.NORMAL).forGetter(World::treeStyle),
                Codec.DOUBLE.optionalFieldOf("spawnTrees", Odds.oddsLikely).forGetter(World::spawnTrees),
                SUBSURFACE_CODEC.optionalFieldOf("subSurfaceStyle", SubSurfaceStyle.LAND).forGetter(World::subSurfaceStyle),
                Codec.DOUBLE.optionalFieldOf("ruralnessLevel", 0.0).forGetter(World::ruralnessLevel),
                Codec.INT.optionalFieldOf("maxBuildingFloors", DEFAULT_MAX_BUILDING_FLOORS).forGetter(World::maxBuildingFloors)
        ).apply(i, World::new));
    }

    // --- where cities/roads/constructs may appear (upstream's [radius]) -------------------------

    public record Radius(
            int centerPointOfChunkRadiusX,
            int centerPointOfChunkRadiusZ,
            int constructChunkRadius,
            int roadChunkRadius,
            int cityChunkRadius,
            boolean buildOutsideRadius,
            int minInbetweenChunkDistanceOfCities) {

        public static final Radius DEFAULT = new Radius(0, 0, MAX_RADIUS, MAX_RADIUS, MAX_RADIUS, false, 0);

        public static final Codec<Radius> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("centerPointOfChunkRadiusX", 0).forGetter(Radius::centerPointOfChunkRadiusX),
                Codec.INT.optionalFieldOf("centerPointOfChunkRadiusZ", 0).forGetter(Radius::centerPointOfChunkRadiusZ),
                Codec.INT.optionalFieldOf("constructChunkRadius", MAX_RADIUS).forGetter(Radius::constructChunkRadius),
                Codec.INT.optionalFieldOf("roadChunkRadius", MAX_RADIUS).forGetter(Radius::roadChunkRadius),
                Codec.INT.optionalFieldOf("cityChunkRadius", MAX_RADIUS).forGetter(Radius::cityChunkRadius),
                Codec.BOOL.optionalFieldOf("buildOutsideRadius", false).forGetter(Radius::buildOutsideRadius),
                Codec.INT.optionalFieldOf("minInbetweenChunkDistanceOfCities", 0).forGetter(Radius::minInbetweenChunkDistanceOfCities)
        ).apply(i, Radius::new));
    }

    // --- word lists: villager and street names (upstream's OdonymProvider tags) ----------------

    /**
     * The nine word lists {@code OdonymProvider_Normal} draws street and villager names from. An
     * empty list (the default) means "keep the compiled hundreds". A non-empty one <em>replaces</em>
     * that list — unless {@link #append} is set, in which case it is <em>added</em> to the compiled
     * defaults instead. Names are joined by the same generator logic either way, so a short custom
     * list still produces varied street names.
     */
    public record Naming(
            List<String> villagerGivenNames,
            List<String> villagerSurnames,
            List<String> streetTerms,
            List<String> streetPrefixes,
            List<String> streetStarts,
            List<String> streetEnds,
            List<String> streetSuffixes,
            List<String> fossilPrefixes,
            List<String> fossilSuffixes,
            boolean append) {

        public static final Naming DEFAULT = new Naming(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), false);

        private static final Codec<List<String>> LIST = Codec.STRING.listOf();

        public static final Codec<Naming> CODEC = RecordCodecBuilder.create(i -> i.group(
                LIST.optionalFieldOf("villagerGivenNames", List.of()).forGetter(Naming::villagerGivenNames),
                LIST.optionalFieldOf("villagerSurnames", List.of()).forGetter(Naming::villagerSurnames),
                LIST.optionalFieldOf("streetTerms", List.of()).forGetter(Naming::streetTerms),
                LIST.optionalFieldOf("streetPrefixes", List.of()).forGetter(Naming::streetPrefixes),
                LIST.optionalFieldOf("streetStarts", List.of()).forGetter(Naming::streetStarts),
                LIST.optionalFieldOf("streetEnds", List.of()).forGetter(Naming::streetEnds),
                LIST.optionalFieldOf("streetSuffixes", List.of()).forGetter(Naming::streetSuffixes),
                LIST.optionalFieldOf("fossilPrefixes", List.of()).forGetter(Naming::fossilPrefixes),
                LIST.optionalFieldOf("fossilSuffixes", List.of()).forGetter(Naming::fossilSuffixes),
                Codec.BOOL.optionalFieldOf("append", false).forGetter(Naming::append)
        ).apply(i, Naming::new));
    }

    // --- weighted mob bags (upstream's Entities_For_* tags) ------------------------------------

    /**
     * The ten weighted entity bags {@code SpawnProvider} draws from — each a list of entity ids
     * (e.g. {@code "minecraft:zombie"}), <b>weighted by repetition</b> exactly as upstream's lists
     * are (list {@code CHICKEN} six times for "mostly chickens"). Empty (the default) keeps the
     * compiled bag; a non-empty list <em>replaces</em> it, unless {@link #append} is set, in which
     * case it is <em>added</em> to the compiled bag (repetition still counts as weight). Unknown ids
     * are logged once and skipped when {@code CityWorldSettings} resolves them, never guessed.
     */
    public record Mobs(
            List<String> goodies,
            List<String> baddies,
            List<String> animals,
            List<String> seaAnimals,
            List<String> vagrants,
            List<String> sewers,
            List<String> mine,
            List<String> bunker,
            List<String> waterPit,
            List<String> lavaPit,
            boolean append) {

        public static final Mobs DEFAULT = new Mobs(List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), false);

        private static final Codec<List<String>> LIST = Codec.STRING.listOf();

        public static final Codec<Mobs> CODEC = RecordCodecBuilder.create(i -> i.group(
                LIST.optionalFieldOf("goodies", List.of()).forGetter(Mobs::goodies),
                LIST.optionalFieldOf("baddies", List.of()).forGetter(Mobs::baddies),
                LIST.optionalFieldOf("animals", List.of()).forGetter(Mobs::animals),
                LIST.optionalFieldOf("seaAnimals", List.of()).forGetter(Mobs::seaAnimals),
                LIST.optionalFieldOf("vagrants", List.of()).forGetter(Mobs::vagrants),
                LIST.optionalFieldOf("sewers", List.of()).forGetter(Mobs::sewers),
                LIST.optionalFieldOf("mine", List.of()).forGetter(Mobs::mine),
                LIST.optionalFieldOf("bunker", List.of()).forGetter(Mobs::bunker),
                LIST.optionalFieldOf("waterPit", List.of()).forGetter(Mobs::waterPit),
                LIST.optionalFieldOf("lavaPit", List.of()).forGetter(Mobs::lavaPit),
                Codec.BOOL.optionalFieldOf("append", false).forGetter(Mobs::append)
        ).apply(i, Mobs::new));
    }

    /**
     * Case-insensitive enum parse with a fallback — mirrors upstream's tolerant {@code validateStyle}
     * so a typo in a hand-edited datapack yields the default rather than a hard decode failure.
     */
    private static <E extends Enum<E>> E parseEnum(Class<E> type, String name, E fallback) {
        try {
            return Enum.valueOf(type, name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return fallback;
        }
    }
}
