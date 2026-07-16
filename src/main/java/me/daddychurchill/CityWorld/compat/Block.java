package me.daddychurchill.CityWorld.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Port shim for {@code org.bukkit.block.Block} — a <em>live, positioned</em> block reference.
 *
 * <p>The decoration half of the generator ({@code SupportBlocks} and friends) is written against
 * Bukkit's {@code Block}: an object that knows its own world and coordinates and can read/write
 * itself. Modern Minecraft has no such object — you carry a {@link LevelAccessor} plus a
 * {@link BlockPos} — so this pairs them up, letting the ~2,400 lines of decoration logic port with
 * type swaps rather than a rewrite.
 *
 * <p>Two mappings worth knowing:
 * <ul>
 *   <li>Bukkit's {@code BlockData} is modern {@link BlockState} — so {@code getBlockData()} /
 *       {@code setBlockData()} here read and write block states.</li>
 *   <li>Bukkit's "apply physics" flag maps onto vanilla's update flags: physics on →
 *       {@code UPDATE_ALL} (notify neighbours), physics off → {@code UPDATE_CLIENTS} (write the
 *       block, skip neighbour reactions) — which is what the generator wants while building.</li>
 * </ul>
 *
 * <p>Unlike {@link Material} these are <em>not</em> interned: each instance is a cheap, throwaway
 * cursor onto one position.
 */
public final class Block {

    /** Write the block and tell clients, but don't trigger neighbour physics (generator default). */
    private static final int NO_PHYSICS = net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
    /** Write the block and let neighbours react. */
    private static final int WITH_PHYSICS = net.minecraft.world.level.block.Block.UPDATE_ALL;

    private final LevelAccessor level;
    private final BlockPos pos;

    public Block(LevelAccessor level, BlockPos pos) {
        this.level = level;
        this.pos = pos;
    }

    public Block(LevelAccessor level, int x, int y, int z) {
        this(level, new BlockPos(x, y, z));
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public int getX() {
        return pos.getX();
    }

    public int getY() {
        return pos.getY();
    }

    public int getZ() {
        return pos.getZ();
    }

    /** The block state at this position (Bukkit's {@code getBlockData()}). */
    public BlockState getBlockData() {
        return level.getBlockState(pos);
    }

    /** Write a block state here, without neighbour physics. */
    public void setBlockData(BlockState state) {
        setBlockData(state, false);
    }

    public void setBlockData(BlockState state, boolean applyPhysics) {
        level.setBlock(pos, state, applyPhysics ? WITH_PHYSICS : NO_PHYSICS);
    }

    /** The material at this position. */
    public Material getType() {
        return Material.of(getBlockData().getBlock());
    }

    /** Place a material here (its default state), without neighbour physics. */
    public void setType(Material material) {
        setType(material, false);
    }

    public void setType(Material material, boolean applyPhysics) {
        BlockState state = material.getBlockState();
        setBlockData(state == null ? Blocks.AIR.defaultBlockState() : state, applyPhysics);
    }

    /**
     * The block entity here, or {@code null} — Bukkit's {@code getState()}, used to reach signs,
     * chests and spawners.
     */
    public BlockEntity getState() {
        return level.getBlockEntity(pos);
    }

    public boolean isEmpty() {
        return getBlockData().isAir();
    }

    public boolean isLiquid() {
        return !level.getFluidState(pos).isEmpty();
    }

    public boolean isType(Material material) {
        return getBlockData().is(material.getBlock());
    }

    /** The neighbouring block in the given direction. */
    public Block getRelative(BlockFace face) {
        return new Block(level, pos.offset(face.getModX(), face.getModY(), face.getModZ()));
    }

    @Override
    public String toString() {
        return "Block(" + pos.toShortString() + " = " + getBlockData().getBlock() + ")";
    }
}
