#!/usr/bin/env python3
"""Original synthetic test sounds for KiscPort Probe (PC only, deterministic).

No game audio: sine/pulse tones written by this script. Every WAV variant carries the same two
signals so formats can be compared by ear:
  A = 'long'  1.5 s steady 330 Hz tone with a slow 5 Hz tremolo (stands in for music);
  B = 'beep'  three 1320 Hz beeps of 80 ms (stands in for a short effect).
Formats: PCM u8 8 kHz (game music today), PCM s16 8 kHz, PCM s16 16 kHz, PCM u8 11.025 kHz (game
effects today), IMA ADPCM 8/16 kHz and Microsoft ADPCM 8 kHz (the Sony Ericsson guideline names
"WAV or ADPCM at 8 or 16 kHz" for mixing with MIDI). MIDI: SMF type 0 melody (3 s) and effect.
Each encoder is checked by decoding it again here (SNR printed)."""
import argparse, hashlib, json, math, struct
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'probe/res'


def signal(kind, rate):
    if kind == 'long':
        n = int(1.5 * rate)
        return [0.55 * math.sin(2 * math.pi * 330 * i / rate) * (0.8 + 0.2 * math.sin(2 * math.pi * 5 * i / rate))
                * min(1.0, i / (0.01 * rate), (n - i) / (0.02 * rate)) for i in range(n)]
    out = []
    for k in range(3):                       # 80 ms beep + 60 ms gap, 3 times
        on, off = int(0.08 * rate), int(0.06 * rate)
        out += [0.7 * math.sin(2 * math.pi * 1320 * i / rate) * min(1.0, i / (0.004 * rate), (on - i) / (0.004 * rate)) for i in range(on)]
        out += [0.0] * off
    return out


def riff(fmt, data, fact=None):
    chunks = b'fmt ' + struct.pack('<I', len(fmt)) + fmt
    if fact is not None:
        chunks += b'fact' + struct.pack('<II', 4, fact)
    chunks += b'data' + struct.pack('<I', len(data)) + data + (b'\0' if len(data) % 2 else b'')
    return b'RIFF' + struct.pack('<I', 4 + len(chunks)) + b'WAVE' + chunks


def s16(x): return max(-32768, min(32767, int(round(x * 32767))))


