package me.daddychurchill.CityWorld.Clipboard;

import me.daddychurchill.CityWorld.compat.BiomeGrid;
import me.daddychurchill.CityWorld.compat.Material;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;

/**
 * One chunk of a placed classic schematic. A building whose footprint spans several chunks becomes
 * several of these — one per chunk of the {@link PlatMap} grid it covers, each carrying the same
 * {@link Clipboard} plus its own {@code (lotX, lotZ)} offset into that footprint.
 *
 * <p>This is the modern counterpart of upstream's {@code ClipboardLot}, but the block-copying is
 * different by design. Upstream sliced the clip by hand — computing per-chunk sub-rectangles and
 * copying block by block through {@code RealBlocks}. Here the block data lives in a native
 * {@link net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate}, so each
 * chunk simply asks {@link Clipboard#pasteChunk} to place the whole building with a placement
 * bounding box clipped to this chunk. The template does the slicing; every chunk computes the same
 * whole-building origin, so the pieces line up.
 *
 * <p>The building is turned by {@code rotation}/{@code mirror}, chosen once at placement time (in
 * {@code PlatMap.placeSpecificClip}) so every footprint chunk shares it. {@code (lotX, lotZ)} are the
 * chunk offsets within the <em>placed (already rotated)</em> footprint, so the whole-building NW
 * corner is still {@code chunkOrigin - lot*16}; {@link Clipboard#pasteChunk} maps the template onto
 * that corner for the given rotation.
 *
 * <p>{@link #shapeFoundation} levels a pad before pasting — it digs the terrain out of the building's
 * vertical span and backfills a stone foundation down to solid ground, so a build neither floats over
 * a dip nor gets speared by a hillside. The schematic stays the building alone; the mod supplies the
 * ground under it. (Still to come: planting the leftover corners of a non-square footprint.)
 */
public class ClipboardLot extends IsolatedLot {

	private final Clipboard clip;
	private final int lotX;
	private final int lotZ;
	private final Rotation rotation;
	private final Mirror mirror;

	public ClipboardLot(PlatMap platmap, int chunkX, int chunkZ, Clipboard clip, int lotX, int lotZ) {
		this(platmap, chunkX, chunkZ, clip, lotX, lotZ, LotStyle.STRUCTURE);
	}

	public ClipboardLot(PlatMap platmap, int chunkX, int chunkZ, Clipboard clip, int lotX, int lotZ, LotStyle style) {
		this(platmap, chunkX, chunkZ, clip, lotX, lotZ, style, Rotation.NONE, Mirror.NONE);
	}

	public ClipboardLot(PlatMap platmap, int chunkX, int chunkZ, Clipboard clip, int lotX, int lotZ, LotStyle style,
			Rotation rotation, Mirror mirror) {
		super(platmap, chunkX, chunkZ);
		this.style = style;
		this.clip = clip;
		this.lotX = lotX;
		this.lotZ = lotZ;
		this.rotation = rotation;
		this.mirror = mirror;
	}

	@Override
	public PlatLot newLike(PlatMap platmap, int chunkX, int chunkZ) {
		return new ClipboardLot(platmap, chunkX, chunkZ, clip, lotX, lotZ, style, rotation, mirror);
	}

	@Override
	public boolean isPlaceableAt(CityWorldGenerator generator, int chunkX, int chunkZ) {
		return generator.getSettings().inCityRange(chunkX, chunkZ);
	}

	/**
	 * Ocean builds keep the natural terrain (deep seabed + sea) under them instead of the flat
	 * foundation pad the terrain phase lays for a STRUCTURE lot — that pad was raising the sea floor to
	 * just under the hull and leaving the ship beached on a mound. Land builds still get their pad.
	 */
	@Override
	public boolean generatesNaturalStrata() {
		return clip.ocean;
	}

	@Override
	protected void generateActualChunk(CityWorldGenerator generator, PlatMap platmap, InitialBlocks chunk,
			BiomeGrid biomes, DataContext context, int platX, int platZ) {
		// No terrain shaping in the first cut; the building is placed whole during decoration.
	}

