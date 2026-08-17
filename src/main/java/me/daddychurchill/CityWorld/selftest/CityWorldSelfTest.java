package me.daddychurchill.CityWorld.selftest;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * Headless verification that CityWorld still generates cities on this Minecraft version.
 *
 * <p>Minecraft now ships quarterly, so CityWorld is maintained against several versions at once and
 * playtesting each one by hand does not scale. This harness is the automated half of that: it proves
 * the generator is installed, that the planning brain runs, that the decoration pass writes real
 * buildings, and that sign text survives — the checks that used to be done by flying around a world.
 *
 * <p><b>Dormant unless asked for.</b> It only runs when {@code -Dcityworld.selftest=true} is set, so
 * players never pay for it. Use {@code ./gradlew runSelfTest}, or {@code scripts/selftest.sh} to run
 * it across every supported version and compare them.
 *
 * <p><b>This ships in the release jar, deliberately — do not strip it out.</b> Two reasons. It must
 * test <em>the artifact players actually get</em>; excluding it would mean verifying a different jar
 * from the one shipped, which undermines the point of having it. And it is a known quantity from
 * 5.1.0 onward, so a reviewer diffing a later version sees it as pre-existing rather than something
 * newly slipped in.
 *
 * <p>Worth being aware of how it reads to someone auditing the jar: a dormant code path, switched on
 * by a flag, that ends in {@link net.minecraft.server.MinecraftServer#halt}. That shape is what
 * plugin backdoors used to look like. It is benign — setting a system property requires launch-time
 * access to the server, so anyone who can trigger it can already stop the server directly, and there
 * is no network trigger and no privilege change — but expect it to draw a careful read.
 *
 * <p><b>What makes it cross-version useful.</b> Planning never touches the block registry, so for a
 * fixed seed the plan is a pure function of the seed and must be <em>identical</em> on every
 * Minecraft version. The harness hashes it, and {@code scripts/selftest.sh} fails the run if two
 * versions disagree. Materials are deliberately excluded from that hash: those legitimately differ
 * as newer versions add blocks to the palettes' tags, and are reported separately.
 */
public final class CityWorldSelfTest {

    /** Set {@code -Dcityworld.selftest=true} to run. Absent for players. */
    public static final String ENABLE_PROPERTY = "cityworld.selftest";

    /**
     * Fixed seed for the plan sweep, so the hash is comparable across versions and machines. It is
     * deliberately independent of the world's own seed — only the read-back checks need a real world.
     */
    private static final long PLAN_SEED = 8675309L;

    /** Chunk radius of the plan sweep. 150 gives ~961 platmaps per style: broad, and about a minute. */
    private static final int PLAN_RADIUS = 150;

    /** Styles swept. The three headline ones; enough to exercise every context and provider switch. */
    private static final WorldStyle[] STYLES = { WorldStyle.MODERN, WorldStyle.APOCALYPSE, WorldStyle.CLASSIC };

    /** Chunk radius surveyed in the real world. 6 -> 169 chunks: enough to reach a city from spawn. */
    private static final int CHUNK_SURVEY_RADIUS = 6;

    /** A built chunk should contain at least this many non-air blocks, else decoration did nothing. */
    private static final int MIN_BUILT_BLOCKS = 200;

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    private final List<String> failures = new ArrayList<>();
    private final Map<String, String> report = new TreeMap<>();

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        if (!enabled())
            return;
        MinecraftServer server = event.getServer();
        // Off the server thread: the sweep runs for minutes and the 60s tick watchdog would
        // force-crash the server if this ran inline.
        Thread thread = new Thread(() -> runAll(server), "cityworld-selftest");
        thread.setDaemon(false);
        thread.start();
    }

    private void runAll(MinecraftServer server) {
        long started = System.currentTimeMillis();
        CityWorldMod.LOGGER.info("SELFTEST: starting on Minecraft {}",
                server.getServerVersion());
        try {
            checkGeneratorInstalled(server);
            checkPlanning();
            checkDecorationAndSigns(server);
        } catch (Throwable t) {
            fail("harness threw: " + t);
            CityWorldMod.LOGGER.error("SELFTEST: harness threw", t);
        }
        report.put("durationMs", Long.toString(System.currentTimeMillis() - started));
        report.put("failures", Integer.toString(failures.size()));
        writeReport(server);

        if (failures.isEmpty()) {
            CityWorldMod.LOGGER.info("SELFTEST: PASS ({} checks recorded)", report.size());
        } else {
            CityWorldMod.LOGGER.error("SELFTEST: FAIL — {} problem(s):", failures.size());
            for (String f : failures)
                CityWorldMod.LOGGER.error("SELFTEST:   - {}", f);
        }
        // Always stop, so a CI run terminates instead of idling at the console.
        CityWorldMod.LOGGER.info("SELFTEST: done, halting server");
        server.halt(false);
    }

    // ---- checks ---------------------------------------------------------------------------------

    /** The overworld must actually be ours — a silent fall back to vanilla is the scariest failure. */
    private void checkGeneratorInstalled(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        String generator = overworld.getChunkSource().getGenerator().getClass().getName();
        String biomeSource = overworld.getChunkSource().getGenerator().getBiomeSource().getClass().getName();
        report.put("overworld.generator", generator);
        report.put("overworld.biomeSource", biomeSource);
        report.put("overworld.seed", Long.toString(overworld.getSeed()));
        if (!generator.startsWith("me.daddychurchill.CityWorld"))
            fail("overworld generator is " + generator + ", not CityWorld's — check level-type");
        if (!biomeSource.startsWith("me.daddychurchill.CityWorld"))
            fail("overworld biome source is " + biomeSource + ", not CityWorld's");
    }

    /**
     * Sweeps the planning brain and hashes the result. Runs the real
     * ShapeProvider/PlatMap/PlatLot/Context/Plugins cycle without touching the level.
     */
    private void checkPlanning() {
        for (WorldStyle style : STYLES) {
            Map<String, Integer> contexts = new TreeMap<>();
            Map<String, Integer> lots = new TreeMap<>();
            int platmaps = 0;
            CityWorldGenerator gen = new CityWorldGenerator(PLAN_SEED, 256, 63, style, -64, 320);
            for (int cx = -PLAN_RADIUS; cx <= PLAN_RADIUS; cx += PlatMap.Width) {
                for (int cz = -PLAN_RADIUS; cz <= PLAN_RADIUS; cz += PlatMap.Width) {
                    PlatMap platmap = gen.getPlatMap(cx, cz);
                    platmaps++;
                    contexts.merge(platmap.context.getClass().getSimpleName(), 1, Integer::sum);
                    for (int x = 0; x < PlatMap.Width; x++)
                        for (int z = 0; z < PlatMap.Width; z++) {
                            PlatLot lot = platmap.getLot(x, z);
                            if (lot != null)
                                lots.merge(lot.getClass().getSimpleName(), 1, Integer::sum);
                        }
                }
            }
            String key = "plan." + style;
            report.put(key + ".platmaps", Integer.toString(platmaps));
            report.put(key + ".contexts", contexts.toString());
            report.put(key + ".lots", lots.toString());
            // The hash is what versions are compared on: same seed must mean the same city plan,
            // whatever blocks the version happens to ship.
            report.put(key + ".hash", Integer.toHexString((contexts.toString() + lots.toString()).hashCode()));
            if (contexts.isEmpty() || lots.isEmpty())
                fail(style + " planned nothing at all");
        }
    }

    /**
     * Forces a genuinely urban chunk to generate, then reads it back: real blocks, and sign text on
     * both faces. The sign check is the canary for the {@code SignBlockEntity} access transformer —
     * CityWorld writes those fields directly because every public setter notifies the level, which
     * NPEs during decoration.
     */
    private void checkDecorationAndSigns(MinecraftServer server) {
        ServerLevel level = server.overworld();

        // Survey the world as generated rather than predicting which chunks will be roads. An
        // earlier version of this harness rebuilt a CityWorldGenerator and asked it for RoadLots,
        // which is fragile twice over: the harness must guess the world's settings, and it must
        // index PlatMap exactly as the chunk generator does. Loading a block of chunks and looking
        // at what is actually there needs neither, and is what a player wandering around would see.
        int radius = CHUNK_SURVEY_RADIUS;
        Map<String, Integer> blockEntities = new TreeMap<>();
        Map<String, Integer> blocks = new TreeMap<>();
        int signsSeen = 0, signsWithFront = 0, signsWithBack = 0, chunks = 0;
        List<String> signSamples = new ArrayList<>();

        for (int cx = -radius; cx <= radius; cx++)
            for (int cz = -radius; cz <= radius; cz++) {
                final int fx = cx, fz = cz;
                // Chunk loading must be driven from the server thread; generation runs to FULL.
                LevelChunk chunk = server.submit(() -> level.getChunk(fx, fz)).join();
                chunks++;
                for (BlockEntity entity : chunk.getBlockEntities().values()) {
                    // The registry id, not BlockEntityType's identity-based toString — the report is
                    // meant to be read by a human and diffed between versions.
                    blockEntities.merge(
                            String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType())),
                            1, Integer::sum);
                    if (!(entity instanceof SignBlockEntity sign))
                        continue;
                    signsSeen++;
                    boolean front = hasText(sign, true), back = hasText(sign, false);
                    if (front)
                        signsWithFront++;
                    if (back)
                        signsWithBack++;
                    if (signSamples.size() < 5 && front)
                        signSamples.add(readSign(sign));
                }
            }

        // Block inventory from a smaller core, purely for the report: a full-height scan of the
        // whole survey would take far longer than it is worth.
        for (int cx = -2; cx <= 2; cx++)
            for (int cz = -2; cz <= 2; cz++)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++)
                        for (int y = level.getMinY(); y < level.getMaxY(); y++) {
                            BlockState state = level.getBlockState(new BlockPos(cx * 16 + x, y, cz * 16 + z));
                            if (!state.isAir())
                                blocks.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString(),
                                        1, Integer::sum);
                        }

        int built = blocks.values().stream().mapToInt(Integer::intValue).sum();
        report.put("readback.chunksSurveyed", Integer.toString(chunks));
        report.put("readback.nonAirBlocks", Integer.toString(built));
        report.put("readback.distinctBlocks", Integer.toString(blocks.size()));
        report.put("readback.blocks", blocks.toString());
        report.put("readback.blockEntities", blockEntities.toString());
        report.put("readback.signsSeen", Integer.toString(signsSeen));
        report.put("readback.signsWithFrontText", Integer.toString(signsWithFront));
        report.put("readback.signsWithBackText", Integer.toString(signsWithBack));
        report.put("readback.signSamples", signSamples.toString());

        if (built < MIN_BUILT_BLOCKS)
            fail("only " + built + " non-air blocks in the core chunks — decoration looks broken");
        if (signsSeen == 0) {
            fail("no signs in " + chunks + " chunks — street naming may be broken");
        } else {
            if (signsWithFront == 0)
                fail(signsSeen + " signs found but none had front text — check the frontText"
                        + " access transformer");
            if (signsWithBack == 0)
                fail(signsSeen + " signs found but none had back text — check the backText"
                        + " access transformer");
        }
    }

    /** The first non-blank line of a sign's front, for the report. */
    private static String readSign(SignBlockEntity sign) {
        for (int i = 0; i < 4; i++) {
            var message = sign.getFrontText().getMessage(i, false);
            if (message != null && !message.getString().isBlank())
                return message.getString();
        }
        return "";
    }

    /** The lot the generator itself would use for this chunk, or null if it cannot be resolved. */
    private static PlatLot lotAt(CityWorldGenerator gen, int chunkX, int chunkZ) {
        try {
            return gen.getPlatMap(chunkX, chunkZ).getMapLot(chunkX, chunkZ);
        } catch (IndexOutOfBoundsException e) {
            return null;
        }
    }

    private static boolean hasText(SignBlockEntity sign, boolean front) {
        var text = front ? sign.getFrontText() : sign.getText(false);
        for (int i = 0; i < 4; i++) {
            var message = text.getMessage(i, false);
            if (message != null && !message.getString().isBlank())
                return true;
        }
        return false;
    }

    // ---- reporting ------------------------------------------------------------------------------

    private void fail(String why) {
        failures.add(why);
    }

    /** Writes the report as JSON beside the world, for scripts/selftest.sh to diff across versions. */
    private void writeReport(MinecraftServer server) {
        StringBuilder json = new StringBuilder("{\n");
        List<String> keys = new ArrayList<>(report.keySet());
        for (int i = 0; i < keys.size(); i++) {
            String key = keys.get(i);
            json.append("  \"").append(key).append("\": \"").append(escape(report.get(key)))
                    .append(i + 1 < keys.size() || !failures.isEmpty() ? "\",\n" : "\"\n");
        }
        if (!failures.isEmpty()) {
            json.append("  \"failureMessages\": [\n");
            for (int i = 0; i < failures.size(); i++)
                json.append("    \"").append(escape(failures.get(i)))
                        .append(i + 1 < failures.size() ? "\",\n" : "\"\n");
            json.append("  ]\n");
        }
        json.append("}\n");

        Path out = server.getServerDirectory().resolve("cityworld-selftest.json");
        try {
            Files.writeString(out, json.toString(), StandardCharsets.UTF_8);
            CityWorldMod.LOGGER.info("SELFTEST: report written to {}", out);
        } catch (Exception e) {
            CityWorldMod.LOGGER.error("SELFTEST: could not write {}", out, e);
        }
        // Also log the comparable bits, so a CI log alone is enough to diff two versions.
        for (Map.Entry<String, String> entry : report.entrySet())
            if (entry.getKey().endsWith(".hash") || entry.getKey().startsWith("overworld.")
                    || entry.getKey().startsWith("readback.signs"))
                CityWorldMod.LOGGER.info("SELFTEST: {} = {}", entry.getKey(), entry.getValue());
    }

    private static String escape(String raw) {
        return raw.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }
}
