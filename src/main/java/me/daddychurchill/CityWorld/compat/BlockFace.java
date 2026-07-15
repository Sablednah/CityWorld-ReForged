package me.daddychurchill.CityWorld.compat;

import net.minecraft.core.Direction;

/**
 * Port shim for {@code org.bukkit.block.BlockFace}.
 *
 * <p>The original CityWorld generator speaks in Bukkit {@link BlockFace} values (~1400 usages,
 * overwhelmingly the four horizontals). This enum mirrors the Bukkit constants the code actually
 * uses — the six primaries, the four intercardinals, the eight secondary intercardinals, and
 * {@code SELF} — with the same {@code getModX/Y/Z} offsets and {@code getOppositeFace()} contract,
 * so ported call sites only need an import change.
 *
 * <p>{@link #toDirection()} bridges to vanilla {@link Direction} for the six primary faces (the
 * ones that map onto block-state facing properties); the diagonal faces are used purely as
 * positional offsets and return {@code null}.
 */
public enum BlockFace {
    NORTH(0, 0, -1),
    EAST(1, 0, 0),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    UP(0, 1, 0),
    DOWN(0, -1, 0),
    NORTH_EAST(1, 0, -1),
    NORTH_WEST(-1, 0, -1),
    SOUTH_EAST(1, 0, 1),
    SOUTH_WEST(-1, 0, 1),
    WEST_NORTH_WEST(-2, 0, -1),
    NORTH_NORTH_WEST(-1, 0, -2),
    NORTH_NORTH_EAST(1, 0, -2),
    EAST_NORTH_EAST(2, 0, -1),
    EAST_SOUTH_EAST(2, 0, 1),
    SOUTH_SOUTH_EAST(1, 0, 2),
    SOUTH_SOUTH_WEST(-1, 0, 2),
    WEST_SOUTH_WEST(-2, 0, 1),
    SELF(0, 0, 0);

    private final int modX;
    private final int modY;
    private final int modZ;

    BlockFace(int modX, int modY, int modZ) {
        this.modX = modX;
        this.modY = modY;
        this.modZ = modZ;
    }

    public int getModX() {
        return modX;
    }

    public int getModY() {
        return modY;
    }

    public int getModZ() {
        return modZ;
    }

    /**
     * The opposite face — the one whose offsets are the negation of this one. {@code SELF} is its
     * own opposite. Every value in this enum has its mirror present, so the lookup always resolves.
     */
    public BlockFace getOppositeFace() {
        for (BlockFace face : values()) {
            if (face.modX == -modX && face.modY == -modY && face.modZ == -modZ) {
                return face;
            }
        }
        return SELF;
    }

    /**
     * The vanilla {@link Direction} for the six primary faces, or {@code null} for the diagonal /
     * secondary faces and {@code SELF} (which are offsets, not orientations).
     */
    public Direction toDirection() {
        switch (this) {
            case NORTH: return Direction.NORTH;
            case EAST:  return Direction.EAST;
            case SOUTH: return Direction.SOUTH;
            case WEST:  return Direction.WEST;
            case UP:    return Direction.UP;
            case DOWN:  return Direction.DOWN;
            default:    return null;
        }
    }

    /** Bridge from a vanilla {@link Direction} to the matching primary face. */
    public static BlockFace fromDirection(Direction direction) {
        switch (direction) {
            case NORTH: return NORTH;
            case EAST:  return EAST;
            case SOUTH: return SOUTH;
            case WEST:  return WEST;
            case UP:    return UP;
            case DOWN:  return DOWN;
            default:    return SELF;
        }
    }
}
