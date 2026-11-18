package me.daddychurchill.CityWorld.Support;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Predicate;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.BaseRailBlock;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

/**
 * Smooths the subway's bends with Slash Rails, as a player with its Track Smoother would (GitHub issue #7; see
 * TRAINS-PLAN.md). Slash Rails only rounds a run when a player asks it to, and keeps the runs it has rounded in the
 * world's saved data, so this asks once per bend: when a bend chunk first comes into the world (a chunk just
 * generated, never one loaded from disk) its two tracks are queued, and a second later, once the chunk is in the world
 * proper, each is smoothed the way the mod's own {@code RunService.smooth(ServerLevel, BlockPos)} does it — by
 * reflection, so nothing here loads without the mod, and a mod that renames its parts is simply not called.
 *
 * <p>The mod walks the whole flat run from the rail it is given, until a dead end, a slope, a rail of another run, an
 * unloaded chunk or 512 rails; so a bend's run is the bend plus whatever loaded straight lies either side, and the next
 * bend's run stops where this one's began. Off with {@code subways.smoothBends}.
 *
 * <p><b>Never a chunk load.</b> The mod's clearance check reads the blocks one round every rail of the run, and a run
 * that stops at the edge of what is loaded ends on a chunk's edge, so {@code smooth} read into the next chunk, and the
 * server thread loaded it (in a new world, GENERATED it, a CityWorld chunk and its neighbours) on the spot: pauses of
 * up to ten seconds while exploring (the owner's 1.21.1 world, 2026-11-17, read from thread dumps). So the steps of
 * {@code smooth} are taken here: the mod's own run detection, stopped also at a rail whose surroundings are not all in
 * loaded chunks; its clearance; its registry; its packet to every player with the mod. And a bend waits until its own
 * chunk and the four beside it are loaded, so the curve is never cut short. Missing any of those parts, nothing is
 * smoothed (never the plain {@code smooth}, which is the call that loads).
 *
 * <p><b>Not while Choo Choo is installed.</b> On a smoothed run Slash Rails moves every minecart itself and skips the
 * cart's own {@code moveAlongTrack}, which is where a Choo Choo locomotive puts down its power: measured on 1.21.1, a
 * running locomotive stood still on smoothed track and drove round the same bend unsmoothed (with Slash Rails
 * installed and without). Choo Choo's trains are the ones CityWorld parks in its stations, so with both mods the bends
 * are left as staircases, which every cart rides.
 */
public final class RailSmoothing {

    private RailSmoothing() {
    }

    private record Pending(ServerLevel level, BlockPos rail, int due, int tries) {
    }

    private static final ConcurrentLinkedQueue<Pending> PENDING = new ConcurrentLinkedQueue<>();

    /** The parts of Slash Rails' {@code RunService.smooth}, found once; {@link #MISSING} when any is not there. */
    private record Api(Method registryGet, Method contains, Method detect, java.lang.reflect.Field maxRunLength,
            Method ok, Method nodes, Method closed, Method clearance, Method add, Method curve,
            Constructor<?> payload, Method platformGet, Method clientHasMod, Method sendRuns) {
    }

    private static final Api MISSING = new Api(null, null, null, null, null, null, null, null, null, null, null, null,
            null, null);
    private static volatile Api api;

    private static Api api() {
        Api a = api;
        if (a == null) {
            try {
                Class<?> registry = Class.forName("com.slashrails.run.SmoothRunRegistry");
                Class<?> detector = Class.forName("com.slashrails.run.RunDetector");
                Class<?> result = Class.forName("com.slashrails.run.RunDetector$Result");
                Class<?> run = Class.forName("com.slashrails.run.SmoothRun");
                Class<?> payload = Class.forName("com.slashrails.net.RunsPayload");
                Class<?> platform = Class.forName("com.slashrails.platform.Platform");
                a = new Api(registry.getMethod("get", ServerLevel.class), registry.getMethod("contains", BlockPos.class),
                        detector.getMethod("detect", net.minecraft.world.level.Level.class, BlockPos.class, int.class,
                                Predicate.class),
                        Class.forName("com.slashrails.Config").getField("maxRunLength"),
                        result.getMethod("ok"), result.getMethod("nodes"), result.getMethod("closed"),
                        Class.forName("com.slashrails.run.Clearance").getMethod("compute",
                                net.minecraft.world.level.Level.class, List.class, boolean.class),
                        registry.getMethod("add", List.class, boolean.class, boolean[].class), run.getMethod("curve"),
                        payload.getConstructor(boolean.class, List.class), platform.getMethod("get"),
                        platform.getMethod("clientHasMod", ServerPlayer.class),
                        platform.getMethod("sendRuns", ServerPlayer.class, payload));
            } catch (ReflectiveOperationException | LinkageError e) {
                if (net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("slashrails"))
                    CityWorldMod.LOGGER.info("CityWorld: this Slash Rails is not the one CityWorld knows ({}), so subway"
                            + " bends are left for its Track Smoother", e.toString());
                a = MISSING;
            }
            api = a;
        }
        return a == MISSING ? null : a;
    }

