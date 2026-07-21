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

	/** Classify the climate at the centre of the given chunk. */
	public static ClimateZone at(CityWorldGenerator generator, int chunkX, int chunkZ) {
		int x = chunkX * 16 + 8;
		int z = chunkZ * 16 + 8;
		double t = generator.getTemperature(x, z);
		double h = generator.getHumidity(x, z);

		Temp tt = t < 0.35 ? Temp.COLD : t < 0.6 ? Temp.TEMPERATE : t < 0.8 ? Temp.WARM : Temp.HOT;
		Humid hh = h < 0.4 ? Humid.DRY : h > 0.65 ? Humid.WET : Humid.NORMAL;
		return new ClimateZone(tt, hh);
	}

	public boolean cold() { return temp == Temp.COLD; }
	public boolean hot() { return temp == Temp.HOT; }
	public boolean dry() { return humid == Humid.DRY; }
	public boolean wet() { return humid == Humid.WET; }
}
