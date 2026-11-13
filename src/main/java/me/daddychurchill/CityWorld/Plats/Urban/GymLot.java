package me.daddychurchill.CityWorld.Plats.Urban;

import java.util.Locale;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plats.Urban.PokecenterLot.Frame;
import me.daddychurchill.CityWorld.Plugins.LootProvider;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.Spawns;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

/**
 * A Pokémon gym: a two-storey battle hall in a municipal district — the arena marked out in the gym's type colour
 * with its type gem at the centre, bleachers down both sides, the leader waiting behind the arena, a prize chest —
 * typed by position so a city's gyms differ (fire, water, grass, electric, rock, ice, psychic, fighting). Planned
 * at {@code shops.oddsOfGym} and only while a Pokémon Center is {@link PokecenterLot#available() available}, which
 * is Cobblemon being installed (GitHub issue #6).
 *
 * <p>The leader is a Cobblemon NPC ({@code cobblemon:npc}) of the class CityWorld ships under
 * {@code data/cityworld/npcs/gym_leader_<type>.json} — a typed party, names, the challenge dialogue
 * {@code cityworld:gym_leader} — written into the chunk the way the {@code cityworld:spawn} registry writes its
 * entities, at {@code shops.gymLeaderLevel} (the party is formed from the class's pool at challenge time, at that
 * level: Cobblemon's static parties are only built on its own spawn path, so ours is {@code isStatic: false}); a Cobblemon without NPCs (1.5.x) gets a villager referee instead. Containers roll
 * {@code cityworld:chests/gym} (+ {@code _extra}).
 */
public class GymLot extends IsolatedLot {
    /** A gym's type: its concrete, the gem block at the arena's heart, and the NPC class suffix. */
    public enum Type {
        FIRE(Material.RED_CONCRETE, Material.ORANGE_CONCRETE), WATER(Material.BLUE_CONCRETE, Material.LIGHT_BLUE_CONCRETE),
        GRASS(Material.GREEN_CONCRETE, Material.LIME_CONCRETE), ELECTRIC(Material.YELLOW_CONCRETE, Material.BLACK_CONCRETE),
        ROCK(Material.BROWN_CONCRETE, Material.GRAY_CONCRETE), ICE(Material.LIGHT_BLUE_CONCRETE, Material.WHITE_CONCRETE),
        PSYCHIC(Material.MAGENTA_CONCRETE, Material.PURPLE_CONCRETE), FIGHTING(Material.ORANGE_CONCRETE, Material.BROWN_CONCRETE);

        final Material main, accent;

        Type(Material main, Material accent) {
            this.main = main;
            this.accent = accent;
        }

        String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        String title() {
            return name().charAt(0) + id().substring(1);
        }

        /** Cobblemon's gem block of this type, or the accent concrete when there is none. */
        Material gem() {
            Material gem = Material.of("cobblemon:" + id() + "_gem_block");
            return gem.getBlock() == net.minecraft.world.level.block.Blocks.AIR ? accent : gem;
        }
    }

    private static final Material WALL = Material.WHITE_CONCRETE;
    private static final Material LIGHT = Material.SEA_LANTERN;
    private static final Material WINDOW = Material.GLASS;
    private static final Material SEAT = Material.QUARTZ_STAIRS;
    private static final ResourceLocation NPC = ResourceLocation.fromNamespaceAndPath("cobblemon", "npc");
    private static final int FLOORS = 2;

    private final Type type;

