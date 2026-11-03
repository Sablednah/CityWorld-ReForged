package me.daddychurchill.CityWorld.Support;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.core.registries.BuiltInRegistries;

/**
 * A road's dressing from a road mod, derived from ONE pool pick: the surface block in {@code #cityworld:fittings/road}
 * (Macaw's Roads and Streets ships {@code mcwroads:asphalt}), and from its id the matching pieces by name —
 * {@code <surface>_broken_line}, {@code <surface>_crosswalk}, {@code <surface>_manhole}, the namespace's
 * {@code sidewalk_step} kerb, {@code roadsign_rod}, and any sign by name ({@code stop}, {@code dead_end},
 * {@code speedlimit_30}, {@code city_1_sign}…). One pick per world, so every street matches; null without the mod,
 * and every lot then draws what it drew before.
 *
 * <p>Deriving by name rather than a tag per piece is deliberate: a road mod names its pieces after its surface, and
 * a pack that wants another mod's road adds its surface to the tag and gets whatever pieces that mod names the same
 * way — a missing piece simply is not drawn.
 */
public final class RoadKit {

    public final Material surface, brokenLine, crosswalk, manhole, kerb, rod;
    private final String namespace;

    private RoadKit(Material surface, String id) {
        this.surface = surface;
        this.namespace = id.substring(0, id.indexOf(':'));
        // the edge-dashed line, not the "center" one: that stripe sits 3-6 px from one edge of its block, and a
        // one-column line on a twelve-wide road can only sit on the centre when the stripe hugs the column's edge
        this.brokenLine = piece(id + "_broken_line");
        this.crosswalk = piece(id + "_crosswalk");
        this.manhole = piece(id + "_manhole");
        this.kerb = piece(namespace + ":sidewalk_step");
        this.rod = piece(namespace + ":roadsign_rod");
    }

    /** One kit per generator (the pool is read once per world, not once per road chunk); never a static per seed. */
    private static final Map<CityWorldGenerator, Optional<RoadKit>> KITS =
            Collections.synchronizedMap(new WeakHashMap<>());

    /** The kit for this world, or null when the pool is empty (no road mod). The same pick for every road chunk. */
    public static RoadKit forWorld(CityWorldGenerator generator) {
        return KITS.computeIfAbsent(generator, g -> Optional.ofNullable(pick(g.getWorldSeed()))).orElse(null);
    }

    private static RoadKit pick(long worldSeed) {
        Material surface = MaterialTags.pick(MaterialTags.FITTINGS_ROAD, new Odds(worldSeed ^ 0x524F414453L), null);
        if (surface == null || surface.getBlock() == null)
            return null;
        var key = BuiltInRegistries.BLOCK.getKey(surface.getBlock());
        return new RoadKit(surface, key.toString());
    }

    /** A sign block of the kit's mod by name, or null if the mod has none of that name. */
    public Material sign(String name) {
        return piece(namespace + ":" + name);
    }

    private static Material piece(String id) {
        Material m = Material.of(id);
        return m == null || m == Material.AIR ? null : m;
    }
}
