package me.daddychurchill.CityWorld.api;

import net.minecraft.world.level.ChunkPos;

/**
 * What the subway has under one chunk, from the plan (so it is right before the chunk exists). Obtain one via
 * {@link CityWorldAPI#subwayAt}. The two lines are on two levels: east-west on the upper, north-south on the
 * lower, so lines cross without meeting; a station has a hall on each level it serves. A chunk with both
 * levels may also join them (an interchange's stairs, or the sloped loop that links an interchange's dead
 * ends), so {@link #contains} then answers for the whole span from the lower floor to the upper ceiling.
 *
 * @param chunk             the chunk
 * @param station           a station (its ticket hall at street level, stairs down) is on this chunk
 * @param eastWest          the upper level (the east-west line) has track, a platform or a hall here
 * @param northSouth        the lower level (the north-south line) has track, a platform or a hall here
 * @param eastWestFloorY    the floor of the upper level: the tunnel's air is {@code floor + 1 .. floor + height}
 * @param northSouthFloorY  the floor of the lower level
 * @param height            the air above a floor in a tunnel or hall
 */
public record SubwayInfo(ChunkPos chunk, boolean station, boolean eastWest, boolean northSouth, int eastWestFloorY,
        int northSouthFloorY, int height) {

    /** True if any subway is under this chunk. */
    public boolean any() {
        return station || eastWest || northSouth;
    }

    /** True if block height {@code y} is inside a tunnel or hall this chunk has (the whole span when it has both). */
    public boolean contains(int y) {
        if (eastWest && northSouth)
            return y > Math.min(eastWestFloorY, northSouthFloorY) && y <= Math.max(eastWestFloorY, northSouthFloorY) + height;
        return eastWest && y > eastWestFloorY && y <= eastWestFloorY + height
                || northSouth && y > northSouthFloorY && y <= northSouthFloorY + height;
    }
}
