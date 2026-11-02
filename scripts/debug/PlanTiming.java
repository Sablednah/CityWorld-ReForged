package me.daddychurchill.CityWorld.debug;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * TEMPORARY (never ships: copy into src/main/java/me/daddychurchill/CityWorld/debug/ for a run, delete after — a
 * shipped jar must have no *debug* class). Times the plan-side costs the ZARP audit named, on a cold world, and
 * logs them as {@code PLANTIMING <key> <value>} lines: a cold platmap grid, the subway cross, lotAt cold/warm, a
 * findLot on the calling thread, and the memo sizes. {@code -Dcityworld.plantiming=true}.
 */
@EventBusSubscriber(modid = CityWorldMod.MODID)
public final class PlanTiming {

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        if (!Boolean.getBoolean("cityworld.plantiming"))
            return;
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        Thread t = new Thread(() -> {
            try {
                run(level);
            } catch (Throwable e) {
                CityWorldMod.LOGGER.error("PLANTIMING failed", e);
            }
            CityWorldMod.LOGGER.warn("PLANTIMING complete");
        }, "cityworld-plantiming");
        t.setDaemon(true);
        t.start();
    }

    private static void run(ServerLevel level) throws Exception {
        CityWorldChunkGenerator cw = (CityWorldChunkGenerator) level.getChunkSource().getGenerator();
        CityWorldGenerator gen = cw.getContext(level);
        int W = PlatMap.Width;

        // 1. a cold 8x8 platmap grid far from spawn: ms per platmap
        List<Long> ms = new ArrayList<>();
        long t0 = System.nanoTime();
        for (int px = 40; px < 48; px++)
            for (int pz = 40; pz < 48; pz++) {
                long a = System.nanoTime();
                gen.getPlatMap(px * W, pz * W);
                ms.add((System.nanoTime() - a) / 1_000_000);
            }
        long grid = (System.nanoTime() - t0) / 1_000_000;
        ms.sort(null);
        log("grid.platmaps", ms.size());
        log("grid.totalMs", grid);
        log("grid.minMs", ms.get(0));
        log("grid.medianMs", ms.get(ms.size() / 2));
        log("grid.p90Ms", ms.get(ms.size() * 9 / 10));
        log("grid.maxMs", ms.get(ms.size() - 1));
        log("forecast.memo", cw.forecast() == null ? -1 : cw.forecast().size());

        // 2. the subway cross, cold: Subway.at on a chunk whose row/column platmaps are unplanned
        int cx = 80 * W + 5, cz = 80 * W + 5;
        long a = System.nanoTime();
        var piece = me.daddychurchill.CityWorld.Support.Subway.at(gen, cx, cz);
        log("subway.coldMs", (System.nanoTime() - a) / 1_000_000);
        log("subway.any", piece.any());
        a = System.nanoTime();
        me.daddychurchill.CityWorld.Support.Subway.at(gen, cx, cz);
        log("subway.warmMs", (System.nanoTime() - a) / 1_000_000);

        // 3. lotAt cold (new area) and warm (same chunk again), then 100 warm asks across a planned platmap
        BlockPos far = new BlockPos(120 * W * 16 + 8, 64, 120 * W * 16 + 8);
        a = System.nanoTime();
        CityWorldAPI.lotAt(level, far);
        log("lotAt.coldMs", (System.nanoTime() - a) / 1_000_000);
        a = System.nanoTime();
        CityWorldAPI.lotAt(level, far);
        log("lotAt.warmUs", (System.nanoTime() - a) / 1_000);
        a = System.nanoTime();
        for (int i = 0; i < 100; i++)
            CityWorldAPI.lotAt(level, far.offset((i % W) * 16, 0, (i / W) * 16));
        log("lotAt.warm100Us", (System.nanoTime() - a) / 1_000);
        a = System.nanoTime();
        for (int i = 0; i < 100; i++)
            CityWorldAPI.lotAtIfPlanned(level, far.offset((i % W) * 16, 0, (i / W) * 16));
        log("lotAtIfPlanned.100Us", (System.nanoTime() - a) / 1_000);

        // 4. a find on the calling thread, cold
        BlockPos from = new BlockPos(-150 * W * 16, 64, -150 * W * 16);
        a = System.nanoTime();
        var hit = CityWorldAPI.findLot(level, from, "hospital", 1500);
        log("findLot.hospital1500Ms", (System.nanoTime() - a) / 1_000_000);
        log("findLot.hit", hit.isPresent());

        // 4b. vanilla's own server-thread callers (/locate, explorer maps, dolphins) ask getBaseHeight, cold
        var random = level.getChunkSource().randomState();
        a = System.nanoTime();
        int h = cw.getBaseHeight(-200 * W * 16 + 8, -200 * W * 16 + 8, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
        log("getBaseHeight.coldMs", (System.nanoTime() - a) / 1_000_000);
        a = System.nanoTime();
        for (int i = 0; i < 100; i++)
            cw.getBaseHeight(-200 * W * 16 + 8 + i * 7, -200 * W * 16 + 8 + i * 3, net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
        log("getBaseHeight.warm100Us", (System.nanoTime() - a) / 1_000);
        log("getBaseHeight.value", h);

        // 4c. the subway cross a second time elsewhere, after the memos exist for the first
        a = System.nanoTime();
        me.daddychurchill.CityWorld.Support.Subway.at(gen, cx + W, cz);
        log("subway.nextPlatmapMs", (System.nanoTime() - a) / 1_000_000);

        // 5. memo sizes
        log("forecast.memo.after", cw.forecast() == null ? -1 : cw.forecast().size());
        Object res = gen.structureReservations;
        if (res != null) {
            try {
                var f = res.getClass().getDeclaredField("memo");
                f.setAccessible(true);
                Object memo = f.get(res);
                log("reservations.memo", memo instanceof java.util.Map<?, ?> m ? m.size() + " blocks" : memo.toString());
                try {
                    log("reservations.memoChunks", res.getClass().getMethod("memoSize").invoke(res));
                } catch (ReflectiveOperationException ignored) {
                }
            } catch (ReflectiveOperationException e) {
                log("reservations.memo", "n/a " + e);
            }
        }
        Runtime rt = Runtime.getRuntime();
        System.gc();
        log("heapUsedMB", (rt.totalMemory() - rt.freeMemory()) / (1024 * 1024));
    }

    private static void log(String key, Object value) {
        CityWorldMod.LOGGER.warn("PLANTIMING {} {}", key, value);
    }
}
