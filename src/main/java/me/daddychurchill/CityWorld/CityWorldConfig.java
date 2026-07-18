package me.daddychurchill.CityWorld;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * A first, focused slice of runtime configuration (COMMON: dedicated server + singleplayer).
 *
 * <p><b>This is not the full P7 settings port.</b> Upstream {@code CityWorldSettings} carries ~100
 * knobs and is <em>per-world</em> (parsed from YAML), whereas a {@code ModConfigSpec} is
 * <em>per-instance</em> — so a faithful port needs a datapack / world-saved-data approach
 * (PORTING.md, top risk #4). Until that lands, this exposes just the "decay" family, because it is
 * the one people actually reach for (the apocalypse preset). Being per-instance, it applies to every
 * {@code cityworld} world on the install, which is fine for that use.
 *
 * <p>{@link CityWorldSettings} overlays these onto its defaults at construction, guarded on
 * {@link ModConfigSpec#isLoaded()} so plan-only paths (probes, tests) that build settings before
 * config load keep the compiled defaults.
 */
public final class CityWorldConfig {

    public static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.BooleanValue INCLUDE_DECAYED_BUILDINGS;
    public static final ModConfigSpec.BooleanValue INCLUDE_DECAYED_ROADS;
    public static final ModConfigSpec.BooleanValue INCLUDE_DECAYED_NATURE;
    public static final ModConfigSpec.BooleanValue INCLUDE_FIRES;
    public static final ModConfigSpec.BooleanValue INCLUDE_SCHEMATICS;

    static {
        BUILDER.comment("Decay / apocalypse options.",
                "These control whether CityWorld demolishes what it builds — holes blown through",
                "structures with debris scattered around them. Applies to newly generated chunks",
                "only; existing chunks never regenerate, so start a fresh world to see a change.").push("decay");

        INCLUDE_DECAYED_BUILDINGS = BUILDER
                .comment("Chew ruin-holes into buildings (houses, highrises, factories, barns, ...).",
                        "The classic 'apocalypse server' switch.")
                .define("includeDecayedBuildings", false);
        INCLUDE_DECAYED_ROADS = BUILDER
                .comment("Break up roads and sidewalks with rubble.")
                .define("includeDecayedRoads", false);
        INCLUDE_DECAYED_NATURE = BUILDER
                .comment("Dry the whole world out — the full post-apocalyptic look.",
                        "Drains the seas (only the deepest water, below deep-sea level, remains),",
                        "forces every biome to desert, and withers crops and ground cover.",
                        "This is what empties the ocean; leave it false for ruined buildings on a",
                        "normal, wet, green world.")
                .define("includeDecayedNature", false);
        INCLUDE_FIRES = BUILDER
                .comment("Whether demolition debris can be left burning (netherrack + fire),",
                        "and whether lit campfires/fire pits appear. On by default.")
                .define("includeFires", true);

        BUILDER.pop();

        BUILDER.comment("Classic schematics — the bundled zarp building catalog.",
                "These are always available on demand via /cityschem; this only controls whether",
                "the generator itself drops them into cities as it builds. Applies to newly",
                "generated chunks only.").push("schematics");

        INCLUDE_SCHEMATICS = BUILDER
                .comment("Let the generator place classic schematics into cities as they generate.",
                        "Off by default — upstream only did this with a WorldEdit-loaded folder, so",
                        "most worlds never saw them. Turn on to salt cities with the old catalog.")
                .define("includeSchematics", false);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CityWorldConfig() {}
}
