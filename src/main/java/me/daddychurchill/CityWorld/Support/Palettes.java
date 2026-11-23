package me.daddychurchill.CityWorld.Support;

import java.util.List;
import java.util.Locale;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Per-part palettes (6.14): a block tag for each part of a building or street, so a datapack can say which blocks
 * CityWorld uses for, say, office walls and nothing else. Asked for on GitHub issue #5 ("choose what kind of blocks
 * cityworld generates per part of the structure"), and the seam the owner's later city styles are meant to be built
 * from: a style is mostly a set of these.
 *
 * <p>A part reads {@code #cityworld:palette/biome/<theme>/<part>} first, when a pack gives the building's biome a
 * theme (7.3, {@code worldgen/BiomeThemes}), then {@code #cityworld:palette/<style>/<part>} (e.g.
 * {@code palette/apocalypse/building/wall}), then {@code #cityworld:palette/<part>}. <b>Every one ships empty</b>, and an empty part changes nothing: the call
 * site keeps the block it already picked, with the rolls it always made. A filled part replaces that block with one
 * from the tag, picked on dice of its own, seeded from the world seed, the part and the position it is given — so a
 * pack never shifts any other choice in the building, and the same seed builds the same city.
 */
public final class Palettes {

    private Palettes() {
    }

    /** Every part a pack can fill, with its tag path under {@code cityworld:palette/}. */
    public enum Part {
        /** Office blocks, shops, libraries, museums and warehouses: the outside walls (and the interior walls). */
        BUILDING_WALL("building/wall"),
        BUILDING_FOUNDATION("building/foundation"),
        /** The floor and ceiling slabs between storeys. */
        BUILDING_FLOOR("building/floor"),
        BUILDING_ROOF("building/roof"),
        /** Corner columns and trim. */
        BUILDING_COLUMN("building/column"),
        BUILDING_WINDOW("building/window"),
        /** City halls and other civic buildings. */
        GOVERNMENT_WALL("government/wall"),
        GOVERNMENT_FOUNDATION("government/foundation"),
        GOVERNMENT_FLOOR("government/floor"),
        GOVERNMENT_ROOF("government/roof"),
        GOVERNMENT_COLUMN("government/column"),
        /** Houses, the modern houses among them. */
        HOUSE_WALL("house/wall"),
        HOUSE_FLOOR("house/floor"),
        HOUSE_CEILING("house/ceiling"),
        HOUSE_ROOF("house/roof"),
        HOUSE_WINDOW("house/window"),
        /** Rural shacks and cabins. */
        SHACK_WALL("shack/wall"),
        SHACK_FLOOR("shack/floor"),
        SHACK_ROOF("shack/roof"),
        /** Garden and farm sheds. */
        SHED_WALL("shed/wall"),
        SHED_ROOF("shed/roof"),
        /** Factories: the hall's inner walls, the office block, the supports and the smokestack. */
        FACTORY_WALL("factory/wall"),
        FACTORY_OFFICE("factory/office"),
        FACTORY_SUPPORT("factory/support"),
        FACTORY_SMOKESTACK("factory/smokestack"),
        /** Paved roads, one pick for the whole world. */
        STREET_SURFACE("street/surface"),
        STREET_LINES("street/lines"),
        /** Placed as given: the default is a stone slab, so a full block raises the pavement a half step. */
        STREET_SIDEWALK("street/sidewalk");

        public final String path;

        Part(String path) {
            this.path = path;
        }
    }

    /**
     * {@code current}, or — when the pack fills {@code part} — a block from it, picked by the world seed, the part
     * and {@code (x, z)}. Lots pass their chunk; a pick that is one per world passes {@code (0, 0)}.
     */
    public static Material pick(CityWorldGenerator generator, Part part, int x, int z, Material current) {
        List<Material> pool = poolFor(generator, part, x, z);
        if (pool.isEmpty())
            return current;
        Odds odds = new Odds(seed(generator.getWorldSeed(), part, x, z));
        return pool.get(odds.getRandomInt(pool.size()));
    }

    /**
     * The palette for {@code part} at a chunk: its biome theme's ({@code #cityworld:palette/biome/<theme>/<part>},
     * the theme from {@code worldgen/BiomeThemes}; 7.3), else the style's, else the general one, else empty.
     */
    public static List<Material> poolFor(CityWorldGenerator generator, Part part, int x, int z) {
        String theme = generator.paletteThemeAt(x, z);
        if (theme != null) {
            List<Material> themed = MaterialTags
                    .resolve(MaterialTags.key("cityworld:palette/biome/" + theme + "/" + part.path));
            if (!themed.isEmpty())
                return themed;
        }
        return poolFor(generator, part);
    }

    /**
     * A pick that is one per world (the street's surface, lines and sidewalk): no place, so no biome theme. Seeded
     * as {@code pick(.., 0, 0, ..)} always was, so a pack's world keeps the street it had.
     */
    public static Material pickWorld(CityWorldGenerator generator, Part part, Material current) {
        List<Material> pool = poolFor(generator, part);
        if (pool.isEmpty())
            return current;
        Odds odds = new Odds(seed(generator.getWorldSeed(), part, 0, 0));
        return pool.get(odds.getRandomInt(pool.size()));
    }

    /** The style's own palette for {@code part}, else the general one, else empty. */
    public static List<Material> poolFor(CityWorldGenerator generator, Part part) {
        String style = generator.worldStyle.name().toLowerCase(Locale.ROOT);
        List<Material> own = MaterialTags.resolve(MaterialTags.key("cityworld:palette/" + style + "/" + part.path));
        return own.isEmpty() ? MaterialTags.resolve(MaterialTags.key("cityworld:palette/" + part.path)) : own;
    }

    private static long seed(long worldSeed, Part part, int x, int z) {
        long h = worldSeed ^ (part.path.hashCode() * 0x9E3779B97F4A7C15L);
        h ^= x * 0xC2B2AE3D27D4EB4FL;
        h ^= z * 0x165667B19E3779F9L;
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }
}
