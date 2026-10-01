package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.properties.Half;

/**
 * Fossil skeletons: a museum's centrepiece, and the bones buried in the rock. Four animals drawn by hand, replacing
 * the creature assembled from random limb counts (which could come out as a pair of legs with a head).
 *
 * <p>Each is drawn TWO blocks wide down the middle (cells 7 and 8), so it stands centred on a museum's even-width
 * floor and has some bulk, and fits a box ten long (z 3..12, head to the north), eight wide and eleven tall. Bone
 * blocks lie along the bone they are part of; ribs, horns and tusks are quartz stairs and diorite wall posts.
 */
public final class Fossils {

	private Fossils() {
	}

	public enum Species {
		THEROPOD("Tyrant lizard"), SAUROPOD("Thunder lizard"), CERATOPSIAN("Horned face"), MAMMOTH("Mammoth");

		public final String title;

		Species(String title) {
			this.title = title;
		}
	}

	private static final Material STAIR = Material.QUARTZ_STAIRS;
	private static final Material POST = Material.of(Blocks.DIORITE_WALL);

	/** One skeleton, its floor (the first block above the ground) at {@code y}. */
	public static Species draw(SupportBlocks chunk, Odds odds, int y) {
		return draw(chunk, Species.values()[odds.getRandomInt(Species.values().length)], y);
	}

	public static Species draw(SupportBlocks chunk, Species species, int y) {
		new Fossils.Pen(chunk, y).draw(species);
		return species;
	}

	/**
	 * The species for a museum hall at this chunk: by position, stepping one along x and two along z, so no hall
	 * shows the same animal as a hall beside it. (Rolled from each chunk's own odds, four halls in a row came out
	 * the same animal: neighbouring chunks' first rolls are correlated.)
	 */
	public static Species speciesAt(int chunkX, int chunkZ) {
		return Species.values()[Math.floorMod(chunkX + 2 * chunkZ, Species.values().length)];
	}

	/** Draws in mirrored pairs about the seam between cells 7 and 8: {@code k} cells out from it on each side. */
	private static final class Pen {
		private final SupportBlocks chunk;
		private final int y0;

		Pen(SupportBlocks chunk, int y0) {
			this.chunk = chunk;
			this.y0 = y0;
		}

		private void bone(int k, int y, int z, Direction.Axis axis) {
			var state = Blocks.BONE_BLOCK.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
			chunk.setBlockState(7 - k, y0 + y, z, state);
			chunk.setBlockState(8 + k, y0 + y, z, state);
		}

		private void post(int k, int y, int z) {
			chunk.setBlock(7 - k, y0 + y, z, POST);
			chunk.setBlock(8 + k, y0 + y, z, POST);
		}

		/** A stair on each side whose high side is toward the spine. */
		private void stairIn(int k, int y, int z, boolean upsideDown) {
			if (upsideDown) {
				chunk.setBlock(7 - k, y0 + y, z, STAIR, BlockFace.EAST, Half.TOP);
				chunk.setBlock(8 + k, y0 + y, z, STAIR, BlockFace.WEST, Half.TOP);
			} else {
				chunk.setBlock(7 - k, y0 + y, z, STAIR, BlockFace.EAST);
				chunk.setBlock(8 + k, y0 + y, z, STAIR, BlockFace.WEST);
			}
		}

		/** A stair on each side, high side toward {@code high}. */
		private void stair(int k, int y, int z, BlockFace high, boolean upsideDown) {
			for (int x : new int[] { 7 - k, 8 + k })
				if (upsideDown)
					chunk.setBlock(x, y0 + y, z, STAIR, high, Half.TOP);
				else
					chunk.setBlock(x, y0 + y, z, STAIR, high);
		}

		/** The spine from {@code (z1, y)} north to {@code z2}. */
		private void spine(int z1, int z2, int y) {
			for (int z = z2; z <= z1; z++)
				bone(0, y, z, Direction.Axis.Z);
		}

		/** A rib each side at {@code z}, hung from a spine at {@code y}: curving out, down and back in. */
		private void rib(int y, int z, int drop) {
			stairIn(1, y, z, false);
			for (int i = 1; i < drop; i++)
				post(1, y - i, z);
			stairIn(1, y - drop, z, true);
		}

		/** A leg each side at {@code z}, from a hip at {@code top} down to a foot with a toe to the north. */
		private void leg(int k, int top, int z) {
			for (int y = top; y >= 1; y--)
				bone(k, y, z, Direction.Axis.Y);
			bone(k, 0, z, Direction.Axis.Z);
			stair(k, 0, z - 1, BlockFace.SOUTH, false);
		}

		void draw(Species species) {
			switch (species) {
			case THEROPOD:
				theropod();
				break;
			case SAUROPOD:
				sauropod();
				break;
			case CERATOPSIAN:
				ceratopsian();
				break;
			default:
				mammoth();
				break;
			}
		}

