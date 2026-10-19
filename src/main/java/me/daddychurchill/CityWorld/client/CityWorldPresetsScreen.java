package me.daddychurchill.CityWorld.client;

import java.io.IOException;
import java.util.List;
import java.util.function.Function;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.StringWidget;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Presets page of the Customize screen: save what the screen holds under a name, and load or delete what was
 * saved before ({@link CityWorldPresets}). Like the Structures page it never returns to the screen that opened it:
 * {@code open} builds a fresh Customize screen around a preset, or around what it was opened with on Back.
 */
public class CityWorldPresetsScreen extends OptionsSubScreen {

    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    private final CityWorldCustomizeScreen.Result current;
    private final Function<CityWorldCustomizeScreen.Result, Screen> open;
    private String typed;

    public CityWorldPresetsScreen(Screen parent, CityWorldCustomizeScreen.Result current,
            Function<CityWorldCustomizeScreen.Result, Screen> open) {
        this(parent, current, open, "");
    }

    private CityWorldPresetsScreen(Screen parent, CityWorldCustomizeScreen.Result current,
            Function<CityWorldCustomizeScreen.Result, Screen> open, String typed) {
        super(parent, Minecraft.getInstance().options, Component.literal("CityWorld Presets"));
        this.current = current;
        this.open = open;
        this.typed = typed;
    }

    @Override
    protected void addOptions() {
        this.list.addHeader(Component.literal("Save these settings"));
        EditBox name = new EditBox(this.font, 0, 0, WIDTH, HEIGHT, Component.literal("Preset name"));
        name.setMaxLength(64);
        name.setHint(Component.literal("Preset name"));
        name.setValue(typed);
        Button save = Button.builder(Component.literal("Save"), b -> save(name.getValue().trim())).width(WIDTH).build();
        save.active = !typed.isBlank();
        save.setTooltip(Tooltip.create(Component.literal(
                "Saved in config/cityworld/presets: the style, the realms and every setting, structures included.")));
        name.setResponder(text -> {
            typed = text;
            save.active = !text.isBlank();
        });
        this.list.addSmall(name, save);

        this.list.addHeader(Component.literal("Saved presets"));
        List<CityWorldPresets.Preset> presets = CityWorldPresets.list();
        if (presets.isEmpty())
            this.list.addSmall(new StringWidget(WIDTH * 2 + 10, HEIGHT, Component.literal("None saved yet"), this.font),
                    null);
        for (CityWorldPresets.Preset preset : presets) {
            Button load = Button.builder(Component.literal(preset.name()), b -> this.minecraft.setScreen(
                    open.apply(preset.result()))).width(WIDTH).build();
            load.setTooltip(Tooltip.create(Component.literal("Load: style "
                    + Component.translatable("cityworld.style." + preset.result().style().name()
                            .toLowerCase(java.util.Locale.ROOT)).getString()
                    + "\n" + preset.file().getFileName())));
            Button delete = Button.builder(Component.literal("Delete"), b -> confirm(
                    Component.literal("Delete the preset \"" + preset.name() + "\"?"), () -> {
                        try {
                            CityWorldPresets.delete(preset);
                        } catch (IOException e) {
                            CityWorldMod.LOGGER.warn("CityWorld presets: could not delete {}: {}", preset.file(),
                                    e.toString());
                        }
                    })).width(WIDTH).build();
            this.list.addSmall(load, delete);
        }
    }

    private void save(String name) {
        if (name.isEmpty())
            return;
        Runnable write = () -> {
            try {
                CityWorldPresets.save(name, current);
                typed = "";
            } catch (IOException e) {
                CityWorldMod.LOGGER.warn("CityWorld presets: could not save \"{}\": {}", name, e.toString());
            }
        };
        if (CityWorldPresets.exists(name))
            confirm(Component.literal("Replace the preset saved as \"" + name + "\"?"), write);
        else {
            write.run();
            this.minecraft.setScreen(fresh());
        }
    }

    /** Ask first; either way come back to a fresh page, which lists the folder as it now stands. */
    private void confirm(Component question, Runnable yes) {
        this.minecraft.setScreen(new ConfirmScreen(sure -> {
            if (sure)
                yes.run();
            this.minecraft.setScreen(fresh());
        }, Component.literal("CityWorld Presets"), question));
    }

    private Screen fresh() {
        return new CityWorldPresetsScreen(this.lastScreen, current, open, typed);
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_BACK, b -> onClose()).width(200).build());
    }

    /** Escape is Back: the Customize screen as it was. */
    @Override
    public void onClose() {
        this.minecraft.setScreen(open.apply(current));
    }
}
