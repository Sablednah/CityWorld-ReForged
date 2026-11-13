package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.MaterialTags;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.resources.Identifier;

/**
 * A Pokémon Center: Cobblemon's healing machine behind a reception counter, a bank of PCs, somewhere to sit, beds
 * upstairs, a pasture out the back, a nurse — in a one-chunk white building with a red band and a Poké Ball on the
 * roof, its door to the street. Planned in lowrise, midrise and highrise districts at {@code shops.oddsOfPokecenter},
 * and only while {@code #cityworld:fittings/healing_machine} resolves (Cobblemon ships the only entry), so a world
 * without the mod never rolls for one and plans exactly as before (GitHub issue #6).
 *
 * <p>Drawn in a canonical orientation — the street to the SOUTH — and turned to the side the road is really on by
 * {@link Frame}, so the door, the counter and the sign face the right way whichever neighbour is the road. The
 * machine, PCs and pasture come from their fittings pools, so a pack can point the Center at another mod's blocks.
 * Containers roll {@code cityworld:chests/pokecenter} (+ its {@code _extra} hook); the nurse is a villager in
 * Cobblemon's {@code cobblemon:nurse} profession, which is what gives her the texture and the healing-machine job.
 */
public class PokecenterLot extends IsolatedLot {
    private static final Material WALL = Material.WHITE_CONCRETE;
    private static final Material TRIM = Material.RED_CONCRETE;
    private static final Material DARK = Material.BLACK_CONCRETE;
    private static final Material WINDOW = Material.GLASS;
    private static final Material FLOOR = Material.WHITE_CONCRETE;
    private static final Material COUNTER = Material.QUARTZ_BLOCK;
    private static final Material LIGHT = Material.SEA_LANTERN;
    private static final Material RUNNER = Material.RED_CARPET;
    private static final Material[] BEDS = { Material.RED_BED, Material.WHITE_BED, Material.RED_BED, Material.WHITE_BED };
    private static final Identifier NURSE = Identifier.fromNamespaceAndPath("cobblemon", "nurse");
    private static final int FLOORS = 2;

