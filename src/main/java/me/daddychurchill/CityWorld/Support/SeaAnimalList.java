package me.daddychurchill.CityWorld.Support;

import java.util.Set;

import me.daddychurchill.CityWorld.compat.EntityType;

/**
 * Fish. They shoal — which is why this list's default herd is the biggest of the three.
 *
 * <p>The switch became set membership for the same reason as {@link AnimalList}.
 */
public final class SeaAnimalList extends AbstractEntityList {

    /** The big swimmers. Three at most. */
    private static final Set<EntityType> swimmers =
            Set.of(EntityType.SQUID, EntityType.DOLPHIN, EntityType.TURTLE);

    /** Guardians keep their own company. */
    private static final Set<EntityType> solitary =
            Set.of(EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN);

    public SeaAnimalList(String name) {
        super(name);
    }

    public SeaAnimalList(String name, EntityType... entities) {
        super(name, entities);
    }

    @Override
    public int getHerdSize(Odds odds, EntityType entity) {
        if (swimmers.contains(entity))
            return odds.getRandomInt(1, 3);
        if (solitary.contains(entity))
            return 1;
        return odds.getRandomInt(3, 6);
    }
}
