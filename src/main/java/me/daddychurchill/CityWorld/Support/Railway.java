package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import net.minecraft.core.Holder;
import net.minecraft.core.UUIDUtil;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.ShiftedRegion;

/**
 * The railway kit: what the subway lays its track from, and what it does for the train mods a player may have
 * (GitHub issue #7, 2026-11-14; see TRAINS-PLAN.md). Everything here is a pool or a check, and every pool ships
 * holding only optional entries, so a world without those mods lays the vanilla rails it always did.
 *
 * <ul>
 * <li><b>Track.</b> {@code #cityworld:fittings/rail} for plain track, {@code fittings/rail_booster} for the powered
 *     lengths, {@code fittings/rail_ruined} for a ruin's track, {@code fittings/rail_buffer_stop} for the end of a
 *     dead-end platform, {@code fittings/rail_stop} for the one length of each platform track a train waits on.
 *     Railcraft Reborn supplies all five (reinforced, booster, abandoned, buffer stop, delayed locking track). One
 *     block per pool per world: the first by id, so a pack that lists two gets one, the same everywhere.</li>
 * <li><b>Stops.</b> Improvement Minecarts stops a train over a lapis block under a rail (a gold block: a terminus,
 *     where it waits, reverses and leaves) and announces the first line of a sign beside that rail. With the mod
 *     installed, as a mod or as a datapack, each platform track gets its stop and a sign with the station's name.</li>
 * <li><b>Parked trains.</b> {@code #cityworld:subway/locomotive} and {@code #cityworld:subway/carriage} (entity type
 *     tags): a locomotive and carriages behind it, stood on one platform track of a station. Choo Choo supplies
 *     both, and its trains are coupled once they are loaded ({@link TrainCouplings}).</li>
 * </ul>
 */
public final class Railway {

    private Railway() {
    }

    public static final TagKey<net.minecraft.world.entity.EntityType<?>> LOCOMOTIVE = entityTag("cityworld:subway/locomotive");
    public static final TagKey<net.minecraft.world.entity.EntityType<?>> CARRIAGE = entityTag("cityworld:subway/carriage");

