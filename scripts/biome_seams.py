#!/usr/bin/env python3
# Reads the CSV that -Dcityworld.probe=survey:biomes writes (run/biomes-<seed>-<at>-<size>.csv) and measures how
# abruptly cold ground meets hot ground: land borders by vanilla-temperature class, the snowy|hot and cool|hot
# biome pairs with a /tp each, and how far snowy land sits from hot land. Usage: python3 scripts/biome_seams.py <csv>
import csv, sys, collections, math
# vanilla base temperatures (1.21)
VT = {'snowy_plains':0.0,'ice_spikes':0.0,'snowy_taiga':-0.5,'taiga':0.25,'old_growth_pine_taiga':0.3,'old_growth_spruce_taiga':0.25,
'grove':-0.2,'snowy_slopes':-0.3,'jagged_peaks':-0.7,'frozen_peaks':-0.7,'stony_peaks':1.0,'windswept_hills':0.2,'windswept_gravelly_hills':0.2,
'windswept_forest':0.2,'windswept_savanna':2.0,'plains':0.8,'sunflower_plains':0.8,'meadow':0.5,'flower_forest':0.7,'forest':0.7,'birch_forest':0.6,
'old_growth_birch_forest':0.6,'dark_forest':0.7,'cherry_grove':0.5,'pale_garden':0.7,'swamp':0.8,'mangrove_swamp':0.8,'jungle':0.95,'sparse_jungle':0.95,
'bamboo_jungle':0.95,'savanna':2.0,'savanna_plateau':2.0,'desert':2.0,'badlands':2.0,'wooded_badlands':2.0,'eroded_badlands':2.0,'mushroom_fields':0.9,
'beach':0.8,'snowy_beach':0.05,'stony_shore':0.2,'river':0.5,'frozen_river':0.0,'ocean':0.5,'deep_ocean':0.5,'lukewarm_ocean':0.5,'deep_lukewarm_ocean':0.5,
'warm_ocean':0.5,'cold_ocean':0.5,'deep_cold_ocean':0.5,'frozen_ocean':0.0,'deep_frozen_ocean':0.5}
WATER = {k for k in VT if 'ocean' in k or 'river' in k}
rows=list(csv.DictReader(open(sys.argv[1])))
xs=sorted({int(r['x']) for r in rows}); zs=sorted({int(r['z']) for r in rows}); px=xs[1]-xs[0]
W=len(xs); ix={x:i for i,x in enumerate(xs)}; iz={z:i for i,z in enumerate(zs)}
B=[[None]*W for _ in zs]; G=[[0]*W for _ in zs]; T=[[0]*W for _ in zs]
for r in rows:
    b=r['biome'].split(':')[1]; B[iz[int(r['z'])]][ix[int(r['x'])]]=b; G[iz[int(r['z'])]][ix[int(r['x'])]]=int(r['ground']); T[iz[int(r['z'])]][ix[int(r['x'])]]=float(r['temperature'])
def cls(b):
    if b in WATER: return 'water'
    t=VT[b]
    return 'snowy' if t<0.2 else 'cool' if t<0.5 else 'temperate' if t<0.95 else 'hot'
pairs=collections.Counter(); ex={}; landpairs=collections.Counter()
for j in range(W):
    for i in range(W):
        a=B[j][i]
        for dj,di in ((0,1),(1,0)):
            if j+dj>=W or i+di>=W: continue
            b=B[j+dj][i+di]
            if a==b: continue
            ca,cb=cls(a),cls(b)
            if 'water' in (ca,cb): continue
            key=tuple(sorted((ca,cb))); pairs[key]+=1
            if key==('hot','snowy'):
                k=tuple(sorted((a,b))); landpairs[k]+=1
                ex.setdefault(k,(xs[i],G[j][i]+2,zs[j],G[j][i],G[j+dj][i+di],T[j][i],T[j+dj][i+di]))
