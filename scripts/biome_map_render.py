#!/usr/bin/env python3
"""Pretty maps from survey:biomes CSVs. Usage:
  python3 scripts/biome_map_render.py <before.csv> <after.csv> <out.png> [zoom_x zoom_z [seed]]
Draws the biome map before and after (map-style biome colours, hill-shaded by the ground), the climate map
(temperature as hue, humidity as saturation, land tiers as brightness), a legend with each biome's share, and a
zoom of a 1024-block box around zoom_x,zoom_z before and after. Rivers and water come from the CSV as sampled."""
import csv, sys, colorsys, collections
from PIL import Image, ImageDraw, ImageFont
SEA=63
COL={'ocean':(0,36,120),'deep_ocean':(0,20,70),'lukewarm_ocean':(0,80,170),'deep_lukewarm_ocean':(0,55,130),'warm_ocean':(0,140,200),
'cold_ocean':(30,50,140),'deep_cold_ocean':(20,30,100),'frozen_ocean':(130,150,210),'deep_frozen_ocean':(90,110,180),'river':(40,90,200),'frozen_river':(160,180,230),
'beach':(250,222,160),'snowy_beach':(240,240,250),'stony_shore':(150,150,150),'swamp':(60,110,70),'mangrove_swamp':(70,120,60),
'plains':(140,190,80),'sunflower_plains':(190,210,70),'meadow':(120,210,120),'flower_forest':(110,170,90),'forest':(40,120,40),'birch_forest':(120,160,90),
'old_growth_birch_forest':(100,150,80),'dark_forest':(30,80,30),'pale_garden':(140,150,140),'cherry_grove':(240,170,210),'mushroom_fields':(230,100,220),
'savanna':(200,180,80),'savanna_plateau':(180,160,70),'windswept_savanna':(210,190,100),'desert':(250,220,120),'badlands':(200,110,50),
'wooded_badlands':(170,110,60),'eroded_badlands':(220,130,70),'jungle':(20,110,20),'sparse_jungle':(60,140,50),'bamboo_jungle':(90,160,40),
'snowy_plains':(255,255,255),'ice_spikes':(200,230,255),'snowy_taiga':(210,225,220),'taiga':(60,110,90),'old_growth_pine_taiga':(70,100,70),
'old_growth_spruce_taiga':(50,90,70),'grove':(190,210,210),'snowy_slopes':(225,235,240),'windswept_hills':(110,130,110),'windswept_gravelly_hills':(130,130,120),
'windswept_forest':(90,120,90),'jagged_peaks':(220,220,235),'frozen_peaks':(235,240,250),'stony_peaks':(170,165,150)}
def load(path):
    rows=list(csv.DictReader(open(path)))
    xs=sorted({int(r['x']) for r in rows}); zs=sorted({int(r['z']) for r in rows}); W=len(xs)
    ix={x:i for i,x in enumerate(xs)}; iz={z:i for i,z in enumerate(zs)}
    B=[[None]*W for _ in zs]; G=[[0]*W for _ in zs]; T=[[0.0]*W for _ in zs]; H=[[0.0]*W for _ in zs]
    for r in rows:
        j,i=iz[int(r['z'])],ix[int(r['x'])]
        B[j][i]=r['biome'].split(':')[1]; G[j][i]=int(r['ground']); T[j][i]=float(r['temperature']); H[j][i]=float(r['humidity'])
    return xs,zs,W,B,G,T,H
def shade(c,G,j,i,W):
    # light from the north-west: brighter where the ground rises to the south-east
    dz=G[min(W-1,j+1)][i]-G[max(0,j-1)][i]; dx=G[j][min(W-1,i+1)]-G[j][max(0,i-1)]
    k=max(0.6,min(1.25,1.0+(dx+dz)/60.0))
    return tuple(min(255,int(v*k)) for v in c)
def biome_img(W,B,G):
    im=Image.new('RGB',(W,W))
    px=im.load()
    for j in range(W):
        for i in range(W):
            c=COL.get(B[j][i],(255,0,255)); px[i,j]=shade(c,G,j,i,W) if G[j][i]>SEA else c
    return im
def climate_img(W,G,T,H):
    im=Image.new('RGB',(W,W)); px=im.load()
    for j in range(W):
        for i in range(W):
            t,h,g=T[j][i],H[j][i],G[j][i]
            hue=0.66-0.66*t                     # blue cold .. red hot
            sat=0.25+0.7*h                      # dry washed-out .. wet saturated
            val=0.45 if g<SEA else 1.0-min(0.5,(g-SEA)/186*0.9)
            r,gg,b=colorsys.hsv_to_rgb(hue,sat if g>=SEA else 0.3,val); px[i,j]=(int(r*255),int(gg*255),int(b*255))
    return im
