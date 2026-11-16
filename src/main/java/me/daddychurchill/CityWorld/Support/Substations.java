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
import net.minecraftforge.energy.IEnergyStorage;

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
 * On Forge 1.20.1 Railcraft offers no block capability for its batteries, so they are reached through its own
 * charge API instead ({@code Charge.distribution.network(level).access(pos).storage()}, by reflection), which hands
 * back the same battery as Forge's {@code IEnergyStorage}.
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
            LOADED.computeIfAbsent(level, l -> new ConcurrentHashMap<>()).put(chunk.getPos().toLong(), found);
    }

    /** From the chunk-unload event. */
    public static void unloaded(ServerLevel level, ChunkAccess chunk) {
        Map<Long, List<BlockPos>> m = LOADED.get(level);
        if (m != null)
            m.remove(chunk.getPos().toLong());
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
        IEnergyStorage storage = storage(level, pos);
        return storage == null || !storage.canReceive() ? 0 : storage.receiveEnergy(charge, false);
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
        IEnergyStorage storage = storage(level, pos);
        return storage == null ? -1 : storage.getEnergyStored();
    }

    private static volatile java.lang.reflect.Method[] api; // Charge.network(ServerLevel), Network.access(BlockPos), Access.storage(); empty without Railcraft
    private static volatile Object distribution;

    /** The battery at {@code pos} through Railcraft's charge API, or null (no Railcraft, or no battery there). */
    private static IEnergyStorage storage(ServerLevel level, BlockPos pos) {
        java.lang.reflect.Method[] m = api;
        if (m == null) {
            try {
                Class<?> charge = Class.forName("mods.railcraft.api.charge.Charge");
                distribution = charge.getField("distribution").get(null);
                m = new java.lang.reflect.Method[] { charge.getMethod("network", ServerLevel.class),
                        Class.forName("mods.railcraft.api.charge.Charge$Network").getMethod("access", BlockPos.class),
                        Class.forName("mods.railcraft.api.charge.Charge$Access").getMethod("storage") };
            } catch (ReflectiveOperationException | LinkageError e) {
                m = new java.lang.reflect.Method[0];
            }
            api = m;
        }
        if (m.length == 0)
            return null;
        try {
            Object network = m[0].invoke(distribution, level);
            Object access = m[1].invoke(network, pos);
            Object storage = ((java.util.Optional<?>) m[2].invoke(access)).orElse(null);
            return storage instanceof IEnergyStorage s ? s : null;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return null;
        }
    }

    public static void clear() {
        LOADED.clear();
    }
}
