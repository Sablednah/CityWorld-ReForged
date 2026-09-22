package me.daddychurchill.CityWorld.worldgen;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;

import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraftforge.event.AddReloadListenerEvent;

import org.jetbrains.annotations.Nullable;

/**
 * The biome → ground-block map: what a biome's surface is made of.
 *
 * <p><b>Why a data map rather than more tags.</b> The ground tags
 * ({@code #cityworld:ground/gravel} and friends) map a biome onto one of a fixed set of <em>vanilla</em>
 * materials, which is fine until a mod's biome is defined by a block only that mod has. Biomes O'
 * Plenty's {@code lush_desert} wants its own orange sand, {@code wasteland} its dried salt,
 * {@code origin_valley} its own grass — none of which a tag named after a vanilla block can express.
 * The tags would force a stand-in and the biome would look approximately right forever.
 *
 * <p>A data map carries a <em>value</em> per biome, so the answer can be any block id at all,
 * including one CityWorld has never heard of. Entries live in
 * {@code data/<namespace>/data_maps/worldgen/biome/ground.json} and a pack can add or override any of
 * them.
 *
 * <p><b>Unknown biome keys are harmless</b> — a key is just an id and simply never matches — but an
 * unknown <em>block</em> id in the value would fail the file, so entries for a mod's blocks carry
 * {@code neoforge:conditions} on {@code mod_loaded}. That is the data-map equivalent of a tag's
 * {@code "required": false}, and it matters: a missing required reference takes the whole file with
 * it.
 */
public final class CityWorldDataMaps {

    private CityWorldDataMaps() {}

    /**
     * A biome's ground: the block at the surface, and optionally what sits under it.
     *
     * <p>{@code subsurface} is optional because most biomes want the sensible default (sand over
     * sandstone, podzol over dirt) and only a few care.
     */
    public record Ground(Block surface, Optional<Block> subsurface) {

        public static final Codec<Ground> CODEC = RecordCodecBuilder.create(i -> i.group(
                BuiltInRegistries.BLOCK.byNameCodec().fieldOf("surface").forGetter(Ground::surface),
                BuiltInRegistries.BLOCK.byNameCodec().optionalFieldOf("subsurface").forGetter(Ground::subsurface)
        ).apply(i, Ground::new));
    }

    /**
     * Forge 1.20.1 has no data maps — they are a NeoForge feature — so the very same JSON is read by
     * a reload listener instead, one per directory, so any pack can still contribute entries. The
     * accessors below keep their signatures, so no caller can tell the difference.
     */
    private static volatile Map<ResourceLocation, Ground> GROUND_DATA = Map.of();

