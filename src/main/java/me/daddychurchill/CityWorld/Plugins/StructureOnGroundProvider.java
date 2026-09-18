package me.daddychurchill.CityWorld.Plugins;

import net.minecraft.world.item.DyeColor;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.LootProvider.LootLocation;
import me.daddychurchill.CityWorld.Support.Colors;
import me.daddychurchill.CityWorld.Support.Colors.ColorSet;
import me.daddychurchill.CityWorld.Support.Mapper;
import me.daddychurchill.CityWorld.Support.MaterialTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.Trees;

public class StructureOnGroundProvider extends Provider {

//	private static RoomProvider contentsKitchen = new HouseKitchens();
//	private static RoomProvider contentsBedroom = new HouseBedrooms();
//	private static RoomProvider contentsDiningRoom = new HouseDiningRooms();
//	private static RoomProvider contentsLivingRoom = new HouseLivingRooms();

	private StructureOnGroundProvider() {
		super();

	}

	public static StructureOnGroundProvider loadProvider(CityWorldGenerator generator) {
		// for now
		return new StructureOnGroundProvider();
	}

	private final static double oddsOfFurnace = Odds.oddsSomewhatUnlikely;
	private final static double oddsOfCraftingTable = Odds.oddsSomewhatUnlikely;

	public void generateShed(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int x,
			int y, int z, int radius, LootLocation location) {
		generateShed(generator, chunk, context, odds, x, y, z, radius, location, location);
	}

	public void generateShed(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int x,
			int y, int z, int radius, LootLocation location, LootLocation other) {
		int x1 = x - radius;
		int x2 = x + radius + 1;
		int z1 = z - radius;
		int z2 = z + radius + 1;
		int y1 = y;
		int y2 = y + DataContext.FloorHeight - 1;
		int xR = x2 - x1 - 2;
		int zR = z2 - z1 - 2;

		Material wallMat = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_ShedWalls.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material roofMat = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_ShedRoofs.getRandomMaterial(odds, Material.COBBLESTONE), odds);

		chunk.setWalls(x1, x2, y1, y2, z1, z2, wallMat);
		chunk.setBlocks(x1 + 1, x2 - 1, y2, z1 + 1, z2 - 1, roofMat);
		Material door = MaterialTags.pick(MaterialTags.FITTINGS_DOOR, odds, Material.BIRCH_DOOR);

