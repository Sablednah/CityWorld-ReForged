#!/usr/bin/env python3
"""Export a box of a saved world as a WorldEdit "Sponge" .schem, for viewing in an NBT/schematic tool.

For judging SHAPE when a rendered PNG is too hard to read — open the file in a schematic viewer and
rotate it. Companion to region_render.py, which draws a plan and elevation; this hands you the blocks.

usage: region_export.py <region dir> x0 x1 y0 y1 z0 z1 out.schem [dataversion]

Sponge v2, because this repo's own SpongeSchematic reader already parses that format: the export
therefore ROUND-TRIPS — drop it in the schematics folder and CityWorld can place it. The vanilla
structure-block .nbt shape would also open in a viewer, but Templates.build strips air from those by
default (a structure-block export records explicit air for its whole box, and placing that stamps an
air cuboid over the terrain), and a terrain region is mostly air above ground — so that format fights
its own reader here.

Air IS exported: you asked for a region, not a build. If you ever want to PLACE one of these, set
keepAir appropriately in the .yml sidecar, or the air above the ground will be stamped down as air.

DataVersion defaults to 0, which makes Templates.build skip the data-fixer altogether rather than
have it "upgrade" a file written by this script; pass the real world version only if a tool demands it.
"""
import gzip, os, struct, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_dump import block_at_fn

AIR_DEFAULT = 'minecraft:air'


# --- minimal NBT writer (mirrors the reader in region_dump.py; only the tags this format needs) ----

def _str(s):
    b = s.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def tag_byte_array(name, data):
    return b'\x07' + _str(name) + struct.pack('>i', len(data)) + bytes(data)


def tag_short(name, v):
    return b'\x02' + _str(name) + struct.pack('>h', v)


def tag_int(name, v):
    return b'\x03' + _str(name) + struct.pack('>i', v)


def tag_string(name, v):
    return b'\x08' + _str(name) + _str(v)


def tag_compound(name, body):
    return b'\x0a' + _str(name) + body + b'\x00'


def tag_int_array(name, values):
    return b'\x0b' + _str(name) + struct.pack('>i', len(values)) \
        + b''.join(struct.pack('>i', v) for v in values)


def varint(v):
    """Unsigned LEB128 — exactly what SpongeSchematic.decodeVarints reads back."""
    out = bytearray()
    while True:
        b = v & 0x7F
        v >>= 7
        out.append(b | (0x80 if v else 0))
        if not v:
            return bytes(out)


def main():
    if len(sys.argv) < 9:
        print(__doc__)
        return 2
    region = sys.argv[1]
    x0, x1, y0, y1, z0, z1 = map(int, sys.argv[2:8])
    out = sys.argv[8]
    data_version = int(sys.argv[9]) if len(sys.argv) > 9 else 0

    x0, x1 = min(x0, x1), max(x0, x1)
    y0, y1 = min(y0, y1), max(y0, y1)
    z0, z1 = min(z0, z1), max(z0, z1)
    w, h, l = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
    if w > 0xFFFF or h > 0xFFFF or l > 0xFFFF:
        print('!! Width/Height/Length are NBT shorts; box is too large in one axis')
        return 2

    at = block_at_fn(region)
    palette = {}          # 'minecraft:foo[bar=baz]' -> id
    data = bytearray()
    missing = 0

    # Sponge order is Y, then Z, then X — verified against SpongeSchematic's decode loop
    # (flat = (y * length + z) * width + x), not from memory: the wrong order yields a file that
    # looks plausible and is silently transposed.
    for y in range(y0, y1 + 1):
        for z in range(z0, z1 + 1):
            for x in range(x0, x1 + 1):
                name = at(x, y, z)
                if not name:
                    name = 'air'
                    missing += 1
                # block_at_fn strips the namespace; the reader's palette keys carry it.
                state = name if ':' in name else 'minecraft:' + name
                idx = palette.get(state)
                if idx is None:
                    idx = len(palette)
                    palette[state] = idx
                data += varint(idx)

    body = b''
    body += tag_int('Version', 2)
    body += tag_int('DataVersion', data_version)
    body += tag_short('Width', w)
    body += tag_short('Height', h)
    body += tag_short('Length', l)
    body += tag_int_array('Offset', [x0, y0, z0])
    body += tag_compound('Palette', b''.join(tag_int(k, v) for k, v in palette.items()))
    body += tag_int('PaletteMax', len(palette))
    body += tag_byte_array('BlockData', data)
    root = tag_compound('Schematic', body)

    with gzip.open(out, 'wb') as f:
        f.write(root)

    size = os.path.getsize(out)
    human = '%.1f MB' % (size / 1e6) if size >= 1e6 else '%.0f KB' % (size / 1e3)
    print('wrote %s  %dx%dx%d = %d blocks, %d palette entries, %s'
          % (out, w, h, l, w * h * l, len(palette), human))
    if missing:
        print('   %d columns had no chunk on disk and were written as air' % missing)
    return 0


if __name__ == '__main__':
    sys.exit(main())
