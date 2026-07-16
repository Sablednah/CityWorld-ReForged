package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * The context that lays down roads.
 *
 * <p>{@link #createRoadLot} is ported for real — it is what {@code PlatMap.paveLot} calls, and so
 * what actually puts roads in the world.
 *
 * <p><b>Two deliberate deviations, both temporary:</b>
 * <ul>
 *   <li>Upstream this extends {@code UrbanContext}, which exists to populate a platmap with
 *       <em>buildings</em> (office/store/library/park lots — none ported yet). Roads do not come
 *       from that path: {@code ShapeProvider.populateLots} places them via
 *       {@code platmap.populateRoads()} before it ever asks a context to populate. So extending
 *       {@code CivilizedContext} directly gets roads now without dragging the building families in.
 *       Restore the real parent when they land.
 *   <li>{@link #createRoundaboutStatueLot} needs {@code RoundaboutCenterLot} (356) or a schematic
 *       (P6). Roundabouts are gated off meanwhile — see {@code CityWorldSettings.includeRoundabouts}.
 * </ul>
 */
public class RoadContext extends CivilizedContext {

	public RoadContext(CityWorldGenerator generator) {
		super(generator);

		// Upstream: setSchematicFamily(SchematicFamily.ROUNDABOUT, 1) — schematics are P6.
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		// Upstream defers to UrbanContext, which places buildings. Wave 2b.
	}

	@Override
	public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
	}

	@Override
	protected PlatLot getBackfillLot(CityWorldGenerator generator, PlatMap platmap, Odds odds, int chunkX, int chunkZ) {
		// Only ever consulted for STRUCTURE lots, which nothing creates yet; null makes the caller
		// recycle the lot back to nature. Returns a real building lot in wave 2b.
		return null;
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

	/**
	 * Wave 2b: a {@code RoundaboutCenterLot}, or a schematic-backed one if any fits (P6). Until
	 * then roundabouts are switched off in settings, so this is unreachable — a plain road lot
	 * keeps it honest if that ever stops being true.
	 */
	public PlatLot createRoundaboutStatueLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return new RoadLot(platmap, platmap.originX + x, platmap.originZ + z, generator.connectedKeyForPavedRoads,
				true);
	}
}
