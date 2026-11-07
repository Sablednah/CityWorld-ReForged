#!/usr/bin/env python3
"""Write CityWorld's parked-vehicle data (registry cityworld:vehicle) from Immersive Vehicles content-pack jars.

    python3 -I scripts/gen_iv_vehicles.py [--kind <pack>:<vehicle>=<kind>]... [--out <dir>] <pack jar>...
        e.g. "MTS Official Pack-1.20.1-V29.jar" OAmP-1.20.1-V3.jar
        a third-party pack: --kind rage:sedan=car --out mypack/data/mypack/cityworld/vehicle <its jar> <OCP jar>

A vehicle's id is its pack's mod id and its file name under assets/<pack>/jsondefs/vehicles/; its paints are that
file's definitions[].subName; a part's slot is its index in the file's parts[]. List a pack's vehicles with --list.

Reads each jar's assets/<pack>/jsondefs/{vehicles,parts}/*.json (IV's JSON has // comments, trailing commas and
leading-zero numbers) and writes src/main/resources/data/cityworld/cityworld/vehicle/<pack>_<vehicle>.json for every
ground vehicle it knows a kind for (KINDS below; aircraft, tracked and unfinished vehicles are left out). IV gives a
vehicle spawned from data only the parts its data names (a player's spawn adds the defaults), so every file lists
its parts by slot: the slot's default part if it has one, a wheel for every wheel slot (the vehicle's own pack's
first, then the Official Content Pack's, by the slot's size range), a seat for every empty seat slot (the vehicle's
own pack's, else OCP's car seat). Police paints are left for a police station. Reads the jars only; runs nothing.
"""
import json, os, re, sys, zipfile

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..', 'src/main/resources/data/cityworld/cityworld/vehicle')
# what each vehicle is, for whatever lot wants one ("car" fills the car parks)
KINDS = {
    'mtsofficialpack:merc230': 'car', 'mtsofficialpack:fordmustang69': 'car', 'mtsofficialpack:scout': 'car',
    'mtsofficialpack:quad': 'quad', 'mtsofficialpack:gmcbrig': 'truck', 'mtsofficialpack:firetruck': 'fire_engine',
    'oamp:camaro': 'car', 'oamp:highwayman': 'car', 'oamp:escargot': 'car', 'oamp:cagouille': 'car',
    'oamp:luxorama': 'car', 'oamp:stationmerc': 'car',
}
OCP = 'mtsofficialpack'


def strip(s):
    out, i, n, ins = [], 0, len(s), False
    while i < n:
        c = s[i]
        if ins:
            out.append(c)
            if c == '\\':
                out.append(s[i + 1]); i += 2; continue
            if c == '"':
                ins = False
            i += 1; continue
        if c == '"':
            ins = True; out.append(c); i += 1; continue
        if s.startswith('//', i):
            j = s.find('\n', i); i = n if j < 0 else j; continue
        if s.startswith('/*', i):
            j = s.find('*/', i); i = n if j < 0 else j + 2; continue
        out.append(c); i += 1
    t = re.sub(r',(\s*[}\]])', r'\1', ''.join(out))
    return re.sub(r'(?<=[\[,:\s])0+(?=\d)', '', t)


def defs(jars):
    vehicles, parts = {}, {}
    for jar in jars:
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                m = re.match(r'assets/([a-z0-9_]+)/jsondefs/(vehicles|parts)/([a-z0-9_]+)\.json$', name)
                if not m:
                    continue
                j = json.loads(strip(z.read(name).decode('utf-8', 'replace')))
                (vehicles if m.group(2) == 'vehicles' else parts)[m.group(1) + ':' + m.group(3)] = j
    return vehicles, parts


def subs(j):
    return [d.get('subName', '') for d in j.get('definitions', [])] or ['']


def main(jars):
    vehicles, parts = defs(jars)
    wheels = {k: (p.get('ground') or {}).get('height', 0) for k, p in parts.items()
              if (p.get('generic') or {}).get('type') == 'ground_wheel'}
    seats = [k for k, p in parts.items() if (p.get('generic') or {}).get('type') == 'seat'
             and not (p.get('generic') or {}).get('customType')]
    os.makedirs(OUT, exist_ok=True)
    written = 0
    if LIST:
        for vid, v in sorted(vehicles.items()):
            m = v.get('motorized') or {}
            print(vid, 'aircraft' if m.get('isAircraft') else 'ground', len(subs(v)), 'paints')
        return
    for vid, v in sorted(vehicles.items()):
        kind = KINDS.get(vid)
        if not kind:
            continue
        pack = vid.split(':')[0]
        def own_first(ids):
            return sorted(ids, key=lambda k: (k.split(':')[0] != pack, k.split(':')[0] != OCP, k))
        lines, wheelz = [], []
        for slot, p in enumerate(v.get('parts', [])):
            types = p.get('types', [])
            part = p.get('defaultPart')
            if part and part.split(':')[0] in ('mts', pack, OCP):
                lines.append({'slot': slot, 'part': part, 'subs': subs(parts.get(part, {})) if part in parts else ['']})
                continue
            if 'ground_wheel' in types:
                lo, hi = p.get('minValue', 0), p.get('maxValue', 99)
                fits = [k for k in own_first(wheels) if lo <= wheels[k] <= hi]
                fits.sort(key=lambda k: 'offroad' in k)  # road tyres for a parked car
                if fits:
                    lines.append({'slot': slot, 'part': fits[0],
                                  'subs': [s for s in subs(parts[fits[0]]) if 'police' not in s] or ['']})
                    if abs(p.get('pos', [0, 0, 0])[0]) > 0.3:  # a spare rides on the centreline
                        wheelz.append(p['pos'][2])
            elif 'seat' in types:
                own = [k for k in own_first(seats) if k.split(':')[0] in (pack, OCP)]
                own = [k for k in own if k != OCP + ':seat'] or own  # the plane seat last
                if own:
                    lines.append({'slot': slot, 'part': own[0], 'subs': [s for s in subs(parts[own[0]]) if s]})
        paints = [s for s in subs(v) if 'police' not in s]
        centre = round((min(wheelz) + max(wheelz)) / 2, 3) if wheelz else 0.0
        out = {'requires_mod': pack, 'vehicle': vid, 'kind': kind, 'paints': paints, 'centre': centre,
               'fuel_capacity': (v.get('motorized') or {}).get('fuelCapacity', 0), 'parts': lines}
        with open(os.path.join(OUT, vid.replace(':', '_') + '.json'), 'w') as f:
            json.dump(out, f, indent=2)
            f.write('\n')
        written += 1
        print(vid, kind, len(paints), 'paints', len(lines), 'parts, centre', centre)
    print(written, 'vehicles written to', os.path.normpath(OUT))


LIST = False

if __name__ == '__main__':
    args, jars = sys.argv[1:], []
    while args:
        a = args.pop(0)
        if a == '--kind':
            k, _, v = args.pop(0).partition('=')
            KINDS[k] = v
        elif a == '--out':
            OUT = args.pop(0)
        elif a == '--list':
            LIST = True
        else:
            jars.append(a)
    main(jars)
