package me.daddychurchill.CityWorld;

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

    /** How rural a world skews; folded into {@code PlatMap.getNaturePercent}. */
    public double ruralnessLevel = 0.0;

    /**
     * Roundabouts need {@code RoundaboutCenterLot} (or a P6 schematic), neither of which is ported.
     * Forced off until then — upstream defaults this on, so restore it with wave 2b.
     */
    public boolean includeRoundabouts = false;

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
}
