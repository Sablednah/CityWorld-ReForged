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

	/** The share of riverside city chunks that are quays; the rest are the natural bank. */
	private static final double QUAY_ODDS = 0.45;

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
		// The city backs away from its river: every chunk of city ground beside it is planned before any building —
		// about half a quay (promenade, mooring, loading quay), the rest left to nature so the natural bank shows
		// and eases up to the street. A road may still take either.
		for (int x = 0; x < PlatMap.Width; x++)
			for (int z = 0; z < PlatMap.Width; z++) {
				int chunkX = platmap.originX + x, chunkZ = platmap.originZ + z;
				if (platmap.getLot(x, z) != null || !ShorelineLot.belongsAt(generator.citySites, chunkX, chunkZ))
					continue;
				if (generator.shapeProvider.getMicroOddsGeneratorAt(chunkX, chunkZ).playOdds(QUAY_ODDS))
					platmap.setLot(x, z, new ShorelineLot(platmap, chunkX, chunkZ));
				else
					platmap.recycleLot(x, z);
			}
	}
}
