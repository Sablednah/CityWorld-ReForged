package me.daddychurchill.CityWorld.client;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.api.LotInfo;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.client.gui.components.debug.DebugScreenEntry;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
            CityWorldGenerator context = cw.getContext(level);

            // The lot/context/shop/schematic readout comes from the shared introspection API, so F3 and
            // /cityinfo never disagree; the level datums below still come straight off the generator.
            LotInfo info = CityWorldAPI.lotAt(level, pos).orElse(null);
            if (info != null) {
                displayer.addLine(String.format("[CityWorld] %s  %s (%s)  nature %.0f%%",
                        cw.resolvedStyle(), info.lotClass(), info.lotStyle(), info.naturePercent() * 100.0));
                displayer.addLine(String.format("[CityWorld] context %s (%s)",
                        info.contextClass(), info.contextFamily()));
                if (info.shop() != null)
                    displayer.addLine("[CityWorld] shop " + info.shop().describe());
                if (info.schematicName() != null)
                    displayer.addLine("[CityWorld] schematic " + info.schematicName());
            }
            // Two lines, not one: the single combined line ran past the width of the F3 overlay.
            displayer.addLine(String.format("[CityWorld] street %d  sea %d  maxFloors %d",
                    context.streetLevel, context.seaLevel, context.getSettings().maxBuildingFloors));
            displayer.addLine(String.format("[CityWorld] tree %d  evergreen %d  snow %d",
                    context.treeLevel, context.evergreenLevel, context.snowLevel));
        } catch (Throwable t) {
            // Never let the debug overlay crash the client; show that something went wrong instead.
            displayer.addLine("[CityWorld] (info unavailable)");
        }
    }
}
