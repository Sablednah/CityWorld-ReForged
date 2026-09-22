#!/usr/bin/env python3
"""Is the ground where each structure piece says it should be?

usage: region_pieceground.py <region dir> <probe log with PLANPAD BEARD lines>

⚠ This is the only structure metric here that CLASSIFIES NOTHING, and it is the one to trust. It
compares terrain against each piece's OWN declared support level (box.minY + groundLevelDelta - 1,
logged by padPlanForStructures under -Dcityworld.padlog), rather than guessing which block in a
column is structural. Six block-classification confounds produced six wrong answers on 2026-09-22 --
house interiors, caves, mine caps, desert masonry, the benign offset-2 population, and roofs over
rooms -- each of which took a cycle to find. This one cannot have them.

Reads: ground = highest natural solid at or below the target, allowing for snow sitting on top of the
grass block (the surface material is AT the target level; snow cover is above it).
"""
import re, sys, collections

sys.path.insert(0, __file__.rsplit('/', 1)[0])
from region_dump import block_at_fn
from region_voids import is_solid, is_natural

SNOWLIKE = ('snow', 'moss_carpet', 'dead_bush', 'short_grass', 'fern')


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    region, log = sys.argv[1], sys.argv[2]
    beards = {}
    with open(log, 'rb') as f:
        for raw in f:
            m = re.search(r'BEARD (\w+) (\w+) box x (-?\d+)\.\.(-?\d+) z (-?\d+)\.\.(-?\d+) '
                          r'y (-?\d+)\.\.(-?\d+) delta (-?\d+) -> top (-?\d+)',
                          raw.decode('utf-8', 'replace'))
            if m:
                g = m.groups()
                beards[(int(g[2]), int(g[3]), int(g[4]), int(g[5]), int(g[6]), int(g[7]))] = int(g[9])
    if not beards:
        print(f"no 'PLANPAD ... BEARD' lines in {log} -- was the probe run with -Dcityworld.padlog=true?")
        return 2

    at = block_at_fn(region)
    diffs = collections.Counter()
    worst = []
    for (x0, x1, z0, z1, _y0, _y1), top in beards.items():
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                ground = None
                for y in range(top + 4, top - 30, -1):
                    n = at(x, y, z)
                    if not n:
                        continue
                    nm = n.split('[')[0].split(':')[-1]
                    if any(k in nm for k in SNOWLIKE):
                        continue          # cover sits ABOVE the surface block
                    if is_solid(n) and is_natural(n):
                        ground = y
                        break
                if ground is None:
                    continue
                d = ground - top
                diffs[d] += 1
                if abs(d) >= 4:
                    worst.append((d, x, z, top, ground))

    n = sum(diffs.values())
    print(f"RIGID pieces logged: {len(beards)}   columns measured: {n}")
    print("ground minus the piece's declared support level (0 == exactly right):")
    for k in sorted(diffs):
        tag = "  <- correct" if k == 0 else ("  (hangs)" if k < 0 else "  (buried)")
        print(f"   {k:>4}: {diffs[k]:>5}  {'#' * min(44, diffs[k])}{tag}")
    if n:
        ok = diffs.get(0, 0)
        print(f"\n  EXACT: {ok} of {n} ({100.0 * ok / n:.1f}%)")
        print(f"  within +/-1: {sum(diffs.get(k, 0) for k in (-1, 0, 1))} "
              f"({100.0 * sum(diffs.get(k, 0) for k in (-1, 0, 1)) / n:.1f}%)")
    worst.sort()
    if worst:
        print("\n  mismatches of 4+ (diff, x, z, target, actual ground):")
        for d, x, z, t, g in worst[:12]:
            print(f"    {d:>4}   x={x} z={z}  target {t}, ground {g}")
    else:
        print("\n  no column off by 4 or more.")
    return 0


if __name__ == '__main__':
    sys.exit(main())
