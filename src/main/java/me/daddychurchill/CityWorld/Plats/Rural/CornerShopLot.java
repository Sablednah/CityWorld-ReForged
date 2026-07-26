package me.daddychurchill.CityWorld.Plats.Rural;

import java.util.List;

import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.api.ShopScale;
import me.daddychurchill.CityWorld.api.ShopTrade;
import me.daddychurchill.CityWorld.api.ShopType;

/**
 * A rural/residential corner shop — the small standalone {@link ShopScale#CORNER_SHOP} counterpart to
 * the high street's {@link me.daddychurchill.CityWorld.Plats.Urban.StoreBuildingLot}. Built as an
 * ordinary {@link HouseLot} (a converted house / village shopfront) that additionally carries a
 * {@link ShopType}, so the shared {@code ShopFitter} pass drops its trade's job block on the ground
 * floor for free — newsagent (lectern), greengrocer (composter), butcher (smoker), fishmonger (barrel)
 * or apothecary (brewing stand). Scale comes from the district ({@code RuralContext} → CORNER_SHOP);
 * the trade is rolled per lot and stays constant for the seed.
 */
public class CornerShopLot extends HouseLot {

    private final ShopType shopType;

    public CornerShopLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        this.shopType = pickShopType(platmap);
    }

    private ShopType pickShopType(PlatMap platmap) {
        ShopScale scale = platmap.context != null ? platmap.context.shopScale() : ShopScale.CORNER_SHOP;
        List<ShopTrade> trades = ShopTrade.tradesFor(scale);
        if (trades.isEmpty())
            return null;
        return new ShopType(scale, trades.get(chunkOdds.getRandomInt(trades.size())));
    }

    @Override
    public ShopType getShopType() {
        return shopType;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new CornerShopLot(platmap, chunkX, chunkZ);
    }
}
