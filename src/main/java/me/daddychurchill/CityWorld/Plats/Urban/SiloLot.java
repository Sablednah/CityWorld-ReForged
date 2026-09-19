package me.daddychurchill.CityWorld.Plats.Urban;

import net.minecraft.world.level.block.state.properties.SlabType;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.BuildingLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A metal storage silo on a red steel frame, with a caged spiral-stair tower beside it and a catwalk
 * onto the roof (owner, 2026-09-19, from a photo of the real thing — the silo schematics it replaces
 * were "swamping industrial"). One chunk: the tank is a radius-5.5 cylinder centred at (6.5, 7.5) so
 * the 5x5 stair tower fits in the north-east corner; a hopper cone hangs inside the frame under the
 * tank. Placed by {@link me.daddychurchill.CityWorld.Context.IndustrialContext} in place of a
 * building, and the backfill's 2x2 flood-fill gives batteries of them — connected silos share their
 * height and paint.
 */
public class SiloLot extends BuildingLot {

	private static final double CENTRE_X = 6.5, CENTRE_Z = 7.5, TANK_R = 5.5;
	private static final int FRAME_H = 7; // legs B..B+6, ring beam at B+6, tank floor at B+7

	// the spiral's eight ring cells (x, z) clockwise from the west-middle, and where each step faces
	private static final int[][] RING = { { 12, 2 }, { 12, 1 }, { 13, 1 }, { 14, 1 }, { 14, 2 }, { 14, 3 },
			{ 13, 3 }, { 12, 3 } };
	// each step faces the way you arrive on it — a corner step keeps the previous run's direction, its back
	// to the cage wall, so the turn happens on the step after (owner's fix, 2026-09-19)
	private static final BlockFace[] STEP = { BlockFace.NORTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.EAST,
			BlockFace.SOUTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.WEST };

	private int turns; // stair revolutions: the tank top sits 8 blocks higher per turn
	private Material tankMat;
	private Material frameMat;
	private Material fillMat;
	private int fillHeight;
	private Material floorMat;

	public SiloLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

		height = 1;
		depth = 0;
		// not trulyIsolated: silos are meant to stand in batteries, and CivilizedContext.validateMap
		// swaps an isolated structure with an isolated neighbour for a fresh building

