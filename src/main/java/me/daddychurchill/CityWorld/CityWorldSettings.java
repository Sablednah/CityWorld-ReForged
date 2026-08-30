package me.daddychurchill.CityWorld;

import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle;
import me.daddychurchill.CityWorld.Plugins.TreeProvider;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData;

/**
 * The runtime world settings — upstream's {@code CityWorldSettings}, the ~100 knobs the generation
 * brain branches on (which structures, which terrain, spawn odds, treasure odds, city radii).
 *
 * <p><b>P7: these now come from a datapack.</b> CityWorld's settings were <em>per-world</em>, while
 * a NeoForge {@code ModConfigSpec} is per-instance (PORTING.md top risk #4). The port resolves the
 * tension with a datapack registry: {@link CityWorldSettingsData} entries live under
 * {@code data/<ns>/cityworld/world_settings/}, the generator references one by holder, and
 * {@link #applyData} copies it onto the fields below. A server op can therefore make each world
 * different. The field initializers here remain as the compiled fallback that
 * {@link CityWorldSettingsData#DEFAULT} mirrors, and cover the few runtime-only fields no datapack
 * entry carries (e.g. {@link #darkEnvironment}, set by the alien/nether styles).
 *
 * <p>The villager-name / street-name / mob lists (upstream's {@code VillagerGivenNames},
 * {@code Entities_For_*}, …) are folded in too, as the datapack's {@code naming} and {@code mobs}
 * groups: each list replaces the built-in bag, or adds to it with {@code "append": true}.
 */
public class CityWorldSettings {

    // Compiled defaults — the fallback CityWorldSettingsData.DEFAULT mirrors; applyData overwrites
    // every field a datapack entry carries.
    public boolean includeRoads = true;
    public boolean includeBuildings = true;
    public boolean includeFarms = true;
    public boolean includeMunicipalities = true;

    /** Back on at upstream's default now that the factory/warehouse/storage family is ported. */
    public boolean includeIndustrialSectors = true;
    public boolean includeCaves = true;
    /** MODERN/APOCALYPSE default: winding "noodle" cave tunnels (that wander + branch, vanilla-like) instead
     *  of the classic noise blobs. A free toggle — any style can opt in for better caves; never style-locked. */
    public boolean windingCaves = false;
    public boolean includeLavaFields = true;
    public boolean includeSeas = true;
    public boolean includeMountains = true;
    public boolean includeAbovegroundFluids = true;
    public boolean includeUndergroundFluids = true;
    public boolean includeDecayedNature = false;
    public boolean includeMines = true;
    public boolean includeBones = true;
    public boolean includeOres = true;
    public boolean includeWorkingLights = true;
    /** Lit campfires and fire pits. */
    public boolean includeFires = true;
    public boolean treasuresInMines = true;
    public boolean spawnersInMines = true;
    public double oddsOfTreasureInMines = Odds.oddsLikely;
    public boolean treasuresInBunkers = true;
    public boolean spawnersInBunkers = true;
    public double oddsOfTreasureInBunkers = Odds.oddsLikely;
    public double oddsOfAlcoveInMines = Odds.oddsLikely;
    public boolean includeSewers = true;
    public boolean treasuresInSewers = true;
    public boolean spawnersInSewers = true;
    public double oddsOfTreasureInSewers = Odds.oddsLikely;
    public boolean includeNamedRoads = true;
    public boolean includeDecayedRoads = false;
    public boolean includeDecayedBuildings = false;
    /** Burning debris (netherrack + fire) sprinkled by the demolition pass. Separate from {@link #includeFires}
     *  (lit campfires/fire pits) so a style can keep campfires but skip the burning ruins — APOCALYPSE does. */
    public boolean includeDecayedFires = true;

    /**
     * When decay is on, the chance a given building/schematic is spared and stays pristine — so a
     * ruined world still hides the odd intact find. Tiny by default; a schematic's {@code .yml} can
     * override it per-building via {@code PristineChance}. Rolled once per building (from its origin),
     * so a multi-chunk building agrees with itself.
     */
    public double oddsOfPristineBuilding = CityWorldSettingsData.Terrain.DEFAULT_ODDS_OF_PRISTINE;

    /**
     * Fine demolition control (the {@code decay} settings group). {@link #buildingDecayIntensity} /
     * {@link #roadDecayIntensity} scale HOW ruined a decayed building / road is — the number and size of
     * the collapse holes (1.0 = baseline, &lt;1 lighter, &gt;1 heavier); each ruined style sets its own
     * (APOCALYPSE a slow gentle decay, DESTROYED heavy nuke/invasion damage). {@link #oddsOfDecayFire} is
     * the fraction of collapse rubble that catches fire (only bites when {@link #includeDecayedFires}).
     * {@link #oddsOfPristineRoad} spares a fraction of roads intact — the road counterpart of
     * {@link #oddsOfPristineBuilding}.
     */
    public double buildingDecayIntensity = CityWorldSettingsData.Decay.DEFAULT_INTENSITY;
    public double roadDecayIntensity = CityWorldSettingsData.Decay.DEFAULT_INTENSITY;
    public double oddsOfDecayFire = CityWorldSettingsData.Decay.DEFAULT_ODDS_OF_DECAY_FIRE;
    public double oddsOfPristineRoad = 0.0;

