package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

import org.jetbrains.annotations.Nullable;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

/**
 * Palettes by biome (7.3, GitHub issue #9): datapack registry {@code cityworld:palette_theme}, one file per theme
 * ({@code data/<ns>/cityworld/palette_theme/<name>.json}) naming the biomes a theme covers. A building that stands
 * in one of them reads {@code #cityworld:palette/biome/<theme>/<part>} before the style's palette and the general one
 * ({@code Support/Palettes}), so a desert city can be sandstone and a taiga city spruce.
 *
 * <pre>
 * { "biomes": ["#c:is_desert", "minecraft:desert", "desert", "dune"], "priority": 0 }
 * </pre>
 *
 * {@code biomes} is a {@link BiomeMatch} list (exact ids, {@code #tags}, words, {@code !} to take one back out).
 * The theme's name is the file's own, or {@code "theme"} when given — so a second pack can add biomes to a theme
 * it does not own from a file of another name. A biome two themes name goes to the one that names it most closely
 * (id, then tag, then word), then the higher {@code priority}, then the first by file id. CityWorld ships none: with
 * no theme installed nothing here is asked and no building changes.
 */
public final class BiomeThemes {

    private BiomeThemes() {
    }

    public record Theme(Optional<String> theme, List<String> biomes, int priority) {
        public static final Codec<Theme> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("theme").forGetter(Theme::theme),
                Codec.STRING.listOf().optionalFieldOf("biomes", List.of()).forGetter(Theme::biomes),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(Theme::priority))
                .apply(i, Theme::new));
    }

    public static final ResourceKey<Registry<Theme>> THEMES = ResourceKey
            .createRegistryKey(ResourceLocation.fromNamespaceAndPath(CityWorldMod.MODID, "palette_theme"));

    private record Bound(String name, BiomeMatch match, int priority) {
    }

    /** The installed themes, the highest priority first (then by file id). */
    private static volatile List<Bound> themes = List.of();

    /** biome id -> its theme; emptied whenever the themes are bound again. */
    private static final ConcurrentHashMap<String, Optional<String>> MEMO = new ConcurrentHashMap<>();

    private static final Set<String> LOGGED = ConcurrentHashMap.newKeySet();

    public static void bind(RegistryAccess access) {
        try {
            Registry<Theme> registry = access.registryOrThrow(THEMES);
            var files = new ArrayList<>(registry.entrySet());
            files.sort(Comparator.comparing(e -> e.getKey().location().toString()));
            List<Bound> out = new ArrayList<>();
            for (var e : files) {
                String name = e.getValue().theme().orElse(e.getKey().location().getPath());
                BiomeMatch match = BiomeMatch.parse(e.getValue().biomes());
                if (!match.isAny() && ResourceLocation.tryParse("cityworld:palette/biome/" + name + "/x") != null)
                    out.add(new Bound(name, match, e.getValue().priority()));
                else
                    CityWorldMod.LOGGER.warn("CityWorld palette themes: {} names no biomes or is not a usable name; ignored",
                            e.getKey().location());
            }
            out.sort(Comparator.comparingInt(b -> -b.priority())); // stable: ties keep file-id order
            bind(out);
            Set<String> names = new TreeSet<>();
            for (Bound b : out)
                names.add(b.name());
            if (!out.isEmpty() && LOGGED.add(names.toString())) {
                CityWorldMod.LOGGER.info("CityWorld palette themes: {}", names);
                if (Boolean.getBoolean("cityworld.diagnostics"))
                    logBiomes(access);
            }
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld palette themes: could not read the registry: {}", e.toString());
        }
    }

    /** {@code -Dcityworld.diagnostics=true}: every installed biome under the theme it gets, for a pack's author. */
    private static void logBiomes(RegistryAccess access) {
        java.util.Map<String, List<String>> byTheme = new java.util.TreeMap<>();
        access.registryOrThrow(net.minecraft.core.registries.Registries.BIOME).holders().forEach(holder -> {
            String theme = themeFor(holder);
            byTheme.computeIfAbsent(theme == null ? "(no theme)" : theme, k -> new ArrayList<>())
                    .add(holder.key().location().toString());
        });
        byTheme.forEach((theme, ids) -> {
            java.util.Collections.sort(ids);
            CityWorldMod.LOGGER.info("CityWorld palette themes: {} = {}", theme, ids);
        });
    }

    private static void bind(List<Bound> bound) {
        themes = List.copyOf(bound);
        MEMO.clear();
    }

    /** Whether any theme is installed: with none, no building asks for its biome. */
    public static boolean any() {
        return !themes.isEmpty();
    }

    /** The theme of {@code biome}, or null. */
    public static @Nullable String themeFor(@Nullable Holder<Biome> biome) {
        List<Bound> local = themes;
        if (biome == null || local.isEmpty())
            return null;
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id == null)
            return null;
        Optional<String> known = MEMO.get(id);
        if (known == null) {
            String best = null;
            int bestRank = BiomeMatch.NONE;
            for (Bound b : local) { // highest priority first: a later theme must name the biome more closely to win
                int rank = b.match().rank(biome);
                if (rank > bestRank) {
                    bestRank = rank;
                    best = b.name();
                }
            }
            known = Optional.ofNullable(best);
            if (themes == local)
                MEMO.put(id, known);
        }
        return known.orElse(null);
    }

    /**
     * For the self-test: bind these themes ({@code name -> biome list}) in place of the registry's, and answer a
     * handle that puts back what was bound.
     */
    public static Runnable bindForTest(java.util.Map<String, String> byName) {
        List<Bound> before = themes;
        List<Bound> out = new ArrayList<>();
        for (var e : new java.util.TreeMap<>(byName).entrySet())
            out.add(new Bound(e.getKey(), BiomeMatch.parse(e.getValue()), 0));
        bind(out);
        return () -> bind(before);
    }
}
