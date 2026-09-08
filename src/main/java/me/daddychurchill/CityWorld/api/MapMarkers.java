package me.daddychurchill.CityWorld.api;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * The seam map mods hook into to learn what CityWorld built and where.
 * <p>
 * CityWorld knows things a minimap cannot: a landmark's name at the moment it is planned, long
 * before any player walks past it. This class carries that out to whoever is listening, in
 * CityWorld's own types only — no map mod's classes appear here, so the core never loads one that
 * isn't installed. The JourneyMap integration lives in
 * {@code me.daddychurchill.CityWorld.integration.journeymap} and registers itself when JourneyMap
 * classloads it; another map mod would add its own package the same way.
 * <p>
 * <b>Listeners are called from the worldgen worker threads</b>, one chunk among many, and must not
 * block or touch the server thread directly — hop via {@code server.execute(...)} for anything that
 * mutates game state. A listener that throws is logged and dropped, never propagated into the
 * generator.
 */
public final class MapMarkers {

    private MapMarkers() {}

    /**
     * A named place CityWorld has just planned.
     *
     * @param dimension the level it was planned in
     * @param kind      the landmark kind, as used by the {@code announcedLandmarks} setting
     *                  (e.g. {@code "schematic"}, {@code "zoo"}) — the grouping key
     * @param title     the human name to show ("The Statue of Liberty")
     * @param x         world X of the lot's centre
     * @param y         a sensible Y for a marker (street level; the map itself is flat)
     * @param z         world Z of the lot's centre
     */
    public record Landmark(ResourceKey<Level> dimension, String kind, String title, int x, int y, int z) {}

    /**
     * A place one player asked about — what {@code /cityfind}, {@code /cityfind lot} and
     * {@code /cwlocate} just located. Unlike a {@link Landmark} this is private to {@code player}.
     *
     * @param player    who searched
     * @param dimension the level searched
     * @param label     what to call the marker ("Nearest zoo")
     */
    public record PlayerMark(UUID player, ResourceKey<Level> dimension, String label, int x, int y, int z) {}

    /** Notified as landmarks are planned. See the threading note on {@link MapMarkers}. */
    public interface Listener {
        void onLandmark(Landmark landmark);

        /** A search result to put on one player's map. Default: ignore — waypoints are optional. */
        default void onPlayerMark(PlayerMark mark) {}

        /** True when this map mod can draw the city plan — districts and streets — on its map. */
        default boolean drawsCityPlan() {
            return false;
        }

        /** A player turned the city plan on or off; redraw or clear for them. */
        default void onCityPlanToggled(UUID player, boolean on) {}
    }

    private static final List<Listener> LISTENERS = new CopyOnWriteArrayList<>();

    /** Registers {@code listener}. Called from a map mod's plugin during its own init. */
    public static void addListener(Listener listener) {
        LISTENERS.add(listener);
        CityWorldMod.LOGGER.debug("MapMarkers listener registered: {}", listener.getClass().getName());
    }

    /** True when at least one map mod is listening — lets the generator skip the work entirely. */
    public static boolean hasListeners() {
        return !LISTENERS.isEmpty();
    }

    /** Announces {@code landmark} to every listener. Safe to call from worldgen threads. */
    public static void landmark(Landmark landmark) {
        for (Listener listener : LISTENERS) {
            try {
                listener.onLandmark(landmark);
            } catch (Throwable t) {
                // A map mod's failure must never fail a chunk.
                CityWorldMod.LOGGER.error("Map marker listener {} failed for '{}'",
                        listener.getClass().getName(), landmark.title(), t);
            }
        }
    }

    // --- the city plan overlay ------------------------------------------------------------------

    /**
     * Players who have turned the plan overlay off. Held as an opt-<em>out</em> because the overlay
     * is on by default: CityWorld knows the street layout of cities nobody has walked yet, and
     * showing it is the point. In memory only — a player who turns it off gets it back next login,
     * which is the cheap end of "remember it forever" and easy to revisit.
     */
    private static final Set<UUID> PLAN_OFF = ConcurrentHashMap.newKeySet();

    /** True when some installed map mod can draw the plan (so {@code /citymap} has something to say). */
    public static boolean cityPlanAvailable() {
        for (Listener listener : LISTENERS)
            if (listener.drawsCityPlan())
                return true;
        return false;
    }

    /** Whether {@code player} wants the plan drawn. On unless they said otherwise. */
    public static boolean wantsCityPlan(UUID player) {
        return !PLAN_OFF.contains(player);
    }

    /** Turns the plan on or off for {@code player} and tells the map mods to act on it. */
    public static void setCityPlan(UUID player, boolean on) {
        if (on)
            PLAN_OFF.remove(player);
        else
            PLAN_OFF.add(player);
        for (Listener listener : LISTENERS) {
            try {
                listener.onCityPlanToggled(player, on);
            } catch (Throwable t) {
                CityWorldMod.LOGGER.error("Map marker listener {} failed toggling the city plan",
                        listener.getClass().getName(), t);
            }
        }
    }

    /** Puts one search result on one player's map. Called from the command threads. */
    public static void playerMark(PlayerMark mark) {
        for (Listener listener : LISTENERS) {
            try {
                listener.onPlayerMark(mark);
            } catch (Throwable t) {
                CityWorldMod.LOGGER.error("Map marker listener {} failed for '{}'",
                        listener.getClass().getName(), mark.label(), t);
            }
        }
    }
}
