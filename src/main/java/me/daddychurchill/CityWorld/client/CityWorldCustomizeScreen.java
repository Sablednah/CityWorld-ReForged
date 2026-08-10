package me.daddychurchill.CityWorld.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.CityWorldSettings.SubSurfaceStyle;
import me.daddychurchill.CityWorld.Plugins.TreeProvider.TreeStyle;
import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.CycleButton;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

/**
 * The "Customize" screen for the CityWorld world type on the single-player create-world screen — the
 * modded counterpart of vanilla's Superflat/Single-Biome editors, reached via
 * {@link net.neoforged.neoforge.client.event.RegisterPresetEditorsEvent} (see {@link CityWorldClient}).
 *
 * <p>It edits the world <b>style</b> plus the value knobs of {@link CityWorldSettingsData}, laid out in
 * scrollable, headed sections (Features / Terrain / Spawns / Treasures / World). On <em>Done</em> it
 * hands back a {@link Result} — the style and an inline settings object — which the caller bakes into
 * the create-world screen's overworld generator (an inline holder, so the tuned world needs no
 * datapack). The word lists, mob bags and city-radius knobs are <em>not</em> exposed here — they are
 * datapack-authored (see {@code config/cityworld/settings-example/}); this screen carries whatever the
 * incoming settings held for them through untouched.
 *
 * <p>Extends {@link OptionsSubScreen} to reuse vanilla's scrollable options list (with section
 * headers) and standard title/footer layout. Odds-style knobs are cycled through a small ladder of
 * named chances ({@link Chance}) rather than a raw 0..1 slider; the datapack gives exact control.
 */
public class CityWorldCustomizeScreen extends OptionsSubScreen {

    private static final Component TITLE = Component.translatable("cityworld.customize.title");
    private static final int WIDTH = 150;
    private static final int HEIGHT = 20;

    /** The result of a Done: the chosen style and the edited settings. */
    public record Result(WorldStyle style, CityWorldSettingsData settings) {}

    private final Consumer<Result> onDone;

    // What the incoming world carried for the parts this screen does not edit — carried through as-is.
    private final CityWorldSettingsData.Radius radius;
    private final CityWorldSettingsData.Naming naming;
    private final CityWorldSettingsData.Mobs mobs;

    // Working state — mutated live by the widgets, read back in buildResult().
    private WorldStyle style;
    // Which settings the current style locks (forces) — those widgets are greyed out. Recomputed on change.
    private java.util.Set<String> lockedKeys;

    // features
    private boolean includeRoads, includeRoundabouts, includeSewers, includeCisterns, includeBasements,
            includeMines, includeBunkers, includeBuildings, includeHouses, includeFarms, includeMunicipalities,
            includeIndustrialSectors, includeAirborneStructures, includeBuildingInteriors, includeSchematics,
            includeNamedRoads;
    // terrain
    private boolean includeCaves, includeLavaFields, includeSeas, includeMountains, includeOres, includeBones,
            includeFires, includeAbovegroundFluids, includeUndergroundFluids, includeWorkingLights,
            includeDecayedRoads, includeDecayedBuildings, includeDecayedNature, includeOvergrowth, capVines,
            includeShops;
    private double oddsOfPristineBuilding, overgrowthIntensity; // carried through untouched (no picker yet)
    // decay group — carried through untouched (the world style sets these; datapack tunes them, no picker yet)
    private double buildingDecayIntensity, roadDecayIntensity, oddsOfDecayFire, oddsOfPristineRoad;
    // spawns
    private Chance spawnBeings, spawnBaddies, spawnAnimals, spawnVagrants;
    private boolean nameVillagers, showVillagersNames;
    // treasures
    private boolean treasuresInMines, spawnersInMines, treasuresInBunkers, spawnersInBunkers, treasuresInSewers,
            spawnersInSewers, treasuresInBuildings;
    private Chance oddsOfTreasureInMines, oddsOfTreasureInBunkers, oddsOfTreasureInSewers,
            oddsOfTreasureInBuildings, oddsOfAlcoveInMines;
    // world
    private TreeStyle treeStyle;
    private Chance spawnTrees;
    private SubSurfaceStyle subSurfaceStyle;
    private Chance ruralnessLevel;
    private int maxBuildingFloors; // carried through untouched for now (no picker yet)

