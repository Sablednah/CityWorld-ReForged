package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.Clipboard;
import me.daddychurchill.CityWorld.Clipboard.ClipboardLot;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Plats.Urban.RoundaboutCenterLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * The context that lays down roads, and the buildings that line them.
 *
 * <p>A built roundabout's centre is a schematic from the {@code ROUNDABOUT} family (the player-name
 * statues) when {@code includeSchematics} is on and one rolls up; otherwise the generated
 * {@code RoundaboutCenterLot} (a procedural fountain/pedestal).
 */
public class RoadContext extends UrbanContext {

	public RoadContext(CityWorldGenerator generator) {
		super(generator);

		// The statues are single-chunk clips that sit at a roundabout's centre.
		setSchematicFamily(SchematicFamily.ROUNDABOUT, 1);
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

		// Ask for a single-chunk ROUNDABOUT clip (a statue); fall back to the generated centre. The
		// odds/facing draw from the lot's own deterministic dice so the choice is seed-stable.
		Odds odds = platmap.getChunkOddsGenerator(platmap.originX + x, platmap.originZ + z);
		Clipboard clip = getSingleSchematic(generator, platmap, odds, x, z);
		if (clip != null)
			return new ClipboardLot(platmap, platmap.originX + x, platmap.originZ + z, clip, 0, 0, LotStyle.ROUNDABOUT);

		return new RoundaboutCenterLot(platmap, platmap.originX + x, platmap.originZ + z);
	}
}
