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
                    // The biome source answers getNoiseBiome from this context (terrain height + climate),
                    // so hand it over the moment it exists — this is the earliest point it can be had.
                    if (this.biomeSource instanceof CityWorldBiomes cityBiomes)
                        cityBiomes.bindContext(local);
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
        int chunkX = chunk.getPos().x();
        int chunkZ = chunk.getPos().z();

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
        this.levelSeed = seed;
        this.levelSeedKnown = true;
        return ChunkGeneratorStructureState.createForNormal(
                randomState, seed, this.biomeSource, onlyAllowed(lookup));
    }

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
        me.daddychurchill.CityWorld.Plats.PlatLot lot = platmap.getMapLot(pos.x(), pos.z());
        boolean wild = context.isModernStyle() && lot != null
                && lot.style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.NATURE
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
            if (features.isEmpty() || !containsCaveBiome(chunk))
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
