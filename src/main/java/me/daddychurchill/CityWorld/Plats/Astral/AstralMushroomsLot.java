package me.daddychurchill.CityWorld.Plats.Astral;

import me.daddychurchill.CityWorld.compat.Material;
import me.daddychurchill.CityWorld.compat.BlockFace;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;
import me.daddychurchill.CityWorld.Support.WorldBlocks;

public abstract class AstralMushroomsLot extends AstralNatureLot {

	AstralMushroomsLot(PlatMap platmap, int chunkX, int chunkZ, double populationChance) {
		super(platmap, chunkX, chunkZ, populationChance);

	}

	protected abstract int maxMushrooms();

	protected abstract void plantMushroom(CityWorldGenerator generator, WorldBlocks blocks, int blockX, int blockY,
			int blockZ, int snowY);

	final static int maxHeight = 18;
	final static int minHeight = maxHeight / 2;

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {

		WorldBlocks blocks = new WorldBlocks(generator, chunk.getWorld(), chunkOdds);
		for (int i = 0; i < maxMushrooms(); i++) {
			if (chunkOdds.playOdds(populationChance)) {
				int x = chunkOdds.getRandomInt(4) * 4;
				int z = chunkOdds.getRandomInt(4) * 4;
				int y = getSurfaceAtY(x, z);

				if (y > 0) {
					int blockY = y;

					// go up until we get just past the stratum
					while (chunk.isType(x, blockY, z, generator.oreProvider.subsurfaceMaterial)) {
						blockY++;
					}

					// go down until we get to the stratum
					while (!chunk.isType(x, blockY, z, generator.oreProvider.subsurfaceMaterial)) {
						blockY--;
						if (blockY == 1 || blockY < y - 5)
							break;
					}

					// move up one little bit
					blockY++;

					// now count how much snow is sitting on top
					int snowY = 0;
					while (chunk.isType(x, blockY + snowY, z, Material.SNOW_BLOCK))
						snowY++;

					// too far?
					if (blockY + snowY + maxHeight <= generator.seaLevel) {
						int blockX = chunk.getBlockX(x);
						int blockZ = chunk.getBlockZ(z);

						plantMushroom(generator, blocks, blockX, blockY, blockZ, snowY);
					}
				}
			}
		}
	}

	// Upstream picked the huge-mushroom cap face from org.bukkit.material.types.MushroomBlockTexture
	// (ALL_PORES / ALL_CAP / STEM_SIDES), a 1.12-era data model the 1.13 flattening removed: huge
	// mushrooms now carry six independent boolean face properties instead of one texture id. There is
	// no getCapFace() to port, so these map onto the port's per-face setter. SELF applies the block's
	// default state, which for BROWN/RED_MUSHROOM_BLOCK is all-cap — so the mushroom keeps its shape;
	// the fine pore/stem texturing is the one cosmetic detail lost in the model change.
	private static final BlockFace flesh = BlockFace.SELF;
	private static final BlockFace shell = BlockFace.SELF;

	private int mushX = 0;
	private int mushZ = 0;
	private int layerY = 0;

	protected abstract Material getMushroomMaterial();

	void startMushroom(WorldBlocks blocks, int baseX, int baseY, int baseZ, int heightY) {

		mushX = baseX;
		mushZ = baseZ;
		layerY = baseY + heightY;

		// the STEM is its own block (stem texture on its sides) — drawing it with the cap block was what made
		// the whole mushroom read as one flat texture
		blocks.setBlocks(mushX, baseY, layerY - 2, mushZ, Material.MUSHROOM_STEM);
	}

	void nextMushroomLevel() {
		layerY--;
	}

	private void prevMushroomLevel() {
		layerY++;
	}

	void drawMushroomSlice(WorldBlocks blocks, int r) {
		if (r > 0) {
			drawMushroomShell(blocks, r);
			prevMushroomLevel();
			drawMushroomFlesh(blocks, r);
		} else {
			blocks.setBlock(mushX, layerY, mushZ, getMushroomMaterial(), shell);
			nextMushroomLevel();
		}
	}

	void drawMushroomShell(WorldBlocks blocks, int r) {
		if (r > 0) {
			blocks.setBlocks(mushX - r + 1, mushX + r, layerY, layerY + 1, mushZ - r, mushZ - r + 1,
					getMushroomMaterial(), shell);
			blocks.setBlocks(mushX - r, mushX - r + 1, layerY, layerY + 1, mushZ - r + 1, mushZ + r,
					getMushroomMaterial(), shell);
			blocks.setBlocks(mushX + r, mushX + r + 1, layerY, layerY + 1, mushZ - r + 1, mushZ + r,
					getMushroomMaterial(), shell);
			blocks.setBlocks(mushX - r + 1, mushX + r, layerY, layerY + 1, mushZ + r, mushZ + r + 1,
					getMushroomMaterial(), shell);
		} else
			blocks.setBlock(mushX, layerY, mushZ, getMushroomMaterial(), shell);
		nextMushroomLevel();
	}

	void drawMushroomTop(WorldBlocks blocks, int r) {
		if (r > 0)
			blocks.setBlocks(mushX - r, mushX + r + 1, layerY, layerY + 1, mushZ - r, mushZ + r + 1,
					getMushroomMaterial(), shell);
		else
			blocks.setBlock(mushX, layerY, mushZ, getMushroomMaterial(), shell);
		nextMushroomLevel();
	}

	private void drawMushroomFlesh(WorldBlocks blocks, int r) {
		if (r > 0) {
			blocks.setBlocks(mushX - r + 1, mushX + r, layerY, layerY + 1, mushZ - r + 1, mushZ + r,
					getMushroomMaterial(), flesh);
			blocks.setBlock(mushX - r + 1, layerY, mushZ - r + 1, getMushroomMaterial(), shell);
			blocks.setBlock(mushX + r - 1, layerY, mushZ - r + 1, getMushroomMaterial(), shell);
			blocks.setBlock(mushX - r + 1, layerY, mushZ + r - 1, getMushroomMaterial(), shell);
			blocks.setBlock(mushX + r - 1, layerY, mushZ + r - 1, getMushroomMaterial(), shell);
		}
		nextMushroomLevel();
	}

}
