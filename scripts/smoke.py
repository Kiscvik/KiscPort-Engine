#!/usr/bin/env python3
"""FreeJ2ME-Plus run of the engine demo, with M3G (default) or without it ('absent'). Desktop only, never hardware."""
from build import ROOT, jdk, run, archive
import os, sys, hashlib, zipfile
from pathlib import Path
os.chdir(ROOT)
variant = 'absent' if 'absent' in sys.argv else 'full'
classes = ROOT/('build/host-tests-' + variant); classes.mkdir(parents=True, exist_ok=True)
emulator = ROOT/'tools/freej2me-plus.jar'
if variant == 'absent':
    with zipfile.ZipFile(emulator) as z:
        entries = {n: z.read(n) for n in z.namelist() if not n.startswith('javax/microedition/m3g/')}
    emulator = ROOT/'build/emulator-without-m3g.jar'
    archive(emulator, entries)
run([jdk()/'bin/javac', '--release', '8', '-cp', emulator, '-d', classes, ROOT/'tests/DemoSmoke.java'])
jar = Path(os.environ.get('KISCPORT_DIST', ROOT/'dist'))/'KiscPortDemo.jar'
evidence = ROOT/'build'/('demo-' + variant); evidence.mkdir(parents=True, exist_ok=True)
print('EVIDENCE SCOPE: FreeJ2ME-Plus emulator; not phone hardware', flush=True)
print('JAR SHA256:', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
work = ROOT/('build/emulator-run-' + variant); work.mkdir(exist_ok=True); os.chdir(work)
run([jdk()/'bin/java', '-Xmx64m', '-Djava.awt.headless=true', '-Dfile.encoding=ISO_8859_1', '-cp', str(classes) + os.pathsep + str(emulator),
     'DemoSmoke', jar, evidence, variant])
if variant == 'full':
    # Mascot beta section renderer on a synthetic level (tests/mascot_fixture.py; page made by scripts/mascot.py).
    sys.path.insert(0, str(ROOT/'tests'))
    import mascot_fixture
    level = ROOT/'build/mascot-level'
    fixture = mascot_fixture.build(level/'mascot-fixture')
    run([sys.executable, ROOT/'scripts/mascot.py', 'texture', fixture/'stage_0.png', fixture/'stage_0.bmp'])
    tests = ROOT/'build/host-tests-mascot'; tests.mkdir(parents=True, exist_ok=True)
    run([jdk()/'bin/javac', '--release', '8', '-cp', str(jar) + os.pathsep + str(emulator), '-d', tests, ROOT/'tests/MascotSectionTests.java'])
    import subprocess
    out = subprocess.run([str(jdk()/'bin/java'), '-Xmx64m', '-Djava.awt.headless=true', '-Dfile.encoding=ISO_8859_1', '-cp',
                          os.pathsep.join(str(p) for p in (tests, level, jar, emulator)), 'kiscport.renderer.MascotSectionTests', str(evidence)],
                         stdout=subprocess.PIPE, stderr=subprocess.STDOUT).stdout.decode('iso-8859-1')
    print(out, end='', flush=True)
    # The emulator can restart itself and exit 0 without running the test: only the test's own last line counts.
    if 'MASCOT SECTION TESTS DONE' not in out:
        raise SystemExit('Mascot section tests did not complete')
    print('MASCOT SECTION SUCCESS')
