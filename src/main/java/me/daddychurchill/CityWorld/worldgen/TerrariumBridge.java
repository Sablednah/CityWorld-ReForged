package me.daddychurchill.CityWorld.worldgen;

import java.lang.reflect.Method;
import java.util.Optional;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.BiomeSource;

/**
 * Terrarium Expanded (an Earth for the overworld) on vanilla land: {@code world.packPreset
 * "terrarium_expanded:earth"} builds CityWorld's cities on its terrain, but Terrarium knows a world as its own only
 * by the dimension's generator, which here is CityWorld's. Whatever depends on that test is put right from here, by
 * name and without a compile-time dependency (GitHub issue #9).
 *
 * <p><b>The spawn.</b> On every server start Terrarium moves the overworld's spawn: to its preset's latitude and
 * longitude in a world it knows, and to block 0, 0 in any other — which on its Earth is open sea in the Gulf of
 * Guinea. {@link #spawnOf} answers the point its own preset would have given.
 */
public final class TerrariumBridge {

    private TerrariumBridge() {
    }

    private static final String SOURCE = "com.github.intplex.earth.biome.EcoregionBiomeSource";
    private static final String CONFIG = "com.github.intplex.earth.EarthGenConfig";

    /** Whether a biome source is Terrarium Expanded's Earth. */
    public static boolean isEarth(@Nullable BiomeSource source) {
        return source != null && source.getClass().getName().equals(SOURCE);
    }

    /**
     * The block Terrarium's own preset spawns a player at (its latitude and longitude, at sea level), or null: not
     * its Earth, or a version of the mod whose names have moved.
     */
    public static @Nullable BlockPos spawnOf(@Nullable BiomeSource source) {
        if (!isEarth(source))
            return null;
        try {
            Class<?> type = source.getClass();
            double latitude = (Double) type.getMethod("spawnLatitude").invoke(source);
            double longitude = (Double) type.getMethod("spawnLongitude").invoke(source);
            int zoom = (Integer) type.getMethod("zoom").invoke(source);
            int seaLevel = (Integer) type.getMethod("seaLevel").invoke(source);
            Method geoToBlock = Class.forName(CONFIG, true, type.getClassLoader()).getMethod("geoToBlock", double.class,
                    double.class, int.class);
            Object found = ((Optional<?>) geoToBlock.invoke(null, latitude, longitude, zoom)).orElse(null);
            if (found == null)
                return null;
            int x = (Integer) found.getClass().getMethod("x").invoke(found);
            int z = (Integer) found.getClass().getMethod("z").invoke(found);
            return new BlockPos(x, seaLevel, z);
        } catch (ReflectiveOperationException | RuntimeException e) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: could not read Terrarium Expanded's spawn point ({}); the spawn stays where it is", e.toString());
            return null;
        }
    }
}
