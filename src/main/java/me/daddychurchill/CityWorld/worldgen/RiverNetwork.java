package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Rivers for CityWorld's own terrain (settings {@code cities.rivers}): springs on high ground, each traced
 * downhill over a coarse node grid and on across the plain to a sea, drawn into the ground as channels, falls and
 * lakes. {@code ShapeProvider_Normal} asks {@link #chunk} for each chunk's columns; {@code survey:rivers} draws the
 * network as a map.
 *
 * <p>Everything is a pure function of the seed and the terrain, so any thread, in any order, draws the same river:
 * <ul>
 * <li><b>Off the mountains</b> a course follows the {@link Terrain#drainage drainage} field node by node: the lowest
 * of the eight neighbours. A pit (no lower neighbour) spills: a bounded priority flood finds the lowest rim and the
 * first node beyond it that runs lower (never back into water this course already passed).</li>
 * <li><b>On the plain</b> (street level and below) the drainage field is a field of little pits and made rivers loop
 * for kilometres, so a course takes the cheapest route to a sea instead ({@link #toSea}): climbing costs, open water
 * is cheap (rivers chain ponds), and a seeded wobble makes it meander. A sea is water of {@link #SEA_NODES} nodes or
 * more; anything smaller is a pond on the way.</li>
 * <li><b>The water level</b> follows the ground downstream and never rises, so a bump is cut through; where the course
 * climbs out of an enclosed basin by more than {@link #LAKE_RISE} the water stands at the rim and the basin is a
 * lake. Along the drawn curve the level steps down where the ground does: a fall.</li>
 * <li><b>Width</b> is mostly how far the water has run, a little how many springs feed it ({@link #width}).</li>
 * </ul>
 *
 * <p>A course reaches at most {@link #MAX_DESCENT} nodes off its mountain and {@link #ROUTE_RADIUS} nodes across the
 * plain, so every course touching a region starts within {@link #REACH} of it: a region lists exactly the courses
 * that can touch it, and counts each node's flow exactly.
 */
public final class RiverNetwork {

    /** What a network reads from the terrain. */
    public interface Terrain {
        /** Smooth height that decides which way water runs. */
        double drainage(int x, int z);

        /** The real ground height, before any river. */
        double ground(int x, int z);

        int seaLevel();
    }

    public static final int NODE = 32; // blocks between nodes
    public static final int CELL = 448; // one spring candidate per cell
    public static final int SPRING_ABOVE = 24; // a spring needs ground this far above the sea
    public static final int MAX_DESCENT = 64; // nodes a course may take coming off the high ground
    public static final int ROUTE_RADIUS = 64; // nodes a plain route may stray from where it started, each way
    public static final int FLOOD_LIMIT = 8000; // nodes a pit's spill search may visit
    public static final int FALL = 8; // a level drop between two nodes this big is a waterfall (survey)
    public static final int LAKE_NODES = 48; // the largest basin that holds a lake
    public static final int LAKE_RISE = 6;
    /** Nodes of connected water a river needs to end in; a smaller pond is a lake it passes through. */
    public static final int SEA_NODES = Integer.getInteger("cityworld.rivers.sea", 256);
    public static final int MAX_WIDTH = 28;
    public static final double HILL_WIDEN = 300.0; // blocks of run per block of width, coming off the high ground
    public static final double PLAIN_WIDEN = 90.0; // ...and once on the plain, where rivers spread out
    public static final int BANK = 28; // blocks beyond the water over which a cut is eased back into the land
    public static final int REGION = 512;
    public static final int REACH = (MAX_DESCENT + ROUTE_RADIUS + 2) * NODE;
    /** Flows are exact for nodes this far outside a region: wider than any channel and its banks. */
    private static final int FLOW_MARGIN = 256;

    public static final int NONE = Integer.MIN_VALUE;

    public enum End { SEA, LAKE, LOST }

    public record Lake(int level, Set<Long> basin) {}

    /**
     * One spring's course: its nodes with the water level and ground at each, then the drawn curve (the nodes nudged
     * off the grid and rounded) with the water level and owning node at each point, and its bounds.
     */
    public record Course(long spring, List<Long> nodes, int[] level, int[] ground, End end, List<Lake> lakes,
            double[] px, double[] pz, int[] plevel, int[] pnode, int minX, int minZ, int maxX, int maxZ, int plainAt,
            float[] pdepth) {}

    private final long seed;
    private final Terrain terrain;
    private final int sea;

    // the river's raggedness: its edge, its width along the course, its banks
    private final me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator edgeNoise, widthNoise, bankNoise;

    public RiverNetwork(long seed, Terrain terrain) {
        this.seed = seed;
        this.terrain = terrain;
        this.sea = terrain.seaLevel();
        edgeNoise = new me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator(seed + 5101);
        widthNoise = new me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator(seed + 5102);
        bankNoise = new me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator(seed + 5103);
    }

    /** The ground here before any river. */
    public double naturalAt(int x, int z) {
        return terrain.ground(x, z);
    }

    // ---- nodes ----

    public static long node(int i, int j) {
        return ((long) i << 32) ^ (j & 0xffffffffL);
    }

    public static int ni(long n) {
        return (int) (n >> 32);
    }

    public static int nj(long n) {
        return (int) n;
    }

    private static final int TILE = 64; // nodes a side

    /** Terrain at every node of a 64x64 block of nodes, sampled once. */
    private final class Tile {
        final float[] drainage = new float[TILE * TILE];
        final short[] ground = new short[TILE * TILE];
        final byte[] sea = new byte[TILE * TILE]; // 0 unknown, 1 pond or dry, 2 part of a sea

        Tile(int ti, int tj) {
            for (int a = 0; a < TILE; a++)
                for (int b = 0; b < TILE; b++) {
                    int x = (ti * TILE + a) * NODE, z = (tj * TILE + b) * NODE;
                    drainage[a * TILE + b] = (float) terrain.drainage(x, z);
                    ground[a * TILE + b] = (short) Math.floor(terrain.ground(x, z));
                }
        }
    }

    private final ConcurrentHashMap<Long, Tile> tiles = new ConcurrentHashMap<>();

    private Tile tileOf(long n) {
        int ti = Math.floorDiv(ni(n), TILE), tj = Math.floorDiv(nj(n), TILE);
        long key = node(ti, tj);
        Tile t = tiles.get(key);
        if (t == null) {
            // built outside the map's locks (a slow compute inside computeIfAbsent stalls unrelated threads)
            if (tiles.size() > 4096)
                tiles.clear();
            Tile built = new Tile(ti, tj);
            t = tiles.putIfAbsent(key, built);
            if (t == null)
                t = built;
        }
        return t;
    }

    private static int at(long n) {
        return Math.floorMod(ni(n), TILE) * TILE + Math.floorMod(nj(n), TILE);
    }

    double drainage(long n) {
        return tileOf(n).drainage[at(n)];
    }

    int ground(long n) {
        return tileOf(n).ground[at(n)];
    }

    /** Whether this underwater node belongs to a body of at least {@link #SEA_NODES} nodes. */
    boolean isSea(long start) {
        byte known = tileOf(start).sea[at(start)];
        if (known != 0)
            return known == 2;
        Set<Long> body = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        todo.add(start);
        body.add(start);
        while (!todo.isEmpty() && body.size() < SEA_NODES) {
            long n = todo.poll();
            for (int k = 0; k < 8; k++) {
                long m = node(ni(n) + DI[k], nj(n) + DJ[k]);
                if (!body.contains(m) && ground(m) < sea) {
                    body.add(m);
                    todo.add(m);
                }
            }
        }
        boolean big = body.size() >= SEA_NODES;
        for (long n : body)
            tileOf(n).sea[at(n)] = (byte) (big ? 2 : 1);
        return big;
    }

    // ---- springs and courses ----

    /** The spring of a cell, or null: a seeded node in the cell, kept only on high ground. */
    public Long spring(int cellX, int cellZ) {
        long h = mix(seed ^ (cellX * 0x9E3779B97F4A7C15L) ^ (cellZ * 0xC2B2AE3D27D4EB4FL));
        int per = CELL / NODE;
        int i = cellX * per + (int) Math.floorMod(h, (long) per);
        int j = cellZ * per + (int) Math.floorMod(h >>> 20, (long) per);
        long n = node(i, j);
        return ground(n) >= sea + SPRING_ABOVE ? n : null;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
        z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
        return z ^ (z >>> 31);
    }

    private static final int[] DI = { -1, 0, 1, -1, 1, -1, 0, 1 };
    private static final int[] DJ = { -1, -1, -1, 0, 0, 1, 1, 1 };

    /** The lowest neighbour if it is lower than here, else null. */
    Long downhill(long n) {
        double best = drainage(n);
        Long down = null;
        for (int k = 0; k < 8; k++) {
            long m = node(ni(n) + DI[k], nj(n) + DJ[k]);
            double d = drainage(m);
            if (d < best) {
                best = d;
                down = m;
            }
        }
        return down;
    }

    /** From a pit, the path over its lowest rim to the first node that runs lower; null if none within reach. */
    List<Long> spill(long pit, Set<Long> passed) {
        record Q(long n, double d) {}
        PriorityQueue<Q> queue = new PriorityQueue<>((a, b) -> Double.compare(a.d, b.d));
        Map<Long, Long> parent = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        queue.add(new Q(pit, drainage(pit)));
        seen.add(pit);
        double water = drainage(pit);
        int popped = 0;
        while (!queue.isEmpty() && popped++ < FLOOD_LIMIT) {
            Q q = queue.poll();
            if (q.n != pit && q.d < water && !passed.contains(q.n)) {
                List<Long> path = new ArrayList<>();
                for (Long at = q.n; at != null && at != pit; at = parent.get(at))
                    path.add(0, at);
                return path;
            }
            water = Math.max(water, q.d);
            for (int k = 0; k < 8; k++) {
                long m = node(ni(q.n) + DI[k], nj(q.n) + DJ[k]);
                if (seen.add(m)) {
                    parent.put(m, q.n);
                    queue.add(new Q(m, drainage(m)));
                }
            }
        }
        return null;
    }

    /**
     * From a river's arrival on the plain, the cheapest route to a sea: a step costs its length, climbing above
     * street level costs extra, open water costs little (so a river chains ponds), and a seeded wobble makes it
     * meander. Within {@link #ROUTE_RADIUS} nodes each way; failing a sea, the nearest pond reached; failing that,
     * null.
     */
    List<Long> toSea(long from, Set<Long> passed) {
        final int side = 2 * ROUTE_RADIUS + 1;
        int i0 = ni(from) - ROUTE_RADIUS, j0 = nj(from) - ROUTE_RADIUS;
        double[] best = new double[side * side];
        int[] parent = new int[side * side];
        java.util.Arrays.fill(best, Double.MAX_VALUE);
        java.util.Arrays.fill(parent, -1);
        // the box's ground and wobble, looked up once each: the search reads each cell's eight times over, and those
        // map lookups were nearly all the cost of a new world (225 ms a route)
        int[] gbox = new int[side * side];
        float[] wobble = new float[side * side];
        for (int a = 0; a < side; a++)
            for (int b = 0; b < side; b++) {
                long m = node(i0 + a, j0 + b);
                gbox[a * side + b] = ground(m);
                wobble[a * side + b] = (float) (0.6 * ((mix(seed * 17 + m) & 0xffff) / 65535.0));
            }
        Heap queue = new Heap(side * side);
        int start = ROUTE_RADIUS * side + ROUTE_RADIUS;
        best[start] = 0;
        queue.push(start, 0);
        int pond = -1, found = -1;
        while (queue.size > 0) {
            double qcost = queue.topCost();
            int qat = queue.pop();
            if (qcost > best[qat])
                continue;
            int qi = qat / side, qj = qat % side;
            long qn = node(i0 + qi, j0 + qj);
            if (qat != start && gbox[qat] < sea) {
                if (isSea(qn)) {
                    found = qat;
                    break;
                }
                if (pond < 0 && !passed.contains(qn))
                    pond = qat;
            }
            for (int k = 0; k < 8; k++) {
                int mi = qi + DI[k], mj = qj + DJ[k];
                if (mi < 0 || mj < 0 || mi >= side || mj >= side)
                    continue;
                int at = mi * side + mj;
                int gm = gbox[at];
                double step = (DI[k] != 0 && DJ[k] != 0) ? 1.414 : 1.0;
                if (gm < sea)
                    step *= 0.4;
                else if (gm > sea + 1)
                    step += (gm - sea - 1) * 0.35;
                step += wobble[at];
                double c = qcost + step;
                if (c < best[at]) {
                    best[at] = c;
                    parent[at] = qat;
                    queue.push(at, c);
                }
            }
        }
        int goal = found >= 0 ? found : pond;
        if (goal < 0)
            return null;
        List<Long> path = new ArrayList<>();
        for (int at = goal; at >= 0 && at != start; at = parent[at])
            path.add(0, node(i0 + at / side, j0 + at % side));
        return path;
    }

    /** A binary min-heap of cell indices by cost, on plain arrays: no object a push (the route search makes ~10^5). */
    private static final class Heap {
        int[] at;
        double[] cost;
        int size;

        Heap(int capacity) {
            at = new int[capacity];
            cost = new double[capacity];
        }

        void push(int a, double c) {
            if (size == at.length) {
                at = java.util.Arrays.copyOf(at, size * 2);
                cost = java.util.Arrays.copyOf(cost, size * 2);
            }
            int i = size++;
            while (i > 0) {
                int up = (i - 1) >> 1;
                if (cost[up] <= c)
                    break;
                at[i] = at[up];
                cost[i] = cost[up];
                i = up;
            }
            at[i] = a;
            cost[i] = c;
        }

        double topCost() {
            return cost[0];
        }

        int pop() {
            int top = at[0];
            int last = at[--size];
            double lc = cost[size];
            int i = 0;
            while (true) {
                int l = 2 * i + 1;
                if (l >= size)
                    break;
                int r = l + 1, m = r < size && cost[r] < cost[l] ? r : l;
                if (cost[m] >= lc)
                    break;
                at[i] = at[m];
                cost[i] = cost[m];
                i = m;
            }
            at[i] = last;
            cost[i] = lc;
            return top;
        }
    }

    /** The basin that holds water standing at {@code rim} around this node, or null if it is not enclosed. */
    Set<Long> basin(long inside, int rim) {
        Set<Long> basin = new HashSet<>();
        ArrayDeque<Long> todo = new ArrayDeque<>();
        basin.add(inside);
        todo.add(inside);
        while (!todo.isEmpty()) {
            long n = todo.poll();
            for (int k = 0; k < 8; k++) {
                long m = node(ni(n) + DI[k], nj(n) + DJ[k]);
                if (!basin.contains(m) && ground(m) - 1 < rim) {
                    if (basin.size() >= LAKE_NODES)
                        return null;
                    basin.add(m);
                    todo.add(m);
                }
            }
        }
        return basin;
    }

    private final ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Course>> courses = new ConcurrentHashMap<>();

    /** A spring's course, traced once: a second thread asking meanwhile waits for it rather than tracing it again. */
    public Course course(long spring) {
        return once(courses, spring, () -> trace(spring));
    }

    /**
     * The value for a key, made by exactly one thread; others asking meanwhile wait for it. The making happens
     * outside the map's locks (a slow compute inside computeIfAbsent stalls unrelated threads).
     */
    private static <T> T once(ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<T>> map, long key,
            java.util.function.Supplier<T> make) {
        var known = map.get(key);
        if (known == null) {
            var mine = new java.util.concurrent.CompletableFuture<T>();
            known = map.putIfAbsent(key, mine);
            if (known == null) {
                try {
                    mine.complete(make.get());
                } catch (Throwable t) {
                    map.remove(key, mine);
                    mine.completeExceptionally(t);
                    throw t;
                }
                return mine.join();
            }
        }
        return known.join();
    }

    public Course trace(long spring) {
        List<Long> nodes = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        long n = spring;
        nodes.add(n);
        visited.add(n);
        End end = End.LOST;
        int plainAt = Integer.MAX_VALUE;
        while (nodes.size() < MAX_DESCENT) {
            if (ground(n) < sea && isSea(n)) {
                end = End.SEA;
                break;
            }
            if (ground(n) <= sea + 1) {
                // on the plain: the cheapest way to a sea from here
                plainAt = nodes.size() - 1;
                List<Long> route = toSea(n, visited);
                if (route == null) {
                    end = End.LAKE;
                    break;
                }
                for (long m : route) {
                    visited.add(m);
                    nodes.add(m);
                }
                long last = nodes.get(nodes.size() - 1);
                end = ground(last) < sea && isSea(last) ? End.SEA : End.LAKE;
                break;
            }
            Long down = downhill(n);
            List<Long> next = down != null && !visited.contains(down) ? List.of(down) : spill(n, visited);
            if (next == null) {
                end = End.LAKE;
                break;
            }
            boolean loop = false;
            for (long m : next) {
                if (!visited.add(m) && m == next.get(next.size() - 1)) {
                    loop = true;
                    break;
                }
                nodes.add(m);
            }
            if (loop) {
                end = End.LAKE;
                break;
            }
            n = nodes.get(nodes.size() - 1);
        }
        int size = nodes.size();
        int[] g = new int[size], level = new int[size];
        for (int k = 0; k < size; k++)
            g[k] = ground(nodes.get(k));
        // the ground the water follows: the lower of the node and its neighbours along the course, so a single
        // high or low node (the terrain's fine noise) neither dams nor drops it
        int[] bed = new int[size];
        for (int k = 0; k < size; k++) {
            int lo = g[k];
            if (k > 0)
                lo = Math.min(lo, Math.max(g[k - 1], g[k]));
            if (k + 1 < size)
                lo = Math.min(lo, Math.max(g[k + 1], g[k]));
            bed[k] = lo;
        }
        // downstream the water never rises: a bump is cut through...
        level[0] = Math.max(sea, bed[0] - 1);
        for (int k = 1; k < size; k++)
            level[k] = Math.max(sea, Math.min(level[k - 1], bed[k] - 1));
        if (end == End.SEA)
            level[size - 1] = sea;
        // ...unless climbing it would cut deeper than LAKE_RISE out of an enclosed basin: then the water stands at
        // the rim and fills the basin behind it as a lake, back up to where the ground is higher than the lake
        List<Lake> lakes = new ArrayList<>();
        for (int k = 1; k < size; k++) {
            int rim = bed[k] - 1;
            if (rim - level[k] <= LAKE_RISE)
                continue;
            boolean climbsOut = false;
            for (int a = k + 1; a < size && a <= k + 3; a++)
                if (bed[a] < bed[k])
                    climbsOut = true;
            if (!climbsOut)
                continue;
            Set<Long> basin = basin(nodes.get(k - 1), rim);
            if (basin == null)
                continue;
            lakes.add(new Lake(rim, basin));
            for (int j = k; j >= 0 && level[j] < rim; j--) {
                if (g[j] - 1 >= rim && j < k)
                    break;
                level[j] = rim;
            }
            for (int j = k + 1; j < size; j++)
                level[j] = Math.max(sea, Math.min(level[j - 1], bed[j] - 1));
            if (end == End.SEA)
                level[size - 1] = sea;
        }
        return drawn(spring, nodes, level, g, end, lakes, plainAt);
    }

    /**
     * The course as drawn: a smooth curve through its nudged nodes. Walking down it, the water follows the real
     * ground under each point and never rises, so it steps down exactly where the ground does (a fall); where its
     * node stands in a lake, the lake's water holds it up.
     */
    private Course drawn(long spring, List<Long> nodes, int[] level, int[] g, End end, List<Lake> lakes, int plainAt) {
        List<double[]> pts = curve(nodes);
        int count = pts.size(), last = nodes.size() - 1;
        double[] px = new double[count], pz = new double[count], along = new double[count];
        int[] plevel = new int[count], pnode = new int[count];
        boolean[] wet = new boolean[count], fixed = new boolean[count];
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int p = 0; p < count; p++) {
            px[p] = pts.get(p)[0];
            pz[p] = pts.get(p)[1];
            if (p > 0)
                along[p] = along[p - 1] + Math.hypot(px[p] - px[p - 1], pz[p] - pz[p - 1]);
        }
        for (int p = 0; p < count; p++) {
            int k = count == 1 ? 0 : (int) Math.round((double) p * last / (count - 1));
            pnode[p] = k;
            // the ground under the water: the lowest of the middle and both banks, so a hillside falling away
            // sideways never leaves the water standing above its lower bank
            int here = (int) Math.floor(terrain.ground((int) Math.floor(px[p]), (int) Math.floor(pz[p])));
            wet[p] = here < sea;
            double tx = px[Math.min(count - 1, p + 1)] - px[Math.max(0, p - 1)];
            double tz = pz[Math.min(count - 1, p + 1)] - pz[Math.max(0, p - 1)];
            double tl = Math.hypot(tx, tz);
            if (tl > 0) {
                // across its banks too, every two blocks, here and half-way to the next point: on a spur or a
                // ridge it cuts down to the lower side, into the hill, and no dip beside it sits below its water
                double reach = width(1, k, plainAt) * 0.65 + ACROSS;
                double ux = -tz / tl, uz = tx / tl;
                double mx = p + 1 < count ? (px[p + 1] - px[p]) / 2 : 0, mz = p + 1 < count ? (pz[p + 1] - pz[p]) / 2 : 0;
                // every four blocks at the point and every four half-way on, staggered by two: the same coverage
                // as every two at both, for half the samples (tracing was most of the cost of a new world)
                for (int half = 0; half <= 1; half++)
                    for (double o = -reach + half * 2; o <= reach; o += 4)
                        here = Math.min(here, (int) Math.floor(terrain.ground((int) Math.floor(px[p] + half * mx + ux * o),
                                (int) Math.floor(pz[p] + half * mz + uz * o))));
            }
            boolean lake = level[k] > g[k] - 1;
            // off the plain a stream cuts down into its hillside, so it runs in a gully rather than on the slope
            int cut = here > sea + 1 ? INCISE : 0;
            int cap = lake ? Math.max(here - 1, level[k]) : here - 1 - cut;
            fixed[p] = lake || cut == 0;
            int prev = p == 0 ? Integer.MAX_VALUE : plevel[p - 1];
            plevel[p] = Math.max(sea, Math.min(prev, cap));
            minX = Math.min(minX, (int) Math.floor(px[p]));
            minZ = Math.min(minZ, (int) Math.floor(pz[p]));
            maxX = Math.max(maxX, (int) Math.ceil(px[p]));
            maxZ = Math.max(maxZ, (int) Math.ceil(pz[p]));
        }
        if (end == End.SEA && count > 0)
            plevel[count - 1] = sea;
        // on the high ground the descent gathers into falls: the water drops FALL_STEP at a time and holds between,
        // in a gully cut down to the pool below each lip. Rounded down, so it never stands above the ground.
        for (int p = 0; p < count; p++)
            if (!fixed[p] && plevel[p] > sea + FALL_STEP)
                plevel[p] = sea + Math.floorDiv(plevel[p] - sea, FALL_STEP) * FALL_STEP;
        for (int p = 1; p < count; p++)
            plevel[p] = Math.min(plevel[p], plevel[p - 1]);
        // where it meets the sea, its channel carries on out across the sea floor, dredged to its depth and rising
        // back to the floor as it goes, so the river meets the sea without a step up
        float[] pdepth = new float[count];
        java.util.Arrays.fill(pdepth, 1f);
        if (end == End.SEA && count > 1) {
            double dx = px[count - 1] - px[count - 2], dz = pz[count - 1] - pz[count - 2], dl = Math.hypot(dx, dz);
            if (dl > 0) {
                int more = (int) (OUT_TO_SEA / 4);
                int total = count + more;
                px = java.util.Arrays.copyOf(px, total);
                pz = java.util.Arrays.copyOf(pz, total);
                plevel = java.util.Arrays.copyOf(plevel, total);
                pnode = java.util.Arrays.copyOf(pnode, total);
                pdepth = java.util.Arrays.copyOf(pdepth, total);
                for (int e = 1; e <= more; e++) {
                    int p = count - 1 + e;
                    px[p] = px[count - 1] + dx / dl * 4 * e;
                    pz[p] = pz[count - 1] + dz / dl * 4 * e;
                    plevel[p] = sea;
                    pnode[p] = pnode[count - 1];
                    pdepth[p] = 1f - (float) e / (more + 1);
                    minX = Math.min(minX, (int) Math.floor(px[p]));
                    minZ = Math.min(minZ, (int) Math.floor(pz[p]));
                    maxX = Math.max(maxX, (int) Math.ceil(px[p]));
                    maxZ = Math.max(maxZ, (int) Math.ceil(pz[p]));
                }
            }
        }
        return new Course(spring, nodes, level, g, end, lakes, px, pz, plevel, pnode, minX, minZ, maxX, maxZ, plainAt,
                pdepth);
    }

    /** Blocks a river's channel is dredged on out into the sea. */
    private static final double OUT_TO_SEA = 48.0;
    /** How far a fall drops: the descent on the high ground is gathered into falls this tall. */
    private static final int FALL_STEP = 5;
    // what a fall's hollow hides (owner, 2026-10-05): one in four hide something; mostly crystals or ore that has
    // no business being there, now and then diamond, very rarely ancient debris — and, rarest of all, a chest
    public static final int FIND_CRYSTALS = 1, FIND_IRON = 2, FIND_COPPER = 3, FIND_GOLD = 4, FIND_DIAMOND = 5,
            FIND_DEBRIS = 6, FIND_CHEST = 9;

    /** {@code -Dcityworld.rivers.find=<FIND_*>}: every hollow hides that, for checking a find you would rarely meet. */
    private static final int FORCE_FIND = Integer.getInteger("cityworld.rivers.find", 0);

    /** What a fall hides, from its own hash: nothing, mostly. */
    static int findFor(long h) {
        if (FORCE_FIND != 0)
            return FORCE_FIND;
        if (Math.floorMod(h, 4) != 0)
            return 0;
        int roll = (int) Math.floorMod(h >>> 8, 1000L);
        if (roll < 25)
            return FIND_CHEST; // one find in forty
        if (roll < 366)
            return FIND_CRYSTALS;
        int ore = (int) Math.floorMod(h >>> 24, 100L);
        return ore < 36 ? FIND_IRON : ore < 72 ? FIND_COPPER : ore < 90 ? FIND_GOLD : ore < 98 ? FIND_DIAMOND : FIND_DEBRIS;
    }
    /** A drop this big between two curve points is a fall with a hollow carved in behind it. */
    private static final int HOLLOW_DROP = 4;
    /** Blocks beyond its edge, each side, that a stream's level looks across for the lowest ground. */
    private static final int ACROSS = 6;
    /** Blocks a stream cuts into the high ground below the lowest ground across it. */
    private static final int INCISE = 3;

    /** How wide a beach bar between a river and the sea it meets may be breached. */
    private static final int BREACH = 3;
    /** Blocks around a column whose ground its water may not stand above. */
    private static final int AROUND = 2;

    /** Where a node really sits: nudged off the grid by seed (up to a third of a node each way) so courses meander. */
    public double[] place(long n) {
        long h = mix(seed * 31 + n);
        double jx = ((h & 0xffff) / 65535.0 - 0.5) * NODE * 0.66, jz = (((h >>> 16) & 0xffff) / 65535.0 - 0.5) * NODE * 0.66;
        return new double[] { ni(n) * NODE + jx, nj(n) * NODE + jz };
    }

    /** A course as a smooth line through its placed nodes (three rounds of corner cutting). */
    public List<double[]> curve(List<Long> nodes) {
        List<double[]> pts = new ArrayList<>();
        for (long n : nodes)
            pts.add(place(n));
        for (int round = 0; round < 3 && pts.size() > 2; round++) {
            List<double[]> cut = new ArrayList<>();
            cut.add(pts.get(0));
            for (int k = 0; k + 1 < pts.size(); k++) {
                double[] a = pts.get(k), b = pts.get(k + 1);
                cut.add(new double[] { a[0] * 0.75 + b[0] * 0.25, a[1] * 0.75 + b[1] * 0.25 });
                cut.add(new double[] { a[0] * 0.25 + b[0] * 0.75, a[1] * 0.25 + b[1] * 0.75 });
            }
            cut.add(pts.get(pts.size() - 1));
            pts = cut;
        }
        return pts;
    }

    /**
     * How wide the water is, in blocks: mostly from how far it has run (a river widens on its way to the sea), a
     * little from how many springs feed it (joining streams add up). It spreads about three times faster once it is
     * out of the mountains.
     */
    public static int width(int flow, int runNodes, int plainAt) {
        int hill = Math.min(runNodes, plainAt), plain = Math.max(0, runNodes - plainAt);
        return (int) Math.min(MAX_WIDTH,
                Math.round(1 + 1.5 * Math.sqrt(flow) + hill * NODE / HILL_WIDEN + plain * NODE / PLAIN_WIDEN));
    }

    /** {@link #width(int, int, int)} not knowing where the plain begins (the survey's tallies). */
    public static int width(int flow, int runNodes) {
        return width(flow, runNodes, Integer.MAX_VALUE);
    }

    /** How deep the channel is below the water, by width and how far across it this column is (0 middle, 1 edge). */
    static int depth(int width, double across) {
        int deepest = Math.max(2, Math.min(6, 2 + width / 3));
        return Math.max(1, (int) Math.round(deepest * (1 - across * across)));
    }

    // ---- regions: every course that can touch a 512-block square, and the flow at its nodes ----

    private record Region(List<Course> courses, Map<Long, Integer> flow, Map<Long, Integer> lakeLevel) {}

    private final ConcurrentHashMap<Long, java.util.concurrent.CompletableFuture<Region>> regions = new ConcurrentHashMap<>();

    private Region region(int rx, int rz) {
        if (regions.size() > 1024)
            regions.clear();
        return once(regions, node(rx, rz), () -> buildRegion(rx, rz));
    }

    private Region buildRegion(int rx, int rz) {
        int x0 = rx * REGION, z0 = rz * REGION, x1 = x0 + REGION, z1 = z0 + REGION;
        int reach = REACH + FLOW_MARGIN;
        List<Long> springs = new ArrayList<>();
        for (int cx = Math.floorDiv(x0 - reach, CELL); cx <= Math.floorDiv(x1 + reach, CELL); cx++)
            for (int cz = Math.floorDiv(z0 - reach, CELL); cz <= Math.floorDiv(z1 + reach, CELL); cz++) {
                Long spring = spring(cx, cz);
                if (spring != null)
                    springs.add(spring);
            }
        // traced side by side: one thread tracing a hundred springs while every other worker waited for it was
        // most of the time a new world took to prepare (each course is still traced once, see course())
        List<Course> near = springs.parallelStream().map(this::course)
                .filter(c -> c.maxX() >= x0 - FLOW_MARGIN && c.minX() <= x1 + FLOW_MARGIN
                        && c.maxZ() >= z0 - FLOW_MARGIN && c.minZ() <= z1 + FLOW_MARGIN)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Map<Long, Integer> flow = new HashMap<>(), lakeLevel = new HashMap<>();
        for (Course c : near) {
            for (long n : new HashSet<>(c.nodes()))
                flow.merge(n, 1, Integer::sum);
            for (Lake lake : c.lakes())
                for (long n : lake.basin())
                    lakeLevel.merge(n, lake.level(), Math::max);
        }
        return new Region(near, flow, lakeLevel);
    }

    // ---- chunks: what the river does to each column ----

    /**
     * A chunk's river columns: the ground after the river (lowered into a channel, eased down a bank, or raised into
     * a bank that holds the water) and the water standing on it ({@link #NONE} where there is none), plus the columns
     * whose water stands above lower water beside it (a fall's lip, which the chunk marks so the water runs when it
     * loads), the lowest riverbed and highest water in it, and the hollow behind each fall ({@code hollow[2i]} to
     * {@code hollow[2i+1]}, air, or {@link #NONE}) what each hides ({@code finds}, {@code FIND_*}) and where a chest stands if
     * it is one.
     */
    public record RiverChunk(boolean any, int[] ground, int[] water, boolean[] lip, int lowestBed, int highestWater,
            int[] hollow, boolean[] treasure, byte[] finds) {
        public static int index(int x, int z) {
            return (x & 15) * 16 + (z & 15);
        }

        public boolean channel() {
            if (!any)
                return false;
            for (int w : water)
                if (w != NONE)
                    return true;
            return false;
        }
    }

    public static final RiverChunk DRY = new RiverChunk(false, null, null, null, Integer.MAX_VALUE, NONE, null, null, null);

    /** Every height asked of the terrain comes through here, so no lock: a full map is simply emptied. */
    private final ConcurrentHashMap<Long, RiverChunk> chunks = new ConcurrentHashMap<>();

    public RiverChunk chunk(int chunkX, int chunkZ) {
        long key = node(chunkX, chunkZ);
        RiverChunk known = chunks.get(key);
        if (known != null)
            return known;
        RiverChunk built = build(chunkX, chunkZ);
        if (chunks.size() > 8192)
            chunks.clear();
        chunks.putIfAbsent(key, built);
        return built;
    }

    /** A stretch of a course between two drawn points, with its half-width at each end (worked out once a chunk). */
    private record Seg(Course c, int k, double h0, double h1) {}

    private static final int MARGIN = (int) (MAX_WIDTH * 0.65) + BANK + 4;

    private List<Seg> segmentsNear(Region r, int x0, int z0, int x1, int z1) {
        List<Seg> segs = new ArrayList<>();
        for (Course c : r.courses()) {
            if (c.maxX() < x0 - MARGIN || c.minX() > x1 + MARGIN || c.maxZ() < z0 - MARGIN || c.minZ() > z1 + MARGIN)
                continue;
            for (int k = 0; k + 1 < c.px().length; k++) {
                double ax = c.px()[k], az = c.pz()[k], bx = c.px()[k + 1], bz = c.pz()[k + 1];
                if (Math.max(ax, bx) < x0 - MARGIN || Math.min(ax, bx) > x1 + MARGIN || Math.max(az, bz) < z0 - MARGIN
                        || Math.min(az, bz) > z1 + MARGIN)
                    continue;
                segs.add(new Seg(c, k, halfAt(r, c, k), halfAt(r, c, k + 1)));
            }
        }
        return segs;
    }

    /**
     * Ground and water at one column, given its natural ground: {ground, water, hollow from, hollow to, chest here,
     * find}. Built only from continuous pieces, so it cannot leave a one-block fin or pillar:
     * <ul>
     * <li>every stretch nearby carves its valley side — rising from its water's edge (its bed's edge under the sea),
     * steepening as it climbs — and the lowest wins;</li>
     * <li>no stretch's water is left perched: within a few blocks of any stretch's water the land falls away from it
     * no steeper than one block a block, and the highest such floor wins;</li>
     * <li>the column takes the lower of that and its natural ground, never more;</li>
     * <li>the nearest stretch's channel, if the column is in it: level water over a bed.</li>
     * </ul>
     * Earlier rounds picked stretches per column and propped columns up singly; their seams were the strips.
     */
    private int[] column(Region r, List<Seg> segs, int x, int z, int raw, int lowAround) {
        boolean underSea = raw < sea;
        double edge = edgeNoise.noise(x / 7.0, z / 7.0) * 1.6;
        double rough = bankNoise.noise(x / 11.0, z / 11.0) * 1.5;
        Course c = null;
        int k = 0, p = 0, w = 0;
        double nearest = Double.MAX_VALUE, d = 0, half = 1, t = 0;
        int sides = Integer.MAX_VALUE, floor = Integer.MIN_VALUE;
        for (Seg s : segs) {
            Course sc = s.c();
            int sk = s.k();
            double ax = sc.px()[sk], az = sc.pz()[sk], dx = sc.px()[sk + 1] - ax, dz = sc.pz()[sk + 1] - az;
            double len2 = dx * dx + dz * dz;
            double st = len2 == 0 ? 0 : Math.max(0, Math.min(1, ((x + 0.5 - ax) * dx + (z + 0.5 - az) * dz) / len2));
            double ex = ax + dx * st - (x + 0.5), ez = az + dz * st - (z + 0.5);
            double sd = Math.sqrt(ex * ex + ez * ez) + edge;
            int sp = st < 0.5 ? sk : sk + 1;
            int node = sc.pnode()[sp];
            int sw = width(r.flow().getOrDefault(sc.nodes().get(node), 1), node, sc.plainAt());
            // the half-width blends along the stretch rather than jumping from point to point
            double sh = s.h0() + (s.h1() - s.h0()) * st;
            double out = sd - sh;
            if (out > BANK)
                continue;
            int level = sc.plevel()[sp];
            if (out < nearest) {
                nearest = out;
                c = sc;
                k = sk;
                p = sp;
                w = sw;
                d = sd;
                half = sh;
                t = st;
            }
            if (out > 0) {
                double rise = out <= 4 ? out : 4 + (out - 4) * 1.4;
                if (out > 1.5)
                    rise += rough;
                // on land from the water's edge; under the sea from the channel's own floor, so a sunken shelf beside
                // it (a beach's shallows, where a river runs along the coast) is cut into one slope down to it
                // (beach-height ground too: by whether a column was a block above or below the sea, neighbours took
                // different slopes, and the shore came out speckled)
                boolean shore = raw <= sea && level == sea;
                int start = underSea || shore ? level - Math.max(1, Math.round(depth(sw, 0) * sc.pdepth()[sp])) : level;
                sides = Math.min(sides, start + Math.max(0, (int) Math.floor(underSea || shore ? out : rise)));
            }
            if (!underSea && level > sea && out > 0 && out <= FLOOR_REACH)
                floor = Math.max(floor, level - Math.max(0, (int) Math.floor(out - 1)));
        }
        int groundY = raw, water = NONE, hollowFrom = NONE, hollowTo = NONE, find = 0;
        boolean treasure = false;
        if (c != null) {
            int land = sides == Integer.MAX_VALUE ? raw : Math.max(sides, floor);
            groundY = Math.min(raw, land);
            if (nearest <= 0) {
                int level = c.plevel()[p];
                int deep = depth(w, Math.max(0, d) / half);
                if (k > 0 && c.plevel()[k - 1] - c.plevel()[k] >= HOLLOW_DROP && t < 0.5)
                    deep += 2; // a plunge pool at the foot of a fall
                if (underSea)
                    deep = Math.max(1, Math.round(deep * c.pdepth()[p]));
                int bed = level - deep;
                groundY = Math.min(groundY, bed);
                if (!underSea) {
                    water = level;
                    int below = c.plevel()[k + 1];
                    if (t < 0.5 && t > 0.1 && level - below >= HOLLOW_DROP && bed - 2 > below + 1) {
                        hollowFrom = below + 1;
                        hollowTo = bed - 3;
                        find = findFor(mix(seed ^ c.spring() * 31 + k));
                        treasure = find == FIND_CHEST && t <= 0.3 && Math.abs(d - edge) < 0.75;
                    }
                }
            }
        }
        if (underSea)
            return new int[] { groundY, NONE, NONE, NONE, 0, 0 };
        // a lake: the basin's columns below its water
        if (!r.lakeLevel().isEmpty()) {
            Integer lake = r.lakeLevel().get(node(Math.floorDiv(x + NODE / 2, NODE), Math.floorDiv(z + NODE / 2, NODE)));
            if (lake != null && raw < lake && (water == NONE || water < lake))
                water = lake;
        }
        if (water != NONE && groundY >= water)
            groundY = water - 1;
        if (water == NONE || hollowTo < hollowFrom) {
            hollowFrom = hollowTo = NONE;
            find = 0;
            treasure = false;
        }
        return new int[] { groundY, water, hollowFrom, hollowTo, treasure ? 1 : 0, find };
    }

    /** Blocks from a stretch's water within which the land may not fall away faster than a block a block. */
    private static final int FLOOR_REACH = 8;

    private double halfAt(Region r, Course c, int p) {
        int node = c.pnode()[p];
        int w = width(r.flow().getOrDefault(c.nodes().get(node), 1), node, c.plainAt());
        return Math.max(1.0, w / 2.0 * (1 + 0.3 * widthNoise.noise(c.px()[p] / 90.0, c.pz()[p] / 90.0)));
    }

    private RiverChunk build(int chunkX, int chunkZ) {
        int x0 = chunkX * 16, z0 = chunkZ * 16;
        Region r = region(Math.floorDiv(x0, REGION), Math.floorDiv(z0, REGION));
        List<Seg> segs = segmentsNear(r, x0 - 1, z0 - 1, x0 + 16, z0 + 16);
        if (segs.isEmpty() && r.lakeLevel().isEmpty())
            return DRY;
        // natural ground over the chunk and a border wide enough for every column's (and its neighbours') look around
        final int B = Math.max(AROUND, BREACH) + 1, SIDE = 16 + 2 * B;
        int[] raws = new int[SIDE * SIDE];
        for (int a = 0; a < SIDE; a++)
            for (int b = 0; b < SIDE; b++)
                raws[a * SIDE + b] = (int) Math.floor(terrain.ground(x0 + a - B, z0 + b - B));
        java.util.function.IntBinaryOperator rawAt = (lx, lz) -> raws[(lx + B) * SIDE + (lz + B)];
        java.util.function.IntBinaryOperator lowAt = (lx, lz) -> {
            int low = Integer.MAX_VALUE;
            for (int a = -AROUND; a <= AROUND; a++)
                for (int b = -AROUND; b <= AROUND; b++)
                    low = Math.min(low, rawAt.applyAsInt(lx + a, lz + b));
            return low;
        };
        // every column of the chunk and a one-block ring around it
        final int R = 18;
        int[] g18 = new int[R * R], w18 = new int[R * R], hollow = new int[512];
        java.util.Arrays.fill(hollow, NONE);
        boolean[] treasure = new boolean[256];
        byte[] finds = new byte[256];
        boolean any = false;
        for (int x = -1; x <= 16; x++)
            for (int z = -1; z <= 16; z++) {
                int raw = rawAt.applyAsInt(x, z);
                int[] col = column(r, segs, x0 + x, z0 + z, raw, lowAt.applyAsInt(x, z));
                g18[(x + 1) * R + z + 1] = col[0];
                w18[(x + 1) * R + z + 1] = col[1];
                if (x >= 0 && x < 16 && z >= 0 && z < 16) {
                    any |= col[1] != NONE || col[0] != raw;
                    hollow[(x * 16 + z) * 2] = col[2];
                    hollow[(x * 16 + z) * 2 + 1] = col[3];
                    treasure[x * 16 + z] = col[4] != 0;
                    finds[x * 16 + z] = (byte) col[5];
                }
            }
        if (!any)
            return DRY;
        // containment: a dry column is never lower than the water beside it. Next to a fall, the pool below cut
        // its bank down beside the pool above, and the upper water ran out into the cut. The water never stands
        // above the natural ground around it, so this only gives back ground the cutting took — never a wall.
        int[] groundY = new int[256], water = new int[256];
        int[][] around = { { 1, 0 }, { -1, 0 }, { 0, 1 }, { 0, -1 } };
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int g = g18[(x + 1) * R + z + 1], w = w18[(x + 1) * R + z + 1];
                // (not under the sea: the sea's own water stands there, and raising it back to its floor left a
                // ridge wherever a channel's edge ran below sea level)
                if (w == NONE && rawAt.applyAsInt(x, z) >= sea)
                    for (int[] o : around) {
                        int nw = w18[(x + 1 + o[0]) * R + z + 1 + o[1]];
                        if (nw != NONE && g < nw)
                            g = Math.min(nw, rawAt.applyAsInt(x, z));
                    }
                groundY[x * 16 + z] = g;
                water[x * 16 + z] = w;
            }
        // a hollow stays below every riverbed beside it, or the water beside it would pour in
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int i = (x * 16 + z) * 2;
                if (hollow[i] == NONE)
                    continue;
                for (int[] o : around) {
                    int at = (x + 1 + o[0]) * R + z + 1 + o[1];
                    if (w18[at] != NONE)
                        hollow[i + 1] = Math.min(hollow[i + 1], g18[at] - 1);
                }
                if (hollow[i + 1] < hollow[i]) {
                    hollow[i] = hollow[i + 1] = NONE;
                    finds[x * 16 + z] = 0;
                    treasure[x * 16 + z] = false;
                }
            }
        // a bar of low beach between a river at sea level and the open water it is meeting is breached, so they
        // join (the course can run along a shore and leave one); a few passes for a bar a few blocks wide
        for (int pass = 0; pass < BREACH; pass++) {
            boolean opened = false;
            int[] before = water.clone(); // a block a pass: in place, one pass raced right across the chunk
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++) {
                    int i = x * 16 + z;
                    int raw = rawAt.applyAsInt(x, z);
                    // dry beach only: under the sea the sea's own water is already there, and digging it out to
                    // the river's depth cut a chunk-square hole in the shallows with a straight ridge at its edge
                    if (before[i] != NONE || raw > sea + 1 || raw < sea)
                        continue;
                    boolean byRiver = false;
                    int bed = sea - 1;
                    for (int[] o : around) {
                        int nx = x + o[0], nz = z + o[1];
                        boolean inside = nx >= 0 && nx < 16 && nz >= 0 && nz < 16;
                        int nw = inside ? before[nx * 16 + nz] : w18[(nx + 1) * R + nz + 1];
                        if (nw == sea) {
                            byRiver = true;
                            bed = Math.min(bed, inside ? groundY[nx * 16 + nz] : g18[(nx + 1) * R + nz + 1]);
                        }
                    }
                    if (!byRiver)
                        continue;
                    boolean byOpenWater = false;
                    for (int a = -BREACH; a <= BREACH && !byOpenWater; a++)
                        for (int b = -BREACH; b <= BREACH && !byOpenWater; b++)
                            byOpenWater = rawAt.applyAsInt(x + a, z + b) < sea;
                    if (byOpenWater) {
                        water[i] = sea;
                        groundY[i] = Math.min(groundY[i], bed); // as deep as the river beside it, no sill
                        opened = true;
                    }
                }
            if (!opened)
                break;
        }
        // lips: water standing above lower water beside it — a fall or a step down a cascade
        boolean[] lip = new boolean[256];
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int w = water[x * 16 + z];
                if (w == NONE || w <= sea)
                    continue;
                for (int[] o : around) {
                    int nw = w18[(x + 1 + o[0]) * R + z + 1 + o[1]];
                    if (nw != NONE && nw < w) {
                        lip[x * 16 + z] = true;
                        break;
                    }
                }
            }
        int lowestBed = Integer.MAX_VALUE, highestWater = NONE;
        for (int i = 0; i < 256; i++)
            if (water[i] != NONE) {
                lowestBed = Math.min(lowestBed, groundY[i]);
                highestWater = Math.max(highestWater, water[i]);
            }
        return new RiverChunk(true, groundY, water, lip, lowestBed, highestWater, hollow, treasure, finds);
    }
}
