package me.daddychurchill.CityWorld.Plats.Urban;

import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * One chunk-slice of a gasometer — a telescoping gas holder in a lattice guide frame, the kind whose
 * bell "raises and lowers depending how full of gas they are" (owner, 2026-09-19). A rare large lot:
 * the placer in {@link me.daddychurchill.CityWorld.Context.IndustrialContext} claims a 2x2 or 3x3
 * run of chunks and hands every chunk its offset in the footprint plus the shared roll (frame
 * height, fill, paint), the {@link BigBiodomeLot} pattern; each chunk draws only its own columns of
 * the shared circle. The frame is a ring of columns with a slab girder and a rail of bars every six
 * levels, standing in a water trough; the bell is three nested lifts — the crown always shows, the
 * wider lifts rise out of the water only as the fill climbs.
 */
public class GasometerLot extends IsolatedLot {

	private static final int GIRDER_EVERY = 6;

	private final int size, offX, offZ;
	private final int frameHeight;
	private final int fill; // 0 .. frameHeight-1: how high the bell stands
	private final int frameStyle;
	private final long paveSeed; // one paving roll per structure, not per chunk (owner: "each chunk has different floor")

	public GasometerLot(PlatMap platmap, int chunkX, int chunkZ, int size, int offX, int offZ, int frameHeight,
			int fill, int frameStyle, long paveSeed) {
		super(platmap, chunkX, chunkZ);
		style = LotStyle.STRUCTURE;
		this.size = size;
		this.offX = offX;
		this.offZ = offZ;
		this.frameHeight = frameHeight;
		this.fill = fill;
		this.frameStyle = frameStyle;
		this.paveSeed = paveSeed;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new GasometerLot(platmap, chunkX, chunkZ, size, offX, offZ, frameHeight, fill, frameStyle, paveSeed);
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
		return generator.streetLevel + 2 + frameHeight + 4;
	}

	private double centreX() {
		return (getChunkX() - offX) * 16 + size * 8.0;
	}

	private double centreZ() {
		return (getChunkZ() - offZ) * 16 + size * 8.0;
	}

	private double frameRadius() {
		return size * 8 - 1.5;
	}

	private Material frameMaterial() {
		switch (frameStyle) {
		case 0:
			return Material.GRAY_CONCRETE;
		case 1:
			return Material.RED_TERRACOTTA;
		case 2:
			return Material.GREEN_TERRACOTTA;
		default:
			return Material.IRON_BLOCK;
		}
	}

	private Material bellMaterial() {
		switch (frameStyle) {
		case 0:
			return Material.LIGHT_GRAY_CONCRETE;
		case 1:
			return Material.LIGHT_GRAY_TERRACOTTA;
		case 2:
			return Material.CYAN_TERRACOTTA;
		default:
			return Material.POLISHED_ANDESITE;
		}
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		int groundY = generator.streetLevel;
		chunk.airoutLayer(generator, groundY + 2, frameHeight + 6, 0, true);
		chunk.setLayer(groundY - 3, 3, Material.DIRT);
		Odds paveOdds = new Odds(paveSeed);
		Material floorMat = generator.materialProvider.deOre(generator.materialProvider
				.itemsSelectMaterial_FactoryInsides.getRandomMaterial(paveOdds, Material.SMOOTH_STONE), paveOdds);
		chunk.setLayer(groundY, 2, floorMat);
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		int base = generator.streetLevel + 2; // first walkable level; the ground's top block is base-1
		if (offX == 0 && offZ == 0)
			generator.reportLocation("gasometer", "Gasometer " + size + "x" + size, chunk, size, size);

		int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
		double cX = centreX(), cZ = centreZ();
		double rFrame = frameRadius();
		double rBell = rFrame - 2;
		Material frameMat = frameMaterial();
		Material bellMat = bellMaterial();

		// the trough: water level with the ground inside a low wall, the frame's ring
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
				if (d <= rFrame - 1)
					chunk.setBlock(lx, base - 1, lz, Material.WATER);
				else if (d <= rFrame)
					chunk.setBlocks(lx, base - 1, base + 1, lz, frameMat);
			}