    /**
     * Whether the bundled classic schematics (the old zarp catalog) may be dropped into generated
     * cities. Upstream only placed schematics when a WorldEdit-loaded folder existed, so it was
     * effectively off for most players; here the catalog always ships, so this defaults off and is
     * an opt-in. Independent of {@code /cityschem}, which pastes on demand regardless.
     */
    public boolean includeSchematics = false;

    /**
     * Let nature reclaim the built world: a post-decoration pass drapes buildings and roads in moss,
     * vines, leaf litter, azalea and small reclaim trees. Runs after any decay (so the greenery isn't
     * itself chewed up), and works with or without decay on — an "overgrown" look in its own right.
     */
    public boolean includeOvergrowth = false;

    /** Overgrowth density multiplier: 1.0 = tuned default, higher = more and longer vines/plants. */
    public double overgrowthIntensity = CityWorldSettingsData.Overgrowth.DEFAULT_INTENSITY;

    /**
     * The underground dials — structure carve halo, cave-patch surface margin, and per-biome patch
     * shaping. Carried through whole rather than flattened into fields: {@code patches} is a list, and
     * the two consumers ({@code CaveRegions} and the chunk generator's carve) want the group as a unit.
     */
    public CityWorldSettingsData.Caves caves = CityWorldSettingsData.Caves.DEFAULT;

    /**
     * Let installed TerraBlender biome mods (Biomes O' Plenty and most others) contribute biomes.
     *
     * <p><b>On by default, deliberately.</b> Installing a biome mod is itself the statement of intent —
     * a player who added Biomes O' Plenty expects to see its biomes, and having to find a switch to get
     * them would read as CityWorld being broken. Turning them <em>off</em> is the deliberate act. It is
     * also the consistent choice: BoP's blocks already appear on their own through the palette tags, so
     * gating its biomes behind a toggle was the odd one out.
     *
     * <p>Costs nothing when no such mod is installed — the bridge simply finds no regions.
     */
    public boolean useModdedBiomes = true;

    /**
     * On a MODERN/APOCALYPSE <em>nature</em> lot, run vanilla's own biome decoration after CityWorld's —
     * so wild land grows biome-appropriate vanilla trees, flowers, coral and sugar cane on top of
     * CityWorld's cover.
     *
     * <p><b>Both passes run, and that is the point.</b> The doubling was a deliberate trial (the owner
     * looked and kept it), but it means wild land is denser than either pass alone would make it. Turn
     * this off to let CityWorld's cover be the only decorator. City, road and structure lots are never
     * touched by it either way — vanilla decoration would carve lakes and trees through a build.
     */
    public CityWorldSettingsData.WildDecoration wildDecoration = CityWorldSettingsData.WildDecoration.BOTH;

    /** How far the temperature field leans warm; see {@code World.DEFAULT_CLIMATE_WARMTH}. */
    public double climateWarmth = CityWorldSettingsData.World.DEFAULT_CLIMATE_WARMTH;

    /** Biome region size multiplier; see {@code World.DEFAULT_BIOME_SCALE}. */
    public double biomeScale = CityWorldSettingsData.World.DEFAULT_BIOME_SCALE;

    /** Whether vanilla's biome features run on wild land. */
    public boolean vanillaDecoratesWild() {
        return wildDecoration != CityWorldSettingsData.WildDecoration.CITYWORLD;
    }

    /** Whether CityWorld's own surface pass runs on wild land. */
    public boolean cityworldDecoratesWild() {
        return wildDecoration != CityWorldSettingsData.WildDecoration.VANILLA;
    }
    /** Cap each outer wall-vine string with a glow lichen so live vine growth can't extend it. */
    public boolean capVines = false;

    /**
     * Theme classified shops with a villager job-site block (cartography table = map seller, fletching
     * table = fletcher, …) dropped on the ground floor, so a store reads as its trade. MODERN dressing;
     * off in CLASSIC. The shop <em>classification</em> (api / {@code /cityinfo}) is always computed —
     * this only governs block placement.
     */
    public boolean includeShops = false;

    /** Who turns up, and how often. {@code SpawnProvider} branches on all of these. */
    public double spawnBeings = Odds.oddsLikely;
    public double spawnBaddies = Odds.oddsPrettyUnlikely;
    public double spawnAnimals = Odds.oddsVeryLikely;
    public double spawnVagrants = Odds.oddsSomewhatUnlikely;
    public boolean nameVillagers = true;
    public boolean showVillagersNames = true;
    public boolean includeBuildingInteriors = true;
    public boolean includeHouses = true;
    /** Buried bunkers under the midlands and highlands, planned by NatureContext. */
    public boolean includeBunkers = true;
    /** Balloons, blimps and saucers over the fields (see {@code StructureInAirProvider}). */
    public boolean includeAirborneStructures = true;

    /** Which family of trees a world grows. Only NORMAL is ported; SPOOKY/CRYSTAL grow as NORMAL. */
    public TreeProvider.TreeStyle treeStyle = TreeProvider.TreeStyle.NORMAL;
    public double spawnTrees = Odds.oddsLikely;
    /** Set by the alien/nether styles; makes ground cover sparser. */
    public boolean darkEnvironment = false;
    public boolean includeBasements = true;
    public boolean includeCisterns = true;
    public boolean treasuresInBuildings = true;
    public double oddsOfTreasureInBuildings = Odds.oddsLikely;

    /** How rural a world skews; folded into {@code PlatMap.getNaturePercent}. */
    public double ruralnessLevel = 0.0;

