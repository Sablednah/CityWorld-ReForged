package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.StorageDoubleRowRoom;
import me.daddychurchill.CityWorld.Rooms.StorageSingleRowRoom;

public class WarehouseWithBooks extends RoomProvider {

	public WarehouseWithBooks() {
		super();

		roomTypes.add(new StorageSingleRowRoom(Material.BOOKSHELF));
		roomTypes.add(new StorageDoubleRowRoom(Material.BOOKSHELF));
	}

}
