package me.daddychurchill.CityWorld;

import org.slf4j.Logger;

import com.mojang.logging.LogUtils;

import me.daddychurchill.CityWorld.compat.Material;

/**
 * Skeleton of the CityWorld generator context.
 *
 * <p><b>Phase 1 placeholder.</b> The original Bukkit {@code CityWorldGenerator} both <em>was</em>
 * the {@code ChunkGenerator} and held all the per-world providers/levels. In the NeoForge port this
 * class will (Phase 3) become a codec-registered {@code net.minecraft.world.level.chunk.ChunkGenerator}
 * and own the provider stack. For now it exposes only the narrow surface the block-writing layer
 * ({@code AbstractBlocks}) actually touches, so that layer can be ported and compiled first.
 *
 * <p>Everything here is intentionally minimal and will be replaced/expanded as later phases land.
 */
public class CityWorldGenerator {

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * World height in blocks. The old generator derived this from the ShapeProvider (0..255 in
     * 1.14). Phase 4 modernizes it to the full {@code -64..319} range. Defaulted here so the block
     * layer's bounds checks have a sane value until the ShapeProvider is ported.
     */
    public int height = 384;

    /**
     * Y of the streets — the datum the whole city is laid out around (buildings measure their
     * floors above and basements below it). The real value comes from
     * {@code shapeProvider.getStreetLevel()} once the ShapeProvider is ported, which computes it as
     * {@code seaLevel + 1}; with Bukkit's sea level of 63 that is the 64 stubbed here. Phase 4
     * revisits it along with the rest of the Y math for -64..319.
     */
    public int streetLevel = 64;

    /**
     * Whether the "atmosphere" (sky column) should be actively cleared/filled during generation.
     * Stubbed {@code false} for Phase 1 (only the alien/void styles set this); the real value comes
     * from the ShapeProvider once ported.
     */
    public boolean clearAtmosphere() {
        return false;
    }

    /**
     * The block to fill the atmosphere with at a given Y (air for normal worlds; nether/end styles
     * override). Stubbed to air for Phase 1.
     */
    public Material findAtmosphereMaterialAt(int blockY) {
        return Material.AIR;
    }

    /**
     * Diagnostic logging, used throughout the generator (mostly from commented-out tracing that the
     * port preserves). The original routed this through the Bukkit plugin's logger; here it goes to
     * the mod's log at debug level, so leftover tracing stays silent unless it is asked for.
     */
    public void reportFormatted(String format, Object... objects) {
        LOGGER.debug(String.format(format, objects));
    }
}
