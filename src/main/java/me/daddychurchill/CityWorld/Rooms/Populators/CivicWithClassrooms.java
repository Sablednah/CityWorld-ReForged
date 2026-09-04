package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.ClassRoom;
import me.daddychurchill.CityWorld.Rooms.DeskAdminRoom;
import me.daddychurchill.CityWorld.Rooms.LibrarySingleRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeChairsRoom;

/** School floors: classrooms, the teachers' office, and the school library. */
public class CivicWithClassrooms extends RoomProvider {

	public CivicWithClassrooms() {
		super();

		roomTypes.add(new ClassRoom());
		roomTypes.add(new ClassRoom());
		roomTypes.add(new ClassRoom());
		roomTypes.add(new ClassRoom());
		roomTypes.add(new DeskAdminRoom());
		roomTypes.add(new LibrarySingleRoom());
		roomTypes.add(new LoungeChairsRoom());
	}
}
