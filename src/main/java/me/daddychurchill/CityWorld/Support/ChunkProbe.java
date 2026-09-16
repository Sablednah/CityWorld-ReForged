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

    private static int[] findStructure(MinecraftServer server, ServerLevel level, String id) {
        var registry = level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.STRUCTURE);
        var holder = registry.get(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.STRUCTURE, net.minecraft.resources.Identifier.parse(id)));
        if (holder.isEmpty())
            throw new IllegalArgumentException("unknown structure " + id);
        // -Dcityworld.probe.samples=N: also report the nearest start around N origins on a 1,600-block ring, so a
        // structure's placement against the city is measured over many starts rather than one.
        int samples = Integer.getInteger("cityworld.probe.samples", 0);
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * i / samples;
            net.minecraft.core.BlockPos origin = new net.minecraft.core.BlockPos((int) (1600 * Math.cos(angle)), 0,
                    (int) (1600 * Math.sin(angle)));
            var found = server.submit(() -> level.getChunkSource().getGenerator().findNearestMapStructure(level,
                    net.minecraft.core.HolderSet.direct(holder.get()), origin, 60, false)).join();
            if (found == null) {
                CityWorldMod.LOGGER.warn("PROBE sample {}: none near {}", i, origin.toShortString());
                continue;
            }
            int sx = found.getFirst().getX() >> 4, sz = found.getFirst().getZ() >> 4;
            ChunkAccess sc = server.submit(() -> level.getChunk(sx, sz, ChunkStatus.FULL, true)).join();
            var st = level.structureManager().getStartForStructure(net.minecraft.core.SectionPos.bottomOf(sc),
                    holder.get().value(), sc);
            int ladders = 0, air = 0, volume = 0, haloAir = 0, halo = 0;
            java.util.List<net.minecraft.world.level.levelgen.structure.BoundingBox> pieceBoxes = new java.util.ArrayList<>();
            if (st != null)
                for (var piece : st.getPieces())
                    pieceBoxes.add(piece.getBoundingBox());
            for (int x = 0; x < 16; x++)
                for (int z = 0; z < 16; z++)
                    for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                        var cell = new net.minecraft.core.BlockPos(sc.getPos().getMinBlockX() + x, y, sc.getPos().getMinBlockZ() + z);
                        var state = sc.getBlockState(cell);
                        if (state.is(net.minecraft.world.level.block.Blocks.LADDER))
                            ladders++;
                        // the cavern: air inside the start's box, below the street
                        if (st != null && st.getBoundingBox().isInside(cell) && y < 64) {
                            volume++;
                            if (state.isAir())
                                air++;
                            // the control: just outside every piece (within 3 blocks of one) — solid netherrack
                            // unless the cavern carve hollowed it
                            boolean inPiece = false, nearPiece = false;
                            for (var b : pieceBoxes) {
                                if (b.isInside(cell)) {
                                    inPiece = true;
                                    break;
                                }
                                if (cell.getX() >= b.minX() - 3 && cell.getX() <= b.maxX() + 3 && cell.getZ() >= b.minZ() - 3
                                        && cell.getZ() <= b.maxZ() + 3 && y >= b.minY() && y <= b.maxY())
                                    nearPiece = true;
                            }
                            if (!inPiece && nearPiece) {
                                halo++;
                                if (state.isAir())
                                    haloAir++;
                            }
                        }
                    }
            // The shaft may land in any chunk under the bastion, so count ladders and soul campfires across the
            // whole footprint (generating each chunk it covers).
            int footLadders = 0, footFires = 0, footChunks = 0;
            if (st != null) {
                var fb = st.getBoundingBox();
                for (int fx = fb.minX() >> 4; fx <= fb.maxX() >> 4; fx++)
                    for (int fz = fb.minZ() >> 4; fz <= fb.maxZ() >> 4; fz++) {
                        int gx = fx, gz = fz;
                        ChunkAccess fc = server.submit(() -> level.getChunk(gx, gz, ChunkStatus.FULL, true)).join();
                        footChunks++;
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                for (int y = 30; y < 90; y++) {
                                    var s2 = fc.getBlockState(new net.minecraft.core.BlockPos((gx << 4) + x, y, (gz << 4) + z));
                                    if (s2.is(net.minecraft.world.level.block.Blocks.LADDER))
                                        footLadders++;
                                    else if (s2.is(net.minecraft.world.level.block.Blocks.SOUL_CAMPFIRE)) {
                                        footFires++;
                                        // does the shaft actually reach daylight? the campfire sits on the collar,
                                        // so its Y should be at this column's real surface, not metres under it
                                        int wx = (gx << 4) + x, wz = (gz << 4) + z;
                                        int surface = level.getHeight(
                                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
                                        CityWorldMod.LOGGER.warn("PROBE shaft: soul campfire at {} {} {} — column surface {} (delta {})",
                                                wx, y, wz, surface, y - surface);
                                    }
                                }
                    }
            }
            String lot = "-";
            int street = -1;
            if (level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw) {
                var context = cw.getContext(level);
                street = context.streetLevel;
                lot = java.util.Optional.ofNullable(context.getPlatMap(sx, sz).getMapLot(sx, sz)).map(l -> l.getClass().getSimpleName()).orElse("-");
            }
            CityWorldMod.LOGGER.warn("PROBE sample {}: chunk {}, {} box y {}..{} street {} lot {} ladders-in-start-chunk {} cavern air {}/{} below street, halo air {}/{}, footprint {} chunks: ladders {} soul campfires {}",
                    i, sx, sz, st == null ? "?" : st.getBoundingBox().minY(), st == null ? "?" : st.getBoundingBox().maxY(),
                    street, lot, ladders, air, volume, haloAir, halo, footChunks, footLadders, footFires);
        }
        var located = server.submit(() -> level.getChunkSource().getGenerator().findNearestMapStructure(level,
                net.minecraft.core.HolderSet.direct(holder.get()), net.minecraft.core.BlockPos.ZERO, 100, false)).join();
        if (located == null)
            return null;
        net.minecraft.core.BlockPos at = located.getFirst();
        int cx = at.getX() >> 4, cz = at.getZ() >> 4;
        ChunkAccess chunk = server.submit(() -> level.getChunk(cx, cz, ChunkStatus.FULL, true)).join();
        var start = level.structureManager().getStartForStructure(
                net.minecraft.core.SectionPos.bottomOf(chunk), holder.get().value(), chunk);
        CityWorldMod.LOGGER.warn("PROBE: nearest {} at {} (chunk {}, {})", id, at.toShortString(), cx, cz);
        if (start != null && start.isValid()) {
            var box = start.getBoundingBox();
            CityWorldMod.LOGGER.warn("PROBE: {} start box y {}..{}, x {}..{}, z {}..{}, {} pieces", id, box.minY(), box.maxY(),
                    box.minX(), box.maxX(), box.minZ(), box.maxZ(), start.getPieces().size());
            java.util.Map<Integer, Integer> tops = new java.util.TreeMap<>();
            for (var piece : start.getPieces())
                tops.merge(piece.getBoundingBox().maxY(), 1, Integer::sum);
            CityWorldMod.LOGGER.warn("PROBE: {} piece top-Y histogram {}", id, tops);
        } else {
            CityWorldMod.LOGGER.warn("PROBE: no valid start stored in chunk {}, {} (start={})", cx, cz, start);
        }
        if (level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw) {
            var context = cw.getContext(level);
            int ground = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, at.getX(), at.getZ());
            CityWorldMod.LOGGER.warn("PROBE: city here — streetLevel {}, seaLevel {}, surface {} at the start's column, lot {}",
                    context.streetLevel, context.seaLevel, ground,
                    java.util.Optional.ofNullable(context.getPlatMap(cx, cz).getMapLot(cx, cz)).map(l -> l.getClass().getSimpleName()).orElse("-"));
        }
        return new int[] { cx, cz };
    }

    private static int[] findLot(ServerLevel level, String lotClass) {
        if (!(level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw))
            return null;
        var context = cw.getContext(level);
        int width = PlatMap.Width;
        for (int ring = 0; ring <= 20; ring++)
            for (int px = -ring; px <= ring; px++)
                for (int pz = -ring; pz <= ring; pz++) {
                    if (Math.max(Math.abs(px), Math.abs(pz)) != ring)
                        continue;
                    PlatMap platmap = context.getPlatMap(px * width, pz * width);
                    for (int x = 0; x < width; x++)
                        for (int z = 0; z < width; z++) {
                            var lot = platmap.getLot(x, z);
                            if (lot != null && lot.getClass().getSimpleName().equals(lotClass))
                                return new int[] { platmap.originX + x, platmap.originZ + z };
                        }
                }
        return null;
    }

    private void run(MinecraftServer server) {
        try {
            String spec = System.getProperty(PROPERTY).trim();
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
            int cx, cz;
            if (spec.startsWith("find:structure:")) {
                // -Dcityworld.probe=find:structure:minecraft:bastion_remnant — the nearest start of that structure
                // in the probed dimension, with where its pieces sit against the city's street level.
                int[] found = findStructure(server, level, spec.substring("find:structure:".length()));
                if (found == null)
                    throw new IllegalStateException("no " + spec + " found within 100 chunks of the origin");
                cx = found[0];
                cz = found[1];
            } else if (spec.startsWith("find:")) {
                // -Dcityworld.probe=find:ParkLot — the nearest chunk (by platmap ring) planned as that lot class.
                int[] found = findLot(level, spec.substring(5));
                if (found == null)
                    throw new IllegalStateException("no " + spec.substring(5) + " planned within 20 platmaps of the origin");
                cx = found[0];
                cz = found[1];
                CityWorldMod.LOGGER.warn("PROBE: nearest {} is chunk {}, {}", spec.substring(5), cx, cz);
            } else {
                String[] parts = spec.split(",");
                cx = Integer.parseInt(parts[0].trim());
                cz = Integer.parseInt(parts[1].trim());
            }
            // -Dcityworld.probe.radius=N: generate every chunk within N of the target, one at a time, logging each
            // before it starts — a chunk whose generation never returns is then named by the last line logged.
            int sweep = Integer.getInteger("cityworld.probe.radius", 0);
            for (int ring = 1; ring <= sweep; ring++)
                for (int dx = -ring; dx <= ring; dx++)
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                            continue;
                        int sx = cx + dx, sz = cz + dz;
                        CityWorldMod.LOGGER.warn("PROBE sweep: generating chunk {}, {}", sx, sz);
                        server.submit(() -> level.getChunk(sx, sz, ChunkStatus.FULL, true)).join();
                    }
            if (sweep > 0) {
                CityWorldMod.LOGGER.warn("PROBE sweep: all chunks within {} of ({}, {}) generated", sweep, cx, cz);
                // What each biome's ground actually IS: the top solid block of every column, keyed by the biome
                // there. A biome whose signature block never appears is generating with someone else's ground.
                java.util.Map<String, java.util.Map<String, Integer>> byBiome = new java.util.TreeMap<>();
                for (int dx = -sweep; dx <= sweep; dx += 2)
                    for (int dz = -sweep; dz <= sweep; dz += 2) {
                        ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                        for (int x = 0; x < 16; x += 2)
                            for (int z = 0; z < 16; z += 2) {
                                int wx = c.getPos().getMinBlockX() + x, wz = c.getPos().getMinBlockZ() + z;
                                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                                var state = level.getBlockState(new BlockPos(wx, top, wz));
                                String biome = level.getBiome(new BlockPos(wx, top, wz)).unwrapKey()
                                        .map(k -> k.identifier().getPath()).orElse("?");
                                byBiome.computeIfAbsent(biome, k -> new java.util.TreeMap<>())
                                        .merge(state.getBlock().getName().getString(), 1, Integer::sum);
                            }
                    }
                byBiome.forEach((biome, blocks) -> CityWorldMod.LOGGER.warn("PROBE ground: {} -> {}", biome, blocks));
            }
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
