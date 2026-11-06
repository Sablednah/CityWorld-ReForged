package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Highways between the cities of a vanilla-terrain world: one road from each city to its neighbour east and south,
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
 * {@link #FILL_MAX}), or {@link Kind#BRIDGE} over water or over a drop deeper than that. Short runs are merged, so a
 * bridge is a bridge from bank to bank.
 *
 * <p>Spike (2026-11-06): behind {@code -Dcityworld.highways=true} until it is measured and made a setting.
 */
public final class Highways {

    /** Blocks between centreline samples. */
    static final int STEP = 2;
    /** Blocks between route waypoints, and how far sideways each may move, in steps of {@link #OFFSET}. */
    static final int WAYPOINT = 192, OFFSET = 48, OFFSETS = 6;
    /** Blocks a link runs straight out of each city before it may turn. */
    static final int RUN_OUT = 40;
    /** The deck's steepest climb: blocks of rise per block of run. */
    static final double GRADE = 0.1;
    /** Samples either side the ground is averaged over before the grade is applied. */
    static final int SMOOTH = 12;
    /** The deepest a road is banked up; deeper (or over water) it is a bridge. */
    static final int FILL_MAX = 8;
    /** How high a deck stands over water, above sea level. */
    static final int WATER_CLEARANCE = 5;
    /** Half the deck's width (the deck is 2 * this + 1 wide), and how far the cut and bank slopes reach past it. */
    public static final int HALF_WIDTH = 4, SIDE = 14;
    /** Grid the route search reads heights on. */
    static final int GRID = 32;

    public enum Kind { GROUND, BRIDGE }

    /**
     * One highway: the centreline as samples ({@code x}, {@code z}, deck {@code y}, {@link Kind}), and the box
     * (in blocks, the slopes included) it can change anything in.
     */
    public record Link(CitySites.Site a, CitySites.Site b, double[] x, double[] z, int[] y, Kind[] kind,
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
    private final ConcurrentHashMap<Long, java.util.Optional<Link>> links = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Long, Integer> grid = new ConcurrentHashMap<>();

    public Highways(CitySites sites) {
        this.sites = sites;
        this.seaLevel = sites.seaLevel();
    }

    /** Whether the spike is on for this run. */
    public static boolean enabled() {
        return Boolean.getBoolean("cityworld.highways");
    }

    /**
     * Every link whose box reaches into this square of blocks. A link joins a cell to its east or south neighbour,
     * and its route stays within {@link #OFFSETS} offsets of the line between them, so only the links of the cells
     * around the square's own need asking — and only those whose rough box (from the two sites alone, before any
     * height is read) reaches it are planned.
     */
    public List<Link> linksTouching(int x0, int z0, int x1, int z1) {
        List<Link> found = new ArrayList<>();
        int cell = sites.cell();
        int cx0 = Math.floorDiv(x0, cell) - 1, cx1 = Math.floorDiv(x1, cell) + 1;
        int cz0 = Math.floorDiv(z0, cell) - 1, cz1 = Math.floorDiv(z1, cell) + 1;
        int pad = OFFSETS * OFFSET + HALF_WIDTH + SIDE + RUN_OUT;
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                CitySites.Site here = sites.siteAt(cx * cell, cz * cell);
                if (here == null)
                    continue;
                for (int dir = 0; dir < 2; dir++) {
                    int nx = cx + (dir == 0 ? 1 : 0), nz = cz + (dir == 0 ? 0 : 1);
                    CitySites.Site there = sites.siteAt(nx * cell, nz * cell);
                    if (there == null)
                        continue;
                    int bx0 = Math.min(here.centreX(), there.centreX()) - pad, bx1 = Math.max(here.centreX(), there.centreX()) + pad;
                    int bz0 = Math.min(here.centreZ(), there.centreZ()) - pad, bz1 = Math.max(here.centreZ(), there.centreZ()) + pad;
                    if (x1 < bx0 || x0 > bx1 || z1 < bz0 || z0 > bz1)
                        continue;
                    Link link = link(cx, cz, dir, here, there);
                    if (link != null && link.touches(x0, z0, x1, z1))
                        found.add(link);
                }
            }
        return found;
    }

    /** Every link out of the cells in this square of cells, planned: for the survey. */
    public List<Link> linksOfCells(int cx0, int cz0, int cx1, int cz1) {
        List<Link> found = new ArrayList<>();
        int cell = sites.cell();
        for (int cx = cx0; cx <= cx1; cx++)
            for (int cz = cz0; cz <= cz1; cz++) {
                CitySites.Site here = sites.siteAt(cx * cell, cz * cell);
                if (here == null)
                    continue;
                for (int dir = 0; dir < 2; dir++) {
                    CitySites.Site there = sites.siteAt((cx + (dir == 0 ? 1 : 0)) * cell, (cz + (dir == 0 ? 0 : 1)) * cell);
                    if (there == null)
                        continue;
                    Link link = link(cx, cz, dir, here, there);
                    if (link != null)
                        found.add(link);
                }
            }
        return found;
    }

    private Link link(int cx, int cz, int dir, CitySites.Site here, CitySites.Site there) {
        long key = ((long) cx << 33) ^ ((long) cz << 1) ^ dir;
        var known = links.get(key);
        if (known != null)
            return known.orElse(null);
        // outside any map lock: planning reads hundreds of vanilla heights, and any thread gets the same answer
        Link planned;
        try {
            planned = plan(here, there);
        } catch (RuntimeException e) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: highway {},{} dir {} FAILED", cx, cz, dir, e);
            planned = null;
        }
        var raced = links.putIfAbsent(key, java.util.Optional.ofNullable(planned));
        return raced != null ? raced.orElse(null) : planned;
    }

    // --- planning -------------------------------------------------------------------------------------------

    /** Where a link leaves a city: the point on its edge, and the unit direction it runs out in. */
    private record Gate(double x, double z, double dx, double dz) {
    }

    /**
     * The gate on {@code site}'s side facing {@code toward}: along the road row (or column) nearest where the straight
     * line between the centres crosses the city's edge, at the first chunk past the city's ground.
     */
    private Gate gate(CitySites.Site site, CitySites.Site toward) {
        double dx = toward.centreX() - site.centreX(), dz = toward.centreZ() - site.centreZ();
        boolean alongX = Math.abs(dx) >= Math.abs(dz);
        int sign = alongX ? (dx >= 0 ? 1 : -1) : (dz >= 0 ? 1 : -1);
        double length = Math.hypot(dx, dz);
        // where the line leaves the disc, across the run of the road rows
        double across = alongX ? site.centreZ() + dz / length * site.radius() : site.centreX() + dx / length * site.radius();
        double centreAcross = alongX ? site.centreZ() : site.centreX();
        // keep the row well inside the disc, or it meets the edge almost at once
        double limit = site.radius() * 0.6;
        across = Math.max(centreAcross - limit, Math.min(centreAcross + limit, across));
        int row = nearestRoadLine(Math.floorDiv((int) Math.round(across), 16));
        // walk out from the centre along the row to the first chunk that is not city ground
        int start = Math.floorDiv(alongX ? site.centreX() : site.centreZ(), 16);
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
        return alongX ? new Gate(edge, rowCentre, sign, 0) : new Gate(rowCentre, edge, 0, sign);
    }

    /** The road line (chunk index 2 or 7 within its platmap) nearest this chunk index. */
    static int nearestRoadLine(int chunk) {
        int base = Math.floorDiv(chunk - 2, 5) * 5 + 2;
        return chunk - base <= 2 ? base : base + 5;
    }

    private Link plan(CitySites.Site a, CitySites.Site b) {
        long started = System.nanoTime();
        Gate ga = gate(a, b), gb = gate(b, a);
        double ax = ga.x + ga.dx * RUN_OUT, az = ga.z + ga.dz * RUN_OUT;
        double bx = gb.x + gb.dx * RUN_OUT, bz = gb.z + gb.dz * RUN_OUT;
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

        // the control points: gate, run-out end, waypoints, run-out end, gate
        List<double[]> control = new ArrayList<>();
        control.add(new double[] { ga.x, ga.z });
        for (int i = 0; i <= segments; i++) {
            double t = i / (double) segments;
            control.add(new double[] { ax + lx * t + nx * offset[i] * OFFSET, az + lz * t + nz * offset[i] * OFFSET });
        }
        control.add(new double[] { gb.x, gb.z });
        double[][] line = resample(catmullRom(control), STEP);
        int n = line.length;
        double[] xs = new double[n], zs = new double[n];
        for (int i = 0; i < n; i++) {
            xs[i] = line[i][0];
            zs[i] = line[i][1];
        }

        // the ground under the centreline, every other sample, read exactly (a narrow river hides between grid points)
        double[] ground = new double[n];
        boolean[] wet = new boolean[n];
        for (int i = 0; i < n; i += 2) {
            int top = sites.heights().top((int) Math.floor(xs[i]), (int) Math.floor(zs[i]));
            wet[i] = top < seaLevel - 1;
            ground[i] = wet[i] ? seaLevel + WATER_CLEARANCE : top;
        }
        for (int i = 1; i < n; i += 2) {
            int j = Math.min(n - 1, i + 1);
            if (j % 2 == 1)
                j = i - 1;
            ground[i] = (ground[i - 1] + ground[j]) / 2;
            wet[i] = wet[i - 1] || wet[j];
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
        double perStep = GRADE * STEP;
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
        boolean[] over = new boolean[n];
        for (int i = 0; i < n; i++)
            over[i] = wet[i] || ys[i] - ground[i] > FILL_MAX;
        // a bridge reaches onto its banks: widened from the raw answer, never from its own output (which ran the
        // first bridge on to the end of the road)
        Kind[] kind = new Kind[n];
        for (int i = 0; i < n; i++) {
            boolean bridge = false;
            for (int j = Math.max(0, i - 3); j <= Math.min(n - 1, i + 3) && !bridge; j++)
                bridge = over[j];
            kind[i] = bridge ? Kind.BRIDGE : Kind.GROUND;
        }
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        int reach = HALF_WIDTH + SIDE + 2;
        for (int i = 0; i < n; i++) {
            minX = Math.min(minX, (int) Math.floor(xs[i]) - reach);
            maxX = Math.max(maxX, (int) Math.ceil(xs[i]) + reach);
            minZ = Math.min(minZ, (int) Math.floor(zs[i]) - reach);
            maxZ = Math.max(maxZ, (int) Math.ceil(zs[i]) + reach);
        }
        return new Link(a, b, xs, zs, ys, kind, minX, minZ, maxX, maxZ, (System.nanoTime() - started) / 1_000_000);
    }

    /** What a turn of one offset step costs, in the same units as a block of ground off the road's level. */
    private static final double TURN_COST = 1500;
    /** What a sample over water costs on top of its height difference. */
    private static final double WATER_COST = 60;

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
            if (top < seaLevel - 1)
                cost += WATER_COST + Math.abs(seaLevel + WATER_CLEARANCE - want);
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
        int top = sites.heights().top(gx * GRID, gz * GRID);
        grid.putIfAbsent(key, top);
        return top;
    }

    /** A centripetal-free uniform Catmull-Rom curve through the points, as a dense polyline (about a block apart). */
    private static List<double[]> catmullRom(List<double[]> points) {
        List<double[]> out = new ArrayList<>();
        int n = points.size();
        for (int i = 0; i < n - 1; i++) {
            double[] p0 = points.get(Math.max(0, i - 1)), p1 = points.get(i), p2 = points.get(i + 1),
                    p3 = points.get(Math.min(n - 1, i + 2));
            int steps = Math.max(1, (int) Math.ceil(Math.hypot(p2[0] - p1[0], p2[1] - p1[1])));
            for (int s = 0; s < steps; s++) {
                double t = s / (double) steps, t2 = t * t, t3 = t2 * t;
                double[] q = new double[2];
                for (int c = 0; c < 2; c++)
                    q[c] = 0.5 * (2 * p1[c] + (-p0[c] + p2[c]) * t + (2 * p0[c] - 5 * p1[c] + 4 * p2[c] - p3[c]) * t2
                            + (-p0[c] + 3 * p1[c] - 3 * p2[c] + p3[c]) * t3);
                out.add(q);
            }
        }
        out.add(points.get(n - 1).clone());
        return out;
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
    public record Near(double distance, double along, int deck, Kind kind, int index, Link link) {
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
                    Kind k = link.kind[f < 0.5 ? i : i + 1];
                    best = new Near(d, (i + f) * STEP, y, k, i, link);
                }
            }
        }
        return best;
    }
}
