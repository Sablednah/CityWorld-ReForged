package me.daddychurchill.CityWorld.Plugins;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;
import net.minecraft.world.level.block.state.properties.Half;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractBlocks;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

public class ThingProvider extends Provider {

	private ThingProvider() {
		// TODO Auto-generated constructor stub
	}

	public static ThingProvider loadProvider(CityWorldGenerator generator) {
		// for now
		return new ThingProvider();
	}

	private static final byte NON = 0;
	private static final byte WHT = 1;
	private static final byte YEL = 2;
	private static final byte RED = 3;
	private static final byte BLK = 4;

	private final byte[][] chickenFeet = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 2
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 3
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 4
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 5
			{ NON, YEL, YEL, YEL, YEL, YEL, YEL, NON }, // 6
			{ NON, YEL, YEL, YEL, YEL, YEL, YEL, NON }, // 7
			{ NON, NON, YEL, NON, NON, YEL, NON, NON }, // 8
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 9
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 10
			{ NON, NON, NON, NON, NON, NON, NON, NON } // 11
	};

	private final byte[][] chickenLegs = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 2
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 3
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 4
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 5
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 6
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 7
			{ NON, NON, YEL, NON, NON, YEL, NON, NON }, // 8
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 9
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 10
			{ NON, NON, NON, NON, NON, NON, NON, NON } // 11
	};

	private final byte[][] chickenUnder = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 2
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 3
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 4
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 5
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 6
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 7
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 8
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 9
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 10
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON } // 11
	};

	private final byte[][] chickenBody = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 2
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 3
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 4
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 5
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 6
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 7
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 8
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 9
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 10
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON } // 11
	};

	private final byte[][] chickenMouth = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, RED, RED, NON, NON, NON }, // 1
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 2
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 3
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON }, // 4
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 5
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 6
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 7
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 8
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 9
			{ WHT, WHT, WHT, WHT, WHT, WHT, WHT, WHT }, // 10
			{ NON, WHT, WHT, WHT, WHT, WHT, WHT, NON } // 11
	};

	private final byte[][] chickenHead = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, YEL, YEL, NON, NON, NON }, // 0
			{ NON, NON, NON, YEL, YEL, NON, NON, NON }, // 1
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 2
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 3
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 4
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 5
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 6
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 8
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 9
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 10
			{ NON, NON, NON, NON, NON, NON, NON, NON } // 11
	};

	private final byte[][] chickenEyes = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, BLK, WHT, WHT, BLK, NON, NON }, // 2
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 3
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 4
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 5
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 6
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 8
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 9
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 10
			{ NON, NON, NON, NON, NON, NON, NON, NON } // 11
	};

	private final byte[][] chickenTops = new byte[][] {
			// 0 1 2 3 4 5 6 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 0
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 1
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 2
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 3
			{ NON, NON, WHT, WHT, WHT, WHT, NON, NON }, // 4
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 5
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 6
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 7
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 8
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 9
			{ NON, NON, NON, NON, NON, NON, NON, NON }, // 10
			{ NON, NON, NON, NON, NON, NON, NON, NON } // 11
	};

	private void generateLayer(AbstractBlocks chunk, int x, int y, int z, byte[][] layer) {
		for (int xI = 0; xI < 12; xI++) {
			for (int zI = 0; zI < 8; zI++) {
				switch (layer[xI][zI]) {
				default:
				case NON:
					break;
				case WHT:
//					if (cI % 2 == 0)
					chunk.setBlock(x + xI, y, z + zI, Material.WHITE_WOOL);
//					else
//						chunk.setBlock(x + xI, y, z + zI, Material.WHITE_WOOL, DyeColor.LIGHT_GRAY);
					break;
				case YEL:
					chunk.setBlock(x + xI, y, z + zI, Material.YELLOW_WOOL);
					break;
				case RED:
					chunk.setBlock(x + xI, y, z + zI, Material.RED_WOOL);
					break;
				case BLK:
					chunk.setBlock(x + xI, y, z + zI, Material.BLACK_WOOL);
					break;
				}
			}
		}
	}

	public void generateChicken(AbstractBlocks chunk, int x, int y, int z) {
		generateLayer(chunk, x, y, z, chickenFeet);
		generateLayer(chunk, x, y + 1, z, chickenLegs);
		generateLayer(chunk, x, y + 2, z, chickenLegs);
		generateLayer(chunk, x, y + 3, z, chickenLegs);
		generateLayer(chunk, x, y + 4, z, chickenLegs);
		generateLayer(chunk, x, y + 5, z, chickenUnder);
		generateLayer(chunk, x, y + 6, z, chickenUnder);
		generateLayer(chunk, x, y + 7, z, chickenBody);
		generateLayer(chunk, x, y + 8, z, chickenBody);
		generateLayer(chunk, x, y + 9, z, chickenMouth);
		generateLayer(chunk, x, y + 10, z, chickenMouth);
		generateLayer(chunk, x, y + 11, z, chickenHead);
		generateLayer(chunk, x, y + 12, z, chickenHead);
		generateLayer(chunk, x, y + 13, z, chickenEyes);
		generateLayer(chunk, x, y + 14, z, chickenTops);
	}

	/** A fossil buried in the rock under {@code blockYs}: one of the {@link me.daddychurchill.CityWorld.Support.Fossils}. */
	public void generateBones(CityWorldGenerator generator, PlatLot lot, SupportBlocks chunk, AbstractCachedYs blockYs,
			Odds odds) {
		int y = odds.calcRandomRange(10, blockYs.getMinHeight() - 20);
		me.daddychurchill.CityWorld.Support.Fossils.draw(chunk, odds, y);
	}
}
