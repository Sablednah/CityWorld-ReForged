package me.daddychurchill.CityWorld;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.daddychurchill.CityWorld.Plugins.OreProvider;
import me.daddychurchill.CityWorld.Plugins.ShapeProvider;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Per-world context for the generator: the seed, the world's vertical layout, the settings, and the
 * provider stack the ~300 algorithm files reach through ({@code generator.shapeProvider},
 * {@code generator.oreProvider}, {@code generator.getSettings()}, …).
 *
 * <p><b>This is not the {@code ChunkGenerator}.</b> In the original, this class was both — it
 * extended Bukkit's {@code ChunkGenerator} <em>and</em> held all per-world state, initialized lazily
 * from a {@code World} via {@code initializeWorldInfo(World)}. That does not survive the move: a
 * modern {@code ChunkGenerator} is codec-registered, shared, and immutable, so it cannot hold
 * per-world state (PORTING.md, top risk #1 — the same trap that reshaped {@code RealBlocks}). The
 * split is therefore:
 *
 * <ul>
 *   <li>{@code worldgen/CityWorldChunkGenerator} — the shared, codec-registered engine plug-in;
 *   <li>this class — the mutable per-world context, constructed <em>once per world</em> with the
 *       facts it used to read off {@code World} ({@link #getWorldSeed()},
 *       {@link #getWorldMaxHeight()}, {@link #getWorldSeaLevel()}) and passed down to everything.
 * </ul>
 *
 * <p>The old lazy {@code initializeWorldInfo} is gone with it: the context is built in the
 * constructor, so there is no half-initialized window for the chunk pipeline's threads to observe.
 * Wiring the two together is P3.
 */
public class CityWorldGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * World styles. Only {@code NORMAL} is wired up; the rest are the deferred
     * {@code ShapeProvider} variants (PORTING.md P8 re-enables them).
     */
    public enum WorldStyle {
        FLOATING, // very low terrain with floating houses and cities
        FLOODED, // traditional terrain and cities but with raised sea level
        SNOWDUNES, // traditional terrain and cities but covered with snow dunes
        SANDDUNES, // traditional terrain and cities but covered with sand dunes
        ASTRAL, // alien landscape
        MAZE, // mazes with smaller cities
        NATURE, // just nature, no constructs anywhere
        METRO, // just buildings, no nature
        SPARSE, // a world of cities but away from each other
        DESTROYED, // normal landscape with destroyed cities
        NORMAL
    }

    // --- what the original read off the Bukkit World -------------------------------------------

    private final long worldSeed;
    private final int worldMaxHeight;
    private final int worldSeaLevel;

    public final WorldStyle worldStyle;

    // --- the provider stack -------------------------------------------------------------------

    public ShapeProvider shapeProvider;
    public OreProvider oreProvider;

    private final CityWorldSettings settings;

    // --- the vertical datums, all derived from the ShapeProvider --------------------------------

    public int height;
    public int seaLevel;
    public int landRange;
    public int seaRange;
    public int structureLevel;
    /** Y of the streets — the datum the whole city is laid out around ({@code seaLevel + 1}). */
    public int streetLevel;
    public int deepseaLevel;
    public int snowLevel;
    public int evergreenLevel;
    public int treeLevel;
    public int deciduousRange;
    public int evergreenRange;

    /**
     * Builds the per-world context.
     *
     * @param worldSeed      the world seed — every provider derives its noise from this, so it is
     *                       what makes generation reproducible
     * @param worldMaxHeight what Bukkit's {@code World.getMaxHeight()} used to answer; supply it
     *                       from the level's {@code LevelHeightAccessor} at P3
     * @param worldSeaLevel  what Bukkit's {@code World.getSeaLevel()} used to answer
     * @param worldStyle     which {@code ShapeProvider} variant to load
     */
    public CityWorldGenerator(long worldSeed, int worldMaxHeight, int worldSeaLevel, WorldStyle worldStyle) {
        this.worldSeed = worldSeed;
        this.worldMaxHeight = worldMaxHeight;
        this.worldSeaLevel = worldSeaLevel;
        this.worldStyle = worldStyle;
        this.settings = new CityWorldSettings();

        // The original's initializeWorldInfo, minus the lazy-init dance. Order matters: the
        // providers read the world facts above, and the datums below read the providers.
        shapeProvider = ShapeProvider.loadProvider(this, new Odds(getRelatedSeed()));
        oreProvider = OreProvider.loadProvider(this);

        // get ranges and contexts
        height = shapeProvider.getWorldHeight();
        seaLevel = shapeProvider.getSeaLevel();
        landRange = shapeProvider.getLandRange();
        seaRange = shapeProvider.getSeaRange();
        structureLevel = shapeProvider.getStructureLevel();
        streetLevel = shapeProvider.getStreetLevel();

        // now the other vertical points
        deepseaLevel = seaLevel - seaRange / 3;
        snowLevel = seaLevel + (landRange / 4 * 3);
        evergreenLevel = seaLevel + (landRange / 4 * 2);
        treeLevel = seaLevel + (landRange / 4);
        deciduousRange = evergreenLevel - treeLevel;
        evergreenRange = snowLevel - evergreenLevel;
    }

    public long getWorldSeed() {
        return worldSeed;
    }

    /** Replaces {@code getWorld().getMaxHeight()}. */
    public int getWorldMaxHeight() {
        return worldMaxHeight;
    }

    /** Replaces {@code getWorld().getSeaLevel()}. */
    public int getWorldSeaLevel() {
        return worldSeaLevel;
    }

    public CityWorldSettings getSettings() {
        return settings;
    }

    private int deltaSeed = 0;

    /**
     * A seed derived from the world seed, distinct per caller. Each provider gets its own so their
     * noise fields don't correlate. Carried over verbatim — including the fact that it depends on
     * <em>call order</em>, which is why the constructor above builds the stack in the original's
     * order and why it must stay single-threaded.
     */
    private long getRelatedSeed() {
        deltaSeed++;
        return worldSeed + deltaSeed;
    }

    /** Whether the sky column should be actively cleared/filled during generation. */
    public boolean clearAtmosphere() {
        return shapeProvider.clearAtmosphere(this);
    }

    /** The block to fill the atmosphere with at a given Y (air for normal worlds). */
    public Material findAtmosphereMaterialAt(int blockY) {
        return shapeProvider.findAtmosphereMaterialAt(this, blockY);
    }

    /**
     * Diagnostic logging, used throughout the generator (mostly from commented-out tracing that the
     * port preserves). The original routed this through the Bukkit plugin's logger; here it goes to
     * the mod's log at debug level, so leftover tracing stays silent unless it is asked for.
     */
    public void reportFormatted(String format, Object... objects) {
        LOGGER.debug(String.format(format, objects));
    }

    /**
     * The generator's habit of swallowing exceptions and carrying on, preserved. Worth knowing when
     * a chunk comes out wrong: the original reports and continues rather than failing the chunk.
     */
    public void reportException(String message, Exception e) {
        LOGGER.error(message, e);
    }
}
