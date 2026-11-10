package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlaceNames;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ImposterProtoChunk;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Anything a pack wants in the city — a mob, an NPC from another mod, a parked vehicle, a block entity (a vending
 * machine, a mod's workstation) — by where it belongs: datapack registry {@code cityworld:spawn}, one file per thing
 * ({@code data/<ns>/cityworld/spawn/<name>.json}). Written straight into the chunk being generated, as a saved chunk
 * holds them, so nothing touches the live level (see the deadlock rule in CLAUDE.md).
 *
 * <p>A file names one of {@code entity} ({@code /summon} form: {@code "minecraft:villager{VillagerData:{...}}"}),
 * {@code vehicle} (a {@link Vehicles} kind: {@code car}, {@code fire_engine} ...) or {@code block} ({@code /setblock}
 * form, its data included); where — {@code lots} (lot class names such as {@code ParkingLot}, or the lot kinds
 * {@code structure}, {@code road}, {@code roundabout}, {@code nature}), {@code districts} ({@code highrise},
 * {@code municipal}, {@code park} ... the district's building family), {@code styles} ({@code modern},
 * {@code apocalypse} ...), {@code places} (place names, {@link PlaceNames}), {@code environments} ({@code normal},
 * {@code nether}, {@code the_end}) and {@code land} ({@code any}, {@code cityworld}, {@code vanilla}), each empty for
 * any; {@code at} ({@code outdoors}: on top of the ground or a roof; {@code indoors}: a floor with something over it;
 * {@code anywhere}); and how many: {@code odds} per chunk, then {@code count} to {@code count_max} of them. An entity
 * is kept (no despawning) unless {@code persistent} is false or its own data says otherwise.
 *
 * <p>Each file rolls on its own stream from the world seed, the chunk and its id, so adding or removing a file never
 * moves anything else. Nothing ships in this registry: an untouched world draws exactly what it drew before.
 */
public final class Spawns {

    private Spawns() {
    }

    public record Spawn(Optional<String> requiresMod, Optional<String> entity, Optional<String> vehicle,
            Optional<String> block, List<String> lots, List<String> schematics, List<String> districts,
            List<String> styles, List<String> places, List<String> environments, String land, String at,
            double odds, int count, int countMax, boolean persistent) {

        private static final Codec<List<String>> LIST = Codec.STRING.listOf();

        public static final Codec<Spawn> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(Spawn::requiresMod),
                Codec.STRING.optionalFieldOf("entity").forGetter(Spawn::entity),
                Codec.STRING.optionalFieldOf("vehicle").forGetter(Spawn::vehicle),
                Codec.STRING.optionalFieldOf("block").forGetter(Spawn::block),
                LIST.optionalFieldOf("lots", List.of()).forGetter(Spawn::lots),
                LIST.optionalFieldOf("schematics", List.of()).forGetter(Spawn::schematics),
                LIST.optionalFieldOf("districts", List.of()).forGetter(Spawn::districts),
                LIST.optionalFieldOf("styles", List.of()).forGetter(Spawn::styles),
                LIST.optionalFieldOf("places", List.of()).forGetter(Spawn::places),
                LIST.optionalFieldOf("environments", List.of()).forGetter(Spawn::environments),
                Codec.STRING.optionalFieldOf("land", "any").forGetter(Spawn::land),
                Codec.STRING.optionalFieldOf("at", "outdoors").forGetter(Spawn::at),
                Codec.doubleRange(0, 1).optionalFieldOf("odds", 1.0).forGetter(Spawn::odds),
                Codec.intRange(0, 64).optionalFieldOf("count", 1).forGetter(Spawn::count),
                Codec.intRange(0, 64).optionalFieldOf("count_max", 0).forGetter(Spawn::countMax),
                Codec.BOOL.optionalFieldOf("persistent", true).forGetter(Spawn::persistent))
                .apply(i, Spawn::new));
    }

    public static final ResourceKey<Registry<Spawn>> SPAWNS = ResourceKey
            .createRegistryKey(new ResourceLocation(CityWorldMod.MODID, "spawn"));

    /** A file, read: its id (the stream's salt), the entity's id and data or the block, already parsed. */
    record Bound(String id, Spawn spawn, String entityId, CompoundTag entityData, BlockState state,
            CompoundTag blockData) {
    }

    private static volatile List<Bound> bound = List.of();

    /** Reads the registry off a loaded level; a file whose mod is absent is left out quietly, a broken one loudly. */
    public static void bind(RegistryAccess access) {
        try {
            Registry<Spawn> registry = access.registryOrThrow(SPAWNS);
            var files = new ArrayList<>(registry.entrySet());
            files.sort(Comparator.comparing(e -> e.getKey().location().toString()));
            var blockLookup = access.lookupOrThrow(Registries.BLOCK);
            List<Bound> out = new ArrayList<>();
            List<String> problems = new ArrayList<>();
            for (var e : files) {
                String id = e.getKey().location().toString();
                Spawn s = e.getValue();
                if (!s.requiresMod().map(Spawns::loaded).orElse(true))
                    continue;
                try {
                    String entityId = null;
                    CompoundTag entityData = null, blockData = null;
                    BlockState state = null;
                    if (s.entity().isPresent()) {
                        String text = s.entity().get().trim();
                        int brace = text.indexOf('{');
                        entityId = (brace < 0 ? text : text.substring(0, brace)).trim();
                        if (!entityId.contains(":"))
                            entityId = "minecraft:" + entityId;
                        if (!loaded(entityId.substring(0, entityId.indexOf(':'))))
                            continue;
                        final String wanted = entityId; // the registry's keys, by name: the same on every line
                        if (BuiltInRegistries.ENTITY_TYPE.keySet().stream().noneMatch(k -> k.toString().equals(wanted))) {
                            problems.add(id + ": no entity '" + entityId + "'");
                            continue;
                        }
                        entityData = brace < 0 ? new CompoundTag() : parseCompound(text.substring(brace));
                    } else if (s.block().isPresent()) {
                        var parsed = BlockStateParser.parseForBlock(blockLookup, s.block().get(), true);
                        state = parsed.blockState();
                        blockData = parsed.nbt();
                    } else if (s.vehicle().isEmpty()) {
                        problems.add(id + ": names no entity, vehicle or block");
                        continue;
                    }
                    out.add(new Bound(id, s, entityId, entityData, state, blockData));
                } catch (Exception ex) {
                    problems.add(id + ": " + ex.getMessage());
                }
            }
            bound = List.copyOf(out);
            String report = out.size() + " file(s)" + (problems.isEmpty() ? "" : ", skipped: " + problems);
            if ((!out.isEmpty() || !problems.isEmpty()) && LOGGED.add(report))
                CityWorldMod.LOGGER.info("CityWorld spawns: {}", report);
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld spawns: could not read the registry: {}", e.toString());
        }
    }

    /** How many files this world uses (the self-test's count). */
    public static int count() {
        return bound.size();
    }

    public static CompoundTag parseCompound(String snbt) throws Exception {
        return TagParser.parseTag(snbt);
    }

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean loaded(String mod) {
        return mod.equals("minecraft") || net.minecraftforge.fml.ModList.get().isLoaded(mod);
    }

    /** Places what belongs in this chunk; called once a lot has drawn everything it draws. */
    public static void apply(CityWorldGenerator generator, PlatMap platmap, PlatLot lot, RealBlocks chunk) {
        List<Bound> list = bound;
        if (list.isEmpty() || lot == null)
            return;
        String place = null;
        java.util.Map<String, List<int[]>> found = new java.util.HashMap<>(); // the cells, read once per kind of spot
        for (Bound b : list) {
            Spawn s = b.spawn();
            if (!matches(generator, platmap, lot, s))
                continue;
            if (!s.places().isEmpty()) {
                if (place == null)
                    place = PlaceNames.at(generator, chunk.sectionX, chunk.sectionZ);
                if (!containsIgnoreCase(s.places(), place))
                    continue;
            }
            Odds odds = new Odds(generator.getWorldSeed() ^ ((long) chunk.sectionX * 341873128712L)
                    ^ ((long) chunk.sectionZ * 132897987541L) ^ ((long) b.id().hashCode() << 20));
            if (!odds.playOdds(s.odds()))
                continue;
            int n = s.countMax() > s.count() ? s.count() + odds.getRandomInt(s.countMax() - s.count() + 1) : s.count();
            if (n <= 0)
                continue;
            List<int[]> spots = new ArrayList<>(found.computeIfAbsent(s.at().toLowerCase(Locale.ROOT),
                    at -> at.equals("driveway") ? drivewaySpots(generator, lot, chunk) : spots(generator, chunk, at)));
            for (int k = 0; k < n && !spots.isEmpty(); k++) {
                int[] p = spots.remove(odds.getRandomInt(spots.size()));
                place(generator, chunk, b, p[0], p[1], p[2], p.length > 3 ? p[3] : -1, odds);
            }
        }
    }

    private static boolean matches(CityWorldGenerator generator, PlatMap platmap, PlatLot lot, Spawn s) {
        if (!s.lots().isEmpty() && !containsIgnoreCase(s.lots(), lot.getClass().getSimpleName())
                && !(lot.style != null && containsIgnoreCase(s.lots(), lot.style.name())))
            return false;
        if (!s.schematics().isEmpty() && !(lot instanceof me.daddychurchill.CityWorld.Clipboard.ClipboardLot cl
                && containsIgnoreCase(s.schematics(), cl.getClip().name)))
            return false;
        if (!s.districts().isEmpty()) {
            var family = platmap.context == null ? null : platmap.context.getSchematicFamily();
            if (family == null || !containsIgnoreCase(s.districts(), family.name()))
                return false;
        }
        if (!s.styles().isEmpty() && !containsIgnoreCase(s.styles(), generator.worldStyle.name()))
            return false;
        if (!s.environments().isEmpty() && !containsIgnoreCase(s.environments(), generator.worldEnvironment.name()))
            return false;
        boolean vanillaLand = generator.citySites != null;
        return switch (s.land().toLowerCase(Locale.ROOT)) {
            case "cityworld" -> !vanillaLand;
            case "vanilla" -> vanillaLand;
            default -> true;
        };
    }

    private static boolean containsIgnoreCase(List<String> list, String value) {
        for (String s : list)
            if (s.equalsIgnoreCase(value))
                return true;
        return false;
    }

    /**
     * The cells a thing may stand in, in a fixed order: feet and head clear, a sturdy top underfoot. Outdoors is the
     * first such cell down each column (ground, a roof, a road); indoors any with something within 16 blocks over it.
     */
    static List<int[]> spots(CityWorldGenerator generator, RealBlocks chunk, String at) {
        boolean outdoors = !"indoors".equalsIgnoreCase(at), indoors = !"outdoors".equalsIgnoreCase(at);
        int low = generator.streetLevel - 48; // below the world, cells read as void air: nothing to stand on
        int high = Math.min(generator.height - 2, generator.streetLevel + 160);
        List<int[]> out = new ArrayList<>();
        for (int x = 0; x < 16; x++)
            for (int z = 0; z < 16; z++) {
                int top = high;
                while (top > low && chunk.isEmpty(x, top, z))
                    top--;
                if (outdoors && top > low && top + 2 < high && chunk.isSturdyTop(x, top, z))
                    out.add(new int[] { x, top + 1, z });
                if (!indoors)
                    continue;
                for (int y = low + 1; y < top - 1; y++) {
                    if (!chunk.isEmpty(x, y, z) || !chunk.isEmpty(x, y + 1, z) || !chunk.isSturdyTop(x, y - 1, z))
                        continue;
                    for (int up = y + 2; up < Math.min(top + 1, y + 17); up++)
                        if (!chunk.isEmpty(x, up, z)) {
                            out.add(new int[] { x, y, z });
                            break;
                        }
                }
            }
        return out;
    }

    /**
     * The cells at a schematic's driveways ({@code at: driveway}): the build's edge row at each driveway and the
     * paving out to the road, each with the yaw that faces the road; nothing for a lot that is not a schematic.
     */
    static List<int[]> drivewaySpots(CityWorldGenerator generator, PlatLot lot, RealBlocks chunk) {
        List<int[]> out = new ArrayList<>();
        if (!(lot instanceof me.daddychurchill.CityWorld.Clipboard.ClipboardLot cl))
            return out;
        int low = generator.streetLevel - 48, high = Math.min(generator.height - 2, generator.streetLevel + 160);
        for (int[] c : cl.drivewayStands(generator)) {
            int x = c[0], z = c[1], top = high;
            while (top > low && chunk.isEmpty(x, top, z))
                top--;
            if (top > low && top + 2 < high && chunk.isSturdyTop(x, top, z))
                out.add(new int[] { x, top + 1, z, c[2] });
        }
        return out;
    }

    /** {@code yaw} is the facing (Minecraft's: 0 south, 90 west, 180 north, 270 east), or -1 for any. */
    private static void place(CityWorldGenerator generator, RealBlocks chunk, Bound b, int x, int y, int z, int yaw,
            Odds odds) {
        double wx = chunk.getBlockX(x) + 0.5, wz = chunk.getBlockZ(z) + 0.5;
        int heading = yaw < 0 ? odds.getRandomInt(4) * 90 : yaw;
        if (b.spawn().vehicle().isPresent()) {
            // a vehicle's heading runs the other way round (forward is (sin, cos)): east is 90 there, 270 here
            Vehicles.park(chunk.getWorld(), b.spawn().vehicle().get(), wx, y, wz, (360 - heading) % 360,
                    generator.getSettings().vehicleFuel, odds);
            return;
        }
        var access = chunk.getWorld().getChunk(chunk.sectionX, chunk.sectionZ);
        if (!(access instanceof ProtoChunk proto) || access instanceof ImposterProtoChunk)
            return;
        int lift = ShiftedRegion.shiftOf(chunk.getWorld()); // a city drawn below its level (vanilla land) stands lifted
        if (b.state() != null) {
            var cell = chunk.getActualBlock(x, y, z);
            cell.setBlockData(b.state(), false);
            BlockState placed = cell.getBlockData(); // after any block substitute
            if (b.blockData() != null && placed.getBlock() instanceof EntityBlock eb) {
                BlockPos pos = new BlockPos(chunk.getBlockX(x), y + lift, chunk.getBlockZ(z));
                var be = eb.newBlockEntity(pos, placed);
                if (be != null) {
                    CompoundTag tag = b.blockData().copy();
                    tag.putString("id", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
                    tag.putInt("x", pos.getX());
                    tag.putInt("y", pos.getY());
                    tag.putInt("z", pos.getZ());
                    proto.setBlockEntityNbt(tag);
                }
            }
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("id", b.entityId());
        ListTag pos = new ListTag();
        pos.add(DoubleTag.valueOf(wx));
        pos.add(DoubleTag.valueOf(y + lift));
        pos.add(DoubleTag.valueOf(wz));
        tag.put("Pos", pos);
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf(heading));
        rotation.add(FloatTag.valueOf(0f));
        tag.put("Rotation", rotation);
        if (b.spawn().persistent())
            tag.putBoolean("PersistenceRequired", true);
        tag.merge(b.entityData());
        proto.addEntity(tag);
    }
}
