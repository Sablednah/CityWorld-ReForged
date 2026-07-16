package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.Support.PlatMap;

public abstract class ConnectedLot extends PlatLot {

	protected long connectedkey;

	protected ConnectedLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

		// Upstream: platmap.generator.getConnectionKey() — one shared RNG, so the key depended on
		// how many lots had been built before this one. That cannot survive concurrent, arbitrarily
		// ordered platmap planning. Derived from position instead; only key equality is ever
		// tested, so the meaning is unchanged. See CityWorldGenerator.getConnectionKey.
		connectedkey = platmap.generator.getConnectionKey(chunkX, chunkZ);
	}

	@Override
	public long getConnectedKey() {
		return connectedkey;
	}

	@Override
	public boolean makeConnected(PlatLot relative) {
		if (relative == null)
			return false;
		connectedkey = relative.getConnectedKey();
		return isConnected(relative);
	}

	@Override
	public boolean isConnectable(PlatLot relative) {
		if (relative == null)
			return false;
		return getClass().isInstance(relative);
	}

	@Override
	public boolean isConnected(PlatLot relative) {
		if (relative == null)
			return false;
		return connectedkey == relative.getConnectedKey();
	}

	@Override
	public PlatLot validateLot(PlatMap platmap, int platX, int platZ) {
		return null;
	}

}