		switch (odds.getRandomInt(4)) {
		case 0: // north
			chunk.setDoor(x1 + odds.getRandomInt(xR) + 1, y1, z1, door, BlockFace.NORTH_NORTH_EAST);
			chunk.setBlock(x1 + odds.getRandomInt(xR) + 1, y1 + 1, z2 - 1, materialGlass);
			placeShedTable(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z2 - 2, BlockFace.SOUTH);
			placeShedChest(generator, chunk, odds, x1 - 1, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.WEST,
					location);
			placeShedChest(generator, chunk, odds, x2, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.EAST, other);
			break;
		case 1: // south
			chunk.setDoor(x1 + odds.getRandomInt(xR) + 1, y1, z2 - 1, door, BlockFace.SOUTH_SOUTH_WEST);
			chunk.setBlock(x1 + odds.getRandomInt(xR) + 1, y1 + 1, z1, materialGlass);
			placeShedTable(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z1 + 1, BlockFace.NORTH);
			placeShedChest(generator, chunk, odds, x1 - 1, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.WEST,
					location);
			placeShedChest(generator, chunk, odds, x2, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.EAST, other);
			break;
		case 2: // west
			chunk.setDoor(x1, y1, z1 + odds.getRandomInt(zR) + 1, door, BlockFace.WEST_NORTH_WEST);
			chunk.setBlock(x2 - 1, y1 + 1, z1 + odds.getRandomInt(zR) + 1, materialGlass);
			placeShedTable(generator, chunk, odds, x2 - 2, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.EAST);
			placeShedChest(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z1 - 1, BlockFace.NORTH,
					location);
			placeShedChest(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z2, BlockFace.SOUTH, other);
			break;
		default: // east
			chunk.setDoor(x1, y1, z1 + odds.getRandomInt(zR) + 1, door, BlockFace.EAST_SOUTH_EAST);
			chunk.setBlock(x2 - 1, y1 + 1, z1 + odds.getRandomInt(zR) + 1, materialGlass);
			placeShedTable(generator, chunk, odds, x1 + 1, y1, z1 + odds.getRandomInt(zR) + 1, BlockFace.WEST);
			placeShedChest(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z1 - 1, BlockFace.NORTH,
					location);
			placeShedChest(generator, chunk, odds, x1 + odds.getRandomInt(xR) + 1, y1, z2, BlockFace.SOUTH, other);
			break;
		}
	}

	private void placeShedTable(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z,
			BlockFace direction) {
		if (odds.playOdds(oddsOfFurnace))
			chunk.setBlock(x, y, z, Material.FURNACE, direction);
		else if (odds.playOdds(oddsOfCraftingTable))
			chunk.setBlock(x, y, z, Material.CRAFTING_TABLE);
		else {
			chunk.setBlock(x, y, z, Material.SPRUCE_FENCE);
			chunk.setBlock(x, y + 1, z, Material.BIRCH_PRESSURE_PLATE);
		}
	}

	private void placeShedChest(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int y, int z,
			BlockFace direction, LootLocation location) {
		switch (direction) {
		default:
		case NORTH:
			chunk.setChest(generator, x + 1, y, z, direction, odds, generator.lootProvider, location);
			break;
		case SOUTH:
			chunk.setChest(generator, x - 1, y, z, direction, odds, generator.lootProvider, location);
			break;
		case WEST:
			chunk.setChest(generator, x, y, z + 1, direction, odds, generator.lootProvider, location);
			break;
		case EAST:
			chunk.setChest(generator, x, y, z - 1, direction, odds, generator.lootProvider, location);
			break;
		}
	}

	private final static Material matWindow = Material.GLASS_PANE;
	private final static Material matPole = Material.SPRUCE_FENCE;

	public void generateCampground(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds,
			int baseY) {

		// what are we made of?
		boolean matCamo = odds.playOdds(Odds.oddsSomewhatUnlikely);
		Colors colors = new Colors(odds);
		Material matBed = colors.getBed();
		if (matCamo)
			if (colors.getRandomColor() == DyeColor.PINK)
				colors.setColors(ColorSet.PINK);
			else
				colors.setColors(ColorSet.GREEN);
		else
			colors.fixColor();

		// direction?
		if (odds.flipCoin()) {

			// north/south tent first
			for (int z = 3; z < 9; z++) {
				chunk.setBlock(3, baseY, z, colors.getWool());
				chunk.setBlock(4, baseY + 1, z, colors.getWool());
				chunk.setBlock(5, baseY + 2, z, colors.getWool());
				chunk.setBlock(6, baseY + 3, z, colors.getWool());
				chunk.setBlock(7, baseY + 2, z, colors.getWool());
				chunk.setBlock(8, baseY + 1, z, colors.getWool());
				chunk.setBlock(9, baseY, z, colors.getWool());
			}

			// back wall
			chunk.setBlock(4, baseY, 3, colors.getWool());
			chunk.setBlock(5, baseY, 3, colors.getWool());
			chunk.setBlock(5, baseY + 1, 3, colors.getWool());
			chunk.setBlock(6, baseY, 3, colors.getWool());
			chunk.setBlock(6, baseY + 1, 3, matWindow, BlockFace.EAST, BlockFace.WEST);
			chunk.setBlock(6, baseY + 2, 3, colors.getWool());
			chunk.setBlock(7, baseY + 1, 3, colors.getWool());
			chunk.setBlock(7, baseY, 3, colors.getWool());
			chunk.setBlock(8, baseY, 3, colors.getWool());

			// post
			chunk.setBlocks(6, baseY, baseY + 3, 8, matPole);

			// beds
			if (odds.playOdds(Odds.oddsPrettyLikely))
				chunk.setBed(5, baseY, 4, matBed, BlockFace.SOUTH);
			if (odds.playOdds(Odds.oddsPrettyLikely))
				chunk.setBed(7, baseY, 4, matBed, BlockFace.SOUTH);
		} else {
			// north/south tent first
			for (int x = 3; x < 9; x++) {
				chunk.setBlock(x, baseY, 3, colors.getWool());
				chunk.setBlock(x, baseY + 1, 4, colors.getWool());
				chunk.setBlock(x, baseY + 2, 5, colors.getWool());
				chunk.setBlock(x, baseY + 3, 6, colors.getWool());
				chunk.setBlock(x, baseY + 2, 7, colors.getWool());
				chunk.setBlock(x, baseY + 1, 8, colors.getWool());
				chunk.setBlock(x, baseY, 9, colors.getWool());
			}

			// back wall
			chunk.setBlock(3, baseY, 4, colors.getWool());
			chunk.setBlock(3, baseY, 5, colors.getWool());
			chunk.setBlock(3, baseY + 1, 5, colors.getWool());
			chunk.setBlock(3, baseY, 6, colors.getWool());
			chunk.setBlock(3, baseY + 1, 6, matWindow, BlockFace.NORTH, BlockFace.SOUTH);
			chunk.setBlock(3, baseY + 2, 6, colors.getWool());
			chunk.setBlock(3, baseY + 1, 7, colors.getWool());
			chunk.setBlock(3, baseY, 7, colors.getWool());
			chunk.setBlock(3, baseY, 8, colors.getWool());

			// post
			chunk.setBlocks(8, baseY, baseY + 3, 6, matPole);

			// beds
			if (odds.playOdds(Odds.oddsPrettyLikely))
				chunk.setBed(4, baseY, 5, matBed, BlockFace.EAST);
			if (odds.playOdds(Odds.oddsPrettyLikely))
				chunk.setBed(4, baseY, 7, matBed, BlockFace.EAST);
		}

		// now the fire pit
		generateFirePit(generator, chunk, odds, 10, baseY, 10);
//		if (odds.playOdds(Odds.oddsPrettyLikely)) {
//			// stairs around the fire
////			chunk.setStair(11, baseY - 1, 10, matFireRing, BlockFace.SOUTH);
////			chunk.setStair(12, baseY - 1, 11, matFireRing, BlockFace.WEST);
////			chunk.setStair(11, baseY - 1, 12, matFireRing, BlockFace.NORTH);
////			chunk.setStair(10, baseY - 1, 11, matFireRing, BlockFace.EAST);
////			chunk.setStair(10, baseY - 1, 10, matFireRing, BlockFace.SOUTH, StairsShape.OUTER_LEFT);
////			chunk.setStair(12, baseY - 1, 10, matFireRing, BlockFace.WEST, StairsShape.OUTER_LEFT);
////			chunk.setStair(12, baseY - 1, 12, matFireRing, BlockFace.NORTH, StairsShape.OUTER_LEFT);
////			chunk.setStair(10, baseY - 1, 12, matFireRing, BlockFace.EAST, StairsShape.OUTER_LEFT);
//
//			chunk.setStair(11, baseY - 1, 10, matFireRing, BlockFace.NORTH);
//			chunk.setStair(12, baseY - 1, 11, matFireRing, BlockFace.EAST);
//			chunk.setStair(11, baseY - 1, 12, matFireRing, BlockFace.SOUTH);
//			chunk.setStair(10, baseY - 1, 11, matFireRing, BlockFace.WEST);
//			chunk.setStair(10, baseY - 1, 10, matFireRing, BlockFace.NORTH, StairsShape.INNER_LEFT);
//			chunk.setStair(12, baseY - 1, 10, matFireRing, BlockFace.EAST, StairsShape.INNER_LEFT);
//			chunk.setStair(12, baseY - 1, 12, matFireRing, BlockFace.SOUTH, StairsShape.INNER_LEFT);
//			chunk.setStair(10, baseY - 1, 12, matFireRing, BlockFace.WEST, StairsShape.INNER_LEFT);
//
//			// and the fire itself
////			chunk.setBlock(11, baseY - 1, 11, matFireBase);
//			if (odds.playOdds(Odds.oddsPrettyLikely)) {
////				chunk.clearBlocks(9, 14, baseY, 9, 14); // we do this to keep the grass and such away from the fire so
////				// it doesn't go firebug on us
//				if (odds.flipCoin())
//					chunk.setBlock(11, baseY - 2, 11, matFireSmoke);
//				else
//					chunk.setBlock(11, baseY - 2, 11, matFireBase);
//				chunk.setBlock(11, baseY - 1, 11, matFire, generator.getSettings().includeFires);
//			}
//		}

		// and the logs
		Trees trees = new Trees(odds);
		Material logMat = trees.getRandomWoodLog();
		if (odds.playOdds(Odds.oddsPrettyLikely)) {
			chunk.setBlock(11, baseY, 8, logMat, BlockFace.EAST);
			chunk.setBlock(12, baseY, 8, logMat, BlockFace.EAST);
		}
		if (odds.playOdds(Odds.oddsPrettyLikely)) {
			chunk.setBlock(8, baseY, 11, logMat, BlockFace.NORTH);
			chunk.setBlock(8, baseY, 12, logMat, BlockFace.NORTH);
		}
	}

	private final static Material matFire = Material.CAMPFIRE;
	private final static Material matFireSmoke = Material.HAY_BLOCK;
	private final static Material matFireBase = Material.COBBLESTONE;
	private final static Material matFireRing = Material.COBBLESTONE_STAIRS;
	public void generateFirePit(CityWorldGenerator generator, RealBlocks chunk, Odds odds, int x, int baseY, int z) {

		// now the fire pit
		chunk.clearBlocks(x, x + 3, baseY, baseY + 3, z, z + 3);

		chunk.setStair(x + 1, baseY - 1, z, matFireRing, BlockFace.NORTH);
		chunk.setStair(x + 2, baseY - 1, z + 1, matFireRing, BlockFace.EAST);
		chunk.setStair(x + 1, baseY - 1, z + 2, matFireRing, BlockFace.SOUTH);
		chunk.setStair(x, baseY - 1, z + 1, matFireRing, BlockFace.WEST);
		chunk.setStair(x, baseY - 1, z, matFireRing, BlockFace.NORTH, StairsShape.INNER_LEFT);
		chunk.setStair(x + 2, baseY - 1, z, matFireRing, BlockFace.EAST, StairsShape.INNER_LEFT);
		chunk.setStair(x + 2, baseY - 1, z + 2, matFireRing, BlockFace.SOUTH, StairsShape.INNER_LEFT);
		chunk.setStair(x, baseY - 1, z + 2, matFireRing, BlockFace.WEST, StairsShape.INNER_LEFT);

		if (odds.flipCoin())
			chunk.setBlock(x + 1, baseY - 2, z + 1, matFireSmoke);
		else
			chunk.setBlock(x + 1, baseY - 2, z + 1, matFireBase);
		chunk.setBlock(x + 1, baseY - 1, z + 1, matFire, generator.getSettings().includeFires);
	}

	private enum HouseRoofStyle {
		FLAT, NORTHSOUTH, WESTEAST, ANGLED
	}

	public int generateRuralShack(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds,
			int baseY, int roomWidth) {

		// what are we made of?
		Material matWall = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_ShackWalls.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matFloor = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_ShackWalls.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matRoof = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_ShackRoofs.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matCeiling = matRoof;
		HouseRoofStyle styleRoof = pickRoofStyle(odds);
		int floors = 1;

		// chunk.setWalls(2, 13, baseY, baseY + ContextData.FloorHeight, 2, 13,
		// Material.SPRUCE_WOOD);
		generateColonial(generator, chunk, context, odds, baseY, matFloor, matWall, matCeiling, matRoof, floors,
				roomWidth, roomWidth, styleRoof, false);
		return floors;
	}

	public int generateHouse(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int baseY,
			int maxFloors, int maxRoomWidth) {

		// what are we made of? (deOre swaps plain stone for a decorative stone on MODERN, so the ore pass
		// doesn't pepper the build with diorite/dirt)
		Material matWall = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_HouseWalls.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matFloor = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_HouseFloors.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matCeiling = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_HouseCeilings.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		Material matRoof = generator.materialProvider.deOre(
				generator.materialProvider.itemsSelectMaterial_HouseRoofs.getRandomMaterial(odds, Material.COBBLESTONE), odds);
		HouseRoofStyle styleRoof = pickRoofStyle(odds);
		int floors = odds.getRandomInt(maxFloors) + 1;

		// TODO add bed
		// TODO add kitchen
		// TODO add living room
		// TODO add split level house style

		// draw the house
		generateColonial(generator, chunk, context, odds, baseY, matFloor, matWall, matCeiling, matRoof, floors,
				MinSize, maxRoomWidth, styleRoof, true);
		return floors;
	}

	public int generateHouse(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds, int baseY,
			int maxFloors) {
		return generateHouse(generator, chunk, context, odds, baseY, maxFloors, MaxSize);
	}

	private void generateColonial(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds,
			int baseY, Material matFloor, Material matWall, Material matCeiling, Material matRoof, int floors,
			int minRoomWidth, int maxRoomWidth, HouseRoofStyle styleRoof, boolean allowMissingRooms) {

		Trees trees = new Trees(odds);
		// The fittings, one pick per house so the rooms match: a trapdoor for the attic hatch, a front
		// door, an interior door, a window for the bands the walls leave, and a fence for the railings
		// round a missing upper room. Each pool falls back to what the house was always built with.
		Material matTrapDoor = MaterialTags.pick(MaterialTags.FITTINGS_TRAPDOOR, odds, trees.getRandomWoodTrapDoor());
		Material matDoor = MaterialTags.pick(MaterialTags.FITTINGS_DOOR, odds, Material.BIRCH_DOOR);
		Material matInteriorDoor = MaterialTags.pick(MaterialTags.FITTINGS_INTERIOR_DOOR, odds, Material.BIRCH_DOOR);
		Material matWindow = MaterialTags.pick(MaterialTags.FITTINGS_WINDOW, odds, materialGlass);
		Material matFence = MaterialTags.pick(MaterialTags.FITTINGS_FENCE, odds, materialFence);
		Material matStairs = MaterialTags.pick(MaterialTags.FITTINGS_STAIRS, odds, null);
		// drawn only when there is a pooled stair to style: a draw here on a vanilla world would shift
		// every later choice the house makes (measured: the rooms moved)
		String railStyle = matStairs == null ? RAIL_STYLES[0] : RAIL_STYLES[odds.getRandomInt(RAIL_STYLES.length)];

		// what are the rooms like?
		Room[][][] rooms = new Room[floors][2][2];
		for (int f = 0; f < floors; f++) {
			boolean missingRoom = false;
			for (int x = 0; x < 2; x++) {
				for (int z = 0; z < 2; z++) {

					// missing rooms?
					boolean thisRoomMissing = false;
					if (allowMissingRooms && floors > 1) {
						thisRoomMissing = odds.getRandomInt(MissingRoomOdds) == 0;
					}

					// what does the room "look" like?
					int thisRoomWidthZ = getRoomWidth(odds, minRoomWidth, maxRoomWidth);
					int thisRoomWidthX = getRoomWidth(odds, minRoomWidth, maxRoomWidth);
					boolean thisRoomHasWalls = true;
					// bedrooms by default, but every so often a bathroom (the ground floor's KITCHEN/DINING/
					// LIVING are assigned explicitly below and override this)
					Room.Style thisRoomStyle = odds.getRandomInt(3) == 0 ? Room.Style.BATHROOM : Room.Style.BED;

					// create the room
					rooms[f][x][z] = new Room(thisRoomMissing, thisRoomWidthZ, thisRoomWidthX, thisRoomHasWalls,
							thisRoomStyle, matTrapDoor, matDoor, matInteriorDoor, matWindow, matFence, matStairs, railStyle);

					// single floor is a little different
					if (floors == 1) {
						if (rooms[f][x][z].missing) {
							if (!missingRoom)
								missingRoom = true;
							else
								rooms[f][x][z].missing = false;
						}
					} else {

						// first floor must be complete
						if (f == 0)
							rooms[f][x][z].missing = false;

							// each additional floors must include any missing rooms from below
						else if (rooms[f - 1][x][z].missing)
							rooms[f][x][z].missing = true;

							// only one new missing room per floor
						else if (rooms[f][x][z].missing) {
							if (!missingRoom)
								missingRoom = true;
							else
								rooms[f][x][z].missing = false;
						}

						// all rooms must be the same size (or smaller) than the one below it
						if (f > 0) {
							rooms[f][x][z].widthX = Math.min(rooms[f][x][z].widthX, rooms[f - 1][x][z].widthX);
							rooms[f][x][z].widthZ = Math.min(rooms[f][x][z].widthZ, rooms[f - 1][x][z].widthZ);
						}
					}
				}
			}
		}

		// find a non-missing room on the first floor
		int roomX = odds.getRandomInt(2);
		int roomZ = odds.getRandomInt(2);
		while (rooms[0][roomX][roomZ].missing) {
			roomX = odds.getRandomInt(2);
			roomZ = odds.getRandomInt(2);
		}

		// pick the entry room
		for (int f = 0; f < floors; f++) {

			// set the style and make sure there is room for stairs
			rooms[f][roomX][roomZ].missing = false;
			rooms[f][roomX][roomZ].style = Room.Style.ENTRY;
			rooms[f][roomX][roomZ].widthX = maxRoomWidth;
			rooms[f][roomX][roomZ].widthZ = maxRoomWidth;

			// and on the second floor
			if (f == 1) {

				// if one of the side rooms is missing, make it not missing and make the
				// opposite one is
				if (rooms[f][roomX][flip(roomZ)].missing) {
					rooms[f][roomX][flip(roomZ)].missing = false;
					rooms[f][flip(roomX)][flip(roomZ)].missing = true;
				} else if (rooms[f][flip(roomX)][roomZ].missing) {
					rooms[f][flip(roomX)][roomZ].missing = false;
					rooms[f][flip(roomX)][flip(roomZ)].missing = true;
				}
			}
		}

		// now the kitchen
		roomZ = flip(roomZ);
		if (rooms[0][roomX][roomZ].missing) {
			roomX = flip(roomX);
			roomZ = flip(roomZ);
		}
		rooms[0][roomX][roomZ].style = Room.Style.KITCHEN;

		// is this a single story house?
		if (floors == 1) {

			// next find the dining room
			roomX = flip(roomX);
			if (!rooms[0][roomX][roomZ].missing) {
				rooms[0][roomX][roomZ].style = Room.Style.DINING;
			}

			// put the bed in the last spot
			roomZ = flip(roomZ);
			rooms[0][roomX][roomZ].missing = false;
			rooms[0][roomX][roomZ].style = Room.Style.BED;

			// got more floors!
		} else {

			// next find the dining room
			roomX = flip(roomX);
			if (!rooms[0][roomX][roomZ].missing) {
				rooms[0][roomX][roomZ].style = Room.Style.DINING;

				// put the living room in the last spot if available
				roomZ = flip(roomZ);
				if (!rooms[0][roomX][roomZ].missing) {
					rooms[0][roomX][roomZ].style = Room.Style.LIVING;
				}

				// only one room left, dining room please!
			} else {
				roomZ = flip(roomZ);
				if (!rooms[0][roomX][roomZ].missing) {
					rooms[0][roomX][roomZ].style = Room.Style.DINING;
				}
			}
		}

		// where is the center of the house?
		int roomOffsetX = chunk.width / 2 + odds.getRandomInt(2) - 1;
		int roomOffsetZ = chunk.width / 2 + odds.getRandomInt(2) - 1;

		// draw the individual rooms
		for (int f = 0; f < floors; f++) {

			// just in case we come across an entry way
			int entryX = -1;
			int entryZ = -1;

			// floor material?
			Material thisFloor = matFloor;
			if (f > 0)
				thisFloor = matCeiling;

			// do the rooms
			for (int x = 0; x < 2; x++) {
				for (int z = 0; z < 2; z++) {

					// do entry ways later
					if (rooms[f][x][z].style == Room.Style.ENTRY) {
						entryX = x;
						entryZ = z;
					} else
						drawRoom(generator, chunk, context, odds, rooms, f, floors, x, z, roomOffsetX, roomOffsetZ,
								baseY, thisFloor, matWall, matCeiling, matRoof);
				}
			}

			// found an entry
			if (entryX != -1) {
				drawRoom(generator, chunk, context, odds, rooms, f, floors, entryX, entryZ, roomOffsetX, roomOffsetZ,
						baseY, thisFloor, matWall, matCeiling, matRoof);
			}
		}

		// contents-aware-of-doors: furniture is placed before the doors are cut, so a piece can end up
		// standing in a doorway. Now that every door and every furnishing is down, clear whatever blocks
		// a door's threshold so you can always walk through.
		clearDoorways(chunk, baseY, floors);

		// figure out roofs
		int roofBottom = baseY + floors * DataContext.FloorHeight - 1;
		int roofHeight = DataContext.FloorHeight + 1;
		boolean makeAttic = true;

		switch (styleRoof) {
		case ANGLED:
		default:

			// place the roof!
			for (int y = 0; y < roofHeight; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (y == 0) {
							if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x - 1, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x + 1, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x, yAt, z - 1, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x, yAt, z + 1, matRoof, matTrapDoor))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						} else {
							if (chunk.isType(x, yAt, z, matRoof) && chunk.isType(x - 1, yAt, z, matRoof)
									&& chunk.isType(x + 1, yAt, z, matRoof) && chunk.isType(x, yAt, z - 1, matRoof)
									&& chunk.isType(x, yAt, z + 1, matRoof))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						}
//						if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor) && 
//							!chunk.isEmpty(x - 1, yAt, z) && !chunk.isEmpty(x + 1, yAt, z) &&
//							!chunk.isEmpty(x, yAt, z - 1) && !chunk.isEmpty(x, yAt, z + 1))
//							chunk.setBlock(x, yAt + 1, z, matRoof);
					}
				}
			}
			break;
		case NORTHSOUTH:

			// place the roof!
			for (int y = 0; y < roofHeight; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (y == 0) {
							if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x - 1, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x + 1, yAt, z, matRoof, matTrapDoor))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						} else {
							if (chunk.isType(x, yAt, z, matRoof) && chunk.isType(x - 1, yAt, z, matRoof)
									&& chunk.isType(x + 1, yAt, z, matRoof))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						}
