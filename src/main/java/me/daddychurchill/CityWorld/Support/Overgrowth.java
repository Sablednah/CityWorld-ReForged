package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The MODERN "overgrown" pass — nature reclaiming the built world. Runs once per built lot (buildings,
 * roads, roundabouts, placed schematics), on the live decoration level, <em>after</em> the lot's own
 * decoration and any decay, so the greenery it drapes is never itself chewed up. Works with or without
 * decay: moss and vines on a clean city read as overgrown just as well as on a ruined one.
 *
 * <p>All writes stay inside the decorating chunk's own columns (the same edge rule the rest of the
 * decoration side follows). Every plant is checked with {@link BlockState#canSurvive} before it lands,
 * so nothing pops on the next block update — a fern won't stick to a stone roof, but moss carpet will.
 */
public final class Overgrowth {

    private Overgrowth() {}

    /** Sprinkle overgrowth across one built chunk. {@code odds} is the lot's per-chunk RNG. */
    public static void apply(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, Odds odds) {
        if (!(chunk instanceof RealBlocks real))
            return;
        ServerLevelAccessor level = real.getServerLevel();
        if (level == null)
            return;

        boolean road = lot.style == PlatLot.LotStyle.ROAD || lot.style == PlatLot.LotStyle.ROUNDABOUT;
        int oX = real.getOriginX(), oZ = real.getOriginZ();
        int floor = generator.streetLevel - 6; // below this is basement/underground, left to the mines
        // density multiplier — 1.0 default, cranked up by the overgrowthIntensity setting for a heavier look
        double intensity = Math.max(0.1, Math.min(6.0, generator.getSettings().overgrowthIntensity));
        // cap the OUTER wall strings with a glow lichen at their bottom so live vine growth can't run them
        // longer over time (and it reads as a lit tip)
        boolean capVines = generator.getSettings().capVines;

        // 1) tops — moss/leaf-litter/plants creeping over every exposed built surface (roofs, floors,
        //    road, the ground around a build), plus the odd mossy-brick swap for age.
        for (int x = 0; x < real.width; x++)
            for (int z = 0; z < real.width; z++) {
                int wx = oX + x, wz = oZ + z;
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
                if (surfaceY - 1 < floor)
                    continue;
                BlockPos topPos = new BlockPos(wx, surfaceY - 1, wz);
                BlockState top = level.getBlockState(topPos);
                if (top.isAir() || !top.getFluidState().isEmpty())
                    continue;
                // Weather some stone/brick/cobble surfaces to mossy first — this DOES apply to the slab,
                // stair and wall shapes (they keep their form), which is the point.
                if (odds.playOdds(0.16))
                    mossySwap(level, topPos, top);
                // Plants, though, only sit on a full flat top. Moss/leaf-litter dropped onto a half-slab,
                // fence post or stairs float a notch above and read wrong — a sturdy-up-face test skips
                // all of those and keeps the loose overgrowth to proper block tops.
                if (!top.isFaceSturdy(level, topPos, Direction.UP))
                    continue;
                BlockPos onTop = new BlockPos(wx, surfaceY, wz);
                // roads carry only a light dusting (mostly thin leaf litter) so the streets stay readable;
                // buildings/ground get the fuller creep. Scaled by intensity (capped so it never fully tiles).
                if (level.getBlockState(onTop).isAir() && odds.playOdds(Math.min(0.85, (road ? 0.12 : 0.24) * intensity)))
                    placeTopPlant(level, onTop, road, odds);
            }

        // 2a) interior nooks — the bit that already read well: from a column's top scan DOWN to the first
        //     air cell against a wall (a courtyard, a gap between rooms) and tuck a short vine there.
        int nookTries = (int) Math.ceil((road ? 4 : 24) * intensity);
        for (int i = 0; i < nookTries; i++) {
            int x = odds.getRandomInt(real.width), z = odds.getRandomInt(real.width);
            int wx = oX + x, wz = oZ + z;
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
            for (int y = surfaceY - 1; y > floor && y > surfaceY - 24; y--) {
                if (!level.getBlockState(new BlockPos(wx, y, wz)).isAir())
                    continue;
                Direction dir = wallDir(level, wx, y, wz);
                if (dir == null)
                    continue;
                hangVineString(level, wx, y, wz, dir, 1 + odds.getRandomInt(3), odds, false); // nooks: no cap
                break;
            }
        }

        // 2b) OUTER walls — the bit that was missing. Outer building faces rise ABOVE the ground outside
        //     the building, so scan UP from a column's exterior surface, find the highest air cell that
        //     still has a wall beside it (up at the roofline), and hang a full vine string DOWN from there
        //     — strings biased to the top, dangling down. The wall may be in the neighbouring chunk (an
        //     outer face sits right on the chunk seam): the vine is written in THIS chunk, only the wall is
        //     read across the boundary (guarded). Roads carry this since they flank the buildings.
        int wallTries = (int) Math.ceil((road ? 40 : 30) * intensity);
        int ceilingY = generator.streetLevel + 200; // above the tallest building; the scan breaks at a roof
        for (int i = 0; i < wallTries; i++) {
            int x = odds.getRandomInt(real.width), z = odds.getRandomInt(real.width);
            int wx = oX + x, wz = oZ + z;
            int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
            // quick reject: no wall beside the column just above ground -> open space, don't scan the sky
            if (wallDir(level, wx, surfaceY + 1, wz) == null && wallDir(level, wx, surfaceY + 2, wz) == null)
                continue;
            int topY = -1;
            Direction topDir = null;
            for (int y = surfaceY + 1; y <= ceilingY; y++) {
                if (!level.getBlockState(new BlockPos(wx, y, wz)).isAir())
                    break; // a roof/overhang closes the column above here
                Direction dir = wallDir(level, wx, y, wz);
                if (dir != null) {
                    topY = y;
                    topDir = dir;
                }
            }
            if (topY >= 0) {
                // Length is a random fraction of the wall's own height, anywhere from a quarter to full —
                // so a facade reads as a varied mix of stubby and long strings, not a uniform full-height
                // curtain. (Intensity drives how MANY strings, above, not how long each one is.) The string
                // then dangles down until the wall runs out (a window/ledge stops it early).
                int wallH = Math.max(4, topY - generator.streetLevel);
                int len = wallH / 4 + odds.getRandomInt(wallH - wallH / 4 + 1);
                hangVineString(level, wx, topY, wz, topDir, len, odds, capVines);
            }
        }

        // 3) roads — the occasional sapling breaking through the tarmac into a small reclaim tree (more
        //    chances at higher intensity).
        int treeTries = road ? (int) Math.ceil(intensity) : 0;
        for (int i = 0; i < treeTries; i++)
            if (odds.playOdds(0.18))
                placeSmallTree(level, oX, oZ, floor, real.width, odds);

        // 4) basements — dripstone reclaiming a building's cellars, the same as the mine shafts. Buildings
        //    only (roads have no rooms below); scans the below-street air for a room ceiling/floor.
        if (!road)
            dripstoneUnderground(real, level, oX, oZ, generator.streetLevel, odds);
    }

    /** Sprinkle dripstone into the enclosed air rooms below street level (basements/cellars). */
    private static void dripstoneUnderground(RealBlocks real, ServerLevelAccessor level, int oX, int oZ,
            int street, Odds odds) {
        int bottom = real.minY + 1;
        for (int i = 0; i < 22; i++) {
            int wx = oX + odds.getRandomInt(real.width), wz = oZ + odds.getRandomInt(real.width);
            for (int y = street - 2; y > bottom + 1; y--) {
                if (!level.getBlockState(new BlockPos(wx, y, wz)).isAir())
                    continue;
                boolean ceiling = !level.getBlockState(new BlockPos(wx, y + 1, wz)).isAir();
                boolean floorBelow = !level.getBlockState(new BlockPos(wx, y - 1, wz)).isAir();
                if (ceiling && odds.flipCoin()) {
                    growDripstone(level, wx, y, wz, Direction.DOWN, odds);
                    break;
                }
                if (floorBelow) {
                    growDripstone(level, wx, y, wz, Direction.UP, odds);
                    break;
                }
            }
        }
    }

    /**
     * Underground reclamation for an overgrown world: dripstone taking over an abandoned mine corridor —
     * stalactites hanging from the ceiling, stalagmites rising from the floor, the odd dripstone-block
     * clump. Called from the mine decoration (which knows the corridor's floor), gated on the setting.
     * The corridor is a 3-high cut: floor at {@code floorY}, air at {@code floorY+1/+2}, ceiling at
     * {@code floorY+3}.
     */
    public static void dripstoneMine(SupportBlocks chunk, int floorY, Odds odds) {
        if (!(chunk instanceof RealBlocks real))
            return;
        ServerLevelAccessor level = real.getServerLevel();
        if (level == null)
            return;
        int oX = real.getOriginX(), oZ = real.getOriginZ(), ceilY = floorY + 3;
        int count = 3 + odds.getRandomInt(5);
        for (int i = 0; i < count; i++) {
            int x = odds.getRandomInt(1, 15), z = odds.getRandomInt(1, 15);
            if (odds.flipCoin()) {
                if (!real.isEmpty(x, ceilY, z) && real.isEmpty(x, ceilY - 1, z))
                    growDripstone(level, oX + x, ceilY - 1, oZ + z, Direction.DOWN, odds); // stalactite
            } else if (!real.isEmpty(x, floorY, z) && real.isEmpty(x, floorY + 1, z))
                growDripstone(level, oX + x, floorY + 1, oZ + z, Direction.UP, odds); // stalagmite
        }
        for (int i = 0; i < 2; i++) { // a couple of dripstone-block clumps in the ceiling
            int x = odds.getRandomInt(1, 15), z = odds.getRandomInt(1, 15);
            if (!real.isEmpty(x, ceilY, z) && odds.playOdds(0.4))
                level.setBlock(new BlockPos(oX + x, ceilY, oZ + z), Material.DRIPSTONE_BLOCK.getBlockState(),
                        Block.UPDATE_CLIENTS);
        }
    }

    /** A 1-2 tall pointed-dripstone spike from an anchored cell, growing in {@code dir}. */
    private static void growDripstone(ServerLevelAccessor level, int wx, int anchorY, int wz, Direction dir,
            Odds odds) {
        int step = dir == Direction.DOWN ? -1 : 1;
        boolean tall = odds.playOdds(0.4) && level.getBlockState(new BlockPos(wx, anchorY + step, wz)).isAir();
        if (tall) {
            setDrip(level, wx, anchorY, wz, dir, net.minecraft.world.level.block.state.properties.DripstoneThickness.FRUSTUM);
            setDrip(level, wx, anchorY + step, wz, dir, net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
        } else {
            setDrip(level, wx, anchorY, wz, dir, net.minecraft.world.level.block.state.properties.DripstoneThickness.TIP);
        }
    }

    private static void setDrip(ServerLevelAccessor level, int wx, int y, int wz, Direction dir,
            net.minecraft.world.level.block.state.properties.DripstoneThickness thickness) {
        level.setBlock(new BlockPos(wx, y, wz), Blocks.POINTED_DRIPSTONE.defaultBlockState()
                .setValue(BlockStateProperties.VERTICAL_DIRECTION, dir)
                .setValue(BlockStateProperties.DRIPSTONE_THICKNESS, thickness), Block.UPDATE_CLIENTS);
    }

    /**
     * Weather a stone-brick / cobblestone / stone surface into its mossy variant — the full block and
     * its slab, stair and wall shapes alike (shape and orientation carried over, so a bottom slab stays a
     * bottom slab). Plain stone has no mossy form of its own, so it (and its slab/stairs) ages toward
     * mossy cobblestone, which reads as old weathered stone.
     */
    private static void mossySwap(ServerLevelAccessor level, BlockPos pos, BlockState top) {
        Block moss = mossyOf(top.getBlock());
        if (moss != null)
            level.setBlock(pos, reshape(top, moss), Block.UPDATE_CLIENTS);
    }

    private static Block mossyOf(Block b) {
        // stone-brick family -> mossy stone brick
        if (b == Blocks.STONE_BRICKS) return Blocks.MOSSY_STONE_BRICKS;
        if (b == Blocks.STONE_BRICK_SLAB) return Blocks.MOSSY_STONE_BRICK_SLAB;
        if (b == Blocks.STONE_BRICK_STAIRS) return Blocks.MOSSY_STONE_BRICK_STAIRS;
        if (b == Blocks.STONE_BRICK_WALL) return Blocks.MOSSY_STONE_BRICK_WALL;
        // cobblestone family -> mossy cobblestone
        if (b == Blocks.COBBLESTONE) return Blocks.MOSSY_COBBLESTONE;
        if (b == Blocks.COBBLESTONE_SLAB) return Blocks.MOSSY_COBBLESTONE_SLAB;
        if (b == Blocks.COBBLESTONE_STAIRS) return Blocks.MOSSY_COBBLESTONE_STAIRS;
        if (b == Blocks.COBBLESTONE_WALL) return Blocks.MOSSY_COBBLESTONE_WALL;
        // plain stone has no mossy form -> age toward mossy cobble
        if (b == Blocks.STONE) return Blocks.MOSSY_COBBLESTONE;
        if (b == Blocks.STONE_SLAB) return Blocks.MOSSY_COBBLESTONE_SLAB;
        if (b == Blocks.STONE_STAIRS) return Blocks.MOSSY_COBBLESTONE_STAIRS;
        return null;
    }

    /** The target block's default state with every shared property (slab type, stair facing, …) carried
     *  over from the source, so a swap keeps the exact shape and orientation. */
    private static BlockState reshape(BlockState from, Block to) {
        BlockState result = to.defaultBlockState();
        for (net.minecraft.world.level.block.state.properties.Property<?> p : from.getProperties())
            result = carry(from, result, p);
        return result;
    }

    private static <T extends Comparable<T>> BlockState carry(BlockState from, BlockState to,
            net.minecraft.world.level.block.state.properties.Property<T> p) {
        return to.hasProperty(p) ? to.setValue(p, from.getValue(p)) : to;
    }

    /** Drop a surface plant onto {@code pos}, preferring what can actually live on the block below it. */
    private static void placeTopPlant(ServerLevelAccessor level, BlockPos pos, boolean road, Odds odds) {
        // Weighted pool. Carpets/litter sit on any solid top; the leafy plants only take on soil, so
        // canSurvive() decides — a fern rolled onto a stone roof simply falls through to moss carpet.
        int r = odds.getRandomInt(100);
        Material pick;
        if (road)
            // roads lean to thin, flat litter/grass rather than chunky moss carpet
            pick = r < 62 ? Material.LEAF_LITTER : r < 74 ? Material.MOSS_CARPET
                    : r < 88 ? Material.SHORT_GRASS : r < 96 ? Material.FERN : Material.PINK_PETALS;
        else
            pick = r < 32 ? Material.MOSS_CARPET : r < 55 ? Material.LEAF_LITTER
                    : r < 66 ? Material.SHORT_GRASS : r < 74 ? Material.FERN
                    : r < 80 ? Material.PINK_PETALS : r < 86 ? Material.AZALEA
                    : r < 90 ? Material.FLOWERING_AZALEA : r < 94 ? Material.SMALL_DRIPLEAF
                    : r < 97 ? Material.PALE_MOSS_CARPET
                    : r < 99 ? Material.BROWN_MUSHROOM : Material.RED_MUSHROOM;

        if (!tryPlace(level, pos, pick.getBlockState()))
            tryPlace(level, pos, Material.MOSS_CARPET.getBlockState()); // always-safe fallback
    }

    private static boolean tryPlace(ServerLevelAccessor level, BlockPos pos, BlockState state) {
        if (!state.canSurvive(level, pos))
            return false;
        level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /** The direction from the air cell {@code (wx,y,wz)} to an adjacent solid wall face, or null. Reads
     *  the neighbour even across a chunk boundary (guarded by {@code hasChunk}), so a wall on the seam is
     *  still found — the caller only ever WRITES the vine into the air cell itself, which is in-chunk. */
    private static Direction wallDir(ServerLevelAccessor level, int wx, int y, int wz) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            int nwx = wx + dir.getStepX(), nwz = wz + dir.getStepZ();
            if (!level.hasChunk(nwx >> 4, nwz >> 4))
                continue;
            BlockPos wall = new BlockPos(nwx, y, nwz);
            if (level.getBlockState(wall).isFaceSturdy(level, wall, dir.getOpposite()))
                return dir;
        }
        return null;
    }

    /** Hang a vine string from {@code (wx,y,wz)} straight down the wall on face {@code dir}, up to
     *  {@code len} blocks, stopping where the wall or the air runs out. The odd string is glow lichen. When
     *  {@code cap} is set, the cell just below the string gets a glow lichen (if a wall backs it) so live
     *  vine growth can't extend it later — a lit tip that caps the string. */
    private static void hangVineString(ServerLevelAccessor level, int wx, int y, int wz, Direction dir, int len,
            Odds odds, boolean cap) {
        boolean lichen = odds.playOdds(0.12);
        int bottom = y + 1; // one above the top; becomes the last placed row
        for (int k = 0; k < len; k++) {
            BlockPos cell = new BlockPos(wx, y - k, wz);
            if (!level.getBlockState(cell).isAir())
                break;
            BlockPos wall = cell.relative(dir);
            if (!level.hasChunk(wall.getX() >> 4, wall.getZ() >> 4)
                    || !level.getBlockState(wall).isFaceSturdy(level, wall, dir.getOpposite()))
                break; // wall ended (a window/ledge) — stop the string here
            BlockState face = (lichen ? Material.GLOW_LICHEN.getBlockState() : Blocks.VINE.defaultBlockState())
                    .setValue(faceProp(dir), true);
            level.setBlock(cell, face, Block.UPDATE_CLIENTS);
            bottom = y - k;
        }
        if (cap && bottom <= y) { // something was placed
            BlockPos below = new BlockPos(wx, bottom - 1, wz);
            BlockPos wall = below.relative(dir);
            if (level.getBlockState(below).isAir() && level.hasChunk(wall.getX() >> 4, wall.getZ() >> 4)
                    && level.getBlockState(wall).isFaceSturdy(level, wall, dir.getOpposite()))
                level.setBlock(below, Material.GLOW_LICHEN.getBlockState().setValue(faceProp(dir), true),
                        Block.UPDATE_CLIENTS);
        }
    }

    private static net.minecraft.world.level.block.state.properties.BooleanProperty faceProp(Direction dir) {
        return switch (dir) {
            case NORTH -> BlockStateProperties.NORTH;
            case SOUTH -> BlockStateProperties.SOUTH;
            case WEST -> BlockStateProperties.WEST;
            default -> BlockStateProperties.EAST;
        };
    }

    /** A tiny reclaim tree: a short trunk pushing up through the road, capped with a small leaf ball. */
    private static void placeSmallTree(ServerLevelAccessor level, int oX, int oZ, int floor, int width, Odds odds) {
        int x = odds.getRandomInt(2, width - 3), z = odds.getRandomInt(2, width - 3); // keep the ball in-chunk
        int wx = oX + x, wz = oZ + z;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz);
        if (surfaceY - 1 < floor)
            return;
        BlockState below = level.getBlockState(new BlockPos(wx, surfaceY - 1, wz));
        if (below.isAir() || !below.getFluidState().isEmpty())
            return;
        int trunk = 1 + odds.getRandomInt(2); // 1..2 logs
        for (int dy = 0; dy < trunk; dy++)
            level.setBlock(new BlockPos(wx, surfaceY + dy, wz), Blocks.OAK_LOG.defaultBlockState(), Block.UPDATE_CLIENTS);
        int topY = surfaceY + trunk;
        BlockState leaf = Blocks.OAK_LEAVES.defaultBlockState().setValue(BlockStateProperties.PERSISTENT, true);
        // a compact ball: the four sides at the top log, a cap above
        leafAt(level, wx, topY - 1, wz, 1, 0, leaf);
        leafAt(level, wx, topY - 1, wz, -1, 0, leaf);
        leafAt(level, wx, topY - 1, wz, 0, 1, leaf);
        leafAt(level, wx, topY - 1, wz, 0, -1, leaf);
        setIfAir(level, new BlockPos(wx, topY, wz), leaf);
        setIfAir(level, new BlockPos(wx, topY + 1, wz), leaf);
    }

    private static void leafAt(ServerLevelAccessor level, int wx, int y, int wz, int dx, int dz, BlockState leaf) {
        setIfAir(level, new BlockPos(wx + dx, y, wz + dz), leaf);
    }

    private static void setIfAir(ServerLevelAccessor level, BlockPos pos, BlockState state) {
        if (level.getBlockState(pos).isAir())
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
    }
}
