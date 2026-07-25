package me.daddychurchill.CityWorld.Clipboard;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import me.daddychurchill.CityWorld.CityWorldMod;

import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.ChestType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Reads a legacy MCEdit/pre-1.13 {@code .schematic} (gzipped NBT: {@code Blocks}/{@code Data} byte
 * arrays with numeric ids, {@code Width}/{@code Height}/{@code Length} shorts) and converts it to a
 * modern {@link StructureTemplate} — the vanilla {@code .nbt} representation.
 *
 * <p>This is the one-time conversion the whole schematic pipeline funnels through: once a legacy
 * schematic is a {@code StructureTemplate}, it loads, places and re-saves through native code. Block
 * ids are mapped by {@link LegacyBlocks}; air is dropped so the placed structure overlays terrain
 * rather than punching a box of air around itself.
 */
public final class LegacySchematic {

    public final int width;
    public final int height;
    public final int length;
    private final byte[] blocks;
    private final byte[] data;
    /** Block-entity nbt to attach, keyed by {@link #index}. Currently just sign text. */
    private final Map<Integer, CompoundTag> blockEntities;

    private LegacySchematic(int width, int height, int length, byte[] blocks, byte[] data,
            Map<Integer, CompoundTag> blockEntities) {
        this.width = width;
        this.height = height;
        this.length = length;
        this.blocks = blocks;
        this.data = data;
        this.blockEntities = blockEntities;
    }

    public static LegacySchematic read(InputStream in) throws IOException {
        CompoundTag tag = NbtIo.readCompressed(in, NbtAccounter.unlimitedHeap());
        int w = tag.getShort("Width").orElse((short) 0);
        int h = tag.getShort("Height").orElse((short) 0);
        int l = tag.getShort("Length").orElse((short) 0);
        byte[] b = tag.getByteArray("Blocks").orElse(new byte[0]);
        byte[] d = tag.getByteArray("Data").orElse(new byte[0]);
        if (b.length != w * h * l)
            throw new IOException("Schematic Blocks length " + b.length + " != " + w + "x" + h + "x" + l);
        LegacySchematic schem = new LegacySchematic(w, h, l, b, d, new HashMap<>());
        schem.readBlockEntities(tag);
        return schem;
    }

