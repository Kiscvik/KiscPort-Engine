package kiscport.probe;

import java.util.Vector;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import com.mascotcapsule.micro3d.v3.AffineTrans;
import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import com.mascotcapsule.micro3d.v3.Texture;

/** Synthetic Mascot Capsule Micro3D v3 benchmark (proprietary API, loaded by name): the same grids, timing,
 *  framing and steps as M3GBench, so both results can be read side by side. Generated grids, checker
 *  textures and full-screen quads only (no game content).
 *  Differences that the numbers carry (stated in PROBE.md): renderPrimitives sends unshared vertices, at most
 *  255 primitives per call, one colour per face (M3G: per vertex, smooth), 8-bit palette BMP textures and no
 *  depth buffer (the API sorts polygons). Only the core calls are used: bind(Graphics), renderPrimitives,
 *  flush, release(Graphics), FigureLayout setAffineTrans/setCenter/setPerspective, Effect3D(), Texture(byte[],
 *  true) and the public fields of AffineTrans; the turn uses its own table, not Util3D. */
final class MascotBench extends GameCanvas implements Bench, Runnable {
    static final int WARMUP_MS = 500, MEASURE_MS = 2000, QUICK_MS = 150, STOP_MS = 500;
    /** Same grids as M3GBench (copied: reading them from M3GBench would load a class that needs M3G). */
    static final int[][] GRIDS = {{2, 3}, {6, 8}, {8, 12}, {12, 16}, {16, 24}, {24, 32}, {32, 48}};
    /** Textures up to 256 px: the largest a Micro3D BMP texture is documented to take. */
    static final int[] TEXTURES = {16, 32, 64, 128, 256};
    /** Primitives per renderPrimitives call allowed by the API. */
    static final int BATCH = 255;
    /** Model units (M3G 0.001 = 1 here) and 4096 = 1.0 in AffineTrans. The camera of M3GBench sits at 2.2 units. */
    static final int DISTANCE = 2200, NEAR = 100, FAR = 10000, ONE = 4096;

    private ProbeCanvas owner;
    private Display display;
    private int test;
    private boolean quick;
    private final Vector out = new Vector();
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private Graphics3D g3d;
    private FigureLayout layout;
    private AffineTrans trans;
    private Effect3D effect;
    /** Rotation about Y for frame f % 40 (-20..19 degrees, as M3GBench), x 4096. */
    private final int[] cos = new int[40], sin = new int[40];
    private volatile String status = "";
    private volatile boolean stop, captureRequest;
    private int step, steps;
    private String captured;
    private long capturedAt;
    private int extentX = 800, extentY = 1000;
    private boolean still;
    /** Shared normals argument: the commands send none, but an implementation may not accept null. */
    private final int[] noNormals = new int[BATCH * 4 * 3];

    MascotBench() { super(false); setFullScreenMode(true); }

    public void start(ProbeCanvas owner, Display display, int test, boolean quick) {
        this.owner = owner; this.display = display; this.test = test; this.quick = quick;
        display.setCurrent(this);
        new Thread(this).start();
    }

    /** 0 saves a capture of the next frame, * stops the run (the current step ends, the rest are skipped). Any other
     *  key does nothing, so a key can be pressed to keep the phone's screen from turning off during a long run. */
    protected void keyPressed(int code) {
        if (code == KEY_NUM0) captureRequest = true;
        else if (code == KEY_STAR) stop = true;
    }

