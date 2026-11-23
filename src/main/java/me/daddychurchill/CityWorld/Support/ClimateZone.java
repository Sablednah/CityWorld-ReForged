package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;

/**
 * The MODERN biome-climate at a chunk, bucketed into coarse temperature/humidity bands so in-city
 * lots (parks, farms) can pick plants and soil that fit the biome they sit in.
 *
 * <p>The thresholds mirror {@code CityWorldClimateBiomeSource} (cold {@literal <} 0.35, temperate
 * {@literal <} 0.6, warm {@literal <} 0.8, else hot; dry {@literal <} 0.4, wet {@literal >} 0.65)
 * so a farm reads as savanna-ish exactly where the surrounding wild is a savanna. It's only
 * meaningful in MODERN — CLASSIC lots never consult it and keep their original behaviour.
 */
public final class ClimateZone {

	public enum Temp { COLD, TEMPERATE, WARM, HOT }

	public enum Humid { DRY, NORMAL, WET }

	public final Temp temp;
	public final Humid humid;

	private ClimateZone(Temp temp, Humid humid) {
		this.temp = temp;
		this.humid = humid;
	}

	/**
	 * Classify the climate at the centre of the given chunk. On vanilla land the climate is the real biome's (the
	 * one the chunk's city stands in), not CityWorld's own climate map, which that land does not follow: a park came
	 * out under snow in a jungle (the owner's Earth, 2026-11-23).
	 */
	public static ClimateZone at(CityWorldGenerator generator, int chunkX, int chunkZ) {
		if (generator.vanillaLand) {
			var biome = generator.buildingBiomeAt(chunkX, chunkZ);
			if (biome != null)
				return of(biome.value());
		}
		int x = chunkX * 16 + 8;
		int z = chunkZ * 16 + 8;
		double t = generator.getTemperature(x, z);
		double h = generator.getHumidity(x, z);

		Temp tt = t < 0.35 ? Temp.COLD : t < 0.6 ? Temp.TEMPERATE : t < 0.8 ? Temp.WARM : Temp.HOT;
		Humid hh = h < 0.4 ? Humid.DRY : h > 0.65 ? Humid.WET : Humid.NORMAL;
		return new ClimateZone(tt, hh);
	}

	/**
	 * A biome's own climate in the same bands, by vanilla's numbers: snowy plains 0.0 and taiga 0.25 are cold,
	 * plains and forest (0.7–0.8) temperate, jungle (0.95) warm, desert, savanna and badlands (2.0) hot; a
	 * downfall of 0 is dry (the last three), 0.8 and up wet (taiga, jungle, swamp, forest).
	 */
	public static ClimateZone of(net.minecraft.world.level.biome.Biome biome) {
		float t = biome.getBaseTemperature();
		float h = biome.getModifiedClimateSettings().downfall();
		Temp tt = t < 0.3F ? Temp.COLD : t < 0.9F ? Temp.TEMPERATE : t < 1.5F ? Temp.WARM : Temp.HOT;
		Humid hh = h < 0.3F ? Humid.DRY : h >= 0.8F ? Humid.WET : Humid.NORMAL;
		return new ClimateZone(tt, hh);
	}

	public boolean cold() { return temp == Temp.COLD; }
	public boolean hot() { return temp == Temp.HOT; }
	public boolean dry() { return humid == Humid.DRY; }
	public boolean wet() { return humid == Humid.WET; }
}
