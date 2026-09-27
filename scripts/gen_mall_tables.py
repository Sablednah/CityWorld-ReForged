#!/usr/bin/env python3
"""The mall's loot tables: one per shop kind (Plats/Urban/Mall.Kind), each ending in its _extra hook.

    scripts/gen_mall_tables.py <loot dir> <reference key> <dialect>

<loot dir> is data/cityworld/loot_table/chests (loot_tables/ on 1.20.1); <reference key> is the
loot_table entry's id field: "value" on 1.21+, "name" on 1.20.1; <dialect> is "legacy" (1.20.1 to 26.2)
or "26.3" (typed rolls, a single "modifier"). Writes mall_<kind>.json (overwriting) and an empty
mall_<kind>_extra.json a pack can replace (never overwritten). Only items that exist from 1.20.1 on.
"""
import json, os, sys
d, key, dialect = sys.argv[1], sys.argv[2], sys.argv[3]
modern = dialect == '26.3'

def item(name, weight, lo=None, hi=None, enchant=False):
    e = {'type': 'minecraft:item' if modern else 'item', 'name': 'minecraft:' + name, 'weight': weight}
    mods = []
    if lo is not None:
        count = {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if modern else {'min': lo, 'max': hi}
        mods.append({'type' if modern else 'function': 'set_count', 'count': count})
    if enchant:
        mods.append({'type' if modern else 'function': 'enchant_randomly'})
    if mods:
        if modern:
            e['modifier'] = mods[0] if len(mods) == 1 else {'type': 'sequence', 'modifiers': mods}
        else:
            e['functions'] = mods
    return e

def empty(weight):
    return {'type': 'minecraft:empty' if modern else 'empty', 'weight': weight}

def rolls(lo, hi):
    return {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if modern else {'min': lo, 'max': hi}

I = item
KINDS = {
    'music': [I('music_disc_13', 3), I('music_disc_cat', 3), I('music_disc_blocks', 3), I('music_disc_chirp', 3),
              I('music_disc_far', 3), I('music_disc_mall', 4), I('music_disc_mellohi', 3), I('music_disc_stal', 3),
              I('music_disc_strad', 3), I('music_disc_ward', 2), I('music_disc_wait', 3), I('note_block', 6, 1, 2),
              I('jukebox', 2), I('goat_horn', 1)],
    'florist': [I('poppy', 8, 1, 6), I('dandelion', 8, 1, 6), I('cornflower', 6, 1, 4), I('allium', 5, 1, 4),
                I('red_tulip', 5, 1, 4), I('oxeye_daisy', 5, 1, 4), I('azure_bluet', 5, 1, 4), I('lily_of_the_valley', 4, 1, 3),
                I('sunflower', 3, 1, 2), I('rose_bush', 3, 1, 2), I('peony', 3, 1, 2), I('flower_pot', 6, 1, 4),
                I('bone_meal', 8, 2, 8), I('wheat_seeds', 4, 2, 6), I('azalea', 2), I('flowering_azalea', 2), I('cherry_sapling', 1)],
    'fashion': [I('leather_helmet', 6, enchant=True), I('leather_chestplate', 6, enchant=True), I('leather_leggings', 6, enchant=True),
                I('leather_boots', 6, enchant=True), I('white_wool', 6, 1, 4), I('pink_wool', 4, 1, 4), I('black_wool', 4, 1, 4),
                I('string', 6, 2, 6), I('pink_dye', 3, 1, 3), I('light_blue_dye', 3, 1, 3), I('golden_helmet', 1)],
    'hardware': [I('iron_pickaxe', 3), I('iron_shovel', 3), I('iron_axe', 3), I('stone_pickaxe', 5), I('shears', 4),
                 I('flint_and_steel', 3), I('bucket', 4), I('iron_nugget', 8, 3, 9), I('iron_ingot', 4, 1, 3),
                 I('lantern', 4, 1, 2), I('ladder', 5, 2, 8), I('rail', 4, 2, 8), I('torch', 6, 4, 12),
                 I('oak_planks', 5, 4, 12), I('stick', 5, 4, 12)],
    'grocer': [I('bread', 10, 1, 4), I('apple', 8, 1, 4), I('carrot', 8, 1, 5), I('potato', 8, 1, 5), I('beetroot', 5, 1, 4),
               I('melon_slice', 6, 2, 6), I('pumpkin', 3), I('sweet_berries', 5, 2, 6), I('egg', 5, 1, 4), I('sugar', 4, 1, 4),
               I('cookie', 4, 2, 6), I('baked_potato', 4, 1, 3)],
    'bakery': [I('bread', 12, 1, 5), I('cake', 3), I('cookie', 10, 2, 8), I('pumpkin_pie', 6, 1, 3), I('wheat', 6, 2, 6),
               I('sugar', 6, 1, 4), I('egg', 4, 1, 4)],
    'cafe': [I('cookie', 8, 2, 6), I('pumpkin_pie', 4, 1, 2), I('bread', 5, 1, 3), I('honey_bottle', 5, 1, 2),
             I('milk_bucket', 3), I('glass_bottle', 6, 1, 4), I('sugar', 5, 1, 4), I('cocoa_beans', 6, 1, 6), I('sweet_berries', 4, 2, 5)],
    'books': [I('book', 12, 1, 4), I('writable_book', 5), I('enchanted_book', 3, enchant=True), I('paper', 8, 2, 8),
              I('map', 3), I('feather', 5, 1, 4), I('ink_sac', 5, 1, 3), I('lectern', 1), I('bookshelf', 2)],
    'toys': [I('snowball', 8, 2, 8), I('egg', 4, 1, 3), I('firework_rocket', 6, 1, 4), I('slime_ball', 5, 1, 4),
             I('name_tag', 3), I('lead', 4), I('fishing_rod', 3), I('bow', 2), I('arrow', 4, 2, 8), I('white_banner', 3),
             I('bell', 1), I('carrot_on_a_stick', 3), I('spyglass', 2)],
    'pets': [I('lead', 8, 1, 2), I('name_tag', 6), I('bone', 8, 1, 4), I('cod', 6, 1, 4), I('salmon', 5, 1, 3),
             I('wheat_seeds', 6, 2, 8), I('hay_block', 3), I('tropical_fish_bucket', 2), I('saddle', 1), I('rabbit_foot', 1)],
    'jeweller': [I('gold_nugget', 12, 2, 9), I('gold_ingot', 6, 1, 3), I('emerald', 6, 1, 3), I('amethyst_shard', 8, 1, 4),
                 I('diamond', 2), I('clock', 3), I('golden_apple', 1), I('lapis_lazuli', 6, 2, 6), I('quartz', 6, 2, 6)],
    'electronics': [I('redstone', 12, 2, 12), I('repeater', 6, 1, 3), I('comparator', 4, 1, 2), I('redstone_lamp', 4, 1, 2),
                    I('observer', 3), I('daylight_detector', 3), I('clock', 3), I('compass', 3), I('spyglass', 2), I('lever', 5, 1, 3),
                    I('redstone_torch', 5, 1, 4)],
    'sports': [I('bow', 5, enchant=True), I('arrow', 10, 4, 16), I('fishing_rod', 5), I('crossbow', 3), I('shield', 3),
               I('saddle', 2), I('leather_boots', 4, enchant=True), I('target', 2), I('lead', 3), I('trident', 1)],
    'pharmacy': [I('glass_bottle', 10, 1, 4), I('glistering_melon_slice', 4, 1, 2), I('golden_carrot', 4, 1, 3),
                 I('honey_bottle', 6), I('milk_bucket', 3), I('spider_eye', 4), I('sugar', 6, 1, 4), I('nether_wart', 3, 1, 3),
                 I('brewing_stand', 1), I('ghast_tear', 1), I('blaze_powder', 2)],
    'furniture': [I('oak_planks', 8, 4, 16), I('white_bed', 3), I('white_carpet', 6, 2, 6), I('lantern', 4, 1, 2),
                  I('painting', 5), I('item_frame', 5, 1, 3), I('flower_pot', 4, 1, 3), I('oak_stairs', 5, 2, 6),
                  I('bookshelf', 2), I('candle', 4, 1, 3)],
    'gifts': [I('candle', 10, 1, 4), I('white_candle', 6, 1, 3), I('decorated_pot', 3), I('flower_pot', 5, 1, 2), I('painting', 4),
              I('firework_rocket', 4, 1, 3), I('cake', 2), I('cookie', 6, 2, 6), I('name_tag', 2), I('pink_dye', 3, 1, 3)],
    'art': [I('painting', 8, 1, 2), I('item_frame', 6, 1, 3), I('red_dye', 5, 1, 4), I('blue_dye', 5, 1, 4), I('yellow_dye', 5, 1, 4),
            I('green_dye', 5, 1, 4), I('white_dye', 5, 1, 4), I('black_dye', 5, 1, 4), I('paper', 5, 2, 6), I('ink_sac', 4, 1, 3),
            I('brush', 2)],
    'department': [I('leather_chestplate', 4, enchant=True), I('white_wool', 5, 1, 4), I('book', 5, 1, 3), I('clock', 3),
                   I('compass', 3), I('bread', 5, 1, 3), I('candle', 5, 1, 3), I('lantern', 3), I('painting', 3),
                   I('gold_nugget', 5, 2, 6), I('redstone', 4, 2, 6), I('cookie', 5, 2, 6), I('white_bed', 1)],
    'food': [I('bread', 8, 1, 3), I('cookie', 10, 2, 8), I('baked_potato', 8, 1, 3), I('cooked_chicken', 6, 1, 2),
             I('cooked_beef', 5, 1, 2), I('pumpkin_pie', 5, 1, 2), I('melon_slice', 6, 2, 6), I('honey_bottle', 4),
             I('mushroom_stew', 3), I('sweet_berries', 5, 2, 6)],
}
for kind, entries in KINDS.items():
    entries = [e for e in entries if e['weight'] > 0]
    table = {'type': 'minecraft:chest', 'pools': [{'rolls': rolls(2, 5), 'entries': entries + [empty(10),
             {'type': 'minecraft:loot_table', key: 'cityworld:chests/mall_%s_extra' % kind, 'weight': 15}]}]}
    p = os.path.join(d, 'mall_%s.json' % kind)
    json.dump(table, open(p, 'w'), indent=2); open(p, 'a').write('\n')
    x = os.path.join(d, 'mall_%s_extra.json' % kind)
    if not os.path.exists(x):
        json.dump({'type': 'minecraft:chest', 'pools': []}, open(x, 'w'), indent=2); open(x, 'a').write('\n')
print('wrote', len(KINDS), 'mall tables')
