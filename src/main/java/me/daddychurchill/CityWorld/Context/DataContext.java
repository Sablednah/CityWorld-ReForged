package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.compat.Material;

/**
 * Stub of the original {@code DataContext}.
 *
 * <p><b>Phase 1 placeholder.</b> {@code DataContext} is one node of the generator's mutually
 * recursive brain ({@code ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔ Plugins}), so porting it
 * for real pulls in essentially the whole tree. {@code SupportBlocks} touches exactly one member of
 * it — {@code torchMat}, in {@code drawCrane} — which makes this a thin edge worth cutting so the
 * block seam can compile and be reviewed on its own.
 *
 * <p>The real class (lot odds, floor-height maths, schematic families, light materials chosen from
 * the world settings) arrives with the contexts in a later wave, at which point this file is
 * replaced wholesale rather than extended.
 */
public abstract class DataContext {

    /** Blocks per building floor. A real constant, not a stub — carried over verbatim. */
    public static final int FloorHeight = 4;

    /**
     * The torch material for this context. The original picks between {@code TORCH} and
     * {@code REDSTONE_TORCH} based on the per-world {@code includeWorkingLights} setting; settings
     * are Phase 7, so the stub takes the working-lights branch.
     */
    public final Material torchMat = Material.TORCH;
}
