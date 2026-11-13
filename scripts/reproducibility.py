#!/usr/bin/env python3
from build import ROOT,run
import hashlib,os
os.chdir(ROOT)
def hashes():
    return {name:hashlib.sha256((__import__('pathlib').Path(os.environ.get('KISCPORT_DIST', ROOT/'dist'))/name).read_bytes()).hexdigest()
            for name in ('KiscPortDemo.jar','KiscPortDemo.jad','SHA256SUMS')}
run([os.sys.executable,ROOT/'scripts/build.py']); first=hashes()
run([os.sys.executable,ROOT/'scripts/build.py']); second=hashes()
assert first==second,(first,second)
print('PASS: two builds produce byte-for-byte identical JAR, JAD and SHA256SUMS')
print(second)
