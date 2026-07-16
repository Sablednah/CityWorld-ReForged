package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * Stub of the original {@code NatureContext} (230 lines) — the context for the parts of the world
 * that stay wild.
 *
 * <p><b>Wave 1 placeholder.</b> Upstream this seeds a platmap with the nature lots (mountains,
 * gravel pits, bunkers, oil platforms, radio towers, castles, …), none of which are ported yet, and
 * it is also the fallback every other context falls back to. Inheriting {@link DataContext}'s
 * {@code createNaturalLot} gives plain natural chunks, which is what wave 1 wants: real terrain,
 * nothing built on it. Wave 2 replaces this wholesale.
 */
public class NatureContext extends DataContext {

    public NatureContext(CityWorldGenerator generator) {
        super();
    }

    @Override
    public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
    }

    @Override
    public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
    }
}
