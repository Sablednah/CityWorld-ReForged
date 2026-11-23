#!/usr/bin/env python3
"""gen.py [--check biomes.json ...] — write the Biome Palettes add-on's data from the tables below.

Run from anywhere; it rewrites packs/biome-palettes/src/data. A theme is a file in CityWorld's
cityworld:palette_theme registry (the biomes it covers) plus one block tag per building part
(#cityworld:palette/biome/<theme>/<part>). Tags are written twice, tags/block (1.21+) and tags/blocks (1.20.1).

--check takes JSON files of {namespace: {biome path: ...}} (any harvest of the biomes your mods add) and prints
the theme each biome gets by ids, words and patterns. Biome tags are not known here, so a biome a theme reaches
only by a tag shows as unthemed, and one a tag gives to another theme shows the word's; the game's own answer
is in the log with -Dcityworld.diagnostics=true.
"""
import json, os, re, shutil, sys

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.path.join(HERE, 'src', 'data')
NS = 'cityworld_biome_palettes'

# Biome lists: 'ns:id' exact, '#tag', a word (any biome whose name contains it), a pattern with *, '!' to exclude.
# A biome two themes name goes to the closest naming (id, tag, word), then the higher priority.
THEMES = {
    'frozen': {
        'priority': 40,
        'biomes': ['minecraft:snowy_plains', 'minecraft:ice_spikes', 'minecraft:frozen_river',
                   'minecraft:frozen_ocean', 'minecraft:deep_frozen_ocean', 'minecraft:snowy_beach',
                   '#c:is_icy', '#forge:is_icy',
                   'tundra', 'glacial', 'glacier', 'arctic', 'frozen', 'icy', '*:ice_*', '*:*_ice', '*:*_ice_*',
                   'cold_desert', 'wintry_lowlands', 'white_shore'],
        'wall': ['spruce_planks', 'calcite', 'white_concrete', 'polished_diorite', 'light_gray_concrete',
                 'white_terracotta', 'light_blue_terracotta', 'stone_bricks', 'packed_ice'],
        'roof': ['spruce_planks', 'calcite', 'light_blue_terracotta', 'deepslate_tiles'],
        'column': ['stripped_spruce_wood', 'polished_diorite', 'packed_ice'],
        'foundation': ['stone_bricks', 'cobbled_deepslate', 'polished_andesite'],
        'floor': ['spruce_planks', 'polished_diorite'],
        'wood': ['spruce_planks'],
    },
    'blossom': {
        'priority': 35,
        'biomes': ['minecraft:cherry_grove', 'cherry', 'sakura', 'blossom', 'blooming', 'wisteria', 'jacaranda',
                   'magnolia'],
        'wall': ['cherry_planks', 'white_terracotta', 'pink_terracotta', 'calcite', 'birch_planks',
                 'white_concrete', 'stripped_cherry_wood'],
        'roof': ['cherry_planks', 'pink_terracotta', 'deepslate_tiles'],
        'column': ['stripped_cherry_wood', 'calcite', 'stripped_birch_wood'],
        'foundation': ['stone_bricks', 'calcite', 'polished_diorite'],
        'floor': ['cherry_planks', 'birch_planks'],
        'wood': ['cherry_planks', 'birch_planks'],
    },
    'badlands': {
        'priority': 30,
        'biomes': ['minecraft:badlands', 'minecraft:eroded_badlands', 'minecraft:wooded_badlands',
                   '#minecraft:is_badlands', 'badlands', 'mesa', 'canyon', 'outback', 'painted'],
        'wall': ['red_sandstone', 'smooth_red_sandstone', 'cut_red_sandstone', 'terracotta', 'orange_terracotta',
                 'red_terracotta', 'brown_terracotta', 'yellow_terracotta', 'light_gray_terracotta',
                 'white_terracotta'],
        'roof': ['cut_red_sandstone', 'smooth_red_sandstone', 'brown_terracotta', 'red_terracotta'],
        'column': ['chiseled_red_sandstone', 'cut_red_sandstone', 'stripped_dark_oak_wood'],
        'foundation': ['red_sandstone', 'terracotta'],
        'floor': ['smooth_red_sandstone', 'dark_oak_planks', 'spruce_planks'],
        'wood': ['dark_oak_planks', 'spruce_planks'],
    },
    'swamp': {
        'priority': 25,
        'biomes': ['minecraft:swamp', 'minecraft:mangrove_swamp', '#c:is_swamp', '#forge:is_swamp',
                   'swamp', 'bayou', 'marsh', 'wetland', 'mangrove', 'morass', 'mire', 'muskeg', 'moor',
                   'floodplain', '*:delta', '*:*_delta', '*:bog', '*:*_bog', '*:fen', '*:*_fen', 'haunted_lakes'],
        'wall': ['mangrove_planks', 'dark_oak_planks', 'mud_bricks', 'mossy_cobblestone', 'mossy_stone_bricks',
                 'oak_planks', 'packed_mud'],
        'roof': ['mangrove_planks', 'dark_oak_planks', 'mud_bricks'],
        'column': ['stripped_mangrove_wood', 'stripped_dark_oak_wood', 'mud_bricks'],
        'foundation': ['mud_bricks', 'mossy_cobblestone', 'packed_mud'],
        'floor': ['mangrove_planks', 'dark_oak_planks'],
        'wood': ['mangrove_planks', 'dark_oak_planks'],
    },
    'tropical': {
        'priority': 20,
        'biomes': ['minecraft:jungle', 'minecraft:sparse_jungle', 'minecraft:bamboo_jungle',
                   '#minecraft:is_jungle', 'jungle', 'rainforest', 'tropic', 'bamboo', 'cloud_forest', '!temperate'],
        'wall': ['mossy_stone_bricks', 'stone_bricks', 'jungle_planks', 'bamboo_planks', 'mossy_cobblestone',
                 'mud_bricks', 'white_terracotta'],
        'roof': ['jungle_planks', 'bamboo_mosaic', 'mud_bricks'],
        'column': ['stripped_jungle_wood', 'mossy_stone_bricks', 'stripped_bamboo_block'],
        'foundation': ['mossy_cobblestone', 'mud_bricks', 'mossy_stone_bricks'],
        'floor': ['jungle_planks', 'bamboo_planks'],
        'wood': ['jungle_planks', 'bamboo_planks'],
    },
    'desert': {
        'priority': 15,
        'biomes': ['minecraft:desert', '#c:is_desert', '#forge:is_desert', '#c:is_sandy', '#forge:is_sandy',
                   'desert', 'dune', 'oasis', 'arid', 'dryland', 'wasteland', 'ancient_sands', 'sandstone',
                   'mirage', '!cold_desert'],
        'wall': ['sandstone', 'smooth_sandstone', 'cut_sandstone', 'terracotta', 'white_terracotta',
                 'yellow_terracotta', 'orange_terracotta'],
        'roof': ['smooth_sandstone', 'cut_sandstone', 'terracotta'],
        'column': ['chiseled_sandstone', 'cut_sandstone'],
        'foundation': ['sandstone', 'red_sandstone'],
        'floor': ['smooth_sandstone', 'cut_sandstone'],
        'wood': ['acacia_planks', 'birch_planks'],
    },
    'savanna': {
        'priority': 15,
        'biomes': ['minecraft:savanna', 'minecraft:savanna_plateau', 'minecraft:windswept_savanna',
                   '#minecraft:is_savanna', 'savanna', 'savannah', 'baobab', 'bushland', 'brushland', 'scrubland',
                   'shrubland', 'steppe', '!cold_shrubland'],
        'wall': ['acacia_planks', 'orange_terracotta', 'terracotta', 'yellow_terracotta', 'mud_bricks',
                 'packed_mud', 'smooth_sandstone'],
        'roof': ['acacia_planks', 'mud_bricks', 'brown_terracotta'],
        'column': ['stripped_acacia_wood', 'mud_bricks'],
        'foundation': ['packed_mud', 'terracotta', 'mud_bricks'],
        'floor': ['acacia_planks', 'mud_bricks'],
        'wood': ['acacia_planks'],
    },
    'boreal': {
        'priority': 15,
        'biomes': ['minecraft:taiga', 'minecraft:snowy_taiga', 'minecraft:old_growth_pine_taiga',
                   'minecraft:old_growth_spruce_taiga', 'minecraft:grove', '#minecraft:is_taiga',
                   'taiga', 'boreal', 'spruce', '*:pine_*', '*:*_pine_*', '*:*_pine', 'pinelands', 'conifer', 'redwood', 'larch', 'hemlock', 'siberian',
                   'shield', 'snowy', 'wintry', 'yosemite', 'yellowstone', '*:fir_*', '*:*_fir_*'],
        'wall': ['spruce_planks', 'stone_bricks', 'cobblestone', 'deepslate_bricks', 'dark_oak_planks', 'bricks'],
        'roof': ['spruce_planks', 'dark_oak_planks', 'deepslate_tiles'],
        'column': ['stripped_spruce_wood', 'stone_bricks', 'polished_deepslate'],
        'foundation': ['cobblestone', 'stone_bricks', 'cobbled_deepslate'],
        'floor': ['spruce_planks', 'stone_bricks'],
        'wood': ['spruce_planks', 'dark_oak_planks'],
    },
    'coastal': {
        'priority': 10,
        'biomes': ['minecraft:beach', 'minecraft:stony_shore', '#minecraft:is_beach', '#minecraft:is_ocean',
                   'beach', 'shore', 'coast', 'ocean', 'cove', 'reef', 'lagoon', 'isles', 'island'],
        'wall': ['white_terracotta', 'smooth_sandstone', 'birch_planks', 'white_concrete', 'light_blue_terracotta',
                 'cyan_terracotta', 'prismarine_bricks', 'bricks'],
        'roof': ['birch_planks', 'prismarine_bricks', 'dark_prismarine', 'cyan_terracotta', 'oak_planks'],
        'column': ['stripped_birch_wood', 'cut_sandstone', 'prismarine_bricks'],
        'foundation': ['sandstone', 'stone_bricks', 'cobblestone'],
        'floor': ['birch_planks', 'oak_planks', 'smooth_sandstone'],
        'wood': ['birch_planks', 'oak_planks'],
    },
    'mountain': {
        'priority': 5,
        'biomes': ['minecraft:jagged_peaks', 'minecraft:frozen_peaks', 'minecraft:stony_peaks',
                   'minecraft:snowy_slopes', 'minecraft:windswept_hills', 'minecraft:windswept_gravelly_hills',
                   'minecraft:windswept_forest', '#minecraft:is_mountain',
                   'mountain', 'peaks', 'cliffs', 'highland', 'alpine', 'crag', 'stony', 'windswept', 'slopes',
                   'spires', 'volcan', 'caldera', 'basalt', 'heights'],
        'wall': ['stone_bricks', 'polished_andesite', 'cobblestone', 'spruce_planks', 'deepslate_bricks', 'bricks',
                 'polished_deepslate'],
        'roof': ['deepslate_tiles', 'spruce_planks', 'cobbled_deepslate', 'stone_bricks'],
        'column': ['polished_deepslate', 'stripped_spruce_wood', 'polished_andesite', 'chiseled_stone_bricks'],
        'foundation': ['cobblestone', 'cobbled_deepslate', 'stone_bricks'],
        'floor': ['spruce_planks', 'polished_andesite', 'stone_bricks'],
        'wood': ['spruce_planks', 'oak_planks'],
    },
}