		turns = 2 + chunkOdds.getRandomInt(2);
		switch (chunkOdds.getRandomInt(4)) {
		case 0:
			tankMat = Material.IRON_BLOCK;
			break;
		case 1:
			tankMat = Material.WHITE_CONCRETE;
			break;
		case 2:
			tankMat = Material.POLISHED_ANDESITE;
			break;
		default:
			tankMat = Material.LIGHT_GRAY_CONCRETE;
			break;
		}
		frameMat = chunkOdds.playOdds(0.75) ? Material.RED_CONCRETE : Material.RED_TERRACOTTA;
		switch (chunkOdds.getRandomInt(4)) {
		case 0:
			fillMat = Material.HAY_BLOCK;
			break;
		case 1:
			fillMat = Material.SAND;
			break;
		case 2:
			fillMat = Material.GRAVEL;
			break;
		default:
			fillMat = Material.COARSE_DIRT;
			break;
		}
		fillHeight = chunkOdds.getRandomInt(tankHeight() - 1);
		floorMat = platmap.generator.materialProvider.deOre(platmap.generator.materialProvider
				.itemsSelectMaterial_FactoryInsides.getRandomMaterial(chunkOdds, Material.SMOOTH_STONE), chunkOdds);
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new SiloLot(platmap, chunkX, chunkZ);
	}

	@Override
	public boolean makeConnected(PlatLot relative) {
		boolean result = super.makeConnected(relative);
		if (result && relative instanceof SiloLot) {
			SiloLot other = (SiloLot) relative;
			turns = other.turns;
			tankMat = other.tankMat;
			frameMat = other.frameMat;
			fillMat = other.fillMat;
			floorMat = other.floorMat;
		}
		return result;
	}

	private int tankHeight() {
		return turns * 8; // wall B+8 .. B+7+8*turns, so the last step (index 7) lands level with the top
	}

	private static double dist(int x, int z) {
		return Math.hypot(x - CENTRE_X, z - CENTRE_Z);
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		chunk.setLayer(getBottomY(generator), 2, floorMat);
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		int base = getBottomY(generator) + 2; // first walkable level
		int tankBottom = base + FRAME_H; // the floor disc
		int tankTop = tankBottom + tankHeight(); // last wall layer
		generator.reportLocation("silo", "Silo", chunk);

		// the hopper cone under the tank, narrowing to a chute two blocks off the ground
		for (int k = 0; k < 4; k++)
			ring(chunk, tankBottom - 1 - k, TANK_R - 1 - k, k == 3, tankMat);
		for (int x = 6; x <= 7; x++)
			for (int z = 7; z <= 8; z++)
				chunk.setBlock(x, base + 2, z, Material.IRON_TRAPDOOR);

		// the tank: floor disc, wall, and whatever it holds
		ring(chunk, tankBottom, TANK_R, true, tankMat);
		for (int y = tankBottom + 1; y <= tankTop; y++)
			ring(chunk, y, TANK_R, false, tankMat);
		for (int y = tankBottom + 1; y <= tankBottom + fillHeight; y++)
			ring(chunk, y, TANK_R - 1, true, fillMat);

		// the conical roof: stepped rings, each edged with a slab so the slope reads as a slope
		for (int k = 0; k < 5; k++) {
			double r = TANK_R - k;
			int y = tankTop + 1 + k;
			for (int x = 0; x < 16; x++)
				for (int z = 0; z < 16; z++) {
					double d = dist(x, z);
					if (d <= r - 1)
						chunk.setBlock(x, y, z, tankMat);
					else if (d <= r)
						chunk.setBlocks(x, y, y + 1, z, Material.POLISHED_ANDESITE_SLAB, SlabType.BOTTOM);
				}
		}
		chunk.setBlock(10, tankTop + 2, 6, Material.IRON_TRAPDOOR); // the roof hatch, by the catwalk

		// the stair tower: a 5x5 cage of bars (x 11..15, z 0..4) round a 3x3 spiral on a red mast
		for (int x = 11; x <= 15; x++)
			for (int z = 0; z <= 4; z++) {
				if (x > 11 && x < 15 && z > 0 && z < 4)
					continue;
				for (int y = base; y <= tankTop + 1; y++) {
					if ((y - base) % 8 == 0 || y == tankTop + 1) // a ring girder every turn, and the top rail
						chunk.setBlocks(x, y, y + 1, z, Material.POLISHED_ANDESITE_SLAB, SlabType.BOTTOM);
					else
						chunk.setBlock(x, y, z, Material.IRON_BARS);
				}
			}
		chunk.setBlocks(11, base, base + 3, 2, Material.AIR); // the way in, west side
		chunk.setBlocks(13, base, tankTop + 2, 2, frameMat); // the mast
		chunk.setBlock(13, tankTop + 2, 2, Material.LANTERN);
		for (int y = base; y <= tankTop; y++) {
			int i = (y - base) % 8;
			if (y == base) // the bottom step sits in the doorway, and the first ring cell is a landing block
				chunk.setStair(11, y, 2, Material.POLISHED_ANDESITE_STAIRS, BlockFace.EAST);
			chunk.setStair(RING[i][0], y, RING[i][1], Material.POLISHED_ANDESITE_STAIRS, STEP[i]);
		}
		chunk.setBlock(12, base, 2, Material.POLISHED_ANDESITE);

		// the red frame: four legs outside the tank's outline, ring beams at mid-height and the top
		// (drawn after the cage — its north-east leg and beam ends stand inside the tower's shell)
		for (int[] leg : new int[][] { { 2, 3 }, { 11, 3 }, { 2, 12 }, { 11, 12 } })
			chunk.setBlocks(leg[0], base, base + FRAME_H, leg[1], frameMat);
		for (int y : new int[] { base + 3, base + FRAME_H - 1 }) {
			chunk.setBlocks(2, 12, y, y + 1, 3, 4, frameMat);
			chunk.setBlocks(2, 12, y, y + 1, 12, 13, frameMat);
			chunk.setBlocks(2, 3, y, y + 1, 3, 13, frameMat);
			chunk.setBlocks(11, 12, y, y + 1, 3, 13, frameMat);
		}

		// the catwalk from the top step onto the roof's edge ring
		chunk.setBlocks(12, tankTop + 1, tankTop + 2, 4, Material.POLISHED_ANDESITE_SLAB, SlabType.BOTTOM);
		chunk.setBlocks(12, tankTop + 1, tankTop + 2, 5, Material.POLISHED_ANDESITE_SLAB, SlabType.BOTTOM);
		chunk.setBlock(13, tankTop + 2, 5, Material.IRON_BARS); // a handrail post either side
		chunk.setBlock(11, tankTop + 2, 4, Material.IRON_BARS);

		// bars only join up when told to — the cage is drawn cell by cell
		chunk.reconnect(11, 16, base, tankTop + 3, 0, 6);

		if (buildingsDecay(generator))
			destroyLot(generator, base, base + 4);
		generator.spawnProvider.spawnBeing(generator, chunk, chunkOdds, 7, base, 14);
	}

	/** One horizontal ring (or filled disc) of the tank's circle at radius {@code r}. */
	private void ring(RealBlocks chunk, int y, double r, boolean fill, Material material) {
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				double d = dist(x, z);
				if (d <= r && (fill || d > r - 1))
					chunk.setBlock(x, y, z, material);
			}
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return generator.streetLevel;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return generator.streetLevel + 2 + FRAME_H + tankHeight() + 7;
	}
}
