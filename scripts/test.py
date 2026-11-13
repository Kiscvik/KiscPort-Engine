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
print('TESTS PASSED')
