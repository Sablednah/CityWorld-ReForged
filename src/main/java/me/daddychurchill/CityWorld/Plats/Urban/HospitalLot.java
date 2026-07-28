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

/**
 * One chunk-slice of a HOSPITAL building — either the big multi-chunk sprawling general hospital (wings
 * around courtyards) or a small single-chunk ancillary department. Multi-chunk like {@link BigBiodomeLot}:
 * the placer picks size/floors/kind once and hands each chunk its offset; each chunk draws only the part of
 * the building in its own 0..15 columns from a shared, deterministic footprint mask (world-coord based, so
 * the slices join seamlessly). White NHS-ish palette with green medical crosses on the walls and roof, and
 * a name sign over the entrance. (The red cross is a protected emblem — green only.)
 */
public class HospitalLot extends IsolatedLot {

    private static final Material WALL = Material.WHITE_CONCRETE;
    private static final Material TRIM = Material.LIGHT_GRAY_CONCRETE;
    private static final Material WINDOW = Material.LIGHT_BLUE_STAINED_GLASS;
    private static final Material ROOF = Material.LIGHT_GRAY_CONCRETE;
    private static final Material CROSS = Material.GREEN_CONCRETE;

    private static final String[] PREFIX = { "St. Mary's", "St. Thomas'", "Royal", "King's", "Queen's",
            "Prince Edward", "St. James's", "Victoria", "General", "Trinity" };
    private static final String[] SUFFIX = { "General Hospital", "Royal Infirmary", "Memorial Hospital",
            "University Hospital", "Medical Centre", "Trust Hospital" };
    private static final String[] DEPT = { "A & E", "Radiology", "Maternity", "Outpatients", "Pharmacy",
            "Cardiology", "Pathology", "Physiotherapy", "Oncology", "Paediatrics" };

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
     *  central cross-corridor, leaving courtyards; small ancillaries are a solid box. Inset 1 from the
     *  footprint edge so there's a pavement gap to the road. */
    private boolean isSolid(int wx, int wz) {
        int rx = wx - nwX(), rz = wz - nwZ();
        int w = sizeX * 16, h = sizeZ * 16;
        if (rx < 1 || rx >= w - 1 || rz < 1 || rz >= h - 1)
            return false;
        if (!main)
            return true; // ancillary: solid box
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
                if (!isSolid(wx, wz)) {
                    // courtyard / grounds — plain grass so the wings read as separate blocks
                    continue;
                }
                boolean wall = isWall(wx, wz);
                chunk.setBlock(lx, street, lz, TRIM); // ground slab
                for (int k = 0; k < floors; k++) {
                    int fy = street + k * floorH;
                    chunk.setBlock(lx, fy, lz, k == 0 ? TRIM : WALL); // storey floor
                    if (wall) {
                        for (int yy = fy + 1; yy < fy + floorH; yy++) {
                            boolean win = yy == fy + 2 && ((wx + wz) & 1) == 0; // a striped window band
                            chunk.setBlock(lx, yy, lz, win ? WINDOW : WALL);
                        }
                    } else if (((wx * 5 + wz) % 11) == 0) {
                        chunk.setBlock(lx, fy + floorH - 1, lz, Material.SEA_LANTERN); // ward ceiling light
                    }
                }
                chunk.setBlock(lx, roofY, lz, ROOF); // flat roof
                if (wall)
                    chunk.setBlock(lx, roofY + 1, lz, TRIM); // parapet
            }

        entranceAndSign(chunk, street, oX, oZ);
        roofCross(chunk, roofY, oX, oZ);
        wallCrosses(chunk, street, oX, oZ);
    }

    /** A doorway on the south face at ground level, with a green cross and the name sign over it. */
    private void entranceAndSign(RealBlocks chunk, int street, int oX, int oZ) {
        int ex = nwX() + sizeX * 8; // south-centre of the footprint
        int ez = nwZ() + sizeZ * 16 - (main ? 6 : 1) - 1; // just inside the south wall face
        if (!owns(chunk, ex, ez) || !isWall(ex, ez))
            return;
        int lx = ex - oX, lz = ez - oZ;
        chunk.setBlocks(lx, street + 1, street + 2, lz, Material.AIR);
        chunk.setDoor(lx, street + 1, lz, Material.OAK_DOOR, BlockFace.SOUTH);
        chunk.setBlock(lx, street + 3, lz, CROSS); // a green cross block over the door
        if (lz + 1 < 16)
            chunk.setWallSign(lx, street + 3, lz + 1, Material.OAK_WALL_SIGN, BlockFace.SOUTH, signLines());
    }

    /** A big flat green cross on the roof centre (visible from the air). */
    private void roofCross(RealBlocks chunk, int roofY, int oX, int oZ) {
        int cx = nwX() + sizeX * 8, cz = nwZ() + sizeZ * 8;
        int arm = main ? 3 : 1;
        for (int d = -arm; d <= arm; d++) {
            plot(chunk, cx + d, roofY + 1, cz, oX, oZ);
            plot(chunk, cx, roofY + 1, cz + d, oX, oZ);
        }
    }

    /** Green crosses on the south exterior wall face, at first-floor height. */
    private void wallCrosses(RealBlocks chunk, int street, int oX, int oZ) {
        int y = street + DataContext.FloorHeight + 2;
        for (int wx = nwX() + 3; wx < nwX() + sizeX * 16 - 3; wx += 7) {
            int wz = nwZ() + sizeZ * 16 - (main ? 6 : 1) - 1;
            if (!isWall(wx, wz))
                continue;
            plot(chunk, wx, y, wz, oX, oZ);
            plot(chunk, wx - 1, y, wz, oX, oZ);
            plot(chunk, wx + 1, y, wz, oX, oZ);
            plot(chunk, wx, y - 1, wz, oX, oZ);
            plot(chunk, wx, y + 1, wz, oX, oZ);
        }
    }

    /** Place a CROSS block at a world position if it falls in this chunk. */
    private void plot(RealBlocks chunk, int wx, int y, int wz, int oX, int oZ) {
        if (wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16)
            chunk.setBlock(wx - oX, y, wz - oZ, CROSS);
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
