package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Support.Odds;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.UUIDUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ProtoChunk;

/**
 * Immersive Vehicles' vehicles, parked by CityWorld: registry {@code cityworld:vehicle}, files at
 * {@code data/<ns>/cityworld/vehicle/<name>.json} (CityWorld's own are written from the content packs by
 * {@code scripts/gen_iv_vehicles.py}).
 *
 * <p>A file names a {@code vehicle} ({@code <pack>:<system name>}), its {@code kind} (what a lot asks for: {@code car}
 * fills the car parks), the {@code paints} (sub names) to choose from, the {@code centre} of its wheelbase forward of
 * its origin (so it stands in the middle of a bay), {@code requires_mod} (its pack), and its {@code parts} by slot:
 * IV gives a vehicle loaded from data only the parts its data names — a player's spawn adds the defaults — so a file
 * lists the wheels, seats, engine and the rest, each with the sub names to choose from (one pick per part per vehicle,
 * so the seats match). {@code fuel_capacity} holds a parked vehicle's fuel to its tank. A vehicle's starting
 * variables (a crafted car comes with its doors and bonnet open) are left out: a parked car has its doors shut.
 *
 * <p>A vehicle is written as entity data into the chunk being generated ({@link ProtoChunk#addEntity(CompoundTag)}):
 * IV builds it from that data on its first tick, exactly as when a saved chunk loads. CityWorld calls nothing of IV's
 * and needs it only at run time; without it, or without a file's pack, there is nothing to park.
 */
public final class Vehicles {

    private Vehicles() {
    }

