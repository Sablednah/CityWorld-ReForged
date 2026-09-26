#!/usr/bin/env python3
"""The vault's per-floor loot tables: the depth gradient's data half.

    scripts/gen_vault_floor_tables.py <loot dir> <reference key> <dialect>

<loot dir> is data/cityworld/loot_table/chests (loot_tables/ on 1.20.1); <reference key> is the
loot_table entry's id field: "value" on 1.21+, "name" on 1.20.1; <dialect> is "legacy" (1.20.1
through 26.2: "functions" lists, plain min/max rolls, bare entry types) or "26.3" (a single
"modifier", typed rolls, namespaced entry types).

Writes, idempotently and overwriting:
  vault_floor<k>.json          k = 1..3   the depth bonus for floor k, ending in the pack hook
  vault_floor<k>_extra.json               the hook, empty unless a pack replaces it (never overwritten)
  vault_<room>_floor<k>.json   room in quarters/office/armoury/ammo
                                          = the room's table + the floor's bonus, which is how
                                            "the same room, deeper, better AND worse" is spelled:
                                            a chest holds ONE table id, so composition lives here.
VaultLot.lootTierAt/putLoot pick <table>_floor<k> for a container on floor k (0 = the entry level,
which keeps the plain tables).
"""
import json, os, sys
d, key, dialect = sys.argv[1], sys.argv[2], sys.argv[3]
modern = dialect == '26.3'
ROOMS = ['quarters', 'office', 'armoury', 'ammo']

def item(name, weight, lo=None, hi=None, enchant=False):
    e = {'type': 'minecraft:item' if modern else 'item', 'name': name, 'weight': weight}
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

def ref(name, weight=None):
    e = {'type': 'minecraft:loot_table', key: 'cityworld:chests/' + name}
    if weight is not None:
        e['weight'] = weight
    return e

def rolls(lo, hi):
    if lo == hi:
        return lo
    return {'type': 'minecraft:uniform', 'min': lo, 'max': hi} if modern else {'min': lo, 'max': hi}

def write(name, table, overwrite=True):
    p = os.path.join(d, name + '.json')
    if not overwrite and os.path.exists(p):
        return
    json.dump(table, open(p, 'w'), indent=2)
    open(p, 'a').write('\n')
    print('wrote', name)

# the depth bonus, floor by floor: a little metal one down, gems two down, the real prizes at the bottom
BONUS = {
    1: (rolls(0, 1), [item('minecraft:iron_ingot', 10, 1, 3), item('minecraft:gold_ingot', 5, 1, 2),
                      item('minecraft:book', 3, enchant=True), item('minecraft:golden_apple', 2),
                      item('minecraft:experience_bottle', 3, 1, 3), empty(10)]),
    2: (rolls(1, 2), [item('minecraft:iron_ingot', 8, 2, 4), item('minecraft:gold_ingot', 6, 1, 3),
                      item('minecraft:diamond', 4, 1, 2), item('minecraft:book', 4, enchant=True),
                      item('minecraft:golden_apple', 3), item('minecraft:experience_bottle', 4, 2, 5), empty(6)]),
    3: (rolls(1, 3), [item('minecraft:diamond', 6, 1, 3), item('minecraft:netherite_scrap', 2),
                      item('minecraft:enchanted_golden_apple', 1), item('minecraft:diamond_sword', 2, enchant=True),
                      item('minecraft:diamond_chestplate', 2, enchant=True), item('minecraft:book', 5, enchant=True),
                      item('minecraft:totem_of_undying', 1), item('minecraft:golden_apple', 4, 1, 2),
                      item('minecraft:experience_bottle', 4, 3, 6), empty(4)]),
}
for k, (r, entries) in BONUS.items():
    write('vault_floor%d' % k, {'type': 'minecraft:chest', 'pools': [
        {'rolls': r, 'entries': entries + [ref('vault_floor%d_extra' % k, 15)]}]})
    write('vault_floor%d_extra' % k, {'type': 'minecraft:chest', 'pools': []}, overwrite=False)
    for room in ROOMS:
        write('vault_%s_floor%d' % (room, k), {'type': 'minecraft:chest', 'pools': [
            {'rolls': 1, 'entries': [ref('vault_' + room)]},
            {'rolls': 1, 'entries': [ref('vault_floor%d' % k)]}]})
