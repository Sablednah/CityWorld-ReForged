package me.daddychurchill.CityWorld;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.Plugins.TreeProvider;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.Odds;

/**
 * Stub of the original {@code CityWorldSettings} (961 lines).
 *
 * <p><b>Wave 1 placeholder.</b> The real class parses per-world YAML and carries ~100 knobs. Only
 * the flags the {@code ShapeProvider} family actually branches on are here, each at its upstream
 * default, so terrain generates exactly as an unconfigured CityWorld world would.
 *
 * <p>The full port arrives at P7, and it is not straight: CityWorld's settings are
 * <em>per-world</em>, while NeoForge's {@code ModConfigSpec} is per-instance, so it needs a
 * datapack or world-saved-data approach rather than a config file (PORTING.md, top risk #4). A first
 * slice is wired ahead of that, though — {@link CityWorldConfig} exposes the decay/apocalypse
 * toggles as a per-instance config, overlaid onto the defaults in the constructor below.
 */
public class CityWorldSettings {

    // Defaults carried over verbatim from the upstream field initializers.
    public boolean includeRoads = true;
    public boolean includeBuildings = true;
    public boolean includeFarms = true;
    public boolean includeMunicipalities = true;

    /** Back on at upstream's default now that the factory/warehouse/storage family is ported. */
    public boolean includeIndustrialSectors = true;
    public boolean includeCaves = true;
    public boolean includeLavaFields = true;
    public boolean includeSeas = true;
    public boolean includeMountains = true;
    public boolean includeAbovegroundFluids = true;
    public boolean includeUndergroundFluids = true;
    public boolean includeDecayedNature = false;
    public boolean includeMines = true;
    public boolean includeBones = true;
    public boolean includeOres = true;
    public boolean includeWorkingLights = true;
    /** Lit campfires and fire pits. */
    public boolean includeFires = true;
    public boolean treasuresInMines = true;
    public boolean spawnersInMines = true;
    public double oddsOfTreasureInMines = Odds.oddsLikely;
    public boolean treasuresInBunkers = true;
    public boolean spawnersInBunkers = true;
    public double oddsOfTreasureInBunkers = Odds.oddsLikely;
    public double oddsOfAlcoveInMines = Odds.oddsLikely;
    public boolean includeSewers = true;
    public boolean treasuresInSewers = true;
    public boolean spawnersInSewers = true;
    public double oddsOfTreasureInSewers = Odds.oddsLikely;
    public boolean includeNamedRoads = true;
    public boolean includeDecayedRoads = false;
    public boolean includeDecayedBuildings = false;

    /**
     * Whether the bundled classic schematics (the old zarp catalog) may be dropped into generated
     * cities. Upstream only placed schematics when a WorldEdit-loaded folder existed, so it was
     * effectively off for most players; here the catalog always ships, so this defaults off and is
     * an opt-in. Independent of {@code /cityschem}, which pastes on demand regardless.
     */
    public boolean includeSchematics = false;

    /** Who turns up, and how often. {@code SpawnProvider} branches on all of these. */
    public double spawnBeings = Odds.oddsLikely;
    public double spawnBaddies = Odds.oddsPrettyUnlikely;
    public double spawnAnimals = Odds.oddsVeryLikely;
    public double spawnVagrants = Odds.oddsSomewhatUnlikely;
    public boolean nameVillagers = true;
    public boolean showVillagersNames = true;
    public boolean includeBuildingInteriors = true;
    public boolean includeHouses = true;
    /** Buried bunkers under the midlands and highlands, planned by NatureContext. */
    public boolean includeBunkers = true;
    /** Balloons and blimps over the fields. The lots that carry them are P5/P8. */
    public boolean includeAirborneStructures = true;

