package me.daddychurchill.CityWorld.Plats.Urban;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.block.entity.ChestBlockEntity;

/**
 * One chunk-slice of a HOSPITAL building — the big multi-chunk sprawling general hospital (wings around
 * courtyards) or a small single-chunk ancillary department. Multi-chunk like {@link BigBiodomeLot}: the
 * placer picks size/floors/kind once and hands each chunk its offset; each chunk draws only the part of the
 * building in its own 0..15 columns from a shared, deterministic footprint mask (world-coord based, so the
 * slices join seamlessly). White NHS-ish palette, green medical crosses (the red cross is a protected
 * emblem), partitioned wards + labs, a stair core, recessed lighting, "Dr." clerics and a medicine chest.
 */
public class HospitalLot extends IsolatedLot {

    private static final Material WALL = Material.WHITE_CONCRETE;
    private static final Material TRIM = Material.LIGHT_GRAY_CONCRETE;
    private static final Material WINDOW = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material ROOF = Material.LIGHT_GRAY_CONCRETE;
    private static final Material CROSS = Material.GREEN_CONCRETE;
    private static final Material LIGHT = Material.SEA_LANTERN;

    private static final String[] PREFIX = { "St. Mary's", "St. Thomas'", "Royal", "King's", "Queen's",
            "Prince Edward", "St. James's", "Victoria", "General", "Trinity" };
    private static final String[] SUFFIX = { "General Hospital", "Royal Infirmary", "Memorial Hospital",
            "University Hospital", "Medical Centre", "Trust Hospital" };
    private static final String[] DEPT = { "A & E", "Radiology", "Maternity", "Outpatients", "Pharmacy",
            "Cardiology", "Pathology", "Physiotherapy", "Oncology", "Paediatrics" };
    private static final Material[] BEDS = { Material.WHITE_BED, Material.LIGHT_GRAY_BED, Material.LIGHT_BLUE_BED };

    private final int sizeX, sizeZ, offX, offZ, floors;
    private final boolean main;

    public HospitalLot(PlatMap platmap, int chunkX, int chunkZ, int sizeX, int sizeZ, int offX, int offZ,
            int floors, boolean main) {
        super(platmap, chunkX, chunkZ);
        style = LotStyle.STRUCTURE;
        this.sizeX = sizeX;
        this.sizeZ = sizeZ;
        this.offX = offX;
        this.offZ = offZ;
        this.floors = floors;
        this.main = main;
    }

