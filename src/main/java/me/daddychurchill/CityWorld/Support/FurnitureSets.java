package me.daddychurchill.CityWorld.Support;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.Facing;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.properties.Property;

import org.jspecify.annotations.Nullable;

/**
 * Furniture sets recognised at <em>runtime</em> from the block registry, so a set CityWorld has never
 * been built against still furnishes the city.
 *
 * <p>Fantasy's Furniture is a family: every set mod (Nordic, Necrolord, the next one) registers the
 * same block names — {@code chair}, {@code wardrobe}, {@code bed_double} — through the base mod's
 * {@code FurnitureUtil}, and the base mod itself ships nothing but a crafting station. The vocabulary
 * lives in one JSON resource shared with {@code scripts/gen_furniture_tags.py}: the generator bakes
 * tag files and data map entries for the sets installed at build time, and this class derives the
 * same roles, layouts and properties on the fly for any set present at runtime. A namespace is a set
 * when it holds enough of the vocabulary's {@code detect} names (all of them names no other furniture
 * mod uses), a jar carrying two sets is two namespaces and needs nothing special, and a set that
 * reshapes a piece is skipped with a log line rather than placed as a row of origins: a declared
 * layout must agree with the block's own index property.
 *
 * <p>Results merge into the tag pools ({@link MaterialTags#resolve}) and the furniture data map
 * ({@code CityWorldDataMaps}); an explicit datapack entry always wins over a derived one, so a pack
 * author can still correct or remove a piece.
 */
public final class FurnitureSets {

    private FurnitureSets() {}

    private static final String VOCABULARY = "/cityworld/furniture_vocabulary/fantasyfurniture.json";

    private record Scan(List<String> namespaces, Map<TagKey<Block>, List<Block>> pools, Map<Block, Facing> data,
            List<String> notes) {
        static final Scan EMPTY = new Scan(List.of(), Map.of(), Map.of(), List.of());
    }

    private static volatile @Nullable Scan scan;

    /** Blocks this scan adds to {@code tag} beyond what the tag itself declares. */
    public static List<Block> extra(TagKey<Block> tag) {
        return scan().pools().getOrDefault(tag, List.of());
    }

    /** The derived furniture declaration for a block, or {@code null} if it is not a set piece. */
    public static @Nullable Facing dataFor(Block block) {
        return scan().data().get(block);
    }

    /** The namespaces recognised as sets, sorted. */
    public static List<String> detected() {
        return scan().namespaces();
    }

    /** What the scan skipped or could not reconcile, for the self-test and diagnostics. */
    public static List<String> notes() {
        return scan().notes();
    }

    /** Forget the scan — for a registry reload in development; harmless otherwise. */
    public static void reset() {
        scan = null;
    }

    private static Scan scan() {
        Scan s = scan;
        if (s == null) {
            synchronized (FurnitureSets.class) {
                s = scan;
                if (s == null)
                    scan = s = build();
            }
        }
        return s;
    }

