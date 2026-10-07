#!/usr/bin/env python3
"""River checks on a saved region (rivers, 2026-10-05..07): pillars beside water (3+ above every dry neighbour) and one-block fins/slots in the band near water.
    FIN=3 river_pillars.py <region dir> x0 x1 z0 z1   (compare against the same seed with -Dcityworld.rivers.off=true)
"""
FIN=int(__import__("os").environ.get("FIN","2"))
import sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from region_dump import block_at_fn
b=block_at_fn(sys.argv[1]); x0,x1,z0,z1=map(int,sys.argv[2:6])
def n(x,y,z):
    try: return (b(x,y,z) or '?').replace('minecraft:','').split('[')[0]
    except Exception: return '?'
SOFT=('air','?','short_grass','tall_grass','dead_bush','fern','large_fern')
def top(x,z):
    for y in range(170,55,-1):
        v=n(x,y,z)
        if v in SOFT or 'leaves' in v or v.endswith('_bush'): continue
        return y, v
    return None, None
T={}
for x in range(x0-1,x1+1):
    for z in range(z0-1,z1+1):
        T[(x,z)]=top(x,z)
pillars=[]
for x in range(x0,x1):
    for z in range(z0,z1):
        y,v=T[(x,z)]
        if y is None or v=='water': continue
        nb=[T[(x+dx,z+dz)] for dx,dz in ((1,0),(-1,0),(0,1),(0,-1))]
        if not any(t[1]=='water' for t in nb): continue
        dry=[t[0] for t in nb if t[1]!='water' and t[0] is not None]
        wat=[t[0] for t in nb if t[1]=='water']
        if dry and y - max(dry) >= 3 and y - max(wat) >= 1:
            pillars.append((x,y,z,y-max(dry)))
print('pillars beside water:',len(pillars), pillars[:15])
# strips: within 6 of water, a column 2+ above or below both neighbours on a line (a one-block fin or slot)
near=set()
for (x,z),(y,v) in T.items():
    if v=='water':
        for a in range(-6,7):
            for b2 in range(-6,7): near.add((x+a,z+b2))
fins=[]
for x in range(x0,x1):
    for z in range(z0,z1):
        if (x,z) not in near: continue
        y,v=T[(x,z)]
        if y is None or v=='water': continue
        for (a,b2) in ((1,0),(0,1)):
            p=T.get((x-a,z-b2)); q=T.get((x+a,z+b2))
            if not p or not q or p[0] is None or q[0] is None: continue
            if (y-p[0]>=FIN and y-q[0]>=FIN) or (p[0]-y>=FIN and q[0]-y>=FIN):
                fins.append((x,y,z)); break
print('one-block fins/slots near water:',len(fins), fins[:10])
