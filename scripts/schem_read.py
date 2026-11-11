#!/usr/bin/env python3
"""schem_read.py <schematic> x0 x1 y0 y1 z0 z1 : print (id,data) per cell, one layer at a time (z rows, x columns)."""
import gzip, struct, sys
src = sys.argv[1]; x0, x1, y0, y1, z0, z1 = map(int, sys.argv[2:8])
raw = gzip.open(src, 'rb').read()
def short_tag(name, tagtype):
    key = bytes([tagtype]) + struct.pack('>H', len(name)) + name.encode()
    i = raw.find(key); assert i >= 0, name; return i + len(key)
def dim(name):
    at = short_tag(name, 2); return struct.unpack('>h', raw[at:at + 2])[0]
W, H, L = dim('Width'), dim('Height'), dim('Length')
b = short_tag('Blocks', 7) + 4; d = short_tag('Data', 7) + 4
print('size', W, H, L)
for y in range(y0, y1 + 1):
    print('y', y)
    for z in range(z0, z1 + 1):
        print(f'{z:3d} ' + ' '.join(f"{raw[b+(y*L+z)*W+x]:3d}:{raw[d+(y*L+z)*W+x]:<2d}" for x in range(x0, x1 + 1)))
