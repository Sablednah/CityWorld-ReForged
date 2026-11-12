#!/usr/bin/env python3
"""Predict where vanilla's random_spread placement will TRY a structure set, from the seed alone — no server.

    scripts/structure_sites.py <seed> [--spacing 34 --separation 8 --salt 10387312 --spread linear|triangular] [--radius 150]

Defaults are 1.21's villages (data/minecraft/worldgen/structure_set/villages.json: linear spread). Prints the attempt
chunks within the radius; vanilla then places the structure there only if the biome fits, and CityWorld only if the
set may stand there (allowed, and wild for a wilderness-only set). A Java LCG port, checked against three recorded
attempts on the self-test seed 8675309 (2026-11-12). Pair with structure_starts.py to read what a world did with them.
"""
import argparse
MASK = (1 << 48) - 1

class JRandom:
    def __init__(self, seed): self.s = (seed ^ 0x5DEECE66D) & MASK
    def next(self, bits):
        self.s = (self.s * 0x5DEECE66D + 0xB) & MASK
        r = (self.s >> (48 - bits)) & 0xffffffff
        return r - (1 << 32) if r >= (1 << 31) else r
    def nextInt(self, bound):
        if bound & (bound - 1) == 0: return (bound * self.next(31)) >> 31
        while True:
            bits = self.next(31); val = bits % bound
            if bits - val + (bound - 1) < (1 << 31): return val

def to_long(x):
    x &= (1 << 64) - 1
    return x - (1 << 64) if x >= (1 << 63) else x

def attempt(seed, rx, rz, spacing, sep, salt, spread):
    r = JRandom(to_long(rx * 341873128712 + rz * 132897987541 + seed + salt)); span = spacing - sep
    if spread == "triangular":
        ox = (r.nextInt(span) + r.nextInt(span)) // 2; oz = (r.nextInt(span) + r.nextInt(span)) // 2
    else:
        ox = r.nextInt(span); oz = r.nextInt(span)
    return rx * spacing + ox, rz * spacing + oz

if __name__ == "__main__":
    a = argparse.ArgumentParser()
    a.add_argument("seed", type=int); a.add_argument("--spacing", type=int, default=34)
    a.add_argument("--separation", type=int, default=8); a.add_argument("--salt", type=int, default=10387312)
    a.add_argument("--spread", default="linear", choices=["linear", "triangular"]); a.add_argument("--radius", type=int, default=150)
    o = a.parse_args(); n = o.radius // o.spacing + 2
    pts = sorted(p for rx in range(-n, n + 1) for rz in range(-n, n + 1)
                 for p in [attempt(o.seed, rx, rz, o.spacing, o.separation, o.salt, o.spread)]
                 if abs(p[0]) <= o.radius and abs(p[1]) <= o.radius)
    print(len(pts), "attempt chunks within", o.radius, "chunks:")
    for x, z in pts: print(f"  chunk {x},{z}   (x={x*16} z={z*16})")
