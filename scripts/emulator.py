#!/usr/bin/env python3
"""Open a JAR in FreeJ2ME-Plus (desktop). Default: the engine demo in dist/."""
import argparse, subprocess
from pathlib import Path
from build import ROOT, jdk
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('jar', nargs='?', default=str(ROOT/'dist/KiscPortDemo.jar'))
parser.add_argument('--size', default='240x320')
args = parser.parse_args()
w, h = args.size.split('x')
subprocess.run([str(jdk()/'bin/java'), '-jar', str(ROOT/'tools/freej2me-plus.jar'), Path(args.jar).resolve().as_uri(), w, h], check=True)
