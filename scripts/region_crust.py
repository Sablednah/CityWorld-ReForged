#!/usr/bin/env python3
"""Crust thickness over the first cavity in each column -- the floating-lid signature.

A cavity under METRES of stone is a cave: CityWorld cuts those everywhere and they are not a bug.
A cavity under 1-3 blocks of grass and dirt is a LID: the decoration pass painted a surface at the
planned height where the ground had been shaved away. Counting cavities cannot tell these apart;
counting what sits on top of them can.

usage: region_crust.py <region dir> x0 x1 y0 y1 z0 z1 <label>

Measured 2026-09-22 on four 441-column boxes, two inside a village footprint the structure pad had
shaped and two outside it, all in one world: padded boxes read 1.6% and 0.0% lid-like, unpadded
controls 32.6% and 0.0%. That killed the hypothesis the numbers were meant to test. The plain cavity
count could not: it read ~50% in BOTH, because it was dominated by CityWorld's own caves and by
village house interiors, which are also "solid above, air, solid below". Never quote a cavity count
without a control box and this crust split beside it.
"""
import os, sys, collections
sys.path.insert(0, 'scripts')
from region_dump import block_at_fn
from region_voids import is_solid, is_natural

region, x0, x1, y0, y1, z0, z1, label = sys.argv[1], *map(int, sys.argv[2:8]), sys.argv[8]
at = block_at_fn(region)
hist = collections.Counter()
thin = []
cols = 0
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        col = [at(x, y, z) for y in range(y0, y1 + 1)]
        top = next((i for i in range(len(col) - 1, -1, -1) if is_solid(col[i])), None)
        if top is None:
            continue
        cols += 1
        # crust = consecutive solid from the surface down
        i = top
        while i >= 0 and is_solid(col[i]):
            i -= 1
        crust = top - i
        if i < 0:
            continue
        # the air run directly beneath that crust
        j = i
        while j >= 0 and not is_solid(col[j]):
            j -= 1
        run = i - j
        if j < 0 or run < 3:
            continue
        if not (is_natural(col[i + 1]) and is_natural(col[j])):
            continue          # a house floor/room, not ground
        hist[crust] += 1
        if crust <= 3:
            thin.append((crust, run, x, z, y0 + top))

print(f"=== {label}: {cols} columns with ground")
print(f"    columns whose FIRST cavity (>=3 tall, natural) sits under a crust of:")
for k in sorted(hist):
    bar = '#' * min(60, hist[k])
    print(f"      {k:>3} block(s): {hist[k]:>4}  {bar}")
lid = sum(v for k, v in hist.items() if k <= 3)
tot = sum(hist.values())
print(f"    LID-LIKE (crust <= 3): {lid} of {tot} cavity columns"
      f"  ({100.0*lid/tot:.1f}%)" if tot else "    no cavity columns")
if thin:
    thin.sort(key=lambda t: -t[1])
    print("    thinnest-crust worst cases (crust, air run, x, z, surface y):")
    for c, r, x, z, ty in thin[:8]:
        print(f"      crust {c}, {r:>2} tall air   x={x} z={z}  surface y={ty}")
print()
