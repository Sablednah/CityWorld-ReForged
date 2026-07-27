package me.daddychurchill.CityWorld.Plugins;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
import me.daddychurchill.CityWorld.Support.Odds;
import me.daddychurchill.CityWorld.Support.MaterialList;
import me.daddychurchill.CityWorld.compat.Material;

/**
 * The per-world material palettes — what roads are paved with, what buildings are made of.
 *
 * <p><b>These lists ship populated, and that matters.</b> An earlier wave left them empty on the
 * assumption that callers' fallback defaults were what an unconfigured world used. They are not:
 * upstream fills every list at construction and the config (P7) only *overrides* it. Leaving them
 * empty silently paved every road in the caller's fallback colour instead of upstream's
 * {@code WHITE_TERRACOTTA} — invisible to the compiler, and only catchable by comparing against the
 * original.
 *
 * <p>Only the lists the ported code reads are here; the rest arrive with their call sites. Reading
 * the overrides from config is P7 (upstream tracked every list in a {@code listOfLists} for exactly
 * that, which is why each carries its config tag name).
 */
public class MaterialProvider {

    private List<MaterialList> listOfLists;

    // --- maze walls (MAZE style) ---------------------------------------------------------------

    private final static String tagMaterialListFor_MazeWalls = "Materials_List_For_MazeWalls";
    public final MaterialList itemsMaterialListFor_MazeWalls = createList(tagMaterialListFor_MazeWalls,

            // ORDER MATTERS IN THIS CASE
            Material.OBSIDIAN, // Walls
            Material.OBSIDIAN); // Underlayment

    // --- astral (ASTRAL style) -----------------------------------------------------------------

    private final static String tagSelectMaterial_AstralTowerLight = "Materials_For_AstralTowerLight";
    public final MaterialList itemsSelectMaterial_AstralTowerLight = createList(tagSelectMaterial_AstralTowerLight,
            Material.END_STONE, Material.END_STONE_BRICKS);

    private final static String tagSelectMaterial_AstralTowerDark = "Materials_For_AstralTowerDark";
    public final MaterialList itemsSelectMaterial_AstralTowerDark = createList(tagSelectMaterial_AstralTowerDark,
            Material.OBSIDIAN, Material.BLACK_CONCRETE);

    private final static String tagSelectMaterial_AstralTowerOres = "Materials_For_AstralTowerOres";
    public final MaterialList itemsSelectMaterial_AstralTowerOres = createList(tagSelectMaterial_AstralTowerOres,
            Material.LAVA, Material.WATER, Material.STONE, Material.INFESTED_STONE, Material.COAL_ORE,
            Material.DIAMOND_ORE, Material.EMERALD_ORE, Material.GOLD_ORE, Material.IRON_ORE, Material.LAPIS_ORE,
            Material.NETHER_QUARTZ_ORE, Material.REDSTONE_ORE);

    private final static String tagSelectMaterial_AstralTowerHalls = "Materials_For_AstralTowerHalls";
    public final MaterialList itemsSelectMaterial_AstralTowerHalls = createList(tagSelectMaterial_AstralTowerHalls,
            Material.OBSIDIAN, Material.STONE, Material.BRICKS, Material.COBBLESTONE, Material.SMOOTH_STONE,
            Material.MOSSY_COBBLESTONE);

    private final static String tagSelectMaterial_AstralTowerTrim = "Materials_For_AstralTowerTrim";
    public final MaterialList itemsSelectMaterial_AstralTowerTrim = createList(tagSelectMaterial_AstralTowerTrim,
            Material.AIR, Material.GLOWSTONE);