    public PokecenterLot(PlatMap platmap, int chunkX, int chunkZ) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
    }

    /** Whether a Center can be built in this game: something fills the healing-machine pool. */
    public static boolean available() {
        return !MaterialTags.resolve(MaterialTags.FITTINGS_HEALING_MACHINE).isEmpty();
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new PokecenterLot(platmap, chunkX, chunkZ);
    }

    @Override
    public LootProvider.LootLocation defaultLoot() {
        return LootProvider.LootLocation.POKECENTER;
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
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, FLOORS * DataContext.FloorHeight + 4, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    /**
     * The canonical drawing frame: {@code u} runs west to east and {@code v} north to south across the building, the
     * street at {@code v = 14}. {@link #x}/{@link #z} turn a canonical cell to the chunk, {@link #face} a canonical
     * direction, so the whole lot is laid out once and faces its road.
     */
    static final class Frame {
        final BlockFace front;

        Frame(BlockFace front) {
            this.front = front;
        }

        int x(int u, int v) {
            return switch (front) {
            case NORTH, SOUTH -> u;
            case EAST -> v;
            default -> 15 - v;
            };
        }

        int z(int u, int v) {
            return switch (front) {
            case SOUTH -> v;
            case NORTH -> 15 - v;
            default -> u;
            };
        }

        BlockFace face(BlockFace d) {
            int dx = d.getModX(), dz = d.getModZ(), rx, rz;
            switch (front) {
            case SOUTH -> { rx = dx; rz = dz; }
            case NORTH -> { rx = dx; rz = -dz; }
            case EAST -> { rx = dz; rz = dx; }
            default -> { rx = -dz; rz = dx; }
            }
            for (BlockFace f : new BlockFace[] { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST })
                if (f.getModX() == rx && f.getModZ() == rz)
                    return f;
            return d;
        }

        /** The yaw (degrees, Minecraft's: 0 = south) of a canonical facing. */
        int yaw(BlockFace d) {
            return switch (face(d)) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            default -> 270;
            };
        }

        /** The side of the lot a road is on, the street taking precedence over anything else; south when none. */
        static Frame of(PlatMap platmap, int platX, int platZ) {
            if (platmap.isExistingRoad(platX, platZ + 1))
                return new Frame(BlockFace.SOUTH);
            if (platmap.isExistingRoad(platX, platZ - 1))
                return new Frame(BlockFace.NORTH);
            if (platmap.isExistingRoad(platX + 1, platZ))
                return new Frame(BlockFace.EAST);
            if (platmap.isExistingRoad(platX - 1, platZ))
                return new Frame(BlockFace.WEST);
            return new Frame(BlockFace.SOUTH);
        }
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int street = generator.streetLevel;
        int fh = DataContext.FloorHeight;
        int roofY = street + FLOORS * fh;
        Frame f = Frame.of(platmap, platX, platZ);
        generator.reportLocation("pokecenter", "Pokémon Center", chunk);

        // the shell: a slab, two storeys of white wall with window rows, a red band under the roof, a parapet
        for (int u = 1; u <= 14; u++)
            for (int v = 1; v <= 14; v++) {
                int x = f.x(u, v), z = f.z(u, v);
                boolean wall = u == 1 || u == 14 || v == 1 || v == 14;
                chunk.setBlock(x, street, z, FLOOR);
                for (int k = 0; k < FLOORS; k++) {
                    int fy = street + k * fh;
                    if (k > 0)
                        chunk.setBlock(x, fy, z, isCeilingLight(u, v) ? LIGHT : WALL);
                    if (wall)
                        for (int yy = fy + 1; yy < fy + fh; yy++) {
                            boolean band = k == FLOORS - 1 && yy == fy + fh - 1;
                            boolean window = yy == fy + 2 && !isCorner(u, v) && (u + v) % 2 == 0;
                            chunk.setBlock(x, yy, z, band ? TRIM : window ? WINDOW : WALL);
                        }
                }
                chunk.setBlock(x, roofY, z, roofBlock(u, v));
                if (wall)
                    chunk.setBlock(x, roofY + 1, z, TRIM);
            }

        // the door to the street, under a red awning, the sign beside it
        int dy = street + 1;
        for (int u = 7; u <= 8; u++)
            for (int yy = dy; yy <= dy + 1; yy++)
                chunk.setBlock(f.x(u, 14), yy, f.z(u, 14), Material.AIR);
        chunk.setDoubleDoor(f.x(7, 14), dy, f.z(7, 14), Material.OAK_DOOR, f.face(BlockFace.SOUTH));
        // the awning and sign hang over the SIDEWALK, which stands a block above the street (and our floor): dy + 2
        // was head height for someone walking in (owner, 2026-11-13: "door is blocked by the red")
        for (int u = 6; u <= 9; u++)
            chunk.setBlock(f.x(u, 15), dy + 3, f.z(u, 15), TRIM);
        chunk.setWallSign(f.x(9, 15), dy + 2, f.z(9, 15), f.face(BlockFace.SOUTH), "Pokémon", "Center");

        // reception: the counter across the room, the healing machine and nurse behind it, a runner from the door
        for (int u = 5; u <= 9; u++)
            chunk.setBlock(f.x(u, 4), dy, f.z(u, 4), COUNTER);
        Material machine = MaterialTags.pick(MaterialTags.FITTINGS_HEALING_MACHINE, chunkOdds, null);
        if (machine != null)
            chunk.setBlock(f.x(7, 3), dy, f.z(7, 3), machine, f.face(BlockFace.SOUTH));
        for (int v = 5; v <= 13; v++)
            for (int u = 7; u <= 8; u++)
                chunk.setBlock(f.x(u, v), dy, f.z(u, v), RUNNER);
        generator.spawnProvider.spawnWorker(generator, chunk, chunkOdds, f.x(9, 3), dy, f.z(9, 3), NURSE);

        // the PC bank along the west wall, screens to the room; seating along the east wall facing them
        Material pc = MaterialTags.pick(MaterialTags.FITTINGS_PC, chunkOdds, null);
        if (pc != null)
            for (int v = 6; v <= 9; v++)
                chunk.setFurniture(f.x(2, v), dy, f.z(2, v), pc, FurnitureTags.facingFor(pc, f.face(BlockFace.EAST)));
        for (int v = 6; v <= 10; v += 2)
            chair(chunk, f.x(13, v), dy, f.z(13, v), f.face(BlockFace.WEST));
        Material table = FurnitureTags.pick(FurnitureTags.TABLE, chunkOdds);
        chunk.setBlock(f.x(12, 8), dy, f.z(12, 8), table != null ? table : Material.QUARTZ_BLOCK);
        Material planter = FurnitureTags.pick(FurnitureTags.SURFACE_DECOR, chunkOdds);
        chunk.setBlock(f.x(12, 8), dy + 1, f.z(12, 8), planter != null ? planter : Material.POTTED_FERN);
        for (int u = 2; u <= 13; u += 11)
            chunk.setBlock(f.x(u, 13), dy, f.z(u, 13), Material.POTTED_FERN);

        // the back room: the pasture, a supply chest, the ladder to the beds
        Material pasture = MaterialTags.pick(MaterialTags.FITTINGS_PASTURE, chunkOdds, null);
        if (pasture != null)
            chunk.setFurniture(f.x(11, 2), dy, f.z(11, 2), pasture, FurnitureTags.facingFor(pasture, f.face(BlockFace.SOUTH)));
        chunk.setChest(generator, f.x(2, 2), dy, f.z(2, 2), f.face(BlockFace.SOUTH), chunkOdds, generator.lootProvider,
                LootProvider.LootLocation.POKECENTER);
        ladder(chunk, f, street, fh);

        // upstairs: beds for trainers, two more PCs, a window seat
        int uy = street + fh + 1;
        chunk.setBed(f.x(3, 2), uy, f.z(3, 2), BEDS[0], f.face(BlockFace.NORTH));
        chunk.setBed(f.x(5, 2), uy, f.z(5, 2), BEDS[1], f.face(BlockFace.NORTH));
        chunk.setBed(f.x(3, 11), uy, f.z(3, 11), BEDS[2], f.face(BlockFace.SOUTH));
        chunk.setBed(f.x(5, 11), uy, f.z(5, 11), BEDS[3], f.face(BlockFace.SOUTH));
        if (pc != null)
            for (int v = 7; v <= 8; v++)
                chunk.setFurniture(f.x(13, v), uy, f.z(13, v), pc, FurnitureTags.facingFor(pc, f.face(BlockFace.WEST)));
        chair(chunk, f.x(10, 12), uy, f.z(10, 12), f.face(BlockFace.NORTH));
        chair(chunk, f.x(11, 12), uy, f.z(11, 12), f.face(BlockFace.NORTH));
        chunk.setChest(generator, f.x(12, 2), uy, f.z(12, 2), f.face(BlockFace.SOUTH), chunkOdds, generator.lootProvider,
                LootProvider.LootLocation.POKECENTER);
    }

    private static boolean isCorner(int u, int v) {
        return (u == 1 || u == 14) && (v == 1 || v == 14);
    }

    private static boolean isCeilingLight(int u, int v) {
        return (u == 4 || u == 11) && (v == 4 || v == 11);
    }

    /** The roof is a Poké Ball seen from above: red to the back, white to the street, a black band with the button. */
    private static Material roofBlock(int u, int v) {
        boolean band = v == 7 || v == 8;
        boolean button = u >= 6 && u <= 9 && v >= 6 && v <= 9;
        boolean buttonCentre = u >= 7 && u <= 8 && band;
        if (buttonCentre)
            return Material.WHITE_CONCRETE;
        if (band || button)
            return DARK;
        return v < 7 ? TRIM : Material.WHITE_CONCRETE;
    }

    private void chair(RealBlocks chunk, int x, int y, int z, BlockFace facing) {
        Material chair = FurnitureTags.pick(FurnitureTags.CHAIR, chunkOdds);
        if (chair != null)
            chunk.setBlock(x, y, z, chair, FurnitureTags.facingFor(chair, facing));
        else
            chunk.setBlock(x, y, z, Material.QUARTZ_STAIRS, facing.getOppositeFace());
    }

    /** A ladder up the back wall from the ground floor to the beds, the floor over it opened. */
    private void ladder(RealBlocks chunk, Frame f, int street, int fh) {
        int x = f.x(13, 2), z = f.z(13, 2);
        chunk.setBlock(x, street + fh, z, Material.AIR);
        chunk.setLadder(x, street + 1, street + fh + 1, z, f.face(BlockFace.SOUTH));
    }

    /** For the plan's F3 line and the map hover. */
    @Override
    public String getInteriorDescription() {
        return "Pokémon Center";
    }
}
