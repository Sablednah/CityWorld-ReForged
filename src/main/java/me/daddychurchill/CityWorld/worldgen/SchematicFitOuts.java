package me.daddychurchill.CityWorld.worldgen;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.mojang.brigadier.StringReader;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A pack's changes to a schematic building, from data: {@code cityworld:schematic_fitout}, files at
 * {@code data/<ns>/cityworld/schematic_fitout/<name>.json}.
 *
 * <p>A file names the {@code schematic} it fits out (the file name without extension, e.g. {@code winchester}; every
 * copy of that name in every family) and carries {@code blocks} and {@code frames}, each at a {@code pos} in the
 * schematic's own coordinates as it is stored, before CityWorld turns it to fit its lot:
 * <ul>
 *   <li>a block line's {@code block} is a block state in {@code /setblock} syntax ({@code phoenix:phoenix[facing=east]},
 *       facing as stored). It replaces the block the schematic places there, and turns with the building.</li>
 *   <li>a frame line hangs an item frame ({@code "glow": true} for a glow frame) in the cell at {@code pos}, on the
 *       wall behind it, facing {@code facing} (as stored), holding {@code item} in {@code /give} syntax
 *       ({@code minecraft:diamond_sword[minecraft:custom_name='"Ed"']}). It is hung after decay, and only if the
 *       cell is still clear and the wall still stands.</li>
 * </ul>
 * The file and each line can carry {@code requires_mod}; anything whose mod is absent, or whose block or item does
 * not parse, is skipped (a bad line is logged once, when the world loads). A block line can carry its block entity's
 * data in {@code /setblock} form ({@code mts:fuelpump{packID:"mtsofficialpack",...}}), and {@code heading}: the name
 * of a number in that data holding a compass heading, turned with the building (see {@link Data}).
 */
public final class SchematicFitOuts {

    private SchematicFitOuts() {
    }