    /**
     * Tallest a building may rise, in floors above street level (was the hardcoded
     * {@code absoluteAbsoluteMaximumFloorsAbove}). 20 is CLASSIC's 1.14 look; MODERN ships taller.
     * {@code DataContext} raises the building Y ceiling to fit this, clamped to the world ceiling.
     */
    public int maxBuildingFloors = CityWorldSettingsData.World.DEFAULT_MAX_BUILDING_FLOORS;

    /**
     * Announce landmarks in chat as they generate ("Castle placed near x, z") — upstream's
     * {@code broadcastSpecialPlaces}. Off by default: chunks generate wherever players explore, so on a
     * busy server this is chatty. Announcements go to everyone in the world the landmark generated in.
     */
    public boolean broadcastSpecialPlaces = false;

    /** Which landmark kinds may chat-announce — see {@link CityWorldSettingsData.World#DEFAULT_ANNOUNCED}. */
    public java.util.Set<String> announcedLandmarks = java.util.Set.copyOf(CityWorldSettingsData.World.DEFAULT_ANNOUNCED);

    /** Back on at upstream's default now that {@code RoundaboutCenterLot} is ported (wave 2b). */
    public boolean includeRoundabouts = true;

    /**
     * What fills the space under a FLOATING world's floating land. Upstream defined this on
     * {@code SurfaceProvider_Floating}; kept here with the settings it belongs to. Only the FLOATING
     * shape/surface providers read it (P8 step 4); every other style just sets it to {@code NONE} in
     * {@link #validateSettingsAgainstWorldStyle}.
     */
    public enum SubSurfaceStyle {
        NONE, LAND, CLOUD, LAVA
    }

    public SubSurfaceStyle subSurfaceStyle = SubSurfaceStyle.LAND;

    // --- per-world word lists and mob bags (empty = keep the provider's compiled defaults) -------
    // Populated by applyData from the datapack's [naming]/[mobs] groups. OdonymProvider_Normal reads
    // the nine name lists; SpawnProvider reads the ten mob bags. Empty means "not overridden", which
    // every reader treats as "use the hardcoded list" — upstream's getNames(...) fallback.

    public java.util.List<String> villagerGivenNames = java.util.List.of();
    public java.util.List<String> villagerSurnames = java.util.List.of();
    public java.util.List<String> streetTerms = java.util.List.of();
    public java.util.List<String> streetPrefixes = java.util.List.of();
    public java.util.List<String> streetStarts = java.util.List.of();
    public java.util.List<String> streetEnds = java.util.List.of();
    public java.util.List<String> streetSuffixes = java.util.List.of();
    public java.util.List<String> fossilPrefixes = java.util.List.of();
    public java.util.List<String> fossilSuffixes = java.util.List.of();
    /** Role-themed worker surnames as {@code "profession:Surname"} entries (e.g. {@code "fletcher:Bowman"}). */
    public java.util.List<String> professionNames = java.util.List.of();
    /** When true, the name lists above are appended to the compiled defaults instead of replacing them. */
    public boolean namesAppend = false;

    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobGoodies = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobBaddies = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobAnimals = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobSeaAnimals = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobVagrants = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobSewers = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobMine = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobBunker = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobWaterPit = java.util.List.of();
    public java.util.List<me.daddychurchill.CityWorld.compat.EntityType> mobLavaPit = java.util.List.of();
    /** When true, the mob bags above are appended to the compiled bags instead of replacing them. */
    public boolean mobsAppend = false;

    // --- city-placement radii (upstream's [radius] settings) -----------------------------------
    // The SPARSE style and the datapack [radius] group move these off their "everywhere" defaults.
    // inCityRange/inRoadRange/inConstructRange below gate where cities, roads and constructs appear.

    /** 1875000 — the chunk-radius ceiling for the modern world format (30,000,000 blocks / 16). */
    private static final int maxRadius = 30000000 / AbstractBlocks.sectionBlockWidth;

    private int centerPointOfChunkRadiusX = 0;
    private int centerPointOfChunkRadiusZ = 0;
    private int constructChunkRadius = maxRadius;
    private boolean checkConstructRange = false;
    private int roadChunkRadius = maxRadius;
    private boolean checkRoadRange = false;
    private int cityChunkRadius = maxRadius;
    private boolean checkCityRange = false;
    private boolean buildOutsideRadius = false;
    private int minInbetweenChunkDistanceOfCities = 0;
    private boolean checkMinInbetweenChunkDistanceOfCities = false;

    public CityWorldSettings() {
        this(WorldStyle.CLASSIC, java.util.Optional.empty(), CityWorldSettingsData.DEFAULT);
    }