def font(sz):
    for f in ('/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf','/usr/share/fonts/truetype/liberation/LiberationSans-Regular.ttf'):
        try: return ImageFont.truetype(f,sz)
        except Exception: pass
    return ImageFont.load_default()
def main():
    before,after,out=sys.argv[1:4]; zx,zz=(int(sys.argv[4]),int(sys.argv[5])) if len(sys.argv)>5 else (2280,-3464)
    seed=sys.argv[6] if len(sys.argv)>6 else '?'
    xs,zs,W,B0,G,T,H=load(before); _,_,_,B1,_,_,_=load(after)
    px=xs[1]-xs[0]; S=2; M=W*S  # map size on the sheet
    f,fs,fb=font(15),font(12),font(22)
    legend_w=270; sheet=Image.new('RGB',(M*2+40+legend_w, M*2+140),(28,28,32)); d=ImageDraw.Draw(sheet)
    d.text((16,10),f"CityWorld biome matrix, seed {seed}, centre {xs[0]-px//2+W*px//2},{zs[0]-px//2+W*px//2}, {W*px} blocks square, {px} blocks a pixel",font=fb,fill=(240,240,240))
    def put(im,x,y,title):
        sheet.paste(im.resize((M,M),Image.NEAREST),(x,y)); d.text((x,y-20),title,font=f,fill=(240,240,240))
    put(biome_img(W,B0,G),16,70,"BEFORE: windswept hills/forest on every warm mountain (snow over jungle and savanna)")
    put(biome_img(W,B1,G),M+32,70,"AFTER: warm mountains carry savanna, forest and jungle up to stony peaks")
    put(climate_img(W,G,T,H),16,M+110,"CLIMATE: hue = temperature (blue cold, red hot), saturation = humidity, darker = higher land")
    # zoom: 1024 blocks around the worst seam, before and after
    zi=(zx-xs[0])//px; zj=(zz-zs[0])//px; r=32
    def crop(B):
        im=biome_img(W,B,G).crop((zi-r,zj-r,zi+r,zj+r)); return im.resize((M//2-8,M//2-8),Image.NEAREST)
    zx0=M+32; zy=M+110
    sheet.paste(crop(B0),(zx0,zy)); sheet.paste(crop(B1),(zx0+M//2+8,zy))
    d.text((zx0,zy-20),f"ZOOM {2*r*px} blocks around {zx},{zz}: before | after (black = a snowy biome touching a hot one)",font=f,fill=(240,240,240))
    # mark the seams in the zooms
    VT={'snowy_plains':0,'ice_spikes':0,'snowy_taiga':-0.5,'grove':-0.2,'snowy_slopes':-0.3,'jagged_peaks':-0.7,'frozen_peaks':-0.7,'windswept_hills':0.2,'windswept_gravelly_hills':0.2,'windswept_forest':0.2,'taiga':0.25,'old_growth_pine_taiga':0.3,'old_growth_spruce_taiga':0.25,'snowy_beach':0.05}
    HOT={'jungle','sparse_jungle','bamboo_jungle','savanna','savanna_plateau','windswept_savanna','desert','badlands','wooded_badlands','eroded_badlands','stony_peaks'}
    def looks_snowy(b,g): return b in VT and VT[b]-max(0,g-80)*0.00125<0.15
    k=(M//2-8)/(2*r)
    for n,B in enumerate((B0,B1)):
        ox=zx0+n*(M//2+8)
        for j in range(zj-r,zj+r):
            for i in range(zi-r,zi+r):
                for dj,di in ((0,1),(1,0)):
                    if j+dj>=W or i+di>=W: continue
                    a,b=B[j][i],B[j+dj][i+di]
                    if (looks_snowy(a,G[j][i]) and b in HOT) or (looks_snowy(b,G[j+dj][i+di]) and a in HOT):
                        d.rectangle((ox+(i-zi+r)*k,zy+(j-zj+r)*k,ox+(i-zi+r+1)*k-1,zy+(j-zj+r+1)*k-1),fill=(0,0,0))
    # legend with before -> after shares
    c0=collections.Counter(b for row in B0 for b in row); c1=collections.Counter(b for row in B1 for b in row); n=W*W
    lx=M*2+48; ly=70
    d.text((lx,ly-20),"biome   before -> after",font=f,fill=(240,240,240))
    order=sorted(COL,key=lambda b:-(c0[b]+c1[b]))
    for b in order:
        if c0[b]+c1[b]==0: continue
        d.rectangle((lx,ly,lx+14,ly+14),fill=COL[b]); chg=c1[b]-c0[b]
        d.text((lx+20,ly),f"{b.replace('_',' ')}  {100*c0[b]/n:.1f} -> {100*c1[b]/n:.1f}%" + ("  *" if abs(chg)>n//1000 else ""),font=fs,fill=(240,240,240) if abs(chg)>n//1000 else (170,170,170)); ly+=17
    sheet.save(out); print("wrote",out)
main()
