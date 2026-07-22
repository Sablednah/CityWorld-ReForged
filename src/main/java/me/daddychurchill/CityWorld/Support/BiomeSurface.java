package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.compat.Material;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;

/**
 * MODERN biome-aware ground: the signature surface/subsurface block for a biome whose look isn't grass
 * — sand in deserts, red sand + terracotta in badlands, mycelium in mushroom fields, gravel in the
 * gravelly/stony biomes, snow over the cold ones. CityWorld lays plain grass everywhere (vanilla's
 * surface rules are suppressed), so this is where the wild gets its real ground back — and giving
 * deserts real sand is what lets vanilla's cactus/dead-bush decoration actually place.
 *
 * <p>{@code null} means "leave the world default" (grass over dirt).
 */
public final class BiomeSurface {

	private BiomeSurface() {}

	/** Top block for the biome, or null to keep grass. */
	public static Material surface(ResourceKey<Biome> b) {
		if (b == null)
			return null;
		if (b == Biomes.DESERT || b == Biomes.BEACH)
			return Material.SAND;
		if (b == Biomes.SNOWY_BEACH)
			return Material.SAND; // sand, snow-dusted below
		if (b == Biomes.BADLANDS || b == Biomes.WOODED_BADLANDS || b == Biomes.ERODED_BADLANDS)
			return Material.RED_SAND;
		if (b == Biomes.MUSHROOM_FIELDS)
			return Material.MYCELIUM;
		if (b == Biomes.WINDSWEPT_GRAVELLY_HILLS || b == Biomes.STONY_SHORE)
			return Material.GRAVEL;
		if (b == Biomes.STONY_PEAKS)
			return Material.STONE;
		if (b == Biomes.ICE_SPIKES)
			return Material.SNOW_BLOCK; // packed-ice spikes come from vanilla decoration
		return null;
	}

	/** A few blocks of sub-surface under {@link #surface}, or null to keep dirt. */
	public static Material subsurface(ResourceKey<Biome> b) {
		if (b == null)
			return null;
		if (b == Biomes.DESERT || b == Biomes.BEACH || b == Biomes.SNOWY_BEACH)
			return Material.SANDSTONE;
		if (b == Biomes.BADLANDS || b == Biomes.WOODED_BADLANDS || b == Biomes.ERODED_BADLANDS)
			return Material.TERRACOTTA; // approximation of the coloured bands; good enough at a glance
		if (b == Biomes.WINDSWEPT_GRAVELLY_HILLS || b == Biomes.STONY_SHORE || b == Biomes.STONY_PEAKS)
			return Material.STONE;
		return null;
	}

	/** Cold biomes that want a snow layer laid over their ground (at low elevation; peaks are iced by
	 *  the icecap pass). Excludes ice spikes, which get a solid snow-block surface above instead. */
	public static boolean snowy(ResourceKey<Biome> b) {
		return b == Biomes.SNOWY_PLAINS || b == Biomes.SNOWY_TAIGA || b == Biomes.SNOWY_BEACH;
	}

	/** Badlands family — gets red sand over striped terracotta bands rather than the flat sub-surface. */
	public static boolean isBadlands(ResourceKey<Biome> b) {
		return b == Biomes.BADLANDS || b == Biomes.WOODED_BADLANDS || b == Biomes.ERODED_BADLANDS;
	}
}
