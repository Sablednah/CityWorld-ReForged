package me.daddychurchill.CityWorld.Plugins;

import java.util.Random;


import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;

public abstract class OdonymProvider extends Provider {

	public abstract String[] generateFossilOdonym(CityWorldGenerator generator, Odds odds);

	public abstract String[] generateNorthSouthStreetOdonym(CityWorldGenerator generator, int x, int z);

	public abstract String[] generateWestEastStreetOdonym(CityWorldGenerator generator, int x, int z);

	public abstract String generateVillagerName(CityWorldGenerator generator, Odds odds);

	/** Just a given (first) name — for composing a household where members share a surname. */
	public abstract String generateGivenName(CityWorldGenerator generator, Odds odds);

	/** Just a family (sur)name — one per household, shared across its members. */
	public abstract String generateSurname(CityWorldGenerator generator, Odds odds);

	/**
	 * A role-themed name for an employed villager — a given name plus an occupational surname fitting the
	 * trade (a fletcher becomes "Alice Fletcher", a fisher "Bob Angler"). {@code professionPath} is the
	 * vanilla profession id path ({@code fletcher}, {@code farmer}, {@code fisherman}, …).
	 */
	public abstract String generateWorkerName(CityWorldGenerator generator, Odds odds, String professionPath);

	/**
	 * A random shop name for a hanging shopfront sign, given the trade's display label (e.g. "Fletcher",
	 * "Map seller"). Returns the sign's lines — a shop name over the trade, drawn from the same villager
	 * name pools as everything else ("Cooper & Sons", "The Golden Fletcher", "Vance's").
	 */
	public abstract String[] generateShopName(CityWorldGenerator generator, Odds odds, String tradeLabel);

	// Upstream also declares read/write(ConfigurationSection) here, so a world can supply its own
	// word lists. Config is P7; the names generated below are what an unconfigured world uses.

	// yep it is a little one... we will make it bigger in a moment
	private final int baseSeed;

	OdonymProvider(int baseSeed) {
		super();
		this.baseSeed = baseSeed;
	}

	Random getRandomFor(int i) {
		return new Random((long) i * (long) Integer.MAX_VALUE + (long) baseSeed);
	}

	public void decaySign(Odds odds, String[] text) {
		for (int i = 0; i < text.length; i++) {
			text[i] = decayLine(odds, text[i]);
		}
	}

	private final static double oddsOfDecay = Odds.oddsExtremelyLikely;

	private String decayLine(Odds odds, String line) {
		StringBuilder result = new StringBuilder();
		for (int i = 0; i < line.length(); i++) {
			if (odds.playOdds(oddsOfDecay))
				result.append(line.charAt(i));
			else
				result.append(' ');
		}
		return result.toString();
	}

	// Based on work contributed by drew-bahrue
	// (https://github.com/echurchill/CityWorld/pull/2)
	public static OdonymProvider loadProvider(CityWorldGenerator generator, Odds odds) {

		OdonymProvider provider = null;

//		// need something like PhatLoot but for Odonym
//		provider = OdonymProvider_PhatOdonym.loadPhatOdonym();

		// default to stock OreProvider
		if (provider == null) {

//			if (generator.settings.environment == Environment.NETHER)
//				provider = new NameProvider_Nether(random);
//			else if (generator.settings.environment == Environment.THE_END)
//				provider = new NameProvider_TheEnd(random);
//			else
			provider = new OdonymProvider_Normal(odds.getRandomInt(), generator.getSettings());
		}

		return provider;
	}

}
