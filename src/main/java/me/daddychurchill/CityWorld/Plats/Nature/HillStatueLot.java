package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

/**
 * A monument on a hill overlooking the city — Ed's "TODO statue overlooking the city?". Placed by
 * {@code NatureContext.placeHillStatue} on a district's highest natural chunk. A stepped stone plinth on the highest
 * ground under it, its footing built down to the ground where the hill falls away, and one of five monuments on it.
 *
 * <p>Every monument is drawn for the plinth's EVEN width (centred on the seam between cells 7 and 8) and is at least
 * two blocks deep: the first version stood the city statue here, which is five wide and one deep, so it sat off
 * centre and read as a cut-out (owner's playtest, 2026-10-01).
 */
public class HillStatueLot extends ConstructLot {

	private enum Design {
		TORCH_BEARER, OBELISK, ARCH, GLOBE, SWORD
	}

	public HillStatueLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
		trulyIsolated = true;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new HillStatueLot(platmap, chunkX, chunkZ);
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return blockYs.getMaxHeight();
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
	}

	private RealBlocks chunk;
	private boolean turned; // drawn along z instead of x

	/** One block of the monument, in its own frame: {@code a} across, {@code c} deep. */
	private void put(int a, int y, int c, Material material) {
		if (turned)
			chunk.setBlock(c, y, a, material);
		else
			chunk.setBlock(a, y, c, material);
	}

	private void box(int a1, int a2, int y1, int y2, int c1, int c2, Material material) {
		for (int a = a1; a <= a2; a++)
			for (int y = y1; y <= y2; y++)
				for (int c = c1; c <= c2; c++)
					put(a, y, c, material);
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		generateSurface(generator, chunk, false);
		this.chunk = chunk;
		this.turned = chunkOdds.flipCoin();
		Design design = Design.values()[chunkOdds.getRandomInt(Design.values().length)];

		// the plinth stands on the highest ground under it, its footing filled down to the ground elsewhere
		int top = Integer.MIN_VALUE;
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++)
				top = Math.max(top, blockYs.getBlockY(x, z));
		for (int x = 4; x <= 11; x++)
			for (int z = 4; z <= 11; z++) {
				chunk.setBlocks(x, blockYs.getBlockY(x, z) + 1, top + 1, z, Material.STONE_BRICKS);
				chunk.clearBlocks(x, top + 1, top + 16, z);
			}
		chunk.setBlocks(4, 12, top + 1, 4, 12, Material.of(net.minecraft.world.level.block.Blocks.STONE_BRICK_SLAB));
		chunk.setBlocks(5, 11, top + 1, 5, 11, Material.CHISELED_STONE_BRICKS);
		// the pedestal: four wide, or six for the arch
		int inset = design == Design.ARCH ? 5 : 6;
		chunk.setBlocks(inset, 16 - inset, top + 2, inset, 16 - inset, Material.POLISHED_ANDESITE);

		Material[] stones = { Material.QUARTZ_BLOCK, Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER,
				Material.POLISHED_DIORITE };
		Material stone = stones[chunkOdds.getRandomInt(stones.length)];
		int y = top + 3;
		String name;
		switch (design) {
		case TORCH_BEARER: {
			// a robed figure, one arm raised with a light in its hand (either arm)
			int raised = chunkOdds.flipCoin() ? 9 : 6, lowered = raised == 9 ? 6 : 9;
			box(6, 9, y, y + 1, 7, 8, stone); // the robe's skirt
			box(7, 8, y + 2, y + 6, 7, 8, stone); // body
			box(lowered, lowered, y + 4, y + 6, 7, 8, stone); // the arm at its side
			box(raised, raised, y + 6, y + 9, 7, 8, stone); // the raised arm
			box(7, 8, y + 7, y + 8, 7, 8, stone); // head
			box(raised, raised, y + 10, y + 10, 7, 8, Material.GLOWSTONE);
			name = "Torch Bearer";
			break;
		}
		case OBELISK:
			box(6, 9, y, y, 6, 9, stone);
			box(7, 8, y + 1, y + 9, 7, 8, stone);
			box(7, 8, y + 10, y + 10, 7, 8, Material.GOLD_BLOCK);
			name = "Obelisk";
			break;
		case ARCH:
			box(5, 6, y, y + 5, 7, 8, stone);
			box(9, 10, y, y + 5, 7, 8, stone);
			box(5, 10, y + 6, y + 7, 7, 8, stone);
			box(6, 9, y + 8, y + 8, 7, 8, stone);
			name = "Memorial Arch";
			break;
		case GLOBE:
			box(7, 8, y, y + 1, 7, 8, Material.POLISHED_ANDESITE);
			for (int a = 6; a <= 9; a++)
				for (int h = 0; h < 4; h++)
					for (int c = 6; c <= 9; c++) {
						int edges = (a == 6 || a == 9 ? 1 : 0) + (h == 0 || h == 3 ? 1 : 0) + (c == 6 || c == 9 ? 1 : 0);
						if (edges < 3) // the eight corners off: a ball
							put(a, y + 2 + h, c, stone);
					}
			name = "Globe";
			break;
		default: // SWORD, point down in the stone
			box(7, 8, y, y + 6, 7, 8, Material.IRON_BLOCK);
			box(5, 10, y + 7, y + 7, 7, 8, Material.GOLD_BLOCK);
			box(7, 8, y + 8, y + 9, 7, 8, Material.POLISHED_ANDESITE);
			box(7, 8, y + 10, y + 10, 7, 8, Material.GOLD_BLOCK);
			name = "Sword Monument";
			break;
		}
		generator.reportLocation("statue", name, chunk);
		this.chunk = null;
	}
}