    private final static String tagSelectMaterial_AstralCubeOres = "Materials_For_AstralCubeOres";
    public final MaterialList itemsSelectMaterial_AstralCubeOres = createList(tagSelectMaterial_AstralCubeOres,
            Material.DIRT, Material.STONE, Material.INFESTED_STONE, Material.INFESTED_CHISELED_STONE_BRICKS,
            Material.INFESTED_COBBLESTONE, Material.INFESTED_CRACKED_STONE_BRICKS, Material.INFESTED_STONE_BRICKS,
            Material.COBBLESTONE, Material.SPRUCE_PLANKS, Material.IRON_BLOCK, Material.COAL_BLOCK,
            Material.DIAMOND_BLOCK, Material.REDSTONE_BLOCK, Material.QUARTZ_BLOCK);

    // --- roads ---------------------------------------------------------------------------------

    private final static String tagMaterialListFor_Roads = "Materials_List_For_Roads";
    public final MaterialList itemsMaterialListFor_Roads = createList(tagMaterialListFor_Roads,

            // ORDER MATTERS IN THIS CASE
            Material.WHITE_TERRACOTTA, // Pavement
            Material.QUARTZ_BLOCK, // Lines
            Material.STONE_SLAB, // Sidewalks
            Material.GRASS_PATH, // Dirt roads
            Material.GRASS_PATH); // Dirt sidewalks

    // --- buildings -----------------------------------------------------------------------------

    private final static String tagSelectMaterial_BuildingWalls = "Materials_For_BuildingWalls";
    public final MaterialList itemsSelectMaterial_BuildingWalls = createList(tagSelectMaterial_BuildingWalls,
            Material.COBBLESTONE, Material.SANDSTONE, Material.BRICKS, Material.MOSSY_COBBLESTONE, Material.CLAY,
            Material.NETHERRACK, Material.SOUL_SAND, Material.STONE, Material.SMOOTH_STONE, Material.STONE_BRICKS,
            Material.NETHER_BRICKS, Material.QUARTZ_BLOCK, Material.CHISELED_STONE_BRICKS,
            Material.CRACKED_STONE_BRICKS, Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS,
            Material.JUNGLE_PLANKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.WHITE_WOOL,
            Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL,
            Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL);

    private final static String tagSelectMaterial_BuildingFoundation = "Materials_For_BuildingFoundation";
    public final MaterialList itemsSelectMaterial_BuildingFoundation = createList(tagSelectMaterial_BuildingFoundation,
            Material.COBBLESTONE, Material.SANDSTONE, Material.BRICKS, Material.MOSSY_COBBLESTONE, Material.CLAY,
            Material.NETHERRACK, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.NETHER_BRICKS,
            Material.QUARTZ_BLOCK, Material.CHISELED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS);

    private final static String tagSelectMaterial_BuildingCeilings = "Materials_For_BuildingCeilings";
    public final MaterialList itemsSelectMaterial_BuildingCeilings = createList(tagSelectMaterial_BuildingCeilings,
            Material.COBBLESTONE, Material.SANDSTONE, Material.BRICKS, Material.MOSSY_COBBLESTONE, Material.CLAY,
            Material.NETHERRACK, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.NETHER_BRICKS,
            Material.QUARTZ_BLOCK, Material.CHISELED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS);

    private final static String tagSelectMaterial_BuildingRoofs = "Materials_For_BuildingRoofs";
    public final MaterialList itemsSelectMaterial_BuildingRoofs = createList(tagSelectMaterial_BuildingRoofs,
            Material.COBBLESTONE, Material.SANDSTONE, Material.BRICKS, Material.MOSSY_COBBLESTONE, Material.CLAY,
            Material.NETHERRACK, Material.SMOOTH_STONE, Material.STONE_BRICKS, Material.NETHER_BRICKS,
            Material.QUARTZ_BLOCK, Material.CHISELED_STONE_BRICKS, Material.CRACKED_STONE_BRICKS,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS);

    private final static String tagSelectMaterial_UnfinishedBuildings = "Materials_For_UnfinishedBuildings";
    public final MaterialList itemsSelectMaterial_UnfinishedBuildings = createList(
            tagSelectMaterial_UnfinishedBuildings,
            Material.CLAY, Material.WHITE_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.BLACK_TERRACOTTA, Material.WHITE_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.BLACK_CONCRETE);

