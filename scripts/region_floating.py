#!/usr/bin/env python3
"""Is the building RESTING on the ground? Lowest build block near the surface, air beneath it.

v1 of this took the FIRST build block from the top with air under it -- which for a house is the roof
over its own room, so 3363 of 3635 "gaps" were 2 blocks tall and were simply interiors, and the loop
broke there and never saw the real gap under the floor. Same house-interior confound that made the
void scan read ~50% everywhere. Take the LOWEST build block within 20 of the surface instead: the
question is whether the structure sits on ground, and only its underside answers that.

usage: region_floating.py <region dir> x0 x1 y0 y1 z0 z1 <label>

⚠ INDICATIVE ONLY. Use scripts/region_pieceground.py to judge whether structures sit on the ground.
This script cannot tell a HANG from an INTERIOR, and not for want of patching -- it is missing the
information needed. A column inside a house whose floor is the terrain itself has its roof as the
only build block in the column, with the room's air beneath and real ground below that; x1266,-751
reads "spruce_log over 10 air" and is a correctly seated upstairs, with a bed and a wall torch two
columns away at the same heights. Deciding that needs the PIECE BOUNDING BOX, which only the
generator's own PLANPAD log carries. Two successive attempts to fix it here (first-from-top ->
lowest-near-surface -> skip-if-build-below) each fixed one case and missed the next.

⚠ Run this beside region_voids.py, never instead of it. They catch OPPOSITE faults and each is blind
to the other's: voids wants ENCLOSED air (a lid painted over a cavity), this wants OPEN air (a piece
left hanging). The structure pad produced one fault, was "fixed", and produced the other; four clean-
ish void scans said nothing was wrong while the owner found floating houses in a schematic viewer in
one look. A gap of 2-3 blocks under the TOP build block of a column is usually just a room, which is
why this measures the bottom one.

Measured on a taiga village with the pad enabled, 2026-09-22: attempt one 1994 of 3682 build-bearing
columns (54.2%) hanging, attempt two 1612 of 3349 (48.1%). See CityWorldChunkGenerator.PAD_ENABLED
for why that is a design limit rather than a constant to tune.
"""
import sys, collections
sys.path.insert(0, 'scripts')
from region_dump import block_at_fn
from region_voids import is_solid, is_natural

region, x0, x1, y0, y1, z0, z1, label = sys.argv[1], *map(int, sys.argv[2:8]), sys.argv[8]
at = block_at_fn(region)
hits = []
gaps = collections.Counter()
resting = 0
for x in range(x0, x1 + 1):
    for z in range(z0, z1 + 1):
        col = [at(x, y, z) for y in range(y0, y1 + 1)]
        top = next((i for i in range(len(col) - 1, -1, -1) if is_solid(col[i])), None)
        if top is None:
            continue
        built = [i for i in range(max(0, top - 20), top + 1)
                 if is_solid(col[i]) and not is_natural(col[i])]
        if not built:
            continue
        low = min(built)
        # ⚠ A BURIED build is not a floating one. x1240,-760 is a copper_grate/copper_chain mine cap
        # under 15 blocks of intact stone, sitting over its own shaft -- and it was reported as an
        # 18-block "floating build", which then got quoted as the worst regression of a pad attempt
        # that had not caused it. If natural ground lies between this block and the surface, the
        # structure is underground and its air pocket belongs to region_voids.py, not here.
        if any(is_solid(col[k]) and is_natural(col[k]) for k in range(low + 1, top + 1)):
            continue
        j = low - 1
        run = 0
        while j >= 0 and not is_solid(col[j]):
            run += 1
            j -= 1
        # ⚠ A ROOF OVER A ROOM IS NOT A HANG. Confound six, 2026-09-22: v1 took the first build
        # block from the top (a roof, over its own room); v2 took the LOWEST build block near the
        # surface -- but where a column's only blocks are upper storey, with the floor being the
        # terrain itself, the lowest IS the roof. x1266,-751 reported "spruce_log over 10 air" and
        # was quoted as a floating house; the column actually reads y87 roof, interior air, then
        # grass_block at y77 exactly where the beard put it, and x1269,-752 has a bed and a wall
        # torch in that same "gap". If the air below is bounded by MORE of the structure lower down
        # in this column, or the structure continues below the gap, it is an interior.
        below_build = any(is_solid(col[k]) and not is_natural(col[k]) for k in range(0, low))
        if below_build:
            resting += 1
            continue
        if run == 0:
            resting += 1
        elif j >= 0:
            gaps[run] += 1
            hits.append((run, x, z, y0 + low, col[low].split('[')[0].split(':')[-1],
                         col[j].split('[')[0].split(':')[-1]))

tot = resting + sum(gaps.values())
print(f"=== {label}")
print(f"    columns whose lowest build block sits ON solid ground: {resting} of {tot}")
print(f"    columns whose lowest build block HANGS over air:       {sum(gaps.values())} of {tot}"
      + (f"  ({100.0*sum(gaps.values())/tot:.1f}%)" if tot else ""))
if gaps:
    print("    air gap beneath the build:")
    for k in sorted(gaps):
        print(f"      {k:>3} block(s): {gaps[k]:>4}  {'#' * min(50, gaps[k])}")
hits.sort(reverse=True)
if hits:
    print("    worst (gap, x, z, build y, block, ground below):")
    for run, x, z, by, blk, under in hits[:12]:
        print(f"      {run:>3} air   x={x} z={z} y={by}  {blk}  over {under}")
print()
