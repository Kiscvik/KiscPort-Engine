package kiscport.renderer;

import com.mascotcapsule.micro3d.v3.AffineTrans;
import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Texture;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Graphics;

/** Beta de Mascot para estudio futuro: not part of the production renderer (M3G).
 *  BETA: one KPS3 level chunk drawn with Mascot Capsule Micro3D v3 instead of M3G. It reads the same chunkN.kps as
 *  AssetSection and stage_N.bmp pages made by scripts/mascot.py texture. It does not use AssetSection or M3G, so it
 *  works on a phone without JSR-184. Load it by name, as the demo loads its scenes: it is, with MascotCubeScene,
 *  the only engine class linked to com.mascotcapsule (scripts/audit.py). Public methods use only ints and lcdui.
 *
 *  Use: loadPages() once per level, new MascotSection(n) per chunk, then every frame view(...), begin(g),
 *  draw() for every visible chunk, end(g). Mascot sorts polygons inside one begin/end (no depth buffer), so draw
 *  all chunks of a frame between the same begin and end.
 *
 *  Differences from AssetSection, by API limit: textures are not multiplied by the vertex colour (no Gouraud
 *  shading on textured triangles), transparency is a colour key (palette index 0 of a page that has one) instead
 *  of an alpha threshold, polygons are sorted instead of depth-tested, and both faces are drawn. No visibility
 *  intervals and no step-by-step loading yet: the constructor reads the whole chunk. Memory: renderPrimitives
 *  wants every triangle's vertices unshared, BYTES_PER_TRIANGLE bytes each (scripts/mascot.py check prints the
 *  total). Positions are KPS3's raw values; view() takes the camera in that space, in Mascot's axes. */
public final class MascotSection {
    /** Resource folder of the level (same meaning as AssetSection.ROOT; ends with "/"). */
    public static String ROOT = "/kiscport/level/";
    /** Primitives per renderPrimitives call allowed by the API. */
    static final int BATCH = 255;
    /** int arrays per triangle: 9 coordinates, 6 texel coordinates, 1 face colour. */
    public static final int BYTES_PER_TRIANGLE = (9 + 6 + 1) * 4;

    private static Graphics3D g3d;
    private static FigureLayout layout;
    private static AffineTrans trans;
    private static Effect3D effect;
    private static final int[] normals = new int[BATCH * 3 * 3];
    private static Texture[] pages;
    private static int[] pageWidth, pageHeight;
    private static boolean[] keyed;

    private final int[] count, command;
    private final int[][] vertices, coords, colours;
    private final Texture[] texture;
    private final int triangles;

    private static void setUp() {
        if (g3d != null) return;
        g3d = new Graphics3D(); layout = new FigureLayout(); trans = new AffineTrans(); effect = new Effect3D();
        trans.m00 = trans.m11 = trans.m22 = 4096;
        layout.setAffineTrans(trans);
    }

    /** Reads stage_0.bmp, stage_1.bmp... (at most 4) from ROOT. A page whose palette entry 0 is magenta FF00FF has a
     *  colour key (scripts/mascot.py writes it so only for pages with transparent pixels). Returns the page count. */
    public static int loadPages() throws IOException {
        setUp();
        int n = 0;
        while (n < 4 && MascotSection.class.getResourceAsStream(ROOT + "stage_" + n + ".bmp") != null) n++;
        if (n == 0) throw new IOException("Recursos del nivel no incluidos");
        Texture[] loaded = new Texture[n];
        int[] w = new int[n], h = new int[n];
        boolean[] key = new boolean[n];
        for (int i = 0; i < n; i++) {
            byte[] bmp = read(ROOT + "stage_" + i + ".bmp");
            if (bmp.length < 54 + 1024 || bmp[0] != 'B' || bmp[1] != 'M' || bmp[28] != 8) throw new IOException("Pagina BMP invalida");
            w[i] = le(bmp, 18); h[i] = le(bmp, 22); if (h[i] < 0) h[i] = -h[i];
            if (w[i] < 1 || w[i] > 256 || h[i] < 1 || h[i] > 256) throw new IOException("Pagina fuera de presupuesto");
            key[i] = (bmp[54] & 0xff) == 0xff && bmp[55] == 0 && (bmp[56] & 0xff) == 0xff;
            loaded[i] = new Texture(bmp, true);
        }
        pages = loaded; pageWidth = w; pageHeight = h; keyed = key;
        return n;
    }

    /** Drops the pages (before loading another level). */
    public static void releasePages() { pages = null; pageWidth = pageHeight = null; keyed = null; }

    /** Camera for the next frame. m: 12 values of an AffineTrans (rows m00..m03, m10..m13, m20..m23, 4096 = 1.0)
     *  taking KPS3 positions to Mascot's camera space (looking down +z, y down the screen); from an M3G camera, that
     *  is its inverse with rows 1 and 2 negated, as MascotCubeScene does. angle: field angle, 4096 = 360 degrees. */
    public static void view(int[] m, int near, int far, int angle, int centerX, int centerY) {
        setUp();
        trans.m00 = m[0]; trans.m01 = m[1]; trans.m02 = m[2]; trans.m03 = m[3];
        trans.m10 = m[4]; trans.m11 = m[5]; trans.m12 = m[6]; trans.m13 = m[7];
        trans.m20 = m[8]; trans.m21 = m[9]; trans.m22 = m[10]; trans.m23 = m[11];
        layout.setAffineTrans(trans);
        layout.setPerspective(near, far, angle);
        layout.setCenter(centerX, centerY);
    }

