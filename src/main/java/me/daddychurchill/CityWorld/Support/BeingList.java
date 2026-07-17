package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.EntityType;

/** People and monsters. They arrive one at a time — the inherited herd size of 1. */
public final class BeingList extends AbstractEntityList {

    public BeingList(String name) {
        super(name);
    }

    public BeingList(String name, EntityType... entities) {
        super(name, entities);
    }
}
