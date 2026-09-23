package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * What vanilla WILL put in a chunk, computed before the chunk exists.
 *
 * <p><b>Why this is possible.</b> {@code ChunkGenerator.createStructures} is a pure function of the
 * seed, the structure sets, the generator's own {@code getBaseHeight}/biome answers, and the template
 * files. It reads a chunk only to count references, which never moves a piece. So the same call can be
 * made from the planner, for any chunk at any distance, with no chunk in existence, and it must give
 * the same {@link StructureStart} the chunk will later carry. Not "override where structures go":
 * compute what vanilla will compute, earlier. Cataclysm's own structure classes read nothing outside
 * the {@code GenerationContext} either (decompiled, 2026-09-23).
 *
 * <p><b>Proved</b> with the probe ({@code -Dcityworld.probe.forecast=true}): 5 of 5 starts in 2,809
 * chunks matched the stored start on footprint, floor and piece count. Compare exactly those three and
 * never {@code maxY}: vanilla's own chunk reload rebuilds a {@code TERRAIN_MATCHING} piece's box from
 * its template and drops the upward growth {@code JigsawPlacement} gave it, so a stored village
 * street can read 1 block tall where the forecast (and the start as first generated) reads 13.
 *
 * <p><b>Two rules keep it a pure function.</b> {@code getBaseHeight} must keep answering the RAW
 * terrain, never the planned or padded height -- vanilla asks it while placing, and if it answered
 * the plan there would be a real chicken-and-egg cycle. And nothing here may recurse into
 * {@code getPlatMap} or throw into planning: a failure forecasts nothing and logs under diagnostics.
 *
 * <p><b>Cost.</b> About 80 ms per start the first time (template loads included), 0.15 ms per chunk
 * for the placement scan over every set. Memoised per origin chunk, so it is paid once per structure
 * per world. The memo is filled OUTSIDE any map lock: the stall of 2026-09-23 was a long mapping
 * function inside {@code computeIfAbsent}, and a jigsaw assembly is exactly that.
 */
public final class StructureForecast {

    /**
     * Chunks from an origin a structure may reach. Vanilla bounds a jigsaw at 128 blocks (8 chunks);
     * Cataclysm's own jigsaw at 192 (12). A candidate further out than its structure reaches costs one
     * memo lookup and nothing else, so this errs wide.
     */
    public static final int REACH = 12;

    private static final boolean DIAG = System.getProperty("cityworld.probe") != null
            || System.getProperty("cityworld.diagnostics") != null;

    private final CityWorldChunkGenerator generator;
    private final ChunkGeneratorStructureState state;
    private final List<Holder<StructureSet>> sets;

    private record Bound(RegistryAccess registries, StructureTemplateManager templates,
            LevelHeightAccessor height, ResourceKey<Level> dimension) {
    }

    private volatile Bound bound;
    private volatile boolean warnedUnbound;

    /** Origin chunk -> the starts that fire there (every set, so a candidate is computed once). */
    private final ConcurrentHashMap<Long, List<StructureStart>> memo = new ConcurrentHashMap<>();

    public StructureForecast(CityWorldChunkGenerator generator, ChunkGeneratorStructureState state) {
        this.generator = generator;
        this.state = state;
        this.sets = state.possibleStructureSets();
    }

    /** Binds to the level that owns this generator; called from createStructures and lazily on first use. */
    public boolean bind() {
        if (bound != null)
            return true;
        try {
            MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
            if (server == null)
                return false;
            for (ServerLevel level : server.getAllLevels()) {
                if (level.getChunkSource().getGenerator() == generator) {
                    bound = new Bound(level.registryAccess(), level.getStructureManager(), level, level.dimension());
                    return true;
                }
            }
        } catch (Throwable t) {
            if (DIAG)
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("FORECAST bind FAILED", t);
        }
        return false;
    }

