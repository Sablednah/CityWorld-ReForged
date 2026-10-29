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
 * optional): one on a base block against an inside wall, screen to the room, under a ceiling, clear of the doors,
 * with room in front to stand. City halls and courthouses, shops and malls call it once per ground-floor chunk, each at its own odds
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
        // The machine stands on a base block (a pedestal, from #cityworld:fittings/atm_base): Modernlife's model is
        // twelve pixels tall and read as dropped on the floor (owner, 2026-10-29). Base at y, machine at y + 1, room
        // over it; the wall is behind the machine; the cell in front is open to stand in; and there is a ceiling over
        // both, so it is indoors (one stood outside on a civic building's front steps, backed by a pillar).
        List<int[]> spots = new ArrayList<>();
        for (int x = 1; x < 15; x++)
            for (int z = 1; z < 15; z++) {
                int y = floorY.applyAsInt(x, z);
                if (y < 0 || blocked.test(x, z) || chunk.isEmpty(x, y - 1, z) || !chunk.isEmpty(x, y, z)
                        || !chunk.isEmpty(x, y + 1, z) || !chunk.isEmpty(x, y + 2, z)
                        || chunk.isBesideDoor(x, y, z) || chunk.isBesideDoor(x, y + 1, z) || !roofed(chunk, x, y, z))
                    continue;
                for (BlockFace out : SIDES) {
                    int fx = x + out.getModX(), fz = z + out.getModZ();
                    // a real wall, three high behind the base, the machine and over it: a shop's display of pumpkins or
                    // a lamp on a shelf passed for one at a single block
                    int bx = x - out.getModX(), bz = z - out.getModZ();
                    if (chunk.isWallBacking(bx, y, bz, out) && chunk.isWallBacking(bx, y + 1, bz, out)
                            && chunk.isWallBacking(bx, y + 2, bz, out)
                            && chunk.isEmpty(fx, y, fz) && chunk.isEmpty(fx, y + 1, fz) && !chunk.isEmpty(fx, y - 1, fz)
                            && roofed(chunk, fx, y, fz)) {
                        spots.add(new int[] { x, y, z, out.ordinal() });
                        break;
                    }
                }
            }
        if (spots.isEmpty())
            return false;
        int[] spot = spots.get(dice.getRandomInt(spots.size()));
        List<Material> bases = MaterialTags.resolve(MaterialTags.FITTINGS_ATM_BASE);
        chunk.setBlock(spot[0], spot[1], spot[2], bases.isEmpty() ? Material.SMOOTH_STONE
                : bases.get(dice.getRandomInt(bases.size())));
        chunk.setBlock(spot[0], spot[1] + 1, spot[2], pool.get(dice.getRandomInt(pool.size())),
                BlockFace.values()[spot[3]]);
        return true;
    }

    /** Whether something solid closes the column above (x, z) within a storey: a ceiling, not the sky. */
    private static boolean roofed(RealBlocks chunk, int x, int y, int z) {
        for (int up = y + 3; up <= y + 12; up++)
            if (!chunk.isEmpty(x, up, z))
                return true;
        return false;
    }

    /** {@link #place} on one flat floor at height {@code y}. */
    public static boolean place(CityWorldGenerator generator, RealBlocks chunk, Odds odds, double chance, int y,
            BiPredicate<Integer, Integer> blocked) {
        return place(generator, chunk, odds, chance, (x, z) -> y, blocked);
    }
}