    /** Which family of trees a world grows. Only NORMAL is ported; SPOOKY/CRYSTAL are P8 styles. */
    public TreeProvider.TreeStyle treeStyle = TreeProvider.TreeStyle.NORMAL;
    public double spawnTrees = Odds.oddsLikely;
    /** Set by the alien/nether styles; makes ground cover sparser. */
    public boolean darkEnvironment = false;
    public boolean includeBasements = true;
    public boolean includeCisterns = true;
    public boolean treasuresInBuildings = true;
    public double oddsOfTreasureInBuildings = Odds.oddsLikely;

    /** How rural a world skews; folded into {@code PlatMap.getNaturePercent}. */
    public double ruralnessLevel = 0.0;

    /** Back on at upstream's default now that {@code RoundaboutCenterLot} is ported (wave 2b). */
    public boolean includeRoundabouts = true;

    /**
     * What fills the space under a FLOATING world's floating land. Upstream defined this on
     * {@code SurfaceProvider_Floating}; kept here with the settings it belongs to. Only the FLOATING
     * shape/surface providers read it (P8 step 4); every other style just sets it to {@code NONE} in
     * {@link #validateSettingsAgainstWorldStyle}.
     */
    public enum SubSurfaceStyle {
        NONE, LAND, CLOUD, LAVA
    }

    public SubSurfaceStyle subSurfaceStyle = SubSurfaceStyle.LAND;

    // --- city-placement radii (upstream's [radius] settings) -----------------------------------
    // The SPARSE style is the only thing that moves these off their "everywhere" defaults so far;
    // per-world YAML control over them is P7. inCityRange/inRoadRange/inConstructRange below gate
    // where cities, roads and constructs may appear.

    /** 1875000 — the chunk-radius ceiling for the modern world format (30,000,000 blocks / 16). */
    private static final int maxRadius = 30000000 / AbstractBlocks.sectionBlockWidth;

    private int centerPointOfChunkRadiusX = 0;
    private int centerPointOfChunkRadiusZ = 0;
    private int constructChunkRadius = maxRadius;
    private boolean checkConstructRange = false;
    private int roadChunkRadius = maxRadius;
    private boolean checkRoadRange = false;
    private int cityChunkRadius = maxRadius;
    private boolean checkCityRange = false;
    private boolean buildOutsideRadius = false;
    private int minInbetweenChunkDistanceOfCities = 0;
    private boolean checkMinInbetweenChunkDistanceOfCities = false;

    public CityWorldSettings() {
        this(WorldStyle.NORMAL, java.util.Optional.empty());
    }

    /**
     * @param worldStyle    the world style, which overrides a slice of the defaults below via
     *                      {@link #validateSettingsAgainstWorldStyle} (roads/mines/decay toggles and,
     *                      for {@code SPARSE}, the city-placement radii).
     * @param decayOverride a per-dimension decay override (from the generator's {@code "decayed"}
     *                      JSON field). When present it forces {@link #includeDecayedBuildings} and
     *                      {@link #includeDecayedRoads} on or off for this dimension, winning over
     *                      the config — so two same-seed dimensions can be the same city intact and
     *                      in ruins. Empty means "follow the config". Deliberately does <em>not</em>
     *                      touch {@link #includeDecayedNature} (that drains the seas and deserts the
     *                      world — a whole-world mood, kept purely config-controlled).
     */
    public CityWorldSettings(WorldStyle worldStyle, java.util.Optional<Boolean> decayOverride) {
        // Overlay the runtime config's decay slice onto the compiled defaults. Guarded on isLoaded()
        // so plan-only paths (probes, tests) that build settings before config load keep the
        // defaults instead of throwing. The rest of the ~100 knobs still come from the field
        // initializers above until the full per-world settings port (P7).
        if (CityWorldConfig.SPEC.isLoaded()) {
            includeDecayedBuildings = CityWorldConfig.INCLUDE_DECAYED_BUILDINGS.get();
            includeDecayedRoads = CityWorldConfig.INCLUDE_DECAYED_ROADS.get();
            includeDecayedNature = CityWorldConfig.INCLUDE_DECAYED_NATURE.get();
            includeFires = CityWorldConfig.INCLUDE_FIRES.get();
            includeSchematics = CityWorldConfig.INCLUDE_SCHEMATICS.get();
        }

        // A per-dimension override wins over the config for the building/road ruin.
        decayOverride.ifPresent(decayed -> {
            includeDecayedBuildings = decayed;
            includeDecayedRoads = decayed;
        });

        // Bend the defaults to the chosen world style, then derive the range gates from whatever
        // radii the style left behind. Upstream ran validate before computing the range flags (its
        // early loadSettings call at line 363), so SPARSE's minInbetween/radii are in force when the
        // flags below are set — the flags must be computed *after* the style, not before.
        validateSettingsAgainstWorldStyle(worldStyle);
        deriveRangeFlags();
    }

