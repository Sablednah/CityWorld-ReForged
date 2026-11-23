package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

/**
 * A list of biomes as a pack author writes it, shared by palette themes ({@link BiomeThemes}) and a schematic's
 * {@code Biome:} line. Each term is one of:
 * <ul>
 * <li>{@code minecraft:desert} — that biome exactly;</li>
 * <li>{@code #c:is_desert} — every biome in that biome tag;</li>
 * <li>{@code desert} — a word: every biome, of any mod, whose name (the part after the colon) contains it, so
 * {@code minecraft:desert}, {@code biomesoplenty:lush_desert} and {@code terralith:desert_canyon} alike;</li>
 * <li>{@code terralith:*}, {@code *:snowy_*} — a pattern over the whole id, {@code *} standing for anything.</li>
 * </ul>
 * A term that starts with {@code !} takes its biomes back out, whatever else names them ({@code forest, !dark}).
 * A list of nothing but {@code !} terms means every other biome.
 */
public final class BiomeMatch {

    /** How closely a biome was named: the exact id beats a tag, a tag beats a word or a pattern. */
    public static final int NONE = 0, WORD = 1, TAG = 2, EXACT = 3;

    /** No limit: every biome, and no biome at all. */
    public static final BiomeMatch ANY = new BiomeMatch(List.of(), List.of());

    private record Term(int kind, String text, @Nullable TagKey<Biome> tag, @Nullable Pattern pattern) {

        int rank(String id, Holder<Biome> biome) {
            switch (kind) {
                case EXACT:
                    return id.equals(text) ? EXACT : NONE;
                case TAG:
                    return tag != null && biome.is(tag) ? TAG : NONE;
                default:
                    if (pattern != null)
                        return pattern.matcher(id).matches() ? WORD : NONE;
                    return id.substring(id.indexOf(':') + 1).contains(text) ? WORD : NONE;
            }
        }
    }

    private final List<Term> include;
    private final List<Term> exclude;

    private BiomeMatch(List<Term> include, List<Term> exclude) {
        this.include = include;
        this.exclude = exclude;
    }

    /** {@code "desert, #c:is_snowy, !minecraft:ice_spikes"}: terms apart by commas or spaces. */
    public static BiomeMatch parse(@Nullable String list) {
        if (list == null || list.isBlank())
            return ANY;
        return parse(List.of(list.trim().split("[,\\s]+")));
    }

    public static BiomeMatch parse(Collection<String> terms) {
        List<Term> include = new ArrayList<>(), exclude = new ArrayList<>();
        for (String raw : terms) {
            String text = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            // a YAML list written on one line: [desert, badlands]
            text = text.replace("[", "").replace("]", "").replace("\"", "").replace("'", "");
            boolean not = text.startsWith("!");
            if (not)
                text = text.substring(1).trim();
            if (text.isEmpty())
                continue;
            Term term;
            if (text.startsWith("#")) {
                ResourceLocation id = ResourceLocation.tryParse(text.substring(1));
                term = new Term(TAG, text, id == null ? null : TagKey.create(Registries.BIOME, id), null);
            } else if (text.indexOf('*') >= 0) {
                String glob = text.indexOf(':') >= 0 ? text : "*:" + text;
                term = new Term(WORD, text, null,
                        Pattern.compile(("\\Q" + glob + "\\E").replace("*", "\\E.*\\Q")));
            } else if (text.indexOf(':') >= 0) {
                term = new Term(EXACT, text, null, null);
            } else {
                term = new Term(WORD, text, null, null);
            }
            (not ? exclude : include).add(term);
        }
        return include.isEmpty() && exclude.isEmpty() ? ANY : new BiomeMatch(List.copyOf(include), List.copyOf(exclude));
    }

    /** Whether this names no biome at all: no limit. */
    public boolean isAny() {
        return this == ANY;
    }

    /**
     * How closely {@code biome} is named — {@link #EXACT}, {@link #TAG}, {@link #WORD} — or {@link #NONE}: not
     * named, taken out by a {@code !} term, or unknown (null, or a biome with no id).
     */
    public int rank(@Nullable Holder<Biome> biome) {
        if (biome == null || isAny())
            return NONE;
        String id = biome.unwrapKey().map(k -> k.location().toString()).orElse(null);
        if (id == null)
            return NONE;
        for (Term term : exclude)
            if (term.rank(id, biome) != NONE)
                return NONE;
        if (include.isEmpty())
            return WORD;
        int best = NONE;
        for (Term term : include)
            best = Math.max(best, term.rank(id, biome));
        return best;
    }

    /** Whether {@code biome} is one of these; with no limit, always. An unknown biome fits only no limit. */
    public boolean matches(@Nullable Holder<Biome> biome) {
        return isAny() || rank(biome) != NONE;
    }

    @Override
    public String toString() {
        if (isAny())
            return "any";
        List<String> out = new ArrayList<>();
        for (Term term : include)
            out.add(term.text());
        for (Term term : exclude)
            out.add("!" + term.text());
        return String.join(", ", out);
    }
}
