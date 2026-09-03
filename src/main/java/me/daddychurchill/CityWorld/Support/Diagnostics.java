package me.daddychurchill.CityWorld.Support;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * The diagnostics report — normally off, enabled with {@code -Dcityworld.diagnostics=true} (and
 * always on under the self-test, so CI logs carry it).
 *
 * <p>This exists because this project's worst bugs have all had the same shape: a pool silently
 * resolving empty while its fallback looked plausible — a discarded tag file, a key registered in
 * the wrong namespace, an id that never existed. Each cost a playtest round to notice. This sweeps
 * every {@code TagKey} constant on {@link MaterialTags} and {@link FurnitureTags} by reflection
 * (so new tags are covered the moment they are declared), logs a count for each, and shouts about
 * the empties. Vanilla-seeded pools that come back empty are called out as broken outright.
 */
public final class Diagnostics {

    private static final String ENABLE_PROPERTY = "cityworld.diagnostics";

    public static boolean enabled() {
        return Boolean.getBoolean(ENABLE_PROPERTY)
                || me.daddychurchill.CityWorld.selftest.CityWorldSelfTest.enabled();
    }

    @SubscribeEvent
    public void onStarted(ServerStartedEvent event) {
        if (enabled())
            runReport();
    }

    /** Sweep every declared tag; log one line each, WARN for the empties. */
    public static void runReport() {
        CityWorldMod.LOGGER.info("CityWorld diagnostics: sweeping every declared tag pool");
        int total = 0, empty = 0;
        for (Class<?> holder : new Class<?>[] { MaterialTags.class, FurnitureTags.class }) {
            for (Field field : holder.getFields()) {
                if (!Modifier.isStatic(field.getModifiers()) || !TagKey.class.isAssignableFrom(field.getType()))
                    continue;
                try {
                    @SuppressWarnings("unchecked")
                    TagKey<Block> tag = (TagKey<Block>) field.get(null);
                    int n = MaterialTags.resolve(tag).size();
                    total++;
                    if (n == 0) {
                        empty++;
                        CityWorldMod.LOGGER.warn("CityWorld diagnostics: #{} ({}.{}) is EMPTY — "
                                + "whatever draws from it is silently falling back",
                                tag.location(), holder.getSimpleName(), field.getName());
                    } else {
                        CityWorldMod.LOGGER.info("CityWorld diagnostics: #{} = {} blocks", tag.location(), n);
                    }
                } catch (IllegalAccessException e) {
                    CityWorldMod.LOGGER.warn("CityWorld diagnostics: could not read {}.{}", holder.getSimpleName(),
                            field.getName());
                }
            }
        }
        CityWorldMod.LOGGER.info("CityWorld diagnostics: {} pools swept, {} empty", total, empty);
    }
}