		// the guide frame: columns round the ring, a girder and a rail every few levels
		int columns = size * 8;
		for (int i = 0; i < columns; i++) {
			double a = i * 2 * Math.PI / columns;
			int wx = (int) Math.round(cX + rFrame * Math.cos(a));
			int wz = (int) Math.round(cZ + rFrame * Math.sin(a));
			if (wx >= oX && wx < oX + 16 && wz >= oZ && wz < oZ + 16)
				chunk.setBlocks(wx - oX, base - 1, base + frameHeight + 1, wz - oZ, frameMat);
		}
		for (int y = base + GIRDER_EVERY; y <= base + frameHeight; y += GIRDER_EVERY) {
			boolean top = y + GIRDER_EVERY > base + frameHeight;
			for (int lx = 0; lx < 16; lx++)
				for (int lz = 0; lz < 16; lz++) {
					double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
					if (d > rFrame - 1 && d <= rFrame) {
						if (chunk.isEmpty(lx, y, lz))
							chunk.setBlocks(lx, y, y + 1, lz, Material.POLISHED_ANDESITE_SLAB, top ? SlabType.BOTTOM : SlabType.TOP);
						if (!top && chunk.isEmpty(lx, y + 1, lz)) // the top ring is a walkway
							chunk.setBlock(lx, y + 1, lz, Material.IRON_BARS);
					}
				}
		}

		// a ladder up the west column to the top girder
		int wx = (int) Math.round(cX - rFrame), wz = (int) Math.round(cZ);
		if (wx - 1 >= oX && wx - 1 < oX + 16 && wz >= oZ && wz < oZ + 16) {
			int topGirder = base + (frameHeight / GIRDER_EVERY) * GIRDER_EVERY;
			chunk.setLadder(wx - 1 - oX, base, topGirder + 2, wz - oZ, BlockFace.WEST);
			// a step off the ladder either side onto the top ring
			for (int dz = -1; dz <= 1; dz += 2)
				if (wz + dz >= oZ && wz + dz < oZ + 16)
					chunk.setBlocks(wx - 1 - oX, topGirder, topGirder + 1, wz + dz - oZ, Material.POLISHED_ANDESITE_SLAB,
							SlabType.BOTTOM);
		}

		// the bell: three nested lifts, the narrowest (the crown) always showing, each wider one rising
		// out of the trough only as the fill climbs past the one above it
		int liftHeight = Math.max(4, frameHeight / 3);
		int crownBottom = base + Math.max(0, fill - liftHeight);
		int crownTop = crownBottom + liftHeight;
		int middleShowing = Math.max(0, Math.min(liftHeight, fill - liftHeight));
		int outerShowing = Math.max(0, Math.min(liftHeight, fill - 2 * liftHeight));
		lift(chunk, oX, oZ, cX, cZ, rBell, base - 1, base + outerShowing, bellMat, frameMat);
		lift(chunk, oX, oZ, cX, cZ, rBell - 1, base - 1 + outerShowing, base + outerShowing + middleShowing, bellMat,
				frameMat);
		lift(chunk, oX, oZ, cX, cZ, rBell - 2, base - 1, crownTop, bellMat, frameMat);
		// the crown's shallow domed top
		for (int k = 0; k <= 3; k++) {
			double r = rBell - 2 - 2 * k;
			if (r < 1)
				break;
			for (int lx = 0; lx < 16; lx++)
				for (int lz = 0; lz < 16; lz++) {
					double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
					if (d <= r)
						chunk.setBlock(lx, crownTop + k, lz, bellMat);
				}
		}

		chunk.reconnect(0, 16, base - 1, base + frameHeight + 2, 0, 16);

		if (buildingsDecay(generator))
			destroyLot(generator, base, base + 4);
	}

	/** One lift of the bell: a hollow cylinder of radius {@code r} from y1 up to y2, with a rib of the
	 *  frame's colour every few levels so the sections read as riveted plate. */
	private void lift(RealBlocks chunk, int oX, int oZ, double cX, double cZ, double r, int y1, int y2,
			Material plate, Material rib) {
		if (y2 <= y1)
			return;
		for (int lx = 0; lx < 16; lx++)
			for (int lz = 0; lz < 16; lz++) {
				double d = Math.hypot(oX + lx - cX, oZ + lz - cZ);
				if (d > r - 1 && d <= r)
					for (int y = y1; y < y2; y++)
						chunk.setBlock(lx, y, lz, (y - y1) % 4 == 3 ? rib : plate);
			}
	}
}
