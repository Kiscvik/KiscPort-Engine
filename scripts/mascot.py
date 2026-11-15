#!/usr/bin/env python3
"""BETA tools for Mascot Capsule Micro3D v3, PC only (pinned JDK; nothing here goes into a MIDlet).

python3 scripts/mascot.py texture IN.png OUT.bmp   PNG -> 8-bit BMP for Mascot, with a report of what is lost
python3 scripts/mascot.py check LEVEL_DIR          Does a KiscPort level (chunkN.kps KPS3 + stage_N.png) fit Mascot?

See scripts/MascotTools.java and docs/ENGINE.md ("Mascot Capsule v3"). Verified on PC and FreeJ2ME-Plus only.
"""
import sys
from build import ROOT, jdk, run


def main(argv):
    if len(argv) < 1 or argv[0] not in ('texture', 'check'):
        raise SystemExit(__doc__)
    classes = ROOT/'build/mascot-tools'
    classes.mkdir(parents=True, exist_ok=True)
    run([jdk()/'bin/javac', '--release', '8', '-encoding', 'UTF-8', '-d', classes, ROOT/'scripts/MascotTools.java'])
    import subprocess
    code = subprocess.call([str(jdk()/'bin/java'), '-Djava.awt.headless=true', '-cp', str(classes), 'MascotTools'] + argv)
    sys.exit(code)


if __name__ == '__main__':
    main(sys.argv[1:])
