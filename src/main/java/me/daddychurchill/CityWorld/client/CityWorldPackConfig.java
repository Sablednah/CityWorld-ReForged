package me.daddychurchill.CityWorld.client;

import java.util.Optional;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Modpack-author settings for the client's create-world screen — {@code config/cityworld-startup.toml}.
 *
 * <p>This is deliberately <b>not</b> a world setting: those are per-world and datapack-driven
 * ({@code CityWorldSettingsData}). This one is per-<em>instance</em>, which is exactly what a modpack ships:
 * "every world made in this pack is CityWorld Apocalypse".
 *
 * <p><b>Why a STARTUP config and not CLIENT.</b> FML opens a STARTUP config the moment it is registered, in
 * the mod constructor. CLIENT/COMMON configs load at a later loading stage, and the preset-editor
 * registration that reads this ({@code PresetEditorManager.init}, from {@code ClientHooks.initClientHooks})
 * is not guaranteed to come after it. A lock read once at boot is also the right semantics — a pack does
 * not change its world type mid-session.
 */
public final class CityWorldPackConfig {

    private CityWorldPackConfig() {}

    public static final ModConfigSpec SPEC;
    private static final ModConfigSpec.ConfigValue<String> LOCKED_WORLD_PRESET;
    private static final ModConfigSpec.ConfigValue<String> RUINED_NETHER;
    private static final ModConfigSpec.ConfigValue<String> CITYWORLD_END;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
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
        b.pop();
        SPEC = b.build();
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

    /** The preset new worlds are locked to, or empty when no lock is configured (or the id is malformed). */
    public static Optional<ResourceKey<WorldPreset>> lockedWorldPreset() {
        String raw = LOCKED_WORLD_PRESET.get().trim();
        if (raw.isEmpty())
            return Optional.empty();
        Identifier id = Identifier.tryParse(raw);
        if (id == null) {
            me.daddychurchill.CityWorld.CityWorldMod.LOGGER.warn(
                    "CityWorld: lockedWorldPreset \"{}\" is not a valid id — no world type lock applied", raw);
            return Optional.empty();
        }
        return Optional.of(ResourceKey.create(Registries.WORLD_PRESET, id));
    }
}
