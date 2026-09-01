package me.daddychurchill.CityWorld.worldgen;

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
     * How a furniture block's {@code facing} relates to the way its occupant looks, in degrees
     * clockwise.
     *
     * <p>Needed because furniture mods disagree, and measurably so: Macaw's chair uses {@code facing}
     * for the direction the sitter looks, Macaw's <em>sofa</em> is 90° off that, and Refurbished uses
     * it for the direction the backrest points. A boolean "front or back?" cannot express three
     * conventions, and one mod contradicting itself rules out declaring it per mod.
     */
    public record Facing(int facingOffset) {

        public static final Codec<Facing> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("facingOffset", 0).forGetter(Facing::facingOffset)
        ).apply(i, Facing::new));
    }

    public static final DataMapType<Block, Facing> FURNITURE = DataMapType
            .builder(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "furniture"), Registries.BLOCK,
                    Facing.CODEC)
            .build();

    /** Registered from {@code CityWorldMod} on the mod event bus. */
    public static void register(RegisterDataMapTypesEvent event) {
        event.register(GROUND);
        event.register(FURNITURE);
    }

    /** The declared facing offset for a furniture block, or {@code 0} if it declares none. */
    public static int facingOffsetFor(me.daddychurchill.CityWorld.compat.Material piece) {
        if (piece == null)
            return 0;
        Block block = piece.getBlock();
        if (block == null)
            return 0;
        Holder<Block> holder = BuiltInRegistries.BLOCK.wrapAsHolder(block);
        if (holder instanceof Holder.Reference<Block> reference) {
            Facing facing = reference.getData(FURNITURE);
            if (facing != null)
                return facing.facingOffset();
        }
        return 0;
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
