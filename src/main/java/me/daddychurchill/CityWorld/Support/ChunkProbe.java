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

    /**
     * The block watch: {@code -Dcityworld.watch=<x>,<y>,<z>} (world coordinates) logs every write
     * to that cell with the state written and the CityWorld frames that wrote it. Built for the
     * line-of-blocks building (seed -3729467216436926281, block 24 76 -169): a row of wall material
     * across the stair head on every floor, same x/z — "who draws this?" answered by the stack
     * rather than by reading every stairwell routine.
     */
    private static final BlockPos WATCH = parseWatch();

    private static BlockPos parseWatch() {
        String value = System.getProperty("cityworld.watch");
        if (value == null)
            return null;
        String[] p = value.split(",");
        return new BlockPos(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), Integer.parseInt(p[2].trim()));
    }

    public static void watch(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
        if (WATCH == null || !WATCH.equals(pos))
            return;
        StringBuilder frames = new StringBuilder();
        int shown = 0;
        for (StackTraceElement frame : Thread.currentThread().getStackTrace()) {
            if (!frame.getClassName().startsWith("me.daddychurchill") || frame.getClassName().endsWith("ChunkProbe"))
                continue;
            frames.append("\n      at ").append(frame.getClassName().substring(frame.getClassName().lastIndexOf('.') + 1))
                    .append('.').append(frame.getMethodName()).append(':').append(frame.getLineNumber());
            if (++shown >= 14)
                break;
        }
        CityWorldMod.LOGGER.warn("WATCH {} <- {}{}", pos.toShortString(), state, frames);
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
            // -Dcityworld.probe.dim=minecraft:the_nether probes another dimension (default: the overworld).
            String dim = System.getProperty("cityworld.probe.dim");
            ServerLevel level = dim == null ? server.overworld()
                    : server.getLevel(net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION,
                            net.minecraft.resources.Identifier.parse(dim)));
            if (level == null)
                throw new IllegalArgumentException("cityworld.probe.dim " + dim + " is not a loaded dimension");
            CityWorldMod.LOGGER.warn("PROBE: dimension {} generator {}", level.dimension().identifier(),
                    level.getChunkSource().getGenerator().getClass().getSimpleName());
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
            // Whole-height picture of the 3x3 region: which blocks, and which biomes at the surface.
            java.util.Map<String, Integer> blocks = new java.util.TreeMap<>();
            java.util.Map<String, Integer> biomes = new java.util.TreeMap<>();
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                    for (int x = 0; x < 16; x++)
                        for (int z = 0; z < 16; z++) {
                            int wx = c.getPos().getMinBlockX() + x, wz = c.getPos().getMinBlockZ() + z;
                            for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                                var state = c.getBlockState(new BlockPos(wx, y, wz));
                                if (!state.isAir())
                                    blocks.merge(state.getBlock().getName().getString(), 1, Integer::sum);
                            }
                            int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
                            biomes.merge(level.getBiome(new BlockPos(wx, top, wz)).unwrapKey()
                                    .map(k -> k.identifier().toString()).orElse("?"), 1, Integer::sum);
                        }
                }
            CityWorldMod.LOGGER.warn("PROBE region blocks: {}", blocks);
            CityWorldMod.LOGGER.warn("PROBE region surface biomes: {}", biomes);
            // Climate at scale, no chunk generation: percentiles of the two axes a CityWorld biome source
            // classifies on, and the biome split its classify() would give over ~12 km.
            if (level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw
                    && cw.getBiomeSource() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldBiomes source) {
                var context = cw.getContext(level);
                int n = 0, side = 384;
                double[] temps = new double[side * side], humids = new double[side * side];
                java.util.Map<String, Integer> split = new java.util.TreeMap<>();
                for (int i = 0; i < side; i++)
                    for (int j = 0; j < side; j++) {
                        int wx = (i - side / 2) * 32, wz = (j - side / 2) * 32;
                        double t = context.getTemperature(wx, wz), h = context.getHumidity(wx, wz);
                        temps[n] = t;
                        humids[n++] = h;
                        split.merge(source.classify(context, 70, t, h, false).unwrapKey()
                                .map(k -> k.identifier().toString()).orElse("?"), 1, Integer::sum);
                    }
                java.util.Arrays.sort(temps);
                java.util.Arrays.sort(humids);
                java.util.function.Function<double[], String> pct = a -> String.format("p5=%.3f p25=%.3f p50=%.3f p75=%.3f p95=%.3f",
                        a[a.length * 5 / 100], a[a.length / 4], a[a.length / 2], a[a.length * 3 / 4], a[a.length * 95 / 100]);
                CityWorldMod.LOGGER.warn("PROBE climate temperature: {}", pct.apply(temps));
                CityWorldMod.LOGGER.warn("PROBE climate humidity: {}", pct.apply(humids));
                CityWorldMod.LOGGER.warn("PROBE climate biome split ({} samples): {}", n, split);
            }
        } catch (Throwable t) {
            CityWorldMod.LOGGER.error("PROBE failed", t);
        } finally {
            server.halt(false);
        }
    }
}
