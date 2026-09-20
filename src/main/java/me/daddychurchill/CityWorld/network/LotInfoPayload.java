package me.daddychurchill.CityWorld.network;

import net.minecraft.network.FriendlyByteBuf;

/**
 * Server -> client: what is planned in one chunk, already worded for display
 * ("Highrise · OfficeTowerLot · bakery · marble lobby").
 *
 * <p>A formatted string rather than the structured {@code LotInfo}: the client only ever shows it,
 * and the server already owns the vocabulary — a new lot kind or shop type then needs no client
 * change at all. An empty summary means "nothing planned here" (not a CityWorld level).
 */
public record LotInfoPayload(int chunkX, int chunkZ, String summary) {

    public static void encode(LotInfoPayload payload, FriendlyByteBuf buf) {
        buf.writeVarInt(payload.chunkX);
        buf.writeVarInt(payload.chunkZ);
        buf.writeUtf(payload.summary, 256);
    }

    public static LotInfoPayload decode(FriendlyByteBuf buf) {
        return new LotInfoPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(256));
    }
}
