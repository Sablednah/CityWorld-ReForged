package me.daddychurchill.CityWorld.Rooms;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;

public abstract class PlatRoom {

	PlatRoom() {
		// TODO Auto-generated constructor stub
	}

	public abstract void drawFixture(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int floor, int x, int y,
			int z, int width, int height, int depth, BlockFace sideWithWall, Material materialWall,
			Material materialGlass);

	private final Material[] TableTops = { Material.ACACIA_PRESSURE_PLATE, Material.BIRCH_PRESSURE_PLATE,
			Material.DARK_OAK_PRESSURE_PLATE, Material.JUNGLE_PRESSURE_PLATE, Material.OAK_PRESSURE_PLATE,
			Material.SPRUCE_PRESSURE_PLATE,

			Material.HEAVY_WEIGHTED_PRESSURE_PLATE, Material.LIGHT_WEIGHTED_PRESSURE_PLATE,
			Material.STONE_PRESSURE_PLATE,

			Material.BLACK_CARPET, Material.BLUE_CARPET, Material.BROWN_CARPET, Material.CYAN_CARPET,
			Material.GRAY_CARPET, Material.GREEN_CARPET, Material.LIGHT_BLUE_CARPET, Material.LIGHT_GRAY_CARPET,
			Material.LIME_CARPET, Material.MAGENTA_CARPET, Material.ORANGE_CARPET, Material.PINK_CARPET,
			Material.PURPLE_CARPET, Material.RED_CARPET, Material.WHITE_CARPET, Material.YELLOW_CARPET };

	private final Material[] TableLegs = { Material.ACACIA_FENCE, Material.BIRCH_FENCE, Material.DARK_OAK_FENCE,
			Material.JUNGLE_FENCE, Material.OAK_FENCE, Material.SPRUCE_FENCE,

			Material.NETHER_BRICK_FENCE, Material.COBBLESTONE_WALL, Material.MOSSY_COBBLESTONE_WALL,
			Material.IRON_BARS };

	Material getTableTop(Odds odds) {
		return odds.getRandomMaterial(TableTops);
	}

	Material getTableLeg(Odds odds) {
		return odds.getRandomMaterial(TableLegs);
	}

	// ---- pooled furniture (the interiors round) ------------------------------------------------
	// Rooms predate the furniture pools; these helpers let every room draw real desks, chairs and
	// tables when a furniture mod supplies them, and fall back to the classic fence-and-plate /
	// stair furniture when none does. The STAIR fallback keeps the rooms' original convention:
	// the face passed is the BACKREST side, and pooled seats look the opposite way.

	/** A desk region fronting {@code front} — pooled desks (with an occasional computer on top on
	 *  MODERN), or the classic fence-and-plate table. */
	void drawDesk(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y, int z1, int z2,
			BlockFace front) {
		Material desk = me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.DESK, odds);
		if (desk == null) {
			chunk.setTable(x1, x2, y, z1, z2, getTableLeg(odds), getTableTop(odds));
			return;
		}
		Material computer = me.daddychurchill.CityWorld.Support.Furniture.modern(generator)
				? me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
						me.daddychurchill.CityWorld.Support.FurnitureTags.COMPUTER, odds)
				: null;
		boolean screenPlaced = false;
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++) {
				chunk.setBlock(x, y, z, desk,
						me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(desk, front));
				chunk.reconnect(x, y, z);
				if (!screenPlaced && computer != null && odds.playOdds(0.5) && chunk.isEmpty(x, y + 1, z)) {
					chunk.setBlock(x, y + 1, z, computer,
							me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(computer, front));
					screenPlaced = true;
				}
			}
	}

	/** A table region — pooled auto-connecting tables, or the classic fence-and-plate one. */
	void drawTable(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x1, int x2, int y, int z1,
			int z2) {
		Material table = me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.TABLE, odds);
		if (table == null) {
			chunk.setTable(x1, x2, y, z1, z2, getTableLeg(odds), getTableTop(odds));
			return;
		}
		for (int x = x1; x < x2; x++)
			for (int z = z1; z < z2; z++) {
				chunk.setBlock(x, y, z, table);
				chunk.reconnect(x, y, z);
			}
	}

	/** Single-cell forms of the two above, for the rooms that place one table block. */
	void drawDesk(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace front) {
		drawDesk(generator, chunk, odds, x, x + 1, y, z, z + 1, front);
	}

	void drawTable(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z) {
		drawTable(generator, chunk, odds, x, x + 1, y, z, z + 1);
	}

	/** True when a seat here would stare into a wall — 2-high solid in the look direction. A desk
	 *  or table in front is 1-high and fine; the apartment couch nose-to-stairwell was not. */
	private static boolean facesWall(RealBlocks chunk, int x, int y, int z, BlockFace look) {
		int lx = x + look.getModX(), lz = z + look.getModZ();
		return !chunk.isEmpty(lx, y, lz) && !chunk.isEmpty(lx, y + 1, lz);
	}

	/** A single seat; {@code backrest} is the stair-fallback's facing, pooled chairs look opposite. */
	void drawSeat(RealBlocks chunk, Odds odds, int x, int y, int z, BlockFace backrest) {
		if (!chunk.isEmpty(x, y, z) || facesWall(chunk, x, y, z, backrest.getOppositeFace()))
			return;
		Material chair = me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.CHAIR, odds);
		if (chair != null)
			chunk.setBlock(x, y, z, chair, me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(chair,
					backrest.getOppositeFace()));
		else
			chunk.setBlock(x, y, z, Material.BIRCH_STAIRS, backrest);
	}

	/** One sofa for the whole room's run — a run of mismatched colours reads as a jumble sale.
	 *  Null without a furniture mod; {@link #drawCouchSeat} then falls back to stairs. */
	Material pooledSofa(Odds odds) {
		return me.daddychurchill.CityWorld.Support.FurnitureTags.pick(
				me.daddychurchill.CityWorld.Support.FurnitureTags.SOFA, odds);
	}

	/** One cell of a couch run; same backrest convention as {@link #drawSeat}. PlatRooms are shared
	 *  instances, so the room picks its sofa once and passes it here rather than holding state. */
	void drawCouchSeat(RealBlocks chunk, Material sofa, int x, int y, int z, BlockFace backrest) {
		if (!chunk.isEmpty(x, y, z) || facesWall(chunk, x, y, z, backrest.getOppositeFace()))
			return;
		if (sofa != null)
			chunk.setBlock(x, y, z, sofa, me.daddychurchill.CityWorld.Support.FurnitureTags.facingFor(sofa,
					backrest.getOppositeFace()));
		else
			chunk.setBlock(x, y, z, Material.BIRCH_STAIRS, backrest);
	}
}
