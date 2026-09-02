package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.BedNookRoom;
import me.daddychurchill.CityWorld.Rooms.ClosetRoom;
import me.daddychurchill.CityWorld.Rooms.LibraryStudyRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeCouchRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeKitchenetteRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeTVRoom;

/** Residential tower floors: bed nooks, kitchenettes, lounges and studies. */
public class ApartmentWithFlats extends RoomProvider {

	public ApartmentWithFlats() {
		super();

		roomTypes.add(new BedNookRoom());
		roomTypes.add(new BedNookRoom());
		roomTypes.add(new LoungeKitchenetteRoom());
		roomTypes.add(new LoungeCouchRoom());
		roomTypes.add(new LoungeTVRoom());
		roomTypes.add(new LibraryStudyRoom());
		roomTypes.add(new ClosetRoom());
	}
}