    /**
     * @param worldStyle    the world style, which overrides a slice of the settings via
     *                      {@link #validateSettingsAgainstWorldStyle} (roads/mines/decay toggles and,
     *                      for {@code SPARSE}, the city-placement radii).
     * @param decayOverride a per-dimension decay override (from the generator's {@code "decayed"}
     *                      JSON field). When present it forces {@link #includeDecayedBuildings} and
     *                      {@link #includeDecayedRoads} on or off for this dimension, winning over
     *                      the datapack settings — so two same-seed dimensions can be the same city
     *                      intact and in ruins. Empty means "follow the settings". Deliberately does
     *                      <em>not</em> touch {@link #includeDecayedNature} (that drains the seas and
     *                      deserts the world — a whole-world mood, kept purely settings-controlled).
     * @param data          the per-world settings, resolved from the {@code cityworld:world_settings}
     *                      datapack registry (or {@link CityWorldSettingsData#DEFAULT}). Copied onto
     *                      the fields first, so the style and decay overrides below still win.
     */
    public CityWorldSettings(WorldStyle worldStyle, java.util.Optional<Boolean> decayOverride,
            CityWorldSettingsData data) {
        // The datapack settings are the source of truth (P7): copy them onto the fields, replacing
        // the compiled defaults above. The field initializers remain as the compiled fallback that
        // CityWorldSettingsData.DEFAULT mirrors, and cover the handful of runtime-only fields (e.g.
        // darkEnvironment) that no datapack entry carries.
        applyData(data);

        // A per-dimension override wins over the datapack for the building/road ruin.
        decayOverride.ifPresent(decayed -> {
            includeDecayedBuildings = decayed;
            includeDecayedRoads = decayed;
        });

        // Bend the defaults to the chosen world style, then derive the range gates from whatever
        // radii the style left behind. Upstream ran validate before computing the range flags (its
        // early loadSettings call at line 363), so SPARSE's minInbetween/radii are in force when the
        // flags below are set — the flags must be computed *after* the style, not before.
        validateSettingsAgainstWorldStyle(worldStyle);
        deriveRangeFlags();
    }

    /**
     * The default settings for a world style — the compiled defaults ({@link CityWorldSettingsData#DEFAULT})
     * bent by the style's own {@link #validateSettingsAgainstWorldStyle}. The Customize screen loads this when
     * you switch style, so picking a style resets the options to that style's defaults rather than carrying the
     * previous style's over.
     */
    public static CityWorldSettingsData styleDefaults(WorldStyle style) {
        CityWorldSettings s = new CityWorldSettings(style, java.util.Optional.empty(), CityWorldSettingsData.DEFAULT);
        // Soft, toggleable per-style defaults — set here rather than in validation so they DON'T lock (the
        // screen greys only what validation forces). MODERN/APOCALYPSE ship winding caves on; any style can
        // turn them on or off.
        if (style == WorldStyle.MODERN || style == WorldStyle.APOCALYPSE)
            s.windingCaves = true;
        return s.toData();
    }

    /**
     * Which editable settings the given style <em>locks</em> (forces regardless of the datapack/toggles) — the
     * Customize screen greys these out. Computed generically by running the style validation over two opposite
     * baselines (all-on vs all-off) and keeping the keys whose result agrees: if the style pins a setting, both
     * baselines land on the same value. Keys are the field names {@link CityWorldSettingsData} exposes.
     */
    public static java.util.Set<String> styleLocks(WorldStyle style) {
        CityWorldSettingsData on = new CityWorldSettings(style, java.util.Optional.empty(),
                allToggles(true)).toData();
        CityWorldSettingsData off = new CityWorldSettings(style, java.util.Optional.empty(),
                allToggles(false)).toData();
        java.util.Set<String> locked = new java.util.HashSet<>();
        java.util.function.BiConsumer<String, Boolean> mark = (k, same) -> {
            if (same)
                locked.add(k);
        };
        CityWorldSettingsData.Features fa = on.features(), fb = off.features();
        mark.accept("Roads", fa.includeRoads() == fb.includeRoads());
        mark.accept("Roundabouts", fa.includeRoundabouts() == fb.includeRoundabouts());
        mark.accept("Sewers", fa.includeSewers() == fb.includeSewers());
        mark.accept("Cisterns", fa.includeCisterns() == fb.includeCisterns());
        mark.accept("Basements", fa.includeBasements() == fb.includeBasements());
        mark.accept("Mines", fa.includeMines() == fb.includeMines());
        mark.accept("Bunkers", fa.includeBunkers() == fb.includeBunkers());
        mark.accept("Buildings", fa.includeBuildings() == fb.includeBuildings());
        mark.accept("Houses", fa.includeHouses() == fb.includeHouses());
        mark.accept("Farms", fa.includeFarms() == fb.includeFarms());
        mark.accept("Municipalities", fa.includeMunicipalities() == fb.includeMunicipalities());
        mark.accept("Industrial sectors", fa.includeIndustrialSectors() == fb.includeIndustrialSectors());
        mark.accept("Airborne structures", fa.includeAirborneStructures() == fb.includeAirborneStructures());
        mark.accept("Building interiors", fa.includeBuildingInteriors() == fb.includeBuildingInteriors());
        mark.accept("Schematics", fa.includeSchematics() == fb.includeSchematics());
        mark.accept("Named roads", fa.includeNamedRoads() == fb.includeNamedRoads());
        CityWorldSettingsData.Terrain ta = on.terrain(), tb = off.terrain();
        mark.accept("Caves", ta.includeCaves() == tb.includeCaves());
        mark.accept("Lava fields", ta.includeLavaFields() == tb.includeLavaFields());
        mark.accept("Seas", ta.includeSeas() == tb.includeSeas());
        mark.accept("Mountains", ta.includeMountains() == tb.includeMountains());
        mark.accept("Ores", ta.includeOres() == tb.includeOres());
        mark.accept("Bones / fossils", ta.includeBones() == tb.includeBones());
        mark.accept("Fires", ta.includeFires() == tb.includeFires());
        mark.accept("Aboveground fluids", ta.includeAbovegroundFluids() == tb.includeAbovegroundFluids());
        mark.accept("Underground fluids", ta.includeUndergroundFluids() == tb.includeUndergroundFluids());
        mark.accept("Working lights", ta.includeWorkingLights() == tb.includeWorkingLights());
        mark.accept("Decayed roads", ta.includeDecayedRoads() == tb.includeDecayedRoads());
        mark.accept("Decayed buildings", ta.includeDecayedBuildings() == tb.includeDecayedBuildings());
        mark.accept("Decayed nature", ta.includeDecayedNature() == tb.includeDecayedNature());
        mark.accept("Overgrowth", on.overgrowth().enabled() == off.overgrowth().enabled());
        mark.accept("Shops", on.shops().enabled() == off.shops().enabled());
        return locked;
    }

