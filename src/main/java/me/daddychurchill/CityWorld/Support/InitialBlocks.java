package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * The terrain-writing implementation of the block seam, backed by a {@link ChunkAccess}
 * (a {@code ProtoChunk} during world generation). This replaces the Bukkit
 * {@code ChunkGenerator.ChunkData} used by the original.
 *
 * <p>Callers address blocks in chunk-local coordinates (x,z in {@code 0..15}, y in world space);
 * this class maps them to world {@link BlockPos} for the chunk it wraps. {@code ProtoChunk} updates
 * the generation heightmaps automatically on {@code setBlockState}.
 */
public final class InitialBlocks extends AbstractBlocks {

    public final ChunkAccess chunkData;

    public InitialBlocks(CityWorldGenerator aGenerator, ChunkAccess chunk, int sectionX, int sectionZ) {
        super(aGenerator);

        this.sectionX = sectionX;
        this.sectionZ = sectionZ;
        this.chunkData = chunk;
    }

    /** Chunk-local (x,z)+world y → world block position for the wrapped chunk. */
    private BlockPos at(int x, int y, int z) {
        return new BlockPos(getOriginX() + x, y, getOriginZ() + z);
    }

    private void put(int x, int y, int z, BlockState state) {
        chunkData.setBlockState(at(x, y, z), state);
    }

    public BlockState getState(int x, int y, int z) {
        return chunkData.getBlockState(at(x, y, z));
    }

    public boolean isType(int x, int y, int z, Material material) {
        return getState(x, y, z).is(material.getBlock());
    }

    public boolean isType(int x, int y, int z, Material... materials) {
        BlockState block = getState(x, y, z);
        for (Material material : materials)
            if (block.is(material.getBlock()))
                return true;
        return false;
    }

    @Override
    public boolean isEmpty(int x, int y, int z) {
        return getState(x, y, z).isAir();
    }

    @Override
    public void setAtmosphereBlock(int x, int y, int z, Material material) {
        put(x, y, z, material.getBlockState());

        // Disconnect any neighbouring connectable blocks (fences/panes/walls) that were facing into
        // this newly-cleared cell, matching the old MultipleFacing cleanup.
        if (x > 0)
            clearFaceToward(x - 1, y, z, BlockStateProperties.EAST);
        if (x < 15)
            clearFaceToward(x + 1, y, z, BlockStateProperties.WEST);
        if (z > 0)
            clearFaceToward(x, y, z - 1, BlockStateProperties.SOUTH);
        if (z < 15)
            clearFaceToward(x, y, z + 1, BlockStateProperties.NORTH);
    }

    private void clearFaceToward(int x, int y, int z, BooleanProperty face) {
        BlockState state = getState(x, y, z);
        if (state.hasProperty(face))
            put(x, y, z, state.setValue(face, false));
    }

    public Material getBlock(int x, int y, int z) {
        return Material.of(getState(x, y, z).getBlock());
    }

    @Override
    public void setBlock(int x, int y, int z, Material material) {
        put(x, y, z, material.getBlockState());
    }

    @Override
    protected void setBlock(int x, int y, int z, Material material, SlabType type) {
        put(x, y, z, material.asSlab(type));
    }

    @Override
    public void setBlock(int x, int y, int z, Material material, BlockFace facing) {
        put(x, y, z, material.withFacing(facing));
    }

    @Override
    public void setBlock(int x, int y, int z, Material material, BlockFace... facing) {
        put(x, y, z, material.withFaces(facing));
    }

    @Override
    public void setBlockIfEmpty(int x, int y, int z, Material material) {
        if (isEmpty(x, y, z) && !isEmpty(x, y - 1, z))
            put(x, y, z, material.getBlockState());
    }

    @Override
    public void clearBlock(int x, int y, int z) {
        put(x, y, z, Blocks.AIR.defaultBlockState());
    }

    // ================ Walls
    @Override
    public void setWalls(int x1, int x2, int y1, int y2, int z1, int z2, Material material) {
        setBlocks(x1, x2, y1, y2, z1, z1 + 1, material);
        setBlocks(x1, x2, y1, y2, z2 - 1, z2, material);
        setBlocks(x1, x1 + 1, y1, y2, z1 + 1, z2 - 1, material);
        setBlocks(x2 - 1, x2, y1, y2, z1 + 1, z2 - 1, material);
    }

    // ================ Layers
    @Override
    public int setLayer(int blocky, Material material) {
        setBlocks(0, width, blocky, blocky + 1, 0, width, material);
        return blocky + 1;
    }

    @Override
    public int setLayer(int blocky, int height, Material material) {
        setBlocks(0, width, blocky, blocky + height, 0, width, material);
        return blocky + height;
    }

    @Override
    public int setLayer(int blocky, int height, int inset, Material material) {
        setBlocks(inset, width - inset, blocky, blocky + height, inset, width - inset, material);
        return blocky + height;
    }

    @Override
    public void setDoor(int x, int y, int z, Material material, BlockFace facing) {
        clearBlock(x, y, z);
        clearBlock(x, y + 1, z);

        facing = fixFacing(facing);
        facing = facing.getOppositeFace();

        put(x, y, z, material.asDoorHalf(false, facing));
        put(x, y + 1, z, material.asDoorHalf(true, facing));
    }
}