	@Override
	protected void generateActualBlocks(CityWorldGenerator generator, PlatMap platmap, RealBlocks chunk,
			DataContext context, int platX, int platZ) {

		ServerLevelAccessor level = chunk.getServerLevel();
		if (level == null)
			return; // not a live worldgen level — nothing safe to place onto

		// The centred build's NW corner, in world blocks — see buildNwX/Z.
		int nwX = buildNwX();
		int nwZ = buildNwZ();

		// Level a pad first so the building neither floats over low ground nor has a hillside poking up
		// through it — dig the terrain out of its vertical span and backfill a stone foundation down to
		// solid ground. (The schematic itself is kept to the building; the mod supplies the ground.)
		shapeFoundation(generator, chunk, nwX, nwZ);

		clip.pasteChunk(level, nwX, surfaceLevel(generator), nwZ,
				chunk.getOriginX(), chunk.getOriginZ(), rotation, mirror, level.getRandom());

		// A build that asked to announce itself does so once — from the footprint's NW chunk, since every
		// chunk of a multi-chunk building runs this. Uses the .yml Title if it set one, so a bare
		// "winchester.schematic" can still read as "The Winchester Tavern". Known tradeoff: if a player
		// only ever generates the other footprint chunks, the line waits until the NW chunk decorates
		// (possibly never) — the same single-designated-chunk rule every procedural landmark uses; the
		// alternative (first-chunk-wins) needs shared state across threaded workers and double-announces
		// across restarts.
		if (clip.broadcastLocation && lotX == 0 && lotZ == 0)
			generator.reportLocation("schematic", clip.title, level, nwX, nwZ);

		// An ocean build sits in open water: flood its below-waterline blocks so the sea flows through
		// (waterlog fences/stairs/slabs/… rather than leaving dry pockets), and — if it asked to be
		// anchored — drop legs from its base to the real sea floor so it isn't perched on a rock stub.
		if (clip.ocean)
			finishOceanBuild(generator, chunk, level, nwX, nwZ);
		else
			// Otherwise integrate with a flooded / snow-buried world by refilling the footprint's empty cells
			// with the world's own atmosphere material below the flood line (water in FLOODED, snow in
			// SNOWDUNES), so the build doesn't leave dry air pockets poking through the surroundings.
			finishStyleFill(generator, chunk, level, nwX, nwZ);

		if (clip.decayable && generator.getSettings().includeDecayedBuildings && !isPristine(generator, nwX, nwZ)) {
			int depth = surfaceLevel(generator) - clip.groundLevelY;
			destroyLot(generator, depth, depth + clip.sizeY);
		}
	}

	/**
	 * Dig a level pad for this building and backfill its foundation, clipped to the current chunk. The
	 * schematic omits air and carries no ground of its own, so on anything but perfectly flat terrain it
	 * would otherwise float (low ground) or be speared by a hillside (high ground). For every column of
	 * the footprint that lies in this chunk we:
	 * <ul>
	 *   <li>clear the building's whole vertical span {@code [base, base+sizeY)} — removing any terrain,
	 *       surface or hill that reaches into where the building goes; the paste then refills its own
	 *       blocks and leaves the interior air, and</li>
	 *   <li>fill stone from just under the base straight down until it meets solid ground, so a building
	 *       over a dip stands on a real foundation instead of hovering.</li>
	 * </ul>
	 * All writes stay inside this chunk's columns, so nothing crosses the decorating region's edge.
	 */
	private void shapeFoundation(CityWorldGenerator generator, RealBlocks chunk, int nwX, int nwZ) {
		int base = surfaceLevel(generator) - clip.groundLevelY;
		int top = base + clip.sizeY;

		// the footprint's block extent, rotated, then clipped to this chunk's 0..15 columns
		int rotSizeX = Clipboard.swapsFootprint(rotation) ? clip.sizeZ : clip.sizeX;
		int rotSizeZ = Clipboard.swapsFootprint(rotation) ? clip.sizeX : clip.sizeZ;
		int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
		int bx1 = Math.max(0, nwX - oX), bx2 = Math.min(chunk.width, nwX + rotSizeX - oX);
		int bz1 = Math.max(0, nwZ - oZ), bz2 = Math.min(chunk.width, nwZ + rotSizeZ - oZ);
		boolean hasBuild = bx1 < bx2 && bz1 < bz2;

		// An ocean build does NOTHING to the terrain: it floats on the open sea and we just drop the ship
		// (or rig, or buoy) straight into the natural water. The terrain phase already leaves the real
		// deep seabed and sea in place (see generatesNaturalStrata below, which keeps this lot off the
		// flat STRUCTURE foundation pad), so there is nothing to level, clear or flood here — the paste
		// alone puts the hull in the water with clear sea beneath and the deck at the surface.
		if (clip.ocean)
			return;

		Material stratum = generator.oreProvider.stratumMaterial;
		int floor = chunk.minY + 1; // never fill onto the bedrock course

		// the building's own columns: carve the span clear (the paste refills its blocks) and backfill a
		// stone foundation down to the first solid block, so it never floats over a dip
		if (hasBuild) {
			chunk.clearBlocks(bx1, bx2, base, top, bz1, bz2);
			for (int x = bx1; x < bx2; x++)
				for (int z = bz1; z < bz2; z++)
					for (int y = base - 1; y > floor && chunk.isEmpty(x, y, z); y--)
						chunk.setBlock(x, y, z, stratum);
		}
	}

