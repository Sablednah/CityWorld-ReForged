package me.daddychurchill.CityWorld.client;

import java.util.Locale;
import java.util.function.Consumer;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The "Customize" screen for the CityWorld world type on the single-player create-world screen — the
 * modded counterpart of vanilla's Superflat/Single-Biome editors, reached via
 * {@link net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent} (see {@link CityWorldClient}).
 *
 * <p>For now it offers just the world-style picker; it is the intended home of the wider per-world
 * settings UI when those land (PORTING.md P7). On <em>Done</em> it hands the chosen style back to the
 * caller, which rewrites the create-world screen's overworld generator to carry it.
 */
public class CityWorldCustomizeScreen extends Screen {

    private static final Component TITLE = Component.translatable("cityworld.customize.title");

    private final Screen parent;
    private final Consumer<WorldStyle> onDone;
    private final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
    private WorldStyle style;

    public CityWorldCustomizeScreen(Screen parent, WorldStyle initial, Consumer<WorldStyle> onDone) {
        super(TITLE);
        this.parent = parent;
        this.style = initial;
        this.onDone = onDone;
    }

    /** A human-readable label for a style, e.g. {@code cityworld.style.flooded} → "Flooded". */
    private static Component label(WorldStyle s) {
        return Component.translatable("cityworld.style." + s.name().toLowerCase(Locale.ROOT));
    }

    @Override
    protected void init() {
        this.layout.addTitleHeader(this.title, this.font);

        LinearLayout body = this.layout.addToContents(LinearLayout.vertical().spacing(8));
        body.addChild(
                CycleButton.<WorldStyle>builder(CityWorldCustomizeScreen::label, this.style)
                        .withValues(WorldStyle.values())
                        .create(0, 0, 250, 20, Component.translatable("cityworld.customize.style"),
                                (button, value) -> this.style = value));

        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.onDone.accept(this.style);
            this.onClose();
        }).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).build());

        this.layout.visitWidgets(this::addRenderableWidget);
        this.repositionElements();
    }

    @Override
    protected void repositionElements() {
        this.layout.arrangeElements();
    }

    @Override
    public void onClose() {
        this.minecraft.setScreen(this.parent);
    }
}