    public record Part(int slot, String part, List<String> subs) {
        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("slot").forGetter(Part::slot),
                Codec.STRING.fieldOf("part").forGetter(Part::part),
                Codec.STRING.listOf().optionalFieldOf("subs", List.of("")).forGetter(Part::subs))
                .apply(i, Part::new));
    }

    public record Vehicle(String vehicle, String kind, List<String> paints, double centre, Optional<String> requiresMod,
            int fuelCapacity, List<Part> parts) {
        public static final Codec<Vehicle> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("vehicle").forGetter(Vehicle::vehicle),
                Codec.STRING.fieldOf("kind").forGetter(Vehicle::kind),
                Codec.STRING.listOf().optionalFieldOf("paints", List.of("")).forGetter(Vehicle::paints),
                Codec.DOUBLE.optionalFieldOf("centre", 0.0).forGetter(Vehicle::centre),
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(Vehicle::requiresMod),
                Codec.INT.optionalFieldOf("fuel_capacity", 0).forGetter(Vehicle::fuelCapacity),
                Part.CODEC.listOf().optionalFieldOf("parts", List.of()).forGetter(Vehicle::parts))
                .apply(i, Vehicle::new));
    }

    public static final ResourceKey<Registry<Vehicle>> VEHICLES = ResourceKey
            .createRegistryKey(ResourceLocation.fromNamespaceAndPath(CityWorldMod.MODID, "vehicle"));

    /** The installed vehicles by kind, in id order (a seed picks the same one every time). */
    private static volatile Map<String, List<Vehicle>> byKind = Map.of();

    /** Reads the registry off a loaded level, keeping the vehicles whose pack (and IV) is installed. */
    public static void bind(RegistryAccess access) {
        try {
            if (!loaded("mts")) {
                byKind = Map.of();
                return;
            }
            Registry<Vehicle> registry = access.registryOrThrow(VEHICLES);
            var files = new ArrayList<>(registry.entrySet());
            files.sort(Comparator.comparing(e -> e.getKey().location().toString()));
            Map<String, List<Vehicle>> kinds = new HashMap<>();
            for (var e : files) {
                Vehicle v = e.getValue();
                if (v.requiresMod().map(Vehicles::loaded).orElse(true) && loaded(v.vehicle().split(":")[0]))
                    kinds.computeIfAbsent(v.kind(), k -> new ArrayList<>()).add(v);
            }
            Map<String, List<Vehicle>> frozen = new HashMap<>();
            kinds.forEach((k, list) -> frozen.put(k, List.copyOf(list)));
            byKind = Map.copyOf(frozen);
            if (!frozen.isEmpty() && LOGGED.add(String.valueOf(frozen.keySet()) + frozen.values()))
                CityWorldMod.LOGGER.info("CityWorld vehicles: {}", frozen.entrySet().stream()
                        .map(e -> e.getValue().size() + " " + e.getKey()).sorted().toList());
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld vehicles: could not read the registry: {}", e.toString());
        }
    }

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean loaded(String mod) {
        return net.neoforged.fml.ModList.get().isLoaded(mod);
    }

    /** Whether there is a vehicle of that kind to park. */
    public static boolean any(String kind) {
        return byKind.containsKey(kind);
    }

    /**
     * Parks a vehicle of that kind, its wheelbase centred on ({@code x}, {@code z}) and its wheels on {@code y} (world
     * coordinates), heading {@code heading} degrees (0 south, 90 east, 180 north, 270 west: IV's yaw), with the brake
     * on, a charged battery and {@code fuel} millibuckets of lava (IV's stock fuel). False when there is none of that
     * kind, or the column is not in a chunk being generated.
     */
    public static boolean park(LevelAccessor level, String kind, double x, double y, double z, double heading,
            int fuel, Odds odds) {
        List<Vehicle> list = byKind.get(kind);
        if (list == null || list.isEmpty())
            return false;
        Vehicle v = list.get(odds.getRandomInt(list.size()));
        heading = safeHeading(heading);
        double rad = Math.toRadians(heading);
        // the origin sits `centre` behind the wheelbase's middle: forward is (sin, cos) in IV's yaw
        double ox = x - Math.sin(rad) * v.centre(), oz = z - Math.cos(rad) * v.centre();
        var access = level.getChunk(net.minecraft.util.Mth.floor(ox) >> 4, net.minecraft.util.Mth.floor(oz) >> 4);
        // only the chunk being generated: a full neighbour hands out an ImposterProtoChunk, which drops entity data
        if (!(access instanceof ProtoChunk chunk) || access instanceof net.minecraft.world.level.chunk.ImposterProtoChunk)
            return false;
        y += ShiftedRegion.shiftOf(level); // a city drawn below its level (vanilla land) stands lifted
        String[] id = v.vehicle().split(":", 2);
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "mts:builder_existing");
        tag.put("Pos", doubles(ox, y, oz));
        tag.put("Motion", doubles(0, 0, 0));
        ListTag rotation = new ListTag();
        rotation.add(FloatTag.valueOf((float) -heading));
        rotation.add(FloatTag.valueOf(0f));
        tag.put("Rotation", rotation);
        tag.putIntArray("UUID", UUIDUtil.uuidToIntArray(uuid(odds)));
        tag.putString("entityid", "EntityVehicleF_Physics");
        tag.putString("packID", id[0]);
        tag.putString("systemName", id[1]);
        tag.putString("subName", v.paints().get(odds.getRandomInt(v.paints().size())));
        tag.putString("uniqueUUID", uuid(odds).toString());
        tag.putDouble("positionx", ox);
        tag.putDouble("positiony", y);
        tag.putDouble("positionz", oz);
        if (heading != 0)
            tag.putDouble("anglesy", heading);
        tag.putDouble("electricPower", 12.0);
        tag.putDouble("p_brake", 1.0);
        if (v.fuelCapacity() > 0)
            fuel = Math.min(fuel, v.fuelCapacity());
        if (fuel > 0) {
            CompoundTag tank = new CompoundTag();
            tank.putString("currentFluid", "lava");
            tank.putString("currentFluidMod", "minecraft");
            tank.putDouble("fluidLevel", fuel);
            tag.put("fuelTank", tank);
        }
        Map<String, String> picked = new HashMap<>();
        for (Part p : v.parts()) {
            String[] pid = p.part().split(":", 2);
            if (pid.length < 2 || !loaded(pid[0]) && !pid[0].equals("mts"))
                continue;
            String sub = picked.computeIfAbsent(p.part(), k -> p.subs().isEmpty() ? ""
                    : p.subs().get(odds.getRandomInt(p.subs().size())));
            CompoundTag part = new CompoundTag();
            part.putString("packID", pid[0]);
            part.putString("systemName", pid[1]);
            part.putString("subName", sub);
            tag.put("part_" + p.slot(), part);
        }
        chunk.addEntity(tag);
        return true;
    }

    /** Degrees a parked heading keeps away from exactly 180: half a degree moves a five-block car's tail 0.04 blocks. */
    static final double HEADING_NUDGE = 0.5;

    /**
     * A heading Immersive Vehicles can interpolate from. A yaw of exactly 180 is a rotation whose quaternion has
     * w = 0, and IV up to 24.0.0 divides by it when it blends the previous and current orientation for the camera
     * and the renderer, so the first frame such a vehicle turns, its orientation is NaN, the camera's angles are
     * NaN and vanilla's {@code Frustum.offsetToFullyIncludeCameraCube} loops for ever: the client freezes while
     * the server drives on (the owner's two lock-ups driving a parked car, 2026-11-10, read from a heap dump of
     * the frozen game; fixed upstream on 2026-10-27, after 24.0.0). Car-park bays and kerbs face the cardinal
     * directions, so a quarter of parked cars faced exactly 180 and every one of them froze the game when driven.
     */
    public static double safeHeading(double heading) {
        double h = ((heading % 360) + 360) % 360;
        return Math.abs(h - 180) < HEADING_NUDGE ? heading - h + 180 - HEADING_NUDGE : heading;
    }

    private static UUID uuid(Odds odds) {
        return new UUID(odds.getRandomLong(), odds.getRandomLong());
    }

    private static ListTag doubles(double... values) {
        ListTag list = new ListTag();
        for (double d : values)
            list.add(DoubleTag.valueOf(d));
        return list;
    }
}
