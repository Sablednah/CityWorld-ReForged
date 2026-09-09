package me.daddychurchill.CityWorld.client;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Draws "what is planned here" over a map mod's fullscreen map, next to the cursor.
 *
 * <p>Why CityWorld draws this itself rather than handing the text to JourneyMap: JourneyMap's info
 * slots belong to the minimap (and only appear if the player picks them in its settings), its
 * fullscreen block-info bar is read-only to addons, and its own render event hands out a
 * {@code GuiGraphicsExtractor} that exists only as a runtime mixin — nothing an addon can compile
 * against. Drawing on the screen with NeoForge's own event needs none of that, and works the same
 * for any map mod that tells us which chunk the mouse is over.
 */
public final class CityPlanHud {

    private CityPlanHud() {}

    /** No chunk hovered. */
    private static final long NONE = Long.MIN_VALUE;

    /**
     * The chunk last reported under the cursor. Deliberately <b>not</b> expiring on a timer: a map
     * mod only reports the position while the mouse is <em>moving</em>, so a timeout made the caption
     * disappear a second after the player stopped to read it. It is cleared when the map closes.
     */
    private static volatile long hovered = NONE;

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ScreenEvent.Render.Post.class, CityPlanHud::onRenderScreen);
    }

    /** A map mod telling us the mouse is over this chunk. */
    public static void hover(int chunkX, int chunkZ) {
        hovered = (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    public static void clearHover() {
        hovered = NONE;
    }

    /**
     * Only draws over a map mod's screen, and only when that mod has just told us where the mouse is
     * — so this is inert in every other screen in the game, including when no map mod is installed.
     */
    private static void onRenderScreen(ScreenEvent.Render.Post event) {
        try {
            long at = hovered;
            if (at == NONE)
                return;
            Screen screen = event.getScreen();
            if (screen == null || !isMapScreen(screen))
                return;

            int chunkX = (int) at;
            int chunkZ = (int) (at >> 32);
            CityPlanClient.request(chunkX, chunkZ);
            String info = CityPlanClient.infoFor(chunkX, chunkZ);
            if (info == null || info.isEmpty())
                return;

            Minecraft minecraft = Minecraft.getInstance();
            Font font = minecraft.font;
            GuiGraphics graphics = event.getGuiGraphics();
            Component text = Component.literal(info);
            int width = font.width(text);

            // Below-right of the cursor, the way a tooltip sits — nudged back inside the screen when
            // the cursor is near an edge, so the text never runs off.
            int x = Math.max(4, Math.min(event.getMouseX() + 10, screen.width - width - 4));
            int y = Math.max(4, Math.min(event.getMouseY() + 20, screen.height - font.lineHeight - 4));

            // A wash rather than a panel: this sits on top of a map the player is trying to read, so
            // the ground beneath it should still show through.
            graphics.fill(x - 3, y - 3, x + width + 3, y + font.lineHeight + 2, 0x70000000);
            graphics.drawString(font, text, x, y, 0xFFFFFFFF);
        } catch (Throwable t) {
            // Never take a screen down over a caption.
            CityWorldMod.LOGGER.debug("CityWorld hover text failed", t);
            hovered = NONE;
        }
    }

    /**
     * Is this a map mod's screen? Matched on the class's own package rather than a hard reference,
     * so this file stays free of any map mod's types and works for the next one too.
     */
    private static boolean isMapScreen(Screen screen) {
        String owner = screen.getClass().getName().toLowerCase(java.util.Locale.ROOT);
        return owner.contains("journeymap") || owner.contains("xaero") || owner.contains("ftbchunks");
    }
}
