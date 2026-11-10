#!/usr/bin/env python3
"""Read named objects out of an hprof heap dump of a (hung) Minecraft client or server.

    python3 -I scripts/hprof_fields.py <dump.hprof> <intermediateToNamed.srg> <class>...

Prints the static fields of each named class and every instance of it with its fields, SRG field names mapped
through the srg file (a production jar's fields are f_NNN_; the dev runtime's are named already), following
references two levels into small value classes (Vec3, JOML vectors/quaternions, IV's Point3D/RotationMatrix).
Take the dump with the JDK's jcmd over the attach API, which the hung JVM writes itself in seconds:
    jcmd.exe <pid> GC.heap_dump 'D:\\path\\hang.hprof'     (the owner's game from WSL; 4.4 GB for a 4 GB heap)
and delete it afterwards. Reads the whole file once (about 90 s for 4.4 GB). jhsdb (the serviceability agent)
failed against Mojang's runtime with "bad SID 0": this is the route that worked (2026-11-10, the Immersive
Vehicles camera NaN: the Frustum's view vector, IV's cameraAdjustedOrientation and a seat's orientation matrices
read straight out of the frozen game)."""
import struct, sys
if len(sys.argv) < 4:
    sys.exit(__doc__)
dump, srgfile, *targets = sys.argv[1:]
targets = [t.replace('.', '/') for t in targets]
VALUE = {'net/minecraft/world/phys/Vec3', 'org/joml/Vector4f', 'org/joml/Vector3f', 'org/joml/Vector3d',
         'minecrafttransportsimulator/baseclasses/Point3D', 'minecrafttransportsimulator/baseclasses/RotationMatrix',
         'net/minecraft/world/phys/AABB', 'com/mojang/math/Transformation', 'org/joml/Quaternionf'}
srg = {}
for line in open(srgfile, errors='replace'):
    if line.startswith('FD: '):
        a, b = line.split()[1:3]
        srg[a.rsplit('/', 1)[1]] = b.rsplit('/', 1)[1]
f = open(dump, 'rb')
hdr = b''
while True:
    c = f.read(1); hdr += c
    if c == b'\0': break
idsz = struct.unpack('>I', f.read(4))[0]; f.read(8)
ID = '>Q' if idsz == 8 else '>I'
TSZ = {2: idsz, 4: 1, 5: 2, 6: 4, 7: 8, 8: 1, 9: 2, 10: 4, 11: 8}
strings, classes, layouts, supers = {}, {}, {}, {}   # id->str, classObjId->name, classObjId->[(fieldname,type)], classObjId->super
want = set()    # class ids of targets + value classes
statics = {}
inst = {}       # objId -> (offset, classId, len)  for instances of want
def rid(b, o): return struct.unpack_from(ID, b, o)[0]
# pass 1
while True:
    h = f.read(9)
    if len(h) < 9: break
    tag, t, ln = struct.unpack('>BII', h)
    if tag == 1:
        b = f.read(ln); strings[rid(b, 0)] = b[idsz:].decode('utf8', 'replace')
    elif tag == 2:
        b = f.read(ln); classes[rid(b, 4)] = strings.get(rid(b, 4 + idsz + 4), '?')
    elif tag in (0x0c, 0x1c):
        base = f.tell(); b = f.read(ln); o = 0
        while o < ln:
            st = b[o]; o += 1
            if st == 0xff: o += idsz
            elif st in (0x01, 0x05, 0x07): o += idsz * (2 if st == 1 else 1)
            elif st in (0x02, 0x03, 0x08): o += idsz + 8
            elif st in (0x04, 0x06): o += idsz + 4
            elif st == 0x20:
                cid = rid(b, o); sup = rid(b, o + idsz + 4); o += idsz + 4 + idsz * 6 + 4
                n = struct.unpack_from('>H', b, o)[0]; o += 2
                for _ in range(n): ty = b[o + 2]; o += 3 + TSZ[ty]
                n = struct.unpack_from('>H', b, o)[0]; o += 2
                st_fields = []
                for _ in range(n):
                    ty = b[o + idsz]; fo = o + idsz + 1
                    if ty == 2: val = rid(b, fo)
                    else: val = struct.unpack_from({4: '>?', 5: '>H', 6: '>f', 7: '>d', 8: '>b', 9: '>h', 10: '>i', 11: '>q'}[ty], b, fo)[0]
                    st_fields.append((strings.get(rid(b, o), '?'), ty, val)); o += idsz + 1 + TSZ[ty]
                if classes.get(cid) in targets: statics[cid] = st_fields
                n = struct.unpack_from('>H', b, o)[0]; o += 2
                fl = []
                for _ in range(n):
                    fl.append((strings.get(rid(b, o), '?'), b[o + idsz])); o += idsz + 1
                layouts[cid] = fl; supers[cid] = sup
                nm = classes.get(cid)
                if nm in targets or nm in VALUE: want.add(cid)
            elif st == 0x21:
                oid = rid(b, o); cid = rid(b, o + idsz + 4); l = struct.unpack_from('>I', b, o + 2 * idsz + 4)[0]
                if cid in want: inst[oid] = (base + o, cid, l)
                o += 2 * idsz + 8 + l
            elif st == 0x22:
                n = struct.unpack_from('>I', b, o + idsz + 4)[0]; o += 2 * idsz + 8 + n * idsz
            elif st == 0x23:
                n = struct.unpack_from('>I', b, o + idsz + 4)[0]; ty = b[o + idsz + 8]; o += idsz + 9 + n * TSZ[ty]
            else: raise SystemExit('bad subtag %x at %d' % (st, base + o))
    else: f.seek(ln, 1)
def fields(cid):
    out = []
    while cid:
        out = out + layouts.get(cid, []); cid = supers.get(cid, 0)
    return out
def read(oid, depth):
    off, cid, l = inst[oid]; f.seek(off + 2 * idsz + 8); b = f.read(l); o = 0; res = []
    for nm, ty in fields(cid):
        nm = srg.get(nm, nm)
        if ty == 2:
            r = rid(b, o); v = None
            if r in inst and depth > 0 and classes.get(inst[r][1]) in VALUE: v = read(r, depth - 1)
            res.append((nm, v if v is not None else ('null' if r == 0 else '@%x' % r)))
        else:
            fmt = {4: '>?', 5: '>H', 6: '>f', 7: '>d', 8: '>b', 9: '>h', 10: '>i', 11: '>q'}[ty]
            res.append((nm, struct.unpack_from(fmt, b, o)[0]))
        o += TSZ[ty]
    return res
def show(v):
    if isinstance(v, list): return '{' + ', '.join('%s=%s' % (k, show(x)) for k, x in v) + '}'
    return str(v)
for cid, fl in statics.items():
    print('== STATICS', classes.get(cid))
    for nm, ty, val in fl:
        nm = srg.get(nm, nm)
        if ty == 2 and val in inst and classes.get(inst[val][1]) in VALUE: val = read(val, 2)
        elif ty == 2: val = 'null' if val == 0 else '@%x' % val
        print('   %s = %s' % (nm, show(val)))
for oid, (off, cid, l) in inst.items():
    nm = classes.get(cid)
    if nm not in targets: continue
    print('==', nm, '@%x' % oid)
    for k, v in read(oid, 2):
        print('   %s = %s' % (k, show(v)))
print('classes', len(classes), 'instances of interest', len(inst))
