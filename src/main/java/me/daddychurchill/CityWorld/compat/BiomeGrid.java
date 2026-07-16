package me.daddychurchill.CityWorld.compat;

/**
 * Shim for Bukkit's {@code ChunkGenerator.BiomeGrid} — the sink CityWorld writes a biome per column
 * into while it shapes a chunk.
 *
 * <p>Narrow on purpose: of the four methods Bukkit exposed, the entire CityWorld tree only ever
 * calls {@code setBiome(x, z, biome)}. Keeping the interface to what is actually used means the
 * modern implementation (P3/P4) has one method to satisfy rather than four to fake.
 *
 * <p>The impedance mismatch this hides: modern worldgen resolves biomes through a
 * {@code BiomeSource} queried by the engine, rather than letting the generator push them. The
 * likely landing is a {@code BiomeSource} backed by the same {@code ShapeProvider} maths, with an
 * implementation of this interface recording columns during generation. Until then a no-op
 * implementation is enough to run the terrain path — see PORTING.md.
 */
public interface BiomeGrid {

    /**
     * Sets the biome for a column, in chunk-relative coordinates ({@code 0..15}).
     *
     * @param x chunk-relative X
     * @param z chunk-relative Z
     * @param biome the biome for the column
     */
    void setBiome(int x, int z, Biome biome);
}
