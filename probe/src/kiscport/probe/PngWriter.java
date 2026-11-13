package kiscport.probe;

import java.io.IOException;
import java.io.OutputStream;
import javax.microedition.lcdui.Image;

/** PNG (RGB, 8 bit) of a mutable Image, pure CLDC 1.1. Rows are read one at a time with getRGB, so memory is
 *  O(width) besides the image itself. Deflate uses stored blocks (no compression): ~230 KB for 240x320, simple
 *  and exact; any viewer opens it. Lengths are known in advance, so nothing is buffered. */
final class PngWriter {
    private static final int[] CRC = new int[256];
    static {
        for (int n = 0; n < 256; n++) {
            int c = n;
            for (int k = 0; k < 8; k++) c = (c & 1) != 0 ? 0xedb88320 ^ (c >>> 1) : c >>> 1;
            CRC[n] = c;
        }
    }
    private static final int BLOCK = 65535;

    private OutputStream out;
    private int crc, adlerA, adlerB, blockLeft, rawLeft;

    static void write(Image image, OutputStream out) throws IOException { new PngWriter().run(image, out); }

    private void run(Image image, OutputStream out) throws IOException {
        this.out = out;
        int w = image.getWidth(), h = image.getHeight(), row = 1 + 3 * w, raw = h * row;
        int blocks = (raw + BLOCK - 1) / BLOCK;
        out.write(new byte[] {(byte)0x89, 'P', 'N', 'G', '\r', '\n', 26, '\n'});
        chunk("IHDR", 13);
        int32(w); int32(h); byte8(8); byte8(2); byte8(0); byte8(0); byte8(0);   // 8 bit, RGB, no interlace
        end();
        chunk("IDAT", 2 + blocks * 5 + raw + 4);
        byte8(0x78); byte8(0x01);                                                 // zlib, no compression
        adlerA = 1; adlerB = 0; rawLeft = raw; blockLeft = 0;
        int[] argb = new int[w];
        byte[] line = new byte[row];
        for (int y = 0; y < h; y++) {
            image.getRGB(argb, 0, w, 0, y, w, 1);
            line[0] = 0;                                                          // filter: none
            for (int x = 0, k = 1; x < w; x++) {
                int p = argb[x];
                line[k++] = (byte)(p >> 16); line[k++] = (byte)(p >> 8); line[k++] = (byte)p;
            }
            data(line);
        }
        int32((adlerB << 16) | adlerA);
        end();
        chunk("IEND", 0);
        end();
        out.flush();
    }

    /** Raw scanline bytes into stored deflate blocks (header written when a block starts). */
    private void data(byte[] b) throws IOException {
        int at = 0;
        while (at < b.length) {
            if (blockLeft == 0) {
                int len = rawLeft < BLOCK ? rawLeft : BLOCK;
                byte8(len == rawLeft ? 1 : 0);                                     // BFINAL on the last block
                byte8(len & 0xff); byte8(len >> 8); byte8(~len & 0xff); byte8((~len >> 8) & 0xff);
                blockLeft = len;
            }
            int n = b.length - at < blockLeft ? b.length - at : blockLeft;
            out.write(b, at, n);
            for (int i = at; i < at + n; i++) {
                int v = b[i] & 0xff;
                crc = CRC[(crc ^ v) & 0xff] ^ (crc >>> 8);
                adlerA = (adlerA + v) % 65521; adlerB = (adlerB + adlerA) % 65521;
            }
            at += n; blockLeft -= n; rawLeft -= n;
        }
    }

    private void chunk(String type, int length) throws IOException {
        int32raw(length);
        crc = 0xffffffff;
        for (int i = 0; i < 4; i++) byte8(type.charAt(i));
    }

    private void end() throws IOException { int32raw(~crc); }

    private void byte8(int v) throws IOException {
        out.write(v);
        crc = CRC[(crc ^ v) & 0xff] ^ (crc >>> 8);
    }

    private void int32(int v) throws IOException { byte8(v >>> 24); byte8((v >>> 16) & 0xff); byte8((v >>> 8) & 0xff); byte8(v & 0xff); }

    private void int32raw(int v) throws IOException { out.write(v >>> 24); out.write((v >>> 16) & 0xff); out.write((v >>> 8) & 0xff); out.write(v & 0xff); }
}