    /** Whether Slash Rails is there to ask. */
    public static boolean available() {
        return api() != null;
    }

    private static volatile Boolean chooChoo;

    /** Whether a mod whose trains cannot run on smoothed track is installed (see the class comment). */
    public static boolean heldBack() {
        Boolean c = chooChoo;
        if (c == null) {
            c = net.minecraftforge.fml.ModList.get() != null && net.minecraftforge.fml.ModList.get().isLoaded("choochoo");
            chooChoo = c;
            if (c && available())
                CityWorldMod.LOGGER.info("CityWorld: Choo Choo is installed, so subway bends are not smoothed with Slash Rails"
                        + " (a Choo Choo locomotive cannot drive on smoothed track)");
        }
        return c;
    }

    /**
     * From the chunk-load event, for a chunk just generated: queue its own bends, and the bends of its four
     * neighbours that are in the world but not yet smoothed. The mod smooths only what is loaded, and turns down a
     * run that is too short ("Too few connected rails"), so a bend generated before the track either side of it was
     * smoothed by nothing until the chunk beside it arrives; that arrival is its second chance.
     */
    public static void generated(ServerLevel level, int chunkX, int chunkZ) {
        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator g))
            return;
        CityWorldGenerator plan = g.getContext(level);
        if (plan == null || !plan.getSettings().smoothBends || !Subway.planned(plan) || !available() || heldBack())
            return;
        int due = level.getServer().getTickCount() + 20;
        queueChunk(level, plan, chunkX, chunkZ, due, false);
        queueChunk(level, plan, chunkX + 1, chunkZ, due, true);
        queueChunk(level, plan, chunkX - 1, chunkZ, due, true);
        queueChunk(level, plan, chunkX, chunkZ + 1, due, true);
        queueChunk(level, plan, chunkX, chunkZ - 1, due, true);
    }

    private static void queueChunk(ServerLevel level, CityWorldGenerator plan, int chunkX, int chunkZ, int due,
            boolean neighbour) {
        if (neighbour && !level.hasChunk(chunkX, chunkZ))
            return; // it will queue itself when it generates
        Subway.Piece piece = Subway.at(plan, chunkX, chunkZ);
        if (!piece.any() || piece.station() || piece.ramp() != null)
            return;
        queue(level, chunkX, chunkZ, piece.ewMask(), piece.ewPlatform(), Subway.ewFloor(plan) + 1, due, neighbour);
        queue(level, chunkX, chunkZ, piece.nsMask(), piece.nsPlatform(), Subway.nsFloor(plan) + 1, due, neighbour);
    }

    private static void queue(ServerLevel level, int chunkX, int chunkZ, int mask, boolean platform, int railY, int due,
            boolean unlessSmoothed) {
        if (platform || !Subway.isBend(mask))
            return;
        for (int line : new int[] { 6, 9 }) {
            var cells = Subway.arc(mask, line);
            int[] mid = cells.get(cells.size() / 2); // any rail of the curve would do; the middle is furthest from a seam
            BlockPos rail = new BlockPos(chunkX * 16 + mid[0], railY, chunkZ * 16 + mid[1]);
            if (unlessSmoothed && smoothed(level, rail))
                continue;
            PENDING.add(new Pending(level, rail, due, 10));
        }
    }

    private static volatile Method[] registry; // SmoothRunRegistry.get(ServerLevel), contains(BlockPos); empty when absent

    /** Whether the rail at {@code rail} already belongs to one of the mod's smoothed runs. */
    static boolean smoothed(ServerLevel level, BlockPos rail) {
        Method[] r = registry;
        if (r == null) {
            try {
                Class<?> c = Class.forName("com.slashrails.run.SmoothRunRegistry");
                r = new Method[] { c.getMethod("get", ServerLevel.class), c.getMethod("contains", BlockPos.class) };
            } catch (ReflectiveOperationException | LinkageError e) {
                r = new Method[0];
            }
            registry = r;
        }
        if (r.length == 0)
            return false;
        try {
            return (Boolean) r[1].invoke(r[0].invoke(null, level), rail);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /** From the server tick: smooth what is due. */
    public static void tick(MinecraftServer server) {
        if (!PENDING.isEmpty())
            drain(server.getTickCount(), false);
    }

    /** Smooth everything waiting now, due or not (a headless check, whose server may not be ticking). */
    public static void drainNow(MinecraftServer server) {
        drain(server.getTickCount(), true);
    }

    private static void drain(int now, boolean all) {
        Api m = api();
        for (int i = PENDING.size(); i > 0; i--) {
            Pending p = PENDING.poll();
            if (p == null)
                break;
            if (!all && now < p.due()) {
                PENDING.add(p);
                continue;
            }
            if (m == null)
                continue;
            if (!aroundLoaded(p.level(), p.rail().getX() >> 4, p.rail().getZ() >> 4)) {
                if (p.tries() > 0)
                    PENDING.add(new Pending(p.level(), p.rail(), now + 20, p.tries() - 1));
                continue;
            }
            BlockPos rail = findRail(p.level(), p.rail());
            if (rail == null)
                continue; // a ruin's gap: nothing to start from here
            if (smoothed(p.level(), rail))
                continue; // a neighbour's run took it already
            try {
                smooth(m, p.level(), rail);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                Throwable why = e instanceof InvocationTargetException t && t.getCause() != null ? t.getCause() : e;
                CityWorldMod.LOGGER.warn("CityWorld: Slash Rails would not smooth the subway bend at {}: {}", rail, why.toString());
            }
        }
    }

    /** The bend's chunk and the four beside it are loaded (all its curve's rails, and one block round each). */
    private static boolean aroundLoaded(ServerLevel level, int chunkX, int chunkZ) {
        var chunks = level.getChunkSource();
        return chunks.getChunkNow(chunkX, chunkZ) != null && chunks.getChunkNow(chunkX + 1, chunkZ) != null
                && chunks.getChunkNow(chunkX - 1, chunkZ) != null && chunks.getChunkNow(chunkX, chunkZ + 1) != null
                && chunks.getChunkNow(chunkX, chunkZ - 1) != null;
    }

    /** Every chunk the blocks one round {@code at} lie in is loaded (what the mod's clearance check reads). */
    private static boolean surroundingsLoaded(ServerLevel level, BlockPos at) {
        var chunks = level.getChunkSource();
        for (int cx = (at.getX() - 1) >> 4; cx <= (at.getX() + 1) >> 4; cx++)
            for (int cz = (at.getZ() - 1) >> 4; cz <= (at.getZ() + 1) >> 4; cz++)
                if (chunks.getChunkNow(cx, cz) == null)
                    return false;
        return true;
    }

    /**
     * {@code RunService.smooth}, step by step, except that the run also stops before a rail whose surroundings are
     * not all loaded (see the class comment): so the clearance check reads only loaded blocks.
     */
    private static void smooth(Api m, ServerLevel level, BlockPos rail) throws ReflectiveOperationException {
        Object registry = m.registryGet().invoke(null, level);
        Predicate<BlockPos> stop = at -> {
            if (!surroundingsLoaded(level, at))
                return true;
            try {
                return (Boolean) m.contains().invoke(registry, at);
            } catch (ReflectiveOperationException e) {
                return true;
            }
        };
        Object result = m.detect().invoke(null, level, rail, m.maxRunLength().getInt(null), stop);
        if (!(Boolean) m.ok().invoke(result))
            return; // too short, sloped, nothing to smooth: as the mod would say to a player
        List<?> nodes = (List<?>) m.nodes().invoke(result);
        boolean closed = (Boolean) m.closed().invoke(result);
        boolean[] clear = (boolean[]) m.clearance().invoke(null, level, nodes, closed);
        Object run = m.add().invoke(registry, nodes, closed, clear);
        m.curve().invoke(run); // the mod fits the curve here, and says so if it cannot
        Object payload = m.payload().newInstance(false, List.of(run));
        Object platform = m.platformGet().invoke(null);
        for (ServerPlayer player : level.players())
            if ((Boolean) m.clientHasMod().invoke(platform, player))
                m.sendRuns().invoke(platform, player, payload);
    }

    /** The rail at {@code at}, or a rail beside it (a ruined curve may have lost the middle one). */
    private static BlockPos findRail(ServerLevel level, BlockPos at) {
        if (level.getBlockState(at).getBlock() instanceof BaseRailBlock)
            return at;
        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                BlockPos p = at.offset(dx, 0, dz);
                if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof BaseRailBlock)
                    return p;
            }
        return null;
    }

    /** A server stopped: nothing waiting outlives it. */
    public static void clear() {
        PENDING.clear();
    }
}
