#!/usr/bin/env python3
"""The promo stat: CityWorld saves across every CurseForge instance, and their generated chunks (region headers,
every dimension). A save counts if its world settings name cityworld. 2026-10-07: 455 worlds, 7.6M chunks, 1,945 km2."""
import os, gzip, struct, glob, sys
base='/mnt/c/Users/darre/curseforge/minecraft/Instances'
worlds=0; chunks=0; skipped=0; per={}
def is_cityworld(save):
    for f in [os.path.join(save,'level.dat'), os.path.join(save,'data','minecraft','world_gen_settings.dat')]:
        try:
            if b'cityworld' in gzip.open(f).read(): return True
        except Exception: pass
    return False
for inst in sorted(os.listdir(base)):
    saves=os.path.join(base,inst,'saves')
    if not os.path.isdir(saves): continue
    for w in os.listdir(saves):
        save=os.path.join(saves,w)
        if not os.path.isfile(os.path.join(save,'level.dat')): continue
        if not is_cityworld(save): skipped+=1; continue
        n=0
        for mca in glob.glob(os.path.join(save,'**','region','*.mca'), recursive=True):
            try:
                with open(mca,'rb') as fh: head=fh.read(4096)
            except Exception: continue
            for i in range(0,len(head),4):
                if head[i:i+4]!=b'\0\0\0\0': n+=1
        worlds+=1; chunks+=n; per[inst]=per.get(inst,0)+n
print('cityworld worlds',worlds,'chunks',chunks,'km2',round(chunks*256/1e6,1),'non-cityworld saves skipped',skipped)
for k,v in sorted(per.items(), key=lambda kv:-kv[1]): print(f'  {v:>10} {k}')
