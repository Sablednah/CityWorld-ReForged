package me.daddychurchill.CityWorld.worldgen;

import java.util.List;

import net.minecraft.core.Holder;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;

/**
 * Which chunks a structure has already spoken for, so CityWorld can plan <em>around</em> them rather
 * than building a city where one is about to appear.
 *
 * <p><b>Why this can be answered at planning time at all.</b> Structure placement is analytic: a
 * {@code StructurePlacement} decides from the world seed, a salt and its spacing/separation whether a
 * given chunk is a candidate, with no chunk, no terrain and no biomes required. That is exactly how
 * {@code /locate} finds a structure thousands of blocks away in land nobody has generated. So the
 * planner can ask "is anything coming here?" before it lays a single road, which is the only order
 * that works — reserving after the fact would mean amputating roads and buildings at the boundary,
 * which looks worse than the problem it fixes.
 *
 * <p><b>Two ways to answer, and the first is exact.</b> With a {@link StructureForecast} the reservation
 * is the structure's REAL footprint: the forecast makes vanilla's own {@code createStructures} call
 * ahead of any chunk, so it knows the bounding box, and it knows when the biome or water check will
 * fail and nothing will be built at all. Reserved = a forecast start's box plus a one-chunk margin for
 * the pad's taper. The owner's complaint this answers (2026-09-23): "we reserve the best space we can
 * when plotting the city, but sometimes nothing ends up in there, and when it does it has to reserve
 * loads more". The clearance square below is now the FALLBACK, for the moments before the level that
 * owns the generator is registered (the forecast cannot bind yet) or when {@code -Dcityworld.reserve=clearance}
 * asks for the old behaviour.
 *
 * <p><b>The fallback deliberately over-reserves.</b> {@code hasStructureChunkInRange} answers the placement
 * question only; whether the structure's <em>biome</em> predicate will pass is decided later, at
 * structure-start time. So some reserved chunks never receive a structure and simply stay natural.
 *
 * <p><b>Concentric rings are skipped</b> — that is strongholds. They are underground, so they never
 * compete with what CityWorld builds on the surface, and asking about them forces
 * {@code ensureStructuresGenerated()} to lay out the whole ring set under a lock, which is not
 * something to trigger from several planning threads at once.
 */
public final class StructureReservations {

    /**
     * Chunks of clear ground reserved around a planned structure.
     *
     * <p><b>Five, because that is what the structures themselves declare.</b> This started at 1 — a
     * single polite ring — on the assumption that the reservation only had to keep a road off the
     * doorstep. It does not: the reservation has to cover the structure's own <em>footprint</em>, and a
     * vanilla village declares {@code max_distance_from_center: 80}, which is 5 chunks. At 1 the city
     * planned straight through everything beyond the start chunk, which in game looked like a cleared
     * corner with the rest of the structure still sliced through a farm and a road grid (owner,
     * 2026-09-21, on Cataclysm's cursed pyramid — but villages and pillager outposts, both also 80,
     * would have done exactly the same).
     *
     * <p>Vanilla bounds any jigsaw structure at {@code MAX_TOTAL_STRUCTURE_RANGE = 128} blocks, so 5
     * covers villages and outposts outright and most of the worst case. It is not free: at village
     * spacing (34 chunks) an 11x11 reservation is roughly a tenth of the land, and a structure that
     * grows from its origin rather than its centre — a custom, non-jigsaw one — is still only partly
     * covered, because nothing exposes that offset without generating the structure first.
     */
    public static final int DEFAULT_CLEARANCE = 5;

    private final ChunkGeneratorStructureState state;
    private final int clearance;
    /** Null when the world has none (a twin without a forecast, or a test harness). */
    private final StructureForecast forecast;
    private static final boolean FORCE_CLEARANCE = "clearance".equals(System.getProperty("cityworld.reserve"));
    /** Chunks of margin around a forecast footprint: the pad's taper needs one. */
    public static final int FOOTPRINT_MARGIN = 1;

