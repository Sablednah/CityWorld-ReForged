#!/usr/bin/env python3
"""Render a box of a saved world to a PNG: a top-down plan above a side elevation (looking north).

For judging SHAPE from a headless run — does the city sit on the island, are there chunk-square slabs, what
hangs underneath — which block tallies cannot show and a screenshot needs a client for. No dependencies:
reads regions through region_dump.py and writes the PNG by hand.

usage: region_render.py <region dir> x0 x1 y0 y1 z0 z1 out.png [scale] [north|west]
The elevation looks north by default (x across); "west" looks along x instead (z across) — for anything that
runs north-south, which end-on is just a dot.
"""
import struct, sys, zlib, hashlib, os
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_dump import block_at_fn

AIR = {'air', 'cave_air', 'void_air'}
FIXED = {'end_stone': (222, 224, 164), 'obsidian': (30, 20, 50), 'chorus_plant': (150, 100, 150),
         'chorus_flower': (190, 150, 190), 'glass': (180, 220, 235), 'glass_pane': (180, 220, 235),
         'grass_block': (110, 170, 80), 'stone_slab': (150, 150, 150), 'dirt_path': (150, 120, 70)}

def colour(name):
    name = name.split('[')[0]
    if name in FIXED:
        return FIXED[name]
    h = hashlib.md5(name.encode()).digest()
    return (60 + h[0] % 160, 60 + h[1] % 160, 60 + h[2] % 160)

def shade(rgb, f):
    return tuple(max(0, min(255, int(c * f))) for c in rgb)

def write_png(path, rows):
    h, w = len(rows), len(rows[0])
    raw = b''.join(b'\x00' + bytes(v for px in row for v in px) for row in rows)
    def chunk(tag, data):
        return struct.pack('>I', len(data)) + tag + data + struct.pack('>I', zlib.crc32(tag + data) & 0xFFFFFFFF)
    with open(path, 'wb') as f:
        f.write(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 2, 0, 0, 0))
                + chunk(b'IDAT', zlib.compress(raw, 6)) + chunk(b'IEND', b''))

def main():
    region = sys.argv[1]
    x0, x1, y0, y1, z0, z1 = map(int, sys.argv[2:8])
    out = sys.argv[8]
    scale = int(sys.argv[9]) if len(sys.argv) > 9 else 3
    west = len(sys.argv) > 10 and sys.argv[10] == 'west'
    at = block_at_fn(region)
    void = (12, 8, 20)
    w, d, hgt = x1 - x0 + 1, z1 - z0 + 1, y1 - y0 + 1
    plan = [[void] * w for _ in range(d)]
    across = d if west else w
    side = [[void] * across for _ in range(hgt)]
    depth = [[None] * across for _ in range(hgt)]
    # nearest block wins the elevation: southmost when looking north, eastmost when looking west
    for xi in (range(w - 1, -1, -1) if west else range(w)):
        for zi in range(d - 1, -1, -1):
            top = None
            for y in range(y1, y0 - 1, -1):
                b = at(x0 + xi, y, z0 + zi)
                if b in AIR:
                    continue
                if top is None:
                    top = (y, b)
                col, near = (zi, xi / w) if west else (xi, zi / d)
                if depth[y1 - y][col] is None:
                    depth[y1 - y][col] = 1
                    side[y1 - y][col] = shade(colour(b), 0.55 + 0.45 * near)
            if top:
                plan[zi][xi] = shade(colour(top[1]), 0.6 + 0.4 * (top[0] - y0) / hgt)
    width = max(w, across)
    pad = lambda row: row + [void] * (width - len(row))
    rows = [pad(r) for r in plan] + [[(60, 60, 60)] * width for _ in range(2)] + [pad(r) for r in side]
    big = [[px for px in row for _ in range(scale)] for row in rows for _ in range(scale)]
    write_png(out, big)
    print('wrote', out, len(big[0]), 'x', len(big))

if __name__ == '__main__':
    main()
