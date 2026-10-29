package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.IntBinaryOperator;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Cash machines from {@code #cityworld:fittings/atm}, which only a mod fills (Modernlife's ATM is listed,
 * optional): one against an inside wall, screen to the room, in a free cell clear of the doors, with room in front
 * to stand. City halls and courthouses, shops and malls call it once per ground-floor chunk, each at its own odds
 * setting ({@code shops.oddsOfAtmInCivic}, {@code oddsOfAtmInShops}, {@code oddsOfAtmInMalls}).
 *
 * <p>While the pool is empty, or the odds are 0, it draws nothing, so a world without such a mod generates exactly as
 * before. Otherwise it takes ONE draw from the caller's dice and works on a fork of them: the cells it finds depend on
 * what is already in the room (see {@code PlatLot.withForkedDice}).
 */
public final class Atms {

    private Atms() {
    }

    private static final BlockFace[] SIDES = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };

    /**
     * @param floorY  the standing height of the floor in column (x, z) of the chunk, or a negative number for none
     * @param blocked cells to leave alone (a stairwell's claim)
     * @return whether a machine was placed
     */
    public static boolean place(CityWorldGenerator generator, RealBlocks chunk, Odds odds, double chance,
            IntBinaryOperator floorY, BiPredicate<Integer, Integer> blocked) {
        List<Material> pool = MaterialTags.resolve(MaterialTags.FITTINGS_ATM);
        if (pool.isEmpty() || chance <= 0)
            return false;
        Odds dice = odds.fork();
        if (!dice.playOdds(chance))
            return false;
        List<int[]> spots = new ArrayList<>();
        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++) {
                int y = floorY.applyAsInt(x, z);
                if (y < 0 || blocked.test(x, z) || !chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z)
                        || chunk.isEmpty(x, y - 1, z) || chunk.isBesideDoor(x, y, z))
                    continue;
                for (BlockFace out : SIDES)
                    if (chunk.isWallBacking(x - out.getModX(), y, z - out.getModZ(), out)
                            && chunk.isEmpty(x + out.getModX(), y, z + out.getModZ())
                            && chunk.isEmpty(x + out.getModX(), y + 1, z + out.getModZ())) {
                        spots.add(new int[] { x, y, z, out.ordinal() });
                        break;
                    }
            }
        if (spots.isEmpty())
            return false;
        int[] spot = spots.get(dice.getRandomInt(spots.size()));
        chunk.setBlock(spot[0], spot[1], spot[2], pool.get(dice.getRandomInt(pool.size())), BlockFace.values()[spot[3]]);
        return true;
    }

    /** {@link #place} on one flat floor at height {@code y}. */
    public static boolean place(CityWorldGenerator generator, RealBlocks chunk, Odds odds, double chance, int y,
            BiPredicate<Integer, Integer> blocked) {
        return place(generator, chunk, odds, chance, (x, z) -> y, blocked);
    }
}
