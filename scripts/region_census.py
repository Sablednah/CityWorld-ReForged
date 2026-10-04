#!/usr/bin/env python3
"""Every block id in a saved region folder, counted exactly, in one pass.

    python3 scripts/region_census.py <region dir> [substring ...]

region_tally.py answers one prefix per scan; this decodes each section once and counts every id, then
prints the ids containing any of the given substrings (all ids when none are given), largest first.
How the neutral palette add-on was measured (2026-10-04): a city with and without the pack.
"""
import collections, glob, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import region_dump as rd
d, wanted = sys.argv[1], sys.argv[2:]
counts = collections.Counter()
for f in sorted(glob.glob(d + '/r.*.mca')):
    rx, rz = map(int, re.findall(r'r\.(-?\d+)\.(-?\d+)', f)[0])
    for i in range(32):
        for j in range(32):
            try:
                n = rd.chunk_nbt(d, rx * 32 + i, rz * 32 + j)
            except Exception:
                continue
            if not n:
                continue
            for sec in n.get('sections', []):
                bs = sec.get('block_states') or {}
                pal = bs.get('palette') or []
                if not pal:
                    continue
                data = bs.get('data')
                if data is None:
                    counts[pal[0]['Name']] += 4096
                    continue
                bits = max(4, (len(pal) - 1).bit_length()); per = 64 // bits; mask = (1 << bits) - 1
                local = collections.Counter()
                for word in data:
                    w = word & 0xFFFFFFFFFFFFFFFF
                    for k in range(per):
                        local[(w >> (k * bits)) & mask] += 1
                # the last word may hold padding past index 4095
                extra = len(data) * per - 4096
                if extra > 0:
                    w = data[-1] & 0xFFFFFFFFFFFFFFFF
                    for k in range(per - extra, per):
                        local[(w >> (k * bits)) & mask] -= 1
                for v, c in local.items():
                    if v < len(pal):
                        counts[pal[v]['Name']] += c
for name, c in counts.most_common():
    if not wanted or any(w in name for w in wanted):
        print(f'{c:9d} {name}')
