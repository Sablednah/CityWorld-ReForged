package me.daddychurchill.CityWorld.Context;

import me.daddychurchill.CityWorld.CityWorldGenerator;
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
	private static final int absoluteAbsoluteMaximumFloorsAbove = 20; // that is tall enough folks
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

		// where is the ground
		// NOTE: generator.height is the *terrain* ceiling (256), not the world's (319) — see
		// CityWorldGenerator.worldMinY. So buildings still cap where they did in 1.14; letting them
		// use the extra headroom is a deliberate P4 follow-up, not an oversight.
		buildingMaximumY = Math.min(192 + FudgeFloorsAbove * FloorHeight, generator.height);
		absoluteMaximumFloorsBelow = Math.max(
				Math.min(generator.streetLevel / FloorHeight - FudgeFloorsBelow, absoluteAbsoluteMaximumFloorsBelow),
				0);
		absoluteMaximumFloorsAbove = Math
				.max(Math.min((buildingMaximumY - generator.streetLevel) / FloorHeight - FudgeFloorsAbove,
						absoluteAbsoluteMaximumFloorsAbove), absoluteMinimumFloorsAbove);

		// calculate the extremes for this plat
		maximumFloorsAbove = Math.min(maximumFloorsAbove, absoluteMaximumFloorsAbove);
		maximumFloorsBelow = Math.min(maximumFloorsBelow, absoluteMaximumFloorsBelow);
	}

	public abstract void populateMap(CityWorldGenerator generator, PlatMap platmap);

	public abstract void validateMap(CityWorldGenerator generator, PlatMap platmap);

	/**
	 * Lets the user's own schematics claim lots before the generator fills the rest with its own
	 * buildings — which is why {@code UrbanContext.populateMap} calls it first.
	 *
	 * <p>No-op until P6 ports {@code Clipboard}/{@code PasteProvider}; with no schematics loaded,
	 * upstream's version does nothing either.
	 */
	void populateSchematics(CityWorldGenerator generator, PlatMap platmap) {
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
