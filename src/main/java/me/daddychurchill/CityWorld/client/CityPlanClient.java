package me.daddychurchill.CityWorld.client;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.network.CityPlanTogglePayload;
import me.daddychurchill.CityWorld.network.LotInfoPayload;
import me.daddychurchill.CityWorld.network.LotInfoRequestPayload;

import net.minecraft.client.Minecraft;
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
        return KNOWN.get(key(chunkX, chunkZ));
    }

    /**
     * Asks the server what is planned in a chunk, at most once per chunk per session. The plan for a
     * chunk never changes, so a cached answer never goes stale.
     */
    public static void request(int chunkX, int chunkZ) {
        long key = key(chunkX, chunkZ);
        if (KNOWN.containsKey(key) || !ASKED.add(key))
            return;
        // Hop to the client thread: a map mod may poll its info slots from a timer of its own, and
        // sending a packet from there is not safe.
        send(() -> {
            if (Minecraft.getInstance().getConnection() != null)
                ClientPacketDistributor.sendToServer(new LotInfoRequestPayload(chunkX, chunkZ));
            else
                ASKED.remove(key); // not connected yet; let it be asked again later
        });
    }

    /** An answer arrived. */
    public static void accept(LotInfoPayload payload) {
        KNOWN.put(key(payload.chunkX(), payload.chunkZ()), payload.summary());
    }

    /** Tells the server this client's plan settings, as set in its map mod's UI. */
    public static void setCityPlan(boolean on, int keep) {
        send(() -> {
            if (Minecraft.getInstance().getConnection() != null)
                ClientPacketDistributor.sendToServer(new CityPlanTogglePayload(on, keep));
        });
    }

    /** Runs {@code work} on the client thread, swallowing anything it throws. */
    private static void send(Runnable work) {
        Minecraft.getInstance().execute(() -> {
            try {
                work.run();
            } catch (Throwable t) {
                CityWorldMod.LOGGER.debug("CityWorld map request failed", t);
            }
        });
    }

    /**
     * Two chunk coordinates in one long. Packed here rather than with {@code ChunkPos.asLong}, which
     * 26.1 removed when ChunkPos became a record — and this integration is meant to be one source on
     * every version branch.
     */
    private static long key(int chunkX, int chunkZ) {
        return (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** Leaving a world drops what we learned; the next one is a different plan entirely. */
    public static void forget() {
        KNOWN.clear();
        ASKED.clear();
    }
}
