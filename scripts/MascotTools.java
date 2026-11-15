import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.BufferedInputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;

/** BETA, PC only (pinned host JDK; never compiled into a MIDlet). Tools for Mascot Capsule Micro3D v3:
 *
 *  texture IN.png OUT.bmp   PNG -> 8-bit indexed BMP (the layout KiscPort Probe's MascotBench used on the W995 and
 *                           the W900i: BITMAPINFOHEADER, 256-entry palette, bottom-up, rows padded to 4 bytes),
 *                           with a report of what is lost.
 *  check DIR                Reads a KiscPort level folder (chunkN.kps in KPS3, stage_N.png) and says whether it fits
 *                           Mascot, with which losses, and what goes beyond what was measured on the phones.
 *
 *  Transparency: like M3G's alpha threshold 0.5 in AssetSection, alpha < 128 is transparent. A page with transparent
 *  pixels gets a colour key: palette entry 0 is magenta FF00FF and no opaque pixel uses it (255 colours left).
 *  A page without transparency never has magenta at entry 0, so the renderer can tell them apart. The colour key
 *  (palette index 0 with PATTR_COLORKEY) is how the API is documented and how FreeJ2ME-Plus implements it; it has
 *  not been run on a phone yet.
 *
 *  Measured on phones (Probe 1.3.0, owner's reports): at most 3072 triangles per frame (768 already ~29 fps on
 *  the W900i), coordinates within about +-2600 at z ~2200 with far 10000, square textures 16-256 px on the W995 and
 *  only 16 and 32 px on the W900i. Anything beyond is reported as "fuera de lo medido", not as an error. */
public final class MascotTools {
    static final int KEY = 0xff00ff, MAX_SIDE = 256, BATCH = 255;
    static final int MEASURED_TRIS = 3072, W900I_TRIS = 768, MEASURED_COORD = 2600;
    /** Bytes per triangle once expanded for renderPrimitives (int arrays: 9 coordinates, 6 texel coordinates,
     *  1 face colour), as kiscport.renderer.MascotSection holds them. */
    static final int MASCOT_BYTES_PER_TRI = (9 + 6 + 1) * 4;

    public static void main(String[] args) throws Exception {
        if (args.length == 3 && args[0].equals("texture")) System.exit(texture(new File(args[1]), new File(args[2])));
        else if (args.length == 2 && args[0].equals("check")) System.exit(check(new File(args[1])));
        System.err.println("usage: texture IN.png OUT.bmp | check DIR");
        System.exit(2);
    }

    // ------------------------------------------------------------------ texture

    /** Result of converting one image: indices (or null if it does not fit), palette and the numbers to report. */
    static final class Converted {
        int width, height, sourceColours, paletteSize, transparent, partial, maxError;
        double rms;
        boolean keyed;
        int[] palette;
        byte[] indices;
        String refusal;
    }

    static BufferedImage read(File png) throws IOException {
        BufferedImage image = ImageIO.read(png);
        if (image == null) throw new IOException("not a readable image: " + png);
        return image;
    }

