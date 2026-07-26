package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;

abstract class RuralContext extends CivilizedContext {

	RuralContext(CityWorldGenerator generator) {
		super(generator);

		maximumFloorsAbove = 1;
		maximumFloorsBelow = 1;
	}

	// Rural and residential districts (Neighborhood/Farm/Outland) get small standalone corner shops
	// rather than the commercial core's high-street parade.
	@Override
	public me.daddychurchill.CityWorld.api.ShopScale shopScale() {
		return me.daddychurchill.CityWorld.api.ShopScale.CORNER_SHOP;
	}
}
