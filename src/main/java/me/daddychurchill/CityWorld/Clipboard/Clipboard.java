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
import net.minecraft.world.level.block.Mirror;
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

    /**
     * {@code Title:} in the {@code .yml} — the building's display name, used when it announces itself.
     * Defaults to the file's own name, which is fine for a catalog already named "Fire Station" but not
     * for a bare {@code winchester.schematic}: a title lets that read "The Winchester Tavern".
     */
    public final String title;

    /**
     * {@code Ocean: true} in the {@code .yml} — an offshore build (rig, ship, lighthouse) that sits
     * <em>on</em> deep open water at the surface, on its own legs/hull, with no foundation: it places
     * only in genuinely deep water and the mod does nothing to the terrain, just drops the build in.
     */
    public final boolean ocean;

    /**
     * {@code KeepAir: true} in the {@code .yml} — place the schematic's air blocks instead of skipping
     * them, so enclosed voids (a cellar, a hollow tower) stay open rather than being filled by the
     * terrain the build overlays. Costs the overlay behaviour (the whole bounding box, air included, is
     * stamped over the ground), so it is opt-in for builds that carry their own interior.
     */
    public final boolean keepAir;

    /**
     * {@code Anchor: true} in the {@code .yml}, only meaningful with {@link #ocean} — after placing,
     * extend each solid column of the build's bottom row straight down until it meets the sea floor, so
     * an offshore build stands on real legs to the seabed instead of floating on a stub of rock.
     */
    public final boolean anchor;

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
        this.title = meta.title == null || meta.title.isBlank() ? name : meta.title;
        this.ocean = meta.ocean;
        this.keepAir = meta.keepAir;
        this.anchor = meta.anchor;
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
        // Parse the .yml first: keepAir decides whether the reader drops the schematic's air blocks.
        Meta meta = yml != null ? Meta.parse(yml) : new Meta();
        StructureTemplate template = readTemplate(fileName, data, blocks, meta.keepAir);
        return new Clipboard(name, family, template, meta);
    }

    /** Pick a reader by file extension: native {@code .nbt}, WorldEdit {@code .schem}, or legacy. */
    private static StructureTemplate readTemplate(String fileName, InputStream data, HolderGetter<Block> blocks,
            boolean keepAir) throws IOException {
        String lower = fileName.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".nbt")) {
            // A .nbt file already IS a structure tag; data-fix it (old files) then load. It keeps
            // whatever air the file stored, so keepAir has no extra effect here.
            CompoundTag tag = NbtIo.readCompressed(data, NbtAccounter.unlimitedHeap());
            return Templates.build(tag, tag.getInt("DataVersion").orElse(0), blocks);
        }
        if (lower.endsWith(".schem"))
            return SpongeSchematic.read(data).toTemplate(blocks, keepAir);
        if (lower.endsWith(".litematic"))
            return LitematicSchematic.read(data).toTemplate(blocks, keepAir);
        return LegacySchematic.read(data).toTemplate(blocks, keepAir); // legacy .schematic
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
     *
     * <p>{@code rotation}/{@code mirror} turn the whole building about its footprint. {@code (nwX, nwZ)}
     * is still the NW corner the <em>rotated</em> footprint should occupy — {@link #footprintChunkX}/
     * {@link #footprintChunkZ} give that footprint's size in chunks (swapped for 90°/270°). The origin
     * arithmetic is vanilla's own {@code getZeroPositionWithTransform}, which returns the placement
     * offset that lands the transformed structure's minimum corner exactly on the target (pivot stays
     * at {@code ZERO}) — the same pairing {@code FossilFeature} uses. The clip bounding box is world
     * space, so it needs no rotation awareness; it just restricts writes to this chunk.
     */
    public void pasteChunk(ServerLevelAccessor level, int nwX, int groundY, int nwZ,
            int chunkMinX, int chunkMinZ, Rotation rotation, Mirror mirror, RandomSource random) {
        int bottom = groundY - groundLevelY;
        BlockPos origin = template.getZeroPositionWithTransform(new BlockPos(nwX, bottom, nwZ), mirror, rotation);
        BoundingBox chunkBox = new BoundingBox(chunkMinX, bottom, chunkMinZ,
                chunkMinX + 15, bottom + sizeY, chunkMinZ + 15);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation)
                .setMirror(mirror)
                .setIgnoreEntities(true)
                .setBoundingBox(chunkBox);
        template.placeInWorld(level, origin, origin, settings, random, Block.UPDATE_CLIENTS);
    }

    /** The four quarter-turns, indexed by {@code odds.getRandomInt(4)} at placement time. */
    public static final Rotation[] ROTATIONS = {
            Rotation.NONE, Rotation.CLOCKWISE_90, Rotation.CLOCKWISE_180, Rotation.COUNTERCLOCKWISE_90 };

    /** Footprint width in chunks after {@code rotation} — X and Z swap for the 90°/270° turns. */
    public int footprintChunkX(Rotation rotation) {
        return swapsFootprint(rotation) ? chunkZ : chunkX;
    }

    /** Footprint depth in chunks after {@code rotation}. See {@link #footprintChunkX}. */
    public int footprintChunkZ(Rotation rotation) {
        return swapsFootprint(rotation) ? chunkX : chunkZ;
    }

    /** A quarter turn (90°/270°) exchanges the X and Z extents; 0°/180° leave them be. */
    public static boolean swapsFootprint(Rotation rotation) {
        return rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90;
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
        String title = null; // null = fall back to the file's own name
        boolean ocean = false;
        boolean keepAir = false;
        boolean anchor = false;

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
                            case "Title" -> m.title = stripQuotes(val);
                            case "Ocean" -> m.ocean = Boolean.parseBoolean(val);
                            case "KeepAir" -> m.keepAir = Boolean.parseBoolean(val);
                            case "Anchor" -> m.anchor = Boolean.parseBoolean(val);
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

        /** YAML lets a value be quoted; a title is the one key where that is likely (it has spaces). */
        private static String stripQuotes(String v) {
            if (v.length() >= 2 && (v.startsWith("\"") && v.endsWith("\"") || v.startsWith("'") && v.endsWith("'")))
                return v.substring(1, v.length() - 1);
            return v;
        }
    }
}
