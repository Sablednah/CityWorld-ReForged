package me.daddychurchill.CityWorld.worldgen;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import java.util.List;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.SharedConstants;
import net.minecraft.server.packs.PackType;

/**
 * Writes a {@link CityWorldSettingsData} out as a ready-to-use world datapack — the bridge from
 * "settings I have in hand" to "a folder a server op drops into {@code <world>/datapacks/}".
 *
 * <p>Two callers (P7): {@code /cityexport} bottles a live world's effective settings (single-player
 * trial → server), and first-run setup drops a fully-spelled-out example pack plus a plain-text
 * reference next to the schematics drop-in folder. Both share the one encoder, so an exported pack and
 * the example are byte-identical in shape to what the mod itself reads.
 */
public final class SettingsDatapack {

    private SettingsDatapack() {}

    /** The registry entry a bundled/overriding pack uses so a {@code cityworld:default} world picks it up. */
    public static final String DEFAULT_ENTRY = "default";
    /** The entry the City (Modern) world preset and the {@code /cityworld} dimension read. */
    public static final String MODERN_ENTRY = "modern";
    /** The entry the Apocalypse world preset reads. */
    public static final String APOCALYPSE_ENTRY = "apocalypse";

    private static final com.google.gson.Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * Encodes settings via the generator's own codec — <b>sparse</b>: {@code optionalFieldOf} omits
     * any field equal to its default, so a default world round-trips to {@code {}}. Correct for
     * reading back, but not what a human wants to see; the datapacks we <em>write</em> use
     * {@link #toFullJson}. Kept for round-trip checks.
     */
    public static String toJson(CityWorldSettingsData data) {
        JsonElement json = CityWorldSettingsData.CODEC.encodeStart(JsonOps.INSTANCE, data)
                .getOrThrow(msg -> new IllegalStateException("CityWorld: failed to encode settings — " + msg));
        return GSON.toJson(json);
    }

    /**
     * Serialises settings with <b>every</b> field present — the "see everything that can change" form
     * the example pack and {@code /cityexport} write. Hand-rolled (not the codec) precisely because the
     * codec drops defaults; the reference text is the authority on what each field means, so the small
     * duplication of field names here is a documentation surface, not logic. Decodes back through the
     * same codec unchanged.
     */
    public static String toFullJson(CityWorldSettingsData d) {
        JsonObject root = new JsonObject();

        CityWorldSettingsData.Features f = d.features();
        JsonObject features = new JsonObject();
        features.addProperty("includeRoads", f.includeRoads());
        features.addProperty("includeRoundabouts", f.includeRoundabouts());
        features.addProperty("includeSewers", f.includeSewers());
        features.addProperty("includeCisterns", f.includeCisterns());
        features.addProperty("includeBasements", f.includeBasements());
        features.addProperty("includeMines", f.includeMines());
        features.addProperty("includeBunkers", f.includeBunkers());
        features.addProperty("includeBuildings", f.includeBuildings());
        features.addProperty("includeHouses", f.includeHouses());
        features.addProperty("includeFarms", f.includeFarms());
        features.addProperty("includeMunicipalities", f.includeMunicipalities());
        features.addProperty("includeIndustrialSectors", f.includeIndustrialSectors());
        features.addProperty("includeAirborneStructures", f.includeAirborneStructures());
        features.addProperty("includeBuildingInteriors", f.includeBuildingInteriors());
        features.addProperty("includeSchematics", f.includeSchematics());
        features.addProperty("includeNamedRoads", f.includeNamedRoads());
        root.add("features", features);

        CityWorldSettingsData.Terrain t = d.terrain();
        JsonObject terrain = new JsonObject();
        terrain.addProperty("includeCaves", t.includeCaves());
        terrain.addProperty("includeLavaFields", t.includeLavaFields());
        terrain.addProperty("includeSeas", t.includeSeas());
        terrain.addProperty("includeMountains", t.includeMountains());
        terrain.addProperty("includeOres", t.includeOres());
        terrain.addProperty("includeBones", t.includeBones());
        terrain.addProperty("includeFires", t.includeFires());
        terrain.addProperty("includeAbovegroundFluids", t.includeAbovegroundFluids());
        terrain.addProperty("includeUndergroundFluids", t.includeUndergroundFluids());
        terrain.addProperty("includeWorkingLights", t.includeWorkingLights());
        terrain.addProperty("includeDecayedRoads", t.includeDecayedRoads());
        terrain.addProperty("includeDecayedBuildings", t.includeDecayedBuildings());
        terrain.addProperty("includeDecayedNature", t.includeDecayedNature());
        terrain.addProperty("oddsOfPristineBuilding", t.oddsOfPristineBuilding());
        terrain.addProperty("windingCaves", t.windingCaves());
        terrain.addProperty("largeCaverns", t.largeCaverns());
        root.add("terrain", terrain);

        CityWorldSettingsData.Overgrowth og = d.overgrowth();
        JsonObject overgrowth = new JsonObject();
        overgrowth.addProperty("enabled", og.enabled());
        overgrowth.addProperty("intensity", og.intensity());
        overgrowth.addProperty("capVines", og.capVines());
        root.add("overgrowth", overgrowth);

        CityWorldSettingsData.Shops shops = d.shops();
        JsonObject shopsJson = new JsonObject();
        shopsJson.addProperty("enabled", shops.enabled());
        shopsJson.addProperty("malls", shops.malls()); // was never exported: /cityexport dropped a malls-off world's choice
        shopsJson.addProperty("oddsOfAtmInCivic", shops.oddsOfAtmInCivic());
        shopsJson.addProperty("oddsOfAtmInShops", shops.oddsOfAtmInShops());
        shopsJson.addProperty("oddsOfAtmInMalls", shops.oddsOfAtmInMalls());
        root.add("shops", shopsJson);

        CityWorldSettingsData.Decay dk = d.decay();
        JsonObject decay = new JsonObject();
        decay.addProperty("buildingIntensity", dk.buildingIntensity());
        decay.addProperty("roadIntensity", dk.roadIntensity());
        decay.addProperty("oddsOfDecayFire", dk.oddsOfDecayFire());
        decay.addProperty("oddsOfPristineRoad", dk.oddsOfPristineRoad());
        root.add("decay", decay);

        CityWorldSettingsData.Spawns s = d.spawns();
        JsonObject spawns = new JsonObject();
        spawns.addProperty("spawnBeings", s.spawnBeings());
        spawns.addProperty("spawnBaddies", s.spawnBaddies());
        spawns.addProperty("spawnAnimals", s.spawnAnimals());
        spawns.addProperty("spawnVagrants", s.spawnVagrants());
        spawns.addProperty("nameVillagers", s.nameVillagers());
        spawns.addProperty("showVillagersNames", s.showVillagersNames());
        spawns.addProperty("wildAnimals", s.wildAnimals());
        spawns.addProperty("cityAnimals", s.cityAnimals());
        root.add("spawns", spawns);

        CityWorldSettingsData.Treasures r = d.treasures();
        JsonObject treasures = new JsonObject();
        treasures.addProperty("treasuresInMines", r.treasuresInMines());
        treasures.addProperty("spawnersInMines", r.spawnersInMines());
        treasures.addProperty("treasuresInBunkers", r.treasuresInBunkers());
        treasures.addProperty("spawnersInBunkers", r.spawnersInBunkers());
        treasures.addProperty("treasuresInSewers", r.treasuresInSewers());
        treasures.addProperty("spawnersInSewers", r.spawnersInSewers());
        treasures.addProperty("treasuresInBuildings", r.treasuresInBuildings());
        treasures.addProperty("oddsOfTreasureInMines", r.oddsOfTreasureInMines());
        treasures.addProperty("oddsOfTreasureInBunkers", r.oddsOfTreasureInBunkers());
        treasures.addProperty("oddsOfTreasureInSewers", r.oddsOfTreasureInSewers());
        treasures.addProperty("oddsOfTreasureInBuildings", r.oddsOfTreasureInBuildings());
        treasures.addProperty("oddsOfAlcoveInMines", r.oddsOfAlcoveInMines());
        treasures.addProperty("oddsOfFinds", r.oddsOfFinds());
        root.add("treasures", treasures);

        CityWorldSettingsData.World w = d.world();
        JsonObject world = new JsonObject();
        world.addProperty("treeStyle", w.treeStyle().name());
        world.addProperty("spawnTrees", w.spawnTrees());
        world.addProperty("subSurfaceStyle", w.subSurfaceStyle().name());
        world.addProperty("ruralnessLevel", w.ruralnessLevel());
        world.addProperty("maxBuildingFloors", w.maxBuildingFloors());
        world.addProperty("broadcastSpecialPlaces", w.broadcastSpecialPlaces());
        JsonArray announced = new JsonArray();
        for (String kind : w.announcedLandmarks())
            announced.add(kind);
        world.add("announcedLandmarks", announced);
        world.addProperty("useModdedBiomes", w.useModdedBiomes());
        world.addProperty("wildDecoration", w.wildDecoration().name());
        world.addProperty("climateWarmth", w.climateWarmth());
        world.addProperty("biomeScale", w.biomeScale());
        world.addProperty("moddedBiomeShare", w.moddedBiomeShare());
        world.addProperty("packPreset", w.packPreset());
        world.addProperty("moddedBiomeGrain", w.moddedBiomeGrain());
        root.add("world", world);

        CityWorldSettingsData.Radius rad = d.radius();
        JsonObject radius = new JsonObject();
        radius.addProperty("centerPointOfChunkRadiusX", rad.centerPointOfChunkRadiusX());
        radius.addProperty("centerPointOfChunkRadiusZ", rad.centerPointOfChunkRadiusZ());
        radius.addProperty("constructChunkRadius", rad.constructChunkRadius());
        radius.addProperty("roadChunkRadius", rad.roadChunkRadius());
        radius.addProperty("cityChunkRadius", rad.cityChunkRadius());
        radius.addProperty("buildOutsideRadius", rad.buildOutsideRadius());
        radius.addProperty("minInbetweenChunkDistanceOfCities", rad.minInbetweenChunkDistanceOfCities());
        root.add("radius", radius);

        CityWorldSettingsData.Naming n = d.naming();
        JsonObject naming = new JsonObject();
        naming.add("villagerGivenNames", arr(n.villagerGivenNames()));
        naming.add("villagerSurnames", arr(n.villagerSurnames()));
        naming.add("streetTerms", arr(n.streetTerms()));
        naming.add("streetPrefixes", arr(n.streetPrefixes()));
        naming.add("streetStarts", arr(n.streetStarts()));
        naming.add("streetEnds", arr(n.streetEnds()));
        naming.add("streetSuffixes", arr(n.streetSuffixes()));
        naming.add("fossilPrefixes", arr(n.fossilPrefixes()));
        naming.add("fossilSuffixes", arr(n.fossilSuffixes()));
        naming.add("professionNames", arr(n.professionNames()));
        naming.addProperty("append", n.append());
        naming.add("placeNames", arr(n.placeNames()));
        naming.add("placeStarts", arr(n.placeStarts()));
        naming.add("placeEnds", arr(n.placeEnds()));
        naming.addProperty("placeFormat", n.placeFormat());
        naming.addProperty("districtSize", n.districtSize());
        root.add("naming", naming);

        CityWorldSettingsData.Mobs m = d.mobs();
        JsonObject mobs = new JsonObject();
        mobs.add("goodies", arr(m.goodies()));
        mobs.add("baddies", arr(m.baddies()));
        mobs.add("animals", arr(m.animals()));
        mobs.add("seaAnimals", arr(m.seaAnimals()));
        mobs.add("vagrants", arr(m.vagrants()));
        mobs.add("sewers", arr(m.sewers()));
        mobs.add("mine", arr(m.mine()));
        mobs.add("bunker", arr(m.bunker()));
        mobs.add("waterPit", arr(m.waterPit()));
        mobs.add("lavaPit", arr(m.lavaPit()));
        mobs.addProperty("append", m.append());
        root.add("mobs", mobs);

        CityWorldSettingsData.Caves cv = d.caves();
        JsonObject caves = new JsonObject();
        caves.addProperty("structureCarveHalo", cv.structureCarveHalo());
        caves.addProperty("structureCarveHaloUp", cv.structureCarveHaloUp());
        caves.addProperty("surfaceMargin", cv.surfaceMargin());
        // an empty list means "the built-in patches"; a list replaces them, so it is only written when set
        root.add("caves", caves);

        CityWorldSettingsData.Subways sw = d.subways();
        JsonObject subways = new JsonObject();
        subways.addProperty("enabled", sw.enabled());
        subways.addProperty("spawners", sw.spawners());
        root.add("subways", subways);

        CityWorldSettingsData.Structures st = d.structures();
        JsonObject structures = new JsonObject();
        structures.add("allow", arr(st.allow()));
        structures.add("deny", arr(st.deny()));
        structures.addProperty("wildernessOnly", st.wildernessOnly());
        root.add("structures", structures);

        CityWorldSettingsData.Cities c = d.cities();
        JsonObject cities = new JsonObject();
        cities.addProperty("vanillaTerrain", c.vanillaTerrain());
        cities.addProperty("spacing", c.spacing());
        cities.addProperty("districts", c.districts());
        cities.addProperty("districtsVariance", c.districtsVariance());
        cities.addProperty("levelRange", c.levelRange());
        cities.addProperty("maxWater", c.maxWater());
        cities.addProperty("minNear", c.minNear());
        cities.addProperty("rivers", c.rivers());
        root.add("cities", cities);

        CityWorldSettingsData.Roads rd = d.roads();
        JsonObject roads = new JsonObject();
        roads.addProperty("driveOnLeft", rd.driveOnLeft());
        roads.addProperty("speedLimitUrban", rd.speedLimitUrban());
        roads.addProperty("speedLimitRural", rd.speedLimitRural());
        roads.addProperty("oddsOfSpeedRepeat", rd.oddsOfSpeedRepeat());
        roads.addProperty("skybridges", rd.skybridges());
        roads.addProperty("oddsOfSkybridge", rd.oddsOfSkybridge());
        roads.addProperty("skybridgeMinFloors", rd.skybridgeMinFloors());
        roads.addProperty("oddsOfStreetRope", rd.oddsOfStreetRope());
        roads.addProperty("wildBridges", rd.wildBridges());
        roads.addProperty("wildBridgeDepth", rd.wildBridgeDepth());
        roads.addProperty("wildBridgeSpan", rd.wildBridgeSpan());
        roads.addProperty("oddsOfWildBridge", rd.oddsOfWildBridge());
        roads.addProperty("wildBridgeRegion", rd.wildBridgeRegion());
        roads.addProperty("vehicles", rd.vehicles());
        roads.addProperty("oddsOfParkedVehicle", rd.oddsOfParkedVehicle());
        roads.addProperty("vehicleFuel", rd.vehicleFuel());
        root.add("roads", roads);

        // The codec's own encoding goes over the top. It is sparse (a field at its default is left out, and
        // decodes back to that same default), so it can never lose a value — which makes it the guarantee:
        // a field added to the codec and forgotten above is still exported whenever it differs from its
        // default. Before this, /cityexport silently dropped five whole groups (rivers, structure choices,
        // vanilla land, roads, caves) and seven world fields.
        JsonElement exact = CityWorldSettingsData.CODEC.encodeStart(JsonOps.INSTANCE, d)
                .getOrThrow(msg -> new IllegalStateException("CityWorld: failed to encode settings — " + msg));
        overlay(root, exact.getAsJsonObject());
        return GSON.toJson(root);
    }

    /** Copies every member of {@code from} into {@code into}, recursing into objects both have. */
    private static void overlay(JsonObject into, JsonObject from) {
        for (var e : from.entrySet()) {
            JsonElement mine = into.get(e.getKey());
            if (mine != null && mine.isJsonObject() && e.getValue().isJsonObject())
                overlay(mine.getAsJsonObject(), e.getValue().getAsJsonObject());
            else
                into.add(e.getKey(), e.getValue().deepCopy());
        }
    }

    /**
     * Where an exported world's settings go: the {@code world_settings} entry a server's world must read
     * them from, and the world type ({@code level-type}) that reads that entry.
     *
     * @param namespace the entry's namespace (a datapack folder name under {@code data/})
     * @param entry     the entry's name, and so the file name ({@code <entry>.json})
     * @param worldType the world preset that reads it, or null when only a dimension naming it does
     */
    public record Target(String namespace, String entry, String worldType) {
        public String id() {
            return namespace + ":" + entry;
        }
    }

    /**
     * The entry a world's settings belong in. A world whose generator names a registry entry (every world
     * preset does) exports to that entry; a Customize-made world carries its settings inline, so it exports
     * to the entry its style's preset reads — {@code modern} for the City preset, {@code apocalypse} for
     * Apocalypse, {@code default} for every other style. Exporting everything to {@code default}, as before
     * 6.13.1, gave a pack that a Modern or Apocalypse world never read.
     *
     * @param named the entry the generator names, as {@code namespace:path}; null for inline settings
     */
    public static Target targetFor(String named, me.daddychurchill.CityWorld.CityWorldGenerator.WorldStyle style) {
        String preset = switch (style) {
            case MODERN -> "cityworld:city";
            default -> "cityworld:" + style.name().toLowerCase(java.util.Locale.ROOT);
        };
        String byStyle = switch (style) {
            case MODERN -> MODERN_ENTRY;
            case APOCALYPSE -> APOCALYPSE_ENTRY;
            default -> DEFAULT_ENTRY;
        };
        if (named == null)
            return new Target(CityWorldMod.MODID, byStyle, preset);
        int colon = named.indexOf(':');
        String namespace = colon < 0 ? "minecraft" : named.substring(0, colon);
        String entry = colon < 0 ? named : named.substring(colon + 1);
        boolean shipped = CityWorldMod.MODID.equals(namespace) && entry.equals(byStyle);
        return new Target(namespace, entry, shipped ? preset : null);
    }

    private static JsonArray arr(List<String> items) {
        JsonArray a = new JsonArray();
        items.forEach(a::add);
        return a;
    }

    /**
     * The running version's data-pack format, so a generated {@code pack.mcmeta} is never flagged
     * "incompatible". 1.21.9+ moved to a {@code [major, minor]} format with mandatory
     * {@code min_format}/{@code max_format}; the legacy {@code pack_format} int is kept alongside for
     * older loaders.
     */
    private static String packMeta(String description) {
        int format = SharedConstants.getCurrentVersion().getPackVersion(PackType.SERVER_DATA);
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": " + GSON.toJson(description) + ",\n"
                + "    \"pack_format\": " + format + "\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * Writes a complete datapack at {@code packRoot}: {@code pack.mcmeta} plus the settings entry at
     * {@code data/cityworld/cityworld/world_settings/<entry>.json}. Returns {@code packRoot}.
     */
    public static Path writePack(Path packRoot, String entry, CityWorldSettingsData data, String description)
            throws IOException {
        return writePack(packRoot, CityWorldMod.MODID, entry, data, description);
    }

    /** As {@link #writePack(Path, String, CityWorldSettingsData, String)}, for an entry in any namespace. */
    public static Path writePack(Path packRoot, String namespace, String entry, CityWorldSettingsData data,
            String description) throws IOException {
        Files.createDirectories(packRoot);
        Files.writeString(packRoot.resolve("pack.mcmeta"), packMeta(description));
        writeEntry(packRoot, namespace, entry, data);
        return packRoot;
    }

    /** Writes one settings entry into an existing pack: {@code data/<namespace>/cityworld/world_settings/<entry>.json}. */
    public static Path writeEntry(Path packRoot, String namespace, String entry, CityWorldSettingsData data)
            throws IOException {
        Path settings = packRoot.resolve("data").resolve(namespace).resolve(CityWorldMod.MODID)
                .resolve("world_settings");
        Files.createDirectories(settings);
        Path file = settings.resolve(entry + ".json");
        Files.writeString(file, toFullJson(data));
        return file;
    }
}
