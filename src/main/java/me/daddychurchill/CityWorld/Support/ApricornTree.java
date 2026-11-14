package me.daddychurchill.CityWorld.Support;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.core.Holder;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.feature.configurations.BlockStateConfiguration;
import net.minecraft.world.level.levelgen.feature.configurations.SimpleRandomFeatureConfiguration;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/**
 * Cobblemon's apricorn tree, drawn by CityWorld. Its own feature ({@code cobblemon:apricorn_tree}) refuses to place
 * while the chunk is still generating unless the biome sits in one of its {@code has_feature/apricorns_*} tags, and
 * then rolls its {@code baseApricornTreeGenerationChance} (0.1) once more — so an orchard that drew an apricorn from
 * {@code #cityworld:orchard/<climate>} grew one about never (0 apricorn logs in eight temperate orchards, the 1.21.1
 * self-test, 2026-11-14) and fell back to the classic oak orchard. This is a transcription of
 * {@code ApricornTreeFeature.place} (Cobblemon 1.8.4): a five-log trunk, the four-high leaf collar round it, the
 * two-high ring two out at the second storey with the fruit hanging off it, a topper and two or three leaf tufts
 * at the fourth storey, up to eight apricorns of random ripeness. Leaves carry a computed distance like the
 * original's, so they neither decay nor need to be persistent.
 */
public final class ApricornTree {

	private ApricornTree() {
	}

	private static final BlockFace[] SIDES = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

	private static boolean isApricornFeature(Holder<ConfiguredFeature<?, ?>> feature) {
		return feature.value().feature().getClass().getName().endsWith("ApricornTreeFeature")
				&& feature.value().config() instanceof BlockStateConfiguration;
	}

	/**
	 * The apricorn (fruit block state) a pooled configured feature would grow, or {@code null} when the feature is
	 * not Cobblemon's apricorn tree: the tree feature itself, or a {@code simple_random_selector} of them
	 * ({@code cobblemon:apricorn_trees}), in which case one of its members is drawn with {@code odds}.
	 */
	public static BlockState fruitFor(Holder<ConfiguredFeature<?, ?>> pick, Odds odds) {
		if (isApricornFeature(pick))
			return ((BlockStateConfiguration) pick.value().config()).state;
		if (pick.value().config() instanceof SimpleRandomFeatureConfiguration selector) {
			List<BlockState> fruits = new ArrayList<>();
			for (Holder<PlacedFeature> placed : selector.features)
				if (isApricornFeature(placed.value().feature()))
					fruits.add(((BlockStateConfiguration) placed.value().feature().value().config()).state);
			if (!fruits.isEmpty())
				return fruits.get(odds.getRandomInt(fruits.size()));
		}
		return null;
	}

	/** The apricorn log and leaves, when Cobblemon is here. */
	private static Material block(String id) {
		Material m = Material.of(id);
		return m != null && m.isBlock() && m.getBlock() != Blocks.AIR ? m : null;
	}

