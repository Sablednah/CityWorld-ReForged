package me.daddychurchill.CityWorld.Support;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The Nether's caves get their biome's ground, not just the ore provider's netherrack.
 *
 * <p><b>Why this has to exist.</b> CityWorld never runs a biome's surface rules — it lays terrain from the
 * {@code OreProvider} and then swaps the <em>exposed top</em> of each column to the biome's signature block
 * ({@code PlatLot.applyBiomeGround}). Vanilla's nether gets its nylium, soul soil and basalt from surface
 * rules, which apply to every air-facing face including the ones inside caves; ours only ever touched the
 * surface. So a crimson forest was crimson on top and plain netherrack the moment you went underground —
 * right biome, right fog, right canopy, and a cave system that belonged to no biome at all. The owner's
 * report: "crimson and warped look great on the surface, but they don't seem to want to do anything with
 * the caves below" (2026-09-16), and it was never crimson-and-warped specific — every nether biome with a
 * distinctive ground had the same hollow underneath.
 *
 * <p><b>Floors only, deliberately.</b> Vanilla's nether does the same: inside a crimson forest cave the
 * floor is nylium and the walls and ceiling stay netherrack. It also happens to be what the modded features
 * want — BoP's flesh tendons grow <em>upward</em> from flesh ground, so a coated floor is what puts them in
 * a cave. Coating walls too would read as a painted tunnel rather than a biome.
 *
 * <p><b>Cost.</b> Unlike {@link LushCaves}, which scans only the ~5% of columns inside a lush patch, this
 * runs on every column of every nether chunk. That is why it reads raw {@link BlockState}s through one
 * {@link BlockPos.MutableBlockPos} instead of {@code SupportBlocks}'s per-read {@code Block} wrapper: the
 * same scan through the wrapper would allocate about 30,000 short-lived objects per chunk.
 */
public final class NetherCaveGround {

    private NetherCaveGround() {}

    public static void apply(CityWorldGenerator generator, SupportBlocks chunk) {
        if (!(chunk instanceof RealBlocks real))
            return;
        ServerLevelAccessor level = real.getServerLevel();
        if (level == null)
            return;

        int oX = real.getOriginX(), oZ = real.getOriginZ();
        int top = generator.seaLevel - 3; // caves, not the surface pass's business
        int bottom = real.minY + 6; // above the bedrock floor
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = 0; x < real.width; x++)
            for (int z = 0; z < real.width; z++) {
                int wx = oX + x, wz = oZ + z;
                boolean airAbove = false;
                for (int y = top; y > bottom; y--) {
                    BlockState state = level.getBlockState(pos.set(wx, y, wz));
                    if (state.isAir()) {
                        airAbove = true;
                        continue;
                    }
                    if (airAbove && isRawStratum(state))
                        coat(real, x, y, z);
                    airAbove = false;
                }
            }
    }

    /**
     * Only the untouched stratum the ore provider laid. Anything else in a cave-facing position was put
     * there by something that meant it — a mine's supports, a sewer wall, a bastion, a lava lake's basalt
     * lining, an ore — and a biome swap has no business overwriting it.
     */
    private static boolean isRawStratum(BlockState state) {
        return state.is(Blocks.NETHERRACK);
    }

    /** Swap this cave floor to the biome's ground, if that biome declares one and it isn't already there. */
    private static void coat(RealBlocks real, int x, int y, int z) {
        var key = real.getBiomeKey(x, y, z);
        if (key == null)
            return;
        Material surface = BiomeSurface.surface(real.getBiomeHolder(x, y, z), key);
        if (surface == null)
            return; // a biome whose ground IS netherrack (nether wastes) — nothing to do
        real.setBlock(x, y, z, surface);
    }
}
