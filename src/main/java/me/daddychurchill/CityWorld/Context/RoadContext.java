package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * Stub of the original {@code RoadContext} (70 lines) — the context that lays down roads and
 * roundabouts.
 *
 * <p><b>Wave 1 placeholder.</b> Upstream this extends {@code UrbanContext} and builds {@code
 * RoadLot} / {@code RoundaboutCenterLot} (and their schematic-backed variants), none of which are
 * ported yet. It exists here only so {@code ShapeProvider}'s road hooks have something to call; the
 * lots it hands back are plain natural ones, so a wave-1 world has terrain but no roads. Wave 2
 * replaces this wholesale.
 */
public class RoadContext extends DataContext {

    public RoadContext(CityWorldGenerator generator) {
        super();
    }

    @Override
    public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
    }

    @Override
    public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
    }

    /** Wave 2: returns a real {@code RoadLot}, honouring {@code oldLot.repaveLot} first. */
    public PlatLot createRoadLot(CityWorldGenerator generator, PlatMap platmap, int x, int z, boolean roundaboutPart,
            PlatLot oldLot) {
        return new PlatLot(generator, platmap.originX + x, platmap.originZ + z);
    }

    /** Wave 2: returns a real {@code RoundaboutCenterLot}, or a schematic-backed one if any fits. */
    public PlatLot createRoundaboutStatueLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
        return new PlatLot(generator, platmap.originX + x, platmap.originZ + z);
    }
}
