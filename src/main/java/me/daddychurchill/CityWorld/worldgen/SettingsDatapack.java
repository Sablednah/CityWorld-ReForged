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
import net.minecraft.server.packs.metadata.pack.PackFormat;

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
        root.add("treasures", treasures);

        CityWorldSettingsData.World w = d.world();
        JsonObject world = new JsonObject();
        world.addProperty("treeStyle", w.treeStyle().name());
        world.addProperty("spawnTrees", w.spawnTrees());
        world.addProperty("subSurfaceStyle", w.subSurfaceStyle().name());
        world.addProperty("ruralnessLevel", w.ruralnessLevel());
        world.addProperty("maxBuildingFloors", w.maxBuildingFloors());
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

        return GSON.toJson(root);
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
        PackFormat f = SharedConstants.getCurrentVersion().packVersion(PackType.SERVER_DATA);
        String range = "[" + f.major() + ", " + f.minor() + "]";
        return "{\n"
                + "  \"pack\": {\n"
                + "    \"description\": " + GSON.toJson(description) + ",\n"
                + "    \"pack_format\": " + f.major() + ",\n"
                + "    \"min_format\": " + range + ",\n"
                + "    \"max_format\": " + range + "\n"
                + "  }\n"
                + "}\n";
    }

    /**
     * Writes a complete datapack at {@code packRoot}: {@code pack.mcmeta} plus the settings entry at
     * {@code data/cityworld/cityworld/world_settings/<entry>.json}. Returns {@code packRoot}.
     */
    public static Path writePack(Path packRoot, String entry, CityWorldSettingsData data, String description)
            throws IOException {
        Files.createDirectories(packRoot);
        Files.writeString(packRoot.resolve("pack.mcmeta"), packMeta(description));
        Path settings = packRoot.resolve("data").resolve(CityWorldMod.MODID).resolve(CityWorldMod.MODID)
                .resolve("world_settings");
        Files.createDirectories(settings);
        Files.writeString(settings.resolve(entry + ".json"), toFullJson(data));
        return packRoot;
    }
}
