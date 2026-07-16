package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;

/**
 * Street names — an "odonym" is a road's name.
 *
 * <p><b>Stubbed for wave 2 (78 lines upstream).</b> The names get painted onto street signs in the
 * decoration pass, which the port does not drive yet (P5). Returning blank lines means
 * {@code RoadLot} puts up an unlettered sign rather than crashing; the real word lists arrive with
 * P5, and reading them from config is P7.
 */
public class OdonymProvider extends Provider {

    private static final String[] NO_NAME = new String[] { "", "", "", "" };

    public static OdonymProvider loadProvider(CityWorldGenerator generator, Odds odds) {
        return new OdonymProvider();
    }

    /** P5: the four sign lines naming a north/south street. */
    public String[] generateNorthSouthStreetOdonym(CityWorldGenerator generator, int x, int z) {
        return NO_NAME.clone();
    }

    /** P5: the four sign lines naming a west/east street. */
    public String[] generateWestEastStreetOdonym(CityWorldGenerator generator, int x, int z) {
        return NO_NAME.clone();
    }

    /** P5: weathers a sign's text for the decayed world styles. */
    public void decaySign(Odds odds, String[] text) {
    }
}
