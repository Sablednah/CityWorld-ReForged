package me.daddychurchill.CityWorld.Context.TheEnd;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.NatureContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.TheEnd.EndNatureLot;
import me.daddychurchill.CityWorld.Support.HeightInfo;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The End's wilderness: the survey that keeps cities off everything but flat island tops, and nothing else.
 *
 * <p>The overworld's {@link NatureContext} also seeds set-pieces by terrain — bunkers under hills, oil platforms
 * at sea, mine entrances, castles on peaks. None of that belongs on a vanilla End island (and "sea" here is the
 * void), so this keeps only the half of {@code populateMap} that matters: any chunk that is not flat at street
 * level goes to nature, which draws nothing.
 */
public class EndNatureContext extends NatureContext {

	public EndNatureContext(CityWorldGenerator generator) {
		super(generator);
	}

	@Override
	public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return new EndNatureLot(platmap, platmap.originX + x, platmap.originZ + z);
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++)
				if (platmap.getLot(x, z) == null
						&& !HeightInfo.isBuildableAt(generator, (platmap.originX + x) * SupportBlocks.sectionBlockWidth,
								(platmap.originZ + z) * SupportBlocks.sectionBlockWidth))
					platmap.recycleLot(x, z);
	}
}
