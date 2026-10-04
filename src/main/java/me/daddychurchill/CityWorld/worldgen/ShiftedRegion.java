package me.daddychurchill.CityWorld.worldgen;

import java.util.function.Predicate;

import org.jspecify.annotations.Nullable;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.ticks.ScheduledTick;
import net.minecraft.world.ticks.TickPriority;

/**
 * A decoration region seen from {@code shift} blocks lower: every position handed to it is raised by the shift
 * before it reaches the real region, and every height it reports is lowered by it.
 *
 * <p><b>Why.</b> In a vanilla-terrain world each city stands at its own level ({@link CitySites}), but CityWorld
 * plans and draws against ONE street level — some three hundred files read {@code generator.streetLevel} and its
 * kin. So the city is drawn exactly as it always is, at the usual street level, and lifted on the way into the
 * world. The generation half goes through {@code InitialBlocks.yShift}; this is the decoration half, and it has
 * to be the level itself because decoration reaches the world by many roads: {@code compat.Block}, block entities,
 * loot, schematics pasted by vanilla's template code, trees grown through vanilla features, entities. All of them
 * end at the handful of methods below.
 *
 * <p><b>It is a real {@code WorldGenRegion}</b> (a subclass over the same chunk cache), because code down the
 * line asks {@code instanceof WorldGenRegion} and reads its centre. But the shifted calls are forwarded to the
 * region vanilla made, not to {@code super}: vanilla's own methods call each other ({@code setBlock} asks
 * {@code ensureCanWrite}, {@code destroyBlock} asks {@code getBlockState}), and through {@code super} each of
 * those hops would shift again.
 *
 * <p>Block entities come back with their real position, and an entity's position is raised as it is added — so
 * nothing in the saved world knows it was drawn lower.
 */
public final class ShiftedRegion extends WorldGenRegion {

    private final WorldGenRegion real;
    private final int shift;

    private ShiftedRegion(WorldGenRegion real, int shift) {
        super(real.getLevel(), holders(real), real.generatingStep, real.center);
        this.real = real;
        this.shift = shift;
    }

    /**
     * 26.3: the region maps the chunk-holder cache it was built from into plain chunks and keeps only those, so a
     * second region over the same chunks rebuilds that cache from the server's own holders — the same ones vanilla
     * read, for the same square (the region drops anything past its step's dependencies itself).
     */
    private static net.minecraft.util.StaticCache2D<net.minecraft.server.level.GenerationChunkHolder> holders(
            WorldGenRegion real) {
        var chunks = real.getLevel().getChunkSource().chunkMap;
        var centre = real.center.getPos();
        int range = real.generatingStep.directDependencies().size();
        return net.minecraft.util.StaticCache2D.create(centre.x(), centre.z(), range, (x, z) -> {
            long key = net.minecraft.world.level.ChunkPos.pack(x, z);
            var holder = chunks.getUpdatingChunkIfPresent(key);
            return holder != null ? holder : chunks.getVisibleChunkIfPresent(key);
        });
    }

    /** The level to hand a lot drawn {@code shift} blocks below where it belongs; the level itself when zero. */
    public static WorldGenLevel of(WorldGenLevel level, int shift) {
        if (shift == 0)
            return level;
        if (level instanceof ShiftedRegion already)
            return of(already.real, already.shift + shift);
        if (level instanceof WorldGenRegion region)
            return new ShiftedRegion(region, shift);
        throw new IllegalStateException("CityWorld: a city at its own level needs a WorldGenRegion to draw into, got "
                + level.getClass().getName());
    }

    private BlockPos up(BlockPos pos) {
        return pos.offset(0, shift, 0);
    }

    @Override
    public BlockState getBlockState(BlockPos pos) {
        return real.getBlockState(up(pos));
    }

    @Override
    public FluidState getFluidState(BlockPos pos) {
        return real.getFluidState(up(pos));
    }

    @Override
    public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
        return real.getBlockEntity(up(pos));
    }

    @Override
    public boolean ensureCanWrite(BlockPos pos) {
        return real.ensureCanWrite(up(pos));
    }

    @Override
    public boolean setBlock(BlockPos pos, BlockState state, int flags, int recursionLeft) {
        return real.setBlock(up(pos), state, flags, recursionLeft);
    }

    @Override
    public boolean removeBlock(BlockPos pos, boolean movedByPiston) {
        return real.removeBlock(up(pos), movedByPiston);
    }

    @Override
    public boolean destroyBlock(BlockPos pos, boolean dropBlock, @Nullable Entity entity, int recursionLeft) {
        return real.destroyBlock(up(pos), dropBlock, entity, recursionLeft);
    }

    @Override
    public boolean isStateAtPosition(BlockPos pos, Predicate<BlockState> test) {
        return real.isStateAtPosition(up(pos), test);
    }

    @Override
    public boolean isFluidAtPosition(BlockPos pos, Predicate<FluidState> test) {
        return real.isFluidAtPosition(up(pos), test);
    }

    @Override
    public DifficultyInstance getCurrentDifficultyAt(BlockPos pos) {
        return real.getCurrentDifficultyAt(up(pos));
    }

    @Override
    public int getHeight(Heightmap.Types type, int x, int z) {
        return real.getHeight(type, x, z) - shift;
    }

    @Override
    public Holder<Biome> getUncachedNoiseBiome(int quartX, int quartY, int quartZ) {
        return real.getUncachedNoiseBiome(quartX, quartY + (shift >> 2), quartZ);
    }

    @Override
    public boolean addFreshEntity(Entity entity) {
        // built at the height the city is drawn at; it lives where the city stands
        entity.setPos(entity.getX(), entity.getY() + shift, entity.getZ());
        return real.addFreshEntity(entity);
    }

    @Override
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay, TickPriority priority) {
        return super.createTick(up(pos), type, delay, priority);
    }

    @Override
    public <T> ScheduledTick<T> createTick(BlockPos pos, T type, int delay) {
        return super.createTick(up(pos), type, delay);
    }
}
