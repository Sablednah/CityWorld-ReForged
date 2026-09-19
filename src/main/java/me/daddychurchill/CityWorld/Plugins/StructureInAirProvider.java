package me.daddychurchill.CityWorld.Plugins;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.MaterialTags;
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
     * livery with four tail fins, and a rounded, glazed control car slung underneath with a stern
     * observation deck and an engine and propeller either side, in a random wood. Both halves call this
     * with the same dice, and each draws only the cells of the shared 32×16 frame that fall inside its own
     * chunk, so they meet at the seam.
     *
     * @param keelY      the control car's floor; the envelope's belly is six blocks above it
     * @param alongX     the ship's length runs along X (a west-east pair) rather than Z
     * @param secondHalf false for the west/north chunk, true for the east/south one
     */
    public void generateAirship(CityWorldGenerator generator, SupportBlocks chunk, int keelY, boolean alongX,
            boolean secondHalf, Odds odds) {
        new Airship(chunk, keelY, alongX, secondHalf, odds).draw();
    }

    /**
     * The airship's control car as it lands for one heading: position (along x, height above the keel,
     * along z, within the two-chunk footprint) to block state, in spruce. Built with the same mapping and
     * rotate/mirror as placement, without a chunk, so the self-test can check that the stencil turns
     * correctly in every heading — one generated airship only ever shows one.
     */
    public static Map<BlockPos, BlockState> airshipCarLayout(boolean alongX, boolean bowPositive) {
        return Airship.layout(alongX, bowPositive, "minecraft:spruce");
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
     * The airship. The envelope and fins are drawn from geometry; the control car — cabin, observation deck,
     * engines, propellers and the hangers up to the envelope — is placed from a stencil of exact block states,
     * {@code /cityworld/airship/car.txt}. That stencil is the owner's own in-game rework of the first
     * generated car, read back out of the save: rounded bow and stern, a squarer propeller joined to its
     * nacelle.
     *
     * <p>Positions use a frame independent of heading: {@code u} runs stern (0) to bow (31) along the two
     * chunks, {@code v} across (0..15), {@code y} is world height. Every write goes through {@link #ours},
     * which maps the frame onto this chunk and drops what belongs to the other half.
     *
     * <p><b>Why a stencil of states and vanilla's rotate/mirror, not hand-placed blocks.</b> The stencil was
     * captured from a ship lying west-east with its bow to the west. {@link BlockState#rotate} and
     * {@link BlockState#mirror} turn it to any other heading and also turn stair corner shapes and fence and
     * pane connections, which is exactly what a hand-written rotation gets subtly wrong.
     */
    private static final class Airship {
        private static final int LENGTH = 32;
        private static final double MID_U = 16.0, MID_V = 8.0, RADIUS = 6.0, SQUASH = 0.85;
        private static final String CAR_STENCIL = "/cityworld/airship/car.txt";

        private final SupportBlocks chunk;
        private final int keelY, shift;
        private final boolean alongX, bowPositive;
        private final double midY;
        private final Material hull, trim;
        /** The wood family's id stem, e.g. {@code minecraft:spruce}; {@code WOOD_stairs} becomes its stairs. */
        private final String wood;
        private final Material passengerSeat, crewSeat;
        private final Rotation rotation;
        private final Mirror mirror;
        private final BlockFace plusU;

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
            wood = pickWood(odds);
            passengerSeat = FurnitureTags.pick(FurnitureTags.CHAIR, odds); // one roll for every passenger seat
            crewSeat = FurnitureTags.pick(FurnitureTags.CHAIR, odds); // and one for the crew at the helm

            plusU = alongX ? (bowPositive ? BlockFace.EAST : BlockFace.WEST)
                    : (bowPositive ? BlockFace.SOUTH : BlockFace.NORTH);
            rotation = rotationFor(alongX, bowPositive);
            mirror = mirrorFor(alongX, bowPositive);
        }

        /*
         * From the stencil's heading (west-east, bow west: x = 31 - u, z = v) to a ship's, applied as rotate
         * then mirror. West-east with the bow east flips x (FRONT_BACK). North-south with the bow south maps
         * (x, z) to (z, -x), a quarter turn anticlockwise; with the bow north it swaps x and z, which is a
         * quarter turn clockwise followed by flipping x.
         */
        private static Rotation rotationFor(boolean alongX, boolean bowPositive) {
            return alongX ? Rotation.NONE : (bowPositive ? Rotation.COUNTERCLOCKWISE_90 : Rotation.CLOCKWISE_90);
        }

        private static Mirror mirrorFor(boolean alongX, boolean bowPositive) {
            return alongX ? (bowPositive ? Mirror.FRONT_BACK : Mirror.NONE)
                    : (bowPositive ? Mirror.NONE : Mirror.FRONT_BACK);
        }

        /** See {@link StructureInAirProvider#airshipCarLayout}. */
        static Map<BlockPos, BlockState> layout(boolean alongX, boolean bowPositive, String wood) {
            Rotation rotation = rotationFor(alongX, bowPositive);
            Mirror mirror = mirrorFor(alongX, bowPositive);
            Map<BlockPos, BlockState> out = new LinkedHashMap<>();
            for (CarCell cell : carStencil()) {
                BlockState state = stateFor(cell, wood, rotation, mirror);
                if (state == null)
                    continue;
                int w = bowPositive ? cell.u() : LENGTH - 1 - cell.u();
                out.put(new BlockPos(alongX ? w : cell.v(), cell.dy(), alongX ? cell.v() : w), state);
            }
            return out;
        }

        void draw() {
            drawEnvelope();
            drawFins();
            drawCar();
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

        // ---- control car, from the stencil ------------------------------------------------------------

        /** Everything but the seats first, so a two-tall pool chair finds the cabin it has to fit inside. */
        private void drawCar() {
            List<CarCell> stencil = carStencil();
            for (CarCell cell : stencil)
                if (cell.seat() == null)
                    placeCell(cell);
            for (CarCell cell : stencil) {
                if (cell.seat() == null || !ours(cell.u(), keelY + cell.dy(), cell.v()))
                    continue;
                Material piece = "crew".equals(cell.seat()) ? crewSeat : passengerSeat;
                if (piece == null || !placeSeat(cell, piece))
                    placeCell(cell); // no furniture mod, or it did not fit: the stencil's own stair
            }
        }

        private void placeCell(CarCell cell) {
            int y = keelY + cell.dy();
            if (!ours(cell.u(), y, cell.v()))
                return;
            BlockState state = stateFor(cell);
            if (state != null)
                chunk.setBlockState(localX(cell.u(), cell.v()), y, localZ(cell.u(), cell.v()), state);
        }

        /** A pool chair where the stencil has a stair seat, its sitter looking the way the stair's did. */
        private boolean placeSeat(CarCell cell, Material piece) {
            Direction back = null;
            for (Direction d : Direction.values())
                if (d.getSerializedName().equals(cell.props().get("facing")))
                    back = d;
            if (back == null)
                return false;
            Direction look = mirror.mirror(rotation.rotate(back.getOpposite()));
            return chunk.setFurniture(localX(cell.u(), cell.v()), keelY + cell.dy(), localZ(cell.u(), cell.v()),
                    piece, FurnitureTags.facingFor(piece, BlockFace.fromDirection(look)));
        }

        private BlockState stateFor(CarCell cell) {
            return stateFor(cell, wood, rotation, mirror);
        }

        /** The stencil cell as a real state in this wood, turned to this heading. */
        private static BlockState stateFor(CarCell cell, String wood, Rotation rotation, Mirror mirror) {
            String id = cell.block().startsWith("WOOD_") ? wood + cell.block().substring(4)
                    : "minecraft:" + cell.block();
            BlockState state = Material.of(id).getBlockState();
            if (state == null || state.isAir())
                return null;
            for (Map.Entry<String, String> prop : cell.props().entrySet()) {
                Property<?> property = state.getBlock().getStateDefinition().getProperty(prop.getKey());
                if (property != null)
                    state = withValue(state, property, prop.getValue());
            }
            return state.rotate(rotation).mirror(mirror);
        }

        private static <T extends Comparable<T>> BlockState withValue(BlockState state, Property<T> property,
                String value) {
            return property.getValue(value).map(v -> state.setValue(property, v)).orElse(state);
        }

        /**
         * A wood family from {@code #cityworld:build/planks} whose stairs, slab and fence exist under the
         * planks' own name — so a mod's woods join in when they follow vanilla's naming, and one that does
         * not is simply never picked. Sorted by id (the tag resolves sorted), so both halves agree.
         */
        private static String pickWood(Odds odds) {
            List<String> woods = new ArrayList<>();
            for (Material planks : MaterialTags.resolve(MaterialTags.BUILD_PLANKS)) {
                if (planks.getBlock() == null)
                    continue;
                String id = BuiltInRegistries.BLOCK.getKey(planks.getBlock()).toString();
                if (!id.endsWith("_planks"))
                    continue;
                String stem = id.substring(0, id.length() - "_planks".length());
                if (exists(stem + "_stairs") && exists(stem + "_slab") && exists(stem + "_fence"))
                    woods.add(stem);
            }
            return woods.isEmpty() ? "minecraft:spruce" : woods.get(odds.getRandomInt(woods.size()));
        }

        private static boolean exists(String id) {
            return BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse(id));
        }

        /** One stencil cell: frame position, block (vanilla id or {@code WOOD_} slot), properties, seat role. */
        private record CarCell(int u, int dy, int v, String block, Map<String, String> props, String seat) {}

        private static volatile List<CarCell> stencil;

        private static List<CarCell> carStencil() {
            List<CarCell> cells = stencil;
            if (cells == null)
                stencil = cells = loadStencil();
            return cells;
        }

        private static List<CarCell> loadStencil() {
            List<CarCell> cells = new ArrayList<>();
            try (InputStream in = StructureInAirProvider.class.getResourceAsStream(CAR_STENCIL)) {
                if (in == null) {
                    CityWorldMod.LOGGER.warn("CityWorld: airship car stencil {} missing from the jar", CAR_STENCIL);
                    return List.of();
                }
                BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
                for (String line; (line = reader.readLine()) != null;) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#"))
                        continue;
                    String[] parts = line.split("\\s+");
                    String state = parts[3];
                    int bracket = state.indexOf('[');
                    Map<String, String> props = new LinkedHashMap<>();
                    if (bracket >= 0)
                        for (String pair : state.substring(bracket + 1, state.length() - 1).split(",")) {
                            int eq = pair.indexOf('=');
                            props.put(pair.substring(0, eq), pair.substring(eq + 1));
                        }
                    cells.add(new CarCell(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]), bracket >= 0 ? state.substring(0, bracket) : state, props,
                            parts.length > 4 ? parts[4] : null));
                }
            } catch (Exception e) {
                CityWorldMod.LOGGER.warn("CityWorld: airship car stencil {} unreadable: {}", CAR_STENCIL, e.toString());
                return List.of();
            }
            return List.copyOf(cells);
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

        private void setFacing(int u, int y, int v, Material material, BlockFace facing) {
            if (ours(u, y, v))
                chunk.setBlock(localX(u, v), y, localZ(u, v), material, facing);
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
