package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * One chunk-slice of a BIG glass biodome spanning an NxN block of chunks. Modelled on {@code ClipboardLot}:
 * the placer picks the biome and size once and hands every chunk its offset ({@code offX,offZ}) within the
 * footprint; each chunk reconstructs the shared structure centre from {@code (chunkX-offX)} and draws only
 * the part of the hemisphere that falls in its own 0..15 columns, so the slices join seamlessly with no
 * cross-chunk writes. See PORTING.md "multi-chunk structures".
 */
public class BigBiodomeLot extends IsolatedLot {

    // must match BiodomeLot.Dome ordering — the placer rolls an index so every chunk agrees on the biome
    static final int JUNGLE = 0, SWAMP = 1, FLOWER_FOREST = 2, PALE_GARDEN = 3, CAVE = 4, END = 5, NETHER = 6;
    public static final int BIOMES = 7;

    private final int sizeX, sizeZ, offX, offZ, biome, radius;

    public BigBiodomeLot(PlatMap platmap, int chunkX, int chunkZ, int sizeX, int sizeZ, int offX, int offZ,
            int biome) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.offX = offX;
        this.offZ = offZ;
        this.biome = biome;
        // fit the dome inside the footprint. -2 (not -1) keeps the south doorway off the chunk seam:
        // at -1 a 2x2 dome's door lands at local z=15, so the sign+step-out (which write z+1, the next
        // chunk) were silently skipped — that was the "no sign on big domes" bug.
        this.radius = Math.min(sizeX, sizeZ) * 8 - 2;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new BigBiodomeLot(platmap, chunkX, chunkZ, sizeX, sizeZ, offX, offZ, biome);
    }

    @Override
    public boolean allowsWildDecoration() {
        return false;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return generator.streetLevel;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return generator.streetLevel;
    }

    private double centreX() {
        return (getChunkX() - offX) * 16 + sizeX * 8.0;
    }

    private double centreZ() {
        return (getChunkZ() - offZ) * 16 + sizeZ * 8.0;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, radius + 4, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int y = generator.streetLevel;
        if (offX == 0 && offZ == 0)
            generator.reportLocation("biodome", "Big Biodome " + sizeX + "x" + sizeZ, chunk);

        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        double cX = centreX(), cZ = centreZ();
        Material floor = floorFor(biome);

        // floor + a watertight midpoint-sphere shell, computed per column from the shared structure centre
        // (glass where the 3D distance rounds to radius — gap-free on the slopes, unlike stacked rings)
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
                if (d <= radius) // floor right up to the wall (was radius-1, leaving a ring of bare grass)
                    chunk.setBlock(lx, y, lz, floor);
                if (d <= radius + 0.5)
                    for (int dy = 0; dy <= radius; dy++)
                        if (Math.round(Math.sqrt(d * d + (double) dy * dy)) == radius)
                            chunk.setBlock(lx, y + 1 + dy, lz, dy == 0 ? Material.SMOOTH_STONE : Material.GLASS);
            }

        // light terraforming so the big floor isn't dead flat
        for (int i = 0; i < 5; i++) {
            int lx = chunkOdds.getRandomInt(16), lz = chunkOdds.getRandomInt(16);
            if (Math.hypot(oX + lx - cX, oZ + lz - cZ) > radius - 3)
                continue;
            int h = 1 + chunkOdds.getRandomInt(2);
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    int mx = lx + dx, mz = lz + dz;
                    if (mx < 0 || mx > 15 || mz < 0 || mz > 15
                            || Math.hypot(oX + mx - cX, oZ + mz - cZ) > radius - 2)
                        continue;
                    for (int j = 1; j <= (dx == 0 && dz == 0 ? h : h - 1); j++)
                        chunk.setBlock(mx, y + j, mz, floor);
                }
        }

        // one doorway where the south wall meets the ground — only the chunk that owns that column draws it
        int doorWX = (int) Math.round(cX);
        int doorWZ = (int) Math.round(cZ + radius);
        if (doorWX >= oX && doorWX < oX + 16 && doorWZ >= oZ && doorWZ < oZ + 16) {
            int lx = doorWX - oX, lz = doorWZ - oZ;
            chunk.setBlocks(lx, y + 1, y + 2, lz, Material.AIR); // a clean 2-tall opening in the base wall
            chunk.setDoor(lx, y + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
            if (lz + 1 < 16) {
                chunk.setBlocks(lx, y + 1, y + 2, lz + 1, Material.AIR); // step out
                chunk.setWallSign(lx, y + 3, lz + 1, Material.OAK_WALL_SIGN, BlockFace.SOUTH,
                        new String[] { "Biodome", nameFor(biome) }); // biome name over the door
            }
        }

        fill(generator, chunk, oX, oZ, cX, cZ, y);
    }

    /** Scatter a little biome flora across this chunk's floor, and spawn the themed mob near the centre. */
    private void fill(CityWorldGenerator generator, RealBlocks chunk, int oX, int oZ, double cX, double cZ, int y) {
        // swamp/cave domes get water pools (the swamp "froggy" dome had none)
        if (biome == SWAMP || biome == CAVE)
            for (int i = 0; i < 4; i++) {
                int lx = 2 + chunkOdds.getRandomInt(12), lz = 2 + chunkOdds.getRandomInt(12);
                if (Math.hypot(oX + lx - cX, oZ + lz - cZ) > radius - 3)
                    continue;
                int r = 1 + chunkOdds.getRandomInt(2);
                for (int dx = -r; dx <= r; dx++)
                    for (int dz = -r; dz <= r; dz++) {
                        int px = lx + dx, pz = lz + dz;
                        if (px < 1 || px > 14 || pz < 1 || pz > 14 || Math.hypot(dx, dz) > r + 0.3)
                            continue;
                        chunk.setBlock(px, y - 1, pz, Material.WATER);
                        chunk.setBlock(px, y, pz, Material.WATER); // a 2-deep pool at floor level
                    }
            }
        // the End dome grows proper chorus plants (connected stalks + a flower), like the single dome
        if (biome == END)
            for (int i = 0; i < 5; i++) {
                int lx = 2 + chunkOdds.getRandomInt(12), lz = 2 + chunkOdds.getRandomInt(12);
                if (Math.hypot(oX + lx - cX, oZ + lz - cZ) > radius - 3 || !chunk.isEmpty(lx, y + 1, lz)
                        || chunk.isEmpty(lx, y, lz))
                    continue;
                chorusPlant(chunk, lx, y, lz);
            }
        Material plant = plantFor(biome), log = logFor(biome), leaf = leafFor(biome);
        for (int i = 0; i < 6; i++) {
            int lx = 2 + chunkOdds.getRandomInt(12), lz = 2 + chunkOdds.getRandomInt(12);
            double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
            if (d > radius - 2 || !chunk.isEmpty(lx, y + 1, lz) || chunk.isEmpty(lx, y, lz))
                continue;
            if (chunkOdds.playOdds(0.35) && log != null)
                smallTree(chunk, lx, y, lz, log, leaf);
            else if (plant != null)
                chunk.setBlock(lx, y + 1, lz, plant);
        }
        // themed mob near the structure centre — the chunk that holds the centre spawns it
        int mcx = (int) Math.round(cX), mcz = (int) Math.round(cZ);
        if (mcx >= oX && mcx < oX + 16 && mcz >= oZ && mcz < oZ + 16) {
            EntityType mob = mobFor(biome);
            if (mob != null)
                generator.spawnProvider.spawnAnimals(generator, chunk, chunkOdds, mcx - oX, y + 1, mcz - oZ, mob);
        }
    }

    /** A connected chorus stalk topped with a flower — connections set explicitly (worldgen setBlock skips
     *  the neighbour update that would otherwise join the cubes). Mirrors the single dome. */
    private void chorusPlant(RealBlocks chunk, int x, int y, int z) {
        int h = 2 + chunkOdds.getRandomInt(2); // 2..3 segments
        for (int j = 0; j < h; j++)
            chunk.setPipeBlock(x, y + 1 + j, z, Material.CHORUS_PLANT, BlockFace.DOWN, BlockFace.UP);
        chunk.setBlock(x, y + 1 + h, z, Material.CHORUS_FLOWER);
    }

    private void smallTree(RealBlocks chunk, int x, int y, int z, Material log, Material leaves) {
        int h = 3 + chunkOdds.getRandomInt(2);
        chunk.setBlocks(x, y + 1, y + 1 + h, z, log);
        for (int lx = x - 1; lx <= x + 1; lx++)
            for (int lz = z - 1; lz <= z + 1; lz++)
                for (int ly = y + h; ly <= y + h + 1; ly++)
                    if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16)
                        chunk.setLeaves(lx, ly, lz, leaves);
        chunk.setLeaves(x, y + h + 2, z, leaves);
    }

    private static String nameFor(int b) {
        return switch (b) {
        case JUNGLE -> "Jungle";
        case SWAMP -> "Swamp";
        case FLOWER_FOREST -> "Flower Forest";
        case PALE_GARDEN -> "Pale Garden";
        case CAVE -> "Cave";
        case END -> "The End";
        default -> "Nether";
        };
    }

    private static Material floorFor(int b) {
        return switch (b) {
        case JUNGLE, FLOWER_FOREST -> Material.GRASS_BLOCK;
        case SWAMP -> Material.MUD;
        case PALE_GARDEN -> Material.PALE_MOSS_BLOCK;
        case CAVE -> Material.STONE_BRICKS;
        case END -> Material.END_STONE;
        default -> Material.NETHERRACK;
        };
    }

    private static Material plantFor(int b) {
        return switch (b) {
        case JUNGLE -> Material.FERN;
        case SWAMP -> Material.BLUE_ORCHID;
        case FLOWER_FOREST -> Material.POPPY;
        case PALE_GARDEN -> Material.OPEN_EYEBLOSSOM;
        case CAVE -> Material.GLOW_LICHEN;
        case END -> Material.PURPUR_BLOCK;
        default -> Material.CRIMSON_FUNGUS;
        };
    }

    private static Material logFor(int b) {
        return switch (b) {
        case JUNGLE -> Material.JUNGLE_LOG;
        case SWAMP -> Material.MANGROVE_LOG;
        case FLOWER_FOREST -> Material.BIRCH_LOG;
        case PALE_GARDEN -> Material.PALE_OAK_LOG;
        default -> null;
        };
    }

    private static Material leafFor(int b) {
        return switch (b) {
        case JUNGLE -> Material.JUNGLE_LEAVES;
        case SWAMP -> Material.MANGROVE_LEAVES;
        case FLOWER_FOREST -> Material.BIRCH_LEAVES;
        case PALE_GARDEN -> Material.PALE_OAK_LEAVES;
        default -> Material.OAK_LEAVES;
        };
    }

    private static EntityType mobFor(int b) {
        return switch (b) {
        case JUNGLE -> EntityType.PARROT;
        case SWAMP -> EntityType.FROG;
        case FLOWER_FOREST -> EntityType.BEE;
        case CAVE -> EntityType.AXOLOTL;
        default -> null;
        };
    }
}
