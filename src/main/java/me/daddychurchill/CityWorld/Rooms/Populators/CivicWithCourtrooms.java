package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.CourtRoom;
import me.daddychurchill.CityWorld.Rooms.DeskAdminRoom;
import me.daddychurchill.CityWorld.Rooms.LibrarySingleRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeChairsRoom;

/** Courthouse floors: courtrooms, clerks' desks, and the law library. */
public class CivicWithCourtrooms extends RoomProvider {

	public CivicWithCourtrooms() {
		super();

		roomTypes.add(new CourtRoom());
		roomTypes.add(new CourtRoom());
		roomTypes.add(new CourtRoom());
		roomTypes.add(new DeskAdminRoom());
		roomTypes.add(new LibrarySingleRoom());
		roomTypes.add(new LoungeChairsRoom());
	}
}
