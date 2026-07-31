package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BlockFace;
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

    /** Only the built level bands are hollow; everything else stays solid rock (no hollowed-out mountain).
     *  The entrance lobby gets a taller top band; a second level sits in a band below the first. */
    @Override
    public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
        return vaultIsValidStrataY(blockY, bottomOfBunker, topOfBunker, entrance);
    }

    static final int NUM_LEVELS = 4; // level 0 (entry) + three below

    /** Floor of level {@code k} — the levels stack every 8 blocks below the entry level. */
    static int levelFloor(int bottom, int k) {
        return floorY(bottom) - 8 * k;
    }

    static boolean vaultIsValidStrataY(int blockY, int bottom, int top, boolean entrance) {
        int c0 = entrance ? lobbyCeilingY(bottom, top) : ceilingY(bottom, top); // level 0 is taller for the lobby
        if (blockY >= floorY(bottom) && blockY <= c0)
            return false;
        for (int k = 1; k < NUM_LEVELS; k++) {
            int fk = levelFloor(bottom, k);
            if (blockY >= fk && blockY <= fk + 6)
                return false;
        }
        return true;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return levelFloor(bottomOfBunker, NUM_LEVELS - 1) - 1; // extend down to cover the deepest level
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return false; // no mine shafts cutting through the vault
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        boolean[] walls = wallFlags(platmap, platX, platZ);
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        if (entrance) {
            int num = vaultNumber();
            generateLobby(chunk, chunkOdds, bottomOfBunker, topOfBunker, blockYs.getBlockY(2, 1), num, walls);
            generator.reportLocation("Vault " + num, chunk);
        } else {
            generateVaultHall(chunk, bottomOfBunker, topOfBunker, walls, oX, oZ);
        }
        generateLowerLevels(chunk, bottomOfBunker, walls, oX, oZ, entrance, getChunkX(), getChunkZ());
    }

    /** The corridor-and-rooms levels beneath level 0, plus stairwell shafts down between levels (spread
     *  across interior chunks, not the entrance chunk's top level). */
    static void generateLowerLevels(SupportBlocks chunk, int bottom, boolean[] walls, int oX, int oZ,
            boolean entrance, int chunkX, int chunkZ) {
        for (int k = 1; k < NUM_LEVELS; k++)
            generateLevel(chunk, levelFloor(bottom, k), levelFloor(bottom, k) + 6, walls, oX, oZ);
        for (int k = 0; k < NUM_LEVELS - 1; k++)
            if (!(entrance && k == 0) && Math.floorMod(chunkX * 7 + chunkZ * 13 + k * 29, 3) == 0)
                stairwellDown(chunk, levelFloor(bottom, k), levelFloor(bottom, k + 1));
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

    /** Ceiling of the entrance lobby — taller than the hall, so the big door + decor read as grand. */
    static int lobbyCeilingY(int bottom, int top) {
        return Math.min(bottom + 12, top - 1);
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

    /** The vault interior of one chunk: the corridor-and-rooms level. */
    static void generateVaultHall(SupportBlocks chunk, int bottom, int top, boolean[] wall, int oX, int oZ) {
        generateLevel(chunk, floorY(bottom), ceilingY(bottom, top), wall, oX, oZ);
    }

    /**
     * One vault level in the hollow band {@code [floorY, ceilY]}: a central 2-wide cross corridor that
     * reaches all four chunk edges (so connected chunks tile into one navigable corridor grid — the "maze"),
     * with a room in each quadrant opening onto the corridor through a doorway. The rooms are furnished in a
     * later phase; for now they're lit empty cells. Corridor centre is x/z 7-8 so it lines up with the
     * centre-positioned lobby door.
     */
    static void generateLevel(SupportBlocks chunk, int floorY, int ceilY, boolean[] wall, int oX, int oZ) {
        chunk.setLayer(floorY, FLOOR);
        chunk.setLayer(ceilY, CEIL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 16, Material.AIR); // clear

        // partition into a cross corridor (x7-8, z7-8) + four quadrant rooms
        for (int z = 0; z < 16; z++)
            if (z < 7 || z > 8) {
                chunk.setBlocks(6, floorY + 1, ceilY, z, WALL);
                chunk.setBlocks(9, floorY + 1, ceilY, z, WALL);
            }
        for (int x = 0; x < 16; x++)
            if (x < 7 || x > 8) {
                chunk.setBlocks(x, floorY + 1, ceilY, 6, WALL);
                chunk.setBlocks(x, floorY + 1, ceilY, 9, WALL);
            }
        // a doorway from each quadrant room onto the corridor
        chunk.setBlocks(6, 7, floorY + 1, floorY + 4, 2, 4, Material.AIR); // NW
        chunk.setBlocks(9, 10, floorY + 1, floorY + 4, 2, 4, Material.AIR); // NE
        chunk.setBlocks(6, 7, floorY + 1, floorY + 4, 12, 14, Material.AIR); // SW
        chunk.setBlocks(9, 10, floorY + 1, floorY + 4, 12, 14, Material.AIR); // SE

        // finished perimeter walls only on rock-facing sides (so the vault edge isn't bare rock)
        if (wall[0])
            chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 1, WALL);
        if (wall[1])
            chunk.setBlocks(0, 16, floorY + 1, ceilY, 15, 16, WALL);
        if (wall[2])
            chunk.setBlocks(15, 16, floorY + 1, ceilY, 0, 16, WALL);
        if (wall[3])
            chunk.setBlocks(0, 1, floorY + 1, ceilY, 0, 16, WALL);

        // lights — corridor ceiling + each room
        for (int[] p : new int[][] { { 7, 3 }, { 8, 12 }, { 3, 7 }, { 12, 8 }, { 3, 3 }, { 12, 3 }, { 3, 12 },
                { 12, 12 } })
            chunk.setBlock(p[0], ceilY, p[1], LIGHT);

        // a copper trim band at head height along the corridor walls (Fallout-industrial accent) — only where
        // there's actually a wall, so it never floats in the corridor or a doorway
        for (int i = 0; i < 16; i++) {
            copperTrim(chunk, 6, floorY + 4, i);
            copperTrim(chunk, 9, floorY + 4, i);
            copperTrim(chunk, i, floorY + 4, 6);
            copperTrim(chunk, i, floorY + 4, 9);
        }

        // divide some merged rooms into smaller isolated rooms (coherent via the corner hash) so room sizes
        // vary — a divided quadrant walls its corner-facing edges; an open one stays merged into a big hall
        divideQuadrant(chunk, floorY, ceilY, 0, 5, 0, 5, oX, oZ); // NW, corner (oX,oZ)
        divideQuadrant(chunk, floorY, ceilY, 10, 15, 0, 5, oX + 16, oZ); // NE
        divideQuadrant(chunk, floorY, ceilY, 0, 5, 10, 15, oX, oZ + 16); // SW
        divideQuadrant(chunk, floorY, ceilY, 10, 15, 10, 15, oX + 16, oZ + 16); // SE

        // furnish each quadrant — the room TYPE is keyed to the chunk-corner it belongs to, so all four
        // quadrants of a room merged across chunk boundaries agree and it reads as one coherent room
        furnishRoom(chunk, floorY, 1, 5, 1, 5, oX, oZ); // NW quadrant -> corner (oX, oZ)
        furnishRoom(chunk, floorY, 10, 14, 1, 5, oX + 16, oZ); // NE -> (oX+16, oZ)
        furnishRoom(chunk, floorY, 1, 5, 10, 14, oX, oZ + 16); // SW -> (oX, oZ+16)
        furnishRoom(chunk, floorY, 10, 14, 10, 14, oX + 16, oZ + 16); // SE -> (oX+16, oZ+16)
    }

    static final Material[] BEDS = { Material.WHITE_BED, Material.LIGHT_GRAY_BED, Material.LIGHT_BLUE_BED };

    /**
     * Furnish one quadrant. The room TYPE comes from the chunk-corner {@code (cornerX, cornerZ)} this
     * quadrant belongs to (so merged rooms are coherent); the per-quadrant layout varies by its own
     * position. Fallout-industrial × Black-Mesa mix: mostly living quarters, some labs/offices, the odd
     * hydroponics or mess. Everything is guarded with empty-cell checks so nothing lands in a wall/doorway.
     */
    static void furnishRoom(SupportBlocks chunk, int floorY, int x1, int x2, int z1, int z2, int cornerX,
            int cornerZ) {
        int fy = floorY + 1; // furniture stands on the floor
        switch (Math.floorMod(cornerX * 13 + cornerZ * 7, 10)) {
        case 0, 1, 2, 3 -> livingQuarters(chunk, fy, x1, x2, z1, z2);
        case 4, 5 -> office(chunk, fy, x1, x2, z1, z2);
        case 6 -> lab(chunk, fy, x1, x2, z1, z2);
        case 7 -> storage(chunk, fy, x1, x2, z1, z2);
        case 8 -> hydroponics(chunk, floorY, x1, x2, z1, z2);
        default -> messHall(chunk, fy, x1, x2, z1, z2); // 9
        }
    }

    private static void messHall(SupportBlocks chunk, int fy, int x1, int x2, int z1, int z2) {
        int tx = x1 + 1, tz = z1 + 1; // a dining table with chairs, and a bit of kitchen
        if (chunk.isEmpty(tx, fy, tz) && !chunk.isEmpty(tx, fy - 1, tz))
            chunk.setTable(tx, fy, tz, Material.QUARTZ_PILLAR, Material.SMOOTH_QUARTZ_SLAB);
        chairAt(chunk, tx - 1, fy, tz, BlockFace.WEST);
        chairAt(chunk, tx + 1, fy, tz, BlockFace.EAST);
        put(chunk, x2, fy, z1, Material.BARREL); // kitchen store
        put(chunk, x2, fy, z2, Material.CAULDRON); // a pot
    }

    private static void chairAt(SupportBlocks chunk, int x, int fy, int z, BlockFace facing) {
        if (chunk.isEmpty(x, fy, z) && !chunk.isEmpty(x, fy - 1, z))
            chunk.setBlock(x, fy, z, Material.QUARTZ_STAIRS, facing);
    }

    private static void put(SupportBlocks chunk, int x, int y, int z, Material m) {
        if (chunk.isEmpty(x, y, z) && !chunk.isEmpty(x, y - 1, z))
            chunk.setBlock(x, y, z, m);
    }

    private static void copperTrim(SupportBlocks chunk, int x, int y, int z) {
        if (!chunk.isEmpty(x, y, z)) // only dress an actual wall, never float in the corridor/doorway
            chunk.setBlock(x, y, z, Material.CUT_COPPER);
    }

    /** Wall a quadrant's chunk-boundary (corner-facing) edges so a merged room breaks into smaller isolated
     *  rooms — decided by the corner hash so all four quadrants of a merged room agree. ~Half stay open. */
    static void divideQuadrant(SupportBlocks chunk, int floorY, int ceilY, int x1, int x2, int z1, int z2,
            int cornerX, int cornerZ) {
        int h = cornerX * 374761393 + cornerZ * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        if ((h & 1) != 0)
            return; // ~half the merged rooms stay open (big); the rest divide into smaller rooms
        int xe = x1 == 0 ? 0 : 15; // the chunk-edge on the corner side
        int ze = z1 == 0 ? 0 : 15;
        for (int z = z1; z <= z2; z++)
            chunk.setBlocks(xe, floorY + 1, ceilY, z, WALL);
        for (int x = x1; x <= x2; x++)
            chunk.setBlocks(x, floorY + 1, ceilY, ze, WALL);
    }

    private static void livingQuarters(SupportBlocks chunk, int fy, int x1, int x2, int z1, int z2) {
        if (z1 + 1 <= z2 && chunk.isEmpty(x1, fy, z1) && chunk.isEmpty(x1, fy, z1 + 1)
                && !chunk.isEmpty(x1, fy - 1, z1))
            chunk.setBed(x1, fy, z1, BEDS[Math.floorMod(x1 + z1, BEDS.length)], BlockFace.SOUTH); // head to the wall
        put(chunk, x2, fy, z1, Material.CHEST); // footlocker
        put(chunk, x1, fy, z2, Material.CRAFTING_TABLE);
        put(chunk, x2, fy, z2, Material.POTTED_FERN);
    }

    private static void office(SupportBlocks chunk, int fy, int x1, int x2, int z1, int z2) {
        put(chunk, x1, fy, z1, Material.QUARTZ_BLOCK); // desk
        put(chunk, x1, fy + 1, z1, Material.LECTERN); // a terminal on it
        if (chunk.isEmpty(x1 + 1, fy, z1) && !chunk.isEmpty(x1 + 1, fy - 1, z1))
            chunk.setBlock(x1 + 1, fy, z1, Material.QUARTZ_STAIRS, BlockFace.EAST); // chair facing the desk
        put(chunk, x2, fy, z2, Material.CHEST); // filing
        put(chunk, x2, fy, z1, Material.BARREL);
    }

    private static void lab(SupportBlocks chunk, int fy, int x1, int x2, int z1, int z2) {
        put(chunk, x1, fy, z1, Material.BREWING_STAND);
        put(chunk, x1 + 1, fy, z1, Material.CAULDRON);
        put(chunk, x2, fy, z2, Material.CHISELED_BOOKSHELF);
        put(chunk, x2, fy, z1, Material.CRAFTING_TABLE);
        put(chunk, x1, fy, z2, Material.COPPER_BULB); // a lit lab lamp
    }

    private static void storage(SupportBlocks chunk, int fy, int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x += 2) // a row of crates along the back wall
            put(chunk, x, fy, z1, Material.BARREL);
        put(chunk, x1, fy, z2, Material.CHEST);
        put(chunk, x2, fy, z2, Material.CHEST);
    }

    private static void hydroponics(SupportBlocks chunk, int floorY, int x1, int x2, int z1, int z2) {
        for (int x = x1; x <= x2; x++)
            for (int z = z1; z <= z2; z++) {
                boolean water = ((x - x1) & 1) == 1; // alternating water channels
                chunk.setBlock(x, floorY, z, water ? Material.WATER : Material.FARMLAND);
                if (!water && chunk.isEmpty(x, floorY + 1, z))
                    chunk.setBlock(x, floorY + 1, z, Material.WHEAT, 1.0); // grown crops
            }
    }

    /** A ladder shaft in the SE room dropping from this level ({@code floorY1}) to the one below
     *  ({@code floorY2}) — the "lift" down between levels. */
    static void stairwellDown(SupportBlocks chunk, int floorY1, int floorY2) {
        int x = 12, z = 12; // inside the SE quadrant room
        chunk.setBlocks(x - 1, floorY2, floorY1 + 1, z, WALL); // solid backing for the ladder across both levels
        chunk.setBlocks(x, floorY2 + 1, floorY1 + 1, z, Material.AIR); // the shaft (a hole through the level-1 floor)
        chunk.setBlocks(x, floorY2 + 1, floorY1 + 1, z + 1, Material.AIR); // 1x2 so you can step onto the ladder
        chunk.setLadder(x, floorY2 + 1, floorY1 + 1, z, BlockFace.EAST); // faces east, backs onto x-1
        chunk.setBlock(x + 1, floorY1 + 1, z, Material.IRON_BARS); // a little rail round the hole up top
        chunk.setBlock(x + 1, floorY1 + 1, z + 1, Material.IRON_BARS);
    }

    /** The entrance chunk as a tall sealed LOBBY: full walls + decor, the surface-shaft ladder + hatch, and
     *  the ONLY ways into the vault interior beyond — a grand part-open blast door and a glass-walled security
     *  booth you route through (in one side, out the other). */
    static void generateLobby(SupportBlocks chunk, Odds odds, int bottom, int top, int surfaceY, int num,
            boolean[] wall) {
        int floorY = floorY(bottom), ceilY = lobbyCeilingY(bottom, top);
        chunk.setLayer(floorY, FLOOR);
        chunk.setLayer(ceilY, CEIL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 16, Material.AIR);

        // full perimeter walls — the doors are the only way through
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 0, 1, WALL);
        chunk.setBlocks(0, 16, floorY + 1, ceilY, 15, 16, WALL);
        chunk.setBlocks(0, 1, floorY + 1, ceilY, 0, 16, WALL);
        chunk.setBlocks(15, 16, floorY + 1, ceilY, 0, 16, WALL);

        decorateLobby(chunk, floorY, ceilY);
        surfaceShaft(chunk, floorY, ceilY, surfaceY);

        int side = doorSide(wall);
        bigVaultDoor(chunk, floorY, side);
        securityRoom(chunk, floorY, ceilY, side);
        chunk.setSignPost(13, floorY + 1, 8, Material.OAK_SIGN, BlockFace.WEST,
                new String[] { "VAULT", Integer.toString(num) });
    }

    /** Industrial dressing (Fallout × Black-Mesa): iron pillars, a copper trim ring, a lit ceiling grid,
     *  hanging lanterns, and a floor accent border. */
    static void decorateLobby(SupportBlocks chunk, int floorY, int ceilY) {
        for (int[] p : new int[][] { { 1, 1 }, { 14, 1 }, { 1, 14 }, { 14, 14 } }) // corner pillars only —
            chunk.setBlocks(p[0], floorY + 1, ceilY, p[1], PILLAR); // mid-wall ones stood in the doorway
        chunk.setBlocks(1, 15, ceilY - 1, ceilY, 1, 2, Material.CUT_COPPER); // copper trim ring under the ceiling
        chunk.setBlocks(1, 15, ceilY - 1, ceilY, 14, 15, Material.CUT_COPPER);
        chunk.setBlocks(1, 2, ceilY - 1, ceilY, 1, 15, Material.CUT_COPPER);
        chunk.setBlocks(14, 15, ceilY - 1, ceilY, 1, 15, Material.CUT_COPPER);
        for (int x = 3; x < 15; x += 4)
            for (int z = 3; z < 15; z += 4)
                chunk.setBlock(x, ceilY, z, LIGHT); // lit ceiling grid
        for (int[] p : new int[][] { { 3, 3 }, { 12, 3 }, { 3, 12 }, { 12, 12 } })
            chunk.setHangingLantern(p[0], ceilY - 2, p[1], Material.LANTERN);
        chunk.setBlocks(1, 15, floorY, floorY + 1, 1, 2, Material.GRAY_CONCRETE); // floor accent border
        chunk.setBlocks(1, 15, floorY, floorY + 1, 14, 15, Material.GRAY_CONCRETE);
        chunk.setBlocks(1, 2, floorY, floorY + 1, 1, 15, Material.GRAY_CONCRETE);
        chunk.setBlocks(14, 15, floorY, floorY + 1, 1, 15, Material.GRAY_CONCRETE);
    }

    /** A ladder up the lobby's north wall to a surface hatch, with a SOLID concrete backing column the whole
     *  way (so caves the shaft passes through can't leave a ladderless gap) and reaching this column's own
     *  surface height (not another column's, which left it short). */
    static void surfaceShaft(SupportBlocks chunk, int floorY, int ceilY, int surfaceY) {
        // hut floor ≈ the real ground top (+3: getBlockY reports the base terrain, a few short of the top)
        int hutFloor = surfaceY + 3;
        chunk.setBlocks(2, ceilY, hutFloor + 4, 0, WALL); // solid backing all the way up (incl. the hut ladder)
        chunk.setBlocks(2, ceilY, hutFloor + 2, 1, Material.AIR); // the climb slot
        chunk.setBlocks(2, ceilY, hutFloor + 2, 2, Material.AIR); // headroom beside the ladder
        chunk.setLadder(2, floorY + 1, hutFloor + 2, 1, BlockFace.SOUTH); // up to the hut floor
        surfaceHut(chunk, hutFloor);
    }

    /** A small concrete hut on the surface over the shaft, with an iron door (a button each side) at ground
     *  level — a proper little vault entrance building instead of a hatch poking out of the hillside. */
    static void surfaceHut(SupportBlocks chunk, int hutFloor) {
        // 3x3 footprint (x1..3, z1..3): the ladder emerges at (2,1), you step south and out the door at (2,3)
        chunk.setBlocks(1, 4, hutFloor, hutFloor + 1, 1, 4, WALL); // floor pad
        chunk.setBlocks(1, 4, hutFloor + 1, hutFloor + 4, 1, 4, WALL); // fill the box...
        chunk.setBlocks(2, 3, hutFloor + 1, hutFloor + 4, 1, 3, Material.AIR); // ...then hollow the interior
        chunk.setBlocks(1, 4, hutFloor + 4, hutFloor + 5, 1, 4, WALL); // roof
        // foundation skirt so the hut plints into the slope instead of floating on the downhill side
        chunk.setBlocks(1, 4, hutFloor - 3, hutFloor, 1, 2, WALL);
        chunk.setBlocks(1, 4, hutFloor - 3, hutFloor, 3, 4, WALL);
        chunk.setBlocks(1, 2, hutFloor - 3, hutFloor, 1, 4, WALL);
        chunk.setBlocks(3, 4, hutFloor - 3, hutFloor, 1, 4, WALL);
        chunk.setBlocks(2, hutFloor - 3, hutFloor + 3, 1, Material.AIR); // keep the ladder column open (skirt + hut)
        chunk.setLadder(2, hutFloor, hutFloor + 2, 1, BlockFace.SOUTH); // a couple more rungs into the hut
        chunk.setBlocks(2, hutFloor + 1, hutFloor + 3, 3, Material.AIR); // door opening
        chunk.setDoor(2, hutFloor + 1, 3, Material.IRON_DOOR_BLOCK, BlockFace.SOUTH);
        chunk.setWallButton(2, hutFloor + 1, 2, Material.STONE_BUTTON, BlockFace.WEST); // inside opener (east wall)
        chunk.setWallButton(3, hutFloor + 1, 4, Material.STONE_BUTTON, BlockFace.SOUTH); // outside opener (SE wall)
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

    /** A grand 7x7 blast door recessed in the {@code side} wall — an iron frame ring, a copper gear ring, and
     *  a concrete body — part-open (a 3-wide x 4-tall walkway carved through the bottom-centre). */
    static void bigVaultDoor(SupportBlocks chunk, int floorY, int side) {
        boolean zWall = side == 0 || side == 1;
        int fc = side == 1 || side == 2 ? 15 : 0; // S/E wall at 15, N/W at 0
        int cy = floorY + 4;
        for (int a = -3; a <= 3; a++)
            for (int dy = -3; dy <= 3; dy++) {
                double d = Math.hypot(a, dy);
                Material m = d >= 2.4 && d <= 3.4 ? Material.IRON_BLOCK
                        : d >= 1.2 && d < 2.4 ? Material.CUT_COPPER
                        : d < 1.2 ? WALL : null;
                if (m != null)
                    putWall(chunk, zWall, 8 + a, cy + dy, fc, m);
            }
        // part-open walkway — 4 wide (x6-9) so it frames the 2-wide corridor between its partition walls
        // (x6/x9) instead of sitting half a block off, and a block taller
        for (int a = -2; a <= 1; a++)
            for (int dy = 0; dy <= 4; dy++)
                putWall(chunk, zWall, 8 + a, floorY + 1 + dy, fc, Material.AIR);
    }

    /** A glass-walled security booth in the corner of the door wall: you enter it from the lobby and exit the
     *  far side into the vault interior — a "secret" way in that's watched over through the windows. */
    static void securityRoom(SupportBlocks chunk, int floorY, int ceilY, int side) {
        int roomTop = Math.min(floorY + 4, ceilY - 1);
        // roof over the booth footprint (along 12..15, depth 0..4 — clear of the big door, which reaches x=11)
        for (int a = 12; a <= 15; a++)
            for (int dp = 0; dp <= 4; dp++)
                setAt(chunk, side, a, roomTop, dp, CEIL);
        // glass dividers facing the lobby: the depth=4 wall (along 12..15) and the along=12 wall (depth 0..4)
        for (int a = 12; a <= 15; a++)
            for (int y = floorY + 1; y <= roomTop; y++)
                setAt(chunk, side, a, y, 4, y >= floorY + 2 ? Material.GLASS : WALL);
        for (int dp = 0; dp <= 4; dp++)
            for (int y = floorY + 1; y <= roomTop; y++)
                setAt(chunk, side, 12, y, dp, y >= floorY + 2 ? Material.GLASS : WALL);
        // entry door from the lobby (depth=4 divider) + exit door to the interior (depth=0 perimeter wall)
        doorAt(chunk, side, 13, floorY, 4);
        doorAt(chunk, side, 13, floorY, 0);
        setAt(chunk, side, 13, roomTop, 2, LIGHT); // a light inside the booth
    }

    // --- wall-plane / booth coordinate mappers -------------------------------------------------

    /** Place at a wall-plane cell: {@code zWall} means the wall runs along X at fixed z={@code fc}. */
    private static void putWall(SupportBlocks chunk, boolean zWall, int along, int y, int fc, Material m) {
        if (zWall)
            chunk.setBlock(along, y, fc, m);
        else
            chunk.setBlock(fc, y, along, m);
    }

    private static void setAt(SupportBlocks chunk, int side, int along, int y, int depth, Material m) {
        chunk.setBlock(mapX(side, along, depth), y, mapZ(side, along, depth), m);
    }

    private static void doorAt(SupportBlocks chunk, int side, int along, int floorY, int depth) {
        chunk.setDoor(mapX(side, along, depth), floorY + 1, mapZ(side, along, depth), Material.OAK_DOOR,
                outwardFace(side));
    }

    /** Map (along the wall, depth into the lobby from the door wall) to local X for the given door side. */
    private static int mapX(int side, int along, int depth) {
        return switch (side) {
        case 2 -> 15 - depth; // east wall
        case 3 -> depth; // west wall
        default -> along; // N/S walls run along X
        };
    }

    private static int mapZ(int side, int along, int depth) {
        return switch (side) {
        case 1 -> 15 - depth; // south wall
        case 0 -> depth; // north wall
        default -> along; // E/W walls run along Z
        };
    }

    private static BlockFace outwardFace(int side) {
        return switch (side) {
        case 0 -> BlockFace.NORTH;
        case 2 -> BlockFace.EAST;
        case 3 -> BlockFace.WEST;
        default -> BlockFace.SOUTH;
        };
    }
}
