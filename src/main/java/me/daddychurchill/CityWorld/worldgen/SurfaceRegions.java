package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.Stream;

import net.minecraft.core.Holder;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.HolderSet;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;

import org.jspecify.annotations.Nullable;

/**
 * Surface biomes that get a patch of ground <b>regardless of whether they win the climate lookup</b>.
 *
 * <p><b>Why this exists, measured rather than assumed.</b> With Biomes O' Plenty installed, 54 of its
 * 59 biomes generate through the ordinary climate route. A handful never do — not because CityWorld
 * cannot express their climate, but because some other biome always sits closer in vanilla's
 * seven-dimensional nearest-match. {@code moddedBiomeShare} cannot reach them either: turning it to
 * {@code 1.0} removes vanilla from the contest and they still lose, this time to other modded biomes.
 * Widening an axis cannot help when there is no gap to widen, and a share cannot help when the biome
 * loses every contest it enters. The only remaining answer is to stop holding a contest.
 *
 * <p>So this is the same shape as {@link CaveRegions}, above ground: a seed-stable grid of cells, a
 * small percentage of which belong to a pool biome outright. Membership comes from the
 * {@code #cityworld:surface_pool} tag, so a pack can hand any biome a route without code — the point
 * being that the next biome mod gets the same lever without CityWorld naming it.
 *
 * <p><b>Patches still respect the biome's own climate.</b> A cell is only claimed where the column's
 * climate falls inside the ranges <em>that biome itself declared</em> to TerraBlender, widened by a
 * slack. Nothing is hardcoded: the ranges are the mod author's own answer to "where does this belong",
 * harvested along with the biome. Without that gate a bog would appear in deserts, which is a worse
 * result than the bog never appearing at all.
 */
public final class SurfaceRegions {

    private SurfaceRegions() {}

