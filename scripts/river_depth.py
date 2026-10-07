#!/usr/bin/env python3
"""River checks on a saved region (rivers, 2026-10-05..07): a map of water depth below 63 (. dry land): where a river meets the sea, ridges and sills show as lines.
    river_depth.py <region dir> x0 x1 z0 z1
"""
import sys
sys.path.insert(0, __import__('os').path.dirname(__import__('os').path.abspath(__file__)))
from region_dump import block_at_fn
b=block_at_fn(sys.argv[1]); x0,x1,z0,z1=map(int,sys.argv[2:6])
SOFT=('water','seagrass','tall_seagrass','kelp','kelp_plant','air')
print('depth below 63 (. dry land), x %d..%d across, z %d..%d down'%(x0,x1,z0,z1))
for z in range(z0,z1+1):
    row=''
    for x in range(x0,x1+1):
        w=(b(x,63,z) or '').replace('minecraft:','').split('[')[0]
        if w not in SOFT or w=='air': row+=' .'; continue
        y=62
        while y>40 and (b(x,y,z) or '').replace('minecraft:','').split('[')[0] in SOFT: y-=1
        row+='%2d'%(63-y)
    print('%5d %s'%(z,row))
