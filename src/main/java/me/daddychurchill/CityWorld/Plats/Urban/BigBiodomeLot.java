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
        this.radius = Math.min(sizeX, sizeZ) * 8 - 1; // fit the dome inside the footprint
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
            generator.reportLocation("Big Biodome " + sizeX + "x" + sizeZ, chunk);

        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        double cX = centreX(), cZ = centreZ();
        Material floor = floorFor(biome);

        // floor + dome shell, computed per column from the shared structure centre
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
                if (d <= radius - 1)
                    chunk.setBlock(lx, y, lz, floor);
                // vertical shell rings (walls) — one pass over height per column
                for (int dy = 0; dy <= radius; dy++) {
                    double r = Math.sqrt((double) radius * radius - (double) dy * dy);
                    if (d <= r + 0.5 && d > r - 0.8)
                        chunk.setBlock(lx, y + 1 + dy, lz, dy == 0 ? Material.SMOOTH_STONE : Material.GLASS);
                }
                // roof cap — closes the apex the rings can't reach
                if (d <= radius + 0.5) {
                    int domeY = (int) Math.round(Math.sqrt(Math.max(0.0, (double) radius * radius - d * d)));
                    if (domeY > 0)
                        chunk.setBlock(lx, y + 1 + domeY, lz, Material.GLASS);
                }
            }

        // one doorway on the south face of the whole structure — only the chunk that owns that column draws it
        int doorWX = (int) Math.round(cX);
        int doorWZ = (int) Math.round(cZ + radius - 1);
        if (doorWX >= oX && doorWX < oX + 16 && doorWZ >= oZ && doorWZ < oZ + 16) {
            int lx = doorWX - oX, lz = doorWZ - oZ;
            chunk.setBlocks(lx, y + 1, y + 3, lz, Material.AIR);
            chunk.setDoor(lx, y + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
        }

        fill(generator, chunk, oX, oZ, cX, cZ, y);
    }

    /** Scatter a little biome flora across this chunk's floor, and spawn the themed mob near the centre. */
    private void fill(CityWorldGenerator generator, RealBlocks chunk, int oX, int oZ, double cX, double cZ, int y) {
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
