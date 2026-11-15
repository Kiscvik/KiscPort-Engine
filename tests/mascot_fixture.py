#!/usr/bin/env python3
"""Synthetic level for the Mascot beta tests (test code only, not an importer): stage_0.png and two KPS3 chunks.

stage_0.png, 128 x 64, asymmetric so that a flip or a wrong colour key shows:
  left half   gradient with thousands of colours (forces palette reduction)
  right half  blue, with a red 8 x 8 mark in its top-left corner and a 16 x 16 transparent hole bottom-right
  row 0..1 of the right half, x 112..119: alpha 64 (partial alpha, counted as a loss)
chunk0.kps  one quad over the whole page (white vertices) and one quad with a red vertex (2 + 2 triangles)
chunk1.kps  a 20 x 20 grid (800 triangles), white, page 0
"""
import struct, sys, zlib
from pathlib import Path

W, H = 128, 64


def pixel(x, y):
    if x < 64:
        return ((x * 4) & 255, (y * 4) & 255, (x * y) & 0x1f << 3, 255)
    if 64 <= x < 72 and y < 8:
        return (230, 30, 30, 255)
    if x >= 112 and y >= 48:
        return (0, 0, 0, 0)
    if 112 <= x < 120 and y < 2:
        return (40, 60, 220, 64)
    return (40, 60, 220, 255)


def png(path, w=W, h=H, colour=pixel):
    rows = b''.join(b'\x00' + bytes(c for x in range(w) for c in colour(x, y)) for y in range(h))
    def chunk(kind, data):
        return struct.pack('>I', len(data)) + kind + data + struct.pack('>I', zlib.crc32(kind + data) & 0xffffffff)
    path.write_bytes(b'\x89PNG\r\n\x1a\n' + chunk(b'IHDR', struct.pack('>IIBBBBB', w, h, 8, 6, 0, 0, 0))
                     + chunk(b'IDAT', zlib.compress(rows, 9)) + chunk(b'IEND', b''))


def kps3(path, vertices, parts):
    """vertices: (x, y, z, u, v, (r, g, b)) with u, v in 1/256 of the page; parts: (page, [(a, b, c), ...])."""
    out = struct.pack('>IHHH', 0x4b505333, 3, len(vertices), len(parts))
    out += b''.join(struct.pack('>hhh', *v[:3]) for v in vertices)
    out += b''.join(struct.pack('>hh', v[3], v[4]) for v in vertices)
    out += b''.join(bytes(v[5]) for v in vertices)
    for page, triangles in parts:
        out += struct.pack('>HH', page, len(triangles)) + b''.join(struct.pack('>HHH', *t) for t in triangles)
    path.write_bytes(out)


def build(folder):
    folder = Path(folder); folder.mkdir(parents=True, exist_ok=True)
    png(folder/'stage_0.png')
    white, red = (255, 255, 255), (255, 0, 0)
    quad = [(-500, -250, 0, 0, 0, white), (500, -250, 0, 255, 0, white), (-500, 250, 0, 0, 255, white), (500, 250, 0, 255, 255, white)]
    tinted = [(x, y + 600, z, u, v, red if i == 0 else white) for i, (x, y, z, u, v, _) in enumerate(quad)]
    kps3(folder/'chunk0.kps', quad + tinted, [(0, [(0, 1, 2), (2, 1, 3), (4, 5, 6), (6, 5, 7)])])
    grid, triangles = [], []
    for j in range(21):
        for i in range(21):
            grid.append((i * 50 - 500, j * 50 - 500, 0, i * 255 // 20, j * 255 // 20, white))
    for j in range(20):
        for i in range(20):
            a = j * 21 + i
            triangles += [(a, a + 1, a + 21), (a + 21, a + 1, a + 22)]
    kps3(folder/'chunk1.kps', grid, [(0, triangles)])
    return folder


if __name__ == '__main__':
    print(build(sys.argv[1]))
