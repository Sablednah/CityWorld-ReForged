#!/usr/bin/env python3
"""Count structure starts by id across a region folder, INVALID attempts counted separately (an attempt vanilla or
CityWorld's wilderness-only rule refused): structure_starts.py <region dir> [id prefix]. Pure Python over every chunk:
~10 min for a 13,000-chunk world. Pair with structure_sites.py (where a set will be TRIED on a seed)."""
import collections, glob, re, sys
sys.path.insert(0, '/mnt/d/Repos/sable/CityWorld-ReForged/scripts')
import region_dump as rd
d = sys.argv[1]; prefix = sys.argv[2] if len(sys.argv) > 2 else ''
starts = collections.Counter(); chunks = 0; examples = {}
for f in sorted(glob.glob(d + '/r.*.mca')):
    rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', f)[0])
    for i in range(32):
        for j in range(32):
            cx, cz = rx * 32 + i, rz * 32 + j
            try: n = rd.chunk_nbt(d, cx, cz)
            except Exception: continue
            if not n: continue
            chunks += 1
            st = (n.get('structures') or {}).get('starts') or {}
            for sid, s in st.items():
                if s.get('id', '') == 'INVALID':
                    starts['INVALID ' + sid] += 1; examples.setdefault('INVALID ' + sid, (cx * 16, cz * 16)); continue
                if sid.startswith(prefix):
                    starts[sid] += 1; examples.setdefault(sid, (cx * 16, cz * 16))
print('chunks', chunks)
for sid, c in starts.most_common(): print(f'{c:5} {sid}  e.g. x={examples[sid][0]} z={examples[sid][1]}')
