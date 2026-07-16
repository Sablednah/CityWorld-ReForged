package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.EntityType;

/**
 * Stub of the original {@code SpawnProvider} (333 lines) — the mobs and spawners CityWorld seeds
 * into sewers, mines, bunkers and the wild.
 *
 * <p><b>Wave 2 placeholder.</b> Every method here runs in the decoration pass, on a live level,
 * which the port does not drive yet. It also needs Bukkit's {@code EntityType} mapped onto modern
 * {@code EntityType} — explicitly P5 work (PORTING.md). {@code AbstractEntityList} is a marker for
 * now so the call sites keep their shape.
 */
public class SpawnProvider extends Provider {

    /** Placeholder for upstream's {@code AbstractEntityList} — an odds-weighted entity list. */
    public static final class AbstractEntityList {
    }

    public final AbstractEntityList itemsEntities_Sewers = new AbstractEntityList();
    public final AbstractEntityList itemsEntities_Mine = new AbstractEntityList();
    public final AbstractEntityList itemsEntities_Bunker = new AbstractEntityList();
    public final AbstractEntityList itemsEntities_LavaPit = new AbstractEntityList();
    public final AbstractEntityList itemsEntities_WaterPit = new AbstractEntityList();

    public SpawnProvider(CityWorldGenerator generator) {
    }

    /** P5: place a mob, or a spawner for one. */
    public void setSpawnOrSpawner(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            boolean doSpawner, AbstractEntityList entities) {
    }

    /** P5: place a spawner for one of {@code entities}. */
    public void setSpawner(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            AbstractEntityList entities) {
    }

    /** P5. */
    public void spawnBeings(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
    }

    /** P5: one being, rather than a group. */
    public void spawnBeing(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
    }

    /** P5: farm animals, of whatever the world fancies. */
    public void spawnAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
    }

    /** P5: farm animals of a named kind — a barn asks for horses or donkeys. */
    public void spawnAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType animal) {
    }

    /** P5. */
    public void spawnSeaAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
    }

    /** P5. */
    public void spawnVagrants(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
    }
}