    /**
     * Pull the legacy {@code TileEntities} we can carry into a {@link StructureTemplate}: sign text
     * (plain {@code Text1..Text4} → a modern sign's {@code front_text}) and container inventories
     * (chests/furnaces/... → a modern {@code Items} list). Item-only tile entities we can't map yet
     * (e.g. jukebox records) are skipped.
     */
    private void readBlockEntities(CompoundTag root) {
        ListTag list = root.getList("TileEntities").orElse(null);
        if (list == null)
            return;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag te = list.getCompoundOrEmpty(i);
            int x = te.getIntOr("x", 0);
            int y = te.getIntOr("y", 0);
            int z = te.getIntOr("z", 0);
            if (x < 0 || x >= width || y < 0 || y >= height || z < 0 || z >= length)
                continue;
            CompoundTag nbt = switch (te.getStringOr("id", "")) {
                case "Sign" -> signNbt(new String[] {
                        te.getStringOr("Text1", ""), te.getStringOr("Text2", ""),
                        te.getStringOr("Text3", ""), te.getStringOr("Text4", "") });
                case "Chest", "Trap", "Furnace", "Dispenser", "Dropper", "Hopper", "Brewingstand" ->
                        containerNbt(te);
                default -> null;
            };
            if (nbt != null)
                blockEntities.put(index(x, y, z), nbt);
        }
    }

    /** Modern container block-entity nbt: {@code Items} rebuilt from the legacy stacks we can map. */
    private static CompoundTag containerNbt(CompoundTag te) {
        ListTag legacy = te.getList("Items").orElse(null);
        if (legacy == null || legacy.isEmpty())
            return null;
        ListTag items = new ListTag();
        for (int i = 0; i < legacy.size(); i++) {
            CompoundTag it = legacy.getCompoundOrEmpty(i);
            int legacyId = it.getIntOr("id", -1);
            int count = it.getIntOr("Count", 0);
            int slot = it.getIntOr("Slot", 0);
            int damage = it.getIntOr("Damage", 0);
            if (legacyId < 0 || count < 1)
                continue;
            String modern = modernItemId(legacyId, damage);
            if (modern == null) {
                if (unknownItems.add(legacyId))
                    CityWorldMod.LOGGER.info("LegacySchematic: no item mapping for legacy id {} (stack skipped)",
                            legacyId);
                continue;
            }
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) slot);
            entry.putString("id", modern);
            entry.putInt("count", Math.min(count, 99));
            items.add(entry);
        }
        if (items.isEmpty())
            return null;
        CompoundTag be = new CompoundTag();
        be.put("Items", items);
        return be;
    }

    // Legacy numeric item ids for stocked containers. Block items (< 256) reuse the block mapping; the
    // 1.8-era item ids (256+) are tabled below. Anything still unknown is logged once and skipped.
    private static final Set<Integer> unknownItems = ConcurrentHashMap.newKeySet();

    private static String modernItemId(int legacyId, int damage) {
        // Block items (id < 256) share their id with the block; reuse the block mapping and take the
        // placed block's item form (so a wall-torch stack still becomes a torch item, etc.). Air-only
        // blocks (fire, water) have no item and are skipped.
        if (legacyId > 0 && legacyId < 256) {
            Item item = LegacyBlocks.of(legacyId, damage).getBlock().asItem();
            return item == Items.AIR ? null : BuiltInRegistries.ITEM.getKey(item).toString();
        }
        String id = switch (legacyId) {
            case 256 -> "iron_shovel";       case 257 -> "iron_pickaxe";     case 258 -> "iron_axe";
            case 259 -> "flint_and_steel";   case 260 -> "apple";            case 261 -> "bow";
            case 262 -> "arrow";             case 263 -> damage == 1 ? "charcoal" : "coal";
            case 264 -> "diamond";           case 265 -> "iron_ingot";       case 266 -> "gold_ingot";
            case 267 -> "iron_sword";        case 268 -> "wooden_sword";     case 269 -> "wooden_shovel";
            case 270 -> "wooden_pickaxe";    case 271 -> "wooden_axe";       case 272 -> "stone_sword";
            case 273 -> "stone_shovel";      case 274 -> "stone_pickaxe";    case 275 -> "stone_axe";
            case 276 -> "diamond_sword";     case 277 -> "diamond_shovel";   case 278 -> "diamond_pickaxe";
            case 279 -> "diamond_axe";       case 280 -> "stick";            case 281 -> "bowl";
            case 282 -> "mushroom_stew";     case 283 -> "golden_sword";     case 284 -> "golden_shovel";
            case 285 -> "golden_pickaxe";    case 286 -> "golden_axe";       case 287 -> "string";
            case 288 -> "feather";           case 289 -> "gunpowder";        case 290 -> "wooden_hoe";
            case 291 -> "stone_hoe";         case 292 -> "iron_hoe";         case 293 -> "diamond_hoe";
            case 294 -> "golden_hoe";        case 295 -> "wheat_seeds";      case 296 -> "wheat";
            case 297 -> "bread";             case 298 -> "leather_helmet";   case 299 -> "leather_chestplate";
            case 300 -> "leather_leggings";  case 301 -> "leather_boots";    case 302 -> "chainmail_helmet";
            case 303 -> "chainmail_chestplate"; case 304 -> "chainmail_leggings"; case 305 -> "chainmail_boots";
            case 306 -> "iron_helmet";       case 307 -> "iron_chestplate";  case 308 -> "iron_leggings";
            case 309 -> "iron_boots";        case 310 -> "diamond_helmet";   case 311 -> "diamond_chestplate";
            case 312 -> "diamond_leggings";  case 313 -> "diamond_boots";    case 314 -> "golden_helmet";
            case 315 -> "golden_chestplate"; case 316 -> "golden_leggings";  case 317 -> "golden_boots";
            case 318 -> "flint";             case 319 -> "porkchop";         case 320 -> "cooked_porkchop";
            case 321 -> "painting";          case 322 -> "golden_apple";     case 323 -> "oak_sign";
            case 324 -> "oak_door";          case 325 -> "bucket";           case 326 -> "water_bucket";
            case 327 -> "lava_bucket";       case 328 -> "minecart";         case 329 -> "saddle";
            case 330 -> "iron_door";         case 331 -> "redstone";         case 332 -> "snowball";
            case 333 -> "oak_boat";          case 334 -> "leather";          case 335 -> "milk_bucket";
            case 336 -> "brick";             case 337 -> "clay_ball";        case 338 -> "sugar_cane";
            case 339 -> "paper";             case 340 -> "book";             case 341 -> "slime_ball";
            case 342 -> "chest_minecart";    case 343 -> "furnace_minecart"; case 344 -> "egg";
            case 345 -> "compass";           case 346 -> "fishing_rod";      case 347 -> "clock";
            case 348 -> "glowstone_dust";    case 349 -> fish(damage);       case 350 -> damage == 1 ? "cooked_salmon" : "cooked_cod";
            case 351 -> dye(damage);         case 352 -> "bone";             case 353 -> "sugar";
            case 354 -> "cake";              case 355 -> "red_bed";          case 356 -> "repeater";
            case 357 -> "cookie";            case 358 -> "filled_map";       case 359 -> "shears";
            case 360 -> "melon_slice";       case 361 -> "pumpkin_seeds";    case 362 -> "melon_seeds";
            case 363 -> "beef";              case 364 -> "cooked_beef";      case 365 -> "chicken";
            case 366 -> "cooked_chicken";    case 367 -> "rotten_flesh";     case 368 -> "ender_pearl";
            case 369 -> "blaze_rod";         case 370 -> "ghast_tear";       case 371 -> "gold_nugget";
            case 372 -> "nether_wart";       case 373 -> "potion";           case 375 -> "spider_eye";
            case 376 -> "fermented_spider_eye"; case 377 -> "blaze_powder";  case 378 -> "magma_cream";
            case 384 -> "experience_bottle"; case 385 -> "fire_charge";      case 386 -> "writable_book";
            case 387 -> "written_book";      case 388 -> "emerald";          case 390 -> "flower_pot";
            case 391 -> "carrot";            case 392 -> "potato";           case 393 -> "baked_potato";
            case 394 -> "poisonous_potato";  case 395 -> "map";              case 396 -> "golden_carrot";
            case 400 -> "pumpkin_pie";       case 403 -> "enchanted_book";   case 406 -> "quartz";
            case 2256 -> "music_disc_13";    case 2257 -> "music_disc_cat";
            default -> null;
        };
        return id == null ? null : "minecraft:" + id;
    }

    /** Legacy raw fish (id 349): damage 0=cod, 1=salmon, 2=tropical fish, 3=pufferfish. */
    private static String fish(int damage) {
        return switch (damage) {
            case 1 -> "salmon";
            case 2 -> "tropical_fish";
            case 3 -> "pufferfish";
            default -> "cod";
        };
    }

    /** Legacy dye (id 351): damage 0..15 in the old ink-sac-first order → the modern dye/ingredient. */
    private static String dye(int damage) {
        return switch (damage) {
            case 1 -> "red_dye";     case 2 -> "green_dye";      case 3 -> "cocoa_beans";
            case 4 -> "lapis_lazuli"; case 5 -> "purple_dye";    case 6 -> "cyan_dye";
            case 7 -> "light_gray_dye"; case 8 -> "gray_dye";    case 9 -> "pink_dye";
            case 10 -> "lime_dye";   case 11 -> "yellow_dye";    case 12 -> "light_blue_dye";
            case 13 -> "magenta_dye"; case 14 -> "orange_dye";   case 15 -> "bone_meal";
            default -> "ink_sac";
        };
    }

    /** Modern sign block-entity nbt: {@code front_text.messages} from the four legacy lines. */
    private static CompoundTag signNbt(String[] lines) {
        CompoundTag be = new CompoundTag();
        be.put("front_text", signText(lines));
        be.put("back_text", signText(new String[] { "", "", "", "" }));
        be.putBoolean("is_waxed", false);
        return be;
    }

    private static CompoundTag signText(String[] lines) {
        ListTag messages = new ListTag();
        // Each message is a text component; a bare string decodes as a literal, which is exactly what
        // the plain legacy lines are (pre-1.8 signs stored raw text, not JSON).
        for (int i = 0; i < 4; i++)
            messages.add(StringTag.valueOf(i < lines.length ? lines[i] : ""));
        CompoundTag text = new CompoundTag();
        text.put("messages", messages);
        return text;
    }

    /** MCEdit block ordering: index = (y * length + z) * width + x. */
    private int index(int x, int y, int z) {
        return (y * length + z) * width + x;
    }

    private int idAt(int x, int y, int z) {
        return blocks[index(x, y, z)] & 0xFF;
    }

    private int dataAt(int x, int y, int z) {
        int i = index(x, y, z);
        return i < data.length ? data[i] & 0x0F : 0;
    }

    /** The mapped modern block at a local coordinate (air where the legacy id is 0). */
    public BlockState stateAt(int x, int y, int z) {
        return stateFor(x, y, z, idAt(x, y, z));
    }

    /**
     * Map one block, decoding a door from both of its halves (the hinge lives on the upper block, so
     * a per-block decode can't produce a correct double door). Everything else is a plain id+data map.
     */
    private BlockState stateFor(int x, int y, int z, int id) {
        if (LegacyBlocks.isDoor(id)) {
            int d = dataAt(x, y, z);
            boolean upper = (d & 8) != 0;
            int lowerData, upperData;
            if (upper) {
                upperData = d;
                lowerData = (y > 0 && idAt(x, y - 1, z) == id) ? dataAt(x, y - 1, z) : 0;
            } else {
                lowerData = d;
                upperData = (y + 1 < height && idAt(x, y + 1, z) == id) ? dataAt(x, y + 1, z) : 0;
            }
            return LegacyBlocks.doorState(id, lowerData, upperData, upper);
        }
        if (id == 54) // chest — pair adjacent same-facing chests into a double
            return chestState(x, y, z, dataAt(x, y, z));
        return LegacyBlocks.of(id, dataAt(x, y, z));
    }

    /**
     * A chest, with {@code CHEST_TYPE} set so an adjacent same-facing chest becomes a double (raw
     * {@code setBlock} placement doesn't auto-merge chests the way interactive placement does). Vanilla
     * rule: a LEFT chest's partner sits at {@code facing.getClockWise()}, a RIGHT chest's at
     * {@code getCounterClockWise()} — so a matching neighbour on the clockwise side makes this the left.
     */
    private BlockState chestState(int x, int y, int z, int data) {
        BlockState base = LegacyBlocks.of(54, data);
        Direction facing = LegacyBlocks.chestFacing(data);
        if (matchingChest(x, y, z, facing, facing.getClockWise()))
            return base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.LEFT);
        if (matchingChest(x, y, z, facing, facing.getCounterClockWise()))
            return base.setValue(BlockStateProperties.CHEST_TYPE, ChestType.RIGHT);
        return base;
    }

    private boolean matchingChest(int x, int y, int z, Direction facing, Direction toward) {
        int nx = x + toward.getStepX();
        int nz = z + toward.getStepZ();
        if (nx < 0 || nx >= width || nz < 0 || nz >= length)
            return false;
        return idAt(nx, y, nz) == 54 && LegacyBlocks.chestFacing(dataAt(nx, y, nz)) == facing;
    }

    /**
     * Build the vanilla structure NBT (the same shape {@link StructureTemplate#save} writes) and load
     * it into a fresh template — so downstream placement and re-saving are all native.
     */
    public StructureTemplate toTemplate(HolderGetter<Block> blockGetter, boolean keepAir) {
        ListTag palette = new ListTag();
        Map<BlockState, Integer> paletteIndex = new HashMap<>();
        ListTag blockList = new ListTag();

        for (int y = 0; y < height; y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    int id = idAt(x, y, z);
                    if (!keepAir && LegacyBlocks.isAir(id))
                        continue;
                    BlockState state = stateFor(x, y, z, id);
                    Integer idx = paletteIndex.get(state);
                    if (idx == null) {
                        idx = palette.size();
                        palette.add(NbtUtils.writeBlockState(state));
                        paletteIndex.put(state, idx);
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(x, y, z));
                    block.putInt("state", idx);
                    CompoundTag nbt = blockEntities.get(index(x, y, z));
                    if (nbt != null && state.hasBlockEntity()) // only attach to a block that hosts one
                        block.put("nbt", nbt);
                    blockList.add(block);
                }
            }
        }

        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(width, height, length));
        tag.put("palette", palette);
        tag.put("blocks", blockList);
        tag.put("entities", new ListTag());

        StructureTemplate template = new StructureTemplate();
        template.load(blockGetter, tag);
        return template;
    }

    private static ListTag intList(int a, int b, int c) {
        ListTag list = new ListTag();
        list.add(IntTag.valueOf(a));
        list.add(IntTag.valueOf(b));
        list.add(IntTag.valueOf(c));
        return list;
    }
}
