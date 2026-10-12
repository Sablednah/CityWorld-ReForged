package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
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
import me.daddychurchill.CityWorld.worldgen.ZooCatalog;

/**
 * One chunk-slice of a BIG zoo enclosure spanning an NxM block of chunks — a single large paddock (fenced)
 * or sunken pit (moat wall + rail, look down at the animals). Like {@link BigBiodomeLot}, the placer picks
 * the resident ({@link ZooCatalog}), size and style once and hands each chunk its offset; each chunk draws only its columns of the pen
 * from the shared world bounds ({@link ZooEnclosure}), so the slices join seamlessly.
 */
public class BigZooLot extends IsolatedLot {

    private final int sizeX, sizeZ, offX, offZ;
    /** The resident's catalog id ({@link ZooCatalog}), picked once by the placer for every chunk of the pen. */
    private final String animal;
    private final boolean sunken;

    public BigZooLot(PlatMap platmap, int chunkX, int chunkZ, int sizeX, int sizeZ, int offX, int offZ, String animal,
            boolean sunken) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.offX = offX;
        this.offZ = offZ;
        this.animal = animal;
        this.sunken = sunken;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new BigZooLot(platmap, chunkX, chunkZ, sizeX, sizeZ, offX, offZ, animal, sunken);
    }

    @Override
    public boolean allowsWildDecoration() {
        return false;
    }

    /** The habitat is the pen's ground: the biome-ground pass would turn a jungle pen's grass to snow. */
    @Override
    protected boolean wantsBiomeGround() {
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
        ZooCatalog.Entry resident = ZooCatalog.byId(animal);
        ZooCatalog.Habitat habitat = resident.habitat();
        String name = resident.animal().name();
        if (offX == 0 && offZ == 0)
            generator.reportLocation("zoo", (sunken ? "Big Sunken Zoo: " : "Big Zoo: ") + name, chunk);

        // pen interior in world coords (inset 2 from the structure edge for a walkway rim)
        int penX1 = nwX() + 2, penX2 = nwX() + sizeX * 16 - 3;
        int penZ1 = nwZ() + 2, penZ2 = nwZ() + sizeZ * 16 - 3;
        Material ground = ZooCatalog.Habitat.block(habitat.ground(), Material.GRASS_BLOCK);
        ZooEnclosure.draw(chunk, penX1, penX2, penZ1, penZ2, y, sunken, ground,
                ZooCatalog.Habitat.block(habitat.fence(), Material.OAK_FENCE), ZooEnclosure.fenceHeight(resident));

        // a gate on the south fence (fenced pens only), where that column lands
        if (!sunken) {
            int gateWX = (penX1 + penX2) / 2, gateWZ = penZ2;
            if (owns(chunk, gateWX, gateWZ))
                chunk.setGate(gateWX - chunk.getOriginX(), y + 1, gateWZ - chunk.getOriginZ(),
                        ZooCatalog.Habitat.block(habitat.gate(), Material.OAK_FENCE_GATE), BlockFace.SOUTH, false);
        }

        // a name sign at the structure's NW corner — a plain standing signpost on a fence post (no
        // floating hanging-sign hardware)
        int signWX = nwX() + 1, signWZ = nwZ() + 1;
        if (owns(chunk, signWX, signWZ)) {
            int sx = signWX - chunk.getOriginX(), sz = signWZ - chunk.getOriginZ();
            chunk.setBlock(sx, y + 1, sz, Material.OAK_FENCE);
            chunk.setSignPost(sx, y + 2, sz, Material.OAK_SIGN, BlockFace.SOUTH, new String[] { "The Zoo", name });
        }

        int floorY = sunken ? y - ZooEnclosure.PIT_DEPTH : y;
        int animalY = floorY + 1;
        // rolling terrain — the deeper sunken pit has room for proper hummocks — then the habitat: a pool round
        // the pen centre where it has one, rocks, trees and plants through every chunk of it
        ZooEnclosure.mounds(chunk, penX1, penX2, penZ1, penZ2, floorY, ground, chunkOdds, sunken ? 5 : 3);
        ZooEnclosure.furnish(chunk, habitat, penX1, penX2, penZ1, penZ2, floorY, 5, chunkOdds);
        if (!sunken && generator.getSettings().includeDecayedBuildings)
            ZooEnclosure.breakOpen(chunk, penX1, penX2, penZ1, penZ2, y + 1, y + ZooEnclosure.fenceHeight(resident));

        // the residents — ONE group, spawned only by the chunk that owns the enclosure centre (was one herd
        // per chunk per try, which packed the pen). ignoreFlood=true: the sunken floor is dry but below sea.
        // Beside the centre, not on it: a pool sits there.
        int ccx = (penX1 + penX2) / 2, ccz = (penZ1 + penZ2) / 2 + (habitat.pool() ? 7 : 0);
        if (owns(chunk, ccx, ccz))
            generator.spawnProvider.spawnZooAnimals(generator, chunk, chunkOdds, ccx - chunk.getOriginX(), animalY,
                    ccz - chunk.getOriginZ(), resident.entity(), resident.animal().count().roll(chunkOdds),
                    resident.animal().babyChance(), resident.animal().finalizeSpawn(), true);
    }

    private static boolean owns(RealBlocks chunk, int wx, int wz) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        return wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16;
    }
}
