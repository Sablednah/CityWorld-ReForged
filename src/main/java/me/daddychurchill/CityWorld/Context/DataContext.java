package me.daddychurchill.CityWorld.Context;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Clipboard.Clipboard;
import me.daddychurchill.CityWorld.Clipboard.ClipboardList;
import me.daddychurchill.CityWorld.Clipboard.SchematicLibrary;
import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Plats.NatureLot;
import me.daddychurchill.CityWorld.Plats.PlatLot;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.PlatMap;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * A context decides which lots populate a {@link PlatMap} — i.e. whether a patch of world becomes
 * downtown, suburb, farm or wilderness. The odds below are the knobs that give each kind of district
 * its character.
 *
 * <p>Ported for real in wave 2, with one half deferred: the <b>schematic</b> methods
 * ({@code getSingleSchematic}, {@code populateSchematics}, {@code setSchematicFamily}) need
 * {@code Clipboard}/{@code PasteProvider}, which are P6. The schematic family a context declares is
 * still tracked so the P6 work has somewhere to land.
 */
public abstract class DataContext {

	// While these are initialized here, the real defaults live in CivilizedContext
	// and UncivilizedContext

	protected double oddsOfIsolatedLots = Odds.oddsNeverGoingToHappen;
	protected double oddsOfIsolatedConstructs = Odds.oddsNeverGoingToHappen;
	protected double oddsOfParks = Odds.oddsNeverGoingToHappen; // parks show up 1/n of the time

	public double oddsOfIdenticalBuildingHeights = Odds.oddsNeverGoingToHappen; // similar height 1/n of the time
	public double oddsOfSimilarBuildingHeights = Odds.oddsNeverGoingToHappen; // identical height 1/n of the time
	public final double oddsOfRoundedBuilding = Odds.oddsEnormouslyLikely;// Odds.oddsLikely; // how naturally rounded are
	// buildings that can be rounded
	public double oddsOfSimilarBuildingRounding = Odds.oddsNeverGoingToHappen; // like rounding 1/n of the time
	public double oddsOfStairWallMaterialIsWallMaterial = Odds.oddsNeverGoingToHappen; // stair walls are the same as
	// walls 1/n of the time
	public int rangeOfWallInset = 2; // 1 or 2 in... but not zero
	public final double oddsOfForcedNarrowInteriorMode = Odds.oddsLikely;
	public final double oddsOfDifferentInteriorModes = Odds.oddsUnlikely;

	public double oddsOfOnlyUnfinishedBasements = Odds.oddsNeverGoingToHappen; // unfinished buildings only have
	// basements 1/n of the time
	public double oddsOfCranes = Odds.oddsVeryLikely; // plop a crane on top of the last horizontal girder 1/n of the
	// time

	public double oddsOfBuildingWallInset = Odds.oddsNeverGoingToHappen; // building walls inset as they go up 1/n of
	// the time
	public double oddsOfSimilarInsetBuildings = Odds.oddsNeverGoingToHappen; // the east/west inset is used for
	// north/south inset 1/n of the time
	public double oddsOfFlatWalledBuildings = Odds.oddsNeverGoingToHappen; // the ceilings are inset like the walls 1/n
	// of the time

	// TODO oddsOfMissingRoad is current not used... I need to fix this
	// public double oddsOfMissingRoad = oddsNeverGoingToHappen; // roads are
	// missing 1/n of the time
	public double oddsOfRoundAbouts = Odds.oddsNeverGoingToHappen; // roundabouts are created 1/n of the time

	public double oddsOfArt = Odds.oddsNeverGoingToHappen; // art is missing 1/n of the time
	public double oddsOfNaturalArt = Odds.oddsNeverGoingToHappen; // sometimes nature is art 1/n of the time

	public final Material lightMat;
	public final Material torchMat;

	public static final int FloorHeight = 4;
	private static final int FudgeFloorsBelow = 2;
	private static final int FudgeFloorsAbove = 0;// 3;
	private static final int absoluteMinimumFloorsAbove = 5; // shortest tallest building
	private static final int absoluteAbsoluteMaximumFloorsBelow = 3; // that is as many basements as I personally can
	// tolerate
	// The tallest-building cap used to be a hardcoded 20 ("tall enough folks"); it is now the
	// per-world maxBuildingFloors setting (20 for CLASSIC, taller for MODERN). See DataContext ctor.
	public final int buildingMaximumY;
	public int maximumFloorsAbove = 2;
	public int maximumFloorsBelow = 2;
	private final int absoluteMaximumFloorsBelow;
	protected final int absoluteMaximumFloorsAbove;

	protected double oddsOfUnfinishedBuildings = Odds.oddsNeverGoingToHappen;

