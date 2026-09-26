#!/usr/bin/env python3
"""ASCII slices of a saved region, for reading a headless run back without a client.

    region_slice.py <region dir> plan <y> x0 x1 z0 z1        top-down at one height
    region_slice.py <region dir> xsec <z> x0 x1 y0 y1        a vertical section, x across
    region_slice.py <region dir> zsec <x> z0 z1 y0 y1        a vertical section, z across

One character per block (see CH; anything else prints its first letter, "." is air). Block tallies said the
subway station was fine; one xsec through its stair shaft showed every landing and step, and one zsec
found a mine lift dropping through the upper hall (2026-09-26). Companion to region_render.py.
"""
# region_slice.py <region dir> plan <y> x0 x1 z0 z1 | slice.py <region dir> xsec <z> x0 x1 y0 y1 | zsec <x> z0 z1 y0 y1
import sys, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_dump import block_at_fn
CH = {'air': '.', 'cave_air': '.', 'rail': '=', 'powered_rail': '#', 'gravel': ':', 'polished_andesite': 'p',
      'polished_andesite_stairs': 'S', 'stone_bricks': 'B', 'smooth_stone': 's', 'white_concrete': 'W',
      'yellow_concrete': 'Y', 'sea_lantern': '*', 'iron_bars': '|', 'iron_block': 'I', 'glass_pane': 'g',
      'redstone_block': 'R', 'lantern': 'l', 'chest': 'C', 'smooth_stone_slab': '_', 'quartz_block': 'Q',
      'lectern': 'L', 'oak_wall_sign': 'w', 'copper_bulb': 'o', 'water': '~', 'stone': '%', 'deepslate': '%', 'dirt': 'd'}
blk = block_at_fn(sys.argv[1])
def ch(x, y, z):
    try:
        n = (blk(x, y, z) or '?').split('[')[0].replace('minecraft:', '')
    except (FileNotFoundError, KeyError, IndexError):
        return '?' # region or chunk not generated
    if n in CH: return CH[n]
    if n.endswith('_concrete'): return 'c'
    if n.endswith('stairs'): return 'S'
    return n[0] if n else '?'
mode = sys.argv[2]; a = [int(v) for v in sys.argv[3:]]
if mode == 'plan':
    y, x0, x1, z0, z1 = a
    print('plan y=%d  x %d..%d (across), z %d..%d (down)' % (y, x0, x1, z0, z1))
    for z in range(z0, z1 + 1): print('%6d ' % z + ''.join(ch(x, y, z) for x in range(x0, x1 + 1)))
elif mode == 'xsec':
    z, x0, x1, y0, y1 = a
    print('section at z=%d  x %d..%d across, y %d..%d top-down' % (z, x0, x1, y0, y1))
    for y in range(y1, y0 - 1, -1): print('%4d ' % y + ''.join(ch(x, y, z) for x in range(x0, x1 + 1)))
else:
    x, z0, z1, y0, y1 = a
    print('section at x=%d  z %d..%d across, y %d..%d top-down' % (x, z0, z1, y0, y1))
    for y in range(y1, y0 - 1, -1): print('%4d ' % y + ''.join(ch(x, y, z) for z in range(z0, z1 + 1)))
