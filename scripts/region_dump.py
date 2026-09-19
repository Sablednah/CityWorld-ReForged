#!/usr/bin/env python3
"""Minimal Anvil region reader: dumps block names in a world-coordinate box, layer by layer.

For reading back a build someone reworked by hand in game (it made the airship car stencil,
src/main/resources/cityworld/airship/car.txt). Copy the region files out of the save first, so a
running game cannot write under it. On 26.x the overworld is <save>/dimensions/minecraft/overworld/region.
import block_at_fn from this module to list cells with coordinates and full states.

usage: dump.py <region dir> x0 x1 y0 y1 z0 z1   (inclusive)
Prints, per y, a grid with z down the rows and x across the columns, using a legend of short codes.
"""
import io, math, struct, sys, zlib, gzip

def read_nbt(buf):
    def rd(fmt):
        size = struct.calcsize(fmt)
        v = struct.unpack(fmt, buf.read(size))
        return v[0]
    def name():
        n = rd('>H')
        return buf.read(n).decode('utf-8', 'replace')
    def payload(t):
        if t == 1: return rd('>b')
        if t == 2: return rd('>h')
        if t == 3: return rd('>i')
        if t == 4: return rd('>q')
        if t == 5: return rd('>f')
        if t == 6: return rd('>d')
        if t == 7:
            n = rd('>i'); return buf.read(n)
        if t == 8: return name()
        if t == 9:
            et = rd('>b'); n = rd('>i')
            return [payload(et) for _ in range(n)]
        if t == 10:
            d = {}
            while True:
                ct = rd('>b')
                if ct == 0: return d
                k = name(); d[k] = payload(ct)
        if t == 11:
            n = rd('>i'); return list(struct.unpack('>%di' % n, buf.read(4 * n)))
        if t == 12:
            n = rd('>i'); return list(struct.unpack('>%dq' % n, buf.read(8 * n)))
        raise ValueError('tag %d' % t)
    t = rd('>b'); name()
    return payload(t)

def chunk_nbt(region_dir, cx, cz):
    rx, rz = cx >> 5, cz >> 5
    path = '%s/r.%d.%d.mca' % (region_dir, rx, rz)
    with open(path, 'rb') as f:
        data = f.read()
    idx = ((cx & 31) + (cz & 31) * 32) * 4
    off = struct.unpack('>I', data[idx:idx + 4])[0]
    sector, count = off >> 8, off & 0xFF
    if sector == 0:
        return None
    start = sector * 4096
    length = struct.unpack('>I', data[start:start + 4])[0]
    comp = data[start + 4]
    raw = data[start + 5:start + 4 + length]
    if comp == 2: raw = zlib.decompress(raw)
    elif comp == 1: raw = gzip.decompress(raw)
    elif comp == 3: pass
    else: raise ValueError('compression %d' % comp)
    return read_nbt(io.BytesIO(raw))

def block_at_fn(region_dir):
    cache = {}
    def sections(cx, cz):
        key = (cx, cz)
        if key not in cache:
            nbt = chunk_nbt(region_dir, cx, cz)
            secs = {}
            if nbt:
                for s in nbt.get('sections', []):
                    bs = s.get('block_states')
                    if bs is None: continue
                    pal = bs['palette']
                    names = []
                    for p in pal:
                        # 26.3 writes a default state as a bare id string and a non-default one as
                        # {id, properties}; 1.21-26.2 wrote {Name, Properties} for every entry.
                        if isinstance(p, str):
                            p = {'Name': p}
                        n = (p.get('Name') or p.get('id') or p.get('')).replace('minecraft:', '')
                        props = p.get('Properties') or p.get('properties')
                        if props:
                            n += '[' + ','.join('%s=%s' % kv for kv in sorted(props.items())) + ']'
                        names.append(n)
                    secs[s['Y']] = (names, bs.get('data'))
            cache[key] = secs
        return cache[key]
    def at(x, y, z):
        secs = sections(x >> 4, z >> 4)
        s = secs.get(y >> 4)
        if s is None: return 'air'
        names, packed = s
        if packed is None: return names[0]
        bits = max(4, math.ceil(math.log2(len(names))))
        per = 64 // bits
        i = ((y & 15) * 16 + (z & 15)) * 16 + (x & 15)
        word = packed[i // per] & 0xFFFFFFFFFFFFFFFF
        v = (word >> ((i % per) * bits)) & ((1 << bits) - 1)
        return names[v]
    return at

if __name__ == '__main__':
    region = sys.argv[1]
    x0, x1, y0, y1, z0, z1 = map(int, sys.argv[2:8])
    full = len(sys.argv) > 8 and sys.argv[8] == 'full'
    at = block_at_fn(region)
    legend = {}
    codes = iter('ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@#$%&*+=?<>~^!')
    for y in range(y1, y0 - 1, -1):
        rows = []
        any_block = False
        for z in range(z0, z1 + 1):
            row = ''
            for x in range(x0, x1 + 1):
                b = at(x, y, z)
                if b == 'air' or b == 'cave_air' or b == 'void_air':
                    row += '.'
                    continue
                any_block = True
                key = b if full else b.split('[')[0]
                if key not in legend:
                    legend[key] = next(codes)
                row += legend[key]
            rows.append(row)
        if any_block:
            print('y=%d  (x %d..%d across, z %d..%d down)' % (y, x0, x1, z0, z1))
            for z, r in zip(range(z0, z1 + 1), rows):
                print('  %5d %s' % (z, r))
    print('legend:')
    for k, v in legend.items():
        print('  %s %s' % (v, k))
