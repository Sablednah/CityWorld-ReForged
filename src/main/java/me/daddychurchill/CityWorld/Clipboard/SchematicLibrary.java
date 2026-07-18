package me.daddychurchill.CityWorld.Clipboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;

/**
 * The bundled classic schematics, indexed and lazily loaded.
 *
 * <p>Jar resources cannot be directory-listed, so the build ships an {@code index.txt} manifest
 * ({@code Family/name.schematic} per line, generated from the asset tree). This reads it once to know
 * what exists, then converts each {@link Clipboard} from its legacy {@code .schematic} on first use
 * and caches it — the one-time legacy→{@code StructureTemplate} conversion happens at most once per
 * building per run.
 *
 * <p>This is the loader the {@code /cityschem} command uses now and worldgen placement will use next;
 * it is intentionally free of any live-level or generator dependency.
 */
public final class SchematicLibrary {

    private SchematicLibrary() {}

    private static final String ROOT = "/cityworld/schematics/";
    private static final String INDEX = ROOT + "index.txt";

    private record Entry(String name, SchematicFamily family, String path) {}

    /** All entries, index order. The same building name can appear in more than one family. */
    private static volatile List<Entry> index;
    private static final Map<String, Clipboard> cache = new ConcurrentHashMap<>();

    private static List<Entry> index() {
        List<Entry> local = index;
        if (local == null) {
            synchronized (SchematicLibrary.class) {
                local = index;
                if (local == null) {
                    local = loadIndex();
                    index = local;
                }
            }
        }
        return local;
    }

    private static List<Entry> loadIndex() {
        List<Entry> list = new ArrayList<>();
        try (InputStream in = SchematicLibrary.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                CityWorldMod.LOGGER.warn("SchematicLibrary: no index at {}", INDEX);
                return list;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || !line.endsWith(".schematic"))
                        continue;
                    int slash = line.indexOf('/');
                    if (slash <= 0)
                        continue;
                    String folder = line.substring(0, slash);
                    String file = line.substring(slash + 1);
                    String name = file.substring(0, file.length() - ".schematic".length());
                    SchematicFamily family = familyOf(folder);
                    if (family == null)
                        continue;
                    list.add(new Entry(name, family, ROOT + line));
                }
            }
        } catch (IOException e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to read index", e);
        }
        CityWorldMod.LOGGER.info("SchematicLibrary: indexed {} classic schematics", list.size());
        return list;
    }

    private static SchematicFamily familyOf(String folder) {
        try {
            return SchematicFamily.valueOf(folder.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    /** Distinct building names, in index order (a name shared across families is listed once). */
    public static List<String> names() {
        java.util.LinkedHashSet<String> out = new java.util.LinkedHashSet<>();
        for (Entry e : index())
            out.add(e.name);
        return new ArrayList<>(out);
    }

    /** Building names in one family. */
    public static List<String> names(SchematicFamily family) {
        List<String> out = new ArrayList<>();
        for (Entry e : index())
            if (e.family == family)
                out.add(e.name);
        return out;
    }

    /** Load (or fetch the cached) clipboard by building name, case-insensitively; null if unknown. */
    public static Clipboard get(String name) {
        for (Entry e : index())
            if (e.name.equalsIgnoreCase(name))
                return cache.computeIfAbsent(e.path, p -> load(e));
        return null;
    }

    /** Load all clipboards in a family (for worldgen selection); failures are skipped. */
    public static List<Clipboard> family(SchematicFamily family) {
        List<Clipboard> out = new ArrayList<>();
        for (Entry e : index())
            if (e.family == family) {
                Clipboard clip = cache.computeIfAbsent(e.path, p -> load(e));
                if (clip != null)
                    out.add(clip);
            }
        return out;
    }

    private static Clipboard load(Entry entry) {
        try (InputStream schematic = SchematicLibrary.class.getResourceAsStream(entry.path);
             InputStream yml = SchematicLibrary.class.getResourceAsStream(entry.path + ".yml")) {
            if (schematic == null) {
                CityWorldMod.LOGGER.warn("SchematicLibrary: missing resource {}", entry.path);
                return null;
            }
            return Clipboard.load(entry.name, entry.family, schematic, yml);
        } catch (Exception e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to load {}", entry.name, e);
            return null;
        }
    }
}
