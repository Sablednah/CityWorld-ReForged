package me.daddychurchill.CityWorld.worldgen;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import me.daddychurchill.CityWorld.CityWorldMod;

/**
 * Sites another mod has asked to keep as open, level ground — a modpack's starting camp beside a vault at
 * world spawn (ZARP, 2026-09-28). Asked for through {@code CityWorldAPI.reserveSite} before the chunks exist.
 *
 * <p>A site is planned exactly like a structure reservation ({@code CityWorldGenerator.isStructureReserved}
 * answers true across it): nothing built, no road, no CityWorld trees. Its CORE (the square of {@link Site#radius}
 * chunks around the centre) is also levelled to one height and kept free of vanilla's wild decoration; the
 * {@link #MARGIN} ring around it is reserved too, and is where the ground blends back to natural — the same
 * reason a structure's reservation is sized by its blend: a city planned inside the taper gets lifted around.
 *
 * <p><b>Persisted, because the plan must stay a pure function of what it is given.</b> A site changes the plan,
 * so a restart that forgot it would plan those chunks differently from the ones already generated beside them.
 * The list lives in {@code <world>/data/cityworld_sites.json}, keyed by dimension, and is bound to the level's
 * context when the level loads — before any chunk of it is planned.
 */
public final class ReservedSites {

    /** Chunks of blend ring around a site's core, reserved with it. */
    public static final int MARGIN = 2;

    /** The core square: chunks within {@code radius} (Chebyshev) of the centre, levelled to {@code y}. */
    public record Site(int centreX, int centreZ, int radius, int y) {
        public boolean inCore(int chunkX, int chunkZ) {
            return Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ)) <= radius;
        }

        public boolean inReservation(int chunkX, int chunkZ) {
            return Math.max(Math.abs(chunkX - centreX), Math.abs(chunkZ - centreZ)) <= radius + MARGIN;
        }

        /** Block bounds of the core, inclusive. */
        public int minBlockX() {
            return (centreX - radius) * 16;
        }

        public int maxBlockX() {
            return (centreX + radius) * 16 + 15;
        }

        public int minBlockZ() {
            return (centreZ - radius) * 16;
        }

        public int maxBlockZ() {
            return (centreZ + radius) * 16 + 15;
        }
    }

    private final Path file;
    private final String dimension;
    private final List<Site> sites = new CopyOnWriteArrayList<>();

    private ReservedSites(Path file, String dimension) {
        this.file = file;
        this.dimension = dimension;
    }

    public List<Site> sites() {
        return List.copyOf(sites);
    }

    public boolean isEmpty() {
        return sites.isEmpty();
    }

    public boolean isReserved(int chunkX, int chunkZ) {
        for (Site site : sites)
            if (site.inReservation(chunkX, chunkZ))
                return true;
        return false;
    }

    /** The site whose reservation holds this chunk, or null. */
    public Site siteAt(int chunkX, int chunkZ) {
        for (Site site : sites)
            if (site.inReservation(chunkX, chunkZ))
                return site;
        return null;
    }

    public boolean isCore(int chunkX, int chunkZ) {
        for (Site site : sites)
            if (site.inCore(chunkX, chunkZ))
                return true;
        return false;
    }

    /** Add a site and write the file. Idempotent: the same site twice is kept once. */
    public synchronized void add(Site site) {
        if (!sites.contains(site))
            sites.add(site);
        save();
    }

    /** Take a site back out (a reservation that would have moved a vault is rolled back). */
    public synchronized void remove(Site site) {
        sites.remove(site);
        save();
    }

    // --- binding -------------------------------------------------------------------------------

    private static final Object BIND_LOCK = new Object();

    /**
     * This level's sites, loading them into its context the first time — called when the level loads (so
     * they are in place before it plans a chunk) and by the API. Null off a CityWorld level.
     */
    public static ReservedSites bind(net.minecraft.server.level.ServerLevel level) {
        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator generator))
            return null;
        me.daddychurchill.CityWorld.CityWorldGenerator context = generator.getContext(level);
        synchronized (BIND_LOCK) {
            ReservedSites bound = context.reservedSites;
            if (bound == null) {
                Path file = level.getServer().getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                        .resolve("data").resolve("cityworld_sites.json");
                bound = load(file, level.dimension().location().toString());
                context.reservedSites = bound;
            }
            return bound;
        }
    }

    // --- the file -------------------------------------------------------------------------------

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /** Read this dimension's sites from {@code file} (none if it is missing or unreadable). */
    public static ReservedSites load(Path file, String dimension) {
        ReservedSites loaded = new ReservedSites(file, dimension);
        if (!Files.isRegularFile(file))
            return loaded;
        try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject root = GSON.fromJson(in, JsonObject.class);
            if (root != null && root.get(dimension) instanceof JsonArray list)
                for (JsonElement e : list) {
                    JsonObject o = e.getAsJsonObject();
                    loaded.sites.add(new Site(o.get("x").getAsInt(), o.get("z").getAsInt(),
                            o.get("radius").getAsInt(), o.get("y").getAsInt()));
                }
            if (!loaded.sites.isEmpty())
                CityWorldMod.LOGGER.info("CityWorld: {} reserved site(s) in {}: {}", loaded.sites.size(), dimension,
                        loaded.sites);
        } catch (IOException | RuntimeException e) {
            CityWorldMod.LOGGER.error("CityWorld: could not read reserved sites from {}", file, e);
        }
        return loaded;
    }

    /** Rewrite this dimension's entry, keeping every other dimension's. */
    private void save() {
        try {
            JsonObject root = null;
            if (Files.isRegularFile(file))
                try (Reader in = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    root = GSON.fromJson(in, JsonObject.class);
                }
            if (root == null)
                root = new JsonObject();
            JsonArray list = new JsonArray();
            for (Site site : sites) {
                JsonObject o = new JsonObject();
                o.addProperty("x", site.centreX());
                o.addProperty("z", site.centreZ());
                o.addProperty("radius", site.radius());
                o.addProperty("y", site.y());
                list.add(o);
            }
            root.add(dimension, list);
            Files.createDirectories(file.getParent());
            try (Writer out = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(root, out);
            }
        } catch (IOException | RuntimeException e) {
            CityWorldMod.LOGGER.error("CityWorld: could not save reserved sites to {}", file, e);
        }
    }
}
