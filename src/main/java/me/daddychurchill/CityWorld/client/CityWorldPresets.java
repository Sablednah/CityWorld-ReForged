package me.daddychurchill.CityWorld.client;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;

import net.minecraft.client.Minecraft;

/**
 * The player's saved Customize choices: one JSON file each in {@code config/cityworld/presets/}, holding a name,
 * the style, both realm switches and the whole settings object in its datapack form — so a preset file can also
 * be copied into a datapack's {@code world_settings} by hand. Saved and loaded only by the Presets page
 * ({@link CityWorldPresetsScreen}); a file that does not read is skipped with a warning, never fatal.
 */
public final class CityWorldPresets {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** A saved preset: what the page lists, and the file it came from. */
    public record Preset(String name, Path file, CityWorldCustomizeScreen.Result result) {}

    private CityWorldPresets() {}

    public static Path folder() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve("cityworld").resolve("presets");
    }

    /** Every preset that reads, by name. */
    public static List<Preset> list() {
        List<Preset> presets = new ArrayList<>();
        Path folder = folder();
        if (!Files.isDirectory(folder))
            return presets;
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(f -> f.getFileName().toString().endsWith(".json")).forEach(f -> read(f).ifPresent(presets::add));
        } catch (IOException e) {
            CityWorldMod.LOGGER.warn("CityWorld presets: could not list {}: {}", folder, e.toString());
        }
        presets.sort(Comparator.comparing((Preset p) -> p.name().toLowerCase(Locale.ROOT)));
        return presets;
    }

    private static Optional<Preset> read(Path file) {
        try {
            JsonObject json = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
            String name = json.has("name") ? json.get("name").getAsString() : stem(file);
            WorldStyle style = WorldStyle.valueOf(json.get("style").getAsString().toUpperCase(Locale.ROOT));
            Optional<CityWorldSettingsData> settings = CityWorldSettingsData.CODEC
                    .parse(JsonOps.INSTANCE, json.get("settings")).result();
            if (settings.isEmpty()) {
                CityWorldMod.LOGGER.warn("CityWorld presets: {} has settings that do not read; skipped", file);
                return Optional.empty();
            }
            boolean nether = json.has("ruinedNether") && json.get("ruinedNether").getAsBoolean();
            boolean end = json.has("cityWorldEnd") && json.get("cityWorldEnd").getAsBoolean();
            return Optional.of(new Preset(name, file,
                    new CityWorldCustomizeScreen.Result(style, settings.get(), nether, end)));
        } catch (Exception e) {
            CityWorldMod.LOGGER.warn("CityWorld presets: {} does not read ({}); skipped", file, e.toString());
            return Optional.empty();
        }
    }

    /** Saves under the name, replacing a preset of the same file name. */
    public static void save(String name, CityWorldCustomizeScreen.Result result) throws IOException {
        Optional<JsonElement> settings = CityWorldSettingsData.CODEC.encodeStart(JsonOps.INSTANCE, result.settings())
                .result();
        if (settings.isEmpty())
            throw new IOException("the settings would not encode");
        JsonObject json = new JsonObject();
        json.addProperty("name", name);
        json.addProperty("style", result.style().name());
        json.addProperty("ruinedNether", result.ruinedNether());
        json.addProperty("cityWorldEnd", result.cityWorldEnd());
        json.add("settings", settings.get());
        Files.createDirectories(folder());
        Files.writeString(fileFor(name), GSON.toJson(json), StandardCharsets.UTF_8);
    }

    public static void delete(Preset preset) throws IOException {
        Files.deleteIfExists(preset.file());
    }

    /** Whether saving under this name would replace a file already there. */
    public static boolean exists(String name) {
        return Files.exists(fileFor(name));
    }

    /** The name made safe for a file name on every system: letters and digits, the rest as underscores. */
    static Path fileFor(String name) {
        String stem = name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_").replaceAll("^_+|_+$", "");
        return folder().resolve((stem.isEmpty() ? "preset" : stem) + ".json");
    }

    private static String stem(Path file) {
        String name = file.getFileName().toString();
        return name.substring(0, name.length() - ".json".length());
    }
}
