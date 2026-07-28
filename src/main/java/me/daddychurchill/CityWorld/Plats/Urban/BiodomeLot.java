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
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A glass biodome: a domed greenhouse over a slice of a biome we couldn't fit into the open world — a
 * jungle, a swamp, a flower forest, a pale garden, a cave, and rarely the End and the Nether. Placed as a
 * one-off in park zones (like the zoo enclosures), so a run of them reads as a botanical/science park.
 */
public class BiodomeLot extends IsolatedLot {

    private enum Dome {
        JUNGLE, SWAMP, FLOWER_FOREST, PALE_GARDEN, CAVE, END, NETHER;
    }

    private final Dome dome;

    public BiodomeLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        // the End and Nether are the rare treats; the overworld biomes are the common domes
        int roll = chunkOdds.getRandomInt(16);
        dome = roll < 3 ? Dome.JUNGLE : roll < 6 ? Dome.SWAMP : roll < 9 ? Dome.FLOWER_FOREST
                : roll < 12 ? Dome.PALE_GARDEN : roll < 14 ? Dome.CAVE : roll == 14 ? Dome.END : Dome.NETHER;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new BiodomeLot(platmap, chunkX, chunkZ);
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

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, DataContext.FloorHeight * 3, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    private static final int CX = 8, CZ = 8, R = 7, HEIGHT = 9;

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int y = generator.streetLevel;
        generator.reportLocation("Biodome: " + dome, chunk);

