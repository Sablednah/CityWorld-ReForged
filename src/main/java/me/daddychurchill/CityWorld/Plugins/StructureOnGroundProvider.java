package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * Stub of the original {@code StructureOnGroundProvider} (1158 lines) — the set-pieces that stand on
 * the ground: water towers, sheds, campgrounds, tents.
 *
 * <p><b>Wave 2b placeholder.</b> All of it is decoration-pass work (P5), and it wants palettes and
 * loot the port has not reached yet ({@code itemsSelectMaterial_Shed*}, chest contents). Only the
 * one method the ported lots reach for is here: a park lays out correctly, it just has no water
 * tower standing in it.
 */
public class StructureOnGroundProvider extends Provider {

    public static StructureOnGroundProvider loadProvider(CityWorldGenerator generator) {
        return new StructureOnGroundProvider();
    }

    /** P5. */
    public void drawWaterTower(CityWorldGenerator generator, RealBlocks chunk, int x, int y, int z, Odds odds) {
    }

    /**
     * P5: builds a small house and returns how many floors it managed.
     *
     * <p>Returning 0 says "nothing built", which is what the callers key off — so a neighbourhood
     * lays out its plots and leaves them vacant rather than misreporting houses that aren't there.
     */
    public int generateHouse(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int baseY,
            int maxFloors) {
        return 0;
    }

    /**
     * P5: a small shed with a chest in it, the {@code SHED} content of a {@code StorageLot}.
     *
     * <p>Safe to leave empty — both call sites are {@code void} and read nothing back, unlike
     * {@link #generateHouse} above, whose return value the callers key off. The visible cost is that
     * a storage lot which rolled {@code SHED} is simply an empty yard, and that
     * {@code LootLocation.STORAGE_SHED} stays unreached even though its loot table ships and rolls
     * (see "Closed: mobs and loot" in PORTING.md).
     */
    public void generateShed(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int x,
            int y, int z, int radius, LootLocation location) {
        generateShed(generator, chunk, context, odds, x, y, z, radius, location, location);
    }

    /** P5. */
    public void generateShed(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int x,
            int y, int z, int radius, LootLocation location, LootLocation other) {
    }
}
