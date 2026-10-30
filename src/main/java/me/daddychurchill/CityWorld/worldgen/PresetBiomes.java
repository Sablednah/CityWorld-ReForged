package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.JsonOps;

import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;

/**
 * The overworld a pack gave <em>vanilla</em>, read from a world preset ({@code settings.world.packPreset},
 * {@code minecraft:normal} by default).
 *
 * <p>Biome mods reach vanilla's overworld by three roads, and CityWorld's own biome source saw only one of
 * them. TerraBlender mods register regions ({@link TerraBlenderBridge} harvests those). Datapack biome
 * mods — Terralith, and Dreamwoods' Grand Terrain — rewrite the {@code minecraft:normal} world preset
 * itself: its overworld {@code NoiseBasedChunkGenerator} carries their noise settings and a
 * {@code MultiNoiseBiomeSource} with their biomes written INLINE, never into the {@code minecraft:overworld}
 * parameter list CityWorld used to read. So a vanilla-land world had Grand Terrain's hills and none of
 * Dreamwoods' biomes, and CityWorld's own land could not have them at all (a player's report,
 * 2026-10-29). This class reads that preset: vanilla land takes its generator settings and biome source
 * from it, and CityWorld's land offers its foreign biomes through the same share mechanism as TerraBlender's.
 *
 * <p>The points are read back through the biome source's own codec rather than a private field, so the
 * same code serves every line (1.20.1's production runtime has SRG field names).
 */
public final class PresetBiomes {

    private PresetBiomes() {}

    /** The overworld noise generator a world preset defines, if it has one. */
    public static Optional<NoiseBasedChunkGenerator> overworldOf(RegistryAccess registries, String presetId) {
        if (presetId == null || presetId.isBlank())
            return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(presetId);
        if (id == null)
            return Optional.empty();
        try {
            return registries.lookupOrThrow(Registries.WORLD_PRESET)
                    .get(ResourceKey.create(Registries.WORLD_PRESET, id))
                    .map(p -> p.value().createWorldDimensions().dimensions().get(LevelStem.OVERWORLD))
                    .filter(stem -> stem.generator() instanceof NoiseBasedChunkGenerator)
                    .map(stem -> (NoiseBasedChunkGenerator) stem.generator());
        } catch (RuntimeException e) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: could not read world preset {} ({})",
                    presetId, e.toString());
            return Optional.empty();
        }
    }

    /**
     * Every {@code (climate point, biome)} the preset's overworld biome source declares — a
     * {@code MultiNoiseBiomeSource}'s list, whether it names a registry parameter list or writes its
     * biomes inline. Empty for any other kind of source, or when the preset is absent.
     */
    public static List<Pair<Climate.ParameterPoint, Holder<Biome>>> points(RegistryAccess registries, String presetId) {
        Optional<NoiseBasedChunkGenerator> overworld = overworldOf(registries, presetId);
        if (overworld.isEmpty())
            return List.of();
        return points(registries, overworld.get().getBiomeSource());
    }

    /** The biome source's climate list, read back through its codec. */
    public static List<Pair<Climate.ParameterPoint, Holder<Biome>>> points(RegistryAccess registries, BiomeSource source) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> out = new ArrayList<>();
        try {
            RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, registries);
            JsonElement encoded = BiomeSource.CODEC.encodeStart(ops, source).result().orElse(null);
            if (!(encoded instanceof JsonObject json))
                return out;
            var biomes = registries.lookupOrThrow(Registries.BIOME);
            if (json.has("preset")) {
                ResourceLocation id = ResourceLocation.tryParse(json.get("preset").getAsString());
                if (id != null)
                    registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                            .get(ResourceKey.create(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST, id))
                            .ifPresent(list -> out.addAll(list.value().parameters().values()));
            } else if (json.has("biomes")) {
                for (JsonElement entry : json.getAsJsonArray("biomes")) {
                    if (!(entry instanceof JsonObject e) || !e.has("biome") || !e.has("parameters"))
                        continue;
                    ResourceLocation id = ResourceLocation.tryParse(e.get("biome").getAsString());
                    Climate.ParameterPoint point = Climate.ParameterPoint.CODEC.parse(JsonOps.INSTANCE, e.get("parameters"))
                            .result().orElse(null);
                    if (id == null || point == null)
                        continue;
                    biomes.get(ResourceKey.create(Registries.BIOME, id)).ifPresent(h -> out.add(Pair.of(point, h)));
                }
            }
        } catch (RuntimeException e) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn("CityWorld: could not read a biome source's climate list ({})",
                    e.toString());
        }
        return out;
    }

    /** Every biome vanilla itself defines — the {@link Biomes} constants (values only; the field names are SRG on 1.20.1). */
    public static final Set<ResourceKey<Biome>> VANILLA = vanilla();

    private static Set<ResourceKey<Biome>> vanilla() {
        Set<ResourceKey<Biome>> keys = new java.util.HashSet<>();
        for (java.lang.reflect.Field f : Biomes.class.getDeclaredFields())
            if (java.lang.reflect.Modifier.isStatic(f.getModifiers()) && f.getType() == ResourceKey.class)
                try {
                    Object v = f.get(null);
                    if (v instanceof ResourceKey<?> k && k.isFor(Registries.BIOME)) {
                        @SuppressWarnings("unchecked")
                        ResourceKey<Biome> typed = (ResourceKey<Biome>) k;
                        keys.add(typed);
                    }
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                }
        return Set.copyOf(keys);
    }

    /**
     * Whether a biome is foreign to vanilla: anything vanilla itself does not define, whatever its
     * namespace. A mod's own biomes are; so is a backport's {@code minecraft:pale_garden} on a version that
     * has none (VanillaBackport contributes it through a TerraBlender overlay), which a namespace test called
     * vanilla and so never placed. The vanilla biomes a region or a preset also carries stay vanilla's, and
     * CityWorld's own matrix stays in charge of that ground.
     */
    public static boolean isForeign(Holder<Biome> biome) {
        return biome.unwrapKey().map(k -> !VANILLA.contains(k) && !isPlaceholder(k)).orElse(false);
    }

    /**
     * TerraBlender's {@code terrablender:deferred_placeholder}: a region's "vanilla's biome goes here" marker, real
     * enough to be in the registry. Measured as 15.5% of the columns of a 1.21.1 world the moment the bridge
     * worked there (2026-10-30) — a biome with no features and no look — so it is never foreign and never placed.
     */
    public static boolean isPlaceholder(ResourceKey<Biome> key) {
        return key.location().getNamespace().equals("terrablender");
    }
}
