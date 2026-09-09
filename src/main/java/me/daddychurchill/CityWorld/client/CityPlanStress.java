package me.daddychurchill.CityWorld.client;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Frame-rate readout for measuring what map overlays cost the client:
 * {@code -Dcityworld.mapstress=true}.
 *
 * <p>The city plan's only real cost is client-side — the server never re-plans or re-sends a
 * retained overlay, while the client walks its list of them every frame. That is exactly the sort of
 * claim this project has been wrong about before, so rather than reasoning about it, this logs the
 * frame rate every five seconds while the plan streams in. Paired with the server's own count of
 * overlays pushed, it gives a before-and-after in a single run.
 */
public final class CityPlanStress {

    private CityPlanStress() {}

    private static final String ENABLE_PROPERTY = "cityworld.mapstress";

    /** Five seconds at 20 ticks. */
    private static final int INTERVAL = 100;

    private static int ticks;

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY);
    }

    public static void register() {
        if (!enabled())
            return;
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, CityPlanStress::onTick);
        CityWorldMod.LOGGER.info("CityWorld: -D{} on — logging the frame rate every {} ticks",
                ENABLE_PROPERTY, INTERVAL);
    }

    private static void onTick(ClientTickEvent.Post event) {
        if (++ticks < INTERVAL)
            return;
        ticks = 0;
        Minecraft minecraft = Minecraft.getInstance();
        CityWorldMod.LOGGER.info("CityWorld stress: {} fps, frame {} ms, screen {}",
                minecraft.getFps(), minecraft.getFrameTimeNs() / 1_000_000.0,
                minecraft.screen == null ? "none" : minecraft.screen.getClass().getSimpleName());
    }
}