    // --- quarries ------------------------------------------------------------------------------

    // The repetition is upstream's, and deliberate: the list is sampled uniformly, so repeating an
    // entry is how it weights the odds. Gravel is common, diamond and emerald are not.
    private final static String tagSelectMaterial_StoneWorksPiles = "Materials_For_QuaryPiles";
    public final MaterialList itemsSelectMaterial_QuaryPiles = createList(tagSelectMaterial_StoneWorksPiles,
            Material.GRAVEL, Material.GRAVEL, Material.GRAVEL, Material.GRAVEL, Material.GRAVEL, Material.COAL_ORE,
            Material.COAL_ORE, Material.COAL_ORE, Material.COAL_ORE, Material.IRON_ORE, Material.IRON_ORE,
            Material.IRON_ORE, Material.GOLD_ORE, Material.LAPIS_ORE, Material.REDSTONE_ORE, Material.DIAMOND_ORE,
            Material.EMERALD_ORE);

    // --- nature set-pieces ---------------------------------------------------------------------
    // Oil platforms get a random splash of coloured concrete; castles a weathered stone palette.

    private final static String tagSelectMaterial_OilPlatformFloor = "Materials_For_OilPlatformFloor";
    public final MaterialList itemsSelectMaterial_OilPlatformFloor = createList(tagSelectMaterial_OilPlatformFloor,
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE,
            Material.STONE);

    private final static String tagSelectMaterial_OilPlatformColumn = "Materials_For_OilPlatformColumn";
    public final MaterialList itemsSelectMaterial_OilPlatformColumn = createList(tagSelectMaterial_OilPlatformColumn,
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE,
            Material.STONE);

    private final static String tagSelectMaterial_Castles = "Materials_For_Castles";
    public final MaterialList itemsSelectMaterial_Castles = createList(tagSelectMaterial_Castles, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.CRACKED_STONE_BRICKS, Material.CHISELED_STONE_BRICKS,
            Material.QUARTZ_PILLAR, Material.CHISELED_QUARTZ_BLOCK);

    // --- municipal -----------------------------------------------------------------------------
    // Government buildings share one pale, civic palette across walls/foundations/ceilings.

    private final static String tagSelectMaterial_GovernmentWalls = "Materials_For_GovernmentWalls";
    public final MaterialList itemsSelectMaterial_GovernmentWalls = createList(tagSelectMaterial_GovernmentWalls,
            Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.QUARTZ_BLOCK, Material.QUARTZ_PILLAR,
            Material.CHISELED_QUARTZ_BLOCK, Material.END_STONE, Material.END_STONE_BRICKS, Material.WHITE_WOOL);

    private final static String tagSelectMaterial_GovernmentFoundations = "Materials_For_GovernmentFoundations";
    public final MaterialList itemsSelectMaterial_GovernmentFoundations = createList(
            tagSelectMaterial_GovernmentFoundations,
            Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.QUARTZ_BLOCK, Material.QUARTZ_PILLAR,
            Material.CHISELED_QUARTZ_BLOCK, Material.END_STONE, Material.END_STONE_BRICKS, Material.WHITE_WOOL);

    private final static String tagSelectMaterial_GovernmentCeilings = "Materials_For_GovernmentCeilings";
    public final MaterialList itemsSelectMaterial_GovernmentCeilings = createList(
            tagSelectMaterial_GovernmentCeilings,
            Material.WHITE_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA, Material.QUARTZ_BLOCK, Material.QUARTZ_PILLAR,
            Material.CHISELED_QUARTZ_BLOCK, Material.END_STONE, Material.END_STONE_BRICKS, Material.WHITE_WOOL);

    private final boolean modern;

