package me.daddychurchill.CityWorld.Clipboard;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;

/**
 * The clips a context may drop into one {@link PlatMap} — the schematics of a single
 * {@link PasteProvider.SchematicFamily}, already loaded.
 *
 * <p>Ported from upstream's {@code ClipboardList} with two changes: it is backed by a list rather
 * than a name-keyed map (the classic catalog has buildings that share a name across families, and a
 * map silently dropped the duplicates), and it is immutable once built — the family's clips are
 * resolved from {@link SchematicLibrary} up front, so there is nothing to mutate at populate time.
 */
public final class ClipboardList implements Iterable<Clipboard> {

	private final List<Clipboard> list;

	public ClipboardList(List<Clipboard> clips) {
		this.list = new ArrayList<>(clips);
	}

	public boolean isEmpty() {
		return list.isEmpty();
	}

	public int count() {
		return list.size();
	}

	@Override
	public Iterator<Clipboard> iterator() {
		return list.iterator();
	}

	/**
	 * Give each clip a roll of the dice; the ones that come up drop into an empty run of lots. The
	 * odds and the placement search both draw from the platmap's own deterministic {@link Odds}, so
	 * the same seed lays out the same buildings.
	 */
	public void populate(CityWorldGenerator generator, PlatMap platmap) {
		populate(generator, platmap, Integer.MAX_VALUE);
	}

	/**
	 * As {@link #populate(CityWorldGenerator, PlatMap)}, but stop after {@code maxPlacements} clips have
	 * dropped. City families place unlimited (their platmaps are mostly full of roads/buildings anyway,
	 * so the empty-run search is the real limiter); the wild is different — a Nature platmap is 100
	 * empty lots at populate time, so <em>every</em> clip that rolls would fit and the wild fills up.
	 * A small cap keeps it sparse — a landmark here and there, not wall-to-wall.
	 */
	public void populate(CityWorldGenerator generator, PlatMap platmap, int maxPlacements) {
		Odds odds = platmap.getOddsGenerator();
		int placed = 0;
		for (Clipboard clip : list) {
			if (placed >= maxPlacements)
				break;
			// Count only clips that ACTUALLY placed. A clip whose odds come up but finds no home (a land
			// build in an ocean platmap, an ocean build inland) must not burn a slot -- otherwise the
			// abundant land clips would use up the cap on water platmaps and the ocean/shore builds, whose
			// terrain IS present there, would never get a turn.
			if (odds.playOdds(clip.oddsOfAppearance) && platmap.placeSpecificClip(generator, odds, clip))
				placed++;
		}
	}

	/** The first single-chunk clip whose appearance odds come up, or {@code null} — for spot fills. */
	public Clipboard getSingleLot(CityWorldGenerator generator, PlatMap platmap, Odds odds, int placeX, int placeZ) {
		for (Clipboard clip : list) {
			if (clip.chunkX == 1 && clip.chunkZ == 1 && odds.playOdds(clip.oddsOfAppearance))
				return clip;
		}
		return null;
	}
}