    /** A {@link CityWorldSettingsData} with every boolean toggle set to {@code v} (doubles/enums/lists at the
     *  compiled default) — the two baselines {@link #styleLocks} diffs to find what a style pins. */
    private static CityWorldSettingsData allToggles(boolean v) {
        CityWorldSettingsData d = CityWorldSettingsData.DEFAULT;
        CityWorldSettingsData.Features f = new CityWorldSettingsData.Features(v, v, v, v, v, v, v, v, v, v, v, v, v,
                v, v, v);
        CityWorldSettingsData.Terrain t = d.terrain();
        CityWorldSettingsData.Terrain t2 = new CityWorldSettingsData.Terrain(v, v, v, v, v, v, v, v, v, v, v, v, v,
                t.oddsOfPristineBuilding(), v);
        CityWorldSettingsData.Overgrowth og = d.overgrowth();
        CityWorldSettingsData.Overgrowth og2 = new CityWorldSettingsData.Overgrowth(v, og.intensity(), v);
        CityWorldSettingsData.Shops sh = new CityWorldSettingsData.Shops(v);
        return new CityWorldSettingsData(f, t2, d.spawns(), d.treasures(), d.world(), d.radius(), d.naming(),
                d.mobs(), og2, sh, d.decay(), d.caves());
    }

    /**
     * Copies a {@link CityWorldSettingsData} (the datapack registry element) onto the runtime fields.
     * Grouped exactly as the record is — features, terrain, spawns, treasures, world, radius — so the
     * two stay easy to diff. Runs before the style/decay overrides in the constructor, so those win.
     */
    private void applyData(CityWorldSettingsData data) {
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
        windingCaves = t.windingCaves();
        CityWorldSettingsData.Overgrowth og = data.overgrowth();
        includeOvergrowth = og.enabled();
        overgrowthIntensity = og.intensity();
        capVines = og.capVines();
        caves = data.caves();
        includeShops = data.shops().enabled();
        CityWorldSettingsData.Decay dk = data.decay();
        buildingDecayIntensity = dk.buildingIntensity();
        roadDecayIntensity = dk.roadIntensity();
        oddsOfDecayFire = dk.oddsOfDecayFire();
        oddsOfPristineRoad = dk.oddsOfPristineRoad();

        CityWorldSettingsData.Spawns s = data.spawns();
        spawnBeings = s.spawnBeings();
        spawnBaddies = s.spawnBaddies();
        spawnAnimals = s.spawnAnimals();
        spawnVagrants = s.spawnVagrants();
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
        oddsOfTreasureInMines = r.oddsOfTreasureInMines();
        oddsOfTreasureInBunkers = r.oddsOfTreasureInBunkers();
        oddsOfTreasureInSewers = r.oddsOfTreasureInSewers();
        oddsOfTreasureInBuildings = r.oddsOfTreasureInBuildings();
        oddsOfAlcoveInMines = r.oddsOfAlcoveInMines();

        CityWorldSettingsData.World w = data.world();
        useModdedBiomes = w.useModdedBiomes();
        wildDecoration = w.wildDecoration();
        climateWarmth = w.climateWarmth();
        biomeScale = w.biomeScale();
        treeStyle = w.treeStyle();
        spawnTrees = w.spawnTrees();
        subSurfaceStyle = w.subSurfaceStyle();
        ruralnessLevel = w.ruralnessLevel();
        maxBuildingFloors = w.maxBuildingFloors();
        broadcastSpecialPlaces = w.broadcastSpecialPlaces();
        announcedLandmarks = java.util.Set.copyOf(w.announcedLandmarks());

        CityWorldSettingsData.Radius d = data.radius();
        centerPointOfChunkRadiusX = d.centerPointOfChunkRadiusX();
        centerPointOfChunkRadiusZ = d.centerPointOfChunkRadiusZ();
        constructChunkRadius = d.constructChunkRadius();
        roadChunkRadius = d.roadChunkRadius();
        cityChunkRadius = d.cityChunkRadius();
        buildOutsideRadius = d.buildOutsideRadius();
        minInbetweenChunkDistanceOfCities = d.minInbetweenChunkDistanceOfCities();

        CityWorldSettingsData.Naming n = data.naming();
        villagerGivenNames = n.villagerGivenNames();
        villagerSurnames = n.villagerSurnames();
        streetTerms = n.streetTerms();
        streetPrefixes = n.streetPrefixes();
        streetStarts = n.streetStarts();
        streetEnds = n.streetEnds();
        streetSuffixes = n.streetSuffixes();
        fossilPrefixes = n.fossilPrefixes();
        fossilSuffixes = n.fossilSuffixes();
        professionNames = n.professionNames();
        namesAppend = n.append();

        CityWorldSettingsData.Mobs m = data.mobs();
        mobsAppend = m.append();
        mobGoodies = resolveEntities(m.goodies(), "goodies");
        mobBaddies = resolveEntities(m.baddies(), "baddies");
        mobAnimals = resolveEntities(m.animals(), "animals");
        mobSeaAnimals = resolveEntities(m.seaAnimals(), "seaAnimals");
        mobVagrants = resolveEntities(m.vagrants(), "vagrants");
        mobSewers = resolveEntities(m.sewers(), "sewers");
        mobMine = resolveEntities(m.mine(), "mine");
        mobBunker = resolveEntities(m.bunker(), "bunker");
        mobWaterPit = resolveEntities(m.waterPit(), "waterPit");
        mobLavaPit = resolveEntities(m.lavaPit(), "lavaPit");
    }

