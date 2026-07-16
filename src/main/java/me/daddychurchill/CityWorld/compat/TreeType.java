package me.daddychurchill.CityWorld.compat;

/**
 * Shim for Bukkit's {@code org.bukkit.TreeType}.
 *
 * <p><b>An enum, unlike {@link Material} and {@link Biome}</b> — and for the opposite reason. Those
 * are value vocabularies over an open registry, so they became interned wrappers. This one is only
 * ever <em>switched on</em>: {@code TreeProvider} reads a {@code TreeType} to decide a trunk height
 * and pick log/leaf materials, then lays the tree out block by block itself.
 *
 * <p>That is why this costs nothing to port. In Bukkit a {@code TreeType} was an argument to
 * {@code World.generateTree(...)} — the server grew the tree for you — and there is no modern
 * equivalent to that call short of vanilla's configured features. CityWorld never used it: it draws
 * its own trees, so all it ever needed was the name.
 *
 * <p>Only the thirteen values CityWorld actually names are here. Bukkit's enum is longer (cocoa,
 * chorus, the nether fungi, azalea); the rest can be added if a ported file ever asks.
 */
public enum TreeType {
    TREE,
    BIG_TREE,
    REDWOOD,
    TALL_REDWOOD,
    MEGA_REDWOOD,
    BIRCH,
    TALL_BIRCH,
    JUNGLE,
    SMALL_JUNGLE,
    JUNGLE_BUSH,
    SWAMP,
    ACACIA,
    DARK_OAK,
    COCOA_TREE,

    // Named in TreeProvider's height switch (as "we don't do these yet") but never placed.
    BROWN_MUSHROOM,
    RED_MUSHROOM
}