    public MaterialProvider(CityWorldGenerator generator) {
        this.modern = generator.isModernStyle();

        // MODERN: retire plain STONE from the build pools (the ore pass peppers it) and fold the whole
        // decorative-stone palette in with equal weight, so a MODERN city grows blackstone, mud-brick,
        // purpur, basalt and copper buildings alongside the wood/brick/terracotta ones. CLASSIC keeps its
        // 1.8-era palette untouched.
        if (modern) {
            MaterialList[] buildPools = { itemsSelectMaterial_HouseWalls, itemsSelectMaterial_HouseRoofs,
                    itemsSelectMaterial_HouseFloors, itemsSelectMaterial_HouseCeilings,
                    itemsSelectMaterial_BuildingWalls, itemsSelectMaterial_BuildingRoofs,
                    itemsSelectMaterial_BuildingCeilings, itemsSelectMaterial_BuildingFoundation,
                    itemsSelectMaterial_ShackWalls, itemsSelectMaterial_ShackRoofs,
                    itemsSelectMaterial_ShedWalls, itemsSelectMaterial_ShedRoofs,
                    itemsSelectMaterial_FactoryInsides };
            for (MaterialList pool : buildPools) {
                pool.remove(Material.STONE);
                pool.add(MODERN_BUILD_STONES);
            }
        }
    }

    /**
     * Decorative stones for MODERN builds — used in place of plain {@code STONE}. On MODERN, vanilla's
     * {@code UNDERGROUND_ORES} step runs on build chunks (for ore veins in the rock below), and it also
     * peppers any {@code minecraft:stone} — including a stone wall or roof — with diorite/andesite/dirt
     * blobs. None of these are in the ore-replaceables tag, so they stay clean; the coppers also weather
     * for free. (The blackstone/basalt/etc. families are all here for variety.)
     */
    private static final Material[] MODERN_BUILD_STONES = {
            Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE_BRICKS,
            Material.CHISELED_POLISHED_BLACKSTONE, Material.CRACKED_POLISHED_BLACKSTONE_BRICKS, Material.GILDED_BLACKSTONE,
            Material.POLISHED_ANDESITE, Material.POLISHED_DIORITE, Material.POLISHED_GRANITE,
            Material.MUD_BRICKS, Material.RESIN_BRICKS,
            Material.BASALT, Material.POLISHED_BASALT, Material.SMOOTH_BASALT,
            Material.PRISMARINE, Material.PRISMARINE_BRICKS, Material.DARK_PRISMARINE,
            Material.PURPUR_BLOCK, Material.PURPUR_PILLAR,
            Material.COPPER_BLOCK, Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER,
            Material.OXIDIZED_CUT_COPPER };

    /**
     * MODERN only: swap plain {@code STONE} (and the raw granite/diorite/andesite/tuff the ore pass also
     * rewrites) for a decorative stone the ore pass leaves alone, so a stone building no longer picks up
     * dirt and diorite blobs. CLASSIC keeps its 1.8-era stone (its ore pass is CityWorld's own and never
     * touches builds). Call it on a build material the moment it's chosen.
     */
    public Material deOre(Material m, Odds odds) {
        if (modern && m == Material.STONE)
            return MODERN_BUILD_STONES[odds.getRandomInt(MODERN_BUILD_STONES.length)];
        return m;
    }

    private final static String tagSelectMaterial_FactoryInsides = "Materials_For_FactoryInsides";
    public final MaterialList itemsSelectMaterial_FactoryInsides = createList(tagSelectMaterial_FactoryInsides,
            Material.STONE, Material.SMOOTH_STONE, Material.QUARTZ_BLOCK, Material.CLAY, Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE);