    /**
     * Snapshots the <em>effective</em> settings back into a {@link CityWorldSettingsData} — the values
     * as they generate, i.e. after {@code applyData}, the {@code decayed} override and the world-style
     * validation have all run. This is what {@code /cityexport} bottles into a datapack, so a world
     * hand-tuned in the single-player Customize screen (or by a chosen style) can be shipped to a
     * server verbatim. The name/mob lists round-trip as whatever the source carried: empty when the
     * world kept the compiled defaults (so an export stays compact), the overriding list otherwise.
     */
    public CityWorldSettingsData toData() {
        CityWorldSettingsData.Features features = new CityWorldSettingsData.Features(
                includeRoads, includeRoundabouts, includeSewers, includeCisterns, includeBasements, includeMines,
                includeBunkers, includeBuildings, includeHouses, includeFarms, includeMunicipalities,
                includeIndustrialSectors, includeAirborneStructures, includeBuildingInteriors, includeSchematics,
                includeNamedRoads);
        CityWorldSettingsData.Terrain terrain = new CityWorldSettingsData.Terrain(
                includeCaves, includeLavaFields, includeSeas, includeMountains, includeOres, includeBones,
                includeFires, includeAbovegroundFluids, includeUndergroundFluids, includeWorkingLights,
                includeDecayedRoads, includeDecayedBuildings, includeDecayedNature, oddsOfPristineBuilding,
                windingCaves);
        CityWorldSettingsData.Spawns spawns = new CityWorldSettingsData.Spawns(
                spawnBeings, spawnBaddies, spawnAnimals, spawnVagrants, nameVillagers, showVillagersNames);
        CityWorldSettingsData.Treasures treasures = new CityWorldSettingsData.Treasures(
                treasuresInMines, spawnersInMines, treasuresInBunkers, spawnersInBunkers, treasuresInSewers,
                spawnersInSewers, treasuresInBuildings, oddsOfTreasureInMines, oddsOfTreasureInBunkers,
                oddsOfTreasureInSewers, oddsOfTreasureInBuildings, oddsOfAlcoveInMines);
        CityWorldSettingsData.World world = new CityWorldSettingsData.World(
                treeStyle, spawnTrees, subSurfaceStyle, ruralnessLevel, maxBuildingFloors, broadcastSpecialPlaces,
                java.util.List.copyOf(announcedLandmarks), useModdedBiomes, wildDecoration, climateWarmth, biomeScale);
        CityWorldSettingsData.Radius radius = new CityWorldSettingsData.Radius(
                centerPointOfChunkRadiusX, centerPointOfChunkRadiusZ, constructChunkRadius, roadChunkRadius,
                cityChunkRadius, buildOutsideRadius, minInbetweenChunkDistanceOfCities);
        CityWorldSettingsData.Naming naming = new CityWorldSettingsData.Naming(
                villagerGivenNames, villagerSurnames, streetTerms, streetPrefixes, streetStarts, streetEnds,
                streetSuffixes, fossilPrefixes, fossilSuffixes, professionNames, namesAppend);
        CityWorldSettingsData.Mobs mobs = new CityWorldSettingsData.Mobs(
                ids(mobGoodies), ids(mobBaddies), ids(mobAnimals), ids(mobSeaAnimals), ids(mobVagrants),
                ids(mobSewers), ids(mobMine), ids(mobBunker), ids(mobWaterPit), ids(mobLavaPit), mobsAppend);
        CityWorldSettingsData.Overgrowth overgrowth = new CityWorldSettingsData.Overgrowth(
                includeOvergrowth, overgrowthIntensity, capVines);
        CityWorldSettingsData.Shops shops = new CityWorldSettingsData.Shops(includeShops);
        CityWorldSettingsData.Decay decay = new CityWorldSettingsData.Decay(
                buildingDecayIntensity, roadDecayIntensity, oddsOfDecayFire, oddsOfPristineRoad);
        return new CityWorldSettingsData(features, terrain, spawns, treasures, world, radius, naming, mobs,
                overgrowth, shops, decay, caves);
    }

    private static List<String> ids(List<EntityType> types) {
        return types.stream().map(EntityType::toString).toList();
    }

    /**
     * Resolves a datapack mob-bag (entity ids) to {@code EntityType}s, dropping and logging any the
     * registry doesn't know — a typo skips one entity rather than crashing the world, matching
     * upstream's reader which validated names and reported the unknowns. An empty input stays empty,
     * which the {@code SpawnProvider} reader reads as "keep the compiled bag".
     */
    private static java.util.List<me.daddychurchill.CityWorld.compat.EntityType> resolveEntities(
            java.util.List<String> ids, String listName) {
        if (ids.isEmpty())
            return java.util.List.of();
        java.util.List<me.daddychurchill.CityWorld.compat.EntityType> out = new java.util.ArrayList<>(ids.size());
        for (String id : ids) {
            me.daddychurchill.CityWorld.compat.EntityType type =
                    me.daddychurchill.CityWorld.compat.EntityType.of(id);
            if (type == null)
                CityWorldMod.LOGGER.warn("CityWorld: mob list \"{}\" names unknown entity \"{}\" — skipping it",
                        listName, id);
            else
                out.add(type);
        }
        return out;
    }

