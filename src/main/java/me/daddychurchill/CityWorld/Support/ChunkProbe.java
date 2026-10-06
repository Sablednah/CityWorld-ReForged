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
 * ring so decoration runs), dumps every non-air block column summary in it, then stops. It does
 * NOT shut the server down — whoever started the run ends it (see the finally block below).
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
            var st = level.structureManager().getStartForStructure(holder.get().value(), sc);
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
        var start = level.structureManager().getStartForStructure(holder.get().value(), chunk);
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

    /**
     * {@code -Dcityworld.probe=find:biome:biomesoplenty:withered_abyss} — the nearest column the biome source
     * answers with that biome, <b>without generating a single chunk</b>, plus a census of everything it did
     * answer.
     *
     * <p>Why this exists: a radius sweep generates terrain, which in a roofed dimension costs about half a
     * second a chunk, and the server watchdog counts the whole probe as one tick — a blind sweep for a rare
     * biome dies at 60 seconds long before it reaches one (measured 2026-09-16: killed at 144 chunks). Asking
     * the biome source directly is free, and it answers the question that has to come first: <i>is the biome
     * anywhere near?</i> "Feature missing" and "biome never generated" read identically in a block tally, and
     * this project has already mistaken the second for the first twice.
     */
    private static int[] findBiome(MinecraftServer server, ServerLevel level, String id) {
        // Generate one chunk FIRST. CityWorld's Nether/End biome sources classify by delegating to the twin
        // overworld's terrain through a context that is only bound during generation; asked before any chunk
        // exists, they fall back and answer the SAME biome for every column. That is not a hypothetical: this
        // returned "all 63,001 columns are minecraft:nether_wastes" on a world whose caves and surface plainly
        // held six biomes, and the resulting "biome not found" aborted the run (2026-09-16).
        server.submit(() -> level.getChunk(0, 0, ChunkStatus.FULL, true)).join();
        var source = level.getChunkSource().getGenerator().getBiomeSource();
        var sampler = level.getChunkSource().randomState().createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build());
        java.util.Map<String, Integer> census = new java.util.TreeMap<>();
        int limit = Integer.getInteger("cityworld.probe.scan", 3000);
        int[] found = null;
        for (int ring = 0; ring * 16 <= limit; ring++)
            for (int dx = -ring; dx <= ring; dx++)
                for (int dz = -ring; dz <= ring; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                        continue;
                    int wx = dx * 16, wz = dz * 16;
                    String here = source.createResolver(sampler).getNoiseBiome(net.minecraft.core.QuartPos.fromBlock(wx),
                            net.minecraft.core.QuartPos.fromBlock(64),
                            net.minecraft.core.QuartPos.fromBlock(wz)).getRegisteredName();
                    census.merge(here, 1, Integer::sum);
                    if (found == null && here.equals(id))
                        found = new int[] { wx >> 4, wz >> 4 };
                }
        CityWorldMod.LOGGER.warn("PROBE biome census within {} blocks ({} columns sampled): {}", limit,
                census.values().stream().mapToInt(Integer::intValue).sum(), census);
        return found;
    }

    /**
     * {@code -Dcityworld.probe=survey:end} (with {@code -Dcityworld.probe.dim=minecraft:the_end}): how high, how
     * flat and how contiguous vanilla's outer End islands are, sampled the way {@link HeightInfo} samples a chunk
     * (centre + four corners). Answers "where could a fixed street level put a city on vanilla's islands" with
     * numbers before any design leans on a guess. Nothing is generated — this only asks the noise.
     */
    private static void surveyEnd(ServerLevel level) {
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        int span = Integer.getInteger("cityworld.probe.span", 100), x0 = 70, z0 = -span / 2;
        if (generator instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw) {
            surveyEndPlan(cw.getContext(level), span, x0, z0);
            // Could an end city start here at all? Its two gates, asked the way EndCityStructure asks them:
            // the lowest of four columns at y >= 60, in highlands or midlands.
            java.util.Map<String, Integer> biomes = new java.util.TreeMap<>();
            int tall = 0, both = 0, side = Math.min(span, 40);
            for (int i = 0; i < side; i++)
                for (int j = 0; j < side; j++) {
                    int bx = (x0 + i) * 16 + 7, bz = (z0 + j) * 16 + 7, lowest = Integer.MAX_VALUE;
                    for (int[] d : new int[][] { { 0, 0 }, { 5, 0 }, { 0, 5 }, { 5, 5 } })
                        lowest = Math.min(lowest, generator.getFirstOccupiedHeight(bx + d[0], bz + d[1],
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random));
                    String biome = generator.getBiomeSource().createResolver(random.createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().enableCaches().build()))
                            .getNoiseBiome(bx >> 2, lowest >> 2, bz >> 2).unwrapKey().map(k -> k.identifier().getPath()).orElse("?");
                    biomes.merge(biome, 1, Integer::sum);
                    if (lowest >= 60) {
                        tall++;
                        if (biome.equals("end_highlands") || biome.equals("end_midlands"))
                            both++;
                    }
                }
            CityWorldMod.LOGGER.warn("SURVEY end cities: of {} chunks, {} stand at y >= 60 and {} of those are highlands/"
                    + "midlands; biomes {}", side * side, tall, both, biomes);
            return;
        }
        int[][] lo = new int[span][span], hi = new int[span][span], solid = new int[span][span];
        // EndTerrain must agree with vanilla exactly — the planner trusts it for chunks that do not exist yet.
        if (generator instanceof net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator noise) {
            var terrain = new me.daddychurchill.CityWorld.worldgen.EndTerrain(random,
                    noise.generatorSettings().value());
            int checked = 0, wrong = 0, worst = 0;
            long mine = 0, theirs = 0;
            for (int i = 0; i < 24; i++)
                for (int j = 0; j < 24; j++) {
                    long t0 = System.nanoTime();
                    short[] tops = terrain.chunkTops(x0 + i * 3, z0 + j * 3);
                    mine += System.nanoTime() - t0;
                    for (int k = 0; k < 5; k++) {
                        int bx = (x0 + i * 3) * 16 + ox(k), bz = (z0 + j * 3) * 16 + oz(k);
                        t0 = System.nanoTime();
                        int h = generator.getBaseHeight(bx, bz,
                                net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
                        theirs += System.nanoTime() - t0;
                        int top = h <= level.getMinY() ? 0 : h - 1, got = tops[(bx & 15) << 4 | (bz & 15)];
                        checked++;
                        if (top != got) {
                            wrong++;
                            worst = Math.max(worst, Math.abs(top - got));
                            if (wrong <= 8)
                                CityWorldMod.LOGGER.warn("SURVEY end: MISMATCH at {},{}: vanilla top {} EndTerrain {}",
                                        bx, bz, top, got);
                        }
                    }
                }
            CityWorldMod.LOGGER.warn("SURVEY end: EndTerrain vs getBaseHeight over {} columns: {} wrong (worst {} blocks); "
                    + "{} us per CHUNK of 256 columns vs {} us per single vanilla column", checked, wrong, worst,
                    mine / 1000 / (24 * 24), theirs / 1000 / Math.max(1, checked));
        }
        long started = System.nanoTime();
        for (int i = 0; i < span; i++)
            for (int j = 0; j < span; j++) {
                lo[i][j] = Integer.MAX_VALUE;
                hi[i][j] = Integer.MIN_VALUE;
                for (int k = 0; k < 5; k++) {
                    int h = generator.getBaseHeight((x0 + i) * 16 + ox(k), (z0 + j) * 16 + oz(k),
                            net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE_WG, level, random);
                    if (h <= level.getMinY())
                        continue;
                    solid[i][j]++;
                    lo[i][j] = Math.min(lo[i][j], h);
                    hi[i][j] = Math.max(hi[i][j], h);
                }
            }
        double perCall = (System.nanoTime() - started) / 1e3 / (span * span * 5.0);
        int chunks = span * span, any = 0, full = 0;
        java.util.TreeMap<Integer, Integer> tops = new java.util.TreeMap<>(), ranges = new java.util.TreeMap<>();
        for (int i = 0; i < span; i++)
            for (int j = 0; j < span; j++) {
                if (solid[i][j] > 0)
                    any++;
                if (solid[i][j] < 5)
                    continue;
                full++;
                tops.merge((lo[i][j] + hi[i][j]) / 2 / 2 * 2, 1, Integer::sum);
                ranges.merge(Math.min(hi[i][j] - lo[i][j], 20) / 2 * 2, 1, Integer::sum);
            }
        CityWorldMod.LOGGER.warn("SURVEY end: {} chunks from chunk {},{}; {} touch an island, {} are solid at all 5 "
                + "samples; {} us per getBaseHeight", chunks, x0, z0, any, full, String.format("%.1f", perCall));
        CityWorldMod.LOGGER.warn("SURVEY end: mid-height of fully solid chunks (2-block buckets): {}", tops);
        CityWorldMod.LOGGER.warn("SURVEY end: height range within a fully solid chunk (2-block buckets, 20+ capped): {}", ranges);
        for (int tolerance : new int[] { 2, 4, 6, 8, 12 }) {
            StringBuilder line = new StringBuilder();
            for (int street = 50; street <= 76; street += 2) {
                int fit = 0;
                for (int i = 0; i < span; i++)
                    for (int j = 0; j < span; j++)
                        if (solid[i][j] == 5 && lo[i][j] >= street - tolerance && hi[i][j] <= street + tolerance)
                            fit++;
                line.append(street).append('=').append(fit * 100 / Math.max(1, full)).append("% ");
            }
            CityWorldMod.LOGGER.warn("SURVEY end: street level -> share of solid chunks within +/-{}: {}", tolerance, line);
        }
    }

    /**
     * The CityWorld End's plan as a chunk map, one character a chunk, without generating anything: how the city
     * sits on vanilla's islands is a question about shapes, and block tallies cannot answer it. {@code ' '} void,
     * {@code '.'} island edge (some columns void), {@code ':'} wild island, {@code '#'} road, {@code 'B'} structure,
     * {@code 'o'} anything else planned (parks, roundabouts).
     */
    private static void surveyEndPlan(me.daddychurchill.CityWorld.CityWorldGenerator context, int span, int x0, int z0) {
        java.util.Map<String, Integer> lots = new java.util.TreeMap<>();
        java.util.List<String> bridges = new java.util.ArrayList<>(); // somewhere to point a probe at
        int island = 0, built = 0, overhang = 0;
        long started = System.nanoTime();
        for (int j = 0; j < span; j++) {
            StringBuilder row = new StringBuilder();
            for (int i = 0; i < span; i++) {
                int cx = x0 + i, cz = z0 + j, solid = 0;
                for (short top : context.endTerrain.chunkTops(cx, cz))
                    if (top > 0)
                        solid++;
                var lot = context.getPlatMap(cx, cz).getMapLot(cx, cz);
                var style = lot == null ? null : lot.style;
                boolean nature = style == null || style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.NATURE;
                if (solid > 0)
                    island++;
                if (!nature) {
                    built++;
                    overhang += 256 - solid;
                    if (solid < 64 && bridges.size() < 12)
                        bridges.add(cx + "," + cz + (solid == 0 ? " (void)" : " (" + solid + " columns of land)"));
                    lots.merge(lot.getClass().getSimpleName(), 1, Integer::sum);
                }
                row.append(!nature ? (style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.ROAD ? '#'
                        : style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.STRUCTURE ? 'B' : 'o')
                        : solid == 0 ? ' ' : solid < 256 ? '.' : ':');
            }
            CityWorldMod.LOGGER.warn("PLAN {}", row);
        }
        CityWorldMod.LOGGER.warn("SURVEY end plan: {}x{} chunks from {},{} in {} ms: {} touch an island, {} are built on "
                + "({}%); built chunks hang {} columns over the void in total", span, span, x0, z0,
                (System.nanoTime() - started) / 1_000_000, island, built, built * 100 / Math.max(1, island), overhang);
        CityWorldMod.LOGGER.warn("SURVEY end plan lots: {}", lots);
        CityWorldMod.LOGGER.warn("SURVEY end plan: built chunks with little or no land under them: {}", bridges);
    }

    /**
     * {@code -Dcityworld.probe=survey:sites} on a vanilla-terrain world: every city cell within
     * {@code -Dcityworld.probe.cells} (default 4) of the origin — the site it holds or why each try was refused —
     * and the PLAN of the accepted city nearest the origin as a chunk map. No chunk is generated.
     * {@code 'B'} building, {@code '#'} road, {@code 'o'} anything else planned, {@code ':'} city ground left to
     * nature, {@code '.'} the blend ring, {@code ' '} untouched vanilla.
     */
    private static void surveySites(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw)
                || cw.citySites() == null) {
            CityWorldMod.LOGGER.warn("SURVEY sites: this dimension is not a vanilla-terrain CityWorld");
            return;
        }
        var sites = cw.citySites();
        int cells = Integer.getInteger("cityworld.probe.cells", 4);
        int cell = sites.cell();
        int accepted = 0, total = 0;
        java.util.Map<Integer, Integer> bySize = new java.util.TreeMap<>(), byLevel = new java.util.TreeMap<>();
        me.daddychurchill.CityWorld.worldgen.CitySites.Site nearest = null;
        long started = System.nanoTime();
        for (int cz = -cells; cz <= cells; cz++) {
            StringBuilder row = new StringBuilder();
            for (int cx = -cells; cx <= cells; cx++) {
                var verdict = sites.verdictAt(cx * cell + cell / 2, cz * cell + cell / 2);
                var site = verdict.site();
                total++;
                if (site == null) {
                    row.append(" . ");
                    CityWorldMod.LOGGER.warn("SITE cell {},{}: none — {}", cx, cz, verdict.refusals());
                    continue;
                }
                accepted++;
                row.append(' ').append(site.districts()).append(' ');
                // how much river runs through the disc: biome samples on a 16-block grid (the handed-over
                // vanilla source answers; the sampler argument is ignored by it)
                int river = 0, samples = 0;
                var source = level.getChunkSource().getGenerator().getBiomeSource();
                var sampler = level.getChunkSource().randomState().createClimateSampler(net.minecraft.world.level.levelgen.densityfunction.SamplerContext.builder().build());
                for (int dx = -site.radius(); dx <= site.radius(); dx += 16)
                    for (int dz = -site.radius(); dz <= site.radius(); dz += 16) {
                        if ((long) dx * dx + (long) dz * dz > (long) site.radius() * site.radius())
                            continue;
                        samples++;
                        var key = source.createResolver(sampler).getNoiseBiome((site.centreX() + dx) >> 2, site.level() >> 2,
                                (site.centreZ() + dz) >> 2).unwrapKey().orElse(null);
                        if (key == net.minecraft.world.level.biome.Biomes.RIVER
                                || key == net.minecraft.world.level.biome.Biomes.FROZEN_RIVER)
                            river++;
                    }
                if (river > 0)
                    CityWorldMod.LOGGER.warn("SITE river: city at {}, {} (chunk {}, {}) has river biome under {} of {} samples",
                            site.centreX(), site.centreZ(), site.centreX() >> 4, site.centreZ() >> 4, river, samples);
                bySize.merge(site.districts(), 1, Integer::sum);
                byLevel.merge(site.level() / 8 * 8, 1, Integer::sum);
                if (nearest == null || Math.hypot(site.centreX(), site.centreZ()) < Math.hypot(nearest.centreX(), nearest.centreZ()))
                    nearest = site;
                CityWorldMod.LOGGER.warn("SITE cell {},{}: city at {}, {} — {} districts, ground y {}, water {}%, near-level {}%{}",
                        cx, cz, site.centreX(), site.centreZ(), site.districts(), site.level(),
                        Math.round(site.water() * 100), Math.round(site.near() * 100),
                        verdict.refusals().isEmpty() ? "" : " (after: " + verdict.refusals() + ")");
            }
            CityWorldMod.LOGGER.warn("CELLS {}", row);
        }
        CityWorldMod.LOGGER.warn("SURVEY sites: {} of {} cells ({} blocks each) hold a city, judged in {} ms; by districts across {}; by ground level (8s) {}",
                accepted, total, cell, (System.nanoTime() - started) / 1_000_000, bySize, byLevel);
        // -Dcityworld.probe.at=x,z: map the city nearest that point instead of the origin's
        String at = System.getProperty("cityworld.probe.at");
        if (at != null) {
            String[] p = at.split(",");
            nearest = sites.nearest(Integer.parseInt(p[0].trim()), Integer.parseInt(p[1].trim()), 2);
        }
        if (nearest == null)
            return;
        var context = cw.getContext(level);
        int span = (nearest.reach() + 32) / 16;
        int x0 = (nearest.centreX() >> 4) - span, z0 = (nearest.centreZ() >> 4) - span;
        java.util.Map<String, Integer> lots = new java.util.TreeMap<>(), contexts = new java.util.TreeMap<>();
        int city = 0, built = 0, rivers = 0;
        started = System.nanoTime();
        for (int j = 0; j <= span * 2; j++) {
            StringBuilder row = new StringBuilder();
            for (int i = 0; i <= span * 2; i++) {
                int cx = x0 + i, cz = z0 + j;
                boolean ground = sites.isCityChunk(cx, cz);
                if (!ground) {
                    row.append(sites.influencing(cx, cz) != null
                            && sites.edgeDistance(nearest, cx * 16 + 8, cz * 16 + 8) < 126 ? '.' : ' ');
                    continue;
                }
                city++;
                if (sites.isRiverChunk(cx, cz)) {
                    rivers++;
                    row.append('~');
                    continue;
                }
                var platmap = context.getPlatMap(cx, cz);
                var lot = platmap.getMapLot(cx, cz);
                var style = lot == null ? null : lot.style;
                boolean nature = style == null || style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.NATURE;
                if (!nature) {
                    built++;
                    lots.merge(lot.getClass().getSimpleName(), 1, Integer::sum);
                }
                if (Math.floorMod(cx, 10) == 5 && Math.floorMod(cz, 10) == 5)
                    contexts.merge(String.valueOf(platmap.context == null ? null : platmap.context.getClass().getSimpleName()), 1, Integer::sum);
                row.append(nature ? ':' : style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.ROAD ? '#'
                        : style == me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle.STRUCTURE ? 'B' : 'o');
            }
            CityWorldMod.LOGGER.warn("PLAN {}", row);
        }
        CityWorldMod.LOGGER.warn("SURVEY city at {}, {} (chunk {}, {}): {} districts, ground y {}; {} city chunks, {} built ({}%), planned in {} ms",
                nearest.centreX(), nearest.centreZ(), nearest.centreX() >> 4, nearest.centreZ() >> 4, nearest.districts(),
                nearest.level(), city, built, built * 100 / Math.max(1, city), (System.nanoTime() - started) / 1_000_000);
        CityWorldMod.LOGGER.warn("SURVEY city river chunks ('~'): {} of {}", rivers, city);
        CityWorldMod.LOGGER.warn("SURVEY city districts (by platmap centre): {}", contexts);
        CityWorldMod.LOGGER.warn("SURVEY city lots: {}", lots);
    }

    /**
     * {@code -Dcityworld.probe=survey:rivers}: the river network of CityWorld's own terrain
     * ({@code worldgen/RiverNetwork}), drawn over the ground heights as {@code run/rivers-<seed>.png} with its
     * numbers in the log. {@code -Dcityworld.probe.size} blocks square (default 8192) at
     * {@code -Dcityworld.probe.px} blocks a pixel (default 8), centred on {@code -Dcityworld.probe.at=x,z}
     * (default 0,0). No chunk is generated. Map: sea blue, beach sand, flat street-level ground (where cities
     * go) pale grey, hills green to white; rivers dark blue, width by flow; red dots falls, cyan lakes, black
     * springs, magenta mouths.
     */
    private static void surveyRivers(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator cw)
                || !(cw.getContext(level).shapeProvider instanceof me.daddychurchill.CityWorld.Plugins.ShapeProvider_Normal shape)) {
            CityWorldMod.LOGGER.warn("SURVEY rivers: this dimension is not CityWorld's normal terrain");
            return;
        }
        var ctx = cw.getContext(level);
        int sea = shape.getSeaLevel();
        var terrain = new me.daddychurchill.CityWorld.worldgen.RiverNetwork.Terrain() {
            public double drainage(int x, int z) { return shape.drainageAt(x, z); }
            public double ground(int x, int z) { return shape.naturalY(ctx, x, z); }
            public int seaLevel() { return sea; }
        };
        long seed = level.getSeed();
        var net = new me.daddychurchill.CityWorld.worldgen.RiverNetwork(seed, terrain);
        int size = Integer.getInteger("cityworld.probe.size", 8192), px = Integer.getInteger("cityworld.probe.px", 8);
        String[] at = System.getProperty("cityworld.probe.at", "0,0").split(",");
        int x0 = Integer.parseInt(at[0].trim()) - size / 2, z0 = Integer.parseInt(at[1].trim()) - size / 2;
        int margin = 3072, cell = me.daddychurchill.CityWorld.worldgen.RiverNetwork.CELL;
        int NODE = me.daddychurchill.CityWorld.worldgen.RiverNetwork.NODE;
        long started = System.nanoTime();
        java.util.List<me.daddychurchill.CityWorld.worldgen.RiverNetwork.Course> courses = new java.util.ArrayList<>();
        int cells = 0;
        for (int cx = Math.floorDiv(x0 - margin, cell); cx <= Math.floorDiv(x0 + size + margin, cell); cx++)
            for (int cz = Math.floorDiv(z0 - margin, cell); cz <= Math.floorDiv(z0 + size + margin, cell); cz++) {
                cells++;
                Long spring = net.spring(cx, cz);
                if (spring != null)
                    courses.add(net.trace(spring));
            }
        long traced = (System.nanoTime() - started) / 1_000_000;
        java.util.Map<Long, Integer> flow = new java.util.HashMap<>(), lvl = new java.util.HashMap<>(), gr = new java.util.HashMap<>();
        java.util.Map<Long, Long> next = new java.util.HashMap<>();
        java.util.Map<Long, Integer> run = new java.util.HashMap<>();
        java.util.Set<Long> falls = new java.util.HashSet<>(), lakes = new java.util.HashSet<>();
        java.util.Map<me.daddychurchill.CityWorld.worldgen.RiverNetwork.End, Integer> ends = new java.util.TreeMap<>();
        java.util.Map<Integer, Integer> cutHist = new java.util.TreeMap<>(), dropHist = new java.util.TreeMap<>();
        long totalLen = 0;
        for (var c : courses) {
            ends.merge(c.end(), 1, Integer::sum);
            totalLen += c.nodes().size();
            for (int k = 0; k < c.nodes().size(); k++) {
                long n = c.nodes().get(k);
                flow.merge(n, 1, Integer::sum);
                run.merge(n, k, Math::max);
                lvl.merge(n, c.level()[k], Math::max);
                gr.put(n, c.ground()[k]);
                if (k + 1 < c.nodes().size()) {
                    next.putIfAbsent(n, c.nodes().get(k + 1));
                    int drop = c.level()[k] - c.level()[k + 1];
                    if (drop >= me.daddychurchill.CityWorld.worldgen.RiverNetwork.FALL)
                        falls.add(n);
                }
                if (c.level()[k] > c.ground()[k] - 1 && c.ground()[k] >= sea)
                    lakes.add(n);
            }
        }
        for (var e : lvl.entrySet()) {
            int cut = gr.get(e.getKey()) - 1 - e.getValue();
            cutHist.merge(cut <= 0 ? 0 : cut <= 3 ? 3 : cut <= 8 ? 8 : cut <= 16 ? 16 : cut <= 32 ? 32 : 99, 1, Integer::sum);
        }
        for (var e : next.entrySet()) {
            int drop = lvl.get(e.getKey()) - lvl.getOrDefault(e.getValue(), lvl.get(e.getKey()));
            dropHist.merge(drop <= 0 ? 0 : drop <= 1 ? 1 : drop <= 3 ? 3 : drop <= 8 ? 8 : drop <= 16 ? 16 : 99, 1, Integer::sum);
        }
        java.util.Map<Integer, Integer> widthHist = new java.util.TreeMap<>(), plainWidth = new java.util.TreeMap<>();
        for (long n : flow.keySet()) {
            if (gr.get(n) < sea)
                continue;
            int wd = me.daddychurchill.CityWorld.worldgen.RiverNetwork.width(flow.get(n), run.get(n));
            int bucket = wd <= 3 ? 3 : wd <= 7 ? 7 : wd <= 12 ? 12 : wd <= 16 ? 16 : 20;
            widthHist.merge(bucket, 1, Integer::sum);
            if (gr.get(n) == sea + 1)
                plainWidth.merge(bucket, 1, Integer::sum);
        }
        CityWorldMod.LOGGER.warn("SURVEY rivers: width (nodes on land, by up-to blocks) {}; across city ground {}", widthHist, plainWidth);
        courses.stream().sorted((a, b) -> Long.compare(
                b.nodes().stream().filter(n -> gr.get(n) == sea + 1).count(), a.nodes().stream().filter(n -> gr.get(n) == sea + 1).count()))
                .limit(6).forEach(c -> {
                    long plain = c.nodes().stream().filter(n -> gr.get(n) == sea + 1).count();
                    long water = c.nodes().stream().filter(n -> gr.get(n) < sea).count();
                    double[] sp = net.place(c.spring()), en = net.place(c.nodes().get(c.nodes().size() - 1));
                    CityWorldMod.LOGGER.warn("SURVEY rivers course: spring {},{} -> {},{} {}: {} nodes, {} on city ground, {} in water",
                            (int) sp[0], (int) sp[1], (int) en[0], (int) en[1], c.end(), c.nodes().size(), plain, water);
                });
        java.util.Map<Integer, Integer> flowHist = new java.util.TreeMap<>();
        for (int f : flow.values())
            flowHist.merge(f <= 2 ? 2 : f <= 6 ? 6 : f <= 15 ? 15 : 99, 1, Integer::sum);
        CityWorldMod.LOGGER.warn("SURVEY rivers: seed {}, {} cells, {} springs, traced in {} ms; ends {}; mean course {} nodes",
                seed, cells, courses.size(), traced, ends, courses.isEmpty() ? 0 : totalLen / courses.size());
        CityWorldMod.LOGGER.warn("SURVEY rivers: {} river nodes; flow (stream<=2, creek<=6, river<=15, big) {}; falls {}; lake nodes {}",
                flow.size(), flowHist, falls.size(), lakes.size());
        CityWorldMod.LOGGER.warn("SURVEY rivers: gorge cut below ground (blocks, upper bound) {}; level drop per node {}", cutHist, dropHist);

        // the picture
        int w = size / px;
        var img = new java.awt.image.BufferedImage(w, w, java.awt.image.BufferedImage.TYPE_INT_RGB);
        int[] groundPx = new int[w * w];
        int flat = 0, seaPx = 0;
        for (int pz = 0; pz < w; pz++)
            for (int pxi = 0; pxi < w; pxi++) {
                int g = (int) Math.floor(shape.findPerciseY(ctx, x0 + pxi * px + px / 2, z0 + pz * px + px / 2));
                groundPx[pz * w + pxi] = g;
                int rgb;
                if (g < sea) {
                    int d = Math.min(40, sea - g);
                    rgb = rgb(40 - d / 2, 90 - d, 200 - d * 2);
                    seaPx++;
                } else if (g == sea)
                    rgb = rgb(220, 205, 150);
                else if (g == sea + 1) {
                    rgb = rgb(205, 205, 200);
                    flat++;
                } else {
                    int h = g - sea;
                    if (h < 30)
                        rgb = rgb(110 + h * 2, 160 - h, 80);
                    else if (h < 70)
                        rgb = rgb(150 - (h - 30), 130 - (h - 30), 90);
                    else if (h < 110)
                        rgb = rgb(120 + (h - 70) * 2, 110 + (h - 70) * 2, 100 + (h - 70) * 2);
                    else
                        rgb = rgb(245, 245, 250);
                }
                img.setRGB(pxi, pz, rgb);
            }
        // lakes: pixels near a lake node whose ground sits below its water
        int reach = 6 * NODE / px;
        for (long n : lakes) {
            int cxp = (me.daddychurchill.CityWorld.worldgen.RiverNetwork.ni(n) * NODE - x0) / px;
            int czp = (me.daddychurchill.CityWorld.worldgen.RiverNetwork.nj(n) * NODE - z0) / px;
            int water = lvl.get(n);
            for (int dz = -reach; dz <= reach; dz++)
                for (int dx = -reach; dx <= reach; dx++) {
                    int a = cxp + dx, b = czp + dz;
                    if (a >= 0 && b >= 0 && a < w && b < w && dx * dx + dz * dz <= reach * reach && groundPx[b * w + a] <= water
                            && groundPx[b * w + a] > sea)
                        img.setRGB(a, b, rgb(90, 200, 230));
                }
        }
        var g2 = img.createGraphics();
        g2.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setColor(new java.awt.Color(20, 50, 190));
        int onPlain = 0;
        for (var e : gr.entrySet())
            if (e.getValue() == sea + 1)
                onPlain++;
        CityWorldMod.LOGGER.warn("SURVEY rivers: {} river nodes ({} blocks of river) cross flat street-level ground",
                onPlain, onPlain * NODE);
        // each course as its smooth curve, as wide as the flow at that point; nothing drawn across open water
        for (var c : courses) {
            var pts = net.curve(c.nodes());
            int per = pts.size() / Math.max(1, c.nodes().size() - 1); // curve points per node step, about 8
            for (int k = 0; k + 1 < pts.size(); k++) {
                double[] p0 = pts.get(k), p1 = pts.get(k + 1);
                int idx = Math.min(c.nodes().size() - 1, Math.round((float) k / Math.max(1, per)));
                long n = c.nodes().get(idx);
                if (gr.get(n) < sea)
                    continue;
                float width = me.daddychurchill.CityWorld.worldgen.RiverNetwork.width(flow.get(n), run.get(n));
                g2.setStroke(new java.awt.BasicStroke(Math.max(1f, width / px), java.awt.BasicStroke.CAP_ROUND, java.awt.BasicStroke.JOIN_ROUND));
                g2.drawLine((int) ((p0[0] - x0) / px), (int) ((p0[1] - z0) / px), (int) ((p1[0] - x0) / px), (int) ((p1[1] - z0) / px));
            }
        }
        java.util.function.BiConsumer<Long, java.awt.Color> dot = (n, colour) -> {
            g2.setColor(colour);
            double[] p = net.place(n);
            int a = (int) ((p[0] - x0) / px), b = (int) ((p[1] - z0) / px);
            g2.fillOval(a - 2, b - 2, 5, 5);
        };
        for (long n : falls)
            dot.accept(n, java.awt.Color.RED);
        for (var c : courses) {
            dot.accept(c.spring(), java.awt.Color.BLACK);
            long last = c.nodes().get(c.nodes().size() - 1);
            dot.accept(last, c.end() == me.daddychurchill.CityWorld.worldgen.RiverNetwork.End.SEA ? java.awt.Color.MAGENTA
                    : java.awt.Color.CYAN);
        }
        g2.setColor(java.awt.Color.BLACK);
        g2.drawString("seed " + seed + "  " + size + " blocks, " + px + "/px, centre " + (x0 + size / 2) + "," + (z0 + size / 2), 6, 14);
        g2.dispose();
        try {
            var out = java.nio.file.Path.of("rivers-" + seed + "-" + System.getProperty("cityworld.probe.at", "0,0").replace(',', '_')
                    + "-" + size + ".png").toAbsolutePath();
            javax.imageio.ImageIO.write(img, "png", out.toFile());
            CityWorldMod.LOGGER.warn("SURVEY rivers: map {} ({}% flat street-level ground, {}% sea), drawn in {} ms", out,
                    flat * 100 / (w * w), seaPx * 100 / (w * w), (System.nanoTime() - started) / 1_000_000);
        } catch (java.io.IOException e) {
            CityWorldMod.LOGGER.warn("SURVEY rivers: could not write the map", e);
        }
    }

    private static int rgb(int r, int g, int b) {
        return (Math.max(0, Math.min(255, r)) << 16) | (Math.max(0, Math.min(255, g)) << 8) | Math.max(0, Math.min(255, b));
    }

    /** {@link HeightInfo}'s five sample columns: the centre, then the four corners. */
    private static int ox(int k) {
        return new int[] { 8, 0, 15, 0, 15 }[k];
    }

    private static int oz(int k) {
        return new int[] { 8, 0, 0, 15, 15 }[k];
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
            if (spec.startsWith("survey:end")) {
                surveyEnd(level);
                return;
            }
            if (spec.startsWith("survey:rivers")) {
                surveyRivers(level);
                return;
            }
            if (spec.startsWith("survey:sites")) {
                surveySites(level);
                return;
            }
            if (spec.startsWith("find:biome:")) {
                String id = spec.substring("find:biome:".length());
                int[] found = findBiome(server, level, id);
                if (found == null)
                    throw new IllegalStateException("no " + id + " in the scanned area — see the census above; "
                            + "a feature of a biome that never generates is not a missing feature");
                cx = found[0];
                cz = found[1];
                CityWorldMod.LOGGER.warn("PROBE: nearest {} is chunk {}, {}", id, cx, cz);
            } else if (spec.startsWith("find:structure:")) {
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
                CityWorldMod.LOGGER.warn("PROBE: mall sites {}", me.daddychurchill.CityWorld.Plats.Urban.Mall.sites());
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
            if (Boolean.getBoolean("cityworld.probe.forecast")
                    && level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator pcw) {
                for (var f : pcw.forecast().startsAt(cx, cz))
                    CityWorldMod.LOGGER.warn("FORECAST pre-sweep: chunk {},{} {} box {} x{}", cx, cz, f.getStructure(), f.getBoundingBox(), f.getPieces().size());
            }
            for (int ring = 1; ring <= sweep; ring++)
                for (int dx = -ring; dx <= ring; dx++)
                    for (int dz = -ring; dz <= ring; dz++) {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                            continue;
                        int sx = cx + dx, sz = cz + dz;
                        CityWorldMod.LOGGER.warn("PROBE sweep: generating chunk {}, {}", sx, sz);
                        server.submit(() -> level.getChunk(sx, sz, ChunkStatus.FULL, true)).join();
                    }
            // SPIKE -Dcityworld.probe.forecast=true: for every swept chunk, compute what vanilla WILL place there
            // (StructureForecast) and compare with what the chunk actually carries. Same boxes = the planner can
            // know a structure's real footprint before any chunk exists.
            if (Boolean.getBoolean("cityworld.probe.forecast")
                    && level.getChunkSource().getGenerator() instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator fcw) {
                int match = 0, mismatch = 0, missing = 0, extra = 0, chunksWithStarts = 0;
                long nanos = 0, forecastNanos = 0; int forecasts = 0;
                var sets = fcw.structureState().possibleStructureSets();
                for (int dx = -sweep; dx <= sweep; dx++)
                    for (int dz = -sweep; dz <= sweep; dz++) {
                        int sx = cx + dx, sz = cz + dz;
                        ChunkAccess sc = server.submit(() -> level.getChunk(sx, sz, ChunkStatus.FULL, true)).join();
                        long t0 = System.nanoTime();
                        var forecast = fcw.forecast().startsAt(sx, sz);
                        long t1 = System.nanoTime();
                        nanos += t1 - t0;
                        if (!forecast.isEmpty()) { forecastNanos += t1 - t0; forecasts += forecast.size(); }
                        // what the chunk really carries, per structure of every set
                        java.util.Map<String, net.minecraft.world.level.levelgen.structure.StructureStart> actual = new java.util.HashMap<>();
                        for (var set : sets)
                            for (var entry : set.value().structures()) {
                                var st = level.structureManager().getStartForStructure(entry.structure().value(), sc);
                                if (st != null && st.isValid())
                                    actual.put(String.valueOf(entry.structure().unwrapKey().map(k -> k.identifier()).orElse(null)), st);
                            }
                        if (!actual.isEmpty()) chunksWithStarts++;
                        java.util.Set<String> seenIds = new java.util.HashSet<>();
                        for (var f : forecast) {
                            String id = String.valueOf(fcw.structureState().possibleStructureSets().stream()
                                    .flatMap(set -> set.value().structures().stream())
                                    .filter(e -> e.structure().value() == f.getStructure())
                                    .map(e -> e.structure().unwrapKey().map(k -> k.identifier()).orElse(null)).findFirst().orElse(null));
                            seenIds.add(id);
                            var a = actual.get(id);
                            if (a == null) { extra++; CityWorldMod.LOGGER.warn("FORECAST extra: chunk {},{} {} box {} ({} pieces) but chunk has no such start", sx, sz, id, f.getBoundingBox(), f.getPieces().size()); continue; }
                            boolean same = a.getBoundingBox().equals(f.getBoundingBox()) && a.getPieces().size() == f.getPieces().size();
                            if (same) {
                                for (int i = 0; i < a.getPieces().size() && same; i++)
                                    same = a.getPieces().get(i).getBoundingBox().equals(f.getPieces().get(i).getBoundingBox());
                            }
                            if (same) match++; else {
                                mismatch++;
                                CityWorldMod.LOGGER.warn("FORECAST MISMATCH: chunk {},{} {} forecast {} x{} vs actual {} x{}", sx, sz, id, f.getBoundingBox(), f.getPieces().size(), a.getBoundingBox(), a.getPieces().size());
                                for (int i = 0; i < Math.min(a.getPieces().size(), f.getPieces().size()); i++) {
                                    var pa = a.getPieces().get(i); var pf = f.getPieces().get(i);
                                    if (!pa.getBoundingBox().equals(pf.getBoundingBox())) {
                                        String proj = pf instanceof net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece pp ? String.valueOf(pp.getElement().getProjection()) + " " + pp.getElement() : "n/a";
                                        CityWorldMod.LOGGER.warn("FORECAST   piece {} {} {}: forecast {} vs actual {}", i, pa.getClass().getSimpleName(), proj, pf.getBoundingBox(), pa.getBoundingBox());
                                    }
                                }
                                // is the forecast itself stable? compute it again right now
                                var again = fcw.forecast().startsAt(sx, sz);
                                for (var g : again) if (g.getStructure() == f.getStructure())
                                    CityWorldMod.LOGGER.warn("FORECAST   re-forecast now: {} x{} ({})", g.getBoundingBox(), g.getPieces().size(), g.getBoundingBox().equals(f.getBoundingBox()) ? "same as first forecast" : "DIFFERENT from first forecast");
                            }
                        }
                        for (var e : actual.entrySet())
                            if (!seenIds.contains(e.getKey())) { missing++; CityWorldMod.LOGGER.warn("FORECAST missing: chunk {},{} has {} box {} that the forecast did not predict", sx, sz, e.getKey(), e.getValue().getBoundingBox()); }
                    }
                int n = (2 * sweep + 1) * (2 * sweep + 1);
                CityWorldMod.LOGGER.warn("FORECAST: {} chunks, {} with real starts: match {}, mismatch {}, missing {}, extra {}; total {} ms ({} us/chunk), {} forecast starts costing {} ms together",
                        n, chunksWithStarts, match, mismatch, missing, extra, nanos / 1_000_000, nanos / 1000 / Math.max(1, n), forecasts, forecastNanos / 1_000_000);
                CityWorldMod.LOGGER.warn("FORECAST: memo holds {} origins", fcw.forecast().size());
            }
            // -Dcityworld.probe.layers=<y1>..<y2>: what is on each layer of the swept region, top down. "What hangs
            // under the End's islands" and "what did a lot draw below the street" are both questions about height.
            String layers = System.getProperty("cityworld.probe.layers");
            if (sweep > 0 && layers != null) {
                int lo = Integer.parseInt(layers.split("\\.\\.")[0].trim()), hi = Integer.parseInt(layers.split("\\.\\.")[1].trim());
                for (int y = hi; y >= lo; y--) {
                    java.util.Map<String, Integer> tally = new java.util.TreeMap<>();
                    for (int dx = -sweep; dx <= sweep; dx++)
                        for (int dz = -sweep; dz <= sweep; dz++) {
                            ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                            for (int x = 0; x < 16; x++)
                                for (int z = 0; z < 16; z++) {
                                    var state = c.getBlockState(new BlockPos(x, y, z));
                                    if (!state.isAir())
                                        tally.merge(state.getBlock().getName().getString(), 1, Integer::sum);
                                }
                        }
                    CityWorldMod.LOGGER.warn("PROBE layer y={}: {}", y, tally);
                }
            }
            // Plan against world, chunk by chunk: what each swept chunk was PLANNED as, and how much actually stands
            // above its street. "The map and F3 say city, the world says empty" is a disagreement between exactly
            // these two, and a tally of the whole sweep cannot show which chunks disagree.
            if (sweep > 0 && level.getChunkSource().getGenerator()
                    instanceof me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator planner) {
                var planContext = planner.getContext(level);
                for (int dz = -sweep; dz <= sweep; dz++) {
                    StringBuilder row = new StringBuilder();
                    for (int dx = -sweep; dx <= sweep; dx++) {
                        var lot = planContext.getPlatMap(cx + dx, cz + dz).getMapLot(cx + dx, cz + dz);
                        ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                        int above = 0;
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                for (int y = planContext.streetLevel + 2; y < planContext.streetLevel + 12; y++)
                                    if (!c.getBlockState(new BlockPos(x, y, z)).isAir())
                                        above++;
                        String name = lot == null ? "null" : lot.getClass().getSimpleName().replace("Lot", "");
                        row.append(String.format("%-14s", (name.length() > 9 ? name.substring(0, 9) : name) + ":" + above));
                    }
                    CityWorldMod.LOGGER.warn("PLANvWORLD z{} {}", cz + dz, row);
                }
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
                                // A roofed dimension (the Nether) hides its floor: WORLD_SURFACE is the
                                // bedrock ceiling, and scanning down from it finds the netherrack UNDER the
                                // roof (measured: crimson forest reading 23,702 netherrack and no nylium).
                                // Scan UP from the world floor for the first solid block with air above it.
                                int top = level.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz) - 1;
                                var state = level.getBlockState(new BlockPos(wx, top, wz));
                                if (state.is(net.minecraft.world.level.block.Blocks.BEDROCK)) {
                                    int roof = top;
                                    top = -1;
                                    for (int y = level.getMinY() + 1; y < roof - 1; y++) {
                                        var here = level.getBlockState(new BlockPos(wx, y, wz));
                                        if (here.isAir() || here.is(net.minecraft.world.level.block.Blocks.BEDROCK))
                                            continue;
                                        if (level.getBlockState(new BlockPos(wx, y + 1, wz)).isAir()
                                                && level.getBlockState(new BlockPos(wx, y + 2, wz)).isAir()) {
                                            top = y;
                                            state = here;
                                            break;
                                        }
                                    }
                                    if (top < 0)
                                        continue;
                                }
                                String biome = level.getBiome(new BlockPos(wx, top, wz)).unwrapKey()
                                        .map(k -> k.identifier().getPath()).orElse("?");
                                byBiome.computeIfAbsent(biome, k -> new java.util.TreeMap<>())
                                        .merge(state.getBlock().getName().getString(), 1, Integer::sum);
                            }
                    }
                byBiome.forEach((biome, blocks) -> CityWorldMod.LOGGER.warn("PROBE ground: {} -> {}", biome, blocks));
                // Blocks across the WHOLE swept area. A 3x3 region sits in ONE biome, so it can never witness
                // another biome's features — "zero" then means "wrong place", not "broken" (that mistake was
                // made three times in one session, 2026-09-16).
                java.util.Map<String, Integer> swept = new java.util.TreeMap<>();
                for (int dx = -sweep; dx <= sweep; dx += 2)
                    for (int dz = -sweep; dz <= sweep; dz += 2) {
                        ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                        for (int x = 0; x < 16; x++)
                            for (int z = 0; z < 16; z++)
                                for (int y = level.getMinY() + 1; y < level.getMaxY(); y++) {
                                    var st2 = c.getBlockState(new BlockPos(c.getPos().getMinBlockX() + x, y,
                                            c.getPos().getMinBlockZ() + z));
                                    if (!st2.isAir())
                                        swept.merge(st2.getBlock().getName().getString(), 1, Integer::sum);
                                }
                    }
                CityWorldMod.LOGGER.warn("PROBE swept blocks: {}", swept);

                // HEADROOM: the tallest unbroken run of air in each column, bucketed. This is the direct
                // measure of "can a feature that needs N blocks of open height actually stand here" —
                // BoP's large_rose_quartz wants 9-21. A block tally cannot answer that: fewer solid blocks
                // can mean wider caves OR taller ones, and today a -2.4% netherrack reading came with no
                // change in pillars at all. Measure the quantity the feature actually tests.
                java.util.Map<Integer, Integer> headroom = new java.util.TreeMap<>();
                int tall = 0, columns = 0;
                for (int dx = -sweep; dx <= sweep; dx += 2)
                    for (int dz = -sweep; dz <= sweep; dz += 2) {
                        ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                        for (int x = 0; x < 16; x += 2)
                            for (int z = 0; z < 16; z += 2) {
                                int wx = c.getPos().getMinBlockX() + x, wz = c.getPos().getMinBlockZ() + z;
                                int run = 0, best = 0;
                                // Stop at the surface: open sky is not headroom, it is outdoors.
                                int ceiling = level.getHeight(
                                        net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, wx, wz);
                                for (int y = level.getMinY() + 1; y < ceiling; y++) {
                                    if (c.getBlockState(new BlockPos(wx, y, wz)).isAir())
                                        best = Math.max(best, ++run);
                                    else
                                        run = 0;
                                }
                                columns++;
                                headroom.merge((best / 4) * 4, 1, Integer::sum);
                                if (best >= 9)
                                    tall++;
                            }
                    }
                CityWorldMod.LOGGER.warn("PROBE cave headroom (tallest air run per column, {} columns): {}",
                        columns, headroom);
                CityWorldMod.LOGGER.warn("PROBE cave headroom: {} columns ({}%) have 9+ blocks — the bar "
                        + "large_rose_quartz has to clear", tall, columns == 0 ? 0 : (tall * 100 / columns));

                // -Dcityworld.probe.where=<block_id,...>: WHERE a block is, not just how many. A feature can
                // generate in bulk and still be invisible if every one is enclosed (634 willow vines, none with
                // sky above). Block IDS, not display names: JAVA_TOOL_OPTIONS splits its value on whitespace,
                // so "Willow Vine" kills the JVM with "Unrecognized option: Vine".
                for (String want : System.getProperty("cityworld.probe.where", "").split(",")) {
                    if (want.isBlank())
                        continue;
                    String id = want.trim();
                    java.util.Map<Integer, Integer> bands = new java.util.TreeMap<>();
                    java.util.Map<String, Integer> above = new java.util.TreeMap<>(), below = new java.util.TreeMap<>();
                    int open = 0, covered = 0;
                    for (int dx = -sweep; dx <= sweep; dx += 2)
                        for (int dz = -sweep; dz <= sweep; dz += 2) {
                            ChunkAccess c = level.getChunk(cx + dx, cz + dz);
                            for (int x = 0; x < 16; x++)
                                for (int z = 0; z < 16; z++)
                                    for (int y = level.getMinY() + 1; y < level.getMaxY() - 1; y++) {
                                        int wx = c.getPos().getMinBlockX() + x, wz = c.getPos().getMinBlockZ() + z;
                                        // Either form matches: "crimson_nylium" or "minecraft:crimson_nylium".
                                        // This compared the PATH ALONE against whatever was passed, so a
                                        // namespaced id — the obvious reading of "block ids" above, and the
                                        // only form that can name a modded block unambiguously — matched
                                        // nothing and reported a confident zero. That cost three runs and one
                                        // wrong claim to the owner about a feature that was generating fine
                                        // (2026-09-16); a diagnostic that answers "none" when it means "I did
                                        // not understand the question" is worse than no diagnostic at all.
                                        var blockKey = net.minecraft.core.registries.BuiltInRegistries.BLOCK
                                                .getKey(c.getBlockState(new BlockPos(wx, y, wz)).getBlock());
                                        if (!blockKey.getPath().equalsIgnoreCase(id)
                                                && !blockKey.toString().equalsIgnoreCase(id))
                                            continue;
                                        bands.merge((y / 8) * 8, 1, Integer::sum);
                                        above.merge(c.getBlockState(new BlockPos(wx, y + 1, wz)).getBlock()
                                                .getName().getString(), 1, Integer::sum);
                                        below.merge(c.getBlockState(new BlockPos(wx, y - 1, wz)).getBlock()
                                                .getName().getString(), 1, Integer::sum);
                                        boolean clear = true;
                                        for (int up = y + 1; up < Math.min(y + 25, level.getMaxY()); up++)
                                            if (!c.getBlockState(new BlockPos(wx, up, wz)).isAir()) {
                                                clear = false;
                                                break;
                                            }
                                        if (clear)
                                            open++;
                                        else
                                            covered++;
                                    }
                        }
                    CityWorldMod.LOGGER.warn("PROBE where {}: y-bands {} | open sky above: {}, covered: {}", id, bands, open, covered);
                    CityWorldMod.LOGGER.warn("PROBE where {}: directly above {}", id, above);
                    CityWorldMod.LOGGER.warn("PROBE where {}: directly below {}", id, below);
                }
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
            // This used to call server.halt(false). It does not any more, and must not again:
            // CurseForge rejected 5.7.0 and 5.8.0 with "Please remove any function that shuts the
            // Minecraft server down" (2026-09-16). The call only ever ran behind -Dcityworld.probe,
            // but a reviewer greps the shipped bytecode, not the flag that guards it — and they are
            // right to: a worldgen mod has no business being able to stop someone's server.
            // The probe now just stops; whoever started it ends the run (scripts kill the process
            // group — never a pkill pattern, see CLAUDE.md).
            CityWorldMod.LOGGER.warn("PROBE complete — the server is still running; stop it yourself.");
        }
    }
}
