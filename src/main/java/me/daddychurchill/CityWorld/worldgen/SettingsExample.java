package me.daddychurchill.CityWorld.worldgen;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import me.daddychurchill.CityWorld.BuildInfo;
import me.daddychurchill.CityWorld.CityWorldMod;

import net.neoforged.fml.loading.FMLPaths;

/**
 * Drops a copy-and-edit settings example next to the schematics drop-in folder:
 * {@code config/cityworld/settings-example/}. It is a complete, valid world datapack holding the three
 * settings entries CityWorld ships — {@code modern} (the City preset and the {@code /cityworld} dimension),
 * {@code apocalypse}, and {@code default} (every other preset) — each written out with every field, plus a
 * short {@code README.txt}. A server op copies the folder into {@code <world>/datapacks/}, keeps the file
 * for their world type and edits it.
 *
 * <p>Deliberately <em>not</em> a datapack the game auto-loads: it sits under {@code config/}, so it is
 * inert until copied into a world.
 *
 * <p><b>Rewritten whenever the build changes</b> (a {@code .written-by} stamp holds {@link BuildInfo#version()}).
 * It used to be written once and never again, so an install kept the example — and a 400-line hand-written
 * reference — from whichever version it first ran, while every release added settings to neither. The
 * descriptions now live in one place, the wiki page generated from {@code CURSEFORGE-CONFIGURATION.md}; the
 * README says so and says to copy the folder rather than edit it here.
 */
public final class SettingsExample {

    private SettingsExample() {}

    private static final String[] ENTRIES = { SettingsDatapack.MODERN_ENTRY, SettingsDatapack.APOCALYPSE_ENTRY,
            SettingsDatapack.DEFAULT_ENTRY };

    private static Path folder() {
        return FMLPaths.CONFIGDIR.get().resolve(CityWorldMod.MODID).resolve("settings-example");
    }

    /** Write (or refresh) the example pack. Best-effort: a failure only logs. */
    public static void ensureExampleFolder() {
        try {
            Path root = folder();
            Path stamp = root.resolve(".written-by");
            String version = BuildInfo.version();
            if (Files.exists(stamp) && Files.readString(stamp).strip().equals(version))
                return;
            SettingsDatapack.writePack(root, SettingsDatapack.DEFAULT_ENTRY, shipped(SettingsDatapack.DEFAULT_ENTRY),
                    "CityWorld settings example — copy into <world>/datapacks and edit");
            for (String entry : ENTRIES)
                SettingsDatapack.writeEntry(root, CityWorldMod.MODID, entry, shipped(entry));
            Files.deleteIfExists(root.resolve("settings-reference.txt")); // the old hand-kept copy, long stale
            Files.writeString(root.resolve("README.txt"), README);
            Files.writeString(stamp, version + "\n");
            CityWorldMod.LOGGER.info("CityWorld: wrote settings example to {}", root);
        } catch (IOException | RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld: could not write settings example", e);
        }
    }

    /** One of the entries in this jar, decoded through the generator's own codec (so absent fields read as
     *  the defaults the game would give them). The compiled default if the file cannot be read. */
    private static CityWorldSettingsData shipped(String entry) {
        String path = "/data/" + CityWorldMod.MODID + "/" + CityWorldMod.MODID + "/world_settings/" + entry + ".json";
        try (InputStream in = SettingsExample.class.getResourceAsStream(path)) {
            if (in == null)
                return CityWorldSettingsData.DEFAULT;
            var json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            return CityWorldSettingsData.CODEC.parse(JsonOps.INSTANCE, json).result()
                    .orElse(CityWorldSettingsData.DEFAULT);
        } catch (IOException | RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld: could not read the shipped settings entry {}", entry, e);
            return CityWorldSettingsData.DEFAULT;
        }
    }

    private static final String README = """
            CityWorld — world settings example
            ==================================

            This folder is rewritten whenever CityWorld updates. Copy it; don't edit it here.

            Every setting, what it does and its range:
              https://github.com/Sablednah/CityWorld-ReForged/wiki/Configuring-CityWorld
            New to datapacks:
              https://github.com/Sablednah/CityWorld-ReForged/wiki/Beginners-Guide-to-Datapacks

            WHICH FILE
            ----------
            data/cityworld/cityworld/world_settings/ holds the settings CityWorld ships, every field written out.
            Each world type reads one of them:

              modern.json       the CityWorld world type (Modern), and the /cityworld dimension
              apocalypse.json   CityWorld: Apocalypse
              default.json      every other CityWorld world type (Classic, Metro, Sparse, Nature, ...)

            TO USE IT
            ---------
              1. Copy this whole folder into your world:  <world>/datapacks/my-cityworld-settings/
                 (a server: world/datapacks/, next to level.dat)
              2. Keep the file for your world type, delete the other two, and edit it.
              3. Create the world with the pack in place — or restart the world (or the server) after adding it.
                 /reload does not apply world settings.

            Only NEW chunks change: land already generated never regenerates.

            Your file REPLACES the shipped entry; it does not merge with it. A field you delete takes the
            built-in default from the wiki page, not this file's value — so edit values rather than removing
            lines.

            A world made with the Customize screen keeps its settings inside the world, so a settings pack never
            changes it. To move such a world's settings to a server, run /cityexport <name> in it.
            """;
}
