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
 * A single zoo enclosure: a fenced pen of themed ground with a matching animal and a name sign. One of a
 * handful of themes is rolled per lot (pandas in bamboo, camels in the desert, a frog pool, …). Placed
 * rarely as a one-off (like the barn/fish-pond), so a run of them reads as a little zoo. This is CityWorld's
 * home for the animals that don't turn up in the farms and fields.
 */
public class ZooLot extends IsolatedLot {

    private enum Theme {
        PANDA("Pandas", EntityType.PANDA),
        CAMEL("Camels", EntityType.CAMEL),
        FROG("Frogs", EntityType.FROG),
        POLAR("Polar Bears", EntityType.POLAR_BEAR),
        FOX("Foxes", EntityType.FOX),
        GOAT("Goats", EntityType.GOAT),
        PARROT("Parrots", EntityType.PARROT),
        BEE("Bees", EntityType.BEE),
        AXOLOTL("Axolotls", EntityType.AXOLOTL),
        TURTLE("Turtles", EntityType.TURTLE);

        final String name;
        final EntityType animal;

        Theme(String name, EntityType animal) {
            this.name = name;
            this.animal = animal;
        }
    }

    private final Theme theme;

    public ZooLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        theme = Theme.values()[chunkOdds.getRandomInt(Theme.values().length)];
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new ZooLot(platmap, chunkX, chunkZ);
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
        chunk.airoutLayer(generator, generator.streetLevel + 1, DataContext.FloorHeight * 2, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 2, 2, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int y = generator.streetLevel;
        generator.reportLocation("Zoo: " + theme.name, chunk);

        // themed ground across the pen interior
        chunk.setLayer(y, groundFor(theme));

        // a low fence pen (inset 2) with a gate on the south side
        chunk.setWalls(2, 14, y + 1, y + 2, 2, 14, Material.OAK_FENCE);
        chunk.setGate(7, y + 1, 13, Material.OAK_FENCE_GATE, BlockFace.SOUTH, false);

        // theme features + a pool where the animal wants water
        boolean pool = theme == Theme.FROG || theme == Theme.AXOLOTL || theme == Theme.TURTLE
                || theme == Theme.POLAR;
        if (pool)
            digPool(chunk, y);
        addFeatures(generator, chunk, odds(), y);

        // a name sign on a post at the pen's front-left corner
        chunk.setBlocks(3, y + 1, y + 3, 2, Material.OAK_FENCE);
        if (chunk.isEmpty(3, y + 3, 2))
            chunk.setBlock(3, y + 3, 2, Material.OAK_PLANKS);
        chunk.setSignPost(3, y + 2, 2, Material.OAK_HANGING_SIGN, BlockFace.SOUTH, new String[] { "The Zoo", theme.name });

        // and the residents
        stock(generator, chunk, y);
    }

    private Odds odds() {
        return chunkOdds;
    }

    private static Material groundFor(Theme t) {
        return switch (t) {
        case PANDA, PARROT -> Material.GRASS_BLOCK;
        case CAMEL, TURTLE -> Material.SAND;
        case FROG -> Material.MUD;
        case POLAR -> Material.SNOW_BLOCK;
        case FOX -> Material.PODZOL;
        case GOAT -> Material.STONE_BRICKS;
        case BEE -> Material.GRASS_BLOCK;
        case AXOLOTL -> Material.MOSS_BLOCK;
        };
    }

    /** A shallow water pool in the pen centre for the water-loving animals. */
    private void digPool(RealBlocks chunk, int y) {
        chunk.setBlocks(5, 11, y - 1, y + 1, 5, 11, Material.WATER);
        if (theme == Theme.POLAR) {
            chunk.setBlock(5, y, 5, Material.PACKED_ICE);
            chunk.setBlock(10, y, 10, Material.PACKED_ICE);
        }
    }

    private void addFeatures(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int y) {
        switch (theme) {
        case PANDA -> {
            for (int i = 0; i < 10; i++)
                bamboo(chunk, odds, 3 + odds.getRandomInt(9), y, 3 + odds.getRandomInt(9));
            smallTree(chunk, 5, y, 9, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES);
        }
        case CAMEL -> {
            for (int i = 0; i < 5; i++)
                cactus(chunk, odds, 3 + odds.getRandomInt(9), y, 3 + odds.getRandomInt(9));
            chunk.setBlock(6, y + 1, 6, Material.DEAD_BUSH);
        }
        case FROG -> {
            chunk.setBlock(6, y + 1, 6, Material.LILY_PAD);
            chunk.setBlock(9, y + 1, 8, Material.LILY_PAD);
            chunk.setBlocks(4, y + 1, y + 3, 4, Material.MANGROVE_ROOTS);
        }
        case AXOLOTL -> {
            chunk.setBlock(6, y, 6, Material.POINTED_DRIPSTONE);
            chunk.setBlock(9, y - 1, 9, Material.DRIPSTONE_BLOCK);
        }
        case TURTLE -> chunk.setBlock(4, y + 1, 4, Material.SUGAR_CANE);
        case FOX -> {
            smallTree(chunk, 5, y, 5, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES);
            smallTree(chunk, 10, y, 9, Material.SPRUCE_LOG, Material.SPRUCE_LEAVES);
        }
        case PARROT -> smallTree(chunk, 7, y, 7, Material.JUNGLE_LOG, Material.JUNGLE_LEAVES);
        case GOAT -> {
            chunk.setBlocks(6, 9, y + 1, y + 3, 6, 9, Material.STONE_BRICKS); // a little crag to climb
        }
        case BEE -> {
            smallTree(chunk, 7, y, 7, Material.OAK_LOG, Material.OAK_LEAVES);
            chunk.setBlock(7, y + 3, 7, Material.BEE_NEST);
            chunk.setBlock(4, y + 1, 5, Material.DANDELION);
            chunk.setBlock(10, y + 1, 6, Material.POPPY);
        }
        case POLAR -> {
        }
        }
    }

    private void bamboo(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (!chunk.isEmpty(x, y + 1, z))
            return;
        int h = 2 + odds.getRandomInt(3);
        chunk.setBlocks(x, y + 1, y + 1 + h, z, Material.BAMBOO);
    }

    private void cactus(RealBlocks chunk, Odds odds, int x, int y, int z) {
        if (!chunk.isEmpty(x, y + 1, z))
            return;
        chunk.setBlocks(x, y + 1, y + 2 + odds.getRandomInt(2), z, Material.CACTUS);
    }

    private void smallTree(RealBlocks chunk, int x, int y, int z, Material log, Material leaves) {
        chunk.setBlocks(x, y + 1, y + 4, z, log);
        chunk.setBlocks(x - 1, x + 2, y + 4, y + 6, z - 1, z + 2, leaves);
        chunk.setBlock(x, y + 6, z, leaves);
    }

    private void stock(CityWorldGenerator generator, RealBlocks chunk, int y) {
        generator.spawnProvider.spawnAnimals(generator, chunk, chunkOdds, 7, y + 1, 7, theme.animal);
        generator.spawnProvider.spawnAnimals(generator, chunk, chunkOdds, 9, y + 1, 8, theme.animal);
    }
}
