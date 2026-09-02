package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.EmptyRoom;
import me.daddychurchill.CityWorld.Rooms.StorageDoubleShelvesRoom;
import me.daddychurchill.CityWorld.Rooms.StorageSingleShelvesRoom;
import me.daddychurchill.CityWorld.Rooms.WorkshopRoom;

/** Industrial floors: workbenches, anvils and crates. */
public class WorkshopWithBenches extends RoomProvider {

	public WorkshopWithBenches() {
		super();

		roomTypes.add(new WorkshopRoom());
		roomTypes.add(new WorkshopRoom());
		roomTypes.add(new WorkshopRoom());
		roomTypes.add(new StorageSingleShelvesRoom());
		roomTypes.add(new StorageDoubleShelvesRoom());
		roomTypes.add(new EmptyRoom());
	}
}