    /** Biomes that get a patch whether or not the climate lookup would ever pick them. */
    public static final TagKey<Biome> SURFACE_POOL = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("cityworld", "surface_pool"));

    /**
     * Beach and shore variants that may stand in for CityWorld's own beach.
     *
     * <p>A separate pool because shores are a separate problem. CityWorld decides them from terrain —
     * a column at or just below sea level <em>is</em> a beach — and the modded climate lookup is
     * deliberately gated to land above the waterline, since a climate guess about a coastline would
     * only get wrong what the terrain already knows exactly. So a biome like BoP's {@code gravel_beach}
     * is not out-competed, it is never asked about: no amount of share or patching on the surface pool
     * would ever produce it. Substituting on ground CityWorld has already ruled a shore keeps the
     * terrain's answer and changes only the label.
     */
    public static final TagKey<Biome> SHORE_POOL = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("cityworld", "shore_pool"));

    /**
     * Ocean variants that may stand in for CityWorld's own ocean and deep ocean.
     *
     * <p>Same reasoning as the shore pool, one band lower. CityWorld picks its oceans from terrain
     * depth and temperature, and the modded climate lookup only runs above the waterline — so a mod's
     * ocean biome could never be chosen, however well it declared itself. Biomes O' Plenty adds no
     * overworld oceans, so this ships empty; it exists because "modded oceans are silently impossible"
     * is a gap worth closing for the mods that do add them, not a BoP-shaped one.
     */
    public static final TagKey<Biome> OCEAN_POOL = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("cityworld", "ocean_pool"));

    /**
     * Patch grid, in blocks, and the share of cells a pool biome claims.
     *
     * <p>Bigger cells than the cave pool's (which is underground, where a patch is a cave system) —
     * a surface biome wants to read as a region you walk into. {@value #PERCENT}% each keeps the pool
     * a garnish: with three biomes in it that is under a quarter of the ground, and the climate gate
     * cuts it further.
     */
    private static final int CELL = 384;
    private static final int PERCENT = 7;

    /** How far outside its declared climate a patch may still sit, in raw climate units. */
    private static final float SLACK = 0.15F;

    private record Patch(Holder<Biome> biome, long salt) {
    }

    /** The pool as it exists on this world — resolved once from the tag, then immutable. */
    public static final class Pool {

        private final List<Patch> patches;

        private Pool(HolderGetter<Biome> biomes, TagKey<Biome> tag) {
            List<Patch> built = new ArrayList<>();
            biomes.get(tag)
                    .<HolderSet<Biome>>map(named -> named)
                    .ifPresent(set -> set.forEach(h -> built.add(new Patch(h, saltFor(idOf(h))))));
            this.patches = List.copyOf(built);
        }

        /** Every pool biome — these must be in the source's {@code possibleBiomes()} or they generate bare. */
        public Stream<Holder<Biome>> biomes() {
            return patches.stream().map(Patch::biome);
        }

        public boolean isEmpty() {
            return patches.isEmpty();
        }

        /**
         * The pool biome owning this column, or {@code null}.
         *
         * <p>{@code climateAllows} is asked only for a biome whose cell already matched, so the
         * expensive half runs on a few percent of columns rather than all of them.
         */
        public @Nullable Holder<Biome> at(long worldSeed, int blockX, int blockZ,
                Predicate<Holder<Biome>> climateAllows) {
            for (Patch patch : patches)
                if (inCell(worldSeed, patch, blockX, blockZ) && climateAllows.test(patch.biome()))
                    return patch.biome();
            return null;
        }
    }

    /** All three pools for a world, resolved together. */
    public record Pools(Pool surface, Pool shore, Pool ocean) {

        public Stream<Holder<Biome>> biomes() {
            return Stream.concat(surface.biomes(), Stream.concat(shore.biomes(), ocean.biomes()));
        }
    }

    public static Pools of(HolderGetter<Biome> biomes) {
        return new Pools(new Pool(biomes, SURFACE_POOL), new Pool(biomes, SHORE_POOL),
                new Pool(biomes, OCEAN_POOL));
    }

    /**
     * Whether this climate suits the biome: <b>temperature and humidity only</b>, plus {@value #SLACK}
     * slack.
     *
     * <p><b>Gating on all five axes was measured and produced nothing at all.</b> The analysis that
     * justified this pool said these biomes "overlap CityWorld's ranges on every axis" — but that is
     * <em>marginal</em> overlap, measured one axis at a time. Requiring a column to sit inside the
     * biome's box on all five axes simultaneously is a far stronger condition, and no column satisfied
     * it: every patch was rejected and the ground was unchanged. Overlapping on each axis separately
     * says nothing about whether any single point lands inside the joint box.
     *
     * <p>Temperature and humidity are the right two to keep. They are what a player reads off a
     * landscape — a bog in a desert is the failure worth preventing — while continentalness, erosion
     * and weirdness are terrain-shape axes that CityWorld derives on its own terms and which say little
     * about whether a biome looks at home. Depth is skipped for the same reason it always was: this is
     * a surface lookup.
     */
    public static boolean climateFits(net.minecraft.world.level.biome.Climate.ParameterPoint point,
            float temperature, float humidity, float continentalness, float erosion, float weirdness) {
        return within(point.temperature(), temperature) && within(point.humidity(), humidity);
    }

    private static boolean within(net.minecraft.world.level.biome.Climate.Parameter range, float value) {
        long q = net.minecraft.world.level.biome.Climate.quantizeCoord(value);
        long slack = net.minecraft.world.level.biome.Climate.quantizeCoord(SLACK)
                - net.minecraft.world.level.biome.Climate.quantizeCoord(0.0F);
        return q >= range.min() - slack && q <= range.max() + slack;
    }

    private static String idOf(Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    /** Salt from the biome's own id, so each gets an independent grid without a hand-picked constant. */
    private static long saltFor(String biomeId) {
        return biomeId.hashCode() * 0x9E3779B97F4A7C15L;
    }

    private static boolean inCell(long worldSeed, Patch patch, int blockX, int blockZ) {
        long h = (long) Math.floorDiv(blockX, CELL) * 341873128712L
                + (long) Math.floorDiv(blockZ, CELL) * 132897987541L;
        return Math.floorMod(worldSeed + patch.salt() + h, 100) < PERCENT;
    }
}
