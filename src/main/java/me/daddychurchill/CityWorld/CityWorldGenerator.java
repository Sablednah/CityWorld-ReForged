package me.daddychurchill.CityWorld;

import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.daddychurchill.CityWorld.Plugins.CoverProvider;
import me.daddychurchill.CityWorld.Plugins.LootProvider;
import me.daddychurchill.CityWorld.Plugins.MaterialProvider;
import me.daddychurchill.CityWorld.Plugins.OdonymProvider;
import me.daddychurchill.CityWorld.Plugins.OreProvider;
import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Plugins.ShapeProvider;
import me.daddychurchill.CityWorld.Plugins.SpawnProvider;
import me.daddychurchill.CityWorld.Plugins.StructureInAirProvider;
import me.daddychurchill.CityWorld.Plugins.StructureOnGroundProvider;
import me.daddychurchill.CityWorld.Plugins.SurfaceProvider;
import me.daddychurchill.CityWorld.Plugins.ThingProvider;
import me.daddychurchill.CityWorld.Plugins.TreeProvider;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
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
    private final int terrainCeiling;
    private final int worldSeaLevel;

    public final WorldStyle worldStyle;

    /**
     * The world's real block bounds — {@code -64} and {@code 319} for a modern overworld.
     *
     * <p><b>These are deliberately not the same as {@link #height}.</b> In 1.14 the world was
     * {@code 0..255} and one number meant both "how tall the world is" and "how tall terrain
     * scales"; upstream conflated them in {@code world.getMaxHeight()}. At P4 they separate:
     * terrain still scales against a 256 ceiling (so its shape stays exactly upstream's — the whole
     * point of vendoring Bukkit's noise), while the world itself runs {@code -64..319}. The extra
     * room is not stretched terrain: below is 64 blocks of new underground, above is sky/building
     * headroom.
     */
    public final int worldMinY;

    /** Inclusive — {@code 319} for a modern overworld. See {@link #worldMinY}. */
    public final int worldMaxY;

    // --- the provider stack -------------------------------------------------------------------

    public ShapeProvider shapeProvider;
    public OreProvider oreProvider;
    public MaterialProvider materialProvider;
    public SurfaceProvider surfaceProvider;
    public OdonymProvider odonymProvider;
    public SpawnProvider spawnProvider;
    public ThingProvider thingProvider;
    public CoverProvider coverProvider;
    public LootProvider lootProvider;
    public RoomProvider roomProvider;
    public StructureInAirProvider structureInAirProvider;
    public StructureOnGroundProvider structureOnGroundProvider;
    public TreeProvider treeProvider;

    private final CityWorldSettings settings;

    /** Shared identity for every paved road, so they all count as connected to each other. */
    public long connectedKeyForPavedRoads;
    /** As above, for parks. */
    public long connectedKeyForParks;

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
     * @param terrainCeiling what Bukkit's {@code World.getMaxHeight()} used to answer, and what
     *                       terrain still scales against; pass {@code 256} for upstream's shape.
     *                       <em>Not</em> the world's ceiling — see {@link #worldMinY}
     * @param worldSeaLevel  what Bukkit's {@code World.getSeaLevel()} used to answer
     * @param worldStyle     which {@code ShapeProvider} variant to load
     * @param worldMinY      the world's real floor, from the level's {@code LevelHeightAccessor}
     * @param worldMaxY      the world's real ceiling (inclusive), likewise
     */
    public CityWorldGenerator(long worldSeed, int terrainCeiling, int worldSeaLevel, WorldStyle worldStyle,
            int worldMinY, int worldMaxY) {
        this.worldSeed = worldSeed;
        this.terrainCeiling = terrainCeiling;
        this.worldSeaLevel = worldSeaLevel;
        this.worldStyle = worldStyle;
        this.worldMinY = worldMinY;
        this.worldMaxY = worldMaxY;
        this.settings = new CityWorldSettings();

        // The original's initializeWorldInfo, minus the lazy-init dance. Order matters: the
        // providers read the world facts above, and the datums below read the providers.
        shapeProvider = ShapeProvider.loadProvider(this, new Odds(getRelatedSeed()));
        oreProvider = OreProvider.loadProvider(this);
        materialProvider = new MaterialProvider(this);
        treeProvider = TreeProvider.loadProvider(this, new Odds(getRelatedSeed()));
        surfaceProvider = SurfaceProvider.loadProvider(this, new Odds(getRelatedSeed()));
        odonymProvider = OdonymProvider.loadProvider(this, new Odds(getRelatedSeed()));
        spawnProvider = new SpawnProvider(this);
        thingProvider = ThingProvider.loadProvider(this);
        coverProvider = CoverProvider.loadProvider(this, new Odds(getRelatedSeed()));
        lootProvider = LootProvider.loadProvider(this);
        structureInAirProvider = StructureInAirProvider.loadProvider(this);
        structureOnGroundProvider = StructureOnGroundProvider.loadProvider(this);

        // Fixed per world, so every road shares one identity. Derived straight from the seed rather
        // than from a running RNG — see getConnectionKey.
        connectedKeyForPavedRoads = new Odds(worldSeed + 101).getRandomLong();
        connectedKeyForParks = new Odds(worldSeed + 102).getRandomLong();

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

    /**
     * Replaces {@code getWorld().getMaxHeight()} <em>for terrain scaling only</em> — it feeds
     * {@code landRange}, which sets how tall mountains get. Kept at upstream's 256 so terrain keeps
     * its original shape; the world's actual ceiling is {@link #worldMaxY}.
     */
    public int getTerrainCeiling() {
        return terrainCeiling;
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

    /** The terrain height at a position, without generating anything there. */
    public int getFarBlockY(int blockX, int blockZ) {
        return shapeProvider.findBlockY(this, blockX, blockZ);
    }

    // --- the platmap collection ----------------------------------------------------------------

    /**
     * The city plans, one per {@link PlatMap#Width}² block of chunks, built on demand.
     *
     * <p><b>Concurrent by necessity.</b> Upstream used a {@code Hashtable} with a get-then-put,
     * which is not atomic — two threads that miss together both build a PlatMap and one silently
     * wins. Bukkit generated chunks on one thread so it never happened; the modern pipeline does
     * not, so this is a {@code ConcurrentHashMap} and {@link #getPlatMap} uses
     * {@code computeIfAbsent}, which builds exactly once per key however many threads ask at once.
     */
    private final ConcurrentHashMap<Long, PlatMap> platmaps = new ConcurrentHashMap<>();

    public PlatMap getPlatMap(int chunkX, int chunkZ) {

        // find the origin for the plat
        int platX = calcOrigin(chunkX);
        int platZ = calcOrigin(chunkZ);

        // calculate the plat's key
        Long platkey = ((long) platX * (long) Integer.MAX_VALUE + (long) platZ);

        // Build-once, however many threads race here. Note this runs PlatMap's constructor —
        // i.e. the whole city plan for that block — inside the map's per-bin lock, which is what
        // makes "exactly once" true. Everything it calls must therefore avoid touching the platmap
        // collection again, or it would deadlock; nothing does, because planning only ever reads
        // the shape provider.
        return platmaps.computeIfAbsent(platkey, key -> new PlatMap(this, shapeProvider, platX, platZ));
    }

    // Supporting code used by getPlatMap
    private int calcOrigin(int i) {
        if (i >= 0) {
            return i / PlatMap.Width * PlatMap.Width;
        } else {
            return -((Math.abs(i + 1) / PlatMap.Width * PlatMap.Width) + PlatMap.Width);
        }
    }

    /** How many platmaps have been planned — diagnostics only. */
    public int getPlatMapCount() {
        return platmaps.size();
    }

    /**
     * A fresh identity for a lot, used to decide which lots are connected to which.
     *
     * <p><b>Deliberately not upstream's implementation, and it has to be.</b> Upstream drew this
     * from one shared, mutable RNG ({@code connectionKeyGen.getRandomLong()}), so the value a lot
     * received depended on how many lots had been created before it. Under Bukkit's single-threaded
     * generation that was reproducible; under the modern pipeline platmaps are planned concurrently
     * and in arbitrary order, so the same seed would produce a different world every run — and the
     * RNG itself would be raced.
     *
     * <p>Only <em>equality</em> of these keys is ever tested ({@code ConnectedLot.isConnected}), so
     * deriving them from the lot's position preserves the meaning exactly — every lot still starts
     * with its own distinct key, and {@code makeConnected} still propagates a neighbour's — while
     * making it order-independent and reproducible.
     */
    public long getConnectionKey(int chunkX, int chunkZ) {
        return new Odds(worldSeed + ((long) chunkX << 32 ^ chunkZ)).getRandomLong();
    }

    /**
     * Clears a region — used when a lot decides to demolish what is under it.
     *
     * <p>Stubbed: it belongs to the decoration pass, which runs on a live level and is not driven
     * yet (P5). Only the destroyed-city styles call it.
     */
    public void destroyWithin(int x1, int x2, int y1, int y2, int z1, int z2) {
    }

    /**
     * Blows a rough sphere out of the world — how the unfinished/decayed styles chew holes in
     * things. Stubbed alongside {@link #destroyWithin}; both belong to the decoration pass (P5).
     */
    public void destroyArea(int x, int y, int z, int radius) {
    }

    /**
     * Diagnostic logging, used throughout the generator (mostly from commented-out tracing that the
     * port preserves). The original routed this through the Bukkit plugin's logger; here it goes to
     * the mod's log at debug level, so leftover tracing stays silent unless it is asked for.
     */
    public void reportFormatted(String format, Object... objects) {
        LOGGER.debug(String.format(format, objects));
    }

    public void reportMessage(String message) {
        LOGGER.debug(message);
    }

    /**
     * The generator's habit of swallowing exceptions and carrying on, preserved. Worth knowing when
     * a chunk comes out wrong: the original reports and continues rather than failing the chunk.
     */
    public void reportException(String message, Exception e) {
        LOGGER.error(message, e);
    }
}
