package me.daddychurchill.CityWorld.network;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server -> client: what is planned in one chunk, already worded for display
 * ("Highrise · OfficeTowerLot · bakery · marble lobby").
 *
 * <p>A formatted string rather than the structured {@code LotInfo}: the client only ever shows it,
 * and the server already owns the vocabulary — a new lot kind or shop type then needs no client
 * change at all. An empty summary means "nothing planned here" (not a CityWorld level).
 */
public record LotInfoPayload(int chunkX, int chunkZ, String summary) implements CustomPacketPayload {

    public static final Type<LotInfoPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "lot_info"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LotInfoPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.chunkX);
                buf.writeVarInt(p.chunkZ);
                buf.writeUtf(p.summary, 256);
            },
            buf -> new LotInfoPayload(buf.readVarInt(), buf.readVarInt(), buf.readUtf(256)));

    @Override
    public Type<LotInfoPayload> type() {
        return TYPE;
    }
}
