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
 * <p><b>This deliberately over-reserves.</b> {@code hasStructureChunkInRange} answers the placement
 * question only; whether the structure's <em>biome</em> predicate will pass is decided later, at
 * structure-start time. So some reserved chunks never receive a structure and simply stay natural. The
 * error is one-directional and cheap — an unexplained meadow, never a half-built city — and the
 * alternative (resolving biomes per candidate chunk during planning) would drag the biome source into
 * the planner for a cosmetic gain.
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
            List<Reserved> sets) {
        this.state = state;
        this.clearance = clearance;
        this.sets = sets;
    }

    /**
     * Builds the reservations for a world, or returns {@code null} when there is nothing to reserve —
     * no state, or every allowed set is a ring placement. A null result lets the caller skip the check
     * entirely rather than pay for a query that can only ever answer false.
     */
    public static StructureReservations of(ChunkGeneratorStructureState state, int clearance) {
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
            return usable.isEmpty() ? null : new StructureReservations(state, base, usable);
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
     * can vary with generation order.
     *
     * <p>{@code hasStructureChunkInRange} is a flat {@code (2r+1)^2} scan whose every candidate builds
     * a {@code WorldgenRandom} and runs {@code setLargeFeatureWithSalt} — an allocation plus seeded
     * maths per candidate. 121 per query at the default clearance, 625 at the 12 an acropolis declares,
     * and planning asks the same coordinates from THREE sites (PlatLot, setLot, paveLot).
     */
    private final java.util.concurrent.ConcurrentHashMap<Long, Boolean> memo =
            new java.util.concurrent.ConcurrentHashMap<>();

    public boolean isReserved(int chunkX, int chunkZ) {
        return memo.computeIfAbsent(net.minecraft.world.level.ChunkPos.asLong(chunkX, chunkZ), key -> {
            try {
                for (Reserved reserved : sets)
                    if (state.hasStructureChunkInRange(reserved.set(), chunkX, chunkZ, reserved.clearance()))
                        return Boolean.TRUE;
            } catch (Throwable t) {
                return Boolean.FALSE;
            }
            return Boolean.FALSE;
        });
    }
}
