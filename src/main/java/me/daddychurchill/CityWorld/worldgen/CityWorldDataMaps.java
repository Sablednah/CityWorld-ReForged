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
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.registries.datamaps.DataMapType;
import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;

import org.jspecify.annotations.Nullable;

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

    public static final DataMapType<Biome, Ground> GROUND = DataMapType
            .builder(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "ground"), Registries.BIOME, Ground.CODEC)
            .build();

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

    public static final DataMapType<Block, Facing> FURNITURE = DataMapType
            .builder(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "furniture"), Registries.BLOCK,
                    Facing.CODEC)
            .build();

    /**
     * How much room a structure needs, and whether CityWorld should build ground up to meet it.
     *
     * <p><b>clearance</b> — chunks of clear ground reserved around the structure, overriding
     * {@link StructureReservations#DEFAULT_CLEARANCE}. It exists because the declared
     * {@code max_distance_from_center} is unreachable for the structures that need it most: it is a
     * private field on {@code JigsawStructure} (so an Access Transformer at best), and a custom
     * structure type such as {@code cataclysm:cataclysm_jigsaw} does not use that class at all, so
     * its declared 187 lives in a codec CityWorld cannot see. Measured on the owner's worlds,
     * 2026-09-22: acropolis spans 209x103 with a reach of 109 blocks and cursed_pyramid 89x107 with
     * 106 — both need 7 chunks against a default of 5, so both are clipped today.
     *
     * <p><b>beard</b> — build the planned ground up to each piece even though the structure declares
     * {@code terrain_adaptation: none}. Vanilla's own {@code NONE} structures must NOT get this: three
     * of them ({@code desert_pyramid}, {@code jungle_pyramid}, {@code swamp_hut}) are
     * {@code ScatteredFeaturePiece}s that re-level themselves, and the rest are deliberately not on
     * the ground — {@code ruined_portal} is half-buried by design, {@code bastion_remnant} floats over
     * lava, {@code shipwreck} and {@code ocean_ruin} sit on the seabed. {@code buildCity} runs in every
     * dimension, so an automatic rule would reach the Nether and the End. Hence opt-in, per structure,
     * and nothing is opted in by default.
     *
     * <p><b>shave</b> — for a standing structure that cuts its own volume out of a hill (Cataclysm's
     * acropolis: its NBT is air where the hill was). The plan is LOWERED to the storey the hill cuts
     * into, inside the box, and feathered back to natural ground outside it, so the cut is painted as
     * ground rather than left as a raw strata face. Only ever lowers, never below the waterline. See
     * {@code padPlanForStructures}.
     *
     * <p>Values are plain numbers and flags, so — unlike the ground map, whose values are block ids —
     * an entry for an absent mod needs no {@code neoforge:conditions}: the key simply never matches.
     */
    public record StructureFit(int clearance, boolean beard, boolean shave) {

        public static final Codec<StructureFit> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("clearance", 0).forGetter(StructureFit::clearance),
                Codec.BOOL.optionalFieldOf("beard", false).forGetter(StructureFit::beard),
                Codec.BOOL.optionalFieldOf("shave", false).forGetter(StructureFit::shave)
        ).apply(i, StructureFit::new));
    }

    // ⚠ The 1.20.1 Forge line has NO DataMapType and hand-rolls a SimpleJsonResourceReloadListener,
    // which collapses every pack's copy of this file onto one id and keeps only the winner (proved
    // 2026-09-22). So a compat pack that extends structure_fit works here and silently does nothing
    // there. Keep the two in step, and see CityWorldDataMaps on mc1.20.1 before relying on packs.
    public static final DataMapType<net.minecraft.world.level.levelgen.structure.Structure, StructureFit>
            STRUCTURE_FIT = DataMapType
                    .builder(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "structure_fit"),
                            Registries.STRUCTURE, StructureFit.CODEC)
                    .build();

    /**
     * How many structures declare a fit, for the probe. A data map that a pack REPLACED rather than
     * extended looks identical to one that merged, unless you count what is actually in it.
     */
    public static java.util.List<String> declaredFits(net.minecraft.core.HolderLookup.RegistryLookup<
            net.minecraft.world.level.levelgen.structure.Structure> lookup) {
        java.util.List<String> out = new java.util.ArrayList<>();
        try {
            lookup.listElements().forEach(reference -> {
                if (reference.getData(STRUCTURE_FIT) != null)
                    out.add(reference.key().identifier().toString());
            });
        } catch (Throwable t) {
            return java.util.List.of("<threw: " + t + ">");
        }
        java.util.Collections.sort(out);
        return out;
    }

    /** The declaration for this structure, or {@code null} if it has none. */
    public static @Nullable StructureFit fitFor(
            @Nullable Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        if (structure instanceof Holder.Reference<net.minecraft.world.level.levelgen.structure.Structure> reference)
            return reference.getData(STRUCTURE_FIT);
        return null;
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

    /** Whether this structure asks for the plan to be shaved to it. */
    public static boolean shaves(
            @Nullable Holder<net.minecraft.world.level.levelgen.structure.Structure> structure) {
        StructureFit fit = fitFor(structure);
        return fit != null && fit.shave();
    }

    /** Registered from {@code CityWorldMod} on the mod event bus. */
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(GROUND);
        event.register(FURNITURE);
        event.register(STRUCTURE_FIT);
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
        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
        if (holder instanceof Holder.Reference<Block> reference) {
            Facing declared = reference.getData(FURNITURE);
            if (declared != null)
                return declared; // a datapack entry always wins over a runtime-derived one
        }
        return me.daddychurchill.CityWorld.Support.FurnitureSets.dataFor(block);
    }

    /**
     * The ground declared for this biome, or {@code null} if none is.
     *
     * <p>Data maps hang off {@code Holder.Reference}; a direct holder (one not backed by a registry
     * entry) carries no data, which is a legitimate answer rather than an error.
     */
    public static @Nullable Ground groundFor(@Nullable Holder<Biome> biome) {
        if (biome instanceof Holder.Reference<Biome> reference)
            return reference.getData(GROUND);
        return null;
    }
}
