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
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.WorldBlocks;
import me.daddychurchill.CityWorld.compat.Environment;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;

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
 * {@code CityWorldChunkGenerator} constructs one per world.
 */
public class CityWorldGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * World styles. All of them ship; the providers dispatch on this in {@code ShapeProvider},
     * {@code SurfaceProvider} and {@code SpawnProvider}.
     */
    public enum WorldStyle {
        // Order matters: the single-player Customize style picker cycles in declaration order, and the
        // three headline styles lead. MODERN is the default for new worlds; APOCALYPSE is MODERN gone to
        // ruin; CLASSIC is the 1.8-era look.
        MODERN, // uses modern MC — tall builds, modern blocks/ores/trees/ice; the default for new worlds
        APOCALYPSE, // MODERN cities gone to ruin — heavy overgrowth, road/building decay, zombie-infested
        CLASSIC, // the faithful 1.8-era look — was NORMAL; the fieldless/legacy default
        FLOATING, // very low terrain with floating houses and cities
        FLOODED, // traditional terrain and cities but with raised sea level
        SNOWDUNES, // traditional terrain and cities but covered with snow dunes
        SANDDUNES, // traditional terrain and cities but covered with sand dunes
        ASTRAL, // alien landscape
        MAZE, // mazes with smaller cities
        NATURE, // just nature, no constructs anywhere
        METRO, // just buildings, no nature
        SPARSE, // a world of cities but away from each other
        DESTROYED // normal landscape with destroyed cities
    }

    /**
     * The MODERN-family styles: the ones that render with modern MC (tall builds, the decorative block
     * palette, modern furniture, climate biomes, vanilla wild decoration). {@code APOCALYPSE} is a
     * MODERN world gone to ruin, so it shares the modern look — this keeps the ~dozen style checks that
     * used to test {@code == MODERN} behaviour-preserving for MODERN while extending them to APOCALYPSE.
     */
    public boolean isModernStyle() {
        return worldStyle == WorldStyle.MODERN || worldStyle == WorldStyle.APOCALYPSE;
    }

    /**
     * Resolves the generator's {@code "style"} JSON field (a case-insensitive style name, or empty)
     * to a {@link WorldStyle}. Absent or unrecognised falls back to {@link WorldStyle#CLASSIC} —
     * upstream's {@code validateStyle} did the same, so a typo yields a plain world rather than a
     * crash. This is the one place the raw string becomes an enum.
     *
     * <p>{@code "normal"} is accepted as a legacy alias for {@code CLASSIC} (the style was renamed when
     * the MODERN style landed), so worlds and presets written before the rename still resolve cleanly.
     */
    public static WorldStyle parseStyle(java.util.Optional<String> name) {
        if (name.isEmpty())
            return WorldStyle.CLASSIC;
        String raw = name.get().trim().toUpperCase(java.util.Locale.ROOT);
        if (raw.equals("NORMAL"))
            return WorldStyle.CLASSIC; // legacy alias
        try {
            return WorldStyle.valueOf(raw);
        } catch (IllegalArgumentException e) {
            LOGGER.warn("CityWorld: unknown world style \"{}\" — falling back to CLASSIC", name.get());
            return WorldStyle.CLASSIC;
        }
    }

    // --- what the original read off the Bukkit World -------------------------------------------

    private final long worldSeed;
    private final int terrainCeiling;
    private final int worldSeaLevel;

    public final WorldStyle worldStyle;

    /**
     * Always {@code NORMAL}: the port registers one overworld dimension. Kept because ported code
     * legitimately branches on it (a farm grows netherwart instead of wheat in the Nether). See
     * {@code compat/Environment}.
     */
    public final Environment worldEnvironment = Environment.NORMAL;

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

    /** The MODERN biome source's horizontal climate axes; see the ctor and {@link #getTemperature}. */
    private final me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator temperatureShape;
    private final me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator humidityShape;

    /**
     * The two extra climate axes vanilla's biome parameters use and CityWorld had no equivalent of —
     * see {@link #getErosion} and {@link #getWeirdness}. Only consulted when something asks for a full
     * vanilla-shaped climate point, i.e. the TerraBlender bridge.
     */
    private final me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator erosionShape;
    private final me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator weirdnessShape;

    /** Seeded terracotta colour table for badlands, sampled by elevation; see {@link #badlandsBandAt}. */
    private final me.daddychurchill.CityWorld.compat.Material[] badlandsBands;
    private final me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator badlandsOffsetShape;

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
        this(worldSeed, terrainCeiling, worldSeaLevel, worldStyle, worldMinY, worldMaxY,
                java.util.Optional.empty());
    }

    /**
     * @param decayOverride a per-dimension decay override (from the generator's {@code "decayed"}
     *                      JSON field), or empty. See
     *                      {@link CityWorldSettings#CityWorldSettings(WorldStyle, java.util.Optional, me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData)}.
     */
    public CityWorldGenerator(long worldSeed, int terrainCeiling, int worldSeaLevel, WorldStyle worldStyle,
            int worldMinY, int worldMaxY, java.util.Optional<Boolean> decayOverride) {
        this(worldSeed, terrainCeiling, worldSeaLevel, worldStyle, worldMinY, worldMaxY, decayOverride,
                me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData.DEFAULT);
    }

    /**
     * @param settingsData the per-world settings resolved from the {@code cityworld:world_settings}
     *                     datapack registry (or {@link me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData#DEFAULT}
     *                     when the generator carries no settings holder). Overlaid onto the runtime
     *                     {@link CityWorldSettings} <em>before</em> the world-style and decay
     *                     overrides, so those still win.
     */
    public CityWorldGenerator(long worldSeed, int terrainCeiling, int worldSeaLevel, WorldStyle worldStyle,
            int worldMinY, int worldMaxY, java.util.Optional<Boolean> decayOverride,
            me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData settingsData) {
        this.worldSeed = worldSeed;
        this.terrainCeiling = terrainCeiling;
        this.worldSeaLevel = worldSeaLevel;
        this.worldStyle = worldStyle;
        this.worldMinY = worldMinY;
        this.worldMaxY = worldMaxY;
        this.settings = new CityWorldSettings(worldStyle, decayOverride, settingsData);

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

        // Slow, large-region temperature/humidity fields — the horizontal climate axis the MODERN
        // biome source lays across CityWorld's elevation, so warm/dry vs cool/wet regions pick
        // different biomes. Seeded off the world (independent of terrain) so climate and terrain don't
        // correlate; scale set for biome-sized (~hundreds of blocks) patches.
        temperatureShape = new me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator(worldSeed + 71, 2);
        temperatureShape.setScale(0.0009);
        humidityShape = new me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator(worldSeed + 137, 2);
        humidityShape.setScale(0.0012);

        // Erosion and weirdness: two MORE independent fields, on their own seeds and their own scales.
        // Vanilla generates continentalness, erosion and ridges as three separate 2D noises and derives
        // terrain FROM them; CityWorld goes the other way, so the honest equivalent is to add the axes
        // it lacks rather than to compute them from the terrain it already has. Deriving them from
        // elevation would put several axes on one scale, and any modded biome asking for a combination
        // we cannot express — high erosion at low elevation, say — could then never be chosen.
        // Continentalness is the deliberate exception; see getContinentalness.
        erosionShape = new me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator(worldSeed + 211, 2);
        erosionShape.setScale(0.0011);
        weirdnessShape = new me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator(worldSeed + 313, 2);
        weirdnessShape.setScale(0.0016);

        // Badlands terracotta bands: a seeded colour table sampled by elevation, with a gentle low-freq
        // offset so the stripes undulate instead of being dead flat (mirrors vanilla's mesa surface).
        badlandsBands = buildBadlandsBands(worldSeed);
        badlandsOffsetShape = new me.daddychurchill.CityWorld.compat.noise.SimplexOctaveGenerator(worldSeed + 211, 1);
        badlandsOffsetShape.setScale(0.012);

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
     * The MODERN biome climate at a column: temperature (and {@link #getHumidity humidity}) as
     * {@code 0.0} (cold / dry) .. {@code 1.0} (hot / wet). A slow, large-region field seeded off the
     * world and independent of terrain, so the biome source can lay warm/cool and wet/dry regions
     * across the elevation bands.
     */
    public double getTemperature(int x, int z) {
        return climate01(temperatureShape.noise(x, z, 0.5, 0.8));
    }

    public double getHumidity(int x, int z) {
        return climate01(humidityShape.noise(x, z, 0.5, 0.8));
    }

    /**
     * Vanilla's <em>erosion</em> axis, in {@code -1..1} — how worn-down the land reads as. Its own noise
     * field, independent of terrain and of the other axes.
     */
    public double getErosion(int x, int z) {
        return clampUnit(erosionShape.noise(x, z, 0.5, 0.8));
    }

    /**
     * Vanilla's <em>weirdness</em> axis (its "ridges"), in {@code -1..1} — what picks biome variants.
     * Its own noise field, likewise independent.
     */
    public double getWeirdness(int x, int z) {
        return clampUnit(weirdnessShape.noise(x, z, 0.5, 0.8));
    }

    /**
     * Vanilla's <em>continentalness</em> axis, in {@code -1..1} — and the one axis deliberately taken
     * from the terrain rather than from a fresh noise field.
     *
     * <p>Continentalness means "how far inland is this", which in CityWorld is a fact we already know
     * exactly: the column's height against sea level. Rolling a separate noise for it would put ocean
     * biomes on dry hilltops, which is a worse failure than the correlation it would avoid.
     *
     * <p><b>This is not the same axis as depth</b>, even though both touch elevation. Depth varies
     * <em>within</em> a column (surface to bedrock) and continentalness does not vary with Y at all;
     * they are perpendicular, so tying both to the height map costs nothing.
     */
    public double getContinentalness(int x, int z) {
        int terrainY = getFarBlockY(x, z);
        if (terrainY <= seaLevel) {
            // Below the waterline: -1 at the bottom of the deep sea, ~0 at the shore.
            int deep = Math.max(1, seaLevel - deepseaLevel);
            return clampUnit(-1.0 + (double) (terrainY - deepseaLevel) / deep);
        }
        // Above it: 0 at the shore rising towards +1 at the top of the land range.
        return clampUnit((double) (terrainY - seaLevel) / Math.max(1, landRange));
    }

    /** Clamp to vanilla's {@code -1..1} climate range. */
    private static double clampUnit(double noise) {
        return Math.max(-1.0, Math.min(1.0, noise));
    }

    private static double climate01(double noise) {
        return Math.max(0.0, Math.min(1.0, (noise + 1.0) / 2.0));
    }

    /**
     * The badlands terracotta colour at a block — the seeded band table indexed by elevation, nudged by
     * a gentle low-frequency offset so the stripes wander a little (as vanilla's mesas do) rather than
     * ringing the world dead level. Continuous across columns: a given elevation is the same colour
     * everywhere bar the wander.
     */
    public me.daddychurchill.CityWorld.compat.Material badlandsBandAt(int x, int y, int z) {
        int offset = (int) Math.round(badlandsOffsetShape.noise(x, z, 0.5, 0.8) * 1.5); // very gentle wander
        return badlandsBands[Math.floorMod(y + offset, badlandsBands.length)];
    }

    /**
     * The badlands <em>surface</em> block: red sand skims the high spots of a surface noise (like the
     * plateau tops of a vanilla mesa) while the rest shows the bare terracotta band for its elevation, so
     * a smooth slope reads as stripes instead of a solid red-sand skin.
     */
    public me.daddychurchill.CityWorld.compat.Material badlandsSurfaceAt(int x, int y, int z) {
        if (badlandsOffsetShape.noise(x + 4096, z - 4096, 0.5, 0.8) > 0.35)
            return me.daddychurchill.CityWorld.compat.Material.RED_SAND;
        return badlandsBandAt(x, y, z);
    }

    // Mesa band table. Our terrain is far bumpier than a vanilla mesa, so thin bands read as colour
    // speckle (every little step lands in a different band). Instead: bold 3-6 block bands cycling an
    // orange-dominant palette with clear accents, so bumps within a few blocks share a colour and the
    // surface shows coherent stripe zones. Seeded off the world for determinism.
    private static me.daddychurchill.CityWorld.compat.Material[] buildBadlandsBands(long seed) {
        java.util.Random r = new java.util.Random(seed);
        me.daddychurchill.CityWorld.compat.Material[] palette = {
                me.daddychurchill.CityWorld.compat.Material.TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.ORANGE_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.BROWN_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.YELLOW_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.ORANGE_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.RED_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.WHITE_TERRACOTTA,
                me.daddychurchill.CityWorld.compat.Material.LIGHT_GRAY_TERRACOTTA };
        var bands = new me.daddychurchill.CityWorld.compat.Material[64];
        int idx = 0;
        int p = r.nextInt(palette.length);
        while (idx < bands.length) {
            me.daddychurchill.CityWorld.compat.Material colour = palette[p % palette.length];
            int thick = 3 + r.nextInt(4); // 3-6 blocks — bold
            for (int j = 0; j < thick && idx < bands.length; j++)
                bands[idx++] = colour;
            p++;
        }
        return bands;
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
     * The demolition tool bound to the chunk currently being decorated.
     *
     * <p><b>Thread-local, and it has to be.</b> This generator is one shared per-world context, but
     * decoration runs concurrently on chunk-generation workers, each with its own live
     * {@code WorldGenLevel} and each in the middle of one chunk. Upstream kept a single shared
     * {@code decayBlocks} with a single mutable {@code Odds} — reproducible only because Bukkit
     * generated one chunk at a time. Under the modern pipeline that field would be raced and its RNG
     * order non-deterministic. A thread-local, re-seeded per chunk from the chunk position (the same
     * order-independent trick {@link #getConnectionKey} uses), keeps every worker's demolition on its
     * own level and reproducible regardless of scheduling.
     */
    private final ThreadLocal<WorldBlocks> decayBlocks = new ThreadLocal<>();

    /**
     * Binds {@link #decayBlocks} to the chunk about to be decorated. Call from the decoration pass
     * before running the lots, and pair with {@link #endDecoration()} in a {@code finally}.
     */
    public void beginDecoration(LevelAccessor level, ChunkPos pos) {
        Odds odds = new Odds(worldSeed + ((long) pos.x() << 32 ^ pos.z()));
        decayBlocks.set(new WorldBlocks(this, level, odds));
    }

    /** Releases this thread's demolition tool once the chunk's decoration is done. */
    public void endDecoration() {
        decayBlocks.remove();
    }

    /**
     * Clears a region — used when a lot decides to demolish what is under it. Delegates to the
     * thread-local {@link WorldBlocks} bound by {@link #beginDecoration}; a no-op if called outside
     * the decoration pass (e.g. a plan-only sweep with no live level).
     */
    public void destroyWithin(int x1, int x2, int y1, int y2, int z1, int z2) {
        destroyWithin(x1, x2, y1, y2, z1, z2, getSettings().buildingDecayIntensity);
    }

    /**
     * Overload with an explicit demolition-<em>intensity</em> scalar — building lots pass
     * {@code buildingDecayIntensity}, roads pass {@code roadDecayIntensity}. It scales the number and size
     * of the collapse holes. Burning debris is gated on both {@code includeFires} and {@code includeDecayedFires}
     * and sprinkled at the tunable {@code oddsOfDecayFire}.
     */
    public void destroyWithin(int x1, int x2, int y1, int y2, int z1, int z2, double intensity) {
        destroyWithin(x1, x2, y1, y2, z1, z2, intensity, true);
    }

    /** As above but a caller can force fire OFF regardless of the world's decay-fire setting — for a ruin
     *  that weathered rather than burned (the old ex-castle). */
    public void destroyWithin(int x1, int x2, int y1, int y2, int z1, int z2, double intensity, boolean allowFire) {
        WorldBlocks blocks = decayBlocks.get();
        if (blocks != null)
            blocks.destroyWithin(x1, x2, y1, y2, z1, z2, allowFire && decayFire(), getSettings().oddsOfDecayFire,
                    intensity);
    }

    /**
     * Blows a rough sphere out of the world — how the unfinished/decayed styles chew holes in
     * things. Delegates like {@link #destroyWithin}; a no-op outside the decoration pass.
     */
    public void destroyArea(int x, int y, int z, int radius) {
        destroyArea(x, y, z, radius, getSettings().buildingDecayIntensity);
    }

    /** Overload that scales the blast {@code radius} by an intensity scalar (set-pieces are buildings). */
    public void destroyArea(int x, int y, int z, int radius, double intensity) {
        WorldBlocks blocks = decayBlocks.get();
        if (blocks != null)
            blocks.destroyArea(x, y, z, Math.max(1, (int) Math.round(radius * intensity)), decayFire(),
                    getSettings().oddsOfDecayFire);
    }

    /** Burning demolition debris only when the world both keeps fires AND keeps decay-fires (campfires can
     *  stay while burning rubble is off — APOCALYPSE does exactly that). */
    private boolean decayFire() {
        return getSettings().includeFires && getSettings().includeDecayedFires;
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
     * Announces a landmark's location — always to the log at debug; in chat only when the world's
     * {@code broadcastSpecialPlaces} setting is on <em>and</em> {@code kind} is in its
     * {@code announcedLandmarks} list (the genuine rares by default — see
     * {@code CityWorldSettingsData.World#DEFAULT_ANNOUNCED}). Chat goes to the players in the world
     * the landmark generated in, not the whole server.
     */
    public void reportLocation(String kind, String title, AbstractBlocks chunk) {
        ServerLevelAccessor level = chunk instanceof RealBlocks real ? real.getServerLevel() : null;
        reportLocation(kind, title, level, chunk.getOriginX(), chunk.getOriginZ());
    }

    public void reportLocation(String kind, String title, ServerLevelAccessor level, int x, int z) {
        LOGGER.debug("{} placed near {}, {}", title, x, z);
        if (getSettings().broadcastSpecialPlaces && getSettings().announcedLandmarks.contains(kind))
            broadcastLocation(level, title, x, z);
    }

    /**
     * Chat-announce a landmark to the generating world's own players. Called from the worldgen
     * workers, so it hops to the server thread before touching the player list. A null {@code level}
     * (a plan-only sweep with no live world) simply skips the chat — the debug log above still fired.
     */
    private void broadcastLocation(ServerLevelAccessor level, String title, int x, int z) {
        if (level == null)
            return;
        ServerLevel world = level.getLevel();
        Component message = Component.literal(String.format("%s generated near %d, %d", title, x, z))
                .withStyle(ChatFormatting.GRAY);
        world.getServer().execute(() -> {
            for (ServerPlayer player : world.players())
                player.sendSystemMessage(message);
        });
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
