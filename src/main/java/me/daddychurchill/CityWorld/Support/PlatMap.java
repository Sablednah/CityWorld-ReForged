package me.daddychurchill.CityWorld.Support;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Context.DataContext;
import me.daddychurchill.CityWorld.Plugins.ShapeProvider;

/**
 * Stub of the original {@code PlatMap} (548 lines) — a 10×10 grid of chunks, and the unit of city
 * planning.
 *
 * <p><b>Wave 1 cycle-breaker.</b> Only the members {@code ShapeProvider} reaches for are here.
 * Everything {@code PlatMap} really does — laying out roads and lots across the grid — is the city
 * planning half of the brain and lands in wave 2, alongside {@code PlatLot}'s subclasses and the
 * contexts.
 *
 * <p>Worth carrying forward when that happens: {@code PlatMap} is <b>seed-deterministic</b>, which
 * is what makes it safe under the modern multithreaded chunk pipeline and what makes per-chunk
 * regeneration viable at P5. Whatever replaces this must keep that property (PORTING.md, top
 * risk #1).
 */
public class PlatMap {

	/** A platmap is Width×Width chunks. Carried over verbatim, name included. */
	public static final int Width = 10;

	public final CityWorldGenerator generator;
	public final int originX;
	public final int originZ;
	public DataContext context;

	public PlatMap(CityWorldGenerator generator, ShapeProvider shapeProvider, int originX, int originZ) {
		super();

		// populate the instance data
		this.generator = generator;
		this.originX = originX;
		this.originZ = originZ;

		// do the deed
		shapeProvider.populateLots(generator, this);

		// Upstream also allocates the Width×Width PlatLot grid here and recycles the empty lots
		// afterwards. That grid is the city planning half of the brain — wave 2.
	}

	/**
	 * How much of this platmap is still natural (0.0 = fully built up, 1.0 = untouched) — upstream
	 * grades this into the ladder that picks a context, so it is what decides whether a platmap
	 * becomes downtown or farmland.
	 *
	 * <p>Upstream computes {@code naturalPlats / (Width * Width) + settings.ruralnessLevel} off the
	 * lot grid. With no grid yet nothing has been built, so wave 1 answers fully-natural. Nothing
	 * reads it either way while {@code getContext} is stubbed — see {@code ShapeProvider_Normal}.
	 */
	public double getNaturePercent() {
		return 1.0;
	}

    public Odds getOddsGenerator() {
        return generator.shapeProvider.getMacroOddsGeneratorAt(originX, originZ);
    }

    /** No-op until road planning is ported (wave 2). */
    public void populateRoads() {
    }

    /** No-op until road planning is ported (wave 2). */
    public void validateRoads() {
    }
}
