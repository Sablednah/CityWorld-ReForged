package me.daddychurchill.CityWorld.Clipboard;

import me.daddychurchill.CityWorld.Plugins.Provider;

/**
 * Stub of the original {@code PasteProvider} — loads player-supplied schematics and hands out the
 * clips a context can drop into a platmap.
 *
 * <p><b>P6 placeholder.</b> The real work is reimplementing {@code Clipboard} on vanilla
 * {@code StructureTemplate} and converting the bundled {@code schematics/} assets to {@code .nbt}.
 * Upstream also had a WorldEdit-backed implementation; that dependency does not come along.
 *
 * <p>{@link SchematicFamily} is here now because it is not really schematic machinery — it is the
 * vocabulary each context uses to declare what kind of place it is building, and every context
 * states its own in its constructor. Keeping it means those declarations survive rather than being
 * stripped out and having to be rediscovered at P6.
 */
public abstract class PasteProvider extends Provider {

    public enum SchematicFamily {
        ROUNDABOUT, PARK, HIGHRISE, MIDRISE, LOWRISE, INDUSTRIAL, MUNICIPAL, CONSTRUCTION, NEIGHBORHOOD, FARM, NATURE,
        ASTRAL, OUTLAND
    }

    protected PasteProvider() {
        super();
    }
}
