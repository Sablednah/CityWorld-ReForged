package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * Stub of the original {@code StructureInAirProvider} (207 lines) — the things floating above a
 * city: hot air balloons and blimps, tethered to building roofs.
 *
 * <p><b>Wave 2b placeholder.</b> Drawn in the decoration pass (P5). Buildings get their roofs
 * either way; they just have nothing moored to them.
 */
public class StructureInAirProvider extends Provider {

    public final static int hotairBalloonHeight = 16;

    public static StructureInAirProvider loadProvider(CityWorldGenerator generator) {
        return new StructureInAirProvider();
    }

    /** P5: moors a balloon to a roof. */
    public void generateBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachX,
            int attachY, int attachZ, Odds odds) {
    }

    /** P5: a hot air balloon sitting on the ground, as a farm's landed one. */
    public void generateHotairBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context,
            int bottomY, Odds odds) {
    }

    /** P5: moors a larger balloon, centred on the roof. */
    public void generateBigBalloon(CityWorldGenerator generator, SupportBlocks chunk, DataContext context, int attachY,
            Odds odds) {
    }

    /**
     * P5: a flying saucer, which a bunker occasionally keeps one of.
     *
     * <p>Safe to leave empty — the call site ({@code BunkerLot}) is {@code void} and reads nothing
     * back. The bunker builds; there is just nothing parked in it.
     */
    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int y, boolean drawLegs) {
        generateSaucer(generator, chunk, 7, y, 7, drawLegs);
    }

    /** P5. */
    public void generateSaucer(CityWorldGenerator generator, SupportBlocks chunk, int x, int y, int z,
            boolean drawLegs) {
    }
}
