package me.daddychurchill.CityWorld.client;

import java.util.Locale;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.FixedBiomeSource;
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
    }

    private static void onRegisterPresetEditors(RegisterPresetEditorsEvent event) {
        event.register(CITY, (parent, context) -> new CityWorldCustomizeScreen(
                parent,
                currentStyle(context),
                chosen -> parent.getUiState().updateDimensions(configurator(chosen))));
    }

    /** Reads the style off whatever generator is currently selected, so the picker opens on it. */
    private static WorldStyle currentStyle(WorldCreationContext context) {
        if (context.selectedDimensions().overworld() instanceof CityWorldChunkGenerator cw)
            return cw.resolvedStyle();
        return WorldStyle.NORMAL;
    }

    /** Rewrites the overworld to a CityWorld generator carrying the chosen style. */
    private static WorldCreationContext.DimensionsUpdater configurator(WorldStyle style) {
        return (registries, dimensions) ->
                dimensions.replaceOverworldGenerator(registries, buildGenerator(registries, style));
    }

    private static ChunkGenerator buildGenerator(RegistryAccess.Frozen registries, WorldStyle style) {
        Holder<Biome> plains = registries.lookupOrThrow(Registries.BIOME).getOrThrow(Biomes.PLAINS);
        // NORMAL is the codec's default, so leave "style" absent for it and set it otherwise.
        Optional<String> styleField = style == WorldStyle.NORMAL
                ? Optional.empty()
                : Optional.of(style.name().toLowerCase(Locale.ROOT));
        return new CityWorldChunkGenerator(new FixedBiomeSource(plains), Optional.empty(), styleField);
    }
}
