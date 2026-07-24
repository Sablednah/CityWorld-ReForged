package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.Nature.BunkerLot;
import me.daddychurchill.CityWorld.Plats.Nature.FlyingSaucerLot;
import me.daddychurchill.CityWorld.Plats.Nature.HotairBalloonLot;
import me.daddychurchill.CityWorld.Plats.Nature.MineEntranceLot;
import me.daddychurchill.CityWorld.Plats.Nature.MountainShackLot;
import me.daddychurchill.CityWorld.Plats.Nature.MountainTentLot;
import me.daddychurchill.CityWorld.Plats.Nature.OilPlatformLot;
import me.daddychurchill.CityWorld.Plats.Nature.OldCastleLot;
import me.daddychurchill.CityWorld.Plats.Nature.RadioTowerLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.AbstractYs.HeightState;
import me.daddychurchill.CityWorld.Support.HeightInfo;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * The context for the parts of the world that stay wild — and the fallback every other context
 * falls back to.
 *
 * <p><b>{@link #populateMap} runs before anything else is planned, and deciding where the cities
 * <em>can't</em> go is its real job.</b> It surveys every chunk's terrain and hands the unbuildable
 * ones — mountains, seas, anything not flat at street level — straight to nature. Two things depend
 * on that, and both break loudly without it:
 *
 * <ul>
 *   <li>Roads and buildings only ever fill lots that are still empty afterwards, so this is what
 *       keeps cities off the mountains rather than flattening them.
 *   <li>It is what makes {@code PlatMap.getNaturePercent()} mean anything. With nothing marked
 *       natural it reads 0.0 for every platmap, and {@code ShapeProvider_Normal.getContext}'s ladder
 *       then grades every single platmap as downtown highrise.
 * </ul>
 *
 * <p>Skipping this is what produced the reported "mountain mid-city / stone columns" — with the
 * whole map planned as city, the only lots left natural were roads that {@code validateRoads}
 * reclaimed, each one a 16×16 column of untouched terrain standing in a flattened downtown.
 *
 * <p>Beyond the survey, it also seeds set-pieces by terrain type: bunkers buried under the midlands
 * and highlands, mountain shacks and tents on sort-of-flat midlands, and — at the platmap's single
 * highest and lowest inner spots — a "special" lot chosen for that spot's {@link HeightState}: oil
 * platforms in deep sea, flying saucers and hot air balloons overhead, mine entrances in the
 * midlands, radio towers on highlands, an old castle on a peak. This is where {@code HeightInfo}'s
 * classification finally has a consumer.
 */
public class NatureContext extends UncivilizedContext {

	public NatureContext(CityWorldGenerator generator) {
		super(generator);

		// bumped from oddsSomewhatLikely: mine entrances are now gated on a shaft actually sitting below
		// (mines cluster into rare fields), so more isolated-construct spots are checked to make sure a
		// field reliably gets surface access.
		oddsOfIsolatedConstructs = Odds.oddsLikely;

		// Wild landmarks (castles, remote builds) run large and have room out here — allow up to a
		// 9-chunk footprint, well past the 4-chunk default the dense city families keep.
		setSchematicFamily(SchematicFamily.NATURE, 9);
	}

	private final static double oddsOfBunkers = Odds.oddsLikely;

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {

		// Let the user's own schematics claim their empty runs first; the terrain survey below only
		// ever acts on lots that are still empty, so it plugs the remaining holes with nature (natural
		// lots + set-pieces) around whatever the schematics took. (Caveat: placement doesn't yet check
		// the ground is flat under a wild build — that's the terrain-blending follow-up.)
		// Place wild schematics, but ONLY on the real post-road pass. populateMap runs twice for a
		// nature platmap (pre-road survey, then again as the committed context); placing in the survey
		// drops them before roads exist, so the road grid then stamps over half of each build and the
		// second pass drops a duplicate behind it. Gating on roadsPopulated lands them after the roads,
		// so the empty-run search steers clear. Capped so the wild stays sparse (a couple per platmap).
		if (platmap.roadsPopulated)
			populateSchematics(generator, platmap, 2);

		// random stuff?
		Odds platmapOdds = platmap.getOddsGenerator();
		boolean doBunkers = generator.getSettings().includeBunkers && platmapOdds.playOdds(oddsOfBunkers);
		boolean didBunkerEntrance = false;

		// where it all begins
		int originX = platmap.originX;
		int originZ = platmap.originZ;
		HeightInfo heights;

		// highest special place
		int maxHeight = Integer.MIN_VALUE;
		int maxHeightX = -1;
		int maxHeightZ = -1;
		HeightState maxState = HeightState.BUILDING;

		// lowest special place
		int minHeight = Integer.MAX_VALUE;
		int minHeightX = -1;
		int minHeightZ = -1;
		HeightState minState = HeightState.BUILDING;

		// is this natural or buildable?
		for (int x = 0; x < PlatMap.Width; x++) {
			for (int z = 0; z < PlatMap.Width; z++) {
				PlatLot current = platmap.getLot(x, z);
				if (current == null) {

					// what is the world location of the lot?
					int chunkX = originX + x;
					int chunkZ = originZ + z;
					int blockX = chunkX * SupportBlocks.sectionBlockWidth;
					int blockZ = chunkZ * SupportBlocks.sectionBlockWidth;

					// get the height info for this chunk
					heights = HeightInfo.getHeightsFaster(generator, blockX, blockZ);
					if (!heights.isBuildable()) {

						// our inner chunks?
						if (x > 0 && x < PlatMap.Width - 1 && z > 0 && z < PlatMap.Width - 1) {

							// extreme changes?
							if (heights.getMinHeight() < minHeight) {
								minHeight = heights.getMinHeight();
								minHeightX = x;
								minHeightZ = z;
								minState = heights.getState();
							}
							if (heights.getMaxHeight() > maxHeight) {
								maxHeight = heights.getMaxHeight();
								maxHeightX = x;
								maxHeightZ = z;
								maxState = heights.getState();
							}

							// innermost chunks?
							boolean potentialRoads = (x == (RoadLot.PlatMapRoadInset - 1)
									|| x == (PlatMap.Width - RoadLot.PlatMapRoadInset))
									&& (z == (RoadLot.PlatMapRoadInset - 1)
									|| z == (PlatMap.Width - RoadLot.PlatMapRoadInset));
							boolean doBunkerEntrance = doBunkers && !didBunkerEntrance && !potentialRoads;

							// what type of height are we talking about?
							switch (heights.getState()) {
							case MIDLAND:
								if (doBunkers && minHeight > BunkerLot.calcBunkerMinHeight(generator)) {
									current = createBuriedBuildingLot(generator, platmap, chunkX, chunkZ,
											doBunkerEntrance);

								} else if (heights.isSortaFlat() && generator.shapeProvider
										.isIsolatedConstructAt(originX + x, originZ + z, oddsOfIsolatedConstructs)) {
									current = createSurfaceBuildingLot(generator, platmap, originX + x, originZ + z,
											heights);
								}

								break;
							case HIGHLAND:
							case PEAK:
								if (doBunkers) {
									current = createBuriedBuildingLot(generator, platmap, chunkX, chunkZ,
											doBunkerEntrance);
								}
								break;
							default:
								break;
							}

							// did we do it?
							if (doBunkerEntrance && current != null)
								didBunkerEntrance = true;
						}

						// did current get defined?
						if (current != null)
							platmap.setLot(x, z, current);
						else
							platmap.recycleLot(x, z);
					}
				}
			}
		}

		// any special things to do?
		populateSpecial(generator, platmap, maxHeightX, maxHeight, maxHeightZ, maxState);
		populateSpecial(generator, platmap, minHeightX, minHeight, minHeightZ, minState);
	}

	private PlatLot createBuriedBuildingLot(CityWorldGenerator generator, PlatMap platmap, int x, int z,
			boolean firstOne) {
		if (generator.getSettings().includeBunkers)
			return new BunkerLot(platmap, x, z, firstOne);
		return null;
	}

	protected PlatLot createSurfaceBuildingLot(CityWorldGenerator generator, PlatMap platmap, int x, int z,
			HeightInfo heights) {
		if (generator.getSettings().includeHouses)
			if (platmap.getOddsGenerator().flipCoin())
				return new MountainShackLot(platmap, x, z);
			else
				return new MountainTentLot(platmap, x, z);
		return null;
	}

	protected void populateSpecial(CityWorldGenerator generator, PlatMap platmap, int x, int y, int z,
			HeightState state) {

		// what type of height are we talking about?
		if (state != HeightState.BUILDING && generator.shapeProvider.isIsolatedConstructAt(platmap.originX + x,
				platmap.originZ + z, oddsOfIsolatedConstructs)) {
//		if (state != HeightState.BUILDING) {
			PlatLot current = null;
			Odds platmapOdds = platmap.getOddsGenerator();

			// A mine entrance takes priority wherever a CityWorld shaft sits below — at any land band, not
			// just MIDLAND — so mine fields (now clustered, and common under mountains) reliably get
			// surface access.
			if (generator.getSettings().includeMines && generator.shapeProvider.hasMineShaftBelow(generator,
					platmap.originX + x, platmap.originZ + z))
				current = new MineEntranceLot(platmap, platmap.originX + x, platmap.originZ + z);

			// what to make?
			if (current == null)
				switch (state) {
			case DEEPSEA:
				// Oil rigs
				if (generator.getSettings().includeBuildings)
					current = new OilPlatformLot(platmap, platmap.originX + x, platmap.originZ + z);
				break;
			case SEA:
				if (generator.getSettings().includeAirborneStructures) {
					if (platmapOdds.playOdds(Odds.oddsEnormouslyUnlikely))
						current = new FlyingSaucerLot(platmap, platmap.originX + x, platmap.originZ + z);
					else if (platmapOdds.playOdds(Odds.oddsSomewhatLikely))
						current = new HotairBalloonLot(platmap, platmap.originX + x, platmap.originZ + z);

					// TODO boat!
				}
				break;
//			case BUILDING:
//				break;
			case LOWLAND:
				if (generator.getSettings().includeAirborneStructures) {
					if (platmapOdds.playOdds(Odds.oddsEnormouslyUnlikely))
						current = new FlyingSaucerLot(platmap, platmap.originX + x, platmap.originZ + z);
					else if (platmapOdds.playOdds(Odds.oddsSomewhatLikely))
						current = new HotairBalloonLot(platmap, platmap.originX + x, platmap.originZ + z);

					// TODO statue overlooking the city?
				}
				break;
			case MIDLAND:
				// mine entrances handled above (any band over a field); nothing else here
				break;
			case HIGHLAND:
				// Radio towers
				if (generator.getSettings().includeBuildings)
					current = new RadioTowerLot(platmap, platmap.originX + x, platmap.originZ + z);
				break;
			case PEAK:
				// Old castle
				if (generator.getSettings().includeBuildings)
					current = new OldCastleLot(platmap, platmap.originX + x, platmap.originZ + z);
				break;
			default:
				break;
			}

			if (current != null) {
				platmap.setLot(x, z, current);
			}
		}
	}

	/** Inherited from {@link UncivilizedContext}: recycles lots whose isolation no longer holds. */
	@Override
	public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
		super.validateMap(generator, platmap);
	}
}
