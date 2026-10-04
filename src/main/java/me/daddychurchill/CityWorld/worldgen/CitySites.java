package me.daddychurchill.CityWorld.worldgen;

import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.compat.noise.SimplexNoiseGenerator;
import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;

/**
 * Where the cities are in a vanilla-terrain world ({@code "terrain": "vanilla"}): a few, far apart, each on its own
 * patch of levelled ground, with vanilla's world everywhere else.
 *
 * <p><b>The idea.</b> The world is cut into square cells ({@code cities.spacing} blocks across) and each cell may hold one
 * city, the way a structure set's spread placement gives each cell one village. A city is a rough disc two to four
 * districts (platmaps, 160 blocks) across, standing on ONE level: the median of vanilla's ground under it, kept
 * within {@code cities.levelRange} of CityWorld's own street level. Ground inside the disc is brought to that level, and a
 * ring around it eases back into vanilla's terrain with the structure pad's taper arithmetic run the other way
 * (the land bends to the city, where the pad bends the plan to a structure).
 *
 * <p><b>Everything here is a pure function of the seed and vanilla's terrain.</b> A site is decided from vanilla's
 * own base heights (which the city never changes: {@code getBaseHeight} keeps answering raw terrain), so any thread
 * may ask about any column at any time and get the same answer; the memo below only saves the work.
 *
 * <p><b>The planner never sees a height here.</b> It is told "street level" for every chunk of the disc and "not
 * street level" for everything else ({@code ShapeProvider_Vanilla}); the real blocks are moved by the chunk
 * generator from the chunk vanilla filled. The city is planned and drawn at CityWorld's usual street level and
 * lifted to the site's own level at the block seam ({@code InitialBlocks.yShift}, {@code ShiftedRegion}).
 */
public final class CitySites {

    /** A platmap, in blocks: the unit a city's size is counted in. */
    private static final int DISTRICT = 160;
    /** Flat ground kept at city level beyond the disc's edge before the taper begins. */
    private static final int APRON = 6;
    /** Blocks of run per block of climb, the pad's own figure; and the taper's floor and cap. */
    private static final double SLOPE = 2.5;
    private static final int TAPER_MIN = 16, TAPER_MAX = 120;
    /** How far the disc's edge wanders in and out, as a share of its radius, and over what distance. */
    private static final double WOBBLE = 0.12;
    private static final double WOBBLE_SCALE = 1.0 / 72.0;
    /** Candidate positions tried per cell before the cell goes without a city. */
    private static final int TRIES = 3;
    /** A column counts as "near" the level when its ground is within this many blocks of it. */
    private static final int NEAR = 16;

    /** Vanilla's ground at a column: the y of the top solid block, water not counted. */
    public interface Heights {
        int top(int blockX, int blockZ);
    }

    /** Whether vanilla's biome at a column is a river (the biome, which is cheap, not the water, which is not). */
    public interface Rivers {
        boolean river(int blockX, int blockZ);
    }

    /**
     * One city: the centre and radius of its disc, and the y of its ground's top block.
     *
     * @param districts how many platmaps across it was rolled to be
     * @param water     share of sampled columns under the sea, for the survey
     * @param near      share of sampled columns within {@link #NEAR} of the level
     */
    public record Site(int centreX, int centreZ, int radius, int level, int districts, double water, double near) {

        /** Blocks from the centre past which this city changes nothing at all. */
        public int reach() {
            return (int) Math.ceil(radius * (1.0 + WOBBLE)) + APRON + TAPER_MAX;
        }
    }

    /** What a cell's candidates came to, kept for the survey: the site, or why each try was refused. */
    public record Verdict(Site site, String refusals) {
    }

