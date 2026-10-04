package me.daddychurchill.CityWorld.worldgen;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * What other mods do to an overworld biome source at server start, done again for the vanilla overworld source a
 * vanilla-land world builds inside CityWorld's generator ({@code CityWorldChunkGenerator.vanillaOverworld}). Those
 * mods set up only the sources of real dimension stems, and CityWorld's overworld stem is not a vanilla one.
 *
 * <p><b>Alex's Caves</b> (owner's 1.20.1 instance, 2026-10-04): its mixin on {@code MultiNoiseBiomeSource} answers its
 * rare cave biomes out of a key-to-biome map that its {@code onServerAboutToStart} fills; on an unprepared source the
 * map is empty, so wherever a cave biome belonged the source answered null and every chunk failed. Prepared the same
 * way here — every biome in the map, its own cave biomes added to the possible biomes, this world's seed and the
 * overworld as the dimension it samples for — its caves also appear in vanilla land. Reflection, so nothing breaks
 * where the mod is absent or reshaped (TerraBlender has its own bridge).
 */
public final class ModdedBiomeSources {

    private ModdedBiomeSources() {}

    private static final String AC = "com.github.alexmodguy.alexscaves.server.level.biome.";

    public static void prepareOverworld(BiomeSource source, net.minecraft.core.RegistryAccess registries, long seed) {
        try {
            Class<?> accessor = Class.forName(AC + "BiomeSourceAccessor");
            if (!accessor.isInstance(source))
                return;
            var biomes = registries.lookupOrThrow(Registries.BIOME);
            Map<ResourceKey<Biome>, Holder<Biome>> all = new HashMap<>();
            biomes.listElements().forEach(holder -> all.put(holder.key(), holder));
            accessor.getMethod("setResourceKeyMap", Map.class).invoke(source, all);
            Set<Holder<Biome>> caves = new java.util.LinkedHashSet<>();
            for (Object key : (List<?>) Class.forName(AC + "ACBiomeRegistry").getField("ALEXS_CAVES_BIOMES").get(null)) {
                @SuppressWarnings("unchecked")
                Holder<Biome> holder = all.get((ResourceKey<Biome>) key);
                if (holder != null)
                    caves.add(holder);
            }
            accessor.getMethod("expandBiomesWith", Set.class).invoke(source, caves);
            Class<?> multiNoise = Class.forName(AC + "MultiNoiseBiomeSourceAccessor");
            if (multiNoise.isInstance(source)) {
                multiNoise.getMethod("setLastSampledSeed", long.class).invoke(source, seed);
                multiNoise.getMethod("setLastSampledDimension", ResourceKey.class).invoke(source,
                        net.minecraft.world.level.Level.OVERWORLD);
            }
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.info(
                    "CityWorld: prepared vanilla land's biome source for Alex's Caves ({} cave biomes)", caves.size());
        } catch (ClassNotFoundException absent) {
            // the mod is not installed
        } catch (Throwable t) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: could not prepare vanilla land's biome source for Alex's Caves", t);
        }
    }
}
