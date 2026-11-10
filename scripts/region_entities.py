#!/usr/bin/env python3
"""Count the entities saved in a world's entity regions by id, optionally filtered by an id prefix.

  python3 -I scripts/region_entities.py <world>/entities [<id prefix>] [--pos]

Reads every r.*.mca there (the entities folder, not the region folder), prints "id count" lines in count order;
with a prefix only ids starting with it, and --pos lists each one's position and rotation too. Zero prints nothing."""
import collections, glob, os, struct, sys, zlib
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import region_dump as rd

def chunks(path):
    with open(path, 'rb') as f:
        data = f.read()
    for i in range(1024):
        off = int.from_bytes(data[i * 4:i * 4 + 3], 'big') * 4096
        if off == 0:
            continue
        n = int.from_bytes(data[off:off + 4], 'big')
        kind = data[off + 4]
        raw = data[off + 5:off + 4 + n]
        if kind == 2:
            raw = zlib.decompress(raw)
        elif kind == 3:
            pass
        elif kind == 1:
            import gzip
            raw = gzip.decompress(raw)
        yield rd.read_nbt(__import__('io').BytesIO(raw))

if __name__ == '__main__':
    args = [a for a in sys.argv[1:] if not a.startswith('--')]
    if not args:
        sys.exit(__doc__)
    d = args[0]
    prefix = args[1] if len(args) > 1 else ''
    show = '--pos' in sys.argv
    counts = collections.Counter()
    for path in sorted(glob.glob(os.path.join(d, 'r.*.mca'))):
        for c in chunks(path):
            for e in c.get('Entities', []):
                eid = e.get('id', '?')
                if not eid.startswith(prefix):
                    continue
                counts[eid] += 1
                if show:
                    print(eid, [round(v, 1) for v in e.get('Pos', [])], [round(v, 1) for v in e.get('Rotation', [])],
                          {k: e[k] for k in ('battery', 'fuel', 'license_plate', 'systemName') if k in e})
    for eid, n in counts.most_common():
        print(eid, n)
