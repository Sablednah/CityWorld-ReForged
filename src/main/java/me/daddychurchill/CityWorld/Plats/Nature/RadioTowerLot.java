package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Plugins.LootProvider;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.FurnitureTags;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.Point;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public class RadioTowerLot extends ConstructLot {

	public RadioTowerLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		trulyIsolated = true;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new RadioTowerLot(platmap, chunkX, chunkZ);
	}

	private final static int platformWidth = 8;
	private final static int heightRange = 15;
	private final static int heightShortest = 10;
	private final static int heightTallest = heightShortest + heightRange;
	private final boolean antennaBuilt = false;
	private boolean tallestBuilt = false;

	private final static Material platformMaterial = Material.SMOOTH_STONE;
	private final static Material supportMaterial = Material.COBBLESTONE;
	private final static Material wallMaterial = Material.SMOOTH_STONE;
	//	private final static Material roofMaterial = Material.STONE_SLAB;
	private final static Material baseMaterial = Material.CLAY;
	private final static Material antennaMaterial = Material.IRON_BARS;

	/** The operator's hut: walls one in from the platform edge (a ledge to walk round, where the ladder comes
	 *  up), a 4x4 room three high inside, roof on top, and the antennas standing on the roof. */
	private final static int hutInset = 1;
	private final static int hutHeight = 3;

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return blockYs.getMaxHeight() + 2;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return getBottomY(generator) + hutHeight + 1 + heightTallest + 2;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {

		// compute offset to start of chunk
		int platformOffset = platformWidth / 2;
		Point highPoint = blockYs.getHighPoint();
		int originX = Math.min(platformOffset, Math.max(chunk.width - platformOffset - 1, highPoint.getX()));
		int originZ = Math.min(platformOffset, Math.max(chunk.width - platformOffset - 1, highPoint.getZ()));
		int platformY = getBottomY(generator);

		// base
		for (int x = originX + 1; x < originX + platformWidth - 1; x++) {
			for (int z = originZ + 1; z < originZ + platformWidth - 1; z++) {
				for (int y = platformY - 2; y > blockYs.getMinHeight(); y--) {
					if (!chunk.setEmptyBlock(x, y, z, supportMaterial)) {
						chunk.setBlocks(x, y - 3, y + 1, z, supportMaterial);
						break;
					}
				}
			}
		}

		// top it off
		chunk.setBlocks(originX, originX + platformWidth, platformY - 1, originZ, originZ + platformWidth,
				platformMaterial);

//		// base
//		if (minHeight > generator.evergreenLevel)
//			generator.oreProvider.sprinkleSnow(generator, chunk, chunkOdds, originX, originX + platformWidth, platformY, originZ, originZ + platformWidth);
//
		// building: the operator's hut, hollow, with a window in three of its walls
		int hx1 = originX + hutInset, hx2 = originX + platformWidth - hutInset; // walls at hx1 and hx2 - 1
		int hz1 = originZ + hutInset, hz2 = originZ + platformWidth - hutInset;
		chunk.setBlocks(hx1, hx2, platformY, platformY + hutHeight, hz1, hz2, wallMaterial);
		chunk.airoutBlocks(generator, hx1 + 1, hx2 - 1, platformY, platformY + hutHeight, hz1 + 1, hz2 - 1, true);
		chunk.setBlocks(hx1, hx2, platformY + hutHeight, platformY + hutHeight + 1, hz1, hz2, platformMaterial);
		int mid = originZ + platformWidth / 2 - 1, midX = originX + platformWidth / 2 - 1;
		chunk.setBlocks(midX, midX + 2, platformY + 1, platformY + 2, hz1, hz1 + 1, Material.GLASS); // north
		chunk.setBlocks(midX, midX + 2, platformY + 1, platformY + 2, hz2 - 1, hz2, Material.GLASS); // south
		chunk.setBlocks(hx2 - 1, hx2, platformY + 1, platformY + 2, mid, mid + 2, Material.GLASS); // east
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		generator.reportLocation("radiotower", "Radio Tower", chunk);

		// compute offset to start of chunk
		int platformOffset = platformWidth / 2;
		Point highPoint = blockYs.getHighPoint();
		int originX = Math.min(platformOffset, Math.max(chunk.width - platformOffset - 1, highPoint.getX()));
		int originZ = Math.min(platformOffset, Math.max(chunk.width - platformOffset - 1, highPoint.getZ()));
		int platformY = getBottomY(generator);

		// place snow
		generateSurface(generator, chunk, false);

		int hx1 = originX + hutInset, hx2 = originX + platformWidth - hutInset;
		int hz1 = originZ + hutInset, hz2 = originZ + platformWidth - hutInset;
		int roofY = platformY + hutHeight + 1;

		// blow it all up? First, so the way in and the room's chest are always there afterwards: the chest is the
		// tower's one guaranteed find (a pack's quest can hang on it)
		if (generator.getSettings().includeDecayedBuildings) {
			int x1 = chunk.getBlockX(originX);
			int z1 = chunk.getBlockZ(originZ);
			generator.destroyWithin(x1, x1 + platformWidth, blockYs.getAverageHeight(), roofY, z1,
					z1 + platformWidth);
		}

		// the way in: the ladder up the west side of the base, and a door in the hut's west wall beside it
		int ladderBase = platformY - 2;
		while (chunk.isEmpty(originX, ladderBase, originZ + 4)) {
			ladderBase--;
		}
		chunk.setLadder(originX, ladderBase, platformY, originZ + 4, BlockFace.WEST); // fixed
		chunk.airoutBlock(generator, originX, platformY, originZ + 4);
		chunk.setDoor(hx1, platformY, originZ + 3, Material.BIRCH_DOOR, BlockFace.WEST_NORTH_WEST); // the ladder holds the wall beside it

		// inside: the operator's chair beside a table against the north wall, a chest of spares in the far corner,
		// and a lantern overhead (the door's cell, just inside, stays clear)
		furnish(generator, chunk, hx1 + 1, hx2 - 2, platformY, hz1 + 1, hz2 - 2);

		// place antennas, on the roof
		generateAntenna(generator, chunk, context, hx1, roofY, hz1, false);
		generateAntenna(generator, chunk, context, hx1, roofY, hz2 - 2, false);
		generateAntenna(generator, chunk, context, hx2 - 2, roofY, hz1, false);
		generateAntenna(generator, chunk, context, hx2 - 2, roofY, hz2 - 2, true);
	}

	/** The room's furniture, inside x {@code x1..x2}, z {@code z1..z2} (inclusive) on floor {@code y}. The chair
	 *  and table come from the furniture pools ({@code #cityworld:furniture/chair}, {@code .../table}), so a
	 *  furniture mod's own can stand there; without one, a stair and a fence-post table. */
	private void furnish(CityWorldGenerator generator, RealBlocks chunk, int x1, int x2, int y, int z1, int z2) {
		int tableX = x1 + 2, chairX = x1 + 1; // chair to the west of the table, facing it
		Material table = FurnitureTags.pick(FurnitureTags.TABLE, chunkOdds);
		if (table == null || !chunk.setFurniture(tableX, y, z1, table, FurnitureTags.facingFor(table, BlockFace.SOUTH))) {
			chunk.setBlock(tableX, y, z1, Material.OAK_FENCE);
			chunk.setBlock(tableX, y + 1, z1, Material.OAK_PRESSURE_PLATE);
		}
		if (chunk.isEmpty(chairX, y, z1)) {
			Material chair = FurnitureTags.pick(FurnitureTags.CHAIR, chunkOdds);
			if (chair != null)
				chunk.setBlock(chairX, y, z1, chair, FurnitureTags.facingFor(chair, BlockFace.EAST));
			else
				chunk.setBlock(chairX, y, z1, Material.OAK_STAIRS, BlockFace.WEST);
		}
		// the only container on the radio tower's table: a pack adding guaranteed loot to it (ZARP's quest) gets one
		// haul per tower, so the lot keeps the ordinary default for anything else that holds things
		chunk.setChest(generator, x2, y, z2, BlockFace.WEST, chunkOdds, generator.lootProvider,
				LootProvider.LootLocation.RADIO_TOWER);
		chunk.setHangingLantern(x1 + 1, y + hutHeight - 1, z1 + 2, Material.LANTERN);
	}

	private void generateAntenna(CityWorldGenerator generator, RealBlocks chunk, DataContext context, int x, int y,
			int z, boolean lastChance) {

		// build an antenna?
		if ((lastChance && !antennaBuilt) || chunkOdds.flipCoin()) {
			if (!chunk.isEmpty(x, y - 1, z)) {
//				chunk.setBlocks(x, y, y + 2, z, baseMaterial);

				// how tall?
				int antennaHeight = heightShortest;
				if (!tallestBuilt && (lastChance || chunkOdds.flipCoin())) {
					antennaHeight = heightTallest;
					tallestBuilt = true;
				} else
					antennaHeight += chunkOdds.getRandomInt(heightRange);

				// stuff going out?
				boolean middleStuff = chunkOdds.flipCoin();
				boolean topStuff = chunkOdds.flipCoin();

				// actually build the antenna
				for (int y1 = y; y1 < y + antennaHeight; y1++) {

					// horizontal stuff?
					if ((middleStuff && y1 == y + antennaHeight - 5) || (topStuff && y1 == y + antennaHeight - 1)) {

						if (chunkOdds.playOdds(Odds.oddsExtremelyLikely))
							chunk.setBlocks(x - 2, x, y1, z, z + 1, antennaMaterial, BlockFace.EAST, BlockFace.WEST);
						if (chunkOdds.playOdds(Odds.oddsExtremelyLikely))
							chunk.setBlocks(x + 2, x + 4, y1, z + 1, z + 2, antennaMaterial, BlockFace.EAST,
									BlockFace.WEST);
						if (chunkOdds.playOdds(Odds.oddsExtremelyLikely))
							chunk.setBlocks(x + 1, x + 2, y1, z - 2, z, antennaMaterial, BlockFace.NORTH,
									BlockFace.SOUTH);
						if (chunkOdds.playOdds(Odds.oddsExtremelyLikely))
							chunk.setBlocks(x, x + 1, y1, z + 2, z + 4, antennaMaterial, BlockFace.NORTH,
									BlockFace.SOUTH);

						chunk.setBlocks(x, x + 2, y1, z, z + 2, baseMaterial);

						// vertical stuff?
					} else {
						if (chunk.isEmpty(x, y1, z))
							chunk.setBlock(x, y1, z, Material.IRON_BARS, BlockFace.EAST, BlockFace.SOUTH);
						if (chunk.isEmpty(x + 1, y1, z))
							chunk.setBlock(x + 1, y1, z, Material.IRON_BARS, BlockFace.WEST, BlockFace.SOUTH);
						if (chunk.isEmpty(x, y1, z + 1))
							chunk.setBlock(x, y1, z + 1, Material.IRON_BARS, BlockFace.EAST, BlockFace.NORTH);
						if (chunk.isEmpty(x + 1, y1, z + 1))
							chunk.setBlock(x + 1, y1, z + 1, Material.IRON_BARS, BlockFace.WEST, BlockFace.NORTH);
					}
				}

				// top of the tallest one?
				if (antennaHeight == heightTallest) {
					chunk.setBlocks(x, x + 2, y + antennaHeight, z, z + 2, Material.END_ROD); // a lit beacon crown
					// ...spiked with a lightning rod above the crown. The tallest structure for miles, so it
					// genuinely takes the strikes — stand back on a storm and watch it light up.
					chunk.setBlock(x, y + antennaHeight + 1, z, Material.LIGHTNING_ROD);
				} else {
					// cap every other antenna with a lightning rod at its apex too
					chunk.setBlock(x, y + antennaHeight, z, Material.LIGHTNING_ROD);
				}
			}
		}
	}
}
