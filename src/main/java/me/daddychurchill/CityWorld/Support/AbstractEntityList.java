package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.EntityType;

/**
 * A weighted bag of entity types — weighted by repetition, which is how CityWorld says "mostly
 * chickens, occasionally a wolf": the list literally holds {@code CHICKEN} six times.
 *
 * <p>Two things upstream did are gone:
 *
 * <ul>
 * <li><b>The {@code ConfigurationSection} read/write.</b> Half this class was parsing entity names
 * out of per-world YAML and reporting the ones it didn't recognise. The port has no per-world
 * settings yet (P7, and it won't be YAML), so there is nothing to read and the {@code listName}
 * that keyed it has no reader. The names are kept anyway — they're the tag each list would be
 * stored under, and they cost nothing.
 * <li><b>The {@code isAlive()} filter on {@code add}.</b> It existed to reject nonliving types that
 * a config could name; a hardcoded list can't contain one. See {@code compat/EntityType}.
 * </ul>
 *
 * <p>{@code getFirstEntity} returned Bukkit's {@code UNKNOWN} sentinel on an empty list; this
 * returns {@code null}, which every caller already had to handle.
 */
public abstract class AbstractEntityList {

    private final String listName;
    private final List<EntityType> items = new ArrayList<>();

    AbstractEntityList(String name) {
        this.listName = name;
    }

    AbstractEntityList(String name, EntityType... entities) {
        this.listName = name;
        add(entities);
    }

    public String getListName() {
        return listName;
    }

    private void add(EntityType... entities) {
        for (EntityType entity : entities)
            items.add(entity);
    }

    public void remove(EntityType entity) {
        items.removeIf(item -> item == entity);
    }

    private int count() {
        return items.size();
    }

    /** How many of this kind travel together. Overridden per list; a being walks alone. */
    public int getHerdSize(Odds odds, EntityType entity) {
        return 1;
    }

    private EntityType getFirstEntity() {
        return items.isEmpty() ? null : items.get(0);
    }

    public EntityType getRandomEntity(Odds odds) {
        return getRandomEntity(odds, getFirstEntity());
    }

    private EntityType getRandomEntity(Odds odds, EntityType defaultEntity) {
        return items.isEmpty() ? defaultEntity : items.get(odds.getRandomInt(count()));
    }
}