    private final static String tagSelectMaterial_FactoryTanks = "Materials_For_FactoryTanks";
    public final MaterialList itemsSelectMaterial_FactoryTanks = createList(tagSelectMaterial_FactoryTanks, Material.LAVA,
            Material.ICE, Material.PACKED_ICE, Material.SNOW_BLOCK, Material.SLIME_BLOCK, Material.COAL_BLOCK,
            Material.SAND, Material.WATER, Material.GLASS, Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA, Material.WHITE_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER,
            Material.MAGENTA_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER,
            Material.LIME_CONCRETE_POWDER, Material.PINK_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER,
            Material.LIGHT_GRAY_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER, Material.PURPLE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER,
            Material.RED_CONCRETE_POWDER, Material.BLACK_CONCRETE_POWDER);

    private final static String tagSelectMaterial_BunkerBuildings = "Materials_For_BunkerBuildings";
    public final MaterialList itemsSelectMaterial_BunkerBuildings = createList(tagSelectMaterial_BunkerBuildings,
            Material.CLAY, Material.QUARTZ_BLOCK, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA, Material.WHITE_TERRACOTTA, Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE,
            Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE, Material.LIME_CONCRETE,
            Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE,
            Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE, Material.GREEN_CONCRETE,
            Material.RED_CONCRETE, Material.BLACK_CONCRETE);

    private final static String tagSelectMaterial_BunkerPlatforms = "Materials_For_BunkerPlatforms";
    public final MaterialList itemsSelectMaterial_BunkerPlatforms = createList(tagSelectMaterial_BunkerPlatforms,
            Material.CLAY, Material.QUARTZ_BLOCK, Material.QUARTZ_PILLAR, Material.CHISELED_QUARTZ_BLOCK,
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
            Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.WHITE_CONCRETE, Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE,
            Material.YELLOW_CONCRETE, Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE,
            Material.LIGHT_GRAY_CONCRETE, Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE,
            Material.BROWN_CONCRETE, Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE);

    private final static String tagSelectMaterial_BunkerBilge = "Materials_For_BunkerBilge";
    public final MaterialList itemsSelectMaterial_BunkerBilge = createList(tagSelectMaterial_BunkerBilge, Material.AIR,
            Material.LAVA, Material.WATER, Material.ICE, Material.PACKED_ICE);

    private final static String tagSelectMaterial_BunkerTanks = "Materials_For_BunkerTanks";
    public final MaterialList itemsSelectMaterial_BunkerTanks = createList(tagSelectMaterial_BunkerTanks, Material.SPONGE,
            Material.REDSTONE_BLOCK, Material.END_STONE, Material.EMERALD_BLOCK, Material.LAVA, Material.ICE,
            Material.PACKED_ICE, Material.SNOW_BLOCK, Material.SLIME_BLOCK, Material.COAL_BLOCK, Material.SAND,
            Material.WATER, Material.GLASS, Material.WHITE_STAINED_GLASS, Material.ORANGE_STAINED_GLASS,
            Material.MAGENTA_STAINED_GLASS, Material.LIGHT_BLUE_STAINED_GLASS, Material.YELLOW_STAINED_GLASS,
            Material.LIME_STAINED_GLASS, Material.PINK_STAINED_GLASS, Material.GRAY_STAINED_GLASS,
            Material.LIGHT_GRAY_STAINED_GLASS, Material.CYAN_STAINED_GLASS, Material.PURPLE_STAINED_GLASS,
            Material.BLUE_STAINED_GLASS, Material.BROWN_STAINED_GLASS, Material.GREEN_STAINED_GLASS,
            Material.RED_STAINED_GLASS, Material.BLACK_STAINED_GLASS, Material.TERRACOTTA, Material.WHITE_TERRACOTTA,
            Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA,
            Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA, Material.WHITE_CONCRETE_POWDER, Material.ORANGE_CONCRETE_POWDER,
            Material.MAGENTA_CONCRETE_POWDER, Material.LIGHT_BLUE_CONCRETE_POWDER, Material.YELLOW_CONCRETE_POWDER,
            Material.LIME_CONCRETE_POWDER, Material.PINK_CONCRETE_POWDER, Material.GRAY_CONCRETE_POWDER,
            Material.LIGHT_GRAY_CONCRETE_POWDER, Material.CYAN_CONCRETE_POWDER, Material.PURPLE_CONCRETE_POWDER,
            Material.BLUE_CONCRETE_POWDER, Material.BROWN_CONCRETE_POWDER, Material.GREEN_CONCRETE_POWDER,
            Material.RED_CONCRETE_POWDER, Material.BLACK_CONCRETE_POWDER);

