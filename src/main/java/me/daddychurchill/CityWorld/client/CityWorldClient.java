package me.daddychurchill.CityWorld.client;

import java.util.Locale;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldBiomeSource;
import me.daddychurchill.CityWorld.worldgen.CityWorldClimateBiomeSource;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;
import me.daddychurchill.CityWorld.worldgen.CityWorldRegistries;
import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent;

/**
 * Client-only wiring: registers the {@link CityWorldCustomizeScreen} as the preset editor for the
 * {@code cityworld:city} world preset, so the create-world screen shows a <em>Customize</em> button
 * for the CityWorld world type (exactly as Superflat/Single-Biome do). Loaded only on the client —
 * {@link CityWorldMod} calls {@link #init} behind a {@code Dist.CLIENT} guard, so on a dedicated
 * server this class (and the client-only types it touches) is never loaded. No {@code @OnlyIn} is
 * used: that annotation is for vanilla-patched code, and NeoForge warns when a mod applies it; the
 * dist guard is the supported way to keep client code off the server.
 */
public final class CityWorldClient {

    private CityWorldClient() {}

    /** The world preset key whose Customize button opens {@link CityWorldCustomizeScreen}. */
    private static final ResourceKey<WorldPreset> CITY = ResourceKey.create(Registries.WORLD_PRESET,
            Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "city"));

    public static void init(IEventBus modEventBus) {
        modEventBus.addListener(CityWorldClient::onRegisterPresetEditors);
        modEventBus.addListener(CityWorldClient::onRegisterDebugEntries);
    }

    /** Adds CityWorld's plan/technical readout to the F3 debug screen (see {@link CityWorldDebugEntry}). */
    private static void onRegisterDebugEntries(net.neoforged.neoforge.client.event.RegisterDebugEntriesEvent event) {
        Identifier id = Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "cityinfo");
        event.register(id, new CityWorldDebugEntry());
        // IN_OVERLAY = shown while the F3 overlay is up (how PLAYER_POSITION and friends are set).
        event.includeInProfile(id, net.minecraft.client.gui.components.debug.DebugScreenProfile.DEFAULT,
                net.minecraft.client.gui.components.debug.DebugScreenEntryStatus.IN_OVERLAY);
    }

    private static void onRegisterPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(CITY, (parent, context) -> new CityWorldCustomizeScreen(
                parent,
                currentStyle(context),
                currentSettings(context),
                result -> parent.getUiState().updateDimensions(configurator(result))));
    }

    /** Reads the style off whatever generator is currently selected, so the picker opens on it. */
    private static WorldStyle currentStyle(WorldCreationContext context) {
        if (context.selectedDimensions().overworld() instanceof CityWorldChunkGenerator cw)
            return cw.resolvedStyle();
        return WorldStyle.CLASSIC;
    }

    /** Reads the settings off the selected generator so the screen opens on the current values. */
    private static CityWorldSettingsData currentSettings(WorldCreationContext context) {
        if (context.selectedDimensions().overworld() instanceof CityWorldChunkGenerator cw)
            return cw.resolvedSettings();
        return CityWorldSettingsData.DEFAULT;
    }

    /** Rewrites the overworld to a CityWorld generator carrying the chosen style + edited settings. */
    private static WorldCreationContext.DimensionsUpdater configurator(CityWorldCustomizeScreen.Result result) {
        return (registries, dimensions) ->
                dimensions.replaceOverworldGenerator(registries, buildGenerator(registries, result));
    }

    private static ChunkGenerator buildGenerator(RegistryAccess.Frozen registries,
            CityWorldCustomizeScreen.Result result) {
        // A Customize-created world must get the SAME terrain-driven biomes the presets ship, not a
        // flat plains. MODERN uses the full climate source (matches the city preset); the others use
        // the elevation-only source with the standard palette (matches the classic preset).
        var biomeReg = registries.lookupOrThrow(Registries.BIOME);
        boolean modernFamily = result.style() == WorldStyle.MODERN || result.style() == WorldStyle.APOCALYPSE;
        net.minecraft.world.level.biome.BiomeSource biomeSource = modernFamily
                // Both arguments, and the second one matters: it is the registry the TerraBlender
                // bridge harvests from. Passing only the getter left it null, so a world created from
                // this screen had NO modded surface biomes until it was restarted — at which point it
                // reloads through the codec, which does supply it, and they appear. Cave biomes were
                // never affected (they use the getter), which is exactly how the bug presented.
                ? new CityWorldClimateBiomeSource(biomeReg, biomeReg)
                : new CityWorldBiomeSource(
                        biomeReg,
                        biomeReg.getOrThrow(Biomes.DEEP_OCEAN),
                        biomeReg.getOrThrow(Biomes.OCEAN),
                        biomeReg.getOrThrow(Biomes.BEACH),
                        biomeReg.getOrThrow(Biomes.PLAINS),
                        biomeReg.getOrThrow(Biomes.FOREST),
                        biomeReg.getOrThrow(Biomes.TAIGA),
                        biomeReg.getOrThrow(Biomes.SNOWY_SLOPES),
                        biomeReg.getOrThrow(Biomes.DESERT));
        // CLASSIC is the codec's default, so leave "style" absent for it and set it otherwise.
        Optional<String> styleField = result.style() == WorldStyle.CLASSIC
                ? Optional.empty()
                : Optional.of(result.style().name().toLowerCase(Locale.ROOT));
        // Bake the edited settings in as an INLINE (direct) holder — the world carries its own tuned
        // settings, needing no datapack. RegistryFileCodec encodes a direct holder inline into level.dat.
        Optional<Holder<CityWorldSettingsData>> settingsField =
                Optional.of(Holder.direct(result.settings()));
        return new CityWorldChunkGenerator(biomeSource, Optional.empty(), styleField, settingsField);
    }
}
