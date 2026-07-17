package me.daddychurchill.CityWorld.Plugins;

import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.entity.SpawnerBlockEntity;
import net.neoforged.neoforge.event.EventHooks;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.AbstractEntityList;
import me.daddychurchill.CityWorld.Support.AnimalList;
import me.daddychurchill.CityWorld.Support.BeingList;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SeaAnimalList;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Block;
import me.daddychurchill.CityWorld.compat.EntityType;
import me.daddychurchill.CityWorld.compat.Location;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * Who lives here — the mobs and spawners CityWorld seeds into its cities, sewers, mines and fields.
 *
 * <p>Runs in the decoration pass, against the live {@code WorldGenLevel} that
 * {@code applyBiomeDecoration} hands over. Three notes on how that changed the port:
 *
 * <p><b>{@code generator.getWorld()} turned out not to be needed</b> — it was recorded as the port's
 * top risk, on the grounds that upstream reached for a whole {@code World} here. It doesn't need
 * one. Bukkit's {@code Location} carried its world, and so does {@code compat/Location}, so the
 * level comes from {@code blocks.getBlockLocation(…).getLevel()} — the same object upstream already
 * had in its hand when it called {@code getWorld()}.
 *
 * <p><b>Spawning is create-then-add, not {@code spawnEntity}.</b> Every modern
 * {@code EntityType.spawn} overload demands a concrete {@code ServerLevel} and would add the mob to
 * the live level, bypassing the region being generated. Vanilla's own worldgen never calls it;
 * {@code OceanMonumentPieces}, {@code SwampHutPiece} and {@code MineshaftPieces} all do
 * {@code create(level.getLevel(), reason)} → {@code snapTo} → {@code finalizeSpawn(region, …)} →
 * {@code region.addFreshEntityWithPassengers(…)}, and so does {@link #placeEntity}. Note the split:
 * {@code getLevel()} bridges to {@code ServerLevel} purely to <em>construct</em> the entity, while
 * everything that places it takes the accessor.
 *
 * <p><b>The biome fixup is gone.</b> Upstream nudged the column's biome to {@code FOREST} before
 * spawning a wolf and {@code JUNGLE} before an ocelot, so the mob would stand in a biome it belonged
 * to. There is no modern equivalent to reinstate: nothing on {@code LevelAccessor},
 * {@code ChunkAccess} or {@code LevelChunk} can write a biome, the section containers are read-only
 * ({@code PalettedContainerRO}), and biomes are settled at the {@code BIOMES} status five steps
 * before {@code FEATURES} — by decoration every consumer has already read them. This was the
 * "BiomeGrid problem" in PORTING.md, and the honest answer is that it belongs to the
 * {@code BiomeSource}, not here. Nothing depends on it: a directly-placed mob is not subject to
 * natural spawn rules, so it spawns regardless. A wolf in a city simply stands in whatever biome
 * the city is.
 */
public class SpawnProvider extends Provider {

    private final static String tagEntities_Goodies = "Entities_For_Goodies";
    private final AbstractEntityList itemsEntities_Goodies = new BeingList(tagEntities_Goodies, EntityType.VILLAGER,
            EntityType.VILLAGER, EntityType.VILLAGER, EntityType.VILLAGER, EntityType.VILLAGER, EntityType.VILLAGER,
            EntityType.VILLAGER, EntityType.VILLAGER, EntityType.VILLAGER, EntityType.WITCH);

    private final static String tagEntities_Baddies = "Entities_For_Baddies";
    private final AbstractEntityList itemsEntities_Baddies = new BeingList(tagEntities_Baddies, EntityType.CREEPER,
            EntityType.CREEPER, EntityType.CREEPER, EntityType.CREEPER, EntityType.SKELETON, EntityType.SKELETON,
            EntityType.SKELETON, EntityType.SKELETON, EntityType.ZOMBIE, EntityType.ZOMBIE, EntityType.ZOMBIE,
            EntityType.ZOMBIE, EntityType.ZOMBIE_HORSE, EntityType.ZOMBIE_VILLAGER, EntityType.SPIDER,
            EntityType.SPIDER, EntityType.SPIDER, EntityType.WITCH, EntityType.WITCH, EntityType.ZOMBIFIED_PIGLIN,
            EntityType.ENDERMAN, EntityType.PHANTOM, EntityType.BLAZE);

    private final static String tagEntities_Animals = "Entities_For_Animals";
    private final AbstractEntityList itemsEntities_Animals = new AnimalList(tagEntities_Animals, EntityType.HORSE,
            EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA, EntityType.COW, EntityType.COW, EntityType.SHEEP,
            EntityType.SHEEP, EntityType.PIG, EntityType.PIG, EntityType.PARROT, EntityType.PARROT, EntityType.CHICKEN,
            EntityType.CHICKEN, EntityType.CHICKEN, EntityType.CHICKEN, EntityType.CHICKEN, EntityType.CHICKEN,
            EntityType.RABBIT, EntityType.RABBIT, EntityType.RABBIT, EntityType.RABBIT, EntityType.WOLF,
            EntityType.OCELOT, EntityType.CAT, EntityType.FOX);

    private final static String tagEntities_SeaAnimals = "Entities_For_SeaAnimals";
    private final AbstractEntityList itemsEntities_SeaAnimals = new SeaAnimalList(tagEntities_SeaAnimals,
            EntityType.TROPICAL_FISH, EntityType.TROPICAL_FISH, EntityType.TROPICAL_FISH, EntityType.TROPICAL_FISH,
            EntityType.TROPICAL_FISH, EntityType.PUFFERFISH, EntityType.PUFFERFISH, EntityType.PUFFERFISH,
            EntityType.SALMON, EntityType.SALMON, EntityType.SALMON, EntityType.DOLPHIN, EntityType.DOLPHIN,
//			EntityType.GUARDIAN,
//			EntityType.ELDER_GUARDIAN,
            EntityType.SQUID, EntityType.SQUID, EntityType.COD, EntityType.COD, EntityType.COD);

    private final static String tagEntities_Vagrants = "Entities_For_Vagrants";
    private final AbstractEntityList itemsEntities_Vagrants = new BeingList(tagEntities_Vagrants, EntityType.CHICKEN,
            EntityType.CHICKEN, EntityType.RABBIT, EntityType.RABBIT, EntityType.WOLF, EntityType.WOLF, EntityType.FOX,
            EntityType.OCELOT, EntityType.CAT, EntityType.HORSE, EntityType.DONKEY, EntityType.LLAMA,
            EntityType.VILLAGER, EntityType.VILLAGER, EntityType.PARROT,
//			EntityType.POLAR_BEAR,
            EntityType.SPIDER, EntityType.CREEPER);

    private final static String tagEntities_Sewers = "Entities_For_Sewers";
    public final AbstractEntityList itemsEntities_Sewers = new BeingList(tagEntities_Sewers, EntityType.ZOMBIE,
            EntityType.ZOMBIE, EntityType.CREEPER, EntityType.SPIDER, EntityType.BAT, EntityType.BAT, EntityType.BAT,
            EntityType.BAT);

    private final static String tagEntities_Mine = "Entities_For_Mine";
    public final AbstractEntityList itemsEntities_Mine = new BeingList(tagEntities_Mine, EntityType.ZOMBIE,
            EntityType.ZOMBIE, EntityType.SKELETON, EntityType.SKELETON, EntityType.CREEPER, EntityType.CREEPER,
            EntityType.CAVE_SPIDER, EntityType.CAVE_SPIDER, EntityType.BAT, EntityType.BAT, EntityType.BAT,
            EntityType.BAT, EntityType.ENDERMITE, EntityType.CAT);

    private final static String tagEntities_Bunker = "Entities_For_Bunker";
    public final AbstractEntityList itemsEntities_Bunker = new BeingList(tagEntities_Bunker,
            EntityType.ZOMBIFIED_PIGLIN, EntityType.ENDERMAN, EntityType.EVOKER, EntityType.ILLUSIONER, EntityType.BAT,
            EntityType.BAT, EntityType.BLAZE);

    private final static String tagEntities_WaterPit = "Entities_For_WaterPit";
    public final AbstractEntityList itemsEntities_WaterPit = new BeingList(tagEntities_WaterPit, EntityType.SQUID,
            EntityType.SQUID, EntityType.SQUID, EntityType.SQUID, EntityType.SQUID, EntityType.SQUID, EntityType.SQUID,
            EntityType.SQUID, EntityType.GUARDIAN);

    private final static String tagEntities_LavaPit = "Entities_For_LavaPit";
    public final AbstractEntityList itemsEntities_LavaPit = new BeingList(tagEntities_LavaPit, EntityType.BLAZE,
            EntityType.WITHER, EntityType.MAGMA_CUBE, EntityType.SHULKER);

    public SpawnProvider(CityWorldGenerator generator) {
    }

    // https://en.wikipedia.org/wiki/List_of_English_terms_of_venery,_by_animal
    public final void spawnAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
        spawnAnimals(generator, blocks, odds, x, y, z, itemsEntities_Animals.getRandomEntity(odds));
    }

    public final void spawnAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity) {
        if (!generator.getSettings().includeDecayedBuildings) {
            int herdSize = itemsEntities_Animals.getHerdSize(odds, entity);
            if (herdSize > 0)
                spawnAnimal(generator, blocks, odds, x, y, z, entity);
            if (herdSize > 1)
                spawnAnimal(generator, blocks, odds, x + 1, y, z, entity);
            if (herdSize > 2)
                spawnAnimal(generator, blocks, odds, x, y, z + 1, entity);
            if (herdSize > 3)
                spawnAnimal(generator, blocks, odds, x + 1, y, z + 1, entity);
            if (herdSize > 4)
                spawnAnimal(generator, blocks, odds, x - 1, y, z, entity);
            if (herdSize > 5)
                spawnAnimal(generator, blocks, odds, x, y, z - 1, entity);
        }
    }

    private void spawnAnimal(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity) {
        if (odds.playOdds(generator.getSettings().spawnAnimals) && blocks.insideXYZ(x, y, z))
            spawnEntity(generator, blocks, odds, x, y, z, entity, false, true);
    }

    public final void spawnSeaAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y,
            int z) {
        spawnSeaAnimals(generator, blocks, odds, x, y, z, itemsEntities_SeaAnimals.getRandomEntity(odds));
    }

    private void spawnSeaAnimals(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity) {
        if (!generator.getSettings().includeDecayedBuildings) {
            int herdSize = itemsEntities_SeaAnimals.getHerdSize(odds, entity);
            if (herdSize > 0)
                spawnSeaAnimal(generator, blocks, odds, x, y, z, entity);
            if (herdSize > 1)
                spawnSeaAnimal(generator, blocks, odds, x + 1, y, z, entity);
            if (herdSize > 2)
                spawnSeaAnimal(generator, blocks, odds, x, y, z + 1, entity);
            if (herdSize > 3)
                spawnSeaAnimal(generator, blocks, odds, x + 1, y, z + 1, entity);
            if (herdSize > 4)
                spawnSeaAnimal(generator, blocks, odds, x - 1, y, z, entity);
            if (herdSize > 5)
                spawnSeaAnimal(generator, blocks, odds, x, y, z - 1, entity);
        }
    }

    private void spawnSeaAnimal(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity) {
        if (odds.playOdds(generator.getSettings().spawnAnimals) && blocks.insideXYZ(x, y, z)) {
            if (blocks.isWater(x, y - 1, z) && blocks.isWater(x, y, z)) {
                spawnEntity(generator, blocks, odds, x, y, z, entity, true, false);
            }
        }
    }

    public final void spawnBeing(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
        spawnBeing(generator, blocks, odds, x, y, z, itemsEntities_Goodies.getRandomEntity(odds),
                itemsEntities_Baddies.getRandomEntity(odds));
    }

    public final void spawnBeings(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z) {
        EntityType goodies = itemsEntities_Goodies.getRandomEntity(odds);
        EntityType baddies = itemsEntities_Baddies.getRandomEntity(odds);
        int count = odds.getRandomInt(1, 3); // shimmy
        for (int i = 0; i < count; i++)
            spawnBeing(generator, blocks, odds, blocks.clampXZ(x + i), y, blocks.clampXZ(z + i), goodies, baddies);
    }

    public final void spawnBeing(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType goody, EntityType baddy) {
        if (odds.playOdds(generator.getSettings().spawnBeings))
            spawnGoodOrBad(generator, blocks, odds, x, y, z, goody, baddy);
    }

    public final void spawnVagrants(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y,
            int z) {
        EntityType goodies = itemsEntities_Vagrants.getRandomEntity(odds);
        EntityType baddies = itemsEntities_Baddies.getRandomEntity(odds);
        int count = odds.getRandomInt(1, 3); // shimmy
        for (int i = 0; i < count; i++)
            spawnVagrant(generator, blocks, odds, blocks.clampXZ(x + i), y, blocks.clampXZ(z + i), goodies, baddies);
    }

    public final void spawnVagrant(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType goody, EntityType baddy) {
        if (odds.playOdds(generator.getSettings().spawnVagrants))
            spawnGoodOrBad(generator, blocks, odds, x, y, z, goody, baddy);
    }

    private void spawnGoodOrBad(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType goody, EntityType baddy) {
        if (!blocks.isEmpty(x, y, z) && !blocks.isEmpty(x, y - 1, z))
            if (generator.getSettings().includeDecayedBuildings || odds.playOdds(generator.getSettings().spawnBaddies))
                spawnEntity(generator, blocks, odds, x, y, z, baddy, false, true);
            else
                spawnEntity(generator, blocks, odds, x, y, z, goody, false, true);
    }

    /**
     * Put one there, if there is room and it is above the water.
     *
     * <p>The {@code insideXYZ} guard is not the politeness it was under Bukkit — it is what keeps
     * the server up. {@code WorldGenRegion.addFreshEntity} does <em>not</em> consult
     * {@code ensureCanWrite} the way {@code setBlock} does; it resolves the target chunk directly,
     * and a chunk outside the region's cache throws {@code ReportedException} rather than declining.
     * Since the shimmy below moves {@code x}/{@code z} around, both it and the guard clamp to this
     * chunk.
     */
    private void spawnEntity(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity, boolean ignoreFlood, boolean ensureSpace) {
        if (!blocks.insideXYZ(x, y, z) || entity == null)
            return;

        // make sure we have room
        if (ensureSpace) {
            boolean foundSpot = false;
            int countDown = 10;
            while (countDown > 0) {
                int emptyY = blocks.findFirstEmptyAbove(x, y, z, y + 3);
                if (blocks.isEmpty(x, y, z) && blocks.isEmpty(x, y + 1, z)) {
                    y = emptyY;
                    foundSpot = true;
                    break;
                } else
                    countDown--;

                // shimmy
                x = blocks.clampXZ(x + odds.calcRandomRange(-2, 2));
                z = blocks.clampXZ(z + odds.calcRandomRange(-2, 2));
            }
            if (!foundSpot)
                return;
        }
        Location at = blocks.getBlockLocation(x, y, z);

        // ignore flood level or make sure that we are above it
        if (ignoreFlood || y > generator.shapeProvider.findFloodY(generator, at.getBlockX(), at.getBlockZ())) {

            // make sure there is a clear space
            if (ensureSpace)
                blocks.clearBlocks(x, y, y + 2, z);

            placeEntity(generator, odds, at, entity);
        }
    }

    /** The three-step vanilla worldgen spawn — see this class's notes. */
    private void placeEntity(CityWorldGenerator generator, Odds odds, Location at, EntityType entity) {
        LevelAccessor level = at.getLevel();
        if (!(level instanceof ServerLevelAccessor server))
            return;

        Entity being = entity.getType().create(server.getLevel(), EntitySpawnReason.CHUNK_GENERATION);
        if (being == null)
            return;

        // Bukkit spawned at the location's corner; vanilla centres on the block, which is what keeps
        // a mob out of the wall it was placed against.
        being.snapTo(at.getBlockX() + 0.5, at.getBlockY(), at.getBlockZ() + 0.5, 0.0F, 0.0F);

        // Gives the mob its equipment, variant and group data — what "being spawned" means beyond
        // existing at a position. Takes the accessor, not the level.
        //
        // Via EventHooks rather than Mob.finalizeSpawn directly: NeoForge marks the latter
        // @ApiStatus.OverrideOnly, and going through here fires FinalizeSpawnEvent so other mods get
        // their say on our mobs. It must follow snapTo, since the event reports the mob's position.
        // A cancelled spawn needs no handling on our side — Forge marks the mob and
        // WorldGenRegion.addFreshEntity drops it.
        if (being instanceof Mob mob)
            EventHooks.finalizeMobSpawn(mob, server, server.getCurrentDifficultyAt(mob.blockPosition()),
                    EntitySpawnReason.CHUNK_GENERATION, null);

        being.setDeltaMovement(odds.getRandomVelocity());

        if (generator.getSettings().nameVillagers && entity == EntityType.VILLAGER) {
            String beingName = generator.odonymProvider.generateVillagerName(generator, odds);
            being.setCustomName(Component.literal(beingName));
            if (generator.getSettings().showVillagersNames)
                being.setCustomNameVisible(true);
        }

        server.addFreshEntityWithPassengers(being);
    }

    public final void setSpawnOrSpawner(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y,
            int z, boolean doSpawner, AbstractEntityList entities) {
        EntityType entity = entities.getRandomEntity(odds);
        if (doSpawner)
            setSpawner(generator, blocks, odds, x, y, z, entity);
        else
            spawnEntity(generator, blocks, odds, x, y, z, entity, false, true);
    }

    public final void setSpawner(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            AbstractEntityList list) {
        setSpawner(generator, blocks, odds, x, y, z, list.getRandomEntity(odds));
    }

    /**
     * A spawner, wound up for one kind of mob.
     *
     * <p>Upstream wrote the block, called {@code getState().update(true)} "for good measure", then
     * re-read the block to check the write had landed. Both belong to Bukkit's copy-on-read block
     * states — the block entity here is the live one, so there is nothing to push back and nothing
     * to re-check.
     *
     * <p>No {@code setLevel} dance either, unlike {@code SupportBlocks.setSignText}:
     * {@code SpawnerBlockEntity.setEntityId} passes its (possibly null) level straight through to
     * {@code BaseSpawner}, which declares it {@code @Nullable}, and the {@code setChanged()} that
     * follows guards on it. This is exactly what {@code MineshaftPieces} does to place its cave
     * spider spawners during generation.
     */
    private void setSpawner(CityWorldGenerator generator, SupportBlocks blocks, Odds odds, int x, int y, int z,
            EntityType entity) {
        if (entity == null || !odds.playOdds(generator.getSettings().spawnBaddies))
            return;

        Block block = blocks.getActualBlock(x, y, z);
        block.setType(Material.SPAWNER);
        if (block.getState() instanceof SpawnerBlockEntity spawner)
            spawner.setEntityId(entity.getType(), RandomSource.create(odds.getRandomLong()));
    }
}
