package me.daddychurchill.CityWorld.Plugins;

import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.Colors;
import me.daddychurchill.CityWorld.Support.Colors.ColorSet;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The things floating above the world: hot-air balloons and blimps moored to rooftops, free-floating
 * balloons/saucers over the wild, and (via {@link #generateAirship}) the rare two-chunk airship. Ported from
 * upstream's {@code StructureInAirProvider}; drawn in the decoration pass, in-chunk only.
 */
public class StructureInAirProvider extends Provider {

    public final static int hotairBalloonHeight = 30;

    public static StructureInAirProvider loadProvider(CityWorldGenerator generator) {
        return new StructureInAirProvider();
    }

    /** A small balloon moored to a roof: a string up from (attachX, attachY, attachZ) to a colour-blocked
     *  teardrop envelope. No-op if there's nothing solid at the attach point to tie to. */
    public void generateBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachX,
            int attachY, int attachZ, Odds odds) {
        int bx = attachX, bz = attachZ;
        int by1 = attachY + 5 + odds.getRandomInt(10);
        int by2 = by1 + 8 + odds.getRandomInt(3);

        if (!attachString(chunk, bx, attachY, by1, bz))
            return;

        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material primary = colors.getConcrete();
        Material secondary = colors.getConcrete();

        chunk.setBlocks(bx, bx + 1, by1, by1 + 2, bz, bz + 1, primary);

        chunk.setBlocks(bx - 1, bx + 2, by1 + 2, by1 + 4, bz - 1, bz + 2, primary);
        chunk.setBlock(bx - 1, by1 + 2, bz - 1, Material.AIR);
        chunk.setBlock(bx - 1, by1 + 2, bz + 1, Material.AIR);
        chunk.setBlock(bx + 1, by1 + 2, bz - 1, Material.AIR);
        chunk.setBlock(bx + 1, by1 + 2, bz + 1, Material.AIR);

        chunk.setBlocks(bx - 2, bx + 3, by1 + 4, by1 + 6, bz - 2, bz + 3, primary);
        chunk.setBlock(bx - 2, by1 + 4, bz - 2, Material.AIR);
        chunk.setBlock(bx - 2, by1 + 4, bz + 2, Material.AIR);
        chunk.setBlock(bx + 2, by1 + 4, bz - 2, Material.AIR);
        chunk.setBlock(bx + 2, by1 + 4, bz + 2, Material.AIR);

        chunk.setBlocks(bx - 2, bx + 3, by1 + 6, by2 - 1, bz - 2, bz + 3, secondary);
        chunk.setBlocks(bx - 2, bx + 3, by2 - 1, by2, bz - 2, bz + 3, primary);
        chunk.setBlocks(bx - 1, bx + 2, by2, by2 + 1, bz - 1, bz + 2, primary);
    }

    /** A hot-air balloon floating at {@code bottomY}: a concrete basket with an occupant, four strings up
     *  to a hollow envelope with a fire burner. This is the free-floating one (its own basket is the
     *  anchor), used by the nature/farm airborne lots. */
    public void generateHotairBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context,
            int bottomY, Odds odds) {
        int balloonY1 = bottomY + 6;
        int balloonY2 = balloonY1 + 20;

        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material basket = colors.getConcrete();
        chunk.setBlocks(6, 10, bottomY, 6, 10, basket);
        chunk.setWalls(5, 11, bottomY + 1, 5, 11, basket);
        chunk.setWalls(5, 11, bottomY + 2, 5, 11, basket);

        generator.spawnProvider.spawnBeing(generator, chunk, odds, 7, bottomY + 1, 7);

        attachString(chunk, 5, bottomY + 2, balloonY1, 5);
        attachString(chunk, 5, bottomY + 2, balloonY1, 10);
        attachString(chunk, 10, bottomY + 2, balloonY1, 5);
        attachString(chunk, 10, bottomY + 2, balloonY1, 10);

        drawBigBalloon(generator, chunk, balloonY1, balloonY2, odds, true);
    }

    /** A big balloon moored to a roof (the standalone envelope), if it can be strung to the roof. */
    public void generateBigBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachY,
            Odds odds) {
        int balloonY1 = attachY + 4 + odds.getRandomInt(4);
        int balloonY2 = balloonY1 + 15 + odds.getRandomInt(15);

        boolean strung = attachString(chunk, 7 + odds.getRandomInt(2), attachY, balloonY1 + 5, 4);
        strung = attachString(chunk, 7 + odds.getRandomInt(2), attachY, balloonY1 + 5, 11) || strung;
        strung = attachString(chunk, 4, attachY, balloonY1 + 5, 7 + odds.getRandomInt(2)) || strung;
        strung = attachString(chunk, 11, attachY, balloonY1 + 5, 7 + odds.getRandomInt(2)) || strung;

        if (strung)
            drawBigBalloon(generator, chunk, balloonY1, balloonY2, odds, false);
    }

    /**
     * One half of the two-chunk airship (see {@code AirshipLot}): a cigar-shaped envelope in a two-colour
     * livery with four tail fins, a glazed control car slung underneath with a stern observation deck, and an
     * engine nacelle and propeller either side. Both halves call this with the same dice, and each draws only
     * the cells of the shared 32×16 frame that fall inside its own chunk, so they meet at the seam.
     *
     * @param keelY      the control car's floor; the envelope's belly is six blocks above it
     * @param alongX     the ship's length runs along X (a west-east pair) rather than Z
     * @param secondHalf false for the west/north chunk, true for the east/south one
     */
    public void generateAirship(CityWorldGenerator generator, SupportBlocks chunk, int keelY, boolean alongX,
            boolean secondHalf, Odds odds) {
        new Airship(chunk, keelY, alongX, secondHalf, odds).draw();
    }

    /** Envelope colour, then trim colour. Plain concrete reads cleanly from the ground at this altitude. */
    private static final Material[][] AIRSHIP_LIVERIES = { { Material.WHITE_CONCRETE, Material.RED_CONCRETE },
            { Material.LIGHT_GRAY_CONCRETE, Material.BLUE_CONCRETE },
            { Material.LIGHT_GRAY_CONCRETE, Material.BLACK_CONCRETE },
            { Material.YELLOW_CONCRETE, Material.BLACK_CONCRETE },
            { Material.WHITE_CONCRETE, Material.CYAN_CONCRETE },
            { Material.GRAY_CONCRETE, Material.ORANGE_CONCRETE },
            { Material.WHITE_CONCRETE, Material.GREEN_CONCRETE },
            { Material.PURPLE_CONCRETE, Material.YELLOW_CONCRETE } };

    /**
     * The airship's geometry, in a frame independent of which way it faces: {@code u} runs stern (0) to bow
     * (31) along the two chunks, {@code v} runs across (0..15), {@code y} is world height. Every write goes
     * through {@link #set}, which maps the frame onto this chunk and drops what belongs to the other half.
     */
    private static final class Airship {
        private static final int LENGTH = 32;
        private static final double MID_U = 16.0, MID_V = 8.0, RADIUS = 6.0, SQUASH = 0.85;
        private static final int CAR_STERN = 12, CAR_BOW = 22, CAR_PORT = 5, CAR_STARBOARD = 10;
        private static final int DECK_STERN = 8;

        private final SupportBlocks chunk;
        private final int keelY, shift;
        private final boolean alongX, bowPositive;
        private final double midY;
        private final Material hull, trim, planks, slab, stairs, fence;
        private final BlockFace plusU, minusU, plusV, minusV;

        Airship(SupportBlocks chunk, int keelY, boolean alongX, boolean secondHalf, Odds odds) {
            this.chunk = chunk;
            this.keelY = keelY;
            this.alongX = alongX;
            this.shift = secondHalf ? 16 : 0;
            this.midY = keelY + 11.0;

            // every roll up front and in a fixed order: both halves must draw the same numbers
            bowPositive = odds.flipCoin();
            Material[] livery = AIRSHIP_LIVERIES[odds.getRandomInt(AIRSHIP_LIVERIES.length)];
            hull = livery[0];
            trim = livery[1];
            boolean darkOak = odds.flipCoin();
            planks = darkOak ? Material.DARK_OAK_PLANKS : Material.SPRUCE_PLANKS;
            slab = darkOak ? Material.DARK_OAK_SLAB : Material.SPRUCE_SLAB;
            stairs = darkOak ? Material.DARK_OAK_STAIRS : Material.SPRUCE_STAIRS;
            fence = darkOak ? Material.DARK_OAK_FENCE : Material.SPRUCE_FENCE;

            BlockFace ahead = alongX ? BlockFace.EAST : BlockFace.SOUTH;
            BlockFace behind = alongX ? BlockFace.WEST : BlockFace.NORTH;
            plusU = bowPositive ? ahead : behind;
            minusU = bowPositive ? behind : ahead;
            plusV = alongX ? BlockFace.SOUTH : BlockFace.EAST;
            minusV = alongX ? BlockFace.NORTH : BlockFace.WEST;
        }

        void draw() {
            drawEnvelope();
            drawFins();
            drawCar();
            drawEngine(false);
            drawEngine(true);
            // a mooring spike on the nose
            setFacing(LENGTH - 1, keelY + 10, 7, Material.LIGHTNING_ROD, plusU);
        }

        // ---- envelope ---------------------------------------------------------------------------------

        /** Half-width of the envelope at this station, zero past nose and tail: a blunt bow, a long taper aft. */
        private static double radius(int u) {
            double t = (u + 0.5 - MID_U) / 15.0;
            double a = Math.abs(t);
            return a >= 1.0 ? 0.0 : RADIUS * Math.sqrt(1.0 - Math.pow(a, t > 0 ? 2.0 : 1.6));
        }

        private boolean inside(int u, int y, int v) {
            double r = u < 0 || u >= LENGTH ? 0.0 : radius(u);
            if (r <= 0.0)
                return false;
            double dv = (v + 0.5 - MID_V) / r, dy = (y + 0.5 - midY) / (r * SQUASH);
            return dv * dv + dy * dy <= 1.0;
        }

        /** Solid, not a shell: a hollow ten-block envelope is a dark sealed room, and mobs would spawn in it. */
        private void drawEnvelope() {
            for (int u = 0; u < LENGTH; u++)
                for (int v = 0; v < 16; v++)
                    for (int y = keelY + 4; y <= keelY + 18; y++)
                        if (inside(u, y, v)) {
                            boolean noseCap = (u + 0.5 - MID_U) / 15.0 > 0.8;
                            boolean beltLine = Math.abs(y + 0.5 - midY) < 1.0;
                            set(u, y, v, noseCap || beltLine ? trim : hull);
                        }
        }

        /** Four fins on the tail cone — top, bottom, port, starboard — tapering forward. */
        private void drawFins() {
            for (int u = 1; u <= 7; u++) {
                int reach = (int) Math.round((7 - u) * 0.8 + 1.0);
                for (int v = 7; v <= 8; v++) {
                    int top = keelY + 18;
                    while (top > keelY && !inside(u, top, v))
                        top--;
                    if (top == keelY)
                        continue;
                    int bottom = keelY + 4;
                    while (!inside(u, bottom, v))
                        bottom++;
                    for (int y = top + 1; y <= top + reach; y++)
                        set(u, y, v, trim);
                    for (int y = bottom - 1; y >= bottom - Math.max(1, reach * 2 / 3); y--)
                        set(u, y, v, trim);
                }
                for (int y = keelY + 10; y <= keelY + 11; y++) {
                    int port = 0;
                    while (port < 16 && !inside(u, y, port))
                        port++;
                    if (port == 16)
                        continue;
                    int starboard = 15;
                    while (!inside(u, y, starboard))
                        starboard--;
                    for (int v = port - 1; v >= Math.max(0, port - reach); v--)
                        set(u, y, v, trim);
                    for (int v = starboard + 1; v <= Math.min(15, starboard + reach); v++)
                        set(u, y, v, trim);
                }
            }
        }

        // ---- control car --------------------------------------------------------------------------------

        private void drawCar() {
            int floor = keelY, roof = keelY + 4;

            for (int u = DECK_STERN; u <= CAR_BOW; u++)
                for (int v = CAR_PORT; v <= CAR_STARBOARD; v++) {
                    set(u, floor, v, planks);
                    if (u >= CAR_STERN)
                        set(u, roof, v, planks);
                    if (u > CAR_STERN && u < CAR_BOW && v > CAR_PORT && v < CAR_STARBOARD)
                        setSlab(u, floor - 1, v); // a shallow keel under the cabin
                }

            for (int y = floor + 1; y < roof; y++) {
                for (int u = CAR_STERN; u <= CAR_BOW; u++)
                    for (int v : new int[] { CAR_PORT, CAR_STARBOARD })
                        if (y == floor + 2 && u > CAR_STERN && u < CAR_BOW)
                            set(u, y, v, Material.GLASS_PANE, plusU, minusU);
                        else
                            set(u, y, v, planks);
                for (int v = CAR_PORT + 1; v < CAR_STARBOARD; v++) {
                    set(CAR_BOW, y, v, Material.GLASS_PANE, plusV, minusV); // the bridge, glazed right across
                    if (y == roof - 1 || (v != 7 && v != 8))
                        set(CAR_STERN, y, v, planks); // stern bulkhead, with a doorway onto the deck
                }
            }

            // hung from the envelope: a plank ridge along the roof and a chain at each corner
            for (int u = CAR_STERN; u <= CAR_BOW; u++)
                for (int v = 7; v <= 8; v++)
                    hangUp(u, roof + 1, v, planks);
            for (int u : new int[] { CAR_STERN, CAR_BOW })
                for (int v : new int[] { CAR_PORT, CAR_STARBOARD })
                    hangUp(u, roof + 1, v, Material.IRON_CHAIN);

            // aboard: a runner down the aisle, seats along the windows, stores aft, the helm forward
            for (int u = CAR_STERN + 1; u < CAR_BOW; u++)
                for (int v = 7; v <= 8; v++)
                    set(u, floor + 1, v, Material.RED_CARPET);
            for (int u = 15; u <= 19; u += 2) {
                setStair(u, floor + 1, CAR_PORT + 1, minusV);
                setStair(u, floor + 1, CAR_STARBOARD - 1, plusV);
            }
            set(CAR_STERN + 1, floor + 1, CAR_PORT + 1, Material.BARREL);
            set(CAR_STERN + 1, floor + 1, CAR_STARBOARD - 1, Material.BARREL);
            setFacing(CAR_BOW - 1, floor + 1, CAR_PORT + 1, Material.LECTERN, minusU);
            set(CAR_BOW - 1, floor + 1, CAR_STARBOARD - 1, Material.CARTOGRAPHY_TABLE);
            hang(14, roof - 1, 7);
            hang(18, roof - 1, 8);
            hang(CAR_BOW - 1, roof - 1, 8);

            // the observation deck astern, railed, with a lantern on each after post
            for (int u = DECK_STERN; u < CAR_STERN; u++)
                for (int v = CAR_PORT; v <= CAR_STARBOARD; v++)
                    if (isRail(u, v))
                        set(u, floor + 1, v, fence, railFaces(u, v));
            set(DECK_STERN, floor + 2, CAR_PORT, Material.LANTERN);
            set(DECK_STERN, floor + 2, CAR_STARBOARD, Material.LANTERN);
        }

        private void hangUp(int u, int fromY, int v, Material material) {
            for (int y = fromY; y < fromY + 6 && !inside(u, y, v); y++)
                set(u, y, v, material);
        }

        private static boolean isRail(int u, int v) {
            return u >= DECK_STERN && u < CAR_STERN && v >= CAR_PORT && v <= CAR_STARBOARD
                    && (u == DECK_STERN || v == CAR_PORT || v == CAR_STARBOARD);
        }

        private BlockFace[] railFaces(int u, int v) {
            java.util.List<BlockFace> faces = new java.util.ArrayList<>(4);
            if (isRail(u + 1, v) || u + 1 == CAR_STERN)
                faces.add(plusU);
            if (isRail(u - 1, v))
                faces.add(minusU);
            if (isRail(u, v + 1))
                faces.add(plusV);
            if (isRail(u, v - 1))
                faces.add(minusV);
            return faces.toArray(new BlockFace[0]);
        }

        // ---- engines ------------------------------------------------------------------------------------

        /** An iron nacelle on an outrigger from the car's plank row, with a four-bladed propeller astern. */
        private void drawEngine(boolean starboard) {
            BlockFace out = starboard ? plusV : minusV;
            BlockFace in = starboard ? minusV : plusV;
            for (int u = 13; u <= 15; u++)
                for (int d = 1; d <= 2; d++)
                    for (int y = keelY + 2; y <= keelY + 3; y++)
                        set(u, y, across(starboard, d), Material.IRON_BLOCK);
            for (int d = 3; d <= 4; d++)
                set(14, keelY + 3, across(starboard, d), fence, out, in);
            for (int y = keelY + 1; y <= keelY + 4; y++) {
                boolean hub = y == keelY + 2 || y == keelY + 3;
                for (int d = hub ? 0 : 1; d <= (hub ? 3 : 2); d++)
                    set(12, y, across(starboard, d), Material.IRON_BARS,
                            faces(d > (hub ? 0 : 1) ? out : null, d < (hub ? 3 : 2) ? in : null));
            }
        }

        private static int across(boolean starboard, int d) {
            return starboard ? 15 - d : d;
        }

        private static BlockFace[] faces(BlockFace a, BlockFace b) {
            return a == null ? (b == null ? new BlockFace[0] : new BlockFace[] { b })
                    : (b == null ? new BlockFace[] { a } : new BlockFace[] { a, b });
        }

        // ---- the frame, mapped onto this chunk ----------------------------------------------------------

        private int localX(int u, int v) {
            int w = bowPositive ? u : LENGTH - 1 - u;
            return alongX ? w - shift : v;
        }

        private int localZ(int u, int v) {
            int w = bowPositive ? u : LENGTH - 1 - u;
            return alongX ? v : w - shift;
        }

        /** Whether this cell is in this chunk. The block setters do not clip: they write into neighbours. */
        private boolean ours(int u, int y, int v) {
            return chunk.insideXYZ(localX(u, v), y, localZ(u, v));
        }

        private void set(int u, int y, int v, Material material) {
            if (ours(u, y, v))
                chunk.setBlock(localX(u, v), y, localZ(u, v), material);
        }

        private void set(int u, int y, int v, Material material, BlockFace... faces) {
            if (ours(u, y, v))
                chunk.setBlock(localX(u, v), y, localZ(u, v), material, faces);
        }

        private void setFacing(int u, int y, int v, Material material, BlockFace facing) {
            if (ours(u, y, v))
                chunk.setBlock(localX(u, v), y, localZ(u, v), material, facing);
        }

        private void setSlab(int u, int y, int v) {
            if (ours(u, y, v))
                chunk.setBlock(localX(u, v), y, localZ(u, v), slab, SlabType.TOP);
        }

        private void setStair(int u, int y, int v, BlockFace facing) {
            if (ours(u, y, v))
                chunk.setStair(localX(u, v), y, localZ(u, v), stairs, facing);
        }

        private void hang(int u, int y, int v) {
            if (ours(u, y, v))
                chunk.setHangingLantern(localX(u, v), y, localZ(u, v), Material.LANTERN);
        }
    }

    /** The saucer a bunker occasionally keeps (called with legs off from the air lots, on from the bunker). */
    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int y, boolean drawLegs) {
        generateSaucer(generator, chunk, 7, y, 7, drawLegs);
    }

    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
            boolean drawLegs) {
        if (drawLegs) {
            drawSaucer(chunk, x, y + 2, z);
            chunk.setBlocks(x - 3, y, y + 2, z - 3, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x + 2, y, y + 2, z - 3, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x - 3, y, y + 2, z + 2, Material.QUARTZ_BLOCK);
            chunk.setBlocks(x + 2, y, y + 2, z + 2, Material.QUARTZ_BLOCK);
        } else
            drawSaucer(chunk, x, y, z);
    }

    // ---------------------------------------------------------------------------------------------

    private void drawBigBalloon(CityWorldGenerator generator, SupportBlocks chunk, int balloonY1, int balloonY2,
            Odds odds, boolean hollow) {
        Colors colors = new Colors(odds, ColorSet.LIGHT);
        Material primary = colors.getConcrete();
        Material secondary = hollow ? colors.getGlass() : colors.getConcrete();

        // bottom taper
        chunk.setCircle(8, 8, 3, balloonY1 - 1, primary, false);
        chunk.setCircle(8, 8, 4, balloonY1, balloonY1 + 4, primary, true);
        chunk.setCircle(8, 8, 5, balloonY1 + 4, balloonY1 + 7, primary, true);
        chunk.setCircle(8, 8, 6, balloonY1 + 7, primary, true);
        if (hollow) {
            chunk.setCircle(8, 8, 3, balloonY1, balloonY1 + 4, Material.AIR, true);
            chunk.setCircle(8, 8, 4, balloonY1 + 4, balloonY1 + 7, Material.AIR, true);
            chunk.setCircle(8, 8, 5, balloonY1 + 7, Material.AIR, true);
        }

        // striped middle
        int step = 2 + odds.getRandomInt(4);
        int y = balloonY1 + 8;
        do {
            chunk.setCircle(8, 8, 6, y, y % step != 0 ? secondary : primary, true);
            if (hollow)
                chunk.setCircle(8, 8, 5, y, Material.AIR, true);
            y++;
        } while (y < balloonY2 - 3);

        // top taper
        chunk.setCircle(8, 8, 6, balloonY2 - 3, primary, true);
        chunk.setCircle(8, 8, 5, balloonY2 - 2, balloonY2 - 1, primary, true);
        chunk.setCircle(8, 8, 4, balloonY2 - 1, balloonY2, primary, true);
        chunk.setCircle(8, 8, 3, balloonY2, secondary, true);
        if (hollow) {
            chunk.setCircle(8, 8, 5, balloonY2 - 3, Material.AIR, true);
            chunk.setCircle(8, 8, 4, balloonY2 - 2, balloonY2 - 1, Material.AIR, true);
            chunk.setCircle(8, 8, 3, balloonY2 - 1, balloonY2, Material.AIR, true);
        }

        // a fire burner under the hollow envelope
        if (hollow) {
            chunk.setBlocks(7, 9, balloonY1 - 2, 7, 9, Material.STONE_SLAB, SlabType.TOP);
            chunk.setBlocks(7, 9, balloonY1 - 1, 7, 9, Material.NETHERRACK);
            chunk.setBlocks(7, 9, balloonY1, 7, 9, Material.FIRE);
            chunk.setBlocks(7, 8, balloonY1 - 1, 5, 7, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
            chunk.setBlocks(8, 9, balloonY1 - 1, 9, 11, Material.IRON_BARS, BlockFace.NORTH, BlockFace.SOUTH);
            chunk.setBlocks(9, 11, balloonY1 - 1, 7, 8, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
            chunk.setBlocks(5, 7, balloonY1 - 1, 8, 9, Material.IRON_BARS, BlockFace.EAST, BlockFace.WEST);
        }
    }

    private void drawSaucer(SupportBlocks chunk, int x, int y, int z) {
        chunk.setCircle(x, z, 4, y, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 1, y, Material.GLASS, true);
        chunk.setCircle(x, z, 5, y + 1, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 2, y + 1, Material.GLASS, true);
        chunk.setCircle(x, z, 4, y + 2, Material.REDSTONE_BLOCK, true);
        chunk.setCircle(x, z, 2, y + 3, Material.QUARTZ_BLOCK, true);
        chunk.setCircle(x, z, 1, y + 4, Material.GLASS, true);
    }

    private boolean attachString(AbstractBlocks chunk, int x, int y1, int y2, int z) {
        boolean anchored = !chunk.isEmpty(x, y1 - 1, z);
        if (anchored)
            chunk.setBlocks(x, y1, y2, z, Material.IRON_BARS);
        return anchored;
    }
}
