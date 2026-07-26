package me.daddychurchill.CityWorld.api;

/**
 * The <b>setting</b> a shop sits in — the coarse half of CityWorld's shop taxonomy (the fine half is
 * {@link ShopTrade}). Decided by the district a lot lands in, not by the lot itself: rural and
 * residential families ({@code Neighborhood}/{@code Farm}/{@code Outland}) yield {@link #CORNER_SHOP},
 * the commercial cores ({@code Lowrise}/{@code Midrise}/{@code Highrise}) yield {@link #HIGH_STREET}.
 *
 * <p>Part of CityWorld's small <b>public API</b>: other mods may read this (via {@link CityWorldShops})
 * to react to what kind of retail a player is standing in. The set of values is a stable contract —
 * additions here are additive, existing names will not be renamed or removed.
 */
public enum ShopScale {

    /** A small standalone shop in a rural or residential setting — the village newsagent / general store. */
    CORNER_SHOP("Corner shop"),

    /** A shopfront in a dense commercial row — the Oxford-Street parade of specialist retailers. */
    HIGH_STREET("High street");

    private final String displayName;

    ShopScale(String displayName) {
        this.displayName = displayName;
    }

    /** Human-readable label for UI/commands. */
    public String displayName() {
        return displayName;
    }
}
