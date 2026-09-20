package me.daddychurchill.CityWorld.client;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState.WorldTypeEntry;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * Locks the create-world screen's World Type to {@link CityWorldPackConfig#lockedWorldPreset} — the modpack
 * "every new world is CityWorld Apocalypse" switch. Inert when no lock is configured.
 *
 * <p>Three layers, because vanilla gives each of them a way back:
 * <ol>
 *   <li><b>Select it.</b> The screen always opens on {@code minecraft:normal} (hardcoded in
 *       {@code CreateWorldScreen.openFresh}), so on init the preset is set if it is not already. Only
 *       <em>if not already</em>: init re-runs on every return from Customize and on resize, and
 *       {@code setWorldType} rebuilds the dimensions, which would throw the player's customisation away.</li>
 *   <li><b>Leave nothing to cycle to.</b> The World Type button cycles the ui state's preset lists, which are
 *       its own mutable lists — they are cut to the locked entry. A datapack change on the More tab
 *       ({@code setSettings}) refills them, so a ui-state listener re-cuts them and re-selects the preset.</li>
 *   <li><b>Show it is locked.</b> The button lives in a private tab class and only joins the screen's
 *       children when that tab is shown, and its own listener re-enables it on every change, so it is found
 *       and greyed each frame instead of once.</li>
 * </ol>
 *
 * <p>Client-side only; a vanilla client is never involved, and a dedicated server uses {@code level-type}.
 */
public final class WorldTypeLock {

    private WorldTypeLock() {}

    /** Ui states already carrying our listener — the screen object is re-initialised, the state is not. */
    private static final Set<WorldCreationUiState> HOOKED = Collections.newSetFromMap(new WeakHashMap<>());

    private static final Component LOCKED = Component.translatable("cityworld.lock.world_type");

    public static void register() {
        if (CityWorldPackConfig.lockedWorldPreset().isEmpty() && CityWorldPackConfig.lockedRuinedNether().isEmpty()
                && CityWorldPackConfig.lockedCityWorldEnd().isEmpty())
            return;
        MinecraftForge.EVENT_BUS.addListener(WorldTypeLock::onInit);
        MinecraftForge.EVENT_BUS.addListener(WorldTypeLock::onRender);
    }

    private static void onInit(ScreenEvent.Init.Post event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen))
            return;
        WorldCreationUiState state = screen.getUiState();
        if (enforce(state) && HOOKED.add(state))
            state.addListener(WorldTypeLock::enforce);
    }

    private static void onRender(ScreenEvent.Render.Pre event) {
        if (!(event.getScreen() instanceof CreateWorldScreen screen))
            return;
        for (var child : screen.children()) {
            if (child instanceof CycleButton<?> button && button.getValue() instanceof WorldTypeEntry && button.active) {
                button.active = false;
                button.setTooltip(Tooltip.create(LOCKED));
            }
        }
    }

    /** Both locks, type first: selecting a preset rebuilds every dimension, so the Nether is re-applied after it. */
    private static boolean enforce(WorldCreationUiState state) {
        boolean type = enforceType(state);
        boolean nether = enforceNether(state);
        boolean end = enforceEnd(state);
        return type || nether || end;
    }

    /** A pack's {@code ruinedNether} lock: swap the Nether whenever the selected dimensions disagree with it. */
    private static boolean enforceNether(WorldCreationUiState state) {
        Optional<Boolean> ruined = CityWorldPackConfig.lockedRuinedNether();
        if (ruined.isEmpty())
            return false;
        if (me.daddychurchill.CityWorld.worldgen.CityWorldRealms.hasRuinedNether(state.getSettings().selectedDimensions()) != ruined.get())
            state.updateDimensions((registries, dimensions) ->
                    me.daddychurchill.CityWorld.worldgen.CityWorldRealms.withNether(registries, dimensions, ruined.get()));
        return true;
    }

    /**
     * Selects the locked preset and trims the cycle lists to it. Returns false (and changes nothing) if the
     * preset does not exist in this screen's registries — a typo in the config, or the pack's datapack missing —
     * so a bad lock leaves vanilla's screen usable rather than stuck on nothing.
     */
    /** A pack's {@code cityworldEnd} lock: swap the End whenever the selected dimensions disagree with it. */
    private static boolean enforceEnd(WorldCreationUiState state) {
        Optional<Boolean> cityWorld = CityWorldPackConfig.lockedCityWorldEnd();
        if (cityWorld.isEmpty())
            return false;
        if (me.daddychurchill.CityWorld.worldgen.CityWorldRealms.hasCityWorldEnd(state.getSettings().selectedDimensions()) != cityWorld.get())
            state.updateDimensions((registries, dimensions) ->
                    me.daddychurchill.CityWorld.worldgen.CityWorldRealms.withEnd(registries, dimensions, cityWorld.get()));
        return true;
    }

    private static boolean enforceType(WorldCreationUiState state) {
        Optional<ResourceKey<WorldPreset>> key = CityWorldPackConfig.lockedWorldPreset();
        if (key.isEmpty())
            return false;
        Optional<Holder.Reference<WorldPreset>> preset = state.getSettings().worldgenLoadContext()
                .lookupOrThrow(Registries.WORLD_PRESET).get(key.get());
        if (preset.isEmpty()) {
            CityWorldMod.LOGGER.warn("CityWorld: lockedWorldPreset {} is not a registered world preset — lock not applied",
                    key.get().location());
            return false;
        }
        WorldTypeEntry entry = new WorldTypeEntry(preset.get());
        trim(state.getNormalPresetList(), entry);
        trim(state.getAltPresetList(), entry);
        Holder<WorldPreset> current = state.getWorldType().preset();
        // Compare by key: the setWorldType below re-enters this through onChanged, and must find it done.
        if (current == null || !current.is(key.get()))
            state.setWorldType(entry);
        return true;
    }

    private static void trim(List<WorldTypeEntry> list, WorldTypeEntry entry) {
        if (list.size() == 1 && list.get(0).equals(entry))
            return;
        list.clear();
        list.add(entry);
    }
}
