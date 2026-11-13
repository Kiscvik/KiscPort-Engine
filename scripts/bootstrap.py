#!/usr/bin/env python3
"""Pinned, SHA256-checked local tools, no global Java installation changes."""
import argparse,hashlib,json,os,platform,tarfile,tempfile,urllib.request
from pathlib import Path
from build import ROOT,run
p=argparse.ArgumentParser(); p.add_argument('--offline',action='store_true'); args=p.parse_args()
os.chdir(ROOT)
tools=ROOT/'tools'; tools.mkdir(exist_ok=True)
for item in json.loads((tools/'dependencies.lock.json').read_text()):
    dest=tools/item['file']
    if item['file']=='jdk.tar.gz' and (platform.system()!='Darwin' or platform.machine()!='arm64'):
        if not os.environ.get('KISCPORT_JDK'): raise SystemExit('This JDK archive is macOS arm64; set KISCPORT_JDK to a host JDK 17 on other hosts.')
        continue
    if not dest.exists():
        if args.offline: raise SystemExit('Missing offline dependency: '+dest.name)
        print('Download:',item['url'],flush=True)
        with urllib.request.urlopen(item['url'],timeout=120) as response, tempfile.NamedTemporaryFile(dir=tools,delete=False) as out:
            temp=Path(out.name)
            while True:
                block=response.read(1024*1024)
                if not block: break
                out.write(block)
        if hashlib.sha256(temp.read_bytes()).hexdigest()!=item['sha256']:
            raise SystemExit('SHA256 mismatch: '+str(temp))
        temp.rename(dest)
    if hashlib.sha256(dest.read_bytes()).hexdigest()!=item['sha256']:
        raise SystemExit('SHA256 mismatch: '+str(dest))
    print('SHA256 OK:',dest.name)

def extract(archive,destination,strip=False):
    destination.mkdir(exist_ok=True)
    with tarfile.open(archive) as tar:
        members=[]
        for member in tar.getmembers():
            parts=Path(member.name).parts
            if strip: parts=parts[1:]
            if not parts: continue
            if Path(*parts).is_absolute() or '..' in parts: raise SystemExit('Unsafe archive path')
            member.name=str(Path(*parts))
            target=(destination/member.name).resolve()
            if destination.resolve() not in target.parents: raise SystemExit('Unsafe extraction target')
            if member.issym() or member.islnk():
                link=(target.parent/member.linkname).resolve()
                if destination.resolve() not in link.parents: raise SystemExit('Unsafe symlink')
            members.append(member)
        tar.extractall(destination,members=members)
if not (tools/'jdk-17.0.21+9/Contents/Home/bin/java').exists() and not os.environ.get('KISCPORT_JDK'):
    extract(tools/'jdk.tar.gz',tools)
if not (tools/'emulator/src').exists(): extract(tools/'freej2me.tar.gz',tools/'emulator',True)
run([os.sys.executable,ROOT/'scripts/prepare_emulator.py'])
