package me.daddychurchill.CityWorld.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.minecraft.core.registries.BuiltInRegistries;

/**
 * Shim for Bukkit's {@code org.bukkit.entity.EntityType}, wrapping a modern
 * {@code net.minecraft.world.entity.EntityType}.
 *
 * <p>Interned, like {@link Material} and {@link Biome}, and for the same reason: entity types live
 * in an open registry that datapacks and other mods extend, so an enum would be wrong.
 *
 * <p>These are the 46 the tree names, plus {@link #MOOSHROOM} and {@link #TURTLE}, which only ever
 * appear as bare labels in the herd-size switches. Most are a straight rename; four were not:
 *
 * <ul>
 * <li>1.14's {@code PIG_ZOMBIE} is {@link #ZOMBIFIED_PIGLIN} — the Nether Update split zombie pigmen
 * into piglins and their zombified form, and this is the half that kept the old mob's role.
 * <li>{@code SNOWMAN} is {@link #SNOW_GOLEM} and {@code MUSHROOM_COW} is {@link #MOOSHROOM} — pure
 * renames, but not ones you would guess.
 * <li><b>{@code UNKNOWN} has no modern counterpart and is deliberately absent.</b> Bukkit used it as
 * a sentinel, and CityWorld only ever produced it from {@code AbstractEntityList.getFirstEntity} on
 * an empty list — a "there is nothing here" that then failed {@code isAlive()} and spawned nothing.
 * The port returns {@code null} for that instead, which the callers already test for, so the
 * sentinel has no work left to do. Inventing a fake registry entry to stand in for it would be
 * strictly worse.
 * </ul>
 *
 * <p>There is no {@code isAlive()} here either. Bukkit's answered whether the type had a
 * {@code LivingEntity} class, and CityWorld used it to filter entity names supplied by per-world
 * YAML — here the datapack's mob bags, validated on load — and to guard spawns from its own hardcoded
 * lists, every member of which is alive. What the spawn path actually needs to know is whether the
 * type is a {@code Mob} (so it can be given a {@code finalizeSpawn}), and that is a plain
 * {@code instanceof} on the constructed entity, so {@code SpawnProvider} asks it that way.
 */
public final class EntityType {

    private static final Map<net.minecraft.world.entity.EntityType<?>, EntityType> interned =
            new ConcurrentHashMap<>();

    private final net.minecraft.world.entity.EntityType<?> type;

    private EntityType(net.minecraft.world.entity.EntityType<?> type) {
        this.type = type;
    }

    public static EntityType of(net.minecraft.world.entity.EntityType<?> type) {
        return interned.computeIfAbsent(type, EntityType::new);
    }

    /**
     * Resolves an entity id (e.g. {@code "minecraft:zombie"}, or a bare {@code "zombie"} defaulting to
     * the {@code minecraft} namespace) against the entity registry. Returns {@code null} for an
     * unknown or malformed id — the caller logs and skips it, which is how per-world mob-list
     * overrides handle a typo without guessing (see {@code CityWorldSettings}).
     */
    public static EntityType of(String id) {
        net.minecraft.resources.Identifier key = net.minecraft.resources.Identifier.tryParse(id);
        if (key == null)
            return null;
        net.minecraft.world.entity.EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(key);
        // getValue falls back to the default entry (pig) for a missing key, so confirm the id maps
        // to a registered entry rather than trusting a non-null return.
        if (type == null || !BuiltInRegistries.ENTITY_TYPE.containsKey(key))
            return null;
        return of(type);
    }

    /** The vanilla type this stands for — what {@code SpawnProvider} spawns from. */
    public net.minecraft.world.entity.EntityType<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    // Beings — the goodies, the baddies, and the things in the dark.
    public static final EntityType VILLAGER = of(net.minecraft.world.entity.EntityTypes.VILLAGER);
    public static final EntityType ZOMBIE_VILLAGER = of(net.minecraft.world.entity.EntityTypes.ZOMBIE_VILLAGER);
    public static final EntityType WITCH = of(net.minecraft.world.entity.EntityTypes.WITCH);
    public static final EntityType CREEPER = of(net.minecraft.world.entity.EntityTypes.CREEPER);
    public static final EntityType SKELETON = of(net.minecraft.world.entity.EntityTypes.SKELETON);
    public static final EntityType SKELETON_HORSE = of(net.minecraft.world.entity.EntityTypes.SKELETON_HORSE);
    public static final EntityType ZOMBIE = of(net.minecraft.world.entity.EntityTypes.ZOMBIE);
    public static final EntityType ZOMBIE_HORSE = of(net.minecraft.world.entity.EntityTypes.ZOMBIE_HORSE);
    public static final EntityType SPIDER = of(net.minecraft.world.entity.EntityTypes.SPIDER);
    public static final EntityType CAVE_SPIDER = of(net.minecraft.world.entity.EntityTypes.CAVE_SPIDER);
    public static final EntityType ENDERMAN = of(net.minecraft.world.entity.EntityTypes.ENDERMAN);
    public static final EntityType ENDERMITE = of(net.minecraft.world.entity.EntityTypes.ENDERMITE);
    public static final EntityType PHANTOM = of(net.minecraft.world.entity.EntityTypes.PHANTOM);
    public static final EntityType BLAZE = of(net.minecraft.world.entity.EntityTypes.BLAZE);
    public static final EntityType BAT = of(net.minecraft.world.entity.EntityTypes.BAT);
    public static final EntityType EVOKER = of(net.minecraft.world.entity.EntityTypes.EVOKER);
    public static final EntityType ILLUSIONER = of(net.minecraft.world.entity.EntityTypes.ILLUSIONER);
    public static final EntityType MAGMA_CUBE = of(net.minecraft.world.entity.EntityTypes.MAGMA_CUBE);
    public static final EntityType SHULKER = of(net.minecraft.world.entity.EntityTypes.SHULKER);
    public static final EntityType WITHER = of(net.minecraft.world.entity.EntityTypes.WITHER);
    public static final EntityType IRON_GOLEM = of(net.minecraft.world.entity.EntityTypes.IRON_GOLEM);