    static Converted convert(BufferedImage image) {
        Converted c = new Converted();
        int w = c.width = image.getWidth(), h = c.height = image.getHeight();
        if (w > MAX_SIDE || h > MAX_SIDE) { c.refusal = w + "x" + h + " px: Mascot v3 admite hasta " + MAX_SIDE + " px por lado"; return c; }
        int[] argb = image.getRGB(0, 0, w, h, null, 0, w);
        Map<Integer, int[]> histogram = new HashMap<Integer, int[]>();
        for (int p : argb) {
            int a = p >>> 24;
            if (a > 0 && a < 255) c.partial++;
            if (a < 128) { c.transparent++; continue; }
            int[] n = histogram.get(p & 0xffffff);
            if (n == null) histogram.put(p & 0xffffff, n = new int[1]);
            n[0]++;
        }
        c.keyed = c.transparent > 0;
        c.sourceColours = histogram.size();
        int cap = c.keyed ? 255 : 256;
        int[][] entries = new int[histogram.size()][];
        int k = 0;
        for (Map.Entry<Integer, int[]> e : histogram.entrySet()) entries[k++] = new int[] {e.getKey(), e.getValue()[0]};
        Arrays.sort(entries, new Comparator<int[]>() { public int compare(int[] x, int[] y) { return x[0] - y[0]; } });
        int[] colours = entries.length <= cap ? exact(entries) : medianCut(entries, cap);
        List<Integer> palette = new ArrayList<Integer>();
        if (c.keyed) palette.add(KEY);
        for (int col : colours) palette.add(col);
        if (palette.isEmpty()) palette.add(0);
        if (!c.keyed && palette.get(0) == KEY) {
            // Entry 0 magenta means "colour key"; a page without transparency must not look like one.
            if (palette.size() > 1) { palette.set(0, palette.get(1)); palette.set(1, KEY); }
            else palette.add(0, 0x000000);
        }
        c.palette = new int[palette.size()];
        for (int i = 0; i < c.palette.length; i++) c.palette[i] = palette.get(i);
        c.paletteSize = c.palette.length;
        c.indices = new byte[w * h];
        Map<Integer, Integer> nearest = new HashMap<Integer, Integer>();
        long squares = 0, opaque = 0;
        int first = c.keyed ? 1 : 0;
        for (int i = 0; i < argb.length; i++) {
            int p = argb[i];
            if ((p >>> 24) < 128) { c.indices[i] = 0; continue; }
            int rgb = p & 0xffffff;
            Integer at = nearest.get(rgb);
            if (at == null) nearest.put(rgb, at = nearest(c.palette, first, rgb));
            c.indices[i] = (byte)(int)at;
            int q = c.palette[at];
            for (int s = 0; s <= 16; s += 8) {
                int d = ((rgb >> s) & 255) - ((q >> s) & 255);
                squares += d * d;
                if (Math.abs(d) > c.maxError) c.maxError = Math.abs(d);
            }
            opaque++;
        }
        c.rms = opaque == 0 ? 0 : Math.sqrt((double)squares / (3 * opaque));
        return c;
    }

    static int[] exact(int[][] entries) {
        int[] out = new int[entries.length];
        for (int i = 0; i < out.length; i++) out[i] = entries[i][0];
        return out;
    }

    /** Deterministic median cut over the colour histogram: split the box with the widest channel range (ties: more
     *  pixels, then lower index) at its pixel-weighted median; each box becomes its weighted mean. */
    static int[] medianCut(int[][] entries, int cap) {
        List<int[][]> boxes = new ArrayList<int[][]>();
        boxes.add(entries);
        while (boxes.size() < cap) {
            int best = -1, bestRange = 0, bestCount = 0, bestChannel = 0;
            for (int b = 0; b < boxes.size(); b++) {
                int[][] box = boxes.get(b);
                if (box.length < 2) continue;
                int count = 0;
                for (int[] e : box) count += e[1];
                for (int s = 16; s >= 0; s -= 8) {
                    int lo = 255, hi = 0;
                    for (int[] e : box) { int v = (e[0] >> s) & 255; if (v < lo) lo = v; if (v > hi) hi = v; }
                    int range = hi - lo;
                    if (range > bestRange || (range == bestRange && range > 0 && count > bestCount)) {
                        best = b; bestRange = range; bestCount = count; bestChannel = s;
                    }
                }
            }
            if (best < 0) break;
            final int s = bestChannel;
            int[][] box = boxes.get(best).clone();
            Arrays.sort(box, new Comparator<int[]>() {
                public int compare(int[] x, int[] y) {
                    int d = ((x[0] >> s) & 255) - ((y[0] >> s) & 255);
                    return d != 0 ? d : x[0] - y[0];
                }
            });
            long total = 0;
            for (int[] e : box) total += e[1];
            long run = 0;
            int cut = 1;
            for (int i = 0; i < box.length - 1; i++) { run += box[i][1]; cut = i + 1; if (run * 2 >= total) break; }
            boxes.set(best, Arrays.copyOfRange(box, 0, cut));
            boxes.add(best + 1, Arrays.copyOfRange(box, cut, box.length));
        }
        int[] out = new int[boxes.size()];
        for (int b = 0; b < out.length; b++) {
            long r = 0, g = 0, bl = 0, n = 0;
            for (int[] e : boxes.get(b)) {
                r += (long)((e[0] >> 16) & 255) * e[1]; g += (long)((e[0] >> 8) & 255) * e[1]; bl += (long)(e[0] & 255) * e[1]; n += e[1];
            }
            out[b] = (int)((r + n / 2) / n) << 16 | (int)((g + n / 2) / n) << 8 | (int)((bl + n / 2) / n);
        }
        return out;
    }

