package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Plats.Urban.RoundaboutCenterLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * The context that lays down roads, and the buildings that line them.
 *
 * <p>The schematic-backed roundabout centre is the one piece still deferred — it needs
 * {@code Clipboard}/{@code PasteProvider} (P6), so a built roundabout always gets the generated
 * {@code RoundaboutCenterLot} rather than a player's schematic.
 */
public class RoadContext extends UrbanContext {

	public RoadContext(CityWorldGenerator generator) {
		super(generator);

		// Upstream: setSchematicFamily(SchematicFamily.ROUNDABOUT, 1) — schematics are P6.
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		super.populateMap(generator, platmap);

	}

	@Override
	public void validateMap(CityWorldGenerator generator, PlatMap platmap) {

	}

	public PlatLot createRoadLot(CityWorldGenerator generator, PlatMap platmap, int x, int z, boolean roundaboutPart,
			PlatLot oldLot) {
		PlatLot result = null;

		// see if the old lot has a suggestion?
		if (oldLot != null)
			result = oldLot.repaveLot(generator, platmap);

		// if not then lets do return the standard one
		if (result == null)
			result = new RoadLot(platmap, platmap.originX + x, platmap.originZ + z, generator.connectedKeyForPavedRoads,
					roundaboutPart);

		// ok... we are done
		return result;
	}

	public PlatLot createRoundaboutStatueLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {

		// Upstream first asks getSingleSchematic(...) for a player-supplied roundabout centre and
		// falls back to the generated one. Schematics are P6, so only the fallback exists.
		return new RoundaboutCenterLot(platmap, platmap.originX + x, platmap.originZ + z);
	}
}