		/** Upright on two legs, a long tail behind, a big head with the jaw open. */
		private void theropod() {
			// tail, rising to the hips
			bone(0, 2, 12, Direction.Axis.Z);
			stair(0, 3, 12, BlockFace.NORTH, false);
			bone(0, 3, 11, Direction.Axis.Z);
			stair(0, 4, 11, BlockFace.NORTH, false);
			// hips and legs
			bone(0, 4, 10, Direction.Axis.Z);
			bone(1, 4, 10, Direction.Axis.X);
			leg(1, 3, 10);
			// back, rising to the shoulders
			stair(0, 5, 10, BlockFace.NORTH, false);
			spine(9, 8, 5);
			rib(5, 9, 2);
			rib(5, 8, 2);
			stair(0, 6, 8, BlockFace.NORTH, false);
			bone(0, 6, 7, Direction.Axis.Z);
			post(1, 5, 7); // the little arms
			stair(1, 4, 6, BlockFace.SOUTH, true);
			// neck
			stair(0, 7, 7, BlockFace.NORTH, false);
			bone(0, 7, 6, Direction.Axis.Z);
			// skull: the back of the head, the upper jaw and brow, and the lower jaw hanging open
			bone(0, 6, 5, Direction.Axis.Y);
			bone(0, 7, 5, Direction.Axis.Y);
			bone(0, 8, 5, Direction.Axis.Z);
			bone(0, 9, 5, Direction.Axis.Z);
			bone(0, 8, 4, Direction.Axis.Z);
			bone(0, 9, 4, Direction.Axis.Z);
			bone(0, 8, 3, Direction.Axis.Z);
			stair(0, 9, 3, BlockFace.SOUTH, false);
			bone(0, 6, 4, Direction.Axis.Z);
			stair(0, 6, 3, BlockFace.SOUTH, true);
		}

		/** Four pillar legs, a barrel of ribs, and a neck that goes up and up. */
		private void sauropod() {
			// tail
			bone(0, 3, 12, Direction.Axis.Z);
			stair(0, 4, 12, BlockFace.NORTH, false);
			bone(0, 4, 11, Direction.Axis.Z);
			stair(0, 5, 11, BlockFace.NORTH, false);
			// body
			spine(10, 6, 5);
			bone(1, 5, 10, Direction.Axis.X);
			leg(1, 4, 10);
			bone(1, 5, 6, Direction.Axis.X);
			leg(1, 4, 6);
			rib(5, 9, 3);
			rib(5, 8, 3);
			rib(5, 7, 3);
			// neck
			stair(0, 6, 6, BlockFace.NORTH, false);
			bone(0, 6, 5, Direction.Axis.Z);
			stair(0, 7, 5, BlockFace.NORTH, false);
			bone(0, 7, 4, Direction.Axis.Y);
			bone(0, 8, 4, Direction.Axis.Y);
			bone(0, 9, 4, Direction.Axis.Y);
			// the small head
			bone(0, 10, 4, Direction.Axis.Z);
			bone(0, 10, 3, Direction.Axis.Z);
			stair(0, 9, 3, BlockFace.SOUTH, true);
		}

		/** Low and heavy, with a great frill behind the head and three horns. */
		private void ceratopsian() {
			// tail
			bone(0, 2, 12, Direction.Axis.Z);
			stair(0, 3, 12, BlockFace.NORTH, false);
			// body
			spine(11, 7, 3);
			bone(1, 3, 11, Direction.Axis.X);
			leg(1, 2, 11);
			bone(1, 3, 7, Direction.Axis.X);
			leg(1, 2, 7);
			rib(3, 10, 2);
			rib(3, 9, 2);
			rib(3, 8, 2);
			// the frill: four wide, three tall, its top corners rounded
			for (int y = 3; y <= 5; y++) {
				bone(0, y, 6, Direction.Axis.Y);
				if (y < 5)
					bone(1, y, 6, Direction.Axis.Y);
			}
			stairIn(1, 5, 6, false);
			// the face, sloping down to the beak
			bone(0, 3, 5, Direction.Axis.Z);
			bone(0, 4, 5, Direction.Axis.Z);
			bone(0, 2, 5, Direction.Axis.Z);
			bone(0, 3, 4, Direction.Axis.Z);
			bone(0, 2, 4, Direction.Axis.Z);
			stair(0, 2, 3, BlockFace.SOUTH, false);
			// brow horns, out to the sides, and the nose horn
			post(1, 4, 5);
			post(1, 5, 5);
			post(0, 4, 4);
		}

		/** Tall, with a domed skull and tusks curving down and forward. */
		private void mammoth() {
			// tail
			stair(0, 4, 11, BlockFace.NORTH, true);
			// body
			spine(10, 6, 5);
			bone(1, 5, 10, Direction.Axis.X);
			leg(1, 4, 10);
			bone(1, 5, 6, Direction.Axis.X);
			leg(1, 4, 6);
			rib(5, 9, 3);
			rib(5, 8, 3);
			rib(5, 7, 3);
			// the shoulder hump and the domed skull
			bone(0, 6, 7, Direction.Axis.Z);
			bone(0, 6, 6, Direction.Axis.Z);
			for (int y = 4; y <= 7; y++)
				bone(0, y, 5, Direction.Axis.Y);
			bone(0, 5, 4, Direction.Axis.Y);
			bone(0, 6, 4, Direction.Axis.Y);
			stair(0, 7, 4, BlockFace.SOUTH, false);
			// tusks: down from the jaw, then forward and up at the tip
			post(1, 4, 4);
			post(1, 3, 4);
			stair(1, 2, 4, BlockFace.SOUTH, false);
			stair(1, 2, 3, BlockFace.NORTH, true);
			post(1, 3, 3);
		}
	}
}
