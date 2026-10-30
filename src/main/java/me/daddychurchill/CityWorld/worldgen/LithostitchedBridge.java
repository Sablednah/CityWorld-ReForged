package me.daddychurchill.CityWorld.worldgen;

import com.mojang.serialization.Lifecycle;

import net.minecraft.core.Holder;
import net.minecraft.core.MappedRegistry;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistrationInfo;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * Lets the biome mods built on <a href="https://modrinth.com/mod/lithostitched">Lithostitched</a> — Regions
 * Unexplored, and Terralith's modifiers — reach <b>vanilla land</b>.
 *
 * <p>Lithostitched is the third road into vanilla's overworld (TerraBlender's regions and datapack world presets
 * are the other two, {@link TerraBlenderBridge} and {@link PresetBiomes}). At server start it walks the level-stem
 * registry and, for every stem whose generator is a {@code NoiseBasedChunkGenerator}, adds its surface rules to the
 * generator's noise settings and wraps the generator's biome source in its {@code InjectorBiomeSource}, which lays
 * the mod's biomes over vanilla's by density functions and regions of its own. A CityWorld world's overworld stem is
 * CityWorld's generator, so it is skipped — and the vanilla generator CityWorld builds for vanilla land
 * ({@code CityWorldChunkGenerator.vanillaOverworld}) did not exist yet. This hands that generator to the same two
 * passes through a one-stem registry, exactly as TerraBlender's {@code initializeOverworld} does for its regions.
 *
 * <p>Its injectors are not climate points, so there is nothing to harvest for CityWorld's own land: a
 * Lithostitched mod's biomes appear on vanilla land only, and the log says so once.
 *
 * <p>Reflection, for the same reason as {@link TerraBlenderBridge}: nothing outside this class may name a
 * Lithostitched type, and any surprise disables the bridge rather than the world.
 */
public final class LithostitchedBridge {
    private static final String MOD_ID = "lithostitched";
    private static final String SURFACE_RULES = "dev.worldgen.lithostitched.worldgen.surface.SurfaceRuleManager";
    private static final String INJECTORS = "dev.worldgen.lithostitched.impl.worldgen.biomeinjector.internal.BiomeInjectorManager";

    private LithostitchedBridge() {}

    private static final boolean PRESENT = detect();
    /** Settings objects whose surface rules this bridge has already merged (identity, weak — a registry reload frees them). */
    private static final java.util.Set<Object> SURFACE_RULES_DONE = java.util.Collections
            .synchronizedSet(java.util.Collections.newSetFromMap(new java.util.WeakHashMap<>()));

    private static boolean detect() {
        try {
            return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false; // no mod list (datagen, tests) — treat as absent
        }
    }

    public static boolean present() {
        return PRESENT;
    }

    /**
     * Runs Lithostitched's surface-rule and biome-injector passes over vanilla land's generator, as they ran over
     * every vanilla stem at server start. Afterwards {@code built.getBiomeSource()} may be a different object (the
     * injector wrapper): read it back, never keep the source handed in.
     */
    public static void initializeOverworld(RegistryAccess registries, Holder<DimensionType> overworldType,
            NoiseBasedChunkGenerator built, long seed) {
        if (!PRESENT)
            return;
        try {
            MappedRegistry<LevelStem> stems = new MappedRegistry<>(Registries.LEVEL_STEM, Lifecycle.stable());
            stems.register(LevelStem.OVERWORLD, new LevelStem(overworldType, built), RegistrationInfo.BUILT_IN);
            stems.freeze();
            var before = built.getBiomeSource();
            // The surface-rule pass edits the NoiseGeneratorSettings object IN PLACE, and that object is the registry's
            // — shared by every generator built from it. Run twice on one object it nests the merged rule inside a
            // reference to itself: StackOverflowError in SurfaceRules.apply on every chunk (two vanilla-land generators
            // are built per server). Once per settings object, and never over a rule Lithostitched already merged.
            var settings = built.generatorSettings().value();
            boolean merged = false; // 26.3: the settings record has no surfaceRule() accessor; the weak set alone guards
            try {
                Object rule = settings.getClass().getMethod("surfaceRule").invoke(settings);
                merged = rule != null && rule.getClass().getName().endsWith("TransientMergedRule");
            } catch (ReflectiveOperationException | RuntimeException ignored) {
            }
            if (!merged && SURFACE_RULES_DONE.add(settings))
                Class.forName(SURFACE_RULES).getMethod("applySurfaceRules", RegistryAccess.class, Registry.class)
                        .invoke(null, registries, stems);
            Class.forName(INJECTORS).getMethod("applyBiomeInjectors", RegistryAccess.class, Registry.class, long.class)
                    .invoke(null, registries, stems, seed);
            var after = built.getBiomeSource();
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.info(
                    "CityWorld: Lithostitched applied to vanilla land — biome source {} -> {} with {} biomes "
                            + "(its biomes appear on vanilla land only; CityWorld's own land has no climate points to read)",
                    before.getClass().getSimpleName(), after.getClass().getSimpleName(), after.possibleBiomes().size());
        } catch (Throwable t) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: could not apply Lithostitched to vanilla land — its mods' biomes will not appear", t);
        }
    }
}