    /**
     * Turns the raw radius knobs into the {@code checkXxxRange} gates that {@link #inCityRange} and
     * friends consult. A verbatim port of upstream's "validate the range values" block; the radii
     * themselves come from the datapack's {@code radius} block. {@code buildOutsideRadius} inverts the sense:
     * build the ring <em>outside</em> the radius rather than the disc inside it.
     */
    private void deriveRangeFlags() {
        if (buildOutsideRadius) {
            constructChunkRadius = Math.max(0, constructChunkRadius);
            roadChunkRadius = Math.max(constructChunkRadius, roadChunkRadius);
            cityChunkRadius = Math.max(roadChunkRadius, cityChunkRadius);

            checkConstructRange = constructChunkRadius > 0;
            checkRoadRange = roadChunkRadius > 0;
            checkCityRange = cityChunkRadius > 0;

            if (roadChunkRadius == Integer.MAX_VALUE) {
                includeRoads = false;
                includeSewers = false;
            }
            if (cityChunkRadius == Integer.MAX_VALUE) {
                includeCisterns = false;
                includeBasements = false;
                includeMines = false;
                includeBunkers = false;
                includeBuildings = false;
                includeHouses = false;
                includeFarms = false;
            }
        } else {
            constructChunkRadius = Math.min(Integer.MAX_VALUE, constructChunkRadius);
            roadChunkRadius = Math.min(constructChunkRadius, roadChunkRadius);
            cityChunkRadius = Math.min(roadChunkRadius, cityChunkRadius);

            checkConstructRange = constructChunkRadius < Integer.MAX_VALUE;
            checkRoadRange = roadChunkRadius < Integer.MAX_VALUE;
            checkCityRange = cityChunkRadius < Integer.MAX_VALUE;

            if (roadChunkRadius == 0) {
                includeRoads = false;
                includeSewers = false;
            }
            if (cityChunkRadius == 0) {
                includeCisterns = false;
                includeBasements = false;
                includeMines = false;
                includeBunkers = false;
                includeBuildings = false;
                includeHouses = false;
                includeFarms = false;
            }
        }
        checkMinInbetweenChunkDistanceOfCities = minInbetweenChunkDistanceOfCities > 0;
    }

