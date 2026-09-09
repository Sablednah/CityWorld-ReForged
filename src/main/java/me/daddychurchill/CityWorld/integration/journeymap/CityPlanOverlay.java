package me.daddychurchill.CityWorld.integration.journeymap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import journeymap.api.v2.client.util.UIState;
import journeymap.api.v2.server.IServerAPI;
import journeymap.api.v2.server.overlay.OverlayPoints;
import journeymap.api.v2.server.overlay.OverlayPolygon;
import journeymap.api.v2.server.overlay.OverlayShapeProps;
import journeymap.api.v2.server.overlay.ServerPolygon;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.api.MapMarkers;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Draws CityWorld's <em>plan</em> on the JourneyMap map: district blocks and the street grid, around
 * each player, in cities they have never been to.
 *
 * <p>This is the thing only CityWorld can do. JourneyMap maps what a player has seen; CityWorld
 * decided where every road and district goes before anyone arrived, and can answer for any chunk
 * without generating it ({@code PlatMap} planning is deterministic, thread-safe and cached). So the
 * map can show the shape of a city from the edge of it.
 *
 * <p>Two overlays per platmap, pushed with {@code IServerOverlayAPI}:
 * <ul>
 *   <li>a district square (the whole 160×160 platmap) tinted by its {@link SchematicFamily}, over a
 *       wide radius — this is the zoomed-out "where is the city" picture;
 *   <li>the streets of the nearer platmaps, as merged rectangles in road grey.
 * </ul>
 *
 * <p>Planning is off the server thread (measured: a cold 7×7 sweep is ~9 seconds, ~180 ms per
 * platmap — the same reason {@code /cityfind} threads its search) and happens only when a player
 * crosses into a new platmap. Two things keep that affordable: the sweep runs ring by ring and
 * pushes each ring as it finishes, so the map fills outwards from the player within a fraction of a
 * second rather than after nine; and every player's sweep shares <em>one</em> worker thread, because
 * planned platmaps are cached and shared, so two players in the same city cost barely more than one.
 */
final class CityPlanOverlay {

    /**
     * Platmaps each way for the district tint: 7×7 platmaps, about 1120 blocks across.
     *
     * <p>Overridable with {@code -Dcityworld.mapradius=N} so a stationary player can be handed
     * thousands of overlays at once — the only practical way to measure what they cost a client
     * without walking for an hour. Not a player-facing setting; the budget
     * ({@code MapMarkers.cityPlanBudget}) is the knob that matters in play.
     */
    private static final int DISTRICT_RADIUS = Integer.getInteger("cityworld.mapradius", 3);

    /** Platmaps each way for the street grid: 5×5, about 800 blocks. Streets are the part of this
     *  that reads best in play, so they reach further than the first cut allowed. */
    private static final int ROAD_RADIUS = 2;

    /** Ticks between position checks. The unit of change is a 160-block platmap; 2s is plenty. */
    private static final int CHECK_INTERVAL = 40;

