package me.daddychurchill.CityWorld.Support;

import net.minecraft.world.level.block.state.properties.RailShape;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Plats.Urban.SubwayStationLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Environment;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The subway (a commenter's idea, via the owner, 2026-09-26): a station under every urban district, and
 * twin-track tunnels from each station to its neighbours' stations. Two things make it plannable without
 * the planner ever looking past its own platmap:
 *
 * <ol>
 * <li><b>The station is a lot.</b> {@link #placeStation} claims one chunk of an urban platmap (a
 *     {@link SubwayStationLot}: the ticket hall at street level, the platforms below) before the
 *     buildings fill in — beside a road, inside the platmap's interior (plat 1..8 both ways, so the jog
 *     column below is never the station's own). Where it went is recorded on the platmap.</li>
 * <li><b>Every tunnel chunk is a pure function of two stations.</b> The link from a station {@code A}
 *     in platmap {@code P} to the station {@code B} in the platmap east of it runs east along A's row to
 *     P's last column, jogs north or south along that column to B's row, and runs east into B. A link to
 *     the platmap south runs the same way on the other axis. So a chunk asks: where is my platmap's
 *     station, and where are the four neighbours' ({@link #at}) — and nothing else. Planning a platmap
 *     never asks its neighbours (that would be planning inside planning); drawing a chunk may, and does.</li>
 * </ol>
 *
 * <p><b>Two levels, so lines never junction.</b> East-west tunnels run on the upper level, north-south on
 * the lower (the interchange is the station itself: stairs between its two halls), the way a real metro
 * stacks its lines. A chunk can carry both. Vanilla has no crossing rail, which is why the rule is a level
 * per axis rather than a junction piece.
 *
 * <p><b>What is drawn</b> (all at decoration time, sealed boxes cut into whatever is there — walls all
 * round, so a cave or mine that crosses is closed off, and the mines are kept out of the band by
 * {@link #blocksMines}): a straight is a 6-wide bed with the two tracks, a bend curves both tracks
 * (outer-with-outer, so they never cross), the station hall is the bed flanked by platforms with the
 * chunk on either side along the line widened into platform too, and the tunnels are lit, ringed in
 * brick every four blocks, and boosted with powered rails so a cart actually runs. An APOCALYPSE world
 * ruins it: dark lamps, missing rails, standing water, sewer-bag spawners in wall niches.
 */
public final class Subway {

    private Subway() {
    }

    /** Floor of the east-west level below street level: the rise to the ticket hall is 24, six zigzag pairs. */
    public static final int EW_DEPTH = 24;
    /** Floor of the north-south level: eight below the other, two pairs of stairs, one block of rock between. */
    public static final int NS_DEPTH = 32;
    /** Air above a floor slab; the ceiling slab sits at floor + HEIGHT + 1. */
    public static final int HEIGHT = 5;

    public static final int N = 1, S = 2, E = 4, W = 8;

    public static final Material SHELL = Material.SMOOTH_STONE;
    public static final Material RING = Material.STONE_BRICKS;
    public static final Material BED = Material.GRAVEL;
    public static final Material PLATFORM = Material.POLISHED_ANDESITE;
    public static final Material EDGE = Material.YELLOW_CONCRETE;
    public static final Material HALL_WALL = Material.WHITE_CONCRETE;
    public static final Material LIGHT = Material.SEA_LANTERN;
    public static final Material DEAD_LIGHT = Material.COPPER_BULB;
    public static final Material[] LINE_COLOURS = { Material.LIGHT_BLUE_CONCRETE, Material.RED_CONCRETE,
            Material.LIME_CONCRETE, Material.ORANGE_CONCRETE, Material.PURPLE_CONCRETE, Material.YELLOW_CONCRETE };

    // ---- what is where -------------------------------------------------------------------------

    /** Whether this world plans a subway at all. */
    public static boolean planned(CityWorldGenerator generator) {
        return generator.getSettings().includeSubways && generator.getSettings().includeRoads
                && generator.getSettings().includeBuildings && generator.shapeProvider.supportsSubways();
    }

    /** Whether this lot's chunk may carry subway blocks: a planned world, the overworld, inside the city. */
    public static boolean drawsIn(CityWorldGenerator generator, PlatLot lot) {
        return planned(generator) && generator.worldEnvironment == Environment.NORMAL
                && generator.getSettings().inCityRange(lot.getChunkX(), lot.getChunkZ());
    }

    public static int ewFloor(CityWorldGenerator generator) {
        return generator.streetLevel - EW_DEPTH;
    }

    public static int nsFloor(CityWorldGenerator generator) {
        return generator.streetLevel - NS_DEPTH;
    }

    /**
     * Whether a mine level starting at {@code mineY} would cut the subway band. Asked for every city chunk
     * rather than only the chunks with a piece, because the answer is needed while the chunk's terrain is
     * generated — before it may look at its neighbours. A level reaches from ten below its base (its lift
     * drops into the level under it) to nine above (its junction ceiling), and the first probe found a
     * lift from the level ABOVE the band dropping straight through the upper hall.
     */
    public static boolean blocksMines(CityWorldGenerator generator, PlatLot lot, int mineY) {
        return drawsIn(generator, lot) && mineY + 9 >= nsFloor(generator) && mineY - 10 <= ewFloor(generator) + HEIGHT + 1;
    }

    /**
     * Claim the station chunk of an urban platmap: inside the interior (1..8), empty, beside a road; the
     * first such cell from a seed-stable start. Runs before the schematics and the backfill, so it is
     * never refused for being built on — only for a structure reservation, in which case the next
     * candidate is tried. Its own dice, so the platmap's other rolls are the same as before it existed.
     */
    public static void placeStation(CityWorldGenerator generator, PlatMap platmap) {
        if (!planned(generator) || platmap.subwayStation != null)
            return;
        java.util.List<int[]> candidates = new java.util.ArrayList<>();
        for (int x = 1; x <= PlatMap.Width - 2; x++)
            for (int z = 1; z <= PlatMap.Width - 2; z++)
                // never ON a road line (the grid's rows/columns 2 and 7): the station's lines run along its
                // row and column, and a roundabout's centre digs thirty blocks down through the band
                if (x != RoadLot.PlatMapRoadInset - 1 && x != PlatMap.Width - RoadLot.PlatMapRoadInset
                        && z != RoadLot.PlatMapRoadInset - 1 && z != PlatMap.Width - RoadLot.PlatMapRoadInset
                        && platmap.isEmptyLot(x, z) && (platmap.isExistingRoad(x - 1, z) || platmap.isExistingRoad(x + 1, z)
                        || platmap.isExistingRoad(x, z - 1) || platmap.isExistingRoad(x, z + 1)))
                    candidates.add(new int[] { x, z });
        if (candidates.isEmpty())
            return;
        Odds odds = new Odds(platmap.originX * 341873128L + platmap.originZ * 132897987L + 7L);
        int start = odds.getRandomInt(candidates.size());
        for (int i = 0; i < candidates.size(); i++) {
            int[] c = candidates.get((start + i) % candidates.size());
            SubwayStationLot lot = new SubwayStationLot(platmap, platmap.originX + c[0], platmap.originZ + c[1],
                    odds.getRandomInt(LINE_COLOURS.length));
            if (platmap.setLot(c[0], c[1], lot)) {
                platmap.subwayStation = c;
                return;
            }
        }
    }

    /** The station of the platmap holding chunk {@code (chunkX, chunkZ)}, as absolute chunk coords, or null. */
    public static int[] stationNear(CityWorldGenerator generator, int chunkX, int chunkZ) {
        return stationOf(generator.getPlatMap(chunkX, chunkZ));
    }

    /** The platmap's station as absolute chunk coords — if the lot there still IS the station (a later
     *  pass replacing it would otherwise leave halls with no stairs down to them). */
    private static int[] stationOf(PlatMap p) {
        if (p.subwayStation == null || !(p.getLot(p.subwayStation[0], p.subwayStation[1]) instanceof SubwayStationLot))
            return null;
        return new int[] { p.originX + p.subwayStation[0], p.originZ + p.subwayStation[1] };
    }

    /**
     * What one chunk carries. {@code ewMask}/{@code nsMask} are the open sides (N/S/E/W bits) of the
     * piece on each level, 0 for none. A station always has an east-west hall (both ends closed if it has
     * no link that way); {@code ewWide}/{@code nsWide} say which of that hall's two ends open into a
     * widened platform chunk rather than a plain tunnel; {@code platform} marks that widened chunk.
     */
    public record Piece(int ewMask, int nsMask, boolean station, int ewWide, int nsWide, boolean ewPlatform,
            boolean nsPlatform) {

        public static final Piece NONE = new Piece(0, 0, false, 0, 0, false, false);

        public boolean any() {
            return station || ewMask != 0 || nsMask != 0;
        }
    }

    public static Piece at(CityWorldGenerator generator, int chunkX, int chunkZ) {
        if (!planned(generator))
            return Piece.NONE;
        PlatMap p = generator.getPlatMap(chunkX, chunkZ);
        int[] own = stationOf(p);
        if (own == null)
            return Piece.NONE;
        int p0x = p.originX, p0z = p.originZ, last = PlatMap.Width - 1;
        int ax = own[0], az = own[1];
        int[] east = stationNear(generator, p0x + PlatMap.Width, p0z);
        int[] west = stationNear(generator, p0x - PlatMap.Width, p0z);
        int[] south = stationNear(generator, p0x, p0z + PlatMap.Width);
        int[] north = stationNear(generator, p0x, p0z - PlatMap.Width);

        int ew = 0;
        if (chunkZ == az) {
            if (chunkX < ax) {
                if (west != null)
                    ew = W | E;
            } else if (chunkX == ax) {
                ew = (west != null ? W : 0) | (east != null ? E : 0);
            } else if (east != null) {
                if (chunkX < p0x + last)
                    ew = W | E;
                else
                    ew = east[1] == az ? W | E : W | (east[1] > az ? S : N);
            }
        } else if (east != null && chunkX == p0x + last && east[1] != az && between(chunkZ, az, east[1])) {
            ew = chunkZ == east[1] ? (az < east[1] ? N : S) | E : N | S;
        }

        int ns = 0;
        if (chunkX == ax) {
            if (chunkZ < az) {
                if (north != null)
                    ns = N | S;
            } else if (chunkZ == az) {
                ns = (north != null ? N : 0) | (south != null ? S : 0);
            } else if (south != null) {
                if (chunkZ < p0z + last)
                    ns = N | S;
                else
                    ns = south[0] == ax ? N | S : N | (south[0] > ax ? E : W);
            }
        } else if (south != null && chunkZ == p0z + last && south[0] != ax && between(chunkX, ax, south[0])) {
            ns = chunkX == south[0] ? (ax < south[0] ? W : E) | S : W | E;
        }

        boolean station = chunkX == ax && chunkZ == az;
        // the chunk either side of the station along a line is a platform if it is a plain straight there
        boolean ewPlatform = !station && chunkZ == az && Math.abs(chunkX - ax) == 1 && ew == (W | E);
        boolean nsPlatform = !station && chunkX == ax && Math.abs(chunkZ - az) == 1 && ns == (N | S);
        int ewWide = 0, nsWide = 0;
        if (station) {
            if ((ew & W) != 0 && straightEW(generator, p, ax - 1, az))
                ewWide |= W;
            if ((ew & E) != 0 && straightEW(generator, p, ax + 1, az))
                ewWide |= E;
            if ((ns & N) != 0 && straightNS(generator, p, ax, az - 1))
                nsWide |= N;
            if ((ns & S) != 0 && straightNS(generator, p, ax, az + 1))
                nsWide |= S;
        }
        return new Piece(ew, ns, station, ewWide, nsWide, ewPlatform, nsPlatform);
    }

    /** {@code v} strictly past {@code from} on the way to {@code to}, up to and including {@code to}. */
    private static boolean between(int v, int from, int to) {
        return to > from ? v > from && v <= to : v < from && v >= to;
    }

    /** Whether the east-west piece at a station's neighbour is a plain straight (its jog, if any, is further on). */
    private static boolean straightEW(CityWorldGenerator generator, PlatMap p, int chunkX, int chunkZ) {
        return chunkX >= p.originX && chunkX < p.originX + PlatMap.Width - 1 // never the jog column
                || chunkX == p.originX + PlatMap.Width - 1 && stationNear(generator, chunkX + 1, chunkZ) != null
                        && stationNear(generator, chunkX + 1, chunkZ)[1] == chunkZ;
    }

    private static boolean straightNS(CityWorldGenerator generator, PlatMap p, int chunkX, int chunkZ) {
        return chunkZ >= p.originZ && chunkZ < p.originZ + PlatMap.Width - 1
                || chunkZ == p.originZ + PlatMap.Width - 1 && stationNear(generator, chunkX, chunkZ + 1) != null
                        && stationNear(generator, chunkX, chunkZ + 1)[0] == chunkX;
    }

    // ---- drawing -------------------------------------------------------------------------------

    /**
     * Everything below street level for this chunk: called from the end of every city lot's decoration
     * (the station lot calls it itself, first, since its stairs cut through what this draws).
     */
    public static void generate(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk, Odds odds) {
        Piece piece = at(generator, chunk.sectionX, chunk.sectionZ);
        if (!piece.any())
            return;
        boolean ruined = generator.isApocalypseStyle();
        int lineColour = Math.floorMod(chunk.sectionX / PlatMap.Width * 31 + chunk.sectionZ / PlatMap.Width * 17, LINE_COLOURS.length);
        Material stripe = lot instanceof SubwayStationLot s ? s.lineColour() : LINE_COLOURS[lineColour];
        if (piece.station()) {
            hall(generator, chunk, odds, ewFloor(generator), true, piece.ewMask(), piece.ewWide(), stripe, ruined);
            if (piece.nsMask() != 0)
                hall(generator, chunk, odds, nsFloor(generator), false, piece.nsMask(), piece.nsWide(), stripe, ruined);
            return;
        }
        if (piece.ewMask() != 0) {
            if (piece.ewPlatform())
                hall(generator, chunk, odds, ewFloor(generator), true, piece.ewMask(),
                        chunk.sectionX < stationNear(generator, chunk.sectionX, chunk.sectionZ)[0] ? E : W, stripe, ruined);
            else
                tunnel(generator, chunk, odds, ewFloor(generator), piece.ewMask(), ruined);
        }
        if (piece.nsMask() != 0) {
            if (piece.nsPlatform())
                hall(generator, chunk, odds, nsFloor(generator), false, piece.nsMask(),
                        chunk.sectionZ < stationNear(generator, chunk.sectionX, chunk.sectionZ)[1] ? S : N, stripe, ruined);
            else
                tunnel(generator, chunk, odds, nsFloor(generator), piece.nsMask(), ruined);
        }
    }

    /**
     * A running tunnel: the 6x6 centre plus an arm out to each open side, walled, floored and roofed one
     * block beyond that, so a straight, a bend and (never planned, but drawable) a junction are one shape.
     */
    static void tunnel(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floorY, int mask, boolean ruined) {
        boolean[][] inside = new boolean[16][16];
        for (int x = 5; x <= 10; x++)
            for (int z = 5; z <= 10; z++)
                inside[x][z] = true;
        for (int i = 0; i < 5; i++)
            for (int j = 5; j <= 10; j++) {
                if ((mask & W) != 0)
                    inside[i][j] = true;
                if ((mask & E) != 0)
                    inside[15 - i][j] = true;
                if ((mask & N) != 0)
                    inside[j][i] = true;
                if ((mask & S) != 0)
                    inside[j][15 - i] = true;
            }
        int ceilY = floorY + HEIGHT + 1;
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                if (inside[x][z]) {
                    chunk.setBlock(x, floorY, z, BED);
                    chunk.setBlocks(x, floorY + 1, ceilY, z, Material.AIR);
                    chunk.setBlock(x, ceilY, z, SHELL);
                } else if (nextToInside(inside, x, z)) {
                    // a ring of brick every four blocks along whichever way the tunnel runs here
                    boolean ring = (mask == (W | E) ? x : mask == (N | S) ? z : x + z) % 4 == 0;
                    chunk.setBlocks(x, floorY, ceilY + 1, z, ring ? RING : SHELL);
                }
            }
        // lights along the crown, dark in a ruin
        for (int i = 2; i < 16; i += 4) {
            int x = mask == (N | S) ? 7 + (i / 4 & 1) : i;
            int z = mask == (N | S) ? i : 7 + (i / 4 & 1);
            if (inside[x][z])
                chunk.setBlock(x, ceilY, z, ruined && Math.floorMod(chunk.sectionX * 7 + chunk.sectionZ * 13 + i, 5) < 3
                        ? DEAD_LIGHT : LIGHT);
        }
        rails(chunk, odds, floorY, mask, ruined);
        if (ruined)
            ruin(generator, chunk, odds, floorY, mask, inside);
    }

    private static boolean nextToInside(boolean[][] inside, int x, int z) {
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int nx = x + dx, nz = z + dz;
                if (nx >= 0 && nx < 16 && nz >= 0 && nz < 16 && inside[nx][nz])
                    return true;
            }
        return false;
    }

    /**
     * The two tracks through a piece. Straights run at 6 and 9; a bend pairs the line farther from the
     * turn with the line farther from the entry (outer with outer) so the two curves never cross — vanilla
     * has no crossing rail. Powered every eighth block over a redstone block, so a cart keeps going; a
     * ruin has plain rails with gaps.
     */
    static void rails(RealBlocks chunk, Odds odds, int floorY, int mask, boolean ruined) {
        int railY = floorY + 1;
        if (mask == (W | E) || mask == (N | S)) {
            boolean ew = mask == (W | E);
            for (int line : new int[] { 6, 9 })
                for (int i = 0; i < 16; i++)
                    rail(chunk, odds, ew ? i : line, floorY, ew ? line : i, ew ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH,
                            !ruined && i % 8 == 4, ruined);
            return;
        }
        int a = mask & (W | E), b = mask & (N | S);
        if (a == 0 || b == 0)
            return; // a dead end has no track to lay
        int zFar = b == S ? 6 : 9, zNear = 15 - zFar;
        int xFar = a == W ? 9 : 6, xNear = 15 - xFar;
        RailShape corner = a == W ? (b == S ? RailShape.SOUTH_WEST : RailShape.NORTH_WEST)
                : (b == S ? RailShape.SOUTH_EAST : RailShape.NORTH_EAST);
        for (int[] t : new int[][] { { xFar, zFar }, { xNear, zNear } }) {
            int cx = t[0], cz = t[1];
            for (int x = a == W ? 0 : 15; x != cx; x += a == W ? 1 : -1)
                rail(chunk, odds, x, floorY, cz, RailShape.EAST_WEST, false, ruined);
            for (int z = b == S ? 15 : 0; z != cz; z += b == S ? -1 : 1)
                rail(chunk, odds, cx, floorY, z, RailShape.NORTH_SOUTH, false, ruined);
            chunk.setBlock(cx, railY, cz, Material.RAIL, corner, false);
        }
    }

    private static void rail(RealBlocks chunk, Odds odds, int x, int floorY, int z, RailShape shape, boolean powered,
            boolean ruined) {
        if (ruined && odds.playOdds(0.12))
            return; // a missing length of track
        if (powered) {
            chunk.setBlock(x, floorY, z, Material.REDSTONE_BLOCK);
            chunk.setBlock(x, floorY + 1, z, Material.POWERED_RAIL, shape, true);
        } else
            chunk.setBlock(x, floorY + 1, z, Material.RAIL, shape, false);
    }

    /** The ruin: puddles in the bed, rubble, and a sewer-bag spawner in a niche cut into one wall. */
    private static void ruin(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floorY, int mask,
            boolean[][] inside) {
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++)
                if (inside[x][z] && chunk.isEmpty(x, floorY + 1, z)) {
                    if (odds.playOdds(0.05))
                        chunk.setBlock(x, floorY, z, Material.WATER);
                    else if (odds.playOdds(0.03))
                        chunk.setBlock(x, floorY + 1, z, Material.COBBLESTONE);
                    else if (odds.playOdds(0.02))
                        chunk.setBlock(x, floorY + HEIGHT, z, Material.COBWEB);
                }
        if (!generator.getSettings().spawnersInSubways || !odds.playOdds(Odds.oddsSomewhatUnlikely))
            return;
        // a niche in the wall beside the bed, on a straight only: two deep, the spawner at the back
        if (mask == (W | E)) {
            int z = odds.flipCoin() ? 3 : 12, x = 6 + odds.getRandomInt(4);
            chunk.setBlocks(x, x + 2, floorY + 1, floorY + 4, Math.min(z, 4), Math.max(z, 11) + 1, Material.AIR);
            generator.spawnProvider.setSpawnOrSpawner(generator, chunk, odds, x, floorY + 1, z, true,
                    generator.spawnProvider.itemsEntities_Sewers);
        } else if (mask == (N | S)) {
            int x = odds.flipCoin() ? 3 : 12, z = 6 + odds.getRandomInt(4);
            chunk.setBlocks(Math.min(x, 4), Math.max(x, 11) + 1, floorY + 1, floorY + 4, z, z + 2, Material.AIR);
            generator.spawnProvider.setSpawnOrSpawner(generator, chunk, odds, x, floorY + 1, z, true,
                    generator.spawnProvider.itemsEntities_Sewers);
        }
    }

    /**
     * A station hall, or the widened platform chunk beside one: the bed with its two tracks down the
     * middle, a platform either side a block above it with a yellow edge, tiled walls with the line's
     * colour stripe, pillars between the tracks, lit. {@code alongX} is the line's axis; {@code mask}
     * says which ends are open (a closed end gets a wall and buffer stops), {@code wide} which open
     * ends continue into platform (no end wall at all) rather than narrowing to the tunnel.
     */
    static void hall(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floorY, boolean alongX, int mask,
            int wide, Material stripe, boolean ruined) {
        int ceilY = floorY + HEIGHT + 1;
        int negSide = alongX ? W : N, posSide = alongX ? E : S;
        // the box: floor, ceiling, and the two long walls
        for (int along = 0; along < 16; along++)
            for (int across = 0; across < 16; across++) {
                int x = alongX ? along : across, z = alongX ? across : along;
                if (across == 0 || across == 15) {
                    chunk.setBlocks(x, floorY, ceilY + 1, z, along % 4 == 0 ? RING : HALL_WALL);
                    chunk.setBlock(x, floorY + 3, z, stripe);
                    continue;
                }
                boolean bed = across >= 6 && across <= 9;
                chunk.setBlock(x, floorY, z, bed ? BED : PLATFORM);
                chunk.setBlocks(x, floorY + 1, ceilY, z, Material.AIR);
                if (!bed)
                    chunk.setBlock(x, floorY + 1, z, across == 5 || across == 10 ? EDGE : PLATFORM);
                chunk.setBlock(x, ceilY, z, along % 4 == 0 ? RING : SHELL);
            }
        // the ends
        for (int end = 0; end < 2; end++) {
            int side = end == 0 ? negSide : posSide;
            int along = end == 0 ? 0 : 15;
            boolean open = (mask & side) != 0, continues = (wide & side) != 0;
            for (int across = 1; across <= 14; across++) {
                int x = alongX ? along : across, z = alongX ? across : along;
                boolean bed = across >= 5 && across <= 10;
                if (!open || (!bed && !continues))
                    chunk.setBlocks(x, floorY + 1, ceilY, z, HALL_WALL);
            }
        }
        // pillars between the tracks, and lights over the bed and both platforms
        for (int along = 2; along < 16; along += 5)
            for (int across = 7; across <= 8; across++)
                chunk.setBlocks(alongX ? along : across, floorY + 1, ceilY, alongX ? across : along, RING);
        for (int along = 1; along < 16; along += 4)
            for (int across : new int[] { 3, 7, 12 }) {
                boolean dead = ruined && Math.floorMod(chunk.sectionX * 7 + chunk.sectionZ * 13 + along * 3 + across, 5) < 2;
                chunk.setBlock(alongX ? along : across, ceilY, alongX ? across : along, dead ? DEAD_LIGHT : LIGHT);
            }
        // benches against the walls
        for (int along : new int[] { 5, 6, 9, 10 })
            for (int across : new int[] { 1, 14 })
                chunk.setBlock(alongX ? along : across, floorY + 2, alongX ? across : along, Material.SMOOTH_STONE_SLAB);
        rails(chunk, odds, floorY, negSide | posSide, ruined && odds.playOdds(0.5));
        for (int end = 0; end < 2; end++) { // no line that way: the track stops short at a pair of buffers
            if ((mask & (end == 0 ? negSide : posSide)) != 0)
                continue;
            for (int line : new int[] { 6, 9 }) {
                int wall = end == 0 ? 0 : 15, stop = end == 0 ? 1 : 14;
                chunk.setBlock(alongX ? wall : line, floorY + 1, alongX ? line : wall, HALL_WALL); // the rail took the wall cell
                chunk.setBlock(alongX ? stop : line, floorY + 1, alongX ? line : stop, Material.IRON_BLOCK);
            }
        }
        // lost property: the odd chest at a platform's end
        if (odds.playOdds(Odds.oddsSomewhatLikely)) {
            int along = odds.flipCoin() ? 1 : 14, across = odds.flipCoin() ? 1 : 14;
            chunk.setChest(generator, alongX ? along : across, floorY + 2, alongX ? across : along, odds,
                    generator.lootProvider, LootLocation.BUILDING);
        }
        if (ruined) {
            for (int i = 0; i < 6; i++) {
                int along = odds.getRandomInt(16), across = odds.getRandomInt(16);
                int x = alongX ? along : across, z = alongX ? across : along;
                if (chunk.isEmpty(x, floorY + 2, z) && !chunk.isEmpty(x, floorY + 1, z))
                    chunk.setBlock(x, floorY + 2, z, odds.flipCoin() ? Material.MOSS_CARPET : Material.COBWEB);
            }
        }
    }

    // ---- the stairs (used by the station lot) ----------------------------------------------------

    /** Where a switchback shaft opens: at the end of its flights, or through one of its long sides. */
    public enum Door {
        ALONG, ACROSS_LOW, ACROSS_HIGH
    }

    /**
     * A switchback stair in a 4x4 shaft whose corner is {@code (x0, z0)}: two 2-wide lanes side by side,
     * flights running along x ({@code alongX}) or z. Each pair of flights is a landing, two steps, a
     * landing, then the same back in the other lane — four of rise per pair, so the rise from
     * {@code standBottom} to {@code standTop} (the heights you stand at) must be a multiple of four, and
     * with an even number of pairs you enter and leave in the same lane at the same end
     * ({@code entryPositive}: the far end of the along axis). {@code bottomDoor}/{@code topDoor} say
     * which wall is opened there: the end wall, or a long side beside the landing (the interchange stair
     * arrives on a platform that runs across it). Walls all round, from the floor below to the top.
     */
    public static void zigzag(RealBlocks chunk, int x0, int z0, boolean alongX, boolean entryPositive, int standBottom,
            int standTop, int wallsFrom, Door bottomDoor, Door topDoor) {
        // the lane beside the across-side door is lane A (the one the landings are in); else the low lane
        int laneA = topDoor == Door.ACROSS_HIGH || bottomDoor == Door.ACROSS_HIGH ? 2 : 0;
        int laneB = 2 - laneA;
        int aIn = entryPositive ? 3 : 0, aOut = 3 - aIn;
        int step = entryPositive ? -1 : 1;
        BlockFace away = alongX ? (entryPositive ? BlockFace.WEST : BlockFace.EAST)
                : (entryPositive ? BlockFace.NORTH : BlockFace.SOUTH);
        BlockFace back = away.getOppositeFace();

        // the shaft: air inside the 4x4 from the bottom stand (the floor there stays) to the top stand's
        // floor, walls one block outside it from wallsFrom up — the hall's ceiling, so the first flight
        // rises in the open off the platform and the shaft proper starts overhead
        for (int a = -1; a <= 4; a++)
            for (int c = -1; c <= 4; c++) {
                int x = alongX ? x0 + a : x0 + c, z = alongX ? z0 + c : z0 + a;
                if (x < 0 || x > 15 || z < 0 || z > 15)
                    continue;
                boolean wall = a < 0 || a > 3 || c < 0 || c > 3;
                if (wall)
                    chunk.setBlocks(x, wallsFrom, standTop, z, RING);
                else
                    chunk.setBlocks(x, standBottom, standTop, z, Material.AIR);
            }
        door(chunk, x0, z0, alongX, aIn, laneA, entryPositive, standBottom, bottomDoor);
        door(chunk, x0, z0, alongX, aIn, laneA, entryPositive, standTop, topDoor);

        for (int stand = standBottom; stand + 4 <= standTop; stand += 4) {
            lane(chunk, x0, z0, alongX, aIn, laneA, stand - 1, PLATFORM, null); // landing
            lane(chunk, x0, z0, alongX, aIn + step, laneA, stand, Material.POLISHED_ANDESITE_STAIRS, away);
            lane(chunk, x0, z0, alongX, aIn + 2 * step, laneA, stand + 1, Material.POLISHED_ANDESITE_STAIRS, away);
            lane(chunk, x0, z0, alongX, aOut, laneA, stand + 1, PLATFORM, null); // turn
            lane(chunk, x0, z0, alongX, aOut, laneB, stand + 1, PLATFORM, null);
            lane(chunk, x0, z0, alongX, aOut - step, laneB, stand + 2, Material.POLISHED_ANDESITE_STAIRS, back);
            lane(chunk, x0, z0, alongX, aOut - 2 * step, laneB, stand + 3, Material.POLISHED_ANDESITE_STAIRS, back);
            lane(chunk, x0, z0, alongX, aIn, laneB, stand + 3, PLATFORM, null); // turn
            // a lamp in the end wall over each turn
            int lx = alongX ? x0 + (entryPositive ? 4 : -1) : x0 + 1, lz = alongX ? z0 + 1 : z0 + (entryPositive ? 4 : -1);
            if (lx >= 0 && lx <= 15 && lz >= 0 && lz <= 15 && stand + 2 >= wallsFrom)
                chunk.setBlock(lx, stand + 2, lz, LIGHT);
        }
        lane(chunk, x0, z0, alongX, aIn, laneA, standTop - 1, PLATFORM, null); // the exit landing, two cells
        lane(chunk, x0, z0, alongX, aIn + step, laneA, standTop - 1, PLATFORM, null);
    }

    /** Open the wall beside the landing at {@code stand} on the {@code door} side, three high. */
    private static void door(RealBlocks chunk, int x0, int z0, boolean alongX, int aIn, int laneA, boolean entryPositive,
            int stand, Door door) {
        for (int i = 0; i < 2; i++) {
            int a, c;
            switch (door) {
            case ALONG -> {
                a = entryPositive ? 4 : -1;
                c = laneA + i;
            }
            case ACROSS_LOW -> {
                a = aIn - i * (entryPositive ? 1 : -1);
                c = -1;
            }
            default -> {
                a = aIn - i * (entryPositive ? 1 : -1);
                c = 4;
            }
            }
            int x = alongX ? x0 + a : x0 + c, z = alongX ? z0 + c : z0 + a;
            if (x >= 0 && x <= 15 && z >= 0 && z <= 15)
                chunk.setBlocks(x, stand, stand + 3, z, Material.AIR);
        }
    }

    /** One 2-wide cell of a stair lane: a landing block, or a step facing {@code facing}. */
    private static void lane(RealBlocks chunk, int x0, int z0, boolean alongX, int a, int lane, int y, Material material,
            BlockFace facing) {
        for (int c = lane; c <= lane + 1; c++) {
            int x = alongX ? x0 + a : x0 + c, z = alongX ? z0 + c : z0 + a;
            if (facing == null)
                chunk.setBlock(x, y, z, material);
            else
                chunk.setStair(x, y, z, material, facing);
        }
    }
}
