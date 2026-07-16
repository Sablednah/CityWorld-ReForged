package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Stub of the original {@code DataContext} (165 lines) — a context decides which lots populate a
 * {@code PlatMap}, i.e. whether a patch of world becomes downtown, suburb, farm or wilderness.
 *
 * <p><b>Wave 1 placeholder.</b> {@code DataContext} is one node of the generator's mutually
 * recursive brain ({@code ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔ Plugins}), so porting it
 * for real pulls in essentially the whole tree — the ~20 concrete contexts each reach for their own
 * families of lots. Only the members the block seam and the {@code ShapeProvider} touch are here.
 *
 * <p>The real class (lot odds, floor-height maths, schematic families, light materials chosen from
 * the world settings) arrives with the contexts in wave 2, at which point this file is replaced
 * wholesale rather than extended.
 */
public abstract class DataContext {

    /** Blocks per building floor. A real constant, not a stub — carried over verbatim. */
    public static final int FloorHeight = 4;

    /**
     * The torch material for this context. The original picks between {@code TORCH} and
     * {@code REDSTONE_TORCH} based on the per-world {@code includeWorkingLights} setting; settings
     * are P7, so the stub takes the working-lights branch.
     */
    public final Material torchMat = Material.TORCH;

    /** Fills a platmap with the lots this context calls for. Wave 2. */
    public abstract void populateMap(CityWorldGenerator generator, PlatMap platmap);

    /** Second pass over a populated platmap, fixing up lots that don't agree with their neighbours. Wave 2. */
    public abstract void validateMap(CityWorldGenerator generator, PlatMap platmap);

    /**
     * Makes the "nothing here but nature" lot for a chunk.
     *
     * <p>Upstream this picks among the nature lots (mountains, gravel, bunkers, oil platforms, …);
     * the wave-1 stub returns the plain base {@code PlatLot}, which is the natural, unplanned chunk
     * the terrain path is built around.
     */
    public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
        return new PlatLot(generator, platmap.originX + x, platmap.originZ + z);
    }
}