    /**
     * Turns the raw radius knobs into the {@code checkXxxRange} gates that {@link #inCityRange} and
     * friends consult. A verbatim port of upstream's "validate the range values" block, minus the
     * YAML clamping (the radii are code-set for now). {@code buildOutsideRadius} inverts the sense:
     * build the ring <em>outside</em> the radius rather than the disc inside it.
     */
    private void deriveRangeFlags() {
        if (buildOutsideRadius) {
            constructChunkRadius = Math.max(0, constructChunkRadius);
            roadChunkRadius = Math.max(constructChunkRadius, roadChunkRadius);
            cityChunkRadius = Math.max(roadChunkRadius, cityChunkRadius);

            checkConstructRange = constructChunkRadius > 0;
            checkRoadRange = roadChunkRadius > 0;
            checkCityRange = cityChunkRadius > 0;

            if (roadChunkRadius == Integer.MAX_VALUE) {
                includeRoads = false;
                includeSewers = false;
            }
            if (cityChunkRadius == Integer.MAX_VALUE) {
                includeCisterns = false;
                includeBasements = false;
                includeMines = false;
                includeBunkers = false;
                includeBuildings = false;
                includeHouses = false;
                includeFarms = false;
            }
        } else {
            constructChunkRadius = Math.min(Integer.MAX_VALUE, constructChunkRadius);
            roadChunkRadius = Math.min(constructChunkRadius, roadChunkRadius);
            cityChunkRadius = Math.min(roadChunkRadius, cityChunkRadius);

            checkConstructRange = constructChunkRadius < Integer.MAX_VALUE;
            checkRoadRange = roadChunkRadius < Integer.MAX_VALUE;
            checkCityRange = cityChunkRadius < Integer.MAX_VALUE;

            if (roadChunkRadius == 0) {
                includeRoads = false;
                includeSewers = false;
            }
            if (cityChunkRadius == 0) {
                includeCisterns = false;
                includeBasements = false;
                includeMines = false;
                includeBunkers = false;
                includeBuildings = false;
                includeHouses = false;
                includeFarms = false;
            }
        }
        checkMinInbetweenChunkDistanceOfCities = minInbetweenChunkDistanceOfCities > 0;
    }

