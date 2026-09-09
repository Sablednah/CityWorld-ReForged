package me.daddychurchill.CityWorld.network;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Client -> server: "what is planned in this chunk?", asked as the mouse moves over the map.
 *
 * <p>The client cannot answer it. CityWorld's plan lives on the server, and for a chunk nobody has
 * visited it does not exist anywhere else at all — which is the whole point of showing it.
 */
public record LotInfoRequestPayload(int chunkX, int chunkZ) implements CustomPacketPayload {

    public static final Type<LotInfoRequestPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "lot_info_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, LotInfoRequestPayload> CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.chunkX);
                buf.writeVarInt(p.chunkZ);
            },
            buf -> new LotInfoRequestPayload(buf.readVarInt(), buf.readVarInt()));

    @Override
    public Type<LotInfoRequestPayload> type() {
        return TYPE;
    }
}
