package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A mall's car park, one chunk: asphalt at road level, two rows of bays with painted lines either side of
 * an aisle, lamp posts down the aisle, a planted verge on any side that is not a road, the mall or more
 * car park — and cars in some of the bays, abandoned in a ruined world. Open to the road on every side
 * that fronts one, so the roads run straight in.
 */
public class ParkingLot extends IsolatedLot {

    private final Mall mall;

    public ParkingLot(PlatMap platmap, int chunkX, int chunkZ, Mall mall) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.mall = mall;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new ParkingLot(platmap, chunkX, chunkZ, mall);
    }

    @Override
    public String getInteriorDescription() {
        return mall.name + " car park";
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
        return generator.streetLevel + 6;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, 8, 0, true);
        chunk.setLayer(generator.streetLevel, me.daddychurchill.CityWorld.Plats.RoadLot.cityPavement(generator));
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    private static boolean belongs(PlatMap platmap, int x, int z) {
        if (!platmap.inBounds(x, z))
            return false;
        PlatLot lot = platmap.getLot(x, z);
        return lot instanceof ParkingLot || lot instanceof MallLot || platmap.isExistingRoad(x, z);
    }

    private static final Material[] CAR_PAINT = { Material.RED_CONCRETE, Material.BLUE_CONCRETE, Material.WHITE_CONCRETE,
            Material.BLACK_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.GREEN_CONCRETE, Material.YELLOW_CONCRETE };

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int y = generator.streetLevel, stand = y + 1;
        boolean ruined = generator.isApocalypseStyle();
        Odds odds = chunkOdds;
        Material asphalt = me.daddychurchill.CityWorld.Plats.RoadLot.cityPavement(generator);
        // bays run north-south in two rows (z 1..5 and 10..14) either side of an aisle (z 6..9)
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                boolean bayRow = z >= 1 && z <= 5 || z >= 10 && z <= 14;
                boolean line = bayRow && x % 4 == 0;
                Material m = line ? Material.WHITE_CONCRETE : (z == 7 || z == 8) && x % 3 == 0 ? Material.YELLOW_CONCRETE
                        : asphalt;
                if (ruined && odds.playOdds(0.06))
                    m = odds.flipCoin() ? Material.GRAVEL : Material.MOSS_BLOCK;
                chunk.setBlock(x, y, z, m);
            }
        // verges on the sides with nothing to join to
        boolean[] open = { belongs(platmap, platX, platZ - 1), belongs(platmap, platX, platZ + 1),
                belongs(platmap, platX + 1, platZ), belongs(platmap, platX - 1, platZ) };
        for (int i = 0; i < 16; i++) {
            if (!open[0])
                verge(chunk, odds, i, stand, 0);
            if (!open[1])
                verge(chunk, odds, i, stand, 15);
        }
        for (int i = 0; i < 16; i++) {
            if (!open[2])
                vergeX(chunk, odds, 15, stand, i);
            if (!open[3])
                vergeX(chunk, odds, 0, stand, i);
        }
        // lamp posts down the aisle
        for (int x : new int[] { 3, 11 }) {
            chunk.setBlocks(x, stand, stand + 4, 7, Material.COBBLESTONE_WALL);
            chunk.setBlock(x, stand + 4, 7, Material.SEA_LANTERN);
        }
        // cars in some bays, nose to the kerb
        for (int bay = 0; bay < 4; bay++)
            for (int row = 0; row < 2; row++) {
                if (!odds.playOdds(ruined ? 0.35 : 0.5))
                    continue;
                int x0 = bay * 4 + 1, z0 = row == 0 ? 1 : 11;
                car(chunk, odds, x0, stand, z0, CAR_PAINT[odds.getRandomInt(CAR_PAINT.length)], ruined);
            }
        // a sign for the mall at one corner
        if (!open[3] && !open[0])
            chunk.setSignPost(1, stand, 1, Material.OAK_SIGN, BlockFace.SOUTH, new String[] { mall.name, "Customer", "Parking" });
        chunk.reconnect(0, 16, stand, stand + 4, 0, 16);
    }

    private static void verge(RealBlocks chunk, Odds odds, int x, int stand, int z) {
        chunk.setBlock(x, stand - 1, z, Material.GRASS_BLOCK);
        if (x % 5 == 2)
            chunk.setBlock(x, stand, z, Material.AZALEA);
        else if (odds.playOdds(0.4))
            chunk.setBlock(x, stand, z, Material.GRASS);
    }

    private static void vergeX(RealBlocks chunk, Odds odds, int x, int stand, int z) {
        verge(chunk, odds, z, stand, x); // the same, transposed
    }

    /** A little car, two wide and four long: body, glass cabin, dark wheels. Rusted and doorless in a ruin. */
    private static void car(RealBlocks chunk, Odds odds, int x0, int stand, int z0, Material paint, boolean ruined) {
        for (int x = x0; x <= x0 + 1; x++)
            for (int z = z0; z <= z0 + 3; z++) {
                boolean wheel = z == z0 || z == z0 + 3;
                chunk.setBlock(x, stand, z, wheel ? Material.BLACK_CONCRETE : paint);
                if (z == z0 + 1 || z == z0 + 2)
                    chunk.setBlock(x, stand + 1, z, ruined && odds.flipCoin() ? Material.AIR : Material.GLASS);
                else
                    chunk.setBlock(x, stand + 1, z, ruined ? Material.of(net.minecraft.world.level.block.Blocks.GRANITE) : paint);
            }
    }
}
