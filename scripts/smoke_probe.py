#!/usr/bin/env python3
"""FreeJ2ME-Plus smoke run of KiscPort Probe (desktop only; never hardware evidence)."""
from build import ROOT, jdk, run
import os, hashlib
from pathlib import Path
os.chdir(ROOT)
jar = ROOT / 'dist/probe/KiscPortProbe.jar'
classes = ROOT / 'build/probe/host'; classes.mkdir(parents=True, exist_ok=True)
emulator = ROOT / 'tools/freej2me-plus.jar'
evidence = ROOT / 'build/probe/emulator'; evidence.mkdir(parents=True, exist_ok=True)
run([jdk() / 'bin/javac', '--release', '8', '-cp', emulator, '-d', classes, ROOT / 'tests/ProbeSmoke.java'])
print('EVIDENCE SCOPE: FreeJ2ME-Plus emulator; not W900i/W995 hardware', flush=True)
print('JAR SHA256:', hashlib.sha256(jar.read_bytes()).hexdigest(), flush=True)
work = ROOT / 'build/probe/run'; work.mkdir(exist_ok=True); os.chdir(work)
# FreeJ2ME maps the file root freej2me_system/ to this folder: the saved report and PNG captures land here.
files = work / 'freej2me_system'
import shutil
shutil.rmtree(files, ignore_errors=True); files.mkdir()
run([jdk() / 'bin/java', '-Xmx64m', '-Djava.awt.headless=true', '-Dfile.encoding=ISO_8859_1', '-cp', str(classes) + os.pathsep + str(emulator),
     'ProbeSmoke', jar, evidence, files])
