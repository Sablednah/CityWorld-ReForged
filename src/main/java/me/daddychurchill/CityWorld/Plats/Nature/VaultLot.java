package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.world.level.block.state.properties.Half;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

/**
 * A rare Fallout-style Vault buried under high terrain (APOCALYPSE only) — a spawn-hub set piece. Built on
 * the buried-bunker machinery for seeding + repave-to-road, but with its own strata rule and hall so it
 * reads as a vault, not a bunker:
 *
 * <ul>
 * <li>the hollow Y-box is trimmed to ONLY the built hall band ({@link #isValidStrataY}), so the mountain
 *     above stays solid rock instead of a giant hollowed void;
 * <li>the hall gets finished concrete perimeter walls only where it meets rock (neighbour-aware), leaving
 *     the boundaries between connected vault chunks open so a run of them tiles into one hall;
 * <li>the entrance chunk drives a guaranteed ladder shaft up to the surface with a hatch + the cog door, so
 *     the vault is always reachable — the road-tunnel branch ({@link RoadThroughVaultLot}) is a bonus.</li>
 * </ul>
 *
 * <p>Spike scope: one lit hall level. The multi-level room set is the next phase (the Y-box already reaches
 * up under the terrain; deeper levels just add more hollow bands + fill).
 */
public class VaultLot extends BunkerLot {

    static final Material WALL = Material.LIGHT_GRAY_CONCRETE;
    static final Material FLOOR = Material.LIGHT_GRAY_CONCRETE;
    static final Material CEIL = Material.GRAY_CONCRETE;
    static final Material PILLAR = Material.IRON_BLOCK;
    static final Material LIGHT = Material.SEA_LANTERN;

    static final long VAULT_KEY = 246813579L; // distinct from BunkerLot's shared key

    private final boolean entrance;

    public VaultLot(PlatMap platmap, int chunkX, int chunkZ, boolean firstOne) {
        super(platmap, chunkX, chunkZ, firstOne);
        this.entrance = firstOne; // the platmap's first buried lot drives the guaranteed surface entrance
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
        return new RoadThroughVaultLot(platmap, chunkX, chunkZ, generator.connectedKeyForPavedRoads, false, this,
                entrance);
    }

