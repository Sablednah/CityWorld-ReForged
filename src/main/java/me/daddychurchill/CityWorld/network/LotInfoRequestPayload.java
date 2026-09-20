package me.daddychurchill.CityWorld.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Client -> server: "what is planned in this chunk?", asked as the mouse moves over the map.
 *
 * <p>The client cannot answer it. CityWorld's plan lives on the server, and for a chunk nobody has
 * visited it does not exist anywhere else at all — which is the whole point of showing it.
 */
public record LotInfoRequestPayload(int chunkX, int chunkZ) {

    public static void encode(LotInfoRequestPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.chunkX);
        buf.writeVarInt(payload.chunkZ);
    }

    public static LotInfoRequestPayload decode(FriendlyByteBuf buf) {
        return new LotInfoRequestPayload(buf.readVarInt(), buf.readVarInt());
    }
}
