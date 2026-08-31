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
import me.daddychurchill.CityWorld.worldgen.CityWorldBiomes;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
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

    /**
     * Half-width, in blocks, of the biome sweep — ±2048 stepping 64 is 4,225 columns over 4km. Wide
     * because the cave pool is deliberately patchy (cells up to 176 blocks at 4%); it costs no chunk
     * generation, only noise.
     */
    private static final int BIOME_SWEEP_BLOCKS = 2048;
    private static final int BIOME_SWEEP_STEP = 64;

    /** Sampled well above any terrain, so it is always the surface classification. */
    private static final int BIOME_SWEEP_SURFACE_Y = 96;

    /**
     * The wide-coverage sweep: ±4km at 32-block steps, so 66,049 samples. Fine enough that "a few blocks
     * wide" shows up as fragmentation rather than being stepped straight over.
     */
    private static final int WIDE_BLOCKS = 4096;
    private static final int WIDE_STEP = 32;

    /** Depths sampled per column — one in each of the cave pool's Y bands. */
    private static final int[] BIOME_SWEEP_DEPTHS = { -56, -32, 0, 32 };

    /**
     * How far out to look for a chunk the trial-chamber placement claims. Spacing is 34 chunks, so 40
     * is comfortably more than one grid cell in every direction.
     */
    private static final int STRUCTURE_SCAN_CHUNKS = 40;

    /** Cap on how many claimed chunks are actually generated — each one is real worldgen. */
    private static final int STRUCTURE_SCAN_CANDIDATES = 8;

    /**
     * How far to look for an ancient city. Spacing is 24 chunks but only the {@code deep_dark} patches
     * can host one, so the search has to cover many grid cells to find any at all — it is cheap,
     * because the filter runs before anything is generated.
     */
    private static final int ANCIENT_CITY_SCAN_CHUNKS = 256;

    /** How many confirmed ancient-city chunks to actually generate and measure. */
    private static final int ANCIENT_CITY_SAMPLES = 3;

    /** The structure's own {@code start_height} — an absolute Y, so the biome gate is asked there. */
    private static final int ANCIENT_CITY_START_Y = -27;

    /**
     * The floor of the deepest thing CityWorld hangs off street level — the park/roundabout
     * <b>cistern</b>, at {@code streetLevel - cisternDepth + 1} = {@code 64 - 16 + 1}. Sewers are
     * shallower ({@code y 57-62}), so the cistern is what an ancient city would reach first.
     *
     * <p>Mines are excluded deliberately: they go far deeper and are <em>expected</em> to run into a
     * deep-dark city — that reads as the miners having downed tools when they broke through, which is
     * the good version of this collision. A cistern opening into one does not.
     */
    private static final int CITYWORLD_SHALLOWEST_UNDERGROUND_FLOOR = 49;

    /**
     * How much of an ancient city's bounding box must be air for it to count as excavated rather than
     * entombed. Deliberately low — the box includes the city's own walls, floors and pillars, so even a
     * healthy one is mostly solid. Buried measured near zero; this only has to separate the two.
     */
    private static final int ANCIENT_CITY_MIN_AIR_PCT = 15;

    /** Block entities only a trial chamber places, so finding one proves pieces really landed. */
    private static final java.util.Set<String> TRIAL_CHAMBER_BLOCK_ENTITIES = java.util.Set.of(
            "minecraft:trial_spawner", "minecraft:vault");


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
            checkFarmCrops();
            checkBiomeDepth(server);
            checkStructures(server);
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
     * What the farms actually grow — a census of every {@code FarmLot} in the MODERN plan.
     *
     * <p><b>Added because "the fields look empty" needed an answer, not a theory.</b> Bare fields are
     * partly by design: {@code FALLOW} and {@code TRELLIS} plant nothing, and {@code makeConnected}
     * copies a lot's crop across its neighbours, so a single bare roll paints a whole multi-chunk farm
     * region rather than one chunk. That makes bare farms far more conspicuous than their share of the
     * rolls suggests, which is exactly how it looked from the air.
     *
     * <p>So this records the split rather than asserting a feeling, and fails only if the tilled crops
     * vanish entirely — the regression that switching fields to a tag-driven pool could actually cause.
     */
    private void checkFarmCrops() {
        Map<String, Integer> crops = new TreeMap<>();
        int farms = 0, bare = 0, tilled = 0;
        CityWorldGenerator gen = new CityWorldGenerator(PLAN_SEED, 256, 63, WorldStyle.MODERN, -64, 320);
        for (int cx = -PLAN_RADIUS; cx <= PLAN_RADIUS; cx += PlatMap.Width)
            for (int cz = -PLAN_RADIUS; cz <= PLAN_RADIUS; cz += PlatMap.Width) {
                PlatMap platmap = gen.getPlatMap(cx, cz);
                for (int x = 0; x < PlatMap.Width; x++)
                    for (int z = 0; z < PlatMap.Width; z++) {
                        if (!(platmap.getLot(x, z) instanceof me.daddychurchill.CityWorld.Plats.Rural.FarmLot farm))
                            continue;
                        farms++;
                        var type = farm.getCropType();
                        crops.merge(type.name(), 1, Integer::sum);
                        switch (type) {
                        case FALLOW, TRELLIS -> bare++;
                        case WHEAT, CARROT, POTATO, BEETROOT -> tilled++;
                        default -> { }
                        }
                    }
            }
        // What the field pools actually resolve to, with whatever mods are installed. A field falls back
        // to its hardcoded vanilla flower when its pool is empty, and that fallback looks exactly like
        // "the feature does nothing" from in-world — so the pool contents are worth stating outright.
        var flowers = me.daddychurchill.CityWorld.Support.MaterialTags
                .resolve(me.daddychurchill.CityWorld.Support.MaterialTags.FARM_FLOWERS);
        var tall = me.daddychurchill.CityWorld.Support.MaterialTags
                .resolve(me.daddychurchill.CityWorld.Support.MaterialTags.FARM_TALL_FLOWERS);
        var seeds = me.daddychurchill.CityWorld.Support.MaterialTags
                .resolve(me.daddychurchill.CityWorld.Support.MaterialTags.FARM_CROPS);
        report.put("farm.pool.flowers", flowers.size() + ": " + sample(flowers));
        report.put("farm.pool.tallFlowers", tall.size() + ": " + sample(tall));
        report.put("farm.pool.crops", seeds.size() + ": " + sample(seeds));
        if (flowers.isEmpty() || tall.isEmpty() || seeds.isEmpty())
            fail("a farm pool resolved empty — fields will silently fall back to one hardcoded vanilla plant");

        report.put("farm.lots", Integer.toString(farms));
        report.put("farm.crops", crops.toString());
        report.put("farm.barePct", farms == 0 ? "0"
                : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * bare / farms));
        report.put("farm.tilledPct", farms == 0 ? "0"
                : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * tilled / farms));
        if (farms > 0 && tilled == 0)
            fail("no farm grows a tilled crop — the crop pool is planting nothing");
    }

    /** First few entries of a resolved pool, named, for eyeballing what a mod contributed. */
    private static String sample(java.util.List<me.daddychurchill.CityWorld.compat.Material> pool) {
        return pool.stream().limit(60)
                .map(m -> {
                    var block = m.getBlock();
                    return block == null ? "?"
                            : net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(block).toString();
                })
                .toList().toString();
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
     * The biome map must be genuinely <b>three-dimensional</b>, and the cave pool must be reachable.
     *
     * <p>This is the canary for the thing that kept vanilla structures from ever placing: CityWorld
     * used to classify columns in {@code createBiomes} and leave {@code BiomeSource.getNoiseBiome} a
     * constant stub — but {@code getNoiseBiome} is the method {@code Structure.isValidBiome} consults,
     * so every structure was gated against one biome at every height. A regression here would be
     * invisible in a world you fly around: the surface would look completely normal.
     *
     * <p>It asks the biome source directly rather than loading chunks. That is not "predicting the
     * world" — the source <em>is</em> the thing under test, and querying it costs no chunk generation,
     * so the sweep can cover kilometres instead of the 169 chunks the block survey manages. The cave
     * pool is patchy by design ({@link CaveRegions}), so a survey the size of the block one would find
     * nothing and the check would fail at random.
     *
     * <p>Both assertions are presence-based, per this harness's rule: "at least one" and "any of",
     * never an exact count.
     */
    private void checkBiomeDepth(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        BiomeSource source = generator.getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();

        report.put("biome.possible", Integer.toString(source.possibleBiomes().size()));

        // The pool as this Minecraft version actually resolved it — sulfur caves is 26.2+, so this
        // legitimately differs between versions and is reported rather than asserted.
        java.util.Set<String> poolNames = new java.util.TreeSet<>();
        if (source instanceof CityWorldBiomes cityBiomes)
            cityBiomes.cavePool().biomes().forEach(
                    h -> h.unwrapKey().ifPresent(k -> poolNames.add(k.identifier().getPath())));
        report.put("biome.cavePool", poolNames.toString());
        if (poolNames.isEmpty())
            fail("the cityworld:cave_pool tag resolved to nothing — no cave biomes at all, so ancient "
                    + "cities cannot place and cave decoration has nothing to key off");

        Map<String, Integer> deepBiomes = new TreeMap<>();
        Map<String, Integer> surfaceBiomes = new TreeMap<>();
        int columns = 0, columnsVaryingWithDepth = 0, poolHits = 0;

        for (int x = -BIOME_SWEEP_BLOCKS; x <= BIOME_SWEEP_BLOCKS; x += BIOME_SWEEP_STEP)
            for (int z = -BIOME_SWEEP_BLOCKS; z <= BIOME_SWEEP_BLOCKS; z += BIOME_SWEEP_STEP) {
                columns++;
                int qx = QuartPos.fromBlock(x), qz = QuartPos.fromBlock(z);
                String surface = biomeName(source, qx, QuartPos.fromBlock(BIOME_SWEEP_SURFACE_Y), qz, sampler);
                surfaceBiomes.merge(surface, 1, Integer::sum);

                boolean varies = false;
                for (int y : BIOME_SWEEP_DEPTHS) {
                    String deep = biomeName(source, qx, QuartPos.fromBlock(y), qz, sampler);
                    deepBiomes.merge(deep, 1, Integer::sum);
                    if (!deep.equals(surface))
                        varies = true;
                    if (poolNames.contains(deep))
                        poolHits++;
                }
                if (varies)
                    columnsVaryingWithDepth++;
            }

        report.put("biome.sweep.columns", Integer.toString(columns));
        report.put("biome.sweep.varyingWithDepth", Integer.toString(columnsVaryingWithDepth));
        report.put("biome.sweep.surface", surfaceBiomes.keySet().toString());
        report.put("biome.sweep.deep", deepBiomes.keySet().toString());
        report.put("biome.sweep.poolHits", Integer.toString(poolHits));

        // The climate itself, not just which biomes exist. climateWarmth leans the temperature field
        // warm, and "did it actually move, and by how much" is only answerable as a mean — a list of
        // distinct biomes shows presence, never proportion.
        if (level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator gen) {
            CityWorldGenerator ctx = gen.getContext(level);
            double sum = 0; int n = 0, warm = 0, cold = 0;
            for (int x = -BIOME_SWEEP_BLOCKS; x <= BIOME_SWEEP_BLOCKS; x += BIOME_SWEEP_STEP)
                for (int z = -BIOME_SWEEP_BLOCKS; z <= BIOME_SWEEP_BLOCKS; z += BIOME_SWEEP_STEP) {
                    double t = ctx.getTemperature(x, z);
                    sum += t; n++;
                    if (t >= 0.6) warm++;
                    else if (t < 0.35) cold++;
                }
            report.put("climate.meanTemperature", String.format("%.4f", sum / Math.max(1, n)));
            report.put("climate.warmColumnsPct", Integer.toString(warm * 100 / Math.max(1, n)));
            report.put("climate.coldColumnsPct", Integer.toString(cold * 100 / Math.max(1, n)));
            report.put("climate.warmthSetting", String.format("%.2f", ctx.getSettings().climateWarmth));
        }

        if (columnsVaryingWithDepth == 0)
            fail("biome never varies with depth across " + columns
                    + " columns — getNoiseBiome is still answering per column (2D), so structures "
                    + "are gated against the surface biome at every height");
        if (poolHits == 0)
            fail("no cave-pool biome (" + poolNames + ") anywhere in the sweep — "
                    + "deep_dark unreachable means ancient cities can never place");

        checkCaveDecorationVocabulary(level);
        checkTerraBlender(level, source, sampler);
        checkWideBiomeCoverage(level, source, sampler);
    }

    /**
     * How much of an installed TerraBlender mod's biome set CityWorld can actually reach.
     *
     * <p><b>Reachability is the measurement that matters, not presence.</b> Vanilla's climate points
     * have seven axes and CityWorld models a subset; if the mapping from CityWorld's climate into those
     * axes is too narrow, the parameter list keeps returning the same handful of biomes and the
     * integration looks like it works while doing almost nothing. That failure is silent — no
     * exception, no missing mod, just a suspiciously dull world. So this reports **harvested vs
     * actually reachable** and lets a human judge the ratio.
     *
     * <p>Reports only, never fails: with no TerraBlender installed the honest answer is zero, and that
     * is not a fault.
     */
    private void checkTerraBlender(ServerLevel level, BiomeSource source, Climate.Sampler sampler) {
        report.put("terraBlender.modPresent", Boolean.toString(
                me.daddychurchill.CityWorld.worldgen.TerraBlenderBridge.present()));
        if (!(source instanceof me.daddychurchill.CityWorld.worldgen.CityWorldClimateBiomeSource climate))
            return;
        var bridge = climate.terraBlender();
        if (bridge == null) {
            report.put("terraBlender.harvested", "0");
            return;
        }
        report.put("terraBlender.harvested", Integer.toString(bridge.biomes().size()));

        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator generator))
            return;
        CityWorldGenerator context = generator.getContext(level);

        // Sweep the same grid the biome sweep uses, asking the bridge directly with CityWorld's axes —
        // so this measures the axis mapping, independent of whether useModdedBiomes is switched on.
        java.util.Set<String> reached = new java.util.TreeSet<>();
        java.util.Set<String> withShare = new java.util.TreeSet<>();
        java.util.Set<String> directOnly = new java.util.TreeSet<>();
        int shareColumns = 0, total = 0, directGround = 0, shareGround = 0;
        double share = context.getSettings().moddedBiomeShare;
        for (int x = -BIOME_SWEEP_BLOCKS; x <= BIOME_SWEEP_BLOCKS; x += BIOME_SWEEP_STEP)
            for (int z = -BIOME_SWEEP_BLOCKS; z <= BIOME_SWEEP_BLOCKS; z += BIOME_SWEEP_STEP) {
                Climate.TargetPoint target = Climate.target(
                        (float) (context.getTemperature(x, z) * 2.0 - 1.0),
                        (float) (context.getHumidity(x, z) * 2.0 - 1.0),
                        (float) context.getContinentalness(x, z),
                        (float) context.getErosion(x, z),
                        0.0F,
                        (float) context.getWeirdness(x, z));
                var hit = bridge.find(target);
                total++;
                if (hit != null)
                    hit.unwrapKey().ifPresent(k -> reached.add(k.identifier().toString()));

                // What a player would actually meet: the direct win where there is one, and on the
                // reserved share the modded-only answer. This is the number the share dial exists to
                // move, so measuring only the direct wins would report the feature as doing nothing.
                boolean reserved = share > 0.0 && context.getModdedShare(x, z) < share;
                if (reserved)
                    shareColumns++;
                boolean directWin = hit != null
                        && me.daddychurchill.CityWorld.worldgen.TerraBlenderBridge.isModded(hit);
                if (directWin) {
                    directGround++;
                    hit.unwrapKey().ifPresent(k -> directOnly.add(k.identifier().toString()));
                }
                var effective = directWin ? hit : reserved ? bridge.findModded(target) : null;
                if (effective != null) {
                    shareGround++;
                    effective.unwrapKey().ifPresent(k -> withShare.add(k.identifier().toString()));
                }
            }
        // ⚠ `reachable` counts EVERY hit, vanilla included — TerraBlender's regions carry vanilla biomes
        // and they win most points. It measures the axis mapping, not what a mod contributes, so it
        // reads far higher than the number of modded biomes a player can actually meet. The two
        // *modded* counts below are the comparable pair, and the honest before/after for the share.
        report.put("terraBlender.reachable", Integer.toString(reached.size()));
        report.put("terraBlender.reachableModdedDirect", Integer.toString(directOnly.size()));
        report.put("terraBlender.reachableWithShare", Integer.toString(withShare.size()));
        report.put("terraBlender.moddedGroundDirectPct",
                total == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * directGround / total));
        report.put("terraBlender.moddedGroundWithSharePct",
                total == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * shareGround / total));
        report.put("terraBlender.shareOfGround",
                total == 0 ? "0" : String.format(java.util.Locale.ROOT, "%.1f%%", 100.0 * shareColumns / total));
        analyseUnreachable(bridge, context, reached);
        report.put("terraBlender.reachedSample", reached.stream().limit(200).toList().toString());

        // The share must not become "modded biomes everywhere" — the failure the modded-hit check was
        // added to prevent, which cost swamp 80% of its ground before it was caught.
        if (share > 0.0 && share < 1.0 && total > 0 && shareColumns > total * 0.9)
            fail("the modded share claimed " + shareColumns + " of " + total + " columns at share=" + share
                    + " — the share field is not selective, so CityWorld's own palette is being replaced");
    }

    /**
     * The features the cave-decoration pass will place under a city.
     *
     * <p>This filter is the whole safety argument for that pass, and it is the thing most likely to be
     * quietly wrong. Lush caves keep their character in {@code VEGETAL_DECORATION} — the step that also
     * plants trees, which {@code dripstone_caves} and {@code deep_dark} both carry
     * ({@code trees_plains}, {@code patch_pumpkin}). Keeping only features no non-cave biome has is what
     * stops a city growing a forest down its high street, so the test asserts both halves: the cave
     * vocabulary is present, and nothing tree-shaped survived.
     */
    private void checkCaveDecorationVocabulary(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator generator))
            return;
        java.util.Set<String> names = new java.util.TreeSet<>();
        generator.caveOnlyFeatures()
                .forEach(h -> h.unwrapKey().ifPresent(k -> names.add(k.identifier().getPath())));
        report.put("biome.caveFeatures", names.toString());

        if (names.isEmpty()) {
            fail("no cave-only features resolved — cave biomes would be labelled but never decorated");
            return;
        }
        // A cave biome may legitimately own a tree — BoP's fungal_jungle grows giant mushrooms, and its
        // trees_fungal_jungle is exclusive to it, so the cave-only filter passes it through correctly.
        // Those are safe: the pool only ever assigns a cave biome underground, so the "tree" is a cave
        // tree. What must never appear is a feature belonging to a biome the SURFACE can produce.
        java.util.Set<String> caveOwned = new java.util.TreeSet<>();
        if (level.getChunkSource().getGenerator().getBiomeSource() instanceof CityWorldBiomes cb)
            cb.cavePool().biomes().forEach(
                    h -> h.unwrapKey().ifPresent(k -> caveOwned.add(k.identifier().getPath())));
        for (String surfaceish : List.of("trees_", "patch_pumpkin", "flower_plains", "patch_grass_plain"))
            for (String name : names) {
                if (!(name.startsWith(surfaceish) || name.equals(surfaceish)))
                    continue;
                if (caveOwned.stream().anyMatch(name::contains))
                    continue; // named for a cave biome — its own, and only placed underground
                fail("cave decoration would place '" + name + "' — a surface feature reached the "
                        + "cave-only set, so cities will grow trees and pumpkins on their roads");
            }
        if (names.stream().noneMatch(n -> n.contains("lush") || n.contains("dripstone") || n.contains("sculk")))
            fail("the cave-only set has none of lush/dripstone/sculk (" + names
                    + ") — the filter is excluding the very features the pass exists to place");
    }

    /**
     * The vanilla structures a CityWorld world keeps must survive selection, be positioned off the
     * <em>world</em> seed, and actually put blocks in the ground.
     *
     * <p>Three distinct things can break here, and only the third is visible from a player's chair:
     * <ul>
     *   <li><b>Selection</b> — a set is dropped if the biome source cannot produce any of its biomes.
     *       Ancient cities are the canary: they are gated on {@code deep_dark}, so if the cave pool
     *       regresses they vanish from this list with no other symptom.
     *   <li><b>Seeding</b> — strongholds are laid out on concentric rings from a seed that
     *       {@code createForFlat} hardcodes to {@code 0L}. Non-empty ring positions prove the normal,
     *       level-seeded path is in use; the first ring position is recorded so a human diffing two
     *       reports can see it move if that regresses.
     *   <li><b>Placement</b> — starts are decided at one chunk stage and the blocks are laid down in
     *       {@code applyBiomeDecoration}, which CityWorld overrides. Getting the first two right and
     *       the third wrong yields structures sliced to a few chunks, which reads like corrupt
     *       worldgen. Hence the read-back below: find a chunk the placement math claims, generate it,
     *       and look for the structure's own block entities.
     * </ul>
     */
    private void checkStructures(MinecraftServer server) {
        ServerLevel level = server.overworld();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();

        Map<String, Holder<StructureSet>> sets = new TreeMap<>();
        for (Holder<StructureSet> set : state.possibleStructureSets())
            set.unwrapKey().ifPresent(k -> sets.put(k.identifier().toString(), set));
        report.put("structures.sets", sets.keySet().toString());

        if (sets.isEmpty()) {
            fail("no structure sets survived selection — no stronghold means no End portal, so eyes "
                    + "of ender have nothing to find");
            return;
        }
        for (String wanted : List.of("minecraft:strongholds", "minecraft:trial_chambers", "minecraft:ancient_cities"))
            if (!sets.containsKey(wanted))
                fail("structure set " + wanted + " did not survive selection — either it is missing from "
                        + "the cityworld:allowed tag, or the biome source cannot produce any biome it needs");

        // --- seeding: the stronghold rings ---------------------------------------------------------
        Holder<StructureSet> strongholds = sets.get("minecraft:strongholds");
        if (strongholds != null
                && strongholds.value().placement() instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> ringPositions = state.getRingPositionsFor(rings);
            int count = ringPositions == null ? 0 : ringPositions.size();
            report.put("structures.stronghold.ringPositions", Integer.toString(count));
            if (count == 0)
                fail("stronghold ring positions are empty — nothing for an eye of ender to point at");
            else
                report.put("structures.stronghold.firstRing",
                        ringPositions.get(0).x() + "," + ringPositions.get(0).z());
        } else {
            fail("the stronghold set is not on a concentric-rings placement — eyes of ender rely on it");
        }

        // --- placement: does a claimed chunk actually contain structure blocks? --------------------
        Holder<StructureSet> trials = sets.get("minecraft:trial_chambers");
        if (trials == null)
            return;
        StructurePlacement placement = trials.value().placement();
        int examined = 0, withStarts = 0, structureBlockEntities = 0;
        String foundAt = "none";

        outer:
        for (int r = 0; r <= STRUCTURE_SCAN_CHUNKS; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r)
                        continue; // outer ring only
                    if (!placement.isStructureChunk(state, cx, cz))
                        continue;
                    if (++examined > STRUCTURE_SCAN_CANDIDATES)
                        break outer;
                    final int fx = cx, fz = cz;
                    LevelChunk chunk = server.submit(() -> level.getChunk(fx, fz)).join();
                    if (chunk.getAllStarts().isEmpty())
                        continue; // claimed by the spread, but the biome check rejected it
                    withStarts++;
                    for (BlockEntity entity : chunk.getBlockEntities().values()) {
                        String id = String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(entity.getType()));
                        if (TRIAL_CHAMBER_BLOCK_ENTITIES.contains(id))
                            structureBlockEntities++;
                    }
                    if (structureBlockEntities > 0) {
                        foundAt = fx + "," + fz;
                        break outer;
                    }
                }

        report.put("structures.trial.candidatesExamined", Integer.toString(examined));
        report.put("structures.trial.chunksWithStarts", Integer.toString(withStarts));
        report.put("structures.trial.blockEntities", Integer.toString(structureBlockEntities));
        report.put("structures.trial.foundAt", foundAt);

        checkAncientCityDepth(server, sets.get("minecraft:ancient_cities"));

        if (examined == 0)
            fail("the trial-chamber placement claimed no chunk within " + STRUCTURE_SCAN_CHUNKS
                    + " chunks of spawn — placement math is not running");
        else if (withStarts == 0)
            fail("no claimed trial-chamber chunk carried a structure start — the biome gate is "
                    + "rejecting every candidate (getNoiseBiome answering wrongly?)");
        else if (structureBlockEntities == 0)
            fail("trial-chamber starts exist but the chunk holds none of " + TRIAL_CHAMBER_BLOCK_ENTITIES
                    + " — starts are being decided and then never placed, which is what happens when "
                    + "applyBiomeDecoration skips the structure step");
    }

    /**
     * How high an ancient city actually reaches — measured, because it decides whether they can collide
     * with CityWorld's own underground.
     *
     * <p>The structure's {@code start_height} is an absolute {@code y = -27}, but that is only where the
     * jigsaw <em>starts</em>; pieces connect outward and upward from there, so the real ceiling has to
     * be read off a generated start's bounding box rather than inferred from the JSON. CityWorld's
     * sewers sit just under the road (street level 64, less two 4-block floors ≈ y 57–62) and its mines
     * go far deeper, so the question is only ever "do the two bands overlap".
     *
     * <p><b>Also a cross-version canary.</b> If a future Minecraft moves the deep dark or reshapes the
     * city, this number moves and the report shows it — which is cheaper than finding out from a
     * screenshot of a sewer opening into a warden's lair.
     *
     * <p>Costs almost nothing to search: {@code isStructureChunk} is pure maths and the biome gate can
     * be asked of the biome source directly, so only the handful of chunks that pass both are ever
     * generated.
     */
    private void checkAncientCityDepth(MinecraftServer server, Holder<StructureSet> ancientCities) {
        if (ancientCities == null)
            return;
        ServerLevel level = server.overworld();
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        StructurePlacement placement = ancientCities.value().placement();

        int candidates = 0, generated = 0, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        long airInBox = 0, boxBlocks = 0;
        String at = "none";

        outer:
        for (int r = 0; r <= ANCIENT_CITY_SCAN_CHUNKS; r++)
            for (int cx = -r; cx <= r; cx++)
                for (int cz = -r; cz <= r; cz++) {
                    if (Math.max(Math.abs(cx), Math.abs(cz)) != r)
                        continue;
                    if (!placement.isStructureChunk(state, cx, cz))
                        continue;
                    candidates++;
                    // Ask the biome source before generating anything: an ancient city only starts where
                    // the biome at its own start height is deep_dark.
                    String biome = biomeName(source, QuartPos.fromBlock(cx * 16 + 8),
                            QuartPos.fromBlock(ANCIENT_CITY_START_Y), QuartPos.fromBlock(cz * 16 + 8), sampler);
                    if (!"deep_dark".equals(biome))
                        continue;
                    if (++generated > ANCIENT_CITY_SAMPLES)
                        break outer;
                    final int fx = cx, fz = cz;
                    LevelChunk chunk = server.submit(() -> level.getChunk(fx, fz)).join();
                    for (var start : chunk.getAllStarts().values()) {
                        if (!start.isValid())
                            continue;
                        minY = Math.min(minY, start.getBoundingBox().minY());
                        maxY = Math.max(maxY, start.getBoundingBox().maxY());
                        at = fx + "," + fz;
                        airInBox += countAir(chunk, start.getBoundingBox());
                        boxBlocks += boxVolumeInChunk(chunk, start.getBoundingBox());
                    }
                }

        report.put("structures.ancientCity.candidates", Integer.toString(candidates));
        report.put("structures.ancientCity.generated", Integer.toString(generated));
        report.put("structures.ancientCity.foundAt", at);
        if (minY <= maxY) {
            report.put("structures.ancientCity.minY", Integer.toString(minY));
            report.put("structures.ancientCity.maxY", Integer.toString(maxY));

            // Is it actually walkable, or stamped into solid rock? An ancient city is terrain_adaptation
            // "beard_box": vanilla carves the terrain away from it via the Beardifier density function,
            // which CityWorld has no equivalent of unless carveForStructures runs. Without it the city
            // generates and /locate finds it, but it is entombed — which is what shipped first and is
            // invisible to every other check here.
            int airPct = boxBlocks == 0 ? 0 : (int) (airInBox * 100 / boxBlocks);
            report.put("structures.ancientCity.airPercent", Integer.toString(airPct));
            if (airPct < ANCIENT_CITY_MIN_AIR_PCT)
                fail("an ancient city's bounding box is only " + airPct + "% air (want >= "
                        + ANCIENT_CITY_MIN_AIR_PCT + ") — it is buried in solid terrain, so terrain "
                        + "adaptation (carveForStructures) is not running for beard_box structures");
            if (maxY >= CITYWORLD_SHALLOWEST_UNDERGROUND_FLOOR)
                fail("an ancient city reaches y=" + maxY + ", at or above CityWorld's cistern floor (y="
                        + CITYWORLD_SHALLOWEST_UNDERGROUND_FLOOR
                        + ") — cisterns and sewers can now open into one");
        }
    }

    /** Air blocks inside the part of {@code box} that lies in this chunk. */
    private static long countAir(LevelChunk chunk, net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        ChunkPos pos = chunk.getPos();
        int x0 = Math.max(box.minX(), pos.getMinBlockX()), x1 = Math.min(box.maxX(), pos.getMinBlockX() + 15);
        int z0 = Math.max(box.minZ(), pos.getMinBlockZ()), z1 = Math.min(box.maxZ(), pos.getMinBlockZ() + 15);
        int y0 = Math.max(box.minY(), chunk.getMinY()), y1 = Math.min(box.maxY(), chunk.getMaxY());
        long air = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
            for (int z = z0; z <= z1; z++)
                for (int y = y0; y <= y1; y++)
                    if (chunk.getBlockState(cursor.set(x, y, z)).isAir())
                        air++;
        return air;
    }

    /** Total blocks in the part of {@code box} that lies in this chunk — the denominator for the above. */
    private static long boxVolumeInChunk(LevelChunk chunk,
            net.minecraft.world.level.levelgen.structure.BoundingBox box) {
        ChunkPos pos = chunk.getPos();
        long w = Math.max(0, Math.min(box.maxX(), pos.getMinBlockX() + 15) - Math.max(box.minX(), pos.getMinBlockX()) + 1);
        long d = Math.max(0, Math.min(box.maxZ(), pos.getMinBlockZ() + 15) - Math.max(box.minZ(), pos.getMinBlockZ()) + 1);
        long h = Math.max(0, Math.min(box.maxY(), chunk.getMaxY()) - Math.max(box.minY(), chunk.getMinY()) + 1);
        return w * d * h;
    }

    /**
     * A wide, fine-grained sweep of the surface biome map — how much ground each biome actually covers,
     * and how fragmented the result is.
     *
     * <p><b>Distinct-biome counts flatter a biome map.</b> A biome that appears as a handful of
     * three-block slivers counts the same as one covering a province, so "86 biomes reachable" can
     * coexist with a world that reads as noise. This samples every {@value #WIDE_STEP} blocks over
     * ±{@value #WIDE_BLOCKS} and reports **share of ground** and an **edge density** — the percentage
     * of samples whose eastern or southern neighbour is a different biome.
     *
     * <p>Edge density is the fragmentation number. Large coherent regions give a low figure; a
     * speckled, sliver-ridden map gives a high one. It has no single "right" value, so it is reported
     * rather than asserted — but comparing it between a modded and an unmodded world says plainly
     * whether the modded path is fragmenting the map.
     */
    private void checkWideBiomeCoverage(ServerLevel level, BiomeSource source, Climate.Sampler sampler) {
        int side = (WIDE_BLOCKS * 2) / WIDE_STEP + 1;
        String[][] grid = new String[side][side];
        Map<String, Integer> counts = new TreeMap<>();
        int total = 0;

        for (int ix = 0; ix < side; ix++)
            for (int iz = 0; iz < side; iz++) {
                int x = -WIDE_BLOCKS + ix * WIDE_STEP, z = -WIDE_BLOCKS + iz * WIDE_STEP;
                String b = biomeId(source, QuartPos.fromBlock(x),
                        QuartPos.fromBlock(BIOME_SWEEP_SURFACE_Y), QuartPos.fromBlock(z), sampler);
                grid[ix][iz] = b;
                counts.merge(b, 1, Integer::sum);
                total++;
            }

        int edges = 0, pairs = 0;
        for (int ix = 0; ix < side; ix++)
            for (int iz = 0; iz < side; iz++) {
                if (ix + 1 < side) { pairs++; if (!grid[ix][iz].equals(grid[ix + 1][iz])) edges++; }
                if (iz + 1 < side) { pairs++; if (!grid[ix][iz].equals(grid[ix][iz + 1])) edges++; }
            }

        final int samples = total;
        int modded = counts.entrySet().stream()
                .filter(e -> !e.getKey().startsWith("minecraft:"))
                .mapToInt(Map.Entry::getValue).sum();

        report.put("biome.wide.columns", Integer.toString(total));
        report.put("biome.wide.stepBlocks", Integer.toString(WIDE_STEP));
        report.put("biome.wide.distinct", Integer.toString(counts.size()));
        report.put("biome.wide.edgeDensityPct", Integer.toString(edges * 100 / Math.max(1, pairs)));
        report.put("biome.wide.moddedGroundPct", Integer.toString(modded * 100 / Math.max(1, total)));

        // Top biomes by ground covered — the share is what "does this biome really exist" means.
        String top = counts.entrySet().stream()
                .sorted((a, b) -> b.getValue() - a.getValue()).limit(20)
                .map(e -> e.getKey() + "=" + (e.getValue() * 1000 / Math.max(1, samples)) / 10.0 + "%")
                .toList().toString();
        report.put("biome.wide.top", top);

        // And the tail: biomes present but on almost no ground, which is what "a few blocks wide" looks
        // like in the data.
        long slivers = counts.values().stream().filter(v -> v * 1000 / Math.max(1, samples) < 1).count();
        report.put("biome.wide.sliverBiomes", Long.toString(slivers) + " of " + counts.size() + " under 0.1%");
    }

    /**
     * Why each unreachable biome is unreachable — which climate axis is the gap.
     *
     * <p>"31 of 113 unreachable" says a problem exists but not what to do about it. This measures the
     * range CityWorld actually <em>produces</em> on each axis, measures the range each biome
     * <em>demands</em>, and names the axes that do not overlap. That distinguishes the two fixes: an
     * axis CityWorld under-produces across many biomes argues for widening that axis, while a biome
     * demanding something no axis of ours could express argues for overriding it directly.
     *
     * <p>Depth is excluded from the verdict: the bridge deliberately queries at the surface, so a
     * biome wanting depth is out of scope by design rather than by accident.
     */
    private void analyseUnreachable(me.daddychurchill.CityWorld.worldgen.TerraBlenderBridge bridge,
            CityWorldGenerator context, java.util.Set<String> reached) {
        // What CityWorld actually produces, sampled over the same grid.
        double tLo = 9, tHi = -9, hLo = 9, hHi = -9, cLo = 9, cHi = -9, eLo = 9, eHi = -9, wLo = 9, wHi = -9;
        for (int x = -BIOME_SWEEP_BLOCKS; x <= BIOME_SWEEP_BLOCKS; x += BIOME_SWEEP_STEP)
            for (int z = -BIOME_SWEEP_BLOCKS; z <= BIOME_SWEEP_BLOCKS; z += BIOME_SWEEP_STEP) {
                double t = context.getTemperature(x, z) * 2.0 - 1.0, h = context.getHumidity(x, z) * 2.0 - 1.0;
                double c = context.getContinentalness(x, z), e = context.getErosion(x, z),
                        w = context.getWeirdness(x, z);
                tLo = Math.min(tLo, t); tHi = Math.max(tHi, t);
                hLo = Math.min(hLo, h); hHi = Math.max(hHi, h);
                cLo = Math.min(cLo, c); cHi = Math.max(cHi, c);
                eLo = Math.min(eLo, e); eHi = Math.max(eHi, e);
                wLo = Math.min(wLo, w); wHi = Math.max(wHi, w);
            }
        report.put("axes.cityworld.temperature", range(tLo, tHi));
        report.put("axes.cityworld.humidity", range(hLo, hHi));
        report.put("axes.cityworld.continentalness", range(cLo, cHi));
        report.put("axes.cityworld.erosion", range(eLo, eHi));
        report.put("axes.cityworld.weirdness", range(wLo, wHi));

        double[][] ours = { { tLo, tHi }, { hLo, hHi }, { cLo, cHi }, { eLo, eHi }, { wLo, wHi } };
        String[] axisNames = { "temperature", "humidity", "continentalness", "erosion", "weirdness" };

        // Per biome, the union of what its points demand on each axis.
        Map<String, double[][]> demand = new TreeMap<>();
        for (var pair : bridge.points()) {
            String id = pair.getSecond().unwrapKey().map(k -> k.identifier().toString()).orElse("?");
            Climate.ParameterPoint pt = pair.getFirst();
            Climate.Parameter[] axes = { pt.temperature(), pt.humidity(), pt.continentalness(), pt.erosion(),
                    pt.weirdness() };
            double[][] d = demand.computeIfAbsent(id, k -> {
                double[][] init = new double[5][2];
                for (double[] a : init) { a[0] = 9; a[1] = -9; }
                return init;
            });
            for (int i = 0; i < 5; i++) {
                d[i][0] = Math.min(d[i][0], Climate.unquantizeCoord(axes[i].min()));
                d[i][1] = Math.max(d[i][1], Climate.unquantizeCoord(axes[i].max()));
            }
        }

        Map<String, Integer> blamed = new TreeMap<>();
        List<String> detail = new ArrayList<>();
        for (var e : demand.entrySet()) {
            String id = e.getKey();
            if (reached.contains(id))
                continue;
            List<String> gaps = new ArrayList<>();
            for (int i = 0; i < 5; i++) {
                double[] d = e.getValue()[i];
                if (d[1] < ours[i][0] || d[0] > ours[i][1]) { // no overlap at all
                    gaps.add(axisNames[i] + " wants " + range(d[0], d[1]));
                    blamed.merge(axisNames[i], 1, Integer::sum);
                }
            }
            if (gaps.isEmpty())
                blamed.merge("(overlaps on every axis — lost to a nearer biome)", 1, Integer::sum);
            if (detail.size() < 12)
                detail.add(id.replace("biomesoplenty:", "") + ": "
                        + (gaps.isEmpty() ? "no single-axis gap" : String.join("; ", gaps)));
        }
        report.put("axes.unreachableCount", Integer.toString(
                (int) demand.keySet().stream().filter(k -> !reached.contains(k)).count()));
        report.put("axes.blamedAxis", blamed.toString());
        report.put("axes.examples", detail.toString());
    }

    private static String range(double lo, double hi) {
        return String.format("%.2f..%.2f", lo, hi);
    }

    /** Full {@code namespace:path} — the wide probe needs the namespace to tell modded from vanilla. */
    private static String biomeId(BiomeSource source, int quartX, int quartY, int quartZ,
            Climate.Sampler sampler) {
        return source.getNoiseBiome(quartX, quartY, quartZ, sampler).unwrapKey()
                .map(k -> k.identifier().toString()).orElse("?");
    }

    /** The biome's registry path at a quart position, or {@code "?"} if it carries no key. */
    private static String biomeName(BiomeSource source, int quartX, int quartY, int quartZ,
            Climate.Sampler sampler) {
        return source.getNoiseBiome(quartX, quartY, quartZ, sampler).unwrapKey()
                .map(k -> k.identifier().getPath()).orElse("?");
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
