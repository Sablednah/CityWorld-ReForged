#!/usr/bin/env python3
"""Find air trapped UNDER the ground: solid above, solid below, near the surface.

usage: region_voids.py <region dir> x0 x1 y0 y1 z0 z1 [near] [--list N]

This exists because of a fault that every other tool here was blind to. The structure pad rewrote
blocks during terrain generation while the PLANNED column heights (AbstractCachedYs.blockYs) kept
their original values; the decoration pass then painted grass and snow at those planned heights, so
wherever the pad had shaved ground away it left a floating lid over a cavity. Measured on the owner's
world, 2026-09-21: 28.5% of columns in one village footprint, 18,653 void blocks, runs up to 20 tall.

region_render.py could not see it, and neither could the height transects, because both report the
TOPMOST SOLID BLOCK per column — that is, the lid. The blocks were there in the .schem export all
along; nobody asked the right question of them. This asks it.

"near" (default 25) limits the scan to that many blocks below each column's surface: CityWorld cuts
real caves, mines and sewers deeper down, and counting those would bury the signal in legitimate
holes. Raise it only if you know what you are looking for.

Exit status is 1 if any void is found, so this can gate a build or a self-test.
"""
import os, sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from region_dump import block_at_fn

AIR = {'air', 'cave_air', 'void_air'}

# Things that legitimately stand in open air: they are not a ceiling and not a floor.
THIN = ('snow', 'grass', 'fern', 'flower', 'tulip', 'poppy', 'dandelion', 'bush', 'sapling',
        'leaves', 'vine', 'torch', 'lantern', 'rail', 'sign', 'pot', 'mushroom', 'seagrass',
        'kelp', 'sugar', 'wheat', 'carrot', 'potato', 'beet', 'berry', 'lily', 'azalea',
        'moss_carpet', 'dead_bush', 'water', 'lava', 'cobweb', 'button', 'lever', 'tripwire')


def is_solid(name):
    if not name:
        return False
    name = name.split('[')[0]
    if name in AIR:
        return False
    return not any(k in name for k in THIN)


def main():
    if len(sys.argv) < 8:
        print(__doc__)
        return 2
    region = sys.argv[1]
    x0, x1, y0, y1, z0, z1 = map(int, sys.argv[2:8])
    rest = sys.argv[8:]
    near = 25
    listn = 12
    if rest and not rest[0].startswith('--'):
        near = int(rest[0]); rest = rest[1:]
    if '--list' in rest:
        listn = int(rest[rest.index('--list') + 1])

    x0, x1 = min(x0, x1), max(x0, x1)
    y0, y1 = min(y0, y1), max(y0, y1)
    z0, z1 = min(z0, z1), max(z0, z1)
    at = block_at_fn(region)

    cols = 0
    hit_cols = 0
    total = 0
    unclosed = 0
    worst = []
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            column = [at(x, y, z) for y in range(y0, y1 + 1)]
            top = None
            for i in range(len(column) - 1, -1, -1):
                if is_solid(column[i]):
                    top = i
                    break
            if top is None:
                continue
            cols += 1
            found = 0
            biggest = 0
            open_run = False
            i = top - 1
            floor_i = max(0, top - near)
            while i >= floor_i:
                if not is_solid(column[i]):
                    start = i
                    while i >= 0 and not is_solid(column[i]):
                        i -= 1
                    if i >= 0:                     # closed underneath -> genuinely enclosed
                        run = start - i
                        found += run
                        biggest = max(biggest, run)
                    else:
                        # The air reached the BOTTOM of the scan window without meeting a floor, so
                        # we cannot tell whether it is a cavity or an open shaft — and dropping it
                        # silently is how this tool under-reported by a factor of three: scanning
                        # y60..110 gave "max 6 tall", while y40..130 over the identical box gave 18.
                        # A window that clips what you are measuring returns a QUIETER answer, not an
                        # error. Count it and say so, rather than let a truncated scan read as clean.
                        open_run = True
                i -= 1
            if open_run:
                unclosed += 1
            if found:
                hit_cols += 1
                total += found
                worst.append((biggest, found, x, z, y0 + top))

    worst.sort(reverse=True)
    pct = (100.0 * hit_cols / cols) if cols else 0.0
    print(f"columns with ground: {cols}")
    print(f"columns with enclosed air within {near} of the surface: {hit_cols}  ({pct:.1f}%)")
    print(f"total enclosed air blocks: {total}")
    if unclosed:
        print(f"!! {unclosed} column(s) had air reaching the BOTTOM of the scan window (y{y0}) with no")
        print(f"   floor beneath it, so they were NOT counted. Lower y0 and re-run: a window that clips")
        print(f"   a cavity reports a quieter answer, not an error. (y60..110 once gave 'max 6 tall'")
        print(f"   where y40..130 over the same box gave 18.)")
    if worst:
        print("worst columns (tallest run, total, x, z, surface y):")
        for big, tot, x, z, ty in worst[:listn]:
            print(f"   {big:>3} tall, {tot:>3} total   x={x} z={z}  surface y={ty}")
    return 1 if hit_cols else 0


if __name__ == '__main__':
    sys.exit(main())