	/**
	 * Finish a placed ocean build (this chunk's slice of its footprint). Two passes, both on the live
	 * level since they read and rewrite the just-pasted blocks:
	 * <ul>
	 *   <li><b>Waterlog</b> every below-waterline block that can hold water (fences, stairs, slabs,
	 *       panes …) so the sea reads as flowing through the hull instead of leaving dry air pockets.</li>
	 *   <li><b>Anchor</b> (only if the schematic set {@code Anchor: true}): extend each solid column of
	 *       the build's bottom row straight down until it meets the sea floor, so the build stands on
	 *       real legs to the seabed rather than perched on a floating stub of rock.</li>
	 * </ul>
	 * All writes stay in this chunk's own columns (down a single column), so nothing crosses the
	 * decorating region's edge.
	 */
	private void finishOceanBuild(CityWorldGenerator generator, RealBlocks chunk, ServerLevelAccessor level,
			int nwX, int nwZ) {
		int base = surfaceLevel(generator) - clip.groundLevelY; // the build's bottom row
		int sea = generator.seaLevel;
		int rotSizeX = Clipboard.swapsFootprint(rotation) ? clip.sizeZ : clip.sizeX;
		int rotSizeZ = Clipboard.swapsFootprint(rotation) ? clip.sizeX : clip.sizeZ;
		int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
		int bx1 = Math.max(0, nwX - oX), bx2 = Math.min(chunk.width, nwX + rotSizeX - oX);
		int bz1 = Math.max(0, nwZ - oZ), bz2 = Math.min(chunk.width, nwZ + rotSizeZ - oZ);
		int floor = chunk.minY + 1;

		for (int x = bx1; x < bx2; x++)
			for (int z = bz1; z < bz2; z++) {
				int wx = oX + x, wz = oZ + z;

				if (clip.anchor) {
					BlockState bottom = level.getBlockState(new BlockPos(wx, base, wz));
					// only anchor solid base blocks (the legs/hull), not the open-water columns
					if (!bottom.isAir() && bottom.getFluidState().isEmpty())
						for (int y = base - 1; y > floor; y--) {
							BlockPos pos = new BlockPos(wx, y, wz);
							BlockState here = level.getBlockState(pos);
							if (!here.isAir() && here.getFluidState().isEmpty())
								break; // reached the sea floor
							level.setBlock(pos, bottom, Block.UPDATE_CLIENTS);
						}
				}

				for (int y = base; y <= sea; y++) {
					BlockPos pos = new BlockPos(wx, y, wz);
					BlockState st = level.getBlockState(pos);
					if (st.hasProperty(BlockStateProperties.WATERLOGGED)
							&& !st.getValue(BlockStateProperties.WATERLOGGED))
						level.setBlock(pos, st.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_CLIENTS);
				}
			}
	}

