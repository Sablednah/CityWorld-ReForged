package me.daddychurchill.CityWorld.Plugins;

/**
 * Base of the provider stack ({@code ShapeProvider}, {@code LootProvider}, {@code OreProvider}, …).
 *
 * <p>The original's only member was a helper that version-checked an optional Bukkit {@code Plugin}
 * before integrating with it (PhatLoots, WorldEdit). There is no analogue in the port — soft
 * dependencies on other mods are resolved differently, and the WorldEdit path becomes vanilla
 * {@code StructureTemplate} work in Phase 6 — so the class stays empty and exists to preserve the
 * hierarchy the ~300 algorithm files are written against.
 */
public abstract class Provider {

    protected Provider() {
    }
}