//						if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor) && 
//							!chunk.isEmpty(x - 1, yAt, z) && !chunk.isEmpty(x + 1, yAt, z))
//							chunk.setBlock(x, yAt + 1, z, matRoof);
					}
				}
			}
			break;
		case WESTEAST:

			// place the roof!
			for (int y = 0; y < roofHeight; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (y == 0) {
							if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x, yAt, z - 1, matRoof, matTrapDoor)
									&& chunk.isOfTypes(x, yAt, z + 1, matRoof, matTrapDoor))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						} else {
							if (chunk.isType(x, yAt, z, matRoof) && chunk.isType(x, yAt, z - 1, matRoof)
									&& chunk.isType(x, yAt, z + 1, matRoof))
								chunk.setBlock(x, yAt + 1, z, matRoof);
						}
//						if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor) && 
//							!chunk.isEmpty(x, yAt, z - 1) && !chunk.isEmpty(x, yAt, z + 1))
//							chunk.setBlock(x, yAt + 1, z, matRoof);
					}
				}
			}
			break;
		case FLAT:

			// place the roof!
			for (int y = 0; y < 1; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (chunk.isOfTypes(x, yAt, z, matRoof, matTrapDoor)
								&& (chunk.isEmpty(x - 1, yAt, z) || chunk.isEmpty(x + 1, yAt, z)
								|| chunk.isEmpty(x, yAt, z - 1) || chunk.isEmpty(x, yAt, z + 1)))
							chunk.setBlock(x, yAt + 1, z, matRoof);
					}
				}
			}
			makeAttic = false;
			break;
		}

		// MODERN houses get a pitched roof: the stepped layers above become slopes (see slopeRoof).
		// CLASSIC keeps the stepped full-block roof it has had since 1.8. This runs while the layers
		// are still solid — the attic pass below hollows them, and a ring block with air on its
		// inside as well as its outside would read as a ridge, not a slope (measured: 6 stairs a
		// layer instead of a ring of them).
		// The slope starts at the ceiling layer's own edge, on the wall tops — the owner's hand-fix on
		// every house: an eave, not a ledge.
		if (makeAttic && generator.isModernStyle())
			slopeRoof(generator, chunk, odds, matRoof, roofBottom, roofBottom + roofHeight + 1,
					styleRoof == HouseRoofStyle.NORTHSOUTH, styleRoof == HouseRoofStyle.WESTEAST);

		if (makeAttic) {

			// fill the potential attic space with something silly
			for (int y = 1; y < roofHeight - 1; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (/* !chunk.isEmpty(x, yAt + 1, z)) */
								chunk.isType(x, yAt, z, matRoof))
							chunk.setBlock(x, yAt, z, Material.BEDROCK); // mark potential attic space
					}
				}
			}

			// but don't over do it and go too far
			for (int y = 1; y < roofHeight - 1; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (chunk.isType(x, yAt, z, Material.BEDROCK)) { // where we think the attic might be
							if (chunk.isEmpty(x - 1, yAt, z) || chunk.isEmpty(x + 1, yAt, z)
									|| chunk.isEmpty(x, yAt, z - 1) || chunk.isEmpty(x, yAt, z + 1)
									|| besideSlope(chunk, x, yAt, z))
								chunk.setBlock(x, yAt, z, matRoof);
						}
					}
				}
			}

			// finally remove the silliness from the attic
			for (int y = 1; y < roofHeight - 1; y++) {
				for (int x = 1; x < chunk.width - 1; x++) {
					for (int z = 1; z < chunk.width - 1; z++) {
						int yAt = y + roofBottom;
						if (chunk.isType(x, yAt, z, Material.BEDROCK))
							chunk.clearBlock(x, yAt, z);
					}
				}
			}
		}
	}

	/**
	 * Turn the stepped roof layers between {@code yFrom} (inclusive) and {@code yTo} (exclusive) into a
	 * pitched one. Each layer above the ceiling is a rectangle inset one block from the layer below, so its
	 * edge blocks are exactly where a 45° slope wants a stair: a full block with open air on one side
	 * becomes a roof stair facing inward (its high side toward the ridge); air on two adjacent sides is an
	 * outer corner; air on two opposite sides, or three, or four, is the ridge itself; and a block with no
	 * open side but an open <em>diagonal</em> between two edge blocks is the valley where two wings meet,
	 * an inner corner. The stair shapes — inner, outer — are then derived by {@link SupportBlocks#reconnect},
	 * i.e. by the roof block's own neighbour logic, exactly as if a player had placed them.
	 *
	 * <p>A gable roof ({@code gableX}: the layers narrow along x, the ridge runs north–south; {@code gableZ}
	 * the other way) slopes on one axis only. Its end faces are vertical, so air on the gable axis does not
	 * count and those blocks stay solid — the owner's first look had every gable end as a stack of stairs,
	 * each with its notch, "flat end bits that are steps".
	 *
	 * <p>The roof block comes from {@code #cityworld:fittings/roof}: the entry named after the roof
	 * material when there is one ({@code oak_planks} → {@code oak_planks_roof}), else any entry (a
	 * cobblestone house under a terracotta roof), else the vanilla stairs of that material — so a world
	 * without a roof mod still gets pitched roofs, in stairs. The ridge takes the matching
	 * {@code *_top_roof} cap when the mod has one, else it stays a full block.
	 *
	 * <p>Only blocks of {@code matRoof} are touched, and only where they have air beside them at their
	 * own height. It runs while the layers are still solid — after the attic pass hollows them, a ring
	 * block has air on its inside too and reads as a ridge (measured: 6 stairs a layer instead of 35).
	 */
	private void slopeRoof(CityWorldGenerator generator, RealBlocks chunk, Odds odds, Material matRoof, int yFrom,
			int yTo, boolean gableX, boolean gableZ) {
		Material slope = pickRoofBlock(odds, matRoof);
		Material ridge = ridgeFor(slope);
		Material gable = gableWallFor(slope, matRoof);
		if (slope == null)
			return;
		for (int y = yFrom; y < yTo; y++) {
			// the layer as it was before this pass touched it: a valley test asks whether its neighbours
			// are edge blocks, and the scan has already turned the earlier ones into stairs
			boolean[][] roof = new boolean[chunk.width][chunk.width];
			for (int x = 0; x < chunk.width; x++)
				for (int z = 0; z < chunk.width; z++)
					roof[x][z] = chunk.isType(x, y, z, matRoof);
			for (int x = 1; x < chunk.width - 1; x++) {
				for (int z = 1; z < chunk.width - 1; z++) {
					if (!roof[x][z])
						continue;
					// a gable's end faces are walls, not slopes: air there does not count
					boolean north = !gableX && chunk.isEmpty(x, y, z - 1), south = !gableX && chunk.isEmpty(x, y, z + 1);
					boolean west = !gableZ && chunk.isEmpty(x - 1, y, z), east = !gableZ && chunk.isEmpty(x + 1, y, z);
					int open = (north ? 1 : 0) + (south ? 1 : 0) + (west ? 1 : 0) + (east ? 1 : 0);
					if (open == 0) {
						if (gableX || gableZ) {
							// an end wall of a gable: the roof's own wood (the owner rebuilt every gable end
							// in redwood logs under a redwood roof; the house's roof stone looked wrong)
							boolean endWall = gableX ? (chunk.isEmpty(x, y, z - 1) || chunk.isEmpty(x, y, z + 1))
									: (chunk.isEmpty(x - 1, y, z) || chunk.isEmpty(x + 1, y, z));
							if (endWall && gable != matRoof)
								chunk.setBlock(x, y, z, gable);
							continue;
						}
						// a valley: an open diagonal between two edge blocks of this layer is the inner
						// corner where two wings of the house meet. Face it away from the diagonal on one
						// axis; reconnect turns it into the inner shape from its neighbours.
						BlockFace valley = null;
						for (int dx = -1; dx <= 1 && valley == null; dx += 2)
							for (int dz = -1; dz <= 1 && valley == null; dz += 2)
								if (chunk.isEmpty(x + dx, y, z + dz) && roof[x + dx][z] && roof[x][z + dz])
									valley = dz < 0 ? BlockFace.SOUTH : BlockFace.NORTH;
						if (valley != null)
							chunk.setStair(x, y, z, slope, valley);
						continue;
					}
					if (open == 1 || (open == 2 && north != south)) {
						// one open side: a stair whose high side faces away from it. Two adjacent open sides:
						// an outer corner, which the roof block's own logic (reconnect, below) only recognises
						// when the block IN FRONT of it runs across it — so of the two possible facings take
						// the one whose front neighbour is an edge block on the other axis. Along a diagonal
						// hip every block has two open sides, and the wrong choice there is a sawtooth of
						// straight stairs instead of a run of corners.
						BlockFace alongZ = north ? BlockFace.SOUTH : BlockFace.NORTH;
						BlockFace alongX = west ? BlockFace.EAST : BlockFace.WEST;
						BlockFace facing;
						if (open == 1)
							facing = (north || south) ? alongZ : alongX;
						else if (edgeAcross(chunk, roof, x + alongZ.getModX(), y, z + alongZ.getModZ(), true))
							facing = alongZ;
						else if (edgeAcross(chunk, roof, x + alongX.getModX(), y, z + alongX.getModZ(), false))
							facing = alongX;
						else
							facing = alongZ;
						chunk.setStair(x, y, z, slope, facing);
					} else if (ridge != null) {
						chunk.setBlock(x, y, z, ridge); // a ridge run, its end, or a pyramid's tip
					}
				}
			}
		}
		// every cell placed; now let each roof block read its neighbours for corner and ridge shapes
		chunk.reconnect(1, chunk.width - 1, yFrom, yTo, 1, chunk.width - 1);
	}

	/** Whether the roof block at (x, z) of this layer is an edge block whose slope runs across the axis of the
	 *  block asking — i.e. it is open on the OTHER axis ({@code zAxis}: the asker faces along z, so this one
	 *  must be open east or west). */
	private static boolean edgeAcross(RealBlocks chunk, boolean[][] roof, int x, int y, int z, boolean zAxis) {
		if (x < 0 || x >= chunk.width || z < 0 || z >= chunk.width || !roof[x][z])
			return false;
		return zAxis ? (chunk.isEmpty(x - 1, y, z) || chunk.isEmpty(x + 1, y, z))
				: (chunk.isEmpty(x, y, z - 1) || chunk.isEmpty(x, y, z + 1));
	}

	/** The sloped block for a roof of {@code matRoof}: the pool's namesake, else any pool entry, else that
	 *  material's vanilla stairs. */
	private Material pickRoofBlock(Odds odds, Material matRoof) {
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(matRoof.getBlock()).getPath();
		Material named = MaterialTags.named(MaterialTags.FITTINGS_ROOF, path + "_roof");
		if (named != null)
			return named;
		return MaterialTags.pick(MaterialTags.FITTINGS_ROOF, odds, Mapper.getStairsFor(matRoof));
	}

	/**
	 * The wall block for a gable end under a pooled roof block: the block the roof is "made of" —
	 * {@code nether_bricks_roof} → {@code nether_bricks}, {@code redwood_planks_roof} → {@code redwood_planks},
	 * {@code redwood_roof} (a log-textured roof) → {@code redwood_log} — found by path in any namespace, else the
	 * house's own roof material (which is also what a vanilla-stairs roof keeps).
	 */
	private Material gableWallFor(Material slope, Material matRoof) {
		if (slope == null)
			return matRoof;
		String path = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(slope.getBlock()).getPath();
		if (!path.endsWith("_roof"))
			return matRoof;
		String base = path.substring(0, path.length() - "_roof".length());
		for (String candidate : new String[] { base, base + "_log", base + "_planks" }) {
			for (net.minecraft.resources.Identifier id : net.minecraft.core.registries.BuiltInRegistries.BLOCK.keySet())
				if (id.getPath().equals(candidate)) {
					Material found = Material.of(id.toString());
					if (found != Material.AIR && found.isOccluding())
						return found;
				}
		}
		return matRoof;
	}

	/** The ridge cap that goes with a sloped roof block ({@code x_roof} → {@code x_top_roof}), or null. */
	private Material ridgeFor(Material slope) {
		if (slope == null)
			return null;
		net.minecraft.resources.Identifier id = net.minecraft.core.registries.BuiltInRegistries.BLOCK
				.getKey(slope.getBlock());
		if (!id.getPath().endsWith("_roof"))
			return null;
		String top = id.getPath().substring(0, id.getPath().length() - "_roof".length()) + "_top_roof";
		Material ridge = Material.of(id.getNamespace() + ":" + top);
		return ridge == Material.AIR ? null : ridge;
	}

	/**
	 * Whether a cell has a sloped roof block beside it that is not facing it — i.e. the cell is the wall the
	 * slope runs against, not the attic behind it. Where a lower roof meets a taller wing's gable, the gable
	 * cells' only neighbours are the lower roof's blocks (never air), so the attic pass cleared them and left
	 * holes into the attic (the owner filled every one by hand, 2026-09-18). A stair's high side faces the
	 * interior, so the one neighbour that IS attic is the cell a stair faces.
	 */
	private static boolean besideSlope(RealBlocks chunk, int x, int y, int z) {
		for (BlockFace d : HORIZ) {
			BlockFace f = chunk.getFacing(x + d.getModX(), y, z + d.getModZ());
			if (f != null && f != d.getOppositeFace() && chunk.isStairLike(x + d.getModX(), y, z + d.getModZ()))
				return true;
		}
		return false;
	}

	private int flip(int i) {
		return i == 0 ? 1 : 0;
	}

	private void drawRoom(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds,
			Room[][][] rooms, int floor, int floors, int x, int z, int roomOffsetX, int roomOffsetZ, int baseY,
			Material matFloor, Material matWall, Material matCeiling, Material matRoof) {

		// which room?
		Room room = rooms[floor][x][z];

		// missing?
		if (room.missing) {

			// is there a floor below?
			if (floor > 0 && !rooms[floor - 1][x][z].missing)
				rooms[floor - 1][x][z].DrawRailing(chunk);
		} else {

			// draw bottom bits
			if (floor == 0) {
				room.DrawFloor(chunk, context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY, matFloor);
			}

			// draw outside bits
			room.DrawWalls(chunk, context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY, matWall);

			// top floor's top
			if (floor == floors - 1) {
				room.DrawRoof(chunk, context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY, matRoof);

			} else {
				room.DrawCeiling(chunk, context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY, matCeiling);
			}

			// now the inner bits
			room.DrawStyle(generator, chunk, context, odds, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);
		}
	}

	private int getRoomWidth(Odds odds, int minRoomWidth, int maxRoomWidth) {
		return odds.getRandomInt(maxRoomWidth - minRoomWidth + 1) + minRoomWidth;
	}

	private static final BlockFace[] HORIZ = { BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST };
	private static final Material[] BED_MATS = { Material.WHITE_BED, Material.ORANGE_BED, Material.MAGENTA_BED,
			Material.LIGHT_BLUE_BED, Material.YELLOW_BED, Material.LIME_BED, Material.PINK_BED, Material.GRAY_BED,
			Material.LIGHT_GRAY_BED, Material.CYAN_BED, Material.PURPLE_BED, Material.BLUE_BED, Material.BROWN_BED,
			Material.GREEN_BED, Material.RED_BED, Material.BLACK_BED };

	/**
	 * Clear any furnishing left standing in a doorway. For every door (its lower half), the two
	 * horizontal directions that are open above the doorframe are the through-path (interior + exterior);
	 * the other two are the wall the door sits in. We wipe the walk cells (floor + head height) on the
	 * open sides, so a couch or table that landed across a threshold no longer traps the room.
	 */
	private void clearDoorways(RealBlocks chunk, int baseY, int floors) {
		int yTop = baseY + floors * DataContext.FloorHeight + 2;
		for (int y = baseY; y <= yTop; y++)
			for (int x = 0; x < chunk.width; x++)
				for (int z = 0; z < chunk.width; z++) {
					if (!isDoor(chunk, x, y, z) || isDoor(chunk, x, y - 1, z))
						continue; // only act on the lower half of a door
					for (BlockFace dir : HORIZ) {
						int nx = x + dir.getModX(), nz = z + dir.getModZ();
						if (nx < 0 || nx >= chunk.width || nz < 0 || nz >= chunk.width)
							continue;
						if (!chunk.isEmpty(nx, y + 2, nz))
							continue; // solid above => this side is the wall, not the passage
						clearFurniture(chunk, nx, y, nz);
						clearFurniture(chunk, nx, y + 1, nz);
					}
				}
	}

	private boolean isDoor(RealBlocks chunk, int x, int y, int z) {
		return chunk.isDoor(x, y, z); // any DoorBlock: the pools bring doors DOOR_MATS never listed
	}

	private boolean isBed(RealBlocks chunk, int x, int y, int z) {
		for (Material b : BED_MATS)
			if (chunk.isType(x, y, z, b))
				return true;
		return false;
	}

	/** Clear a blocking cell from a doorway — but if it's a bed, take the WHOLE bed (its paired half too),
	 *  so we never leave a bisected "half bed" straddling the door frame. */
	private void clearFurniture(RealBlocks chunk, int x, int y, int z) {
		if (chunk.isEmpty(x, y, z))
			return;
		if (isBed(chunk, x, y, z))
			for (BlockFace dir : HORIZ) {
				int nx = x + dir.getModX(), nz = z + dir.getModZ();
				if (nx >= 0 && nx < chunk.width && nz >= 0 && nz < chunk.width && isBed(chunk, nx, y, nz))
					chunk.setBlock(nx, y, nz, Material.AIR);
			}
		chunk.setBlock(x, y, z, Material.AIR);
	}

	private HouseRoofStyle pickRoofStyle(Odds odds) {
		switch (odds.getRandomInt(4)) {
		default:
		case 0:
			return HouseRoofStyle.ANGLED;
		case 1:
			return HouseRoofStyle.NORTHSOUTH;
		case 2:
			return HouseRoofStyle.WESTEAST;
		case 3:
			return HouseRoofStyle.FLAT;
		}
	}

	private final static String[] RAIL_STYLES = { "classic", "harp", "smooth" };

	/** The direction on the right hand of someone facing {@code f}. */
	private static BlockFace rightOf(BlockFace f) {
		switch (f) {
		case NORTH: return BlockFace.EAST;
		case EAST: return BlockFace.SOUTH;
		case SOUTH: return BlockFace.WEST;
		default: return BlockFace.NORTH;
		}
	}

	private final static Material materialAir = Material.AIR;
	private final static Material materialGlass = Material.GLASS;
	private final static Material materialFence = Material.SPRUCE_FENCE;
	private final static Material materialStair = Material.BIRCH_STAIRS;
	private final static Material materialUnderStairs = Material.BIRCH_PLANKS;

	private final static int MinSize = 4;
	private final static int MaxSize = 6;
	private final static int MissingRoomOdds = 5; // 1/n of the time a room is missing

	// the description of a single room
	private final static class Room {
		public enum Style {
			BED, KITCHEN, DINING, ENTRY, LIVING, BATHROOM
		}

		int widthX;
		int widthZ;
		boolean missing;
		final boolean walls;
		Style style;
		final Material trapDoor;
		final Material door; // the front door (an exterior wall)
		final Material interiorDoor; // between rooms
		final Material window; // the band a wall leaves open — glass, or a framed window from the pool
		final Material fence; // the railing round a missing room's floor
		final Material stairs; // a pooled one-block stair tread, or null for the vanilla run
		final String railStyle; // classic / harp / smooth, for the pooled railings and balconies

		Room(boolean aMissing, int aWidthX, int aWidthZ, boolean aWalls, Style aStyle, Material aTrapDoor,
				Material aDoor, Material aInteriorDoor, Material aWindow, Material aFence, Material aStairs,
				String aRailStyle) {
			super();

			missing = aMissing;
			widthX = aWidthX;
			widthZ = aWidthZ;
			walls = aWalls;
			style = aStyle;
			trapDoor = aTrapDoor;
			door = aDoor;
			interiorDoor = aInteriorDoor;
			window = aWindow;
			fence = aFence;
			stairs = aStairs;
			railStyle = aRailStyle;
		}

		// where are we?
		boolean located;
		boolean roomEast;
		boolean roomSouth;
		int x1;
		int x2;
		int z1;
		int z2;
		int y1;
		int y2;

		void Locate(DataContext context, int floor, int floors, int x, int z, int roomOffsetX,
				int roomOffsetZ, int baseY) {
			if (!located) {
				located = true;
				roomEast = x != 0;
				roomSouth = z != 0;
				x1 = roomOffsetX - (roomEast ? 0 : widthX);
				x2 = roomOffsetX + (roomEast ? widthX : 0);
				z1 = roomOffsetZ - (roomSouth ? 0 : widthZ);
				z2 = roomOffsetZ + (roomSouth ? widthZ : 0);
				y1 = baseY + floor * DataContext.FloorHeight;
				y2 = y1 + DataContext.FloorHeight - 1;
			}
		}

		void DrawWalls(RealBlocks chunk, DataContext context, int floor, int floors, int x, int z,
				int roomOffsetX, int roomOffsetZ, int baseY, Material matWall) {

			// find ourselves
			Locate(context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);

			// draw the walls. A window band is placed the way a pane would be connected — along the
			// wall — which is what turns a framed window from the pool across the wall (Material.withFaces;
			// plain glass ignores it), and is then reconnected so its blocks read each other into one run
			// of frames, which worldgen placement alone never triggers
			if (roomEast) {
				chunk.setBlocks(x2, x2 + 1, y1, y2, z1, z2 + 1, matWall); // east wall
				chunk.setBlocks(x2, x2 + 1, y1 + 1, y2 - 1, z1 + 1, z2, window, BlockFace.NORTH, BlockFace.SOUTH); // eastern window

				if (roomSouth) {
					chunk.setBlocks(x1, x2 + 1, y1, y2, z2, z2 + 1, matWall); // south wall
					chunk.setBlocks(x1 + 1, x2, y1 + 1, y2 - 1, z2, z2 + 1, window, BlockFace.EAST, BlockFace.WEST); // southern window

					chunk.setBlocks(x1, x2 + 1, y1, y2, z1, z1 + 1, matWall); // north wall
					chunk.setBlocks(x1, x1 + 1, y1, y2, z1, z2 + 1, matWall); // west wall

				} else {
					chunk.setBlocks(x1, x2 + 1, y1, y2, z1, z1 + 1, matWall); // north wall
					chunk.setBlocks(x1 + 1, x2, y1 + 1, y2 - 1, z1, z1 + 1, window, BlockFace.EAST, BlockFace.WEST); // northern window

					chunk.setBlocks(x1, x2 + 1, y1, y2, z2, z2 + 1, matWall); // south wall
					chunk.setBlocks(x1, x1 + 1, y1, y2, z1, z2 + 1, matWall); // west wall

				}
			} else {
				chunk.setBlocks(x1, x1 + 1, y1, y2, z1, z2 + 1, matWall); // west wall
				chunk.setBlocks(x1, x1 + 1, y1 + 1, y2 - 1, z1 + 1, z2, window, BlockFace.NORTH, BlockFace.SOUTH); // western window

				if (roomSouth) {
					chunk.setBlocks(x1, x2 + 1, y1, y2, z2, z2 + 1, matWall); // south wall
					chunk.setBlocks(x1 + 1, x2, y1 + 1, y2 - 1, z2, z2 + 1, window, BlockFace.EAST, BlockFace.WEST); // southern window

					chunk.setBlocks(x1, x2 + 1, y1, y2, z1, z1 + 1, matWall); // north wall
					chunk.setBlocks(x2, x2 + 1, y1, y2, z1, z2 + 1, matWall); // east wall

				} else {
					chunk.setBlocks(x1, x2 + 1, y1, y2, z1, z1 + 1, matWall); // north wall
					chunk.setBlocks(x1 + 1, x2, y1 + 1, y2 - 1, z1, z1 + 1, window, BlockFace.EAST, BlockFace.WEST); // northern window

					chunk.setBlocks(x1, x2 + 1, y1, y2, z2, z2 + 1, matWall); // south wall
					chunk.setBlocks(x2, x2 + 1, y1, y2, z1, z2 + 1, matWall); // east wall
				}
			}

			if (window != materialGlass)
				chunk.reconnect(x1, x2 + 1, y1 + 1, y2 - 1, z1, z2 + 1);
		}

		void DrawFloor(RealBlocks chunk, DataContext context, int floor, int floors, int x, int z,
				int roomOffsetX, int roomOffsetZ, int baseY, Material matFloor) {

			// find ourselves
			Locate(context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);

			// put the rug down
			chunk.setBlocks(x1, x2 + 1, y1 - 1, y1, z1, z2 + 1, matFloor);
		}

		void DrawCeiling(RealBlocks chunk, DataContext context, int floor, int floors, int x, int z,
				int roomOffsetX, int roomOffsetZ, int baseY, Material matCeiling) {

			// find ourselves
			Locate(context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);

			// put the rug down
			chunk.setBlocks(x1, x2 + 1, y2, y2 + 1, z1, z2 + 1, matCeiling);
		}

		void DrawRoof(RealBlocks chunk, DataContext context, int floor, int floors, int x, int z,
				int roomOffsetX, int roomOffsetZ, int baseY, Material matRoof) {

			// find ourselves
			Locate(context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);

			// put roof on top
			// TODO need fancier roofs
			chunk.setBlocks(x1, x2 + 1, y2, y2 + 1, z1, z2 + 1, matRoof);
		}

		/**
		 * The staircase: a corner landing at {@code (cx, y1, cz)}, a first step beside it on the {@code side}
		 * facing back onto it, then three treads rising one block per cell along {@code run}, and the top
		 * landing after them a floor up — upstream's shape, the same in all four corners of the entry room.
		 *
		 * <p>Vanilla: plank landings, stairs with upside-down stairs underneath. With a tread from
		 * {@code #cityworld:fittings/stairs} (Macaw's compact or terrace stairs, a full riser per block) the
		 * underside is not needed, the landings are the matching {@code platform}, a {@code railing} stands
		 * in the cell beside each tread on the open side — its {@code toggle} chosen so the banister sits on
		 * the edge it shares with the tread: {@code toggle=true} puts the rail on the LEFT edge of its own
		 * cell looking the way it faces — and the matching {@code balcony} rails the upper floor along the
		 * opening. All of it measured from what the owner built by hand in two houses (2026-09-18: treads
		 * {@code compact_stairs[facing=west]}, railings beside them {@code toggle=false}, platforms at both
		 * landings, {@code balcony[north=true]} along the hole).
		 */
		void drawStairRun(RealBlocks chunk, int cx, int y1, int cz, BlockFace run, BlockFace side) {
			BlockFace back = side.getOppositeFace(); // the first step faces this way, onto the corner landing
			int sx = side.getModX(), sz = side.getModZ(), rx = run.getModX(), rz = run.getModZ();
			Material tread = stairs;
			Material railing = tread == null ? null : MaterialTags.stairPart(tread, "railing");
			Material platform = tread == null ? null : MaterialTags.stairPart(tread, "platform");
			Material balcony = tread == null ? null : MaterialTags.stairPart(tread, "balcony");
			if (tread == null) {
				chunk.setBlock(cx, y1, cz, materialUnderStairs);
				chunk.setBlock(cx + sx, y1, cz + sz, materialStair, back);
				for (int k = 1; k <= 3; k++) {
					chunk.setBlock(cx + k * rx, y1 + k, cz + k * rz, materialStair, run);
					chunk.setBlock(cx + k * rx, y1 + k - 1, cz + k * rz, materialStair, run.getOppositeFace(), Half.TOP);
				}
				chunk.setBlock(cx + 4 * rx, y1 + 3, cz + 4 * rz, materialUnderStairs);
				return;
			}
			chunk.setBlock(cx, y1, cz, platform != null ? platform : materialUnderStairs);
			chunk.setBlock(cx + sx, y1, cz + sz, tread, back);
			if (railing != null) // beside the first step, on its open side (the run's side of it)
				chunk.setBlock(cx + sx + rx, y1, cz + sz + rz, railing, back, "toggle", String.valueOf(rightOf(back) == run),
						"style", railStyle);
			for (int k = 1; k <= 3; k++) {
				chunk.setBlock(cx + k * rx, y1 + k, cz + k * rz, tread, run);
				if (railing != null)
					chunk.setBlock(cx + k * rx + sx, y1 + k, cz + k * rz + sz, railing, run, "toggle",
							String.valueOf(rightOf(run) == side), "style", railStyle);
				if (balcony != null && chunk.isEmpty(cx + k * rx + sx, y1 + 4, cz + k * rz + sz))
					chunk.setBlock(cx + k * rx + sx, y1 + 4, cz + k * rz + sz, balcony, null,
							back.name().toLowerCase(java.util.Locale.ROOT), "true", "style", railStyle);
			}
			chunk.setBlock(cx + 4 * rx, y1 + 3, cz + 4 * rz, platform != null ? platform : materialUnderStairs);
		}

		void DrawRailing(RealBlocks chunk) {

			// only if we have found ourselves
			if (located) {

				// north and south ones
				chunk.setEmptyBlocks(x1 + 1, x2, y2 + 1, z1, z1 + 1, fence, BlockFace.EAST, BlockFace.WEST);
				chunk.setEmptyBlocks(x1 + 1, x2, y2 + 1, z2, z2 + 1, fence, BlockFace.EAST, BlockFace.WEST);

				// west and east ones
				chunk.setEmptyBlocks(x1, x1 + 1, y2 + 1, z1 + 1, z2, fence, BlockFace.NORTH, BlockFace.SOUTH);
				chunk.setEmptyBlocks(x2, x2 + 1, y2 + 1, z1 + 1, z2, fence, BlockFace.NORTH, BlockFace.SOUTH);

				// corners
				chunk.setEmptyBlock(x1, y2 + 1, z1, fence, BlockFace.SOUTH, BlockFace.EAST);
				chunk.setEmptyBlock(x1, y2 + 1, z2, fence, BlockFace.NORTH, BlockFace.EAST);
				chunk.setEmptyBlock(x2, y2 + 1, z1, fence, BlockFace.SOUTH, BlockFace.WEST);
				chunk.setEmptyBlock(x2, y2 + 1, z2, fence, BlockFace.NORTH, BlockFace.WEST);
			}
		}

		void DrawStyle(CityWorldGenerator generator, RealBlocks chunk, DataContext context, Odds odds,
				int floor, int floors, int x, int z, int roomOffsetX, int roomOffsetZ, int baseY) {

			// which door or halls do we do?
			boolean doorNorth = false;
			boolean doorSouth = false;
			boolean doorWest = false;
			boolean doorEast = false;
			boolean hallNorth = walls;
			boolean hallSouth = walls;
			boolean hallWest = walls;
			boolean hallEast = walls;

			// find ourselves
			Locate(context, floor, floors, x, z, roomOffsetX, roomOffsetZ, baseY);

			// our bits!
			switch (style) {
			case KITCHEN:

				// where is the door?
				if (odds.flipCoin()) {
					doorNorth = !roomSouth;
					doorSouth = roomSouth;
				} else {
					doorWest = !roomEast;
					doorEast = roomEast;
				}

				break;
			case DINING:

				break;
			case ENTRY:

				// where is the door?
				if (floor == 0) {
					if (odds.flipCoin()) {
						doorNorth = !roomSouth;
						doorSouth = roomSouth;
					} else {
						doorWest = !roomEast;
						doorEast = roomEast;
					}
				}

				// below the top floor: the staircase up, in the corner of the entry that is furthest from
				// the room's open sides, with the hole in the ceiling above it
				if (floor < floors - 1) {
					if (roomEast) {
						if (roomSouth) {
							chunk.setBlocks(x1 + 1, x2, y2, z1 + 1, z1 + 2, materialAir);
							drawStairRun(chunk, x1 + 5, y1, z1 + 1, BlockFace.WEST, BlockFace.SOUTH);
						} else {
							chunk.setBlocks(x1 + 1, x1 + 2, y2, z1 + 1, z2, materialAir);
							drawStairRun(chunk, x1 + 1, y1, z2 - 5, BlockFace.SOUTH, BlockFace.EAST);
						}
					} else {
						if (roomSouth) {
							chunk.setBlocks(x2 - 1, x2, y2, z1 + 1, z2, materialAir);
							drawStairRun(chunk, x2 - 1, y1, z1 + 5, BlockFace.NORTH, BlockFace.WEST);
						} else {
							chunk.setBlocks(x1 + 1, x2, y2, z2 - 1, z2, materialAir);
							drawStairRun(chunk, x2 - 5, y1, z2 - 1, BlockFace.EAST, BlockFace.NORTH);
						}
					}
				}

				// above the bottom floor
				if (floor > 0) {
					if (roomEast) {
						if (roomSouth) {
							hallNorth = false;

						} else {
							hallWest = false;

						}
					} else {
						if (roomSouth) {
							hallEast = false;

						} else {
							hallSouth = false;

						}
					}
				}

				// the top floor
				if (floor == floors - 1) {
					if (roomEast) {
						if (roomSouth) {
							chunk.setLadder(x1 + 1, y1, y1 + 3, z1 + 1, BlockFace.SOUTH); // fixed
							chunk.setBlock(x1 + 1, y2, z1 + 1, trapDoor, BlockFace.NORTH);

						} else {
							chunk.setLadder(x1 + 1, y1, y1 + 3, z2 - 1, BlockFace.EAST); // fixed
							chunk.setBlock(x1 + 1, y2, z2 - 1, trapDoor, BlockFace.WEST);

						}
					} else {
						if (roomSouth) {
							chunk.setLadder(x2 - 1, y1, y1 + 3, z1 + 1, BlockFace.WEST); // fixed
							chunk.setBlock(x2 - 1, y2, z1 + 1, trapDoor, BlockFace.EAST);

						} else {
							chunk.setLadder(x2 - 1, y1, y1 + 3, z2 - 1, BlockFace.NORTH); // fixed
							chunk.setBlock(x2 - 1, y2, z2 - 1, trapDoor, BlockFace.SOUTH);

						}
					}
				}


				break;
			}

			// draw the walls
			if (roomEast) {
				if (roomSouth) {
					if (doorSouth)
						chunk.setDoor(x1 + 3, y1, z2, door, BlockFace.SOUTH_SOUTH_EAST);
					if (doorEast)
						chunk.setDoor(x2, y1, z1 + 3, door, BlockFace.EAST_SOUTH_EAST);

					if (hallNorth)
						chunk.setDoor(x1 + 2, y1, z1, interiorDoor, BlockFace.NORTH_NORTH_WEST);
					if (hallWest)
						chunk.setDoor(x1, y1, z1 + 2, interiorDoor, BlockFace.WEST_NORTH_WEST);

				} else {
					if (doorNorth)
						chunk.setDoor(x1 + 3, y1, z1, door, BlockFace.NORTH_NORTH_EAST);
					if (doorEast)
						chunk.setDoor(x2, y1, z2 - 3, door, BlockFace.EAST_NORTH_EAST);

					if (hallSouth)
						chunk.setDoor(x1 + 2, y1, z2, interiorDoor, BlockFace.SOUTH_SOUTH_WEST);
					if (hallWest)
						chunk.setDoor(x1, y1, z2 - 2, interiorDoor, BlockFace.WEST_SOUTH_WEST);

				}
			} else {
				if (roomSouth) {
					if (doorSouth)
						chunk.setDoor(x2 - 3, y1, z2, door, BlockFace.SOUTH_SOUTH_WEST);
					if (doorWest)
						chunk.setDoor(x1, y1, z1 + 3, door, BlockFace.WEST_SOUTH_WEST);

					if (hallNorth)
						chunk.setDoor(x2 - 2, y1, z1, interiorDoor, BlockFace.NORTH_NORTH_EAST);
					if (hallEast)
						chunk.setDoor(x2, y1, z1 + 2, interiorDoor, BlockFace.EAST_NORTH_EAST);

				} else {
					if (doorNorth)
						chunk.setDoor(x2 - 3, y1, z1, door, BlockFace.NORTH_NORTH_WEST);
					if (doorWest)
						chunk.setDoor(x1, y1, z2 - 3, door, BlockFace.WEST_NORTH_WEST);

					if (hallSouth)
						chunk.setDoor(x2 - 2, y1, z2, interiorDoor, BlockFace.SOUTH_SOUTH_EAST);
					if (hallEast)
						chunk.setDoor(x2, y1, z2 - 2, interiorDoor, BlockFace.EAST_SOUTH_EAST);
				}
			}

			// Furnish AFTER the walls and doors exist — this ordering is load-bearing. Furniture can
			// now refuse doorway approaches (a bed parked in front of a door twice in playtest),
			// wall art has real interior walls to hang on (before this there were none yet, which is
			// why no painting ever appeared), and the bed/bath "wall nearest the chunk edge" trick
			// becomes a preference rather than the only defence.
			switch (style) {
			case KITCHEN:
				me.daddychurchill.CityWorld.Support.Furniture.kitchen(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			case DINING:
				me.daddychurchill.CityWorld.Support.Furniture.dining(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			case ENTRY:
				// ground floor: the living area you walk into; upper floors: the LANDING (console,
				// rug, wall decor), with clearFloor keeping everything off the stair opening
				if (floor == 0)
					me.daddychurchill.CityWorld.Support.Furniture.living(generator, chunk, odds, x1, x2, y1, z1, z2);
				else
					me.daddychurchill.CityWorld.Support.Furniture.hallway(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			case LIVING:
				// some become a study with a furniture mod installed; study() no-ops without a desk
				if (odds.playOdds(me.daddychurchill.CityWorld.Support.Odds.oddsUnlikely))
					me.daddychurchill.CityWorld.Support.Furniture.study(generator, chunk, odds, x1, x2, y1, z1, z2);
				else
					me.daddychurchill.CityWorld.Support.Furniture.living(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			case BED:
				me.daddychurchill.CityWorld.Support.Furniture.bedroom(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			case BATHROOM:
				me.daddychurchill.CityWorld.Support.Furniture.bathroom(generator, chunk, odds, x1, x2, y1, z1, z2);
				break;
			}
		}
	}

	public void drawWaterTower(CityWorldGenerator generator, RealBlocks chunk, int x, int y, int z, Odds odds) {
		int y1 = y;
		int y2 = y1 + 7;
		int y3 = y2 + 6;

		Material legMat = generator.materialProvider.itemsSelectMaterial_WaterTowers.getRandomMaterial(odds,
				Material.CLAY);
		Material topMat = generator.materialProvider.itemsSelectMaterial_WaterTowers.getRandomMaterial(odds,
				Material.WHITE_TERRACOTTA);
		Material platformMat = generator.materialProvider.itemsSelectMaterial_WaterTowers.getRandomMaterial(odds,
				topMat);

		chunk.setBlocks(x, y1, y3, z + 1, legMat);
		chunk.setBlocks(x + 1, y1, y3, z, legMat);

		chunk.setBlocks(x + 6, y1, y3, z, legMat);
		chunk.setBlocks(x + 7, y1, y3, z + 1, legMat);

		chunk.setBlocks(x, y1, y3, z + 6, legMat);
		chunk.setBlocks(x + 1, y1, y3, z + 7, legMat);

		chunk.setBlocks(x + 7, y1, y3, z + 6, legMat);
		chunk.setBlocks(x + 6, y1, y3, z + 7, legMat);

		chunk.setCircle(x + 4, x + 4, 3, y3 - 1, topMat, true);
		chunk.setCircle(x + 4, x + 4, 5, y3, platformMat, true);
		chunk.setCircle(x + 4, x + 4, 4, y3 + 1, y3 + 5, topMat, false);
		chunk.setCircle(x + 4, x + 4, 4, y3 + 5, topMat, true);
		chunk.setCircle(x + 4, x + 4, 3, y3 + 6, topMat, true);

		if (generator.getSettings().includeAbovegroundFluids) {
			// fill most of the tank, not a puddle at the bottom — 1-3 layers in a 5-tall tank was
			// invisible from the rim and read as an empty tower (playtested)
			chunk.setCircle(x + 4, x + 4, 3, y3 + 1, y3 + 4 + odds.getRandomInt(2),
					generator.oreProvider.fluidFluidMaterial, true);
		}
	}
}
