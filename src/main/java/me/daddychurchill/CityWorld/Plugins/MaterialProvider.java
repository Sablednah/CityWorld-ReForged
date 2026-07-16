package me.daddychurchill.CityWorld.Plugins;

import java.util.ArrayList;
import java.util.List;

import me.daddychurchill.CityWorld.CityWorldGenerator;
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

    public MaterialProvider(CityWorldGenerator generator) {
    }

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
