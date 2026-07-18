package me.daddychurchill.CityWorld.Clipboard;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Maps a <b>legacy</b> numeric block id + 4-bit data value (the MCEdit/pre-1.13 encoding CityWorld's
 * {@code .schematic} assets use) to a modern {@link BlockState}.
 *
 * <p>Covers every id used by the bundled classic set (verified: no id falls back). Ids not in the set
 * are logged once (see {@link #unknown}) and fall back to stone, so a new schematic that needs one
 * announces itself instead of vanishing, and a gap shows as a solid patch rather than a hole.
 *
 * <p>Block <em>types</em> and the visible data (wood/stone species, wool colour, slab/stair
 * orientation, flower kind) are decoded; fiddly runtime state (redstone power, crop age, piston
 * extension) is left at the block's default. Doors are the exception that needs both halves at once
 * (hinge lives on the upper block) — see {@link #doorState}, driven from {@code LegacySchematic}.
 * Tile-entity contents are handled in a separate pass in {@code LegacySchematic}: sign text and
 * container inventories (chests/furnaces/dispensers) are both carried now.
 */
public final class LegacyBlocks {

    private LegacyBlocks() {}

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState FALLBACK = Blocks.STONE.defaultBlockState();

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
    private static final Block[] PLANKS = {
        Blocks.OAK_PLANKS, Blocks.SPRUCE_PLANKS, Blocks.BIRCH_PLANKS,
        Blocks.JUNGLE_PLANKS, Blocks.ACACIA_PLANKS, Blocks.DARK_OAK_PLANKS,
    };
    private static final Block[] LOGS = { Blocks.OAK_LOG, Blocks.SPRUCE_LOG, Blocks.BIRCH_LOG, Blocks.JUNGLE_LOG };
    private static final Block[] LEAVES = { Blocks.OAK_LEAVES, Blocks.SPRUCE_LEAVES, Blocks.BIRCH_LEAVES, Blocks.JUNGLE_LEAVES };
    private static final Block[] WOOD_SLAB = {
        Blocks.OAK_SLAB, Blocks.SPRUCE_SLAB, Blocks.BIRCH_SLAB,
        Blocks.JUNGLE_SLAB, Blocks.ACACIA_SLAB, Blocks.DARK_OAK_SLAB,
    };

    private static final Set<Integer> unknown = ConcurrentHashMap.newKeySet();

    public static boolean isAir(int id) {
        return id == 0;
    }

    public static BlockState of(int id, int data) {
        switch (id) {
            case 0:   return AIR;
            case 1:   return switch (data) {
                          case 1 -> Blocks.GRANITE.defaultBlockState();
                          case 2 -> Blocks.POLISHED_GRANITE.defaultBlockState();
                          case 3 -> Blocks.DIORITE.defaultBlockState();
                          case 4 -> Blocks.POLISHED_DIORITE.defaultBlockState();
                          case 5 -> Blocks.ANDESITE.defaultBlockState();
                          case 6 -> Blocks.POLISHED_ANDESITE.defaultBlockState();
                          default -> Blocks.STONE.defaultBlockState();
                      };
            case 2:   return Blocks.GRASS_BLOCK.defaultBlockState();
            case 3:   return switch (data) {
                          case 1 -> Blocks.COARSE_DIRT.defaultBlockState();
                          case 2 -> Blocks.PODZOL.defaultBlockState();
                          default -> Blocks.DIRT.defaultBlockState();
                      };
            case 4:   return Blocks.COBBLESTONE.defaultBlockState();
            case 5:   return PLANKS[data % PLANKS.length].defaultBlockState();
            case 9:   return Blocks.WATER.defaultBlockState();
            case 12:  return data == 1 ? Blocks.RED_SAND.defaultBlockState() : Blocks.SAND.defaultBlockState();
            case 13:  return Blocks.GRAVEL.defaultBlockState();
            case 15:  return Blocks.IRON_ORE.defaultBlockState();
            case 16:  return Blocks.COAL_ORE.defaultBlockState();
            case 17:  return LOGS[data & 3].defaultBlockState();
            case 18:  return LEAVES[data & 3].defaultBlockState();
            case 19:  return Blocks.SPONGE.defaultBlockState();
            case 20:  return Blocks.GLASS.defaultBlockState();
            case 23:  return Blocks.DISPENSER.defaultBlockState();
            case 24:  return switch (data) {
                          case 1 -> Blocks.CHISELED_SANDSTONE.defaultBlockState();
                          case 2 -> Blocks.CUT_SANDSTONE.defaultBlockState();
                          default -> Blocks.SANDSTONE.defaultBlockState();
                      };
            case 25:  return Blocks.NOTE_BLOCK.defaultBlockState();
            case 26:  return bed(data);
            case 29:  return Blocks.STICKY_PISTON.defaultBlockState();
            case 30:  return Blocks.COBWEB.defaultBlockState();
            case 31:  return data == 2 ? Blocks.FERN.defaultBlockState() : Blocks.SHORT_GRASS.defaultBlockState();
            case 33:  return Blocks.PISTON.defaultBlockState();
            case 34:  return Blocks.AIR.defaultBlockState(); // piston head — part of the piston
            case 35:  return WOOL[data & 15];
            case 37:  return Blocks.DANDELION.defaultBlockState();
            case 38:  return redFlower(data);
            case 42:  return Blocks.IRON_BLOCK.defaultBlockState();
            case 43:  return doubleStoneSlab(data & 7);
            case 44:  return slab(stoneSlab(data & 7), data);
            case 45:  return Blocks.BRICKS.defaultBlockState();
            case 47:  return Blocks.BOOKSHELF.defaultBlockState();
            case 48:  return Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case 49:  return Blocks.OBSIDIAN.defaultBlockState();
            case 50:  return torch(data, Blocks.TORCH.defaultBlockState(), Blocks.WALL_TORCH);
            case 51:  return Blocks.FIRE.defaultBlockState();
            case 53:  return stairs(Blocks.OAK_STAIRS, data);
            case 54:  return facing(Blocks.CHEST.defaultBlockState(), data);
            case 55:  return Blocks.REDSTONE_WIRE.defaultBlockState();
            case 58:  return Blocks.CRAFTING_TABLE.defaultBlockState();
            case 60:  return Blocks.FARMLAND.defaultBlockState();
            case 61:  return facing(Blocks.FURNACE.defaultBlockState(), data);
            case 63:  return Blocks.OAK_SIGN.defaultBlockState();
            case 64:  return door(Blocks.OAK_DOOR.defaultBlockState(), data);
            case 65:  return facing(Blocks.LADDER.defaultBlockState(), data);
            case 67:  return stairs(Blocks.COBBLESTONE_STAIRS, data);
            case 68:  return facing(Blocks.OAK_WALL_SIGN.defaultBlockState(), data);
            case 69:  return Blocks.LEVER.defaultBlockState();
            case 70:  return Blocks.STONE_PRESSURE_PLATE.defaultBlockState();
            case 71:  return door(Blocks.IRON_DOOR.defaultBlockState(), data);
            case 72:  return Blocks.OAK_PRESSURE_PLATE.defaultBlockState();
            case 75:  return Blocks.REDSTONE_WALL_TORCH.defaultBlockState();
            case 76:  return Blocks.REDSTONE_WALL_TORCH.defaultBlockState();
            case 77:  return Blocks.STONE_BUTTON.defaultBlockState();
            case 80:  return Blocks.SNOW_BLOCK.defaultBlockState();
            case 82:  return Blocks.CLAY.defaultBlockState();
            case 84:  return Blocks.JUKEBOX.defaultBlockState();
            case 85:  return Blocks.OAK_FENCE.defaultBlockState();
            case 87:  return Blocks.NETHERRACK.defaultBlockState();
            case 88:  return Blocks.SOUL_SAND.defaultBlockState();
            case 89:  return Blocks.GLOWSTONE.defaultBlockState();
            case 90:  return Blocks.NETHER_PORTAL.defaultBlockState();
            case 93:  return Blocks.REPEATER.defaultBlockState();
            case 96:  return Blocks.OAK_TRAPDOOR.defaultBlockState();
            case 98:  return switch (data) {
                          case 1 -> Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
                          case 2 -> Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
                          case 3 -> Blocks.CHISELED_STONE_BRICKS.defaultBlockState();
                          default -> Blocks.STONE_BRICKS.defaultBlockState();
                      };
            case 99:  return Blocks.BROWN_MUSHROOM_BLOCK.defaultBlockState();
            case 101: return Blocks.IRON_BARS.defaultBlockState();
            case 102: return Blocks.GLASS_PANE.defaultBlockState();
            case 106: return Blocks.VINE.defaultBlockState();
            case 107: return Blocks.OAK_FENCE_GATE.defaultBlockState()
                          .setValue(BlockStateProperties.HORIZONTAL_FACING, rot4Facing(data));
            case 109: return stairs(Blocks.STONE_BRICK_STAIRS, data);
            case 112: return Blocks.NETHER_BRICKS.defaultBlockState();
            case 113: return Blocks.NETHER_BRICK_FENCE.defaultBlockState();
            case 114: return stairs(Blocks.NETHER_BRICK_STAIRS, data);
            case 117: return Blocks.BREWING_STAND.defaultBlockState();
            case 118: return Blocks.CAULDRON.defaultBlockState();
            case 123: return Blocks.REDSTONE_LAMP.defaultBlockState();
            case 124: return Blocks.REDSTONE_LAMP.defaultBlockState();
            case 125: return PLANKS[data % PLANKS.length].defaultBlockState(); // double wooden slab -> planks
            case 126: return slab(WOOD_SLAB[(data & 7) % WOOD_SLAB.length].defaultBlockState(), data);
            case 128: return stairs(Blocks.SANDSTONE_STAIRS, data);
            case 130: return facing(Blocks.ENDER_CHEST.defaultBlockState(), data);
            case 131: return Blocks.TRIPWIRE_HOOK.defaultBlockState();
            case 134: return stairs(Blocks.SPRUCE_STAIRS, data);
            case 135: return stairs(Blocks.BIRCH_STAIRS, data);
            case 136: return stairs(Blocks.JUNGLE_STAIRS, data);
            case 139: return (data & 1) == 1 ? Blocks.MOSSY_COBBLESTONE_WALL.defaultBlockState()
                                             : Blocks.COBBLESTONE_WALL.defaultBlockState();
            case 140: return Blocks.FLOWER_POT.defaultBlockState();
            case 141: return Blocks.CARROTS.defaultBlockState();
            case 142: return Blocks.POTATOES.defaultBlockState();
            case 143: return Blocks.OAK_BUTTON.defaultBlockState();
            default:
                if (unknown.add(id))
                    CityWorldMod.LOGGER.warn("LegacyBlocks: no mapping for legacy block id {} (data {}); using stone",
                            id, data);
                return FALLBACK;
        }
    }

    private static BlockState redFlower(int data) {
        return switch (data) {
            case 1 -> Blocks.BLUE_ORCHID.defaultBlockState();
            case 2 -> Blocks.ALLIUM.defaultBlockState();
            case 3 -> Blocks.AZURE_BLUET.defaultBlockState();
            case 4 -> Blocks.RED_TULIP.defaultBlockState();
            case 5 -> Blocks.ORANGE_TULIP.defaultBlockState();
            case 6 -> Blocks.WHITE_TULIP.defaultBlockState();
            case 7 -> Blocks.PINK_TULIP.defaultBlockState();
            case 8 -> Blocks.OXEYE_DAISY.defaultBlockState();
            default -> Blocks.POPPY.defaultBlockState();
        };
    }

    /** Legacy horizontal facing on data 2..5: 2=north, 3=south, 4=west, 5=east. */
    private static Direction hFacing(int data) {
        return switch (data) {
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.WEST;
            case 5 -> Direction.EAST;
            default -> Direction.NORTH;
        };
    }

    private static BlockState facing(BlockState base, int data) {
        return base.hasProperty(BlockStateProperties.HORIZONTAL_FACING)
                ? base.setValue(BlockStateProperties.HORIZONTAL_FACING, hFacing(data)) : base;
    }

    /** Legacy torch/redstone-torch: data 1=east,2=west,3=south,4=north wall, 5=floor. */
    private static BlockState torch(int data, BlockState floor, Block wall) {
        Direction dir = switch (data) {
            case 1 -> Direction.EAST;
            case 2 -> Direction.WEST;
            case 3 -> Direction.SOUTH;
            case 4 -> Direction.NORTH;
            default -> null;
        };
        return dir == null ? floor
                : wall.defaultBlockState().setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
    }

    /** Legacy stairs: data bits 0-1 facing (0 east,1 west,2 south,3 north), bit 2 = upside-down. */
    private static BlockState stairs(Block block, int data) {
        Direction dir = switch (data & 3) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.WEST;
            case 2 -> Direction.SOUTH;
            default -> Direction.NORTH;
        };
        BlockState s = block.defaultBlockState();
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
            s = s.setValue(BlockStateProperties.HORIZONTAL_FACING, dir);
        if (s.hasProperty(BlockStateProperties.HALF))
            s = s.setValue(BlockStateProperties.HALF, (data & 4) != 0 ? Half.TOP : Half.BOTTOM);
        return s;
    }

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

    /** Legacy doors put the upper half at data bit 8. */
    private static BlockState door(BlockState base, int data) {
        DoubleBlockHalf half = (data & 8) != 0 ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER;
        return base.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                ? base.setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, half) : base;
    }

    /** True for a legacy door id (its two halves must be decoded together — see {@link #doorState}). */
    static boolean isDoor(int id) {
        return switch (id) {
            case 64, 71, 193, 194, 195, 196, 197 -> true;
            default -> false;
        };
    }

    private static Block doorBlock(int id) {
        return switch (id) {
            case 71 -> Blocks.IRON_DOOR;
            case 193 -> Blocks.SPRUCE_DOOR;
            case 194 -> Blocks.BIRCH_DOOR;
            case 195 -> Blocks.JUNGLE_DOOR;
            case 196 -> Blocks.ACACIA_DOOR;
            case 197 -> Blocks.DARK_OAK_DOOR;
            default -> Blocks.OAK_DOOR; // 64
        };
    }

    /**
     * Build a full modern door state from <em>both</em> legacy halves. Legacy doors split their state
     * across the two blocks — the lower half holds facing (bits 0–1) and open (bit 2); the upper half
     * holds the hinge side (bit 0) and powered (bit 1). Decoding a half in isolation is why double
     * doors came out as two identical singles: the hinge lives on the upper block, so it was never
     * read. This combines them, so a mirrored pair reads as a proper double door.
     */
    static BlockState doorState(int id, int lowerData, int upperData, boolean upper) {
        return doorBlock(id).defaultBlockState()
                .setValue(BlockStateProperties.DOUBLE_BLOCK_HALF, upper ? DoubleBlockHalf.UPPER : DoubleBlockHalf.LOWER)
                .setValue(BlockStateProperties.HORIZONTAL_FACING, doorFacing(lowerData))
                .setValue(BlockStateProperties.DOOR_HINGE, (upperData & 1) != 0 ? DoorHingeSide.RIGHT : DoorHingeSide.LEFT)
                .setValue(BlockStateProperties.OPEN, (lowerData & 4) != 0)
                .setValue(BlockStateProperties.POWERED, (upperData & 2) != 0);
    }

    // Legacy lower-half facing (bits 0–1) → modern FACING, the WorldEdit legacy mapping.
    private static Direction doorFacing(int lowerData) {
        return switch (lowerData & 3) {
            case 0 -> Direction.EAST;
            case 1 -> Direction.SOUTH;
            case 2 -> Direction.WEST;
            default -> Direction.NORTH;
        };
    }

    /**
     * The 4-way rotation legacy blocks with two facing bits use (beds, fence gates, ...). Each block
     * carries its own facing, so — unlike doors — no neighbour is needed. Beds store the same bits in
     * both halves and set {@code FACING} foot→head, so head and foot stay consistent and the bed is a
     * valid pair rather than two loose ends that pop off on placement.
     */
    private static Direction rot4Facing(int data) {
        return switch (data & 3) {
            case 0 -> Direction.SOUTH;
            case 1 -> Direction.WEST;
            case 2 -> Direction.NORTH;
            default -> Direction.EAST;
        };
    }

    private static BlockState bed(int data) {
        return Blocks.RED_BED.defaultBlockState()
                .setValue(BlockStateProperties.HORIZONTAL_FACING, rot4Facing(data))
                .setValue(BlockStateProperties.BED_PART, (data & 8) != 0 ? BedPart.HEAD : BedPart.FOOT)
                .setValue(BlockStateProperties.OCCUPIED, false);
    }

    /** The facing a legacy chest (id 54) decodes to — used to pair adjacent chests into doubles. */
    static Direction chestFacing(int data) {
        return of(54, data).getValue(BlockStateProperties.HORIZONTAL_FACING);
    }
}
