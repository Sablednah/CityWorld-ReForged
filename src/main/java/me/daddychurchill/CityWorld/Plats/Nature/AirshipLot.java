package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.NatureLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * One half of an airship: a two-chunk rigid airship drifting high over the wild, the rare MODERN cousin of
 * the hot-air balloon. A cigar-shaped envelope in a two-colour livery with four tail fins, a glazed control
 * car slung underneath (bridge, seats, a stern observation deck) and an engine and propeller either side.
 * The drawing is {@code StructureInAirProvider.generateAirship}.
 *
 * <p><b>Why two lots rather than one.</b> Decoration writes one chunk at a time, so each half draws only its
 * own sixteen columns of a shared 32-block frame. That joins up only if both halves agree on everything, so
 * nothing the drawing uses comes from this chunk: the dice are the <em>anchor</em> half's (the west or north
 * one), and the altitude sits above the higher of the two terrains.
 *
 * <p><b>Both halves or nothing.</b> A later planning pass can still build over one half — a road, a wild
 * schematic, the platmap's second special. Each half checks at draw time that its partner is still standing
 * and draws nothing if not, because half an airship is worse than none.
 *
 * <p>The FLOATING style's upstream {@code FloatingBlimpLot} is unrelated: it is a platform with balloons
 * tied to it.
 */
public class AirshipLot extends ConstructLot {

    private static final String[] NAMES = { "Albatross", "Endeavour", "Zephyr", "Skylark", "Meridian", "Aurora",
            "Nimbus", "Kestrel", "Halcyon", "Wanderer", "Northern Star", "Perseverance", "Stormcrow",
            "Brass Heron", "Cirrus", "Gossamer" };

    private static final int[] STEP_X = { 1, -1, 0, 0 };
    private static final int[] STEP_Z = { 0, 0, 1, -1 };

    /** The ship's length runs along X (a west-east pair) rather than Z (north-south). */
    private final boolean alongX;
    /** The west or north half: it owns the dice and reports the landmark. */
    private final boolean anchor;

    public AirshipLot(PlatMap platmap, int chunkX, int chunkZ, boolean alongX, boolean anchor) {
        super(platmap, chunkX, chunkZ);
        this.alongX = alongX;
        this.anchor = anchor;
        // Deliberately NOT trulyIsolated: validateMap recycles a truly isolated lot with a truly isolated
        // diagonal neighbour, and losing one half that way would leave the other with nothing to join.
        trulyIsolated = false;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new AirshipLot(platmap, chunkX, chunkZ, alongX, anchor);
    }

    /**
     * Claims lot ({@code platX}, {@code platZ}) and one wild neighbour for an airship. Returns false, having
     * changed nothing, when no neighbour is plain nature filler — the caller then falls through to its
     * next choice.
     */
    public static boolean place(PlatMap platmap, int platX, int platZ, Odds odds) {
        int start = odds.getRandomInt(STEP_X.length);
        for (int i = 0; i < STEP_X.length; i++) {
            int d = (start + i) % STEP_X.length;
            int otherX = platX + STEP_X[d], otherZ = platZ + STEP_Z[d];
            if (!platmap.inBounds(otherX, otherZ) || !(platmap.getLot(otherX, otherZ) instanceof NatureLot))
                continue;
            boolean alongX = STEP_X[d] != 0;
            int lowX = Math.min(platX, otherX), lowZ = Math.min(platZ, otherZ);
            int highX = Math.max(platX, otherX), highZ = Math.max(platZ, otherZ);
            if (!platmap.setLot(lowX, lowZ,
                    new AirshipLot(platmap, platmap.originX + lowX, platmap.originZ + lowZ, alongX, true)))
                continue;
            if (!platmap.setLot(highX, highZ,
                    new AirshipLot(platmap, platmap.originX + highX, platmap.originZ + highZ, alongX, false))) {
                platmap.recycleLot(lowX, lowZ);
                return false;
            }
            return true;
        }
        return false;
    }

    /** The other half, if planning left it standing; null means this half draws nothing. */
    public AirshipLot partner(PlatMap platmap, int platX, int platZ) {
        int step = anchor ? 1 : -1;
        PlatLot other = alongX ? platmap.getLot(platX + step, platZ) : platmap.getLot(platX, platZ + step);
        return other instanceof AirshipLot ship && ship.alongX == alongX && ship.anchor != anchor ? ship : null;
    }

    public boolean isAnchor() {
        return anchor;
    }

    public boolean isAlongX() {
        return alongX;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return blockYs.getMaxHeight() + 25;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        generateSurface(generator, chunk, false);

        AirshipLot other = partner(platmap, platX, platZ);
        if (other == null)
            return;

        // Everything below must come out identical in both halves: the anchor chunk's dice, drawn in the
        // same order, and an altitude clear of both chunks' terrain.
        int anchorChunkX = anchor || !alongX ? chunkX : chunkX - 1;
        int anchorChunkZ = anchor || alongX ? chunkZ : chunkZ - 1;
        Odds shipOdds = platmap.getChunkOddsGenerator(anchorChunkX, anchorChunkZ);

        int atY = Math.max(getBottomY(generator), other.getBottomY(generator));
        int keelY = atY + shipOdds.getRandomInt(Math.max(2, chunk.height - 40 - atY));
        String name = NAMES[shipOdds.getRandomInt(NAMES.length)];
        if (anchor)
            generator.reportLocation("airship", "Airship " + name, chunk, alongX ? 2 : 1, alongX ? 1 : 2);
        generator.structureInAirProvider.generateAirship(generator, chunk, keelY, alongX, !anchor, shipOdds);
    }
}
