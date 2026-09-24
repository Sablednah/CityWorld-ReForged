#!/usr/bin/env python3
"""Give every CityWorld chest table an empty `_extra` companion a pack can replace.

    scripts/add_loot_extras.py <loot dir> <reference key>

<loot dir> is data/cityworld/loot_table/chests (loot_tables/ on 1.20.1); <reference key> is the
loot_table entry's id field: "value" on 1.21+, "name" on 1.20.1. Idempotent: a table that already
references its _extra is left alone. The hook is the last entry of the LAST pool at weight 15 --
the same shape the vault rooms, hospital, shop, pond and nightstand have carried since 5.9.0.
"""
import json, os, sys
d, key = sys.argv[1], sys.argv[2]
for f in sorted(os.listdir(d)):
    if not f.endswith('.json') or f.endswith('_extra.json'):
        continue
    name = f[:-5]
    p = os.path.join(d, f)
    t = json.load(open(p))
    ref = 'cityworld:chests/' + name + '_extra'
    if ref in open(p).read():
        continue
    t['pools'][-1]['entries'].append({'type': 'minecraft:loot_table', key: ref, 'weight': 15})
    json.dump(t, open(p, 'w'), indent=2); open(p, 'a').write('\n')
    x = os.path.join(d, name + '_extra.json')
    if not os.path.exists(x):
        json.dump({'type': 'minecraft:chest', 'pools': []}, open(x, 'w'), indent=2); open(x, 'a').write('\n')
    print('hooked', name)