    public record BlockLine(BlockPos pos, String block, Optional<String> requiresMod, Optional<String> heading) {
        public static final Codec<BlockLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(BlockLine::pos),
                Codec.STRING.fieldOf("block").forGetter(BlockLine::block),
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(BlockLine::requiresMod),
                Codec.STRING.optionalFieldOf("heading").forGetter(BlockLine::heading))
                .apply(i, BlockLine::new));
    }

    /**
     * A swapped block's own data ({@code /setblock}'s {@code {...}}): given to its block entity as placed. {@code heading}
     * names a number in it that is a compass heading in degrees (0 south, 90 east, 180 north, 270 west: how Immersive
     * Vehicles stores a decor's facing, {@code anglesy}), turned and mirrored with the building.
     */
    public record Data(net.minecraft.nbt.CompoundTag nbt, Optional<String> heading) {
    }

    public record FrameLine(BlockPos pos, Direction facing, String item, boolean glow, Optional<String> requiresMod) {
        public static final Codec<FrameLine> CODEC = RecordCodecBuilder.create(i -> i.group(
                BlockPos.CODEC.fieldOf("pos").forGetter(FrameLine::pos),
                Direction.CODEC.fieldOf("facing").forGetter(FrameLine::facing),
                Codec.STRING.fieldOf("item").forGetter(FrameLine::item),
                Codec.BOOL.optionalFieldOf("glow", false).forGetter(FrameLine::glow),
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(FrameLine::requiresMod))
                .apply(i, FrameLine::new));
    }

    public record FitOut(String schematic, Optional<String> requiresMod, List<BlockLine> blocks, List<FrameLine> frames) {
        public static final Codec<FitOut> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.STRING.fieldOf("schematic").forGetter(FitOut::schematic),
                Codec.STRING.optionalFieldOf("requires_mod").forGetter(FitOut::requiresMod),
                BlockLine.CODEC.listOf().optionalFieldOf("blocks", List.of()).forGetter(FitOut::blocks),
                FrameLine.CODEC.listOf().optionalFieldOf("frames", List.of()).forGetter(FitOut::frames))
                .apply(i, FitOut::new));
    }

    public static final ResourceKey<Registry<FitOut>> FIT_OUTS = ResourceKey
            .createRegistryKey(Identifier.fromNamespaceAndPath(CityWorldMod.MODID, "schematic_fitout"));

    /** A frame, resolved: where, facing which way (both as stored), and what it holds. */
    public record Frame(BlockPos pos, Direction facing, ItemStack item, boolean glow) {
    }

    /** One schematic's fit-out, resolved: block states by stored position, and frames. */
    public record Resolved(Map<BlockPos, BlockState> blocks, List<Frame> frames, Map<BlockPos, Data> data) {
        public static final Resolved NONE = new Resolved(Map.of(), List.of(), Map.of());
    }

    private static volatile Map<String, Resolved> bySchematic = Map.of();

    /** Reads the registry off a loaded level. Files apply in id order, so a later file's block wins a shared cell. */
    public static void bind(RegistryAccess access) {
        try {
            Registry<FitOut> registry = access.lookupOrThrow(FIT_OUTS);
            List<Map.Entry<ResourceKey<FitOut>, FitOut>> files = new ArrayList<>(registry.entrySet());
            files.sort(Comparator.comparing(e -> e.getKey().identifier().toString()));
            Map<String, Map<BlockPos, BlockState>> blocks = new HashMap<>();
            Map<String, List<Frame>> frames = new HashMap<>();
            Map<String, Map<BlockPos, Data>> data = new HashMap<>();
            int skipped = 0;
            for (Map.Entry<ResourceKey<FitOut>, FitOut> e : files) {
                List<String> problems = new ArrayList<>();
                Resolved r = resolve(access, e.getValue(), problems);
                String name = e.getValue().schematic().toLowerCase(java.util.Locale.ROOT);
                Map<BlockPos, BlockState> theirs = blocks.computeIfAbsent(name, k -> new HashMap<>());
                Map<BlockPos, Data> theirData = data.computeIfAbsent(name, k -> new HashMap<>());
                theirData.keySet().removeAll(r.blocks().keySet()); // a later file's block takes its own data or none
                theirs.putAll(r.blocks());
                theirData.putAll(r.data());
                frames.computeIfAbsent(name, k -> new ArrayList<>()).addAll(r.frames());
                skipped += e.getValue().blocks().size() + e.getValue().frames().size() - r.blocks().size() - r.frames().size();
                for (String problem : problems)
                    if (WARNED.add(e.getKey().identifier() + " " + problem)) // once a game, not once per level
                        CityWorldMod.LOGGER.warn("CityWorld fit-out {}: {}", e.getKey().identifier(), problem);
            }
            Map<String, Resolved> resolved = new HashMap<>();
            for (String name : blocks.keySet())
                if (!blocks.get(name).isEmpty() || !frames.get(name).isEmpty())
                    resolved.put(name, new Resolved(Map.copyOf(blocks.get(name)), List.copyOf(frames.get(name)),
                            Map.copyOf(data.get(name))));
            bySchematic = Map.copyOf(resolved);
            if (!files.isEmpty() && WARNED.add("summary " + files.size() + " " + resolved.size() + " " + skipped))
                CityWorldMod.LOGGER.info("CityWorld fit-outs: {} file(s) for {} schematic(s), {} line(s) skipped",
                        files.size(), resolved.size(), skipped);
        } catch (RuntimeException e) {
            CityWorldMod.LOGGER.warn("CityWorld fit-outs: could not read the registry: {}", e.toString());
        }
    }

    private static final java.util.Set<String> WARNED = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /**
     * One file's lines, resolved: those whose mod is absent are left out quietly, those whose block or item does not
     * parse are left out and described in {@code problems}.
     */
    public static Resolved resolve(RegistryAccess access, FitOut f, List<String> problems) {
        if (!loaded(f.requiresMod()))
            return Resolved.NONE;
        var blockLookup = access.lookupOrThrow(Registries.BLOCK);
        ItemParser items = new ItemParser(access);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        Map<BlockPos, Data> data = new HashMap<>();
        List<Frame> frames = new ArrayList<>();
        for (BlockLine b : f.blocks()) {
            if (!loaded(b.requiresMod()))
                continue;
            try {
                var parsed = BlockStateParser.parseForBlock(blockLookup, b.block(), true);
                blocks.put(b.pos().immutable(), parsed.blockState());
                if (parsed.nbt() != null)
                    data.put(b.pos().immutable(), new Data(parsed.nbt(), b.heading()));
            } catch (Exception ex) {
                problems.add("block '" + b.block() + "' at " + b.pos().toShortString() + ": " + ex.getMessage());
            }
        }
        for (FrameLine fr : f.frames()) {
            if (!loaded(fr.requiresMod()))
                continue;
            try {
                ItemParser.ItemResult parsed = items.parse(new StringReader(fr.item()));
                ItemStack stack = new ItemStack(parsed.item(), 1);
                stack.applyComponents(parsed.components());
                frames.add(new Frame(fr.pos().immutable(), fr.facing(), stack, fr.glow()));
            } catch (Exception ex) {
                problems.add("item '" + fr.item() + "' at " + fr.pos().toShortString() + ": " + ex.getMessage());
            }
        }
        return new Resolved(Map.copyOf(blocks), List.copyOf(frames), Map.copyOf(data));
    }

    private static boolean loaded(Optional<String> mod) {
        return mod.map(m -> net.neoforged.fml.ModList.get().isLoaded(m)).orElse(true);
    }

    /**
     * The data a swap carries, as placed: a copy, its {@code heading} turned as the building is — mirrored first, then
     * rotated, as vanilla places a template. A heading's forward is (sin h, cos h) in (x, z).
     */
    public static net.minecraft.nbt.CompoundTag placed(Data d, net.minecraft.world.level.block.Mirror mirror,
            net.minecraft.world.level.block.Rotation rotation) {
        net.minecraft.nbt.CompoundTag nbt = d.nbt().copy();
        if (d.heading().isEmpty() || !nbt.contains(d.heading().get()))
            return nbt;
        String key = d.heading().get();
        double h;
        try { // SNBT of any number (270.0d, 90f, 180): read it the same way on every version
            h = Double.parseDouble(String.valueOf(nbt.get(key)).replaceAll("[dDfFbBsSlL]$", ""));
        } catch (NumberFormatException e) {
            return nbt;
        }
        if (mirror == net.minecraft.world.level.block.Mirror.LEFT_RIGHT) // z flips
            h = 180 - h;
        else if (mirror == net.minecraft.world.level.block.Mirror.FRONT_BACK) // x flips
            h = -h;
        h -= switch (rotation) {
            case CLOCKWISE_90 -> 90;
            case CLOCKWISE_180 -> 180;
            case COUNTERCLOCKWISE_90 -> 270;
            default -> 0;
        };
        nbt.putDouble(key, ((h % 360) + 360) % 360);
        return nbt;
    }

    /** The fit-out for a schematic by its name, or {@link Resolved#NONE}. */
    public static Resolved forSchematic(String name) {
        return name == null ? Resolved.NONE : bySchematic.getOrDefault(name.toLowerCase(java.util.Locale.ROOT), Resolved.NONE);
    }

    /** How many schematics have a fit-out (the self-test reports it). */
    public static int count() {
        return bySchematic.size();
    }
}
