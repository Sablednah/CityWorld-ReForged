package me.daddychurchill.CityWorld.Support;

import java.lang.reflect.Method;
import java.util.concurrent.ConcurrentLinkedQueue;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.BaseRailBlock;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

/**
 * Smooths the subway's bends with Slash Rails, as a player with its Track Smoother would (GitHub issue #7; see
 * TRAINS-PLAN.md). Slash Rails only rounds a run when a player asks it to, and keeps the runs it has rounded in the
 * world's saved data, so this asks once per bend: when a bend chunk first comes into the world (a chunk just
 * generated, never one loaded from disk) its two tracks are queued, and a second later, once the chunk is in the world
 * proper, each is handed to the mod's own {@code RunService.smooth(ServerLevel, BlockPos)} — by reflection, so nothing
 * here loads without the mod, and a mod that renames it is simply not called.
 *
 * <p>The mod walks the whole flat run from the rail it is given, until a dead end, a slope, a rail of another run, an
 * unloaded chunk or 512 rails; so a bend's run is the bend plus whatever loaded straight lies either side, and the next
 * bend's run stops where this one's began. Off with {@code subways.smoothBends}.
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

    private static volatile Method smooth; // RunService.smooth(ServerLevel, BlockPos), or NONE
    private static final Method NONE;

    static {
        try {
            NONE = Object.class.getMethod("hashCode");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    private static Method smoother() {
        Method m = smooth;
        if (m == null) {
            try {
                m = Class.forName("com.slashrails.run.RunService").getMethod("smooth", ServerLevel.class, BlockPos.class);
            } catch (ReflectiveOperationException | LinkageError e) {
                m = NONE;
            }
            smooth = m;
        }
        return m == NONE ? null : m;
    }

    /** Whether Slash Rails is there to ask. */
    public static boolean available() {
        return smoother() != null;
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
        Method m = smoother();
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
            if (!p.level().isLoaded(p.rail())) {
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
                m.invoke(null, p.level(), rail);
            } catch (ReflectiveOperationException | RuntimeException | LinkageError e) {
                CityWorldMod.LOGGER.warn("CityWorld: Slash Rails would not smooth the subway bend at {}: {}", rail, e.toString());
            }
        }
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
