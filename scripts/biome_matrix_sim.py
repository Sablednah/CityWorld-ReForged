#!/usr/bin/env python3
"""Re-run CityWorld's MODERN biome matrix over the climate a survey:biomes CSV sampled, so a change to the matrix
can be measured on a real seed in seconds without a server. Usage: python3 scripts/biome_matrix_sim.py <csv>
The 'current' matrix must agree with the CSV on every land pixel (the control); each candidate is then scored
on seams between snow-covered and hot ground (vanilla's base temperature, lowered by height as the client does),
on the census, and on patch sizes. Water, shore and river pixels are left as sampled.
"""
import csv, sys, collections
SEA, DEEP, RANGE = 63, 54, 186
LOW, HILL, HIGH = RANGE*15//100, RANGE*45//100, RANGE*72//100
VT = {'snowy_plains':0.0,'ice_spikes':0.0,'snowy_taiga':-0.5,'taiga':0.25,'old_growth_pine_taiga':0.3,'old_growth_spruce_taiga':0.25,
'grove':-0.2,'snowy_slopes':-0.3,'jagged_peaks':-0.7,'frozen_peaks':-0.7,'stony_peaks':1.0,'windswept_hills':0.2,'windswept_gravelly_hills':0.2,
'windswept_forest':0.2,'windswept_savanna':2.0,'plains':0.8,'sunflower_plains':0.8,'meadow':0.5,'flower_forest':0.7,'forest':0.7,'birch_forest':0.6,
'old_growth_birch_forest':0.6,'dark_forest':0.7,'cherry_grove':0.5,'pale_garden':0.7,'swamp':0.8,'mangrove_swamp':0.8,'jungle':0.95,'sparse_jungle':0.95,
'bamboo_jungle':0.95,'savanna':2.0,'savanna_plateau':2.0,'desert':2.0,'badlands':2.0,'wooded_badlands':2.0,'eroded_badlands':2.0,'mushroom_fields':0.9,
'beach':0.8,'snowy_beach':0.05,'stony_shore':0.2,'river':0.5,'frozen_river':0.0,'ocean':0.5,'deep_ocean':0.5,'lukewarm_ocean':0.5,'deep_lukewarm_ocean':0.5,
'warm_ocean':0.5,'cold_ocean':0.5,'deep_cold_ocean':0.5,'frozen_ocean':0.0,'deep_frozen_ocean':0.5,'stony_shore':0.2}
WATER = {k for k in VT if 'ocean' in k or 'river' in k}
cold=lambda t:t<0.35; temperate=lambda t:t<0.6; warm=lambda t:t<0.8; dry=lambda h:h<0.4; wet=lambda h:h>0.65

def current(y,t,h):
    a=y-SEA
    if a<=LOW:
        if cold(t): return 'ice_spikes' if t<0.15 and dry(h) else 'snowy_taiga' if wet(h) else 'snowy_plains'
        if temperate(t): return 'plains' if dry(h) else 'mushroom_fields' if (t>0.5 and h>0.9) else 'flower_forest' if wet(h) else 'meadow'
        if warm(t): return 'savanna' if dry(h) else 'swamp' if h>0.5 else 'sunflower_plains'
        return 'desert' if dry(h) else 'jungle' if wet(h) else 'savanna'
    if a<=HILL:
        if cold(t): return 'snowy_taiga' if dry(h) else 'taiga'
        if temperate(t): return 'forest' if dry(h) else 'pale_garden' if h>0.8 else 'dark_forest' if wet(h) else 'cherry_grove'
        if warm(t): return 'savanna_plateau' if dry(h) else 'sparse_jungle' if wet(h) else 'birch_forest'
        return 'badlands' if dry(h) else 'bamboo_jungle' if wet(h) else 'wooded_badlands'
    if a<=HIGH:
        if cold(t): return 'old_growth_spruce_taiga' if wet(h) else 'old_growth_pine_taiga'
        if temperate(t): return 'windswept_forest' if dry(h) else 'old_growth_birch_forest' if wet(h) else 'grove'
        if warm(t): return ('windswept_gravelly_hills' if h<0.2 else 'windswept_hills') if dry(h) else 'windswept_forest'
        return 'eroded_badlands' if dry(h) else 'windswept_savanna'
    if t<0.3: return 'frozen_peaks'
    if temperate(t): return 'jagged_peaks'
    if warm(t): return 'snowy_slopes'
    return 'stony_peaks'

def candidate(y,t,h):
    """Vanilla's rule: windswept hills/forest, grove, the snowy slopes belong to cold and temperate climates; a warm
    or hot mountain is savanna, forest and jungle up to stony peaks. Lowland and hills unchanged."""
    a=y-SEA
    if a<=HILL: return current(y,t,h)
    if a<=HIGH:
        if cold(t): return ('windswept_gravelly_hills' if h<0.2 else 'windswept_hills') if dry(h) else 'old_growth_spruce_taiga' if wet(h) else 'old_growth_pine_taiga'
        if temperate(t): return 'windswept_forest' if dry(h) else 'old_growth_birch_forest' if wet(h) else 'grove'
        if warm(t): return 'windswept_savanna' if dry(h) else 'sparse_jungle' if wet(h) else 'forest'
        return 'eroded_badlands' if dry(h) else 'jungle' if wet(h) else 'windswept_savanna'
    if cold(t): return 'frozen_peaks'
    if temperate(t): return 'snowy_slopes' if wet(h) else 'jagged_peaks'
    return 'stony_peaks'

