package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The APOCALYPSE pass: zombie spawners lurking in a ruined building's cellars. Runs once per building
 * lot, on the live decoration level, after the interior/decay/overgrowth so it sees the finished
 * basement. Roughly half of what it places is buried in a sealed 2-tall pocket UNDER the basement floor —
 * the intact floor above hides it, so the cellar reads empty until the dead claw their way up. The rest
 * sit out in the open cellar. Sewers and mines get their own zombie spawners through the enabled spawner
 * bags; this is the "under the floor, unseen" half of the request.
 *
 * <p>Coordinates are chunk-local (x/z 0-15, y a world Y), like the rest of the {@link SupportBlocks} side.
 */
public final class ApocalypseSpawners {

    private ApocalypseSpawners() {}

    public static void apply(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, Odds odds) {
        if (!(chunk instanceof RealBlocks real))
            return;
        int street = generator.streetLevel;
        int bottom = street - 30; // covers multi-level cellars; the mines lie below this
        int wanted = 1 + odds.getRandomInt(2); // 1..2 per ruined building
        int placed = 0;
        for (int tries = 0; tries < 24 && placed < wanted; tries++) {
            int x = odds.getRandomInt(3, 13), z = odds.getRandomInt(3, 13); // inset so pockets stay in-chunk

            // find a cellar floor in this column: a 2-tall air room (y, y+1) standing on solid ground (y-1)
            int roomY = -1;
            for (int y = street - 3; y > bottom; y--)
                if (real.isEmpty(x, y, z) && real.isEmpty(x, y + 1, z) && !real.isEmpty(x, y - 1, z)) {
                    roomY = y;
                    break;
                }
            if (roomY < 0)
                continue;

            // ~half buried under the floor (hidden), the rest out in the open cellar
            if (odds.playOdds(0.55) && buryUnderFloor(generator, real, odds, x, roomY - 1, z))
                placed++;
            else {
                generator.spawnProvider.setSpawner(generator, real, odds, x, roomY, z, EntityType.ZOMBIE, true);
                placed++;
            }
        }
    }

    /**
     * Carve a sealed 2-tall pocket in the foundation directly beneath the cellar floor at {@code floorY}
     * and hide a zombie spawner in it. Only carves under columns whose floor block is solid, so the visible
     * floor above always stays intact as the cap. Returns false if there isn't solid foundation to hollow
     * (a sub-basement or flooded cistern below), where a "hidden" spawner wouldn't be hidden at all.
     */
    private static boolean buryUnderFloor(CityWorldGenerator generator, RealBlocks real, Odds odds, int x,
            int floorY, int z) {
        int top = floorY - 1, bot = floorY - 2; // the 2-tall pocket
        // need solid material to hollow through, and a solid base to stand the pocket (and its mobs) on
        if (real.isEmpty(x, top, z) || real.isEmpty(x, bot, z) || real.isEmpty(x, bot - 1, z))
            return false;

        for (int dx = -1; dx <= 1; dx++)
            for (int dz = -1; dz <= 1; dz++) {
                int cx = x + dx, cz = z + dz;
                if (real.isEmpty(cx, floorY, cz))
                    continue; // no solid cap here — carving would open a visible hole; skip this column
                real.setBlock(cx, top, cz, Material.AIR);
                real.setBlock(cx, bot, cz, Material.AIR);
            }

        generator.spawnProvider.setSpawner(generator, real, odds, x, bot, z, EntityType.ZOMBIE, true);
        return true;
    }
}
