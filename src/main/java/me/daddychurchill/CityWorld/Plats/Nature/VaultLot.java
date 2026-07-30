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
        boolean[] walls = wallFlags(platmap, platX, platZ);
        if (entrance) {
            int num = vaultNumber();
            generateLobby(chunk, chunkOdds, bottomOfBunker, topOfBunker, blockYs.getBlockY(8, 8), num, walls);
            generator.reportLocation("Vault " + num, chunk);
        } else {
            generateVaultHall(chunk, bottomOfBunker, topOfBunker, walls);
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

    /** The entrance chunk as a tight sealed LOBBY: full walls, the surface-shaft ladder + hatch, and the
     *  big vault door + a service door as the ONLY openings into the vault interior beyond. */
    static void generateLobby(SupportBlocks chunk, Odds odds, int bottom, int top, int surfaceY, int num,
            boolean[] wall) {
        int floorY = floorY(bottom), ceilY = ceilingY(bottom, top);
        chunk.setLayer(floorY, FLOOR);
        chunk.setLayer(ceilY, CEIL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 16, Material.AIR);

        // full perimeter walls — the doors are the only way through
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 1, WALL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 15, 16, WALL);
        chunk.setBlocks(0, 1, floorY + 1, ceilY, 0, 16, WALL);
        chunk.setBlocks(15, 16, floorY + 1, ceilY, 0, 16, WALL);

        for (int x = 3; x < 16; x += 6)
            for (int z = 3; z < 16; z += 6) {
                chunk.setBlock(x, ceilY, z, LIGHT);
                chunk.setBlock(x, floorY, z, LIGHT);
            }

        surfaceShaft(chunk, floorY, ceilY, surfaceY);

        int side = doorSide(wall);
        bigVaultDoor(chunk, floorY, side);
        secretDoor(chunk, floorY, side);
        chunk.setSignPost(8, floorY + 1, 8, Material.OAK_SIGN, BlockFace.NORTH,
                new String[] { "VAULT", Integer.toString(num) });
    }

    /** A ladder up one lobby wall (backed the whole way) through a carved slot in the rock to a surface hatch. */
    static void surfaceShaft(SupportBlocks chunk, int floorY, int ceilY, int surfaceY) {
        int topY = Math.max(surfaceY, ceilY + 4);
        chunk.setBlocks(2, ceilY, topY + 1, 1, Material.AIR); // slot up through the rock (col x=2, z=1)
        chunk.setBlocks(2, ceilY, topY + 1, 2, Material.AIR); // headroom beside the ladder
        chunk.setLadder(2, floorY + 1, topY, 1, BlockFace.SOUTH); // against the north wall (solid backing all the way)
        chunk.setBlock(2, topY, 1, Material.BIRCH_TRAPDOOR, BlockFace.SOUTH, Half.TOP); // hatch
    }

    /** Choose which wall the doors go in — a side facing the vault interior (a vault neighbour), if any. */
    static int doorSide(boolean[] wall) { // wall = {N, S, E, W}, true = faces rock
        if (!wall[1])
            return 1; // south
        if (!wall[2])
            return 2; // east
        if (!wall[0])
            return 0; // north
        if (!wall[3])
            return 3; // west
        return 1; // isolated vault: south fallback
    }

    /** A big 5x5 iron-framed blast door recessed in the {@code side} wall, part-open (a 3x3 walkway carved
     *  through the bottom-centre), leading into the vault interior. */
    static void bigVaultDoor(SupportBlocks chunk, int floorY, int side) {
        boolean zWall = side == 0 || side == 1;
        int fc = side == 1 || side == 2 ? 15 : 0; // S/E wall at 15, N/W at 0
        int cy = floorY + 3;
        for (int a = -2; a <= 2; a++)
            for (int dy = -2; dy <= 2; dy++) {
                double d = Math.hypot(a, dy);
                Material m = d >= 1.5 && d <= 2.6 ? Material.IRON_BLOCK : d < 1.5 ? WALL : null;
                if (m != null)
                    put(chunk, zWall, 8 + a, cy + dy, fc, m);
            }
        for (int a = -1; a <= 1; a++) // part-open: carve the walkway through the door + wall
            for (int dy = 0; dy <= 2; dy++)
                put(chunk, zWall, 8 + a, floorY + 1 + dy, fc, Material.AIR);
    }

    /** A plain service door flush in the same wall, a few blocks off the big door (the "secret" way in —
     *  the disguise/mechanism is a later polish pass). */
    static void secretDoor(SupportBlocks chunk, int floorY, int side) {
        boolean zWall = side == 0 || side == 1;
        int fc = side == 1 || side == 2 ? 15 : 0;
        int along = 13;
        put(chunk, zWall, along, floorY + 1, fc, Material.AIR);
        put(chunk, zWall, along, floorY + 2, fc, Material.AIR);
        BlockFace face = zWall ? (fc == 15 ? BlockFace.NORTH : BlockFace.SOUTH)
                : (fc == 15 ? BlockFace.WEST : BlockFace.EAST);
        if (zWall)
            chunk.setDoor(along, floorY + 1, fc, Material.OAK_DOOR, face);
        else
            chunk.setDoor(fc, floorY + 1, along, Material.OAK_DOOR, face);
    }

    private static void put(SupportBlocks chunk, boolean zWall, int along, int y, int fc, Material m) {
        if (zWall)
            chunk.setBlock(along, y, fc, m);
        else
            chunk.setBlock(fc, y, along, m);
    }
}
