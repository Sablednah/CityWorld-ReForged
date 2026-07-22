package me.daddychurchill.CityWorld.compat;

import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Port shim for {@code org.bukkit.Material}.
 *
 * <p><b>Generated — do not hand-edit the constant block.</b> It is produced by
 * {@code gen_material.py} from the constants the original Bukkit source actually references,
 * cross-checked against the modern {@link Blocks}/{@link Items} fields.
 *
 * <p>The original generator passes Bukkit {@code Material} constants around as a vocabulary
 * (never switched-on anywhere in the source), so this replaces that vocabulary with interned
 * wrappers over vanilla types; ported call sites keep their {@code Material.STONE} shape and only
 * change an import.
 *
 * <p>Like Bukkit's enum, a {@code Material} may denote a <em>block</em> or an <em>item</em>:
 * block-backed materials expose a {@link BlockState}; item-only ones (loot/chest contents) do not
 * and return {@code null} from the state accessors — see {@link #isBlock()}.
 *
 * <p>Because Bukkit encoded orientation in separate {@code BlockFace}/slab/half arguments applied
 * at placement rather than baked into the constant, a material holds a block's <em>default
 * state</em> and derives oriented states via {@link #withFacing}, {@link #withFaces},
 * {@link #asSlab} and {@link #asDoorHalf}.
 *
 * <p>Instances are interned, so {@code ==}/{@link #equals} compare block (or item) identity,
 * ignoring state — matching how the old code compared {@code Material}s.
 */
public final class Material {

    private static final ConcurrentHashMap<Block, Material> BLOCK_INTERN = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Item, Material> ITEM_INTERN = new ConcurrentHashMap<>();

    private final Block block;
    private final BlockState defaultState;
    private final Item item;

    private Material(Block block) {
        this.block = block;
        this.defaultState = block.defaultBlockState();
        this.item = block.asItem();
    }

    private Material(Item item) {
        this.block = null;
        this.defaultState = null;
        this.item = item;
    }

    /** Interned wrapper for a vanilla block. */
    public static Material of(Block block) {
        return BLOCK_INTERN.computeIfAbsent(block, Material::new);
    }

    /** Interned wrapper for an item-only material (no block form). */
    public static Material ofItem(Item item) {
        return ITEM_INTERN.computeIfAbsent(item, Material::new);
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

    /** Whether this material has a block form (false for item-only materials). */
    public boolean isBlock() {
        return block != null;
    }

    /** The vanilla block, or {@code null} for item-only materials. */
    public Block getBlock() {
        return block;
    }

    /** The block's default {@link BlockState}, or {@code null} for item-only materials. */
    public BlockState getBlockState() {
        return defaultState;
    }

    /** The item form (a block's own item for block-backed materials). */
    public Item getItem() {
        return item;
    }

    /** Approximates Bukkit {@code Material.name()} — the registry path, upper-cased. */
    public String name() {
        Identifier key = block != null
                ? BuiltInRegistries.BLOCK.getKey(block)
                : BuiltInRegistries.ITEM.getKey(item);
        return key.getPath().toUpperCase(Locale.ROOT);
    }

    public boolean is(Material other) {
        if (other == null) {
            return false;
        }
        return block != null ? other.block == block : other.item == item;
    }

    /**
     * Bukkit's {@code Material.isOccluding()} — whether this is a full, opaque cube. The generator
     * uses it to decide what may be stacked on (see {@code SupportBlocks.isNonstackableBlock}).
     * Item-only materials are not occluding.
     */
    public boolean isOccluding() {
        return defaultState != null && defaultState.canOcclude();
    }

    /**
     * Bukkit's {@code Material.hasGravity()} — whether this block falls if unsupported (sand,
     * gravel, anvils, concrete powder). The building code asks before using something as an outset
     * or ceiling, since a floating slab of sand would just drop.
     *
     * <p>Bukkit answered from a flag on the material; vanilla expresses it as a block class, so this
     * asks whether the block is a {@link FallingBlock}. Item-only materials never have gravity.
     */
    public boolean hasGravity() {
        return block instanceof FallingBlock;
    }

    // ---- Orientation derivation (mirrors the old InitialBlocks BlockData logic) --------------

    /**
     * Apply a single facing, matching the old {@code Directional / MultipleFacing / Orientable}
     * fallback chain: a facing property if present, else a single connection face, else an axis.
     */
    public BlockState withFacing(BlockFace facing) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        Direction dir = facing.toDirection();
        if (dir != null && state.hasProperty(BlockStateProperties.FACING)) {
            return state.setValue(BlockStateProperties.FACING, dir);
        }
        if (dir != null && dir.getAxis().isHorizontal()
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        }
        Property<Boolean> faceProp = faceProperty(facing);
        if (faceProp != null && state.hasProperty(faceProp)) {
            return state.setValue(faceProp, true);
        }
        if (state.hasProperty(BlockStateProperties.AXIS)) {
            return state.setValue(BlockStateProperties.AXIS, axisFor(facing));
        }
        return state;
    }

    /** Set several connection faces true (fences, panes, glass, bars, vines). */
    public BlockState withFaces(BlockFace... faces) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
        for (BlockFace face : faces) {
            Property<Boolean> faceProp = faceProperty(face);
            if (faceProp != null && state.hasProperty(faceProp)) {
                state = state.setValue(faceProp, true);
            }
        }
        return state;
    }

    /**
     * Whether this block connects face-by-face — Bukkit's {@code MultipleFacing}. The old code
     * tests this to choose between per-face and bulk placement (see {@code SupportBlocks.setWalls}).
     *
     * <p><b>Walls are deliberately excluded.</b> In 1.14 walls were {@code MultipleFacing} like
     * fences, but the 1.16 flattening moved them to a {@code WallSide} (none/low/tall) enum per
     * side, so they no longer carry the boolean face properties this reports on. They therefore
     * take the bulk-placement branch. Revisit when decoration lands (Phase 5) if wall connections
     * come out wrong.
     */
    public boolean hasFaces() {
        if (defaultState == null) {
            return false;
        }
        for (BlockFace face : new BlockFace[] { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST,
                BlockFace.UP, BlockFace.DOWN }) {
            Property<Boolean> faceProp = faceProperty(face);
            if (faceProp != null && defaultState.hasProperty(faceProp)) {
                return true;
            }
        }
        return false;
    }

    /** The block as a slab of the given type (top / bottom / double), if it is a slab. */
    public BlockState asSlab(SlabType type) {
        BlockState state = defaultState;
        if (state == null) {
            return null;
        }
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
        if (state == null) {
            return null;
        }
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

    /** The boolean connection property for one face, or {@code null} for a face that has none. */
    public static Property<Boolean> faceProperty(BlockFace face) {
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
        if (this == o) {
            return true;
        }
        if (!(o instanceof Material)) {
            return false;
        }
        Material other = (Material) o;
        return block != null ? other.block == block : other.item == item;
    }

    @Override
    public int hashCode() {
        return block != null ? block.hashCode() : item.hashCode();
    }

    @Override
    public String toString() {
        return "Material(" + name() + ")";
    }

    // ---- Blocks (427) — 1.14 names that map 1:1 onto a modern block --------------
    public static final Material ACACIA_DOOR = of(Blocks.ACACIA_DOOR);
    public static final Material ACACIA_FENCE = of(Blocks.ACACIA_FENCE);
    public static final Material ACACIA_FENCE_GATE = of(Blocks.ACACIA_FENCE_GATE);
    public static final Material ACACIA_LEAVES = of(Blocks.ACACIA_LEAVES);
    public static final Material ACACIA_LOG = of(Blocks.ACACIA_LOG);
    public static final Material ACACIA_PLANKS = of(Blocks.ACACIA_PLANKS);
    public static final Material ACACIA_PRESSURE_PLATE = of(Blocks.ACACIA_PRESSURE_PLATE);
    public static final Material ACACIA_SAPLING = of(Blocks.ACACIA_SAPLING);
    public static final Material ACACIA_SIGN = of(Blocks.ACACIA_SIGN);
    public static final Material ACACIA_SLAB = of(Blocks.ACACIA_SLAB);
    public static final Material ACACIA_STAIRS = of(Blocks.ACACIA_STAIRS);
    public static final Material ACACIA_TRAPDOOR = of(Blocks.ACACIA_TRAPDOOR);
    public static final Material ACACIA_WALL_SIGN = of(Blocks.ACACIA_WALL_SIGN);
    public static final Material ACACIA_WOOD = of(Blocks.ACACIA_WOOD);
    public static final Material AIR = of(Blocks.AIR);
    public static final Material ALLIUM = of(Blocks.ALLIUM);
    public static final Material AZURE_BLUET = of(Blocks.AZURE_BLUET);
    public static final Material BARRIER = of(Blocks.BARRIER);
    public static final Material BEACON = of(Blocks.BEACON);
    public static final Material BEDROCK = of(Blocks.BEDROCK);
    public static final Material BEETROOTS = of(Blocks.BEETROOTS);
    public static final Material BIRCH_DOOR = of(Blocks.BIRCH_DOOR);
    public static final Material BIRCH_FENCE = of(Blocks.BIRCH_FENCE);
    public static final Material BIRCH_FENCE_GATE = of(Blocks.BIRCH_FENCE_GATE);
    public static final Material BIRCH_LEAVES = of(Blocks.BIRCH_LEAVES);
    public static final Material BIRCH_LOG = of(Blocks.BIRCH_LOG);
    public static final Material BIRCH_PLANKS = of(Blocks.BIRCH_PLANKS);
    public static final Material BIRCH_PRESSURE_PLATE = of(Blocks.BIRCH_PRESSURE_PLATE);
    public static final Material BIRCH_SAPLING = of(Blocks.BIRCH_SAPLING);
    public static final Material BIRCH_SIGN = of(Blocks.BIRCH_SIGN);
    public static final Material BIRCH_SLAB = of(Blocks.BIRCH_SLAB);
    public static final Material BIRCH_STAIRS = of(Blocks.BIRCH_STAIRS);
    public static final Material BIRCH_TRAPDOOR = of(Blocks.BIRCH_TRAPDOOR);
    public static final Material BIRCH_WALL_SIGN = of(Blocks.BIRCH_WALL_SIGN);
    public static final Material BIRCH_WOOD = of(Blocks.BIRCH_WOOD);
    public static final Material BLACK_BED = of(Blocks.BLACK_BED);
    public static final Material BLACK_CARPET = of(Blocks.BLACK_CARPET);
    public static final Material BLACK_CONCRETE = of(Blocks.BLACK_CONCRETE);
    public static final Material BLACK_CONCRETE_POWDER = of(Blocks.BLACK_CONCRETE_POWDER);
    public static final Material BLACK_GLAZED_TERRACOTTA = of(Blocks.BLACK_GLAZED_TERRACOTTA);
    public static final Material BLACK_STAINED_GLASS = of(Blocks.BLACK_STAINED_GLASS);
    public static final Material BLACK_STAINED_GLASS_PANE = of(Blocks.BLACK_STAINED_GLASS_PANE);
    public static final Material BLACK_TERRACOTTA = of(Blocks.BLACK_TERRACOTTA);
    public static final Material BLACK_WOOL = of(Blocks.BLACK_WOOL);
    public static final Material BLUE_BED = of(Blocks.BLUE_BED);
    public static final Material BLUE_CARPET = of(Blocks.BLUE_CARPET);
    public static final Material BLUE_CONCRETE = of(Blocks.BLUE_CONCRETE);
    public static final Material BLUE_CONCRETE_POWDER = of(Blocks.BLUE_CONCRETE_POWDER);
    public static final Material BLUE_GLAZED_TERRACOTTA = of(Blocks.BLUE_GLAZED_TERRACOTTA);
    public static final Material BLUE_ORCHID = of(Blocks.BLUE_ORCHID);
    public static final Material BLUE_STAINED_GLASS = of(Blocks.BLUE_STAINED_GLASS);
    public static final Material BLUE_STAINED_GLASS_PANE = of(Blocks.BLUE_STAINED_GLASS_PANE);
    public static final Material BLUE_TERRACOTTA = of(Blocks.BLUE_TERRACOTTA);
    public static final Material BLUE_WOOL = of(Blocks.BLUE_WOOL);
    public static final Material BONE_BLOCK = of(Blocks.BONE_BLOCK);
    public static final Material BOOKSHELF = of(Blocks.BOOKSHELF);
    public static final Material BRAIN_CORAL = of(Blocks.BRAIN_CORAL);
    public static final Material BRAIN_CORAL_BLOCK = of(Blocks.BRAIN_CORAL_BLOCK);
    public static final Material BRAIN_CORAL_FAN = of(Blocks.BRAIN_CORAL_FAN);
    public static final Material BRAIN_CORAL_WALL_FAN = of(Blocks.BRAIN_CORAL_WALL_FAN);
    public static final Material BREWING_STAND = of(Blocks.BREWING_STAND);
    public static final Material BRICKS = of(Blocks.BRICKS);
    public static final Material BRICK_STAIRS = of(Blocks.BRICK_STAIRS);
    public static final Material BROWN_BED = of(Blocks.BROWN_BED);
    public static final Material BROWN_CARPET = of(Blocks.BROWN_CARPET);
    public static final Material BROWN_CONCRETE = of(Blocks.BROWN_CONCRETE);
    public static final Material BROWN_CONCRETE_POWDER = of(Blocks.BROWN_CONCRETE_POWDER);
    public static final Material BROWN_GLAZED_TERRACOTTA = of(Blocks.BROWN_GLAZED_TERRACOTTA);
    public static final Material BROWN_MUSHROOM = of(Blocks.BROWN_MUSHROOM);
    public static final Material BROWN_MUSHROOM_BLOCK = of(Blocks.BROWN_MUSHROOM_BLOCK);
    public static final Material BROWN_STAINED_GLASS = of(Blocks.BROWN_STAINED_GLASS);
    public static final Material BROWN_STAINED_GLASS_PANE = of(Blocks.BROWN_STAINED_GLASS_PANE);
    public static final Material BROWN_TERRACOTTA = of(Blocks.BROWN_TERRACOTTA);
    public static final Material BROWN_WOOL = of(Blocks.BROWN_WOOL);
    public static final Material BUBBLE_CORAL = of(Blocks.BUBBLE_CORAL);
    public static final Material BUBBLE_CORAL_BLOCK = of(Blocks.BUBBLE_CORAL_BLOCK);
    public static final Material BUBBLE_CORAL_FAN = of(Blocks.BUBBLE_CORAL_FAN);
    public static final Material BUBBLE_CORAL_WALL_FAN = of(Blocks.BUBBLE_CORAL_WALL_FAN);
    public static final Material CACTUS = of(Blocks.CACTUS);
    public static final Material CAKE = of(Blocks.CAKE);
    public static final Material CAMPFIRE = of(Blocks.CAMPFIRE);
    public static final Material CARROTS = of(Blocks.CARROTS);
    public static final Material CAULDRON = of(Blocks.CAULDRON);
    public static final Material CHEST = of(Blocks.CHEST);
    public static final Material CHISELED_QUARTZ_BLOCK = of(Blocks.CHISELED_QUARTZ_BLOCK);
    public static final Material CHISELED_STONE_BRICKS = of(Blocks.CHISELED_STONE_BRICKS);
    public static final Material CLAY = of(Blocks.CLAY);
    public static final Material COAL_BLOCK = of(Blocks.COAL_BLOCK);
    public static final Material COAL_ORE = of(Blocks.COAL_ORE);
    public static final Material COARSE_DIRT = of(Blocks.COARSE_DIRT);
    public static final Material COBBLESTONE = of(Blocks.COBBLESTONE);
    public static final Material COBBLESTONE_SLAB = of(Blocks.COBBLESTONE_SLAB);
    public static final Material COBBLESTONE_STAIRS = of(Blocks.COBBLESTONE_STAIRS);
    public static final Material COBBLESTONE_WALL = of(Blocks.COBBLESTONE_WALL);
    public static final Material COBWEB = of(Blocks.COBWEB);
    public static final Material CRACKED_STONE_BRICKS = of(Blocks.CRACKED_STONE_BRICKS);
    public static final Material CRAFTING_TABLE = of(Blocks.CRAFTING_TABLE);
    public static final Material CYAN_BED = of(Blocks.CYAN_BED);
    public static final Material CYAN_CARPET = of(Blocks.CYAN_CARPET);
    public static final Material CYAN_CONCRETE = of(Blocks.CYAN_CONCRETE);
    public static final Material CYAN_CONCRETE_POWDER = of(Blocks.CYAN_CONCRETE_POWDER);
    public static final Material CYAN_GLAZED_TERRACOTTA = of(Blocks.CYAN_GLAZED_TERRACOTTA);
    public static final Material CYAN_STAINED_GLASS = of(Blocks.CYAN_STAINED_GLASS);
    public static final Material CYAN_STAINED_GLASS_PANE = of(Blocks.CYAN_STAINED_GLASS_PANE);
    public static final Material CYAN_TERRACOTTA = of(Blocks.CYAN_TERRACOTTA);
    public static final Material CYAN_WOOL = of(Blocks.CYAN_WOOL);
    public static final Material DANDELION = of(Blocks.DANDELION);
    public static final Material DARK_OAK_DOOR = of(Blocks.DARK_OAK_DOOR);
    public static final Material DARK_OAK_FENCE = of(Blocks.DARK_OAK_FENCE);
    public static final Material DARK_OAK_FENCE_GATE = of(Blocks.DARK_OAK_FENCE_GATE);
    public static final Material DARK_OAK_LEAVES = of(Blocks.DARK_OAK_LEAVES);
    public static final Material DARK_OAK_LOG = of(Blocks.DARK_OAK_LOG);
    public static final Material DARK_OAK_PLANKS = of(Blocks.DARK_OAK_PLANKS);
    public static final Material DARK_OAK_PRESSURE_PLATE = of(Blocks.DARK_OAK_PRESSURE_PLATE);
    public static final Material DARK_OAK_SAPLING = of(Blocks.DARK_OAK_SAPLING);
    public static final Material DARK_OAK_SIGN = of(Blocks.DARK_OAK_SIGN);
    public static final Material DARK_OAK_SLAB = of(Blocks.DARK_OAK_SLAB);
    public static final Material DARK_OAK_STAIRS = of(Blocks.DARK_OAK_STAIRS);
    public static final Material DARK_OAK_TRAPDOOR = of(Blocks.DARK_OAK_TRAPDOOR);
    public static final Material DARK_OAK_WALL_SIGN = of(Blocks.DARK_OAK_WALL_SIGN);
    public static final Material DARK_OAK_WOOD = of(Blocks.DARK_OAK_WOOD);
    public static final Material DARK_PRISMARINE = of(Blocks.DARK_PRISMARINE);
    public static final Material DARK_PRISMARINE_STAIRS = of(Blocks.DARK_PRISMARINE_STAIRS);
    public static final Material DEAD_BRAIN_CORAL = of(Blocks.DEAD_BRAIN_CORAL);
    public static final Material DEAD_BRAIN_CORAL_BLOCK = of(Blocks.DEAD_BRAIN_CORAL_BLOCK);
    public static final Material DEAD_BRAIN_CORAL_FAN = of(Blocks.DEAD_BRAIN_CORAL_FAN);
    public static final Material DEAD_BRAIN_CORAL_WALL_FAN = of(Blocks.DEAD_BRAIN_CORAL_WALL_FAN);
    public static final Material DEAD_BUBBLE_CORAL = of(Blocks.DEAD_BUBBLE_CORAL);
    public static final Material DEAD_BUBBLE_CORAL_BLOCK = of(Blocks.DEAD_BUBBLE_CORAL_BLOCK);
    public static final Material DEAD_BUBBLE_CORAL_FAN = of(Blocks.DEAD_BUBBLE_CORAL_FAN);
    public static final Material DEAD_BUBBLE_CORAL_WALL_FAN = of(Blocks.DEAD_BUBBLE_CORAL_WALL_FAN);
    public static final Material DEAD_BUSH = of(Blocks.DEAD_BUSH);
    public static final Material DEAD_FIRE_CORAL = of(Blocks.DEAD_FIRE_CORAL);
    public static final Material DEAD_FIRE_CORAL_BLOCK = of(Blocks.DEAD_FIRE_CORAL_BLOCK);
    public static final Material DEAD_FIRE_CORAL_FAN = of(Blocks.DEAD_FIRE_CORAL_FAN);
    public static final Material DEAD_FIRE_CORAL_WALL_FAN = of(Blocks.DEAD_FIRE_CORAL_WALL_FAN);
    public static final Material DEAD_HORN_CORAL = of(Blocks.DEAD_HORN_CORAL);
    public static final Material DEAD_HORN_CORAL_BLOCK = of(Blocks.DEAD_HORN_CORAL_BLOCK);
    public static final Material DEAD_HORN_CORAL_FAN = of(Blocks.DEAD_HORN_CORAL_FAN);
    public static final Material DEAD_HORN_CORAL_WALL_FAN = of(Blocks.DEAD_HORN_CORAL_WALL_FAN);
    public static final Material DEAD_TUBE_CORAL = of(Blocks.DEAD_TUBE_CORAL);
    public static final Material DEAD_TUBE_CORAL_BLOCK = of(Blocks.DEAD_TUBE_CORAL_BLOCK);
    public static final Material DEAD_TUBE_CORAL_FAN = of(Blocks.DEAD_TUBE_CORAL_FAN);
    public static final Material DEAD_TUBE_CORAL_WALL_FAN = of(Blocks.DEAD_TUBE_CORAL_WALL_FAN);
    public static final Material DIAMOND_BLOCK = of(Blocks.DIAMOND_BLOCK);
    public static final Material DIAMOND_ORE = of(Blocks.DIAMOND_ORE);
    public static final Material DIRT = of(Blocks.DIRT);
    public static final Material EMERALD_BLOCK = of(Blocks.EMERALD_BLOCK);
    public static final Material EMERALD_ORE = of(Blocks.EMERALD_ORE);
    public static final Material END_ROD = of(Blocks.END_ROD);
    public static final Material END_STONE = of(Blocks.END_STONE);
    public static final Material END_STONE_BRICKS = of(Blocks.END_STONE_BRICKS);
    public static final Material FARMLAND = of(Blocks.FARMLAND);
    public static final Material FERN = of(Blocks.FERN);
    public static final Material FIRE = of(Blocks.FIRE);
    public static final Material FIRE_CORAL = of(Blocks.FIRE_CORAL);
    public static final Material FIRE_CORAL_BLOCK = of(Blocks.FIRE_CORAL_BLOCK);
    public static final Material FIRE_CORAL_FAN = of(Blocks.FIRE_CORAL_FAN);
    public static final Material FIRE_CORAL_WALL_FAN = of(Blocks.FIRE_CORAL_WALL_FAN);
    public static final Material FLOWER_POT = of(Blocks.FLOWER_POT);
    public static final Material FROSTED_ICE = of(Blocks.FROSTED_ICE);
    public static final Material FURNACE = of(Blocks.FURNACE);
    public static final Material GLASS = of(Blocks.GLASS);
    public static final Material GLASS_PANE = of(Blocks.GLASS_PANE);
    public static final Material GLOWSTONE = of(Blocks.GLOWSTONE);
    public static final Material GOLD_BLOCK = of(Blocks.GOLD_BLOCK);
    public static final Material GOLD_ORE = of(Blocks.GOLD_ORE);
    public static final Material GRASS_BLOCK = of(Blocks.GRASS_BLOCK);
    public static final Material GRAVEL = of(Blocks.GRAVEL);
    public static final Material GRAY_BED = of(Blocks.GRAY_BED);
    public static final Material GRAY_CARPET = of(Blocks.GRAY_CARPET);
    public static final Material GRAY_CONCRETE = of(Blocks.GRAY_CONCRETE);
    public static final Material GRAY_CONCRETE_POWDER = of(Blocks.GRAY_CONCRETE_POWDER);
    public static final Material GRAY_GLAZED_TERRACOTTA = of(Blocks.GRAY_GLAZED_TERRACOTTA);
    public static final Material GRAY_STAINED_GLASS = of(Blocks.GRAY_STAINED_GLASS);
    public static final Material GRAY_STAINED_GLASS_PANE = of(Blocks.GRAY_STAINED_GLASS_PANE);
    public static final Material GRAY_TERRACOTTA = of(Blocks.GRAY_TERRACOTTA);
    public static final Material GRAY_WOOL = of(Blocks.GRAY_WOOL);
    public static final Material GREEN_BED = of(Blocks.GREEN_BED);
    public static final Material GREEN_CARPET = of(Blocks.GREEN_CARPET);
    public static final Material GREEN_CONCRETE = of(Blocks.GREEN_CONCRETE);
    public static final Material GREEN_CONCRETE_POWDER = of(Blocks.GREEN_CONCRETE_POWDER);
    public static final Material GREEN_GLAZED_TERRACOTTA = of(Blocks.GREEN_GLAZED_TERRACOTTA);
    public static final Material GREEN_STAINED_GLASS = of(Blocks.GREEN_STAINED_GLASS);
    public static final Material GREEN_STAINED_GLASS_PANE = of(Blocks.GREEN_STAINED_GLASS_PANE);
    public static final Material GREEN_TERRACOTTA = of(Blocks.GREEN_TERRACOTTA);
    public static final Material GREEN_WOOL = of(Blocks.GREEN_WOOL);
    public static final Material HAY_BLOCK = of(Blocks.HAY_BLOCK);
    public static final Material HEAVY_WEIGHTED_PRESSURE_PLATE = of(Blocks.HEAVY_WEIGHTED_PRESSURE_PLATE);
    public static final Material HORN_CORAL = of(Blocks.HORN_CORAL);
    public static final Material HORN_CORAL_BLOCK = of(Blocks.HORN_CORAL_BLOCK);
    public static final Material HORN_CORAL_FAN = of(Blocks.HORN_CORAL_FAN);
    public static final Material HORN_CORAL_WALL_FAN = of(Blocks.HORN_CORAL_WALL_FAN);
    public static final Material ICE = of(Blocks.ICE);
    public static final Material INFESTED_CHISELED_STONE_BRICKS = of(Blocks.INFESTED_CHISELED_STONE_BRICKS);
    public static final Material INFESTED_COBBLESTONE = of(Blocks.INFESTED_COBBLESTONE);
    public static final Material INFESTED_CRACKED_STONE_BRICKS = of(Blocks.INFESTED_CRACKED_STONE_BRICKS);
    public static final Material INFESTED_STONE = of(Blocks.INFESTED_STONE);
    public static final Material INFESTED_STONE_BRICKS = of(Blocks.INFESTED_STONE_BRICKS);
    public static final Material IRON_BARS = of(Blocks.IRON_BARS);
    public static final Material IRON_BLOCK = of(Blocks.IRON_BLOCK);
    public static final Material IRON_ORE = of(Blocks.IRON_ORE);
    public static final Material JUNGLE_DOOR = of(Blocks.JUNGLE_DOOR);
    public static final Material JUNGLE_FENCE = of(Blocks.JUNGLE_FENCE);
    public static final Material JUNGLE_FENCE_GATE = of(Blocks.JUNGLE_FENCE_GATE);
    public static final Material JUNGLE_LEAVES = of(Blocks.JUNGLE_LEAVES);
    public static final Material JUNGLE_LOG = of(Blocks.JUNGLE_LOG);
    public static final Material JUNGLE_PLANKS = of(Blocks.JUNGLE_PLANKS);
    public static final Material JUNGLE_PRESSURE_PLATE = of(Blocks.JUNGLE_PRESSURE_PLATE);
    public static final Material JUNGLE_SAPLING = of(Blocks.JUNGLE_SAPLING);
    public static final Material JUNGLE_SIGN = of(Blocks.JUNGLE_SIGN);
    public static final Material JUNGLE_SLAB = of(Blocks.JUNGLE_SLAB);
    public static final Material JUNGLE_STAIRS = of(Blocks.JUNGLE_STAIRS);
    public static final Material JUNGLE_TRAPDOOR = of(Blocks.JUNGLE_TRAPDOOR);
    public static final Material JUNGLE_WALL_SIGN = of(Blocks.JUNGLE_WALL_SIGN);
    public static final Material JUNGLE_WOOD = of(Blocks.JUNGLE_WOOD);
    public static final Material KELP = of(Blocks.KELP);
    public static final Material KELP_PLANT = of(Blocks.KELP_PLANT);
    public static final Material LADDER = of(Blocks.LADDER);
    public static final Material LAPIS_BLOCK = of(Blocks.LAPIS_BLOCK);
    public static final Material LAPIS_ORE = of(Blocks.LAPIS_ORE);
    public static final Material LARGE_FERN = of(Blocks.LARGE_FERN);
    public static final Material LAVA = of(Blocks.LAVA);
    public static final Material LIGHT_BLUE_BED = of(Blocks.LIGHT_BLUE_BED);
    public static final Material LIGHT_BLUE_CARPET = of(Blocks.LIGHT_BLUE_CARPET);
    public static final Material LIGHT_BLUE_CONCRETE = of(Blocks.LIGHT_BLUE_CONCRETE);
    public static final Material LIGHT_BLUE_CONCRETE_POWDER = of(Blocks.LIGHT_BLUE_CONCRETE_POWDER);
    public static final Material LIGHT_BLUE_GLAZED_TERRACOTTA = of(Blocks.LIGHT_BLUE_GLAZED_TERRACOTTA);
    public static final Material LIGHT_BLUE_STAINED_GLASS = of(Blocks.LIGHT_BLUE_STAINED_GLASS);
    public static final Material LIGHT_BLUE_STAINED_GLASS_PANE = of(Blocks.LIGHT_BLUE_STAINED_GLASS_PANE);
    public static final Material LIGHT_BLUE_TERRACOTTA = of(Blocks.LIGHT_BLUE_TERRACOTTA);
    public static final Material LIGHT_BLUE_WOOL = of(Blocks.LIGHT_BLUE_WOOL);
    public static final Material LIGHT_GRAY_BED = of(Blocks.LIGHT_GRAY_BED);
    public static final Material LIGHT_GRAY_CARPET = of(Blocks.LIGHT_GRAY_CARPET);
    public static final Material LIGHT_GRAY_CONCRETE = of(Blocks.LIGHT_GRAY_CONCRETE);
    public static final Material LIGHT_GRAY_CONCRETE_POWDER = of(Blocks.LIGHT_GRAY_CONCRETE_POWDER);
    public static final Material LIGHT_GRAY_GLAZED_TERRACOTTA = of(Blocks.LIGHT_GRAY_GLAZED_TERRACOTTA);
    public static final Material LIGHT_GRAY_STAINED_GLASS = of(Blocks.LIGHT_GRAY_STAINED_GLASS);
    public static final Material LIGHT_GRAY_STAINED_GLASS_PANE = of(Blocks.LIGHT_GRAY_STAINED_GLASS_PANE);
    public static final Material LIGHT_GRAY_TERRACOTTA = of(Blocks.LIGHT_GRAY_TERRACOTTA);
    public static final Material LIGHT_GRAY_WOOL = of(Blocks.LIGHT_GRAY_WOOL);
    public static final Material LIGHT_WEIGHTED_PRESSURE_PLATE = of(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE);
    public static final Material LILAC = of(Blocks.LILAC);
    public static final Material LIME_BED = of(Blocks.LIME_BED);
    public static final Material LIME_CARPET = of(Blocks.LIME_CARPET);
    public static final Material LIME_CONCRETE = of(Blocks.LIME_CONCRETE);
    public static final Material LIME_CONCRETE_POWDER = of(Blocks.LIME_CONCRETE_POWDER);
    public static final Material LIME_GLAZED_TERRACOTTA = of(Blocks.LIME_GLAZED_TERRACOTTA);
    public static final Material LIME_STAINED_GLASS = of(Blocks.LIME_STAINED_GLASS);
    public static final Material LIME_STAINED_GLASS_PANE = of(Blocks.LIME_STAINED_GLASS_PANE);
    public static final Material LIME_TERRACOTTA = of(Blocks.LIME_TERRACOTTA);
    public static final Material LIME_WOOL = of(Blocks.LIME_WOOL);
    public static final Material MAGENTA_BED = of(Blocks.MAGENTA_BED);
    public static final Material MAGENTA_CARPET = of(Blocks.MAGENTA_CARPET);
    public static final Material MAGENTA_CONCRETE = of(Blocks.MAGENTA_CONCRETE);
    public static final Material MAGENTA_CONCRETE_POWDER = of(Blocks.MAGENTA_CONCRETE_POWDER);
    public static final Material MAGENTA_GLAZED_TERRACOTTA = of(Blocks.MAGENTA_GLAZED_TERRACOTTA);
    public static final Material MAGENTA_STAINED_GLASS = of(Blocks.MAGENTA_STAINED_GLASS);
    public static final Material MAGENTA_STAINED_GLASS_PANE = of(Blocks.MAGENTA_STAINED_GLASS_PANE);
    public static final Material MAGENTA_TERRACOTTA = of(Blocks.MAGENTA_TERRACOTTA);
    public static final Material MAGENTA_WOOL = of(Blocks.MAGENTA_WOOL);
    public static final Material MAGMA_BLOCK = of(Blocks.MAGMA_BLOCK);
    public static final Material MELON = of(Blocks.MELON);
    public static final Material MELON_STEM = of(Blocks.MELON_STEM);
    public static final Material MOSSY_COBBLESTONE = of(Blocks.MOSSY_COBBLESTONE);
    public static final Material MOSSY_COBBLESTONE_WALL = of(Blocks.MOSSY_COBBLESTONE_WALL);
    public static final Material MYCELIUM = of(Blocks.MYCELIUM);
    public static final Material NETHERRACK = of(Blocks.NETHERRACK);
    public static final Material NETHER_BRICKS = of(Blocks.NETHER_BRICKS);
    public static final Material NETHER_BRICK_FENCE = of(Blocks.NETHER_BRICK_FENCE);
    public static final Material NETHER_BRICK_SLAB = of(Blocks.NETHER_BRICK_SLAB);
    public static final Material NETHER_BRICK_STAIRS = of(Blocks.NETHER_BRICK_STAIRS);
    public static final Material NETHER_QUARTZ_ORE = of(Blocks.NETHER_QUARTZ_ORE);
    public static final Material NETHER_WART = of(Blocks.NETHER_WART);
    public static final Material OAK_DOOR = of(Blocks.OAK_DOOR);
    public static final Material OAK_FENCE = of(Blocks.OAK_FENCE);
    public static final Material OAK_FENCE_GATE = of(Blocks.OAK_FENCE_GATE);
    public static final Material OAK_LEAVES = of(Blocks.OAK_LEAVES);
    public static final Material OAK_LOG = of(Blocks.OAK_LOG);
    public static final Material OAK_PLANKS = of(Blocks.OAK_PLANKS);
    public static final Material OAK_PRESSURE_PLATE = of(Blocks.OAK_PRESSURE_PLATE);
    public static final Material OAK_SAPLING = of(Blocks.OAK_SAPLING);
    public static final Material OAK_SIGN = of(Blocks.OAK_SIGN);
    public static final Material OAK_SLAB = of(Blocks.OAK_SLAB);
    public static final Material OAK_STAIRS = of(Blocks.OAK_STAIRS);
    public static final Material OAK_TRAPDOOR = of(Blocks.OAK_TRAPDOOR);
    public static final Material OAK_WALL_SIGN = of(Blocks.OAK_WALL_SIGN);
    public static final Material OAK_WOOD = of(Blocks.OAK_WOOD);
    public static final Material OBSIDIAN = of(Blocks.OBSIDIAN);
    public static final Material ORANGE_BED = of(Blocks.ORANGE_BED);
    public static final Material ORANGE_CARPET = of(Blocks.ORANGE_CARPET);
    public static final Material ORANGE_CONCRETE = of(Blocks.ORANGE_CONCRETE);
    public static final Material ORANGE_CONCRETE_POWDER = of(Blocks.ORANGE_CONCRETE_POWDER);
    public static final Material ORANGE_GLAZED_TERRACOTTA = of(Blocks.ORANGE_GLAZED_TERRACOTTA);
    public static final Material ORANGE_STAINED_GLASS = of(Blocks.ORANGE_STAINED_GLASS);
    public static final Material ORANGE_STAINED_GLASS_PANE = of(Blocks.ORANGE_STAINED_GLASS_PANE);
    public static final Material ORANGE_TERRACOTTA = of(Blocks.ORANGE_TERRACOTTA);
    public static final Material ORANGE_TULIP = of(Blocks.ORANGE_TULIP);
    public static final Material ORANGE_WOOL = of(Blocks.ORANGE_WOOL);
    public static final Material OXEYE_DAISY = of(Blocks.OXEYE_DAISY);
    public static final Material PACKED_ICE = of(Blocks.PACKED_ICE);
    public static final Material PEONY = of(Blocks.PEONY);
    public static final Material PINK_BED = of(Blocks.PINK_BED);
    public static final Material PINK_CARPET = of(Blocks.PINK_CARPET);
    public static final Material PINK_CONCRETE = of(Blocks.PINK_CONCRETE);
    public static final Material PINK_CONCRETE_POWDER = of(Blocks.PINK_CONCRETE_POWDER);
    public static final Material PINK_GLAZED_TERRACOTTA = of(Blocks.PINK_GLAZED_TERRACOTTA);
    public static final Material PINK_STAINED_GLASS = of(Blocks.PINK_STAINED_GLASS);
    public static final Material PINK_STAINED_GLASS_PANE = of(Blocks.PINK_STAINED_GLASS_PANE);
    public static final Material PINK_TERRACOTTA = of(Blocks.PINK_TERRACOTTA);
    public static final Material PINK_TULIP = of(Blocks.PINK_TULIP);
    public static final Material PINK_WOOL = of(Blocks.PINK_WOOL);
    public static final Material PISTON = of(Blocks.PISTON);
    public static final Material PODZOL = of(Blocks.PODZOL);
    public static final Material POPPY = of(Blocks.POPPY);
    public static final Material POTATOES = of(Blocks.POTATOES);
    public static final Material POWERED_RAIL = of(Blocks.POWERED_RAIL);
    public static final Material PRISMARINE = of(Blocks.PRISMARINE);
    public static final Material PRISMARINE_BRICKS = of(Blocks.PRISMARINE_BRICKS);
    public static final Material PRISMARINE_BRICK_STAIRS = of(Blocks.PRISMARINE_BRICK_STAIRS);
    public static final Material PRISMARINE_STAIRS = of(Blocks.PRISMARINE_STAIRS);
    public static final Material PUMPKIN = of(Blocks.PUMPKIN);
    public static final Material PUMPKIN_STEM = of(Blocks.PUMPKIN_STEM);
    public static final Material PURPLE_BED = of(Blocks.PURPLE_BED);
    public static final Material PURPLE_CARPET = of(Blocks.PURPLE_CARPET);
    public static final Material PURPLE_CONCRETE = of(Blocks.PURPLE_CONCRETE);
    public static final Material PURPLE_CONCRETE_POWDER = of(Blocks.PURPLE_CONCRETE_POWDER);
    public static final Material PURPLE_GLAZED_TERRACOTTA = of(Blocks.PURPLE_GLAZED_TERRACOTTA);
    public static final Material PURPLE_STAINED_GLASS = of(Blocks.PURPLE_STAINED_GLASS);
    public static final Material PURPLE_STAINED_GLASS_PANE = of(Blocks.PURPLE_STAINED_GLASS_PANE);
    public static final Material PURPLE_TERRACOTTA = of(Blocks.PURPLE_TERRACOTTA);
    public static final Material PURPLE_WOOL = of(Blocks.PURPLE_WOOL);
    public static final Material PURPUR_BLOCK = of(Blocks.PURPUR_BLOCK);
    public static final Material PURPUR_PILLAR = of(Blocks.PURPUR_PILLAR);
    public static final Material PURPUR_STAIRS = of(Blocks.PURPUR_STAIRS);
    public static final Material QUARTZ_BLOCK = of(Blocks.QUARTZ_BLOCK);
    public static final Material QUARTZ_PILLAR = of(Blocks.QUARTZ_PILLAR);
    public static final Material QUARTZ_STAIRS = of(Blocks.QUARTZ_STAIRS);
    public static final Material RAIL = of(Blocks.RAIL);
    public static final Material REDSTONE_BLOCK = of(Blocks.REDSTONE_BLOCK);
    public static final Material REDSTONE_LAMP = of(Blocks.REDSTONE_LAMP);
    public static final Material REDSTONE_ORE = of(Blocks.REDSTONE_ORE);
    public static final Material REDSTONE_TORCH = of(Blocks.REDSTONE_TORCH);
    public static final Material RED_BED = of(Blocks.RED_BED);
    public static final Material RED_CARPET = of(Blocks.RED_CARPET);
    public static final Material RED_CONCRETE = of(Blocks.RED_CONCRETE);
    public static final Material RED_CONCRETE_POWDER = of(Blocks.RED_CONCRETE_POWDER);
    public static final Material RED_GLAZED_TERRACOTTA = of(Blocks.RED_GLAZED_TERRACOTTA);
    public static final Material RED_MUSHROOM = of(Blocks.RED_MUSHROOM);
    public static final Material RED_MUSHROOM_BLOCK = of(Blocks.RED_MUSHROOM_BLOCK);
    public static final Material RED_NETHER_BRICKS = of(Blocks.RED_NETHER_BRICKS);
    public static final Material RED_SANDSTONE = of(Blocks.RED_SANDSTONE);
    public static final Material RED_SANDSTONE_STAIRS = of(Blocks.RED_SANDSTONE_STAIRS);
    public static final Material RED_STAINED_GLASS = of(Blocks.RED_STAINED_GLASS);
    public static final Material RED_STAINED_GLASS_PANE = of(Blocks.RED_STAINED_GLASS_PANE);
    public static final Material RED_TERRACOTTA = of(Blocks.RED_TERRACOTTA);
    public static final Material RED_TULIP = of(Blocks.RED_TULIP);
    public static final Material RED_WOOL = of(Blocks.RED_WOOL);
    public static final Material ROSE_BUSH = of(Blocks.ROSE_BUSH);
    public static final Material SAND = of(Blocks.SAND);
    public static final Material SANDSTONE = of(Blocks.SANDSTONE);
    public static final Material SANDSTONE_STAIRS = of(Blocks.SANDSTONE_STAIRS);
    public static final Material SCAFFOLDING = of(Blocks.SCAFFOLDING);
    public static final Material SEAGRASS = of(Blocks.SEAGRASS);
    public static final Material SEA_LANTERN = of(Blocks.SEA_LANTERN);
    public static final Material SLIME_BLOCK = of(Blocks.SLIME_BLOCK);
    public static final Material SMOOTH_STONE = of(Blocks.SMOOTH_STONE);
    public static final Material SNOW = of(Blocks.SNOW);
    public static final Material SNOW_BLOCK = of(Blocks.SNOW_BLOCK);
    public static final Material SOUL_SAND = of(Blocks.SOUL_SAND);
    public static final Material SPAWNER = of(Blocks.SPAWNER);
    public static final Material SPONGE = of(Blocks.SPONGE);
    public static final Material SPRUCE_DOOR = of(Blocks.SPRUCE_DOOR);
    public static final Material SPRUCE_FENCE = of(Blocks.SPRUCE_FENCE);
    public static final Material SPRUCE_FENCE_GATE = of(Blocks.SPRUCE_FENCE_GATE);
    public static final Material SPRUCE_LEAVES = of(Blocks.SPRUCE_LEAVES);
    public static final Material SPRUCE_LOG = of(Blocks.SPRUCE_LOG);
    public static final Material SPRUCE_PLANKS = of(Blocks.SPRUCE_PLANKS);
    public static final Material SPRUCE_PRESSURE_PLATE = of(Blocks.SPRUCE_PRESSURE_PLATE);
    public static final Material SPRUCE_SIGN = of(Blocks.SPRUCE_SIGN);
    public static final Material SPRUCE_SLAB = of(Blocks.SPRUCE_SLAB);
    public static final Material SPRUCE_STAIRS = of(Blocks.SPRUCE_STAIRS);
    public static final Material SPRUCE_TRAPDOOR = of(Blocks.SPRUCE_TRAPDOOR);
    public static final Material SPRUCE_WALL_SIGN = of(Blocks.SPRUCE_WALL_SIGN);
    public static final Material SPRUCE_WOOD = of(Blocks.SPRUCE_WOOD);
    public static final Material STONE = of(Blocks.STONE);
    public static final Material STONE_BRICKS = of(Blocks.STONE_BRICKS);
    public static final Material STONE_BRICK_STAIRS = of(Blocks.STONE_BRICK_STAIRS);
    public static final Material STONE_PRESSURE_PLATE = of(Blocks.STONE_PRESSURE_PLATE);
    public static final Material STONE_SLAB = of(Blocks.STONE_SLAB);
    public static final Material SUGAR_CANE = of(Blocks.SUGAR_CANE);
    public static final Material SUNFLOWER = of(Blocks.SUNFLOWER);
    public static final Material TALL_GRASS = of(Blocks.TALL_GRASS);
    public static final Material TALL_SEAGRASS = of(Blocks.TALL_SEAGRASS);
    public static final Material TERRACOTTA = of(Blocks.TERRACOTTA);
    public static final Material TNT = of(Blocks.TNT);
    public static final Material TORCH = of(Blocks.TORCH);
    public static final Material TRIPWIRE = of(Blocks.TRIPWIRE);
    public static final Material TRIPWIRE_HOOK = of(Blocks.TRIPWIRE_HOOK);
    public static final Material TUBE_CORAL = of(Blocks.TUBE_CORAL);
    public static final Material TUBE_CORAL_BLOCK = of(Blocks.TUBE_CORAL_BLOCK);
    public static final Material TUBE_CORAL_FAN = of(Blocks.TUBE_CORAL_FAN);
    public static final Material TUBE_CORAL_WALL_FAN = of(Blocks.TUBE_CORAL_WALL_FAN);
    public static final Material VINE = of(Blocks.VINE);
    public static final Material WATER = of(Blocks.WATER);
    public static final Material WET_SPONGE = of(Blocks.WET_SPONGE);
    public static final Material WHEAT = of(Blocks.WHEAT);
    public static final Material WHITE_BANNER = of(Blocks.WHITE_BANNER);
    public static final Material WHITE_BED = of(Blocks.WHITE_BED);
    public static final Material WHITE_CARPET = of(Blocks.WHITE_CARPET);
    public static final Material WHITE_CONCRETE = of(Blocks.WHITE_CONCRETE);
    public static final Material WHITE_CONCRETE_POWDER = of(Blocks.WHITE_CONCRETE_POWDER);
    public static final Material WHITE_GLAZED_TERRACOTTA = of(Blocks.WHITE_GLAZED_TERRACOTTA);
    public static final Material WHITE_STAINED_GLASS = of(Blocks.WHITE_STAINED_GLASS);
    public static final Material WHITE_STAINED_GLASS_PANE = of(Blocks.WHITE_STAINED_GLASS_PANE);
    public static final Material WHITE_TERRACOTTA = of(Blocks.WHITE_TERRACOTTA);
    public static final Material WHITE_TULIP = of(Blocks.WHITE_TULIP);
    public static final Material WHITE_WOOL = of(Blocks.WHITE_WOOL);
    public static final Material YELLOW_BED = of(Blocks.YELLOW_BED);
    public static final Material YELLOW_CARPET = of(Blocks.YELLOW_CARPET);
    public static final Material YELLOW_CONCRETE = of(Blocks.YELLOW_CONCRETE);
    public static final Material YELLOW_CONCRETE_POWDER = of(Blocks.YELLOW_CONCRETE_POWDER);
    public static final Material YELLOW_GLAZED_TERRACOTTA = of(Blocks.YELLOW_GLAZED_TERRACOTTA);
    public static final Material YELLOW_STAINED_GLASS = of(Blocks.YELLOW_STAINED_GLASS);
    public static final Material YELLOW_STAINED_GLASS_PANE = of(Blocks.YELLOW_STAINED_GLASS_PANE);
    public static final Material YELLOW_TERRACOTTA = of(Blocks.YELLOW_TERRACOTTA);
    public static final Material YELLOW_WOOL = of(Blocks.YELLOW_WOOL);

    // ---- Legacy / renamed (14) — 1.14 names with no modern equivalent ----------
    public static final Material BED_BLOCK = of(Blocks.WHITE_BED); // generic bed -> a concrete colour
    public static final Material CARPET = of(Blocks.WHITE_CARPET); // generic carpet -> a concrete colour
    public static final Material DOUBLE_STEP = of(Blocks.SMOOTH_STONE); // legacy double stone slab -> the full block
    public static final Material ENCHANTMENT_TABLE = of(Blocks.ENCHANTING_TABLE); // renamed
    public static final Material ENDER_PORTAL_FRAME = of(Blocks.END_PORTAL_FRAME); // renamed
    public static final Material GRASS = of(Blocks.SHORT_GRASS); // 1.14 GRASS was the plant; renamed in 1.20.3
    public static final Material GRASS_PATH = of(Blocks.DIRT_PATH); // renamed
    public static final Material IRON_DOOR_BLOCK = of(Blocks.IRON_DOOR); // legacy block name
    public static final Material LONG_GRASS = of(Blocks.TALL_GRASS); // legacy tall-grass name
    public static final Material QUARTZ_ORE = of(Blocks.NETHER_QUARTZ_ORE); // renamed
    public static final Material SIGN = of(Blocks.OAK_SIGN); // generic sign -> oak
    public static final Material TRAP_DOOR = of(Blocks.OAK_TRAPDOOR); // legacy generic trapdoor -> oak
    public static final Material WOOD_DOOR = of(Blocks.OAK_DOOR); // legacy generic wooden door -> oak
    public static final Material WOOD_STEP = of(Blocks.OAK_SLAB); // legacy generic wooden slab -> oak

    // ---- Modern extras (5) — blocks the 1.14 vocabulary never had -------------
    public static final Material BLUE_ICE = of(Blocks.BLUE_ICE); // P8 MODERN: glacier-blue ice for the highest peaks; 1.14 had none
    public static final Material DEEPSLATE = of(Blocks.DEEPSLATE); // P4: the deep stratum below y=0; 1.14's world bottomed out at stone
    public static final Material MUD = of(Blocks.MUD); // P8 MODERN: muddy bottoms for carved swamp/mangrove pools; a 1.19 block
    public static final Material POWDER_SNOW = of(Blocks.POWDER_SNOW); // P8 MODERN: powder-snow pockets on snowy slopes; a 1.17 block
    public static final Material RED_SAND = of(Blocks.RED_SAND); // P8 MODERN: badlands surface; the 1.14 Bukkit source never referenced it

    // ---- Items (116) — Bukkit's Material spanned blocks AND items; these are ----
    // ---- item-only (loot/chest contents). They carry no block state. -----------
    public static final Material ACACIA_BOAT = ofItem(Items.ACACIA_BOAT);
    public static final Material APPLE = ofItem(Items.APPLE);
    public static final Material ARROW = ofItem(Items.ARROW);
    public static final Material BAKED_POTATO = ofItem(Items.BAKED_POTATO);
    public static final Material BEEF = ofItem(Items.BEEF);
    public static final Material BEETROOT = ofItem(Items.BEETROOT);
    public static final Material BEETROOT_SOUP = ofItem(Items.BEETROOT_SOUP);
    public static final Material BIRCH_BOAT = ofItem(Items.BIRCH_BOAT);
    public static final Material BONE = ofItem(Items.BONE);
    public static final Material BOOK = ofItem(Items.BOOK);
    public static final Material BOW = ofItem(Items.BOW);
    public static final Material BOWL = ofItem(Items.BOWL);
    public static final Material BREAD = ofItem(Items.BREAD);
    public static final Material BUCKET = ofItem(Items.BUCKET);
    public static final Material CARROT = ofItem(Items.CARROT);
    public static final Material CARROT_ON_A_STICK = ofItem(Items.CARROT_ON_A_STICK);
    public static final Material CHAINMAIL_BOOTS = ofItem(Items.CHAINMAIL_BOOTS);
    public static final Material CHAINMAIL_CHESTPLATE = ofItem(Items.CHAINMAIL_CHESTPLATE);
    public static final Material CHAINMAIL_HELMET = ofItem(Items.CHAINMAIL_HELMET);
    public static final Material CHAINMAIL_LEGGINGS = ofItem(Items.CHAINMAIL_LEGGINGS);
    public static final Material CHICKEN = ofItem(Items.CHICKEN);
    public static final Material CLOCK = ofItem(Items.CLOCK);
    public static final Material COAL = ofItem(Items.COAL);
    public static final Material COD = ofItem(Items.COD);
    public static final Material COMPASS = ofItem(Items.COMPASS);
    public static final Material COOKED_BEEF = ofItem(Items.COOKED_BEEF);
    public static final Material COOKED_CHICKEN = ofItem(Items.COOKED_CHICKEN);
    public static final Material COOKED_COD = ofItem(Items.COOKED_COD);
    public static final Material COOKED_MUTTON = ofItem(Items.COOKED_MUTTON);
    public static final Material COOKED_PORKCHOP = ofItem(Items.COOKED_PORKCHOP);
    public static final Material COOKED_RABBIT = ofItem(Items.COOKED_RABBIT);
    public static final Material COOKIE = ofItem(Items.COOKIE);
    public static final Material DARK_OAK_BOAT = ofItem(Items.DARK_OAK_BOAT);
    public static final Material DIAMOND = ofItem(Items.DIAMOND);
    public static final Material EGG = ofItem(Items.EGG);
    public static final Material EMERALD = ofItem(Items.EMERALD);
    public static final Material FEATHER = ofItem(Items.FEATHER);
    public static final Material FIREWORK_ROCKET = ofItem(Items.FIREWORK_ROCKET);
    public static final Material FISHING_ROD = ofItem(Items.FISHING_ROD);
    public static final Material FLINT = ofItem(Items.FLINT);
    public static final Material FLINT_AND_STEEL = ofItem(Items.FLINT_AND_STEEL);
    public static final Material GLASS_BOTTLE = ofItem(Items.GLASS_BOTTLE);
    public static final Material GLISTERING_MELON_SLICE = ofItem(Items.GLISTERING_MELON_SLICE);
    public static final Material GOLDEN_APPLE = ofItem(Items.GOLDEN_APPLE);
    public static final Material GOLDEN_CARROT = ofItem(Items.GOLDEN_CARROT);
    public static final Material GOLD_INGOT = ofItem(Items.GOLD_INGOT);
    public static final Material GUNPOWDER = ofItem(Items.GUNPOWDER);
    public static final Material IRON_AXE = ofItem(Items.IRON_AXE);
    public static final Material IRON_BOOTS = ofItem(Items.IRON_BOOTS);
    public static final Material IRON_CHESTPLATE = ofItem(Items.IRON_CHESTPLATE);
    public static final Material IRON_HELMET = ofItem(Items.IRON_HELMET);
    public static final Material IRON_HOE = ofItem(Items.IRON_HOE);
    public static final Material IRON_INGOT = ofItem(Items.IRON_INGOT);
    public static final Material IRON_LEGGINGS = ofItem(Items.IRON_LEGGINGS);
    public static final Material IRON_PICKAXE = ofItem(Items.IRON_PICKAXE);
    public static final Material IRON_SHOVEL = ofItem(Items.IRON_SHOVEL);
    public static final Material IRON_SWORD = ofItem(Items.IRON_SWORD);
    public static final Material ITEM_FRAME = ofItem(Items.ITEM_FRAME);
    public static final Material JUNGLE_BOAT = ofItem(Items.JUNGLE_BOAT);
    public static final Material LAVA_BUCKET = ofItem(Items.LAVA_BUCKET);
    public static final Material LEAD = ofItem(Items.LEAD);
    public static final Material LEATHER = ofItem(Items.LEATHER);
    public static final Material LEATHER_BOOTS = ofItem(Items.LEATHER_BOOTS);
    public static final Material LEATHER_CHESTPLATE = ofItem(Items.LEATHER_CHESTPLATE);
    public static final Material LEATHER_HELMET = ofItem(Items.LEATHER_HELMET);
    public static final Material LEATHER_LEGGINGS = ofItem(Items.LEATHER_LEGGINGS);
    public static final Material MAP = ofItem(Items.MAP);
    public static final Material MELON_SEEDS = ofItem(Items.MELON_SEEDS);
    public static final Material MILK_BUCKET = ofItem(Items.MILK_BUCKET);
    public static final Material MUSHROOM_STEW = ofItem(Items.MUSHROOM_STEW);
    public static final Material MUSIC_DISC_11 = ofItem(Items.MUSIC_DISC_11);
    public static final Material MUSIC_DISC_13 = ofItem(Items.MUSIC_DISC_13);
    public static final Material MUSIC_DISC_BLOCKS = ofItem(Items.MUSIC_DISC_BLOCKS);
    public static final Material MUSIC_DISC_CAT = ofItem(Items.MUSIC_DISC_CAT);
    public static final Material MUSIC_DISC_CHIRP = ofItem(Items.MUSIC_DISC_CHIRP);
    public static final Material MUSIC_DISC_FAR = ofItem(Items.MUSIC_DISC_FAR);
    public static final Material MUSIC_DISC_MALL = ofItem(Items.MUSIC_DISC_MALL);
    public static final Material MUSIC_DISC_MELLOHI = ofItem(Items.MUSIC_DISC_MELLOHI);
    public static final Material MUSIC_DISC_STAL = ofItem(Items.MUSIC_DISC_STAL);
    public static final Material MUSIC_DISC_STRAD = ofItem(Items.MUSIC_DISC_STRAD);
    public static final Material MUSIC_DISC_WAIT = ofItem(Items.MUSIC_DISC_WAIT);
    public static final Material MUSIC_DISC_WARD = ofItem(Items.MUSIC_DISC_WARD);
    public static final Material MUTTON = ofItem(Items.MUTTON);
    public static final Material NAME_TAG = ofItem(Items.NAME_TAG);
    public static final Material OAK_BOAT = ofItem(Items.OAK_BOAT);
    public static final Material PAINTING = ofItem(Items.PAINTING);
    public static final Material PAPER = ofItem(Items.PAPER);
    public static final Material POISONOUS_POTATO = ofItem(Items.POISONOUS_POTATO);
    public static final Material PORKCHOP = ofItem(Items.PORKCHOP);
    public static final Material POTATO = ofItem(Items.POTATO);
    public static final Material PUMPKIN_PIE = ofItem(Items.PUMPKIN_PIE);
    public static final Material PUMPKIN_SEEDS = ofItem(Items.PUMPKIN_SEEDS);
    public static final Material RABBIT = ofItem(Items.RABBIT);
    public static final Material RABBIT_FOOT = ofItem(Items.RABBIT_FOOT);
    public static final Material RABBIT_HIDE = ofItem(Items.RABBIT_HIDE);
    public static final Material REDSTONE = ofItem(Items.REDSTONE);
    public static final Material ROTTEN_FLESH = ofItem(Items.ROTTEN_FLESH);
    public static final Material SHEARS = ofItem(Items.SHEARS);
    public static final Material SPRUCE_BOAT = ofItem(Items.SPRUCE_BOAT);
    public static final Material STICK = ofItem(Items.STICK);
    public static final Material STONE_AXE = ofItem(Items.STONE_AXE);
    public static final Material STONE_HOE = ofItem(Items.STONE_HOE);
    public static final Material STONE_PICKAXE = ofItem(Items.STONE_PICKAXE);
    public static final Material STONE_SHOVEL = ofItem(Items.STONE_SHOVEL);
    public static final Material STONE_SWORD = ofItem(Items.STONE_SWORD);
    public static final Material STRING = ofItem(Items.STRING);
    public static final Material SUGAR = ofItem(Items.SUGAR);
    public static final Material TOTEM_OF_UNDYING = ofItem(Items.TOTEM_OF_UNDYING);
    public static final Material WATER_BUCKET = ofItem(Items.WATER_BUCKET);
    public static final Material WHEAT_SEEDS = ofItem(Items.WHEAT_SEEDS);
    public static final Material WOODEN_AXE = ofItem(Items.WOODEN_AXE);
    public static final Material WOODEN_HOE = ofItem(Items.WOODEN_HOE);
    public static final Material WOODEN_PICKAXE = ofItem(Items.WOODEN_PICKAXE);
    public static final Material WOODEN_SHOVEL = ofItem(Items.WOODEN_SHOVEL);
    public static final Material WOODEN_SWORD = ofItem(Items.WOODEN_SWORD);
    public static final Material WRITABLE_BOOK = ofItem(Items.WRITABLE_BOOK);
}
