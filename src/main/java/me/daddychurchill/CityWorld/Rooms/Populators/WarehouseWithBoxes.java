package me.daddychurchill.CityWorld.Rooms.Populators;

import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.StorageDoubleRowRoom;

public class WarehouseWithBoxes extends RoomProvider {

	public WarehouseWithBoxes() {
		super();

		roomTypes.add(new StorageDoubleRowRoom(Material.PISTON));
	}

}
