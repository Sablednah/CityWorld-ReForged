package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.ClosetRoom;
import me.daddychurchill.CityWorld.Rooms.DeskAdminRoom;
import me.daddychurchill.CityWorld.Rooms.DeskExecutiveRoom;
import me.daddychurchill.CityWorld.Rooms.LoungeChairsRoom;
import me.daddychurchill.CityWorld.Rooms.MeetingForFourRoom;
import me.daddychurchill.CityWorld.Rooms.MeetingForSixRoom;

/** City-hall floors: executive and admin desks, meeting rooms, a waiting area. */
public class CivicWithOffices extends RoomProvider {

	public CivicWithOffices() {
		super();

		roomTypes.add(new DeskExecutiveRoom());
		roomTypes.add(new DeskExecutiveRoom());
		roomTypes.add(new DeskAdminRoom());
		roomTypes.add(new DeskAdminRoom());
		roomTypes.add(new MeetingForSixRoom());
		roomTypes.add(new MeetingForFourRoom());
		roomTypes.add(new LoungeChairsRoom());
		roomTypes.add(new ClosetRoom());
	}
}
