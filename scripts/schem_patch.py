#!/usr/bin/env python3
"""Patch cells of a legacy .schematic in place: schem_patch.py <in> <out> <edits.json>
edits.json: [{"x":..,"y":..,"z":..,"id":..,"data":..}, ...]  (ids/data as 1.12 numerics)
Also prints the cell before/after. Blocks/Data arrays are patched inside the raw NBT stream, so every
other tag (TileEntities, WEOffset, ...) is byte-identical."""
import gzip, struct, sys, json
src, dst, edits = sys.argv[1], sys.argv[2], json.load(open(sys.argv[3]))
raw = gzip.open(src, 'rb').read()
def short_tag(name, tagtype):
    key = bytes([tagtype]) + struct.pack('>H', len(name)) + name.encode()
    i = raw.find(key); assert i >= 0 and raw.find(key, i + 1) < 0, name
    return i + len(key)
def dim(name):
    at = short_tag(name, 2); return struct.unpack('>h', raw[at:at + 2])[0]
W, H, L = dim('Width'), dim('Height'), dim('Length')
blocks_at = short_tag('Blocks', 7); nb = struct.unpack('>i', raw[blocks_at:blocks_at + 4])[0]; blocks_at += 4
data_at = short_tag('Data', 7); nd = struct.unpack('>i', raw[data_at:data_at + 4])[0]; data_at += 4
assert nb == nd == W * H * L, (nb, nd, W, H, L)
buf = bytearray(raw)
for e in edits:
    i = (e['y'] * L + e['z']) * W + e['x']
    assert 0 <= e['x'] < W and 0 <= e['y'] < H and 0 <= e['z'] < L, e
    before = (buf[blocks_at + i], buf[data_at + i])
    buf[blocks_at + i] = e['id'] & 0xff; buf[data_at + i] = e.get('data', 0) & 0xff
    print(f"({e['x']},{e['y']},{e['z']}) {before} -> ({e['id']},{e.get('data',0)})")
with gzip.open(dst, 'wb') as f: f.write(bytes(buf))
print('wrote', dst, 'edits', len(edits))
