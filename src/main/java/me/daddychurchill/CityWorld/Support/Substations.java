package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.transfer.energy.EnergyHandler;
import net.neoforged.neoforge.transfer.transaction.Transaction;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

/**
 * The city's supply to its electric subway (Railcraft Reborn; see {@link Railway} and TRAINS-PLAN.md): every station
 * hall on an electric line has a battery ({@code #cityworld:fittings/rail_battery}) at the foot of its middle pillars,
 * one against each track, and while a station's chunk is loaded the city tops its batteries up by
 * {@code subways.stationCharge} a tick. A battery joins the charge network of the track it touches, so a locomotive
 * on the line draws on it. The batteries are found when their chunk loads, by reading the three columns the middle
 * pillars stand in, and fed through the standard energy capability — any battery a pack puts in the pool is charged
 * the same way, and Railcraft's own network registers a battery the first time that capability is asked for it.
 * A player who mines one simply stops it being fed; nothing is put back.
 */
public final class Substations {

    private Substations() {
    }

    /** Per level, per loaded station chunk (its packed position), the batteries in it. */
    private static final Map<ServerLevel, Map<Long, List<BlockPos>>> LOADED = new ConcurrentHashMap<>();

    /** How often the batteries are fed, in ticks; each is given that many ticks' charge at once. */
    static final int INTERVAL = 20;

    /** The columns, in chunk coordinates, the middle pillars of a station hall stand in (either axis). */
    private static final int[][] COLUMNS = { { 7, 7 }, { 7, 8 }, { 8, 7 } };

    private static CityWorldGenerator plan(ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator g))
            return null;
        CityWorldGenerator plan = g.getContext(level);
        if (plan == null || plan.getSettings().stationCharge <= 0 || !Railway.electricLine(plan))
            return null;
        return plan;
    }

    /** From the chunk-load event (any chunk, new or read from disk). */
    public static void loaded(ServerLevel level, ChunkAccess chunk) {
        if (plan(level) == null)
            return;
        List<BlockPos> found = null;
        LevelChunkSection[] sections = chunk.getSections();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int i = 0; i < sections.length; i++) {
            LevelChunkSection section = sections[i];
            if (section == null || section.hasOnlyAir())
                continue;
            int y0 = chunk.getSectionYFromSectionIndex(i) << 4;
            for (int[] c : COLUMNS)
                for (int dy = 0; dy < 16; dy++)
                    if (section.getBlockState(c[0], dy, c[1]).is(MaterialTags.FITTINGS_RAIL_BATTERY)) {
                        if (found == null)
                            found = new ArrayList<>(4);
                        found.add(new BlockPos(x0 + c[0], y0 + dy, z0 + c[1]));
                    }
        }
        if (found != null)
            LOADED.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).put(chunk.getPos().pack(), found);
    }

    /** From the chunk-unload event. */
    public static void unloaded(ServerLevel level, ChunkAccess chunk) {
        Map<Long, List<BlockPos>> m = LOADED.get(level);
        if (m != null)
            m.remove(chunk.getPos().pack());
    }

    /** From the server tick: every {@link #INTERVAL} ticks, each loaded station's batteries are topped up. */
    public static void tick(MinecraftServer server) {
        if (LOADED.isEmpty() || server.getTickCount() % INTERVAL != 0)
            return;
        for (Map.Entry<ServerLevel, Map<Long, List<BlockPos>>> e : LOADED.entrySet()) {
            ServerLevel level = e.getKey();
            CityWorldGenerator plan = plan(level);
            if (plan == null)
                continue;
            int charge = plan.getSettings().stationCharge * INTERVAL;
            for (List<BlockPos> batteries : e.getValue().values())
                for (BlockPos pos : batteries)
                    feed(level, pos, charge);
        }
    }

    /** Gives the battery at {@code pos} up to {@code charge}; the amount it took (0 when it is gone or full). */
    public static int feed(ServerLevel level, BlockPos pos, int charge) {
        if (!level.isLoaded(pos) || !level.getBlockState(pos).is(MaterialTags.FITTINGS_RAIL_BATTERY))
            return 0;
        EnergyHandler storage = level.getCapability(Capabilities.Energy.BLOCK, pos, null);
        if (storage == null)
            return 0;
        try (Transaction tx = Transaction.openRoot()) {
            int took = storage.insert(charge, tx);
            tx.commit();
            return took;
        }
    }

    /** The batteries known in {@code level}'s loaded chunks (the self-test's view). */
    public static List<BlockPos> known(ServerLevel level) {
        List<BlockPos> out = new ArrayList<>();
        Map<Long, List<BlockPos>> m = LOADED.get(level);
        if (m != null)
            m.values().forEach(out::addAll);
        return out;
    }

    /** The charge stored in the battery at {@code pos}, or -1 when there is none to ask. */
    public static int stored(ServerLevel level, BlockPos pos) {
        EnergyHandler storage = level.getCapability(Capabilities.Energy.BLOCK, pos, null);
        return storage == null ? -1 : storage.getAmountAsInt();
    }

    public static void clear() {
        LOADED.clear();
    }
}
