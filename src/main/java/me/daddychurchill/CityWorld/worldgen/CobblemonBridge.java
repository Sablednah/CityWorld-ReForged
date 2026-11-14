package me.daddychurchill.CityWorld.worldgen;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot.LotStyle;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.api.LotInfo;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * Cobblemon's spawning, told about the city. Cobblemon decides what spawns where from the blocks and the vanilla
 * structure starts around a position, so a CityWorld street read as a jungle temple (cobblestone and chiseled stone
 * walls), nothing from its {@code urban} preset ever fired (that wants concrete within four blocks), and its village
 * Pokémon never appeared because no village start lies under a city (the owner's {@code /checkspawns} walk,
 * 2026-11-12). Cobblemon's spawning API has a seam for exactly this: a {@code SpawningInfluenceDetector} is asked for
 * influences over every spawning zone, and an influence sees each candidate position before its conditions are judged.
 *
 * <p>Ours does two things to a position on CityWorld's land, all through Cobblemon's own public API:
 * <ul>
 * <li>adds <b>markers</b> — the strings a spawn entry's {@code "markers"} condition matches against — naming where the
 * position is: {@code cityworld:city} / {@code cityworld:street} / {@code cityworld:farm} / {@code cityworld:wild} at
 * the surface, {@code cityworld:underground} below the street (and {@code cityworld:sewer} under a road, {@code
 * cityworld:subway} at a station), and always {@code cityworld:district/<district>}, {@code cityworld:lot/<lot>} and,
 * for a shop, {@code cityworld:shop/<trade>}. The spawn entries CityWorld ships ({@code data/cityworld/spawn_pool_world/})
 * and any a pack adds use these: Cobblemon's own urban species on the city's streets, Grimer in the industrial
 * district, Gastly in the vault;</li>
 * <li>marks the position's structure cache as inside <b>{@code #cobblemon:spawns/is_civilization}</b> when it is on a
 * city's surface, so the Pokémon Cobblemon puts near people (its village-only entries) spawn in a city too. Not
 * {@code #minecraft:village}: the urban preset forbids that one.</li>
 * </ul>
 *
 * <p>Everything is reflection and {@link Proxy} over Cobblemon's interfaces (Kotlin compiles them to plain Java
 * interfaces with real default methods), so CityWorld compiles with no Cobblemon on the classpath and a Cobblemon
 * without this API (1.5.x) is simply not installed into: {@link #install} answers false and logs once. Installed on
 * {@code ServerAboutToStartEvent}; the detector set is static in Cobblemon, so once per game is enough.
 */
public final class CobblemonBridge {
    private static final String MOD_ID = "cobblemon";
    private static final String PKG = "com.cobblemon.mod.common.api.spawning.";
    private static final String DETECTOR = PKG + "influence.detector.SpawningInfluenceDetector";
    private static final String INFLUENCE = PKG + "influence.SpawningInfluence";
    private static final String UNCONDITIONAL = PKG + "influence.UnconditionalSpawningZoneInfluence";
    private static final String ZONE_INPUT = PKG + "spawner.SpawningZoneInput";
    private static final String POSITION = PKG + "position.SpawnablePosition";
    private static final String CACHE = POSITION + "$StructureChunkCache";

    /** The nearby-block conditions by which Cobblemon reads a structure from blocks alone (its presets jungle_pyramid, desert_pyramid, stronghold). */
    static final Set<String> STRUCTURE_READS = Set.of("#cobblemon:jungle_pyramid_blocks", "#cobblemon:desert_pyramid_blocks",
            "minecraft:cracked_stone_bricks", "minecraft:mossy_stone_bricks");

    /** The structure tag Cobblemon's village-dwelling spawn entries require. */
    public static final TagKey<Structure> CIVILIZATION = TagKey.create(Registries.STRUCTURE,
            ResourceLocation.fromNamespaceAndPath(MOD_ID, "spawns/is_civilization"));

    private CobblemonBridge() {
    }

    private static final boolean PRESENT = detect();
    private static boolean attempted;
    private static boolean installed;
    private static Object detector;

    private static boolean detect() {
        try {
            return net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded(MOD_ID);
        } catch (Throwable t) {
            return false; // no mod list (datagen, tests)
        }
    }

    /** Whether Cobblemon is loaded at all (any version). */
    public static boolean present() {
        return PRESENT;
    }

    /** Whether our detector sits in Cobblemon's set — the one fact the self-test checks. */
    public static synchronized boolean installed() {
        return installed;
    }

    /**
     * Registers the detector with Cobblemon. Idempotent; false when Cobblemon is absent or has no influence-detector
     * API (older than 1.6), in which case cities get Cobblemon's default reading of their blocks and nothing else.
     */
    public static synchronized boolean install() {
        if (installed)
            return true;
        if (!PRESENT || attempted)
            return false;
        attempted = true;
        try {
            Class<?> detectorType = Class.forName(DETECTOR);
            Class<?> influenceType = Class.forName(INFLUENCE);
            Constructor<?> unconditional = Class.forName(UNCONDITIONAL).getConstructor(influenceType);
            Method inputWorld = Class.forName(ZONE_INPUT).getMethod("getWorld");
            Class<?> positionType = Class.forName(POSITION);
            Positions positions = new Positions(positionType.getMethod("getWorld"), positionType.getMethod("getPosition"),
                    positionType.getMethod("getMarkers"), positionType.getMethod("getStructureCache", BlockPos.class),
                    Class.forName(CACHE));
            @SuppressWarnings("unchecked")
            Set<Object> detectors = (Set<Object>) detectorType.getMethod("getDetectors").invoke(null);

            Object influence = Proxy.newProxyInstance(influenceType.getClassLoader(), new Class<?>[] { influenceType },
                    new Influence(positions));
            Object zoneInfluence = unconditional.newInstance(influence);
            List<Object> inCity = List.of(zoneInfluence);
            detector = Proxy.newProxyInstance(detectorType.getClassLoader(), new Class<?>[] { detectorType },
                    (proxy, method, args) -> {
                        switch (method.getName()) {
                        case "detectFromInput": {
                            Object world = inputWorld.invoke(args[1]);
                            return world instanceof ServerLevel level && CityWorldAPI.isCityWorld(level) ? inCity : List.of();
                        }
                        case "detectFromBlock":
                            return List.of();
                        default:
                            return objectMethod(proxy, method, args, "CityWorld spawning influence detector");
                        }
                    });
            detectors.add(detector);
            installed = true;
            CityWorldMod.LOGGER.info("CityWorld: Cobblemon spawning influence installed — city positions carry markers and count as civilization");
            return true;
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            CityWorldMod.LOGGER.info("CityWorld: Cobblemon is installed but has no spawning influence API ({}); cities get its default spawns", e.getMessage());
            return false;
        } catch (Throwable t) {
            CityWorldMod.LOGGER.warn("CityWorld: could not install the Cobblemon spawning influence", t);
            return false;
        }
    }

    private static Object objectMethod(Object proxy, Method method, Object[] args, String name) throws Throwable {
        switch (method.getName()) {
        case "hashCode":
            return System.identityHashCode(proxy);
        case "equals":
            return proxy == args[0];
        case "toString":
            return name;
        default:
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            return defaultValue(method.getReturnType());
        }
    }

    private static Object defaultValue(Class<?> type) {
        if (type == boolean.class)
            return Boolean.TRUE; // every boolean on the influence means "allowed"
        if (type == float.class)
            return 0F;
        if (type == int.class)
            return 0;
        return null;
    }

    /** The reflective handles on a Cobblemon {@code SpawnablePosition}. */
    private record Positions(Method world, Method position, Method markers, Method structureCache, Class<?> cacheType) {
        Method loadStructures() throws NoSuchMethodException {
            return cacheType.getMethod("loadStructures", net.minecraft.world.level.StructureManager.class, BlockPos.class);
        }
    }

    /** The influence: marks each position it is applied to; every other hook keeps Cobblemon's default. */
    private static final class Influence implements InvocationHandler {
        private final Positions positions;
        private final Method loadStructures;
        private final Method foundTags;
        private final Method loaded;

        Influence(Positions positions) throws NoSuchMethodException {
            this.positions = positions;
            this.loadStructures = positions.loadStructures();
            this.foundTags = positions.cacheType().getMethod("getFoundTags");
            this.loaded = positions.cacheType().getMethod("getLoadedStructures");
        }

        @Override
        public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
            if (method.getName().equals("affectWeight") && args != null && args.length == 3) {
                try {
                    if (readsAStructureFromCityBlocks(args[0], args[1]))
                        return 0F;
                } catch (Throwable t) {
                    CityWorldMod.LOGGER.debug("CityWorld: Cobblemon weight not read", t);
                }
                return args[2];
            }
            if (method.getName().equals("affectSpawnablePosition") && args != null && args.length == 1) {
                try {
                    mark(args[0]);
                } catch (Throwable t) {
                    CityWorldMod.LOGGER.debug("CityWorld: Cobblemon position not marked", t);
                }
                return null;
            }
            return objectMethod(proxy, method, args, "CityWorld spawning influence");
        }

        /**
         * Cobblemon reads three structures from blocks alone — a jungle pyramid from cobblestone near
         * {@code #cobblemon:jungle_pyramid_blocks}, a desert pyramid from sandstone near {@code #cobblemon:desert_pyramid_blocks},
         * a stronghold from stone bricks near cracked or mossy ones — and a city's walls and pavements satisfy all three, so
         * Hawlucha, Sigilyph, Meditite and Bronzor made 37% of a street's commons (measured 2026-11-14, {@code survey:cobblemon}).
         * On a city's surface ({@code cityworld:city}) such an entry weighs nothing; underground (the sewers, the vault) it is
         * left alone — a ruin read is right there. Entries that also require the real structure never fire in a city anyway.
         */
        private boolean readsAStructureFromCityBlocks(Object detail, Object position) throws Throwable {
            @SuppressWarnings("unchecked")
            Set<String> markers = (Set<String>) positions.markers().invoke(position);
            if (!markers.contains("cityworld:city"))
                return false;
            List<?> conditions = (List<?>) detail.getClass().getMethod("getConditions").invoke(detail);
            for (Object condition : conditions) {
                Method nearby;
                try {
                    nearby = condition.getClass().getMethod("getNeededNearbyBlocks");
                } catch (NoSuchMethodException e) {
                    continue;
                }
                List<?> needed = (List<?>) nearby.invoke(condition);
                if (needed == null)
                    continue;
                for (Object need : needed) {
                    String id = null;
                    try {
                        id = "#" + ((TagKey<?>) need.getClass().getMethod("getTag").invoke(need)).location();
                    } catch (NoSuchMethodException e) {
                        try {
                            id = need.getClass().getMethod("getIdentifier").invoke(need).toString();
                        } catch (NoSuchMethodException ignored) {
                        }
                    }
                    if (id != null && STRUCTURE_READS.contains(id))
                        return true;
                }
            }
            return false;
        }

        private void mark(Object position) throws Throwable {
            Object world = positions.world().invoke(position);
            if (!(world instanceof ServerLevel level))
                return;
            BlockPos pos = (BlockPos) positions.position().invoke(position);
            Optional<LotInfo> found = CityWorldAPI.lotAtIfPlanned(level, pos);
            if (found.isEmpty())
                return;
            @SuppressWarnings("unchecked")
            Set<String> markers = (Set<String>) positions.markers().invoke(position);
            LotInfo lot = found.get();
            boolean civilised = markersFor(lot, pos.getY(), markers);
            if (civilised) {
                Object cache = positions.structureCache().invoke(position, pos);
                if (!(Boolean) loaded.invoke(cache))
                    loadStructures.invoke(cache, level.structureManager(), pos);
                @SuppressWarnings("unchecked")
                Set<TagKey<Structure>> tags = (Set<TagKey<Structure>>) foundTags.invoke(cache);
                tags.add(CIVILIZATION);
            }
        }
    }

    /**
     * The markers for a position at height {@code y} on {@code lot}, added to {@code into}; answers whether the
     * position counts as civilisation (a city's surface). Public for the self-test.
     */
    public static boolean markersFor(LotInfo lot, int y, Set<String> into) {
        SchematicFamily family = lot.contextFamily();
        boolean wild = family == SchematicFamily.NATURE;
        boolean farm = family == SchematicFamily.FARM;
        boolean city = !wild && !farm;
        boolean surface = y >= lot.streetLevel() - 1;
        String district = lot.contextClass().toLowerCase(Locale.ROOT);
        if (district.endsWith("context"))
            district = district.substring(0, district.length() - "context".length());
        String kind = lot.lotClass().toLowerCase(Locale.ROOT);
        if (kind.endsWith("lot"))
            kind = kind.substring(0, kind.length() - "lot".length());
        into.add("cityworld:district/" + district);
        into.add("cityworld:lot/" + kind);
        if (lot.shop() != null)
            into.add("cityworld:shop/" + lot.shop().trade().name().toLowerCase(Locale.ROOT));
        if (surface) {
            if (wild)
                into.add("cityworld:wild");
            else if (farm)
                into.add("cityworld:farm");
            else {
                into.add("cityworld:city");
                if (lot.isRoad())
                    into.add("cityworld:street");
            }
        } else {
            into.add("cityworld:underground");
            if (city && lot.isRoad())
                into.add("cityworld:sewer");
            if (lot.subway())
                into.add("cityworld:subway");
        }
        return city && surface;
    }
}
