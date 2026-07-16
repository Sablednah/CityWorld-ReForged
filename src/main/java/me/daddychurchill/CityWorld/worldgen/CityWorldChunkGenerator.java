package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.blending.Blender;
import net.minecraft.world.level.levelgen.structure.StructureSet;

/**
 * The CityWorld {@link ChunkGenerator}.
 *
 * <p><b>Phase 3 spike.</b> This is deliberately a placeholder: it lays down a flat bedrock →
 * stone → grass profile through the real {@link InitialBlocks} seam, purely to prove the pipeline
 * end-to-end — that a codec-registered custom generator loads, is selected via a dimension / world
 * preset, and successfully writes blocks through our ported block layer. The actual CityWorld
 * terrain brain (ShapeProvider → PlatMap → contexts) is wired in during later phases; when it is,
 * {@link #fillFromNoise} will drive it instead of this flat fill.
 */
public class CityWorldChunkGenerator extends ChunkGenerator {

    public static final MapCodec<CityWorldChunkGenerator> CODEC = RecordCodecBuilder.mapCodec(
            instance -> instance.group(
                    BiomeSource.CODEC.fieldOf("biome_source").forGetter(ChunkGenerator::getBiomeSource)
            ).apply(instance, CityWorldChunkGenerator::new));

    // Placeholder vertical profile (Phase 4 replaces this with the ShapeProvider's real levels).
    private static final int SURFACE_Y = 63;

    /**
     * The world facts the per-world context needs, at their 1.14 values so the ported
     * {@code ShapeProvider} reproduces upstream's terrain exactly. P4 modernizes this to
     * {@code -64..319} (PORTING.md, "World layout: modernize now").
     */
    private static final int UPSTREAM_MAX_HEIGHT = 256;
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

    public CityWorldChunkGenerator(BiomeSource biomeSource) {
        super(biomeSource);
    }

    /** The per-world context, created on first use. */
    private CityWorldGenerator context() {
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
                    local = new CityWorldGenerator(levelSeed, UPSTREAM_MAX_HEIGHT, UPSTREAM_SEA_LEVEL,
                            CityWorldGenerator.WorldStyle.NORMAL);
                    context = local;
                }
            }
        }
        return local;
    }

    @Override
    protected MapCodec<? extends ChunkGenerator> codec() {
        return CODEC;
    }

    @Override
    public CompletableFuture<ChunkAccess> fillFromNoise(Blender blender, RandomState randomState,
            StructureManager structureManager, ChunkAccess chunk) {
        CityWorldGenerator context = context();
        int minY = chunk.getMinY();

        InitialBlocks blocks = new InitialBlocks(context, chunk, chunk.getPos().x, chunk.getPos().z);
        blocks.setBlocks(0, 16, minY, minY + 1, 0, 16, Material.BEDROCK);
        blocks.setBlocks(0, 16, minY + 1, SURFACE_Y, 0, 16, Material.STONE);
        blocks.setBlocks(0, 16, SURFACE_Y, SURFACE_Y + 1, 0, 16, Material.GRASS_BLOCK);

        return CompletableFuture.completedFuture(chunk);
    }

    @Override
    public void buildSurface(WorldGenRegion region, StructureManager structureManager,
            RandomState randomState, ChunkAccess chunk) {
        // The flat fill already places the surface; nothing extra for the spike.
    }

    @Override
    public void applyCarvers(WorldGenRegion region, long seed, RandomState randomState,
            BiomeManager biomeManager, StructureManager structureManager, ChunkAccess chunk) {
        // No vanilla carvers (caves/ravines) — CityWorld carves its own mines/sewers.
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

    /** Suppress vanilla biome decoration (trees, flowers, ores, lakes) — CityWorld places its own. */
    @Override
    public void applyBiomeDecoration(WorldGenLevel level, ChunkAccess chunk,
            StructureManager structureManager) {
    }

    @Override
    public void spawnOriginalMobs(WorldGenRegion region) {
    }

    @Override
    public int getGenDepth() {
        return 384;
    }

    @Override
    public int getSeaLevel() {
        return SURFACE_Y;
    }

    @Override
    public int getMinY() {
        return -64;
    }

    @Override
    public int getBaseHeight(int x, int z, Heightmap.Types type, LevelHeightAccessor level,
            RandomState randomState) {
        return SURFACE_Y + 1;
    }

    @Override
    public NoiseColumn getBaseColumn(int x, int z, LevelHeightAccessor level, RandomState randomState) {
        int minY = level.getMinY();
        int height = level.getHeight();
        BlockState[] column = new BlockState[height];
        for (int i = 0; i < height; i++) {
            int y = minY + i;
            if (y == minY) {
                column[i] = Blocks.BEDROCK.defaultBlockState();
            } else if (y < SURFACE_Y) {
                column[i] = Blocks.STONE.defaultBlockState();
            } else if (y == SURFACE_Y) {
                column[i] = Blocks.GRASS_BLOCK.defaultBlockState();
            } else {
                column[i] = Blocks.AIR.defaultBlockState();
            }
        }
        return new NoiseColumn(minY, column);
    }

    @Override
    public void addDebugScreenInfo(List<String> info, RandomState randomState, BlockPos pos) {
        info.add("CityWorld: placeholder flat generator (Phase 3 spike)");
    }
}