    /**
     * Applies a world style's fixed overrides on top of the loaded defaults. A verbatim port of
     * upstream's method: each style forces a handful of toggles (and {@code SPARSE} the placement
     * radii). {@code NORMAL} only pins the subsurface style. Comments preserved.
     */
    private void validateSettingsAgainstWorldStyle(WorldStyle style) {
        // now get the right defaults for the world style
        // anything commented out is up for user modification
        switch (style) {
        case NORMAL:
        case METRO:
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SPARSE:
            centerPointOfChunkRadiusX = 0; // DIFFERENT
            centerPointOfChunkRadiusZ = 0; // DIFFERENT
            constructChunkRadius = 150; // DIFFERENT
            roadChunkRadius = 150; // DIFFERENT
            cityChunkRadius = 50; // DIFFERENT
            buildOutsideRadius = false; // DIFFERENT
            minInbetweenChunkDistanceOfCities = 100; // DIFFERENT

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case NATURE:
            includeRoads = false; // DIFFERENT
            includeRoundabouts = false; // DIFFERENT
            break;
        case DESTROYED:
            includeDecayedRoads = true; // DIFFERENT
            includeDecayedBuildings = true; // DIFFERENT
            includeDecayedNature = true; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case MAZE:
            includeRoads = true; // This has to be true in order for things to generate correctly
            includeRoundabouts = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT

            spawnersInMines = false; // DIFFERENT
            spawnersInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInBunkers = false; // DIFFERENT

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case ASTRAL:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeCisterns = false; // DIFFERENT
            includeBasements = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeFarms = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // THIS MUST BE SET TO FALSE
            includeAbovegroundFluids = false; // THIS MUST BE SET TO FALSE
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case FLOATING:
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = false; // DIFFERENT
            includeMountains = true; // THIS MUST BE SET TO TRUE
            includeOres = false; // DIFFERENT
            includeBones = false; // DIFFERENT
            includeFires = false; // DIFFERENT

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // DIFFERENT
            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE

            break;
        case FLOODED:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE
            includeFires = false; // DIFFERENT

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // DIFFERENT
            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            includeDecayedRoads = false; // DIFFERENT
            includeDecayedBuildings = false; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SANDDUNES:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeAbovegroundFluids = false; // THIS MUST BE SET TO FALSE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            includeDecayedNature = true; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SNOWDUNES:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        }
    }

    /**
     * The centre a chunk measures its distance against. With min-inbetween spacing on (SPARSE), each
     * chunk snaps to the nearest tiled centre so cities repeat every {@code minInbetween} chunks;
     * otherwise it is the single configured centre point. Upstream used a Bukkit {@code Vector};
     * here it is two ints and a plain Euclidean distance in {@link #distanceToCentre}.
     */
    private int centreX(int x) {
        return checkMinInbetweenChunkDistanceOfCities ? calcOrigin(x, centerPointOfChunkRadiusX)
                : centerPointOfChunkRadiusX;
    }

    private int centreZ(int z) {
        return checkMinInbetweenChunkDistanceOfCities ? calcOrigin(z, centerPointOfChunkRadiusZ)
                : centerPointOfChunkRadiusZ;
    }

    // Snaps a coordinate down to the nearest multiple of the city spacing, offset from the centre —
    // ported verbatim from upstream's calcOrigin.
    private int calcOrigin(int i, int offset) {
        i = i - offset;
        if (i >= 0) {
            i = i / minInbetweenChunkDistanceOfCities * minInbetweenChunkDistanceOfCities;
        } else {
            i = -((Math.abs(i + 1) / minInbetweenChunkDistanceOfCities * minInbetweenChunkDistanceOfCities)
                    + minInbetweenChunkDistanceOfCities);
        }
        return i + offset;
    }

    private double distanceToCentre(int x, int z) {
        double dx = x - centreX(x);
        double dz = z - centreZ(z);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether a chunk is inside (or, with {@code buildOutsideRadius}, outside) the city radius. When
     * the range is not being checked — every style but {@code SPARSE} so far — this answers
     * {@code true} everywhere, so an unconfigured world builds city wherever the terrain allows.
     */
    public boolean inCityRange(int chunkX, int chunkZ) {
        if (checkCityRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > cityChunkRadius : d <= cityChunkRadius;
        }
        return true;
    }

    /** As {@link #inCityRange}, for the road network's own radius. */
    public boolean inRoadRange(int chunkX, int chunkZ) {
        if (checkRoadRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > roadChunkRadius : d <= roadChunkRadius;
        }
        return true;
    }

    /** As {@link #inCityRange}, for the constructs (quarries, gravel pits). */
    public boolean inConstructRange(int chunkX, int chunkZ) {
        if (checkConstructRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > constructChunkRadius : d <= constructChunkRadius;
        }
        return true;
    }
}
