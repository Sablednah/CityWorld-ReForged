package me.daddychurchill.CityWorld.integration.journeymap;

import journeymap.api.v2.client.IClientAPI;
import journeymap.api.v2.client.IClientPlugin;
import journeymap.api.v2.client.event.FullscreenDisplayEvent;
import journeymap.api.v2.client.event.FullscreenMapEvent;
import journeymap.api.v2.client.event.MappingEvent;
import journeymap.api.v2.client.event.RegistryEvent;
import journeymap.api.v2.common.JourneyMapPlugin;
import journeymap.api.v2.common.event.ClientEventRegistry;
import journeymap.api.v2.common.event.FullscreenEventRegistry;
import journeymap.api.v2.common.option.BooleanOption;
import journeymap.api.v2.common.option.IntegerOption;
import journeymap.api.v2.common.option.OptionCategory;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.api.MapMarkers;
import me.daddychurchill.CityWorld.client.CityPlanClient;
import me.daddychurchill.CityWorld.client.CityPlanHud;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;

/**
 * The client half of the JourneyMap integration: CityWorld's own controls inside JourneyMap's UI,
 * and what the plan says about whatever the mouse is over.
 *
 * <p>Three things the server side cannot do:
 * <ul>
 *   <li>a <b>City plan</b> switch in JourneyMap's options screen, sitting with the map's other
 *       layer toggles rather than behind a chat command;
 *   <li>the same toggle as a button on the fullscreen map's toolbar;
 *   <li><b>hover text</b>: move the mouse over any chunk and the info slot names what CityWorld
 *       planned there — district, lot kind, schematic, shop, interior — <em>including</em> chunks
 *       nobody has been to. The client asks the server (see {@code CityWorldNetwork}); it cannot
 *       know this on its own.
 * </ul>
 *
 * <p>Same rule as the server plugin: nothing outside this package may reference this class, and
 * JourneyMap instantiates it itself after finding the annotation.
 *
 * <p><b>New here?</b> Read this package's {@code package-info} first — the whole approach in one
 * place, including the two crashes this file cost before it settled.
 */
@JourneyMapPlugin(apiVersion = "2.0.0", dependencies = { CityWorldMod.MODID })
public class CityWorldJourneyMapClientPlugin implements IClientPlugin {