    private final long seed;
    private final int streetLevel, seaLevel;
    private final Heights heights;
    private final Rivers rivers;
    /**
     * The world's dials (settings "cities"): one city per {@code spacing}-block cell; {@code districts} give or
     * take {@code districtsVariance} platmaps across; streets up to {@code levelRange} above street level; a
     * candidate refused for water over {@code maxWater} of its columns (a coastal city fills its shore in, as
     * CityWorld always has; seed 8675309 near the origin: 11 of 49 cells at 15%, 20 at 35%) or under
     * {@code minNear} of them within {@link #NEAR} of its level. Baked into the world: a change moves every city.
     */
    private final CityWorldSettingsData.Cities config;
    private final SimplexNoiseGenerator wobble;
    private final ConcurrentHashMap<Long, Verdict> cells = new ConcurrentHashMap<>();

    /**
     * @param streetLevel CityWorld's own street level (the top block of buildable ground), the lowest a city stands
     * @param seaLevel    vanilla's sea level (the first y that is not water)
     */
    public CitySites(long seed, int streetLevel, int seaLevel, Heights heights, Rivers rivers,
            CityWorldSettingsData.Cities config) {
        this.seed = seed;
        this.config = config;
        this.rivers = rivers;
        this.streetLevel = streetLevel;
        this.seaLevel = seaLevel;
        this.heights = heights;
        this.wobble = new SimplexNoiseGenerator(seed + 7411);
    }

    /** Vanilla's sea level: the first y that is not water. */
    public int seaLevel() {
        return seaLevel;
    }

    /** Whether vanilla's biome at a column is a river. */
    public boolean isRiverColumn(int blockX, int blockZ) {
        return rivers.river(blockX, blockZ);
    }

    /**
     * A chunk the river runs through: one of its sixteen biome cells (4x4 columns each) is river and under water at
     * its centre — or is river and borders a cell of the next chunk that is. Nothing is built on it but a bridge, so
     * the river runs through the city as vanilla made it, banks and all.
     *
     * <p>Two failures shaped it (owner, 2026-10-04). By river biome under most of five columns, a river crossing a
     * chunk's corner was filled in there, and two filled corners closed it off ("both diagonals are filled so you
     * can't get through"). By river biome anywhere, the city lost half its ground to dry banks the biome climbs. Wet
     * cells, and the cells beside a wet cell across a chunk edge, keep every chunk the water passes through.
     * Memoised: the planner asks per column, and a wet test is a vanilla height query.
     */
    public boolean isRiverChunk(int chunkX, int chunkZ) {
        if (isWetChunk(chunkX, chunkZ))
            return true;
        // A river passing diagonally between two dry chunks would be cut at the shared corner (water cannot pass a
        // corner point; owner, 2026-10-04: "one place where there's no way through"). For each 2x2 block this chunk
        // is in: wet on one diagonal and dry on the other, the dry chunk on the block's top row is kept for the river.
        for (int ox = -1; ox <= 0; ox++)
            for (int oz = -1; oz <= 0; oz++) {
                int x0 = chunkX + ox, z0 = chunkZ + oz;
                boolean a = isWetChunk(x0, z0), b = isWetChunk(x0 + 1, z0), c = isWetChunk(x0, z0 + 1),
                        d = isWetChunk(x0 + 1, z0 + 1);
                if (a && d && !b && !c && chunkX == x0 + 1 && chunkZ == z0)
                    return true; // the main diagonal: keep the top-right chunk
                if (b && c && !a && !d && chunkX == x0 && chunkZ == z0)
                    return true; // the other diagonal: keep the top-left chunk
            }
        return false;
    }

