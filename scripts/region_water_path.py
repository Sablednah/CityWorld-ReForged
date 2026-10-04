#!/usr/bin/env python3
"""Is there a boat route through? Water connectivity at one y across a box of a saved world.

    python3 scripts/region_water_path.py <region dir> x0 x1 z0 z1 [y=62]

Flood-fills the water columns at y (4-connected, the way a boat moves) and reports each connected body that
touches the box's edge: which edges it touches and how many columns it holds. A river that runs through a city
should be ONE body touching two opposite edges; two bodies each touching one edge is a river cut in two (the
owner's "both diagonals are filled so you can't get through", 2026-10-04). Kill the server first.
"""
import collections, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_dump import block_at_fn

d, x0, x1, z0, z1 = sys.argv[1], *map(int, sys.argv[2:6])
y = int(sys.argv[6]) if len(sys.argv) > 6 else 62
at = block_at_fn(d)
wet = set()
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        try:
            b = at(x, y, z)
        except Exception:
            continue
        if b and ('water' in b or 'waterlogged=true' in b or 'seagrass' in b or 'kelp' in b):
            wet.add((x, z))
seen, bodies = set(), []
for start in wet:
    if start in seen:
        continue
    q, body = collections.deque([start]), []
    seen.add(start)
    while q:
        c = q.popleft()
        body.append(c)
        for nx, nz in ((c[0] + 1, c[1]), (c[0] - 1, c[1]), (c[0], c[1] + 1), (c[0], c[1] - 1)):
            if (nx, nz) in wet and (nx, nz) not in seen:
                seen.add((nx, nz))
                q.append((nx, nz))
    edges = set()
    for bx, bz in body:
        if bx == x0: edges.add('W')
        if bx == x1: edges.add('E')
        if bz == z0: edges.add('N')
        if bz == z1: edges.add('S')
    if edges:
        bodies.append((len(body), ''.join(sorted(edges))))
bodies.sort(reverse=True)
print(f'{len(wet)} water columns at y {y}; bodies touching the edge (columns, edges): {bodies[:10]}')
