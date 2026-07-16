package me.daddychurchill.CityWorld.Plats;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Biome;

/**
 * Stub of the original {@code PlatLot} (612 lines) — one chunk's worth of "what goes here".
 *
 * <p><b>Wave 1 cycle-breaker.</b> {@code PlatLot} sits in the middle of the generator's mutually
 * recursive brain ({@code ShapeProvider ↔ PlatMap ↔ PlatLot ↔ Context ↔ Plugins ↔ Rooms ↔
 * Clipboard}); porting it for real pulls in ~250 files at once. The edge is thin enough to cut
 * because {@code ShapeProvider_Normal} only ever touches the handful of members below — everything
 * else on the real class serves the city-planning and decoration passes.
 *
 * <p>The defaults here are upstream's own base-class answers, not invented ones: a lot is
 * {@code NATURE} until a context says otherwise, every Y is valid strata, and the base biome is
 * {@code PLAINS}. That means the wave-1 terrain path runs the <em>real</em> upstream code path for
 * an unplanned (natural) chunk — which is exactly the P3 gate. The subclasses ({@code RoadLot},
 * {@code BuildingLot}, {@code NatureLot}, …) and the {@code generate*} bodies arrive in wave 2.
 */
public class PlatLot {

    /** Styling — which broad kind of chunk this is. Carried over verbatim. */
    public enum LotStyle {
        NATURE, STRUCTURE, ROAD, ROUNDABOUT
    }

    public LotStyle style;
    public boolean trulyIsolated;

    protected final int chunkX;
    protected final int chunkZ;
    /** The precalculated height column for this chunk — what the strata generator shapes against. */
    public final AbstractCachedYs blockYs;

    public PlatLot(CityWorldGenerator generator, int chunkX, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.style = LotStyle.NATURE;
        this.trulyIsolated = false;

        // precalc the Ys
        this.blockYs = generator.shapeProvider.getCachedYs(generator, chunkX, chunkZ);
    }

    public int getChunkX() {
        return chunkX;
    }

    public int getChunkZ() {
        return chunkZ;
    }

    public Biome getChunkBiome() {
        return Biome.PLAINS;
    }

    /**
     * Whether the strata generator may write at this Y. The base class always says yes; the
     * subclasses that carve (mines, bunkers, basements) are what say no.
     */
    public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
        return true;
    }

    // --- decoration hooks the ShapeProvider calls; bodies arrive with their subclasses ----------

    /** No-op until the mines generator is ported (wave 2). */
    public void generateMines(CityWorldGenerator generator, InitialBlocks chunk) {
    }

    /** No-op until the mines generator is ported (wave 2). */
    public void generateMines(CityWorldGenerator generator, SupportBlocks chunk) {
    }

    /** No-op until the bones generator is ported (wave 2). */
    public void generateBones(CityWorldGenerator generator, SupportBlocks chunk) {
    }

    /** No-op until ore placement is ported (P4/P5, with {@code OreProvider}). */
    public void generateOres(CityWorldGenerator generator, SupportBlocks chunk) {
    }
}