    /** The river's own chunks, before the corner rule of {@link #isRiverChunk}. */
    private boolean isWetChunk(int chunkX, int chunkZ) {
        long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
        Boolean known = riverChunks.get(key);
        if (known != null)
            return known;
        boolean river = false;
        int x0 = chunkX * 16 + 2, z0 = chunkZ * 16 + 2;
        for (int i = 0; i < 4 && !river; i++)
            for (int j = 0; j < 4 && !river; j++) {
                int x = x0 + i * 4, z = z0 + j * 4;
                if (!rivers.river(x, z))
                    continue;
                if (wet(x, z)) {
                    river = true;
                    break;
                }
                // a border cell: the river may arrive from the next chunk through it
                for (int di = -1; di <= 1 && !river; di++)
                    for (int dj = -1; dj <= 1 && !river; dj++) {
                        int ni = i + di, nj = j + dj;
                        if (ni >= 0 && ni < 4 && nj >= 0 && nj < 4)
                            continue; // inside this chunk: judged on its own
                        int nx = x0 + ni * 4, nz = z0 + nj * 4;
                        river = rivers.river(nx, nz) && wet(nx, nz);
                    }
            }
        if (riverChunks.size() > 200_000)
            riverChunks.clear();
        riverChunks.put(key, river);
        return river;
    }

    /** Under the river's water: vanilla's ground below its surface. */
    private boolean wet(int blockX, int blockZ) {
        return heights.top(blockX, blockZ) < seaLevel - 1;
    }

    /** How much of a chunk is river: biome cells of sixteen, for picking which way a quay faces. */
    public int riverCells(int chunkX, int chunkZ) {
        int count = 0, x0 = chunkX * 16, z0 = chunkZ * 16;
        for (int i = 0; i < 4; i++)
            for (int j = 0; j < 4; j++)
                if (rivers.river(x0 + i * 5, z0 + j * 5))
                    count++;
        return count;
    }

    private final ConcurrentHashMap<Long, Boolean> riverChunks = new ConcurrentHashMap<>();

    /** A river chunk inside the city itself: the planner lets nothing but a bridge stand on it. */
    public boolean isChannelChunk(int chunkX, int chunkZ) {
        return isCityChunk(chunkX, chunkZ) && isRiverChunk(chunkX, chunkZ);
    }

    /** Blocks between one city's cell and the next ({@code cities.spacing}). */
    public int cell() {
        return config.spacing();
    }

    /** How far a site's streets stand above the level the city is planned and drawn at. */
    public int shift(Site site) {
        return site.level() - streetLevel;
    }