    /** Only the hall band is hollow; everything else stays solid rock (no hollowed-out mountain). */
    @Override
    public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
        return vaultIsValidStrataY(blockY, bottomOfBunker, topOfBunker);
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return false; // no mine shafts cutting through the vault
    }

    static boolean vaultIsValidStrataY(int blockY, int bottom, int top) {
        return blockY < floorY(bottom) || blockY > ceilingY(bottom, top);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        generateVaultHall(chunk, bottomOfBunker, topOfBunker, wallFlags(platmap, platX, platZ));
        if (entrance) {
            int num = vaultNumber();
            generateEntrance(chunk, chunkOdds, bottomOfBunker, topOfBunker, blockYs.getBlockY(8, 8), num);
            generator.reportLocation("Vault " + num, chunk);
        }
    }

    int vaultNumber() {
        return Math.floorMod(getChunkX() * 31 + getChunkZ() * 17, 100);
    }

    /** Walkable floor of the hall (one above the box bottom, where the entry ladder lands). */
    static int floorY(int bottom) {
        return bottom + 1;
    }

    /** Ceiling of the hall — a ~6-high room, clamped inside the box. */
    static int ceilingY(int bottom, int top) {
        return Math.min(bottom + 7, top - 1);
    }

    /** {N, S, E, W}: true where this chunk faces rock (not another vault chunk) and so wants a finished wall. */
    static boolean[] wallFlags(PlatMap platmap, int platX, int platZ) {
        return new boolean[] { !isVault(lotAt(platmap, platX, platZ - 1)), !isVault(lotAt(platmap, platX, platZ + 1)),
                !isVault(lotAt(platmap, platX + 1, platZ)), !isVault(lotAt(platmap, platX - 1, platZ)) };
    }

    private static boolean isVault(PlatLot lot) {
        return lot instanceof VaultLot || lot instanceof RoadThroughVaultLot;
    }

    private static PlatLot lotAt(PlatMap platmap, int x, int z) {
        if (x < 0 || x >= PlatMap.Width || z < 0 || z >= PlatMap.Width)
            return null;
        return platmap.getLot(x, z);
    }

    /** The lit hall drawn into the hollow band: floor, ceiling, corner pillars, lights, and finished walls
     *  only on the sides that face rock (so connected chunks join into one hall). */
    static void generateVaultHall(SupportBlocks chunk, int bottom, int top, boolean[] wall) {
        int floorY = floorY(bottom);
        int ceilY = ceilingY(bottom, top);

        chunk.setLayer(floorY, FLOOR);
        chunk.setLayer(ceilY, CEIL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 16, Material.AIR); // clear the room

        if (wall[0])
            chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 1, WALL); // north
        if (wall[1])
            chunk.setBlocks(0, 16, floorY + 1, ceilY, 15, 16, WALL); // south
        if (wall[2])
            chunk.setBlocks(15, 16, floorY + 1, ceilY, 0, 16, WALL); // east
        if (wall[3])
            chunk.setBlocks(0, 1, floorY + 1, ceilY, 0, 16, WALL); // west

        chunk.setBlocks(0, floorY + 1, ceilY, 0, PILLAR); // corner pillars
        chunk.setBlocks(15, floorY + 1, ceilY, 0, PILLAR);
        chunk.setBlocks(0, floorY + 1, ceilY, 15, PILLAR);
        chunk.setBlocks(15, floorY + 1, ceilY, 15, PILLAR);

        for (int x = 3; x < 16; x += 6) // recessed floor + ceiling lights (mob-safe spawn hub)
            for (int z = 3; z < 16; z += 6) {
                chunk.setBlock(x, ceilY, z, LIGHT);
                chunk.setBlock(x, floorY, z, LIGHT);
            }
    }

    /** The guaranteed way in: a lined ladder shaft from the hall up to the surface with a hatch, plus the
     *  cog blast door and a VAULT signpost in the hall. */
    static void generateEntrance(SupportBlocks chunk, Odds odds, int bottom, int top, int surfaceY, int num) {
        int floorY = floorY(bottom);
        int ceilY = ceilingY(bottom, top);
        int topY = Math.max(surfaceY, ceilY + 4);

        // a 3x3 concrete-lined shaft (interior columns 6..8) up to the surface
        chunk.setBlocks(5, 10, floorY + 1, topY + 1, 5, 6, WALL); // N wall
        chunk.setBlocks(5, 10, floorY + 1, topY + 1, 9, 10, WALL); // S wall
        chunk.setBlocks(5, 6, floorY + 1, topY + 1, 5, 10, WALL); // W wall
        chunk.setBlocks(9, 10, floorY + 1, topY + 1, 5, 10, WALL); // E wall
        chunk.setBlocks(6, 9, floorY + 1, topY + 1, 6, 9, Material.AIR); // hollow the shaft
        // ladder on the shaft's north wall (z=5, which stays solid) so every rung has backing to cling to;
        // start a block above the floor so the bottom rung isn't buried in the floor slab
        chunk.setLadder(7, floorY + 1, topY, 6, BlockFace.SOUTH);
        chunk.setBlock(7, topY, 6, Material.BIRCH_TRAPDOOR, BlockFace.SOUTH, Half.TOP); // hatch at the top

        cogDoor(chunk, 8, floorY, 12); // the cog blast door across the hall
        chunk.setBlock(11, floorY, 7, PILLAR);
        chunk.setSignPost(11, floorY + 1, 7, Material.OAK_SIGN, BlockFace.EAST,
                new String[] { "VAULT", Integer.toString(num) });
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
