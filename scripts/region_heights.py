#!/usr/bin/env python3
"""Where things stand in a saved world: entities and block entities by y, across a box of chunks.

    python3 scripts/region_heights.py <world dir> cx0 cx1 cz0 cz1

For a vanilla-terrain city drawn at street level 64 and lifted to its site's own level (worldgen/CitySites),
the question is whether EVERYTHING was lifted: villagers, item frames, paintings, armour stands, chests, signs.
Anything left at the drawing height shows up as a second cluster of y values thirty-odd blocks under the city.
Prints a histogram of y (8-block bands) per entity id and per block-entity id. Kill the server first.
"""
import collections, glob, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import region_dump as rd

world, cx0, cx1, cz0, cz1 = sys.argv[1], *map(int, sys.argv[2:6])

def band(y):
    return int(y) // 8 * 8

ents = collections.defaultdict(collections.Counter)
for f in sorted(glob.glob(world + '/entities/r.*.mca')):
    rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', f)[0])
    for i in range(32):
        for j in range(32):
            cx, cz = rx * 32 + i, rz * 32 + j
            if not (cx0 <= cx <= cx1 and cz0 <= cz <= cz1):
                continue
            try:
                n = rd.chunk_nbt(world + '/entities', cx, cz)
            except Exception:
                continue
            if not n:
                continue
            for e in n.get('Entities', []):
                ents[e.get('id', '?')][band(e['Pos'][1])] += 1

bes = collections.defaultdict(collections.Counter)
for f in sorted(glob.glob(world + '/region/r.*.mca')):
    rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', f)[0])
    for i in range(32):
        for j in range(32):
            cx, cz = rx * 32 + i, rz * 32 + j
            if not (cx0 <= cx <= cx1 and cz0 <= cz <= cz1):
                continue
            try:
                n = rd.chunk_nbt(world + '/region', cx, cz)
            except Exception:
                continue
            if not n:
                continue
            for be in n.get('block_entities', []):
                bes[be.get('id', '?')][band(be['y'])] += 1

print('ENTITIES (id: y-band=count)')
for k in sorted(ents):
    print(' ', k, dict(sorted(ents[k].items())))
print('BLOCK ENTITIES (id: y-band=count)')
for k in sorted(bes):
    print(' ', k, dict(sorted(bes[k].items())))