	/**
	 * Integrate a pasted schematic with a world whose empty space isn't air — refill the footprint's empty
	 * cells with the world's atmosphere material below the flood line: WATER in FLOODED, SNOW_BLOCK in
	 * SNOWDUNES, SAND in SANDDUNES (the very {@code findAtmosphereMaterialAt} the terrain gen fills with).
	 * Without it a build leaves dry air pockets poking through the drowned / buried / sanded surroundings. A
	 * no-op in normal worlds (atmosphere is air) or when the build stands above the flood line. Water flows
	 * through waterloggable blocks (fences/slabs) rather than replacing them.
	 */
	private void finishStyleFill(CityWorldGenerator generator, RealBlocks chunk, ServerLevelAccessor level,
			int nwX, int nwZ) {
		// what the world packs its empty space with below the flood line — water (FLOODED), snow (SNOWDUNES),
		// sand (SANDDUNES). Sampled just under the LOWEST flood level so it's the fill even where dunes rise.
		Material fillMat = generator.shapeProvider.findAtmosphereMaterialAt(generator,
				generator.shapeProvider.findLowestFloodY(generator) - 1);
		if (fillMat == Material.AIR)
			return; // a normal (air) world — nothing to fill

		int base = surfaceLevel(generator) - clip.groundLevelY; // the build's bottom row
		boolean water = fillMat == Material.WATER;
		int rotSizeX = Clipboard.swapsFootprint(rotation) ? clip.sizeZ : clip.sizeX;
		int rotSizeZ = Clipboard.swapsFootprint(rotation) ? clip.sizeX : clip.sizeZ;
		int oX = chunk.getOriginX(), oZ = chunk.getOriginZ();
		int bx1 = Math.max(0, nwX - oX), bx2 = Math.min(chunk.width, nwX + rotSizeX - oX);
		int bz1 = Math.max(0, nwZ - oZ), bz2 = Math.min(chunk.width, nwZ + rotSizeZ - oZ);

		boolean snow = fillMat == Material.SNOW_BLOCK;
		for (int x = bx1; x < bx2; x++)
			for (int z = bz1; z < bz2; z++) {
				int wx = oX + x, wz = oZ + z;
				// fill up to THIS column's flood/dune surface, not a flat level — so a snow/sand build follows
				// the undulating drifts instead of getting a flat plateau (findFloodY carries the dune noise;
				// it's flat for FLOODED, which is what we want there)
				int top = generator.shapeProvider.findFloodY(generator, wx, wz);
				// snow finishes in a partial SNOW layer at `top` (like the terrain), the rest is solid below it
				int bodyTop = snow ? top - 1 : top;
				for (int y = base; y <= bodyTop; y++) {
					BlockPos pos = new BlockPos(wx, y, wz);
					BlockState st = level.getBlockState(pos);
					if (st.isAir())
						chunk.setBlock(x, y, z, fillMat);
					else if (water && st.hasProperty(BlockStateProperties.WATERLOGGED)
							&& !st.getValue(BlockStateProperties.WATERLOGGED))
						level.setBlock(pos, st.setValue(BlockStateProperties.WATERLOGGED, true), Block.UPDATE_CLIENTS);
				}
				// cap snow with a partial layer (height from the fractional flood Y) so it meets the terrain's
				// snow-layer surface flush, not a full-block step
				if (snow && level.getBlockState(new BlockPos(wx, top, wz)).isAir())
					chunk.setBlock(x, top, z, Material.SNOW,
							generator.shapeProvider.findPerciseFloodY(generator, wx, wz));
			}
	}

	/**
	 * Whether this building is spared from decay and left intact — a rare find in a ruined world. The
	 * chance is the schematic's own {@code PristineChance} if it set one, else the world's
	 * {@code oddsOfPristineBuilding}. Rolled once from the building's NW origin (the same value every
	 * footprint chunk computes), so a multi-chunk building is wholly pristine or wholly decayed, never
	 * half-and-half.
	 */
	private boolean isPristine(CityWorldGenerator generator, int nwX, int nwZ) {
		double chance = clip.pristineChance >= 0.0 ? clip.pristineChance
				: generator.getSettings().oddsOfPristineBuilding;
		if (chance <= 0.0)
			return false;
		Odds odds = new Odds(generator.getWorldSeed() + nwX * 341873128712L + nwZ * 132897987541L);
		return odds.playOdds(chance);
	}

	/**
	 * The world Y the building's ground layer should sit at. In a city the roads raise the sidewalk to
	 * {@code streetLevel + 1} (see {@code RoadLot.topOfRoad}), so a building whose floor sits at
	 * {@code streetLevel} reads as one block low next to the kerb — which is exactly the "1 too low"
	 * seen for urban schematics (and not for rural ones, where there is no raised sidewalk). So urban
	 * families sit a block higher; rural families stay on the natural surface.
	 *
	 * <p>Keyed off the family rather than the city-radius test, which only narrows anything once a world
	 * sets a radius (SPARSE does) and answers true everywhere on the default unbounded one.
	 */
	private int surfaceLevel(CityWorldGenerator generator) {
		// An ocean build's "ground layer" is its waterline — put it at the sea surface so the deck rides
		// the water and the GroundLevelY layers below it are the legs/hull sitting in the sea.
		if (clip.ocean)
			return generator.seaLevel;
		return generator.streetLevel + (isUrban(clip.family) ? 1 : 0);
	}

