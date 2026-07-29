package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * A rare Fallout-style Vault buried under high terrain (APOCALYPSE only) — a spawn-hub set piece reached by
 * a branch off a road tunnel (see {@link RoadThroughVaultLot}). Built on the buried-bunker machinery: it
 * reuses {@link BunkerLot}'s hollow-Y-box (via {@code isValidStrataY} leaving the box unfilled) and its
 * repave-to-road plumbing, but draws a clean lit vault hall with a cog blast door instead of the bunker's
 * platform rig. Vault lots share their own connection key so a run of them under one mountain tiles into a
 * single continuous hall.
 *
 * <p>Spike scope: one lit atrium level with a cog door at the entrance. The multi-level room set (living
 * quarters / mess hall / hydroponics / storage) is the next phase — the Y-box already spans several
 * 16-block levels, so that is fill, not re-plumbing.
 */
public class VaultLot extends BunkerLot {

    static final Material FLOOR = Material.LIGHT_GRAY_CONCRETE;
    static final Material CEIL = Material.GRAY_CONCRETE;
    static final Material PILLAR = Material.IRON_BLOCK;
    static final Material LIGHT = Material.SEA_LANTERN;

    static final long VAULT_KEY = 246813579L; // distinct from BunkerLot's shared key

    public VaultLot(PlatMap platmap, int chunkX, int chunkZ, boolean firstOne) {
        super(platmap, chunkX, chunkZ, firstOne);
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new VaultLot(platmap, chunkX, chunkZ, false);
    }

    @Override
    public long getConnectedKey() {
        return connectedkey = VAULT_KEY;
    }

    @Override
    public RoadLot repaveLot(CityWorldGenerator generator, PlatMap platmap) {
        return new RoadThroughVaultLot(platmap, chunkX, chunkZ, generator.connectedKeyForPavedRoads, false, this);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        generateVaultHall(generator, chunk, chunkOdds, bottomOfBunker, topOfBunker);
    }

    /** Walkable floor of the vault hall (one above the box bottom, where the entry ladder lands). */
    static int floorY(int bottom) {
        return bottom + 1;
    }

    /** Ceiling of the hall — a ~6-high room, clamped inside the hollow box. */
    static int ceilingY(int bottom, int top) {
        return Math.min(bottom + 7, top - 1);
    }

    /**
     * The shared lit vault hall drawn into the hollow Y-box: a floor slab, a ceiling slab, corner pillars,
     * and recessed sea-lantern lights, so connected chunks tile into one continuous columned hall. Kept
     * fully lit on purpose — this is meant to be a mob-safe spawn hub.
     */
    static void generateVaultHall(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int bottom, int top) {
        int floorY = floorY(bottom);
        int ceilY = ceilingY(bottom, top);

        chunk.setLayer(floorY, FLOOR);
        chunk.setLayer(ceilY, CEIL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 16, Material.AIR); // clear the room (over any strata leftovers)

        chunk.setBlocks(0, floorY + 1, ceilY, 0, PILLAR); // corner pillars
        chunk.setBlocks(15, floorY + 1, ceilY, 0, PILLAR);
        chunk.setBlocks(0, floorY + 1, ceilY, 15, PILLAR);
        chunk.setBlocks(15, floorY + 1, ceilY, 15, PILLAR);

        for (int x = 3; x < 16; x += 6) // recessed floor + ceiling lights
            for (int z = 3; z < 16; z += 6) {
                chunk.setBlock(x, ceilY, z, LIGHT);
                chunk.setBlock(x, floorY, z, LIGHT);
            }
    }

    /** A vertical cog-style blast door disc against a wall at fixed {@code z}, centred on {@code cx}, rising
     *  from {@code baseY}. Concentric iron/concrete rings with a hub — the unmistakable vault-door read. */
    static void cogDoor(SupportBlocks chunk, int cx, int baseY, int z) {
        int cy = baseY + 3;
        for (int dx = -3; dx <= 3; dx++)
            for (int dy = -3; dy <= 3; dy++) {
                double d = Math.sqrt(dx * dx + (double) dy * dy);
                if (d > 3.4)
                    continue;
                int x = cx + dx, y = cy + dy;
                if (x < 0 || x > 15)
                    continue;
                Material m;
                if (d > 2.5 || d < 0.6)
                    m = Material.IRON_BLOCK; // outer frame ring + central hub
                else if (Math.round(d) == 2)
                    m = Material.GRAY_CONCRETE; // an inner ring, for the geared look
                else
                    m = Material.LIGHT_GRAY_CONCRETE; // door body
                chunk.setBlock(x, y, z, m);
            }
    }
}
