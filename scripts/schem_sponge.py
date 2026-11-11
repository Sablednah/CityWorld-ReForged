#!/usr/bin/env python3
"""schem_sponge.py <file.schem>: size, offset, palette, per-y layer summary of a Sponge schematic (v1-3)."""
import gzip, struct, sys
from collections import Counter
raw = gzip.open(sys.argv[1], 'rb').read()
pos = 0
def rd(n):
    global pos; b = raw[pos:pos+n]; pos += n; return b
def name(): l = struct.unpack('>H', rd(2))[0]; return rd(l).decode('utf8', 'replace')
def val(t):
    if t == 1: return struct.unpack('>b', rd(1))[0]
    if t == 2: return struct.unpack('>h', rd(2))[0]
    if t == 3: return struct.unpack('>i', rd(4))[0]
    if t == 4: return struct.unpack('>q', rd(8))[0]
    if t == 5: return struct.unpack('>f', rd(4))[0]
    if t == 6: return struct.unpack('>d', rd(8))[0]
    if t == 7: n = struct.unpack('>i', rd(4))[0]; return rd(n)
    if t == 8: return name()
    if t == 9:
        lt = rd(1)[0]; n = struct.unpack('>i', rd(4))[0]; return [val(lt) for _ in range(n)]
    if t == 10:
        d = {}
        while True:
            t2 = rd(1)[0]
            if t2 == 0: return d
            k = name(); d[k] = val(t2)
    if t == 11: n = struct.unpack('>i', rd(4))[0]; return [struct.unpack('>i', rd(4))[0] for _ in range(n)]
    if t == 12: n = struct.unpack('>i', rd(4))[0]; return [struct.unpack('>q', rd(8))[0] for _ in range(n)]
    raise ValueError(t)
t = rd(1)[0]; rootname = name(); root = val(t)
s = root.get('Schematic', root)
print('root', rootname, 'keys', sorted(k for k in s if k not in ('BlockData',)))
W, H, L = s['Width'], s['Height'], s['Length']
print('size', W, H, L, 'version', s.get('Version'), 'DataVersion', s.get('DataVersion'), 'Offset', s.get('Offset'), 'WEOffset', (s.get('Metadata') or {}).get('WEOffsetX'), (s.get('Metadata') or {}).get('WEOffsetY'), (s.get('Metadata') or {}).get('WEOffsetZ'))
blocks = s.get('Blocks', s)
pal = blocks['Palette'] if 'Palette' in blocks else s['Palette']
inv = {v: k for k, v in pal.items()}
data = blocks['Data'] if 'Data' in blocks else s['BlockData']
# varint decode
ids = []; i = 0
while i < len(data):
    v = 0; sh = 0
    while True:
        b = data[i]; i += 1; v |= (b & 0x7f) << sh; sh += 7
        if not b & 0x80: break
    ids.append(v)
assert len(ids) == W * H * L, (len(ids), W * H * L)
print('palette', len(pal), 'entries; block entities', len(blocks.get('BlockEntities', s.get('BlockEntities', []))))
for y in range(H):
    c = Counter(inv[ids[(y * L + z) * W + x]].split('[')[0] for z in range(L) for x in range(W))
    top = ', '.join(f'{k.replace("minecraft:","")}:{n}' for k, n in c.most_common(5))
    print(f'y{y:2d} {top}')
water = Counter()
for idx, pid in enumerate(ids):
    nm = inv[pid]
    if 'water' in nm or 'lava' in nm: water[(idx // (W * L), nm)] += 1
print('fluids by y:', sorted(water.items()))