    public CityWorldCustomizeScreen(Screen parent, WorldStyle initialStyle, CityWorldSettingsData initial,
            Consumer<Result> onDone) {
        super(parent, Minecraft.getInstance().options, TITLE);
        this.onDone = onDone;
        this.style = initialStyle;
        this.lockedKeys = me.daddychurchill.CityWorld.CityWorldSettings.styleLocks(initialStyle);

        this.radius = initial.radius();
        this.naming = initial.naming();
        this.mobs = initial.mobs();

        loadFrom(initial);
    }

    /** Load every editable field from a settings object — on open, and again on a style change (reset). */
    private void loadFrom(CityWorldSettingsData data) {
        CityWorldSettingsData.Features f = data.features();
        includeRoads = f.includeRoads();
        includeRoundabouts = f.includeRoundabouts();
        includeSewers = f.includeSewers();
        includeCisterns = f.includeCisterns();
        includeBasements = f.includeBasements();
        includeMines = f.includeMines();
        includeBunkers = f.includeBunkers();
        includeBuildings = f.includeBuildings();
        includeHouses = f.includeHouses();
        includeFarms = f.includeFarms();
        includeMunicipalities = f.includeMunicipalities();
        includeIndustrialSectors = f.includeIndustrialSectors();
        includeAirborneStructures = f.includeAirborneStructures();
        includeBuildingInteriors = f.includeBuildingInteriors();
        includeSchematics = f.includeSchematics();
        includeNamedRoads = f.includeNamedRoads();

        CityWorldSettingsData.Terrain t = data.terrain();
        includeCaves = t.includeCaves();
        includeLavaFields = t.includeLavaFields();
        includeSeas = t.includeSeas();
        includeMountains = t.includeMountains();
        includeOres = t.includeOres();
        includeBones = t.includeBones();
        includeFires = t.includeFires();
        includeAbovegroundFluids = t.includeAbovegroundFluids();
        includeUndergroundFluids = t.includeUndergroundFluids();
        includeWorkingLights = t.includeWorkingLights();
        includeDecayedRoads = t.includeDecayedRoads();
        includeDecayedBuildings = t.includeDecayedBuildings();
        includeDecayedNature = t.includeDecayedNature();
        oddsOfPristineBuilding = t.oddsOfPristineBuilding();
        CityWorldSettingsData.Overgrowth og = data.overgrowth();
        includeOvergrowth = og.enabled();
        overgrowthIntensity = og.intensity();
        capVines = og.capVines();
        includeShops = data.shops().enabled();
        CityWorldSettingsData.Decay dk = data.decay();
        buildingDecayIntensity = dk.buildingIntensity();
        roadDecayIntensity = dk.roadIntensity();
        oddsOfDecayFire = dk.oddsOfDecayFire();
        oddsOfPristineRoad = dk.oddsOfPristineRoad();

        CityWorldSettingsData.Spawns s = data.spawns();
        spawnBeings = Chance.nearest(s.spawnBeings());
        spawnBaddies = Chance.nearest(s.spawnBaddies());
        spawnAnimals = Chance.nearest(s.spawnAnimals());
        spawnVagrants = Chance.nearest(s.spawnVagrants());
        nameVillagers = s.nameVillagers();
        showVillagersNames = s.showVillagersNames();

        CityWorldSettingsData.Treasures r = data.treasures();
        treasuresInMines = r.treasuresInMines();
        spawnersInMines = r.spawnersInMines();
        treasuresInBunkers = r.treasuresInBunkers();
        spawnersInBunkers = r.spawnersInBunkers();
        treasuresInSewers = r.treasuresInSewers();
        spawnersInSewers = r.spawnersInSewers();
        treasuresInBuildings = r.treasuresInBuildings();
        oddsOfTreasureInMines = Chance.nearest(r.oddsOfTreasureInMines());
        oddsOfTreasureInBunkers = Chance.nearest(r.oddsOfTreasureInBunkers());
        oddsOfTreasureInSewers = Chance.nearest(r.oddsOfTreasureInSewers());
        oddsOfTreasureInBuildings = Chance.nearest(r.oddsOfTreasureInBuildings());
        oddsOfAlcoveInMines = Chance.nearest(r.oddsOfAlcoveInMines());

        CityWorldSettingsData.World w = data.world();
        treeStyle = w.treeStyle();
        spawnTrees = Chance.nearest(w.spawnTrees());
        subSurfaceStyle = w.subSurfaceStyle();
        ruralnessLevel = Chance.nearest(w.ruralnessLevel());
        maxBuildingFloors = w.maxBuildingFloors();
    }