    private final static String tagSelectMaterial_HouseWalls = "Materials_For_HouseWalls";
    public final MaterialList itemsSelectMaterial_HouseWalls = createList(tagSelectMaterial_HouseWalls, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.STONE, Material.SMOOTH_STONE, Material.SANDSTONE,
            Material.RED_SANDSTONE, Material.STONE_BRICKS, Material.NETHER_BRICKS, Material.BRICKS, Material.CLAY,
            Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
            Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.PRISMARINE,
            Material.PURPUR_BLOCK, Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS,
            Material.JUNGLE_PLANKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE, Material.END_STONE,
            Material.END_STONE_BRICKS);

    private final static String tagSelectMaterial_HouseFloors = "Materials_For_HouseFloors";
    public final MaterialList itemsSelectMaterial_HouseFloors = createList(tagSelectMaterial_HouseFloors,
            Material.COBBLESTONE, Material.COBBLESTONE, Material.COBBLESTONE, Material.STONE, Material.STONE,
            Material.STONE, Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS,
            Material.JUNGLE_PLANKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.WHITE_WOOL,
            Material.ORANGE_WOOL, Material.MAGENTA_WOOL, Material.LIGHT_BLUE_WOOL, Material.YELLOW_WOOL,
            Material.LIME_WOOL, Material.PINK_WOOL, Material.GRAY_WOOL, Material.LIGHT_GRAY_WOOL, Material.CYAN_WOOL,
            Material.PURPLE_WOOL, Material.BLUE_WOOL, Material.BROWN_WOOL, Material.GREEN_WOOL, Material.RED_WOOL,
            Material.BLACK_WOOL, Material.TERRACOTTA, Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA,
            Material.MAGENTA_TERRACOTTA, Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA,
            Material.LIME_TERRACOTTA, Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA,
            Material.LIGHT_GRAY_TERRACOTTA, Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA,
            Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA, Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA,
            Material.BLACK_TERRACOTTA, Material.BLACK_GLAZED_TERRACOTTA, Material.BLUE_GLAZED_TERRACOTTA,
            Material.BROWN_GLAZED_TERRACOTTA, Material.CYAN_GLAZED_TERRACOTTA, Material.GRAY_GLAZED_TERRACOTTA,
            Material.GREEN_GLAZED_TERRACOTTA, Material.LIGHT_BLUE_GLAZED_TERRACOTTA, Material.LIME_GLAZED_TERRACOTTA,
            Material.MAGENTA_GLAZED_TERRACOTTA, Material.ORANGE_GLAZED_TERRACOTTA, Material.PINK_GLAZED_TERRACOTTA,
            Material.PURPLE_GLAZED_TERRACOTTA, Material.RED_GLAZED_TERRACOTTA, Material.LIGHT_GRAY_GLAZED_TERRACOTTA,
            Material.WHITE_GLAZED_TERRACOTTA, Material.YELLOW_GLAZED_TERRACOTTA);

    private final static String tagSelectMaterial_HouseCeilings = "Materials_For_HouseCeilings";
    public final MaterialList itemsSelectMaterial_HouseCeilings = createList(tagSelectMaterial_HouseCeilings,
            Material.COBBLESTONE, Material.SMOOTH_STONE, Material.SANDSTONE, Material.RED_SANDSTONE,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.STONE);