    @Override
    public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
        return new HospitalLot(platmap, chunkX, chunkZ, sizeX, sizeZ, offX, offZ, floors, main);
    }

    public boolean isMainBuilding() {
        return main;
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

    private int nwX() {
        return (getChunkX() - offX) * 16;
    }

    private int nwZ() {
        return (getChunkZ() - offZ) * 16;
    }

    /** Is this world column part of the building mass? Big hospitals are a perimeter ring of wings plus a
     *  central cross-corridor around courtyards; small ancillaries are a solid box. Inset 1 from the edge. */
    private boolean isSolid(int wx, int wz) {
        int rx = wx - nwX(), rz = wz - nwZ();
        int w = sizeX * 16, h = sizeZ * 16;
        if (rx < 1 || rx >= w - 1 || rz < 1 || rz >= h - 1)
            return false;
        if (!main)
            return true;
        int band = 6;
        boolean wing = rx < band || rx >= w - band || rz < band || rz >= h - band;
        boolean spine = Math.abs(rx - w / 2) < 3 || Math.abs(rz - h / 2) < 3;
        return wing || spine;
    }

    private boolean isWall(int wx, int wz) {
        return isSolid(wx, wz) && (!isSolid(wx - 1, wz) || !isSolid(wx + 1, wz) || !isSolid(wx, wz - 1)
                || !isSolid(wx, wz + 1));
    }

    @Override
    protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
            BiomeGrid biomes, DataContext context, int platX, int platZ) {
        chunk.airoutLayer(generator, generator.streetLevel + 1, floors * DataContext.FloorHeight + 4, 0, true);
        chunk.setLayer(generator.streetLevel, generator.oreProvider.surfaceMaterial);
        chunk.setLayer(generator.streetLevel - 3, 3, Material.DIRT);
    }

    @Override
    protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
            DataContext context, int platX, int platZ) {
        int street = generator.streetLevel;
        int floorH = DataContext.FloorHeight;
        int roofY = street + floors * floorH;
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        if (offX == 0 && offZ == 0)
            generator.reportLocation(main ? "Hospital: " + name() : "Hospital dept: " + name(), chunk);

        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (!isSolid(wx, wz))
                    continue;
                int rx = wx - nwX(), rz = wz - nwZ();
                boolean wall = isWall(wx, wz);
                chunk.setBlock(lx, street, lz, TRIM); // ground slab
                for (int k = 0; k < floors; k++) {
                    int fy = street + k * floorH;
                    // storey floor (= ceiling below); some become recessed glowing panels for lighting
                    boolean light = k >= 1 && rx % 5 == 2 && rz % 5 == 2;
                    chunk.setBlock(lx, fy, lz, k == 0 ? TRIM : (light ? LIGHT : WALL));
                    if (wall) {
                        for (int yy = fy + 1; yy < fy + floorH; yy++) {
                            boolean win = yy == fy + 2 && ((wx + wz) & 1) == 0;
                            chunk.setBlock(lx, yy, lz, win ? WINDOW : WALL);
                        }
                    } else if (main && corridor(rx, rz)) {
                        chunk.setBlock(lx, fy + 1, lz, Material.LIGHT_GRAY_CARPET); // carpet runner
                    }
                }
                boolean skylight = !wall && rx % 6 == 3 && rz % 6 == 3;
                chunk.setBlock(lx, roofY, lz, skylight ? LIGHT : ROOF);
                if (wall)
                    chunk.setBlock(lx, roofY + 1, lz, TRIM); // parapet
            }

        stairCore(chunk, street, floorH, oX, oZ);
        if (main)
            decorateWings(generator, chunk, street, floorH, oX, oZ); // wall-hugging beds/offices in the wings
        else
            interiorFeatures(generator, chunk, street, floorH, oX, oZ); // grid wards in the solid ancillary box
        entrance(chunk, street, oX, oZ);
        roofCross(chunk, roofY, oX, oZ);
        wallCrosses(chunk, street, oX, oZ);
    }

    /** A straight-run stair core near the front centre, climbing every storey (with the ceiling opened
     *  above each flight so you can reach the next floor). */
    private void stairCore(RealBlocks chunk, int street, int floorH, int oX, int oZ) {
        int cx = nwX() + sizeX * 8, cz = nwZ() + 3;
        if (!isSolid(cx, cz) || isWall(cx, cz))
            return;
        for (int k = 0; k < floors - 1; k++) { // between-floor flights only — don't climb into the roof
            int fy = street + k * floorH;
            for (int i = 0; i <= floorH; i++) {
                if (i < floorH)
                    air(chunk, cx, fy + floorH, cz + i, oX, oZ); // open the ceiling OVER the flight, but leave
                                                                 // the landing block (cz+floorH) solid to step onto
                air(chunk, cx, fy + 2 + i, cz + i, oX, oZ); // headroom
                air(chunk, cx, fy + 3 + i, cz + i, oX, oZ);
            }
            for (int i = 0; i < floorH; i++)
                stair(chunk, cx, fy + 1 + i, cz + i, oX, oZ);
        }
    }

    /** Decoration tuned to the courtyard hospital's narrow ring-wings: furniture hugs the walls (beds run
     *  parallel to the wall, little desk-and-chair nooks tuck along it) so the corridor down the middle of
     *  each wing stays clear. A mix of wards, offices, medics, medicine chests and lab stations, spaced out
     *  along the wings and deterministic by world position so multi-chunk slices agree. */
    private void decorateWings(CityWorldGenerator generator, RealBlocks chunk, int street, int floorH, int oX,
            int oZ) {
        int entX = sizeX * 8 - 1, entZ = sizeZ * 16 - 2; // keep the main doorway a clear reception space
        for (int lx = 0; lx < 16; lx++)
            for (int lz = 0; lz < 16; lz++) {
                int wx = oX + lx, wz = oZ + lz;
                if (!isSolid(wx, wz) || isWall(wx, wz))
                    continue;
                BlockFace wall = wallSide(wx, wz);
                if (wall == null)
                    continue; // middle-of-corridor lane — leave it open to walk down
                int rx = wx - nwX(), rz = wz - nwZ();
                if (Math.abs(rx - entX) <= 1 && rz >= entZ - 3)
                    continue;
                boolean runZ = wall == BlockFace.EAST || wall == BlockFace.WEST; // wing runs N-S along this wall
                if (Math.floorMod(runZ ? wz : wx, 3) != 0)
                    continue; // one piece every three cells so nothing runs together
                for (int k = 0; k < floors; k++) {
                    int y = street + k * floorH + 1;
                    if (!chunk.isEmpty(lx, y, lz) || chunk.isEmpty(lx, y - 1, lz))
                        continue;
                    switch (Math.floorMod(wx * 13 + wz * 7 + k * 101, 10)) {
                    case 0, 1, 2, 3 -> wingBed(chunk, lx, y, lz, runZ);
                    case 4, 5, 6 -> officeNook(chunk, lx, y, lz, runZ);
                    case 7 -> generator.spawnProvider.spawnMedic(generator, chunk, chunkOdds, lx, y, lz);
                    case 8 -> medicineChest(chunk, lx, y, lz);
                    default -> chunk.setBlock(lx, y, lz, Material.BREWING_STAND); // a little lab station
                    }
                }
            }
    }

    /** Direction to an adjacent wall/void, or null if this cell has open floor on all four sides (a corridor
     *  lane). Used so wing furniture only goes against a wall. */
    private BlockFace wallSide(int wx, int wz) {
        if (blocked(wx, wz - 1))
            return BlockFace.NORTH;
        if (blocked(wx, wz + 1))
            return BlockFace.SOUTH;
        if (blocked(wx - 1, wz))
            return BlockFace.WEST;
        if (blocked(wx + 1, wz))
            return BlockFace.EAST;
        return null;
    }

    private boolean blocked(int wx, int wz) {
        return !isSolid(wx, wz) || isWall(wx, wz);
    }

    /** A bed running parallel to the wall so it hugs it instead of poking across the corridor. */
    private void wingBed(RealBlocks chunk, int x, int y, int z, boolean runZ) {
        Material bed = BEDS[Math.floorMod(x + z, BEDS.length)];
        if (runZ) {
            if (z + 1 < 16 && chunk.isEmpty(x, y, z + 1) && !chunk.isEmpty(x, y - 1, z))
                chunk.setBed(x, y, z, bed, BlockFace.SOUTH);
        } else if (x + 1 < 16 && chunk.isEmpty(x + 1, y, z) && !chunk.isEmpty(x, y - 1, z)) {
            chunk.setBed(x, y, z, bed, BlockFace.EAST);
        }
    }

    /** A desk with a chair drawn up to it, both hugging the wall along the wing. */
    private void officeNook(RealBlocks chunk, int x, int y, int z, boolean runZ) {
        chunk.setBlock(x, y, z, Material.QUARTZ_BLOCK); // the desk
        chunk.setBlock(x, y + 1, z, Material.POTTED_FERN); // a little something on it
        if (runZ) {
            if (z + 1 < 16 && chunk.isEmpty(x, y, z + 1) && !chunk.isEmpty(x, y - 1, z + 1))
                chunk.setBlock(x, y, z + 1, Material.QUARTZ_STAIRS, BlockFace.SOUTH); // chair faces the desk
        } else if (x + 1 < 16 && chunk.isEmpty(x + 1, y, z) && !chunk.isEmpty(x + 1, y - 1, z)) {
            chunk.setBlock(x + 1, y, z, Material.QUARTZ_STAIRS, BlockFace.EAST); // chair faces the desk
        }
    }

    /** One feature per interior room cell per storey: ward beds, a lab (brewing stand), a medicine chest,
     *  a "Dr." cleric, or a plant. Deterministic by world position so multi-chunk slices agree. */
    private void interiorFeatures(CityWorldGenerator generator, RealBlocks chunk, int street, int floorH, int oX,
            int oZ) {
        int w = sizeX * 16, h = sizeZ * 16;
        for (int rx = 4; rx < w - 2; rx += 7)
            for (int rz = 5; rz < h - 2; rz += 7) {
                int wx = nwX() + rx, wz = nwZ() + rz;
                if (!owns(chunk, wx, wz) || !isSolid(wx, wz) || isWall(wx, wz))
                    continue;
                int lx = wx - oX, lz = wz - oZ;
                for (int k = 0; k < floors; k++) {
                    int y = street + k * floorH + 1;
                    if (!chunk.isEmpty(lx, y, lz) || chunk.isEmpty(lx, y - 1, lz))
                        continue;
                    switch (Math.floorMod(wx * 13 + wz * 7 + k * 101, 12)) {
                    case 0, 1, 2, 3, 4 -> ward(chunk, lx, y, lz);
                    case 5 -> lab(chunk, lx, y, lz);
                    case 6 -> medicineChest(chunk, lx, y, lz);
                    case 7 -> generator.spawnProvider.spawnMedic(generator, chunk, chunkOdds, lx, y, lz);
                    case 8 -> chunk.setBlock(lx, y, lz, Material.POTTED_FERN);
                    default -> {
                    }
                    }
                }
            }
    }

    /** A ward bay: two beds side by side (heads to the north) with a white privacy curtain between them.
     *  Both beds sit a column in from the room centre and one row up (head at {@code z-1}), so each keeps a
     *  clear gap to the partition walls on every side instead of being jammed against a divider. */
    private void ward(RealBlocks chunk, int x, int y, int z) {
        bed(chunk, x - 1, y, z - 1);
        bed(chunk, x + 1, y, z - 1);
        curtain(chunk, x, y, z - 1);
    }

    private void bed(RealBlocks chunk, int x, int y, int z) {
        if (x >= 0 && x < 16 && z + 1 < 16 && chunk.isEmpty(x, y, z) && chunk.isEmpty(x, y, z + 1)
                && !chunk.isEmpty(x, y - 1, z))
            chunk.setBed(x, y, z, BEDS[Math.floorMod(x + z, BEDS.length)], BlockFace.SOUTH);
    }

    private void curtain(RealBlocks chunk, int x, int y, int z) {
        for (int dz = 0; dz <= 1; dz++)
            for (int dy = 0; dy <= 1; dy++)
                if (x >= 0 && x < 16 && z + dz < 16 && chunk.isEmpty(x, y + dy, z + dz))
                    chunk.setBlock(x, y + dy, z + dz, Material.WHITE_WOOL);
    }

    private boolean corridor(int rx, int rz) {
        return Math.abs(rx - sizeX * 8) <= 1 || Math.abs(rz - sizeZ * 8) <= 1; // the central cross corridor
    }

    /** A little lab: a brewing stand with a cauldron beside it. */
    private void lab(RealBlocks chunk, int x, int y, int z) {
        chunk.setBlock(x, y, z, Material.BREWING_STAND);
        if (x + 1 < 16)
            chunk.setBlock(x + 1, y, z, Material.CAULDRON);
    }

    /** A medicine chest — a few Potions of Healing and some "Bandage" paper. Uses the region-level block
     *  entity ({@code getActualBlock().getState()}, how signs/spawners are written during worldgen) — the
     *  underlying ServerLevel can't see a block just set in the generation region yet. */
    private void medicineChest(RealBlocks chunk, int x, int y, int z) {
        chunk.setBlock(x, y, z, Material.CHEST, BlockFace.SOUTH);
        if (!(chunk.getActualBlock(x, y, z).getState() instanceof ChestBlockEntity chest))
            return;
        for (int s = 0; s < 3; s++) {
            ItemStack potion = new ItemStack(Items.POTION);
            potion.set(DataComponents.POTION_CONTENTS, new PotionContents(Potions.HEALING));
            chest.setItem(s + 2, potion);
        }
        ItemStack bandage = new ItemStack(Items.PAPER, 4);
        bandage.set(DataComponents.CUSTOM_NAME, Component.literal("Bandage"));
        chest.setItem(6, bandage);
    }

    /** A grand double door on the south face at ground level, with a green cross and the name over it. */
    private void entrance(RealBlocks chunk, int street, int oX, int oZ) {
        int ex = nwX() + sizeX * 8 - 1; // south-centre (leaves room for the second door leaf)
        int ez = nwZ() + sizeZ * 16 - 2; // the EXTERIOR south wall (inset 1 from the footprint edge)
        if (!owns(chunk, ex, ez) || !isWall(ex, ez))
            return;
        int lx = ex - oX, lz = ez - oZ;
        boolean two = lx + 1 < 16; // room for the second leaf in this chunk?
        chunk.setBlocks(lx, street + 1, street + 2, lz, Material.AIR);
        chunk.setBlock(lx, street + 3, lz, CROSS);
        if (two) {
            chunk.setBlocks(lx + 1, street + 1, street + 2, lz, Material.AIR);
            chunk.setDoubleDoor(lx, street + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
            chunk.setBlock(lx + 1, street + 3, lz, CROSS);
        } else {
            chunk.setDoor(lx, street + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
        }
        if (lz + 1 < 16)
            chunk.setWallSign(lx, street + 3, lz + 1, Material.OAK_WALL_SIGN, BlockFace.SOUTH, signLines());
    }

    private void roofCross(RealBlocks chunk, int roofY, int oX, int oZ) {
        int cx = nwX() + sizeX * 8, cz = nwZ() + sizeZ * 8;
        int arm = main ? 3 : 1;
        for (int d = -arm; d <= arm; d++) {
            green(chunk, cx + d, roofY + 1, cz, oX, oZ);
            green(chunk, cx, roofY + 1, cz + d, oX, oZ);
        }
    }

    private void wallCrosses(RealBlocks chunk, int street, int oX, int oZ) {
        int y = street + DataContext.FloorHeight + 2;
        for (int wx = nwX() + 3; wx < nwX() + sizeX * 16 - 3; wx += 7) {
            int wz = nwZ() + sizeZ * 16 - 2; // the exterior south wall
            if (!isWall(wx, wz))
                continue;
            green(chunk, wx, y, wz, oX, oZ);
            green(chunk, wx - 1, y, wz, oX, oZ);
            green(chunk, wx + 1, y, wz, oX, oZ);
            green(chunk, wx, y - 1, wz, oX, oZ);
            green(chunk, wx, y + 1, wz, oX, oZ);
        }
    }

    private void green(RealBlocks chunk, int wx, int y, int wz, int oX, int oZ) {
        if (wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16)
            chunk.setBlock(wx - oX, y, wz - oZ, CROSS);
    }

    private void air(RealBlocks chunk, int wx, int y, int wz, int oX, int oZ) {
        if (wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16)
            chunk.setBlock(wx - oX, y, wz - oZ, Material.AIR);
    }

    private void stair(RealBlocks chunk, int wx, int y, int wz, int oX, int oZ) {
        if (wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16)
            chunk.setBlock(wx - oX, y, wz - oZ, Material.QUARTZ_STAIRS, BlockFace.SOUTH);
    }

    private boolean owns(RealBlocks chunk, int wx, int wz) {
        int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
        return wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16;
    }

    private int hash() {
        return Math.floorMod(nwX() * 31 + nwZ() * 17, 1_000_000);
    }

    private String name() {
        return main ? PREFIX[hash() % PREFIX.length] + " " + SUFFIX[(hash() / 7) % SUFFIX.length]
                : DEPT[hash() % DEPT.length];
    }

    private String[] signLines() {
        if (main)
            return new String[] { PREFIX[hash() % PREFIX.length], SUFFIX[(hash() / 7) % SUFFIX.length] };
        return new String[] { "Dept.", DEPT[hash() % DEPT.length] };
    }
}
