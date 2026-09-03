package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The headless chunk probe: {@code -Dcityworld.probe=<chunkX>,<chunkZ>} forces that chunk (plus a
 * ring so decoration runs), dumps every non-air block column summary in it, then halts the server.
 * Built for the four-times-escaped empty School lobby (seed 2720459862006157221, chunk 5,-7):
 * combined with {@link #tracing()}-gated logging inside the furnishing passes, it answers "what
 * ACTUALLY happened on this floor" instead of feeding another hypothesis.
 */
public final class ChunkProbe {

    private static final String PROPERTY = "cityworld.probe";

    public static boolean enabled() {
        return System.getProperty(PROPERTY) != null;
    }

    /** Furnishing passes log their decisions when this is on. */
    public static boolean tracing() {
        return enabled();
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        MinecraftServer server = event.getServer();
        Thread thread = new Thread(() -> run(server), "cityworld-probe");
        thread.setDaemon(true);
        thread.start();
    }

    private void run(MinecraftServer server) {
        try {
            String[] parts = System.getProperty(PROPERTY).split(",");
            int cx = Integer.parseInt(parts[0].trim()), cz = Integer.parseInt(parts[1].trim());
            ServerLevel level = server.overworld();
            CityWorldMod.LOGGER.warn("PROBE: forcing chunks around ({}, {})", cx, cz);
            // the ring first so the target's decoration has proper neighbours
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int fx = cx + dx, fz = cz + dz;
                    server.submit(() -> level.getChunk(fx, fz, ChunkStatus.FULL, true)).join();
                }
            ChunkAccess chunk = server.submit(() -> level.getChunk(cx, cz, ChunkStatus.FULL, true)).join();
            CityWorldMod.LOGGER.warn("PROBE: chunk ready, dumping interior columns y 60..80");
            for (int x = 0; x < 16; x++) {
                StringBuilder row = new StringBuilder();
                for (int z = 0; z < 16; z++) {
                    // per column: first non-air ABOVE 65 and whether 66..72 holds anything besides air
                    int solidTop = -1;
                    int contents = 0;
                    for (int y = 66; y <= 78; y++) {
                        var state = chunk.getBlockState(new BlockPos(x, y, z));
                        if (!state.isAir()) {
                            contents++;
                            if (solidTop < 0)
                                solidTop = y;
                        }
                    }
                    row.append(contents == 0 ? '.' : Character.forDigit(Math.min(contents, 15), 16));
                }
                CityWorldMod.LOGGER.warn("PROBE row x={}: {}", x, row);
            }
            // name the blocks on the floor band y 67..69 that are NOT structure (sample list)
            for (int y = 66; y <= 76; y++) {
                java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        var state = chunk.getBlockState(new BlockPos(x, y, z));
                        if (!state.isAir())
                            tally.merge(state.getBlock().getName().getString(), 1, Integer::sum);
                    }
                CityWorldMod.LOGGER.warn("PROBE y={}: {}", y, tally);
            }
        } catch (Throwable t) {
            CityWorldMod.LOGGER.error("PROBE failed", t);
        } finally {
            server.halt(false);
        }
    }
}
