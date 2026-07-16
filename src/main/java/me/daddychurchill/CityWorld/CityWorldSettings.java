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
 * <p>The real one arrives at P7, and it is not a straight port: CityWorld's settings are
 * <em>per-world</em>, while NeoForge's {@code ModConfigSpec} is per-instance, so this needs a
 * datapack or world-saved-data approach rather than a config file (PORTING.md, top risk #4).
 */
public class CityWorldSettings {

    // Defaults carried over verbatim from the upstream field initializers.
    public boolean includeRoads = true;
    public boolean includeBuildings = true;
    public boolean includeFarms = true;
    public boolean includeMunicipalities = true;

    /**
     * Upstream-default-on, forced off here because its lot family is not ported: industrial sectors
     * want {@code FactoryBuildingLot}/{@code WarehouseBuildingLot}/{@code StorageLot} (and
     * {@code BunkerLot} underneath them).
     *
     * <p>Using the setting for this is deliberate rather than a hack: upstream already guards that
     * arm of {@code ShapeProvider_Normal.getContext} with this flag, so switching it off makes the
     * band fall through to the next exactly as it would for a player who turned the feature off —
     * no special-casing in the ladder. Flip it back on with its lots.
     */
    public boolean includeIndustrialSectors = false;
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
    public boolean treasuresInMines = true;
    public boolean spawnersInMines = true;
    public double oddsOfTreasureInMines = Odds.oddsLikely;
    public double oddsOfAlcoveInMines = Odds.oddsLikely;
    public boolean includeSewers = true;
    public boolean treasuresInSewers = true;
    public boolean spawnersInSewers = true;
    public double oddsOfTreasureInSewers = Odds.oddsLikely;
    public boolean includeNamedRoads = true;
    public boolean includeDecayedRoads = false;
    public boolean includeDecayedBuildings = false;
    public boolean includeBuildingInteriors = true;
    public boolean includeHouses = true;
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
