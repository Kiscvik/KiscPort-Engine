#!/usr/bin/env python3
"""KTT1 text tables (CLDC TextTable): UTF-16 BE cards, 1 to 32 cards of at most 256 code units each."""
import argparse
import json
import struct
from pathlib import Path


def encode(cards):
    if not isinstance(cards, list) or not 1 <= len(cards) <= 32 or any(not isinstance(x, str) for x in cards):
        raise ValueError('Expected a list of 1 to 32 strings')
    data = bytearray(b'KTT1' + struct.pack('>H', len(cards)))
    for card in cards:
        encoded = card.encode('utf-16-be')
        length = len(encoded) // 2
        if length > 256:
            raise ValueError('Text card exceeds 256 UTF-16 code units')
        data.extend(struct.pack('>H', length))
        data.extend(encoded)
    return bytes(data)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('source', type=Path, help='UTF-8 JSON array of strings')
    parser.add_argument('output', type=Path, help='.ktt file to write')
    args = parser.parse_args()
    data = encode(json.loads(args.source.read_text(encoding='utf-8')))
    args.output.write_bytes(data)
    print('KTT1:', args.output, len(data), 'bytes')


if __name__ == '__main__':
    main()
