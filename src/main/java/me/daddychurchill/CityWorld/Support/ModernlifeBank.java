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
 * finds within 32 blocks of them, ten blocks back from that spot, with full block updates, over whatever is there. In a
 * CityWorld world that was a shop, its item frames and their weapons dropping to the floor (owner, 2026-10-29).
 *
 * <p>So on a new world with Modernlife installed, the spawn goes in the middle of an open, level plaza
 * ({@link CityWorldAPI#reserveSite}) at street level round chunk 0,0, {@code shops.bankPlazaRadius} chunks out; the
 * player arrives on it, and the bank's flat-land search succeeds on the spot. Recognised by its ATM block, not only its
 * mod id: Modern Life (with a space) uses the same id {@code modernlife} and builds no bank.
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
        var result = CityWorldAPI.reserveSite(level, new ChunkPos(0, 0), radius, y);
        if (result != me.daddychurchill.CityWorld.api.SiteResult.RESERVED) {
            CityWorldMod.LOGGER.info("CityWorld: no plaza for Modernlife's bank at spawn ({})", result);
            return false;
        }
        settings.setSpawn(new BlockPos(8, Math.max(y, context.seaLevel + 1) + 1, 8), 0.0f);
        CityWorldMod.LOGGER.info("CityWorld: an open plaza round the spawn ({} chunks) for Modernlife's bank", radius);
        return true;
    }
}
