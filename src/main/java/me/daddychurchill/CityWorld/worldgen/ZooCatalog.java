package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;

/**
 * What lives in CityWorld's zoos, from data: two datapack registries a pack (or a zoo mod) can add to.
 *
 * <ul>
 *   <li>{@code cityworld:zoo/animal} — {@code data/<ns>/cityworld/zoo/animal/<name>.json}: the {@code entity},
 *       the {@code name} on the pen's sign, a {@code weight}, a {@code count} {@code {min, max}}, a
 *       {@code baby_chance}, a pen {@code size} ({@code small} fits any pen, {@code large} only a big multi-chunk
 *       one, {@code tall} any pen with a fence high enough for a giraffe), the {@code habitat} it lives in, an
 *       optional {@code requires_mod}, and {@code finalize} ({@code false} skips the mob's spawn setup, for a
 *       modded mob that reads the world while it is set up — unsafe on a worldgen thread).</li>
 *   <li>{@code cityworld:zoo/habitat} — {@code data/<ns>/cityworld/zoo/habitat/<name>.json}: the {@code ground},
 *       {@code scatter} blocks through it, {@code water} ({@code none} or a {@code pool}), {@code plants} standing
 *       on the ground, {@code pool_blocks} on or in the water, {@code trees}, {@code rocks} to climb, and the
 *       {@code fence} and {@code gate}.</li>
 * </ul>
 *
 * <p>An animal whose entity is not registered, whose {@code requires_mod} is absent, whose habitat does not exist
 * or whose weight is 0 is skipped quietly (so a pack removes one of CityWorld's own by shipping the same file
 * with {@code "weight": 0}). The ten CityWorld ships are the zoo it always had.
 *
 * <p>The registries are read once per world load ({@link #bind}, from the level-load event, before any chunk is
 * planned) into a list sorted by id, so the same seed plans the same zoo whatever order the packs loaded in.
 */
public final class ZooCatalog {

    private ZooCatalog() {
    }

