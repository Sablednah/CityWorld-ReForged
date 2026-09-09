package me.daddychurchill.CityWorld.integration.journeymap;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
import journeymap.api.v2.common.waypoint.WaypointGroup;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.IServerPlugin;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.api.MapMarkers;

import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * CityWorld's JourneyMap integration.
 *
 * <p><b>Nothing else in the mod may reference this class</b>, and this package is the only place a
 * {@code journeymap.*} type may appear. JourneyMap discovers the class itself by scanning for
 * {@link JourneyMapPlugin}, so with JourneyMap absent these classes are never loaded and the
 * JourneyMap API — which is a {@code compileOnly} dependency, never shipped in our jar — is never
 * needed at runtime. The generator talks to us only through {@link MapMarkers}.
 *
 * <p>This is the <em>server</em> plugin ({@link IServerPlugin}, new in JourneyMap API 2.0): the plan
 * lives on the server, and the server API can push waypoints and map overlays straight to connected
 * clients. That covers single-player (the integrated server) and dedicated servers with one
 * implementation, and needs no networking of CityWorld's own.
 *
 * <p><b>New here?</b> Read this package's {@code package-info} first — it is the whole approach in
 * one place, written so the next map mod's integration can be cribbed from it, traps included.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = { CityWorldMod.MODID })
public class CityWorldJourneyMapPlugin implements IServerPlugin, MapMarkers.Listener {

    private IServerAPI api;
    private CityPlanOverlay planOverlay;

    /**
     * Landmarks already marked, as {@code dimension|x|z|title}. A chunk announces its landmark as it
     * is planned, and the planner runs per platmap over many threads, so the same landmark can be
     * reported more than once in a session; the JourneyMap store would happily hold both.
     */
    private final Set<String> marked = ConcurrentHashMap.newKeySet();

    /** The name of each group, and the group itself once made, per player. */
    private static final String LANDMARK_GROUP = "CityWorld Landmarks";
    private static final String FIND_GROUP = "CityWorld Finds";

    /** Keyed by player <em>and</em> group name — a player has both groups, not one at a time. */
    private final Map<String, WaypointGroup> groups = new ConcurrentHashMap<>();

    @Override
    public String getModId() {
        return CityWorldMod.MODID;
    }

    @Override
    public void initialize(final IServerAPI jmServerApi) {
        this.api = jmServerApi;
        this.planOverlay = new CityPlanOverlay(jmServerApi);
        this.planOverlay.register();
        MapMarkers.addListener(this);
        CityWorldMod.LOGGER.info("JourneyMap server API found — CityWorld landmarks and city plan will be mapped");
        this.planOverlay.selfCheck();
    }

    /**
     * Called from a worldgen worker as a landmark is planned. Gives a waypoint to each player in the
     * level it generated in — the same audience the chat announce reaches — on the server thread,
     * since the waypoint store is server state and pushes packets to clients.
     *
     * <p><b>Not a global waypoint, though that reads like the natural fit.</b> The API describes
     * global waypoints as what a player receives "when they log in", and in play they did exactly
     * that: sixteen landmarks announced in chat, not one pin on the map, with no error on either
     * side. Per-player waypoints are the mechanism that demonstrably reaches a connected client.
     */
    @Override
    public void onLandmark(MapMarkers.Landmark landmark) {
        String key = landmark.dimension().identifier() + "|" + landmark.x() + "|" + landmark.z()
                + "|" + landmark.title();
        if (!marked.add(key))
            return;

        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return; // a plan-only sweep with no running server
        server.execute(() -> {
            try {
                int given = 0;
                for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                    if (!player.level().dimension().equals(landmark.dimension()))
                        continue;
                    // A waypoint per recipient: the factory stamps identity into the instance, so
                    // handing the same one to several players is not safe to assume.
                    Waypoint waypoint = WaypointFactory.createWaypoint(CityWorldMod.MODID,
                            new BlockPos(landmark.x(), landmark.y(), landmark.z()),
                            landmark.title(), landmark.dimension(), true);
                    waypoint.setColor(colorFor(landmark.kind()));
                    group(player.getUUID(), LANDMARK_GROUP, waypoint);
                    api.addPlayerWaypoint(player.getUUID(), waypoint);
                    given++;
                }
                CityWorldMod.LOGGER.debug("JourneyMap waypoint '{}' ({}) at {}, {} -> {} player(s)",
                        landmark.title(), landmark.kind(), landmark.x(), landmark.z(), given);
            } catch (Throwable t) {
                CityWorldMod.LOGGER.error("JourneyMap waypoint for '{}' failed", landmark.title(), t);
            }
        });
    }

    /**
     * A search result ({@code /cityfind}, {@code /cwlocate}) put on the searching player's own map.
     * Not global: nobody else asked for it. Persistent, so it survives the trip out to find it.
     */
    @Override
    public void onPlayerMark(MapMarkers.PlayerMark mark) {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        server.execute(() -> {
            try {
                Waypoint waypoint = WaypointFactory.createWaypoint(CityWorldMod.MODID,
                        new BlockPos(mark.x(), mark.y(), mark.z()), mark.label(), mark.dimension(), true);
                waypoint.setColor(FIND_COLOR);
                group(mark.player(), FIND_GROUP, waypoint);
                api.addPlayerWaypoint(mark.player(), waypoint);
            } catch (Throwable t) {
                CityWorldMod.LOGGER.error("JourneyMap waypoint for '{}' failed", mark.label(), t);
            }
        });
    }

    @Override
    public boolean drawsCityPlan() {
        return true;
    }

    @Override
    public void onCityPlanToggled(java.util.UUID player, boolean on) {
        planOverlay.toggled(player, on);
    }

    /**
     * Files a waypoint under one of CityWorld's two groups, so a player can show or hide the
     * landmarks the world announced separately from the places they went looking for.
     *
     * <p>An existing group of the same name is reused — including one saved from an earlier session —
     * so a long-running world does not collect a new "CityWorld Landmarks" every time it loads. The
     * whole thing is best-effort: if grouping fails, the waypoint is still added, ungrouped, because
     * a pin in the wrong drawer beats no pin at all.
     */
    private void group(UUID player, String name, Waypoint waypoint) {
        try {
            WaypointGroup group = groups.computeIfAbsent(player + "|" + name, key -> {
                for (WaypointGroup existing : api.getAllGroups(player))
                    if (CityWorldMod.MODID.equals(existing.getModId()) && name.equals(existing.getName()))
                        return existing;
                WaypointGroup made = WaypointFactory.createWaypointGroup(CityWorldMod.MODID, name);
                api.addPlayerGroup(player, made);
                return made;
            });
            if (group != null)
                group.addWaypoint(waypoint);
        } catch (Throwable t) {
            CityWorldMod.LOGGER.debug("JourneyMap waypoint group '{}' unavailable", name, t);
        }
    }

    /** One colour for every search result, so they read as a set apart from the landmarks. */
    private static final int FIND_COLOR = 0xFFD24D;

    /**
     * A stable colour per landmark kind. Derived from the kind's hash rather than a hand-written
     * table so that kinds added later (and a server's own curated {@code announcedLandmarks}) still
     * get a distinct, unchanging colour; the low bits are forced bright so nothing lands on
     * unreadable near-black.
     */
    private static int colorFor(String kind) {
        int h = kind.hashCode();
        int r = 0x60 | (h & 0x9F);
        int g = 0x60 | ((h >> 8) & 0x9F);
        int b = 0x60 | ((h >> 16) & 0x9F);
        return (r << 16) | (g << 8) | b;
    }
}
