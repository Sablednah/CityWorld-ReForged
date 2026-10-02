#!/usr/bin/env python3
"""Exact block counts for every block id starting with a prefix, across a saved region folder.

    python3 scripts/region_tally.py <region dir> <id prefix>      e.g.  run/world/region farmersdelight:

Decodes each section's packed palette indices, so the numbers are BLOCKS, not "sections whose palette
mentions it" (which is all a palette scan can say, and reads the same for one block as for a thousand).
Prints the count per id with two sample positions and their properties. Kill the server first so the
region is flushed. How Farmer's Delight support was measured (PORTING.md, v5.18.0).
"""
import collections, glob, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import region_dump as rd
d, prefix = sys.argv[1], sys.argv[2]
counts = collections.Counter(); where = collections.defaultdict(list)
for f in sorted(glob.glob(d + '/r.*.mca')):
    rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', f)[0])
    for i in range(32):
        for j in range(32):
            cx, cz = rx * 32 + i, rz * 32 + j
            try: n = rd.chunk_nbt(d, cx, cz)
            except Exception: continue
            if not n: continue
            for sec in n.get('sections', []):
                bs = sec.get('block_states') or {}
                pal = bs.get('palette') or []
                hits = {k for k, p in enumerate(pal) if p['Name'].startswith(prefix)}
                if not hits: continue
                data = bs.get('data')
                y0 = sec['Y'] * 16
                if data is None:
                    key = pal[0]['Name']; counts[key] += 4096; continue
                bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
                for idx in range(4096):
                    v = (data[idx // per] & 0xFFFFFFFFFFFFFFFF) >> ((idx % per) * bits) & mask
                    if v in hits:
                        p = pal[v]; props = p.get('Properties') or {}
                        key = p['Name']
                        counts[key] += 1
                        if len(where[key]) < 3:
                            where[key].append((cx * 16 + (idx & 15), y0 + (idx >> 8), cz * 16 + ((idx >> 4) & 15), dict(props)))
for k, v in sorted(counts.items()):
    print(f"{v:7d} {k}  e.g. {where[k][:2]}")
