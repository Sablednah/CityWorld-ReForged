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
	/**
	 * Ground tags, so a datapack can give a modded biome a signature block.
	 *
	 * <p><b>Labelling a column is not the same as building it.</b> CityWorld lays its own surface and
	 * never runs a biome's surface rules, so tagging a shore column {@code gravel_beach} changed the
	 * fog, the water tint and the mob list — and left the ground sand. The biome was "working" and
	 * invisible at the same time. These tags close that gap: membership decides the block, so any
	 * modded biome can look like itself without CityWorld knowing its name.
	 */
	public static final net.minecraft.tags.TagKey<Biome> GROUND_GRAVEL = groundTag("gravel");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_SAND = groundTag("sand");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_RED_SAND = groundTag("red_sand");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_STONE = groundTag("stone");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_PODZOL = groundTag("podzol");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_COARSE_DIRT = groundTag("coarse_dirt");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_TERRACOTTA = groundTag("terracotta");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_BASALT = groundTag("basalt");
	public static final net.minecraft.tags.TagKey<Biome> GROUND_MUD = groundTag("mud");

	private static net.minecraft.tags.TagKey<Biome> groundTag(String name) {
		return net.minecraft.tags.TagKey.create(net.minecraft.core.registries.Registries.BIOME,
				net.minecraft.resources.Identifier.fromNamespaceAndPath("cityworld", "ground/" + name));
	}

	/**
	 * The surface block for a biome, consulting the ground tags first.
	 *
	 * <p>Tags win over the built-in vanilla answers so a pack can retune those too; a biome in no tag
	 * falls through to the hardcoded map exactly as before.
	 */
	public static Material surface(net.minecraft.core.Holder<Biome> holder, ResourceKey<Biome> b) {
		// The data map wins: it is the only mechanism that can name a block CityWorld does not know,
		// so a pack using it has been more specific than any tag can be.
		var ground = me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.groundFor(holder);
		if (ground != null)
			return Material.of(ground.surface());
		if (holder != null) {
			if (holder.is(GROUND_GRAVEL))
				return Material.GRAVEL;
			if (holder.is(GROUND_SAND))
				return Material.SAND;
			if (holder.is(GROUND_RED_SAND))
				return Material.RED_SAND;
			if (holder.is(GROUND_STONE))
				return Material.STONE;
			if (holder.is(GROUND_PODZOL))
				return Material.PODZOL;
			if (holder.is(GROUND_COARSE_DIRT))
				return Material.COARSE_DIRT;
			if (holder.is(GROUND_TERRACOTTA))
				return Material.TERRACOTTA;
			if (holder.is(GROUND_BASALT))
				return Material.SMOOTH_BASALT;
			if (holder.is(GROUND_MUD))
				return Material.MUD;
		}
		return surface(b);
	}

	/** Subsurface for a tagged biome: gravel and stone sit on stone, sands on their sandstone. */
	public static Material subsurface(net.minecraft.core.Holder<Biome> holder, ResourceKey<Biome> b) {
		var ground = me.daddychurchill.CityWorld.worldgen.CityWorldDataMaps.groundFor(holder);
		if (ground != null && ground.subsurface().isPresent())
			return Material.of(ground.subsurface().get());
		if (holder != null) {
			if (holder.is(GROUND_GRAVEL) || holder.is(GROUND_STONE))
				return Material.STONE;
			if (holder.is(GROUND_SAND))
				return Material.SANDSTONE;
			if (holder.is(GROUND_RED_SAND))
				return Material.RED_SANDSTONE;
			if (holder.is(GROUND_BASALT))
				return Material.BASALT;
			if (holder.is(GROUND_TERRACOTTA))
				return Material.TERRACOTTA;
			// podzol and coarse dirt sit on ordinary dirt, as they do in vanilla forests
			if (holder.is(GROUND_PODZOL) || holder.is(GROUND_COARSE_DIRT))
				return Material.DIRT;
		}
		return subsurface(b);
	}

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
	/**
	 * Biomes whose ground carries a dusting of snow.
	 *
	 * <p>{@code GROVE} is here for a reason worth stating: the surface provider caps high ground with
	 * snow and ice by <em>elevation</em>, which covers the peaks and snowy slopes without anyone naming
	 * them — but a grove is a snowy forest at ordinary height, so it fell between the two and generated
	 * as plain grass.
	 */
	public static boolean snowy(ResourceKey<Biome> b) {
		return b == Biomes.SNOWY_PLAINS || b == Biomes.SNOWY_TAIGA || b == Biomes.SNOWY_BEACH
				|| b == Biomes.GROVE;
	}

	/** Badlands family — gets red sand over striped terracotta bands rather than the flat sub-surface. */
	public static boolean isBadlands(ResourceKey<Biome> b) {
		return b == Biomes.BADLANDS || b == Biomes.WOODED_BADLANDS || b == Biomes.ERODED_BADLANDS;
	}
}
