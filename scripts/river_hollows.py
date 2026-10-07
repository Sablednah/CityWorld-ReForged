#!/usr/bin/env python3
"""River checks on a saved region (rivers, 2026-10-05..07): waterfall hollows: tops, mossy roofs, sand/gravel over air (falls in), still water beside them (pours in).
    river_hollows.py <region dir> x0 x1 z0 z1
"""
import sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from region_dump import block_at_fn
b=block_at_fn(sys.argv[1]); x0,x1,z0,z1=map(int,sys.argv[2:6])
def n(x,y,z):
    try: return (b(x,y,z) or '?').replace('minecraft:','').split('[')[0]
    except Exception: return '?'
hollow=[]; falling=0; leaks=[]; mossyroof=0
for x in range(x0,x1):
  for z in range(z0,z1):
    # river column: water somewhere above; find air cells sealed below a solid roof under that water
    col=[n(x,y,z) for y in range(60,160)]
    for i in range(1,len(col)-1):
        y=60+i
        if col[i]=='air' and col[i+1] not in ('air','water','?') and any(c=='water' for c in col[i+1:i+8]):
            hollow.append((x,y,z))
            if col[i+1] in ('sand','gravel','red_sand'): falling+=1
            if col[i+1]=='mossy_cobblestone': mossyroof+=1
            for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
                v=n(x+dx,y,z+dz)
                if v=='water': leaks.append((x,y,z)); break
print('hollow tops',len(hollow),'mossy roofs',mossyroof,'sand/gravel over air',falling,'still water beside a hollow top',len(leaks), leaks[:8])
print('sample', hollow[:8])
