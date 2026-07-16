package me.daddychurchill.CityWorld.compat;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shim for Bukkit's {@code org.bukkit.entity.EntityType}, wrapping a modern
 * {@code net.minecraft.world.entity.EntityType}.
 *
 * <p>Interned, like {@link Material} and {@link Biome}, and for the same reason: entity types live
 * in an open registry that datapacks and other mods extend, so an enum would be wrong. The ported
 * code only ever names one and passes it to {@code SpawnProvider}.
 *
 * <p><b>Only what is actually named so far.</b> The tree references 46 entity types across
 * {@code SpawnProvider} and the lots, but {@code SpawnProvider} is not ported (it needs entity
 * spawning during worldgen, spawner block entities, and per-column biome writes — all P5), so only
 * the two a ported lot reaches for exist. The rest arrive with it, and several need care rather than
 * a rename: 1.14's {@code PIG_ZOMBIE} is now {@code ZOMBIFIED_PIGLIN}, {@code SNOWMAN} is
 * {@code SNOW_GOLEM}, and {@code UNKNOWN} has no modern counterpart at all.
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

    /** The vanilla type this stands for — what {@code SpawnProvider} will spawn from. */
    public net.minecraft.world.entity.EntityType<?> getType() {
        return type;
    }

    @Override
    public String toString() {
        return net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).toString();
    }

    public static final EntityType HORSE = of(net.minecraft.world.entity.EntityType.HORSE);
    public static final EntityType DONKEY = of(net.minecraft.world.entity.EntityType.DONKEY);
}