	protected DataContext(CityWorldGenerator generator) {

		// lights?
		if (generator.getSettings().includeWorkingLights) {
			lightMat = Material.GLOWSTONE;
			torchMat = Material.TORCH;
		} else {
			lightMat = Material.REDSTONE_LAMP;
			torchMat = Material.REDSTONE_TORCH;
		}

		// How tall may a building get? The floor cap is the per-world maxBuildingFloors setting (20 for
		// CLASSIC's 1.14 look, taller for MODERN). Raise the building Y ceiling to fit that many floors
		// plus a little roof headroom, clamped to the world's *real* ceiling (319, not the 256 terrain
		// ceiling) — so MODERN highrises use the modern headroom while CLASSIC lands exactly where it did.
		int maxFloors = generator.getSettings().maxBuildingFloors;
		int roofHeadroom = 2 * FloorHeight;
		buildingMaximumY = Math.min(
				generator.streetLevel + (maxFloors + FudgeFloorsAbove) * FloorHeight + roofHeadroom,
				generator.worldMaxY - 8);
		absoluteMaximumFloorsBelow = Math.max(
				Math.min(generator.streetLevel / FloorHeight - FudgeFloorsBelow, absoluteAbsoluteMaximumFloorsBelow),
				0);
		absoluteMaximumFloorsAbove = Math
				.max(Math.min((buildingMaximumY - generator.streetLevel) / FloorHeight - FudgeFloorsAbove,
						maxFloors), absoluteMinimumFloorsAbove);

		// calculate the extremes for this plat
		maximumFloorsAbove = Math.min(maximumFloorsAbove, absoluteMaximumFloorsAbove);
		maximumFloorsBelow = Math.min(maximumFloorsBelow, absoluteMaximumFloorsBelow);
	}

	public abstract void populateMap(CityWorldGenerator generator, PlatMap platmap);

	public abstract void validateMap(CityWorldGenerator generator, PlatMap platmap);

	/**
	 * Lets the bundled classic schematics claim lots before the generator fills the rest with its own
	 * buildings — which is why {@code UrbanContext.populateMap} calls it first.
	 *
	 * <p>Off unless {@code includeSchematics} is set (see {@link me.daddychurchill.CityWorld.CityWorldSettings});
	 * upstream only placed schematics when a WorldEdit folder was loaded, so most worlds saw none.
	 */
	void populateSchematics(CityWorldGenerator generator, PlatMap platmap) {
		populateSchematics(generator, platmap, Integer.MAX_VALUE);
	}

	/** As above, but cap the number of schematics dropped in this platmap (see {@link ClipboardList#populate}). */
	void populateSchematics(CityWorldGenerator generator, PlatMap platmap, int maxPlacements) {
		if (!generator.getSettings().includeSchematics)
			return;
		ClipboardList clips = getSchematics(generator);
		if (clips != null && !clips.isEmpty())
			clips.populate(generator, platmap, maxPlacements);
	}

	Clipboard getSingleSchematic(CityWorldGenerator generator, PlatMap platmap, Odds odds, int x, int z) {
		if (!generator.getSettings().includeSchematics)
			return null;
		ClipboardList clips = getSchematics(generator);
		return clips != null ? clips.getSingleLot(generator, platmap, odds, x, z) : null;
	}

	/** The family's clips, resolved once (footprint-filtered to this context's max) and cached. */
	private ClipboardList mapsSchematics;

	private ClipboardList getSchematics(CityWorldGenerator generator) {
		if (mapsSchematics == null) {
			List<Clipboard> fits = new ArrayList<>();
			for (Clipboard clip : SchematicLibrary.family(schematicFamily))
				if (clip.chunkX <= schematicMaxX && clip.chunkZ <= schematicMaxZ)
					fits.add(clip);
			mapsSchematics = new ClipboardList(fits);
		}
		return mapsSchematics;
	}

	// --- schematic family ----------------------------------------------------------------------
	// What kind of place this context builds. Each context declares its own in its constructor, and
	// P6 uses it to pick which of the player's schematics may land here. Tracked now so those
	// declarations survive; nothing reads it until then.

	private static final int schematicMax = 4;
	private SchematicFamily schematicFamily = SchematicFamily.NATURE;
	private int schematicMaxX = schematicMax;
	private int schematicMaxZ = schematicMax;

	protected void setSchematicFamily(SchematicFamily family) {
		setSchematicFamily(family, schematicMax);
	}

	void setSchematicFamily(SchematicFamily family, int maxWidth) {
		schematicFamily = family;
		schematicMaxX = maxWidth;
		schematicMaxZ = maxWidth;
	}

	public SchematicFamily getSchematicFamily() {
		return schematicFamily;
	}

	public PlatLot createNaturalLot(CityWorldGenerator generator, PlatMap platmap, int x, int z) {
		return new NatureLot(platmap, platmap.originX + x, platmap.originZ + z);
	}

	public Material getMapRepresentation() {
		return Material.AIR;
	}
}
