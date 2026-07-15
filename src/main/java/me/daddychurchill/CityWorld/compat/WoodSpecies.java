package me.daddychurchill.CityWorld.compat;

/**
 * Port shim for {@code org.bukkit.TreeSpecies}.
 *
 * <p>The generator uses tree species to pick matching wood variants (logs, planks, stairs, slabs,
 * signs) in {@code Support/Trees}. Bukkit's {@code TreeSpecies} had exactly these six values; the
 * mapping from species to the corresponding vanilla blocks lives in {@code Trees} (ported later).
 * Kept to the original six for parity — newer wood types (mangrove, cherry, bamboo) can be added
 * when {@code Trees} is revisited.
 */
public enum WoodSpecies {
    GENERIC,   // oak
    REDWOOD,   // spruce
    BIRCH,
    JUNGLE,
    ACACIA,
    DARK_OAK
}
