package me.daddychurchill.CityWorld.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biomes;

/**
 * Shim for Bukkit's {@code org.bukkit.block.Biome}, wrapping a modern
 * {@code ResourceKey<net.minecraft.world.level.biome.Biome>}.
 *
 * <p>Interned, like {@link Material}, and for the same reason: the source treats biomes as a value
 * vocabulary (passed around and compared, never switched on), so wrappers work where an enum would
 * force us to enumerate a registry that datapacks can extend.
 *
 * <p><b>This is deliberately a shim.</b> CityWorld writes a biome per column
 * ({@code biomes.setBiome(x, z, …)}); modern worldgen assigns biomes through a {@code BiomeSource}
 * instead. Hiding that behind {@link BiomeGrid} let the brain be ported as-is, and the real
 * {@code CityWorldBiomeSource} now does the assigning. The whole tree only ever names the twelve
 * constants below.
 */
public final class Biome {

    private static final Map<ResourceKey<net.minecraft.world.level.biome.Biome>, Biome> interned =
            new ConcurrentHashMap<>();

    private final ResourceKey<net.minecraft.world.level.biome.Biome> key;

    private Biome(ResourceKey<net.minecraft.world.level.biome.Biome> key) {
        this.key = key;
    }

    /** Interns a wrapper for a vanilla biome key. */
    public static Biome of(ResourceKey<net.minecraft.world.level.biome.Biome> key) {
        return interned.computeIfAbsent(key, Biome::new);
    }

    /** Interns a wrapper for a biome by id, e.g. {@code "minecraft:plains"} — for biomes the 1.14 vocabulary never knew. */
    public static Biome of(String id) {
        return of(ResourceKey.create(Registries.BIOME, Identifier.parse(id)));
    }

    /** The vanilla registry key this biome stands for. */
    public ResourceKey<net.minecraft.world.level.biome.Biome> getKey() {
        return key;
    }

    @Override
    public String toString() {
        return key.identifier().toString();
    }

    // --- the constants CityWorld actually names ------------------------------------------------
    // Interning means == comparison still works, as it did with Bukkit's enum.

    public static final Biome THE_VOID = of(Biomes.THE_VOID);
    public static final Biome PLAINS = of(Biomes.PLAINS);
    public static final Biome DESERT = of(Biomes.DESERT);
    public static final Biome FOREST = of(Biomes.FOREST);
    public static final Biome JUNGLE = of(Biomes.JUNGLE);
    public static final Biome BEACH = of(Biomes.BEACH);
    public static final Biome OCEAN = of(Biomes.OCEAN);
    public static final Biome END_MIDLANDS = of(Biomes.END_MIDLANDS);

    // The 1.18 terrain rework deleted every "hills" variant along with SNOWY_MOUNTAINS, so these
    // four 1.14 names have no modern counterpart and are remapped to the nearest surviving biome.
    // They are only ever used to tint terrain CityWorld has already shaped itself, so the
    // substitution costs colour/mob-spawn flavour, not terrain.

    /** 1.14 {@code BIRCH_FOREST_HILLS} — hills variants removed in 1.18. */
    public static final Biome BIRCH_FOREST_HILLS = of(Biomes.BIRCH_FOREST);
    /** 1.14 {@code TAIGA_HILLS} — hills variants removed in 1.18. */
    public static final Biome TAIGA_HILLS = of(Biomes.TAIGA);
    /** 1.14 {@code DESERT_HILLS} — hills variants removed in 1.18. */
    public static final Biome DESERT_HILLS = of(Biomes.DESERT);
    /** 1.14 {@code SNOWY_MOUNTAINS} — removed in 1.18; {@code SNOWY_SLOPES} is the nearest analogue. */
    public static final Biome SNOWY_MOUNTAINS = of(Biomes.SNOWY_SLOPES);
}
