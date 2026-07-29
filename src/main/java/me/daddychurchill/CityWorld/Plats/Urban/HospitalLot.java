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
    private static final Material PART = Material.WHITE_TERRACOTTA; // interior partition
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

    /** A partition wall column (interior grid line), except at the 2-wide corridor doorways. */
    private boolean partition(int rx, int rz) {
        boolean line = rx % 7 == 0 || rz % 7 == 0;
        boolean gap = (rx % 7 == 0 && (rz % 7 == 3 || rz % 7 == 4))
                || (rz % 7 == 0 && (rx % 7 == 3 || rx % 7 == 4));
        return line && !gap;
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
                    } else if (main && partition(rx, rz)) {
                        for (int yy = fy + 1; yy < fy + floorH; yy++)
                            chunk.setBlock(lx, yy, lz, PART);
                    }
                }
                boolean skylight = !wall && rx % 6 == 3 && rz % 6 == 3;
                chunk.setBlock(lx, roofY, lz, skylight ? LIGHT : ROOF);
                if (wall)
                    chunk.setBlock(lx, roofY + 1, lz, TRIM); // parapet
            }

        stairCore(chunk, street, floorH, oX, oZ);
        interiorFeatures(generator, chunk, street, floorH, oX, oZ);
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
        for (int k = 0; k < floors; k++) {
            int fy = street + k * floorH;
            for (int i = 0; i <= floorH; i++) {
                air(chunk, cx, fy + floorH, cz + i, oX, oZ); // open the ceiling above the flight
                air(chunk, cx, fy + 2 + i, cz + i, oX, oZ); // headroom
                air(chunk, cx, fy + 3 + i, cz + i, oX, oZ);
            }
            for (int i = 0; i < floorH; i++)
                stair(chunk, cx, fy + 1 + i, cz + i, oX, oZ);
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

    /** A ward bed with a bedside cabinet. */
    private void ward(RealBlocks chunk, int x, int y, int z) {
        if (z + 1 < 16 && chunk.isEmpty(x, y, z + 1))
            chunk.setBed(x, y, z, BEDS[Math.floorMod(x + z, BEDS.length)], BlockFace.SOUTH);
        if (x + 1 < 16 && chunk.isEmpty(x + 1, y, z))
            chunk.setBlock(x + 1, y, z, Material.BARREL);
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
        int ez = nwZ() + sizeZ * 16 - (main ? 6 : 1) - 1;
        if (!owns(chunk, ex, ez) || !isWall(ex, ez))
            return;
        int lx = ex - oX, lz = ez - oZ;
        if (lx + 1 >= 16)
            return; // both leaves must sit in this chunk
        chunk.setBlocks(lx, street + 1, street + 2, lz, Material.AIR);
        chunk.setBlocks(lx + 1, street + 1, street + 2, lz, Material.AIR);
        chunk.setDoubleDoor(lx, street + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
        chunk.setBlock(lx, street + 3, lz, CROSS);
        chunk.setBlock(lx + 1, street + 3, lz, CROSS);
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
            int wz = nwZ() + sizeZ * 16 - (main ? 6 : 1) - 1;
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
