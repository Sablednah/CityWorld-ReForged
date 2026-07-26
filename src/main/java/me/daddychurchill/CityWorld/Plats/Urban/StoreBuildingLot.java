package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.FinishedBuildingLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.RoomProvider;
import me.daddychurchill.CityWorld.Rooms.Populators.StoreWithBooks;
import me.daddychurchill.CityWorld.Rooms.Populators.StoreWithNothing;
import me.daddychurchill.CityWorld.Rooms.Populators.StoreWithRandom;
import me.daddychurchill.CityWorld.Rooms.Populators.StoreWithRegisters;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.api.ShopScale;
import me.daddychurchill.CityWorld.api.ShopTrade;
import me.daddychurchill.CityWorld.api.ShopType;

import java.util.List;

public class StoreBuildingLot extends FinishedBuildingLot {

	private static final RoomProvider contentsRandom = new StoreWithRandom();
	private static final RoomProvider contentsBooks = new StoreWithBooks();
	private static final RoomProvider contentsEmpty = new StoreWithNothing();
	private static final RoomProvider contentsRegisters = new StoreWithRegisters();

	public enum ContentStyle {
		RANDOM, BOOKS, EMPTY
	}

	private ContentStyle contentStyle;

	// A store is a shop: classify it (scale from the district, trade rolled per building). Decided here
	// at plan time so it is seed-deterministic and readable without generating blocks (see getShopType).
	private ShopType shopType;

	public StoreBuildingLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		contentStyle = pickContentStyle();
		shopType = pickShopType(platmap);
	}

	private ShopType pickShopType(PlatMap platmap) {
		ShopScale scale = platmap.context != null ? platmap.context.shopScale() : ShopScale.HIGH_STREET;
		List<ShopTrade> trades = ShopTrade.tradesFor(scale);
		if (trades.isEmpty())
			return null;
		return new ShopType(scale, trades.get(chunkOdds.getRandomInt(trades.size())));
	}

	@Override
	public ShopType getShopType() {
		return shopType;
	}

	private ContentStyle pickContentStyle() {
		switch (chunkOdds.getRandomInt(5)) {
		case 1:
			return ContentStyle.BOOKS;
		case 2:
			return ContentStyle.RANDOM;
		default:
			return ContentStyle.EMPTY;
		}
	}

	@Override
	public boolean makeConnected(PlatLot relative) {
		boolean result = super.makeConnected(relative);

		// other bits
		if (result && relative instanceof StoreBuildingLot) {
			StoreBuildingLot relativebuilding = (StoreBuildingLot) relative;

			// any other bits
			contentStyle = relativebuilding.contentStyle;
			// one building = one shop: share the classification across the connected footprint
			shopType = relativebuilding.shopType;
		}

		return result;
	}

	@Override
	protected InteriorStyle getFloorsInteriorStyle(int floor) {
		return InteriorStyle.COLUMNS_OFFICES;
	}

	@Override
	public RoomProvider roomProviderForFloor(CityWorldGenerator generator, SupportBlocks chunk, int floor, int floorY) {
		if (floor == 0)
			return contentsRegisters;
		else
			switch (contentStyle) {
			case BOOKS:
				return contentsBooks;
			case RANDOM:
				return contentsRandom;
			default:
				return contentsEmpty;
			}
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new StoreBuildingLot(platmap, chunkX, chunkZ);
	}

}
