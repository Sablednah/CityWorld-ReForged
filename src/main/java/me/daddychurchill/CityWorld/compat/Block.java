package me.daddychurchill.CityWorld.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;

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
 *   <li>Bukkit's "apply physics" flag maps onto vanilla's update flags — physics on →
 *       {@code UPDATE_ALL} (notify neighbours), physics off → {@code UPDATE_CLIENTS} (write the
 *       block, skip neighbour reactions) — <em>plus an explicit fluid tick</em>. The flag alone is
 *       not enough during generation, because a {@code ProtoChunk} never runs {@code onPlace}; see
 *       {@link #setBlockData(BlockState, boolean)}, which is where the sewers went dry.</li>
 * </ul>
 *
 * <p>Unlike {@link Material} these are <em>not</em> interned: each instance is a cheap, throwaway
 * cursor onto one position.
 */
public final class Block {

    /**
     * Write the block exactly as told and tell clients, with no reactions at all (generator
     * default) — the port of Bukkit's {@code applyPhysics = false}.
     *
     * <p>{@code UPDATE_CLIENTS} alone is <em>not</em> enough, and quietly corrupts what the
     * generator places: it still lets the block run {@code onPlace}, so a powered rail re-reads the
     * redstone around it and un-powers itself, and a plant checks what it is standing on and
     * deletes itself. {@code UPDATE_SKIP_ALL_SIDEEFFECTS} is vanilla's name for the combination
     * that suppresses all of it — skip {@code onPlace}, skip shape updates, suppress drops, and
     * skip block-entity removal side effects (so overwriting a chest doesn't spew its contents).
     */
    private static final int NO_PHYSICS = net.minecraft.world.level.block.Block.UPDATE_SKIP_ALL_SIDEEFFECTS
            | net.minecraft.world.level.block.Block.UPDATE_CLIENTS;
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

    /**
     * Write a block state here, optionally letting it react — the port of Bukkit's
     * {@code setBlockData(data, applyPhysics)}.
     *
     * <p><b>The update flag is not the whole translation, and believing it was cost us the sewers.</b>
     * Decoration writes through a {@code WorldGenRegion} onto a {@code ProtoChunk}, and
     * {@code ProtoChunk.setBlockState} <em>never calls {@code onPlace}</em> and never notifies
     * neighbours — it writes the section, the heightmaps and the light, and stops. So
     * {@code UPDATE_ALL} is very nearly inert here, and the one thing CityWorld actually wants
     * physics for silently did nothing: {@code LiquidBlock.onPlace} is what schedules a fluid's
     * first tick, and without it placed water is simply a block that sits there. Upstream never
     * noticed because a Bukkit {@code BlockPopulator} ran on a live, ticking world, where physics
     * fired at once and the flow was baked into the chunk.
     *
     * <p>So we schedule the tick ourselves, which is exactly what vanilla does when <em>it</em>
     * places a fluid during worldgen — {@code SpringFeature} and {@code LakeFeature} both follow
     * their {@code setBlock} with an explicit {@code scheduleTick}, for this very reason. The delay
     * is the fluid's own, mirroring {@code onPlace} rather than inventing a number.
     *
     * <p>The water then flows once the chunk ticks, rather than during generation. That is the one
     * behavioural difference from upstream that remains, and it is invisible in play: a chunk ticks
     * when a player is near enough to be looking at it.
     */
    public void setBlockData(BlockState state, boolean applyPhysics) {
        level.setBlock(pos, state, applyPhysics ? WITH_PHYSICS : NO_PHYSICS);

        if (applyPhysics) {
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty())
                level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
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