def pcm(samples, rate, bits):
    if bits == 8:
        data = bytes(max(0, min(255, int(round(x * 127)) + 128)) for x in samples)
    else:
        data = struct.pack('<%dh' % len(samples), *[s16(x) for x in samples])
    fmt = struct.pack('<HHIIHH', 1, 1, rate, rate * bits // 8, bits // 8, bits)
    return riff(fmt, data)


IMA_STEP = [7, 8, 9, 10, 11, 12, 13, 14, 16, 17, 19, 21, 23, 25, 28, 31, 34, 37, 41, 45, 50, 55, 60, 66, 73, 80, 88, 97,
            107, 118, 130, 143, 157, 173, 190, 209, 230, 253, 279, 307, 337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
            876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066, 2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428,
            4871, 5358, 5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899, 15289, 16818, 18500, 20350,
            22385, 24623, 27086, 29794, 32767]
IMA_INDEX = [-1, -1, -1, -1, 2, 4, 6, 8]


def ima_nibble(x, pred, index):
    step = IMA_STEP[index]; diff = x - pred; code = 0
    if diff < 0: code = 8; diff = -diff
    delta = step >> 3
    for bit, s in ((4, step), (2, step >> 1), (1, step >> 2)):
        if diff >= s: code |= bit; diff -= s; delta += s
    pred = pred - delta if code & 8 else pred + delta
    pred = max(-32768, min(32767, pred))
    index = max(0, min(88, index + IMA_INDEX[code & 7]))
    return code, pred, index


def ima(samples, rate, block=256):
    per = (block - 4) * 2 + 1
    x = [s16(v) for v in samples]
    x += [0] * ((-len(x)) % per)
    data, decoded, index = bytearray(), [], 0
    for b in range(0, len(x), per):
        pred = x[b]; data += struct.pack('<hBB', pred, index, 0); decoded.append(pred)
        codes = []
        for v in x[b + 1:b + per]:
            c, pred, index = ima_nibble(v, pred, index); codes.append(c); decoded.append(pred)
        data += bytes(codes[i] | (codes[i + 1] << 4) for i in range(0, len(codes), 2))
    fmt = struct.pack('<HHIIHHHH', 0x11, 1, rate, rate * block // per, block, 4, 2, per)
    return riff(fmt, bytes(data), len(x)), decoded, x


MS_COEF = [(256, 0), (512, -256), (0, 0), (192, 64), (240, 0), (460, -208), (392, -232)]
MS_ADAPT = [230, 230, 230, 230, 307, 409, 512, 614, 768, 614, 512, 409, 307, 230, 230, 230]


def msadpcm(samples, rate, block=256):
    """Microsoft ADPCM, mono, predictor 1 (coefficients 512/-256) for every block."""
    per = (block - 7) * 2 + 2
    x = [s16(v) for v in samples]
    x += [0] * ((-len(x)) % per)
    c1, c2 = MS_COEF[1]
    data, decoded = bytearray(), []
    for b in range(0, len(x), per):
        s2, s1 = x[b], x[b + 1]
        delta = max(16, abs(x[b + 2] - s1) if b + 2 < len(x) else 16)
        data += struct.pack('<Bhhh', 1, delta, s1, s2); decoded += [s2, s1]
        codes = []
        for v in x[b + 2:b + per]:
            pred = (s1 * c1 + s2 * c2) // 256
            n = int(round((v - pred) / delta)); n = max(-8, min(7, n))
            out = max(-32768, min(32767, pred + n * delta))
            codes.append(n & 15); decoded.append(out)
            s2, s1 = s1, out
            delta = max(16, MS_ADAPT[n & 15] * delta // 256)
        data += bytes((codes[i] << 4) | codes[i + 1] for i in range(0, len(codes), 2))
    extra = struct.pack('<HH', per, 7) + b''.join(struct.pack('<hh', a, c) for a, c in MS_COEF)
    fmt = struct.pack('<HHIIHHH', 2, 1, rate, rate * block // per, block, 4, len(extra)) + extra
    return riff(fmt, bytes(data), len(x)), decoded, x


def snr(ref, got):
    noise = sum((a - b) ** 2 for a, b in zip(ref, got)) or 1
    return 10 * math.log10(sum(a * a for a in ref) / noise)


def vlq(n):
    out = [n & 127]
    while n > 127:
        n >>= 7; out.insert(0, (n & 127) | 128)
    return bytes(out)


def smf(events, division=96):
    """events: (delta_ticks, bytes). Format 0, one track, end of track appended."""
    track = b''.join(vlq(d) + e for d, e in events) + b'\0\xff\x2f\0'
    return b'MThd' + struct.pack('>IHHH', 6, 0, 1, division) + b'MTrk' + struct.pack('>I', len(track)) + track


def melody():
    ev = [(0, b'\xff\x51\x03\x07\xa1\x20'), (0, bytes([0xC0, 80]))]   # 120 bpm, square lead
    for note in (72, 76, 79, 84, 79, 76, 72, 67):                      # 8 x 0.375 s = 3 s
        ev += [(0, bytes([0x90, note, 100])), (72, bytes([0x80, note, 0]))]
    return smf(ev)


def effect():
    # Channel 10 snare + a short high woodblock-like note on channel 1 (program 115), ~0.3 s.
    return smf([(0, bytes([0xC0, 115])), (0, bytes([0x99, 38, 120])), (0, bytes([0x90, 96, 110])),
                (29, bytes([0x80, 96, 0])), (29, bytes([0x89, 38, 0]))])


def build(out):
    out.mkdir(parents=True, exist_ok=True)
    report = {}
    formats = (('8k8', 8000, 'pcm8'), ('8k16', 8000, 'pcm16'), ('16k16', 16000, 'pcm16'), ('11k8', 11025, 'pcm8'),
               ('ima8k', 8000, 'ima'), ('ima16k', 16000, 'ima'), ('ms8k', 8000, 'ms'))
    for tag, rate, kind in formats:
        for name in ('long', 'beep'):
            s = signal(name, rate)
            if kind == 'pcm8': data = pcm(s, rate, 8); quality = None
            elif kind == 'pcm16': data = pcm(s, rate, 16); quality = None
            elif kind == 'ima': data, dec, ref = ima(s, rate); quality = snr(ref, dec)
            else: data, dec, ref = msadpcm(s, rate); quality = snr(ref, dec)
            if quality is not None and quality < 15:
                raise SystemExit('encoder check failed: %s %s SNR %.1f dB' % (tag, name, quality))
            path = out / ('%s_%s.wav' % (name, tag))
            path.write_bytes(data)
            report[path.name] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), rate=rate, encoding=kind,
                                     snr_db=None if quality is None else round(quality, 1))
    for name, data in (('melody.mid', melody()), ('fx.mid', effect())):
        (out / name).write_bytes(data)
        report[name] = dict(bytes=len(data), sha256=hashlib.sha256(data).hexdigest(), encoding='SMF type 0')
    (out / 'sounds.json').write_text(json.dumps(dict(generator='scripts/make_probe_sounds.py', origin='synthetic, written for this project',
                                                     files=report), indent=1, sort_keys=True) + '\n')
    return report


if __name__ == '__main__':
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--output', type=Path, default=OUT)
    r = build(p.parse_args().output)
    for k, v in sorted(r.items()):
        print('%-16s %6d B %s' % (k, v['bytes'], '' if v.get('snr_db') is None else 'SNR %.1f dB' % v['snr_db']))