    @Override
    protected void addOptions() {
        List<AbstractWidget> row = new ArrayList<>();

        this.list.addHeader(Component.literal("World style"));
        addRow(cycle("Style", WorldStyle.values(), style, CityWorldCustomizeScreen::styleLabel,
                this::onStyleChanged), null);

        this.list.addHeader(Component.literal("Features"));
        pair(row, onOff("Roads", includeRoads, v -> includeRoads = v));
        pair(row, onOff("Roundabouts", includeRoundabouts, v -> includeRoundabouts = v));
        pair(row, onOff("Sewers", includeSewers, v -> includeSewers = v));
        pair(row, onOff("Cisterns", includeCisterns, v -> includeCisterns = v));
        pair(row, onOff("Basements", includeBasements, v -> includeBasements = v));
        pair(row, onOff("Mines", includeMines, v -> includeMines = v));
        pair(row, onOff("Bunkers", includeBunkers, v -> includeBunkers = v));
        pair(row, onOff("Buildings", includeBuildings, v -> includeBuildings = v));
        pair(row, onOff("Houses", includeHouses, v -> includeHouses = v));
        pair(row, onOff("Farms", includeFarms, v -> includeFarms = v));
        pair(row, onOff("Municipalities", includeMunicipalities, v -> includeMunicipalities = v));
        pair(row, onOff("Industrial sectors", includeIndustrialSectors, v -> includeIndustrialSectors = v));
        pair(row, onOff("Airborne structures", includeAirborneStructures, v -> includeAirborneStructures = v));
        pair(row, onOff("Building interiors", includeBuildingInteriors, v -> includeBuildingInteriors = v));
        pair(row, onOff("Schematics", includeSchematics, v -> includeSchematics = v));
        pair(row, onOff("Named roads", includeNamedRoads, v -> includeNamedRoads = v));
        flush(row);

        this.list.addHeader(Component.literal("Terrain"));
        pair(row, onOff("Caves", includeCaves, v -> includeCaves = v));
        pair(row, onOff("Lava fields", includeLavaFields, v -> includeLavaFields = v));
        pair(row, onOff("Seas", includeSeas, v -> includeSeas = v));
        pair(row, onOff("Mountains", includeMountains, v -> includeMountains = v));
        pair(row, onOff("Ores", includeOres, v -> includeOres = v));
        pair(row, onOff("Bones / fossils", includeBones, v -> includeBones = v));
        pair(row, onOff("Fires", includeFires, v -> includeFires = v));
        pair(row, onOff("Aboveground fluids", includeAbovegroundFluids, v -> includeAbovegroundFluids = v));
        pair(row, onOff("Underground fluids", includeUndergroundFluids, v -> includeUndergroundFluids = v));
        pair(row, onOff("Working lights", includeWorkingLights, v -> includeWorkingLights = v));
        pair(row, onOff("Decayed roads", includeDecayedRoads, v -> includeDecayedRoads = v));
        pair(row, onOff("Decayed buildings", includeDecayedBuildings, v -> includeDecayedBuildings = v));
        pair(row, onOff("Decayed nature", includeDecayedNature, v -> includeDecayedNature = v));
        pair(row, onOff("Overgrowth", includeOvergrowth, v -> includeOvergrowth = v));
        pair(row, onOff("Cap vines", capVines, v -> capVines = v));
        pair(row, onOff("Shops", includeShops, v -> includeShops = v));
        flush(row);

        this.list.addHeader(Component.literal("Spawns"));
        pair(row, chance("Beings", spawnBeings, v -> spawnBeings = v));
        pair(row, chance("Baddies", spawnBaddies, v -> spawnBaddies = v));
        pair(row, chance("Animals", spawnAnimals, v -> spawnAnimals = v));
        pair(row, chance("Vagrants", spawnVagrants, v -> spawnVagrants = v));
        pair(row, onOff("Name villagers", nameVillagers, v -> nameVillagers = v));
        pair(row, onOff("Show villager names", showVillagersNames, v -> showVillagersNames = v));
        flush(row);

        this.list.addHeader(Component.literal("Treasures"));
        pair(row, onOff("Chests in mines", treasuresInMines, v -> treasuresInMines = v));
        pair(row, onOff("Spawners in mines", spawnersInMines, v -> spawnersInMines = v));
        pair(row, onOff("Chests in bunkers", treasuresInBunkers, v -> treasuresInBunkers = v));
        pair(row, onOff("Spawners in bunkers", spawnersInBunkers, v -> spawnersInBunkers = v));
        pair(row, onOff("Chests in sewers", treasuresInSewers, v -> treasuresInSewers = v));
        pair(row, onOff("Spawners in sewers", spawnersInSewers, v -> spawnersInSewers = v));
        pair(row, onOff("Chests in buildings", treasuresInBuildings, v -> treasuresInBuildings = v));
        pair(row, chance("Chest odds: mines", oddsOfTreasureInMines, v -> oddsOfTreasureInMines = v));
        pair(row, chance("Chest odds: bunkers", oddsOfTreasureInBunkers, v -> oddsOfTreasureInBunkers = v));
        pair(row, chance("Chest odds: sewers", oddsOfTreasureInSewers, v -> oddsOfTreasureInSewers = v));
        pair(row, chance("Chest odds: buildings", oddsOfTreasureInBuildings, v -> oddsOfTreasureInBuildings = v));
        pair(row, chance("Mine alcove odds", oddsOfAlcoveInMines, v -> oddsOfAlcoveInMines = v));
        flush(row);

        this.list.addHeader(Component.literal("World"));
        pair(row, cycle("Tree style", TreeStyle.values(), treeStyle, e -> Component.literal(nice(e.name())),
                v -> treeStyle = v));
        pair(row, chance("Tree density", spawnTrees, v -> spawnTrees = v));
        pair(row, cycle("Under-floating fill", SubSurfaceStyle.values(), subSurfaceStyle,
                e -> Component.literal(nice(e.name())), v -> subSurfaceStyle = v));
        pair(row, chance("Ruralness", ruralnessLevel, v -> ruralnessLevel = v));
        flush(row);
    }

