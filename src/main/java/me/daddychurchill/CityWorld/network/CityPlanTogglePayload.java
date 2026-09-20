package me.daddychurchill.CityWorld.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: the player's city-plan settings, from their map mod's own UI (JourneyMap's
 * options screen, or the toggle button on its fullscreen map).
 *
 * <p>Both live on the server because that is where the overlay is drawn from — see
 * {@code MapMarkers.setCityPlan} and {@code setCityPlanBudget}. {@code /citymap} sets the same two
 * from the other direction, which is how a player on a server without the map mod's options screen
 * (or without CityWorld installed client-side) still gets a say.
 *
 * @param on   draw the plan at all
 * @param keep how many overlays this client is willing to hold before the furthest are dropped
 */
public record CityPlanTogglePayload(boolean on, int keep) {

    // 1.20.1 has no CustomPacketPayload: a message is a plain object plus an encoder and a decoder,
    // and its identity is the channel index it is registered under — see CityWorldNetwork.
    public static void encode(CityPlanTogglePayload payload, FriendlyByteBuf buf) {
        buf.writeBoolean(payload.on);
        buf.writeVarInt(payload.keep);
    }

    public static CityPlanTogglePayload decode(FriendlyByteBuf buf) {
        return new CityPlanTogglePayload(buf.readBoolean(), buf.readVarInt());
    }
}
