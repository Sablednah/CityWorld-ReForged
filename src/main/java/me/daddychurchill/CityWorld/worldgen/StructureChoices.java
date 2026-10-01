package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.BiomeTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraftforge.fml.ModList;

/**
 * Every structure set this install has — vanilla's and each mod's — with whether CityWorld places it by default.
 * This is what the Customize screen's Structures page lists, so a player ticks "Villages" instead of writing a tag
 * datapack; the answer it edits is {@link CityWorldSettingsData.Structures}, and the generator applies it in
 * {@code CityWorldChunkGenerator.createState}.
 *
 * <p>The unit is the structure SET, not the structure: placement (spacing, separation, the salt) belongs to the
 * set, and that is what vanilla's structure state is built from. A set's structures are listed for the tooltip.
 */
public final class StructureChoices {

    private StructureChoices() {
    }

    /**
     * One structure set.
     *
     * @param id         the set's id, {@code minecraft:villages}
     * @param group      the mod (or datapack namespace) it comes from, for the page's headings
     * @param label      the set's name, readable
     * @param byDefault  whether {@code #cityworld:allowed} holds it — the box starts ticked
     * @param structures the structures in the set
     * @param realms     where its biomes are, by vanilla's {@code is_overworld}/{@code is_nether}/{@code is_end}
     *                   tags; empty when its structures name no biome in any of them
     */
    public record Entry(String id, String group, String label, boolean byDefault, List<String> structures,
            List<String> realms) {
    }

    /** Every structure set in these registries, vanilla's first, then by mod and name. */
    public static List<Entry> list(HolderLookup.Provider registries) {
        HolderLookup<StructureSet> sets = registries.lookup(Registries.STRUCTURE_SET).orElse(null);
        if (sets == null)
            return List.of();
        HolderSet<StructureSet> tagged = sets.get(CityWorldChunkGenerator.ALLOWED_STRUCTURE_SETS)
                .<HolderSet<StructureSet>>map(named -> named).orElseGet(HolderSet::direct);
        List<Entry> entries = new ArrayList<>();
        sets.listElements().forEach(set -> {
            var id = set.key().location();
            List<String> structures = new ArrayList<>();
            Set<String> realms = new LinkedHashSet<>();
            boolean overworld = false, nether = false, end = false;
            for (StructureSet.StructureSelectionEntry entry : set.value().structures()) {
                entry.structure().unwrapKey().ifPresent(key -> structures.add(nice(key.location().getPath())));
                for (Holder<Biome> biome : entry.structure().value().biomes()) {
                    overworld |= biome.is(BiomeTags.IS_OVERWORLD);
                    nether |= biome.is(BiomeTags.IS_NETHER);
                    end |= biome.is(BiomeTags.IS_END);
                }
            }
            if (overworld)
                realms.add("Overworld");
            if (nether)
                realms.add("Nether");
            if (end)
                realms.add("End");
            entries.add(new Entry(id.toString(), group(id.getNamespace()), nice(id.getPath()),
                    tagged.contains(set), List.copyOf(structures), List.copyOf(realms)));
        });
        entries.sort(Comparator.comparing((Entry e) -> !e.id().startsWith("minecraft:"))
                .thenComparing(Entry::group, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(Entry::label, String.CASE_INSENSITIVE_ORDER));
        return List.copyOf(entries);
    }

    /** How many of these sets a world with this choice places. */
    public static int count(List<Entry> entries, CityWorldSettingsData.Structures choice) {
        int on = 0;
        for (Entry entry : entries)
            if (choice.allows(entry.id(), entry.byDefault()))
                on++;
        return on;
    }

    private static String group(String namespace) {
        if (namespace.equals("minecraft"))
            return "Minecraft";
        return ModList.get().getModContainerById(namespace).map(mod -> mod.getModInfo().getDisplayName())
                .orElse(nice(namespace));
    }

    /** {@code ancient_cities} → {@code Ancient cities}; a path's folders are kept ({@code a/b} → {@code A / b}). */
    private static String nice(String path) {
        String text = path.replace('_', ' ').replace("/", " / ").trim();
        return text.isEmpty() ? path : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
    }
}
