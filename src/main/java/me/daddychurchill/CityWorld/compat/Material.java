package me.daddychurchill.CityWorld.compat;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Port shim for {@code org.bukkit.Material} (block half only).
 *
 * <p>The original CityWorld generator passes Bukkit {@code Material} constants as a block
 * vocabulary (~557 constants, almost never switched-on). This class replaces that vocabulary with
 * interned wrappers around vanilla {@link Block}/{@link BlockState}, so ported call sites keep
 * their {@code Material.STONE} shape and only change an import.
 *
 * <p>Because Bukkit encoded orientation in separate {@code BlockFace}/slab/half arguments (applied
 * at placement, not baked into the constant), a {@code Material} here holds a block's <em>default
 * state</em> and exposes {@link #withFacing}, {@link #withFaces}, {@link #asSlab} and
 * {@link #asDoorHalf} to derive the oriented {@link BlockState} the way {@code InitialBlocks} used
 * to via Bukkit's {@code BlockData} interfaces.
 *
 * <p>Instances are interned per {@link Block}, so {@code ==}/{@link #equals} compare block type
 * (ignoring state) — matching how the old code compared {@code Material}s.
 *
 * <p>Only a representative constant set is defined here; exhaustive coverage of the referenced
 * names is Phase 2 (see {@code PORTING.md}). Unlisted blocks resolve by id via {@link #of(String)}.
 */
public final class Material {

    private static final ConcurrentHashMap<Block, Material> INTERN = new ConcurrentHashMap<>();

    private final Block block;
    private final BlockState defaultState;

    private Material(Block block) {
        this.block = block;
        this.defaultState = block.defaultBlockState();
    }

    /** Interned wrapper for a vanilla block. */
    public static Material of(Block block) {
        return INTERN.computeIfAbsent(block, Material::new);
    }

    /**
     * Resolve a block by (namespaced or bare) id, e.g. {@code "stone"} or {@code "minecraft:stone"}.
     * Falls back to {@link #AIR} if unknown. Requires the block registry to be loaded (true during
     * world generation); prefer the typed constants for anything referenced at class-load time.
     */
    public static Material of(String id) {
        Identifier key = id.indexOf(':') >= 0 ? Identifier.parse(id) : Identifier.withDefaultNamespace(id);
        Block resolved = BuiltInRegistries.BLOCK.getValue(key);
        return of(resolved == null ? Blocks.AIR : resolved);
    }

    public Block getBlock() {
        return block;
    }

    /** The block's default {@link BlockState} (unoriented). */
    public BlockState getBlockState() {
        return defaultState;
    }

    /** Approximates Bukkit {@code Material.name()} — the block's registry path, upper-cased. */
    public String name() {
        return BuiltInRegistries.BLOCK.getKey(block).getPath().toUpperCase(Locale.ROOT);
    }

    public boolean is(Material other) {
        return other != null && other.block == block;
    }

    // ---- Orientation derivation (mirrors the old InitialBlocks BlockData logic) --------------

    /**
     * Apply a single facing, matching the old {@code Directional / MultipleFacing / Orientable}
     * fallback chain: a facing property if present, else a single connection face, else an axis.
     */
    public BlockState withFacing(BlockFace facing) {
        BlockState state = defaultState;
        Direction dir = facing.toDirection();
        if (dir != null && state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, dir);
        }
        if (dir != null && dir.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        }
        Property<Boolean> faceProp = booleanFaceProperty(facing);
        if (faceProp != null && state.hasProperty(faceProp)) {
            return state.setValue(faceProp, true);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.setValue(BlockStateProperties.AXIS, axisFor(facing));
        }
        return state;
    }

    /** Set several connection faces true (fences, walls, panes, glass). */
    public BlockState withFaces(BlockFace... faces) {
        BlockState state = defaultState;
        for (BlockFace face : faces) {
            Property<Boolean> faceProp = booleanFaceProperty(face);
            if (faceProp != null && state.hasProperty(faceProp)) {
                state = state.setValue(faceProp, true);
            }
        }
        return state;
    }

    /** The block as a slab of the given type (top / bottom / double), if it is a slab. */
    public BlockState asSlab(SlabType type) {
        BlockState state = defaultState;
        return state.hasProperty(BlockStateProperties.SLAB_TYPE)
                ? state.setValue(BlockStateProperties.SLAB_TYPE, type)
                : state;
    }

    /**
     * One half of a two-tall block (door), facing the given direction. Handles both the modern
     * {@code DOUBLE_BLOCK_HALF} (doors) and the {@code HALF} (stairs/trapdoors) properties.
     */
    public BlockState asDoorHalf(boolean top, BlockFace facing) {
        BlockState state = defaultState;
        Direction dir = facing.toDirection();
        if (dir != null && dir.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        }
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            state = state.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF,
                    top ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER);
        } else if (state.hasProperty(BlockStateProperties.HALF)) {
            state = state.setValue(BlockStateProperties.HALF, top ? Half.TOP : Half.BOTTOM);
        }
        return state;
    }

    private static Property<Boolean> booleanFaceProperty(BlockFace face) {
        switch (face) {
            case NORTH: return BlockStateProperties.NORTH;
            case EAST:  return BlockStateProperties.EAST;
            case SOUTH: return BlockStateProperties.SOUTH;
            case WEST:  return BlockStateProperties.WEST;
            case UP:    return BlockStateProperties.UP;
            case DOWN:  return BlockStateProperties.DOWN;
            default:    return null;
        }
    }

    private static Direction.Axis axisFor(BlockFace facing) {
        switch (facing) {
            case NORTH:
            case SOUTH:
                return Direction.Axis.Z;
            case EAST:
            case WEST:
                return Direction.Axis.X;
            default:
                return Direction.Axis.Y;
        }
    }

    @Override
    public boolean equals(Object o) {
        return this == o || (o instanceof Material && ((Material) o).block == block);
    }

    @Override
    public int hashCode() {
        return block.hashCode();
    }

    @Override
    public String toString() {
        return "Material(" + name() + ")";
    }

    // ---- Representative constants (terrain + common building blocks) -------------------------
    // Exhaustive coverage of the ~557 referenced names is Phase 2; unlisted blocks use of(String).

    public static final Material AIR = of(Blocks.AIR);
    public static final Material CAVE_AIR = of(Blocks.CAVE_AIR);
    public static final Material BEDROCK = of(Blocks.BEDROCK);
    public static final Material STONE = of(Blocks.STONE);
    public static final Material GRANITE = of(Blocks.GRANITE);
    public static final Material DIORITE = of(Blocks.DIORITE);
    public static final Material ANDESITE = of(Blocks.ANDESITE);
    public static final Material DEEPSLATE = of(Blocks.DEEPSLATE);
    public static final Material COBBLESTONE = of(Blocks.COBBLESTONE);
    public static final Material DIRT = of(Blocks.DIRT);
    public static final Material COARSE_DIRT = of(Blocks.COARSE_DIRT);
    public static final Material GRASS_BLOCK = of(Blocks.GRASS_BLOCK);
    public static final Material PODZOL = of(Blocks.PODZOL);
    public static final Material SAND = of(Blocks.SAND);
    public static final Material RED_SAND = of(Blocks.RED_SAND);
    public static final Material GRAVEL = of(Blocks.GRAVEL);
    public static final Material CLAY = of(Blocks.CLAY);
    public static final Material SANDSTONE = of(Blocks.SANDSTONE);
    public static final Material WATER = of(Blocks.WATER);
    public static final Material LAVA = of(Blocks.LAVA);
    public static final Material ICE = of(Blocks.ICE);
    public static final Material PACKED_ICE = of(Blocks.PACKED_ICE);
    public static final Material SNOW_BLOCK = of(Blocks.SNOW_BLOCK);
    public static final Material SNOW = of(Blocks.SNOW);
    public static final Material OBSIDIAN = of(Blocks.OBSIDIAN);
    public static final Material GLASS = of(Blocks.GLASS);
    public static final Material GLASS_PANE = of(Blocks.GLASS_PANE);
    public static final Material GLOWSTONE = of(Blocks.GLOWSTONE);
    public static final Material SEA_LANTERN = of(Blocks.SEA_LANTERN);
    public static final Material TORCH = of(Blocks.TORCH);
    public static final Material IRON_BARS = of(Blocks.IRON_BARS);
    public static final Material IRON_BLOCK = of(Blocks.IRON_BLOCK);
    public static final Material GOLD_BLOCK = of(Blocks.GOLD_BLOCK);
    public static final Material BRICKS = of(Blocks.BRICKS);
    public static final Material STONE_BRICKS = of(Blocks.STONE_BRICKS);
    public static final Material SMOOTH_STONE = of(Blocks.SMOOTH_STONE);
    public static final Material QUARTZ_BLOCK = of(Blocks.QUARTZ_BLOCK);
    public static final Material BOOKSHELF = of(Blocks.BOOKSHELF);
    public static final Material OAK_PLANKS = of(Blocks.OAK_PLANKS);
    public static final Material OAK_LOG = of(Blocks.OAK_LOG);
    public static final Material OAK_LEAVES = of(Blocks.OAK_LEAVES);
    public static final Material OAK_SLAB = of(Blocks.OAK_SLAB);
    public static final Material OAK_STAIRS = of(Blocks.OAK_STAIRS);
    public static final Material OAK_FENCE = of(Blocks.OAK_FENCE);
    public static final Material OAK_DOOR = of(Blocks.OAK_DOOR);
}
