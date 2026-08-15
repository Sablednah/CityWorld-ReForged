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
import me.daddychurchill.CityWorld.Support.ZooEnclosure;

/**
 * One chunk-slice of a BIG zoo enclosure spanning an NxM block of chunks — a single large paddock (fenced)
 * or sunken pit (moat wall + rail, look down at the animals). Like {@link BigBiodomeLot}, the placer picks
 * the theme/size/style once and hands each chunk its offset; each chunk draws only its columns of the pen
 * from the shared world bounds ({@link ZooEnclosure}), so the slices join seamlessly.
 */
public class BigZooLot extends IsolatedLot {

    // must match ZooLot.Theme ordering by the placer's index
    private static final String[] NAMES = { "Pandas", "Camels", "Frogs", "Polar Bears", "Foxes", "Goats",
            "Parrots", "Bees", "Axolotls", "Turtles" };

    public static final int THEMES = NAMES.length;

    private final int sizeX, sizeZ, offX, offZ, theme;
    private final boolean sunken;

    public BigZooLot(PlatMap platmap, int chunkX, int chunkZ, int sizeX, int sizeZ, int offX, int offZ, int theme,
            boolean sunken) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.offX = offX;
        this.offZ = offZ;
        this.theme = theme;
        this.sunken = sunken;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new BigZooLot(platmap, chunkX, chunkZ, sizeX, sizeZ, offX, offZ, theme, sunken);
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

    private int nwX() {
        return (getChunkX() - offX) * 16;
    }

    private int nwZ() {
        return (getChunkZ() - offZ) * 16;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, DataContext.FloorHeight * 2, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - ZooEnclosure.PIT_DEPTH - 2, ZooEnclosure.PIT_DEPTH + 2, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int y = generator.streetLevel;
        if (offX == 0 && offZ == 0)
            generator.reportLocation("zoo", (sunken ? "Big Sunken Zoo: " : "Big Zoo: ") + NAMES[theme], chunk);

        // pen interior in world coords (inset 2 from the structure edge for a walkway rim)
        int penX1 = nwX() + 2, penX2 = nwX() + sizeX * 16 - 3;
        int penZ1 = nwZ() + 2, penZ2 = nwZ() + sizeZ * 16 - 3;
        Material ground = groundFor(theme);
        ZooEnclosure.draw(chunk, penX1, penX2, penZ1, penZ2, y, sunken, ground);

        // a gate on the south fence (fenced pens only), where that column lands
        if (!sunken) {
            int gateWX = (penX1 + penX2) / 2, gateWZ = penZ2;
            if (owns(chunk, gateWX, gateWZ))
                chunk.setGate(gateWX - chunk.getOriginX(), y + 1, gateWZ - chunk.getOriginZ(),
                        Material.OAK_FENCE_GATE, BlockFace.SOUTH, false);
        }

        // a name sign at the structure's NW corner — a plain standing signpost on a fence post (no
        // floating hanging-sign hardware)
        int signWX = nwX() + 1, signWZ = nwZ() + 1;
        if (owns(chunk, signWX, signWZ)) {
            int sx = signWX - chunk.getOriginX(), sz = signWZ - chunk.getOriginZ();
            chunk.setBlock(sx, y + 1, sz, Material.OAK_FENCE);
            chunk.setSignPost(sx, y + 2, sz, Material.OAK_SIGN, BlockFace.SOUTH,
                    new String[] { "The Zoo", NAMES[theme] });
        }

        int floorY = sunken ? y - ZooEnclosure.PIT_DEPTH : y;
        int animalY = floorY + 1;
        // rolling terrain — the deeper sunken pit has room for proper hummocks
        ZooEnclosure.mounds(chunk, penX1, penX2, penZ1, penZ2, floorY, ground, chunkOdds, sunken ? 5 : 3);
        // dense planting (a bamboo grove for pandas)
        int plantings = theme == 0 ? 12 : 5;
        for (int i = 0; i < plantings; i++) {
            int lx = 2 + chunkOdds.getRandomInt(12), lz = 2 + chunkOdds.getRandomInt(12);
            int wx = chunk.getOriginX() + lx, wz = chunk.getOriginZ() + lz;
            if (wx <= penX1 + 1 || wx >= penX2 - 1 || wz <= penZ1 + 1 || wz >= penZ2 - 1)
                continue; // inner floor only (off the walls)
            feature(chunk, lx, floorY, lz);
        }
        // the residents — ONE herd, spawned only by the chunk that owns the enclosure centre (was one herd
        // per chunk per try, which packed the pen). ignoreFlood=true: the sunken floor is dry but below sea.
        int ccx = (penX1 + penX2) / 2, ccz = (penZ1 + penZ2) / 2;
        if (owns(chunk, ccx, ccz))
            generator.spawnProvider.spawnAnimals(generator, chunk, chunkOdds, ccx - chunk.getOriginX(), animalY,
                    ccz - chunk.getOriginZ(), animalFor(theme), true);
    }

    private void feature(RealBlocks chunk, int x, int floorY, int z) {
        // sit on the actual ground top — mounds may have raised it a couple of blocks
        int gy = floorY;
        while (gy < floorY + 4 && !chunk.isEmpty(x, gy + 1, z))
            gy++;
        if (!chunk.isEmpty(x, gy + 1, z))
            return;
        switch (theme) {
        case 0 -> chunk.setBlocks(x, gy + 1, gy + 4 + chunkOdds.getRandomInt(3), z, Material.BAMBOO); // tall grove
        case 1 -> chunk.setBlocks(x, gy + 1, gy + 2 + chunkOdds.getRandomInt(2), z, Material.CACTUS);
        case 2, 8 -> chunk.setBlock(x, gy + 1, z, Material.LILY_PAD);
        default -> smallTree(chunk, x, gy, z);
        }
    }

    private void smallTree(RealBlocks chunk, int x, int y, int z) {
        chunk.setBlocks(x, y + 1, y + 4, z, Material.OAK_LOG);
        for (int lx = x - 1; lx <= x + 1; lx++)
            for (int lz = z - 1; lz <= z + 1; lz++)
                if (lx >= 0 && lx < 16 && lz >= 0 && lz < 16)
                    chunk.setLeaves(lx, y + 4, lz, Material.OAK_LEAVES);
        chunk.setLeaves(x, y + 5, z, Material.OAK_LEAVES);
    }

    private static boolean owns(RealBlocks chunk, int wx, int wz) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        return wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16;
    }

    private static Material groundFor(int t) {
        return switch (t) {
        case 1, 9 -> Material.SAND;
        case 2 -> Material.MUD;
        case 3 -> Material.SNOW_BLOCK;
        case 4 -> Material.PODZOL;
        case 5 -> Material.STONE_BRICKS;
        case 8 -> Material.MOSS_BLOCK;
        default -> Material.GRASS_BLOCK;
        };
    }

    private static EntityType animalFor(int t) {
        return switch (t) {
        case 0 -> EntityType.PANDA;
        case 1 -> EntityType.CAMEL;
        case 2 -> EntityType.FROG;
        case 3 -> EntityType.POLAR_BEAR;
        case 4 -> EntityType.FOX;
        case 5 -> EntityType.GOAT;
        case 6 -> EntityType.PARROT;
        case 7 -> EntityType.BEE;
        case 8 -> EntityType.AXOLOTL;
        default -> EntityType.TURTLE;
        };
    }
}