    private static Scan build() {
        JsonObject vocabulary;
        try (InputStream in = FurnitureSets.class.getResourceAsStream(VOCABULARY)) {
            if (in == null) {
                CityWorldMod.LOGGER.warn("CityWorld: furniture vocabulary {} missing from the jar", VOCABULARY);
                return Scan.EMPTY;
            }
            vocabulary = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (Exception e) {
            CityWorldMod.LOGGER.warn("CityWorld: furniture vocabulary {} unreadable: {}", VOCABULARY, e.toString());
            return Scan.EMPTY;
        }
        Set<String> detect = strings(vocabulary, "detect");
        Set<String> ignore = strings(vocabulary, "ignore");
        int minimum = vocabulary.has("minimum") ? vocabulary.get("minimum").getAsInt() : 4;
        JsonObject blocks = vocabulary.getAsJsonObject("blocks");

        // the registry, grouped by namespace: path -> block
        Map<String, Map<String, Block>> byNamespace = new TreeMap<>();
        for (Identifier id : BuiltInRegistries.BLOCK.keySet())
            byNamespace.computeIfAbsent(id.getNamespace(), k -> new HashMap<>())
                    .put(id.getPath(), BuiltInRegistries.BLOCK.getValue(id));

        List<String> namespaces = new ArrayList<>();
        Map<TagKey<Block>, List<Block>> pools = new HashMap<>();
        Map<Block, Facing> data = new HashMap<>();
        List<String> notes = new ArrayList<>();
        int pieces = 0;
        for (var ns : byNamespace.entrySet()) {
            if (ignore.contains(ns.getKey()))
                continue;
            Map<String, Block> paths = ns.getValue();
            long hits = detect.stream().filter(paths::containsKey).count();
            if (hits < minimum)
                continue;
            namespaces.add(ns.getKey());
            for (var entry : blocks.entrySet()) {
                String name = entry.getKey();
                Block block = paths.get(name);
                if (block == null)
                    block = suffixMatch(paths, name);
                if (block == null)
                    continue;
                JsonObject spec = entry.getValue().getAsJsonObject();
                JsonObject declaration = spec.deepCopy();
                declaration.remove("role");
                declaration.remove("decor");
                Facing facing = Facing.CODEC.parse(JsonOps.INSTANCE, declaration).result().orElse(null);
                if (facing == null) {
                    notes.add(ns.getKey() + ":" + name + " — vocabulary entry did not parse");
                    continue;
                }
                if (facing.multiBlock() && !indexAgrees(block, facing)) {
                    notes.add(ns.getKey() + ":" + name + " — layout has " + facing.cells().size()
                            + " cells but the block's " + facing.indexProperty() + " disagrees; skipped");
                    continue;
                }
                data.put(block, facing);
                for (TagKey<Block> pool : poolsFor(spec))
                    pools.computeIfAbsent(pool, k -> new ArrayList<>()).add(block);
                pieces++;
            }
            Set<String> known = new TreeSet<>(blocks.keySet());
            known.addAll(strings(vocabulary, "notFurniture"));
            for (String path : new TreeSet<>(paths.keySet()))
                if (!known.contains(path) && suffixOf(known, path) == null)
                    notes.add(ns.getKey() + ":" + path + " — not in the vocabulary (a new piece?)");
        }
        if (!namespaces.isEmpty())
            CityWorldMod.LOGGER.info("CityWorld: furniture sets recognised at runtime: {} ({} pieces pooled{})",
                    namespaces, pieces, notes.isEmpty() ? "" : "; " + notes.size() + " notes: " + notes);
        return new Scan(List.copyOf(namespaces), Map.copyOf(pools), Map.copyOf(data), List.copyOf(notes));
    }

    private static List<TagKey<Block>> poolsFor(JsonObject spec) {
        List<TagKey<Block>> keys = new ArrayList<>();
        if (spec.has("role"))
            keys.add(TagKey.create(Registries.BLOCK,
                    Identifier.fromNamespaceAndPath("cityworld", "furniture/" + spec.get("role").getAsString())));
        if (spec.has("decor")) {
            JsonElement decor = spec.get("decor");
            List<String> names = new ArrayList<>();
            if (decor.isJsonArray())
                decor.getAsJsonArray().forEach(e -> names.add(e.getAsString()));
            else
                names.add(decor.getAsString());
            for (String pool : names)
                keys.add(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("cityworld", "decor/" + pool)));
        }
        return keys;
    }

    /** A block named {@code <anything>_<name>} in this namespace, for sets that prefix their pieces. */
    private static @Nullable Block suffixMatch(Map<String, Block> paths, String name) {
        Block found = null;
        for (var e : paths.entrySet())
            if (e.getKey().endsWith("_" + name)) {
                if (found != null)
                    return null; // ambiguous — leave it to the generator's tables
                found = e.getValue();
            }
        return found;
    }

    private static @Nullable String suffixOf(Set<String> names, String path) {
        String best = null;
        for (String name : names)
            if (path.endsWith("_" + name) && (best == null || name.length() > best.length()))
                best = name;
        return best;
    }

    /** Whether the block carries the layout's index property with exactly one value per cell. */
    private static boolean indexAgrees(Block block, Facing facing) {
        if (facing.cells().stream().allMatch(c -> !c.props().isEmpty()))
            return true; // the bed contract names every cell; no index property involved
        for (Property<?> property : block.defaultBlockState().getProperties())
            if (property.getName().equals(facing.indexProperty()))
                return property.getPossibleValues().size() == facing.cells().size();
        return false;
    }

    private static Set<String> strings(JsonObject object, String key) {
        Set<String> out = new HashSet<>();
        if (object.has(key))
            object.getAsJsonArray(key).forEach(e -> out.add(e.getAsString()));
        return out;
    }
}
