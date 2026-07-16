package me.daddychurchill.CityWorld.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.phys.Vec3;

/**
 * Port shim for {@code org.bukkit.Location} — a point in a world.
 *
 * <p>Bukkit bundled the world reference and the coordinates into one object; modern Minecraft keeps
 * them apart ({@link LevelAccessor} plus a {@link BlockPos} or {@link Vec3}). CityWorld only ever
 * uses a Location as a handle to hand somewhere else — spawning, pasting schematics, reporting — so
 * this stays a plain carrier rather than growing Bukkit's arithmetic surface.
 */
public final class Location {

    private final LevelAccessor level;
    private final double x;
    private final double y;
    private final double z;

    public Location(LevelAccessor level, double x, double y, double z) {
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public int getBlockX() {
        return net.minecraft.util.Mth.floor(x);
    }

    public int getBlockY() {
        return net.minecraft.util.Mth.floor(y);
    }

    public int getBlockZ() {
        return net.minecraft.util.Mth.floor(z);
    }

    public BlockPos toBlockPos() {
        return BlockPos.containing(x, y, z);
    }

    public Vec3 toVec3() {
        return new Vec3(x, y, z);
    }

    @Override
    public String toString() {
        return "Location(" + x + ", " + y + ", " + z + ")";
    }
}
