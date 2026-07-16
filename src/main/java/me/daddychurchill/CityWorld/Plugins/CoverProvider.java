package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * Stub of the original {@code CoverProvider} (903 lines, plus 7 style variants) — the ground cover:
 * grass, flowers, mushrooms, vines, snow.
 *
 * <p><b>Wave 2 placeholder.</b> Runs in the decoration pass, which is not driven yet (P5). It is
 * also what {@code SurfaceProvider} delegates to, so the two land together.
 */
public class CoverProvider extends Provider {

    public static CoverProvider loadProvider(CityWorldGenerator generator, Odds odds) {
        return new CoverProvider();
    }

    /** P5: makes a block able to grow things (tills/waters it, as the cover needs). */
    public void makePlantable(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z) {
    }
}
