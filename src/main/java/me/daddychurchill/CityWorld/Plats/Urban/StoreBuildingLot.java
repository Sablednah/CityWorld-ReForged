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

	// A store is a shop: classify it (scale from the district, trade by position — see pickShopType). Decided
	// here at plan time so it is seed-deterministic and readable without generating blocks (see getShopType).
	private ShopType shopType;

	public StoreBuildingLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		contentStyle = pickContentStyle();
		shopType = pickShopType(platmap);
	}

	@Override
	protected net.minecraft.tags.TagKey<net.minecraft.world.level.block.Block> exteriorDoorPool() {
		return me.daddychurchill.CityWorld.Support.MaterialTags.FITTINGS_STORE_DOOR;
	}

	/**
	 * Every chunk of a store is its own shop — it gets its own counter, keeper, name and sign — so every chunk
	 * gets its own TRADE, and within a district no two that touch are the same. It used to be rolled once per building and copied
	 * across the connected footprint, and once stores began to run together that made a block of six fletchers,
	 * then a block of drapers (owner, 2026-10-02).
	 *
	 * <p>By position, not by the chunk's odds: the district's trades are shuffled once per platmap, and a chunk
	 * takes the one at {@code chunkX + 2 * chunkZ} — a step of one east-west and two north-south, so the four
	 * neighbours and the diagonals all differ (any list of four or more). Neighbouring chunks' first odds rolls
	 * are correlated, which is how four museum halls in a row came to show the same fossil.
	 */
	private ShopType pickShopType(PlatMap platmap) {
		ShopScale scale = platmap.context != null ? platmap.context.shopScale() : ShopScale.HIGH_STREET;
		List<ShopTrade> trades = ShopTrade.tradesFor(scale);
		if (trades.isEmpty())
			return null;
		chunkOdds.getRandomInt(trades.size()); // the old roll, still drawn: everything after it stays where it was
		List<ShopTrade> order = new java.util.ArrayList<>(trades);
		java.util.Collections.shuffle(order, new java.util.Random(platmap.generator.getWorldSeed()
				^ (platmap.originX * 341873128712L + platmap.originZ * 132897987541L)));
		return new ShopType(scale, order.get(Math.floorMod(chunkX + 2 * chunkZ, order.size())));
	}

	@Override
	public ShopType getShopType() {
		return shopType;
	}

	// A store's containers hold its trade's stock — the counter barrel, the storeroom chests upstairs, a mod's
	// crates. They used to roll the ordinary building table: an armourer's chests held paper and paintings
	// (owner, 2026-10-02).
	@Override
	public me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation defaultLoot() {
		return me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation.SHOP;
	}

	@Override
	public String ownLootTable() {
		return shopType == null ? null : shopType.stockTable();
	}

	@Override
	public boolean ownLootReplacesTabled() {
		return true;
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
			// the trade is NOT shared: each chunk of the building is a shop of its own (see pickShopType)
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
