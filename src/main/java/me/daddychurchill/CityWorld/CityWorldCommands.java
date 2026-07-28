package me.daddychurchill.CityWorld;

import java.util.Locale;
import java.util.Set;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import net.minecraft.commands.SharedSuggestionProvider;

import me.daddychurchill.CityWorld.Clipboard.Clipboard;
import me.daddychurchill.CityWorld.Clipboard.ClipboardLot;
import me.daddychurchill.CityWorld.Clipboard.SchematicLibrary;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.api.CityWorldAPI;
import me.daddychurchill.CityWorld.api.LotInfo;
import me.daddychurchill.CityWorld.worldgen.CityWorldBiomes;
import me.daddychurchill.CityWorld.worldgen.CityWorldChunkGenerator;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Relative;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The CityWorld command tree — the modern Brigadier port of upstream's {@code CommandCityWorld} /
 * {@code CommandCityInfo} / {@code CommandCityChunk}.
 *
 * <ul>
 *   <li>{@code /cityinfo} — anyone. Reports the plan under the player: context, lot, and how the
 *       planner graded the terrain. Read-only. (Originally contributed to upstream by Sablednah,
 *       PR #4; that attribution is preserved here.)</li>
 *   <li>{@code /cityworld} / {@code /cityworld leave} — op. Teleports into the {@code cityworld:city}
 *       dimension (and back to the overworld), landing on the surface at the player's X/Z.</li>
 * </ul>
 *
 * <p>{@code /citychunk} is intentionally not ported: its {@code regen} relied on Bukkit's runtime
 * {@code World.regenerateChunk}, which modern Minecraft has no safe equivalent for (chunks can only
 * be reset by deleting region files while the world is unloaded).
 */
public final class CityWorldCommands {

    /** The dimension registered in {@code data/cityworld/dimension/city.json}. */
    private static final ResourceKey<Level> CITY = ResourceKey.create(Registries.DIMENSION,
            Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "city"));

    private CityWorldCommands() {}

    /**
     * Tab-completion for a schematic-name argument — the same distinct building names
     * {@code /cityschem list} prints ({@link SchematicLibrary#names()}, bundled + drop-in). Prefix
     * matching is case-insensitive and copes with names containing spaces, since the argument is a
     * greedy string.
     */
    private static final SuggestionProvider<CommandSourceStack> SUGGEST_SCHEMATICS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(SchematicLibrary.names(), builder);

    /** The well-known landmark lot kinds offered as tab-completions for {@code /cityfind lot}. The match
     *  itself is a free substring against every lot's class name (so any lot type is findable — e.g.
     *  "office", "warehouse"), but these are the fun, rare ones worth pointing people at. */
    private static final java.util.List<String> LOT_KINDS = java.util.List.of(
            "zoo", "biodome", "saucer", "balloon", "blimp", "fishpond", "cornershop", "castle",
            "oilplatform", "radiotower", "watertower", "monument", "library", "museum", "campground",
            "mineentrance", "bunker", "farm", "park");

    private static final SuggestionProvider<CommandSourceStack> SUGGEST_LOTKINDS =
            (ctx, builder) -> SharedSuggestionProvider.suggest(LOT_KINDS, builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("cityinfo")
                .executes(CityWorldCommands::cityInfo));

        dispatcher.register(Commands.literal("cityworld")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> teleport(ctx, CITY, "Entering the city..."))
                .then(Commands.literal("leave")
                        .executes(ctx -> teleport(ctx, Level.OVERWORLD, "Leaving CityWorld... come back soon!"))));

        dispatcher.register(Commands.literal("cityschem")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("list")
                        .executes(CityWorldCommands::listSchematics))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(SUGGEST_SCHEMATICS)
                        .executes(CityWorldCommands::pasteSchematic)));

        // /cityfind <name>  (report nearest) or  /cityfind tp <name>  (and teleport there). tp is a
        // literal before the name because the name is greedy (schematic names have spaces).
        //   /cityfind lot <kind> [tp order: lot tp <kind>]  finds a lot by TYPE (zoo, biodome, saucer,
        //   balloon, castle, …) rather than a schematic name — the rare landmarks are hard to stumble on.
        //   /cityfind lots  lists the well-known kinds. (Literals win over the greedy name arg in Brigadier,
        //   so "lot"/"lots" never get swallowed as a schematic name — same trick the "tp" literal uses.)
        dispatcher.register(Commands.literal("cityfind")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("tp")
                        .then(Commands.argument("name", StringArgumentType.greedyString())
                                .suggests(SUGGEST_SCHEMATICS)
                                .executes(ctx -> findSchematic(ctx, true))))
                .then(Commands.literal("lots")
                        .executes(CityWorldCommands::listLotKinds))
                .then(Commands.literal("lot")
                        .then(Commands.literal("tp")
                                .then(Commands.argument("kind", StringArgumentType.word())
                                        .suggests(SUGGEST_LOTKINDS)
                                        .executes(ctx -> findLot(ctx, true))))
                        .then(Commands.argument("kind", StringArgumentType.word())
                                .suggests(SUGGEST_LOTKINDS)
                                .executes(ctx -> findLot(ctx, false))))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .suggests(SUGGEST_SCHEMATICS)
                        .executes(ctx -> findSchematic(ctx, false))));

        dispatcher.register(Commands.literal("cityexport")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .executes(ctx -> exportSettings(ctx, "cityworld-settings"))
                .then(Commands.argument("name", StringArgumentType.greedyString())
                        .executes(ctx -> exportSettings(ctx, StringArgumentType.getString(ctx, "name")))));

        // /cwlocate <biome> [tp] — like vanilla /locate biome, but driven by CityWorld's own climate map
        // (vanilla /locate is blind here: the biome source only reports a plains fallback). Add "tp" to
        // teleport to the match.
        dispatcher.register(Commands.literal("cwlocate")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.argument("biome", StringArgumentType.word())
                        .executes(ctx -> locateBiome(ctx, false))
                        .then(Commands.literal("tp")
                                .executes(ctx -> locateBiome(ctx, true)))));
    }

    // ------------------------------------------------------------------ /cityinfo

    private static int cityInfo(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();

        // Read the plan through the public introspection API (the same one other mods use), so this
        // command and CityWorldAPI can never drift apart.
        java.util.Optional<LotInfo> maybe = CityWorldAPI.lotAt(player.level(), player.blockPosition());
        if (maybe.isEmpty()) {
            ctx.getSource().sendFailure(Component.literal("This world is not generated by CityWorld."));
            return 0;
        }
        LotInfo info = maybe.get();

        line(ctx, "at", "chunk " + info.chunk().x + ", " + info.chunk().z);
        line(ctx, "context", info.contextClass() + " (" + info.contextFamily() + ")");
        line(ctx, "lot", info.lotClass() + " (" + info.lotStyle() + ")");
        if (info.shop() != null)
            line(ctx, "shop", info.shop().describe());
        if (info.schematicName() != null)
            line(ctx, "schematic", info.schematicName());
        line(ctx, "roads", Integer.toString(info.roadCount()));
        line(ctx, "nature", String.format("%.0f%%", info.naturePercent() * 100.0));
        return 1;
    }

    private static void line(CommandContext<CommandSourceStack> ctx, String key, String value) {
        ctx.getSource().sendSuccess(() -> Component.literal(key + ": " + value), false);
    }

    // ------------------------------------------------------------------ /cityschem <name>

    private static int pasteSchematic(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        String name = StringArgumentType.getString(ctx, "name");

        Clipboard clip = SchematicLibrary.get(name);
        if (clip == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "No classic schematic named '" + name + "'. Try /cityschem list."));
            return 0;
        }

        BlockPos at = player.blockPosition();
        clip.paste(level, at.getX(), at.getY(), at.getZ(), level.getRandom());
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Pasted '" + clip.name + "' [" + clip.family + "] (" + clip.sizeX + "x" + clip.sizeY + "x"
                        + clip.sizeZ + ") at " + at.getX() + ", " + at.getY() + ", " + at.getZ()), true);
        return 1;
    }

    private static int listSchematics(CommandContext<CommandSourceStack> ctx) {
        java.util.List<String> names = SchematicLibrary.names();
        ctx.getSource().sendSuccess(() -> Component.literal(
                names.size() + " classic schematics: " + String.join(", ", names)), false);
        return 1;
    }

    // ------------------------------------------------------------------ /cityfind <name>

    /** Where a matching building was found. */
    private record Found(String name, String family, int x, int z, double dist, String compass) {}

    /** Rings of platmaps to search out from the player before giving up (10 chunks = 160 blocks each).
     *  Large, because rare wild/ocean builds (a lone lighthouse) can be thousands of blocks out; the
     *  {@link #FIND_BUDGET_MS} wall-clock cap keeps a genuine miss from planning the whole disc. */
    private static final int FIND_MAX_RINGS = 60; // 9600 blocks

    /** Give up the (off-thread) search after this long and report how far it actually reached — each
     *  platmap is a full deterministic plan (~tens of ms), so an unbounded miss could run for minutes.
     *  The search runs off the server thread (the player keeps playing) and platmaps are cached across
     *  calls, so repeated /cityfind from the same area reach steadily further out. */
    private static final long FIND_BUDGET_MS = 120_000;

    private static int findSchematic(CommandContext<CommandSourceStack> ctx, boolean teleport)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        MinecraftServer server = ctx.getSource().getServer();
        String query = StringArgumentType.getString(ctx, "name").trim();

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof CityWorldChunkGenerator cityGenerator)) {
            ctx.getSource().sendFailure(Component.literal("This world is not generated by CityWorld."));
            return 0;
        }
        CityWorldGenerator context = cityGenerator.getContext(level);
        ChunkPos pos = player.chunkPosition();
        double playerX = player.getX();
        double playerZ = player.getZ();

        ctx.getSource().sendSuccess(() -> Component.literal("Searching for '" + query + "'..."), false);

        // Planning is off-thread (a wide sweep can plan hundreds of platmaps — too slow for the server
        // thread's tick watchdog). getPlatMap is deterministic and thread-safe, so this is safe; the
        // result is delivered back on the server thread via server.execute.
        Thread t = new Thread(() -> {
            int[] reachedRing = { 0 };
            Found best = search(context, query, pos.x, pos.z, playerX, playerZ, reachedRing);
            server.execute(() -> {
                if (best == null) {
                    player.sendSystemMessage(Component.literal("Found no '" + query + "' within "
                            + (reachedRing[0] * PlatMap.Width * 16) + " blocks (searched as far as it could "
                            + "in the time budget — try again from closer, or it may be rarer/further out)."));
                    return;
                }
                player.sendSystemMessage(Component.literal("Nearest '" + best.name() + "' [" + best.family()
                        + "] at x=" + best.x() + " z=" + best.z() + "  (" + Math.round(best.dist())
                        + " blocks " + best.compass() + ")"));
                if (teleport) {
                    // force the target chunk to generate so the heightmap is real, then drop onto it
                    level.getChunk(best.x() >> 4, best.z() >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, best.x(), best.z());
                    player.teleportTo(level, best.x() + 0.5, y, best.z() + 0.5, Set.<Relative>of(), player.getYRot(),
                            player.getXRot(), false);
                    player.sendSystemMessage(Component.literal("Teleported to the nearest '" + best.name() + "'."));
                }
            });
        }, "cityworld-find");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static Found search(CityWorldGenerator context, String query, int px, int pz,
            double playerX, double playerZ, int[] reachedRing) {
        String q = query.toLowerCase(Locale.ROOT);
        int pmX0 = Math.floorDiv(px, PlatMap.Width) * PlatMap.Width;
        int pmZ0 = Math.floorDiv(pz, PlatMap.Width) * PlatMap.Width;
        Found best = null;
        int foundRing = -1;
        long deadline = System.nanoTime() + FIND_BUDGET_MS * 1_000_000L;
        for (int r = 0; r <= FIND_MAX_RINGS; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue; // only the outer ring at radius r
                    PlatMap pm = context.getPlatMap(pmX0 + dx * PlatMap.Width, pmZ0 + dz * PlatMap.Width);
                    Found hit = scanPlatMap(pm, q, playerX, playerZ);
                    if (hit != null && (best == null || hit.dist() < best.dist()))
                        best = hit;
                }
            }
            reachedRing[0] = r;
            if (best != null && foundRing < 0)
                foundRing = r;
            // once found, scan one more ring (a nearer one can sit in an adjacent platmap), then stop
            if (foundRing >= 0 && r >= foundRing + 1)
                break;
            // still nothing? stop once the time budget is spent — planning a whole wide disc of platmaps
            // for a build that isn't there (or is very far) would otherwise run for minutes.
            if (best == null && System.nanoTime() > deadline)
                break;
        }
        return best;
    }

    private static Found scanPlatMap(PlatMap pm, String q, double playerX, double playerZ) {
        Found best = null;
        for (int x = 0; x < PlatMap.Width; x++) {
            for (int z = 0; z < PlatMap.Width; z++) {
                if (pm.getLot(x, z) instanceof ClipboardLot clip
                        && clip.getClip().name.toLowerCase(Locale.ROOT).contains(q)) {
                    int wx = clip.getChunkX() * 16 + 8;
                    int wz = clip.getChunkZ() * 16 + 8;
                    double dist = Math.hypot(wx - playerX, wz - playerZ);
                    if (best == null || dist < best.dist())
                        best = new Found(clip.getClip().name, clip.getClip().family.name(), wx, wz, dist,
                                compass(wx - playerX, wz - playerZ));
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ /cityfind lot <kind>

    private static int listLotKinds(CommandContext<CommandSourceStack> ctx) {
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Findable lot kinds (any lot's type also works as a substring): "
                        + String.join(", ", LOT_KINDS)), false);
        return 1;
    }

    /** Find the nearest lot whose class name matches {@code kind} (e.g. "saucer" -> FlyingSaucerLot). Same
     *  off-thread ring-search as {@link #findSchematic}, matching on the planned lot's TYPE rather than a
     *  schematic name — the way to hunt down the rare landmarks (zoos, biodomes, saucers, castles). */
    private static int findLot(CommandContext<CommandSourceStack> ctx, boolean teleport)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        MinecraftServer server = ctx.getSource().getServer();
        String kind = StringArgumentType.getString(ctx, "kind").trim().toLowerCase(Locale.ROOT);

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof CityWorldChunkGenerator cityGenerator)) {
            ctx.getSource().sendFailure(Component.literal("This world is not generated by CityWorld."));
            return 0;
        }
        CityWorldGenerator context = cityGenerator.getContext(level);
        ChunkPos pos = player.chunkPosition();
        double playerX = player.getX();
        double playerZ = player.getZ();

        ctx.getSource().sendSuccess(() -> Component.literal("Searching for a '" + kind + "' lot..."), false);

        Thread t = new Thread(() -> {
            int[] reachedRing = { 0 };
            Found best = searchLot(context, kind, pos.x, pos.z, playerX, playerZ, reachedRing);
            server.execute(() -> {
                if (best == null) {
                    player.sendSystemMessage(Component.literal("Found no '" + kind + "' lot within "
                            + (reachedRing[0] * PlatMap.Width * 16) + " blocks (searched as far as it could in "
                            + "the time budget — it may be rare, style-specific, or further out; try again "
                            + "from closer). /cityfind lots lists the well-known kinds."));
                    return;
                }
                player.sendSystemMessage(Component.literal("Nearest " + best.name() + " [" + best.family()
                        + "] at x=" + best.x() + " z=" + best.z() + "  (" + Math.round(best.dist())
                        + " blocks " + best.compass() + ")"));
                if (teleport) {
                    level.getChunk(best.x() >> 4, best.z() >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, best.x(), best.z());
                    player.teleportTo(level, best.x() + 0.5, y, best.z() + 0.5, Set.<Relative>of(), player.getYRot(),
                            player.getXRot(), false);
                    player.sendSystemMessage(Component.literal("Teleported to the nearest " + best.name() + "."));
                }
            });
        }, "cityworld-findlot");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static Found searchLot(CityWorldGenerator context, String kind, int px, int pz,
            double playerX, double playerZ, int[] reachedRing) {
        int pmX0 = Math.floorDiv(px, PlatMap.Width) * PlatMap.Width;
        int pmZ0 = Math.floorDiv(pz, PlatMap.Width) * PlatMap.Width;
        Found best = null;
        int foundRing = -1;
        long deadline = System.nanoTime() + FIND_BUDGET_MS * 1_000_000L;
        for (int r = 0; r <= FIND_MAX_RINGS; r++) {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue;
                    PlatMap pm = context.getPlatMap(pmX0 + dx * PlatMap.Width, pmZ0 + dz * PlatMap.Width);
                    Found hit = scanPlatMapForLot(pm, kind, playerX, playerZ);
                    if (hit != null && (best == null || hit.dist() < best.dist()))
                        best = hit;
                }
            reachedRing[0] = r;
            if (best != null && foundRing < 0)
                foundRing = r;
            if (foundRing >= 0 && r >= foundRing + 1)
                break;
            if (best == null && System.nanoTime() > deadline)
                break;
        }
        return best;
    }

    private static Found scanPlatMapForLot(PlatMap pm, String kind, double playerX, double playerZ) {
        Found best = null;
        for (int x = 0; x < PlatMap.Width; x++)
            for (int z = 0; z < PlatMap.Width; z++) {
                PlatLot lot = pm.getLot(x, z);
                if (lot == null)
                    continue;
                if (!lot.getClass().getSimpleName().toLowerCase(Locale.ROOT).contains(kind))
                    continue;
                int wx = lot.getChunkX() * 16 + 8;
                int wz = lot.getChunkZ() * 16 + 8;
                double dist = Math.hypot(wx - playerX, wz - playerZ);
                if (best == null || dist < best.dist())
                    best = new Found(prettyLotName(lot.getClass().getSimpleName()), lot.style.name(), wx, wz,
                            dist, compass(wx - playerX, wz - playerZ));
            }
        return best;
    }

    /** "FlyingSaucerLot" -> "Flying Saucer" — drop the Lot suffix and space the camel-case for the report. */
    private static String prettyLotName(String simpleName) {
        String base = simpleName.endsWith("Lot") ? simpleName.substring(0, simpleName.length() - 3) : simpleName;
        return base.replaceAll("(?<=[a-z0-9])(?=[A-Z])", " ");
    }

    private static String compass(double dx, double dz) {
        // Minecraft axes: +X is east, +Z is south.
        String ns = dz < -8 ? "N" : dz > 8 ? "S" : "";
        String ew = dx < -8 ? "W" : dx > 8 ? "E" : "";
        String dir = ns + ew;
        return dir.isEmpty() ? "here" : dir;
    }

    // ------------------------------------------------------------------ /cwlocate <biome>

    /** Chunk rings to search out before giving up (~2560 blocks). */
    private static final int LOCATE_MAX_CHUNK_RINGS = 160;

    private static int locateBiome(CommandContext<CommandSourceStack> ctx, boolean teleport)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();
        MinecraftServer server = ctx.getSource().getServer();
        String raw = StringArgumentType.getString(ctx, "biome").trim().toLowerCase(Locale.ROOT);
        String query = raw.contains(":") ? raw.substring(raw.indexOf(':') + 1) : raw; // strip minecraft:

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof CityWorldChunkGenerator cityGenerator)) {
            ctx.getSource().sendFailure(Component.literal("This world is not generated by CityWorld."));
            return 0;
        }
        if (!(cityGenerator.getBiomeSource() instanceof CityWorldBiomes classifier)) {
            ctx.getSource().sendFailure(
                    Component.literal("This world doesn't use CityWorld's climate biome map (CLASSIC style?)."));
            return 0;
        }
        CityWorldGenerator context = cityGenerator.getContext(level);
        boolean decayed = context.getSettings().includeDecayedNature;
        ChunkPos pos = player.chunkPosition();
        double playerX = player.getX();
        double playerZ = player.getZ();

        ctx.getSource().sendSuccess(() -> Component.literal("Searching for biome '" + query + "'..."), false);

        // The classify is pure and deterministic (terrain height + climate noise), so scan off the server
        // thread like /cityfind; deliver the result back on the server thread.
        Thread t = new Thread(() -> {
            Found best = searchBiome(context, classifier, query, decayed, pos.x, pos.z, playerX, playerZ);
            server.execute(() -> {
                if (best == null) {
                    player.sendSystemMessage(Component.literal("Found no biome matching '" + query + "' within "
                            + (LOCATE_MAX_CHUNK_RINGS * 16) + " blocks."));
                    return;
                }
                player.sendSystemMessage(Component.literal("Nearest '" + best.name() + "' at x=" + best.x() + " z="
                        + best.z() + "  (" + Math.round(best.dist()) + " blocks " + best.compass() + ")"));
                if (teleport) {
                    // force the target chunk to generate so the heightmap is real, then drop onto it
                    level.getChunk(best.x() >> 4, best.z() >> 4);
                    int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, best.x(), best.z());
                    player.teleportTo(level, best.x() + 0.5, y, best.z() + 0.5, Set.<Relative>of(), player.getYRot(),
                            player.getXRot(), false);
                    player.sendSystemMessage(Component.literal("Teleported to the nearest '" + best.name() + "'."));
                }
            });
        }, "cityworld-locate");
        t.setDaemon(true);
        t.start();
        return 1;
    }

    private static Found searchBiome(CityWorldGenerator context, CityWorldBiomes classifier, String query,
            boolean decayed, int pcx, int pcz, double playerX, double playerZ) {
        Found best = null;
        int foundRing = -1;
        for (int r = 0; r <= LOCATE_MAX_CHUNK_RINGS; r++) {
            for (int dx = -r; dx <= r; dx++)
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue; // only the outer ring at radius r
                    int cx = pcx + dx, cz = pcz + dz;
                    String name = biomeAt(context, classifier, decayed, cx, cz);
                    if (name == null || !(name.equals(query) || name.contains(query)))
                        continue;
                    int wx = cx * 16 + 8, wz = cz * 16 + 8;
                    double dist = Math.hypot(wx - playerX, wz - playerZ);
                    if (best == null || dist < best.dist())
                        best = new Found(name, "", wx, wz, dist, compass(wx - playerX, wz - playerZ));
                }
            if (best != null && foundRing < 0)
                foundRing = r;
            if (foundRing >= 0 && r >= foundRing + 1)
                break; // scan one more ring in case a nearer match sits diagonally, then stop
        }
        return best;
    }

    private static String biomeAt(CityWorldGenerator context, CityWorldBiomes classifier, boolean decayed, int cx,
            int cz) {
        try {
            AbstractCachedYs ys = context.shapeProvider.getCachedYs(context, cx, cz);
            int terrainY = ys.getBlockY(8, 8);
            double temp = context.getTemperature(cx * 16 + 8, cz * 16 + 8);
            double humid = context.getHumidity(cx * 16 + 8, cz * 16 + 8);
            return classifier.classify(context, terrainY, temp, humid, decayed).unwrapKey()
                    .map(k -> k.identifier().getPath()).orElse(null);
        } catch (Throwable t) {
            return null;
        }
    }

    // ------------------------------------------------------------------ /cityexport [name]

    /**
     * Bottles the current world's <em>effective</em> settings into a ready-to-use datapack under
     * {@code config/cityworld/exports/<name>/}. The intended flow: trial a world in single-player
     * (Customize screen / a world style), then export it here to get a datapack to drop on a server.
     * Writes the entry as {@code default.json}, so dropping the pack into any CityWorld world (whose
     * presets reference {@code cityworld:default}) applies these settings.
     */
    private static int exportSettings(CommandContext<CommandSourceStack> ctx, String rawName)
            throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        ServerLevel level = player.level();

        ChunkGenerator generator = level.getChunkSource().getGenerator();
        if (!(generator instanceof CityWorldChunkGenerator cityGenerator)) {
            ctx.getSource().sendFailure(Component.literal("This world is not generated by CityWorld."));
            return 0;
        }

        String name = sanitize(rawName);
        if (name.isEmpty())
            name = "cityworld-settings";
        me.daddychurchill.CityWorld.worldgen.CityWorldSettingsData data =
                cityGenerator.getContext(level).getSettings().toData();
        java.nio.file.Path root = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get()
                .resolve(CityWorldMod.MODID).resolve("exports").resolve(name);
        try {
            me.daddychurchill.CityWorld.worldgen.SettingsDatapack.writePack(root,
                    me.daddychurchill.CityWorld.worldgen.SettingsDatapack.DEFAULT_ENTRY, data,
                    "CityWorld settings exported from " + level.dimension().identifier());
        } catch (java.io.IOException | RuntimeException e) {
            ctx.getSource().sendFailure(Component.literal("Export failed: " + e.getMessage()));
            return 0;
        }

        final String shown = name;
        ctx.getSource().sendSuccess(() -> Component.literal(
                "Exported this world's settings to config/cityworld/exports/" + shown
                        + " — copy that folder into <world>/datapacks/ to use it."), true);
        return 1;
    }

    /** Folds a free-text name down to a safe folder name (lowercase, {@code [a-z0-9_-]}). */
    private static String sanitize(String s) {
        return s.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]+", "-").replaceAll("(^-+|-+$)", "");
    }

    // ------------------------------------------------------------------ /cityworld [leave]

    private static int teleport(CommandContext<CommandSourceStack> ctx, ResourceKey<Level> destination,
            String message) throws CommandSyntaxException {
        ServerPlayer player = ctx.getSource().getPlayerOrException();
        MinecraftServer server = ctx.getSource().getServer();

        ServerLevel target = server.getLevel(destination);
        if (target == null) {
            ctx.getSource().sendFailure(Component.literal(
                    "Destination dimension " + destination.identifier() + " is not loaded."));
            return 0;
        }
        if (player.level() == target) {
            ctx.getSource().sendFailure(Component.literal("You are already there."));
            return 0;
        }

        // Keep the player's horizontal position; drop them onto the surface at the destination. Force
        // the chunk to generate first so the heightmap is real rather than a default.
        int x = player.getBlockX();
        int z = player.getBlockZ();
        target.getChunk(x >> 4, z >> 4);
        int y = target.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);

        ctx.getSource().sendSuccess(() -> Component.literal(message), false);
        player.teleportTo(target, x + 0.5, y, z + 0.5, Set.<Relative>of(), player.getYRot(), player.getXRot(), false);
        return 1;
    }
}
