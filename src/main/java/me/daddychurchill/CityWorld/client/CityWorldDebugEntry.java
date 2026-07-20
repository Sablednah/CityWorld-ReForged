package me.daddychurchill.CityWorld.client;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.ClipboardLot;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jspecify.annotations.Nullable;

/**
 * A CityWorld F3 debug-screen entry — the {@code /cityinfo} readout, live under the crosshair, plus a
 * few technical datums. Registered via {@code RegisterDebugEntriesEvent} (see {@link CityWorldClient}).
 *
 * <p>The plan (context / lot / nature) is computed server-side, so this only fills in when the client
 * is the host of a single-player world (via {@link Minecraft#getSingleplayerServer()}); on a remote
 * server it just says so. Everything is wrapped defensively — a debug overlay must never take the game
 * down — and reads only thread-safe, already-planned state (the platmap the player stands in is
 * planned by the time its chunk exists).
 */
public class CityWorldDebugEntry implements DebugScreenEntry {

    @Override
    public void display(DebugScreenDisplayer displayer, @Nullable Level serverLevel, @Nullable LevelChunk clientChunk,
            @Nullable LevelChunk serverChunkArg) {
        try {
            Minecraft mc = Minecraft.getInstance();
            Entity camera = mc.getCameraEntity();
            if (camera == null || mc.level == null)
                return;

            IntegratedServer server = mc.getSingleplayerServer();
            if (server == null)
                return; // remote server: the plan lives there, not here

            ServerLevel level = server.getLevel(mc.level.dimension());
            if (level == null || !(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator cw))
                return; // not a CityWorld dimension — stay silent

            BlockPos pos = camera.blockPosition();
            ChunkPos cp = new ChunkPos(pos);
            CityWorldGenerator context = cw.getContext(level);
            PlatMap platmap = context.getPlatMap(cp.x, cp.z);
            PlatLot lot = platmap.getMapLot(cp.x, cp.z);
            DataContext data = platmap.context;

            displayer.addLine(String.format("[CityWorld] %s  %s (%s)  nature %.0f%%",
                    cw.resolvedStyle(), lot.getClass().getSimpleName(), lot.style,
                    platmap.getNaturePercent() * 100.0));
            displayer.addLine(String.format("[CityWorld] context %s (%s)",
                    data.getClass().getSimpleName(), data.getSchematicFamily()));
            if (lot instanceof ClipboardLot clip)
                displayer.addLine("[CityWorld] schematic " + clip.getClip().name + " [" + clip.getClip().family + "]");
            displayer.addLine(String.format("[CityWorld] street %d  sea %d  tree %d  evergreen %d  snow %d  maxFloors %d",
                    context.streetLevel, context.seaLevel, context.treeLevel, context.evergreenLevel,
                    context.snowLevel, context.getSettings().maxBuildingFloors));
        } catch (Throwable t) {
            // Never let the debug overlay crash the client; show that something went wrong instead.
            displayer.addLine("[CityWorld] (info unavailable)");
        }
    }
}