    /**
     * Applies a world style's fixed overrides on top of the loaded defaults. A verbatim port of
     * upstream's method: each style forces a handful of toggles (and {@code SPARSE} the placement
     * radii). {@code CLASSIC} only pins the subsurface style. Comments preserved.
     */
    private void validateSettingsAgainstWorldStyle(WorldStyle style) {
        // now get the right defaults for the world style
        // anything commented out is up for user modification
        switch (style) {
        case CLASSIC:
        case MODERN: // shares CLASSIC's only forced override here; MODERN's facets are style-conditional
                    // elsewhere (isModernStyle) rather than settings this pass forces
        case METRO:
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SPARSE:
            centerPointOfChunkRadiusX = 0; // DIFFERENT
            centerPointOfChunkRadiusZ = 0; // DIFFERENT
            constructChunkRadius = 150; // DIFFERENT
            roadChunkRadius = 150; // DIFFERENT
            cityChunkRadius = 50; // DIFFERENT
            buildOutsideRadius = false; // DIFFERENT
            minInbetweenChunkDistanceOfCities = 100; // DIFFERENT

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case NATURE:
            includeRoads = false; // DIFFERENT
            includeRoundabouts = false; // DIFFERENT
            break;
        case DESTROYED:
            includeDecayedRoads = true; // DIFFERENT
            includeDecayedBuildings = true; // DIFFERENT
            includeDecayedNature = true; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            // Heavy war-zone damage, but the buildings still READ as buildings — a touch above baseline
            // collapse, with scattered burning wreckage rather than a firestorm (fires stay ON here).
            buildingDecayIntensity = 1.1; // DIFFERENT — heavier than a normal ruin, not obliterated
            roadDecayIntensity = 1.1; // DIFFERENT
            oddsOfDecayFire = 0.07; // DIFFERENT — the odd burning wreck, not a carpet of flame

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case APOCALYPSE:
            // A MODERN world gone to ruin. Streets and buildings decay, but the land itself stays lush and
            // wet — nature decay stays OFF (it drains the seas), then a heavy overgrowth pass reclaims the
            // ruins and the dark places crawl with the risen dead. These are the style's invariants, applied
            // last so they hold however the style was picked (preset or the Customize screen).
            includeDecayedRoads = true; // DIFFERENT
            includeDecayedBuildings = true; // DIFFERENT
            includeDecayedNature = false; // DIFFERENT — keep the wet green world
            includeDecayedFires = false; // DIFFERENT — no burning-debris fires in the ruins (campfires stay)

            includeOvergrowth = true; // DIFFERENT
            overgrowthIntensity = 3.0; // DIFFERENT — fairly heavy

            // A slow, gentle decay the greenery is reclaiming (I Am Legend, not Mad Max): light collapse,
            // no burning ruins (fires already off above), and a fair few roads still usable.
            buildingDecayIntensity = 0.5; // DIFFERENT — gentle, weathered, not obliterated
            roadDecayIntensity = 0.4; // DIFFERENT
            oddsOfPristineRoad = 0.3; // DIFFERENT — ~30% of roads still pass

            spawnBaddies = Odds.oddsSomewhatLikely; // DIFFERENT — up the hostiles
            spawnersInMines = true; // DIFFERENT
            spawnersInSewers = true; // DIFFERENT
            spawnersInBunkers = true; // DIFFERENT

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT (modern-family)
            break;
        case MAZE:
            includeRoads = true; // This has to be true in order for things to generate correctly
            includeRoundabouts = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT

            spawnersInMines = false; // DIFFERENT
            spawnersInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInBunkers = false; // DIFFERENT

            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case ASTRAL:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeCisterns = false; // DIFFERENT
            includeBasements = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeFarms = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // THIS MUST BE SET TO FALSE
            includeAbovegroundFluids = false; // THIS MUST BE SET TO FALSE
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case FLOATING:
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = false; // DIFFERENT
            includeMountains = true; // THIS MUST BE SET TO TRUE
            includeOres = false; // DIFFERENT
            includeBones = false; // DIFFERENT
            includeFires = false; // DIFFERENT

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // DIFFERENT
            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE

            break;
        case FLOODED:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE
            includeFires = false; // DIFFERENT

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeUndergroundFluids = false; // DIFFERENT
            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            includeDecayedRoads = false; // DIFFERENT
            includeDecayedBuildings = false; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SANDDUNES:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeAbovegroundFluids = false; // THIS MUST BE SET TO FALSE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            includeDecayedNature = true; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        case SNOWDUNES:
            includeRoundabouts = false; // DIFFERENT
            includeSewers = false; // DIFFERENT
            includeMines = false; // DIFFERENT
            includeBunkers = false; // DIFFERENT
            includeAirborneStructures = false; // DIFFERENT;

            includeCaves = false; // DIFFERENT
            includeLavaFields = false; // DIFFERENT
            includeSeas = true; // THIS MUST BE SET TO TRUE
            includeMountains = true; // THIS MUST BE SET TO TRUE

            spawnersInBunkers = false; // DIFFERENT
            spawnersInMines = false; // DIFFERENT
            spawnersInSewers = false; // DIFFERENT

            treasuresInBunkers = false; // DIFFERENT
            treasuresInMines = false; // DIFFERENT
            treasuresInSewers = false; // DIFFERENT

            includeAbovegroundFluids = true; // THIS MUST BE SET TO TRUE
            includeWorkingLights = false; // DIFFERENT
            includeNamedRoads = false; // DIFFERENT
            subSurfaceStyle = SubSurfaceStyle.NONE; // DIFFERENT
            break;
        }
    }

    /**
     * The centre a chunk measures its distance against. With min-inbetween spacing on (SPARSE), each
     * chunk snaps to the nearest tiled centre so cities repeat every {@code minInbetween} chunks;
     * otherwise it is the single configured centre point. Upstream used a Bukkit {@code Vector};
     * here it is two ints and a plain Euclidean distance in {@link #distanceToCentre}.
     */
    private int centreX(int x) {
        return checkMinInbetweenChunkDistanceOfCities ? calcOrigin(x, centerPointOfChunkRadiusX)
                : centerPointOfChunkRadiusX;
    }

    private int centreZ(int z) {
        return checkMinInbetweenChunkDistanceOfCities ? calcOrigin(z, centerPointOfChunkRadiusZ)
                : centerPointOfChunkRadiusZ;
    }

    // Snaps a coordinate down to the nearest multiple of the city spacing, offset from the centre —
    // ported verbatim from upstream's calcOrigin.
    private int calcOrigin(int i, int offset) {
        i = i - offset;
        if (i >= 0) {
            i = i / minInbetweenChunkDistanceOfCities * minInbetweenChunkDistanceOfCities;
        } else {
            i = -((Math.abs(i + 1) / minInbetweenChunkDistanceOfCities * minInbetweenChunkDistanceOfCities)
                    + minInbetweenChunkDistanceOfCities);
        }
        return i + offset;
    }

    private double distanceToCentre(int x, int z) {
        double dx = x - centreX(x);
        double dz = z - centreZ(z);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /**
     * Whether a chunk is inside (or, with {@code buildOutsideRadius}, outside) the city radius. When
     * the range is not being checked — every style but {@code SPARSE} so far — this answers
     * {@code true} everywhere, so an unconfigured world builds city wherever the terrain allows.
     */
    public boolean inCityRange(int chunkX, int chunkZ) {
        if (checkCityRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > cityChunkRadius : d <= cityChunkRadius;
        }
        return true;
    }

    /** As {@link #inCityRange}, for the road network's own radius. */
    public boolean inRoadRange(int chunkX, int chunkZ) {
        if (checkRoadRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > roadChunkRadius : d <= roadChunkRadius;
        }
        return true;
    }

    /** As {@link #inCityRange}, for the constructs (quarries, gravel pits). */
    public boolean inConstructRange(int chunkX, int chunkZ) {
        if (checkConstructRange) {
            double d = distanceToCentre(chunkX, chunkZ);
            return buildOutsideRadius ? d > constructChunkRadius : d <= constructChunkRadius;
        }
        return true;
    }
}
