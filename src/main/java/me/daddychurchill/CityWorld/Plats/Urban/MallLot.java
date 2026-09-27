package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * One chunk of a {@link Mall}: the whole plan is shared, and this lot runs its drawing clipped to its own
 * sixteen columns, so the slices join into one building. Containers take the table of the shop they
 * stand in ({@link #lootTableAt}).
 */
public class MallLot extends IsolatedLot {

    final Mall mall;
    private final boolean origin;

    public MallLot(PlatMap platmap, int chunkX, int chunkZ, Mall mall, boolean origin) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.mall = mall;
        this.origin = origin;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new MallLot(platmap, chunkX, chunkZ, mall, false);
    }

    public Mall getMall() {
        return mall;
    }

    @Override
    public LootLocation defaultLoot() {
        return LootLocation.SHOP;
    }

    @Override
    public String lootTableAt(int x, int y, int z) {
        return mall.lootTableAt(x, y, z);
    }

    @Override
    public String getInteriorDescription() {
        return mall.name;
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
        return mall.roofY() + 2;
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return blockY < generator.streetLevel - 20 && super.isShaftableLevel(generator, blockY);
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, mall.floors * Mall.H + 6, 0, true);
        chunk.setLayer(generator.streetLevel, Material.SMOOTH_STONE);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        mall.draw(generator, chunk, chunkOdds);
        if (origin)
            generator.reportLocation("mall", mall.name, chunk, 1, 1);
        if (buildingsDecay(generator))
            destroyLot(generator, generator.streetLevel + 1, mall.roofY());
    }
}