# which of CityWorld's parts each role fills (the street and the windows are left as the world has them)
ROLES = {
    'wall': ['building/wall', 'government/wall', 'house/wall', 'factory/office'],
    'roof': ['building/roof', 'government/roof', 'house/roof'],
    'column': ['building/column', 'government/column'],
    'foundation': ['building/foundation', 'government/foundation'],
    'floor': ['building/floor', 'government/floor', 'house/floor', 'house/ceiling'],
    'wood': ['shack/wall', 'shack/floor', 'shack/roof', 'shed/wall', 'shed/roof'],
}

VANILLA = """badlands bamboo_jungle basalt_deltas beach birch_forest cherry_grove cold_ocean crimson_forest dark_forest
deep_cold_ocean deep_dark deep_frozen_ocean deep_lukewarm_ocean deep_ocean desert dripstone_caves end_barrens
end_highlands end_midlands eroded_badlands flower_forest forest frozen_ocean frozen_peaks frozen_river grove
ice_spikes jagged_peaks jungle lukewarm_ocean lush_caves mangrove_swamp meadow mushroom_fields nether_wastes ocean
old_growth_birch_forest old_growth_pine_taiga old_growth_spruce_taiga pale_garden plains river savanna
savanna_plateau small_end_islands snowy_beach snowy_plains snowy_slopes snowy_taiga soul_sand_valley sparse_jungle
stony_peaks stony_shore sunflower_plains swamp taiga the_end the_void warm_ocean warped_forest windswept_forest
windswept_gravelly_hills windswept_hills windswept_savanna wooded_badlands""".split()


