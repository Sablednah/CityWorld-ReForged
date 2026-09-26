package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.BuildingLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.Subway;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The subway station: a one-storey ticket hall at street level (white tile, a band in the line's colour,
 * glass, a doorway onto every road it touches) with two switchback stairs down to the platforms of the
 * east-west hall twenty-four blocks below, and — where a north-south line also calls here — two more
 * from those platforms down to the lower hall, the interchange. The halls and tunnels themselves are
 * {@link Subway}'s; this lot draws them first, because the stairs cut through them.
 *
 * <p>One per urban platmap, claimed by {@link Subway#placeStation} beside a road before anything else
 * is placed. Never connects to a neighbour (a station is its own building) and never has a basement.
 */
public class SubwayStationLot extends BuildingLot {

    private final int lineColour;

    public SubwayStationLot(PlatMap platmap, int chunkX, int chunkZ, int lineColour) {
        super(platmap, chunkX, chunkZ);
        this.lineColour = lineColour;
        height = 1;
        depth = 0; // the platforms are the basement
    }

    /** The colour of the line this station is on: its wall stripe, above and below ground. */
    public Material lineColour() {
        return Subway.LINE_COLOURS[Math.floorMod(lineColour, Subway.LINE_COLOURS.length)];
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new SubwayStationLot(platmap, chunkX, chunkZ, lineColour);
    }

    @Override
    public boolean isConnectable(PlatLot relative) {
        return false;
    }

    @Override
    public boolean makeConnected(PlatLot relative) {
        return false;
    }

    @Override
    public boolean isConnected(PlatLot relative) {
        return false;
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return blockY < Subway.nsFloor(generator) - 20 && super.isShaftableLevel(generator, blockY);
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return generator.streetLevel;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return generator.streetLevel + 9;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.setLayer(generator.streetLevel, 2, Material.SMOOTH_STONE);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int floor = generator.streetLevel + 1, stand = floor + 1;
        int ewY = Subway.ewFloor(generator), nsY = Subway.nsFloor(generator);
        boolean underground = Subway.drawsIn(generator, this);
        Subway.Piece piece = underground ? Subway.at(generator, chunkX, chunkZ) : Subway.Piece.NONE;
        if (underground)
            Subway.generate(generator, this, chunk, chunkOdds);

        String name = stationName(generator, platmap, platX, platZ);
        ticketHall(generator, chunk, platmap, platX, platZ, floor, stand, name);

        if (underground) {
            // down from the ticket hall to the east-west platforms: one stair at each end of each platform,
            // four steps a flight (six long, three pairs for the 24 of rise), in the hall's two free corners
            int ewCeiling = ewY + Subway.HEIGHT + 1, nsCeiling = nsY + Subway.HEIGHT + 1;
            Subway.zigzag(chunk, 2, 2, true, true, ewY + 2, stand, ewCeiling, Subway.Door.ALONG, Subway.Door.ALONG, 4);
            Subway.zigzag(chunk, 8, 10, true, false, ewY + 2, stand, ewCeiling, Subway.Door.ALONG, Subway.Door.ALONG, 4);
            railings(chunk, stand, 1, 8, 1, 6, 8, 2, 3); // round the openings in the hall floor, a gap at the stair head
            railings(chunk, stand, 7, 14, 9, 14, 7, 10, 11);
            if (piece.nsMask() != 0) { // the interchange: on down to the north-south hall
                Subway.zigzag(chunk, 11, 1, false, true, nsY + 2, ewY + 2, nsCeiling, Subway.Door.ALONG, Subway.Door.ACROSS_LOW);
                Subway.zigzag(chunk, 1, 11, false, false, nsY + 2, ewY + 2, nsCeiling, Subway.Door.ALONG, Subway.Door.ACROSS_HIGH);
                railings(chunk, ewY + 2, 10, 15, 0, 5, 10, 3, 4);
                railings(chunk, ewY + 2, 0, 5, 10, 15, 5, 11, 12);
                signs(chunk, ewY + 3, "INTERCHANGE", "north - south", "line below");
            }
        }
        // panes and bars only join up when told to
        chunk.reconnect(0, 16, stand, stand + 6, 0, 16);
        if (underground)
            chunk.reconnect(0, 16, ewY + 1, ewY + 4, 0, 16);
        generator.reportLocation("subway", name + " Station", chunk);
        if (buildingsDecay(generator))
            destroyLot(generator, stand, stand + 5);
        generator.spawnProvider.spawnBeing(generator, chunk, chunkOdds, 7, stand, 7);
    }

    /** The name of the road this station stands beside, or "Subway" in a world without street names. */
    private String stationName(CityWorldGenerator generator, PlatMap platmap, int platX, int platZ) {
        if (generator.getSettings().includeNamedRoads)
            for (int[] d : new int[][] { { 0, -1 }, { 0, 1 }, { -1, 0 }, { 1, 0 } }) {
                int rx = platX + d[0], rz = platZ + d[1];
                if (platmap.inBounds(rx, rz) && platmap.getLot(rx, rz) instanceof RoadLot road) {
                    boolean[] runs = road.getStreetDirections(platmap, rx, rz);
                    return road.getStreetName(generator, runs[0]);
                }
            }
        return "Subway";
    }

    /** Which sides of this chunk face a road: {N, S, E, W}. */
    private static boolean[] roadSides(PlatMap platmap, int platX, int platZ) {
        return new boolean[] { platmap.isExistingRoad(platX, platZ - 1), platmap.isExistingRoad(platX, platZ + 1),
                platmap.isExistingRoad(platX + 1, platZ), platmap.isExistingRoad(platX - 1, platZ) };
    }

    /**
     * The hall at street level: a box inset one from the chunk edge (a pavement ring round it, and room
     * for the name on the outside), five high, white tile with a glass band and the line's stripe, brick
     * corners and pillars, a flat roof, a doorway in every road-facing wall (the south one if none), the
     * name over each door inside and out, lit, with a ticket booth in the middle.
     */
    private void ticketHall(CityWorldGenerator generator, RealBlocks chunk, PlatMap platmap, int platX, int platZ,
            int floor, int stand, String name) {
        Material stripe = lineColour();
        chunk.setLayer(floor, Subway.PLATFORM);
        chunk.setBlocks(0, 16, stand, stand + 7, 0, 16, Material.AIR);
        for (int i = 1; i <= 14; i++)
            for (int[] w : new int[][] { { i, 1 }, { i, 14 }, { 1, i }, { 14, i } }) {
                int x = w[0], z = w[1];
                boolean pillar = i == 1 || i == 14 || i % 4 == 2;
                chunk.setBlock(x, stand, z, pillar ? Subway.RING : Subway.HALL_WALL);
                chunk.setBlocks(x, stand + 1, stand + 3, z, pillar ? Subway.RING : Material.GLASS_PANE);
                chunk.setBlock(x, stand + 3, z, pillar ? Subway.RING : stripe);
                chunk.setBlock(x, stand + 4, z, pillar ? Subway.RING : Subway.HALL_WALL);
            }
        chunk.setBlocks(1, 15, stand + 5, stand + 6, 1, 15, Subway.SHELL); // the roof
        for (int i = 1; i <= 14; i++) // a low parapet
            for (int[] w : new int[][] { { i, 1 }, { i, 14 }, { 1, i }, { 14, i } })
                chunk.setBlock(w[0], stand + 6, w[1], Subway.RING);

        boolean[] roads = roadSides(platmap, platX, platZ);
        if (!roads[0] && !roads[1] && !roads[2] && !roads[3])
            roads[1] = true;
        for (int side = 0; side < 4; side++) {
            if (!roads[side])
                continue;
            BlockFace into = switch (side) { // the way in through that wall
            case 0 -> BlockFace.SOUTH;
            case 1 -> BlockFace.NORTH;
            case 2 -> BlockFace.WEST;
            default -> BlockFace.EAST;
            };
            int wall = side == 0 || side == 3 ? 1 : 14; // where that wall stands
            for (int along = 6; along <= 9; along++) {
                int x = side < 2 ? along : wall, z = side < 2 ? wall : along;
                chunk.setBlocks(x, stand, stand + 3, z, Material.AIR);
            }
            // the name over the door: inside, facing into the hall, and outside, facing the street
            for (int face = 0; face < 2; face++) {
                int d = face == 0 ? 1 : -1; // one cell in, or one cell out
                int sx = side < 2 ? 7 : wall + (side == 2 ? -d : d), sz = side < 2 ? wall + (side == 0 ? d : -d) : 7;
                int dx = side < 2 ? 1 : 0, dz = side < 2 ? 0 : 1;
                BlockFace facing = face == 0 ? into : into.getOppositeFace();
                chunk.setWallSign(sx, stand + 3, sz, facing, "SUBWAY", name, "Station");
                chunk.setWallSign(sx + dx, stand + 3, sz + dz, facing, face == 0 ? "\u2193 platforms" : "SUBWAY", "", "");
            }
        }

        for (int[] p : new int[][] { { 4, 4 }, { 11, 4 }, { 4, 11 }, { 11, 11 }, { 7, 7 }, { 8, 8 } })
            chunk.setBlock(p[0], stand + 5, p[1], Subway.LIGHT);

        // the ticket booth, in the north-east corner (the stairs take the other two): a quartz counter
        // round a clerk's square, glass on top, the clerk's way in on the west side
        for (int x = 10; x <= 13; x++)
            for (int z = 2; z <= 5; z++)
                if (x == 10 || x == 13 || z == 2 || z == 5) {
                    chunk.setBlock(x, stand, z, Material.QUARTZ_BLOCK);
                    chunk.setBlock(x, stand + 1, z, Material.GLASS_PANE);
                }
        chunk.setBlocks(10, stand, stand + 2, 4, Material.AIR);
        chunk.setBlock(11, stand, 3, Material.LECTERN);
        chunk.setBlock(12, stand + 2, 4, Material.LANTERN);
    }

    /** Iron railings round a shaft's rim at {@code y}: the border of the ring {@code x1..x2, z1..z2}, on the
     *  cells that are open there and solid below (a hall wall is neither), skipping the stair head's two cells. */
    private static void railings(RealBlocks chunk, int y, int x1, int x2, int z1, int z2, int gx, int gz1, int gz2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                if (x != x1 && x != x2 && z != z1 && z != z2)
                    continue; // the border only
                if (x < 0 || x > 15 || z < 0 || z > 15 || x == gx && (z == gz1 || z == gz2))
                    continue;
                if (chunk.isEmpty(x, y, z) && !chunk.isEmpty(x, y - 1, z))
                    chunk.setBlock(x, y, z, Material.IRON_BARS);
            }
    }

    private static void signs(RealBlocks chunk, int y, String... lines) {
        chunk.setWallSign(7, y, 1, BlockFace.SOUTH, lines);
        chunk.setWallSign(8, y, 14, BlockFace.NORTH, lines);
    }
}