    @Override
    protected void addFooter() {
        LinearLayout footer = this.layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> {
            this.onDone.accept(buildResult());
            this.onClose();
        }).width(150).build());
        footer.addChild(Button.builder(CommonComponents.GUI_CANCEL, b -> this.onClose()).width(150).build());
    }

    private Result buildResult() {
        CityWorldSettingsData.Features features = new CityWorldSettingsData.Features(
                includeRoads, includeRoundabouts, includeSewers, includeCisterns, includeBasements, includeMines,
                includeBunkers, includeBuildings, includeHouses, includeFarms, includeMunicipalities,
                includeIndustrialSectors, includeAirborneStructures, includeBuildingInteriors, includeSchematics,
                includeNamedRoads);
        CityWorldSettingsData.Terrain terrain = new CityWorldSettingsData.Terrain(
                includeCaves, includeLavaFields, includeSeas, includeMountains, includeOres, includeBones,
                includeFires, includeAbovegroundFluids, includeUndergroundFluids, includeWorkingLights,
                includeDecayedRoads, includeDecayedBuildings, includeDecayedNature, oddsOfPristineBuilding);
        CityWorldSettingsData.Spawns spawns = new CityWorldSettingsData.Spawns(
                spawnBeings.value, spawnBaddies.value, spawnAnimals.value, spawnVagrants.value, nameVillagers,
                showVillagersNames);
        CityWorldSettingsData.Treasures treasures = new CityWorldSettingsData.Treasures(
                treasuresInMines, spawnersInMines, treasuresInBunkers, spawnersInBunkers, treasuresInSewers,
                spawnersInSewers, treasuresInBuildings, oddsOfTreasureInMines.value, oddsOfTreasureInBunkers.value,
                oddsOfTreasureInSewers.value, oddsOfTreasureInBuildings.value, oddsOfAlcoveInMines.value);
        CityWorldSettingsData.World world = new CityWorldSettingsData.World(
                treeStyle, spawnTrees.value, subSurfaceStyle, ruralnessLevel.value, maxBuildingFloors);
        CityWorldSettingsData.Overgrowth overgrowth = new CityWorldSettingsData.Overgrowth(
                includeOvergrowth, overgrowthIntensity, capVines);
        CityWorldSettingsData.Shops shops = new CityWorldSettingsData.Shops(includeShops);
        CityWorldSettingsData.Decay decay = new CityWorldSettingsData.Decay(
                buildingDecayIntensity, roadDecayIntensity, oddsOfDecayFire, oddsOfPristineRoad);
        CityWorldSettingsData data = new CityWorldSettingsData(
                features, terrain, spawns, treasures, world, radius, naming, mobs, overgrowth, shops, decay);
        return new Result(style, data);
    }

    /**
     * The Style picker changed: reset every option to the new style's defaults, recompute which settings that
     * style locks, and rebuild the widgets so the values and the greyed-out (locked) ones both refresh.
     */
    private void onStyleChanged(WorldStyle newStyle) {
        if (newStyle == style || this.minecraft == null)
            return;
        // Reopen the screen fresh for the new style rather than rebuilding in place: OptionsSubScreen keeps
        // its scrolling list in a layout that rebuildWidgets() doesn't clear, so an in-place rebuild stacked
        // a ghost list behind the options and jammed the style cycle on a stale button. A fresh screen loads
        // the new style's defaults and recomputes the locks cleanly.
        this.minecraft.setScreen(new CityWorldCustomizeScreen(this.lastScreen, newStyle,
                me.daddychurchill.CityWorld.CityWorldSettings.styleDefaults(newStyle), this.onDone));
    }

    // ---- widget helpers ----------------------------------------------------------------------

    private CycleButton<Boolean> onOff(String label, boolean init, Consumer<Boolean> setter) {
        CycleButton<Boolean> button = CycleButton.onOffBuilder(init)
                .create(0, 0, WIDTH, HEIGHT, Component.literal(label), (b, v) -> setter.accept(v));
        greyIfLocked(button, label);
        return button;
    }

    private CycleButton<Chance> chance(String label, Chance init, Consumer<Chance> setter) {
        CycleButton<Chance> button = CycleButton.<Chance>builder(c -> Component.literal(c.label), init)
                .withValues(Chance.values())
                .create(0, 0, WIDTH, HEIGHT, Component.literal(label), (b, v) -> setter.accept(v));
        greyIfLocked(button, label);
        return button;
    }

    /** Disable + tooltip a widget whose setting the current world style locks, so it reads as "you can't
     *  change this here — the style forces it". */
    private void greyIfLocked(AbstractWidget w, String label) {
        if (lockedKeys.contains(label)) {
            w.active = false;
            w.setTooltip(net.minecraft.client.gui.components.Tooltip
                    .create(Component.literal("Locked by the " + styleLabel(style).getString() + " style")));
        }
    }

    private static <T> CycleButton<T> cycle(String label, T[] values, T init,
            java.util.function.Function<T, Component> render, Consumer<T> setter) {
        return CycleButton.<T>builder(render, init).withValues(values)
                .create(0, 0, WIDTH, HEIGHT, Component.literal(label), (b, v) -> setter.accept(v));
    }

    /** Buffers widgets two-to-a-row, flushing a full pair to the list. */
    private void pair(List<AbstractWidget> row, AbstractWidget w) {
        row.add(w);
        if (row.size() == 2)
            flush(row);
    }

    private void flush(List<AbstractWidget> row) {
        if (row.isEmpty())
            return;
        if (row.size() == 1)
            addRow(row.get(0), null);
        else
            addRow(row.get(0), row.get(1));
        row.clear();
    }

    private void addRow(AbstractWidget a, AbstractWidget b) {
        this.list.addSmall(a, b);
    }

    private static Component styleLabel(WorldStyle s) {
        return Component.translatable("cityworld.style." + s.name().toLowerCase(Locale.ROOT));
    }

    private static String nice(String enumName) {
        String lower = enumName.toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    /**
     * A small ladder of named chances for the odds knobs — friendlier than a raw 0..1 slider and it
     * covers every default value exactly. The datapack gives precise control for anything between.
     */
    private enum Chance {
        NEVER("Never", 0.0),
        VERY_RARE("Very rare", 1.0 / 21.0),
        RARE("Rare", 1.0 / 8.0),
        UNLIKELY("Unlikely", 1.0 / 5.0),
        SOMETIMES("Sometimes", 1.0 / 3.0),
        HALF("Half", 1.0 / 2.0),
        LIKELY("Likely", 2.0 / 3.0),
        MOSTLY("Mostly", 4.0 / 5.0),
        ALWAYS("Always", 1.0);

        final String label;
        final double value;

        Chance(String label, double value) {
            this.label = label;
            this.value = value;
        }

        /** The ladder rung closest to a raw odds value (for seeding from datapack/default values). */
        static Chance nearest(double v) {
            Chance best = HALF;
            double bestDelta = Double.MAX_VALUE;
            for (Chance c : values()) {
                double delta = Math.abs(c.value - v);
                if (delta < bestDelta) {
                    bestDelta = delta;
                    best = c;
                }
            }
            return best;
        }
    }
}
