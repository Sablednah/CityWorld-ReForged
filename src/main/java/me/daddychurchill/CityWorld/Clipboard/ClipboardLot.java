package me.daddychurchill.CityWorld.Clipboard;

import me.daddychurchill.CityWorld.compat.BiomeGrid;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plats.IsolatedLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.AbstractCachedYs;
import me.daddychurchill.CityWorld.Support.InitialBlocks;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.Support.RealBlocks;

import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;

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
 * <p><b>Remaining scope</b> (to refine later): no foundation dig / air-carve. The converted template
 * omits air, so a building sits on the street surface cleanly on flat city ground (where cities
 * generate) but does not hollow out a hillside or a basement pocket. Basement-bearing schematics
 * ({@code groundLevelY > 0}) will want the upstream backfill later.
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

		// The whole building's NW corner, in world blocks: this chunk's origin, stepped back by our
		// offset into the footprint. Every chunk of the building computes the same value, so the
		// clipped slices tile together seamlessly.
		int nwX = chunk.getOriginX() - lotX * chunk.width;
		int nwZ = chunk.getOriginZ() - lotZ * chunk.width;

		clip.pasteChunk(level, nwX, surfaceLevel(generator), nwZ,
				chunk.getOriginX(), chunk.getOriginZ(), rotation, mirror, level.getRandom());

		if (clip.decayable && generator.getSettings().includeDecayedBuildings && !isPristine(generator, nwX, nwZ)) {
			int depth = surfaceLevel(generator) - clip.groundLevelY;
			destroyLot(generator, depth, depth + clip.sizeY);
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
	 * <p>Keyed off the family rather than {@code inACity} because that setting is still a stub that
	 * answers true everywhere until the P7 city-radius work lands.
	 */
	private int surfaceLevel(CityWorldGenerator generator) {
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
		int bottom = surfaceLevel(generator) - clip.groundLevelY;
		int top = bottom + clip.sizeY;
		return blockY <= bottom || blockY > top;
	}

	/** The building this lot is a chunk of. (Upstream exposed this too — Sablednah, PR #4.) */
	public Clipboard getClip() {
		return clip;
	}
}
