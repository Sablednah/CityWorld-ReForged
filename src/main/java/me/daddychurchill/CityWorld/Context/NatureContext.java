package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * The context for the parts of the world that stay wild — and the fallback every other context
 * falls back to.
 *
 * <p><b>Simplified for wave 2.</b> Upstream's {@code populateMap} (230 lines) surveys each chunk's
 * {@code HeightInfo} and seeds the landscape with its set-pieces by terrain type — bunkers in the
 * midlands, radio towers on highlands, oil platforms in deep sea, flying saucers and hot air
 * balloons overhead, mine entrances. Every one of those is a lot family of its own
 * ({@code BunkerLot} alone is 1037 lines), so they land as their own wave. Until then this leaves
 * lots empty, which makes {@code PlatMap} recycle them to plain nature — upstream's own answer for
 * a chunk it decided not to put anything on.
 *
 * <p>The survey code is what needs porting when they arrive; it is the only consumer of
 * {@code HeightInfo}'s {@code HeightState} classification.
 */
public class NatureContext extends UncivilizedContext {

	public NatureContext(CityWorldGenerator generator) {
		super(generator);

		oddsOfIsolatedConstructs = Odds.oddsSomewhatLikely;
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		// Leave every lot empty: PlatMap's constructor recycles the holes into nature lots. That is
		// exactly the path upstream takes for a chunk none of its set-pieces claimed.
	}

	/** Inherited from {@link UncivilizedContext}: recycles lots whose isolation no longer holds. */
	@Override
	public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
		super.validateMap(generator, platmap);
	}

	/** Wave 2+: upstream picks among the nature set-pieces here. */
	@Override
	public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return super.createNaturalLot(generator, platmap, x, z);
	}
}
