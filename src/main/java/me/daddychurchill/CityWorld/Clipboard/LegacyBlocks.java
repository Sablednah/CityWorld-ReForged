package me.daddychurchill.CityWorld.Clipboard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Maps a <b>legacy</b> numeric block id + 4-bit data value (the MCEdit/pre-1.13 encoding CityWorld's
 * {@code .schematic} assets use) to a modern {@link BlockState}.
 *
 * <p><b>Not a complete flattening table</b> — deliberately. It covers the blocks the ported
 * schematics actually contain, growing from what the assets use rather than from the full 1.12 id
 * space. Every unmapped id is logged once (see {@link #unknown}) so a schematic that needs a new one
 * announces it instead of vanishing. Unknown ids fall back to stone, so a missing mapping shows up as
 * a solid patch rather than a hole.
 *
 * <p>Orientation-heavy blocks (doors, signs, levers) are mapped to a sensible default facing rather
 * than decoded bit-for-bit; the classic assets read fine that way, and precise orientation can be
 * refined later against specific buildings.
 */
public final class LegacyBlocks {

    private LegacyBlocks() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FALLBACK = Blocks.STONE.defaultBlockState();

    /** Wool/clay/etc. colour order, indexed by the legacy data value 0..15. */
    private static final BlockState[] WOOL = {
        Blocks.WHITE_WOOL.defaultBlockState(), Blocks.ORANGE_WOOL.defaultBlockState(),
        Blocks.MAGENTA_WOOL.defaultBlockState(), Blocks.LIGHT_BLUE_WOOL.defaultBlockState(),
        Blocks.YELLOW_WOOL.defaultBlockState(), Blocks.LIME_WOOL.defaultBlockState(),
        Blocks.PINK_WOOL.defaultBlockState(), Blocks.GRAY_WOOL.defaultBlockState(),
        Blocks.LIGHT_GRAY_WOOL.defaultBlockState(), Blocks.CYAN_WOOL.defaultBlockState(),
        Blocks.PURPLE_WOOL.defaultBlockState(), Blocks.BLUE_WOOL.defaultBlockState(),
        Blocks.BROWN_WOOL.defaultBlockState(), Blocks.GREEN_WOOL.defaultBlockState(),
        Blocks.RED_WOOL.defaultBlockState(), Blocks.BLACK_WOOL.defaultBlockState(),
    };

    private static final Set<Integer> unknown = ConcurrentHashMap.newKeySet();

    public static boolean isAir(int id) {
        return id == 0;
    }

    public static BlockState of(int id, int data) {
        switch (id) {
            case 0:   return AIR;
            case 1:   return Blocks.STONE.defaultBlockState();
            case 2:   return Blocks.GRASS_BLOCK.defaultBlockState();
            case 3:   return Blocks.DIRT.defaultBlockState();
            case 4:   return Blocks.COBBLESTONE.defaultBlockState();
            case 5:   return Blocks.OAK_PLANKS.defaultBlockState();
            case 7:   return Blocks.BEDROCK.defaultBlockState();
            case 12:  return Blocks.SAND.defaultBlockState();
            case 13:  return Blocks.GRAVEL.defaultBlockState();
            case 17:  return Blocks.OAK_LOG.defaultBlockState();
            case 18:  return Blocks.OAK_LEAVES.defaultBlockState();
            case 20:  return Blocks.GLASS.defaultBlockState();
            case 24:  return Blocks.SANDSTONE.defaultBlockState();
            case 35:  return WOOL[data & 15];
            case 42:  return Blocks.IRON_BLOCK.defaultBlockState();
            case 43:  return doubleStoneSlab(data & 7);
            case 44:  return slab(stoneSlab(data & 7), data);
            case 45:  return Blocks.BRICKS.defaultBlockState();
            case 48:  return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case 49:  return Blocks.OBSIDIAN.defaultBlockState();
            case 64:  return door(Blocks.OAK_DOOR.defaultBlockState(), data);
            case 68:  return Blocks.OAK_WALL_SIGN.defaultBlockState();
            case 69:  return Blocks.LEVER.defaultBlockState();
            case 70:  return Blocks.STONE_PRESSURE_PLATE.defaultBlockState();
            case 71:  return door(Blocks.IRON_DOOR.defaultBlockState(), data);
            case 87:  return Blocks.NETHERRACK.defaultBlockState();
            case 89:  return Blocks.GLOWSTONE.defaultBlockState();
            case 98:  return Blocks.STONE_BRICKS.defaultBlockState();
            case 102: return Blocks.GLASS_PANE.defaultBlockState();
            case 112: return Blocks.NETHER_BRICKS.defaultBlockState();
            case 113: return Blocks.NETHER_BRICK_FENCE.defaultBlockState();
            case 126: return slab(woodSlab(data & 7), data);
            case 139: return (data & 1) == 1 ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
                                             : Blocks.COBBLESTONE_WALL.defaultBlockState();
            case 140: return Blocks.FLOWER_POT.defaultBlockState();
            case 143: return Blocks.OAK_BUTTON.defaultBlockState();
            default:
                if (unknown.add(id))
                    CityWorldMod.LOGGER.warn("LegacyBlocks: no mapping for legacy block id {} (data {}); using stone",
                            id, data);
                return FALLBACK;
        }
    }

    /** Legacy slab data has the top-half flag in bit 8; the low bits pick the material. */
    private static BlockState slab(BlockState base, int data) {
        SlabType type = (data & 8) != 0 ? SlabType.TOP : SlabType.BOTTOM;
        return base.hasProperty(BlockStateProperties.SLAB_TYPE)
                ? base.setValue(BlockStateProperties.SLAB_TYPE, type) : base;
    }

    private static BlockState stoneSlab(int material) {
        return switch (material) {
            case 1 -> Blocks.SANDSTONE_SLAB.defaultBlockState();
            case 3 -> Blocks.COBBLESTONE_SLAB.defaultBlockState();
            case 4 -> Blocks.BRICK_SLAB.defaultBlockState();
            case 5 -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
            case 6 -> Blocks.NETHER_BRICK_SLAB.defaultBlockState();
            case 7 -> Blocks.QUARTZ_SLAB.defaultBlockState();
            default -> Blocks.SMOOTH_STONE_SLAB.defaultBlockState();
        };
    }

    private static BlockState doubleStoneSlab(int material) {
        return switch (material) {
            case 1 -> Blocks.SANDSTONE.defaultBlockState();
            case 3 -> Blocks.COBBLESTONE.defaultBlockState();
            case 4 -> Blocks.BRICKS.defaultBlockState();
            case 5 -> Blocks.STONE_BRICKS.defaultBlockState();
            case 6 -> Blocks.NETHER_BRICKS.defaultBlockState();
            case 7 -> Blocks.QUARTZ_BLOCK.defaultBlockState();
            default -> Blocks.SMOOTH_STONE.defaultBlockState();
        };
    }

    private static BlockState woodSlab(int species) {
        return switch (species) {
            case 1 -> Blocks.SPRUCE_SLAB.defaultBlockState();
            case 2 -> Blocks.BIRCH_SLAB.defaultBlockState();
            case 3 -> Blocks.JUNGLE_SLAB.defaultBlockState();
            case 4 -> Blocks.ACACIA_SLAB.defaultBlockState();
            case 5 -> Blocks.DARK_OAK_SLAB.defaultBlockState();
            default -> Blocks.OAK_SLAB.defaultBlockState();
        };
    }

    /** Legacy doors put the upper half at data bit 8. */
    private static BlockState door(BlockState base, int data) {
        DoubleBlockHalf half = (data & 8) != 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
        return base.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                ? base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half) : base;
    }
}