    /** Whether forecasts can be made yet. False only before the owning level is registered with the server. */
    public boolean available() {
        if (bind())
            return true;
        if (!warnedUnbound && DIAG) {
            warnedUnbound = true;
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "FORECAST: no level owns this generator yet -- reservations fall back to clearance squares");
        }
        return false;
    }

    /** The starts whose origin is this chunk: what {@code createStructures} will store there. Never throws. */
    public List<StructureStart> startsAt(int chunkX, int chunkZ) {
        long key = ChunkPos.pack(chunkX, chunkZ);
        List<StructureStart> got = memo.get(key);
        if (got != null)
            return got;
        List<StructureStart> computed;
        try {
            computed = compute(chunkX, chunkZ);
        } catch (Throwable t) {
            if (DIAG)
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("FORECAST FAILED at chunk {},{}", chunkX, chunkZ, t);
            computed = List.of();
        }
        // Two threads may race to the same origin; both compute the same thing, and everyone keeps the
        // winner's instances so identity-based dedupe downstream sees one start, not two.
        List<StructureStart> winner = memo.putIfAbsent(key, computed);
        return winner != null ? winner : computed;
    }

    /**
     * Every start whose bounding box, widened by {@code marginChunks}, covers this chunk -- from any
     * origin within {@link #REACH}. This is the question the planner and the pad ask; neither needs a
     * chunk loaded to get the answer.
     */
    public List<StructureStart> startsCovering(int chunkX, int chunkZ, int marginChunks) {
        List<StructureStart> out = new ArrayList<>();
        if (!available())
            return out;
        int minX = (chunkX << 4) - (marginChunks << 4), maxX = (chunkX << 4) + 15 + (marginChunks << 4);
        int minZ = (chunkZ << 4) - (marginChunks << 4), maxZ = (chunkZ << 4) + 15 + (marginChunks << 4);
        forEachCandidate(chunkX, chunkZ, (ox, oz) -> {
            for (StructureStart start : startsAt(ox, oz)) {
                BoundingBox box = start.getBoundingBox();
                if (box.maxX() < minX || box.minX() > maxX || box.maxZ() < minZ || box.minZ() > maxZ)
                    continue;
                if (!out.contains(start))
                    out.add(start);
            }
        });
        return out;
    }

    private interface Candidate {
        void at(int chunkX, int chunkZ);
    }

    /**
     * The origin chunks within reach that any set could fire in. A {@code RandomSpreadStructurePlacement}
     * (Cataclysm's placement is a subclass) has exactly one candidate per region cell, so within reach
     * that is a handful of cells rather than a (2r+1)^2 scan. Any other placement gets the flat scan.
     */
    private void forEachCandidate(int chunkX, int chunkZ, Candidate visit) {
        java.util.Set<Long> seen = new java.util.HashSet<>();
        boolean flat = false;
        for (Holder<StructureSet> set : sets) {
            StructurePlacement placement = set.value().placement();
            if (placement instanceof RandomSpreadStructurePlacement spread) {
                int spacing = Math.max(1, spread.spacing());
                for (int rx = Math.floorDiv(chunkX - REACH, spacing); rx <= Math.floorDiv(chunkX + REACH, spacing); rx++)
                    for (int rz = Math.floorDiv(chunkZ - REACH, spacing); rz <= Math.floorDiv(chunkZ + REACH, spacing); rz++) {
                        ChunkPos cand = spread.getPotentialStructureChunk(state.getLevelSeed(), rx * spacing, rz * spacing);
                        if (Math.abs(cand.x() - chunkX) > REACH || Math.abs(cand.z() - chunkZ) > REACH)
                            continue;
                        if (seen.add(ChunkPos.pack(cand.x(), cand.z())))
                            visit.at(cand.x(), cand.z());
                    }
            } else if (!(placement instanceof net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement)) {
                flat = true;   // an unknown placement type: scan every chunk in reach once, below
            }
        }
        if (flat)
            for (int x = chunkX - REACH; x <= chunkX + REACH; x++)
                for (int z = chunkZ - REACH; z <= chunkZ + REACH; z++)
                    if (seen.add(ChunkPos.pack(x, z)))
                        visit.at(x, z);
    }

    /** Exactly vanilla's createStructures for one chunk, minus the chunk. */
    private List<StructureStart> compute(int chunkX, int chunkZ) {
        Bound b = bound;
        if (b == null)
            return List.of();
        List<StructureStart> out = new ArrayList<>();
        ChunkPos pos = new ChunkPos(chunkX, chunkZ);
        for (Holder<StructureSet> set : sets) {
            StructurePlacement placement = set.value().placement();
            // Concentric rings (strongholds) force the whole ring set to be laid out under a lock;
            // the reservation never asked about them and the pad never needs them. Skip.
            if (placement instanceof net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement)
                continue;
            if (!placement.isStructureChunk(state, chunkX, chunkZ))
                continue;
            // vanilla: a set is skipped when one of its structures already started here (from an earlier set)
            boolean already = false;
            for (StructureSet.StructureSelectionEntry e : set.value().structures())
                for (StructureStart s : out)
                    if (s.getStructure() == e.structure().value())
                        already = true;
            if (already)
                continue;
            List<StructureSet.StructureSelectionEntry> entries = set.value().structures();
            if (entries.size() == 1) {
                StructureStart start = tryGenerate(entries.get(0), b, pos);
                if (start != null)
                    out.add(start);
                continue;
            }
            // Exactly vanilla's weighted draw order, including the RNG it seeds for it.
            List<StructureSet.StructureSelectionEntry> list = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(state.getLevelSeed(), chunkX, chunkZ);
            int total = 0;
            for (StructureSet.StructureSelectionEntry e : list)
                total += e.weight();
            while (!list.isEmpty()) {
                int roll = random.nextInt(total);
                int k = 0;
                for (StructureSet.StructureSelectionEntry e : list) {
                    roll -= e.weight();
                    if (roll < 0)
                        break;
                    k++;
                }
                StructureSet.StructureSelectionEntry chosen = list.get(k);
                StructureStart start = tryGenerate(chosen, b, pos);
                if (start != null) {
                    out.add(start);
                    break;
                }
                list.remove(k);
                total -= chosen.weight();
            }
        }
        return out.isEmpty() ? List.of() : List.copyOf(out);
    }

    // ⚠ Per-version: Structure.generate has three shapes across the six lines. 1.20.1 and 1.21.1 take
    // (registries, generator, biomes, randomState, templates, seed, pos, references, height, biomePredicate);
    // 1.21.11/26.1/26.2 add the structure holder and the dimension in front; 26.3 adds a Climate.Sampler
    // after the biome source. This is the 1.21.11 shape.
    private StructureStart tryGenerate(StructureSet.StructureSelectionEntry entry, Bound b, ChunkPos pos) {
        var structure = entry.structure().value();
        Predicate<Holder<Biome>> validBiome = structure.biomes()::contains;
        // references = 0: vanilla's fetchReferences counts existing references, which only feeds the
        // start's own counter and never its placement.
        StructureStart start = structure.generate(entry.structure(), b.dimension(), b.registries(), generator,
                generator.getBiomeSource(), state.randomState(), b.templates(), state.getLevelSeed(), pos, 0,
                b.height(), validBiome);
        return start.isValid() ? start : null;
    }

    /** Diagnostics: how many origins have been forecast so far. */
    public int size() {
        return memo.size();
    }
}
