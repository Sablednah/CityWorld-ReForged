package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
 * Which <em>cave biome</em> — if any — a point underground belongs to.
 *
 * <p>This is the generalisation of {@code Support/LushCaves}' {@code lushRegion}: a coarse,
 * seed-stable grid of cells, a small percentage of which are "special". Where that one hardcoded
 * mechanic decided <em>whether to place mossy blocks</em>, this decides <em>which biome the column
 * is</em> — a much bigger lever, because a real biome brings vanilla's own cave decoration, its mob
 * list, and (for {@code deep_dark}) the structures gated on it.
 *
 * <p><b>Patches, not bands.</b> The underground stays whatever the surface above it is, except inside
 * these cells. Vanilla fills its whole underground with cave biomes by 3D noise, which would change
 * mob spawning everywhere and make wardens routine; patches keep the change local and keep
 * {@code deep_dark} genuinely rare.
 *
 * <p><b>Cells are 2D, with a Y band.</b> A cell is picked from {@code (x, z)} only and applies just
 * within its own {@code minY..maxY}; above that band the column reverts to its surface biome. That
 * gives a 3D biome map its vertical variation without paying for a 3D noise field, and stacks the pool
 * by depth for free. The first entry whose cell and Y band both match wins, so the rarest and deepest
 * are ordered first.
 *
 * <p><b>Membership comes from the tag {@code #cityworld:cave_pool}, and that is load-bearing twice
 * over.</b>
 * <ul>
 *   <li><b>Cross-version.</b> CityWorld ships one source tree across three Minecraft versions, and
 *       26.2 added {@code minecraft:sulfur_caves} — the only new biome in that drop. A
 *       {@code Biomes.SULFUR_CAVES} constant would exist on 26.2 and nowhere else, so the file could
 *       not be cherry-picked. A tag entry marked {@code "required": false} is simply absent on older
 *       versions. <b>Probing with {@code HolderGetter.get()} instead does not work</b> — during
 *       datapack decode that <em>registers</em> the missing key as an unbound promise and the registry
 *       freeze then fails with "Unbound values in registry". Tags are the mechanism built for this.
 *   <li><b>Data-driven.</b> A datapack — or a mod's — can add a cave biome to the tag with no code
 *       change, which is what the pool was always meant to allow. Anything in the tag without a
 *       {@link #GEOMETRY} entry gets {@link #DEFAULT_GEOMETRY}, so a modded cave biome works from the
 *       tag alone.
 * </ul>
 *
 * <p>Resolution is deliberately <b>lazy</b> ({@link #of}): tags are not bound at codec-decode time, so
 * reading them in a biome source's constructor would see nothing.
 */
public final class CaveRegions {

    private CaveRegions() {
    }

    /** The pool. Ship it with a datapack; add to it with another. */
    public static final TagKey<Biome> CAVE_POOL = TagKey.create(Registries.BIOME,
            Identifier.fromNamespaceAndPath("cityworld", "cave_pool"));

    /**
     * Order of preference, rarest and deepest first — an entry earlier in this list wins a cell it
     * shares with a later one. Biomes in the tag but not named here are appended in tag order.
     */
    private static final List<String> ORDER = List.of(
            "minecraft:deep_dark", "minecraft:sulfur_caves", "minecraft:lush_caves", "minecraft:dripstone_caves");

    /**
     * Per-biome patch geometry: {@code {cell size, percent of cells, minY, maxY}}.
     *
     * <p>{@code deep_dark} sits below {@code y = -24} because that is where vanilla's ancient city
     * generates (measured: a real one spans {@code y -64..-10}). A shallower band would advertise the
     * biome without ever being able to host the structure gated on it — and that measured ceiling of
     * {@code -10} is comfortably below CityWorld's cisterns ({@code y 49}) and sewers ({@code y 57-62}),
     * so those cannot open into a warden's lair. Only the mines reach that deep, which reads as the
     * miners having downed tools when they broke through.
     */
    private static final Map<String, int[]> GEOMETRY = Map.of(
            "minecraft:deep_dark", new int[] { 176, 4, -64, -24 },
            "minecraft:sulfur_caves", new int[] { 112, 5, -64, -8 },
            "minecraft:lush_caves", new int[] { 80, 5, -40, 40 },
            "minecraft:dripstone_caves", new int[] { 96, 6, -60, 20 });

    /** What an unrecognised (modded, datapack-added) cave biome gets: a mid-depth, modest patch. */
    private static final int[] DEFAULT_GEOMETRY = { 96, 5, -60, 20 };

    /** One resolved entry: a biome that exists here, plus how its patches are shaped. */
    private record Patch(Holder<Biome> biome, int cell, int percent, int minY, int maxY, long salt) {
    }

    /**
     * The pool as it exists on this world — resolved once from the tag, then immutable.
     *
     * <p>Held per biome source and built on first use, because tags bind after codec decode.
     */
    public static final class Pool {

        private final List<Patch> patches;

        private Pool(HolderGetter<Biome> biomes) {
            List<Holder<Biome>> members = biomes.get(CAVE_POOL)
                    .<HolderSet<Biome>>map(named -> named)
                    .map(set -> set.stream().map(h -> (Holder<Biome>) h).toList())
                    .orElse(List.of());

            List<Patch> built = new ArrayList<>(members.size());
            // Named order first, so the rare/deep ones win a shared cell...
            for (String id : ORDER)
                members.stream().filter(h -> id.equals(idOf(h))).findFirst()
                        .ifPresent(h -> built.add(patch(h)));
            // ...then anything else the tag carries (modded cave biomes), in tag order.
            for (Holder<Biome> h : members)
                if (!ORDER.contains(idOf(h)))
                    built.add(patch(h));
            this.patches = List.copyOf(built);
        }

        /** Every pool biome available here — these must be in the biome source's {@code possibleBiomes()}. */
        public Stream<Holder<Biome>> biomes() {
            return patches.stream().map(Patch::biome);
        }

        /** True if the tag resolved to nothing — worth failing a self-test over, not a crash. */
        public boolean isEmpty() {
            return patches.isEmpty();
        }

        /**
         * The cave biome at a block position, or {@code null} for "not in a patch — keep the surface
         * biome". Pure and seed-deterministic, so it is safe on any worldgen worker.
         */
        public @Nullable Holder<Biome> at(long worldSeed, int blockX, int blockY, int blockZ) {
            for (Patch patch : patches) {
                if (blockY < patch.minY() || blockY > patch.maxY())
                    continue;
                if (inCell(worldSeed, patch, blockX, blockZ))
                    return patch.biome();
            }
            return null;
        }
    }

    /** Builds the pool for a biome source. Call lazily — not from a constructor; tags aren't bound yet. */
    public static Pool of(HolderGetter<Biome> biomes) {
        return new Pool(biomes);
    }

    /**
     * Whether {@code (x, z)} falls in the patch grid of a named cave biome — without needing a resolved
     * pool, and so without needing a biome registry.
     *
     * <p>Exists for {@code Support.LushCaves}, CityWorld's own hand-built lush decoration (axolotl
     * pools, spore blossoms, the surface azalea tell). That pass predates real cave biomes and had its
     * own region function, which would have scattered hand-decorated lush caves across cells that are
     * <em>not</em> the lush biome — two disjoint sets of lush-looking places, only one of them labelled
     * lush. Pointing both at the same grid makes the biome and the hand decoration agree, so a lush
     * patch gets vanilla's vegetation and CityWorld's extras in the same cave.
     *
     * @return false for a biome with no geometry entry, since there is no grid to be in
     */
    public static boolean inCellOf(String biomeId, long worldSeed, int blockX, int blockZ) {
        int[] g = GEOMETRY.get(biomeId);
        if (g == null)
            return false;
        return inCell(worldSeed, new Patch(null, g[0], g[1], g[2], g[3], biomeId.hashCode() * 0x9E3779B97F4A7C15L),
                blockX, blockZ);
    }

    private static String idOf(Holder<Biome> holder) {
        return holder.unwrapKey().map(k -> k.identifier().toString()).orElse("");
    }

    private static Patch patch(Holder<Biome> biome) {
        String id = idOf(biome);
        int[] g = GEOMETRY.getOrDefault(id, DEFAULT_GEOMETRY);
        // Salt from the biome's own id, so every type — including one a datapack adds — gets an
        // independent cell grid without anyone having to invent a constant for it.
        return new Patch(biome, g[0], g[1], g[2], g[3], id.hashCode() * 0x9E3779B97F4A7C15L);
    }

    /**
     * Coarse, seed-stable cell test — the same shape as {@code LushCaves.lushRegion}, with the patch's
     * own salt folded in so the pool's grids don't line up on top of each other.
     */
    private static boolean inCell(long worldSeed, Patch patch, int blockX, int blockZ) {
        long h = (long) Math.floorDiv(blockX, patch.cell()) * 341873128712L
                + (long) Math.floorDiv(blockZ, patch.cell()) * 132897987541L;
        return Math.floorMod(worldSeed + patch.salt() + h, 100) < patch.percent();
    }
}
