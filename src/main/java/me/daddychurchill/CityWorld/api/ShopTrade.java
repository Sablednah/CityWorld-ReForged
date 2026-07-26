package me.daddychurchill.CityWorld.api;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.minecraft.resources.Identifier;

/**
 * The <b>trade</b> a shop plies — the fine half of CityWorld's shop taxonomy (the coarse half is
 * {@link ShopScale}). Each trade carries the vanilla villager profession and job-site block it maps
 * to, so the (later) interiors/villager passes and any downstream mod can turn a classification into
 * the right lectern, fletching table or blast furnace, and the right resident.
 *
 * <p>Part of CityWorld's small <b>public API</b>. The vanilla ids are exposed as {@link Identifier}s
 * ({@link #profession()} / {@link #jobBlock()}) rather than live registry objects so this enum stays a
 * pure, early-loadable data table. The set of trades is a stable contract: additive-only, no renames.
 *
 * <p>A trade is eligible for one or more {@link ShopScale}s — everyday needs turn up as corner shops,
 * specialists line the high street, and a few (butcher, apothecary) do both. {@link #tradesFor(ShopScale)}
 * is the picker's menu for a given setting.
 */
public enum ShopTrade {

    // --- everyday needs: corner shops (a couple also work on the high street) --------------------
    NEWSAGENT("Newsagent", "librarian", "lectern", EnumSet.of(ShopScale.CORNER_SHOP)),
    GREENGROCER("Greengrocer", "farmer", "composter", EnumSet.of(ShopScale.CORNER_SHOP)),
    FISHMONGER("Fishmonger", "fisherman", "barrel", EnumSet.of(ShopScale.CORNER_SHOP)),
    BUTCHER("Butcher", "butcher", "smoker", EnumSet.of(ShopScale.CORNER_SHOP, ShopScale.HIGH_STREET)),
    APOTHECARY("Apothecary", "cleric", "brewing_stand", EnumSet.of(ShopScale.CORNER_SHOP, ShopScale.HIGH_STREET)),

    // --- specialists: the high street -----------------------------------------------------------
    BOOKSHOP("Bookshop", "librarian", "lectern", EnumSet.of(ShopScale.HIGH_STREET)),
    CARTOGRAPHER("Map seller", "cartographer", "cartography_table", EnumSet.of(ShopScale.HIGH_STREET)),
    FLETCHER("Fletcher", "fletcher", "fletching_table", EnumSet.of(ShopScale.HIGH_STREET)),
    BUILDERS_MERCHANT("Builder's merchant", "mason", "stonecutter", EnumSet.of(ShopScale.HIGH_STREET)),
    ARMOURER("Armourer", "armorer", "blast_furnace", EnumSet.of(ShopScale.HIGH_STREET)),
    TOOLSMITH("Ironmonger", "toolsmith", "smithing_table", EnumSet.of(ShopScale.HIGH_STREET)),
    WEAPONSMITH("Weaponsmith", "weaponsmith", "grindstone", EnumSet.of(ShopScale.HIGH_STREET)),
    COBBLER("Cobbler", "leatherworker", "cauldron", EnumSet.of(ShopScale.HIGH_STREET)),
    DRAPER("Draper", "shepherd", "loom", EnumSet.of(ShopScale.HIGH_STREET));

    private final String displayName;
    private final Identifier profession;
    private final Identifier jobBlock;
    private final Set<ShopScale> scales;

    ShopTrade(String displayName, String professionPath, String jobBlockPath, EnumSet<ShopScale> scales) {
        this.displayName = displayName;
        this.profession = Identifier.withDefaultNamespace(professionPath);
        this.jobBlock = Identifier.withDefaultNamespace(jobBlockPath);
        this.scales = scales;
    }

    /** Human-readable shop-front label (e.g. "Map seller"). */
    public String displayName() {
        return displayName;
    }

    /** The vanilla {@code minecraft:villager_profession} this trade maps to (e.g. {@code minecraft:cartographer}). */
    public Identifier profession() {
        return profession;
    }

    /** The vanilla job-site block this trade maps to (e.g. {@code minecraft:cartography_table}). */
    public Identifier jobBlock() {
        return jobBlock;
    }

    /** Whether this trade can appear at the given {@link ShopScale}. */
    public boolean appearsAt(ShopScale scale) {
        return scales.contains(scale);
    }

    /** The trades eligible for a given setting, in declaration order — the picker's menu. */
    public static List<ShopTrade> tradesFor(ShopScale scale) {
        return Stream.of(values()).filter(t -> t.appearsAt(scale)).collect(Collectors.toList());
    }
}
