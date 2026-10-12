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
 * A single zoo enclosure: a fenced pen of habitat with its animals and a name sign. The resident is a weighted
 * pick from the zoo catalog ({@link ZooCatalog}: {@code data/<ns>/cityworld/zoo/animal} and {@code .../habitat}),
 * which ships CityWorld's ten (pandas in bamboo, camels in the desert, a frog pool, ...) and takes any a pack or
 * a zoo mod adds. Placed rarely as a one-off (like the barn/fish-pond), so a run of them reads as a little zoo.
 * This is CityWorld's home for the animals that don't turn up in the farms and fields.
 */
public class ZooLot extends IsolatedLot {

    private final ZooCatalog.Entry resident;

    public ZooLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        resident = ZooCatalog.pick(chunkOdds, false);
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new ZooLot(platmap, chunkX, chunkZ);
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
        String name = resident.animal().name();
        ZooCatalog.Habitat habitat = resident.habitat();
        generator.reportLocation("zoo", "Zoo: " + name, chunk);

        // the habitat's ground across the lot
        chunk.setLayer(y, ZooCatalog.Habitat.block(habitat.ground(), Material.GRASS_BLOCK));

        // a pen (inset 2) with a gate on the south side — connected fences (not loose posts), higher for a
        // tall animal
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        int penX1 = oX + 2, penX2 = oX + 13, penZ1 = oZ + 2, penZ2 = oZ + 13;
        ZooEnclosure.fencePerimeter(chunk, penX1, penX2, penZ1, penZ2, y + 1, y + ZooEnclosure.fenceHeight(resident),
                ZooCatalog.Habitat.block(habitat.fence(), Material.OAK_FENCE));
        chunk.setGate(7, y + 1, 13, ZooCatalog.Habitat.block(habitat.gate(), Material.OAK_FENCE_GATE), BlockFace.SOUTH,
                false);

        // pool, rocks, trees and plants
        ZooEnclosure.furnish(chunk, habitat, penX1, penX2, penZ1, penZ2, y, 3, chunkOdds);
        if (generator.getSettings().includeDecayedBuildings)
            ZooEnclosure.breakOpen(chunk, penX1, penX2, penZ1, penZ2, y + 1, y + ZooEnclosure.fenceHeight(resident));

        // a name sign on a post at the pen's front-left corner — a plain standing signpost
        chunk.setBlock(3, y + 1, 2, Material.OAK_FENCE);
        chunk.setSignPost(3, y + 2, 2, Material.OAK_SIGN, BlockFace.SOUTH, new String[] { "The Zoo", name });

        // and the residents
        generator.spawnProvider.spawnZooAnimals(generator, chunk, chunkOdds, 7, y + 1, 7, resident.entity(),
                resident.animal().count().roll(chunkOdds), resident.animal().babyChance(),
                resident.animal().finalizeSpawn(), false);
    }
}
