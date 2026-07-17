package me.daddychurchill.CityWorld.Support;

import java.util.Set;

import me.daddychurchill.CityWorld.compat.EntityType;

/**
 * Livestock and pets, which arrive in groups.
 *
 * <p>Upstream switched on Bukkit's {@code EntityType} enum. The port's is an interned wrapper over
 * an open registry rather than an enum (see {@code compat/EntityType}), so it cannot be a switch
 * label; the cases become set membership, which is what the switch was spelling anyway. Order is not
 * significant — a switch's {@code default} applies when nothing matched, so it reads last here even
 * though upstream wrote it first.
 *
 * <p>Upstream's dead {@code EntityAffilation} enum is not ported: it was declared here and named
 * nowhere in the tree.
 */
public final class AnimalList extends AbstractEntityList {

    /** Pack animals, roughly. Two at most. */
    private static final Set<EntityType> loners =
            Set.of(EntityType.WOLF, EntityType.OCELOT, EntityType.CAT, EntityType.FOX);

    /** The big livestock. Three at most. */
    private static final Set<EntityType> livestock = Set.of(EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA,
            EntityType.COW, EntityType.MOOSHROOM, EntityType.SHEEP, EntityType.PIG);

    public AnimalList(String name) {
        super(name);
    }

    public AnimalList(String name, EntityType... entities) {
        super(name, entities);
    }

    @Override
    public int getHerdSize(Odds odds, EntityType entity) {
        if (loners.contains(entity))
            return odds.getRandomInt(1, 2);
        if (livestock.contains(entity))
            return odds.getRandomInt(1, 3);
        return odds.getRandomInt(1, 6);
    }
}