    public record Range(int min, int max) {
        public static final Codec<Range> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.fieldOf("min").forGetter(Range::min),
                Codec.INT.fieldOf("max").forGetter(Range::max)).apply(i, Range::new));

        public int roll(Odds odds) {
            return max <= min ? min : min + odds.getRandomInt(max - min + 1);
        }
    }

    public record Animal(String entity, String name, int weight, Range count, float babyChance, String size,
            String habitat, Optional<String> requiresMod, boolean finalizeSpawn) {
        public static final Codec<Animal> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("entity").forGetter(Animal::entity),
                Codec.STRING.fieldOf("name").forGetter(Animal::name),
                Codec.INT.optionalFieldOf("weight", 1).forGetter(Animal::weight),
                Range.CODEC.optionalFieldOf("count", new Range(2, 4)).forGetter(Animal::count),
                Codec.FLOAT.optionalFieldOf("baby_chance", 0.0F).forGetter(Animal::babyChance),
                Codec.STRING.optionalFieldOf("size", "small").forGetter(Animal::size),
                Codec.STRING.fieldOf("habitat").forGetter(Animal::habitat),
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(Animal::requiresMod),
                Codec.BOOL.optionalFieldOf("finalize", true).forGetter(Animal::finalizeSpawn))
                .apply(i, Animal::new));

        public boolean large() {
            return "large".equals(size);
        }

        public boolean tall() {
            return "tall".equals(size);
        }
    }

    /** A block stood on the ground (a column {@code height} high), {@code count} times a pen. */
    public record Plant(String block, int count, Range height) {
        public static final Codec<Plant> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(Plant::block),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Plant::count),
                Range.CODEC.optionalFieldOf("height", new Range(1, 1)).forGetter(Plant::height))
                .apply(i, Plant::new));
    }

    /** A block in the pool: {@code surface} replaces the water's top (ice, dripstone), else it floats on it. */
    public record PoolBlock(String block, int count, boolean surface) {
        public static final Codec<PoolBlock> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(PoolBlock::block),
                Codec.INT.optionalFieldOf("count", 1).forGetter(PoolBlock::count),
                Codec.BOOL.optionalFieldOf("surface", false).forGetter(PoolBlock::surface))
                .apply(i, PoolBlock::new));
    }

    /** A small tree (a three-high trunk under a persistent canopy), optionally with a hive in its leaves. */
    public record Tree(String log, String leaves, int count, Optional<String> hive) {
        public static final Codec<Tree> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("log").forGetter(Tree::log),
                Codec.STRING.fieldOf("leaves").forGetter(Tree::leaves),
                Codec.INT.optionalFieldOf("count", 1).forGetter(Tree::count),
                Codec.STRING.optionalFieldOf("hive").forGetter(Tree::hive))
                .apply(i, Tree::new));
    }

    /** A block that replaces the ground here and there. */
    public record Scatter(String block, float chance) {
        public static final Codec<Scatter> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("block").forGetter(Scatter::block),
                Codec.FLOAT.fieldOf("chance").forGetter(Scatter::chance))
                .apply(i, Scatter::new));
    }

    public record Habitat(String ground, List<Scatter> scatter, String water, List<Plant> plants,
            List<PoolBlock> poolBlocks, List<Tree> trees, List<Plant> rocks, String fence, String gate) {
        public static final Codec<Habitat> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("ground").forGetter(Habitat::ground),
                Scatter.CODEC.listOf().optionalFieldOf("scatter", List.of()).forGetter(Habitat::scatter),
                Codec.STRING.optionalFieldOf("water", "none").forGetter(Habitat::water),
                Plant.CODEC.listOf().optionalFieldOf("plants", List.of()).forGetter(Habitat::plants),
                PoolBlock.CODEC.listOf().optionalFieldOf("pool_blocks", List.of()).forGetter(Habitat::poolBlocks),
                Tree.CODEC.listOf().optionalFieldOf("trees", List.of()).forGetter(Habitat::trees),
                Plant.CODEC.listOf().optionalFieldOf("rocks", List.of()).forGetter(Habitat::rocks),
                Codec.STRING.optionalFieldOf("fence", "minecraft:oak_fence").forGetter(Habitat::fence),
                Codec.STRING.optionalFieldOf("gate", "minecraft:oak_fence_gate").forGetter(Habitat::gate))
                .apply(i, Habitat::new));

        public boolean pool() {
            return "pool".equals(water);
        }

        /** The habitat's block {@code id} as a Material, or {@code fallback} for an unknown id. */
        public static Material block(String id, Material fallback) {
            try {
                Material m = id == null ? null : Material.of(id);
                return m == null || m == Material.AIR ? fallback : m; // Material.of answers AIR for an unknown id
            } catch (RuntimeException e) {
                return fallback; // a malformed id
            }
        }
    }

    public static final ResourceKey<Registry<Animal>> ANIMALS = ResourceKey
            .createRegistryKey(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "zoo/animal"));
    public static final ResourceKey<Registry<Habitat>> HABITATS = ResourceKey
            .createRegistryKey(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "zoo/habitat"));

    /** One resolved resident: its id, data, entity type and habitat. */
    public record Entry(String id, Animal animal, EntityType entity, Habitat habitat) {
    }

    /** A pen with no resident — what a zoo draws if no animal is registered at all. */
    public static final Entry EMPTY = new Entry("cityworld:empty",
            new Animal("", "Closed", 1, new Range(0, 0), 0.0F, "small", "", Optional.empty(), true), null,
            new Habitat("minecraft:grass_block", List.of(), "none", List.of(), List.of(), List.of(), List.of(),
                    "minecraft:oak_fence", "minecraft:oak_fence_gate"));

    private static volatile List<Entry> entries = List.of();

    /** Reads both registries off a loaded level. Called for every level; the registries are the server's. */
    public static void bind(RegistryAccess access) {
        try {
            Registry<Habitat> habitats = access.lookupOrThrow(HABITATS);
            Registry<Animal> animals = access.lookupOrThrow(ANIMALS);
            List<Entry> found = new ArrayList<>();
            int skipped = 0;
            for (Map.Entry<ResourceKey<Animal>, Animal> e : animals.entrySet()) {
                Animal a = e.getValue();
                EntityType type = EntityType.of(a.entity());
                Habitat h = habitats.getValue(Identifier.tryParse(a.habitat()));
                boolean modOk = a.requiresMod().map(m -> net.neoforged.fml.ModList.get().isLoaded(m)).orElse(true);
                if (a.weight() <= 0 || type == null || h == null || !modOk) {
                    skipped++;
                    continue;
                }
                found.add(new Entry(e.getKey().identifier().toString(), a, type, h));
            }
            found.sort(Comparator.comparing(Entry::id));
            entries = List.copyOf(found);
            CityWorldMod.LOGGER.info("CityWorld zoo: {} animals ({} skipped: weight 0, mod or entity absent, or no habitat)",
                    found.size(), skipped);
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld zoo: could not read the zoo registries: {}", e.toString());
        }
    }

    public static List<Entry> entries() {
        return entries;
    }

    /** A weighted pick: for a single-chunk pen ({@code big} false) only animals that fit one. One odds roll. */
    public static Entry pick(Odds odds, boolean big) {
        List<Entry> fit = big ? entries : entries.stream().filter(e -> !e.animal().large()).toList();
        int total = 0;
        for (Entry e : fit)
            total += e.animal().weight();
        if (total <= 0)
            return EMPTY;
        int roll = odds.getRandomInt(total);
        for (Entry e : fit) {
            roll -= e.animal().weight();
            if (roll < 0)
                return e;
        }
        return fit.get(fit.size() - 1);
    }

    /** The entry with this id, or {@link #EMPTY} (a big zoo's chunks carry the id their placer picked). */
    public static Entry byId(String id) {
        for (Entry e : entries)
            if (e.id().equals(id))
                return e;
        return EMPTY;
    }
}
