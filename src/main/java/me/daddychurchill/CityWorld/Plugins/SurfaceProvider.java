package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * Stub of the original {@code SurfaceProvider} (106 lines + 7 variants) — grass, snow, flowers and
 * trees on top of the shaped terrain.
 *
 * <p><b>Wave 2a placeholder.</b> This runs in the <em>decoration</em> pass ({@code generateBlocks},
 * on a live {@code LevelAccessor}), which the port does not drive yet — only the terrain pass
 * ({@code generateChunk}) is wired. So it would be dead code today. It lands with P5 alongside
 * {@code TreeProvider} (631) and {@code CoverProvider} (903), which are what it actually delegates
 * to.
 */
public class SurfaceProvider extends Provider {

    public static SurfaceProvider loadProvider(CityWorldGenerator generator) {
        return new SurfaceProvider();
    }

    /** No-op until the decoration pass is wired (P5). Whole-chunk variant. */
    public void generateSurface(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
            AbstractCachedYs blockYs, int addTo, boolean includeTrees) {
    }

    /** No-op until the decoration pass is wired (P5). Single-column variant. */
    public void generateSurface(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, int x, int y, int z,
            boolean includeTrees) {
    }
}