total=sum(pairs.values())
print("land biome borders by class pair (%d borders of %d-block pixels):"%(total,px))
for k,v in pairs.most_common(): print("  %-22s %6d %5.1f%%"%(' | '.join(k),v,100*v/total))
print("\nsnowy|hot borders by biome pair:")
for k,v in landpairs.most_common(): print("  %-45s x%-4d e.g. /tp %d %d %d (ground %d vs %d, temp %.2f vs %.2f)"%(' | '.join(k),v,*ex[k]))
# distance from each snowy land pixel to the nearest hot land pixel
hot=[(j,i) for j in range(W) for i in range(W) if cls(B[j][i])=='hot']
snow=[(j,i) for j in range(W) for i in range(W) if cls(B[j][i])=='snowy']
hs=set(hot); hist=collections.Counter()
for j,i in snow:
    d=None
    for r in range(0,9):
        found=False
        for dj in range(-r,r+1):
            for di in (-r,r) if abs(dj)<r else range(-r,r+1):
                if (j+dj,i+di) in hs: found=True;break
            if found: break
        if found: d=r;break
    hist[d if d is not None else 9]+=1
n=len(snow)
print("\nsnowy land pixels (%d) by distance in pixels (%d blocks) to the nearest hot land pixel:"%(n,px))
acc=0
for d in sorted(hist): acc+=hist[d]; print("  %s: %6d  (%.1f%% within)"%('>8' if d==9 else d,hist[d],100*acc/n))
# what sits between: for each snowy pixel within 3 px of hot, what biomes lie on the straight line between
between=collections.Counter()
for j,i in snow:
    for dj in range(-3,4):
        for di in range(-3,4):
            if (j+dj,i+di) in hs and (dj,di)!=(0,0):
                steps=max(abs(dj),abs(di))
                for s in range(1,steps):
                    jj=j+round(dj*s/steps); ii=i+round(di*s/steps); between[B[jj][ii]]+=1
                break
        else: continue
        break
print("\nbiomes found between a snowy pixel and a hot one up to 3 px away:",between.most_common(12))

# cool|hot by biome pair, with the ground step
ch=collections.Counter(); chex={}; chstep=collections.Counter()
for j in range(W):
    for i in range(W):
        a=B[j][i]
        for dj,di in ((0,1),(1,0)):
            if j+dj>=W or i+di>=W: continue
            b=B[j+dj][i+di]
            if a==b or 'water' in (cls(a),cls(b)): continue
            if {cls(a),cls(b)}=={'cool','hot'}:
                k=tuple(sorted((a,b))); ch[k]+=1; chstep[min(60,abs(G[j][i]-G[j+dj][i+di])//10*10)]+=1
                chex.setdefault(k,(xs[i],G[j][i]+2,zs[j],G[j][i],G[j+dj][i+di],T[j][i],T[j+dj][i+di]))
print("\ncool|hot borders by biome pair (cool = vanilla temp 0.2..0.5: taiga, windswept hills/forest, old growth taiga):")
for k,v in ch.most_common(10): print("  %-45s x%-4d e.g. /tp %d %d %d (ground %d vs %d, temp %.2f vs %.2f)"%(' | '.join(k),v,*chex[k]))
print("  ground step across them (10s of blocks):",sorted(chstep.items()))
# full distance distribution snowy -> hot by BFS from all hot pixels
from collections import deque
D=[[-1]*W for _ in range(W)]; q=deque()
for j,i in hot: D[j][i]=0; q.append((j,i))
while q:
    j,i=q.popleft()
    for dj,di in ((0,1),(1,0),(0,-1),(-1,0)):
        jj,ii=j+dj,i+di
        if 0<=jj<W and 0<=ii<W and D[jj][ii]<0: D[jj][ii]=D[j][i]+1; q.append((jj,ii))
ds=sorted(D[j][i]*px for j,i in snow if D[j][i]>=0)
print("\nsnowy land -> nearest hot land, blocks: 10%%ile %d, median %d, 90%%ile %d"%(ds[len(ds)//10],ds[len(ds)//2],ds[len(ds)*9//10]))
# same for every snowy LOWLAND biome (snowy_plains/taiga/ice spikes/snowy beach) only
low=[(j,i) for j,i in snow if B[j][i] in ('snowy_plains','snowy_taiga','ice_spikes','snowy_beach')]
ds=sorted(D[j][i]*px for j,i in low if D[j][i]>=0)
print("snowy lowland (plains/taiga/spikes/beach) -> nearest hot land, blocks: min %d, 10%%ile %d, median %d"%(ds[0],ds[len(ds)//10],ds[len(ds)//2]))