    /**
     * The toolbar button's icon — <b>CityWorld's own</b>, at
     * {@code assets/cityworld/textures/gui/city_plan.png}.
     *
     * <p>JourneyMap's example mod points addons at its theme icons
     * ({@code journeymap:/resources/assets/journeymap/theme/flat/icon/grid.png}), and that path does
     * not resolve in the shipped mod — the real asset is {@code assets/journeymap/theme/flat/icon/
     * grid.png}. The consequence is not a missing picture: the button renders a null texture, JourneyMap
     * throws inside {@code jm.fullscreen.render()} <em>every frame</em>, and closes the fullscreen map
     * to survive. Which looks exactly like "the map crashes when I press J". Shipping our own icon
     * means no dependency on another mod's internal asset layout, this version's or the next one's.
     */
    private static final Identifier ICON =
            Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "textures/gui/city_plan.png");

    private BooleanOption cityPlan;

    /**
     * How many plan overlays this client is willing to hold. It is an option because the cost is
     * this machine's, not the server's: retained overlays are already planned and never re-sent,
     * while the client walks its list of them every frame — so what is comfortable depends on the
     * machine, and the person at it is the one who can tell.
     */
    private IntegerOption planKeep;

    /** What we last told the server, so a change made in the options screen is noticed and sent. */
    private boolean lastSent = true;
    private int lastSentKeep = MapMarkers.DEFAULT_PLAN_BUDGET;

    /** The chunk the mouse is over on the fullscreen map, or {@code Long.MIN_VALUE} for none. */
    private volatile long hovered = Long.MIN_VALUE;

    @Override
    public String getModId() {
        return CityWorldMod.MODID;
    }

    @Override
    public void initialize(final IClientAPI jmClientApi) {
        // Every handler is wrapped: these run inside JourneyMap's own event bus, during client setup
        // and while the map screen is open, and an exception there takes the game down with it
        // rather than logging a complaint. The same courtesy the generator extends to map mods.
        ClientEventRegistry.OPTIONS_REGISTRY_EVENT.subscribe(CityWorldMod.MODID,
                event -> safely(() -> onOptionsRegistry(event)));
        ClientEventRegistry.INFO_SLOT_REGISTRY_EVENT.subscribe(CityWorldMod.MODID,
                event -> safely(() -> onInfoSlotRegistry(event)));
        ClientEventRegistry.MAPPING_EVENT.subscribe(CityWorldMod.MODID,
                event -> safely(() -> onMapping(event)));
        FullscreenEventRegistry.FULLSCREEN_MAP_MOVE_EVENT.subscribe(CityWorldMod.MODID,
                event -> safely(() -> onMouseMove(event)));
        FullscreenEventRegistry.ADDON_BUTTON_DISPLAY_EVENT.subscribe(CityWorldMod.MODID,
                event -> safely(() -> onAddonButtons(event)));
        CityWorldMod.LOGGER.info("JourneyMap client API found — CityWorld options, map button and hover info added");
    }

    /** Runs one JourneyMap callback; a failure is logged, never thrown back into JourneyMap. */
    private static void safely(Runnable work) {
        try {
            work.run();
        } catch (Throwable t) {
            CityWorldMod.LOGGER.error("CityWorld's JourneyMap client hook failed", t);
        }
    }

    /**
     * Creates the option. Constructing it registers it, which is why it happens here and not in a
     * field initialiser — JourneyMap wants them made while it is asking.
     *
     * <p><b>Do not read the option here.</b> JourneyMap binds an option to its stored config
     * <em>after</em> this event returns, so {@code get()} throws a {@link NullPointerException} until
     * then — and thrown from inside client setup that is a crash on the loading screen, not a
     * warning. It cost exactly that once. Everything reads through {@link #planOn()} instead.
     */
    private void onOptionsRegistry(RegistryEvent.OptionsRegistryEvent event) {
        OptionCategory category = new OptionCategory(CityWorldMod.MODID, "CityWorld",
                "The city plan CityWorld draws on the map");
        cityPlan = new BooleanOption(category, "cityPlan", "City plan (districts and streets)", true);
        planKeep = new IntegerOption(category, "planKeep", "Plan overlays kept (lower if the map slows)",
                MapMarkers.DEFAULT_PLAN_BUDGET, MapMarkers.MIN_PLAN_BUDGET, MapMarkers.MAX_PLAN_BUDGET);
    }

    /** The overlay budget, or the last one we sent while the option is unbound. Never throws. */
    private int planKeep() {
        if (planKeep == null)
            return lastSentKeep;
        try {
            return planKeep.get();
        } catch (RuntimeException notBoundYet) {
            return lastSentKeep;
        }
    }

    /** The option's value, or the last value we know of while it is unbound. Never throws. */
    private boolean planOn() {
        if (cityPlan == null)
            return lastSent;
        try {
            return cityPlan.get();
        } catch (RuntimeException notBoundYet) {
            return lastSent;
        }
    }

    /** Writes the option back, if JourneyMap has bound it. Never throws. */
    private void writeOption(boolean on) {
        if (cityPlan == null)
            return;
        try {
            cityPlan.set(on);
        } catch (RuntimeException notBoundYet) {
            // The toggle still reaches the server; the option catches up when it is next read.
        }
    }

    /**
     * The info slot: what is planned where the mouse is, or where the player is standing when the
     * fullscreen map is not being pointed at anything. Polled by JourneyMap on the interval given.
     */
    private void onInfoSlotRegistry(RegistryEvent.InfoSlotRegistryEvent event) {
        event.register(CityWorldMod.MODID, "CityWorld", 250, this::describeHovered);
    }

    private String describeHovered() {
        try {
            return hoveredText();
        } catch (Throwable t) {
            CityWorldMod.LOGGER.debug("CityWorld info slot failed", t);
            return "";
        }
    }

    private String hoveredText() {
        long at = hovered;
        int chunkX;
        int chunkZ;
        if (at != Long.MIN_VALUE) {
            chunkX = (int) at;
            chunkZ = (int) (at >> 32);
        } else {
            net.minecraft.client.player.LocalPlayer player = net.minecraft.client.Minecraft.getInstance().player;
            if (player == null)
                return "";
            chunkX = player.blockPosition().getX() >> 4;
            chunkZ = player.blockPosition().getZ() >> 4;
        }
        CityPlanClient.request(chunkX, chunkZ);
        String known = CityPlanClient.infoFor(chunkX, chunkZ);
        return known == null ? "" : known;
    }

    /** Fires as the mouse crosses the fullscreen map; remember which chunk it is over. */
    private void onMouseMove(FullscreenMapEvent.MouseMoveEvent event) {
        BlockPos at = event.getLocation();
        if (at == null) {
            hovered = Long.MIN_VALUE;
            CityPlanHud.clearHover();
            return;
        }
        int chunkX = at.getX() >> 4;
        int chunkZ = at.getZ() >> 4;
        hovered = (chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
        CityPlanHud.hover(chunkX, chunkZ);
        CityPlanClient.request(chunkX, chunkZ);
    }

    /** The toolbar toggle, next to JourneyMap's own layer buttons. */
    private void onAddonButtons(FullscreenDisplayEvent.AddonButtonDisplayEvent event) {
        event.getThemeButtonDisplay().addThemeToggleButton("City plan on", "City plan off", ICON,
                planOn(), button -> setCityPlan(!planOn()));
    }

    /**
     * Mapping starting or stopping is also the moment to notice an option changed in the options
     * screen — JourneyMap gives no per-option callback, and this fires on every world change and on
     * closing the settings, which is when a change matters.
     */
    private void onMapping(MappingEvent event) {
        if (event.getStage() == MappingEvent.Stage.MAPPING_STOPPED) {
            hovered = Long.MIN_VALUE;
            CityPlanHud.clearHover();
            CityPlanClient.forget();
            return;
        }
        syncIfChanged();
    }

    /** Flips the option and tells the server, from the toolbar button. */
    private void setCityPlan(boolean on) {
        writeOption(on);
        lastSent = on;
        lastSentKeep = planKeep();
        CityPlanClient.setCityPlan(on, lastSentKeep);
    }

    private void syncIfChanged() {
        if (cityPlan == null)
            return;
        boolean now = planOn();
        int keep = planKeep();
        if (now != lastSent || keep != lastSentKeep) {
            lastSent = now;
            lastSentKeep = keep;
            CityPlanClient.setCityPlan(now, keep);
        }
    }
}