    /** 1.14's {@code PIG_ZOMBIE}. */
    public static final EntityType ZOMBIFIED_PIGLIN = of(net.minecraft.world.entity.EntityTypes.ZOMBIFIED_PIGLIN);
    /** 1.14's {@code SNOWMAN}. */
    public static final EntityType SNOW_GOLEM = of(net.minecraft.world.entity.EntityTypes.SNOW_GOLEM);

    // Animals.
    public static final EntityType HORSE = of(net.minecraft.world.entity.EntityTypes.HORSE);
    public static final EntityType DONKEY = of(net.minecraft.world.entity.EntityTypes.DONKEY);
    public static final EntityType LLAMA = of(net.minecraft.world.entity.EntityTypes.LLAMA);
    public static final EntityType COW = of(net.minecraft.world.entity.EntityTypes.COW);
    public static final EntityType SHEEP = of(net.minecraft.world.entity.EntityTypes.SHEEP);
    public static final EntityType PIG = of(net.minecraft.world.entity.EntityTypes.PIG);
    public static final EntityType CHICKEN = of(net.minecraft.world.entity.EntityTypes.CHICKEN);
    public static final EntityType RABBIT = of(net.minecraft.world.entity.EntityTypes.RABBIT);
    public static final EntityType PARROT = of(net.minecraft.world.entity.EntityTypes.PARROT);
    public static final EntityType WOLF = of(net.minecraft.world.entity.EntityTypes.WOLF);
    public static final EntityType OCELOT = of(net.minecraft.world.entity.EntityTypes.OCELOT);
    public static final EntityType CAT = of(net.minecraft.world.entity.EntityTypes.CAT);
    public static final EntityType FOX = of(net.minecraft.world.entity.EntityTypes.FOX);
    public static final EntityType POLAR_BEAR = of(net.minecraft.world.entity.EntityTypes.POLAR_BEAR);
    /** 1.14's {@code MUSHROOM_COW}. */
    public static final EntityType MOOSHROOM = of(net.minecraft.world.entity.EntityTypes.MOOSHROOM);

    // Sea animals.
    public static final EntityType SQUID = of(net.minecraft.world.entity.EntityTypes.SQUID);
    public static final EntityType DOLPHIN = of(net.minecraft.world.entity.EntityTypes.DOLPHIN);
    public static final EntityType COD = of(net.minecraft.world.entity.EntityTypes.COD);
    public static final EntityType SALMON = of(net.minecraft.world.entity.EntityTypes.SALMON);
    public static final EntityType PUFFERFISH = of(net.minecraft.world.entity.EntityTypes.PUFFERFISH);
    public static final EntityType TROPICAL_FISH = of(net.minecraft.world.entity.EntityTypes.TROPICAL_FISH);
    public static final EntityType TURTLE = of(net.minecraft.world.entity.EntityTypes.TURTLE);
    public static final EntityType GUARDIAN = of(net.minecraft.world.entity.EntityTypes.GUARDIAN);
    public static final EntityType ELDER_GUARDIAN = of(net.minecraft.world.entity.EntityTypes.ELDER_GUARDIAN);

    // P13 zoo: themed-enclosure animals
    public static final EntityType PANDA = of(net.minecraft.world.entity.EntityTypes.PANDA);
    public static final EntityType CAMEL = of(net.minecraft.world.entity.EntityTypes.CAMEL);
    public static final EntityType FROG = of(net.minecraft.world.entity.EntityTypes.FROG);
    public static final EntityType BEE = of(net.minecraft.world.entity.EntityTypes.BEE);
    public static final EntityType GOAT = of(net.minecraft.world.entity.EntityTypes.GOAT);
    public static final EntityType AXOLOTL = of(net.minecraft.world.entity.EntityTypes.AXOLOTL);
    public static final EntityType SNIFFER = of(net.minecraft.world.entity.EntityTypes.SNIFFER);
    public static final EntityType ARMADILLO = of(net.minecraft.world.entity.EntityTypes.ARMADILLO);
    public static final EntityType ALLAY = of(net.minecraft.world.entity.EntityTypes.ALLAY);
    public static final EntityType GLOW_SQUID = of(net.minecraft.world.entity.EntityTypes.GLOW_SQUID);
    public static final EntityType STRIDER = of(net.minecraft.world.entity.EntityTypes.STRIDER);
    public static final EntityType HOGLIN = of(net.minecraft.world.entity.EntityTypes.HOGLIN);
}
