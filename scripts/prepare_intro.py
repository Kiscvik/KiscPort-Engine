#!/usr/bin/env python3
"""Prepare the supplied composite on the PC. No dependencies beyond the pinned JDK.

Example for the 1536x1024 chat reference:
python3 scripts/prepare_intro.py /path/original.png --crop 128 144 1280 572
Rebuild: python3 scripts/prepare_intro.py (reads the preserved original and recipe).
"""
import argparse
import hashlib
import json
import shutil
import subprocess
from pathlib import Path
from build import ROOT, jdk, run


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', nargs='?')
    parser.add_argument('--crop', nargs=4, type=int, metavar=('X', 'Y', 'W', 'H'))
    parser.add_argument('--output-dir', default=str(ROOT/'assets/intro'))
    args = parser.parse_args()
    assets = Path(args.output_dir).resolve()
    assets.mkdir(parents=True, exist_ok=True)
    report_path = assets/'conversion.json'
    if args.source:
        source = Path(args.source).resolve()
        if not args.crop:
            parser.error('--crop is required when importing an original')
        original = assets/('original' + source.suffix.lower())
        if original.resolve() != source:
            if original.exists() and original.read_bytes() != source.read_bytes():
                raise SystemExit('Preserved original differs; choose a new asset directory before replacing it')
            shutil.copyfile(source, original)
        crop = args.crop
    else:
        recipe = json.loads(report_path.read_text())
        original = assets/recipe['source']
        if hashlib.sha256(original.read_bytes()).hexdigest() != recipe['source_sha256']:
            raise SystemExit('Original hash mismatch')
        crop = recipe['crop_xywh']
    classes = ROOT/'build/intro-preparer'
    classes.mkdir(parents=True, exist_ok=True)
    run([jdk()/'bin/javac', '--release', '8', '-d', classes, ROOT/'scripts/PrepareIntro.java'])
    run([jdk()/'bin/java', '-Djava.awt.headless=true', '-cp', classes, 'PrepareIntro', original, assets] + crop)
    version = subprocess.check_output([jdk()/'bin/java', '-version'], stderr=subprocess.STDOUT).decode().strip()
    outputs = {p.name: hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(assets.glob('kisc-labs-*.png'))}
    from make_intro_sound import build as sound
    cue = sound(assets); outputs[cue['file']] = cue['sha256']
    report_path.write_text(json.dumps(dict(source=original.name, source_sha256=hashlib.sha256(original.read_bytes()).hexdigest(),
        crop_xywh=crop, tool='scripts/PrepareIntro.java', java_version=version,
        tool_sha256=hashlib.sha256((ROOT/'scripts/PrepareIntro.java').read_bytes()).hexdigest(),
        conversion='bicubic; fixed 256-colour silver/blue palette; nearest colour; opaque PNG', sound=cue, outputs=outputs), indent=2)+'\n')


if __name__ == '__main__':
    main()