def write(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def rank(term, bid):
    """The game's BiomeMatch, less tags: 3 exact id, 1 word or pattern, 0 no."""
    if term.startswith('#'):
        return 0
    if '*' in term:
        glob = term if ':' in term else '*:' + term
        return 1 if re.fullmatch('.*'.join(re.escape(p) for p in glob.split('*')), bid) else 0
    if ':' in term:
        return 3 if term == bid else 0
    return 1 if term in bid.split(':', 1)[1] else 0


def theme_of(bid):
    best, best_rank = None, 0
    for name, t in sorted(THEMES.items(), key=lambda kv: (-kv[1]['priority'], NS + ':' + kv[0])):
        terms = [x.lower() for x in t['biomes']]
        if any(rank(x[1:], bid) for x in terms if x.startswith('!')):
            continue
        r = max((rank(x, bid) for x in terms if not x.startswith('!')), default=0)
        if r > best_rank:
            best, best_rank = name, r
    return best


def generate():
    for sub in (os.path.join(DATA, NS), os.path.join(DATA, 'cityworld')):
        shutil.rmtree(sub, ignore_errors=True)
    tags = 0
    for name, t in THEMES.items():
        write(f'{DATA}/{NS}/cityworld/palette_theme/{name}.json', {'priority': t['priority'], 'biomes': t['biomes']})
        for role, parts in ROLES.items():
            for part in parts:
                for folder in ('block', 'blocks'):
                    write(f'{DATA}/cityworld/tags/{folder}/palette/biome/{name}/{part}.json',
                          {'replace': False, 'values': ['minecraft:' + x for x in t[role]]})
                tags += 1
    print(f'{len(THEMES)} themes, {tags} palette tags (each in tags/block and tags/blocks) -> {DATA}')


def check(files):
    biomes = {'minecraft:' + b for b in VANILLA}
    for path in files:
        for ns, entries in json.load(open(path)).items():
            biomes |= {f'{ns}:{b}' for b in entries}
    by_theme = {}
    for bid in sorted(biomes):
        by_theme.setdefault(theme_of(bid) or '(none)', []).append(bid)
    for name in list(THEMES) + ['(none)']:
        got = by_theme.get(name, [])
        print(f'\n{name} ({len(got)}):')
        print('  ' + ' '.join(got))


if __name__ == '__main__':
    generate()
    if len(sys.argv) > 2 and sys.argv[1] == '--check':
        check(sys.argv[2:])