	private static boolean isUrban(PasteProvider.SchematicFamily family) {
		return switch (family) {
			case FARM, NATURE, OUTLAND, ASTRAL -> false;
			default -> true; // roundabout, highrise, midrise, lowrise, municipal, industrial,
							  // construction, neighborhood, park — all built on raised city sidewalks
		};
	}

	@Override
	public int getBottomY(CityWorldGenerator generator) {
		return surfaceLevel(generator) - clip.groundLevelY;
	}

	@Override
	public int getTopY(CityWorldGenerator generator, AbstractCachedYs blockYs, int x, int z) {
		return surfaceLevel(generator) - clip.groundLevelY + clip.sizeY;
	}

	/**
	 * Keep the mine network out of the building's vertical span. Mines run under every lot
	 * ({@code ShapeProvider_Normal} calls {@code generateMines} for all of them), and their oak-fence
	 * supports and plank walkways were cutting straight up through placed schematics — the "wooden
	 * platforms in the winchester". Upstream's {@code ClipboardLot} guarded this the same way; the
	 * port had dropped it. Only allow a mine well below the foundation or well above the roof.
	 */
	@Override
	protected boolean isShaftableLevel(CityWorldGenerator generator, int y) {
		int bottom = surfaceLevel(generator) - clip.groundLevelY;
		int top = bottom + clip.sizeY;
		return (y < bottom - 32 || y > top + 16) && super.isShaftableLevel(generator, y);
	}

	/**
	 * Keep the terrain strata out of the building's span too, so the schematic's interior air isn't
	 * pre-filled with stone before it is pasted (strata runs in the terrain phase, the paste in
	 * decoration). Valid only below the foundation or above the roof.
	 */
	@Override
	public boolean isValidStrataY(CityWorldGenerator generator, int blockX, int blockY, int blockZ) {
		// Ocean builds touch nothing: keep every layer of natural terrain (the sea and its floor) exactly
		// as the noise made it, and just paste the ship on top. No carving.
		if (clip.ocean)
			return true;
		int bottom = surfaceLevel(generator) - clip.groundLevelY;
		int top = bottom + clip.sizeY;
		if (blockY <= bottom || blockY > top)
			return true; // outside the build's vertical span — ordinary strata
		// Inside the span, only clear the ground UNDER the building itself. The leftover corners of a
		// larger footprint keep their natural terrain (biome-correct grass/sand, not a bare dirt apron —
		// the strata pass otherwise skips their surface layer here and leaves subsurface dirt showing).
		return !isUnderBuild(blockX, blockZ);
	}

	/**
	 * The foundation pad is only laid under the building itself; the leftover margin of a footprint the
	 * build doesn't fill falls through to natural terrain (keeping its biome surface) rather than the
	 * bare dirt apron the STRUCTURE strata pass would otherwise leave around the build.
	 */
	@Override
	public boolean isFoundationColumnAt(CityWorldGenerator generator, int blockX, int blockZ) {
		return isUnderBuild(blockX, blockZ);
	}

	/** Whether this world column lies under the placed (rotated, centred) building footprint. */
	private boolean isUnderBuild(int blockX, int blockZ) {
		int rotSizeX = Clipboard.swapsFootprint(rotation) ? clip.sizeZ : clip.sizeX;
		int rotSizeZ = Clipboard.swapsFootprint(rotation) ? clip.sizeX : clip.sizeZ;
		int nwX = buildNwX(), nwZ = buildNwZ();
		return blockX >= nwX && blockX < nwX + rotSizeX && blockZ >= nwZ && blockZ < nwZ + rotSizeZ;
	}

	private static final int CHUNK = 16;

	/** The centred build's NW corner (world X), the same value every footprint chunk computes. */
	private int buildNwX() {
		int rotSizeX = Clipboard.swapsFootprint(rotation) ? clip.sizeZ : clip.sizeX;
		return (chunkX - lotX) * CHUNK + (clip.footprintChunkX(rotation) * CHUNK - rotSizeX) / 2;
	}

	/** The centred build's NW corner (world Z). See {@link #buildNwX}. */
	private int buildNwZ() {
		int rotSizeZ = Clipboard.swapsFootprint(rotation) ? clip.sizeX : clip.sizeZ;
		return (chunkZ - lotZ) * CHUNK + (clip.footprintChunkZ(rotation) * CHUNK - rotSizeZ) / 2;
	}

	/** The building this lot is a chunk of. (Upstream exposed this too — Sablednah, PR #4.) */
	public Clipboard getClip() {
		return clip;
	}
}