	/**
	 * Grows one at {@code (x, y, z)} (chunk-local x/z, {@code y} the trunk's lowest log). The crown reaches two
	 * cells out and the fruit three; a trunk two in from the chunk's edge keeps every block inside it.
	 */
	public static boolean plant(SupportBlocks chunk, Odds odds, int x, int y, int z, BlockState fruit) {
		Material log = block("cobblemon:apricorn_log"), leaves = block("cobblemon:apricorn_leaves");
		if (log == null || leaves == null || fruit == null)
			return false;
		if (!chunk.isEmpty(x, y, z))
			return false;

		// everything in tree-local offsets first, then written with leaf distances computed from the trunk
		List<int[]> logs = new ArrayList<>(), leaf = new ArrayList<>();
		List<List<int[]>> spots = new ArrayList<>(); // each: {dx, dy, dz, facing index}
		for (int dy = 0; dy <= 4; dy++)
			logs.add(new int[] { 0, dy, 0 });

		// the collar: the four sides and four diagonals, storeys 1..4, plus two extra cells one side at storey 1
		for (BlockFace d : SIDES)
			for (int dy = 1; dy <= 4; dy++)
				leaf.add(new int[] { d.getModX(), dy, d.getModZ() });
		{
			BlockFace d = SIDES[odds.getRandomInt(4)];
			int ox = d.getModX() * 2, oz = d.getModZ() * 2, side = odds.flipCoin() ? -1 : 1;
			leaf.add(new int[] { ox, 1, oz });
			leaf.add(d.getModX() == 0 ? new int[] { ox + side, 1, oz } : new int[] { ox, 1, oz + side });
		}
		for (int[] c : new int[][] { { 1, 1 }, { -1, -1 }, { 1, -1 }, { -1, 1 } })
			for (int dy = 1; dy <= 4; dy++)
				leaf.add(new int[] { c[0], dy, c[1] });

		// the ring two out at storeys 2 and 3, and the fruit that hangs off it
		for (int i = 0; i < 4; i++) {
			BlockFace d = SIDES[i];
			List<int[]> group = new ArrayList<>();
			for (int dy = 2; dy <= 3; dy++) {
				leaf.add(new int[] { d.getModX() * 2, dy, d.getModZ() * 2 });
				group.add(new int[] { d.getModX() * 3, dy, d.getModZ() * 3, (i + 2) % 4 }); // faces back at its leaf
			}
			spots.add(group);
		}
		for (int[] c : new int[][] { { 1, 2 }, { -1, 2 }, { 1, -2 }, { -2, 1 }, { 2, 1 }, { -2, -1 }, { -1, -2 }, { 2, -1 } }) {
			List<int[]> group = new ArrayList<>();
			for (int dy = 2; dy <= 3; dy++) {
				leaf.add(new int[] { c[0], dy, c[1] });
				for (int i = 0; i < 4; i++)
					group.add(new int[] { c[0] + SIDES[i].getModX(), dy, c[1] + SIDES[i].getModZ(), (i + 2) % 4 });
			}
			spots.add(group);
		}

		// the topper and the tufts at storey 4
		leaf.add(new int[] { 0, 5, 0 });
		for (BlockFace d : SIDES)
			leaf.add(new int[] { d.getModX(), 5, d.getModZ() });
		int tufts = 2 + odds.getRandomInt(2);
		boolean[] used = new boolean[4];
		for (int t = 0; t < tufts; t++) {
			int i;
			do
				i = odds.getRandomInt(4);
			while (used[i]);
			used[i] = true;
			BlockFace d = SIDES[i];
			int ox = d.getModX() * 2, oz = d.getModZ() * 2, side = odds.flipCoin() ? -1 : 1;
			int[] one = { ox, 4, oz }, two = d.getModX() == 0 ? new int[] { ox + side, 4, oz } : new int[] { ox, 4, oz + side };
			if (odds.getRandomInt(3) == 0) {
				leaf.add(one);
				leaf.add(two);
			} else
				leaf.add(odds.flipCoin() ? one : two);
		}

		// write: logs, then leaves where the cell is clear, with the original's leaf distance (steps to a log)
		for (int[] p : logs)
			chunk.setBlock(x + p[0], y + p[1], z + p[2], log);
		java.util.Set<Long> placed = new java.util.HashSet<>();
		List<int[]> kept = new ArrayList<>();
		for (int[] p : leaf) {
			long key = key(p);
			if (placed.contains(key) || !chunk.isEmpty(x + p[0], y + p[1], z + p[2]))
				continue;
			placed.add(key);
			kept.add(p);
		}
		BlockState leafState = leaves.getBlock().defaultBlockState();
		for (int[] p : kept) {
			int distance = Math.min(7, distance(p, logs, kept));
			BlockState s = leafState.setValue(BlockStateProperties.PERSISTENT, false);
			if (s.hasProperty(BlockStateProperties.DISTANCE))
				s = s.setValue(BlockStateProperties.DISTANCE, distance);
			chunk.setBlockState(x + p[0], y + p[1], z + p[2], s);
		}

		// up to eight apricorns, one per group, each hung only where its leaf stands and the cell is clear
		List<List<int[]>> groups = new ArrayList<>(spots);
		int fruits = 0;
		while (!groups.isEmpty() && fruits < 8) {
			List<int[]> group = groups.remove(odds.getRandomInt(groups.size()));
			int[] s = group.get(odds.getRandomInt(group.size()));
			BlockFace face = SIDES[s[3]];
			int lx = s[0] + face.getModX(), ly = s[1], lz = s[2] + face.getModZ();
			if (!placed.contains(key(new int[] { lx, ly, lz })) || !chunk.isEmpty(x + s[0], y + s[1], z + s[2]))
				continue;
			BlockState state = fruit;
			if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
				state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, face.toDirection());
			if (state.hasProperty(BlockStateProperties.AGE_3))
				state = state.setValue(BlockStateProperties.AGE_3, odds.getRandomInt(4));
			chunk.setBlockState(x + s[0], y + s[1], z + s[2], state);
			fruits++;
		}
		return true;
	}

	private static long key(int[] p) {
		return ((long) (p[0] + 8) << 16) | ((long) (p[1] + 8) << 8) | (p[2] + 8);
	}

	/** Steps from a log through six-neighbour leaves, the way {@code LeavesBlock.updateDistance} counts. */
	private static int distance(int[] p, List<int[]> logs, List<int[]> leaves) {
		int best = 7;
		for (int[] l : logs)
			best = Math.min(best, Math.abs(p[0] - l[0]) + Math.abs(p[1] - l[1]) + Math.abs(p[2] - l[2]));
		return best;
	}
}
