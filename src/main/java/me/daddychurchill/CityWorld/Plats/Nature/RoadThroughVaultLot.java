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
 * seed under high terrain) with a rare branch DOWN into the vault — a trapdoor + ladder shaft dropping to
 * the vault hall, fronted by the cog blast door. Mirrors {@link RoadThroughBunkerLot} but draws the vault
 * hall/entrance instead of the bunker rig. Reuses {@link BunkerLot}'s static strata helpers so the vault's
 * Y-box stays hollow for the road-through slice too.
 */
public class RoadThroughVaultLot extends RoadLot {

    private final int bottomOfVault;
    private final int topOfVault;

    public RoadThroughVaultLot(PlatMap platmap, int chunkX, int chunkZ, long globalconnectionkey,
            boolean roundaboutPart, VaultLot originalLot) {
        super(platmap, chunkX, chunkZ, globalconnectionkey, roundaboutPart);
        this.bottomOfVault = originalLot.bottomOfBunker;
        this.topOfVault = originalLot.topOfBunker;
    }

    @Override
    public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
        return BunkerLot.bunkerIsValidStrataY(generator, blockX, blockY, blockZ, bottomOfVault, topOfVault);
    }

    @Override
    protected boolean isShaftableLevel(CityWorldGenerator generator, int blockY) {
        return BunkerLot.bunkerIsShaftableLevel(generator, blockY, bottomOfVault, topOfVault)
                && super.isShaftableLevel(generator, blockY);
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

        // draw the road (a mountain tunnel where the terrain is high, which is exactly where vaults sit)
        super.generateActualBlocks(generator, platmap, chunk, context, platX, platZ);

        // the vault hall below
        VaultLot.generateVaultHall(generator, chunk, chunkOdds, bottomOfVault, topOfVault);

        // and the way down into it
        generateVaultEntrance(generator, chunk, bottomOfVault);
    }

    private String vaultNumber() {
        return Integer.toString(Math.floorMod(getChunkX() * 31 + getChunkZ() * 17, 100));
    }

    private void generateVaultEntrance(CityWorldGenerator generator, RealBlocks chunk, int bottom) {
        int streetY = generator.streetLevel;
        int floorY = VaultLot.floorY(bottom);
        int ceilY = VaultLot.ceilingY(bottom, topOfVault);

        // open a shaft through the hall ceiling for the entry ladder
        chunk.setBlocks(5, 8, ceilY, ceilY + 1, 6, 8, Material.AIR);
        // a backing wall for the ladder, so the drop reads as a shaft rather than a floating ladder
        chunk.setBlocks(7, floorY, streetY + 1, 7, VaultLot.CEIL);
        // trapdoor in the tunnel floor + a ladder all the way down into the hall
        chunk.setBlock(6, streetY, 7, Material.BIRCH_TRAPDOOR, BlockFace.WEST, Half.TOP);
        chunk.setLadder(6, floorY, streetY, 7, BlockFace.WEST);

        // the cog blast door, standing across the landing
        VaultLot.cogDoor(chunk, 8, floorY, 9);

        // a VAULT signpost by the ladder
        chunk.setBlock(4, floorY, 7, VaultLot.PILLAR);
        chunk.setSignPost(4, floorY + 1, 7, Material.OAK_SIGN, BlockFace.EAST,
                new String[] { "VAULT", vaultNumber() });

        generator.reportLocation("Vault " + vaultNumber(), chunk);
    }
}
