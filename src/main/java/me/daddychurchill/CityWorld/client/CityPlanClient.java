package me.daddychurchill.CityWorld.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.network.CityPlanTogglePayload;
import me.daddychurchill.CityWorld.network.LotInfoPayload;
import me.daddychurchill.CityWorld.network.LotInfoRequestPayload;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * The client's side of the map integration: what the server has told us about chunks, and the
 * player's plan-overlay preference on its way back to the server.
 *
 * <p>Kept out of the JourneyMap package on purpose — it holds no map mod's types, so a second map
 * mod's plugin can use exactly the same cache and toggle.
 */
public final class CityPlanClient {

    private CityPlanClient() {}

    /** Answers from the server, newest last. Bounded: a map drag can sweep thousands of chunks. */
    private static final int CACHE_LIMIT = 4096;

    private static final Map<Long, String> KNOWN = java.util.Collections.synchronizedMap(
            new LinkedHashMap<>(256, 0.75f, false) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<Long, String> eldest) {
                    return size() > CACHE_LIMIT;
                }
            });

    /** Chunks already asked about, so a mouse resting on one chunk asks once, not once per frame. */
    private static final Set<Long> ASKED = ConcurrentHashMap.newKeySet();

    /** Server's answer for a chunk, or null if it has not answered yet. Empty string = nothing there. */
    public static String infoFor(int chunkX, int chunkZ) {
        return KNOWN.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * Asks the server what is planned in a chunk, at most once per chunk per session. The plan for a
     * chunk never changes, so a cached answer never goes stale.
     */
    public static void request(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (KNOWN.containsKey(key) || !ASKED.add(key))
            return;
        if (Minecraft.getInstance().getConnection() == null)
            return;
        ClientPacketDistributor.sendToServer(new LotInfoRequestPayload(chunkX, chunkZ));
    }

    /** An answer arrived. */
    public static void accept(LotInfoPayload payload) {
        KNOWN.put(ChunkPos.asLong(payload.chunkX(), payload.chunkZ()), payload.summary());
    }

    /** Tells the server the player turned the plan overlay on or off in their map mod's UI. */
    public static void setCityPlan(boolean on) {
        if (Minecraft.getInstance().getConnection() != null)
            ClientPacketDistributor.sendToServer(new CityPlanTogglePayload(on));
    }

    /** Leaving a world drops what we learned; the next one is a different plan entirely. */
    public static void forget() {
        KNOWN.clear();
        ASKED.clear();
    }
}