    static int nearest(int[] palette, int first, int rgb) {
        int best = first, bestD = Integer.MAX_VALUE;
        for (int i = first; i < palette.length; i++) {
            int q = palette[i], d = 0;
            for (int s = 0; s <= 16; s += 8) { int x = ((rgb >> s) & 255) - ((q >> s) & 255); d += x * x; }
            if (d < bestD) { bestD = d; best = i; }
        }
        return best;
    }

    /** Same layout as Probe's MascotBench.bmp(): 14 + 40 header bytes, 256 palette entries (B G R 0), bottom-up. */
    static byte[] bmp(Converted c) {
        int w = c.width, h = c.height, stride = (w + 3) & ~3, offset = 14 + 40 + 256 * 4, size = offset + stride * h;
        byte[] d = new byte[size];
        d[0] = 'B'; d[1] = 'M'; le(d, 2, size); le(d, 10, offset);
        le(d, 14, 40); le(d, 18, w); le(d, 22, h); d[26] = 1; d[28] = 8; le(d, 34, stride * h); le(d, 46, 256);
        for (int i = 0; i < c.palette.length; i++) {
            int p = c.palette[i], at = 54 + i * 4;
            d[at] = (byte)p; d[at + 1] = (byte)(p >> 8); d[at + 2] = (byte)(p >> 16);
        }
        for (int y = 0; y < h; y++) System.arraycopy(c.indices, y * w, d, offset + (h - 1 - y) * stride, w);
        return d;
    }

    static void le(byte[] d, int at, int v) {
        d[at] = (byte)v; d[at + 1] = (byte)(v >> 8); d[at + 2] = (byte)(v >> 16); d[at + 3] = (byte)(v >> 24);
    }

    /** Report lines for one image; the first line says CABE, CABE CON PERDIDAS or NO CABE. */
    static List<String> report(String name, Converted c) {
        List<String> out = new ArrayList<String>();
        if (c.refusal != null) { out.add(name + ": NO CABE (" + c.refusal + ")"); return out; }
        List<String> losses = new ArrayList<String>(), beyond = new ArrayList<String>();
        if (c.rms > 0) losses.add(c.sourceColours + " colores reducidos a " + (c.keyed ? c.paletteSize - 1 : c.paletteSize)
            + String.format(java.util.Locale.ROOT, " (error RMS %.2f, maximo %d por canal)", c.rms, c.maxError));
        if (c.partial > 0) losses.add(c.partial + " px con alfa parcial pasan a opacos o recortados (umbral 128, como M3G)");
        if (c.width != c.height || Integer.bitCount(c.width) != 1 || c.width < 16)
            beyond.add(c.width + "x" + c.height + " no es cuadrada potencia de dos de 16 a 256 (lo medido)");
        else if (c.width > 32) beyond.add(c.width + " px: el W900i solo se midio con 16 y 32 px");
        out.add(name + ": " + (losses.isEmpty() ? "CABE" : "CABE CON PERDIDAS") + ", " + c.width + "x" + c.height + ", "
            + (c.keyed ? (c.paletteSize - 1) + " colores + colour key (" + c.transparent + " px transparentes)" : c.paletteSize + " colores")
            + ", BMP " + (14 + 40 + 1024 + ((c.width + 3) & ~3) * c.height) + " bytes");
        for (String l : losses) out.add("  perdida: " + l);
        for (String b : beyond) out.add("  fuera de lo medido: " + b);
        if (c.keyed) out.add("  colour key: indice 0 = FF00FF; sin probar en telefono");
        return out;
    }

