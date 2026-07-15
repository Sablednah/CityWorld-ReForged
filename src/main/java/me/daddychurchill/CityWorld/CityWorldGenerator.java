package me.daddychurchill.CityWorld;

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

    /**
     * World height in blocks. The old generator derived this from the ShapeProvider (0..255 in
     * 1.14). Phase 4 modernizes it to the full {@code -64..319} range. Defaulted here so the block
     * layer's bounds checks have a sane value until the ShapeProvider is ported.
     */
    public int height = 384;

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
}
