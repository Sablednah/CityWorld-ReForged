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

    /** Extensions the loader understands, longest/most-specific first (so {@code .schematic} wins). */
    private static final String[] EXTS = { ".schematic", ".litematic", ".schem", ".nbt" };

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
                try (DirectoryStream<Path> files = Files.newDirectoryStream(famDir)) {
                    for (Path f : files) {
                        if (!Files.isRegularFile(f))
                            continue;
                        String file = f.getFileName().toString();
                        String ext = supportedExt(file);
                        if (ext == null)
                            continue;
                        String name = file.substring(0, file.length() - ext.length());
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
            + "         (an optional sidecar sets ground level, odds, etc. -- see below)\n\n"
            + "One subfolder per family; the folder name decides where the building appears:\n"
            + "  Highrise, Midrise, Lowrise, Municipal, Industrial, Construction,\n"
            + "  Park, Neighborhood, Farm, Roundabout, Nature, Outland\n"
            + "  (Outland = the frontier edge of the wild; Nature = the deep wild;\n"
            + "   Roundabout = a single-chunk statue at a roundabout centre.)\n\n"
            + "Formats: .schematic (legacy MCEdit), .schem (WorldEdit), .litematic (Litematica),\n"
            + "and .nbt (vanilla structure). Very old files may lose blocks that were renamed in\n"
            + "later versions (they place as air); modern exports come through cleanly.\n\n"
            + "-- Sidecar (.yml) ----------------------------------------------------------------\n"
            + "Name it <name>.<ext>.yml (e.g. house.schematic.yml) or <name>.yml. Keys:\n\n"
            + "  GroundLevelY: 0      How many of the build's bottom layers sit BELOW ground, i.e.\n"
            + "                       the buried foundation/basement depth. Raise it to sink a build.\n"
            + "  OddsOfAppearance: 0.1  Chance (0..1) it drops in each eligible platmap. Big or\n"
            + "                       landmark builds want this low (0.02); small filler higher.\n"
            + "  FlipableX: false     Allow a random mirror across X / Z. Leave off for anything with\n"
            + "  FlipableZ: false     signs or a deliberate front, or it may read backwards.\n"
            + "  Decayable: true      Whether the ruined-world decay pass may chew it up.\n"
            + "  PristineChance: -1   Per-build override of the world's odds of being spared decay\n"
            + "                       (-1 = use the world default).\n"
            + "  BroadcastLocation: false  Announce its coords in chat when it generates.\n"
            + "  Ocean: false         Offshore build (rig, ship, lighthouse): places only in deep\n"
            + "                       open water and rides the surface on its own legs/hull -- no\n"
            + "                       foundation. Put it in Nature/. GroundLevelY here is how many\n"
            + "                       blocks of legs/hull sit below the waterline.\n"
            + "  KeepAir: false       Place the schematic's air blocks instead of skipping them, so\n"
            + "                       enclosed voids (a cellar, a hollow tower) stay open instead of\n"
            + "                       being filled by the terrain the build overlays. Costs the overlay\n"
            + "                       behaviour -- the whole bounding box, air included, is stamped over\n"
            + "                       the ground -- so trim dead air off the build first.\n\n"
            + "Land builds automatically level a foundation pad and keep off water/mountains.\n"
            + "Ocean: true builds are the exception -- they leave the sea floor untouched and just\n"
            + "drop the build into deep open water.\n\n"
            + "Turn placement on by enabling schematics in the world's settings (the Customize\n"
            + "screen when creating a CityWorld world, or the world_settings datapack), then\n"
            + "generate fresh chunks. Files here are loaded at startup - relaunch after adding.\n";

    /** The supported extension a file ends with (e.g. {@code .schem}), or null if unrecognised. */
    private static String supportedExt(String file) {
        String lower = file.toLowerCase(Locale.ROOT);
        for (String e : EXTS)
            if (lower.endsWith(e))
                return e;
        return null;
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
                Path yml = externalYml(entry.path);
                try (InputStream s = Files.newInputStream(Path.of(entry.path));
                     InputStream y = yml != null ? Files.newInputStream(yml) : null) {
                    return Clipboard.load(entry.name, entry.family, entry.path, s, y);
                }
            }
            try (InputStream s = SchematicLibrary.class.getResourceAsStream(entry.path);
                 InputStream y = SchematicLibrary.class.getResourceAsStream(entry.path + ".yml")) {
                if (s == null) {
                    CityWorldMod.LOGGER.warn("SchematicLibrary: missing resource {}", entry.path);
                    return null;
                }
                return Clipboard.load(entry.name, entry.family, entry.path, s, y);
            }
        } catch (Exception e) {
            CityWorldMod.LOGGER.warn("SchematicLibrary: failed to load {} ({})", entry.name, entry.path, e);
            return null;
        }
    }

    /**
     * Find a schematic's {@code .yml} sidecar. Two conventions are in the wild: {@code name.schematic.yml}
     * (append) and {@code name.yml} (replace the extension) — try both.
     */
    private static Path externalYml(String path) {
        Path appended = Path.of(path + ".yml");
        if (Files.exists(appended))
            return appended;
        int dot = path.lastIndexOf('.');
        if (dot > 0) {
            Path replaced = Path.of(path.substring(0, dot) + ".yml");
            if (Files.exists(replaced))
                return replaced;
        }
        return null;
    }
}
