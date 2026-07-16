package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * Stub of the original {@code ThingProvider} (492 lines) — buried oddities: fossils, wrecks and
 * other set-dressing under the terrain.
 *
 * <p><b>Wave 2 placeholder.</b> Runs in the decoration pass, which is not driven yet (P5).
 */
public class ThingProvider extends Provider {

    public static ThingProvider loadProvider(CityWorldGenerator generator) {
        return new ThingProvider();
    }

    /** P5: buries a fossil somewhere in the column. */
    public void generateBones(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk,
            AbstractCachedYs blockYs, Odds odds) {
    }
}