    public void run() {
        try {
            Thread.sleep(300);                                // let the screen switch before the first frame
            for (int i = 0; i < 40; i++) {
                double r = Math.toRadians(i - 20);
                cos[i] = (int)(Math.cos(r) * ONE); sin[i] = (int)(Math.sin(r) * ONE);
            }
            g3d = new Graphics3D();
            effect = new Effect3D();
            trans = new AffineTrans();
            layout = new FigureLayout();
            int angle = angle(getWidth(), getHeight());
            layout.setPerspective(NEAR, FAR, angle);
            layout.setCenter(getWidth() / 2, getHeight() / 2);
            layout.setAffineTrans(trans);
            out.addElement("MC3 vista " + angle + "/4096 en " + getWidth() + "x" + getHeight() + " (encuadre de M3G 60 grados vertical), lotes de " + BATCH);
            if (test == ProbeCanvas.BENCH_COMPARE) { steps = ProbeCanvas.COMPARE_NAMES.length; compare(); }
            else { steps = GRIDS.length + 2 + TEXTURES.length + 4; geometry(); submission(); textures(); fill(); }
        } catch (Throwable e) {
            out.addElement("MC3 ERROR " + e);
        } finally {
            if (stop) out.addElement("MC3 detenida por el usuario");
            g3d = null; layout = null; trans = null; effect = null;
            String[] lines = new String[out.size()]; out.copyInto(lines);
            owner.benchDone(test, lines, display);
        }
    }