    public GymLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        // by position, not by the lot's dice: neighbouring chunks' first rolls are correlated
        Type[] types = Type.values();
        type = types[Math.floorMod(chunkX * 7 + chunkZ * 13 + (int) (platmap.generator.getWorldSeed() % 1000), types.length)];
    }

    public Type getType() {
        return type;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new GymLot(platmap, chunkX, chunkZ);
    }

    @Override
    public LootProvider.LootLocation defaultLoot() {
        return LootProvider.LootLocation.GYM;
    }

    @Override
    public boolean allowsWildDecoration() {
        return false;
    }

    @Override
    public int getBottomY(CityWorldGenerator generator) {
        return generator.streetLevel;
    }

    @Override
    public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
        return generator.streetLevel;
    }

    @Override
    public String getInteriorDescription() {
        return type.title() + " gym";
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, FLOORS * DataContext.FloorHeight + 4, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int street = generator.streetLevel;
        int fh = DataContext.FloorHeight;
        int roofY = street + FLOORS * fh;
        Frame f = Frame.of(platmap, platX, platZ);
        generator.reportLocation("gym", type.title() + " Gym", chunk);

        // the hall: one tall room, the type's colour on the walls with a white band at each storey, windows high up
        for (int u = 1; u <= 14; u++)
            for (int v = 1; v <= 14; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                boolean wall = u == 1 || u == 14 || v == 1 || v == 14;
                chunk.setBlock(x, street, z, arenaBlock(u, v));
                if (wall)
                    for (int yy = street + 1; yy < roofY; yy++) {
                        int k = (yy - street - 1) % fh;
                        boolean band = k == fh - 1;
                        boolean window = yy > street + fh && k == 1 && (u + v) % 2 == 0 && !corner(u, v);
                        chunk.setBlock(x, yy, z, band ? WALL : window ? WINDOW : type.main);
                    }
                boolean light = (u == 4 || u == 11) && (v == 4 || v == 11);
                chunk.setBlock(x, roofY, z, light ? LIGHT : roofBlock(u, v));
                if (wall)
                    chunk.setBlock(x, roofY + 1, z, type.accent);
            }

        // the door to the street and the sign
        int dy = street + 1;
        for (int u = 7; u <= 8; u++)
            for (int yy = dy; yy <= dy + 1; yy++)
                chunk.setBlock(f.x(u, 14), yy, f.z(u, 14), Material.AIR);
        chunk.setDoubleDoor(f.x(7, 14), dy, f.z(7, 14), Material.DARK_OAK_DOOR, f.face(BlockFace.SOUTH));
        // the awning and sign hang over the SIDEWALK, which stands a block above the street (and our floor): dy + 2
        // was head height for someone walking in (owner, 2026-11-13: "door is blocked by the red")
        for (int u = 6; u <= 9; u++)
            chunk.setBlock(f.x(u, 15), dy + 3, f.z(u, 15), type.accent);
        chunk.setWallSign(f.x(9, 15), dy + 2, f.z(9, 15), f.face(BlockFace.SOUTH), "Pokémon Gym", type.title());

        // bleachers down both long sides, the seats facing the arena
        for (int v = 4; v <= 11; v++) {
            chunk.setBlock(f.x(2, v), dy, f.z(2, v), SEAT, f.face(BlockFace.WEST));
            chunk.setBlock(f.x(13, v), dy, f.z(13, v), SEAT, f.face(BlockFace.EAST));
        }

        // the leader behind the arena, facing the door; the prize chest in a back corner
        leader(generator, chunk, f, dy);
        chunk.setChest(generator, f.x(2, 2), dy, f.z(2, 2), f.face(BlockFace.SOUTH), chunkOdds, generator.lootProvider,
                LootProvider.LootLocation.GYM);
        chunk.setChest(generator, f.x(13, 2), dy, f.z(13, 2), f.face(BlockFace.SOUTH), chunkOdds, generator.lootProvider,
                LootProvider.LootLocation.GYM);
    }

    private static boolean corner(int u, int v) {
        return (u == 1 || u == 14) && (v == 1 || v == 14);
    }

    /** The floor: the arena a square of the type's colour with white lines and its gem at the centre, white round it. */
    private Material arenaBlock(int u, int v) {
        boolean inArena = u >= 4 && u <= 11 && v >= 4 && v <= 11;
        if (!inArena)
            return WALL;
        boolean line = u == 4 || u == 11 || v == 4 || v == 11 || v == 7 || v == 8;
        boolean centre = u >= 7 && u <= 8 && v >= 7 && v <= 8;
        if (centre)
            return type.gem();
        return line ? WALL : type.main;
    }

    /** The roof: the type's colour with a white badge in the middle. */
    private Material roofBlock(int u, int v) {
        boolean badge = u >= 6 && u <= 9 && v >= 6 && v <= 9;
        boolean heart = u >= 7 && u <= 8 && v >= 7 && v <= 8;
        return heart ? type.accent : badge ? WALL : type.main;
    }

    /** The gym leader: Cobblemon's NPC of our class for this type, or a villager when Cobblemon has no NPCs. */
    private void leader(CityWorldGenerator generator, RealBlocks chunk, Frame f, int y) {
        int x = f.x(7, 3), z = f.z(7, 3);
        if (BuiltInRegistries.ENTITY_TYPE.containsKey(NPC)) {
            try {
                // Level: the NPC's own level, which its party is formed at when challenged (a party pool entry's
                // npcLevels is a filter on it; an unset level loads as 1 and would empty the pool)
                var data = Spawns.parseCompound("{NPCClass:\"cityworld:gym_leader_" + type.id() + "\",Level:"
                        + generator.getSettings().gymLeaderLevel + "}");
                if (Spawns.placeEntity(chunk, NPC.toString(), data, x, y, z, f.yaw(BlockFace.SOUTH), true))
                    return;
            } catch (Exception e) {
                CityWorldMod.LOGGER.debug("CityWorld: gym leader not written", e);
            }
        }
        generator.spawnProvider.spawnWorker(generator, chunk, chunkOdds, x, y, z,
                ResourceLocation.withDefaultNamespace("cleric"));
    }
}
