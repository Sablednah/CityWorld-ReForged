package me.daddychurchill.CityWorld.worldgen;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.WorldDimensions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

/**
 * The other realms a CityWorld world can have, built in code so every way of choosing one — the Customize
 * toggle, a modpack lock, a server — makes the identical dimension.
 *
 * <p><b>The ruined-city Nether</b> replaces {@code minecraft:the_nether}: dimension type
 * {@code cityworld:ruined_nether} (vanilla's Nether attributes, {@code coordinate_scale} 1 so portals link
 * 1:1, full {@code -64..319} height, no roof) with a CityWorld generator that is the overworld's twin
 * ({@code twin_of}) — same seed, style and settings, so the same city — {@code decayed}, in the
 * {@code nether} environment, on {@link CityWorldNetherBiomeSource}.
 */
public final class CityWorldRealms {

    private CityWorldRealms() {}

    public static final ResourceKey<DimensionType> RUINED_NETHER_TYPE = ResourceKey.create(Registries.DIMENSION_TYPE,
            Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "ruined_nether"));

    /** Whether a world's dimensions already carry the ruined-city Nether. */
    public static boolean hasRuinedNether(WorldDimensions dimensions) {
        return dimensions.get(LevelStem.NETHER)
                .map(stem -> stem.generator() instanceof CityWorldChunkGenerator)
                .orElse(false);
    }

    /** The ruined-city Nether's level stem. */
    public static LevelStem ruinedNether(HolderLookup.Provider registries) {
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var type = registries.lookupOrThrow(Registries.DIMENSION_TYPE).getOrThrow(RUINED_NETHER_TYPE);
        CityWorldChunkGenerator generator = new CityWorldChunkGenerator(
                new CityWorldNetherBiomeSource(biomes),
                Optional.of(true),
                // Fallbacks only: twin_of takes the overworld's own style and settings at runtime.
                Optional.of("apocalypse"),
                Optional.empty(),
                Optional.of(Level.OVERWORLD),
                Optional.of("nether"));
        return new LevelStem(type, generator);
    }

    /** Whether a world's dimensions already carry the CityWorld End. */
    public static boolean hasCityWorldEnd(WorldDimensions dimensions) {
        return dimensions.get(LevelStem.END)
                .map(stem -> stem.generator() instanceof CityWorldChunkGenerator)
                .orElse(false);
    }

    /**
     * The CityWorld End's level stem. <b>The dimension type stays vanilla's {@code minecraft:the_end}</b>: on
     * 1.21.11 {@code ServerLevel} only creates the dragon fight for that type (26.x asks the type's
     * {@code has_ender_dragon_fight} instead, which vanilla's has), so borrowing it is what keeps the fight,
     * the exit portal and the gateways working.
     */
    public static LevelStem cityWorldEnd(HolderLookup.Provider registries) {
        var biomes = registries.lookupOrThrow(Registries.BIOME);
        var type = registries.lookupOrThrow(Registries.DIMENSION_TYPE)
                .getOrThrow(net.minecraft.world.level.dimension.BuiltinDimensionTypes.END);
        CityWorldChunkGenerator generator = new CityWorldChunkGenerator(
                new CityWorldEndBiomeSource(biomes),
                // Pristine: the dragon kept the End's city untouched while the overworld's fell.
                Optional.of(false),
                // Fallbacks only: twin_of takes the overworld's own style and settings at runtime.
                Optional.of("floating"),
                Optional.empty(),
                Optional.of(Level.OVERWORLD),
                Optional.of("the_end"));
        return new LevelStem(type, generator);
    }

    /** Vanilla's End, as the {@code minecraft:normal} preset builds it — what switching back restores. */
    public static Optional<LevelStem> vanillaEnd(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
                .createWorldDimensions().get(LevelStem.END);
    }

    /** {@code dimensions} with the End swapped for CityWorld's, or back to vanilla's. */
    public static WorldDimensions withEnd(HolderLookup.Provider registries, WorldDimensions dimensions,
            boolean cityWorld) {
        if (cityWorld == hasCityWorldEnd(dimensions))
            return dimensions;
        Map<ResourceKey<LevelStem>, LevelStem> map = new LinkedHashMap<>(dimensions.dimensions());
        if (cityWorld)
            map.put(LevelStem.END, cityWorldEnd(registries));
        else
            vanillaEnd(registries).ifPresentOrElse(stem -> map.put(LevelStem.END, stem),
                    () -> map.remove(LevelStem.END));
        return new WorldDimensions(map);
    }

    /** Vanilla's Nether, as the {@code minecraft:normal} preset builds it — what switching back restores. */
    public static Optional<LevelStem> vanillaNether(HolderLookup.Provider registries) {
        return registries.lookupOrThrow(Registries.WORLD_PRESET).getOrThrow(WorldPresets.NORMAL).value()
                .createWorldDimensions().get(LevelStem.NETHER);
    }

    /** {@code dimensions} with the Nether swapped for the ruined-city one, or for vanilla's when {@code ruined} is false. */
    public static WorldDimensions withNether(HolderLookup.Provider registries, WorldDimensions dimensions,
            boolean ruined) {
        if (ruined == hasRuinedNether(dimensions))
            return dimensions;
        Map<ResourceKey<LevelStem>, LevelStem> map = new LinkedHashMap<>(dimensions.dimensions());
        if (ruined)
            map.put(LevelStem.NETHER, ruinedNether(registries));
        else
            vanillaNether(registries).ifPresentOrElse(stem -> map.put(LevelStem.NETHER, stem),
                    () -> map.remove(LevelStem.NETHER));
        return new WorldDimensions(map);
    }
}
