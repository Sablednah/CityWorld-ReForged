package me.daddychurchill.CityWorld.Context.Vanilla;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.NatureContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Vanilla.ShorelineLot;
import me.daddychurchill.CityWorld.Plats.Vanilla.VanillaNatureLot;
import me.daddychurchill.CityWorld.Support.HeightInfo;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The wilderness of a vanilla-terrain world: the survey that keeps the city inside its own patch of levelled
 * ground, and nothing else.
 *
 * <p>The overworld's {@link NatureContext} also seeds set-pieces by terrain — bunkers under hills, oil platforms
 * at sea, mine entrances, castles on peaks. Here the land outside a city is vanilla's, untouched, so only the
 * half of {@code populateMap} that matters is kept: any chunk that is not city ground goes to a nature lot that
 * draws nothing.
 */
public class VanillaNatureContext extends NatureContext {

	public VanillaNatureContext(CityWorldGenerator generator) {
		super(generator);
	}

	@Override
	public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return new VanillaNatureLot(platmap, platmap.originX + x, platmap.originZ + z);
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++)
				if (platmap.getLot(x, z) == null
						&& !HeightInfo.isBuildableAt(generator, (platmap.originX + x) * SupportBlocks.sectionBlockWidth,
								(platmap.originZ + z) * SupportBlocks.sectionBlockWidth))
					platmap.recycleLot(x, z);
		// The city backs away from its river: every chunk of city ground beside it is kept from the buildings here,
		// and once the district is known it becomes what Plats.River.Waterside chooses — a quay where the city is
		// built up, the natural bank (now and then a rustic jetty) where it is rural (ShapeProvider_Vanilla.validateLots).
		// A road may still take it.
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++) {
				int chunkX = platmap.originX + x, chunkZ = platmap.originZ + z;
				if (platmap.getLot(x, z) != null || !ShorelineLot.belongsAt(generator.citySites, chunkX, chunkZ))
					continue;
				platmap.recycleLot(x, z); // kept for the waterside, chosen once the district is known (validateLots)
			}
	}
}
