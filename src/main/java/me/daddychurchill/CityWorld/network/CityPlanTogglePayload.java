package me.daddychurchill.CityWorld.network;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

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
public record CityPlanTogglePayload(boolean on, int keep) implements CustomPacketPayload {

    public static final Type<CityPlanTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "city_plan_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityPlanTogglePayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.on);
                buf.writeVarInt(p.keep);
            },
            buf -> new CityPlanTogglePayload(buf.readBoolean(), buf.readVarInt()));

    @Override
    public Type<CityPlanTogglePayload> type() {
        return TYPE;
    }
}