    static int texture(File in, File out) throws IOException {
        Converted c = convert(read(in));
        for (String line : report(in.getName(), c)) System.out.println(line);
        if (c.refusal != null) return 1;
        FileOutputStream f = new FileOutputStream(out);
        try { f.write(bmp(c)); } finally { f.close(); }
        System.out.println("escrito " + out.getName());
        return 0;
    }

    // ------------------------------------------------------------------ check

    /** One KPS3 chunk, read exactly as AssetSection reads it. */
    static final class Chunk {
        int vertices, triangles, calls, maxCoord, gouraud, modulated, badUV;
        int[] perPage;
    }

    static Chunk readKps3(File file, int[][] pageSizes) throws IOException {
        DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(file)));
        try {
            if (in.readInt() != 0x4b505333 || in.readUnsignedShort() != 3) throw new IOException("no es KPS3");
            Chunk c = new Chunk();
            int count = c.vertices = in.readUnsignedShort(), parts = in.readUnsignedShort();
            if (count < 3 || count > 8192 || parts < 1) throw new IOException("limites de tramo invalidos");
            short[] uv = new short[count * 2];
            int[] rgb = new int[count];
            for (int i = 0; i < count * 3; i++) c.maxCoord = Math.max(c.maxCoord, Math.abs(in.readShort()));
            for (int i = 0; i < count * 2; i++) uv[i] = in.readShort();
            for (int i = 0; i < count; i++) rgb[i] = in.readUnsignedByte() << 16 | in.readUnsignedByte() << 8 | in.readUnsignedByte();
            c.perPage = new int[pageSizes.length];
            for (int p = 0; p < parts; p++) {
                int page = in.readUnsignedShort(), n = in.readUnsignedShort();
                if (page >= pageSizes.length) throw new IOException("usa la pagina " + page + " y hay " + pageSizes.length);
                if (n < 1 || n > 4096) throw new IOException("limites de tramo invalidos");
                c.perPage[page] += n; c.triangles += n; c.calls += (n + BATCH - 1) / BATCH;
                for (int t = 0; t < n; t++) {
                    int a = in.readUnsignedShort(), b = in.readUnsignedShort(), d = in.readUnsignedShort();
                    if (a >= count || b >= count || d >= count) throw new IOException("indice invalido");
                    if (rgb[a] != rgb[b] || rgb[a] != rgb[d]) c.gouraud++;
                    if (rgb[a] != 0xffffff || rgb[b] != 0xffffff || rgb[d] != 0xffffff) c.modulated++;
                    // KPS3 texel coordinates are in 1/256 of the page (AssetSection: scale 1/256 for U and V).
                    for (int v : new int[] {a, b, d}) {
                        int u = uv[v * 2] * pageSizes[page][0] / 256, w = uv[v * 2 + 1] * pageSizes[page][1] / 256;
                        if (u < 0 || w < 0 || u >= pageSizes[page][0] || w >= pageSizes[page][1]) { c.badUV++; break; }
                    }
                }
            }
            if (in.read() != -1) throw new IOException("datos adicionales");
            return c;
        } catch (EOFException e) {
            throw new IOException("archivo truncado");
        } finally { in.close(); }
    }

    static String size(long bytes) { return bytes < 10240 ? bytes + " bytes" : bytes / 1024 + " KB"; }

    static int check(File dir) throws IOException {
        System.out.println("MASCOT CHECK (beta) " + dir.getName() + ": limites de la API y lo medido en W995/W900i (Probe 1.3.0)");
        boolean refused = false, lossy = false;
        List<int[]> sizes = new ArrayList<int[]>();
        for (int n = 0; ; n++) {
            File png = new File(dir, "stage_" + n + ".png");
            if (!png.isFile()) break;
            BufferedImage image = read(png);
            sizes.add(new int[] {image.getWidth(), image.getHeight()});
            Converted c = convert(image);
            List<String> lines = report(png.getName(), c);
            refused |= c.refusal != null; lossy |= lines.get(0).contains("PERDIDAS");
            for (String l : lines) System.out.println(l);
        }
        if (sizes.isEmpty()) { System.out.println("NO CABE: no hay paginas stage_N.png"); return 1; }
        File[] files = dir.listFiles();
        List<Integer> chunks = new ArrayList<Integer>();
        for (File f : files) {
            String name = f.getName();
            if (name.startsWith("chunk") && name.endsWith(".kps")) {
                try { chunks.add(Integer.parseInt(name.substring(5, name.length() - 4))); } catch (NumberFormatException e) { }
            }
        }
        java.util.Collections.sort(chunks);
        int[][] pages = sizes.toArray(new int[0][]);
        int most = 0;
        long bytes = 0;
        for (int number : chunks) {
            String name = "chunk" + number + ".kps";
            Chunk c;
            try { c = readKps3(new File(dir, name), pages); }
            catch (IOException e) { System.out.println(name + ": NO CABE (" + e.getMessage() + ")"); refused = true; continue; }
            List<String> losses = new ArrayList<String>(), beyond = new ArrayList<String>();
            if (c.modulated > 0) losses.add(c.modulated + " tri con color de vertice distinto de blanco: Mascot no multiplica textura por color");
            if (c.gouraud > 0) losses.add(c.gouraud + " tri con colores distintos en sus vertices (Gouraud)");
            if (c.badUV > 0) losses.add(c.badUV + " tri con coordenadas de textura fuera de la pagina (se recortan)");
            if (c.triangles > MEASURED_TRIS) beyond.add(c.triangles + " tri en un tramo: mas de los " + MEASURED_TRIS + " medidos");
            else if (c.triangles > W900I_TRIS) beyond.add(c.triangles + " tri: en el W900i 768 ya daban ~29 fps");
            if (c.maxCoord > MEASURED_COORD) beyond.add("coordenadas hasta +-" + c.maxCoord + ": lo medido llega a +-" + MEASURED_COORD);
            lossy |= !losses.isEmpty();
            int mascot = c.triangles * MASCOT_BYTES_PER_TRI;
            bytes += mascot; most = Math.max(most, c.triangles);
            System.out.println(name + ": " + (losses.isEmpty() ? "CABE" : "CABE CON PERDIDAS") + ", " + c.triangles + " tri en "
                + c.calls + " llamadas, " + size(mascot) + " expandido para Mascot");
            for (String l : losses) System.out.println("  perdida: " + l);
            for (String b : beyond) System.out.println("  fuera de lo medido: " + b);
        }
        System.out.println("Sin bufer de profundidad: Mascot ordena por poligono; el orden no se comprueba aqui.");
        System.out.println("Mascot expandido: " + size(bytes) + " para " + chunks.size() + " tramos (" + MASCOT_BYTES_PER_TRI
            + " bytes por triangulo); el tramo mayor tiene " + most + " tri.");
        System.out.println("VEREDICTO: " + (refused ? "NO CABE" : lossy ? "CABE CON PERDIDAS" : "CABE")
            + " (beta: comprobado contra el emulador, no contra un telefono)");
        return refused ? 1 : 0;
    }
}
