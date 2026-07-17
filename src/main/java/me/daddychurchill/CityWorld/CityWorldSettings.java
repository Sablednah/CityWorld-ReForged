package me.daddychurchill.CityWorld;

import me.daddychurchill.CityWorld.Plugins.TreeProvider;
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

    public CityWorldSettings() {
        this(java.util.Optional.empty());
    }

    /**
     * @param decayOverride a per-dimension decay override (from the generator's {@code "decayed"}
     *                      JSON field). When present it forces {@link #includeDecayedBuildings} and
     *                      {@link #includeDecayedRoads} on or off for this dimension, winning over
     *                      the config — so two same-seed dimensions can be the same city intact and
     *                      in ruins. Empty means "follow the config". Deliberately does <em>not</em>
     *                      touch {@link #includeDecayedNature} (that drains the seas and deserts the
     *                      world — a whole-world mood, kept purely config-controlled).
     */
    public CityWorldSettings(java.util.Optional<Boolean> decayOverride) {
        // Overlay the runtime config's decay slice onto the compiled defaults. Guarded on isLoaded()
        // so plan-only paths (probes, tests) that build settings before config load keep the
        // defaults instead of throwing. The rest of the ~100 knobs still come from the field
        // initializers above until the full per-world settings port (P7).
        if (CityWorldConfig.SPEC.isLoaded()) {
            includeDecayedBuildings = CityWorldConfig.INCLUDE_DECAYED_BUILDINGS.get();
            includeDecayedRoads = CityWorldConfig.INCLUDE_DECAYED_ROADS.get();
            includeDecayedNature = CityWorldConfig.INCLUDE_DECAYED_NATURE.get();
            includeFires = CityWorldConfig.INCLUDE_FIRES.get();
        }

        // A per-dimension override wins over the config for the building/road ruin.
        decayOverride.ifPresent(decayed -> {
            includeDecayedBuildings = decayed;
            includeDecayedRoads = decayed;
        });
    }

    /**
     * Whether a chunk is inside the configured city radius.
     *
     * <p>Upstream this is gated on {@code checkCityRange}, which defaults off — so an unconfigured
     * world answers {@code true} everywhere, which is what this returns. The real radius maths
     * (centre point, {@code buildOutsideRadius}) lands with the rest of the settings at P7.
     */
    public boolean inCityRange(int chunkX, int chunkZ) {
        return true;
    }

    /** As {@link #inCityRange}, for the road network's own radius. Gated off upstream by default. */
    public boolean inRoadRange(int chunkX, int chunkZ) {
        return true;
    }

    /** As {@link #inCityRange}, for the constructs (quarries, gravel pits). Gated off by default. */
    public boolean inConstructRange(int chunkX, int chunkZ) {
        return true;
    }
}