    /** The verdict for the cell holding this column (computed on first ask). */
    public Verdict verdictAt(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, config.spacing()), cellZ = Math.floorDiv(blockZ, config.spacing());
        long key = ((long) cellX << 32) ^ (cellZ & 0xffffffffL);
        Verdict known = cells.get(key);
        if (known != null)
            return known;
        // Outside any map lock: judging a site asks vanilla for heights, which is slow, and the answer is the
        // same whichever thread works it out.
        Verdict judged = judge(cellX, cellZ);
        Verdict raced = cells.putIfAbsent(key, judged);
        return raced != null ? raced : judged;
    }

    /**
     * The verdict for this column's cell only if it has already been judged, else null — for the F3 line, which
     * runs on the render thread and must not spend half a second asking vanilla for heights.
     */
    public Verdict knownVerdictAt(int blockX, int blockZ) {
        int cellX = Math.floorDiv(blockX, config.spacing()), cellZ = Math.floorDiv(blockZ, config.spacing());
        return cells.get(((long) cellX << 32) ^ (cellZ & 0xffffffffL));
    }

    /** The city whose cell holds this column, or null. A city never reaches outside its own cell. */
    public Site siteAt(int blockX, int blockZ) {
        return verdictAt(blockX, blockZ).site();
    }

    /**
     * The nearest city to a column, looking {@code rings} cells out in every direction, or null. Slow the first
     * time a cell is asked about (each is judged from vanilla's heights); call it off the server thread.
     */
    public Site nearest(int blockX, int blockZ, int rings) {
        Site best = null;
        double bestDistance = Double.MAX_VALUE;
        int cellX = Math.floorDiv(blockX, config.spacing()), cellZ = Math.floorDiv(blockZ, config.spacing());
        for (int ring = 0; ring <= rings; ring++) {
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                        continue;
                    Site site = siteAt((cellX + dx) * config.spacing(), (cellZ + dz) * config.spacing());
                    if (site == null)
                        continue;
                    double distance = Math.hypot(site.centreX() - blockX, site.centreZ() - blockZ);
                    if (distance < bestDistance) {
                        bestDistance = distance;
                        best = site;
                    }
                }
            // a nearer one can still lie one ring further out, but not two
            if (best != null && bestDistance <= ring * (double) config.spacing())
                break;
        }
        return best;
    }

    /**
     * Whether any city's disc or ring reaches into the square of {@code size} blocks at {@code (x0, z0)}. The
     * square may straddle cells, so each distinct cell under its corners is asked.
     */
    public boolean touches(int x0, int z0, int size) {
        int x1 = x0 + size - 1, z1 = z0 + size - 1;
        int[][] corners = { { x0, z0 }, { x1, z0 }, { x0, z1 }, { x1, z1 } };
        long seenCells = Long.MIN_VALUE;
        for (int[] corner : corners) {
            long cell = ((long) Math.floorDiv(corner[0], config.spacing()) << 32) ^ (Math.floorDiv(corner[1], config.spacing()) & 0xffffffffL);
            if (cell == seenCells)
                continue;
            seenCells = cell;
            Site site = siteAt(corner[0], corner[1]);
            if (site == null)
                continue;
            // distance from the centre to the nearest point of the square
            double dx = Math.max(0, Math.max(x0 - site.centreX(), site.centreX() - x1));
            double dz = Math.max(0, Math.max(z0 - site.centreZ(), site.centreZ() - z1));
            if (dx * dx + dz * dz <= (double) site.reach() * site.reach())
                return true;
        }
        return false;
    }

    /** The city that changes anything in this chunk (its disc or its blend ring), or null. */
    public Site influencing(int chunkX, int chunkZ) {
        int x = chunkX * 16 + 8, z = chunkZ * 16 + 8;
        Site site = siteAt(x, z);
        if (site == null)
            return null;
        long dx = x - site.centreX(), dz = z - site.centreZ();
        long reach = site.reach() + 12; // a chunk's centre is at most 12 blocks from its far corner
        return dx * dx + dz * dz <= reach * reach ? site : null;
    }

    /**
     * Blocks from this column to the disc's edge: negative inside the city, positive outside. The edge wanders
     * with a slow noise so a city is not a compass-drawn circle.
     */
    public double edgeDistance(Site site, int blockX, int blockZ) {
        double dx = blockX - site.centreX(), dz = blockZ - site.centreZ();
        double edge = site.radius() * (1.0 + WOBBLE * wobble.noise(blockX * WOBBLE_SCALE, blockZ * WOBBLE_SCALE));
        return Math.sqrt(dx * dx + dz * dz) - edge;
    }

    /**
     * Whether a whole chunk is city ground: its centre and four corners inside the disc. The same five columns
     * {@code HeightInfo} samples, so "buildable" and "levelled" can never disagree about a chunk.
     */
    public boolean isCityChunk(int chunkX, int chunkZ) {
        int x = chunkX * 16, z = chunkZ * 16;
        Site site = siteAt(x + 8, z + 8);
        if (site == null)
            return false;
        long dx = x + 8 - site.centreX(), dz = z + 8 - site.centreZ();
        long outer = (long) (site.radius() * (1.0 + WOBBLE)) + 12;
        if (dx * dx + dz * dz > outer * outer)
            return false;
        return edgeDistance(site, x + 8, z + 8) <= 0 && edgeDistance(site, x, z) <= 0
                && edgeDistance(site, x + 15, z) <= 0 && edgeDistance(site, x, z + 15) <= 0
                && edgeDistance(site, x + 15, z + 15) <= 0;
    }

    /**
     * The y the ground's top block is brought to at a column whose vanilla top block is {@code top}: the city's
     * level across the disc and its apron, vanilla's own height past the taper, and an eased blend between.
     * The taper is as long as the rise is tall ({@link #SLOPE}), so a hill beside a city becomes a slope and not
     * a wall. Column-local, so it cannot leave a seam between chunks.
     */
    public int groundAt(Site site, int blockX, int blockZ, int top) {
        double d = edgeDistance(site, blockX, blockZ) - APRON;
        if (d <= 0)
            return site.level();
        int rise = top - site.level();
        if (rise == 0)
            return top;
        double taper = Math.max(TAPER_MIN, Math.min(TAPER_MAX, Math.abs(rise) * SLOPE));
        double t = d / taper;
        if (t >= 1.0)
            return top;
        double eased = t * t * (3.0 - 2.0 * t);
        return site.level() + (int) Math.round(rise * eased);
    }

    private Verdict judge(int cellX, int cellZ) {
        java.util.Random roll = new java.util.Random(seed * 31L + cellX * 341873128712L + cellZ * 132897987541L);
        StringBuilder refusals = new StringBuilder();
        for (int attempt = 0; attempt < TRIES; attempt++) {
            // districts across: the setting, give or take its variance — the middle twice as likely as either end
            // (owner: "3x3 districts, maybe +/- 1")
            int variance = config.districtsVariance();
            int span = 2 * variance + 1, pick = roll.nextInt(span + 1);
            int districts = Math.max(1, config.districts() + (pick == span ? 0 : pick - variance));
            int radius = districts * DISTRICT / 2;
            // Far enough inside the cell that the disc, its wobble and its blend ring all stay in it: then a
            // column belongs to at most one city, and its cell is the only one that need be asked.
            int margin = (int) Math.ceil(radius * (1.0 + WOBBLE)) + APRON + TAPER_MAX + 16;
            int room = config.spacing() - 2 * margin;
            if (room <= 0) {
                refusals.append("cell too small for ").append(districts).append(" districts; ");
                continue;
            }
            int centreX = cellX * config.spacing() + margin + roll.nextInt(room);
            int centreZ = cellZ * config.spacing() + margin + roll.nextInt(room);
            // on a chunk corner, so the disc is symmetric about the chunk grid the city is planned on
            centreX = Math.floorDiv(centreX, 16) * 16;
            centreZ = Math.floorDiv(centreZ, 16) * 16;

            // Vanilla's ground under the disc, on a grid: enough columns to judge by, few enough to afford
            // (vanilla answers a column in a millisecond or so, and this is asked once per cell).
            int step = Math.max(16, radius / 4);
            java.util.List<Integer> tops = new java.util.ArrayList<>();
            int wet = 0;
            for (int dx = -radius; dx <= radius; dx += step)
                for (int dz = -radius; dz <= radius; dz += step) {
                    if ((long) dx * dx + (long) dz * dz > (long) radius * radius)
                        continue;
                    int top = heights.top(centreX + dx, centreZ + dz);
                    tops.add(top);
                    if (top < seaLevel - 1)
                        wet++;
                }
            double water = wet / (double) tops.size();
            java.util.List<Integer> sorted = new java.util.ArrayList<>(tops);
            java.util.Collections.sort(sorted);
            int median = sorted.get(sorted.size() / 2);
            String where = "(" + centreX + ", " + centreZ + ") x" + districts;
            if (water > config.maxWater()) {
                refusals.append(where).append(" water ").append(Math.round(water * 100)).append("%; ");
                continue;
            }
            if (median > streetLevel + config.levelRange()) {
                refusals.append(where).append(" too high (y ").append(median).append("); ");
                continue;
            }
            int level = Math.max(streetLevel, median);
            int close = 0;
            for (int top : tops)
                if (Math.abs(top - level) <= NEAR)
                    close++;
            double near = close / (double) tops.size();
            if (near < config.minNear()) {
                refusals.append(where).append(" too rough (").append(Math.round(near * 100)).append("% near y ")
                        .append(level).append("); ");
                continue;
            }
            return new Verdict(new Site(centreX, centreZ, radius, level, districts, water, near), refusals.toString());
        }
        return new Verdict(null, refusals.toString());
    }
}
