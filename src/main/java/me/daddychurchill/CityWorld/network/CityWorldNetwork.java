package me.daddychurchill.CityWorld.network;

import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.api.LotInfo;
import me.daddychurchill.CityWorld.api.MapMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * CityWorld's networking: the two questions a map mod's client-side UI needs the server to answer.
 *
 * <p>Both exist because the plan is server-side knowledge. A client can draw a map of where it has
 * been; only the server can say what CityWorld intends to build in a chunk nobody has walked into.
 *
 * <p>The channel is {@code optional()}: a vanilla client, or one without CityWorld, connects
 * perfectly well and simply never asks.
 */
public final class CityWorldNetwork {

    private CityWorldNetwork() {}

    /**
     * Answers hover questions off the server thread. Planning an unvisited platmap is tens to
     * hundreds of milliseconds (measured), and a player sweeping the mouse across the map would
     * otherwise drag the tick along with it. One thread, because planned platmaps are cached and
     * shared — a second question about the same area is nearly free.
     */
    private static final ExecutorService LOOKUP = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cityworld-lot-info");
        thread.setDaemon(true);
        return thread;
    });

    /** Registered on the mod event bus from {@link CityWorldMod}. */
    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(CityPlanTogglePayload.TYPE, CityPlanTogglePayload.CODEC,
                CityWorldNetwork::handleToggle);
        registrar.playToServer(LotInfoRequestPayload.TYPE, LotInfoRequestPayload.CODEC,
                CityWorldNetwork::handleLotInfoRequest);
        registrar.playToClient(LotInfoPayload.TYPE, LotInfoPayload.CODEC, CityWorldNetwork::handleLotInfo);
    }

    private static void handleToggle(CityPlanTogglePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                MapMarkers.setCityPlanBudget(player.getUUID(), payload.keep());
                MapMarkers.setCityPlan(player.getUUID(), payload.on());
            }
        });
    }

    /**
     * Looks the chunk's plan up and sends it back. The lookup itself runs off the server thread; only
     * grabbing the player's level happens on it.
     */
    private static void handleLotInfoRequest(LotInfoRequestPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player))
                return;
            ServerLevel level = player.level();
            LOOKUP.execute(() -> {
                String summary;
                try {
                    summary = describe(level, payload.chunkX(), payload.chunkZ());
                } catch (Throwable t) {
                    CityWorldMod.LOGGER.debug("Lot info lookup failed at {}, {}", payload.chunkX(),
                            payload.chunkZ(), t);
                    summary = "";
                }
                final String answer = summary;
                level.getServer().execute(() -> PacketDistributor.sendToPlayer(player,
                        new LotInfoPayload(payload.chunkX(), payload.chunkZ(), answer)));
            });
        });
    }

    /**
     * Words one chunk's plan for a map tooltip. Reads through the public {@link CityWorldAPI}, the
     * same door other mods use, so this and {@code /cityinfo} can never drift apart.
     */
    public static String describe(ServerLevel level, int chunkX, int chunkZ) {
        Optional<LotInfo> maybe = CityWorldAPI.lotAt(level, new BlockPos((chunkX << 4) + 8, 64, (chunkZ << 4) + 8));
        if (maybe.isEmpty())
            return "";
        LotInfo info = maybe.get();
        String district = title(info.contextFamily().name());
        StringBuilder text = new StringBuilder(district);
        // "Farm · farm · Potato field" says farm twice; a lot named after its own district adds
        // nothing, so it steps aside for what the lot actually holds.
        String kind = readable(info.lotClass());
        if (!kind.equalsIgnoreCase(district))
            text.append(" · ").append(kind);
        if (info.schematicName() != null)
            text.append(" · ").append(info.schematicName());
        if (info.shop() != null)
            text.append(" · ").append(info.shop().describe());
        if (info.interior() != null)
            text.append(" · ").append(info.interior());
        return text.toString();
    }

    /** {@code HIGHRISE} -> {@code Highrise}. */
    private static String title(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    /**
     * {@code OfficeTowerLot} -> {@code office tower}: the class name is the lot's real identity, and
     * using it means a lot kind added later needs nothing here.
     *
     * <p>Three of them are named for what they are in the code rather than what a player sees, so
     * they are translated: {@code ClipboardLot} is a placed schematic, {@code NatureLot} is
     * unbuilt ground, and {@code ConcreteLot} is a paved lot.
     */
    private static String readable(String lotClass) {
        String name = lotClass.endsWith("Lot") ? lotClass.substring(0, lotClass.length() - 3) : lotClass;
        String spaced = name.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ").toLowerCase(Locale.ROOT);
        return switch (spaced) {
            case "clipboard" -> "schematic";
            case "nature" -> "open ground";
            case "concrete" -> "paved lot";
            default -> spaced;
        };
    }

    /**
     * Client-side handler. {@code CityPlanClient} imports client classes, so it is named only inside
     * the enqueued lambda — a dedicated server registering this payload never loads it.
     */
    private static void handleLotInfo(LotInfoPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> me.daddychurchill.CityWorld.client.CityPlanClient.accept(payload));
    }
}