MATRICES={'current':current,'candidate':candidate}

def load(path):
    rows=list(csv.DictReader(open(path)))
    xs=sorted({int(r['x']) for r in rows}); zs=sorted({int(r['z']) for r in rows})
    ix={x:i for i,x in enumerate(xs)}; iz={z:i for i,z in enumerate(zs)}; W=len(xs)
    B=[[None]*W for _ in zs]; G=[[0]*W for _ in zs]; T=[[0.0]*W for _ in zs]; H=[[0.0]*W for _ in zs]
    for r in rows:
        j,i=iz[int(r['z'])],ix[int(r['x'])]
        B[j][i]=r['biome'].split(':')[1]; G[j][i]=int(r['ground']); T[j][i]=float(r['temperature']); H[j][i]=float(r['humidity'])
    return xs,zs,W,B,G,T,H

def apply(m,W,B,G,T,H):
    out=[row[:] for row in B]
    for j in range(W):
        for i in range(W):
            if G[j][i]>SEA and B[j][i] not in WATER: out[j][i]=m(G[j][i],T[j][i],H[j][i])
    return out

def looks(b,y):
    """What the biome reads as on the ground: vanilla's temperature, lowered above y 80 as the client does."""
    if b in WATER: return 'water'
    t=VT[b]-max(0,y-(SEA+17))*0.05/40
    return 'snowy' if t<0.15 else 'cool' if t<0.5 else 'temperate' if t<0.95 else 'hot'

def score(name,grid,W,G,xs,zs):
    seams=collections.Counter(); pairs=collections.Counter(); ex={}
    for j in range(W):
        for i in range(W):
            a=grid[j][i]; la=looks(a,G[j][i])
            for dj,di in ((0,1),(1,0)):
                if j+dj>=W or i+di>=W: continue
                b=grid[j+dj][i+di]
                if a==b: continue
                lb=looks(b,G[j+dj][i+di])
                if 'water' in (la,lb): continue
                k=tuple(sorted((la,lb))); seams[k]+=1
                if k in (('hot','snowy'),('cool','hot')):
                    p=tuple(sorted((a,b))); pairs[p]+=1; ex.setdefault(p,(xs[i],G[j][i]+2,zs[j]))
    # patches: 4-connected components per biome, land only
    seen=[[False]*W for _ in range(W)]; sizes=collections.defaultdict(list)
    for j in range(W):
        for i in range(W):
            if seen[j][i] or grid[j][i] in WATER: continue
            b=grid[j][i]; stack=[(j,i)]; seen[j][i]=True; n=0
            while stack:
                y,x=stack.pop(); n+=1
                for dj,di in ((0,1),(1,0),(0,-1),(-1,0)):
                    yy,xx=y+dj,x+di
                    if 0<=yy<W and 0<=xx<W and not seen[yy][xx] and grid[yy][xx]==b: seen[yy][xx]=True; stack.append((yy,xx))
            sizes[b].append(n)
    total=sum(seams.values())
    print(f"== {name}: {total} land borders; snowy|hot {seams[('hot','snowy')]}, cool|hot {seams[('cool','hot')]}, snowy|temperate {seams[('snowy','temperate')]}, cool|temperate {seams[('cool','temperate')]}")
    for p,v in pairs.most_common(8): print("   %-45s x%-5d /tp %d %d %d"%(' | '.join(p),v,*ex[p]))
    census=collections.Counter(b for row in grid for b in row if b not in WATER); land=sum(census.values())
    print("   census:", ' '.join(f"{b} {100*v/land:.1f}%" for b,v in census.most_common(40)))
    allp=[n for v in sizes.values() for n in v]
    print(f"   patches: {len(allp)} on land, mean {sum(allp)/len(allp):.1f} px, {sum(1 for n in allp if n<=2)} of 1-2 px; by biome (count, mean px): "
          + ' '.join(f"{b} {len(v)}/{sum(v)/len(v):.0f}" for b,v in sorted(sizes.items(), key=lambda e:-sum(e[1]))[:14]))
    return seams

if __name__=='__main__':
    xs,zs,W,B,G,T,H=load(sys.argv[1])
    sim=apply(current,W,B,G,T,H)
    diff=[(xs[i],zs[j],B[j][i],sim[j][i]) for j in range(W) for i in range(W) if B[j][i]!=sim[j][i]]
    print(f"control: python 'current' disagrees with the probe on {len(diff)} of {W*W} pixels", diff[:5])
    for name in (sys.argv[2:] or MATRICES):
        score(name, apply(MATRICES[name],W,B,G,T,H), W,G,xs,zs)
