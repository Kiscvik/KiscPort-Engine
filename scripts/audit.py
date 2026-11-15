#!/usr/bin/env python3
"""Resolve delivery class/member references against the CLDC/MIDP/M3G boot APIs.

Host-only audit, not a replacement for the W900i verifier or a vendor SDK.
"""
import hashlib
import json
import os
import re
import zipfile
from pathlib import Path
from verify import ROOT, DIST, Reader, inspect, props


def model(data):
    r = Reader(data)
    assert r.u4() == 0xcafebabe
    r.take(4)
    cp = [None] * r.u2()
    i = 1
    while i < len(cp):
        tag = r.u1()
        if tag == 1:
            value = r.take(r.u2()).decode('utf-8', errors='replace')
        elif tag in (7, 8, 16, 19, 20):
            value = r.u2()
        elif tag in (9, 10, 11, 12, 18):
            value = (r.u2(), r.u2())
        elif tag == 15:
            value = (r.u1(), r.u2())
        elif tag in (3, 4):
            value = r.take(4)
        elif tag in (5, 6):
            value = r.take(8)
            cp[i] = (tag, value)
            i += 2
            continue
        else:
            raise AssertionError('unknown constant pool tag %s' % tag)
        cp[i] = (tag, value)
        i += 1
    def utf(at): return cp[at][1]
    def cls(at): return utf(cp[at][1]) if at else None
    r.u2()
    name, parent = cls(r.u2()), cls(r.u2())
    parents = ([parent] if parent else []) + [cls(r.u2()) for _ in range(r.u2())]
    members = [set(), set()]
    def attributes():
        for _ in range(r.u2()):
            r.u2()
            r.take(r.u4())
    descriptors = []
    for kind in range(2):
        for _ in range(r.u2()):
            r.u2()
            member, descriptor = utf(r.u2()), utf(r.u2())
            members[kind].add((member, descriptor))
            descriptors.append(descriptor)
            attributes()
    attributes()
    assert r.pos == len(data)
    classes, refs = set(parents), []
    for entry in cp:
        if entry is None: continue
        tag, value = entry
        if tag == 7:
            classes.add(utf(value))
        elif tag in (9, 10, 11):
            owner = cls(value[0])
            pair = cp[value[1]][1]
            member, descriptor = utf(pair[0]), utf(pair[1])
            descriptors.append(descriptor)
            refs.append((0 if tag == 9 else 1, owner, member, descriptor))
    for descriptor in descriptors:
        classes.update(re.findall(r'L([^;]+);', descriptor))
    for array in list(classes):
        if array.startswith('['):
            classes.remove(array)
            classes.update(re.findall(r'L([^;]+);', array))
    return dict(name=name, parents=parents, members=members, classes=classes, refs=refs)


def resolves(models, owner, kind, member, descriptor, seen=None):
    if seen is None: seen = set()
    if owner in seen or owner not in models: return False
    seen.add(owner)
    entry = models[owner]
    if (member, descriptor) in entry['members'][kind]: return True
    if member == '<init>': return False
    return any(resolves(models, p, kind, member, descriptor, seen) for p in entry['parents'])


