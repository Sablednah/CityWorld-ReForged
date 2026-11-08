package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;
import me.daddychurchill.CityWorld.Support.PlaceNames;
import me.daddychurchill.CityWorld.compat.BlockFace;
import me.daddychurchill.CityWorld.compat.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ImposterProtoChunk;

/**
 * Road signs on Immersive Vehicles' poles: datapack registry {@code cityworld:pole_signs}, one file per sign pack
 * ({@code data/<ns>/cityworld/pole_signs/<name>.json}) naming, for each of CityWorld's signs, that pack's sign and
 * the text field it carries (a town's name, a speed limit). Shipped: the Official Signs pack ({@code osip}) and the
 * Official Content Pack's own signs, osip first. A sign no installed set has falls back to Macaw's Roads, if that
 * is installed, as before.
 *
 * <p>CityWorld's signs: {@code stop}, {@code dead_end}, {@code town} and {@code town_end} (the town's name),
 * {@code speed_limit} and {@code speed_limit_end} (the limit), {@code motorway}, {@code motorway_end}, {@code tunnel},
 * {@code steep_ascent}, {@code steep_descent}. A pole is a column of {@code mts:pole} blocks, each holding its core,
 * the sign on the top one's side that faces its traffic. Written as block entity data into the chunk being
 * generated, which Immersive Vehicles builds on its first tick.
 */
public final class PoleSigns {

    private PoleSigns() {
    }

    public record Sign(String sign, String sub, Optional<String> text, int max) {
        public static final Codec<Sign> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("sign").forGetter(Sign::sign),
                Codec.STRING.optionalFieldOf("sub", "").forGetter(Sign::sub),
                Codec.STRING.optionalFieldOf("text").forGetter(Sign::text),
                Codec.intRange(1, 64).optionalFieldOf("max", 20).forGetter(Sign::max))
                .apply(i, Sign::new));
    }

    public record SignSet(Optional<String> requiresMod, String pack, String core, int priority, Map<String, Sign> signs) {
        public static final Codec<SignSet> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(SignSet::requiresMod),
                Codec.STRING.fieldOf("pack").forGetter(SignSet::pack),
                Codec.STRING.optionalFieldOf("core", "pole_core").forGetter(SignSet::core),
                Codec.INT.optionalFieldOf("priority", 0).forGetter(SignSet::priority),
                Codec.unboundedMap(Codec.STRING, Sign.CODEC).optionalFieldOf("signs", Map.of()).forGetter(SignSet::signs))
                .apply(i, SignSet::new));
    }

    public static final ResourceKey<Registry<SignSet>> POLE_SIGNS = ResourceKey
            .createRegistryKey(ResourceLocation.fromNamespaceAndPath(CityWorldMod.MODID, "pole_signs"));

    /** The installed sets, the highest priority first (then by id). */
    private static volatile List<SignSet> sets = List.of();

    public static void bind(RegistryAccess access) {
        try {
            if (!loaded("mts") || Material.of("mts:pole") == null || Material.of("mts:pole") == Material.AIR) {
                sets = List.of();
                return;
            }
            Registry<SignSet> registry = access.registryOrThrow(POLE_SIGNS);
            var files = new ArrayList<>(registry.entrySet());
            files.sort(Comparator.comparing(e -> e.getKey().location().toString()));
            List<SignSet> out = new ArrayList<>();
            for (var e : files)
                if (e.getValue().requiresMod().map(PoleSigns::loaded).orElse(true) && loaded(e.getValue().pack()))
                    out.add(e.getValue());
            out.sort(Comparator.comparingInt(s -> -s.priority())); // stable: ties keep id order
            sets = List.copyOf(out);
            if (!out.isEmpty() && LOGGED.add(String.valueOf(out.stream().map(SignSet::pack).toList())))
                CityWorldMod.LOGGER.info("CityWorld pole signs: {}", out.stream().map(s -> s.pack() + " (" + s.signs().size() + " signs)").toList());
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld pole signs: could not read the registry: {}", e.toString());
        }
    }

    private static final java.util.Set<String> LOGGED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    private static boolean loaded(String mod) {
        return net.neoforged.fml.ModList.get().isLoaded(mod);
    }

    /** Whether a block is one of IV's poles: what the highway sweep leaves standing. */
    public static boolean owns(BlockState state) {
        if (sets.isEmpty())
            return false;
        Material pole = Material.of("mts:pole");
        return pole != null && state.is(pole.getBlockState().getBlock());
    }

    /** Whether any sign set is installed. */
    public static boolean any() {
        return !sets.isEmpty();
    }

    /** Whether an installed set has that sign. */
    public static boolean has(String name) {
        for (SignSet s : sets)
            if (s.signs().containsKey(name))
                return true;
        return false;
    }

    /**
     * A pole from {@code base} to {@code top} (world coordinates, in the chunk being generated) with the sign named
     * on top facing {@code facing}, its text {@code text} cut to the sign's length; a null name is a bare pole (a
     * gantry's post or bar). False when no set has the sign (nothing is placed) or the chunk is not the one being
     * generated.
     */
    public static boolean place(ChunkAccess chunk, String name, String text, int x, int base, int top, int z,
            BlockFace facing) {
        if (chunk instanceof ImposterProtoChunk)
            return false;
        for (SignSet set : sets) {
            Sign sign = name == null ? null : set.signs().get(name);
            if (sign == null && name != null)
                continue; // a bare pole (no name) takes the first set's
            BlockState pole = Material.of("mts:pole").getBlockState();
            for (int y = base; y <= top; y++) {
                CompoundTag tag = component(set.pack(), set.core(), "");
                tag.put("NONE", component(set.pack(), set.core(), ""));
                if (y == top && sign != null) {
                    CompoundTag face = component(set.pack(), sign.sign(), sign.sub());
                    sign.text().ifPresent(field -> face.putString("text" + field,
                            PlaceNames.fit(text == null ? "" : text, sign.max())));
                    tag.put(facing.name(), face);
                }
                blockWithData(chunk, new BlockPos(x, y, z), pole, tag);
            }
            return true;
        }
        return false;
    }

    private static CompoundTag component(String pack, String system, String sub) {
        CompoundTag t = new CompoundTag();
        t.putString("packID", pack);
        t.putString("systemName", system);
        t.putString("subName", sub);
        return t;
    }

    /** A block with its block entity's data, written to the chunk as a saved chunk holds it (world coordinates). */
    static void blockWithData(ChunkAccess chunk, BlockPos pos, BlockState state, CompoundTag data) {
        chunk.setBlockState(pos, state, false);
        if (!(state.getBlock() instanceof EntityBlock eb))
            return;
        var be = eb.newBlockEntity(pos, state);
        if (be == null)
            return;
        CompoundTag tag = data.copy();
        tag.putString("id", String.valueOf(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(be.getType())));
        tag.putInt("x", pos.getX());
        tag.putInt("y", pos.getY());
        tag.putInt("z", pos.getZ());
        chunk.setBlockEntityNbt(tag);
    }
}
