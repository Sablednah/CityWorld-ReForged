package me.daddychurchill.CityWorld.Clipboard;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import me.daddychurchill.CityWorld.Clipboard.PasteProvider.SchematicFamily;
import me.daddychurchill.CityWorld.Support.SupportBlocks;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * One placeable classic building: a native {@link StructureTemplate} (converted once from a legacy
 * {@code .schematic}) plus the CityWorld placement metadata from its {@code .schematic.yml} sidecar.
 *
 * <p>This is the modern counterpart of upstream's {@code Clipboard} — same role (name, footprint,
 * odds, ground level, decayability, insets), but the block data lives in a {@code StructureTemplate}
 * so loading, placing and re-saving are native. Placement goes through {@link StructureTemplate}
 * rather than the per-chunk {@code RealBlocks}, which lets a multi-chunk building drop in one call.
 */
public final class Clipboard {

    public final String name;
    public final SchematicFamily family;
    private final StructureTemplate template;

    public final int sizeX;
    public final int sizeY;
    public final int sizeZ;
    /** Footprint in chunks (rounded up). */
    public final int chunkX;
    public final int chunkZ;

    // Metadata from the .yml sidecar (upstream defaults).
    public final int groundLevelY;
    public final boolean flipableX;
    public final boolean flipableZ;
    public final double oddsOfAppearance;
    public final boolean decayable;
    /** Per-schematic override for the world's {@code oddsOfPristineBuilding}; {@code < 0} = use the world's. */
    public final double pristineChance;
    public final boolean broadcastLocation;

    private Clipboard(String name, SchematicFamily family, StructureTemplate template, Meta meta) {
        this.name = name;
        this.family = family;
        this.template = template;
        this.sizeX = template.getSize().getX();
        this.sizeY = template.getSize().getY();
        this.sizeZ = template.getSize().getZ();
        this.chunkX = ceilDiv(sizeX, SupportBlocks.sectionBlockWidth);
        this.chunkZ = ceilDiv(sizeZ, SupportBlocks.sectionBlockWidth);
        this.groundLevelY = meta.groundLevelY;
        this.flipableX = meta.flipableX;
        this.flipableZ = meta.flipableZ;
        this.oddsOfAppearance = meta.oddsOfAppearance;
        this.decayable = meta.decayable;
        this.pristineChance = meta.pristineChance;
        this.broadcastLocation = meta.broadcastLocation;
    }

    private static int ceilDiv(int a, int b) {
        return (a + b - 1) / b;
    }

    /**
     * Load a clipboard from its legacy {@code .schematic} stream and an optional {@code .yml}
     * metadata stream. The block registry is the static {@link BuiltInRegistries#BLOCK} so this needs
     * no live level (a {@code StructureTemplate} is level-independent until placed).
     */
    public static Clipboard load(String name, SchematicFamily family, String fileName, InputStream data,
            InputStream yml) throws IOException {
        HolderGetter<Block> blocks = BuiltInRegistries.BLOCK;
        StructureTemplate template = readTemplate(fileName, data, blocks);
        Meta meta = yml != null ? Meta.parse(yml) : new Meta();
        return new Clipboard(name, family, template, meta);
    }

    /** Pick a reader by file extension: native {@code .nbt}, WorldEdit {@code .schem}, or legacy. */
    private static StructureTemplate readTemplate(String fileName, InputStream data, HolderGetter<Block> blocks)
            throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".nbt")) {
            // A .nbt file already IS a structure tag; data-fix it (old files) then load.
            CompoundTag tag = NbtIo.readCompressed(data, NbtAccounter.unlimitedHeap());
            return Templates.build(tag, tag.getInt("DataVersion").orElse(0), blocks);
        }
        if (lower.endsWith(".schem"))
            return SpongeSchematic.read(data).toTemplate(blocks);
        if (lower.endsWith(".litematic"))
            return LitematicSchematic.read(data).toTemplate(blocks);
        return LegacySchematic.read(data).toTemplate(blocks); // legacy .schematic
    }

    /**
     * Paste the building so its {@code groundLevelY} layer sits at {@code groundY}, with the NW
     * corner at {@code x, z}. One native placement call — handles a multi-chunk footprint itself.
     */
    public void paste(ServerLevelAccessor level, int x, int groundY, int z, RandomSource random) {
        BlockPos origin = new BlockPos(x, groundY - groundLevelY, z);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(true);
        template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
    }

    /**
     * Paste only the part of this building that falls inside one chunk. Same origin arithmetic as
     * {@link #paste} — the whole building's NW corner is {@code (nwX, nwZ)} and its {@code groundLevelY}
     * layer sits at {@code groundY} — but a placement bounding box clipped to the chunk means the call
     * writes nothing outside it. That is what lets a multi-chunk building be placed one chunk at a time
     * during worldgen decoration, where the region only permits writes to the chunk being decorated
     * (a whole-footprint {@link #paste} would try to write neighbours and be rejected/dropped).
     *
     * <p>Blocks are the same regardless of which chunk triggers a given world position, so the seams
     * line up across chunk boundaries.
     */
    public void pasteChunk(ServerLevelAccessor level, int nwX, int groundY, int nwZ,
            int chunkMinX, int chunkMinZ, RandomSource random) {
        int bottom = groundY - groundLevelY;
        BlockPos origin = new BlockPos(nwX, bottom, nwZ);
        BoundingBox chunkBox = new BoundingBox(chunkMinX, bottom, chunkMinZ,
                chunkMinX + 15, bottom + sizeY, chunkMinZ + 15);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(Rotation.NONE)
                .setIgnoreEntities(true)
                .setBoundingBox(chunkBox);
        template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
    }

    /** Parsed {@code .schematic.yml}; simple {@code key: value} lines, no YAML library needed. */
    private static final class Meta {
        int groundLevelY = 0;
        boolean flipableX = false;
        boolean flipableZ = false;
        double oddsOfAppearance = 0.1;
        boolean decayable = true;
        double pristineChance = -1.0; // < 0 = use the world's oddsOfPristineBuilding
        boolean broadcastLocation = false;

        static Meta parse(InputStream in) throws IOException {
            Meta m = new Meta();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#"))
                        continue;
                    int c = line.indexOf(':');
                    if (c < 0)
                        continue;
                    String key = line.substring(0, c).trim();
                    String val = line.substring(c + 1).trim();
                    try {
                        switch (key) {
                            case "GroundLevelY" -> m.groundLevelY = Math.max(0, Integer.parseInt(val));
                            case "FlipableX" -> m.flipableX = Boolean.parseBoolean(val);
                            case "FlipableZ" -> m.flipableZ = Boolean.parseBoolean(val);
                            case "OddsOfAppearance" -> m.oddsOfAppearance = clamp01(Double.parseDouble(val));
                            case "Decayable" -> m.decayable = Boolean.parseBoolean(val);
                            case "PristineChance" -> m.pristineChance = clamp01(Double.parseDouble(val));
                            case "BroadcastLocation" -> m.broadcastLocation = Boolean.parseBoolean(val);
                            default -> { /* ignore unknown keys */ }
                        }
                    } catch (NumberFormatException ignored) {
                        // keep the default for a malformed value
                    }
                }
            }
            return m;
        }

        private static double clamp01(double v) {
            return Math.max(0.0, Math.min(1.0, v));
        }
    }
}
