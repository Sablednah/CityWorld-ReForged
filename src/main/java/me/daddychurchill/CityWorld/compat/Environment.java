package me.daddychurchill.CityWorld.compat;

/**
 * Shim for Bukkit's {@code org.bukkit.World.Environment}.
 *
 * <p>The port registers a single overworld dimension, so this is always {@link #NORMAL} — but the
 * value is kept rather than the branches deleted, because several ported files legitimately ask
 * (a farm grows netherwart instead of wheat in the Nether, ground cover changes entirely). A
 * Nether or End CityWorld would be a second registered dimension, which is its own piece of work;
 * when it lands, these branches are already written.
 */
public enum Environment {
    NORMAL,
    NETHER,
    THE_END
}
