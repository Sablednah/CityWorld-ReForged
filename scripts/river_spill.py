#!/usr/bin/env python3
"""River checks on a saved region (rivers, 2026-10-05..07): still water with air beside it, split into steps down the channel (fine) and spills onto a bank (bad).
    river_spill.py <region dir> x0 x1 z0 z1
"""
import sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from region_dump import block_at_fn
b=block_at_fn(sys.argv[1]); x0,x1,z0,z1=map(int,sys.argv[2:6])
def n(x,y,z):
    try: return (b(x,y,z) or '?').replace('minecraft:','')
    except Exception: return '?'
exposed=[]; steps=[]; flowing=0; sources=0
for x in range(x0,x1):
  for z in range(z0,z1):
    for y in range(160,62,-1):
      v=n(x,y,z)
      if v.startswith('water'):
        if 'level=0' in v:
          sources+=1
          for dx,dz in ((1,0),(-1,0),(0,1),(0,-1)):
            if n(x+dx,y,z+dz)=='air':
              # what is under that air: lower water is a step down the channel, anything else a spill
              yy=y-1
              while yy>50 and n(x+dx,yy,z+dz)=='air': yy-=1
              (steps if n(x+dx,yy,z+dz).startswith('water') else exposed).append((x,y,z,y-yy)); break
        else: flowing+=1
        break
      if v not in ('air','?') and 'leaves' not in v and 'grass' not in v and 'fern' not in v and 'bush' not in v: break
print('top water: sources',sources,'flowing',flowing,'steps',len(steps),'spills',len(exposed)); print('spills (x,y,z,drop):',exposed[:20])
