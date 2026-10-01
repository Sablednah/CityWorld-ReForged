package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.Material;

/**
 * The monuments that stand on a plinth: on a hill overlooking the city ({@code HillStatueLot}) and on the pedestal of
 * a government monument. Every one is drawn for an EVEN-width plinth (centred on the seam between cells 7 and 8, the
 * middle of a chunk) and is at least two blocks deep. The statue they replace was five wide and one deep, so it sat
 * off centre on anything even and read as a cut-out (owner's playtest, 2026-10-01).
 */
public final class Monuments {

	private Monuments() {
	}

	public enum Design {
		TORCH_BEARER("Torch Bearer"), OBELISK("Obelisk"), ARCH("Memorial Arch"), GLOBE("Globe"),
		SWORD("Sword Monument");

		public final String title;

		Design(String title) {
			this.title = title;
		}

		/** How wide a flat top the design stands on: the arch's pillars are six apart, the rest fit on four. */
		public int footprint() {
			return this == ARCH ? 6 : 4;
		}
	}

	/** Places one block of a monument, in the monument's own frame: {@code a} across, {@code c} deep. */
	public interface Setter {
		void set(int a, int y, int c, Material material);
	}

	private static final Material[] STONES = { Material.QUARTZ_BLOCK, Material.WEATHERED_CUT_COPPER,
			Material.OXIDIZED_CUT_COPPER, Material.POLISHED_DIORITE };

	public static Design pick(Odds odds) {
		return Design.values()[odds.getRandomInt(Design.values().length)];
	}

	public static Material pickStone(Odds odds) {
		return STONES[odds.getRandomInt(STONES.length)];
	}

	private static void box(Setter put, int a1, int a2, int y1, int y2, int c1, int c2, Material material) {
		for (int a = a1; a <= a2; a++)
			for (int y = y1; y <= y2; y++)
				for (int c = c1; c <= c2; c++)
					put.set(a, y, c, material);
	}

	/** Draw {@code design} with its lowest block at {@code y}, centred on the seam between cells 7 and 8. */
	public static void draw(Setter put, Design design, int y, Material stone, Odds odds) {
		switch (design) {
		case TORCH_BEARER: {
			// a robed figure, one arm raised with a light in its hand (either arm)
			int raised = odds.flipCoin() ? 9 : 6, lowered = raised == 9 ? 6 : 9;
			box(put, 6, 9, y, y + 1, 7, 8, stone); // the robe's skirt
			box(put, 7, 8, y + 2, y + 6, 7, 8, stone); // body
			box(put, lowered, lowered, y + 4, y + 6, 7, 8, stone); // the arm at its side
			box(put, raised, raised, y + 6, y + 9, 7, 8, stone); // the raised arm
			box(put, 7, 8, y + 7, y + 8, 7, 8, stone); // head
			box(put, raised, raised, y + 10, y + 10, 7, 8, Material.GLOWSTONE);
			break;
		}
		case OBELISK:
			box(put, 6, 9, y, y, 6, 9, stone);
			box(put, 7, 8, y + 1, y + 9, 7, 8, stone);
			box(put, 7, 8, y + 10, y + 10, 7, 8, Material.GOLD_BLOCK);
			break;
		case ARCH:
			box(put, 5, 6, y, y + 5, 7, 8, stone);
			box(put, 9, 10, y, y + 5, 7, 8, stone);
			box(put, 5, 10, y + 6, y + 7, 7, 8, stone);
			box(put, 6, 9, y + 8, y + 8, 7, 8, stone);
			break;
		case GLOBE:
			box(put, 7, 8, y, y + 1, 7, 8, Material.POLISHED_ANDESITE);
			for (int a = 6; a <= 9; a++)
				for (int h = 0; h < 4; h++)
					for (int c = 6; c <= 9; c++) {
						int edges = (a == 6 || a == 9 ? 1 : 0) + (h == 0 || h == 3 ? 1 : 0) + (c == 6 || c == 9 ? 1 : 0);
						if (edges < 3) // the eight corners off: a ball
							put.set(a, y + 2 + h, c, stone);
					}
			break;
		default: // SWORD, point down in the stone
			box(put, 7, 8, y, y + 6, 7, 8, Material.IRON_BLOCK);
			box(put, 5, 10, y + 7, y + 7, 7, 8, Material.GOLD_BLOCK);
			box(put, 7, 8, y + 8, y + 9, 7, 8, Material.POLISHED_ANDESITE);
			box(put, 7, 8, y + 10, y + 10, 7, 8, Material.GOLD_BLOCK);
			break;
		}
	}
}
