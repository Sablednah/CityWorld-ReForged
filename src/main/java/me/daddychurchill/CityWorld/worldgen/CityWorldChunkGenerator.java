package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plugins.OreProvider;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.util.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * The CityWorld {@link ChunkGenerator} — the adapter between modern worldgen and the ported brain.
 *
 * <p><b>The work happens in two passes, and which one does what is the thing to know here:</b>
 * <ul>
 *   <li>{@link #fillFromNoise} shapes <em>terrain</em> through the ported {@link InitialBlocks} seam
 *       (raw {@code ChunkAccess}).
 *   <li>{@link #applyBiomeDecoration} builds the <em>city</em> through {@link RealBlocks} (a live
 *       {@code WorldGenLevel}). That split is upstream's own: its chunk generator only ever made
 *       terrain and a {@code BlockPopulator} drew the city afterwards.
 * </ul>
 *
 * <p>Two other seams worth knowing about:
 * <ul>
 *   <li><b>Vertical layout</b> — terrain scales against a 256 ceiling (upstream's shape) inside a
 *       {@code -64..319} world; see {@link #TERRAIN_CEILING} and {@code CityWorldGenerator.worldMinY}.
 *   <li><b>Biomes</b> — the shaper pushes them per column, modern gen pulls them from a
 *       {@code BiomeSource}; see {@link #IGNORE_BIOMES}.
 * </ul>
 *
 * <p>It also suppresses vanilla structures, carvers and decoration, so CityWorld owns the chunk.
 */
public class CityWorldChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CityWorldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource),
                    Codec.BOOL.optionalFieldOf("decayed").forGetter(g -> g.decayed),
                    Codec.STRING.optionalFieldOf("style").forGetter(g -> g.style),
                    RegistryFileCodec.create(CityWorldRegistries.WORLD_SETTINGS, CityWorldSettingsData.CODEC)
                            .optionalFieldOf("settings").forGetter(g -> g.settings),
                    net.minecraft.world.level.Level.RESOURCE_KEY_CODEC.optionalFieldOf("twin_of").forGetter(g -> g.twinOf),
                    Codec.STRING.optionalFieldOf("environment").forGetter(g -> g.environment)
            ).apply(instance, CityWorldChunkGenerator::new));

    /**
     * The ceiling terrain scales against, at its 1.14 value so the ported {@code ShapeProvider}
     * reproduces upstream's terrain exactly.
     *
     * <p><b>Not the world's ceiling</b> — the world is {@code -64..319}, taken from the level below.
     * Feeding the shaper 384 would not make the world taller, it would make *mountains* half again
     * as tall (it scales {@code landRange}) and throw away the shape the noise vendoring exists to
     * preserve. P4's modernization is downward: 64 blocks of new underground. See
     * {@code CityWorldGenerator.worldMinY}.
     */
    private static final int TERRAIN_CEILING = 256;

    /** Sea level — 63 in both 1.14 and modern Minecraft, so the surface band already lines up. */
    private static final int UPSTREAM_SEA_LEVEL = 63;

    /**
     * The per-world context, built once and lazily.
     *
     * <p>This generator instance is shared across the chunk pipeline's worker threads, so the
     * context has to be published safely — hence the volatile + double-checked locking rather than
     * a plain field. It cannot simply be built in the constructor because the codec does not carry
     * the world seed; {@link #createState} is the only place vanilla hands it to us.
     */
    private volatile CityWorldGenerator context;

    /** Captured in {@link #createState}; see {@link #context}. */
    private volatile long levelSeed;

    /**
     * Whether {@link #createState} has handed us the seed yet.
     *
     * <p>Tracked separately rather than sniffing for {@code levelSeed == 0} — zero is a perfectly
     * legal world seed.
     */
    private volatile boolean levelSeedKnown;

    /**
     * A per-dimension decay override, straight from the generator's JSON ({@code "decayed": true}).
     *
     * <p>Present {@code true}/{@code false} forces the ruined/pristine styles on for <em>this</em>
     * dimension regardless of the datapack {@link CityWorldSettingsData}; absent means "follow the
     * settings". It's what lets two same-seed dimensions be the same city intact and in ruins — the
     * overworld follows the settings, and the {@code cityworld:city} dimension ships {@code decayed: true}.
     *
     * <p>Deliberately scoped to buildings and roads, not {@code includeDecayedNature}: nature-decay
     * drains the seas and deserts the world, which is a whole-world mood, not "this city is ruined".
     * The twin should read as the same wet, green place with its buildings wrecked.
     */
    private final Optional<Boolean> decayed;

    /**
     * The world style, straight from the generator's JSON ({@code "style": "flooded"}).
     *
     * <p>Absent means {@link CityWorldGenerator.WorldStyle#CLASSIC}. Kept as the raw string (not a
     * parsed {@code WorldStyle}) purely so the codec round-trips exactly what was written; it is
     * resolved to an enum in {@link #context}. This is what a per-style world preset sets, what the
     * single-player Customize screen writes, and — eventually — what a per-world server config will
     * carry. See {@code CityWorldGenerator.parseStyle}.
     */
    private final Optional<String> style;

    /**
     * The per-world settings. Via {@link RegistryFileCodec} the JSON is <em>either</em> a reference
     * to a {@code cityworld:world_settings} registry entry ({@code "settings": "cityworld:default"})
     * <em>or</em> an inline object ({@code "settings": { "features": {...}, ... }}) — resolved at
     * codec-decode time, the one place registry access is clean ({@link #fillFromNoise} never gets a
     * {@code registryAccess()}). The reference form is what the bundled dimension/presets use and what
     * a server op overrides per save; the inline form is what the single-player Customize screen bakes
     * in, so a hand-tuned world carries its own settings without needing a datapack. Absent means the
     * compiled {@link CityWorldSettingsData#DEFAULT} — existing worlds (predating this field) and
     * plan-only probes.
     *
     * <p>This is the P7 per-world config seam (PORTING.md top risk #4). World-style validation and the
     * {@link #decayed} override still run last in {@code CityWorldSettings}, so their invariants win
     * over whatever the settings asked for.
     */
    private final Optional<Holder<CityWorldSettingsData>> settings;

    /**
     * Another dimension this one is the same city as ({@code "twin_of": "minecraft:overworld"}). When that
     * dimension runs a CityWorld generator, its style and settings are used <em>instead of</em> this
     * generator's own {@link #style}/{@link #settings} — read at runtime, because a Customize-made world
     * carries inline settings no static dimension file could name. Same seed + same style + same settings
     * = the same plan; {@link #decayed} then picks the era ({@code /cityworld} is {@code false}: the city
     * before the fall). The own fields remain the fallback when the source is not CityWorld.
     *
     * <p>Biomes still come from this generator's own biome source (a biome source binds to one context, so
     * the overworld's cannot be shared).
     */
    private final Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> twinOf;

    /**
     * The realm, straight from the JSON: {@code "nether"} for the ruined-city Nether; absent means the
     * overworld. Kept raw so the codec round-trips; resolved in {@link #context}.
     */
    private final Optional<String> environment;

    private static me.daddychurchill.CityWorld.compat.Environment parseEnvironment(Optional<String> name) {
        return switch (name.map(n -> n.trim().toLowerCase(java.util.Locale.ROOT)).orElse("")) {
            case "nether", "the_nether" -> me.daddychurchill.CityWorld.compat.Environment.NETHER;
            case "end", "the_end" -> me.daddychurchill.CityWorld.compat.Environment.THE_END;
            default -> me.daddychurchill.CityWorld.compat.Environment.NORMAL;
        };
    }

    /** Whether the context was actually built from {@link #twinOf}'s generator — the self-test's handle. */
    private volatile boolean twinResolved;

    public boolean isTwinResolved() {
        return twinResolved;
    }

    public CityWorldChunkGenerator(BiomeSource biomeSource) {
        this(biomeSource, Optional.empty(), Optional.empty(), Optional.empty());
    }

    /**
     * The world style this generator carries, resolved to the enum — for the single-player Customize
     * screen, which reads it off the currently-selected generator to seed its picker.
     */
    public CityWorldGenerator.WorldStyle resolvedStyle() {
        return CityWorldGenerator.parseStyle(style);
    }

    /**
     * The settings this generator carries, resolved to a value — for the single-player Customize
     * screen, which reads them off the currently-selected generator to seed its controls. Absent
     * holder → {@link CityWorldSettingsData#DEFAULT}.
     */
    public CityWorldSettingsData resolvedSettings() {
        return settings.map(Holder::value).orElse(CityWorldSettingsData.DEFAULT);
    }

    public CityWorldChunkGenerator(BiomeSource biomeSource, Optional<Boolean> decayed, Optional<String> style,
            Optional<Holder<CityWorldSettingsData>> settings) {
        this(biomeSource, decayed, style, settings, Optional.empty());
    }

    public CityWorldChunkGenerator(BiomeSource biomeSource, Optional<Boolean> decayed, Optional<String> style,
            Optional<Holder<CityWorldSettingsData>> settings,
            Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> twinOf) {
        this(biomeSource, decayed, style, settings, twinOf, Optional.empty());
    }

    public CityWorldChunkGenerator(BiomeSource biomeSource, Optional<Boolean> decayed, Optional<String> style,
            Optional<Holder<CityWorldSettingsData>> settings,
            Optional<net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level>> twinOf,
            Optional<String> environment) {
        super(biomeSource);
        this.environment = environment;
        this.decayed = decayed;
        this.style = style;
        this.settings = settings;
        this.twinOf = twinOf;
    }

    /** The CityWorld generator {@link #twinOf} names, or null (none named, not loaded, or not CityWorld). */
    private CityWorldChunkGenerator twinSource() {
        if (twinOf.isEmpty())
            return null;
        net.minecraft.server.MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        net.minecraft.server.level.ServerLevel source = server == null ? null : server.getLevel(twinOf.get());
        if (source != null && source.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator cw && cw != this)
            return cw;
        me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                "CityWorld: twin_of {} is not a loaded CityWorld dimension — using this dimension's own style/settings",
                twinOf.get().identifier());
        return null;
    }

    /**
     * The per-world context, created on first use.
     *
     * @param level supplies the world's real vertical bounds; every caller is already generating
     *              for a chunk, so it has one to hand
     */
    private CityWorldGenerator context(LevelHeightAccessor level) {
        CityWorldGenerator local = context;
        if (local == null) {
            synchronized (this) {
                local = context;
                if (local == null) {
                    // In practice the level's structure state is built before any chunk generates,
                    // so the seed is always known by now. Fail loudly rather than trust it: the
                    // context is cached forever, so seeding it wrong would silently give this world
                    // the wrong terrain for its entire life — with no symptom to trace back.
                    if (!levelSeedKnown)
                        throw new IllegalStateException(
                                "CityWorld: chunk generation began before createState() supplied the world seed, "
                                        + "so the per-world context cannot be seeded. Terrain would be wrong for "
                                        + "this world. Find another way to obtain the seed.");
                    CityWorldSettingsData settingsData =
                            settings.map(Holder::value).orElse(CityWorldSettingsData.DEFAULT);
                    CityWorldGenerator.WorldStyle worldStyle = CityWorldGenerator.parseStyle(style);
                    // A twin plans with its source's style and settings, so both are the same city.
                    CityWorldChunkGenerator twin = twinSource();
                    if (twin != null) {
                        worldStyle = twin.resolvedStyle();
                        settingsData = twin.resolvedSettings();
                        twinResolved = true;
                    }
                    // The End is never ruined, whatever it mirrors: the dragon kept everyone away until it was
                    // killed, so its city stands as built while the overworld's fell (owner, 2026-09-17 — and why
                    // ZARP's voidlings only turn up afterwards). Applied here as well as in the presets, so an End
                    // created before the presets said so is pristine too. An explicit "decayed" still wins.
                    Optional<Boolean> decay = decayed.isEmpty() && isEnd() ? Optional.of(false) : decayed;
                    local = new CityWorldGenerator(levelSeed, TERRAIN_CEILING, UPSTREAM_SEA_LEVEL,
                            worldStyle, level.getMinY(), level.getMaxY(), decay, settingsData,
                            parseEnvironment(environment));
                    // The biome source answers getNoiseBiome from this context (terrain height + climate),
                    // so hand it over the moment it exists — this is the earliest point it can be had.
                    if (this.biomeSource instanceof CityWorldBiomes cityBiomes)
                        cityBiomes.bindContext(local);
                    // The End plans against vanilla's islands, so its planner needs vanilla's noise before the
                    // first platmap is asked for — and the biome source reads the same field for its biomes.
                    if (isEnd()) {
                        vanillaEnd();
                        local.endTerrain = endTerrain;
                        if (this.biomeSource instanceof CityWorldEndBiomeSource endBiomes)
                            endBiomes.bindTerrain(endTerrain, vanillaEnd.getBiomeSource());
                    }
                    // Where vanilla's structures are going, so the planner can leave them room. Built
                    // eagerly: this runs exactly once per world and provably after createState (the seed
                    // check above would have thrown otherwise), so there is nothing to defer.
                    local.structureReservations = StructureReservations.of(structureState,
                            StructureReservations.DEFAULT_CLEARANCE);
                    context = local;
                }
            }
        }
        return local;
    }

    /**
     * The per-world CityWorld context, for read-only callers outside generation — the
     * {@code /cityinfo} and {@code /cityworld} commands. Same lazily-built, seed-checked context the
     * generation path uses, so it plans identically.
     */
    public CityWorldGenerator getContext(LevelHeightAccessor level) {
        return context(level);
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    /**
     * Biome sink for the shaper.
     *
     * <p>CityWorld pushes a biome per column while it shapes, but modern worldgen fills biomes in a
     * <em>separate</em> pass ({@link #createBiomes}) that runs before terrain, so this sink drops the
     * shaper's columns. That is no longer a loss: {@link #createBiomes} now reproduces the same
     * height-band classification against a {@link CityWorldBiomeSource}, so grass/water/foliage
     * colour and biome mobs vary with the land. The shaper's own biome push is kept only because
     * removing it would touch every {@code ShapeProvider}.
     */
    private static final BiomeGrid IGNORE_BIOMES = (x, z, biome) -> {
    };

    /**
     * The End's central zone — vanilla's own radius, 64 sections (1,024 blocks) around the origin, the same
     * test {@code TheEndBiomeSource} uses. Inside it the world is generated by a real vanilla End generator
     * ({@link #vanillaEnd()}), so the central island, the obsidian pillars, the exit podium and the void ring
     * are exactly what the dragon fight expects; CityWorld's islands start beyond it.
     */
    private boolean isEnd() {
        return parseEnvironment(environment) == me.daddychurchill.CityWorld.compat.Environment.THE_END;
    }

    private boolean inEndCentre(ChunkAccess chunk) {
        if (!isEnd())
            return false;
        long x = chunk.getPos().getMinBlockX() + 8, z = chunk.getPos().getMinBlockZ() + 8;
        return x * x + z * z <= 1024L * 1024L;
    }

    /** Vanilla's End generator, built once: {@code NoiseBasedChunkGenerator(TheEndBiomeSource, end noise)}. */
    private net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator vanillaEnd() {
        var local = vanillaEnd;
        if (local == null)
            synchronized (this) {
                local = vanillaEnd;
                if (local == null) {
                    var registries = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer().registryAccess();
                    var biomes = registries.lookupOrThrow(Registries.BIOME);
                    var settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                            .getOrThrow(net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END);
                    vanillaEndRandom = RandomState.create(registries,
                            net.minecraft.world.level.levelgen.NoiseGeneratorSettings.END, levelSeed);
                    endTerrain = new EndTerrain(vanillaEndRandom, settings.value().noiseSettings());
                    local = new net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator(
                            net.minecraft.world.level.biome.TheEndBiomeSource.create(biomes), settings);
                    // As TerraBlender would have done for a vanilla End stem — see TerraBlenderBridge.initializeEnd.
                    // Before the field is published, so no thread meets a half-initialised biome source.
                    TerraBlenderBridge.initializeEnd(registries, registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                            .getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.END), local, levelSeed);
                    vanillaEnd = local;
                }
            }
        return local;
    }

    private volatile net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator vanillaEnd;
    /** The End noise's own random state — the one MC hands us is a dummy for a non-noise generator. */
    private volatile RandomState vanillaEndRandom;
    /** The same noise, asked for heights without generating — what the End's planner and biomes read. */
    private volatile EndTerrain endTerrain;

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        // The End's terrain is vanilla's everywhere: the dragon's island in the centre, and beyond the void ring
        // the outer islands exactly as vanilla grows them. CityWorld then builds on top of what is there
        // (ShapeProvider_TheEnd shapes nothing) — except in the centre, which stays the dragon's alone.
        if (isEnd()) {
            context(chunk);
            CompletableFuture<ChunkAccess> islands =
                    vanillaEnd().fillFromNoise(blender, vanillaEndRandom, structureManager, chunk);
            return inEndCentre(chunk) ? islands : islands.thenApply(filled -> {
                if (!endStructureHere(structureManager, filled))
                    buildCity(structureManager, filled);
                return filled;
            });
        }
        buildCity(structureManager, chunk);
        return CompletableFuture.completedFuture(chunk);
    }

    /**
     * Whether a vanilla structure (in practice an end city) has a piece over this End chunk. CityWorld then draws
     * nothing here and vanilla decorates it, so the two are never built through each other.
     *
     * <p>The planner cannot know: end cities are placed from the structure manager, which exists per chunk at
     * generation and not when a platmap is planned. Measured 2026-09-17, 2 of 7 located end cities started inside
     * a CityWorld lot (a store, a road). The plan still says "road" for such a chunk; the street simply stops at
     * the end city's foot, which is how a city that grew around one would look anyway.
     */
    private boolean endStructureHere(StructureManager structureManager, ChunkAccess chunk) {
        if (!isEnd())
            return false;
        try {
            ChunkPos pos = chunk.getPos();
            for (var start : structureManager.startsForStructure(pos, structure -> true))
                for (var piece : start.getPieces()) {
                    var box = piece.getBoundingBox();
                    if (box.maxX() >= pos.getMinBlockX() && box.minX() <= pos.getMaxBlockX()
                            && box.maxZ() >= pos.getMinBlockZ() && box.minZ() <= pos.getMaxBlockZ())
                        return true;
                }
        } catch (Throwable t) {
            // never let a structure lookup break chunk generation
        }
        return false;
    }

    /** CityWorld's half of {@link #fillFromNoise}: plan the chunk's lot and let it draw. */
    private void buildCity(StructureManager structureManager, ChunkAccess chunk) {
        CityWorldGenerator context = context(chunk);
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();

        InitialBlocks blocks = new InitialBlocks(context, chunk, chunkX, chunkZ);

        // Fetch (or plan) the city block this chunk belongs to, then let it drive. The platmap
        // routes to whichever lot owns this chunk, and the lot calls the shape provider itself —
        // so terrain and city come from one path rather than two.
        PlatMap platmap = context.getPlatMap(chunkX, chunkZ);

        // Smooth the PLANNED ground under any surface structure BEFORE terrain is drawn from it.
        // Order is the whole fix: the previous pad ran after this line and rewrote blocks, leaving the
        // planned heights untouched, so decoration later painted a surface at the old level over a void.
        // The missing half of vanilla's beard_thin contract: bend the planned ground to each piece.
        // Measured 745/745 columns seated exactly, 0 buried; with it off, 70.4% and 32.4%. PAD_ENABLED.
        if (PAD_ENABLED)
            padPlanForStructures(context, structureManager, chunk, platmap);

        platmap.generateChunk(blocks, IGNORE_BIOMES);

        // Make room for any structure that expects the terrain to get out of its way. Vanilla does this
        // in the same pass, via the Beardifier density function — see carveForStructures.
        //
        // A BURIED structure needs terrain removed (a cavern); that is carveForStructures, and it is
        // correct because it only ever deletes blocks that the plan already considers underground.
        //
        // ⚠ The SURFACE half — padForSurfaceStructures — is DISABLED, and deliberately not called.
        // It rewrote blocks at this stage while AbstractCachedYs.blockYs (a final double[][] with no
        // mutator) kept the ORIGINAL planned heights. Decoration then painted grass, snow and biome
        // ground at those planned heights, so wherever the pad had shaved the ground away it laid a
        // floating lid over a void: measured at 28.5% of columns in one village's footprint, with
        // cavities up to 20 blocks tall (2026-09-21). Everything downstream of the plan — the surface
        // pass, getMaxYWithin, foundations, flood, the mine loops — trusts those cached heights, so
        // rewriting blocks behind their back desynchronises the world from its own plan.
        //
        // The rework is to smooth the PLAN: adjust the cached heights before terrain is drawn (and
        // recompute the min/max/average they derive), so terrain, surface and every other consumer
        // follow one agreed ground level. See padForSurfaceStructures for the parts worth keeping.
        carveForStructures(context, structureManager, chunk);
    }

    /**
     * Clears terrain out of the way of structures whose {@code terrain_adaptation} is a <em>beard</em>.
     *
     * <p><b>This is CityWorld's stand-in for vanilla's {@code Beardifier}, and without it ancient cities
     * generate buried.</b> Vanilla feeds {@code Beardifier.forStructuresInChunk} into the density
     * function that shapes terrain, so the ground is pushed away from a structure before a single block
     * is placed. CityWorld's terrain is not density-based — the ported shaper writes blocks directly —
     * so there is nothing to feed, and the structure ends up stamped into solid rock.
     *
     * <p>Which structures need it is exactly the {@code terrain_adaptation} field, and only the beards
     * carve:
     * <ul>
     *   <li>{@code stronghold} is {@code bury} — vanilla piles terrain <em>onto</em> it, and it already
     *       generates correctly here. Carving would be wrong.
     *   <li>{@code trial_chambers} is {@code encapsulate}, and likewise already correct.
     *   <li>{@code ancient_city} is {@code beard_box} — the one that was broken.
     * </ul>
     *
     * <p>Carving the piece's own bounding box is a fair stand-in: for {@code BEARD_BOX}, vanilla's
     * vertical offset is zero anywhere inside the box, so the beard is at full strength throughout it
     * and the 12-block kernel only softens the edges. We lose the soft edge, not the cavern.
     */
    private void carveForStructures(CityWorldGenerator context, StructureManager structureManager,
            ChunkAccess chunk) {
        try {
            int halo = Math.max(0, context.getSettings().caves.structureCarveHalo());
            int haloUp = Math.max(0, context.getSettings().caves.structureCarveHaloUp());
            ChunkPos pos = chunk.getPos();
            // Bearded structures, plus anything a datapack asks a cavern for (#cityworld:carve_cavern — bastions,
            // whose fixed start at y 33 buries them under a full-height city).
            var cavern = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(CARVE_CAVERN);
            List<net.minecraft.world.level.levelgen.structure.StructureStart> starts =
                    structureManager.startsForStructure(pos, structure -> carvesTerrain(structure)
                            || cavern.map(set -> set.stream().anyMatch(h -> h.value() == structure)).orElse(false));
            if (starts.isEmpty())
                return;

            // ⚠ A cavern is right for something BURIED and catastrophic for something standing on the
            // ground. Vanilla never digs a pit: it runs Beardifier during noise generation, which SHAPES
            // terrain around a structure. We have no Beardifier — CityWorld lays its own terrain — so we
            // emulate by deleting, and deleting a surface structure's box plus a 10-block halo open-casts
            // a square quarry around it. BEARD_THIN is what vanilla villages, outposts and pillager towers
            // use, and Cataclysm's desert_site/abandoned_* too, so this disfigured every surface structure
            // anyone enabled. Owner, 2026-09-21: "to a non-tech regular player it reads as a chunk error".
            //
            // So carve only for a start that is genuinely underground — its whole box at or below the
            // natural ground at its own centre — or one a datapack explicitly asked a cavern for. That
            // keeps ancient cities, Cataclysm's ancient_factory (y -30) and tagged bastions carving,
            // and leaves anything standing on the surface to sit on the land as vanilla intends.
            // Ground comes from the shaper that laid the terrain, so it cannot disagree with it.
            starts = starts.stream().filter(start -> {
                if (cavern.map(set -> set.stream().anyMatch(h -> h.value() == start.getStructure())).orElse(false))
                    return true;
                var box = start.getBoundingBox();
                return box.maxY() <= context.shapeProvider.findBlockY(context,
                        (box.minX() + box.maxX()) / 2, (box.minZ() + box.maxZ()) / 2);
            }).toList();
            if (starts.isEmpty())
                return;

            int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();
            // Only the piece boxes whose *halo* reaches this chunk matter. Collected first so the
            // per-block loop can take the nearest piece rather than re-walking every piece of a
            // 7-deep jigsaw for every block.
            List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new java.util.ArrayList<>();
            int regionMinY = Integer.MAX_VALUE, regionMaxY = Integer.MIN_VALUE;
            for (net.minecraft.world.level.levelgen.structure.StructureStart start : starts)
                for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox box = piece.getBoundingBox();
                    if (box.maxX() + halo < minX || box.minX() - halo > minX + 15
                            || box.maxZ() + halo < minZ || box.minZ() - halo > minZ + 15)
                        continue;
                    boxes.add(box);
                    regionMinY = Math.min(regionMinY, box.minY());
                    regionMaxY = Math.max(regionMaxY, box.maxY() + haloUp);
                }
            if (boxes.isEmpty())
                return;

            net.minecraft.world.level.block.state.BlockState air =
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
            me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator noise = carveNoise();

            // minY + 1 keeps the bedrock floor intact, exactly as vanilla's writable area does.
            int y0 = Math.max(regionMinY, chunk.getMinY() + 1);
            int y1 = Math.min(regionMaxY, chunk.getMaxY());

            for (int x = minX; x <= minX + 15; x++)
                for (int z = minZ; z <= minZ + 15; z++)
                    for (int y = y0; y <= y1; y++) {
                        double t = outsideness(boxes, x, y, z, halo, haloUp);
                        if (t >= 1.0)
                            continue;
                        // Inside a piece box, always carve. Outside, carve with a probability that
                        // falls off to nothing at the halo's edge — driven by smooth noise rather than
                        // randomness so the result is a ragged cave wall, not static.
                        if (t > 0.0
                                && noise.noise(x * CARVE_NOISE_SCALE, y * CARVE_NOISE_SCALE, z * CARVE_NOISE_SCALE)
                                        <= t * 2.0 - 1.0)
                            continue;
                        cursor.set(x, y, z);
                        if (!chunk.getBlockState(cursor).isAir())
                            chunk.setBlockState(cursor, air);
                    }
        } catch (Throwable t) {
            // never let terrain adaptation break chunk generation
        }
    }

    /** How far a pad eases back to natural ground. Vanilla's beard falls off over roughly this much. */
    /**
     * Whether the beard runs. <b>ON by default; {@code -Dcityworld.structurepad=false} disables it.</b>
     *
     * <p>CityWorld lays its own terrain and never runs vanilla's {@code Beardifier}, so for a jigsaw
     * structure only half of vanilla's contract was ever honoured: villages declare
     * {@code terrain_adaptation: beard_thin} and {@code project_start_to_heightmap: WORLD_SURFACE_WG},
     * meaning "project the start once, then bend the terrain to each piece" — and nothing bent the
     * terrain. Hence houses in mid-air. This is that missing half.
     *
     * <p><b>Measured 2026-09-22, one taiga village, 31 RIGID pieces, 745 columns, judged against each
     * piece's OWN declared support level</b> ({@code scripts/region_pieceground.py}, the only metric
     * here that classifies nothing):
     * <table>
     *   <tr><th></th><th>beard OFF</th><th>beard ON</th></tr>
     *   <tr><td>seated exactly</td><td>499/709 (70.4%)</td><td><b>745/745 (100.0%)</b></td></tr>
     *   <tr><td>hanging</td><td>210 columns, tail to −17</td><td><b>0</b></td></tr>
     *   <tr><td>buried</td><td>230/709 (32.4%)</td><td><b>0</b></td></tr>
     *   <tr><td>lid-like crust</td><td>3.2%</td><td><b>0.0%</b></td></tr>
     *   <tr><td>enclosed natural air</td><td>37.0% / 623</td><td><b>34.7% / 552</b></td></tr>
     * </table>
     * The worst beard-OFF case is x1216..1220, z−769..−773: target 92, ground 75 — a path and house
     * seventeen blocks up. The metric fails in both directions on the control, so the 100% is a
     * result and not a tautology.
     *
     * <p><b>What made it work, after two failed attempts.</b> The reference level is
     * {@code box.minY() + getGroundLevelDelta()}, per PIECE — the delta is how far a piece's floor
     * sits above its box bottom, it is a field on {@code PoolElementStructurePiece}, and it was simply
     * missing. Aiming at {@code box.minY()} could not work for any constant, because the delta varies
     * per piece. Also from vanilla rather than from eye: radius 12 ({@code BEARD_KERNEL_RADIUS}), only
     * RIGID pieces ({@code TERRAIN_MATCHING} carry a {@code GravityProcessor} and drop deliberately),
     * and starts declaring {@code NONE} excluded.
     *
     * <p>That exclusion matters: a desert pyramid is a {@code ScatteredFeaturePiece} and re-levels
     * ITSELF via {@code updateHeightPositionToLowestGroundHeight}. Beard on and off produced
     * layer-for-layer identical pyramids (140 cut_sandstone, 46 chiseled, 17 stairs both times), so
     * shaping for it was pointless and mildly harmful. It is skipped.
     *
     * <p><b>⚠ Do not compare any of this with figures quoted before {@code fd9ab12b}.</b> {@code is_solid}
     * substring-matched, so GRASS_BLOCK and SNOW_BLOCK counted as not solid — the two commonest ground
     * blocks in a snowy taiga. Every measurement before that fix is skewed the same way, including the
     * ones in {@code 413d525f} and {@code 6e0bfb83}.
     *
     * <p>Reservation is independent of this gate ({@link StructureReservations}): terrain is kept clear
     * around structures either way.
     */
    private static final boolean PAD_ENABLED =
            Boolean.parseBoolean(System.getProperty("cityworld.structurepad", "true"));

    /**
     * How far the beard reaches, horizontally, in blocks. <b>12 because that is
     * {@code Beardifier.BEARD_KERNEL_RADIUS}</b> — vanilla's kernel is 24 wide and every piece is
     * gathered with {@code isCloseToChunk(pos, 12)}. The first two attempts used 8, picked by eye.
     */
    private static final int BEARD_RADIUS = 12;

    /**
     * Blocks of taper for a structure that DECLARES {@code clearance} chunks. 12 is right against a
     * village house and far too tight against something eight chunks across — Cataclysm's
     * frosted_prison blended onto its footprint and then ended in a sheer wall of snow. An UNDECLARED
     * structure must get {@link #BEARD_RADIUS} exactly and never this: feeding the default clearance
     * of 5 through gives 76, which widened every village's gather from 625 to 23,409 blocks^2 and
     * timed the self-test out.
     */
    private static int beardRadiusFor(int clearanceChunks) {
        return Math.max(BEARD_RADIUS, clearanceChunks * 16 - 4);
    }

    private static final int PAD_TAPER = 8;

    /**
     * Whether the pad narrates what it decides, per start and per chunk.
     *
     * <p><b>Why this exists.</b> Judging the pad from the finished world does not work: a height
     * transect through a desert pyramid showed flat sand at y64 butting straight into the structure with
     * no dish and no taper (2026-09-21), and that single observation is consistent with two completely
     * different faults — the start being skipped as buried, or the pad running and having nothing to do
     * because the ground was already at the target. Those want opposite fixes, and no amount of reading
     * blocks afterwards can separate them. So the pad says which branch it took.
     *
     * <p>Automatically on under a probe, because that is exactly when someone is asking. Off otherwise:
     * a line per start per chunk would be in every user's log forever for the sake of one afternoon.
     */
    private static final boolean PAD_LOG = System.getProperty("cityworld.padlog") != null
            || System.getProperty("cityworld.probe") != null
            || System.getProperty("cityworld.diagnostics") != null;

    /**
     * Smooths the <b>planned</b> ground under a surface structure, before terrain is drawn from it.
     *
     * <p>This is the replacement for the block-rewriting pad below, and the difference is the whole
     * point: it edits {@code AbstractCachedYs.blockYs}, which {@code PlatLot.generateChunk} hands to the
     * shape provider at the terrain stage AND {@code PlatLot.generateSurface} hands to the surface
     * provider at decoration. One source of truth, so terrain and surface cannot disagree. The old pad
     * moved blocks and left those heights alone, which put a floating lid over a cavity in 28.5% of one
     * village's columns.
     *
     * <p>Kept from the old one because they were right: buried and {@code #cityworld:carve_cavern}
     * starts belong to {@link #carveForStructures}, and the target is an inverse-square distance-weighted
     * blend of nearby piece bases rather than the nearest one — snapping to the nearest gave adjacent
     * columns targets up to 110 blocks apart on a mountainside village and built the step between them.
     *
     * <p>One-shot per chunk ({@code isPadded}): blending a second time would weigh ground this had
     * already moved. Heights are indexed 0..15 within the chunk while piece distances are world
     * coordinates — easy to conflate, and silently wrong if conflated.
     */
    /**
     * Whether a {@code terrain_adaptation: none} structure has opted in to being bearded.
     *
     * <p>Opt-in only, and nothing is opted in by default. Vanilla's {@code NONE} structures either
     * re-level themselves ({@code desert_pyramid}, {@code jungle_pyramid}, {@code swamp_hut} are
     * {@code ScatteredFeaturePiece}s) or are deliberately not sitting on the ground — a ruined portal
     * is half-buried on purpose, a bastion floats over lava, a shipwreck lies on the seabed. And
     * {@link #buildCity} runs in EVERY dimension, so a blanket rule would reach the Nether and the End.
     * A mod whose pieces are template-based and expect pre-existing terrain (Cataclysm's cursed pyramid
     * has no self-levelling call anywhere in its 1344 classes) declares itself in the data map instead.
     */
    private static final java.util.Map<Object, java.util.Map<
            net.minecraft.world.level.levelgen.structure.Structure,
            me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.StructureFit>> FITS_BY_STRUCTURE =
                    java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /** Declared fits by structure, one cached walk per world. */
    private static java.util.Map<net.minecraft.world.level.levelgen.structure.Structure,
            me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.StructureFit> fitsByStructure(
                    net.minecraft.core.HolderLookup.RegistryLookup<
                            net.minecraft.world.level.levelgen.structure.Structure> lookup) {
        var cached = FITS_BY_STRUCTURE.get(lookup);
        if (cached != null)
            return cached;
        java.util.Map<net.minecraft.world.level.levelgen.structure.Structure,
                me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.StructureFit> out =
                        new java.util.IdentityHashMap<>();
        try {
            lookup.listElements().forEach(reference -> {
                var f = me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.fitFor(reference);
                if (f != null)
                    out.put(reference.value(), f);
            });
        } catch (Throwable t) {
            return java.util.Map.of();
        }
        FITS_BY_STRUCTURE.put(lookup, out);
        return out;
    }

    private static java.util.Set<net.minecraft.world.level.levelgen.structure.Structure> beardOptIn(
            net.minecraft.core.HolderLookup.RegistryLookup<
                    net.minecraft.world.level.levelgen.structure.Structure> lookup) {
        // ⚠ Resolved ONCE per chunk, not once per start. The first cut scanned the whole structure
        // registry inside the startsForStructure predicate, so it ran per start per chunk -- bounded,
        // but needless work in the hot worldgen path. Deliberately NOT cached across calls: data maps
        // are reload-scoped, and a stale cache would survive /reload and quietly disagree with the pack.
        java.util.Set<net.minecraft.world.level.levelgen.structure.Structure> out =
                java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        try {
            lookup.listElements().forEach(reference -> {
                if (me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.beardsAnyway(reference))
                    out.add(reference.value());
            });
        } catch (Throwable t) {
            return java.util.Set.of();
        }
        return out;
    }

    private void padPlanForStructures(CityWorldGenerator context, StructureManager structureManager,
            ChunkAccess chunk, PlatMap platmap) {
        try {
            ChunkPos pos = chunk.getPos();
            me.daddychurchill.CityWorld.Plats.PlatLot lot = platmap.getMapLot(pos.x(), pos.z());
            if (lot == null)
                return;
            me.daddychurchill.CityWorld.Support.AbstractCachedYs ys = lot.getCachedYs();
            if (ys == null || ys.isPadded())
                return;

            // Exactly vanilla's filter (Beardifier.forStructuresInChunk): a structure declaring NONE
            // wants no help. That is not laziness — a desert pyramid is a ScatteredFeaturePiece and
            // RE-LEVELS ITSELF at placement time via updateHeightPositionToLowestGroundHeight, which
            // moves its whole bounding box onto whatever ground it finds. Measured 2026-09-22: pad off
            // and pad on gave layer-for-layer identical pyramids, offset only by the 1 block the pad
            // had moved the ground. Shaping for it is pointless at best and fights it at worst.
            var structureLookup = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE);
            var optedIn = beardOptIn(structureLookup);
            List<net.minecraft.world.level.levelgen.structure.StructureStart> starts =
                    structureManager.startsForStructure(pos,
                            structure -> structure.terrainAdaptation()
                                    != net.minecraft.world.level.levelgen.structure.TerrainAdjustment.NONE
                                    || optedIn.contains(structure));
            if (starts.isEmpty())
                return;

            var cavern = structureLookup.get(CARVE_CAVERN);
            int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();

            // One entry per piece: its footprint, and the ground level it actually wants underneath.
            record Beard(int minX, int minZ, int maxX, int maxZ, double top, int taper) {
            }
            List<Beard> beards = new java.util.ArrayList<>();
            var fitsIndex = fitsByStructure(structureLookup);

            for (net.minecraft.world.level.levelgen.structure.StructureStart start : starts) {
                // ⚠ TAPER SCALING WITHDRAWN — it removed the gradient instead of widening it.
                // The blend is nearest = dist/taper, ease = smoothstep(nearest); a bigger taper makes
                // nearest tiny for every nearby column, so ease -> 0 and the column SNAPS to the flat
                // target rather than easing back to natural ground. The owner saw exactly that:
                // "so is tapering gone completely".
                //
                // And it could never have worked anyway: this method only runs where
                // startsForStructure(pos, ...) returns something, and structure references exist only
                // for chunks the bounding box overlaps. No taper value slopes terrain OUTSIDE the
                // footprint; that needs starts gathered from neighbouring chunks, which is a separate
                // change. beardRadiusFor is kept for when that lands.
                int taper = BEARD_RADIUS;
                String id = PAD_LOG ? String.valueOf(start.getStructure()) : "";
                if (PAD_LOG && optedIn.contains(start.getStructure()))
                    LOGGER_STRUCTURES.warn("PLANPAD chunk {},{}: OPT-IN beard (terrain_adaptation none) — {}",
                            pos.x(), pos.z(), id);
                var whole = start.getBoundingBox();
                if (cavern.map(set -> set.stream().anyMatch(h -> h.value() == start.getStructure())).orElse(false)) {
                    if (PAD_LOG)
                        LOGGER_STRUCTURES.warn("PLANPAD chunk {},{}: SKIP cavern — {}", pos.x(), pos.z(), id);
                    continue;
                }
                int groundAtCentre = context.shapeProvider.findBlockY(context,
                        (whole.minX() + whole.maxX()) / 2, (whole.minZ() + whole.maxZ()) / 2);
                if (whole.maxY() <= groundAtCentre) {
                    if (PAD_LOG)
                        LOGGER_STRUCTURES.warn("PLANPAD chunk {},{}: SKIP buried — {} box y {}..{} vs ground {}",
                                pos.x(), pos.z(), id, whole.minY(), whole.maxY(), groundAtCentre);
                    continue;
                }
                for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                    if (!piece.isCloseToChunk(pos, taper))
                        continue;
                    int delta = 0;
                    if (piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pool) {
                        // TERRAIN_MATCHING pieces carry a GravityProcessor and drop onto the ground on
                        // purpose; vanilla beards only RIGID ones, and so do we.
                        if (pool.getElement().getProjection()
                                != net.minecraft.world.level.levelgen.structure.pools.StructureTemplatePool.Projection.RIGID) {
                            if (PAD_LOG) {
                                var rb = piece.getBoundingBox();
                                LOGGER_STRUCTURES.warn(
                                        "PLANPAD chunk {},{}: REJECT non-rigid {} box x {}..{} z {}..{} y {}..{}",
                                        pos.x(), pos.z(), pool.getElement().getProjection(),
                                        rb.minX(), rb.maxX(), rb.minZ(), rb.maxZ(), rb.minY(), rb.maxY());
                            }
                            continue;
                        }
                        // ⚠ THE FIELD THIS WHOLE FEATURE TURNED ON. Beardifier's reference level is
                        // box.minY() + groundLevelDelta, not box.minY(): the delta is how far the
                        // piece's own floor sits above the bottom of its box. Two earlier attempts
                        // aimed at minY-1 and left houses hanging — 1683 columns at a uniform 2 and a
                        // tail to 12 — because the delta varies per piece and was simply missing. It
                        // was never a constant to tune; it was a field to read.
                        delta = pool.getGroundLevelDelta();
                    }
                    var b = piece.getBoundingBox();
                    if (PAD_LOG)
                        LOGGER_STRUCTURES.warn(
                                "PLANPAD chunk {},{}: BEARD {} {} box x {}..{} z {}..{} y {}..{} delta {} -> top {}",
                                pos.x(), pos.z(), piece.getClass().getSimpleName(),
                                piece instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pl
                                        ? pl.getElement().getProjection() : "n/a",
                                b.minX(), b.maxX(), b.minZ(), b.maxZ(), b.minY(), b.maxY(), delta,
                                b.minY() + delta - 1);
                    beards.add(new Beard(b.minX(), b.minZ(), b.maxX(), b.maxZ(), b.minY() + delta - 1, taper));
                }
            }
            if (beards.isEmpty())
                return;

            int moved = 0;
            double deltaMin = Double.MAX_VALUE, deltaMax = -Double.MAX_VALUE;
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int wx = minX + x, wz = minZ + z;   // plan is chunk-local, boxes are world coords
                    double nearest = 1.0, weightSum = 0.0, baseSum = 0.0;
                    double insideTop = Double.NEGATIVE_INFINITY;
                    for (Beard b : beards) {
                        int dx = Math.max(0, Math.max(b.minX() - wx, wx - b.maxX()));
                        int dz = Math.max(0, Math.max(b.minZ() - wz, wz - b.maxZ()));
                        double dist = Math.max(dx, dz);
                        if (dx == 0 && dz == 0)
                            // Under a piece: the HIGHEST floor wins, so an overlapping piece is never
                            // buried by a lower neighbour.
                            insideTop = Math.max(insideTop, b.top());
                        double d = dist / (double) b.taper();
                        if (d < nearest)
                            nearest = d;
                        double w = 1.0 / (dist * dist + 1.0);
                        weightSum += w;
                        baseSum += w * b.top();
                    }
                    if (nearest >= 1.0 || weightSum <= 0.0)
                        continue;

                    double natural = ys.getPerciseY(x, z);
                    double target;
                    if (insideTop != Double.NEGATIVE_INFINITY) {
                        // Under a piece the ground is EXACTLY the block it stands on, never a blend:
                        // blending here averaged in every other piece in range and dragged the ground
                        // below the floor almost everywhere.
                        target = insideTop;
                    } else {
                        // Outside every footprint: blend toward nearby floors and ease back to natural
                        // ground over BEARD_RADIUS. Snapping to the nearest instead gave adjacent
                        // columns targets up to 110 blocks apart on a mountainside and built the step.
                        double blended = baseSum / weightSum;
                        double ease = nearest * nearest * (3.0 - 2.0 * nearest);
                        target = blended + (natural - blended) * ease;
                    }
                    if (Math.abs(target - natural) >= 0.5) {
                        moved++;
                        deltaMin = Math.min(deltaMin, target - natural);
                        deltaMax = Math.max(deltaMax, target - natural);
                    }
                    ys.setPerciseY(x, z, target);
                }

            // Derived state must follow: calcMinMax only widens, and getMinHeight() drives the mine
            // level loops and calcState decides sea/buildable/peak.
            ys.recompute(context);
            ys.markPadded();
            if (PAD_LOG)
                LOGGER_STRUCTURES.warn("PLANPAD chunk {},{}: {} of 256 columns moved over {} beards, delta {} .. {}",
                        pos.x(), pos.z(), moved, beards.size(),
                        deltaMin == Double.MAX_VALUE ? 0 : Math.round(deltaMin),
                        deltaMax == -Double.MAX_VALUE ? 0 : Math.round(deltaMax));
        } catch (Throwable t) {
            // getMapLot can throw IndexOutOfBounds; nothing here may break terrain generation.
            if (PAD_LOG)
                LOGGER_STRUCTURES.warn("PLANPAD chunk {},{}: FAILED", chunk.getPos().x(), chunk.getPos().z(), t);
        }
    }

    /**
     * ⚠ <b>DISABLED — not called from {@link #buildCity}. Do not re-enable as-is.</b>
     *
     * <p>This approach is wrong at the root, not mis-tuned. It rewrites BLOCKS during terrain
     * generation, but the planned column heights live in {@code AbstractCachedYs.blockYs} — a
     * {@code final double[][]} built once from {@code shapeProvider.findPerciseY}, with no setter
     * anywhere — and this never updated them. {@code PlatLot.generateSurface} hands that same array to
     * the surface provider at DECORATION time, so grass, snow and biome ground were painted at the
     * original heights: wherever this had shaved ground away, the result was a floating lid over a
     * cavity. Measured on the owner's world, 2026-09-21: <b>28.5% of columns</b> in one village's
     * footprint had enclosed air within 25 blocks of the surface, 18,653 void blocks, runs up to 20
     * tall. It was invisible to a top-down render and to height transects, because both report the
     * topmost solid block — that is, the lid.
     *
     * <p>Kept, rather than deleted, because the parts worth reusing are here: the buried/cavern
     * classification, gathering piece boxes within a taper of the chunk, the inverse-square blend of
     * nearby piece bases, and the instrumentation. The rework should apply that blend to the PLAN —
     * the cached heights, before terrain is drawn, with the derived min/max/average recomputed — so
     * that terrain, the surface pass and every other {@code getBlockY} consumer agree on one ground.
     *
     * <p>Below is the original description, kept for the rework.
     *
     * <p>Levels the ground <em>under</em> a surface structure — our stand-in for vanilla's Beardifier.
     *
     * <p><b>Why this exists.</b> Vanilla shapes terrain around a {@code BEARD_THIN}/{@code BEARD_BOX}
     * structure inside noise generation, via {@code Beardifier}. CityWorld lays its own terrain and
     * never runs that, so before this a village simply sat at its own fixed Y with the hillside
     * stepping away underneath — houses on visible platforms, worst on slopes (owner, 2026-09-21).
     * The previous stand-in was {@link #carveForStructures}, which hollowed out the piece boxes plus a
     * 10-block halo; on a surface structure that open-casts a quarry, which is why it now runs only for
     * buried starts. This is the other half: buried structures get a cavern, surface ones get a pad.
     *
     * <p><b>It fills widely and shaves narrowly, and that asymmetry is the whole design.</b> Filling
     * outward across the taper is what removes the platform edge. Shaving outward is what produced the
     * quarry — so terrain is only ever removed <em>inside</em> a piece box, where the structure is about
     * to be built anyway, and never in the taper. The worst case is therefore a structure sitting on a
     * gentle mound, never a pit cut into the landscape.
     *
     * <p>Fill uses {@code oreProvider}'s own surface/subsurface materials, so a pad is snow on Astral,
     * end stone in the End, netherrack in the Nether and sandstone on dunes rather than a dirt scar.
     */
    private void padForSurfaceStructures(CityWorldGenerator context, StructureManager structureManager,
            ChunkAccess chunk) {
        try {
            ChunkPos pos = chunk.getPos();
            // EVERY start, not only the beard-declaring ones. terrain_adaptation describes what
            // vanilla's NOISE pass would have done, not what a structure needs from us — and CityWorld
            // never runs that pass at all. Cataclysm's cursed_pyramid declares "none", so gating on the
            // beard skipped it entirely and it sat in untouched terrain with a hard diagonal edge
            // (owner, 2026-09-21). Vanilla's own mansions, monuments, shipwrecks, desert pyramids and
            // end cities all declare "none" too, so the gate was excluding most of what people enable.
            List<net.minecraft.world.level.levelgen.structure.StructureStart> starts =
                    structureManager.startsForStructure(pos, structure -> true);
            if (starts.isEmpty())
                return;

            // Anything a datapack asked a cavern for is carveForStructures' business, not ours — a
            // bastion breaks the surface, so the buried test below would not catch it and it would get
            // a pad on top of its cavern.
            var cavern = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(CARVE_CAVERN);

            int minX = pos.getMinBlockX(), minZ = pos.getMinBlockZ();
            List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes = new java.util.ArrayList<>();
            for (net.minecraft.world.level.levelgen.structure.StructureStart start : starts) {
                String id = PAD_LOG ? String.valueOf(start.getStructure()) : "";
                var whole = start.getBoundingBox();
                if (cavern.map(set -> set.stream().anyMatch(h -> h.value() == start.getStructure())).orElse(false)) {
                    if (PAD_LOG)
                        LOGGER_STRUCTURES.warn("PAD chunk {},{}: SKIP cavern — {} box y {}..{}",
                                pos.x(), pos.z(), id, whole.minY(), whole.maxY());
                    continue;
                }
                // Buried starts belong to carveForStructures; only the ones standing on the ground get a pad.
                int groundAtCentre = context.shapeProvider.findBlockY(context,
                        (whole.minX() + whole.maxX()) / 2, (whole.minZ() + whole.maxZ()) / 2);
                if (whole.maxY() <= groundAtCentre) {
                    if (PAD_LOG)
                        LOGGER_STRUCTURES.warn("PAD chunk {},{}: SKIP buried — {} box y {}..{} vs ground {}",
                                pos.x(), pos.z(), id, whole.minY(), whole.maxY(), groundAtCentre);
                    continue;
                }
                int before = boxes.size();
                for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                    net.minecraft.world.level.levelgen.structure.BoundingBox b = piece.getBoundingBox();
                    if (b.maxX() + PAD_TAPER < minX || b.minX() - PAD_TAPER > minX + 15
                            || b.maxZ() + PAD_TAPER < minZ || b.minZ() - PAD_TAPER > minZ + 15)
                        continue;
                    boxes.add(b);
                }
                if (PAD_LOG)
                    LOGGER_STRUCTURES.warn(
                            "PAD chunk {},{}: PAD — {} box y {}..{} vs ground {}, {} of {} pieces in range",
                            pos.x(), pos.z(), id, whole.minY(), whole.maxY(), groundAtCentre,
                            boxes.size() - before, start.getPieces().size());
            }
            if (boxes.isEmpty()) {
                if (PAD_LOG)
                    LOGGER_STRUCTURES.warn("PAD chunk {},{}: nothing to pad — {} start(s), no piece in range",
                            pos.x(), pos.z(), starts.size());
                return;
            }

            net.minecraft.world.level.block.state.BlockState surface =
                    context.oreProvider.surfaceMaterial.getBlockState();
            net.minecraft.world.level.block.state.BlockState subsurface =
                    context.oreProvider.subsurfaceMaterial.getBlockState();
            net.minecraft.world.level.block.state.BlockState air =
                    net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
            net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();

            int floorLimit = chunk.getMinY() + 1; // leave bedrock alone, as the carve does
            int roofLimit = chunk.getMaxY();

            // Counted only to be reported (see PAD_LOG): "ran and moved nothing" and "never ran" look
            // identical in a finished world, and telling them apart is the whole point.
            int padFilled = 0, padShaved = 0, padUnchanged = 0;
            int padDeltaMin = Integer.MAX_VALUE, padDeltaMax = Integer.MIN_VALUE;

            for (int x = minX; x <= minX + 15; x++)
                for (int z = minZ; z <= minZ + 15; z++) {
                    // A distance-weighted BLEND of every nearby piece's base — not the nearest one's.
                    //
                    // Nearest-snap is what produced the cliffs. Measured on the owner's world
                    // (2026-09-21): village_taiga scatters 203 pieces with bases from y66 to y176 up a
                    // mountainside — a 110-block spread. Two adjacent columns nearest to different houses
                    // were therefore handed targets 110 blocks apart, and the pad dutifully built the
                    // step between them. Weighting by distance makes the ground RAMP between houses
                    // instead of stepping, which is the whole difference between terraces and terrain.
                    //
                    // Inverse-square, so the piece you are standing on dominates and distant ones only
                    // bend the result. The box list is already limited to pieces within PAD_TAPER of this
                    // chunk, which is exactly the set that can influence any column in it — so no column
                    // sees a different blend depending on which chunk computed it, and there is no seam.
                    double nearest = 1.0, weightSum = 0.0, baseSum = 0.0;
                    for (net.minecraft.world.level.levelgen.structure.BoundingBox b : boxes) {
                        int dx = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
                        int dz = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
                        double dist = Math.max(dx, dz);
                        double d = dist / (double) PAD_TAPER;
                        if (d < nearest)
                            nearest = d;
                        double w = 1.0 / (dist * dist + 1.0);
                        weightSum += w;
                        baseSum += w * (b.minY() - 1); // the block the structure stands ON
                    }
                    if (nearest >= 1.0 || weightSum <= 0.0)
                        continue;
                    double blended = baseSum / weightSum;

                    int natural = chunk.getHeight(
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
                    // Ease from the blended floor out to natural ground across the taper — smoothstep
                    // rather than linear, so the join has no crease where the pad meets the landscape.
                    double ease = nearest * nearest * (3.0 - 2.0 * nearest);
                    int target = (int) Math.round(blended + (natural - blended) * ease);

                    padDeltaMin = Math.min(padDeltaMin, target - natural);
                    padDeltaMax = Math.max(padDeltaMax, target - natural);

                    if (natural < target) {
                        padFilled++;
                        // FILL — everywhere in the taper. This is what removes the platform edge.
                        for (int y = Math.max(natural + 1, floorLimit); y <= Math.min(target, roofLimit); y++) {
                            cursor.set(x, y, z);
                            chunk.setBlockState(cursor, y == target ? surface : subsurface);
                        }
                    } else if (natural > target) {
                        padShaved++;
                        // SHAVE — across the taper too, not just under the piece, so the structure sits in
                        // ground that eases into the landscape instead of on a shelf cut out of it.
                        //
                        // This is only safe because the ground is RESERVED. Every structure that gets a pad
                        // declares a beard; a structure declaring a beard is in a set containing a surface
                        // structure; and StructureReservations keeps the city out of those. So there is
                        // nothing here to quarry. When the shave was last this generous it ran on
                        // unreserved land with a 10-block halo and open-cast a pit through a farm and a
                        // road grid — the difference is the reservation, not the radius.
                        for (int y = Math.max(target + 1, floorLimit); y <= Math.min(natural, roofLimit); y++) {
                            cursor.set(x, y, z);
                            if (!chunk.getBlockState(cursor).isAir())
                                chunk.setBlockState(cursor, air);
                        }
                    } else {
                        // Target already equals the natural surface. Counted, because a chunk full of
                        // these is a pad that ran and correctly had nothing to do — which is exactly what
                        // the flat-desert pyramid transect could not distinguish from a pad that never ran.
                        padUnchanged++;
                    }
                }
            if (PAD_LOG)
                LOGGER_STRUCTURES.warn(
                        "PAD chunk {},{}: {} filled, {} shaved, {} unchanged; target-natural {}..{}",
                        pos.x(), pos.z(), padFilled, padShaved, padUnchanged,
                        padDeltaMin == Integer.MAX_VALUE ? 0 : padDeltaMin,
                        padDeltaMax == Integer.MIN_VALUE ? 0 : padDeltaMax);
        } catch (Throwable t) {
            // A pad must never break chunk generation, exactly as the carve must not — but it must not
            // hide either. A silently swallowed throw is indistinguishable from "ran and did nothing",
            // which is the very ambiguity this instrumentation exists to remove, and this project has
            // already lost half a day to a caught exception reading as scarcity (populateLots FAILED).
            if (PAD_LOG)
                LOGGER_STRUCTURES.warn("PAD chunk {},{}: FAILED",
                        chunk.getPos().x(), chunk.getPos().z(), t);
        }
    }

    /**
     * How far outside the nearest piece box this point is, as {@code 0.0} (inside) to {@code 1.0} (at
     * or beyond the halo). Horizontal and upward distances are normalised separately, because the halo
     * is asymmetric.
     */
    private static double outsideness(List<net.minecraft.world.level.levelgen.structure.BoundingBox> boxes,
            int x, int y, int z, int halo, int haloUp) {
        double best = 1.0;
        for (net.minecraft.world.level.levelgen.structure.BoundingBox box : boxes) {
            int dx = Math.max(0, Math.max(box.minX() - x, x - box.maxX()));
            int dz = Math.max(0, Math.max(box.minZ() - z, z - box.maxZ()));
            // Below the box is never carved: vanilla's beard ADDS material underneath to support the
            // structure, so digging there would leave the city hanging over a void.
            if (y < box.minY())
                continue;
            int dy = Math.max(0, y - box.maxY());
            double t = Math.max(halo == 0 ? (dx + dz > 0 ? 1.0 : 0.0) : Math.max(dx, dz) / (double) halo,
                    haloUp == 0 ? (dy > 0 ? 1.0 : 0.0) : dy / (double) haloUp);
            if (t < best)
                best = t;
            if (best <= 0.0)
                return 0.0;
        }
        return best;
    }

    /** Wavelength of the raggedness on the carve's edge — small, so it reads as rock, not as hills. */
    private static final double CARVE_NOISE_SCALE = 1.0 / 9.0;

    private volatile me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator carveNoise;

    /** The carve's edge noise, built once per world so the taper is reproducible. */
    private me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator carveNoise() {
        me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator local = carveNoise;
        if (local == null)
            synchronized (this) {
                local = carveNoise;
                if (local == null)
                    carveNoise = local =
                            new me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator(levelSeed ^ 0xBEA5DL);
            }
        return local;
    }

    /** Whether this structure expects terrain to be carved away from it (a beard), rather than piled on. */
    /** Structures that get a carved cavern whatever their {@code terrain_adaptation}; see {@link #carveForStructures}. */
    public static final TagKey<net.minecraft.world.level.levelgen.structure.Structure> CARVE_CAVERN = TagKey.create(
            Registries.STRUCTURE, Identifier.fromNamespaceAndPath("cityworld", "carve_cavern"));

    private static boolean carvesTerrain(net.minecraft.world.level.levelgen.structure.Structure structure) {
        net.minecraft.world.level.levelgen.structure.TerrainAdjustment adjustment = structure.terrainAdaptation();
        return adjustment == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_BOX
                || adjustment == net.minecraft.world.level.levelgen.structure.TerrainAdjustment.BEARD_THIN;
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
            RandomState randomState, ChunkAccess chunk) {
        // Nothing: the shaper lays its own surface down in fillFromNoise (that is what the
        // surfaceMaterial/subsurfaceMaterial strata are), so vanilla's surface pass has no job here —
        // except in the End's central zone, which vanilla generated and must surface itself.
        if (inEndCentre(chunk) || isEndWild(structureManager, chunk))
            vanillaEnd().buildSurface(region, structureManager, vanillaEndRandom, chunk);
    }

    /**
     * An End chunk CityWorld leaves to nature (or to an end city). It gets the End's own surface rules, which in a
     * vanilla game do nothing — end stone onto end stone — and with a biome mod lay that mod's ground (BoP's algal
     * and null end stone). City chunks are skipped: the rules repaint any exposed end stone, yards and all.
     */
    private boolean isEndWild(StructureManager structureManager, ChunkAccess chunk) {
        if (!isEnd())
            return false;
        if (endStructureHere(structureManager, chunk))
            return true;
        // Block coordinates, not pos.x(): ChunkPos became a record on 26.1, and this line is the same on every branch.
        int chunkX = chunk.getPos().getMinBlockX() >> 4, chunkZ = chunk.getPos().getMinBlockZ() >> 4;
        CityWorldGenerator context = context(chunk);
        me.daddychurchill.CityWorld.Plats.PlatLot lot = context.getPlatMap(chunkX, chunkZ).getMapLot(chunkX, chunkZ);
        return lot == null || lot.style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.NATURE;
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // No vanilla carvers (caves/ravines) — CityWorld carves its own mines/sewers.
    }

    /**
     * Fills the chunk's biomes from CityWorld's own terrain instead of the flat plains a fixed source
     * would give — ocean in the deeps, beaches at the waterline, forest/hills/snowy peaks up the
     * mountains, and the cave pool underground — so grass, water and foliage colour follow the land.
     *
     * <p><b>This used to do the classifying itself, and that was the bug.</b> Vanilla's own
     * {@code createBiomes} is nothing but {@code fillBiomesFromNoise(biomeSource, sampler)}, so a
     * hand-rolled resolver here produced the right chunk and left {@code getNoiseBiome} a constant stub
     * — which is the method the <em>structure</em> pipeline consults, at an earlier chunk stage. Every
     * structure therefore saw "plains, at every height". Classification now lives in the biome source
     * where vanilla looks for it ({@link CityWorldBiomeLookup}), and this override does nothing but
     * make sure the context is built — and so handed to the source — before the fill runs.
     */
    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
            StructureManager structureManager, ChunkAccess chunk) {
        context(chunk);
        return super.createBiomes(randomState, blender, structureManager, chunk);
    }

    /**
     * Places structure starts — overridden only to build the context first.
     *
     * <p>This is the <em>earliest</em> chunk stage ({@code STRUCTURE_STARTS} runs before
     * {@code BIOMES}), and it is where {@code Structure.isValidBiome} calls
     * {@code getBiomeSource().getNoiseBiome(...)}. Without the context bound by now, the source would
     * answer with its fallback constant and every structure would be biome-gated against plains — which
     * is precisely the state this wave exists to fix. The chunk doubles as the {@code LevelHeightAccessor}
     * the context needs, which is why binding can happen here and not in {@link #createState}.
     */
    @Override
    public void createStructures(RegistryAccess registryAccess, ChunkGeneratorStructureState structureState,
            StructureManager structureManager, ChunkAccess chunk, StructureTemplateManager templateManager,
            ResourceKey<net.minecraft.world.level.Level> dimension) {
        context(chunk);
        super.createStructures(registryAccess, structureState, structureManager, chunk, templateManager, dimension);
    }

    /**
     * The structure-set tag that decides which vanilla structures a CityWorld world keeps.
     *
     * <p>Shipped as {@code data/cityworld/tags/worldgen/structure_set/allowed.json} with strongholds,
     * trial chambers and ancient cities. A datapack can widen it — including to a <em>mod's</em>
     * structure set — with no code change, which is the same seam the block palettes use.
     *
     * <p><b>Absent means none.</b> If the tag is missing, no vanilla structure places: an empty tag
     * fails to today's behaviour rather than silently letting villages and mineshafts loose in a world
     * that builds its own.
     */
    private static final TagKey<StructureSet> ALLOWED_STRUCTURE_SETS = TagKey.create(Registries.STRUCTURE_SET,
            Identifier.fromNamespaceAndPath("cityworld", "allowed"));

    /**
     * Selectively re-enables vanilla structures — CityWorld builds its own cities, but it has no
     * stronghold, and no stronghold means no End portal and nothing for an eye of ender to find.
     *
     * <p><b>Why {@code createForNormal} and not the {@code createForFlat} this used to call.</b> The
     * flat factory takes a stream of sets, which looks like the natural way to pass a chosen few — but
     * it also hardcodes {@code 0L} as the <em>concentric-rings seed</em>, where the normal factory
     * passes the level seed. That seed is exactly what positions strongholds, so the flat path would
     * put every CityWorld world's strongholds in identical places. The constructor taking both seeds is
     * private, so the selective-and-correctly-seeded combination has to come from somewhere else.
     *
     * <p>It comes from {@link #onlyAllowed}: {@code createForNormal} reads the lookup through
     * {@code listElements()} and nothing else, so a filtering delegate gives us both halves with no
     * access transformer. Vanilla then does the rest of the work itself — both factories drop any set
     * whose biomes the biome source cannot produce, so a structure we allow but cannot host (ancient
     * cities, before the biome source could emit {@code deep_dark}) excludes itself.
     */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,
            RandomState randomState, long seed) {
        // Doubles as the one place vanilla tells a ChunkGenerator its world seed — see context().
        if (this.levelSeedKnown && this.levelSeed != seed)
            this.vanillaEnd = null; // a different world: its End noise is not this one's
        this.levelSeed = seed;
        this.levelSeedKnown = true;
        // The End's biomes come from its noise, and structure placement asks for biomes before any chunk exists:
        // a /locate (or the probe's) on a dimension nobody has visited found no end city in 87,000 candidate
        // cells, because an unbound source answers "barrens" and barrens hold none. Bind now, not at first chunk.
        if (isEnd() && net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer() != null) {
            var vanilla = vanillaEnd();
            if (this.biomeSource instanceof CityWorldEndBiomeSource endBiomes)
                endBiomes.bindTerrain(endTerrain, vanilla.getBiomeSource());
        }
        ChunkGeneratorStructureState state = ChunkGeneratorStructureState.createForNormal(
                randomState, seed, this.biomeSource, onlyAllowed(lookup));
        this.structureState = state;
        return state;
    }

    /**
     * The world's structure placements, kept so the <em>planner</em> can ask where structures are going
     * before it lays a city on one. Vanilla hands this out exactly once per world, here, and never
     * offers it to a {@code ChunkGenerator} again — so catching it on the way past is the only way the
     * planning side can ever see it. Read by {@link StructureReservations}; see there for why the
     * question is answerable with no chunk, no terrain and no biomes.
     */
    private volatile ChunkGeneratorStructureState structureState;

    /**
     * The structure-set registry as seen through {@link #ALLOWED_STRUCTURE_SETS} — every element not in
     * the tag simply isn't there.
     *
     * <p>Only {@code listElements()} actually needs filtering ({@code createForNormal} calls nothing
     * else), but {@code get(ResourceKey)} is filtered too so the view can't answer inconsistently if a
     * future vanilla version starts asking that way instead.
     */
    private static HolderLookup<StructureSet> onlyAllowed(HolderLookup<StructureSet> all) {
        HolderSet<StructureSet> allowed = all.get(ALLOWED_STRUCTURE_SETS)
                .<HolderSet<StructureSet>>map(named -> named)
                .orElseGet(HolderSet::direct); // absent tag -> empty -> no vanilla structures
        return new HolderLookup<>() {
            @Override
            public Stream<Holder.Reference<StructureSet>> listElements() {
                return all.listElements().filter(allowed::contains);
            }

            @Override
            public Stream<HolderSet.Named<StructureSet>> listTags() {
                return all.listTags();
            }

            @Override
            public Optional<Holder.Reference<StructureSet>> get(ResourceKey<StructureSet> key) {
                return all.get(key).filter(allowed::contains);
            }

            @Override
            public Optional<HolderSet.Named<StructureSet>> get(TagKey<StructureSet> key) {
                return all.get(key);
            }
        };
    }

    /**
     * Draws the city.
     *
     * <p>This is the decoration pass, and it is where CityWorld actually builds — not
     * {@link #fillFromNoise}. The split is upstream's own: its {@code ChunkGenerator} only ever
     * shaped terrain, and a separate {@code BlockPopulator} laid down the roads, buildings, sewers
     * and bridges afterwards. {@code RoadLot.generateActualChunk} is literally empty, with the
     * comment "moved to other chunk generator"; all 1,600-odd lines of road live in
     * {@code generateActualBlocks}, which needs a <em>live</em> level rather than a raw chunk.
     * {@code applyBiomeDecoration} is the modern equivalent: it runs at the decoration stage and
     * hands us a {@link WorldGenLevel}, which is exactly what {@link RealBlocks} was built to take.
     *
     * <p>Vanilla's own biome decoration (trees, flowers, ores, lakes) is suppressed by not calling
     * {@code super} — CityWorld places its own.
     *
     * <p>Neighbour access is the constraint to respect here (PORTING.md, top risk #2): a
     * {@code WorldGenRegion} only permits writes within a small radius of the chunk being decorated.
     * {@code RealBlocks} already refuses to look past its own chunk edge, which is what makes this
     * legal — and is why the {@code RealBlocks}/{@code RelativeBlocks} split matters more now than
     * it did under Bukkit.
     */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager) {
        // The central zone decorates as vanilla's End: the obsidian pillars (an end_spike feature of the
        // the_end biome) and the spawn platform, which the dragon fight looks for.
        if (inEndCentre(chunk) || endStructureHere(structureManager, chunk)) {
            super.applyBiomeDecoration(level, chunk, structureManager);
            return;
        }
        CityWorldGenerator context = context(level);
        ChunkPos pos = chunk.getPos();

        PlatMap platmap = context.getPlatMap(pos.x(), pos.z());

        // Bind the thread-local demolition tool to this chunk's live level for the duration of the
        // pass, so lots that call generator.destroyWithin/destroyArea (castles, radio towers, oil
        // platforms, unfinished/decayed styles) actually chew holes. Released in finally so a worker
        // never carries a stale level into the next chunk.
        context.beginDecoration(level, pos);
        try {
            platmap.generateBlocks(new RealBlocks(context, level, pos));
        } finally {
            context.endDecoration();
        }

        // MODERN hybrid (trial): let vanilla decorate the WILD chunks — biome-appropriate trees,
        // flowers, coral, sugar cane, etc. for the biome CityWorld assigned — while city/road/structure
        // chunks stay wholly CityWorld-owned (no super). Each chunk is one lot, so a single NATURE-lot
        // check gates it; runs after CityWorld's own pass so vanilla's features sit on the finished
        // terrain. Vanilla's decoration respects the WorldGenRegion radius, so top risk #2 is its own
        // problem here, not ours.
        // Paint cave rock BEFORE any decoration — vanilla's sulfur spikes can only replace sulfur, so
        // the walls have to exist before either branch below tries to grow anything on them.
        paintCaveWalls(context, level, chunk);

        me.daddychurchill.CityWorld.Plats.PlatLot lot = platmap.getMapLot(pos.x(), pos.z());
        // In the End the wild is vanilla's outright — its islands, so its chorus plants too — whatever the style.
        boolean wild = (isEnd() || context.isModernStyle() && context.getSettings().vanillaDecoratesWild())
                && lot != null && lot.style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.NATURE
                && lot.allowsWildDecoration();

        if (wild) {
            // The full vanilla pass: biome-appropriate trees, flowers, coral, sugar cane — and the
            // structure pieces, which vanilla interleaves into the same step loop.
            super.applyBiomeDecoration(level, chunk, structureManager);
            // On iced peaks, vanilla's cold-biome decoration drops snow layers onto our packed/blue
            // ice — the illegal, cascading state the MODERN icecap exists to avoid. Strip any that
            // landed on ice. Only peak lots pay for the scan.
            if (lot != null && lot.getMaxTerrainY() > context.snowLevel)
                stripSnowOnIce(chunk);
        } else {
            // City / road / structure / construct chunks skip the full wild pass (no trees, lakes or
            // springs carving into the build) — but they must still get the two slices of it that a
            // city wants: the vanilla structures allowed by the tag, and ore.
            placeStructures(level, chunk, structureManager);
            // Ore: just the UNDERGROUND_ORES step of the chunk's biome, so vanilla ore veins fill the
            // stone beneath the city exactly as they do in the wild. MODERN only, as before.
            if (context.isModernStyle())
                placeUndergroundOres(level, chunk);
            // Cave biomes decorate themselves — moss and glow berries in lush, dripstone clusters,
            // sculk in the deep dark, sulfur on 26.2. See placeCaveDecoration.
            placeCaveDecoration(level, chunk);
        }

        // A buried structure that got a cavern (#cityworld:carve_cavern — bastions) gets a way down to it.
        drawCavernShafts(context, level, chunk, structureManager);
    }

    /**
     * The surface tell for a structure buried in a carved cavern: a ruined blackstone shaft from the street down onto
     * its highest roof, drawn in the structure's start chunk after the city and the structure are both in place.
     *
     * <p>Why it exists: a bastion starts at absolute y 33 whatever the terrain, which in a full-height ruined-city
     * Nether is well under the streets. {@link #carveForStructures} gives it a cavern; this gives a player a reason
     * to find it — a broken 5x5 blackstone collar with a soul campfire at street level, and a ladder down.
     *
     * <p>Self-sizing: the bottom is the highest piece box under the shaft column, so nothing here assumes where a
     * structure sits. No shaft when that roof is within four blocks of the street (nothing to dig down to), or when
     * no piece lies under the column.
     */
    private void drawCavernShafts(CityWorldGenerator context, WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager) {
        try {
            var cavern = structureManager.registryAccess().lookupOrThrow(Registries.STRUCTURE).get(CARVE_CAVERN);
            if (cavern.isEmpty())
                return;
            ChunkPos pos = chunk.getPos();
            List<net.minecraft.world.level.levelgen.structure.StructureStart> starts = structureManager.startsForStructure(pos,
                    structure -> cavern.get().stream().anyMatch(h -> h.value() == structure));
            for (net.minecraft.world.level.levelgen.structure.StructureStart start : starts) {
                if (!start.isValid())
                    continue;
                int street = context.streetLevel + 1;
                // Measured over 12 bastions: every one breaks the surface — start boxes y 29..32 up to y 75..102
                // against a y 64 street — because a tower or two punches up while the bulk (treasure, bridges,
                // stables) lies at y 33..55. So the shaft is not about finding the bastion; it is the way down to
                // that bulk. Of a 3x3 grid of columns over the start chunk, it takes the highest roof still four
                // under the street (the shortest climb, and never onto a tower).
                //
                // Searched over the whole footprint, not just the start chunk: in the start chunk alone only 2 of 12
                // sampled bastions found a low roof. The choice depends only on the start's pieces, so every chunk the
                // bastion touches picks the same column, and only the chunk that owns it draws — one shaft per
                // bastion. Candidate columns keep two blocks inside their chunk so the 5x5 never crosses a chunk edge.
                var footprint = start.getBoundingBox();
                int bestX = 0, bestZ = 0, roof = Integer.MIN_VALUE;
                for (int cx = footprint.minX() + 2; cx <= footprint.maxX() - 2; cx += 4)
                    for (int cz = footprint.minZ() + 2; cz <= footprint.maxZ() - 2; cz += 4) {
                        int inX = Math.floorMod(cx, 16), inZ = Math.floorMod(cz, 16);
                        if (inX < 2 || inX > 13 || inZ < 2 || inZ > 13)
                            continue;
                        int top = Integer.MIN_VALUE;
                        for (net.minecraft.world.level.levelgen.structure.StructurePiece piece : start.getPieces()) {
                            var box = piece.getBoundingBox();
                            // The ladder's 3x3 core must land on the piece; bastion pieces are often too narrow to
                            // hold the whole 5x5 collar, and demanding that left 5 of 12 sampled bastions shaftless.
                            if (box.minX() <= cx - 1 && box.maxX() >= cx + 1 && box.minZ() <= cz - 1 && box.maxZ() >= cz + 1)
                                top = Math.max(top, box.maxY());
                        }
                        if (top > roof && top < street - 4) {
                            roof = top;
                            bestX = cx;
                            bestZ = cz;
                        }
                    }
                if (roof == Integer.MIN_VALUE || (bestX >> 4) != (pos.getMinBlockX() >> 4) || (bestZ >> 4) != (pos.getMinBlockZ() >> 4))
                    continue;
                // Nothing to climb to if the surface sits at or under the roof we would land on.
                if (surfaceAt(level, bestX, bestZ, street) <= roof + 4)
                    continue;
                drawShaft(level, net.minecraft.util.RandomSource.create(level.getSeed()
                        ^ (((long) pos.getMinBlockX() << 32) ^ (pos.getMinBlockZ() & 0xffffffffL)) * 31L), bestX, bestZ,
                        roof + 1, street);
            }
        } catch (Throwable t) {
            // decoration must never break chunk generation
            LOGGER_STRUCTURES.error("CityWorld: cavern shaft failed for chunk {}", chunk.getPos(), t);
        }
    }

    /**
     * The real surface at a column: scan UP from below the planned street for the first two-tall air gap and take
     * the solid block under it. {@code streetLevel} is a planned datum, not this column's ground — building to it
     * left the bastion shaft short of the surface (owner, 2026-09-16). Same technique as the vault's entrance hut
     * ({@code VaultLot.groundedHutFloor}), and scanning up rather than down means an overhang cannot fool it.
     */
    private static int surfaceAt(WorldGenLevel level, int x, int z, int street) {
        // The heightmap, not a scan: scanning up from under the street (the vault hut's trick, which works
        // because its column is solid rock) stopped inside this bastion's own cavern — campfires landed at y 50
        // under a y 76 surface (measured 2026-09-16). WORLD_SURFACE is the first free Y above the column, so the
        // top solid block is one below it.
        int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        return top > level.getMinY() ? top : street;
    }

    private static void drawShaft(WorldGenLevel level, net.minecraft.util.RandomSource random, int cx, int cz, int bottom,
            int street) {
        var bricks = net.minecraft.world.level.block.Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        var cracked = net.minecraft.world.level.block.Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        var gilded = net.minecraft.world.level.block.Blocks.GILDED_BLACKSTONE.defaultBlockState();
        var light = net.minecraft.world.level.block.Blocks.SHROOMLIGHT.defaultBlockState();
        var air = net.minecraft.world.level.block.Blocks.AIR.defaultBlockState();
        var ladder = net.minecraft.world.level.block.Blocks.LADDER.defaultBlockState()
                .setValue(net.minecraft.world.level.block.LadderBlock.FACING, net.minecraft.core.Direction.SOUTH);
        net.minecraft.core.BlockPos.MutableBlockPos at = new net.minecraft.core.BlockPos.MutableBlockPos();
        int flags = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
        // Build to this column's real ground, not the planned street.
        street = surfaceAt(level, cx, cz, street);
        for (int y = bottom; y <= street + 1; y++)
            for (int dx = -2; dx <= 2; dx++)
                for (int dz = -2; dz <= 2; dz++) {
                    at.set(cx + dx, y, cz + dz);
                    boolean wall = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                    if (!wall) {
                        // the ladder hangs on the north wall's inner face, down the middle
                        level.setBlock(at, dx == 0 && dz == -1 && y <= street ? ladder : air, flags);
                    } else if (y > street) {
                        // a broken collar above the street: about half the ring stands
                        if (random.nextBoolean())
                            level.setBlock(at, random.nextInt(6) == 0 ? cracked : bricks, flags);
                    } else if (dx == 0 && dz == -2) {
                        level.setBlock(at, bricks, flags); // the ladder's backing, never missing
                    } else {
                        int roll = random.nextInt(40);
                        level.setBlock(at, (y - bottom) % 8 == 4 && roll < 10 ? light
                                : roll < 12 ? cracked : roll == 12 ? gilded : bricks, flags);
                    }
                }
        // the tell: a soul campfire burning on the collar's north corner
        at.set(cx - 2, street + 2, cz - 2);
        level.setBlock(at.below(), bricks, flags);
        level.setBlock(at, net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE.defaultBlockState(), flags);
    }

    /**
     * Lets a cave biome under a city grow its own character — the decoration half of the cave pool.
     *
     * <p>Once the biome map became 3D, this is nearly free: {@code PlacedFeature.placeWithBiomeCheck}
     * asks whether the biome <em>at the position</em> has the feature, so vanilla's own lush/dripstone/
     * sculk/sulfur features confine themselves to the patches without CityWorld knowing anything about
     * what they place. A new cave type — vanilla's or a mod's — decorates itself the moment it joins
     * {@code #cityworld:cave_pool}.
     *
     * <p><b>⚠ Why this cannot simply run a generation step.</b> The obvious implementation — run
     * {@code UNDERGROUND_DECORATION} the way {@link #placeUndergroundOres} runs {@code UNDERGROUND_ORES}
     * — gets lush caves wrong and cities badly wrong:
     * <ul>
     *   <li>Lush caves put <em>nothing</em> in {@code UNDERGROUND_DECORATION}. Their whole vocabulary
     *       ({@code lush_caves_vegetation}, {@code cave_vines}, {@code spore_blossom},
     *       {@code rooted_azalea_tree}) is in {@code VEGETAL_DECORATION}.
     *   <li>But {@code VEGETAL_DECORATION} is also the step that plants <em>trees</em> — and
     *       {@code dripstone_caves} and {@code deep_dark} both list {@code trees_plains},
     *       {@code flower_plains} and {@code patch_pumpkin} in it. Vanilla gets away with that because
     *       those biomes are never at the surface. Running the step on a city chunk would sprout trees
     *       on the roads.
     * </ul>
     *
     * <p>So the pass is keyed on <em>features</em>, not steps: {@link #caveOnlyFeatures} keeps only the
     * features that no non-cave biome in this world also has. {@code lush_caves_vegetation} survives
     * (only lush caves has it); {@code trees_plains} does not (plains has it too). The biome check then
     * confines what is left to the patches.
     */
    private void placeCaveDecoration(WorldGenLevel level, ChunkAccess chunk) {
        try {
            List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> features = caveOnlyFeatures();
            // A realm with no cave pool (the Nether, the End) still has underground to fill: run what the
            // chunk's OWN biomes put underground. Without this the Nether's caves were bare and — because the
            // same two steps carry them — BoP's orpiment buds, blackstone spines, flesh tendons, willow trees
            // and rose quartz never appeared anywhere but a wild nature lot (owner, in game, 2026-09-16).
            if (features.isEmpty())
                features = ownBiomeUndergroundFeatures(chunk);
            else if (!containsCaveBiome(chunk))
                return;
            if (features.isEmpty())
                return;

            net.minecraft.core.SectionPos sectionPos = net.minecraft.core.SectionPos.of(chunk.getPos(),
                    level.getMinSectionY());
            BlockPos origin = sectionPos.origin();
            net.minecraft.world.level.levelgen.WorldgenRandom random =
                    new net.minecraft.world.level.levelgen.WorldgenRandom(
                            new net.minecraft.world.level.levelgen.XoroshiroRandomSource(level.getSeed()));
            long decoSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());

            int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal();
            int index = 0;
            for (Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature> feature : features) {
                random.setFeatureSeed(decoSeed, index++, step);
                feature.value().placeWithBiomeCheck(level, this, random, origin);
            }
        } catch (Throwable t) {
            // cave decoration must never break chunk generation
        }
    }

    /**
     * Skins the cave walls of a patch whose biome needs its rock painted rather than decorated —
     * sulfur caves, today. See {@link CaveRegions#wallRockFor} for why that is a real distinction and not
     * an optimisation.
     *
     * <p>Only stone that is actually <em>exposed to the cave</em> is replaced, so this reads as a lining
     * on the walls, floor and ceiling rather than a solid block of sulfur buried in the rock. Restricted
     * to {@code #base_stone_overworld} so it can never eat a CityWorld build, an ore vein, or bedrock.
     *
     * <p>Runs before both decoration branches: vanilla's sulfur spikes only replace sulfur, so the rock
     * must be there first or the features silently no-op — which is exactly how this bug presented, as a
     * biome with fog and water colour and nothing else.
     */
    private void paintCaveWalls(CityWorldGenerator context, WorldGenLevel level, ChunkAccess chunk) {
        if (!(this.biomeSource instanceof CityWorldBiomes cityBiomes))
            return;
        try {
            CaveRegions.Pool pool = cityBiomes.cavePool();
            if (pool.isEmpty())
                return;
            ChunkPos pos = chunk.getPos();
            int originX = pos.getMinBlockX(), originZ = pos.getMinBlockZ();
            long seed = context.getWorldSeed();
            int bottom = chunk.getMinY() + 1;
            int top = Math.min(chunk.getMaxY(), context.seaLevel);
            net.minecraft.core.BlockPos.MutableBlockPos cursor = new net.minecraft.core.BlockPos.MutableBlockPos();
            java.util.Map<String, net.minecraft.world.level.block.state.BlockState> rocks = new java.util.HashMap<>();

            for (int dx = 0; dx < 16; dx++)
                for (int dz = 0; dz < 16; dz++) {
                    int x = originX + dx, z = originZ + dz;
                    // Cheap reject: the column's cave type is fixed, so one question answers the column.
                    if (!pool.paintsWalls(seed, x, z))
                        continue;
                    for (int y = bottom; y <= top; y++) {
                        String rockId = pool.wallRockAt(seed, x, y, z);
                        if (rockId == null)
                            continue;
                        cursor.set(x, y, z);
                        net.minecraft.world.level.block.state.BlockState state = level.getBlockState(cursor);
                        if (!state.is(net.minecraft.tags.BlockTags.BASE_STONE_OVERWORLD))
                            continue;
                        if (!touchesCaveAir(level, cursor))
                            continue;
                        net.minecraft.world.level.block.state.BlockState rock = rocks.computeIfAbsent(rockId,
                                id -> net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                        .getOptional(Identifier.parse(id))
                                        .map(net.minecraft.world.level.block.Block::defaultBlockState)
                                        .orElse(null));
                        if (rock == null)
                            continue; // this version has no such block (sulfur/cinnabar before 26.2)
                        level.setBlock(cursor, rock, net.minecraft.world.level.block.Block.UPDATE_NONE);
                    }
                }
        } catch (Throwable t) {
            // cave skinning must never break chunk generation
        }
    }

    /** Whether this solid block has cave air (or water) against any face — i.e. it is a cave surface. */
    private static boolean touchesCaveAir(WorldGenLevel level,
            net.minecraft.core.BlockPos.MutableBlockPos cursor) {
        int x = cursor.getX(), y = cursor.getY(), z = cursor.getZ();
        for (net.minecraft.core.Direction face : net.minecraft.core.Direction.values()) {
            cursor.set(x + face.getStepX(), y + face.getStepY(), z + face.getStepZ());
            net.minecraft.world.level.block.state.BlockState neighbour = level.getBlockState(cursor);
            if (neighbour.isAir() || neighbour.getFluidState().is(net.minecraft.world.level.material.Fluids.WATER)) {
                cursor.set(x, y, z);
                return true;
            }
        }
        cursor.set(x, y, z);
        return false;
    }

    /** Whether any section of this chunk carries a cave-pool biome — cheap gate before doing the work. */
    private boolean containsCaveBiome(ChunkAccess chunk) {
        if (!(this.biomeSource instanceof CityWorldBiomes cityBiomes))
            return false;
        java.util.Set<Holder<Biome>> pool = cityBiomes.cavePool().biomes()
                .collect(java.util.stream.Collectors.toSet());
        if (pool.isEmpty())
            return false;
        boolean[] found = { false };
        for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections()) {
            section.getBiomes().getAll(b -> {
                if (pool.contains(b))
                    found[0] = true;
            });
            if (found[0])
                return true;
        }
        return false;
    }

    /**
     * The underground features of the biomes actually present in this chunk — for a dimension with no cave
     * pool of its own.
     *
     * <p><b>Two steps only.</b> {@code UNDERGROUND_DECORATION} and {@code LOCAL_MODIFICATIONS} are where the
     * Nether keeps its character (vanilla's glowstone and magma; BoP's orpiment fumaroles, obsidian splatter,
     * blackstone spines, flesh tendons, willow undergrowth, large rose quartz). {@code VEGETAL_DECORATION} is
     * deliberately left out here: those features anchor to the heightmap, so running them on a city chunk
     * would decorate its rooftops.
     */
    private List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> ownBiomeUndergroundFeatures(
            ChunkAccess chunk) {
        java.util.LinkedHashSet<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> found =
                new java.util.LinkedHashSet<>();
        java.util.Set<Holder<Biome>> seen = new java.util.HashSet<>();
        for (net.minecraft.world.level.chunk.LevelChunkSection section : chunk.getSections())
            section.getBiomes().getAll(seen::add);
        for (Holder<Biome> biome : seen) {
            List<net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>> byStep =
                    biome.value().getGenerationSettings().features();
            for (int step : UNDERGROUND_STEPS)
                if (step < byStep.size())
                    byStep.get(step).forEach(found::add);
        }
        return List.copyOf(found);
    }

    /** See {@link #ownBiomeUndergroundFeatures}: the two steps that are safe to run on any chunk. */
    private static final int[] UNDERGROUND_STEPS = {
            net.minecraft.world.level.levelgen.GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal(),
            net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal() };

    /** Memoized; see {@link #caveOnlyFeatures}. */
    private volatile List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> caveOnlyFeatures;

    /**
     * The features that belong to cave-pool biomes and to <em>nothing else this world can generate</em>.
     *
     * <p>The exclusion is what makes the pass safe on a city chunk (see {@link #placeCaveDecoration}),
     * and it is computed against {@code possibleBiomes()} rather than a hardcoded list, so it stays
     * correct as the palette or the pool changes. Shared features are the deliberate cost:
     * {@code glow_lichen} and {@code amethyst_geode} are in half the overworld, so they are dropped
     * here — the wild pass still places them normally.
     */
    public List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> caveOnlyFeatures() {
        List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> local = caveOnlyFeatures;
        if (local != null)
            return local;
        synchronized (this) {
            if (caveOnlyFeatures != null)
                return caveOnlyFeatures;
            List<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> built = List.of();
            if (this.biomeSource instanceof CityWorldBiomes cityBiomes) {
                java.util.Set<Holder<Biome>> pool = cityBiomes.cavePool().biomes()
                        .collect(java.util.stream.Collectors.toSet());
                // Everything a non-cave biome of this world can place, in the three steps a cave biome
                // keeps its character in.
                java.util.Set<net.minecraft.world.level.levelgen.placement.PlacedFeature> elsewhere =
                        new java.util.HashSet<>();
                for (Holder<Biome> biome : this.biomeSource.possibleBiomes())
                    if (!pool.contains(biome))
                        collectCaveSteps(biome, elsewhere::add);
                java.util.LinkedHashSet<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> keep =
                        new java.util.LinkedHashSet<>();
                for (Holder<Biome> biome : pool)
                    collectCaveStepHolders(biome, h -> {
                        if (!elsewhere.contains(h.value()))
                            keep.add(h);
                    });
                built = List.copyOf(keep);
            }
            caveOnlyFeatures = built;
            return built;
        }
    }

    /**
     * The generation steps a cave biome keeps its own look in: {@code LOCAL_MODIFICATIONS}
     * (large dripstone), {@code UNDERGROUND_DECORATION} (dripstone clusters, sculk) and
     * {@code VEGETAL_DECORATION} (all of lush caves). Fluid springs are deliberately excluded — those
     * carve water into whatever is above them.
     */
    private static final int[] CAVE_STEPS = {
            net.minecraft.world.level.levelgen.GenerationStep.Decoration.LOCAL_MODIFICATIONS.ordinal(),
            net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_DECORATION.ordinal(),
            net.minecraft.world.level.levelgen.GenerationStep.Decoration.VEGETAL_DECORATION.ordinal() };

    private static void collectCaveSteps(Holder<Biome> biome,
            java.util.function.Consumer<net.minecraft.world.level.levelgen.placement.PlacedFeature> sink) {
        collectCaveStepHolders(biome, h -> sink.accept(h.value()));
    }

    private static void collectCaveStepHolders(Holder<Biome> biome,
            java.util.function.Consumer<Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature>> sink) {
        List<net.minecraft.core.HolderSet<net.minecraft.world.level.levelgen.placement.PlacedFeature>> byStep =
                biome.value().getGenerationSettings().features();
        for (int step : CAVE_STEPS)
            if (step < byStep.size())
                byStep.get(step).forEach(sink);
    }

    /**
     * Places the pieces of any allowed vanilla structure that reaches into this chunk — the structure
     * half of {@code ChunkGenerator.applyBiomeDecoration}, on its own.
     *
     * <p><b>This is the half that was silently missing.</b> Structure <em>starts</em> are decided at the
     * {@code STRUCTURE_STARTS} chunk stage, but the blocks are laid down here, inside
     * {@code applyBiomeDecoration} — the method CityWorld overrides and, for anything but a MODERN
     * nature lot, does not call {@code super} on. So re-enabling structures without this would have
     * produced strongholds sliced down to whichever chunks happened to be wild: a bug that looks like
     * corrupt worldgen and reads like a vanilla fault.
     *
     * <p>It mirrors vanilla's seeding exactly — {@code setDecorationSeed} on the chunk origin, then
     * {@code setFeatureSeed(seed, indexWithinStep, step)} — so a structure lands in the same place
     * whether it was placed here or by {@code super} on a neighbouring wild chunk. That equivalence is
     * the point: without it, a structure straddling a city/wild boundary would generate as two
     * mismatched halves.
     *
     * <p>Runs <em>after</em> CityWorld's own build, which is exactly where {@code super} sits in the
     * wild branch — so both branches order the world the same way, and the city wins where the two
     * overlap. Wrapped so a structure can never take chunk generation down, the same as ore.
     */
    private void placeStructures(WorldGenLevel level, ChunkAccess chunk, StructureManager structureManager) {
        if (!structureManager.shouldGenerateStructures())
            return;
        try {
            ChunkPos pos = chunk.getPos();
            net.minecraft.core.SectionPos sectionPos = net.minecraft.core.SectionPos.of(pos, level.getMinSectionY());
            BlockPos origin = sectionPos.origin();
            net.minecraft.core.Registry<net.minecraft.world.level.levelgen.structure.Structure> structures =
                    level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);

            net.minecraft.world.level.levelgen.WorldgenRandom random =
                    new net.minecraft.world.level.levelgen.WorldgenRandom(
                            new net.minecraft.world.level.levelgen.XoroshiroRandomSource(
                                    net.minecraft.world.level.levelgen.RandomSupport.generateUniqueSeed()));
            long decoSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());

            // Vanilla groups structures by their generation step and numbers them within it; both feed
            // the feature seed, so the grouping has to be reproduced, not flattened.
            java.util.Map<Integer, java.util.List<net.minecraft.world.level.levelgen.structure.Structure>> byStep =
                    structures.stream().collect(java.util.stream.Collectors.groupingBy(s -> s.step().ordinal()));

            net.minecraft.world.level.levelgen.structure.BoundingBox writable = writableArea(chunk);

            for (int step = 0; step < net.minecraft.world.level.levelgen.GenerationStep.Decoration.values().length;
                    step++) {
                int index = 0;
                for (net.minecraft.world.level.levelgen.structure.Structure structure :
                        byStep.getOrDefault(step, java.util.List.of())) {
                    random.setFeatureSeed(decoSeed, index++, step);
                    for (net.minecraft.world.level.levelgen.structure.StructureStart start :
                            structureManager.startsForStructure(sectionPos, structure))
                        start.placeInChunk(level, structureManager, this, random, writable, pos);
                }
            }
        } catch (Throwable t) {
            // a structure must never break chunk generation
            LOGGER_STRUCTURES.error("CityWorld: structure placement failed for chunk {}", chunk.getPos(), t);
        }
    }

    /**
     * The box a structure may write into for this chunk — the chunk's own columns, full height.
     * Vanilla's equivalent ({@code ChunkGenerator.getWritableArea}) is private, and it is four lines.
     */
    private static net.minecraft.world.level.levelgen.structure.BoundingBox writableArea(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        LevelHeightAccessor height = chunk.getHeightAccessorForGeneration();
        int x = pos.getMinBlockX(), z = pos.getMinBlockZ();
        return new net.minecraft.world.level.levelgen.structure.BoundingBox(
                x, height.getMinY() + 1, z, x + 15, height.getMaxY(), z + 15);
    }

    private static final org.slf4j.Logger LOGGER_STRUCTURES =
            com.mojang.logging.LogUtils.getLogger();

    /**
     * Run only vanilla's {@code UNDERGROUND_ORES} decoration step for the chunk's biome — the ore and
     * stone-blob placed features — so the stone under cities/roads/structures gets the same veins the
     * wild does, without any of the surface/fluid features that would damage a build. Mirrors the
     * seeding {@code ChunkGenerator.applyBiomeDecoration} uses so placement is deterministic. Wrapped so
     * an ore feature can never take chunk generation down.
     */
    private void placeUndergroundOres(WorldGenLevel level, ChunkAccess chunk) {
        try {
            net.minecraft.core.SectionPos sp = net.minecraft.core.SectionPos.of(chunk.getPos(), level.getMinSectionY());
            net.minecraft.core.BlockPos origin = sp.origin();
            net.minecraft.world.level.biome.BiomeGenerationSettings settings = level.getBiome(origin).value()
                    .getGenerationSettings();
            int step = net.minecraft.world.level.levelgen.GenerationStep.Decoration.UNDERGROUND_ORES.ordinal();
            if (step >= settings.features().size())
                return;
            net.minecraft.world.level.levelgen.WorldgenRandom random = new net.minecraft.world.level.levelgen.WorldgenRandom(
                    new net.minecraft.world.level.levelgen.XoroshiroRandomSource(level.getSeed()));
            long decoSeed = random.setDecorationSeed(level.getSeed(), origin.getX(), origin.getZ());
            int index = 0;
            for (net.minecraft.core.Holder<net.minecraft.world.level.levelgen.placement.PlacedFeature> pf : settings
                    .features().get(step)) {
                random.setFeatureSeed(decoSeed, index++, step);
                pf.value().placeWithBiomeCheck(level, this, random, origin);
            }
        } catch (Throwable t) {
            // ore decoration must never break chunk generation
        }
    }

    /** Remove snow layers that vanilla decoration left sitting directly on ice (an illegal, cascading
     *  state). Scans the chunk's own columns only — safe within the decoration write radius. */
    private static void stripSnowOnIce(ChunkAccess chunk) {
        ChunkPos pos = chunk.getPos();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int top = chunk.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z);
                net.minecraft.core.BlockPos p = new net.minecraft.core.BlockPos(pos.getMinBlockX() + x, top,
                        pos.getMinBlockZ() + z);
                if (!chunk.getBlockState(p).is(net.minecraft.world.level.block.Blocks.SNOW))
                    continue;
                net.minecraft.world.level.block.state.BlockState below = chunk.getBlockState(p.below());
                if (below.is(net.minecraft.world.level.block.Blocks.ICE)
                        || below.is(net.minecraft.world.level.block.Blocks.PACKED_ICE)
                        || below.is(net.minecraft.world.level.block.Blocks.BLUE_ICE))
                    chunk.setBlockState(p, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            }
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        // The End is vanilla's 0..256, not the overworld's -64..319: these two feed structure placement and
        // the heightmaps, so claiming overworld bounds in the End would have structures probe outside the world.
        return parseEnvironment(environment) == me.daddychurchill.CityWorld.compat.Environment.THE_END ? 256 : 384;
    }

    /**
     * The first Y that is <em>not</em> water — which is what vanilla means by "sea level"
     * ({@code Aquifer.FluidStatus.at} gives fluid only where {@code y < fluidLevel}).
     *
     * <p><b>That is one above {@link #UPSTREAM_SEA_LEVEL}, and deliberately so.</b> CityWorld fills
     * water <em>through</em> its sea level inclusive ({@code for (y = subsurfaceY + 1; y <= coverY; y++)}
     * with {@code coverY = seaLevel}), so with a sea level of 63 the topmost water block is at 63 and
     * the surface plane is 64.0 — a block higher than vanilla would put it for the same number. Its
     * beaches sit flush with that waterline (sand at 63, dry), which is what makes them read as
     * beaches. So the terrain is right and it is upstream's; it is only the number reported to
     * vanilla that has to be translated, or vanilla thinks our oceans are a block deeper than they
     * are.
     */
    @Override
    public int getSeaLevel() {
        return UPSTREAM_SEA_LEVEL + 1;
    }

    @Override
    public int getMinY() {
        // The signature carries no level to ask, and this is consulted before one exists. It must
        // agree with the dimension's own min_y (minecraft:overworld => -64, minecraft:the_end => 0).
        return parseEnvironment(environment) == me.daddychurchill.CityWorld.compat.Environment.THE_END ? 0 : -64;
    }

    /**
     * The first free Y above the column — vanilla uses this to place spawn and to decide where
     * structures sit, so it has to agree with what {@link #fillFromNoise} actually builds. A flat
     * constant here would strand spawn in the air or inside a mountain.
     *
     * <p>Derived from {@link #getBaseColumn} and the heightmap's own predicate, exactly as vanilla
     * does, so the two can never drift apart. That matters more than it looks: {@code WORLD_SURFACE}
     * counts water as surface while {@code OCEAN_FLOOR} does not, so simply returning the terrain
     * height answers wrong for every sea column — by up to the sea's depth, which would drop spawn
     * under the water.
     */
    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState randomState) {
        // The End's terrain is vanilla's, so vanilla answers for it (end cities ask this for their y >= 60 rule).
        if (isEnd())
            return vanillaEnd().getBaseHeight(x, z, type, level, vanillaEndRandom);
        NoiseColumn column = getBaseColumn(x, z, level, randomState);
        Predicate<BlockState> isOpaque = type.isOpaque();
        for (int y = level.getMaxY(); y >= level.getMinY(); y--)
            if (isOpaque.test(column.getBlock(y)))
                return y + 1;
        return level.getMinY();
    }

    /**
     * The block column at a position, without generating the chunk.
     *
     * <p>Reproduces the shaper's height and its broad vertical profile — bedrock, the
     * deepslate/stone strata, the surface, and the sea fill above a submerged column — but not the
     * detail that needs a whole chunk to compute (caves, lava fields, the exact beach/snow
     * banding). Vanilla asks this to find somewhere solid to stand, so what has to be right is
     * where solid ends and where water sits.
     */
    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        if (isEnd())
            return vanillaEnd().getBaseColumn(x, z, level, vanillaEndRandom);
        CityWorldGenerator context = context(level);
        OreProvider ores = context.oreProvider;
        int terrainY = context.shapeProvider.findBlockY(context, x, z);

        // Mirrors preGenerateChunk: a column at or below sea level gets the fluid palette, and one
        // below sea level is flooded to sea level when the world has aboveground fluids.
        boolean submerged = terrainY < context.seaLevel;
        boolean flooded = submerged && context.getSettings().includeAbovegroundFluids;
        Material surface = (submerged || terrainY == context.seaLevel)
                ? ores.fluidSurfaceMaterial
                : ores.surfaceMaterial;

        int minY = level.getMinY();
        int height = level.getHeight();
        BlockState[] column = new BlockState[height];
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            Material material;
            if (y == minY)
                material = ores.substratumMaterial;
            else if (y < terrainY)
                material = ores.stratumMaterialAt(ores.stratumMaterial, x, y, z);
            else if (y == terrainY)
                material = surface;
            else if (flooded && y <= context.seaLevel)
                material = ores.fluidMaterial;
            else
                material = Material.AIR;
            column[i] = material.getBlockState();
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        CityWorldGenerator context = context();
        if (context == null) {
            info.add("CityWorld: context not built yet");
            return;
        }
        info.add(String.format("CityWorld: %s, terrainY=%d, street=%d, sea=%d",
                context.shapeProvider.getCollectionName(),
                context.shapeProvider.findBlockY(context, pos.getX(), pos.getZ()),
                context.streetLevel, context.seaLevel));
    }

    /** The context if it has been built, else null — for diagnostics that must not force creation. */
    private CityWorldGenerator context() {
        return context;
    }
}
