package me.daddychurchill.CityWorld.api;

/**
 * A lot's shop classification: <em>where</em> it sits ({@link ShopScale}) and <em>what</em> it sells
 * ({@link ShopTrade}). Position-independent — this is the "what kind of shop", carried on the planned
 * lot and decided seed-deterministically at plan time, so it round-trips: the same seed yields the
 * same shop on the same corner forever.
 *
 * <p>A lot that is not a shop carries no {@code ShopType} (the accessor returns {@code null}). To ask
 * "what shop is here" from outside, see {@link CityWorldShops}, which pairs this with a location.
 */
public record ShopType(ShopScale scale, ShopTrade trade) {

    /** Shorthand for UI/commands, e.g. "Map seller (High street)". */
    public String describe() {
        return trade.displayName() + " (" + scale.displayName() + ")";
    }
}
