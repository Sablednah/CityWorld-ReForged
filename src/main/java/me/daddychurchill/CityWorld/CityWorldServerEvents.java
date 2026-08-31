package me.daddychurchill.CityWorld;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
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
}
