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
		Odds odds = platmap.getOddsGenerator();
		for (Clipboard clip : list) {
			if (odds.playOdds(clip.oddsOfAppearance))
				platmap.placeSpecificClip(generator, odds, clip);
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
