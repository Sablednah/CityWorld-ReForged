package me.daddychurchill.CityWorld.api;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.ClipboardLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Nature.RoadThroughVaultLot;
import me.daddychurchill.CityWorld.Plats.Nature.VaultLot;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;
import me.daddychurchill.CityWorld.worldgen.ReservedSites;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * CityWorld's public "what did the generator plan here?" API — the modern port of the Bukkit
 * {@code CityWorldAPI} (contributed to upstream by Sablednah, PR #4/#5). Read-only introspection over
 * the seed-deterministic plan: it re-derives a chunk's context, lot, schematic and shop from the plan
 * (the same path {@code /cityinfo} uses), so answers are correct even for chunks that were never
 * generated, and there is nothing to persist.
 *
 * <p>Two ways in: {@link #lotAt} returns a typed {@link LotInfo}; {@link #getFullInfo} returns the
 * stringly-typed {@code Map} shape the original Bukkit API returned, for continuity. Shops have their
 * own focused facade in {@link CityWorldShops}. Everything fails soft — a non-CityWorld level yields an
 * empty result rather than throwing. Call on the server thread (the underlying plan is deterministic
 * and safe to read).
 */
public final class CityWorldAPI {

    private CityWorldAPI() {}

    /** The plan for the chunk containing {@code pos}, or empty if {@code level} is not a CityWorld level. */
    public static Optional<LotInfo> lotAt(ServerLevel level, BlockPos pos) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return Optional.empty();
        return lotInfo(context, level.dimension(), pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * The plan as a {@code Map<String,String>} with the keys the original Bukkit
     * {@code CityWorldAPI.getFullInfo} used — {@code context}, {@code contextclass}, {@code lot},
     * {@code lotclass}, {@code at}, and {@code schematic} (only when this lot is a schematic) — plus two
     * additive keys, {@code roads} and {@code shop} (only when this lot is a shop). Empty map off a
     * non-CityWorld level.
     */
    public static Map<String, String> getFullInfo(ServerLevel level, BlockPos pos) {
        return lotAt(level, pos).map(CityWorldAPI::toMap).orElseGet(Map::of);
    }

    // --- finding lots -----------------------------------------------------------------------------

    /** How long a find may plan before it gives up — the same wall-clock budget {@code /cityfind} uses. */
    public static final long FIND_BUDGET_MS = 120_000;

    /**
     * The nearest lot whose type matches {@code kind}, within {@code maxBlocks} of {@code from} — the same match
     * as {@code /cityfind lot <kind>}: a case-free substring of the lot's class name ("vault", "saucer", "zoo").
     * Plan only, so it answers for land that has never generated; safe on the server thread, including during
     * world creation. Empty off a non-CityWorld level or on a miss.
     */
    public static Optional<LotInfo> findLot(ServerLevel level, BlockPos from, String kind, int maxBlocks) {
        List<LotInfo> found = findLots(level, from, kind, maxBlocks, 1);
        return found.isEmpty() ? Optional.empty() : Optional.of(found.get(0));
    }

    /** Up to {@code limit} lots matching {@code kind}, nearest first, within {@code maxBlocks} of {@code from}. */
    public static List<LotInfo> findLots(ServerLevel level, BlockPos from, String kind, int maxBlocks, int limit) {
        return findLots(level, from, kind, maxBlocks, limit, FIND_BUDGET_MS);
    }

    /** {@link #findLots(ServerLevel, BlockPos, String, int, int)} with a wall-clock budget of its own. */
    public static List<LotInfo> findLots(ServerLevel level, BlockPos from, String kind, int maxBlocks, int limit,
            long budgetMillis) {
        CityWorldGenerator context = contextFor(level);
        if (context == null || kind == null || kind.isBlank())
            return List.of();
        String match = kind.trim().toLowerCase(Locale.ROOT);
        List<LotInfo> out = new ArrayList<>();
        for (PlatLot lot : searchLots(context, from, maxBlocks, limit, budgetMillis, null,
                lot -> lot.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(match)))
            lotInfo(context, level.dimension(), lot.getChunkX(), lot.getChunkZ()).ifPresent(out::add);
        return out;
    }

    /**
     * Up to {@code limit} lots for which {@code match} holds, nearest first, within {@code maxBlocks} of {@code from}:
     * the search by anything a {@link LotInfo} says (a schematic by name, a district, an interior). Plan only, like
     * {@link #findLots(ServerLevel, BlockPos, String, int, int)}; it builds a {@code LotInfo} per lot looked at, so
     * give it a sensible budget, and call it off the server thread.
     */
    public static List<LotInfo> findLots(ServerLevel level, BlockPos from, java.util.function.Predicate<LotInfo> match,
            int maxBlocks, int limit, long budgetMillis) {
        CityWorldGenerator context = contextFor(level);
        if (context == null || match == null)
            return List.of();
        List<LotInfo> out = new ArrayList<>();
        for (PlatLot lot : searchLots(context, from, maxBlocks, limit, budgetMillis, null,
                (PlatLot lot) -> lotInfo(context, level.dimension(), lot.getChunkX(), lot.getChunkZ()).map(match::test).orElse(false)))
            lotInfo(context, level.dimension(), lot.getChunkX(), lot.getChunkZ()).ifPresent(out::add);
        return out;
    }

    // --- finding by kind: a compass's menu ----------------------------------------------------------

    /** A kind of lot worth walking to, and the lot classes that are it. */
    private record Place(String key, String title, java.util.Set<String> lots) {
    }

    private static final List<Place> PLACES = List.of(
            new Place("hospital", "Hospital", java.util.Set.of("HospitalLot")),
            new Place("mall", "Shopping mall", java.util.Set.of("MallLot")),
            new Place("museum", "Museum", java.util.Set.of("MuseumBuildingLot")),
            new Place("library", "Library", java.util.Set.of("LibraryBuildingLot")),
            new Place("civic", "Civic building", java.util.Set.of("GovernmentBuildingLot")),
            new Place("monument", "Monument", java.util.Set.of("GovernmentMonumentLot", "HillStatueLot")),
            new Place("subway", "Subway station", java.util.Set.of("SubwayStationLot")),
            new Place("zoo", "Zoo", java.util.Set.of("ZooLot", "BigZooLot")),
            new Place("biodome", "Biodome", java.util.Set.of("BiodomeLot", "BigBiodomeLot")),
            new Place("park", "Park", java.util.Set.of("ParkLot", "FloodedParkLot", "SandDunesParkLot", "SnowDunesParkLot")),
            new Place("parking", "Car park", java.util.Set.of("ParkingLot")),
            new Place("factory", "Factory", java.util.Set.of("FactoryBuildingLot")),
            new Place("warehouse", "Warehouse", java.util.Set.of("WarehouseBuildingLot")),
            new Place("silo", "Silos", java.util.Set.of("SiloLot")),
            new Place("gasometer", "Gasometer", java.util.Set.of("GasometerLot")),
            new Place("unfinished", "Unfinished building", java.util.Set.of("UnfinishedBuildingLot",
                    "FloodedUnfinishedBuildingLot", "SandDunesUnfinishedBuildingLot", "SnowDunesUnfinishedBuildingLot")),
            new Place("radio_tower", "Radio tower", java.util.Set.of("RadioTowerLot")),
            new Place("water_tower", "Water tower", java.util.Set.of("WaterTowerLot")),
            new Place("vault", "Vault entrance", java.util.Set.of()), // the entrance hut only: see placeMatches
            new Place("bunker", "Bunker", java.util.Set.of("BunkerLot", "RoadThroughBunkerLot")),
            new Place("mine", "Mine entrance", java.util.Set.of("MineEntranceLot")),
            new Place("campground", "Campground", java.util.Set.of("CampgroundLot")),
            new Place("castle", "Old castle", java.util.Set.of("OldCastleLot")),
            new Place("oil_platform", "Oil platform", java.util.Set.of("OilPlatformLot")),
            new Place("airship", "Airship", java.util.Set.of("AirshipLot")),
            new Place("balloon", "Hot-air balloon", java.util.Set.of("HotairBalloonLot")),
            new Place("saucer", "Flying saucer", java.util.Set.of("FlyingSaucerLot")),
            new Place("farm", "Farm", java.util.Set.of("FarmLot", "FloodedFarmLot", "SandDunesFarmLot", "SnowDunesFarmLot")),
            new Place("barn", "Barn", java.util.Set.of("BarnLot")),
            new Place("well", "Farmyard well", java.util.Set.of("FarmyardWellLot")),
            new Place("fish_pond", "Fish pond", java.util.Set.of("FishPondLot")),
            new Place("quayside", "Quayside", java.util.Set.of("QuaysideLot", "ShorelineLot")));

    /** The districts worth naming, by the family their buildings come from. */
    private static final java.util.Map<me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily, String> DISTRICTS;
    static {
        var f = new java.util.EnumMap<me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily, String>(
                me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.class);
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.HIGHRISE, "High-rise district");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.MIDRISE, "Mid-rise district");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.LOWRISE, "Low-rise district");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.MUNICIPAL, "Civic district");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.INDUSTRIAL, "Industrial district");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.NEIGHBORHOOD, "Neighbourhood");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.CONSTRUCTION, "Construction site");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.PARK, "Parkland");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.ROUNDABOUT, "Roundabout");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.FARM, "Farmland");
        f.put(me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.OUTLAND, "Outland works");
        DISTRICTS = java.util.Collections.unmodifiableMap(f);
    }

    /**
     * Everything {@link #find} can look for in this level, grouped and titled for a player: the nearest city, kinds
     * of place, shops by trade, districts, and every schematic building CityWorld has loaded (by its {@code Title}).
     * A kind this world's style never plans is still listed; a search for it simply finds nothing. Empty off a
     * non-CityWorld level.
     */
    public static List<Findable> findables(ServerLevel level) {
        if (contextFor(level) == null)
            return List.of();
        List<Findable> out = new ArrayList<>();
        out.add(new Findable("city", "city", "Nearest city"));
        for (Place p : PLACES)
            out.add(new Findable("place:" + p.key(), "place", p.title()));
        for (ShopTrade t : ShopTrade.values())
            out.add(new Findable("shop:" + t.name().toLowerCase(Locale.ROOT), "shop", t.displayName()));
        DISTRICTS.forEach((family, title) -> out.add(
                new Findable("district:" + family.name().toLowerCase(Locale.ROOT), "district", title)));
        for (String name : me.daddychurchill.CityWorld.Clipboard.SchematicLibrary.names()) {
            var clip = me.daddychurchill.CityWorld.Clipboard.SchematicLibrary.get(name);
            out.add(new Findable("building:" + name.toLowerCase(Locale.ROOT), "building", clip == null ? name : clip.title));
        }
        return out;
    }

    /**
     * Up to {@code limit} of the {@link Findable} with this {@code id}, nearest first, within {@code maxBlocks} of
     * {@code from}. A multi-chunk place answers with its nearest chunk; {@code city} answers with the centre of the
     * nearest city ({@link #nearestCity}). Plan only: nothing is generated. A search can plan many platmaps, so call
     * it off the server thread (as {@code /cityfind} does) with a budget. Empty off a non-CityWorld level, for an
     * unknown id, or on a miss.
     */
    public static List<LotInfo> find(ServerLevel level, BlockPos from, String id, int maxBlocks, int limit,
            long budgetMillis) {
        CityWorldGenerator context = contextFor(level);
        if (context == null || id == null)
            return List.of();
        String key = id.trim().toLowerCase(Locale.ROOT);
        java.util.function.BiPredicate<PlatMap, PlatLot> wanted = null;
        if (key.equals("city")) {
            return nearestCity(level, from, maxBlocks)
                    .flatMap(c -> lotInfo(context, level.dimension(), c.getX() >> 4, c.getZ() >> 4))
                    .map(List::of).orElse(List.of());
        } else if (key.startsWith("place:")) {
            String k = key.substring(6);
            for (Place p : PLACES)
                if (p.key().equals(k))
                    wanted = (pm, lot) -> placeMatches(p, lot);
        } else if (key.startsWith("shop:")) {
            for (ShopTrade t : ShopTrade.values())
                if (t.name().equalsIgnoreCase(key.substring(5)))
                    wanted = (pm, lot) -> lot.getShopType() != null && lot.getShopType().trade() == t;
        } else if (key.startsWith("district:")) {
            for (var family : DISTRICTS.keySet())
                if (family.name().equalsIgnoreCase(key.substring(9)))
                    wanted = (pm, lot) -> pm.context.getSchematicFamily() == family;
        } else if (key.startsWith("building:")) {
            String name = key.substring(9);
            wanted = (pm, lot) -> lot instanceof ClipboardLot clip && clip.getClip().name.equalsIgnoreCase(name);
        }
        if (wanted == null)
            return List.of();
        List<LotInfo> out = new ArrayList<>();
        for (PlatLot lot : searchLots(context, from, maxBlocks, limit, budgetMillis, null, wanted))
            lotInfo(context, level.dimension(), lot.getChunkX(), lot.getChunkZ()).ifPresent(out::add);
        return out;
    }

    private static boolean placeMatches(Place p, PlatLot lot) {
        if (p.key().equals("vault"))
            return lot instanceof VaultLot vault && vault.isEntrance();
        return p.lots().contains(lot.getClass().getSimpleName());
    }

    /**
     * The middle of the nearest city, at street level, within {@code maxBlocks}. In a vanilla-land world that is the
     * nearest city site; on CityWorld's own land, where city is everywhere it is not wild, it is the nearest chunk of
     * a built-up district (anything but nature and farmland). Plan only; call it off the server thread.
     */
    public static Optional<BlockPos> nearestCity(ServerLevel level, BlockPos from, int maxBlocks) {
        if (!(level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator generator))
            return Optional.empty();
        CityWorldGenerator context = generator.getContext(level);
        if (context == null)
            return Optional.empty();
        var sites = generator.citySites();
        if (sites != null) {
            var site = sites.nearest(from.getX(), from.getZ(), Math.max(1, maxBlocks / sites.cell() + 1));
            if (site == null || Math.hypot(site.centreX() - from.getX(), site.centreZ() - from.getZ()) > maxBlocks)
                return Optional.empty();
            return Optional.of(new BlockPos(site.centreX(), site.level() + 1, site.centreZ()));
        }
        var families = java.util.EnumSet.complementOf(java.util.EnumSet.of(
                me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.NATURE,
                me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.FARM,
                me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.OUTLAND,
                me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily.ASTRAL));
        List<PlatLot> hit = searchLots(context, from, maxBlocks, 1, FIND_BUDGET_MS, null,
                (pm, lot) -> families.contains(pm.context.getSchematicFamily()) && !(lot instanceof me.daddychurchill.CityWorld.Plats.NatureLot));
        return hit.isEmpty() ? Optional.empty()
                : Optional.of(new BlockPos(hit.get(0).getChunkX() * 16 + 8, context.streetLevel + 1, hit.get(0).getChunkZ() * 16 + 8));
    }

    // --- vaults ---------------------------------------------------------------------------------

    /**
     * Up to {@code limit} vault entrances, nearest first, within {@code maxBlocks} of {@code from}: for each, the
     * block a player stands on outside the surface hut's door (see {@link #vaultEntrance}). Plan only.
     */
    public static List<BlockPos> findVaultEntrances(ServerLevel level, BlockPos from, int maxBlocks, int limit) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return List.of();
        List<BlockPos> out = new ArrayList<>();
        if (context.worldStyle != CityWorldGenerator.WorldStyle.APOCALYPSE || !context.getSettings().includeBunkers)
            return List.of(); // vaults are an APOCALYPSE bunker; nothing else plans one
        // Only a vault region can hold one, and that is a hash of the platmap's position — so the search
        // plans ~1 platmap in 12 instead of all of them (5 entrances out to 2,500 blocks: 90 s -> seconds).
        for (PlatLot lot : searchLots(context, from, maxBlocks, limit, FIND_BUDGET_MS,
                (ox, oz) -> me.daddychurchill.CityWorld.Context.NatureContext.isVaultRegion(context, ox, oz),
                lot -> lot instanceof VaultLot vault && vault.isEntrance()))
            doorstep((VaultLot) lot).ifPresent(out::add);
        return out;
    }

    /**
     * The way into the vault whose entrance chunk is {@code chunk}: the position a player stands in just outside
     * the surface hut's iron door (the door faces south; the ladder shaft is behind it at chunk-local x 2, z 1).
     * That is where a path to the vault ends. Answered from the plan, so valid before the chunk generates; the
     * height is the planned ground there, which the hut is built to (it scans the real ground and sits a block
     * off only where a tree trunk stands on that exact column). Empty if {@code chunk} is not a vault entrance —
     * including a vault chunk a road was later laid over, which is entered from its road tunnel instead.
     */
    public static Optional<BlockPos> vaultEntrance(ServerLevel level, ChunkPos chunk) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return Optional.empty();
        try {
            if (context.getPlatMap(chunk.x, chunk.z).getMapLot(chunk.x, chunk.z) instanceof VaultLot vault
                    && vault.isEntrance())
                return doorstep(vault);
        } catch (RuntimeException e) {
            // fail soft
        }
        return Optional.empty();
    }

    private static Optional<BlockPos> doorstep(VaultLot vault) {
        var ys = vault.getCachedYs();
        if (ys == null)
            return Optional.empty();
        return Optional.of(new BlockPos(vault.getChunkX() * 16 + VaultLot.DOORSTEP_X,
                ys.getBlockY(VaultLot.DOORSTEP_X, VaultLot.DOORSTEP_Z) + 1,
                vault.getChunkZ() * 16 + VaultLot.DOORSTEP_Z));
    }

    // --- reserved sites -------------------------------------------------------------------------

    /** The largest core radius {@link #reserveSite} takes: 17x17 chunks, before its blend ring. */
    public static final int MAX_SITE_RADIUS = 8;

    /**
     * Keep a site as open, level ground: the chunks within {@code radiusChunks} of {@code centre} plan as nature
     * (no building, road, CityWorld tree or vanilla wild decoration) with their ground at {@code y} — the height of
     * the top solid block, so stand at {@code y + 1} — and a {@link ReservedSites#MARGIN}-chunk ring around them
     * is reserved too and blends back to the natural ground, the way CityWorld blends ground to a structure. A
     * {@code y} at or under sea level is raised to {@code seaLevel + 1} (below it CityWorld floods the column).
     *
     * <p><b>Call it before any of those chunks generate</b> — from {@code LevelEvent.CreateSpawnPosition} on a new
     * world is the intended place. It re-plans the platmaps it touches, and one whose chunks already exist would no
     * longer match them. The site is saved with the world ({@code data/cityworld_sites.json}) and is in place again
     * before anything plans after a restart. Calling it again with the same arguments changes nothing.
     *
     * <p>It never costs a vault: a site whose reservation covers any vault chunk is refused, and one whose re-plan
     * moves a vault entrance in the platmaps it touches is rolled back. Per level: a twin dimension plans its own.
     */
    public static SiteResult reserveSite(ServerLevel level, ChunkPos centre, int radiusChunks, int y) {
        CityWorldGenerator context = contextFor(level);
        ReservedSites sites = context == null ? null : ReservedSites.bind(level);
        if (sites == null)
            return SiteResult.NOT_CITYWORLD;
        if (radiusChunks < 0 || radiusChunks > MAX_SITE_RADIUS || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight() - 1)
            return SiteResult.INVALID;
        ReservedSites.Site site = new ReservedSites.Site(centre.x, centre.z, radiusChunks,
                Math.max(y, context.seaLevel + 1));
        int reach = radiusChunks + ReservedSites.MARGIN;
        try {
            for (int cx = centre.x - reach; cx <= centre.x + reach; cx++)
                for (int cz = centre.z - reach; cz <= centre.z + reach; cz++) {
                    PlatLot lot = context.getPlatMap(cx, cz).getMapLot(cx, cz);
                    if (lot instanceof VaultLot || lot instanceof RoadThroughVaultLot)
                        return SiteResult.OVERLAPS_VAULT;
                }
            java.util.Set<Long> before = vaultEntrancesAround(context, centre, reach);
            sites.add(site);
            forgetPlatMaps(context, centre, reach);
            if (!before.equals(vaultEntrancesAround(context, centre, reach))) {
                sites.remove(site);
                forgetPlatMaps(context, centre, reach);
                return SiteResult.VAULT_MOVED;
            }
            return SiteResult.RESERVED;
        } catch (RuntimeException e) {
            sites.remove(site);
            forgetPlatMaps(context, centre, reach);
            throw e;
        }
    }

    /** Every vault entrance chunk in the platmaps a site of this reach touches. */
    private static java.util.Set<Long> vaultEntrancesAround(CityWorldGenerator context, ChunkPos centre, int reach) {
        java.util.Set<Long> found = new java.util.HashSet<>();
        for (PlatMap pm : platMapsAround(context, centre, reach))
            for (int x = 0; x < PlatMap.Width; x++)
                for (int z = 0; z < PlatMap.Width; z++)
                    if (pm.getLot(x, z) instanceof VaultLot vault && vault.isEntrance())
                        found.add(ChunkPos.asLong(vault.getChunkX(), vault.getChunkZ()));
        return found;
    }

    private static List<PlatMap> platMapsAround(CityWorldGenerator context, ChunkPos centre, int reach) {
        java.util.Map<Long, PlatMap> maps = new LinkedHashMap<>();
        for (int cx = centre.x - reach; cx <= centre.x + reach; cx++)
            for (int cz = centre.z - reach; cz <= centre.z + reach; cz++) {
                PlatMap pm = context.getPlatMap(cx, cz);
                maps.putIfAbsent(ChunkPos.asLong(pm.originX, pm.originZ), pm);
            }
        return new ArrayList<>(maps.values());
    }

    private static void forgetPlatMaps(CityWorldGenerator context, ChunkPos centre, int reach) {
        for (int cx = centre.x - reach; cx <= centre.x + reach; cx++)
            for (int cz = centre.z - reach; cz <= centre.z + reach; cz++)
                context.forgetPlatMap(cx, cz);
    }

    // --- internals ------------------------------------------------------------------------------

    /** Rings of platmaps out from {@code from} for lots {@code wanted} accepts (in platmaps whose origin chunk
     *  {@code platmapMayHold} accepts, when given — the rest are never planned), nearest first, at most
     *  {@code limit}, within {@code maxBlocks} — the ring search {@code /cityfind} uses: one ring past the one
     *  that filled the quota (a nearer hit can sit in the next ring's corner), and never past the budget. */
    private static List<PlatLot> searchLots(CityWorldGenerator context, BlockPos from, int maxBlocks, int limit,
            long budgetMillis, java.util.function.BiPredicate<Integer, Integer> platmapMayHold,
            java.util.function.Predicate<PlatLot> wanted) {
        return searchLots(context, from, maxBlocks, limit, budgetMillis, platmapMayHold, (pm, lot) -> wanted.test(lot));
    }

    /** {@link #searchLots} for a test that also needs the lot's platmap (its district). */
    private static List<PlatLot> searchLots(CityWorldGenerator context, BlockPos from, int maxBlocks, int limit,
            long budgetMillis, java.util.function.BiPredicate<Integer, Integer> platmapMayHold,
            java.util.function.BiPredicate<PlatMap, PlatLot> wanted) {
        if (limit <= 0 || maxBlocks < 0)
            return List.of();
        int px = from.getX() >> 4, pz = from.getZ() >> 4;
        int pmX0 = Math.floorDiv(px, PlatMap.Width) * PlatMap.Width;
        int pmZ0 = Math.floorDiv(pz, PlatMap.Width) * PlatMap.Width;
        int maxRings = maxBlocks / (PlatMap.Width * 16) + 1;
        long deadline = System.nanoTime() + budgetMillis * 1_000_000L;
        List<PlatLot> hits = new ArrayList<>();
        int filledRing = -1;
        for (int r = 0; r <= maxRings; r++) {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue;
                    int ox = pmX0 + dx * PlatMap.Width, oz = pmZ0 + dz * PlatMap.Width;
                    if (platmapMayHold != null && !platmapMayHold.test(ox, oz))
                        continue;
                    PlatMap pm = context.getPlatMap(ox, oz);
                    for (int x = 0; x < PlatMap.Width; x++)
                        for (int z = 0; z < PlatMap.Width; z++) {
                            PlatLot lot = pm.getLot(x, z);
                            if (lot != null && distance(lot, from) <= maxBlocks && wanted.test(pm, lot))
                                hits.add(lot);
                        }
                }
            if (hits.size() >= limit && filledRing < 0)
                filledRing = r;
            if (filledRing >= 0 && r >= filledRing + 1)
                break;
            if (System.nanoTime() > deadline)
                break;
        }
        hits.sort(java.util.Comparator.comparingDouble(lot -> distance(lot, from)));
        return hits.size() > limit ? List.copyOf(hits.subList(0, limit)) : hits;
    }

    private static double distance(PlatLot lot, BlockPos from) {
        return Math.hypot(lot.getChunkX() * 16 + 8 - from.getX(), lot.getChunkZ() * 16 + 8 - from.getZ());
    }

    /**
     * The street level of a CityWorld level: the height of the road surface, which everything underground is
     * laid out from. Empty off a non-CityWorld level.
     */
    public static java.util.OptionalInt streetLevel(ServerLevel level) {
        CityWorldGenerator context = contextFor(level);
        return context == null ? java.util.OptionalInt.empty() : java.util.OptionalInt.of(context.streetLevel);
    }

    /** True if a subway station or line is under the chunk holding {@code pos}. */
    public static boolean isSubway(ServerLevel level, BlockPos pos) {
        return subwayAt(level, pos).map(SubwayInfo::any).orElse(false);
    }

    /**
     * What the subway has under the chunk holding {@code pos}: a station, a line on either level, and where
     * those levels are (so {@link SubwayInfo#contains} tells whether a height is inside a tunnel). Empty off a
     * non-CityWorld level; a {@link SubwayInfo} with nothing in it where the subway does not run.
     */
    public static Optional<SubwayInfo> subwayAt(ServerLevel level, BlockPos pos) {
        CityWorldGenerator context = contextFor(level);
        if (context == null)
            return Optional.empty();
        int cx = pos.getX() >> 4, cz = pos.getZ() >> 4;
        try {
            PlatLot lot = context.getPlatMap(cx, cz).getMapLot(cx, cz);
            var piece = me.daddychurchill.CityWorld.Support.Subway.drawsIn(context, lot)
                    ? me.daddychurchill.CityWorld.Support.Subway.at(context, cx, cz)
                    : me.daddychurchill.CityWorld.Support.Subway.Piece.NONE;
            return Optional.of(new SubwayInfo(new ChunkPos(cx, cz), piece.station(),
                    piece.station() || piece.ewMask() != 0 || piece.ramp() != null, piece.nsMask() != 0 || piece.ramp() != null,
                    me.daddychurchill.CityWorld.Support.Subway.ewFloor(context),
                    me.daddychurchill.CityWorld.Support.Subway.nsFloor(context),
                    me.daddychurchill.CityWorld.Support.Subway.HEIGHT));
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    private static CityWorldGenerator contextFor(ServerLevel level) {
        if (level.getChunkSource().getGenerator() instanceof CityWorldChunkGenerator cityGenerator)
            return cityGenerator.getContext(level);
        return null;
    }

    private static Optional<LotInfo> lotInfo(CityWorldGenerator context, ResourceKey<Level> dim, int cx, int cz) {
        try {
            PlatMap platmap = context.getPlatMap(cx, cz);
            PlatLot lot = platmap.getMapLot(cx, cz);
            String schematic = lot instanceof ClipboardLot clip ? clip.getClip().name : null;
            return Optional.of(new LotInfo(
                    dim,
                    new ChunkPos(cx, cz),
                    platmap.context.getSchematicFamily(),
                    platmap.context.getClass().getSimpleName(),
                    lot.style,
                    lot.getClass().getSimpleName(),
                    platmap.getNaturePercent(),
                    platmap.getNumberOfRoads(),
                    schematic,
                    lot.getShopType(),
                    lot.getInteriorDescription(context, platmap, cx - platmap.originX, cz - platmap.originZ),
                    context.streetLevel,
                    me.daddychurchill.CityWorld.Support.Subway.drawsIn(context, lot)
                            && me.daddychurchill.CityWorld.Support.Subway.at(context, cx, cz).any()));
        } catch (RuntimeException e) {
            return Optional.empty(); // never let a lookup throw into a caller
        }
    }

    private static Map<String, String> toMap(LotInfo i) {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("context", i.contextFamily().toString());
        m.put("contextclass", i.contextClass());
        m.put("lot", i.lotStyle().toString());
        if (i.interior() != null)
            m.put("interior", i.interior());
        m.put("lotclass", i.lotClass());
        m.put("at", i.chunk().x + "|" + i.chunk().z);
        m.put("roads", Integer.toString(i.roadCount()));
        if (i.schematicName() != null)
            m.put("schematic", i.schematicName());
        if (i.shop() != null)
            m.put("shop", i.shop().describe());
        return m;
    }
}