        // the biome floor under the dome
        Material floor = floorFor(dome);
        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++)
                if (dist(x, z) <= R - 1)
                    chunk.setBlock(x, y, z, floor);

        buildDome(chunk, y);
        fill(generator, chunk, chunkOdds, y);
    }

    /** A gap-free clear glass hemisphere with a solid base ring and a clean doorway. The shell is built two
     *  ways and unioned so it never leaks: horizontal rings close the near-vertical walls, and one glass cap
     *  per column closes the roof/apex the rings alone leave open (the old "containment breach"). */
    private void buildDome(RealBlocks chunk, int y) {
        int base = y + 1;
        // 1) horizontal shell rings — the walls
        for (int dy = 0; dy <= R; dy++) {
            double r = Math.sqrt((double) R * R - (double) dy * dy);
            for (int x = 1; x < 15; x++)
                for (int z = 1; z < 15; z++) {
                    double d = dist(x, z);
                    if (d <= r + 0.5 && d > r - 0.8)
                        chunk.setBlock(x, base + dy, z, dy == 0 ? Material.SMOOTH_STONE : Material.GLASS);
                }
        }
        // 2) one glass cap per interior column at its analytic dome height — closes the roof/apex
        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++) {
                double d = dist(x, z);
                if (d > R + 0.5)
                    continue;
                int domeY = (int) Math.round(Math.sqrt(Math.max(0.0, (double) R * R - d * d)));
                if (domeY > 0)
                    chunk.setBlock(x, base + domeY, z, Material.GLASS);
            }
        // a clean one-wide doorway on the south face (carve just the door column + the step out, so the
        // glass wall either side stays intact)
        int dz = CZ + R - 1;
        chunk.setBlocks(CX, base, base + 1, dz, Material.AIR);
        chunk.setBlocks(CX, base, base + 1, dz + 1, Material.AIR);
        chunk.setDoor(CX, base, dz, Material.OAK_DOOR, BlockFace.SOUTH);
    }

    private double dist(int x, int z) {
        return Math.hypot(x - CX, z - CZ);
    }

    private boolean inside(int x, int z) {
        return dist(x, z) <= R - 1.5;
    }

    private static Material floorFor(Dome d) {
        return switch (d) {
        case JUNGLE, FLOWER_FOREST -> Material.GRASS_BLOCK;
        case SWAMP -> Material.MUD;
        case PALE_GARDEN -> Material.PALE_MOSS_BLOCK;
        case CAVE -> Material.STONE_BRICKS;
        case END -> Material.END_STONE;
        case NETHER -> Material.NETHERRACK;
        };
    }

    private void fill(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int y) {
        switch (dome) {
        case JUNGLE -> {
            tree(chunk, 6, y, 6, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 5);
            tree(chunk, 10, y, 9, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES, 4);
            scatter(chunk, odds, y, Material.FERN, 8);
            chunk.setBlock(9, y + 1, 6, Material.MELON);
            drape(chunk, odds, y, Material.VINE);
            spawn(generator, chunk, EntityType.PARROT);
        }
        case SWAMP -> {
            chunk.setBlocks(5, 10, y - 1, y + 1, 6, 10, Material.WATER);
            tree(chunk, 6, y, 6, Material.MANGROVE_LOG, Material.MANGROVE_LEAVES, 5);
            chunk.setBlock(7, y + 1, 5, Material.LILY_PAD);
            scatter(chunk, odds, y, Material.BLUE_ORCHID, 4);
            scatter(chunk, odds, y, Material.BROWN_MUSHROOM, 4);
            spawn(generator, chunk, EntityType.FROG);
        }
        case FLOWER_FOREST -> {
            tree(chunk, 6, y, 10, Material.BIRCH_LOG, Material.BIRCH_LEAVES, 5);
            scatter(chunk, odds, y, Material.POPPY, 5);
            scatter(chunk, odds, y, Material.DANDELION, 5);
            scatter(chunk, odds, y, Material.OXEYE_DAISY, 4);
            scatter(chunk, odds, y, Material.ALLIUM, 4);
            chunk.setBlock(6, y + 4, 10, Material.BEE_NEST);
            spawn(generator, chunk, EntityType.BEE);
        }
        case PALE_GARDEN -> {
            tree(chunk, 7, y, 7, Material.PALE_OAK_LOG, Material.PALE_OAK_LEAVES, 6);
            chunk.setBlock(7, y + 5, 7, Material.CREAKING_HEART);
            hang(chunk, odds, y, Material.PALE_HANGING_MOSS);
            scatter(chunk, odds, y, Material.OPEN_EYEBLOSSOM, 5);
        }
        case CAVE -> {
            stalactites(chunk, odds, y);
            scatter(chunk, odds, y, Material.GLOW_LICHEN, 6);
            chunk.setBlock(6, y + 1, 6, Material.AMETHYST_CLUSTER);
            chunk.setBlocks(9, y - 1, y + 1, 9, Material.WATER);
            spawn(generator, chunk, EntityType.AXOLOTL);
        }
        case END -> {
            chorusPlant(chunk, odds, 6, y, 6);
            chorusPlant(chunk, odds, 10, y, 9);
            chorusPlant(chunk, odds, 8, y, 11);
            scatter(chunk, odds, y, Material.PURPUR_BLOCK, 3);
        }
        case NETHER -> {
            chunk.setBlock(6, y + 1, 6, Material.NETHER_WART_BLOCK);
            chunk.setBlock(10, y + 1, 9, Material.WARPED_WART_BLOCK);
            tree(chunk, 7, y, 7, Material.CRIMSON_STEM, Material.NETHER_WART_BLOCK, 4);
            scatter(chunk, odds, y, Material.CRIMSON_FUNGUS, 3);
            scatter(chunk, odds, y, Material.WARPED_FUNGUS, 3);
            chunk.setBlock(5, y + 1, 9, Material.SHROOMLIGHT);
            chunk.setBlock(9, y, 5, Material.SOUL_SAND);
        }
        }
    }

    private void tree(RealBlocks chunk, int x, int y, int z, Material log, Material leaves, int h) {
        chunk.setBlocks(x, y + 1, y + 1 + h, z, log);
        leafBox(chunk, x - 1, x + 2, y + h, y + h + 2, z - 1, z + 2, leaves);
        chunk.setLeaves(x, y + h + 2, z, leaves);
    }

    /** Fill a box with non-decaying (PERSISTENT) leaves, so a hand-built canopy never rots when the trunk
     *  is thin or a block is punched out of it. */
    private void leafBox(RealBlocks chunk, int x1, int x2, int y1, int y2, int z1, int z2, Material leaves) {
        for (int lx = x1; lx < x2; lx++)
            for (int ly = y1; ly < y2; ly++)
                for (int lz = z1; lz < z2; lz++)
                    chunk.setLeaves(lx, ly, lz, leaves);
    }

    /** A connected chorus stalk topped with a chorus flower (the "fruit"), so the End dome reads as a proper
     *  chorus plant instead of loose floating cubes — the connections are set explicitly (worldgen setBlock
     *  doesn't run the neighbour update that would join them). */
    private void chorusPlant(RealBlocks chunk, Odds odds, int x, int y, int z) {
        int h = 2 + odds.getRandomInt(2); // 2..3 segments
        for (int j = 0; j < h; j++)
            chunk.setPipeBlock(x, y + 1 + j, z, Material.CHORUS_PLANT, BlockFace.DOWN, BlockFace.UP);
        chunk.setBlock(x, y + 1 + h, z, Material.CHORUS_FLOWER);
    }

    private void scatter(RealBlocks chunk, Odds odds, int y, Material m, int count) {
        for (int i = 0; i < count; i++) {
            int x = 3 + odds.getRandomInt(10), z = 3 + odds.getRandomInt(10);
            if (inside(x, z) && chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y, z))
                chunk.setBlock(x, y + 1, z, m);
        }
    }

    private void drape(RealBlocks chunk, Odds odds, int y, Material m) {
        for (int i = 0; i < 6; i++) {
            int x = 3 + odds.getRandomInt(10), z = 3 + odds.getRandomInt(10);
            if (inside(x, z) && chunk.isEmpty(x, y + 3, z))
                chunk.setBlock(x, y + 3, z, m);
        }
    }

    private void hang(RealBlocks chunk, Odds odds, int y, Material m) {
        for (int i = 0; i < 5; i++) {
            int x = 3 + odds.getRandomInt(10), z = 3 + odds.getRandomInt(10);
            if (inside(x, z) && !chunk.isEmpty(x, y + 5, z) && chunk.isEmpty(x, y + 4, z))
                chunk.setBlock(x, y + 4, z, m);
        }
    }

    private void stalactites(RealBlocks chunk, Odds odds, int y) {
        for (int i = 0; i < 6; i++) {
            int x = 3 + odds.getRandomInt(10), z = 3 + odds.getRandomInt(10);
            if (inside(x, z) && chunk.isEmpty(x, y + 1, z) && !chunk.isEmpty(x, y, z))
                chunk.setBlock(x, y + 1, z, Material.POINTED_DRIPSTONE);
        }
    }

    private void spawn(CityWorldGenerator generator, RealBlocks chunk, EntityType e) {
        generator.spawnProvider.spawnAnimals(generator, chunk, chunkOdds, CX, generator.streetLevel + 1, CZ, e);
    }
}