    /** Field angle across the width for a vertical field of view in degrees (as MascotCubeScene.angle). */
    public static int angle(int width, int height, int verticalDegrees) {
        double target = (double)width / height * Math.tan(verticalDegrees * Math.PI / 360);
        int lo = 1, hi = 2047;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (Math.tan(mid * Math.PI / 4096) < target) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    public static void begin(Graphics g) { setUp(); g3d.bind(g); }

    public static void end(Graphics g) {
        try { g3d.flush(); } finally { g3d.release(g); }
    }

    /** Reads ROOT + "chunk" + chunk + ".kps" (KPS3, as AssetSection) and expands it for renderPrimitives. */
    public MascotSection(int chunk) throws IOException {
        if (pages == null) throw new IOException("Paginas del nivel no cargadas");
        byte[] d = read(ROOT + "chunk" + chunk + ".kps");
        if (d.length < 10 || be(d, 0) != 0x4b505333 || u2(d, 4) != 3) throw new IOException("Formato de tramo invalido");
        int n = u2(d, 6), parts = u2(d, 8);
        if (n < 3 || n > 8192 || parts < 1 || parts > pages.length) throw new IOException("Limites de tramo invalidos");
        int positions = 10, uvs = positions + n * 6, rgb = uvs + n * 4, at = rgb + n * 3;
        // First pass: how many calls (each part split into calls of at most BATCH triangles).
        int calls = 0, total = 0;
        for (int p = 0, q = at; p < parts; p++) {
            if (q + 4 > d.length) throw new IOException("Limites de tramo invalidos");
            int page = u2(d, q), tris = u2(d, q + 2);
            if (page >= pages.length || tris < 1 || tris > 4096) throw new IOException("Limites de tramo invalidos");
            calls += (tris + BATCH - 1) / BATCH; total += tris; q += 4 + tris * 6;
        }
        count = new int[calls]; command = new int[calls]; texture = new Texture[calls];
        vertices = new int[calls][]; coords = new int[calls][]; colours = new int[calls][];
        int call = 0;
        for (int p = 0; p < parts; p++) {
            int page = u2(d, at), tris = u2(d, at + 2), w = pageWidth[page], h = pageHeight[page];
            at += 4;
            int mode = Graphics3D.PRIMITVE_TRIANGLES | Graphics3D.PDATA_TEXURE_COORD | (keyed[page] ? Graphics3D.PATTR_COLORKEY : 0);
            for (int done = 0; done < tris; call++) {
                int k = tris - done < BATCH ? tris - done : BATCH;
                int[] v = new int[k * 9], uv = new int[k * 6], c = new int[k];
                for (int t = 0; t < k; t++) {
                    int r = 0, g = 0, b = 0;
                    for (int corner = 0; corner < 3; corner++) {
                        int index = u2(d, at); at += 2;
                        if (index >= n) throw new IOException("Indice de escena invalido");
                        int pos = positions + index * 6, tex = uvs + index * 4, col = rgb + index * 3;
                        v[t * 9 + corner * 3] = (short)u2(d, pos); v[t * 9 + corner * 3 + 1] = (short)u2(d, pos + 2);
                        v[t * 9 + corner * 3 + 2] = (short)u2(d, pos + 4);
                        // KPS3 texel coordinates are in 1/256 of the page (AssetSection scales them by 1/256).
                        uv[t * 6 + corner * 2] = clamp((short)u2(d, tex) * w / 256, w);
                        uv[t * 6 + corner * 2 + 1] = clamp((short)u2(d, tex + 2) * h / 256, h);
                        r += d[col] & 0xff; g += d[col + 1] & 0xff; b += d[col + 2] & 0xff;
                    }
                    c[t] = (r / 3) << 16 | (g / 3) << 8 | b / 3;
                }
                count[call] = k; command[call] = mode; texture[call] = pages[page];
                vertices[call] = v; coords[call] = uv; colours[call] = c;
                done += k;
            }
        }
        if (at != d.length) throw new IOException("Datos adicionales de escena");
        triangles = total;
    }

    /** Sends the chunk; call between begin and end. */
    public void draw() {
        for (int i = 0; i < count.length; i++)
            g3d.renderPrimitives(texture[i], 0, 0, layout, effect, command[i], count[i], vertices[i], normals, coords[i], colours[i]);
    }

    public int triangles() { return triangles; }
    public int calls() { return count.length; }
    public int bytes() { return triangles * BYTES_PER_TRIANGLE; }

    private static int clamp(int v, int size) { return v < 0 ? 0 : v >= size ? size - 1 : v; }
    private static int u2(byte[] d, int at) { return (d[at] & 0xff) << 8 | (d[at + 1] & 0xff); }
    private static int be(byte[] d, int at) { return u2(d, at) << 16 | u2(d, at + 2); }
    private static int le(byte[] d, int at) { return (d[at] & 0xff) | (d[at + 1] & 0xff) << 8 | (d[at + 2] & 0xff) << 16 | d[at + 3] << 24; }

    private static byte[] read(String name) throws IOException {
        InputStream in = MascotSection.class.getResourceAsStream(name);
        if (in == null) throw new IOException("Recursos del nivel no incluidos");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] block = new byte[2048];
            for (int n; (n = in.read(block)) > 0; ) out.write(block, 0, n);
            return out.toByteArray();
        } finally { in.close(); }
    }
}