    private static TagKey<net.minecraft.world.entity.EntityType<?>> entityTag(String id) {
        return TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(id));
    }

    // ---- track ---------------------------------------------------------------------------------

    private static Material first(TagKey<net.minecraft.world.level.block.Block> tag) {
        List<Material> pool = MaterialTags.resolve(tag);
        return pool.isEmpty() ? null : pool.get(0);
    }

    /** Plain track: the pool's, else vanilla rail. A ruin takes its own pool first. */
    public static Material track(boolean ruined) {
        Material m = ruined ? first(MaterialTags.FITTINGS_RAIL_RUINED) : null;
        if (m == null)
            m = first(MaterialTags.FITTINGS_RAIL);
        return m == null ? Material.RAIL : m;
    }

    /** A powered length: the pool's, else vanilla powered rail. Laid over a redstone block either way. */
    public static Material booster() {
        Material m = first(MaterialTags.FITTINGS_RAIL_BOOSTER);
        return m == null ? Material.POWERED_RAIL : m;
    }

    /** The end of a dead-end platform track, or null: the vanilla end is an iron block across the rail. */
    public static Material bufferStop() {
        return first(MaterialTags.FITTINGS_RAIL_BUFFER_STOP);
    }

    /** The length of platform track a train waits on, or null (Railcraft's delayed locking track). */
    public static Material platformStop() {
        return first(MaterialTags.FITTINGS_RAIL_STOP);
    }

    // ---- Improvement Minecarts -------------------------------------------------------------------

    /** The block under a platform's stop rail (Improvement Minecarts' default station block), and a terminus's. */
    public static final Material STOP_BLOCK = Material.LAPIS_BLOCK;
    public static final Material TERMINUS_BLOCK = Material.GOLD_BLOCK;

    private static final java.util.Map<MinecraftServer, Boolean> STOPS =
            java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

    /**
     * Whether this world marks its stations' stops: the setting is on and Improvement Minecarts is there — as a
     * mod jar (its mod id) or as a world datapack (its tick function), since it ships as both. Asked per station,
     * answered once per server.
     */
    public static boolean marksStops(CityWorldGenerator generator) {
        if (!generator.getSettings().subwayStops)
            return false;
        if (net.neoforged.fml.ModList.get() != null && net.neoforged.fml.ModList.get().isLoaded("improvement_minecarts"))
            return true;
        MinecraftServer server = net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null)
            return false;
        return STOPS.computeIfAbsent(server, s -> {
            try {
                return s.getFunctions().get(ResourceLocation.fromNamespaceAndPath("improvement_minecarts", "tick")).isPresent();
            } catch (RuntimeException e) {
                return false;
            }
        });
    }

    // ---- parked trains --------------------------------------------------------------------------

    private static List<net.minecraft.world.entity.EntityType<?>> types(TagKey<net.minecraft.world.entity.EntityType<?>> tag) {
        List<net.minecraft.world.entity.EntityType<?>> out = new ArrayList<>();
        for (Holder<net.minecraft.world.entity.EntityType<?>> h : BuiltInRegistries.ENTITY_TYPE.getTagOrEmpty(tag))
            out.add(h.value());
        out.sort(java.util.Comparator.comparing(t -> BuiltInRegistries.ENTITY_TYPE.getKey(t).toString()));
        return out;
    }

    /** Whether there is a train to park: a locomotive at least. */
    public static boolean anyTrain() {
        return !types(LOCOMOTIVE).isEmpty();
    }

    /** The scoreboard tag a parked vehicle carries naming the vehicle it couples to, ahead of it in the train. */
    public static final String COUPLE_TAG = "cityworld.couple:";

    /** Centre-to-centre spacing of parked vehicles, in blocks (Choo Choo's, measured: see TRAINS-PLAN.md). */
    static final double SPACING = Double.parseDouble(System.getProperty("cityworld.train.spacing", "3.0"));

    /**
     * Stands a train on one track: a locomotive at {@code (x, z)} (world block coordinates of its rail) facing
     * {@code (dx, dz)} (a unit step along the track), then up to {@code carriages} carriages behind it, each
     * spaced along the track and tagged with the one ahead so {@link TrainCouplings} joins them when they load.
     * {@code room} is how many blocks of track lie behind the locomotive's rail. False when there is no
     * locomotive in the pool, or the chunk is not the one being generated.
     */
    public static boolean parkTrain(LevelAccessor level, int x, int railY, int z, int dx, int dz, int carriages,
            int room, Odds odds) {
        List<net.minecraft.world.entity.EntityType<?>> locos = types(LOCOMOTIVE), cars = types(CARRIAGE);
        if (locos.isEmpty())
            return false;
        var access = level.getChunk(x >> 4, z >> 4);
        if (!(access instanceof ProtoChunk chunk) || access instanceof net.minecraft.world.level.chunk.ImposterProtoChunk)
            return false;
        double y = railY + 0.0625 + ShiftedRegion.shiftOf(level);
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz)); // vanilla: 0 south, 90 west
        UUID ahead = null;
        int n = cars.isEmpty() ? 0 : Math.min(carriages, (int) Math.floor(room / SPACING));
        for (int i = 0; i <= n; i++) {
            var type = i == 0 ? locos.get(odds.getRandomInt(locos.size())) : cars.get(odds.getRandomInt(cars.size()));
            double cx = x + 0.5 - dx * SPACING * i, cz = z + 0.5 - dz * SPACING * i;
            UUID id = new UUID(odds.getRandomLong(), odds.getRandomLong());
            CompoundTag tag = new CompoundTag();
            tag.putString("id", BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(cx));
            pos.add(DoubleTag.valueOf(y));
            pos.add(DoubleTag.valueOf(cz));
            tag.put("Pos", pos);
            ListTag motion = new ListTag();
            for (int k = 0; k < 3; k++)
                motion.add(DoubleTag.valueOf(0));
            tag.put("Motion", motion);
            ListTag rotation = new ListTag();
            rotation.add(FloatTag.valueOf(yaw));
            rotation.add(FloatTag.valueOf(0f));
            tag.put("Rotation", rotation);
            tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(id));
            // Choo Choo keeps headings of its own: the locomotive's as a vector, a carriage's as its "nose" yaw
            // (degrees of atan2(z, x): 0 is +x). A carriage left at its default nose lies east-west whatever its
            // track, so on a north-south line its ends stood beside its neighbours' and it never coupled.
            tag.putDouble("ForwardX", dx);
            tag.putDouble("ForwardZ", dz);
            tag.putFloat("PassengerNose", (float) Math.toDegrees(Math.atan2(dz, dx)));
            if (ahead != null) {
                ListTag tags = new ListTag();
                tags.add(StringTag.valueOf(COUPLE_TAG + ahead));
                tag.put("Tags", tags);
            }
            chunk.addEntity(tag);
            ahead = id;
        }
        return true;
    }
}
