#!/usr/bin/env python3
"""Original, deterministic 340 ms logo sweep; mono PCM UI8/8 kHz for Java ME."""
import hashlib
import math
import wave
from pathlib import Path


def build(folder):
    folder = Path(folder)
    target = folder / 'laser.wav'
    rate, count, phase = 8000, 2720, 0.0
    pcm = bytearray()
    for i in range(count):
        t = i / float(count - 1)
        frequency = 2600 * (0.15 ** t)
        phase += 2 * math.pi * frequency / rate
        envelope = min(1.0, t / 0.025) * (1 - t) ** 1.7
        value = (math.sin(phase) + 0.18 * math.sin(phase * 1.5)) / 1.18
        pcm.append(max(0, min(255, round(128 + 80 * envelope * value))))
    with wave.open(str(target), 'wb') as w:
        w.setnchannels(1); w.setsampwidth(1); w.setframerate(rate); w.writeframes(pcm)
    return {'file': target.name, 'sha256': hashlib.sha256(target.read_bytes()).hexdigest(),
            'bytes': target.stat().st_size, 'samples': count, 'rate': rate,
            'source': 'original mathematical chirp, scripts/make_intro_sound.py'}


if __name__ == '__main__':
    import json
    folder = Path(__file__).resolve().parents[1] / 'assets/intro'
    cue = build(folder)
    report = folder / 'conversion.json'
    recipe = json.loads(report.read_text())
    recipe['sound'] = cue; recipe['outputs'][cue['file']] = cue['sha256']
    report.write_text(json.dumps(recipe, indent=2) + '\n')
    print(json.dumps(cue, indent=2))
