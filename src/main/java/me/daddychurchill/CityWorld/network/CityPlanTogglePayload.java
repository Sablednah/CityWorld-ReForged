package me.daddychurchill.CityWorld.network;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: the player turned the city plan overlay on or off from their map mod's own UI
 * (JourneyMap's options screen, or the toggle button on its fullscreen map).
 *
 * <p>The preference lives on the server because that is where the overlay is drawn from — see
 * {@code MapMarkers.setCityPlan}. {@code /citymap} sets the same flag from the other direction.
 */
public record CityPlanTogglePayload(boolean on) implements CustomPacketPayload {

    public static final Type<CityPlanTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "city_plan_toggle"));

    public static final StreamCodec<RegistryFriendlyByteBuf, CityPlanTogglePayload> CODEC = StreamCodec.of(
            (buf, p) -> buf.writeBoolean(p.on),
            buf -> new CityPlanTogglePayload(buf.readBoolean()));

    @Override
    public Type<CityPlanTogglePayload> type() {
        return TYPE;
    }
}
