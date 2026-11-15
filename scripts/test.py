#!/usr/bin/env python3
"""Desktop checks of the engine: logic tests against the built JAR, CLDC audits, negative compile. No private inputs."""
from build import ROOT, jdk, run
from pathlib import Path
import os, subprocess, tempfile, sys
os.chdir(ROOT)
run([sys.executable, ROOT/'scripts/verify.py'])
classes = ROOT/'build/logic-tests'; classes.mkdir(parents=True, exist_ok=True)
jar = Path(os.environ.get('KISCPORT_DIST', ROOT/'dist'))/'KiscPortDemo.jar'
tests = ['FrameStatsTests', 'BacklightTests', 'StripsTests', 'TextTableTests']
run([jdk()/'bin/javac', '--release', '8', '-cp', jar, '-d', classes] + [ROOT/'tests'/(t + '.java') for t in tests])
sys.path.insert(0, str(ROOT/'scripts'))
from prepare_text import encode
fixture = ROOT/'build/text-table-fixture.ktt'
fixture.write_bytes(encode(['Fixture %d ñ\U0001f680' % i for i in range(8)]))
for invalid in ([], ['x'] * 33, [1] * 8, ['x' * 257] * 8):
    try:
        encode(invalid)
    except ValueError:
        pass
    else:
        raise AssertionError('invalid text table accepted')
for t in tests:
    run([jdk()/'bin/java', '-cp', str(classes) + os.pathsep + str(jar), t] + ([str(fixture)] if t == 'TextTableTests' else []))
run([sys.executable, ROOT/'scripts/audit.py'])
with tempfile.TemporaryDirectory(dir=ROOT/'build') as tmp:
    source = Path(tmp)/'Forbidden.java'
    source.write_text('class Forbidden { java.util.ArrayList list; }')
    apis = os.pathsep.join(str(ROOT/'tools'/x) for x in ['cldcapi11.jar', 'midpapi20.jar', 'm3g-compile-api.jar'])
    p = subprocess.run([str(jdk()/'bin/java'), '-jar', str(ROOT/'tools/ecj.jar'), '-source', '1.3', '-target', '1.1', '-proc:none',
                        '-bootclasspath', apis, '-classpath', apis, '-d', tmp, str(source)], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    assert p.returncode != 0 and b'ArrayList cannot be resolved' in p.stdout, p.stdout
    print('PASS: negative compile rejects Java SE ArrayList')
# Mascot beta tools (PC): synthetic level, texture conversion and fit check.
import struct
sys.path.insert(0, str(ROOT/'tests'))
import mascot_fixture
fixture_dir = mascot_fixture.build(ROOT/'build/mascot-fixture')
def mascot(*args):
    p = subprocess.run([sys.executable, str(ROOT/'scripts/mascot.py')] + [str(a) for a in args], stdout=subprocess.PIPE, stderr=subprocess.STDOUT)
    return p.returncode, p.stdout.decode('utf-8')
code, out = mascot('texture', fixture_dir/'stage_0.png', fixture_dir/'stage_0.bmp')
assert code == 0 and 'CABE CON PERDIDAS, 128x64, 255 colores + colour key (272 px transparentes)' in out, out
assert 'error RMS 8.40' in out and '16 px con alfa parcial' in out and 'no es cuadrada' in out, out
bmp = (fixture_dir/'stage_0.bmp').read_bytes()
assert bmp[:2] == b'BM' and struct.unpack('<iiHH', bmp[18:30]) == (128, 64, 1, 8) and len(bmp) == 14 + 40 + 1024 + 128 * 64
assert bmp[54:57] == bytes((0xff, 0, 0xff)), 'colour key: palette entry 0 is magenta'
def texel(x, y):                                            # bottom-up rows: image row y is stored at row 63 - y
    i = bmp[14 + 40 + 1024 + (63 - y) * 128 + x]
    return tuple(bmp[54 + i * 4 + 2 - c] for c in range(3)), i
assert texel(64, 0) == ((230, 30, 30), texel(64, 0)[1]) and texel(64, 0)[1] != 0, 'red mark top-left of the right half'
assert texel(127, 63)[1] == 0 and texel(112, 0)[1] == 0, 'transparent hole and alpha 64 use the key'
assert texel(100, 30)[0] == (40, 60, 220) and texel(100, 30)[1] != 0, 'opaque blue never uses the key'
print('PASS: Mascot texture: 8-bit BMP as Probe writes it, colour key at 0, orientation, losses reported')
mascot_fixture.png(fixture_dir/'small.png', 16, 16, lambda x, y: (255, 0, 255, 255) if x < 8 else (255, 255, 255, 255))   # magenta sorts first
code, out = mascot('texture', fixture_dir/'small.png', fixture_dir/'small.bmp')
small = (fixture_dir/'small.bmp').read_bytes()
assert code == 0 and 'small.png: CABE, 16x16, 2 colores' in out and 'perdida' not in out, out
assert small[54:57] != bytes((0xff, 0, 0xff)), 'without transparency entry 0 is never magenta'
mascot_fixture.png(fixture_dir/'large.png', 512, 16, lambda x, y: (0, 0, 0, 255))
code, out = mascot('texture', fixture_dir/'large.png', fixture_dir/'large.bmp')
assert code == 1 and 'NO CABE' in out and not (fixture_dir/'large.bmp').exists(), out
print('PASS: Mascot texture: exact palette when it fits, no false colour key, refuses more than 256 px')
code, out = mascot('check', fixture_dir)
for expected in ('chunk0.kps: CABE CON PERDIDAS, 4 tri en 1 llamadas', '1 tri con color de vertice distinto de blanco',
                 '1 tri con colores distintos en sus vertices', 'chunk1.kps: CABE, 800 tri en 4 llamadas',
                 'en el W900i 768 ya daban', '64 bytes por triangulo', 'VEREDICTO: CABE CON PERDIDAS'):
    assert expected in out, (expected, out)
assert code == 0, out
(fixture_dir/'chunk9.kps').write_bytes((fixture_dir/'chunk0.kps').read_bytes()[:-2])
code, out = mascot('check', fixture_dir)
(fixture_dir/'chunk9.kps').unlink()
assert code == 1 and 'chunk9.kps: NO CABE (archivo truncado)' in out and 'VEREDICTO: NO CABE' in out, out
print('PASS: Mascot check: losses, calls, beyond-measured notes, refuses a broken chunk')
print('TESTS PASSED')
