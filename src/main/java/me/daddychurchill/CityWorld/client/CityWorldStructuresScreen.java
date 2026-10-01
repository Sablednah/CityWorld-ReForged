package me.daddychurchill.CityWorld.client;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;
import me.daddychurchill.CityWorld.worldgen.StructureChoices;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The Structures page of the Customize screen: every structure set the install has, vanilla's and each mod's, with
 * an on/off each — the ones CityWorld places by default start on. Before this, placing a village in a CityWorld
 * world meant writing a datapack that widened the tag {@code #cityworld:allowed}.
 *
 * <p>The page edits a {@link CityWorldSettingsData.Structures}: only the differences from the tag are stored. It
 * never returns to the screen that opened it; {@code back} builds a fresh Customize screen around the answer (or
 * around the choice it was opened with, on Cancel), the same way a style change does and for the same reason.
 */
public class CityWorldStructuresScreen extends Screen {

    private static final int WIDTH = CityWorldCustomizeScreen.WIDTH;
    private static final int HEIGHT = CityWorldCustomizeScreen.HEIGHT;

    private final Screen lastScreen;
    /** 1.20.1 has no widget-holding options list, so this is the Customize screen's own (see there). */
    private CityWorldCustomizeScreen.Rows rows;

    private final List<StructureChoices.Entry> entries;
    private final CityWorldSettingsData.Structures initial;
    private final Function<CityWorldSettingsData.Structures, Screen> back;
    /** Set id → whether this world places it; mutated live by the buttons. */
    private final Map<String, Boolean> placed = new LinkedHashMap<>();

    public CityWorldStructuresScreen(Screen parent, List<StructureChoices.Entry> entries,
            CityWorldSettingsData.Structures initial, Function<CityWorldSettingsData.Structures, Screen> back) {
        super(Component.literal("CityWorld Structures"));
        this.lastScreen = parent;
        this.entries = entries;
        this.initial = initial;
        this.back = back;
        for (StructureChoices.Entry entry : entries)
            placed.put(entry.id(), initial.allows(entry.id(), entry.byDefault()));
    }

    private void buildOptions() {
        List<AbstractWidget> row = new ArrayList<>();
        String group = null;
        for (StructureChoices.Entry entry : entries) {
            if (!entry.group().equals(group)) {
                flush(row);
                group = entry.group();
                this.rows.add(new net.minecraft.client.gui.components.StringWidget(WIDTH * 2 + 10, HEIGHT,
                        Component.literal(group), this.font).alignCenter(), null);
            }
            CycleButton<Boolean> button = CycleButton.onOffBuilder(placed.get(entry.id()))
                    .create(0, 0, WIDTH, HEIGHT, Component.literal(entry.label()),
                            (b, on) -> placed.put(entry.id(), on));
            button.setTooltip(Tooltip.create(Component.literal(describe(entry))));
            row.add(button);
            if (row.size() == 2)
                flush(row);
        }
        flush(row);
    }

    private void flush(List<AbstractWidget> row) {
        if (row.isEmpty())
            return;
        this.rows.add(row.get(0), row.size() > 1 ? row.get(1) : null);
        row.clear();
    }

    private static String describe(StructureChoices.Entry entry) {
        StringBuilder text = new StringBuilder(entry.id());
        if (!entry.structures().isEmpty()) {
            int shown = Math.min(entry.structures().size(), 8);
            text.append("\n").append(String.join(", ", entry.structures().subList(0, shown)));
            if (shown < entry.structures().size())
                text.append(" and ").append(entry.structures().size() - shown).append(" more");
        }
        text.append("\n").append(entry.realms().isEmpty() ? "Its biomes are in no vanilla realm"
                : "Found in: " + String.join(", ", entry.realms()));
        text.append("\n").append(entry.byDefault() ? "On by default" : "Off by default");
        return text.toString();
    }

    /** 1.20.1 has neither the layout helper nor the addOptions/addFooter hooks, so the screen builds itself. */
    @Override
    protected void init() {
        this.rows = new CityWorldCustomizeScreen.Rows(this.minecraft, this.width, this.height, 32, this.height - 44,
                HEIGHT + 5);
        buildOptions();
        addWidget(this.rows);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> leave(chosen()))
                .bounds(this.width / 2 - 158, this.height - 32, 100, HEIGHT).build());
        // back to what CityWorld ships: a fresh page around the empty choice, which is the tag exactly
        addRenderableWidget(Button.builder(Component.literal("Defaults"), b -> this.minecraft.setScreen(
                new CityWorldStructuresScreen(this.lastScreen, entries, CityWorldSettingsData.Structures.DEFAULT,
                        back))).bounds(this.width / 2 - 50, this.height - 32, 100, HEIGHT).build());
        addRenderableWidget(Button.builder(CommonComponents.GUI_CANCEL, b -> leave(initial))
                .bounds(this.width / 2 + 58, this.height - 32, 100, HEIGHT).build());
    }

    @Override
    public void render(net.minecraft.client.gui.GuiGraphics graphics, int mouseX, int mouseY, float partial) {
        renderBackground(graphics);
        this.rows.render(graphics, mouseX, mouseY, partial);
        graphics.drawCenteredString(this.font, this.title, this.width / 2, 16, 0xFFFFFF);
        super.render(graphics, mouseX, mouseY, partial);
    }

    private CityWorldSettingsData.Structures chosen() {
        Map<String, Boolean> inTag = new LinkedHashMap<>();
        for (StructureChoices.Entry entry : entries)
            inTag.put(entry.id(), entry.byDefault());
        return initial.with(placed, inTag);
    }

    private void leave(CityWorldSettingsData.Structures answer) {
        this.minecraft.setScreen(back.apply(answer));
    }

    /** Escape is Cancel. */
    @Override
    public void onClose() {
        leave(initial);
    }
}
