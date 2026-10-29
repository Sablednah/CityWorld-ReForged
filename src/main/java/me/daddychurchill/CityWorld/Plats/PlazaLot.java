package me.daddychurchill.CityWorld.Plats;

import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.MaterialTags;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.worldgen.ReservedSites;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * The core of a reserved site that asked to be paved ({@code CityWorldAPI.reservePlaza}): a town square at the
 * site's level, on which another mod then builds — Modernlife's bank next to a new player's spawn
 * ({@code Support/ModernlifeBank}). The terrain and the levelling are the reserved site's, exactly as for a bare
 * site (this is a {@link NatureLot} to the planner, so a vanilla structure, a road or a tree never lands here); only
 * the top is dressed: paving in a pattern laid out from the site's corner so the chunks join without a seam, a kerb
 * of stone bricks round the edge, street lamps on a 12-block grid with a bench beside each, and a planter at every
 * other crossing of the lines.
 *
 * <p>Nothing here stands more than three blocks above the paving. Modernlife looks for flat land before it builds
 * (the heights at the bank's corners may differ by four), and its paste clears its own box, so whatever the bank
 * lands on simply goes and the rest of the square stays. The look is one pick per site from
 * {@code #cityworld:build/plaza}, so one square is one stone throughout.
 */
public class PlazaLot extends NatureLot {

	/** Lamp posts every this many blocks each way, planters at every other crossing of the paving lines. */
	private static final int GRID = 12;

	public PlazaLot(PlatMap platmap, int chunkX, int chunkZ) {
		super(platmap, chunkX, chunkZ);
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new PlazaLot(platmap, chunkX, chunkZ);
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {
		ReservedSites.Site site = generator.pavedSiteAt(getChunkX(), getChunkZ());
		if (site == null) {
			super.generateActualBlocks(generator, platmap, chunk, context, platX, platZ);
			return;
		}

		// one stone per square: the pool is drawn with the SITE's dice, so every chunk of it agrees
		Odds siteOdds = new Odds(generator.getWorldSeed() ^ (site.centreX() * 341873128L + site.centreZ() * 132897987L));
		List<Material> pool = MaterialTags.resolve(MaterialTags.BUILD_PLAZA);
		Material paving = Material.SMOOTH_STONE, lines = Material.POLISHED_ANDESITE;
		if (!pool.isEmpty()) {
			int first = siteOdds.getRandomInt(pool.size());
			paving = pool.get(first);
			lines = pool.size() > 1 ? pool.get((first + 1 + siteOdds.getRandomInt(pool.size() - 1)) % pool.size()) : paving;
		}
		Material lamp = generator.worldEnvironment == me.daddychurchill.CityWorld.compat.Environment.THE_END ? null
				: MaterialTags.pick(MaterialTags.LIGHT_STREET_LAMP, siteOdds, null);
		Material bench = Material.SPRUCE_STAIRS;

		int originX = getChunkX() * 16, originZ = getChunkZ() * 16;
		for (int x = 0; x < 16; x++)
			for (int z = 0; z < 16; z++) {
				int wx = originX + x, wz = originZ + z;
				int top = getBlockY(x, z); // the site's level: its y is the top solid block
				// the pattern, from the site's north-west corner: a line every six blocks, a kerb at the rim
				int px = wx - site.minBlockX(), pz = wz - site.minBlockZ();
				boolean rim = wx == site.minBlockX() || wx == site.maxBlockX() || wz == site.minBlockZ()
						|| wz == site.maxBlockZ();
				boolean onLine = px % (GRID / 2) == 0 || pz % (GRID / 2) == 0;
				chunk.setBlock(x, top, z, rim ? Material.STONE_BRICKS : onLine ? lines : paving);

				// what stands on it, by where the cell falls on the lamp grid: (0,0) is a lamp post, the two cells
				// east of it its bench, and the 3x3 round the crossing midway between posts a planter. Every piece
				// is decided per cell from world coordinates, so one that straddles two chunks is drawn by both.
				int gx = Math.floorMod(px - GRID / 2, GRID), gz = Math.floorMod(pz - GRID / 2, GRID);
				boolean nearRim = px < 2 || pz < 2 || px > site.maxBlockX() - site.minBlockX() - 2
						|| pz > site.maxBlockZ() - site.minBlockZ() - 2;
				if (nearRim)
					continue;
				if (gx == 0 && gz == 0)
					lampPost(chunk, context, x, top, z, lamp);
				else if (gz == 0 && (gx == 1 || gx == 2))
					chunk.setBlock(x, top + 1, z, bench, BlockFace.NORTH); // its back to the post's row
				else {
					int cx = gx - GRID / 2, cz = gz - GRID / 2; // offset from the crossing midway between posts
					int ring = Math.max(Math.abs(cx), Math.abs(cz));
					if (ring == 1)
						chunk.setBlock(x, top + 1, z, Material.SMOOTH_STONE_SLAB, SlabType.BOTTOM);
					else if (ring == 0) {
						chunk.setBlock(x, top + 1, z, Material.GRASS_BLOCK);
						chunk.setBlock(x, top + 2, z, chunkOdds.flipCoin() ? Material.AZALEA : Material.FLOWERING_AZALEA);
					}
				}
			}
	}

	/** A street lamp as the roads draw theirs, one block shorter: the pool's lamp stacked three, else two of fence
	 *  and the style's light. Three above the paving is the most anything on the square rises. */
	private void lampPost(RealBlocks chunk, DataContext context, int x, int top, int z, Material lamp) {
		chunk.setBlock(x, top, z, Material.SMOOTH_STONE);
		if (lamp != null) {
			chunk.setBlocks(x, top + 1, top + 4, z, lamp);
			chunk.reconnect(x, x + 1, top + 1, top + 4, z, z + 1);
			return;
		}
		chunk.setBlocks(x, top + 1, top + 3, z, Material.SPRUCE_FENCE);
		chunk.setBlock(x, top + 3, z, context.lightMat);
	}
}
