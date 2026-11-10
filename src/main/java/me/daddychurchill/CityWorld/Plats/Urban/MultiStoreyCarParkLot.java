package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConnectedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SurroundingLots;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * A multi-storey car park: {@code levels} decks {@link #PITCH} blocks apart, each two rows of bays with painted
 * lines either side of an aisle, joined by slab ramps a vehicle can drive (half a block a cell, ten cells a deck,
 * up the north strip from an even deck and the south strip from an odd one, so a ramp never arrives under the
 * next one). Pillars on the bay lines carry each deck; the open sides stand behind a one-block parapet with the
 * entrances cut through it at street level on the sides that front a road; a ladder up the east wall is the
 * stairwell. Flood-fills into its neighbours like any building ({@link ConnectedLot}: a joined side has no
 * parapet, no pillars and no ladder, the bays run straight through). Vehicles stand in some bays by the
 * {@code vehicles} rules, a chunk's worth spread over its decks; a ruin keeps block wrecks and decays like a building.
 */
public class MultiStoreyCarParkLot extends ConnectedLot {

    /** Deck to deck: four blocks of headroom under each deck. */
    public static final int PITCH = 5;
    /** Ramp cells a deck apart, half a block of rise each. */
    static final int RAMP = PITCH * 2;

    private final int levels;

    public MultiStoreyCarParkLot(PlatMap platmap, int chunkX, int chunkZ, int levels) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.levels = Math.max(2, levels);
    }

    public int getLevels() {
        return levels;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new MultiStoreyCarParkLot(platmap, chunkX, chunkZ, levels);
    }

    private static final boolean[] WHOLE_EDGE = new boolean[16];
    static {
        java.util.Arrays.fill(WHOLE_EDGE, true);
    }

    @Override
    public boolean[] roadOpening(CityWorldGenerator generator, BlockFace toward) {
        // the entrance cut through the parapet on that side (generateActualBlocks), and nothing else: an inner chunk of
        // a big one keeps its parapet-less sides but has no road there, so the mask is the same either way
        boolean[] mask = new boolean[16];
        int from = switch (toward) {
            case NORTH -> 12;
            case SOUTH -> 6;
            default -> 7;
        };
        int to = switch (toward) {
            case NORTH -> 14;
            case SOUTH -> 9;
            default -> 8;
        };
        for (int i = from; i <= to; i++)
            mask[i] = true;
        return mask;
    }

    @Override
    public String getInteriorDescription() {
        return "Multi-storey car park";
    }

    @Override
    public boolean allowsWildDecoration() {
        return false;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return generator.streetLevel;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return generator.streetLevel + 2 + (levels - 1) * PITCH;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, (levels - 1) * PITCH + 3, 0, true);
        chunk.setLayer(generator.streetLevel, RoadLot.cityPavement(generator));
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    /** The strip (rows 0..2 north, 13..15 south) that deck {@code k}'s up-ramp climbs. */
    private static boolean rampNorth(int k) {
        return k % 2 == 0;
    }

    /** The x of ramp cell {@code i} (0 = foot) on the north strip (eastward) or the south (westward). */
    private static int rampX(boolean north, int i) {
        return north ? 2 + i : 13 - i;
    }

    private static boolean inStrip(boolean north, int z) {
        return north ? z <= 2 : z >= 13;
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int street = generator.streetLevel;
        boolean ruined = generator.isApocalypseStyle();
        Odds odds = chunkOdds;
        Material deckMat = RoadLot.cityPavement(generator), line = Material.WHITE_CONCRETE,
                frame = Material.LIGHT_GRAY_CONCRETE, ramp = Material.SMOOTH_STONE, rampSlab = Material.SMOOTH_STONE_SLAB;
        SurroundingLots joined = new SurroundingLots(platmap, platX, platZ);
        boolean openN = !joined.toNorth(), openS = !joined.toSouth(), openE = !joined.toEast(), openW = !joined.toWest();
        boolean roadN = platmap.isExistingRoad(platX, platZ - 1), roadS = platmap.isExistingRoad(platX, platZ + 1),
                roadE = platmap.isExistingRoad(platX + 1, platZ), roadW = platmap.isExistingRoad(platX - 1, platZ);
        if (!(roadN || roadS || roadE || roadW))
            roadS = true; // an inner chunk of a big one still gets a way in from its own side

        for (int k = 0; k < levels; k++) {
            int deck = street + k * PITCH, stand = deck + 1;
            boolean top = k == levels - 1;
            boolean upNorth = rampNorth(k), holeNorth = rampNorth(k - 1); // the hole is over the ramp below, on ITS strip
            // the deck: bays in rows 3..6 and 9..12 with a line every four cells, the aisle between, the strips
            // plain; above the ground the layer is laid here, with the hole the ramp from below comes up through
            if (k > 0)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        boolean bayRow = z >= 3 && z <= 6 || z >= 9 && z <= 12;
                        Material m = bayRow && x % 4 == 0 ? line : deckMat;
                        if (inStrip(holeNorth, z) && isRampCell(holeNorth, x, RAMP - 1))
                            continue; // the hole: cells 0..8 of the ramp below end under this deck
                        if (ruined && odds.playOdds(0.06))
                            m = odds.flipCoin() ? Material.GRAVEL : Material.MOSS_BLOCK;
                        chunk.setBlock(x, deck, z, m);
                    }
            else if (ruined)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        if (odds.playOdds(0.06))
                            chunk.setBlock(x, deck, z, odds.flipCoin() ? Material.GRAVEL : Material.MOSS_BLOCK);
            // bay lines on the ground deck (the pavement layer is the chunk's)
            if (k == 0)
                for (int x = 0; x < 16; x += 4)
                    for (int z = 0; z < 16; z++)
                        if (z >= 3 && z <= 6 || z >= 9 && z <= 12)
                            chunk.setBlock(x, deck, z, line);
            // the up-ramp: cell i rises (i + 1) / 2 blocks above this deck's surface, full blocks then a slab
            if (!top)
                for (int i = 0; i < RAMP; i++) {
                    int x = rampX(upNorth, i), rise = (i + 1) / 2;
                    boolean half = (i + 1) % 2 == 1;
                    for (int z = upNorth ? 0 : 13; z <= (upNorth ? 2 : 15); z++) {
                        if (rise > 0)
                            chunk.setBlocks(x, deck + 1, deck + 1 + rise, z, ramp);
                        if (half)
                            chunk.setBlock(x, deck + 1 + rise, z, rampSlab, SlabType.BOTTOM);
                    }
                }
            // the parapet round the open sides, one block, with the entrances cut through at street level: the
            // aisle on an east or west road, past the ramp's foot on a north one, mid-side on a south one
            for (int i = 0; i < 16; i++) {
                if (openN && !(k == 0 && roadN && i >= 12 && i <= 14))
                    chunk.setBlock(i, stand, 0, frame);
                if (openS && !(k == 0 && roadS && i >= 6 && i <= 9))
                    chunk.setBlock(i, stand, 15, frame);
                if (openE && !(k == 0 && roadE && i >= 7 && i <= 8))
                    chunk.setBlock(15, stand, i, frame);
                if (openW && !(k == 0 && roadW && i >= 7 && i <= 8))
                    chunk.setBlock(0, stand, i, frame);
            }
            // pillars: on the bay lines at the strips' inner rows, and the open corners; up to the deck above
            if (!top) {
                int to = deck + PITCH;
                for (int x = 4; x <= 12; x += 4) {
                    chunk.setBlocks(x, stand, to, 3, frame);
                    chunk.setBlocks(x, stand, to, 12, frame);
                }
                if (openN || openW)
                    chunk.setBlocks(0, stand, to, 0, frame);
                if (openN || openE)
                    chunk.setBlocks(15, stand, to, 0, frame);
                if (openS || openW)
                    chunk.setBlocks(0, stand, to, 15, frame);
                if (openS || openE)
                    chunk.setBlocks(15, stand, to, 15, frame);
                // light under the deck above, set into it
                chunk.setBlock(7, to, 7, Material.SEA_LANTERN);
                chunk.setBlock(8, to, 8, Material.SEA_LANTERN);
            } else
                for (int x : new int[] { 4, 12 }) { // the roof deck: lamp posts
                    chunk.setBlocks(x, stand, stand + 4, x == 4 ? 3 : 12, Material.COBBLESTONE_WALL);
                    chunk.setBlock(x, stand + 4, x == 4 ? 3 : 12, Material.SEA_LANTERN);
                }
        }
        // the stairwell: a wall column on the east edge with a ladder up it, from the street to the roof deck
        int roof = street + (levels - 1) * PITCH + 1;
        if (openE) {
            chunk.setBlocks(15, street + 1, roof + 2, 8, frame);
            chunk.setLadder(14, street + 1, roof + 1, 8, BlockFace.WEST);
        }
        // vehicles in some bays, a chunk's worth spread over the decks; a ruin keeps block wrecks instead
        var settings = generator.getSettings();
        boolean real = !ruined && settings.includeVehicles && me.daddychurchill.CityWorld.worldgen.Vehicles.any("car");
        if (real) {
            for (int slot = 0; slot < settings.carParkVehicles; slot++) {
                if (!odds.playOdds(settings.oddsOfParkedVehicle))
                    continue;
                int k = odds.getRandomInt(levels), bay = odds.getRandomInt(4), row = odds.getRandomInt(2);
                Odds car = odds.fork();
                double cx = chunk.getBlockX(bay * 4 + 1) + 1.5, cz = chunk.getBlockZ(row == 0 ? 3 : 9) + 2.0;
                me.daddychurchill.CityWorld.worldgen.Vehicles.park(chunk.getWorld(), "car", cx, street + 1 + k * PITCH, cz,
                        row == 0 ? 180 : 0, settings.vehicleFuel, car);
            }
        } else if (ruined)
            for (int k = 0; k < levels; k++)
                for (int bay = 0; bay < 4; bay++)
                    for (int row = 0; row < 2; row++)
                        if (odds.playOdds(0.2))
                            ParkingLot.car(chunk, odds, bay * 4 + 1, street + 1 + k * PITCH, row == 0 ? 3 : 9,
                                    ParkingLot.CAR_PAINT[odds.getRandomInt(ParkingLot.CAR_PAINT.length)], true);
        // a sign by the entrance on the ground floor
        if (openS && roadS)
            chunk.setSignPost(5, street + 1, 14, Material.OAK_SIGN, BlockFace.SOUTH, new String[] { "Car Park", "", "Pay & Display" });
        chunk.reconnect(0, 16, street + 1, roof + 2, 0, 16);
        if (ruined)
            destroyLot(generator, street + 1, roof + 2);
    }

    /** Is {@code x} one of ramp cells 0..{@code last} of the strip. */
    private static boolean isRampCell(boolean north, int x, int last) {
        for (int i = 0; i < last; i++)
            if (rampX(north, i) == x)
                return true;
        return false;
    }
}
