package me.daddychurchill.CityWorld.integration.journeymap;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.waypoint.Waypoint;
import journeymap.api.v2.common.waypoint.WaypointFactory;
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
