#!/usr/bin/env python3
"""CLDC build. No javac/java.base on application boot classpath."""
import os, sys, subprocess, zipfile, hashlib
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]
os.chdir(ROOT)
# Optional APIs that are not in the MIDP 2.0 API jar. Compile-only stubs (Probe's), never packaged; each may be
# used by one class only (scripts/audit.py), loaded by name on the phone:
# JSR-135 MIDIControl -> kiscport/audio/MidiControlOut; Mascot Capsule v3 -> kiscport/renderer/MascotCubeScene (alpha).
STUBS = [ROOT/'probe/stubs/javax/microedition/media/control/MIDIControl.java'] + \
    sorted((ROOT/'probe/stubs/com/mascotcapsule/micro3d/v3').glob('*.java'))

def jdk():
    supplied = os.environ.get('KISCPORT_JDK')
    if supplied:
        return Path(supplied)
    candidates = sorted((ROOT/'tools').glob('jdk-*/Contents/Home'))
    if not candidates:
        raise SystemExit('Run python3 scripts/bootstrap.py or set KISCPORT_JDK')
    return candidates[-1]

def run(args):
    print('+', ' '.join(str(x) for x in args), flush=True)
    subprocess.run([str(x) for x in args], check=True)

def archive(path, entries):
    with zipfile.ZipFile(path, 'w', zipfile.ZIP_DEFLATED, compresslevel=9) as z:
        for name, data in sorted(entries.items()):
            info = zipfile.ZipInfo(name, (2000,1,1,0,0,0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o100644 << 16
            z.writestr(info, data)

def stubs(java, base):
    """build/api-stubs.jar from STUBS, compiled like the application (1.3 / 45.3)."""
    import tempfile
    out = ROOT/'build/api-stubs.jar'
    with tempfile.TemporaryDirectory(dir=ROOT/'build', prefix='stub-') as tmp:
        run([java, '-jar', ROOT/'tools/ecj.jar', '-source', '1.3', '-target', '1.1', '-proc:none', '-encoding', 'UTF-8',
             '-nowarn', '-bootclasspath', os.pathsep.join(str(p) for p in base), '-classpath', os.pathsep.join(str(p) for p in base),
             '-d', tmp] + STUBS)
        archive(out, {p.relative_to(tmp).as_posix(): p.read_bytes() for p in Path(tmp).rglob('*.class')})
    return out

def main():
    import argparse
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output-dir', default=os.environ.get('KISCPORT_DIST', str(ROOT/'dist')))
    parser.add_argument('--intro', choices=('on', 'off'), default=os.environ.get('KISCPORT_INTRO', 'on'))
    parser.add_argument('--intro-prefix', default=os.environ.get('KISCPORT_INTRO_PREFIX', '/kiscport/intro/kisc-labs'))
    parser.add_argument('--intro-assets', default=str(ROOT/'assets/intro'))
    args = parser.parse_args()
    output = Path(args.output_dir).resolve()
    os.environ['KISCPORT_DIST'] = str(output)
    java = jdk()/'bin/java'
    build = ROOT/'build'
    build.mkdir(exist_ok=True); output.mkdir(parents=True, exist_ok=True)
    # Isolated output on every build; never package stale application classes.
    import tempfile
    with tempfile.TemporaryDirectory(dir=build, prefix='classes-') as tmp:
        m3g = Path(os.environ.get('M3G_API_JAR', ROOT/'tools/m3g-compile-api.jar'))
        apis = [ROOT/'tools/cldcapi11.jar', ROOT/'tools/midpapi20.jar', m3g]
        for p in apis:
            if not p.is_file(): raise SystemExit('Missing dependency: '+str(p))
        apis.append(stubs(java, apis[:2]))
        sources = sorted((ROOT/'src').rglob('*.java'))
        run([java, '-jar', ROOT/'tools/ecj.jar', '-source', '1.3', '-target', '1.1',
             '-proc:none', '-encoding', 'UTF-8', '-g:lines,source',
             '-bootclasspath', os.pathsep.join(str(p) for p in apis),
             '-classpath', os.pathsep.join(str(p) for p in apis), '-d', tmp] + sources)
        archive(build/'raw.jar', {p.relative_to(tmp).as_posix():p.read_bytes() for p in Path(tmp).rglob('*.class')})
    config = ['-injars "'+str(build/'raw.jar')+'"', '-outjars "'+str(build/'preverified.jar')+'"']
    config += ['-libraryjars "'+str(p)+'"' for p in apis]
    config += ['-microedition','-dontshrink','-dontoptimize','-dontobfuscate',
               '-keepattributes SourceFile,LineNumberTable', '-keep class kiscport.** { *; }']
    (build/'preverify.pro').write_text('\n'.join(config)+'\n')
    run([java, '-jar', ROOT/'tools/proguard.jar', '@'+str(build/'preverify.pro')])
    attrs = {'MIDlet-Name':'KiscPort Demo', 'MIDlet-Version':'0.9.0', 'MIDlet-Vendor':'KISC-LABS',
             'MIDlet-1':'KiscPort Demo,,kiscport.platform.KiscPortMIDlet',
             'MicroEdition-Configuration':'CLDC-1.1', 'MicroEdition-Profile':'MIDP-2.0',
             'KiscPort-Intro':args.intro, 'KiscPort-Intro-Prefix':args.intro_prefix}
    if any('\n' in v or '\r' in v or len(k + ': ' + v) > 72 for k, v in attrs.items()):
        raise SystemExit('Invalid or overlong MIDlet attribute')
    manifest = 'Manifest-Version: 1.0\r\n'+''.join(k+': '+v+'\r\n' for k,v in attrs.items())+'\r\n'
    with zipfile.ZipFile(build/'preverified.jar') as z:
        entries = {n:z.read(n) for n in z.namelist() if n.endswith('.class')}
    entries['META-INF/MANIFEST.MF'] = manifest.encode('ascii')
    import json
    intro_assets = Path(args.intro_assets).resolve()
    intro_report = json.loads((intro_assets/'conversion.json').read_text())
    for name, digest in sorted(intro_report['outputs'].items()):
        data = (intro_assets/name).read_bytes()
        if hashlib.sha256(data).hexdigest() != digest:
            raise SystemExit('Intro resource hash mismatch: '+name)
        entries['kiscport/intro/'+name] = data
    # Demo text table, encoded on every build from its versioned UTF-8 source (no binary in Git).
    sys.path.insert(0, str(ROOT/'scripts'))
    from prepare_text import encode
    entries['kiscport/demo/text.ktt'] = encode(json.loads((ROOT/'assets/demo/text.json').read_text(encoding='utf-8')))
    jar = output/'KiscPortDemo.jar'
    archive(jar, entries)
    attrs.update({'MIDlet-Jar-URL':jar.name, 'MIDlet-Jar-Size':str(jar.stat().st_size)})
    (output/'KiscPortDemo.jad').write_bytes(''.join(k+': '+v+'\r\n' for k,v in attrs.items()).encode('ascii'))
    (output/'SHA256SUMS').write_text(''.join(hashlib.sha256(p.read_bytes()).hexdigest()+'  '+p.name+'\n'
        for p in (jar, output/'KiscPortDemo.jad')))
    print('JAR bytes:',jar.stat().st_size,'SHA256:',hashlib.sha256(jar.read_bytes()).hexdigest())
    run([sys.executable, ROOT/'scripts/verify.py'])
    run([sys.executable, ROOT/'scripts/audit.py'])

if __name__ == '__main__': main()
