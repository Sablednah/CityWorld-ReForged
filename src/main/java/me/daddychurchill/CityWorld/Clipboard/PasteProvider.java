package me.daddychurchill.CityWorld.Clipboard;

import me.daddychurchill.CityWorld.Plugins.Provider;

/**
 * Base of the schematic providers — {@link SchematicLibrary} loads the bundled catalog and any
 * player-supplied schematics (legacy {@code .schematic}, Sponge {@code .schem}, Litematica
 * {@code .litematic}, vanilla {@code .nbt}), and {@code ClipboardList} hands out the clips a context
 * can drop into a platmap. {@link Clipboard} is built on vanilla {@code StructureTemplate}; upstream's
 * WorldEdit-backed implementation did not come along, and that dependency is not needed.
 *
 * <p>{@link SchematicFamily} lives here because it is not really schematic machinery — it is the
 * vocabulary each context uses to declare what kind of place it is building, and every context states
 * its own in its constructor.
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