    private final static String tagSelectMaterial_HouseRoofs = "Materials_For_HouseRoofs";
    public final MaterialList itemsSelectMaterial_HouseRoofs = createList(tagSelectMaterial_HouseRoofs, Material.COBBLESTONE,
            Material.MOSSY_COBBLESTONE, Material.SMOOTH_STONE, Material.SANDSTONE, Material.RED_SANDSTONE,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.STONE);

    private final static String tagSelectMaterial_ShackWalls = "Materials_For_ShackWalls";
    public final MaterialList itemsSelectMaterial_ShackWalls = createList(tagSelectMaterial_ShackWalls,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.MOSSY_COBBLESTONE, Material.RED_SANDSTONE,
            Material.STONE_BRICKS, Material.NETHER_BRICKS, Material.BRICKS, Material.STONE);

    private final static String tagSelectMaterial_ShackRoofs = "Materials_For_ShackRoofs";
    public final MaterialList itemsSelectMaterial_ShackRoofs = createList(tagSelectMaterial_ShackRoofs,
            Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS, Material.JUNGLE_PLANKS,
            Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.STONE);

    private final static String tagSelectMaterial_ShedWalls = "Materials_For_ShedWalls";
    public final MaterialList itemsSelectMaterial_ShedWalls = createList(tagSelectMaterial_ShedWalls, Material.SANDSTONE,
            Material.RED_SANDSTONE, Material.SPRUCE_PLANKS, Material.COBBLESTONE, Material.BRICKS,
            Material.SMOOTH_STONE, Material.ACACIA_PLANKS, Material.BIRCH_PLANKS, Material.DARK_OAK_PLANKS,
            Material.JUNGLE_PLANKS, Material.OAK_PLANKS, Material.SPRUCE_PLANKS, Material.STONE);

    private final static String tagSelectMaterial_ShedRoofs = "Materials_For_ShedRoofs";
    public final MaterialList itemsSelectMaterial_ShedRoofs = createList(tagSelectMaterial_ShedRoofs, Material.STONE_SLAB,
            Material.BIRCH_SLAB);

    private final static String tagSelectMaterial_WaterTowers = "Materials_For_WaterTowers";
    public final MaterialList itemsSelectMaterial_WaterTowers = createList(tagSelectMaterial_WaterTowers, Material.CLAY,
            Material.WHITE_TERRACOTTA, Material.ORANGE_TERRACOTTA, Material.MAGENTA_TERRACOTTA,
            Material.LIGHT_BLUE_TERRACOTTA, Material.YELLOW_TERRACOTTA, Material.LIME_TERRACOTTA,
            Material.PINK_TERRACOTTA, Material.GRAY_TERRACOTTA, Material.LIGHT_GRAY_TERRACOTTA,
            Material.CYAN_TERRACOTTA, Material.PURPLE_TERRACOTTA, Material.BLUE_TERRACOTTA, Material.BROWN_TERRACOTTA,
            Material.GREEN_TERRACOTTA, Material.RED_TERRACOTTA, Material.BLACK_TERRACOTTA, Material.WHITE_CONCRETE,
            Material.ORANGE_CONCRETE, Material.MAGENTA_CONCRETE, Material.LIGHT_BLUE_CONCRETE, Material.YELLOW_CONCRETE,
            Material.LIME_CONCRETE, Material.PINK_CONCRETE, Material.GRAY_CONCRETE, Material.LIGHT_GRAY_CONCRETE,
            Material.CYAN_CONCRETE, Material.PURPLE_CONCRETE, Material.BLUE_CONCRETE, Material.BROWN_CONCRETE,
            Material.GREEN_CONCRETE, Material.RED_CONCRETE, Material.BLACK_CONCRETE);

    private MaterialList createList(String name, Material... materials) {

        // create the list and add all of the goodies
        MaterialList list = new MaterialList(name, materials);

        // add it to the big list so we can generically remember it
        if (listOfLists == null)
            listOfLists = new ArrayList<>();
        listOfLists.add(list);

        return list;
    }
}
