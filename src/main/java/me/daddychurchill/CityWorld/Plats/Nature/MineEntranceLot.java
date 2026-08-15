package me.daddychurchill.CityWorld.Plats.Nature;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.ConstructLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.SupportBlocks;
import me.daddychurchill.CityWorld.compat.Block;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;

public class MineEntranceLot extends ConstructLot {

	public MineEntranceLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);

	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new MineEntranceLot(platmap, chunkX, chunkZ);
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {

	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return 0;
	}

//	@Override
//	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
//		return blockYs.getBlockY(x, z);// + generator.landRange;
////		return generator.streetLevel + DataContext.FloorHeight;
//	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		generator.reportLocation("mineentrance", "Mine Entrance", chunk);

		// find the bottom of the world
		int shaftY = findHighestShaftableLevel(generator, context, chunk);
		shaftY = Math.max(2, shaftY); // make sure we don't go down too far
		int y = shaftY;
		int topY = blockYs.getMaxYWithin(0, 7, 0, 7) - 2;

		// spiral up, remembering where the mouth surfaced so we can sign it
		int mouthX = 3, mouthZ = 0;
		while (y <= topY) {
			y = makeSpace(generator, chunk, chunkOdds, 4, y, 3);
			mouthX = 4;
			mouthZ = 3;
			if (y > topY)
				break;
			y = makeSpace(generator, chunk, chunkOdds, 1, y, 4);
			mouthX = 1;
			mouthZ = 4;
			if (y > topY)
				break;
			y = makeSpace(generator, chunk, chunkOdds, 0, y, 1);
			mouthX = 0;
			mouthZ = 1;
			if (y > topY)
				break;
			y = makeSpace(generator, chunk, chunkOdds, 3, y, 0);
			mouthX = 3;
			mouthZ = 0;
		}

		// place snow
		generateSurface(generator, chunk, false);

		// a named headframe over the mouth — a gallows beam on two posts with the mine's name hung beneath
		generateMineHeadframe(generator, chunk, mouthX, mouthZ);
	}

	// Old-timey mine-name parts. Combined into "<prefix> <noun>" (e.g. "Sable's Gorge", "Widow's Lode").
	private final static String[] mineNamePrefix = { "Sable's", "Old", "Deep", "Widow's", "Miner's", "Black",
			"Lost", "Dead Man's", "Copper", "Rusty", "Silver", "Devil's", "Hangman's", "Gould's", "Redvein",
			"Ironjaw", "Coldwater", "Bleak", "Grim", "Gallows" };
	private final static String[] mineNameNoun = { "Gorge", "Deep", "Shaft", "Lode", "Hollow", "Delve", "Drift",
			"Seam", "Reach", "Pit", "Descent", "Cut", "Folly", "Adit", "Vein", "Gully", "Bore", "Prospect",
			"Chasm", "End" };

	private String[] mineName(Odds odds) {
		String prefix = mineNamePrefix[odds.getRandomInt(mineNamePrefix.length)];
		String noun = mineNameNoun[odds.getRandomInt(mineNameNoun.length)];
		return new String[] { prefix, noun, "", "Est. " + (1850 + odds.getRandomInt(49)) };
	}

	// A gallows headframe: two dark-oak posts either side of the mouth, a beam across the top, and a
	// hanging sign with the mine's name swinging beneath it.
	private void generateMineHeadframe(CityWorldGenerator generator, SupportBlocks chunk, int mouthX, int mouthZ) {
		int x1 = mouthX, x2 = mouthX + 3, z = mouthZ;
		int beamY = blockYs.getMaxYWithin(mouthX, mouthX + 3, mouthZ, mouthZ + 3) + 3;

		// posts, grown downward from the beam until they rest on solid ground (no floating, whatever the
		// terrain does at the mouth)
		raiseHeadframePost(chunk, x1, beamY, z);
		raiseHeadframePost(chunk, x2, beamY, z);

		// the beam across the top (a horizontal dark-oak log)
		for (int xx = x1; xx <= x2; xx++)
			chunk.setBlock(xx, beamY, z, Material.DARK_OAK_LOG, BlockFace.EAST);

		// the named sign hanging beneath the beam
		chunk.setSignPost(mouthX + 1, beamY - 1, z, Material.OAK_HANGING_SIGN, BlockFace.NORTH, mineName(chunkOdds));
	}

	private void raiseHeadframePost(SupportBlocks chunk, int x, int beamY, int z) {
		for (int yy = beamY - 1; yy > beamY - 15; yy--) {
			chunk.setBlock(x, yy, z, Material.DARK_OAK_LOG);
			if (isSolidFooting(chunk, x, yy - 1, z))
				break; // rest on real ground — drive on down through ferns, snow layers and half-slabs
		}
	}

	// True only for a full solid cube, so a post punches past plants/snow/slabs to the first block that
	// can actually bear it (rather than perching on a fern or half-slab).
	private boolean isSolidFooting(SupportBlocks chunk, int x, int y, int z) {
		Block below = chunk.getActualBlock(x, y, z);
		return below.getBlockData().isCollisionShapeFullBlock(below.getLevel(), below.getPos());
	}

	private int makeSpace(CityWorldGenerator generator, SupportBlocks chunk, Odds odds, int x, int y, int z) {
		chunk.airoutBlocks(generator, x, x + 3, y, y + 3, z, z + 3);
		generateMineFloor(chunk, x, x + 3, y - 1, z, z + 3);
		generateMineCeiling(chunk, x, x + 3, y + 2, z, z + 3);
		return y + 1;
	}
}
