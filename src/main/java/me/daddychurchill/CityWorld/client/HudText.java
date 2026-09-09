package me.daddychurchill.CityWorld.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Draws one line of text on a screen.
 *
 * <p>Its only reason to exist is that Minecraft renamed both halves of this call in 26.1:
 * {@code GuiGraphics} became {@code GuiGraphicsExtractor}, and its {@code drawString} became
 * {@code text}. Keeping the call here means <b>this is the only client file that differs between
 * version branches</b> — {@link CityPlanHud} names neither, so it cherry-picks across cleanly. Same
 * arrangement as {@code OverlayProps} on the JourneyMap side; when Minecraft renames the next thing,
 * it comes here too.
 */
final class HudText {

    private HudText() {}

    static void draw(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int colour) {
        graphics.text(font, text, x, y, colour);
    }
}
