// NOT COMPILED FROM HERE. A temporary debug listener: copy to src/main/java/me/daddychurchill/CityWorld/debug/WideLoad.java,
// run scripts/wideload.sh, then DELETE it — nothing under debug/ may reach a commit or a shipped jar.
// This is the NeoForge form (1.21.1 … 26.x). On 1.20.1 Forge the imports are net.minecraftforge.event.server.ServerStartedEvent,
// net.minecraftforge.eventbus.api.SubscribeEvent and @Mod.EventBusSubscriber(modid = CityWorldMod.MODID).
package me.daddychurchill.CityWorld.debug;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** TEMPORARY: load a wide block of chunks at once, list the store chunks and their trades, tick, save. */
@EventBusSubscriber(modid = CityWorldMod.MODID)
public final class WideLoad {
    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        String at = System.getProperty("cityworld.wideload");
        if (at == null)
            return;
        String[] p = at.split(",");
        int ox = Integer.parseInt(p[0].trim()), oz = Integer.parseInt(p[1].trim()), n = Integer.parseInt(p[2].trim());
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Thread t = new Thread(() -> {
            long start = System.currentTimeMillis();
            var gen = ((me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator) level.getChunkSource().getGenerator()).getContext(level);
            StringBuilder shops = new StringBuilder();
            for (int x = ox; x < ox + n; x++)
                for (int z = oz; z < oz + n; z++) {
                    try {
                        var lot = gen.getPlatMap(x, z).getMapLot(x, z);
                        if (lot != null && lot.getShopType() != null)
                            shops.append(x).append(',').append(z).append(',').append(lot.getClass().getSimpleName())
                                    .append(',').append(lot.getShopType().trade().name()).append(';');
                    } catch (RuntimeException e) {
                    }
                }
            CityWorldMod.LOGGER.warn("WIDELOAD SHOPS {}", shops);
            server.submit(() -> {
                for (int x = 0; x < n; x++)
                    for (int z = 0; z < n; z++)
                        level.getChunkSource().updateChunkForced(new ChunkPos(ox + x, oz + z), true);
            }).join();
            int total = n * n, done = 0, last = -1;
            long lastChange = start;
            while (System.currentTimeMillis() - lastChange < 120_000) {
                try {
                    done = server.submit(() -> {
                        int full = 0;
                        for (int x = 0; x < n; x++)
                            for (int z = 0; z < n; z++)
                                if (level.getChunkSource().getChunkNow(ox + x, oz + z) != null)
                                    full++;
                        return full;
                    }).get(10, java.util.concurrent.TimeUnit.SECONDS);
                } catch (Exception e) {
                }
                if (done != last) { last = done; lastChange = System.currentTimeMillis(); }
                if (done == total) break;
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
            CityWorldMod.LOGGER.warn("WIDELOAD: {} of {} chunks full after {} ms -> {}", done, total,
                    System.currentTimeMillis() - start, done == total ? "OK" : "STALLED");
            try { Thread.sleep(15_000); } catch (InterruptedException e) { return; }
            server.submit(() -> level.save(null, true, false)).join();
            CityWorldMod.LOGGER.warn("WIDELOAD complete");
        }, "wideload");
        t.setDaemon(true);
        t.start();
    }
}
