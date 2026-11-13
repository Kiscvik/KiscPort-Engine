#!/usr/bin/env python3
"""Build the unmodified FreeJ2ME-Plus snapshot using a host JDK."""
from build import ROOT, jdk, run, archive
from pathlib import Path
import os
os.chdir(ROOT)
emu = ROOT/'tools/emulator'
classes = emu/'classes'; classes.mkdir(exist_ok=True)
args = emu/'sources.txt'
args.write_text('\n'.join('"'+str(p)+'"' for p in sorted((emu/'src').rglob('*.java'))))
run([jdk()/'bin/javac', '--release', '8', '-encoding', 'UTF-8', '-d', classes, '@'+str(args)])
entries = {p.relative_to(classes).as_posix():p.read_bytes() for p in classes.rglob('*.class')}
for p in (emu/'resources').rglob('*'):
    if p.is_file(): entries[p.relative_to(emu/'resources').as_posix()] = p.read_bytes()
entries['META-INF/MANIFEST.MF'] = b'Manifest-Version: 1.0\r\nMain-Class: org.recompile.freej2me.FreeJ2ME\r\n\r\n'
archive(ROOT/'tools/freej2me-plus.jar', entries)
# Compile/preverify library only: NEVER copied into dist.
archive(ROOT/'tools/m3g-compile-api.jar', {k:v for k,v in entries.items() if k.startswith('javax/microedition/m3g/') and k.endswith('.class')})
print('Built emulator and isolated M3G compile library')
