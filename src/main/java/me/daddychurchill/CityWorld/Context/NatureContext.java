package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
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
 * <p><b>Still simplified:</b> upstream also seeds set-pieces by terrain type here — bunkers in the
 * midlands, radio towers and mine entrances on highlands, oil platforms in deep sea, flying saucers
 * and hot air balloons overhead — and tracks the platmap's highest and lowest spots to place two
 * "special" lots. Those lot families are not ported ({@code BunkerLot} alone is 1037 lines), so the
 * survey below keeps the part that shapes the world and leaves the decoration of it for later. Its
 * {@code HeightState} switch is the only consumer of {@code HeightInfo}'s classification.
 */
public class NatureContext extends UncivilizedContext {

	public NatureContext(CityWorldGenerator generator) {
		super(generator);

		oddsOfIsolatedConstructs = Odds.oddsSomewhatLikely;
	}

	@Override
	public void populateMap(CityWorldGenerator generator, PlatMap platmap) {
		int originX = platmap.originX;
		int originZ = platmap.originZ;

		// is this natural or buildable?
		for (int x = 0; x < PlatMap.Width; x++) {
			for (int z = 0; z < PlatMap.Width; z++) {
				if (platmap.getLot(x, z) == null) {

					// what is the world location of the lot?
					int blockX = (originX + x) * SupportBlocks.sectionBlockWidth;
					int blockZ = (originZ + z) * SupportBlocks.sectionBlockWidth;

					// get the height info for this chunk
					HeightInfo heights = HeightInfo.getHeightsFaster(generator, blockX, blockZ);
					if (!heights.isBuildable()) {

						// Upstream picks a set-piece for the terrain type here (see the class
						// javadoc) and only falls back to plain nature when it chooses nothing.
						// Until those lots are ported, everything unbuildable is plain nature.
						platmap.recycleLot(x, z);
					}
				}
			}
		}
	}

	/** Inherited from {@link UncivilizedContext}: recycles lots whose isolation no longer holds. */
	@Override
	public void validateMap(CityWorldGenerator generator, PlatMap platmap) {
		super.validateMap(generator, platmap);
	}
}
