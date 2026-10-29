package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ServerLevelData;

/**
 * Room for Modernlife's bank (CurseForge 1623799, 1.20.1). Modernlife does not generate the bank as a structure: when
 * a new player picks an economy difficulty, it pastes {@code modernlife:bank} (29 x 19 x 25) on the first flat land it
 * finds near them, with full block updates, over whatever is there. In a CityWorld world that was a shop, its item
 * frames and their weapons dropping to the floor (owner, 2026-10-29).
 *
 * <p>So on a new world with Modernlife installed, the spawn goes on a paved town square
 * ({@link CityWorldAPI#reservePlaza}) at street level round chunk 0,0, {@code shops.bankPlazaRadius} chunks out, and
 * the bank lands on the square. Where exactly is Modernlife's arithmetic, read from its jar (2026-10-29): it tries the
 * offsets (+8, +8) first and on flat land that passes at once, then builds ten blocks back from there, so the bank's
 * corner is the player's position minus two; vanilla scatters a new player up to ten blocks from the spawn. The
 * bank therefore covers spawn x - 12 .. x + 36, z - 12 .. z + 32, centred on a spawn at (-4, -2). The flat-land check
 * samples the heights at the four corners of the 29 x 25 it is about to try, (+8, +8) from the player and 29 further,
 * so those reach 43 blocks out: the square has to be two chunks out (80 blocks) for them all to land on it. At one
 * chunk a corner fell on the road beside the square, whose street lamps are four high, and Modernlife stepped on
 * in eights until the check passed, 24 blocks south, with a third of the bank on the beach (2026-10-29). Recognised
 * by its ATM block,
 * not only its mod id: Modern Life (with a space) uses the same id {@code modernlife} and builds no bank.
 */
public final class ModernlifeBank {

    private ModernlifeBank() {
    }

    /** Reserve the plaza and put the spawn on it; true when it did (the caller then cancels vanilla's spawn search). */
    public static boolean reservePlaza(ServerLevel level, ServerLevelData settings) {
        if (level.dimension() != Level.OVERWORLD
                || !BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("modernlife", "atm"))
                || !(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator generator))
            return false;
        var context = generator.getContext(level);
        int radius = context.getSettings().bankPlazaRadius;
        if (radius <= 0)
            return false;
        int y = context.streetLevel;
        var result = CityWorldAPI.reservePlaza(level, new ChunkPos(0, 0), radius, y);
        if (result != me.daddychurchill.CityWorld.api.SiteResult.RESERVED) {
            CityWorldMod.LOGGER.info("CityWorld: no square for Modernlife's bank at spawn ({})", result);
            return false;
        }
        int ground = Math.max(y, context.seaLevel + 1);
        settings.setSpawn(new BlockPos(-4, ground + 1, -2), 0.0f); // see the class note for the -4, -2
        CityWorldMod.LOGGER.info("CityWorld: a paved square round the spawn ({} chunks) for Modernlife's bank", radius);
        return true;
    }
}