    /** Field angle (4096 = 360 degrees) for the width of the screen, so that the grid covers the same pixels as in
     *  M3GBench (60 degrees vertical): tan(a/2) = (w/h) tan 30. An implementation that applies the angle to the
     *  height instead draws the grid smaller; the capture (0) shows which. */
    static int angle(int w, int h) {
        double target = (double)w / h * Math.tan(Math.PI / 6);
        int lo = 1, hi = 2047;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (Math.tan(mid * Math.PI / 4096) < target) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    // ---------------------------------------------------------------- tests

    /** Triangles per frame at constant screen coverage, sent as quads, one colour per face. */
    private void geometry() {
        for (int i = 0; i < GRIDS.length && !stop; i++) {
            int[] g = GRIDS[i];
            int tris = 2 * g[0] * g[1];
            int[] r;
            try { r = measure(grid(g[0], g[1], true, null, 0), 1, "MC3 GEO " + tris + " tri"); }
            catch (Throwable e) { out.addElement("MC3 GEO " + tris + " tri: NO " + ProbeCanvas.reason(e)); break; }
            if (stop) break;
            out.addElement("MC3 GEO " + tris + " tri QUADS: " + text(r));
            if (r[1] > STOP_MS * 10) { out.addElement("MC3 GEO: serie detenida (> " + STOP_MS + " ms)"); break; }
        }
    }

    /** The same 768 triangles as 768 TRIANGLES (4 calls) and as 384 QUADS (2 calls). */
    private void submission() {
        for (int quads = 0; quads < 2 && !stop; quads++) {
            String name = quads == 1 ? "QUADS" : "TRIANGLES";
            int[] r;
            try { r = measure(grid(16, 24, quads == 1, null, 0), 1, "MC3 ENVIO " + name); }
            catch (Throwable e) { out.addElement("MC3 ENVIO 768 tri " + name + ": NO " + ProbeCanvas.reason(e)); continue; }
            if (stop) return;
            out.addElement("MC3 ENVIO 768 tri " + name + ": " + text(r));
        }
    }

    /** Checker textures (8-bit BMP built here) of growing size: creation time, heap used, 384 textured triangles. */
    private void textures() {
        for (int i = 0; i < TEXTURES.length && !stop; i++) {
            int side = TEXTURES[i];
            System.gc();
            long free = Runtime.getRuntime().freeMemory(), t = System.currentTimeMillis();
            Texture texture;
            try { texture = new Texture(bmp(side), true); }
            catch (Throwable e) { out.addElement("MC3 TEX " + side + ": NO " + ProbeCanvas.reason(e)); break; }
            int createMs = (int)(System.currentTimeMillis() - t);
            long used = free - Runtime.getRuntime().freeMemory();
            int[] r;
            try { r = measure(grid(12, 16, true, texture, side), 1, "MC3 TEX " + side); }
            catch (Throwable e) { out.addElement("MC3 TEX " + side + ": NO al dibujar " + ProbeCanvas.reason(e)); break; }
            if (stop) return;
            out.addElement("MC3 TEX " + side + "x" + side + ": crear " + createMs + " ms, heap " + (used > 0 ? used / 1024 : 0) + " KB, " + text(r));
            if (r[1] > STOP_MS * 10) break;
        }
    }

    /** The comparison loads (ProbeCanvas.COMPARE_NAMES) with Mascot, as in the separate tests; each frame time also
     *  goes to the owner, which sets it against M3G's. */
    private void compare() {
        for (int load = 0; load < ProbeCanvas.COMPARE_NAMES.length && !stop; load++) {
            String name = "CMP Mascot " + ProbeCanvas.COMPARE_NAMES[load];
            int[] r;
            try {
                if (load == 0) r = measure(grid(16, 24, true, null, 0), 1, name);
                else if (load == 1) r = measure(grid(32, 48, true, null, 0), 1, name);
                else if (load == 2) r = measure(grid(12, 16, true, new Texture(bmp(128), true), 128), 1, name);
                else {
                    extentX = 2000; extentY = 2600; still = true;
                    try { r = measure(grid(1, 1, true, null, 0), 4, name); }
                    finally { extentX = 800; extentY = 1000; still = false; }
                }
            } catch (Throwable e) { out.addElement(name + ": NO " + ProbeCanvas.reason(e)); continue; }
            if (stop) break;
            out.addElement(name + ": " + text(r));
            if (r[0] > 0) owner.measured(1, load, r[1]);
        }
    }

    /** One still quad larger than the screen, drawn 1, 2, 4 and 8 times: the cost of pixels alone. */
    private void fill() {
        extentX = 2000; extentY = 2600; still = true;
        try {
            Batches quad = grid(1, 1, true, null, 0);
            int base = 0;
            for (int layers = 1; layers <= 8 && !stop; layers *= 2) {
                int[] r = measure(quad, layers, "MC3 RELLENO x" + layers);
                if (stop) break;
                if (layers == 1) base = r[2];
                out.addElement("MC3 RELLENO pantalla x" + layers + ": " + text(r) + (layers > 1 ? ", +" + (r[2] - base) / 10 + " ms" : ""));
            }
        } catch (Throwable e) { out.addElement("MC3 RELLENO: NO " + ProbeCanvas.reason(e)); }
        extentX = 800; extentY = 1000; still = false;
    }

    // ---------------------------------------------------------------- measurement

    /** As M3GBench.measure: {frames, avg frame ms x10, avg render ms x10, max frame ms, fps x10}. Render = clear
     *  (fillRect, the M3G clear is timed too) + bind + every call + flush + release; the rest is flushGraphics. */
    private int[] measure(Batches mesh, int layers, String label) {
        Graphics g = getGraphics();
        status = label;
        int warm = quick ? QUICK_MS / 3 : WARMUP_MS, measured = quick ? QUICK_MS : MEASURE_MS;
        long start = System.currentTimeMillis(), measureFrom = -1;
        int frames = 0, max = 0;
        long frameSum = 0, renderSum = 0;
        step++;
        for (int f = 0; ; f++) {
            turn(f);
            if (captureRequest) {                            // outside the timing: the window moves forward
                captureRequest = false;
                long t = System.currentTimeMillis();
                capture(mesh, layers, label);
                long spent = System.currentTimeMillis() - t;
                start += spent; if (measureFrom >= 0) measureFrom += spent;
            }
            long a = System.currentTimeMillis();
            if (measureFrom < 0 && a - start >= warm) measureFrom = a;
            if ((measureFrom >= 0 && a - measureFrom >= measured) || stop) break;
            draw(g, mesh, layers);
            long b = System.currentTimeMillis();
            overlay(g, label, a);
            flushGraphics();
            long c = System.currentTimeMillis();
            if (measureFrom >= 0) {
                frames++; frameSum += c - a; renderSum += b - a;
                if (c - a > max) max = (int)(c - a);
            }
            if (c - start > 20000) break;                    // never more than 20 s on one step
        }
        if (frames == 0) return new int[] {0, 0, 0, 0, 0};
        int avg = (int)(frameSum * 10 / frames);
        return new int[] {frames, avg, (int)(renderSum * 10 / frames), max, avg > 0 ? 10000 * 10 / avg : 0};
    }

    /** Object transform of frame f: a turn about Y (none when still), then DISTANCE in front of the camera. */
    private void turn(int f) {
        int k = still ? 20 : f % 40;
        trans.m00 = cos[k]; trans.m01 = 0; trans.m02 = sin[k]; trans.m03 = 0;
        trans.m10 = 0; trans.m11 = ONE; trans.m12 = 0; trans.m13 = 0;
        trans.m20 = -sin[k]; trans.m21 = 0; trans.m22 = cos[k]; trans.m23 = DISTANCE;
        layout.setAffineTrans(trans);
    }

    private void draw(Graphics g, Batches mesh, int layers) {
        g.setColor(0x101820); g.fillRect(0, 0, getWidth(), getHeight());
        g3d.bind(g);
        try {
            for (int k = 0; k < layers; k++)
                for (int i = 0; i < mesh.count.length; i++)
                    g3d.renderPrimitives(mesh.texture, 0, 0, layout, effect, mesh.command, mesh.count[i],
                        mesh.vertices[i], noNormals, mesh.coords[i], mesh.colors[i]);
            g3d.flush();
        } finally { g3d.release(g); }
    }

    private void overlay(Graphics g, String label, long now) {
        int h = font.getHeight(), w = getWidth();
        g.setFont(font);
        g.setColor(0x000000); g.fillRect(0, 0, w, h * 2 + 2); g.fillRect(0, getHeight() - h - 2, w, h + 2);
        g.setColor(0xffff40); g.drawString(label, 2, 1, Graphics.TOP | Graphics.LEFT);
        g.setColor(0xffffff); g.drawString("Mascot: paso " + step + " de " + steps, 2, h + 1, Graphics.TOP | Graphics.LEFT);
        if (captured != null && now - capturedAt < 2500) { g.setColor(0x80ff80); g.drawString(captured, 2, getHeight() - h - 1, Graphics.TOP | Graphics.LEFT); }
        else { g.setColor(0xc0c0c0); g.drawString("0 captura   * parar   (otras no paran)", 2, getHeight() - h - 1, Graphics.TOP | Graphics.LEFT); }
    }

    /** One frame into a mutable image, saved as PNG (JSR-75, loaded by name); never timed. */
    private void capture(Batches mesh, int layers, String label) {
        try {
            Image image = Image.createImage(getWidth(), getHeight());
            Graphics ig = image.getGraphics();
            draw(ig, mesh, layers);
            ig.setFont(font); ig.setColor(0xffff40); ig.drawString("KiscPort Probe - " + label, 2, 1, Graphics.TOP | Graphics.LEFT);
            Saver saver = (Saver)Class.forName("kiscport.probe.ReportFile").newInstance();
            String where = saver.savePng("kiscport-mc3-", image);
            image = null;
            out.addElement("MC3 CAPTURA " + label + ": " + where);
            captured = "Captura guardada";
        } catch (Throwable e) {
            out.addElement("MC3 CAPTURA " + label + " no guardada: " + ProbeCanvas.reason(e));
            captured = "Captura no guardada";
        }
        capturedAt = System.currentTimeMillis();
    }

    private static String text(int[] r) {
        return r[4] / 10 + "." + r[4] % 10 + " fps, " + r[1] / 10 + "." + r[1] % 10 + " ms (render " + r[2] / 10 + "." + r[2] % 10
            + "), max " + r[3] + " ms";
    }

    // ---------------------------------------------------------------- generated primitives

    /** A grid split into calls of at most BATCH primitives; every array starts at offset 0 of its call. */
    static final class Batches {
        int command;
        Texture texture;
        int[] count;
        int[][] vertices, coords, colors;
    }

    /** cols x rows quads over the same area as M3GBench (x -0.8..0.8, y -1..1 unless extentX/Y say otherwise),
     *  as QUADS (corners in order around the quad) or as two TRIANGLES per quad. One colour per face, from the
     *  quad position as the M3G vertex colours; texel coordinates 0..side-1 when textured. Built before timing. */
    private Batches grid(int cols, int rows, boolean quads, Texture texture, int side) {
        int per = quads ? 4 : 3, prims = cols * rows * (quads ? 1 : 2), calls = (prims + BATCH - 1) / BATCH;
        Batches b = new Batches();
        b.command = (quads ? Graphics3D.PRIMITVE_QUADS : Graphics3D.PRIMITVE_TRIANGLES)
            | (texture != null ? Graphics3D.PDATA_TEXURE_COORD : Graphics3D.PDATA_COLOR_PER_FACE);
        b.texture = texture;
        b.count = new int[calls]; b.vertices = new int[calls][]; b.coords = new int[calls][]; b.colors = new int[calls][];
        for (int c = 0; c < calls; c++) {
            int n = Math.min(BATCH, prims - c * BATCH);
            b.count[c] = n; b.vertices[c] = new int[n * per * 3]; b.coords[c] = new int[n * per * 2]; b.colors[c] = new int[n];
        }
        int p = 0;
        for (int r = 0; r < rows; r++) for (int i = 0; i < cols; i++) {
            // Corners a0 (i, r), a1 (i + 1, r), b1 (i + 1, r + 1), b0 (i, r + 1).
            int[] corner = quads ? new int[] {0, 1, 3, 2} : new int[] {2, 0, 3, 3, 0, 1};
            int color = (64 + i * 191 / cols) << 16 | (64 + r * 191 / rows) << 8 | (((i + r) & 1) == 0 ? 200 : 90);
            for (int k = 0; k < corner.length; k++) {
                if (k % per == 0) { b.colors[p / BATCH][p % BATCH] = color; }
                int q = corner[k], x = i + (q & 1), y = r + (q >> 1);
                int[] v = b.vertices[p / BATCH], uv = b.coords[p / BATCH];
                int at = (p % BATCH) * per + k % per;
                v[at * 3] = x * 2 * extentX / cols - extentX; v[at * 3 + 1] = y * 2 * extentY / rows - extentY; v[at * 3 + 2] = 0;
                uv[at * 2] = x * (side > 0 ? side - 1 : 0) / cols; uv[at * 2 + 1] = y * (side > 0 ? side - 1 : 0) / rows;
                if (k % per == per - 1) p++;
            }
        }
        return b;
    }

    /** Uncompressed 8-bit BMP (BITMAPINFOHEADER, 256-colour palette, rows bottom-up and padded to 4 bytes): the
     *  checker of M3GBench (4 px squares, 0xe0e0e0 / 0x2060c0) in palette entries 1 and 2; entry 0 is black. */
    static byte[] bmp(int side) {
        int stride = (side + 3) & ~3, offset = 14 + 40 + 256 * 4, size = offset + stride * side;
        byte[] d = new byte[size];
        d[0] = 'B'; d[1] = 'M'; le(d, 2, size); le(d, 10, offset);
        le(d, 14, 40); le(d, 18, side); le(d, 22, side); d[26] = 1; d[28] = 8; le(d, 34, stride * side); le(d, 46, 256);
        int pal = 54;
        d[pal + 4] = (byte)0xe0; d[pal + 5] = (byte)0xe0; d[pal + 6] = (byte)0xe0;              // entry 1, B G R
        d[pal + 8] = (byte)0xc0; d[pal + 9] = (byte)0x60; d[pal + 10] = (byte)0x20;             // entry 2
        for (int y = 0; y < side; y++) for (int x = 0; x < side; x++)
            d[offset + (side - 1 - y) * stride + x] = (byte)(((x >> 2) + (y >> 2) & 1) == 0 ? 1 : 2);
        return d;
    }

    private static void le(byte[] d, int at, int v) {
        d[at] = (byte)v; d[at + 1] = (byte)(v >> 8); d[at + 2] = (byte)(v >> 16); d[at + 3] = (byte)(v >> 24);
    }
}
