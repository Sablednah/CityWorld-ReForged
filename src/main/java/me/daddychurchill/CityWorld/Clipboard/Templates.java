package me.daddychurchill.CityWorld.Clipboard;

import java.util.Set;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.SharedConstants;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.util.datafix.DataFixers;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Shared helpers for turning a parsed schematic into a {@link StructureTemplate}.
 *
 * <p>The key one is {@link #build}: it runs the raw structure NBT through the vanilla structure
 * <b>data-fixer</b> before loading, so a schematic saved in an older version (renamed blocks, old item
 * ids in containers, ...) is upgraded to the current version rather than losing those blocks to air.
 * This is why the parsers hand over the palette as {@code {Name, Properties}} tags rather than
 * already-resolved {@code BlockState}s — resolving happens <em>after</em> the fix, inside
 * {@code StructureTemplate.load}.
 */
public final class Templates {

    private Templates() {}

    /** Block names that are "air" in every version (never renamed) — skipped so builds overlay terrain. */
    private static final Set<String> AIR = Set.of("minecraft:air", "minecraft:cave_air", "minecraft:void_air");

    public static boolean isAir(String blockName) {
        return blockName == null || AIR.contains(blockName);
    }

    /** The block name (before any {@code [properties]}) of a WorldEdit-style block-state string. */
    public static String nameOf(String stateDescriptor) {
        int bracket = stateDescriptor.indexOf('[');
        return bracket < 0 ? stateDescriptor : stateDescriptor.substring(0, bracket);
    }

    /** Parse a {@code minecraft:foo[a=b,c=d]} descriptor into a {@code {Name, Properties}} palette tag. */
    public static CompoundTag blockStateToTag(String descriptor) {
        CompoundTag tag = new CompoundTag();
        int bracket = descriptor.indexOf('[');
        if (bracket < 0) {
            tag.putString("Name", descriptor);
            return tag;
        }
        tag.putString("Name", descriptor.substring(0, bracket));
        int end = descriptor.lastIndexOf(']');
        CompoundTag props = new CompoundTag();
        for (String kv : descriptor.substring(bracket + 1, end < 0 ? descriptor.length() : end).split(",")) {
            int eq = kv.indexOf('=');
            if (eq > 0)
                props.putString(kv.substring(0, eq).trim(), kv.substring(eq + 1).trim());
        }
        if (!props.isEmpty())
            tag.put("Properties", props);
        return tag;
    }

    /**
     * Data-fix (if the schematic is from an older version) then load a vanilla structure tag
     * ({@code size}/{@code palette}/{@code blocks}/{@code entities}) into a template.
     */
    public static StructureTemplate build(CompoundTag structureTag, int dataVersion, HolderGetter<Block> blocks) {
        CompoundTag tag = structureTag;
        if (dataVersion > 0 && dataVersion < SharedConstants.WORLD_VERSION) {
            try {
                tag = DataFixTypes.STRUCTURE.updateToCurrentVersion(DataFixers.getDataFixer(), structureTag, dataVersion);
            } catch (Exception e) {
                CityWorldMod.LOGGER.warn("Templates: data-fix from version {} failed; loading as-is", dataVersion, e);
                tag = structureTag;
            }
        }
        StructureTemplate template = new StructureTemplate();
        template.load(blocks, tag);
        return template;
    }

    public static ListTag intList(int a, int b, int c) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(a));
        list.add(IntTag.valueOf(b));
        list.add(IntTag.valueOf(c));
        return list;
    }
}
