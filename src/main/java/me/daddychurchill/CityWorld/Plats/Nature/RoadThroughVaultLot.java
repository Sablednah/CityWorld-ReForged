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
        return VaultLot.vaultIsValidStrataY(blockY, bottomOfVault, topOfVault, false);
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return false;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return VaultLot.levelFloor(bottomOfVault, VaultLot.NUM_LEVELS - 1) - 1; // cover the deepest level
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

        // the vault interior below (all levels), and a branch down into it from the tunnel
        boolean[] walls = VaultLot.wallFlags(platmap, platX, platZ);
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        VaultLot.generateVaultHall(generator, chunkOdds, chunk, bottomOfVault, topOfVault, walls, oX, oZ);
        VaultLot.generateLowerLevels(generator, chunkOdds, chunk, bottomOfVault, walls, oX, oZ, false, getChunkX(), getChunkZ());
        tunnelBranch(generator, chunk); // the road-tunnel entry (routed through a vestibule + door)
        // "vaultroad" is off the default announce list: the entrance chunk (VaultLot) is the one true
        // announcement — this fires per road chunk with a per-chunk hash, useful only as a debug trace.
        generator.reportLocation("vaultroad", "Vault road " + Math.floorMod(getChunkX() * 31 + getChunkZ() * 17, 100), chunk);
    }

    /** Carve the trapdoor + ladder shaft from the tunnel floor down through the rock. It lands INSIDE the NE
     *  quadrant room (which has its own doorway onto the corridor) + an iron door on that doorway, so the road
     *  entry is a controlled way in through a door, not a bare drop into the main corridor. */
    private void tunnelBranch(CityWorldGenerator generator, RealBlocks chunk) {
        int streetY = generator.streetLevel;
        int floorY = VaultLot.floorY(bottomOfVault);
        int ceilY = VaultLot.ceilingY(bottomOfVault, topOfVault);
        int lx = 11, lz = 3; // inside the NE room (x10-15, z1-5)

        chunk.setBlocks(lx - 1, lx + 2, ceilY, ceilY + 1, lz - 1, lz + 2, Material.AIR); // open the room ceiling
        chunk.setBlocks(lx, floorY, streetY, lz, Material.AIR); // clear the ladder column through the rock
        chunk.setBlocks(lx + 1, floorY, streetY + 1, lz, VaultLot.CEIL); // backing wall for the ladder
        chunk.setBlock(lx, streetY, lz, Material.BIRCH_TRAPDOOR, BlockFace.WEST, Half.TOP); // hatch in the tunnel floor
        chunk.setLadder(lx, floorY + 1, streetY, lz, BlockFace.WEST);
        // a door on the NE room's corridor doorway (x=9, z 2-3), so you leave the entry through a door
        chunk.setBlocks(9, 10, floorY + 1, floorY + 3, 2, 4, Material.AIR);
        chunk.setDoor(9, floorY + 1, 3, Material.OAK_DOOR, BlockFace.WEST);
    }
}