    /**
     * Resolved once: the sets worth asking about, each with the clearance IT needs.
     *
     * <p>Per set, not per world, because one number cannot fit both a pillager outpost and a 209-block
     * acropolis. Resolved at construction rather than per query so the answer cannot depend on what has
     * generated so far — a reservation that varied with generation order would make the same seed plan
     * differently between runs, and the self-test hashes plans across six Minecraft versions.
     */
    private record Reserved(Holder<StructureSet> set, int clearance) {}

    private final List<Reserved> sets;

    private StructureReservations(ChunkGeneratorStructureState state, int clearance,
            List<Reserved> sets, StructureForecast forecast) {
        this.state = state;
        this.clearance = clearance;
        this.sets = sets;
        this.forecast = FORCE_CLEARANCE ? null : forecast;
    }

    /**
     * Builds the reservations for a world, or returns {@code null} when there is nothing to reserve —
     * no state, or every allowed set is a ring placement. A null result lets the caller skip the check
     * entirely rather than pay for a query that can only ever answer false.
     */
    public static StructureReservations of(ChunkGeneratorStructureState state, int clearance) {
        return of(state, clearance, null);
    }

    public static StructureReservations of(ChunkGeneratorStructureState state, int clearance,
            StructureForecast forecast) {
        if (state == null)
            return null;
        try {
            int base = Math.max(0, clearance);
            List<Reserved> usable = state.possibleStructureSets().stream()
                    .filter(set -> !(set.value().placement() instanceof ConcentricRingsStructurePlacement))
                    .filter(StructureReservations::reachesTheSurface)
                    .map(set -> new Reserved(set, clearanceOf(set, base)))
                    .toList();
            if (DIAG)
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                        "RESERVE: {} of {} possible sets kept (surface, non-ring); clearances {}",
                        usable.size(), state.possibleStructureSets().size(),
                        usable.stream().map(r -> r.set().unwrapKey().map(k -> k.location().getPath()).orElse("?")
                                + "=" + r.clearance()).toList());
            return usable.isEmpty() ? null : new StructureReservations(state, base, usable, forecast);
        } catch (Throwable t) {
            // Planning must never fail because of this: no reservations is the old behaviour.
            return null;
        }
    }

    /**
     * Whether any structure in this set is built at the <em>surface</em> step — the only ones that
     * compete for the ground CityWorld builds on.
     *
     * <p><b>Without this the feature does real harm.</b> {@code #cityworld:allowed} ships trial chambers
     * and ancient cities, and both are ordinary random-spread placements; reserving for them would punch
     * unexplained meadows through the middle of every city on behalf of something buried sixty blocks
     * underneath it, which reads as deliberate design rather than a bug. The generation step is exactly
     * the distinction wanted, it is declared in the structure's own JSON, and — like the placement
     * itself — it can be read with no chunk in existence.
     *
     * <p>A set is kept if <em>any</em> of its structures is a surface one: a mixed set (Cataclysm's
     * {@code desert_structures} pairs a surface village with a buried site) still wants its room.
     */
    /**
     * The widest clearance any structure in this set declares, or {@code base} if none does.
     *
     * <p>A set can hold several structures (Cataclysm's {@code desert_structures} pairs a village with
     * a buried site), and the reservation has to cover whichever turns up.
     */
    private static int clearanceOf(Holder<StructureSet> set, int base) {
        int want = base;
        try {
            for (var entry : set.value().structures())
                want = Math.max(want, me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps
                        .clearanceFor(entry.structure(), base));
        } catch (Throwable t) {
            if (DIAG)
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                        "RESERVE: clearanceOf THREW for {} -- falling back to {}", set.unwrapKey().orElse(null), base, t);
            return base;
        }
        if (DIAG)
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("RESERVE: set {} -> clearance {}",
                    set.unwrapKey().map(Object::toString).orElse("?"), want);
        return want;
    }

    private static final boolean DIAG = System.getProperty("cityworld.probe") != null
            || System.getProperty("cityworld.diagnostics") != null;

    private static boolean reachesTheSurface(Holder<StructureSet> set) {
        for (var entry : set.value().structures())
            if (entry.structure().value().step() == GenerationStep.Decoration.SURFACE_STRUCTURES)
                return true;
        return false;
    }

    /**
     * Whether a structure is planned in or near this chunk, so CityWorld should leave it as nature.
     *
     * <p>Never throws: a structure query that goes wrong must cost a reservation, not a platmap.
     * {@code populateLots} catches everything and falls back to nature for the whole 10x10 grid, so an
     * exception escaping here would read as "the cities stopped appearing".
     */
    /**
     * Memo for {@link #isReserved}. Safe because the answer is a pure function of the chunk position:
     * {@code sets} is fixed at construction and {@code state} is fixed for the world, so nothing here
     * can vary with generation order — unlike a learned clearance, which would have made the same seed
     * plan differently between runs.
     *
     * <p><b>Why it is needed.</b> {@code hasStructureChunkInRange} is a flat {@code (2r+1)^2} scan, and
     * every candidate in it builds a {@code WorldgenRandom(new LegacyRandomSource(0L))} and runs
     * {@code setLargeFeatureWithSalt} — an allocation plus seeded maths per candidate, not a lookup. At
     * the default clearance of 5 that is 121 per query; at the 12 an acropolis declares it is 625. And
     * planning asks the same coordinates repeatedly from THREE sites: the PlatLot constructor,
     * setLot and paveLot. A 10x10 platmap therefore issued on the order of 100-300 queries, each
     * re-deriving what the others had just computed.
     *
     * <p>⚠ <b>This was written as the fix for the owner's minute-long stall, and it was not.</b>
     * The claim here used to read "measured in game 2026-09-22: chunks stopped arriving for a minute
     * or two" — which was the SYMPTOM going away once, not a measurement of this code. Proper timing
     * on 2026-09-23, on the owner's machine with Cataclysm installed, put reservation at 2125 ms
     * across 492,863 calls (0.00 ms mean) against a single {@code context.populateMap} of 71,557 ms.
     * The memo is still worth having — it is the difference between one scan per chunk and three —
     * but it is a tidy-up, not a cure. See PORTING.md, "The worldgen stall".
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> memo =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isReserved(int chunkX, int chunkZ) {
        long key = net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ);
        Boolean got = memo.get(key);
        if (got != null)
            return got;
        // Computed OUTSIDE the map's lock: a forecast may assemble a jigsaw, and a long mapping
        // function inside computeIfAbsent is exactly what froze the server on 2026-09-23.
        // Memoise only an answer that cannot change: a forecast that has not bound to its level yet
        // would fall back to the clearance square, and caching THAT would make the plan depend on
        // timing. Planning never runs before the level registers in practice; this keeps it true.
        boolean cacheable = forecast == null || forecast.available();
        Boolean answer = Boolean.FALSE;
        try {
            answer = compute(chunkX, chunkZ);
        } catch (Throwable t) {
            answer = Boolean.FALSE;
        }
        if (!cacheable)
            return answer;
        Boolean winner = memo.putIfAbsent(key, answer);
        return winner != null ? winner : answer;
    }

    /** Whether the exact footprint path is in use (the probe reports it). */
    public boolean byFootprint() {
        return forecast != null && forecast.available();
    }

    private boolean compute(int chunkX, int chunkZ) {
        if (forecast != null && forecast.available()) {
            // The exact answer: is this chunk under (or one chunk from) a structure that WILL be built?
            // Only surface-step sets compete for the ground; a buried start reserves nothing, exactly
            // as the fallback's reachesTheSurface filter intends.
            // The margin is the blend's own reach for that start: a bearded structure 40 blocks above
            // its plain tapers over ~100 blocks, and a city planned inside that taper gets lifted around
            // (the owner's houses in pits and roads ending at a wall, 2026-09-24). A flat village keeps
            // the one-chunk margin.
            for (net.minecraft.world.level.levelgen.structure.StructureStart start
                    : forecast.startsCovering(chunkX, chunkZ, forecast.generator()::reserveMarginChunks)) {
                if (start.getStructure().step() == GenerationStep.Decoration.SURFACE_STRUCTURES)
                    return true;
            }
            return false;
        }
        for (Reserved reserved : sets)
            if (state.hasStructureChunkInRange(reserved.set(), chunkX, chunkZ, reserved.clearance()))
                return true;
        return false;
    }
}