    /**
     * How a furniture block's {@code facing} relates to the way its front points, in degrees
     * clockwise — plus how many blocks the piece occupies.
     *
     * <p>The offset is needed because furniture mods disagree, and measurably so: Macaw's chair uses
     * {@code facing} for the direction the sitter looks, Macaw's <em>sofa</em> is 90° off that, and
     * Refurbished uses it for the direction the backrest points. A boolean "front or back?" cannot
     * express three conventions, and one mod contradicting itself (classic chair 0, modern chair 180)
     * rules out declaring it per mod.
     *
     * <p>{@code parts} marks bed-like two-block furniture (the Refurbished baths): {@code type=bottom}
     * at the anchor, {@code type=head} one cell along {@code facing}, both halves sharing the facing —
     * measured from {@code BathBlock.setPlacedBy}, and exactly the vanilla bed contract.
     */
    public record Facing(int facingOffset, int parts, List<Part> layout, Map<String, String> props,
            List<String> vary, String indexProperty, boolean reconnect) {

        public static final Codec<Facing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("facingOffset", 0).forGetter(Facing::facingOffset),
                Codec.INT.optionalFieldOf("parts", 1).forGetter(Facing::parts),
                Part.CODEC.listOf().optionalFieldOf("layout", List.of()).forGetter(Facing::layout),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("props", Map.of())
                        .forGetter(Facing::props),
                Codec.STRING.listOf().optionalFieldOf("vary", List.of()).forGetter(Facing::vary),
                Codec.STRING.optionalFieldOf("indexProperty", "multi_block_index").forGetter(Facing::indexProperty),
                Codec.BOOL.optionalFieldOf("reconnect", false).forGetter(Facing::reconnect)
        ).apply(i, Facing::new));

        /** The single-cell, undeclared piece. */
        public static final Facing NONE = new Facing(0, 1, List.of(), Map.of(), List.of(), "multi_block_index",
                false);

        /**
         * The cells this piece occupies, one per part, index {@code i} being the value the piece's
         * index property takes at that cell. Always at least the origin. A legacy {@code parts: 2}
         * declaration (the bed contract: {@code type=bottom} at the anchor, {@code type=head} one
         * cell along facing) is expressed as the same two-part layout.
         */
        public List<Part> cells() {
            if (!layout.isEmpty())
                return layout;
            if (parts == 2)
                return BED_CONTRACT;
            return List.of(Part.ORIGIN);
        }

        private static final List<Part> BED_CONTRACT = List.of(new Part(0, 0, 0, Map.of("type", "bottom")),
                new Part(0, 0, -1, Map.of("type", "head")));

        /** Whether this piece occupies more than one cell. */
        public boolean multiBlock() {
            return cells().size() > 1;
        }
    }

    /**
     * One cell of a multi-block piece, relative to its origin and to the piece's <em>own</em>
     * {@code facing} value: stand where {@code facing} points and look back at the piece —
     * {@code right} is on your right, {@code back} is away from you, {@code up} is up. Written this
     * way because the mods rotate their local layouts by {@code facing}, and so does CityWorld.
     * {@code props} are property values set on this cell only (a bed's {@code part=head}).
     */
    public record Part(int right, int up, int back, Map<String, String> props) {

        public static final Codec<Part> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("right", 0).forGetter(Part::right),
                Codec.INT.optionalFieldOf("up", 0).forGetter(Part::up),
                Codec.INT.optionalFieldOf("back", 0).forGetter(Part::back),
                Codec.unboundedMap(Codec.STRING, Codec.STRING).optionalFieldOf("props", Map.of())
                        .forGetter(Part::props)
        ).apply(i, Part::new));

        public static final Part ORIGIN = new Part(0, 0, 0, Map.of());
    }

    /**
     * How much room a piece takes, in cells along its own right/back/up axes, so a placer can pick
     * something that fits the space it has: a single block is 1×1×1, a vanilla bed 1 wide and 2
     * deep, a wardrobe 2 wide and 3 tall.
     */
    public record Footprint(int width, int depth, int height) {
        public static final Footprint SINGLE = new Footprint(1, 1, 1);

        public boolean fits(int maxWidth, int maxDepth, int maxHeight) {
            return width <= maxWidth && depth <= maxDepth && height <= maxHeight;
        }
    }

    private static volatile Map<ResourceLocation, Facing> FURNITURE_DATA = Map.of();

    /** Registered from {@code CityWorldMod} on the game event bus. */
    public static void register(AddReloadListenerEvent event) {
        event.addListener(loader("data_maps/worldgen/biome", "ground", Ground.CODEC, m -> GROUND_DATA = m));
        event.addListener(loader("data_maps/block", "furniture", Facing.CODEC, m -> FURNITURE_DATA = m));
        event.addListener(loader("data_maps/worldgen/structure", "structure_fit", StructureFit.CODEC,
                m -> STRUCTURE_FIT_DATA = m));
    }

    /**
     * How much room a structure needs, and whether to build ground up to meet it.
     *
     * <p>Mirrors the NeoForge branches' {@code cityworld:structure_fit} data map; this line has no
     * {@code DataMapType}, so it reads the same JSON through the reload listener above. Keep the two
     * in step — the resource file is shared verbatim, only the plumbing differs.
     *
     * <p>{@code clearance} overrides {@link StructureReservations#DEFAULT_CLEARANCE} (never shrinks
     * it); {@code beard} opts a {@code terrain_adaptation: none} structure into shaping. Opt-in only:
     * vanilla's NONE structures either self-level or are deliberately off the ground, and buildCity
     * runs in every dimension.
     */
    public record StructureFit(int clearance, boolean beard) {

        public static final Codec<StructureFit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("clearance", 0).forGetter(StructureFit::clearance),
                Codec.BOOL.optionalFieldOf("beard", false).forGetter(StructureFit::beard)
        ).apply(i, StructureFit::new));
    }

    private static volatile Map<ResourceLocation, StructureFit> STRUCTURE_FIT_DATA = Map.of();

    /** The declaration for this structure, or {@code null} if it has none. */
    public static @Nullable StructureFit fitFor(
            @Nullable Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        if (structure == null)
            return null;
        return structure.unwrapKey().map(key -> STRUCTURE_FIT_DATA.get(key.location())).orElse(null);
    }

    /** Declared clearance in chunks, or {@code fallback} when none is declared. Never shrinks it. */
    public static int clearanceFor(
            @Nullable Holder<net.minecraft.world.level.levelgen.structure.Structure> structure, int fallback) {
        StructureFit fit = fitFor(structure);
        return fit == null || fit.clearance() <= 0 ? fallback : Math.max(fallback, fit.clearance());
    }

    /** Whether this structure opts in to being bearded despite declaring {@code none}. */
    public static boolean beardsAnyway(
            @Nullable Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        StructureFit fit = fitFor(structure);
        return fit != null && fit.beard();
    }

    /**
     * Reads one data-map file out of every pack that has it and decodes its {@code values} block.
     *
     * <p>Two shapes are accepted, because that is what the files contain: a bare value, and a
     * {@code {"neoforge:conditions": …, "value": …}} wrapper. The condition itself needs no
     * evaluating — every entry names a block or biome, so an entry whose id is not in the registry
     * belongs to a mod that is not installed, which is precisely when it should be skipped.
     */
    private static <T> SimpleJsonResourceReloadListener loader(String directory, String file,
            com.mojang.serialization.Codec<T> codec, java.util.function.Consumer<Map<ResourceLocation, T>> sink) {
        return new SimpleJsonResourceReloadListener(new com.google.gson.GsonBuilder().create(), directory) {
            @Override
            protected void apply(Map<ResourceLocation, JsonElement> files, ResourceManager manager,
                    ProfilerFiller profiler) {
                Map<ResourceLocation, T> out = new java.util.HashMap<>();
                files.forEach((id, element) -> {
                    if (!id.getPath().equals(file) || !element.isJsonObject())
                        return;
                    JsonElement values = element.getAsJsonObject().get("values");
                    if (values == null || !values.isJsonObject())
                        return;
                    for (Map.Entry<String, JsonElement> entry : values.getAsJsonObject().entrySet()) {
                        JsonElement value = entry.getValue();
                        if (value.isJsonObject() && value.getAsJsonObject().has("value"))
                            value = value.getAsJsonObject().get("value");
                        codec.parse(JsonOps.INSTANCE, value).result()
                                .ifPresent(decoded -> out.put(new ResourceLocation(entry.getKey()), decoded));
                    }
                });
                sink.accept(Map.copyOf(out));
            }
        };
    }

    /** The declared facing offset for a furniture block, or {@code 0} if it declares none. */
    public static int facingOffsetFor(me.daddychurchill.CityWorld.compat.Material piece) {
        Facing facing = furnitureDataFor(piece);
        return facing == null ? 0 : facing.facingOffset();
    }

    /** How many blocks this furniture piece occupies — {@code 1} unless declared bed-like or laid out. */
    public static int partsFor(me.daddychurchill.CityWorld.compat.Material piece) {
        return furnitureFor(piece).cells().size();
    }

    /** Whether the piece has a data map entry at all — the self-test's "is the map reaching us" probe. */
    public static boolean isDeclared(me.daddychurchill.CityWorld.compat.Material piece) {
        return furnitureDataFor(piece) != null;
    }

    /** The piece's declaration, or {@link Facing#NONE} for an undeclared single block. */
    public static Facing furnitureFor(me.daddychurchill.CityWorld.compat.Material piece) {
        Facing facing = furnitureDataFor(piece);
        return facing == null ? Facing.NONE : facing;
    }

    /** The space a piece takes along its own axes — see {@link Footprint}. */
    public static Footprint footprintFor(me.daddychurchill.CityWorld.compat.Material piece) {
        List<Part> cells = furnitureFor(piece).cells();
        if (cells.size() == 1)
            return Footprint.SINGLE;
        int minR = 0, maxR = 0, minB = 0, maxB = 0, minU = 0, maxU = 0;
        for (Part part : cells) {
            minR = Math.min(minR, part.right());
            maxR = Math.max(maxR, part.right());
            minB = Math.min(minB, part.back());
            maxB = Math.max(maxB, part.back());
            minU = Math.min(minU, part.up());
            maxU = Math.max(maxU, part.up());
        }
        return new Footprint(maxR - minR + 1, maxB - minB + 1, maxU - minU + 1);
    }

    private static @Nullable Facing furnitureDataFor(me.daddychurchill.CityWorld.compat.Material piece) {
        if (piece == null)
            return null;
        Block block = piece.getBlock();
        if (block == null)
            return null;
        Facing declared = FURNITURE_DATA.get(BuiltInRegistries.BLOCK.getKey(block));
        if (declared != null)
            return declared; // a datapack entry always wins over a runtime-derived one
        return me.daddychurchill.CityWorld.Support.FurnitureSets.dataFor(block);
    }

    /**
     * The ground declared for this biome, or {@code null} if none is.
     *
     * <p>Data maps hang off {@code Holder.Reference}; a direct holder (one not backed by a registry
     * entry) carries no data, which is a legitimate answer rather than an error.
     */
    public static @Nullable Ground groundFor(@Nullable Holder<Biome> biome) {
        if (biome == null)
            return null;
        return biome.unwrapKey().map(key -> GROUND_DATA.get(key.location())).orElse(null);
    }
}
