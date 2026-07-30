package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.world.level.block.state.properties.Half;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.RoadLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * The road that crosses a buried {@link VaultLot}: a normal road (usually a mountain tunnel, since vaults
 * seed under high terrain) with a branch DOWN into the vault hall — a trapdoor + ladder shaft carved
 * through the rock. Mirrors the {@link VaultLot} strata rule and hall so the two slices join seamlessly.
 */
public class RoadThroughVaultLot extends RoadLot {

    private final int bottomOfVault;
    private final int topOfVault;
    private final boolean entrance;

    public RoadThroughVaultLot(PlatMap platmap, int chunkX, int chunkZ, long globalconnectionkey,
            boolean roundaboutPart, VaultLot originalLot, boolean entrance) {
        super(platmap, chunkX, chunkZ, globalconnectionkey, roundaboutPart);
        this.bottomOfVault = originalLot.bottomOfBunker;
        this.topOfVault = originalLot.topOfBunker;
        this.entrance = entrance;
    }

    @Override
    public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
        return VaultLot.vaultIsValidStrataY(blockY, bottomOfVault, topOfVault);
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return false;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return bottomOfVault;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return topOfVault;
    }

    @Override
    protected boolean isValidWithBones() {
        return false;
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        super.generateActualChunk(generator, platmap, chunk, biomes, context, platX, platZ);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {

        // the road (a mountain tunnel where the terrain is high, which is exactly where vaults sit)
        super.generateActualBlocks(generator, platmap, chunk, context, platX, platZ);

        // the vault hall below, and a branch down into it from the tunnel
        VaultLot.generateVaultHall(chunk, bottomOfVault, topOfVault, VaultLot.wallFlags(platmap, platX, platZ));
        tunnelBranch(generator, chunk);

        int num = Math.floorMod(getChunkX() * 31 + getChunkZ() * 17, 100);
        if (entrance)
            VaultLot.generateEntrance(chunk, chunkOdds, bottomOfVault, topOfVault, blockYs.getBlockY(8, 8), num);
        generator.reportLocation("Vault " + num, chunk);
    }

    /** Carve the trapdoor + ladder shaft from the tunnel floor down through the rock into the hall. */
    private void tunnelBranch(CityWorldGenerator generator, RealBlocks chunk) {
        int streetY = generator.streetLevel;
        int floorY = VaultLot.floorY(bottomOfVault);
        int ceilY = VaultLot.ceilingY(bottomOfVault, topOfVault);

        chunk.setBlocks(5, 8, ceilY, ceilY + 1, 6, 8, Material.AIR); // open the hall ceiling under the shaft
        chunk.setBlocks(6, floorY, streetY, 7, Material.AIR); // clear the ladder column through the rock
        chunk.setBlocks(7, floorY, streetY + 1, 7, VaultLot.CEIL); // backing wall for the ladder
        chunk.setBlock(6, streetY, 7, Material.BIRCH_TRAPDOOR, BlockFace.WEST, Half.TOP); // hatch in the tunnel floor
        chunk.setLadder(6, floorY + 1, streetY, 7, BlockFace.WEST); // +1 so the bottom rung isn't in the floor
    }
}
