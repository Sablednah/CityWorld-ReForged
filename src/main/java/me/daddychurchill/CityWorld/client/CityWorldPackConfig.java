package me.daddychurchill.CityWorld.client;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * Modpack-author settings for the client's create-world screen — {@code config/cityworld-startup.toml}.
 *
 * <p>This is deliberately <b>not</b> a world setting: those are per-world and datapack-driven
 * ({@code CityWorldSettingsData}). This one is per-<em>instance</em>, which is exactly what a modpack ships:
 * "every world made in this pack is CityWorld Apocalypse".
 *
 * <p><b>Read at startup, by hand.</b> The lock is read in the mod constructor ({@code WorldTypeLock.register})
 * and when the preset editors register ({@code PresetEditorManager.init}), and both come before Forge loads a
 * CLIENT or COMMON config. NeoForge has a STARTUP config type for this; Forge 1.20.1 does not, so {@link #load}
 * opens the file itself. A lock read once at boot is also the right semantics — a pack does not change its
 * world type mid-session.
 *
 * <p><b>What registering it as a CLIENT config did (until 2026-10-01):</b> every read came before the load. A
 * shipped jar got the defaults back without a word — Forge only throws for that in a development environment —
 * so the lock never applied on 1.20.1, and the file Forge wrote was {@code cityworld-client.toml}, not the
 * documented one. The dev client did throw, at startup, which is how it was found.
 */
public final class CityWorldPackConfig {

    private CityWorldPackConfig() {}

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.ConfigValue<String> LOCKED_WORLD_PRESET;
    private static final ForgeConfigSpec.ConfigValue<String> RUINED_NETHER;
    private static final ForgeConfigSpec.ConfigValue<String> CITYWORLD_END;
    private static final ForgeConfigSpec.BooleanValue LOCK_CUSTOMIZE;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Create-world screen settings for modpacks. Per-instance, client-only; a server uses",
                "level-type in server.properties instead.").push("worldCreation");
        LOCKED_WORLD_PRESET = b.comment(
                "World preset every new single-player world is locked to, e.g. \"cityworld:apocalypse\".",
                "The World Type button is greyed out and Customize keeps the preset's style, but its other",
                "settings stay editable. Empty = no lock (vanilla behaviour).")
                .define("lockedWorldPreset", "");
        RUINED_NETHER = b.comment(
                "Lock the Nether of every new world: \"cityworld\" = the ruined-city Nether (the overworld's city at",
                "1:1, burnt), \"vanilla\" = vanilla's Nether. Customize shows the choice greyed out.",
                "Empty = the player chooses in Customize (the CityWorld world types default to the ruined city).")
                .define("ruinedNether", "");
        CITYWORLD_END = b.comment(
                "Lock the End of every new world: \"cityworld\" = CityWorld's End (vanilla's End throughout, with",
                "CityWorld cities on the flat of the outer islands), \"vanilla\" = vanilla's End.",
                "Empty = the player chooses in Customize (the CityWorld world types default to the cities).")
                .define("cityworldEnd", "");
        LOCK_CUSTOMIZE = b.comment(
                "With lockedWorldPreset set: true removes the Customize button for that preset, so every new world",
                "gets the preset's own world_settings datapack entry unchanged (a pack that replaces that entry is",
                "then authoritative). false keeps Customize open with only the style held. Ignored without a lock.")
                .define("lockCustomize", false);
        b.pop();
        SPEC = b.build();
    }

    /**
     * Opens {@code config/cityworld-startup.toml} (written with its defaults and comments when absent) and binds
     * the spec to it, so the values can be read from here on. Called once, from the client's mod constructor.
     */
    public static void load() {
        java.nio.file.Path path = net.minecraftforge.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve("cityworld-startup.toml");
        try {
            com.electronwill.nightconfig.core.file.CommentedFileConfig file =
                    com.electronwill.nightconfig.core.file.CommentedFileConfig.builder(path).sync()
                            .preserveInsertionOrder()
                            .writingMode(com.electronwill.nightconfig.core.io.WritingMode.REPLACE).build();
            file.load();
            SPEC.setConfig(file); // corrects and saves anything missing, which is what writes a fresh file
        } catch (RuntimeException e) {
            // an unreadable file must not stop the game: no lock, said once
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: could not read {} — no world type lock applied", path, e);
            SPEC.setConfig(com.electronwill.nightconfig.core.CommentedConfig.inMemory());
        }
    }

    /** The Nether lock: true = ruined city, false = vanilla, empty = the player's choice (or an unrecognised value). */
    public static Optional<Boolean> lockedRuinedNether() {
        return switch (RUINED_NETHER.get().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "cityworld", "ruined" -> Optional.of(true);
            case "vanilla" -> Optional.of(false);
            case "" -> Optional.empty();
            default -> {
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                        "CityWorld: ruinedNether \"{}\" is not cityworld/vanilla — no Nether lock applied", RUINED_NETHER.get());
                yield Optional.empty();
            }
        };
    }

    /** The End lock: true = CityWorld's End, false = vanilla, empty = the player's choice. */
    public static Optional<Boolean> lockedCityWorldEnd() {
        return switch (CITYWORLD_END.get().trim().toLowerCase(java.util.Locale.ROOT)) {
            case "cityworld" -> Optional.of(true);
            case "vanilla" -> Optional.of(false);
            case "" -> Optional.empty();
            default -> {
                me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                        "CityWorld: cityworldEnd \"{}\" is not cityworld/vanilla — no End lock applied", CITYWORLD_END.get());
                yield Optional.empty();
            }
        };
    }

    /** Whether the locked preset's Customize button is removed as well — meaningless without a preset lock. */
    public static boolean lockCustomize() {
        return LOCK_CUSTOMIZE.get() && lockedWorldPreset().isPresent();
    }

    /** The preset new worlds are locked to, or empty when no lock is configured (or the id is malformed). */
    public static Optional<ResourceKey<WorldPreset>> lockedWorldPreset() {
        String raw = LOCKED_WORLD_PRESET.get().trim();
        if (raw.isEmpty())
            return Optional.empty();
        ResourceLocation id = ResourceLocation.tryParse(raw);
        if (id == null) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: lockedWorldPreset \"{}\" is not a valid id — no world type lock applied", raw);
            return Optional.empty();
        }
        return Optional.of(ResourceKey.create(Registries.WORLD_PRESET, id));
    }
}
