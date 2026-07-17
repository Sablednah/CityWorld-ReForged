package me.daddychurchill.CityWorld;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * Server-side registrations on the NeoForge game event bus. Registered from {@link CityWorldMod}.
 */
public final class CityWorldServerEvents {

    private CityWorldServerEvents() {}

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CityWorldCommands.register(event.getDispatcher());
    }
}
