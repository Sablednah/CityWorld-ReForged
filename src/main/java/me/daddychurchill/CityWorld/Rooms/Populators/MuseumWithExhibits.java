package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.EmptyRoom;
import me.daddychurchill.CityWorld.Rooms.ExhibitRoom;
import me.daddychurchill.CityWorld.Rooms.LibrarySingleRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeChairsRoom;

/** Museum floors: exhibit pedestals with the odd reading room and bench. */
public class MuseumWithExhibits extends RoomProvider {

	public MuseumWithExhibits() {
		super();

		roomTypes.add(new ExhibitRoom());
		roomTypes.add(new ExhibitRoom());
		roomTypes.add(new ExhibitRoom());
		roomTypes.add(new ExhibitRoom());
		roomTypes.add(new LibrarySingleRoom());
		roomTypes.add(new LoungeChairsRoom());
		roomTypes.add(new EmptyRoom());
	}
}
