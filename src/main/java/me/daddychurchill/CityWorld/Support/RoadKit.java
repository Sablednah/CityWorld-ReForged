package me.daddychurchill.CityWorld.Support;

import java.util.Collections;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.compat.BlockFace;
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
 *
 * <p><b>Ultimate Car Mod</b> ({@code car:asphalt}) keeps its markings as thin "paint" blocks in the cell ABOVE the
 * road ({@code car:line_middle}, {@code car:arrow_zebras}…, each turned by {@code facing}), has no manhole or kerb
 * lip, and its signs are text: a {@code car:sign} on a {@code car:sign_post} with the words in its block entity. The
 * kit knows which it holds ({@link #paint}) and {@link #line}, {@link #zebra} and {@link #textSign} draw the right
 * way for either, so the road's own code never asks.
 */
public final class RoadKit {

    public final Material surface, brokenLine, crosswalk, manhole, kerb, rod;
    /** Markings are paint blocks over the surface, and signs are text (Ultimate Car Mod). */
    public final boolean paint;
    private final Material textSignBlock;
    private final String namespace;

    private RoadKit(Material surface, String id) {
        this.surface = surface;
        this.namespace = id.substring(0, id.indexOf(':'));
        this.paint = id.equals("car:asphalt");
        if (paint) {
            this.brokenLine = piece("car:line_middle"); // a dash a cell, its gaps at the cell's ends
            this.crosswalk = piece("car:arrow_zebras"); // one band a cell, across the cell: a stripe of the zebra
            this.manhole = null;
            this.kerb = null;
            this.rod = piece("car:sign_post");
            this.textSignBlock = piece("car:sign");
        } else {
            // the edge-dashed line, not the "center" one: that stripe sits 3-6 px from one edge of its block, and a
            // one-column line on a twelve-wide road can only sit on the centre when the stripe hugs the column's edge
            this.brokenLine = piece(id + "_broken_line");
            this.crosswalk = piece(id + "_crosswalk");
            this.manhole = piece(id + "_manhole");
            this.kerb = piece(namespace + ":sidewalk_step");
            this.rod = piece(namespace + ":roadsign_rod");
            this.textSignBlock = null;
        }
    }

    /**
     * The centre line's dash at a road cell: Macaw's edge-dashed block in the cell itself (facing south on a
     * north-south road puts its stripe on the column's east edge, the road's centre line between columns 7 and 8;
     * west on an east-west road), a paint dash over the surface (its dashes run with the road) otherwise.
     */
    public void line(RealBlocks chunk, int x, int y, int z, boolean roadRunsNS) {
        if (brokenLine == null)
            return;
        if (paint) {
            chunk.setBlock(x, y, z, surface);
            chunk.setBlockState(x, y + 1, z, brokenLine.withFacing(roadRunsNS ? BlockFace.NORTH : BlockFace.EAST));
        } else
            chunk.setBlockState(x, y, z, brokenLine.withFacing(roadRunsNS ? BlockFace.SOUTH : BlockFace.WEST));
    }

    /**
     * A zebra cell on a crossing arm: Macaw's block, its stripes along the road; a paint band over the surface,
     * laid along the road in every cell of the arm so the bands and their gaps make the stripes.
     */
    public void zebra(RealBlocks chunk, int x, int y, int z, boolean roadRunsNS) {
        if (crosswalk == null)
            return;
        if (paint) {
            chunk.setBlock(x, y, z, surface);
            chunk.setBlockState(x, y + 1, z, crosswalk.withFacing(roadRunsNS ? BlockFace.EAST : BlockFace.NORTH));
        } else
            chunk.setBlockState(x, y, z, crosswalk.withFacing(roadRunsNS ? BlockFace.NORTH : BlockFace.EAST));
    }

    /** Whether the kit's signs are text on a post (Ultimate Car Mod) rather than pictures by name. */
    public boolean textSigns() {
        return textSignBlock != null;
    }

    /**
     * A text sign at a cell (the chunk being generated), turned to {@code facing}, the lines on both of its faces
     * (four a face, the mod draws about eighteen characters each). False when the kit has no text sign.
     */
    public boolean textSign(RealBlocks chunk, int x, int y, int z, BlockFace facing, String... lines) {
        if (textSignBlock == null)
            return false;
        var access = chunk.getWorld().getChunk(chunk.sectionX, chunk.sectionZ);
        if (!(access instanceof net.minecraft.world.level.chunk.ProtoChunk proto)
                || access instanceof net.minecraft.world.level.chunk.ImposterProtoChunk)
            return false;
        chunk.setBlockState(x, y, z, textSignBlock.withFacing(facing));
        int lift = me.daddychurchill.CityWorld.worldgen.ShiftedRegion.shiftOf(chunk.getWorld());
        net.minecraft.nbt.CompoundTag tag = new net.minecraft.nbt.CompoundTag();
        tag.putString("id", "car:sign");
        tag.putInt("x", chunk.getBlockX(x));
        tag.putInt("y", y + lift);
        tag.putInt("z", chunk.getBlockZ(z));
        for (int i = 0; i < 4; i++) {
            String line = i < lines.length && lines[i] != null ? lines[i] : "";
            if (line.length() > 20)
                line = line.substring(0, 20);
            tag.putString("text" + i, line);
            tag.putString("text" + (i + 4), line);
        }
        proto.setBlockEntityNbt(tag);
        return true;
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

    /** Whether a block is one of the kit's mod's (a sign or its rod): what a sweep of the road leaves standing. */
    public boolean owns(net.minecraft.world.level.block.state.BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals(namespace);
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
