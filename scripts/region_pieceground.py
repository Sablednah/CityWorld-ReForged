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

# ⚠ Substring matching bit here too: 'snow' matches SNOW_BLOCK, which is ground, not cover. Same
# bug as region_voids' THIN, re-introduced one file over within the hour. Whole names only.
# Laid directly on the ground by a village: the surface is still AT this block.
PAVING = {'dirt_path', 'cobblestone', 'mossy_cobblestone', 'gravel', 'spruce_planks',
          'oak_planks', 'birch_planks', 'acacia_planks', 'jungle_planks', 'dark_oak_planks',
          'stone_bricks', 'smooth_stone', 'stone', 'cobbled_deepslate'}

COVER_EXACT = {'snow', 'moss_carpet', 'dead_bush', 'short_grass', 'tall_grass', 'grass', 'fern',
               'large_fern'}


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 2
    region, log = sys.argv[1], sys.argv[2]
    beards = {}
    with open(log, 'rb') as f:
        for raw in f:
            m = re.search(r'BEARD (?:taper \d+ )?(\w+) (\w+) box x (-?\d+)\.\.(-?\d+) z (-?\d+)\.\.(-?\d+) '
                          r'y (-?\d+)\.\.(-?\d+) delta (-?\d+) -> top (-?\d+)',
                          raw.decode('utf-8', 'replace'))
            if m:
                g = m.groups()
                beards[(int(g[2]), int(g[3]), int(g[4]), int(g[5]), int(g[6]), int(g[7]))] = int(g[9])
    if not beards:
        print(f"no 'PLANPAD ... BEARD' lines in {log} -- was the probe run with -Dcityworld.padlog=true?")
        return 2

    raw_at = block_at_fn(region)

    def at(x, y, z):
        """None for an ungenerated chunk instead of a traceback.

        A region/log mismatch is an easy mistake -- the log's boxes are one world's coordinates and
        the region directory may be another's -- and it should read as NO DATA, not as a crash that
        looks like a broken metric. Hit on 2026-09-22 running a village log against a saved pyramid
        world.
        """
        try:
            return raw_at(x, y, z)
        except Exception:
            return None

    diffs = collections.Counter()
    buried = collections.Counter()
    buried_eg = []
    worst = []
    for (x0, x1, z0, z1, _y0, box_max), top in beards.items():
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                # ⚠ SCAN DOWN FROM THE TARGET, NEVER UP INTO THE STRUCTURE. Two wrong versions
                # preceded this one. Demanding a NATURAL block at the target rejected the village's
                # own paving -- dirt_path (176), cobblestone (19), spruce_planks (8) sitting AT the
                # target with dirt directly below -- and read -1, turning 99.9% into 70.3%. Accepting
                # ANY solid instead, while scanning from target+4, caught the piece's own WALLS above
                # the ground and read +4 buried for 138 columns, which is just the top of the scan
                # window. The structure occupies everything above the ground inside its own footprint,
                # so looking up there can only ever find masonry.
                #
                # Seated means: at the target sits ground, or a thin pavement laid directly ON ground.
                ground = None
                for y in range(top, top - 30, -1):
                    n = at(x, y, z)
                    if not n:
                        continue
                    nm = n.split('[')[0].split(':')[-1]
                    if nm in COVER_EXACT:
                        continue
                    if not is_solid(n):
                        continue
                    if is_natural(n):
                        ground = y
                        break
                    if nm in PAVING:
                        below = at(x, y - 1, z)
                        if below and is_solid(below) and is_natural(below):
                            ground = y
                            break
                    break          # anything else is structure, not ground
                if ground is None:
                    continue
                d = ground - top
                diffs[d] += 1
                if abs(d) >= 4:
                    worst.append((d, x, z, top, ground))

                # ⚠ THE OTHER HALF, and the scan above cannot see it. Walking DOWN from the target
                # can only ever report 0 or negative, so on its own it proves nothing sits BELOW the
                # target and says nothing about terrain piled ABOVE it. A perfect 745/745 from that
                # half alone is not a clean result, it is half a measurement.
                #
                # Burial is unambiguous to test: a structure writes masonry, never grass or dirt, so
                # any NATURAL solid above the target and inside the piece's own box is terrain that
                # should not be there.
                fill = 0
                for y in range(top + 1, min(box_max, top + 30) + 1):
                    n = at(x, y, z)
                    if not n:
                        continue
                    nm = n.split('[')[0].split(':')[-1]
                    if nm in COVER_EXACT:
                        continue
                    if is_solid(n) and is_natural(n):
                        fill += 1
                if fill:
                    buried[fill] += 1
                    if len(buried_eg) < 10:
                        buried_eg.append((fill, x, z, top, box_max))

    n = sum(diffs.values())
    print(f"RIGID pieces logged: {len(beards)}   columns measured: {n}")
    if n == 0:
        print("  NO COLUMNS MEASURED -- the region directory holds none of the log's coordinates.\n"
              "  Check the region/log pair match the same world before reading anything into this.")
        return 2
    print("ground minus the piece's declared support level (0 == exactly right):")
    for k in sorted(diffs):
        tag = "  <- correct" if k == 0 else ("  (hangs)" if k < 0 else "  (buried)")
        print(f"   {k:>4}: {diffs[k]:>5}  {'#' * min(44, diffs[k])}{tag}")
    if n:
        ok = diffs.get(0, 0)
        print(f"\n  EXACT: {ok} of {n} ({100.0 * ok / n:.1f}%)")
        print(f"  within +/-1: {sum(diffs.get(k, 0) for k in (-1, 0, 1))} "
              f"({100.0 * sum(diffs.get(k, 0) for k in (-1, 0, 1)) / n:.1f}%)")
    nb = sum(buried.values())
    print(f"\n  BURIED: {nb} of {n} columns have natural blocks above the target inside the box"
          + (f" ({100.0 * nb / n:.1f}%)" if n else ""))
    if buried:
        for k in sorted(buried):
            print(f"   {k:>4} natural block(s) above target: {buried[k]:>5}")
        print("   examples (count, x, z, target, box maxY):")
        for e in buried_eg:
            print(f"     {e}")
    else:
        print("   none — nothing is entombed in its own footprint.")

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