def audit():
    jar, jad = DIST/'KiscPortDemo.jar', DIST/'KiscPortDemo.jad'
    paths = [ROOT/'tools/cldcapi11.jar', ROOT/'tools/midpapi20.jar',
             Path(os.environ.get('M3G_API_JAR', ROOT/'tools/m3g-compile-api.jar')), ROOT/'build/api-stubs.jar']
    models, libraries = {}, []
    prefixes = [('java/', 'javax/microedition/io/'),
                ('javax/microedition/',), ('javax/microedition/m3g/',), ('javax/microedition/media/control/MIDIControl', 'com/mascotcapsule/micro3d/v3/')]
    for path, allowed in zip(paths, prefixes):
        libraries.append(dict(file=path.name, sha256=hashlib.sha256(path.read_bytes()).hexdigest()))
        with zipfile.ZipFile(path) as z:
            for name in z.namelist():
                if name.endswith('.class') and name.startswith(allowed):
                    # M3G must come only from its separate compile API.
                    if path == paths[1] and name.startswith('javax/microedition/m3g/'): continue
                    m = model(z.read(name))
                    models[m['name']] = m
    application = []
    with zipfile.ZipFile(jar) as z:
        for name in z.namelist():
            if name.endswith('.class'):
                inspect(z.read(name))
                m = model(z.read(name))
                assert m['name'].startswith('kiscport/'), name
                models[m['name']] = m
                application.append(m)
        uncompressed = sum(i.file_size for i in z.infolist())
        manifest = props(z.read('META-INF/MANIFEST.MF'))
    external, references = set(), set()
    for m in application:
        for name in m['classes']:
            assert name in models, 'unavailable class: %s -> %s' % (m['name'], name)
            if not name.startswith('kiscport/'):
                assert name.startswith(('java/lang/', 'java/util/', 'java/io/',
                                        'javax/microedition/lcdui/', 'javax/microedition/midlet/',
                                        'javax/microedition/m3g/', 'javax/microedition/media/',
                                        'com/mascotcapsule/micro3d/v3/')), name
                external.add(name)
        if not m['name'].startswith('kiscport/renderer/'):
            assert not any(c.startswith('javax/microedition/m3g/') for c in m['classes']), 'M3G leaked into diagnostic classes'
        if not m['name'].startswith('kiscport/audio/'):
            assert not any(c.startswith('javax/microedition/media/') for c in m['classes']), 'MMAPI leaked outside kiscport/audio'
        # Optional JSR-135 MIDIControl: one class, loaded by name, so its absence is a catchable error.
        if m['name'] != 'kiscport/audio/MidiControlOut':
            assert 'javax/microedition/media/control/MIDIControl' not in m['classes'], 'MIDIControl outside MidiControlOut: ' + m['name']
        # Mascot Capsule v3 (alpha renderer): optional and proprietary, one class, loaded by name.
        if m['name'] != 'kiscport/renderer/MascotCubeScene':
            assert not any(c.startswith('com/mascotcapsule/') for c in m['classes']), 'Mascot outside MascotCubeScene: ' + m['name']
        for kind, owner, member, descriptor in m['refs']:
            assert resolves(models, owner, kind, member, descriptor), 'unavailable member: %s.%s%s' % (owner, member, descriptor)
            if not owner.startswith('kiscport/'):
                references.add('%s.%s%s' % (owner, member, descriptor))
    return dict(scope='desktop static audit; physical reports are recorded separately by artifact',
                version=manifest['MIDlet-Version'], jar_bytes=jar.stat().st_size,
                jar_sha256=hashlib.sha256(jar.read_bytes()).hexdigest(),
                jad_bytes=jad.stat().st_size, jad_sha256=hashlib.sha256(jad.read_bytes()).hexdigest(),
                uncompressed_bytes=uncompressed, application_classes=len(application),
                external_classes=sorted(external), external_members=sorted(references),
                compile_apis=libraries,
                limits=['M3G compile symbols are from pinned FreeJ2ME-Plus, not a certified Sony Ericsson SDK.',
                        'Reflection strings are exercised by full and absent emulator smoke tests.',
                        'MIDIControl symbols are a hand-written compile stub of JSR-135 (probe/stubs), not a vendor SDK.',
                        'Mascot Capsule v3 symbols are hand-written compile stubs (probe/stubs), not an HI Corporation SDK; the alpha renderer has no phone run yet.',
                        'CLDC verification and driver behavior require the physical W900i.'])


if __name__ == '__main__':
    from verify import main
    main()
    report = audit()
    destination = ROOT/'build'/'audit.json'
    destination.parent.mkdir(parents=True, exist_ok=True)
    destination.write_text(json.dumps(report, indent=2, sort_keys=True)+'\n')
    print('PASS: CLDC/MIDP/M3G class and member dependencies resolve; optional M3G isolated; Mascot only in MascotCubeScene')
    print('Audit:', destination)
