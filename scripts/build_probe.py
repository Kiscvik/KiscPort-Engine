#!/usr/bin/env python3
"""Build KiscPort Probe (KISC-LABS): platform, audio and M3G performance MIDlet for any phone (W995, W900i...).

Same toolchain and limits as the game: ECJ language 1.3 / target 1.1, CLDC 1.1 + MIDP 2.0 boot
classpath, ProGuard -microedition preverification (CLDC StackMap), bytecode 45.3. Compile-only stubs
(MIDIControl, JSR-239 subset) and the M3G symbol jar are never packaged. Output:
dist/probe/KiscPortProbe.{jar,jad} + SHA256SUMS. Successor of the MMAPI diagnostic 1.0.0. Does not touch dist/KiscPortDemo.*."""
import argparse, hashlib, json, os, sys, tempfile, zipfile
from pathlib import Path
from build import ROOT, jdk, run, archive
from verify import inspect, props

DIAG = ROOT / 'probe'
ATTRS = {'MIDlet-Name': 'KiscPort Probe', 'MIDlet-Version': '1.2.0', 'MIDlet-Vendor': 'KISC-LABS',
         'MIDlet-1': 'KiscPort Probe,,kiscport.probe.KiscPortProbe',
         'MicroEdition-Configuration': 'CLDC-1.1', 'MicroEdition-Profile': 'MIDP-2.0'}


def ecj(java, apis, sources, out):
    run([java, '-jar', ROOT / 'tools/ecj.jar', '-source', '1.3', '-target', '1.1', '-proc:none', '-encoding', 'UTF-8',
         '-g:lines,source', '-nowarn', '-bootclasspath', os.pathsep.join(str(p) for p in apis),
         '-classpath', os.pathsep.join(str(p) for p in apis), '-d', out] + sources)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', default=str(ROOT / 'dist/probe'))
    args = parser.parse_args()
    out = Path(args.output_dir).resolve()
    if out == (ROOT / 'dist').resolve():
        raise SystemExit('The probe never writes into dist/ itself (game package).')
    java = jdk() / 'bin/java'
    work = ROOT / 'build/probe'
    work.mkdir(parents=True, exist_ok=True); out.mkdir(parents=True, exist_ok=True)
    base = [ROOT / 'tools/cldcapi11.jar', ROOT / 'tools/midpapi20.jar']
    m3g = Path(os.environ.get('M3G_API_JAR', ROOT / 'tools/m3g-compile-api.jar'))
    for p in base + [m3g]:
        if not p.is_file(): raise SystemExit('Missing dependency: ' + str(p))
    with tempfile.TemporaryDirectory(dir=work, prefix='stubs-') as tmp:
        ecj(java, base, sorted((DIAG / 'stubs').rglob('*.java')), tmp)
        archive(work / 'stubs.jar', {p.relative_to(tmp).as_posix(): p.read_bytes() for p in Path(tmp).rglob('*.class')})
    apis = base + [m3g, work / 'stubs.jar']
    with tempfile.TemporaryDirectory(dir=work, prefix='classes-') as tmp:
        ecj(java, apis, sorted((DIAG / 'src').rglob('*.java')), tmp)
        archive(work / 'raw.jar', {p.relative_to(tmp).as_posix(): p.read_bytes() for p in Path(tmp).rglob('*.class')})
    config = ['-injars "%s"' % (work / 'raw.jar'), '-outjars "%s"' % (work / 'preverified.jar')]
    config += ['-libraryjars "%s"' % p for p in apis]
    config += ['-microedition', '-dontshrink', '-dontoptimize', '-dontobfuscate', '-keepattributes SourceFile,LineNumberTable',
               '-keep class kiscport.probe.** { *; }']
    (work / 'preverify.pro').write_text('\n'.join(config) + '\n')
    if (work / 'preverified.jar').exists(): (work / 'preverified.jar').unlink()
    run([java, '-jar', ROOT / 'tools/proguard.jar', '@' + str(work / 'preverify.pro')])
    manifest = 'Manifest-Version: 1.0\r\n' + ''.join(k + ': ' + v + '\r\n' for k, v in ATTRS.items()) + '\r\n'
    with zipfile.ZipFile(work / 'preverified.jar') as z:
        entries = {n: z.read(n) for n in z.namelist() if n.endswith('.class')}
    entries['META-INF/MANIFEST.MF'] = manifest.encode('ascii')
    sounds = json.loads((DIAG / 'res/sounds.json').read_text())['files']
    for name, meta in sorted(sounds.items()):
        data = (DIAG / 'res' / name).read_bytes()
        if hashlib.sha256(data).hexdigest() != meta['sha256']:
            raise SystemExit('Test sound hash mismatch (run scripts/make_probe_sounds.py): ' + name)
        entries['snd/' + name] = data
    jar = out / 'KiscPortProbe.jar'
    archive(jar, entries)
    attrs = dict(ATTRS, **{'MIDlet-Jar-URL': jar.name, 'MIDlet-Jar-Size': str(jar.stat().st_size)})
    jad = out / 'KiscPortProbe.jad'
    jad.write_bytes(''.join(k + ': ' + v + '\r\n' for k, v in attrs.items()).encode('ascii'))
    (out / 'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest() + '  ' + p.name + '\n' for p in (jar, jad)))
    # Same checks as scripts/verify.py, for this package.
    descriptor = props(jad.read_bytes())
    assert descriptor['MIDlet-Jar-Size'] == str(jar.stat().st_size)
    maps = classes = 0
    with zipfile.ZipFile(jar) as z:
        assert z.testzip() is None
        for name in z.namelist():
            assert name == 'META-INF/MANIFEST.MF' or name.startswith(('kiscport/probe/', 'snd/')), 'unexpected entry ' + name
            if name.endswith('.class'):
                maps += inspect(z.read(name)); classes += 1
        m = props(z.read('META-INF/MANIFEST.MF'))
        assert all(descriptor[k] == v for k, v in m.items() if k != 'Manifest-Version')
    assert maps > 0
    print('PASS: probe package: bytecode 45.3, CLDC StackMap, only kiscport/probe + snd/, stubs not packaged')
    print('classes=%d StackMap methods=%d JAR bytes=%d SHA256=%s' % (classes, maps, jar.stat().st_size, hashlib.sha256(jar.read_bytes()).hexdigest()))


if __name__ == '__main__':
    main()
