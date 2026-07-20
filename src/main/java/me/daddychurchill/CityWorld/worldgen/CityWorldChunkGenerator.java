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
import net.minecraft.core.QuartPos;
import net.minecraft.resources.RegistryFileCodec;
import net.minecraft.server.level.WorldGenRegion;
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
                            .optionalFieldOf("settings").forGetter(g -> g.settings)
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
        super(biomeSource);
        this.decayed = decayed;
        this.style = style;
        this.settings = settings;
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
                    local = new CityWorldGenerator(levelSeed, TERRAIN_CEILING, UPSTREAM_SEA_LEVEL,
                            CityWorldGenerator.parseStyle(style), level.getMinY(), level.getMaxY(), decayed,
                            settingsData);
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

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        CityWorldGenerator context = context(chunk);
        int chunkX = chunk.getPos().x;
        int chunkZ = chunk.getPos().z;

        InitialBlocks blocks = new InitialBlocks(context, chunk, chunkX, chunkZ);

        // Fetch (or plan) the city block this chunk belongs to, then let it drive. The platmap
        // routes to whichever lot owns this chunk, and the lot calls the shape provider itself —
        // so terrain and city come from one path rather than two.
        PlatMap platmap = context.getPlatMap(chunkX, chunkZ);
        platmap.generateChunk(blocks, IGNORE_BIOMES);

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
            RandomState randomState, ChunkAccess chunk) {
        // Nothing: the shaper lays its own surface down in fillFromNoise (that is what the
        // surfaceMaterial/subsurfaceMaterial strata are), so vanilla's surface pass has no job here.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // No vanilla carvers (caves/ravines) — CityWorld carves its own mines/sewers.
    }

    /**
     * Fills the chunk's biomes from CityWorld's own terrain instead of the flat plains a fixed source
     * would give — ocean in the deeps, beaches at the waterline, forest/hills/snowy peaks up the
     * mountains — so grass, water and foliage colour follow the land. The classification lives in
     * {@link CityWorldBiomeSource#classify} (and its configurable palette); the seeded terrain height
     * only the generator has, so it drives it here. Runs before {@link #fillFromNoise}, but the shaper
     * is deterministic, so {@code findBlockY} agrees with what the terrain pass will build.
     *
     * <p>If the dimension isn't using a {@link CityWorldBiomeSource} (e.g. a plain fixed source), this
     * falls back to vanilla's behaviour.
     */
    @Override
    public CompletableFuture<ChunkAccess> createBiomes(RandomState randomState, Blender blender,
            StructureManager structureManager, ChunkAccess chunk) {
        if (!(this.getBiomeSource() instanceof CityWorldBiomeSource source))
            return super.createBiomes(randomState, blender, structureManager, chunk);

        return CompletableFuture.supplyAsync(() -> {
            CityWorldGenerator context = context(chunk);
            boolean decayedNature = context.getSettings().includeDecayedNature;
            // Biome is per column (2D), so classify once per (quartX, quartZ) and reuse down the column.
            java.util.Map<Long, Holder<Biome>> perColumn = new java.util.HashMap<>();
            BiomeResolver resolver = (qx, qy, qz, sampler) -> perColumn.computeIfAbsent(
                    ((long) qx << 32) | (qz & 0xFFFFFFFFL),
                    key -> source.classify(context,
                            context.shapeProvider.findBlockY(context, QuartPos.toBlock(qx), QuartPos.toBlock(qz)),
                            decayedNature));
            chunk.fillBiomesFromNoise(resolver, randomState.sampler());
            return chunk;
        }, Util.backgroundExecutor().forName("cityworld_biomes"));
    }

    /**
     * Suppress vanilla structures (villages, mineshafts, trial chambers, strongholds, …) by giving
     * the structure state an empty set — CityWorld generates its own structures.
     *
     * <p>Future idea (see PORTING.md "Future ideas"): rather than discard these, harvest the points
     * where vanilla <em>would</em> have placed structures and reuse them as city anchors.
     */
    @Override
    public ChunkGeneratorStructureState createState(HolderLookup<StructureSet> lookup,
            RandomState randomState, long seed) {
        // Doubles as the one place vanilla tells a ChunkGenerator its world seed — see context().
        this.levelSeed = seed;
        this.levelSeedKnown = true;
        return ChunkGeneratorStructureState.createForFlat(
                randomState, seed, this.biomeSource, Stream.<Holder<StructureSet>>empty());
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
        CityWorldGenerator context = context(level);
        ChunkPos pos = chunk.getPos();

        PlatMap platmap = context.getPlatMap(pos.x, pos.z);

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
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
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
        // agree with the dimension's own min_y (minecraft:overworld => -64).
        return -64;
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
