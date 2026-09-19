package me.daddychurchill.CityWorld.client;

import com.mojang.serialization.Lifecycle;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.client.gui.screens.worldselection.WorldCreationContext;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.RegistryLayer;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.biome.TheEndBiomeSource;
import net.minecraft.world.level.dimension.BuiltinDimensionTypes;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Skips vanilla's "experimental settings" confirm when a new world is experimental <em>only</em> because of
 * CityWorld.
 *
 * <p><b>Why every CityWorld world triggered it.</b> {@code WorldDimensions.bake} calls a world stable only
 * with exactly the three vanilla dimensions, each vanilla-like ({@code checkStability}). CityWorld has always
 * added a fourth, {@code cityworld:city}, and a ruined-city Nether is never "vanilla-like" (that needs the
 * built-in Nether type on a {@code NoiseBasedChunkGenerator}). Nothing about either is experimental in any
 * sense a player should be warned about.
 *
 * <p><b>It still warns for everything else.</b> The confirm is skipped only if, rebuilding the same verdict
 * vanilla reached: no experimental feature flags are enabled; no registry outside CityWorld's own namespace is
 * less than stable; and every dimension is either CityWorld's generator or passes vanilla's own
 * vanilla-like test (replicated from {@code WorldDimensions}, which keeps it private). Another mod's custom
 * dimension, an experimental datapack, or a deprecated setting all still get the warning.
 *
 * <p>The screen is cancelled as it opens and its callback answered on the next tick, which is exactly what
 * clicking "Yes" does — {@code CreateWorldScreen} then creates the world, and NeoForge marks the warning
 * confirmed so it is not shown when the world is reopened.
 */
public final class ExperimentalWarningSkip {

    private ExperimentalWarningSkip() {}

    private static final String EXPERIMENTAL_TITLE = "selectWorld.warning.experimental.title";

    public static void register() {
        NeoForge.EVENT_BUS.addListener(ExperimentalWarningSkip::onOpening);
    }

    private static void onOpening(ScreenEvent.Opening event) {
        if (!(event.getNewScreen() instanceof ConfirmScreen confirm)
                || !(event.getCurrentScreen() instanceof CreateWorldScreen create))
            return;
        if (!(confirm.getTitle().getContents() instanceof TranslatableContents title)
                || !title.getKey().equals(EXPERIMENTAL_TITLE))
            return;
        if (!onlyCityWorld(create.getUiState().getSettings()))
            return;
        event.setCanceled(true);
        CityWorldMod.LOGGER.info("CityWorld: skipped the experimental-settings warning — its only cause is CityWorld's own dimensions");
        Minecraft.getInstance().execute(() -> confirm.callback.accept(true));
    }

    /** Whether CityWorld's dimensions (and nothing else) are what makes this new world experimental. */
    static boolean onlyCityWorld(WorldCreationContext context) {
        if (FeatureFlags.isExperimental(context.dataConfiguration().enabledFeatures()))
            return false;
        for (RegistryLayer layer : new RegistryLayer[] { RegistryLayer.STATIC, RegistryLayer.WORLDGEN }) {
            boolean foreignUnstable = context.worldgenRegistries().getLayer(layer).registries()
                    .filter(entry -> !entry.key().location().getNamespace().equals(CityWorldMod.MODID))
                    .anyMatch(entry -> entry.value().registryLifecycle() != Lifecycle.stable());
            if (foreignUnstable)
                return false;
        }
        boolean cityWorld = false;
        WorldDimensions.Complete complete = context.selectedDimensions().bake(context.datapackDimensions());
        for (var entry : complete.dimensions().entrySet()) {
            LevelStem stem = entry.getValue();
            if (stem.generator() instanceof CityWorldChunkGenerator) {
                cityWorld = true;
                continue;
            }
            if (!vanillaLike(entry.getKey(), stem))
                return false;
        }
        return cityWorld;
    }

    /** {@code WorldDimensions.isVanillaLike}, which is private. */
    private static boolean vanillaLike(ResourceKey<LevelStem> key, LevelStem stem) {
        if (key == LevelStem.OVERWORLD)
            return (stem.type().is(BuiltinDimensionTypes.OVERWORLD) || stem.type().is(BuiltinDimensionTypes.OVERWORLD_CAVES))
                    && !(stem.generator().getBiomeSource() instanceof MultiNoiseBiomeSource multiNoise
                            && !multiNoise.stable(MultiNoiseBiomeSourceParameterLists.OVERWORLD));
        if (key == LevelStem.NETHER)
            return stem.type().is(BuiltinDimensionTypes.NETHER)
                    && stem.generator() instanceof NoiseBasedChunkGenerator noise
                    && noise.stable(NoiseGeneratorSettings.NETHER)
                    && noise.getBiomeSource() instanceof MultiNoiseBiomeSource multiNoise
                    && multiNoise.stable(MultiNoiseBiomeSourceParameterLists.NETHER);
        if (key == LevelStem.END)
            return stem.type().is(BuiltinDimensionTypes.END)
                    && stem.generator() instanceof NoiseBasedChunkGenerator noise
                    && noise.stable(NoiseGeneratorSettings.END)
                    && noise.getBiomeSource() instanceof TheEndBiomeSource;
        return false;
    }
}
