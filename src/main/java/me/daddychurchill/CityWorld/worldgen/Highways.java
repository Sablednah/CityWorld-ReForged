package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highways between the cities of a vanilla-terrain world: one road from each city to the nearest city east and the
 * nearest south (the next cell's, or up to {@code highways.reach} cells past empty ones: {@link #partner}),
 * a pure function of the seed and the {@link CitySites}, planned once per link and drawn chunk by chunk.
 *
 * <p><b>A link.</b> Each end leaves its city along one of the city's own road rows (chunk lines 2 and 7 of every
 * platmap, the rows {@code RoadLot.PlatMapRoadInset} puts streets on), at the city's own level, from the first chunk
 * past its built ground, and runs straight out for {@link #RUN_OUT} blocks. Between the two run-outs the route is
 * picked from sideways offsets every {@link #WAYPOINT} blocks along the straight line, by the cheapest path over
 * vanilla's heights (how far the ground is from the level the road wants there, water dearer, turns dearer) — so it
 * goes round a hill or along a valley rather than over it. The chosen points are joined by a Catmull-Rom curve
 * sampled every {@link #STEP} blocks, and the deck follows the ground smoothed over {@link #SMOOTH} samples and held
 * to a {@link #GRADE} grade, pinned to each city's level at its gate.
 *
 * <p><b>Each sample is a kind:</b> {@link Kind#GROUND} (on the ground, cut through or banked up within
 * {@link #fillMax}), or {@link Kind#BRIDGE} over water or over a drop deeper than that. Short runs are merged, so a
 * bridge is a bridge from bank to bank.
 *
 * <p>The settings are {@code cities.highways} ({@link CityWorldSettingsData.Highways}): the deck's width, the grade,
 * how deep a bank may be and how high a bridge stands over water. The route search's own figures (waypoint spacing,
 * how far sideways, what a turn or water costs) are fixed: they shape how a road is found, not what it is.
 */
public final class Highways {

    /** Blocks between centreline samples. */
    public static final int STEP = 2;
    /** Blocks between route waypoints, and how far sideways each may move, in steps of {@link #OFFSET}. */
    static final int WAYPOINT = 192, OFFSET = 48, OFFSETS = 6;
    /** Blocks a link runs straight out of each city before it may turn. */
    public static final int RUN_OUT = 40;
    /** The run a climb is measured over for a STEEP sign, in samples (96 blocks). */
    public static final int STEEP_RUN_SAMPLES = 96 / STEP;
    /** Samples either side the ground is averaged over before the grade is applied. */
    static final int SMOOTH = 12;
    /** The shortest tunnel, in blocks. */
    static final int MIN_TUNNEL = 16;
    /** How far the cut and bank slopes reach past the deck's kerb. */
    public static final int SIDE = 14;
    /** Grid the route search reads heights on. */
    static final int GRID = 64;
    /** Every this many samples the ground under the centreline is read exactly; between, it is interpolated. */
    static final int EXACT = 4;

    public enum Kind { GROUND, BRIDGE, TUNNEL }

    /**
     * One highway: the centreline as samples ({@code x}, {@code z}, deck {@code y}, {@link Kind}), and the box
     * (in blocks, the slopes included) it can change anything in.
     */
    public record Link(CitySites.Site a, CitySites.Site b, double[] x, double[] z, int[] y, double[] h, Kind[] kind,
            int minX, int minZ, int maxX, int maxZ, long planMs) {
        public int length() {
            return (x.length - 1) * STEP;
        }

        public boolean touches(int x0, int z0, int x1, int z1) {
            return x1 >= minX && x0 <= maxX && z1 >= minZ && z0 <= maxZ;
        }
    }

    private final CitySites sites;
    private final int seaLevel;
    private final ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<java.util.Optional<Link>>> links =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> grid = new ConcurrentHashMap<>();

    /** A cheap estimate of the ground, for the route search only (vanilla's preliminary surface level). */
    private final CitySites.Heights rough;

    /**
     * @param rough a cheap estimate of vanilla's ground (its noise router's preliminary surface level), which the
     *              route search reads on its grid; or {@code sites.heights()} where a line has none (exact, slow)
     */
    /** The settings, unpacked: half the deck's width (it is 2 * this + 1 wide), the grade, the bank, the clearance. */
    private final int halfWidth, fillMax, clearance, tunnel;
    private final double grade;

    public Highways(CitySites sites, CitySites.Heights rough, CityWorldSettingsData.Highways settings) {
        this.sites = sites;
        this.seaLevel = sites.seaLevel();
        this.rough = rough != null ? rough : sites.heights();
        this.halfWidth = Math.max(1, (settings.width() - 1) / 2);
        this.grade = settings.grade();
        this.fillMax = settings.fillMax();
        this.clearance = settings.clearance();
        this.tunnel = settings.tunnel();
        this.reach = settings.reach();
    }

    /** How many cells a highway may reach past empty ones to the next city ({@code highways.reach}). */
    private final int reach;

    /**
     * What a city's plan has on a chunk, for choosing a gate: one of its streets, something built, or open ground.
     * Asked only of chunks inside a city's patch; {@link #UNKNOWN} when the plan cannot be had (then a gate falls back
     * to the road-line rule alone).
     */
    @FunctionalInterface
    public interface CityPlan {
        int ROAD = 0, BUILT = 1, OPEN = 2, UNKNOWN = -1;

        int at(int chunkX, int chunkZ);
    }

    private volatile CityPlan cityPlan;

    /** The city plan the gates are chosen from (the generator's own PlatMaps); null keeps the road-line rule. */
    public void usePlan(CityPlan plan) {
        this.cityPlan = plan;
    }

    /** Half the deck's width: the deck is {@code 2 * halfWidth() + 1} blocks wide, plus a kerb each side. */
    public int halfWidth() {
        return halfWidth;
    }

    /** The deepest a road is banked up; deeper (or over water) it is a bridge. */
    public int fillMax() {
        return fillMax;
    }

    /**
     * How far the estimate sits below the real ground, measured once per world: vanilla's preliminary surface level
     * runs about twelve blocks under it (seed 1688298913692546204: 8 to 23 by link), and a terrain pack may move
     * that, so it is the median gap over sixteen exact heights near the origin rather than a constant.
     */
    private volatile Integer bias;

    public int biasForSurvey() {
        return bias();
    }

    private int bias() {
        Integer known = bias;
        if (known != null)
            return known;
        if (rough == sites.heights())
            return bias = 0;
        java.util.Random pick = new java.util.Random(sites.seed() ^ 0x5eedL);
        int[] gaps = new int[16];
        for (int i = 0; i < gaps.length; i++) {
            int x = pick.nextInt(4096) - 2048, z = pick.nextInt(4096) - 2048;
            gaps[i] = sites.heights().top(x, z) - rough.top(x, z);
        }
        java.util.Arrays.sort(gaps);
        return bias = gaps[gaps.length / 2];
    }

    /** Mean and worst gap between the estimate and the exact ground over this many points of a link: for the survey. */
    public int[] roughError(Link link, int points) {
        int n = link.x().length, worst = 0;
        long sum = 0;
        int count = 0;
        for (int k = 0; k < points; k++) {
            int i = (int) ((long) k * (n - 1) / Math.max(1, points - 1));
            int x = (int) Math.floor(link.x()[i]), z = (int) Math.floor(link.z()[i]);
            int gap = rough.top(x, z) + bias() - sites.heights().top(x, z);
            sum += gap;
            worst = Math.max(worst, Math.abs(gap));
            count++;
        }
        return new int[] { (int) (sum / Math.max(1, count)), worst };
    }

    /**
     * The city in the cell a site stands in (cells are {@code sites.cell()} blocks), or null.
     */
    private CitySites.Site site(int cx, int cz) {
        int cell = sites.cell();
        return sites.siteAt(cx * cell + cell / 2, cz * cell + cell / 2);
    }

    /** Cell + direction (0 east, 1 south) to the cell its highway runs to; an empty array for none. */
    private final ConcurrentHashMap<Long, int[]> partners = new ConcurrentHashMap<>();

    /**
     * The cell the city in cell {@code (cx, cz)} runs its highway to eastward ({@code dir} 0) or southward (1), or
     * null. The next cell's city when there is one (a world full of cities keeps its grid of roads); otherwise the
     * nearest city in that direction up to {@link #reach} cells away whose straight line passes no other city — so a
     * sparse world (big cities at a small spacing: the owner's, one cell in nine) still has roads between its towns.
     * <p>Eastward is every cell with {@code dx > |dz|}, southward every cell with {@code dz >= |dx|}: between them half
     * the plane, and the other half is the other city's, so every pair of cells is one city's to link and a road is
     * never planned twice.
     */
    int[] partner(int cx, int cz, int dir) {
        long key = ((long) cx << 33) ^ ((long) cz << 1) ^ dir;
        int[] known = partners.get(key);
        if (known == null) {
            int[] found = findPartner(cx, cz, dir);
            known = found == null ? new int[0] : found;
            int[] raced = partners.putIfAbsent(key, known);
            if (raced != null)
                known = raced;
        }
        return known.length == 0 ? null : known;
    }

    private int[] findPartner(int cx, int cz, int dir) {
        CitySites.Site here = site(cx, cz);
        if (here == null)
            return null;
        int nx = cx + (dir == 0 ? 1 : 0), nz = cz + (dir == 0 ? 0 : 1);
        if (site(nx, nz) != null)
            return new int[] { nx, nz };
        int[] best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int k = 1; k <= reach; k++)
            for (int side = -k; side <= k; side++) {
                if (dir == 0 && Math.abs(side) >= k)
                    continue; // eastward: dx > |dz|
                int ox = dir == 0 ? k : side, oz = dir == 0 ? side : k;
                if (ox == nx - cx && oz == nz - cz)
                    continue;
                CitySites.Site there = site(cx + ox, cz + oz);
                if (there == null)
                    continue;
                double distance = Math.hypot(there.centreX() - here.centreX(), there.centreZ() - here.centreZ());
                if (distance < bestDistance && clearBetween(here, there, cx, cz, cx + ox, cz + oz)) {
                    best = new int[] { cx + ox, cz + oz };
                    bestDistance = distance;
                }
            }
        return best;
    }

    /** Whether the straight line between two cities passes clear of every other city near it. */
    private boolean clearBetween(CitySites.Site a, CitySites.Site b, int acx, int acz, int bcx, int bcz) {
        double lx = b.centreX() - a.centreX(), lz = b.centreZ() - a.centreZ(), length2 = lx * lx + lz * lz;
        for (int x = Math.min(acx, bcx) - 1; x <= Math.max(acx, bcx) + 1; x++)
            for (int z = Math.min(acz, bcz) - 1; z <= Math.max(acz, bcz) + 1; z++) {
                CitySites.Site other = site(x, z);
                if (other == null || other.equals(a) || other.equals(b))
                    continue;
                double t = Math.max(0, Math.min(1, ((other.centreX() - a.centreX()) * lx + (other.centreZ() - a.centreZ()) * lz)
                        / Math.max(1, length2)));
                double d = Math.hypot(a.centreX() + t * lx - other.centreX(), a.centreZ() + t * lz - other.centreZ());
                if (d < other.radius() * 1.2 + halfWidth + SIDE + 32)
                    return false;
            }
        return true;
    }

    /** The cities a city's highways run to, out of its cell and into it. */
    private List<CitySites.Site> linkedTo(int cx, int cz) {
        List<CitySites.Site> found = new ArrayList<>();
        for (int dir = 0; dir < 2; dir++) {
            int[] p = partner(cx, cz, dir);
            if (p != null)
                found.add(site(p[0], p[1]));
        }
        // a city to the west whose eastward road comes here (dx > |dz| from it), or to the north whose southward does
        for (int k = 1; k <= reach; k++)
            for (int side = -k; side <= k; side++) {
                if (Math.abs(side) < k) {
                    int[] p = partner(cx - k, cz - side, 0);
                    if (p != null && p[0] == cx && p[1] == cz)
                        found.add(site(cx - k, cz - side));
                }
                int[] q = partner(cx - side, cz - k, 1);
                if (q != null && q[0] == cx && q[1] == cz)
                    found.add(site(cx - side, cz - k));
            }
        return found;
    }

    /**
     * Every link whose box reaches into this square of blocks. A link runs from a cell to its {@link #partner}, at
     * most {@link #reach} cells east or south (or either side of south), and its route stays within {@link #OFFSETS}
     * offsets of the line between them, so only the links of the cells that near the square need asking — and only
     * those whose rough box (from the two sites alone, before any height is read) reaches it are planned.
     */
    public List<Link> linksTouching(int x0, int z0, int x1, int z1) {
        List<Link> found = new ArrayList<>();
        int cell = sites.cell();
        // a link's far end may be `reach` cells off, and its pad (a site is only `margin` from its cell's edge, less
        // than the pad) one more
        int cx0 = Math.floorDiv(x0, cell) - reach - 1, cx1 = Math.floorDiv(x1, cell) + reach + 1;
        int cz0 = Math.floorDiv(z0, cell) - reach - 1, cz1 = Math.floorDiv(z1, cell) + reach;
        // the cells asked, judged side by side first (a partner further out is judged when its cone is searched)
        if (reach > 1)
            sites.judgeAll(cx0, cz0, cx1, cz1);
        List<Object[]> wanted = new ArrayList<>();
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                CitySites.Site here = site(cx, cz);
                if (here == null)
                    continue;
                for (int dir = 0; dir < 2; dir++) {
                    int[] p = partner(cx, cz, dir);
                    if (p == null)
                        continue;
                    CitySites.Site there = site(p[0], p[1]);
                    // the route swings up to OFFSETS offsets off the line between the GATES, and a gate stands up to
                    // the radius across from its centre, on a road line (half a chunk more), and a run may be lengthened
                    // RUN_TRIES chunks to clear a built lot; a box from
                    // the centres alone left a chunk asking nothing and a road ending at its edge
                    int pad = OFFSETS * OFFSET + halfWidth + SIDE + RUN_OUT + 64
                            + RUN_TRIES * 16 + 2 * Math.max(here.radius(), there.radius()); // a road round a city: AROUND radii out, at a corner
                    int bx0 = Math.min(here.centreX(), there.centreX()) - pad, bx1 = Math.max(here.centreX(), there.centreX()) + pad;
                    int bz0 = Math.min(here.centreZ(), there.centreZ()) - pad, bz1 = Math.max(here.centreZ(), there.centreZ()) + pad;
                    if (x1 < bx0 || x0 > bx1 || z1 < bz0 || z0 > bz1)
                        continue;
                    wanted.add(new Object[] { cx, cz, dir, here, there });
                }
            }
        // First plan every link nobody has started, this thread taking each in turn; then wait for the ones others are
        // planning. Taken in order and waited on one by one, a chunk wanting five links waited out the first while
        // every other worker did too, then queued behind the second: the owner's spawn area at the smallest spacing
        // planned its fourteen links one at a time.
        for (Object[] w : wanted)
            if (!links.containsKey(linkKey((int) w[0], (int) w[1], (int) w[2])))
                link((int) w[0], (int) w[1], (int) w[2], (CitySites.Site) w[3], (CitySites.Site) w[4]);
        for (Object[] w : wanted) {
            Link link = link((int) w[0], (int) w[1], (int) w[2], (CitySites.Site) w[3], (CitySites.Site) w[4]);
            if (link != null && link.touches(x0, z0, x1, z1))
                found.add(link);
        }
        return found;
    }

    private static long linkKey(int cx, int cz, int dir) {
        return ((long) cx << 33) ^ ((long) cz << 1) ^ dir;
    }

    /**
     * Whether a highway's deck, kerbs and the slopes beside them come within {@code margin} blocks of this square of
     * blocks: a village street laid across one turns its pavement to a dirt path, and a house stands on it.
     */
    public boolean crosses(int x0, int z0, int x1, int z1, int margin) {
        int reach = halfWidth + SIDE + 1 + margin;
        for (Link link : linksTouching(x0 - reach, z0 - reach, x1 + reach, z1 + reach)) {
            double[] xs = link.x(), zs = link.z();
            for (int i = 0; i < xs.length; i++)
                if (xs[i] >= x0 - reach && xs[i] <= x1 + 1 + reach && zs[i] >= z0 - reach && zs[i] <= z1 + 1 + reach)
                    return true;
        }
        return false;
    }

    /** Every link out of the cells in this square of cells, planned: for the survey. */
    public List<Link> linksOfCells(int cx0, int cz0, int cx1, int cz1) {
        List<Link> found = new ArrayList<>();
        int cell = sites.cell();
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                CitySites.Site here = site(cx, cz);
                if (here == null)
                    continue;
                for (int dir = 0; dir < 2; dir++) {
                    int[] p = partner(cx, cz, dir);
                    if (p == null)
                        continue;
                    CitySites.Site there = site(p[0], p[1]);
                    Link link = link(cx, cz, dir, here, there);
                    if (link != null)
                        found.add(link);
                }
            }
        return found;
    }

    private Link link(int cx, int cz, int dir, CitySites.Site here, CitySites.Site there) {
        long key = linkKey(cx, cz, dir);
        // one thread plans a link and the rest wait for it (every chunk along a new road asks at once); planned outside
        // any map lock, waited through Waiting.join (never a bare join on a worker)
        var mine = new java.util.concurrent.CompletableFuture<java.util.Optional<Link>>();
        var known = links.putIfAbsent(key, mine);
        if (known != null)
            return Waiting.join(known).orElse(null);
        try {
            Link planned = plan(here, there);
            mine.complete(java.util.Optional.ofNullable(planned));
            return planned;
        } catch (RuntimeException e) {
            // not remembered: a one-off failure must not leave this session's chunks roadless and a restart's paved
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: highway {},{} dir {} FAILED", cx, cz, dir, e);
            links.remove(key, mine);
            mine.complete(java.util.Optional.empty());
            return null;
        }
    }

    // --- planning -------------------------------------------------------------------------------------------

    /**
     * Where a link leaves a city: the end of one of its streets, the unit direction it runs out in, and how far it runs
     * straight before the route may turn (through the city's open edge, then {@link #RUN_OUT} more).
     */
    private record Gate(double x, double z, double dx, double dz, double run) {
    }

    /** How far across from a city's centre a gate may be, as a share of its radius. */
    private static final double GATE_REACH = 0.8;

    /**
     * The gate on {@code site}'s side facing {@code toward}. From the city's own plan: the street row (or column),
     * nearest where the straight line between the centres crosses the city's edge, whose last stretch of road reaches
     * open ground with nothing built beyond it; the gate is the end of that road, and the highway runs straight out
     * across the city's open edge. A road line is only where a city MAY run a street (the owner's first look: a
     * highway ran into an office block on one, and others stopped short at the city's nature ring). With no plan,
     * or no such street, the road-line rule alone: along the line nearest the crossing, from the city's edge.
     */
    private final java.util.concurrent.ConcurrentHashMap<List<Integer>, Gate> gates = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * The sides of a chunk a highway leaves from: bits {@link #EXIT_NORTH}, {@link #EXIT_EAST}, {@link #EXIT_SOUTH},
     * {@link #EXIT_WEST}; 0 for most. A street chunk with one carries its road on to that edge and signs it, where it
     * would have ended in a kerb and a DEAD END. From the gates alone (the city plan, no heights), so drawing a city's
     * street never waits on a route being planned.
     */
    public int exitsAt(int chunkX, int chunkZ) {
        CitySites.Site site = sites.influencing(chunkX, chunkZ);
        if (site == null || !sites.isCityChunk(chunkX, chunkZ))
            return 0;
        int cell = sites.cell();
        int cx = Math.floorDiv(site.centreX(), cell), cz = Math.floorDiv(site.centreZ(), cell);
        int bits = 0;
        for (CitySites.Site there : linkedTo(cx, cz)) {
            Gate g = gateOf(site, there);
            int gx = Math.floorDiv((int) Math.floor(g.x - g.dx * 8), 16), gz = Math.floorDiv((int) Math.floor(g.z - g.dz * 8), 16);
            if (gx == chunkX && gz == chunkZ)
                bits |= g.dx > 0 ? EXIT_EAST : g.dx < 0 ? EXIT_WEST : g.dz > 0 ? EXIT_SOUTH : EXIT_NORTH;
        }
        return bits;
    }

    public static final int EXIT_NORTH = 1, EXIT_EAST = 2, EXIT_SOUTH = 4, EXIT_WEST = 8;

    /** A site's gate toward another, worked out once (outside any map lock: it reads the city's plan). */
    private Gate gateOf(CitySites.Site site, CitySites.Site toward) {
        List<Integer> key = List.of(site.centreX(), site.centreZ(), toward.centreX(), toward.centreZ());
        Gate known = gates.get(key);
        if (known != null)
            return known;
        Gate made = gate(site, toward);
        Gate raced = gates.putIfAbsent(key, made);
        return raced != null ? raced : made;
    }

    private Gate gate(CitySites.Site site, CitySites.Site toward) {
        double dx = toward.centreX() - site.centreX(), dz = toward.centreZ() - site.centreZ();
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        int sign = alongX ? (dx >= 0 ? 1 : -1) : (dz >= 0 ? 1 : -1);
        double length = Math.hypot(dx, dz);
        // where the line leaves the disc, across the run of the road rows
        double across = alongX ? site.centreZ() + dz / length * site.radius() : site.centreX() + dx / length * site.radius();
        double centreAcross = alongX ? site.centreZ() : site.centreX();
        double limit = site.radius() * GATE_REACH;
        across = Math.max(centreAcross - limit, Math.min(centreAcross + limit, across));
        int start = Math.floorDiv(alongX ? site.centreX() : site.centreZ(), 16);

        CityPlan plan = cityPlan;
        if (plan != null) {
            // the side facing the other city, then the other side facing it (a city whose every street that way runs
            // into a farm or a block before its edge: a fifth of them, measured), then both with every row to the
            // city's rim, then a street along the rim met as a T, then the sides facing away, the road going round
            // (the owner's dead ends: a road that met the city's edge with no street to meet)
            boolean sideX = !alongX;
            int sideSign = (sideX ? dx : dz) >= 0 ? 1 : -1;
            double sideAcross = sideX ? site.centreZ() + dz / length * site.radius() : site.centreX() + dx / length * site.radius();
            double sideCentre = sideX ? site.centreZ() : site.centreX();
            double wholeAcross = across;
            // {side?, sign, share of the radius, rows only?}: last, a street running along the city's edge, met
            // square on as a T (any chunk across, its last road chunk a cross street's)
            double[][] tries = { { 0, sign, GATE_REACH, 1 }, { 1, sideSign, GATE_REACH, 1 }, { 0, sign, 1, 1 },
                    { 1, sideSign, 1, 1 }, { 0, sign, 1, 0 }, { 1, sideSign, 1, 0 },
                    { 1, -sideSign, 1, 1 }, { 0, -sign, 1, 1 }, { 1, -sideSign, 1, 0 }, { 0, -sign, 1, 0 } };
            for (double[] t : tries) {
                boolean side = t[0] == 1;
                double reach = site.radius() * t[2];
                double centre = side ? sideCentre : centreAcross;
                double want = Math.max(centre - reach, Math.min(centre + reach, side ? sideAcross : wholeAcross));
                Gate found = streetGate(plan, site, side ? sideX : alongX, (int) t[1], want, t[2], t[3] == 1);
                if (found != null)
                    return found;
            }
        }

        int row = nearestRoadLine(Math.floorDiv((int) Math.round(across), 16));
        // walk out from the centre along the row to the first chunk that is not city ground
        int c = start;
        for (int i = 0; i < 64; i++) {
            boolean city = alongX ? sites.isCityChunk(c, row) : sites.isCityChunk(row, c);
            if (!city && i > 0)
                break;
            c += sign;
        }
        // the boundary between the last city chunk and the first that is not
        double edge = sign > 0 ? c * 16.0 : (c + 1) * 16.0;
        double rowCentre = row * 16.0 + 8.0;
        return alongX ? new Gate(edge, rowCentre, sign, 0, RUN_OUT) : new Gate(rowCentre, edge, 0, sign, RUN_OUT);
    }

    /**
     * The end of a street leaving {@code site} along X (or Z) in direction {@code sign}: of the road rows within
     * {@link #GATE_REACH} of the centre, nearest {@code across} first, the first whose last road chunk has only open
     * ground between it and the city's edge. Null when none has, or the plan cannot be had.
     */
    private Gate streetGate(CityPlan plan, CitySites.Site site, boolean alongX, int sign, double across, double share,
            boolean rowsOnly) {
        double centreAcross = alongX ? site.centreZ() : site.centreX();
        double limit = site.radius() * share;
        int start = Math.floorDiv(alongX ? site.centreX() : site.centreZ(), 16);
        List<Integer> rows = new ArrayList<>();
        for (int c = Math.floorDiv((int) Math.floor(centreAcross - limit), 16); c <= Math.floorDiv(
                (int) Math.ceil(centreAcross + limit), 16); c++)
            if (!rowsOnly || Math.floorMod(c, 5) == 2)
                rows.add(c);
        rows.sort(java.util.Comparator.comparingDouble(r -> Math.abs(r * 16.0 + 8.0 - across)));
        for (int row : rows) {
            int lastRoad = Integer.MIN_VALUE, end = Integer.MIN_VALUE;
            boolean builtBeyond = false;
            int c = start;
            // on to the city's OUTERMOST chunk on the line: its rim has gaps (water, the wobble), and farms past a gap
            // stood on a run that stopped at the first (the owner's seed: a road along a row of farms)
            for (int i = 0; i < 64; i++, c += sign) {
                int cx = alongX ? c : row, cz = alongX ? row : c;
                if (!sites.isCityChunk(cx, cz)) {
                    if (end == Integer.MIN_VALUE)
                        end = c;
                    continue;
                }
                end = Integer.MIN_VALUE;
                int kind = plan.at(cx, cz);
                if (kind == CityPlan.UNKNOWN)
                    return null;
                if (kind == CityPlan.ROAD) {
                    lastRoad = c;
                    builtBeyond = false;
                } else if (kind == CityPlan.BUILT)
                    builtBeyond = true;
            }
            if (end == Integer.MIN_VALUE || lastRoad == Integer.MIN_VALUE || builtBeyond)
                continue;
            double edge = sign > 0 ? (lastRoad + 1) * 16.0 : lastRoad * 16.0;
            double cityEdge = sign > 0 ? end * 16.0 : (end + 1) * 16.0;
            double rowCentre = row * 16.0 + 8.0;
            double run = Math.abs(cityEdge - edge) + RUN_OUT;
            return alongX ? new Gate(edge, rowCentre, sign, 0, run) : new Gate(rowCentre, edge, 0, sign, run);
        }
        return null;
    }

    /** The road line (chunk index 2 or 7 within its platmap) nearest this chunk index. */
    static int nearestRoadLine(int chunk) {
        int base = Math.floorDiv(chunk - 2, 5) * 5 + 2;
        return chunk - base <= 2 ? base : base + 5;
    }

    private Link plan(CitySites.Site a, CitySites.Site b) {
        long started = System.nanoTime();
        Gate ga = gateOf(a, b), gb = gateOf(b, a);
        // a turn after the run can cut across a built lot of a row of the same city that reaches further out than the
        // street the road left by (the owner's look: a road under an office): run that end on a chunk further, and
        // again, until the line is clear of every built lot
        // A run that still crosses one after half the tries goes round its city instead (a gate facing sideways whose
        // turn hugs the rim: a road along a row of the city's own farms, on the owner's seed)
        boolean[] round = new boolean[2];
        int[] longer = new int[2];
        List<double[]> dense = route(a, b, ga, gb, round);
        for (int tries = 0; tries < RUN_TRIES; tries++) {
            int[] crossed = builtCrossing(dense, ga, gb);
            if (crossed == null)
                break;
            CitySites.Site owner = sites.influencing(crossed[0], crossed[1]);
            boolean nearA = owner != null ? owner.equals(a)
                    : Math.hypot(crossed[0] * 16 - ga.x, crossed[1] * 16 - ga.z) < Math.hypot(crossed[0] * 16 - gb.x, crossed[1] * 16 - gb.z);
            int end = nearA ? 0 : 1;
            if (longer[end] >= RUN_TRIES / 2 && !round[end])
                round[end] = true;
            else if (nearA) {
                ga = new Gate(ga.x, ga.z, ga.dx, ga.dz, ga.run + 16);
                longer[0]++;
            } else {
                gb = new Gate(gb.x, gb.z, gb.dx, gb.dz, gb.run + 16);
                longer[1]++;
            }
            dense = route(a, b, ga, gb, round);
        }
        double[][] line = resample(dense, STEP);
        return finish(a, b, line, started);
    }

    /** How many times a run is lengthened to clear a built lot before the road is drawn as it is. */
    private static final int RUN_TRIES = 8;

    /**
     * The first chunk past either run whose city plan has a building on it, as {chunkX, chunkZ}; null when the line
     * is clear (or no plan is to be had).
     */
    private int[] builtCrossing(List<double[]> dense, Gate ga, Gate gb) {
        CityPlan plan = cityPlan;
        if (plan == null)
            return null;
        long last = Long.MIN_VALUE;
        for (int i = 0; i < dense.size(); i += 2) {
            double[] q = dense.get(i);
            // the runs themselves are along the street and over open ground: checked when the gate was chosen
            double fromA = (q[0] - ga.x) * ga.dx + (q[1] - ga.z) * ga.dz, fromB = (q[0] - gb.x) * gb.dx + (q[1] - gb.z) * gb.dz;
            if (fromA >= 0 && fromA <= ga.run && Math.abs((q[0] - ga.x) * ga.dz - (q[1] - ga.z) * ga.dx) < 1
                    || fromB >= 0 && fromB <= gb.run && Math.abs((q[0] - gb.x) * gb.dz - (q[1] - gb.z) * gb.dx) < 1)
                continue;
            // the deck and its kerbs reach halfWidth + 1 either side of the line
            for (int side = -1; side <= 1; side++) {
                double[] t = i + 1 < dense.size() ? unit(dense.get(i + 1), q) : unit(q, dense.get(i - 1));
                int cx = Math.floorDiv((int) Math.floor(q[0] - t[1] * side * (halfWidth + 1)), 16);
                int cz = Math.floorDiv((int) Math.floor(q[1] + t[0] * side * (halfWidth + 1)), 16);
                long key = ((long) cx << 32) ^ (cz & 0xffffffffL);
                if (key == last || !sites.isCityChunk(cx, cz))
                    continue;
                last = key;
                if (plan.at(cx, cz) == CityPlan.BUILT)
                    return new int[] { cx, cz };
            }
        }
        return null;
    }

    /** The line between two gates: Viterbi waypoints, straight runs, one-way cubics (dense, about a block apart). */
    private List<double[]> route(CitySites.Site a, CitySites.Site b, Gate ga, Gate gb, boolean[] round) {
        double[] endA = { ga.x + ga.dx * ga.run, ga.z + ga.dz * ga.run }, endB = { gb.x + gb.dx * gb.run, gb.z + gb.dz * gb.run };
        List<double[]> roundA = around(a, ga, endA, b, round[0]), roundB = around(b, gb, endB, a, round[1]);
        double[] fromA = roundA.isEmpty() ? endA : roundA.get(roundA.size() - 1);
        double[] fromB = roundB.isEmpty() ? endB : roundB.get(roundB.size() - 1);
        double ax = fromA[0], az = fromA[1];
        double bx = fromB[0], bz = fromB[1];
        double lx = bx - ax, lz = bz - az, length = Math.hypot(lx, lz);
        double ux = lx / length, uz = lz / length, nx = -uz, nz = ux;
        int segments = Math.max(1, (int) Math.round(length / WAYPOINT));
        int states = 2 * OFFSETS + 1;

        // the cheapest offsets: Viterbi over (previous offset, this offset), so a turn can be priced
        double[][][] cost = new double[segments + 1][states][states];
        int[][][] from = new int[segments + 1][states][states];
        for (double[][] plane : cost)
            for (double[] row : plane)
                java.util.Arrays.fill(row, Double.MAX_VALUE);
        // waypoint 0 and the last are the run-out ends, offset 0
        for (int k = 0; k < states; k++) {
            int ok = k - OFFSETS;
            if (segments > 1 || ok == 0)
                cost[1][OFFSETS][k] = segmentCost(a, b, length, ax, az, ux, uz, nx, nz, 0, 0, 1, ok, segments);
        }
        for (int i = 2; i <= segments; i++)
            for (int p = 0; p < states; p++)
                for (int k = 0; k < states; k++) {
                    if (i == segments && k != OFFSETS)
                        continue;
                    double here = segmentCost(a, b, length, ax, az, ux, uz, nx, nz, i - 1, p - OFFSETS, i, k - OFFSETS, segments);
                    for (int pp = 0; pp < states; pp++) {
                        double before = cost[i - 1][pp][p];
                        if (before == Double.MAX_VALUE)
                            continue;
                        double turn = Math.abs((k - p) - (p - pp)) * TURN_COST;
                        double total = before + here + turn;
                        if (total < cost[i][p][k]) {
                            cost[i][p][k] = total;
                            from[i][p][k] = pp;
                        }
                    }
                }
        int bestP = OFFSETS;
        double best = Double.MAX_VALUE;
        for (int p = 0; p < states; p++)
            if (cost[segments][p][OFFSETS] < best) {
                best = cost[segments][p][OFFSETS];
                bestP = p;
            }
        int[] offset = new int[segments + 1];
        offset[segments] = 0;
        int k = OFFSETS, p = bestP;
        for (int i = segments; i >= 1; i--) {
            offset[i] = k - OFFSETS;
            int pp = from[i][p][k];
            k = p;
            p = pp;
        }
        offset[0] = 0;

        // the line: straight along the street from the gate to the run-out end, then through the waypoints, then
        // straight in along the other street. Each span between them is a cubic leaving one point along the line's
        // heading there and arriving at the next along its heading: at a run-out end the street's own, so the road
        // leaves its city straight and turns one way only (the owner's look: a curve through all the points wiggled
        // out of a gate, overshot, and swung back)
        List<double[]> stops = new ArrayList<>();
        stops.add(endA);
        stops.addAll(roundA);
        for (int i = 1; i < segments; i++) {
            double t = i / (double) segments;
            stops.add(new double[] { ax + lx * t + nx * offset[i] * OFFSET, az + lz * t + nz * offset[i] * OFFSET });
        }
        for (int i = roundB.size() - 1; i >= 0; i--)
            stops.add(roundB.get(i));
        stops.add(endB);
        List<double[]> dense = new ArrayList<>();
        straight(dense, new double[] { ga.x, ga.z }, stops.get(0));
        for (int i = 0; i < stops.size() - 1; i++) {
            double[] p1 = stops.get(i), p2 = stops.get(i + 1);
            double[] t1 = i == 0 ? new double[] { ga.dx, ga.dz } : unit(p2, stops.get(i - 1));
            double[] t2 = i == stops.size() - 2 ? new double[] { -gb.dx, -gb.dz } : unit(stops.get(i + 2), p1);
            cubic(dense, p1, t1, p2, t2);
        }
        straight(dense, stops.get(stops.size() - 1), new double[] { gb.x, gb.z });
        dense.add(new double[] { gb.x, gb.z });
        return dense;
    }

    /**
     * The way round a city for a road that leaves by a street facing away from where it goes (a city whose every
     * street toward the other runs into a farm or a block: the owner's dead ends): out to a corner beside the city,
     * then back abeam its centre on the side toward the other, both well clear of its rim. Empty for a gate that does
     * not face away, unless {@code always}.
     */
    private List<double[]> around(CitySites.Site site, Gate g, double[] end, CitySites.Site other, boolean always) {
        double ox = other.centreX() - site.centreX(), oz = other.centreZ() - site.centreZ();
        if (g.dx * ox + g.dz * oz >= 0 && !always)
            return List.of();
        double px = -g.dz, pz = g.dx; // across the gate, toward the other city
        if (px * ox + pz * oz < 0) {
            px = -px;
            pz = -pz;
        }
        double out = (end[0] - site.centreX()) * g.dx + (end[1] - site.centreZ()) * g.dz;
        double clear = Math.max(out, site.radius() * AROUND + RUN_OUT);
        double[] corner = { site.centreX() + g.dx * out + px * clear, site.centreZ() + g.dz * out + pz * clear };
        // a gate facing away comes back abeam the centre; one facing across goes on from the corner, already clear
        if (g.dx * ox + g.dz * oz >= 0)
            return List.of(corner);
        double[] abeam = { site.centreX() + px * clear, site.centreZ() + pz * clear };
        return List.of(corner, abeam);
    }

    /** How far out, in radii, a road going round a city passes: past the rim's wobble and its blend. */
    private static final double AROUND = 1.4;

    /** Heights, kinds and the bounds of a planned line: the link. */
    private Link finish(CitySites.Site a, CitySites.Site b, double[][] line, long started) {
        int n = line.length;
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = line[i][0];
            zs[i] = line[i][1];
        }

        // the ground under the centreline: read exactly every EXACT samples (a vanilla height costs milliseconds) and
        // interpolated between; a river narrower than that is caught by its biome, which is cheap, on every sample
        double[] ground = new double[n];
        boolean[] wet = new boolean[n];
        int last = -1;
        for (int i = 0; i < n; i++) {
            if (i % EXACT != 0 && i != n - 1)
                continue;
            int top = shaped((int) Math.floor(xs[i]), (int) Math.floor(zs[i]), sites.heights().top((int) Math.floor(xs[i]),
                    (int) Math.floor(zs[i])));
            wet[i] = top < seaLevel - 1;
            ground[i] = wet[i] ? seaLevel + clearance : top;
            if (last >= 0)
                for (int j = last + 1; j < i; j++) {
                    double f = (j - last) / (double) (i - last);
                    ground[j] = ground[last] + (ground[i] - ground[last]) * f;
                    wet[j] = wet[last] && wet[i];
                }
            last = i;
        }
        for (int i = 0; i < n; i++)
            if (!wet[i] && sites.isRiverColumn((int) Math.floor(xs[i]), (int) Math.floor(zs[i]))) {
                wet[i] = true;
                ground[i] = seaLevel + clearance;
            }
        // smoothed, then held to the grade and pinned to each city's level at its gate
        double[] deck = new double[n];
        for (int i = 0; i < n; i++) {
            double sum = 0;
            int count = 0;
            for (int j = Math.max(0, i - SMOOTH); j <= Math.min(n - 1, i + SMOOTH); j++) {
                sum += ground[j];
                count++;
            }
            deck[i] = sum / count;
        }
        double perStep = grade * STEP;
        for (int i = 0; i < n; i++) {
            double fromA = i * perStep, fromB = (n - 1 - i) * perStep;
            double upper = Math.min(a.level() + fromA, b.level() + fromB);
            double lower = Math.max(a.level() - fromA, b.level() - fromB);
            deck[i] = Math.max(lower, Math.min(upper, deck[i]));
        }
        for (int i = 1; i < n; i++)
            deck[i] = Math.max(deck[i - 1] - perStep, Math.min(deck[i - 1] + perStep, deck[i]));
        for (int i = n - 2; i >= 0; i--)
            deck[i] = Math.max(deck[i + 1] - perStep, Math.min(deck[i + 1] + perStep, deck[i]));
        int[] ys = new int[n];
        for (int i = 0; i < n; i++)
            ys[i] = (int) Math.round(deck[i]);

        // the kind of each sample, then runs shorter than a few samples merged into their neighbours
        boolean[] over = new boolean[n], under = new boolean[n];
        for (int i = 0; i < n; i++) {
            over[i] = wet[i] || ys[i] - ground[i] > fillMax;
            under[i] = !wet[i] && ground[i] - ys[i] > tunnel;
        }
        // a bridge reaches onto its banks: widened from the raw answer, never from its own output (which ran the
        // first bridge on to the end of the road)
        Kind[] kind = new Kind[n];
        for (int i = 0; i < n; i++) {
            boolean bridge = false;
            for (int j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3) && !bridge; j++)
                bridge = over[j];
            kind[i] = bridge ? Kind.BRIDGE : under[i] ? Kind.TUNNEL : Kind.GROUND;
        }
        // a tunnel shorter than this is a deep cut instead: a few blocks of roof over a road reads as a mistake
        for (int i = 0; i < n;) {
            if (kind[i] != Kind.TUNNEL) {
                i++;
                continue;
            }
            int j = i;
            while (j < n && kind[j] == Kind.TUNNEL)
                j++;
            if ((j - i) * STEP < MIN_TUNNEL)
                for (int t = i; t < j; t++)
                    kind[t] = Kind.GROUND;
            i = j;
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int reach = halfWidth + SIDE + 2;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, (int) Math.floor(xs[i]) - reach);
            maxX = Math.max(maxX, (int) Math.ceil(xs[i]) + reach);
            minZ = Math.min(minZ, (int) Math.floor(zs[i]) - reach);
            maxZ = Math.max(maxZ, (int) Math.ceil(zs[i]) + reach);
        }
        return new Link(a, b, xs, zs, ys, deck, kind, minX, minZ, maxX, maxZ, (System.nanoTime() - started) / 1_000_000);
    }

    /** What a turn of one offset step costs, in the same units as a block of ground off the road's level. */
    private static final double TURN_COST = 1500;
    /** What a sample over water costs on top of its height difference. */
    private static final double WATER_COST = 60;
    /** What a route pays for each sample on a city's own ground: more than any detour around it. */
    private static final double CITY_COST = 2000;

    /** The cost of the stretch between waypoints {@code i0} (offset {@code k0}) and {@code i1} ({@code k1}). */
    private double segmentCost(CitySites.Site a, CitySites.Site b, double length, double ax, double az, double ux,
            double uz, double nx, double nz, int i0, int k0, int i1, int k1, int segments) {
        double t0 = i0 / (double) segments, t1 = i1 / (double) segments;
        double x0 = ax + ux * length * t0 + nx * k0 * OFFSET, z0 = az + uz * length * t0 + nz * k0 * OFFSET;
        double x1 = ax + ux * length * t1 + nx * k1 * OFFSET, z1 = az + uz * length * t1 + nz * k1 * OFFSET;
        double run = Math.hypot(x1 - x0, z1 - z0);
        int samples = Math.max(1, (int) Math.ceil(run / 16));
        double cost = 0;
        for (int s = 0; s <= samples; s++) {
            double f = s / (double) samples;
            double x = x0 + (x1 - x0) * f, z = z0 + (z1 - z0) * f;
            double want = a.level() + (b.level() - a.level()) * (t0 + (t1 - t0) * f);
            int top = gridTop(x, z);
            // a city's ground is the city's: a route back across it is drawn under its buildings and trees
            if (sites.isCityChunk(Math.floorDiv((int) Math.floor(x), 16), Math.floorDiv((int) Math.floor(z), 16)))
                cost += CITY_COST;
            if (top < seaLevel - 1)
                cost += WATER_COST + Math.abs(seaLevel + clearance - want);
            else
                cost += Math.abs(top - want);
        }
        return cost * run / (samples + 1) + run;
    }

    private int gridTop(double x, double z) {
        int gx = (int) Math.round(x / GRID), gz = (int) Math.round(z / GRID);
        long key = ((long) gx << 32) ^ (gz & 0xffffffffL);
        Integer known = grid.get(key);
        if (known != null)
            return known;
        int top = shaped(gx * GRID, gz * GRID, rough.top(gx * GRID, gz * GRID) + bias());
        grid.putIfAbsent(key, top);
        return top;
    }

    /**
     * The ground as it will be when the highway is drawn: vanilla's, except where a city's patch and blend ring reach,
     * which bring it to the city's level first ({@link CitySites#groundAt}, the same easing the chunk gets). Reading
     * the raw height there put a "tunnel" at a gate, under a hill the city had already levelled away.
     */
    private int shaped(int x, int z, int top) {
        CitySites.Site site = sites.influencing(x >> 4, z >> 4);
        return site == null ? top : sites.groundAt(site, x, z, top);
    }

    /** A straight line from one point toward another, about a block apart (the end itself left to the next piece). */
    private static void straight(List<double[]> out, double[] a, double[] b) {
        int steps = Math.max(1, (int) Math.ceil(Math.hypot(b[0] - a[0], b[1] - a[1])));
        for (int s = 0; s < steps; s++) {
            double t = s / (double) steps;
            out.add(new double[] { a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t });
        }
    }

    /**
     * A cubic from {@code a}, leaving along {@code ta}, to {@code b}, arriving along {@code tb} (unit headings, both
     * the way of travel), about a block apart: its handles a third of the span long, so it never loops.
     */
    private static void cubic(List<double[]> out, double[] a, double[] ta, double[] b, double[] tb) {
        double k = Math.hypot(b[0] - a[0], b[1] - a[1]) / 3;
        double[] c1 = { a[0] + ta[0] * k, a[1] + ta[1] * k }, c2 = { b[0] - tb[0] * k, b[1] - tb[1] * k };
        int steps = Math.max(1, (int) Math.ceil(3 * k));
        for (int s = 0; s < steps; s++) {
            double t = s / (double) steps, u = 1 - t;
            double[] q = new double[2];
            for (int c = 0; c < 2; c++)
                q[c] = u * u * u * a[c] + 3 * u * u * t * c1[c] + 3 * u * t * t * c2[c] + t * t * t * b[c];
            out.add(q);
        }
    }

    /** The unit heading from one point to another. */
    private static double[] unit(double[] to, double[] from) {
        double x = to[0] - from[0], z = to[1] - from[1], l = Math.max(1e-9, Math.hypot(x, z));
        return new double[] { x / l, z / l };
    }

    /** The polyline resampled every {@code step} blocks along its length. */
    private static double[][] resample(List<double[]> line, double step) {
        List<double[]> out = new ArrayList<>();
        out.add(line.get(0));
        double carried = 0;
        for (int i = 1; i < line.size(); i++) {
            double[] p = line.get(i - 1), q = line.get(i);
            double seg = Math.hypot(q[0] - p[0], q[1] - p[1]);
            double at = step - carried;
            while (at <= seg) {
                double f = at / seg;
                out.add(new double[] { p[0] + (q[0] - p[0]) * f, p[1] + (q[1] - p[1]) * f });
                at += step;
            }
            carried = seg - (at - step);
        }
        double[] last = line.get(line.size() - 1);
        double[] end = out.get(out.size() - 1);
        if (Math.hypot(last[0] - end[0], last[1] - end[1]) > 0.25)
            out.add(last);
        return out.toArray(new double[0][]);
    }

    // --- drawing -------------------------------------------------------------------------------------------

    /** Where a column stands against the nearest highway: how far from the centreline, the deck's y, its kind. */
    public record Near(double distance, double along, int deck, double height, Kind kind, int index, Link link) {
    }

    /** The nearest highway centreline to this column within {@code reach} blocks, among {@code candidates}; or null. */
    public static Near nearest(List<Link> candidates, double px, double pz, double reach) {
        Near best = null;
        for (Link link : candidates) {
            if (px < link.minX || px > link.maxX || pz < link.minZ || pz > link.maxZ)
                continue;
            double[] xs = link.x, zs = link.z;
            for (int i = 0; i < xs.length - 1; i++) {
                double x0 = xs[i], z0 = zs[i];
                if (Math.abs(px - x0) > reach + STEP || Math.abs(pz - z0) > reach + STEP)
                    continue;
                double dx = xs[i + 1] - x0, dz = zs[i + 1] - z0;
                double len2 = dx * dx + dz * dz;
                double f = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((px - x0) * dx + (pz - z0) * dz) / len2));
                double cx = x0 + dx * f, cz = z0 + dz * f;
                double d = Math.hypot(px - cx, pz - cz);
                if (d <= reach && (best == null || d < best.distance)) {
                    int y = (int) Math.round(link.y[i] + (link.y[i + 1] - link.y[i]) * f);
                    double h = link.h[i] + (link.h[i + 1] - link.h[i]) * f;
                    Kind k = link.kind[f < 0.5 ? i : i + 1];
                    best = new Near(d, (i + f) * STEP, y, h, k, i, link);
                }
            }
        }
        return best;
    }
}