    /**
     * One worker for every player's sweeps. Deliberately single-threaded: platmap planning is
     * deterministic and cached, so serialising the work means the second player through a city pays
     * almost nothing, while a dozen parallel sweeps would each pay full price.
     */
    private static final ExecutorService PLANNER = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "cityworld-plan-overlay");
        thread.setDaemon(true);
        return thread;
    });

    private final IServerAPI api;

    /** What each player currently has on their map, so a move only pushes and removes the delta. */
    private final Map<UUID, PlayerState> states = new ConcurrentHashMap<>();

    private int tickCounter;

    private static final class PlayerState {
        ResourceKey<Level> dimension;
        int platX = Integer.MIN_VALUE;
        int platZ = Integer.MIN_VALUE;
        /** Overlay id -> the platmap it covers, so distant ones can be dropped and the rest kept. */
        final Map<String, long[]> shown = new java.util.HashMap<>();
        boolean working;
    }

    CityPlanOverlay(IServerAPI api) {
        this.api = api;
    }

    void register() {
        NeoForge.EVENT_BUS.addListener(ServerTickEvent.Post.class, this::onTick);
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerLoggedOutEvent.class, e -> states.remove(e.getEntity().getUUID()));
        // Leaving the level drops what was drawn there. The overlays are per-dimension, so they would
        // not be drawn elsewhere anyway, but they would pile up on the client across round trips.
        NeoForge.EVENT_BUS.addListener(PlayerEvent.PlayerChangedDimensionEvent.class, e -> {
            states.remove(e.getEntity().getUUID());
            if (e.getEntity() instanceof ServerPlayer player)
                api.getOverlayApi().clearAll(player, CityWorldMod.MODID);
        });
    }

    /**
     * {@code -Dcityworld.maptest=true}: build the overlays for the platmap at the origin and log what
     * came out. The shapes themselves are only visible to a player with JourneyMap running, so
     * without this the geometry — is the plan read right, do the road strips merge — is unverifiable
     * except by eye in a live client.
     */
    void selfCheck() {
        if (!Boolean.getBoolean("cityworld.maptest"))
            return;
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        ServerLevel level = server.overworld();
        CityWorldGenerator context = contextFor(level);
        if (context == null) {
            CityWorldMod.LOGGER.info("CityWorld: -Dcityworld.maptest — overworld is not a CityWorld level");
            return;
        }
        List<ServerPolygon> polygons = new ArrayList<>();
        long started = System.nanoTime();
        for (int ring = 0; ring <= DISTRICT_RADIUS; ring++) {
            long ringStarted = System.nanoTime();
            int before = polygons.size();
            build(context, level.dimension(), 0, 0, ring, polygons, new java.util.HashMap<>());
            CityWorldMod.LOGGER.info("CityWorld: city plan ring {} -> {} overlays in {} ms", ring,
                    polygons.size() - before, (System.nanoTime() - ringStarted) / 1_000_000L);
        }
        long ms = (System.nanoTime() - started) / 1_000_000L;
        int shapes = 0;
        for (ServerPolygon polygon : polygons)
            shapes += polygon.polygons().size();
        CityWorldMod.LOGGER.info("CityWorld: city plan around 0,0 -> {} overlays / {} shapes in {} ms",
                polygons.size(), shapes, ms);
        for (ServerPolygon polygon : polygons)
            CityWorldMod.LOGGER.info("CityWorld:   {} — {} shape(s), label {}", polygon.overlayId(),
                    polygon.polygons().size(), polygon.props().label());

        // Sweep the same ground a second time and compare. A player's map showed one platmap wearing
        // two district labels at once ("Lowrise" over "Farm"), which is either the plan answering
        // differently on a later sweep or JourneyMap keeping the overlay it was told to replace —
        // and those want opposite fixes, so measure rather than guess.
        Map<String, String> first = new java.util.LinkedHashMap<>();
        for (ServerPolygon polygon : polygons)
            first.put(polygon.overlayId(), String.valueOf(polygon.props().label()));
        List<ServerPolygon> again = new ArrayList<>();
        for (int ring = 0; ring <= DISTRICT_RADIUS; ring++)
            build(context, level.dimension(), 0, 0, ring, again, new java.util.HashMap<>());
        int changed = 0;
        for (ServerPolygon polygon : again) {
            String was = first.get(polygon.overlayId());
            String now = String.valueOf(polygon.props().label());
            if (was == null) {
                CityWorldMod.LOGGER.warn("CityWorld:   RESWEEP added {} ({})", polygon.overlayId(), now);
                changed++;
            } else if (!was.equals(now)) {
                CityWorldMod.LOGGER.warn("CityWorld:   RESWEEP {} changed {} -> {}", polygon.overlayId(),
                        was, now);
                changed++;
            }
        }
        CityWorldMod.LOGGER.info("CityWorld: re-sweep of the same area -> {} overlays, {} differing",
                again.size(), changed);
    }

    /**
     * The player turned the plan on or off — from {@code /citymap} or from JourneyMap's own options
     * and toolbar button. Either way the slate is wiped: off leaves a clean map, and on redraws from
     * nothing a moment later rather than layering over whatever was left.
     */
    void toggled(UUID player, boolean on) {
        states.remove(player);
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return;
        ServerPlayer sp = server.getPlayerList().getPlayer(player);
        if (sp != null)
            api.getOverlayApi().clearAll(sp, CityWorldMod.MODID);
    }

    private void onTick(ServerTickEvent.Post event) {
        if (++tickCounter < CHECK_INTERVAL)
            return;
        tickCounter = 0;
        for (ServerPlayer player : event.getServer().getPlayerList().getPlayers())
            check(player);
    }

    /** Has this player moved into a platmap they have no overlay for yet? */
    private void check(ServerPlayer player) {
        UUID id = player.getUUID();
        if (!MapMarkers.wantsCityPlan(id)) {
            states.remove(id);
            return;
        }
        ServerLevel level = player.level();
        CityWorldGenerator context = contextFor(level);
        if (context == null) {
            states.remove(id); // not a CityWorld level — nothing to draw
            return;
        }
        // From the block position, not ChunkPos: ChunkPos became a record in 26.1, so its fields are
        // x()/z() there and x/z on 1.21.11 — and this integration is meant to be the same source on
        // every version branch.
        int platX = Math.floorDiv(player.blockPosition().getX() >> 4, PlatMap.Width) * PlatMap.Width;
        int platZ = Math.floorDiv(player.blockPosition().getZ() >> 4, PlatMap.Width) * PlatMap.Width;

        PlayerState state = states.computeIfAbsent(id, k -> new PlayerState());
        synchronized (state) {
            if (state.working)
                return; // a plan sweep for this player is still running
            if (state.platX == platX && state.platZ == platZ && level.dimension().equals(state.dimension))
                return;
            state.working = true;
            state.platX = platX;
            state.platZ = platZ;
            state.dimension = level.dimension();
        }

        MinecraftServer server = player.level().getServer();
        PLANNER.execute(() -> {
            List<ServerPolygon> polygons = new ArrayList<>();
            // What this sweep covers, kept outside the try so the bookkeeping below still runs after
            // a partial failure — a half-drawn plan should still be a plan we remember drawing.
            Map<String, long[]> covered = new java.util.HashMap<>();
            try {
                Set<String> already;
                synchronized (state) {
                    already = new HashSet<>(state.shown.keySet());
                }
                for (int ring = 0; ring <= DISTRICT_RADIUS; ring++) {
                    List<ServerPolygon> ofRing = new ArrayList<>();
                    build(context, level.dimension(), platX, platZ, ring, ofRing, covered);
                    polygons.addAll(ofRing);
                    // Only send what this player does not already have. A platmap's plan never
                    // changes (measured: a re-sweep of the same ground differs in nothing), so
                    // re-showing it is pure waste — and it is how a client ends up wearing two
                    // overlays for one square, which is what put two district labels on top of each
                    // other in play.
                    List<ServerPolygon> fresh = new ArrayList<>();
                    for (ServerPolygon polygon : ofRing)
                        if (!already.contains(polygon.overlayId()))
                            fresh.add(polygon);
                    if (!fresh.isEmpty())
                        server.execute(() -> show(player, fresh));
                }
            } catch (Throwable t) {
                CityWorldMod.LOGGER.error("City plan overlay failed at platmap {}, {}", platX, platZ, t);
                // Forget where we were, so standing still and trying again re-sweeps rather than
                // leaving a half-drawn plan until the player wanders into the next platmap.
                synchronized (state) {
                    state.platX = Integer.MIN_VALUE;
                }
            }
            server.execute(() -> finish(player, state, covered, platX, platZ));
        });
    }

    /** Sends one ring's overlays; re-showing an id the player already has just replaces it. */
    private void show(ServerPlayer player, List<ServerPolygon> polygons) {
        try {
            api.getOverlayApi().show(player, CityWorldMod.MODID, polygons.toArray(new ServerPolygon[0]));
        } catch (Throwable t) {
            CityWorldMod.LOGGER.error("City plan overlay push failed", t);
        }
    }

    /**
     * Remembers what this player now has, and drops the furthest overlays only once they are over
     * <em>their own</em> budget ({@code MapMarkers.cityPlanBudget}, set from the map mod's options or
     * {@code /citymap keep}).
     *
     * <p>A count, not a radius. The first cut dropped anything outside a box around the player, which
     * threw away a city they had crossed twice while holding almost nothing — and the cost being
     * bounded is a client concern, so counting the thing the client actually holds is the honest
     * measure. Retained overlays cost the server nothing: they are already planned and are never
     * re-sent. Nothing is lost when one does drop, either — walking back redraws it.
     */
    private void finish(ServerPlayer player, PlayerState state, Map<String, long[]> covered,
            int centreX, int centreZ) {
        try {
            synchronized (state) {
                state.shown.putAll(covered);
                int budget = MapMarkers.cityPlanBudget(player.getUUID());
                if (state.shown.size() <= budget)
                    return;

                // Over budget: drop the furthest first, so what goes is what the player is least
                // likely to look at next.
                List<Map.Entry<String, long[]>> byDistance = new ArrayList<>(state.shown.entrySet());
                byDistance.sort((a, b) -> Long.compare(distance(b.getValue(), centreX, centreZ),
                        distance(a.getValue(), centreX, centreZ)));
                int over = state.shown.size() - budget;
                for (int i = 0; i < over; i++) {
                    Map.Entry<String, long[]> furthest = byDistance.get(i);
                    api.getOverlayApi().remove(player, CityWorldMod.MODID, furthest.getKey());
                    state.shown.remove(furthest.getKey());
                }
                CityWorldMod.LOGGER.debug("City plan overlay: dropped {} of {} for {} (budget {})",
                        over, over + budget, player.getGameProfile().name(), budget);
            }
        } catch (Throwable t) {
            CityWorldMod.LOGGER.error("City plan overlay cleanup failed", t);
        } finally {
            synchronized (state) {
                state.working = false;
            }
        }
    }

    /** Chebyshev distance in platmaps, squared away from any need for floating point. */
    private static long distance(long[] at, int centreX, int centreZ) {
        return Math.max(Math.abs(at[0] - centreX), Math.abs(at[1] - centreZ));
    }

    // --- building the shapes --------------------------------------------------------------------

    /** Builds one square ring of platmaps at radius {@code ring} around the centre ({@code 0} = just it). */
    private void build(CityWorldGenerator context, ResourceKey<Level> dimension, int centreX, int centreZ,
            int ring, List<ServerPolygon> out, Map<String, long[]> covered) {
        int y = context.streetLevel;
        for (int dx = -ring; dx <= ring; dx++) {
            for (int dz = -ring; dz <= ring; dz++) {
                if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                    continue; // only the ring itself; the inside is already drawn
                int platX = centreX + dx * PlatMap.Width;
                int platZ = centreZ + dz * PlatMap.Width;
                PlatMap platmap = context.getPlatMap(platX, platZ);
                SchematicFamily family = platmap.context.getSchematicFamily();

                // Wild land gets no tint — the map already shows what it looks like, and colouring
                // it would just dirty the view. Its ROADS still get drawn, though: a highway running
                // out through the countryside is the most useful line on the whole map, and skipping
                // the platmap outright dropped every one of them.
                String tooltip = tooltip(platmap, family);
                long[] at = { platX, platZ };
                if (family != SchematicFamily.NATURE) {
                    ServerPolygon district = district(dimension, platmap, family, platX, platZ, y, tooltip);
                    out.add(district);
                    covered.put(district.overlayId(), at);
                }
                if (Math.abs(dx) <= ROAD_RADIUS && Math.abs(dz) <= ROAD_RADIUS) {
                    ServerPolygon roads = roads(dimension, platmap, platX, platZ, y, tooltip);
                    if (roads != null) {
                        out.add(roads);
                        covered.put(roads.overlayId(), at);
                    }
                }
            }
        }
    }

    /**
     * The whole platmap as one tinted square. Deliberately <b>unnamed on the map</b>: JourneyMap
     * pulls a partly-off-screen polygon's label into view, so two districts stacked north-south drew
     * their names on the same screen row on top of each other ("Lowrise" over "Farm"), and since
     * neighbouring platmaps are nearly always different districts, that was most of them. The name
     * lives in the hover text instead — where the client plugin now shows the lot, schematic, shop
     * and interior with it, which is more than a label could say anyway.
     */
    private ServerPolygon district(ResourceKey<Level> dimension, PlatMap platmap, SchematicFamily family,
            int platX, int platZ, int y, String tooltip) {
        int x0 = platX * 16;
        int z0 = platZ * 16;
        int x1 = x0 + PlatMap.Width * 16;
        int z1 = z0 + PlatMap.Width * 16;
        int color = colorFor(family);
        OverlayShapeProps props = OverlayProps.everywhere(color, 0.12f, color, 1.0f, 0.55f, 900,
                UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX, null, tooltip);
        return new ServerPolygon("plan_district_" + platX + "_" + platZ, dimension,
                List.of(new OverlayPolygon(rect(x0, z0, x1, z1, y), null)), props);
    }

    /**
     * The platmap's roads, merged into as few rectangles as possible — a city block's worth of road
     * chunks in a row is one long strip, not ten squares. All of them ride in a single overlay.
     */
    private ServerPolygon roads(ResourceKey<Level> dimension, PlatMap platmap, int platX, int platZ, int y,
            String districtTooltip) {
        boolean[][] road = new boolean[PlatMap.Width][PlatMap.Width];
        boolean any = false;
        for (int x = 0; x < PlatMap.Width; x++) {
            for (int z = 0; z < PlatMap.Width; z++) {
                PlatLot lot = platmap.getLot(x, z);
                road[x][z] = lot != null
                        && (lot.style == PlatLot.LotStyle.ROAD || lot.style == PlatLot.LotStyle.ROUNDABOUT);
                any |= road[x][z];
            }
        }
        if (!any)
            return null;

        List<OverlayPolygon> shapes = new ArrayList<>();
        boolean[][] used = new boolean[PlatMap.Width][PlatMap.Width];
        for (int z = 0; z < PlatMap.Width; z++) {
            for (int x = 0; x < PlatMap.Width; x++) {
                if (!road[x][z] || used[x][z])
                    continue;
                // widest run east, then push it as far south as the same run holds
                int width = 0;
                while (x + width < PlatMap.Width && road[x + width][z] && !used[x + width][z])
                    width++;
                int height = 1;
                while (z + height < PlatMap.Width && rowFree(road, used, x, z + height, width))
                    height++;
                for (int ix = x; ix < x + width; ix++)
                    for (int iz = z; iz < z + height; iz++)
                        used[ix][iz] = true;
                shapes.add(new OverlayPolygon(rect((platX + x) * 16, (platZ + z) * 16,
                        (platX + x + width) * 16, (platZ + z + height) * 16, y), null));
            }
        }

        // Streets sit on top of their district, so their tooltip carries the district's line too —
        // otherwise pointing at a road hides what you were reading.
        OverlayShapeProps props = OverlayProps.everywhere(0x3A3A3A, 0.45f, 0x202020, 0.5f, 0.5f, 1000,
                UIState.FULLSCREEN_ZOOM_MIN, UIState.ZOOM_IN_MAX, null, "Streets · " + districtTooltip);
        return new ServerPolygon("plan_roads_" + platX + "_" + platZ, dimension, shapes, props);
    }

    private static boolean rowFree(boolean[][] road, boolean[][] used, int x, int z, int width) {
        for (int ix = x; ix < x + width; ix++)
            if (!road[ix][z] || used[ix][z])
                return false;
        return true;
    }

    /** A rectangle in block coordinates, clockwise. {@code x1}/{@code z1} are exclusive edges. */
    private static OverlayPoints rect(int x0, int z0, int x1, int z1, int y) {
        return new OverlayPoints(List.of(
                BlockPos.asLong(x0, y, z0),
                BlockPos.asLong(x1, y, z0),
                BlockPos.asLong(x1, y, z1),
                BlockPos.asLong(x0, y, z1)));
    }

    /**
     * District colours. Roughly a planner's zoning map: reds for the dense core, blues for civic,
     * yellow/brown for industry, green for parks and farmland.
     */
    private static int colorFor(SchematicFamily family) {
        return switch (family) {
            case HIGHRISE -> 0xD1495B;
            case MIDRISE -> 0xE08A3C;
            case LOWRISE -> 0xE3C567;
            case NEIGHBORHOOD -> 0xC9A227;
            case MUNICIPAL -> 0x4C8DBE;
            case INDUSTRIAL -> 0x8A6A4F;
            case CONSTRUCTION -> 0xB0A99F;
            case PARK -> 0x5FA45F;
            case FARM -> 0x9BBF5B;
            case ROUNDABOUT -> 0x9A6FB0;
            case ASTRAL -> 0x7B5FD1;
            case OUTLAND -> 0x6FA8A0;
            case NATURE -> 0x74A45C;
        };
    }

    /** The one line that describes a platmap, shared by its district tint and its streets. */
    private static String tooltip(PlatMap platmap, SchematicFamily family) {
        return title(family.name()) + " district · " + platmap.getNumberOfRoads() + " roads · "
                + Math.round(platmap.getNaturePercent() * 100) + "% open land";
    }

    /** {@code HIGHRISE} -> {@code Highrise}. */
    private static String title(String name) {
        return name.charAt(0) + name.substring(1).toLowerCase(Locale.ROOT);
    }

    private static CityWorldGenerator contextFor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator city)
            return city.getContext(level);
        return null;
    }
}
