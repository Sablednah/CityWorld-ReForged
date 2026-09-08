package me.daddychurchill.CityWorld;

import me.daddychurchill.CityWorld.api.MapMarkers;

import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.server.permission.events.PermissionGatherEvent;

/**
 * Server-side registrations on the NeoForge game event bus. Registered from {@link CityWorldMod}.
 */
public final class CityWorldServerEvents {

    private CityWorldServerEvents() {}

    /**
     * Declare CityWorld's permission nodes. Must happen here rather than at command registration:
     * NeoForge gathers nodes once, before a permissions handler is chosen.
     */
    @SubscribeEvent
    public static void onGatherPermissionNodes(PermissionGatherEvent.Nodes event) {
        CityWorldPermissions.onGatherNodes(event);
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CityWorldCommands.register(event.getDispatcher());
    }

    /** {@code -Dcityworld.maptest=true}: send one synthetic landmark to the map at startup. */
    private static final String MAPTEST_PROPERTY = "cityworld.maptest";

    /**
     * Reports whether a map mod has hooked {@link MapMarkers}, and — under {@code -Dcityworld.maptest}
     * — drops a test landmark at the world origin.
     *
     * <p>Both exist because map integration is otherwise only observable by exploring until a rare
     * landmark generates, which is exactly the "looks like working software while doing nothing"
     * shape that has cost this project playtest rounds before. This makes "is the hook wired up?" a
     * question a headless server answers in one run.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        if (!MapMarkers.hasListeners())
            return;
        CityWorldMod.LOGGER.info("CityWorld: map integration active — landmarks will be sent to the map");
        if (!Boolean.getBoolean(MAPTEST_PROPERTY))
            return;
        MinecraftServer server = event.getServer();
        // The origin, not the world spawn: a fixed point needs no level data and reads the same on
        // every Minecraft version this mod builds against.
        MapMarkers.landmark(new MapMarkers.Landmark(server.overworld().dimension(), "schematic",
                "CityWorld map test", 0, 64, 0));
        CityWorldMod.LOGGER.info("CityWorld: -D{} sent a test landmark at 0, 0", MAPTEST_PROPERTY);
    }
}
