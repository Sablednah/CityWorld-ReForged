package me.daddychurchill.CityWorld.Clipboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;

import net.neoforged.fml.loading.FMLPaths;

/**
 * The classic schematics, indexed and lazily loaded — both the set <b>bundled</b> in the mod and any
 * the player has <b>dropped</b> into their instance.
 *
 * <p><b>Bundled:</b> jar resources cannot be directory-listed, so the build ships an {@code index.txt}
 * manifest ({@code Family/name.schematic} per line, generated from the asset tree).
 *
 * <p><b>External:</b> a real folder <em>can</em> be listed, so on top of the manifest this scans
 * {@code config/cityworld/schematics/<Family>/} for {@code .schematic} (+ optional {@code .yml})
 * files and adds them — the drop-in extras folder (created on first run, see
 * {@link #ensureExternalFolder}). This is the modern stand-in for upstream's per-world
 * "Schematics for &lt;world&gt;" WorldEdit folder.
 *
 * <p>Each {@link Clipboard} is converted from its legacy {@code .schematic} on first use and cached —
 * the one-time legacy→{@code StructureTemplate} conversion happens at most once per building per run.
 * The class is intentionally free of any live-level or generator dependency.
 */
public final class SchematicLibrary {

    private SchematicLibrary() {}

    private static final String ROOT = "/cityworld/schematics/";
    private static final String INDEX = ROOT + "index.txt";
    private static final String SCHEMATIC = ".schematic";

    /** For a bundled entry {@code path} is a classpath resource; for an external one, a filesystem path. */
    private record Entry(String name, SchematicFamily family, String path, boolean external) {}

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
        loadBundled(list);
        scanExternal(list);
        CityWorldMod.LOGGER.info("SchematicLibrary: indexed {} schematics ({} distinct names)",
                list.size(), (int) list.stream().map(Entry::name).distinct().count());
        return list;
    }

    private static void loadBundled(List<Entry> list) {
        try (InputStream in = SchematicLibrary.class.getResourceAsStream(INDEX)) {
            if (in == null) {
                CityWorldMod.LOGGER.warn("SchematicLibrary: no bundled index at {}", INDEX);
                return;
            }
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || !line.endsWith(SCHEMATIC))
                        continue;
                    int slash = line.indexOf('/');
                    if (slash <= 0)
                        continue;
                    SchematicFamily family = familyOf(line.substring(0, slash));
                    if (family == null)
                        continue;
                    String file = line.substring(slash + 1);
                    String name = file.substring(0, file.length() - SCHEMATIC.length());
                    list.add(new Entry(name, family, ROOT + line, false));
                }
            }
        } catch (IOException e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to read bundled index", e);
        }
    }

    /** Scan {@code config/cityworld/schematics/<Family>/*.schematic} for player-dropped extras. */
    private static void scanExternal(List<Entry> list) {
        Path root = externalRoot();
        if (root == null || !Files.isDirectory(root))
            return;
        int found = 0;
        try (DirectoryStream<Path> families = Files.newDirectoryStream(root)) {
            for (Path famDir : families) {
                if (!Files.isDirectory(famDir))
                    continue;
                SchematicFamily family = familyOf(famDir.getFileName().toString());
                if (family == null)
                    continue;
                try (DirectoryStream<Path> files = Files.newDirectoryStream(famDir, "*" + SCHEMATIC)) {
                    for (Path f : files) {
                        String file = f.getFileName().toString();
                        String name = file.substring(0, file.length() - SCHEMATIC.length());
                        list.add(new Entry(name, family, f.toAbsolutePath().toString(), true));
                        found++;
                    }
                }
            }
        } catch (IOException e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to scan {}", root, e);
        }
        if (found > 0)
            CityWorldMod.LOGGER.info("SchematicLibrary: +{} custom schematics from {}", found, root);
    }

    private static Path externalRoot() {
        try {
            return FMLPaths.CONFIGDIR.get().resolve("cityworld").resolve("schematics");
        } catch (Throwable t) {
            // FML not initialised (plan-only probe / test context) — external loading is simply off.
            return null;
        }
    }

    /**
     * Create the drop-in folder and a short README on first run, so the player has an obvious place to
     * add schematics. Called from mod init; safe to call once.
     */
    public static void ensureExternalFolder() {
        Path root = externalRoot();
        if (root == null)
            return;
        try {
            Files.createDirectories(root);
            Path readme = root.resolve("README.txt");
            if (!Files.exists(readme))
                Files.writeString(readme, README);
            CityWorldMod.LOGGER.info("SchematicLibrary: custom schematics folder is {}", root);
        } catch (IOException e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: could not prepare {}", root, e);
        }
    }

    private static final String README =
            "Drop custom CityWorld schematics here to have the generator use them.\n\n"
            + "Layout:  config/cityworld/schematics/<Family>/<name>.schematic\n"
            + "         (an optional <name>.schematic.yml sidecar sets ground level, odds, etc.)\n\n"
            + "One subfolder per family; the folder name decides where the building appears:\n"
            + "  Highrise, Midrise, Lowrise, Municipal, Industrial, Construction,\n"
            + "  Park, Neighborhood, Farm, Roundabout, Nature, Outland\n"
            + "  (Outland = wilderness; Roundabout = a statue at a roundabout centre.)\n\n"
            + "Format: the old flat-array MCEdit .schematic (numeric block ids), same as the\n"
            + "bundled set. Modern WorldEdit .schem is not supported yet.\n\n"
            + "Turn placement on with the [schematics] includeSchematics config option, then\n"
            + "generate fresh chunks. Files here are loaded at startup - relaunch after adding.\n";

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
                return cache.computeIfAbsent(cacheKey(e), k -> load(e));
        return null;
    }

    /** Load all clipboards in a family (for worldgen selection); failures are skipped. */
    public static List<Clipboard> family(SchematicFamily family) {
        List<Clipboard> out = new ArrayList<>();
        for (Entry e : index())
            if (e.family == family) {
                Clipboard clip = cache.computeIfAbsent(cacheKey(e), k -> load(e));
                if (clip != null)
                    out.add(clip);
            }
        return out;
    }

    // Cache by family+path so a bundled and an external copy of the same name don't collide.
    private static String cacheKey(Entry e) {
        return e.family.name() + "|" + e.path;
    }

    private static Clipboard load(Entry entry) {
        try {
            if (entry.external) {
                Path schematic = Path.of(entry.path);
                Path yml = Path.of(entry.path + ".yml");
                try (InputStream s = Files.newInputStream(schematic);
                     InputStream y = Files.exists(yml) ? Files.newInputStream(yml) : null) {
                    return Clipboard.load(entry.name, entry.family, s, y);
                }
            }
            try (InputStream s = SchematicLibrary.class.getResourceAsStream(entry.path);
                 InputStream y = SchematicLibrary.class.getResourceAsStream(entry.path + ".yml")) {
                if (s == null) {
                    CityWorldMod.LOGGER.warn("SchematicLibrary: missing resource {}", entry.path);
                    return null;
                }
                return Clipboard.load(entry.name, entry.family, s, y);
            }
        } catch (Exception e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to load {} ({})", entry.name, entry.path, e);
            return null;
        }
    }
}
