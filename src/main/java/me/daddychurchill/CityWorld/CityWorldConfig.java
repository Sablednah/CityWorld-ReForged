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
                .comment("Let the wild set-pieces (radio towers, castles, ...) decay further.")
                .define("includeDecayedNature", false);
        INCLUDE_FIRES = BUILDER
                .comment("Whether demolition debris can be left burning (netherrack + fire),",
                        "and whether lit campfires/fire pits appear. On by default.")
                .define("includeFires", true);

        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    private CityWorldConfig() {}
}
