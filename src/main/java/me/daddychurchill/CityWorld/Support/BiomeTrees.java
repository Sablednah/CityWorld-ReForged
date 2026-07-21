package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.Plugins.CoverProvider.CoverageType;

/**
 * Biome-fitting tree palettes for MODERN blend planting (roadsides, tunnel roofs) — spruce/pine where
 * it's cold, acacia on the savanna, jungle in the warm-wet, oak/birch temperate, nothing in a hot-dry
 * desert. Keyed off {@link ClimateZone} so a road reads as a planted avenue of the right species for the
 * biome it crosses instead of CityWorld's biome-agnostic oak/birch lines.
 */
public final class BiomeTrees {

	private BiomeTrees() {}

	private static final CoverageType[] COLD = { CoverageType.SHORT_PINE_TREE, CoverageType.PINE_TREE,
			CoverageType.MINI_PINE_TREE, CoverageType.SHORT_BIRCH_TREE };
	private static final CoverageType[] TEMPERATE = { CoverageType.SHORT_OAK_TREE, CoverageType.OAK_TREE,
			CoverageType.SHORT_BIRCH_TREE, CoverageType.BIRCH_TREE };
	private static final CoverageType[] SAVANNA = { CoverageType.MINI_ACACIA_TREE, CoverageType.ACACIA_TREE,
			CoverageType.SHORT_OAK_TREE };
	private static final CoverageType[] JUNGLE = { CoverageType.MINI_JUNGLE_TREE, CoverageType.SHORT_JUNGLE_TREE,
			CoverageType.JUNGLE_TREE };
	private static final CoverageType[] NONE = {};

	/** Trees that fit the climate zone; empty for hot-dry desert (nothing to plant there). */
	public static CoverageType[] forZone(ClimateZone zone) {
		switch (zone.temp) {
		case COLD:
			return COLD;
		case TEMPERATE:
			return TEMPERATE;
		case WARM:
			return zone.wet() ? JUNGLE : SAVANNA;
		case HOT:
		default:
			return zone.wet() ? JUNGLE : NONE;
		}
	}
}
