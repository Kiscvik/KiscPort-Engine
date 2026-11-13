package kiscport.probe;

import java.util.Vector;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.lcdui.game.GameCanvas;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.CompositingMode;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Image2D;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Texture2D;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;

/** Synthetic JSR-184 benchmark: generated grids, checker textures and full-screen quads only (no game
 *  content). Each step renders for MEASURE_MS after WARMUP_MS; a frame is bind + clear + render + release
 *  + flush, timed with the millisecond clock. A series stops once a frame takes more than STOP_MS. */
final class M3GBench extends GameCanvas implements Bench, Runnable {
    static final int WARMUP_MS = 500, MEASURE_MS = 2000, QUICK_MS = 150, STOP_MS = 500;
    /** Grid sizes (columns x rows) of the geometry series: 12 to 3072 triangles at constant coverage. */
    static final int[][] GRIDS = {{2, 3}, {6, 8}, {8, 12}, {12, 16}, {16, 24}, {24, 32}, {32, 48}};
    /** How triangles are sent: one strip per triangle, per quad (engine QUAD) or per row. */
    static final int TRI = 0, QUAD = 1, ROW = 2;
    static final String[] MODES = {"TRI", "QUAD", "FILA"};
    static final int[] TEXTURES = {16, 32, 64, 128, 256, 512};

    private ProbeCanvas owner;
    private Display display;
    private int test;
    private boolean quick;
    private final Vector out = new Vector();
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private Graphics3D g3d;
    private Camera camera;
    private Transform cameraTransform, objectTransform;
    private Background background;
    private volatile String status = "";
    /** 0 saves a capture of the next frame (drawn apart, outside the timing); any other key stops the run:
     *  the current step ends and the remaining steps are skipped. */
    private volatile boolean stop, captureRequest;
    private int step, steps;
    private String captured;
    private long capturedAt;
    /** Half extents of generated grids (mesh units of 0.001) and whether the mesh turns while measured. */
    private int extentX = 800, extentY = 1000;
    private boolean still;

    M3GBench() { super(false); setFullScreenMode(true); }

    public void start(ProbeCanvas owner, Display display, int test, boolean quick) {
        this.owner = owner; this.display = display; this.test = test; this.quick = quick;
        display.setCurrent(this);
        new Thread(this).start();
    }

    protected void keyPressed(int code) { if (code == KEY_NUM0) captureRequest = true; else stop = true; }

    public void run() {
        try {
            Thread.sleep(300);                                // let the screen switch before the first frame
            g3d = Graphics3D.getInstance();
            camera = new Camera();
            camera.setPerspective(60, (float)getWidth() / getHeight(), 0.1f, 100);
            cameraTransform = new Transform(); cameraTransform.postTranslate(0, 0, 2.2f);
            objectTransform = new Transform();
            background = new Background(); background.setColor(0x101820);
            steps = test == ProbeCanvas.BENCH_GEOMETRY ? GRIDS.length : test == ProbeCanvas.BENCH_SUBMIT ? 3
                : test == ProbeCanvas.BENCH_TEXTURES ? TEXTURES.length : test == ProbeCanvas.BENCH_FILL ? 4 : test == ProbeCanvas.BENCH_UPLOAD ? 4
                : GRIDS.length + 3 + TEXTURES.length + 4 + 4;
            switch (test) {
                case ProbeCanvas.BENCH_GEOMETRY: geometry(); break;
                case ProbeCanvas.BENCH_SUBMIT: submission(); break;
                case ProbeCanvas.BENCH_TEXTURES: textures(); break;
                case ProbeCanvas.BENCH_FILL: fill(); break;
                case ProbeCanvas.BENCH_UPLOAD: upload(); break;
                case ProbeCanvas.BENCH_ALL: geometry(); submission(); textures(); fill(); upload(); break;
            }
        } catch (Throwable e) {
            out.addElement("ERROR " + e);
        } finally {
            if (stop) out.addElement("Detenida por el usuario");
            g3d = null; camera = null; background = null;
            String[] lines = new String[out.size()]; out.copyInto(lines);
            owner.benchDone(test, lines, display);
        }
    }

    // ---------------------------------------------------------------- tests

    /** Triangles per frame at constant screen coverage, engine submission (QUAD), vertex colours. */
    private void geometry() {
        for (int i = 0; i < GRIDS.length; i++) {
            int[] g = GRIDS[i];
            int tris = 2 * g[0] * g[1];
            int[] r = measure(grid(g[0], g[1], QUAD, null, null), 1, "GEO " + tris + " tri");
            if (stop) break;
            out.addElement("GEO " + tris + " tri QUAD: " + text(r));
            if (r[1] > STOP_MS * 10) { out.addElement("GEO: serie detenida (> " + STOP_MS + " ms)"); break; }
        }
    }

    /** The same 768 triangles sent three ways: shows the per-strip cost of the driver. */
    private void submission() {
        for (int mode = 0; mode < 3; mode++) {
            int[] r = measure(grid(16, 24, mode, null, null), 1, "ENVIO " + MODES[mode]);
            if (stop) return;
            out.addElement("ENVIO 768 tri " + MODES[mode] + ": " + text(r));
        }
    }

    /** Checker textures of growing size: creation time, heap used, and 384 textured triangles drawn. */
    private void textures() {
        int max = 256;
        try { Object m = Graphics3D.getProperties().get("maxTextureDimension"); if (m != null) max = ((Integer)m).intValue(); }
        catch (Throwable e) { }
        out.addElement("TEX maxTextureDimension " + max);
        for (int i = 0; i < TEXTURES.length; i++) {
            int side = TEXTURES[i];
            if (side > max) { out.addElement("TEX " + side + ": mayor que el maximo declarado, no probada"); break; }
            System.gc();
            long free = Runtime.getRuntime().freeMemory(), t = System.currentTimeMillis();
            Texture2D texture;
            try {
                int[] argb = new int[side * side];
                for (int y = 0; y < side; y++) for (int x = 0; x < side; x++)
                    argb[y * side + x] = ((x >> 2) + (y >> 2) & 1) == 0 ? 0xffe0e0e0 : 0xff2060c0;
                Image image = Image.createRGBImage(argb, side, side, false);
                argb = null;
                texture = new Texture2D(new Image2D(Image2D.RGB, image));
                texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
                texture.setBlending(Texture2D.FUNC_REPLACE);
            } catch (Throwable e) { out.addElement("TEX " + side + ": NO " + e); break; }
            int createMs = (int)(System.currentTimeMillis() - t);
            long used = free - Runtime.getRuntime().freeMemory();
            int[] r = measure(grid(12, 16, QUAD, texture, null), 1, "TEX " + side);
            if (stop) return;
            out.addElement("TEX " + side + "x" + side + ": crear " + createMs + " ms, heap " + (used > 0 ? used / 1024 : 0) + " KB, " + text(r));
            if (r[1] > STOP_MS * 10) break;
        }
    }

    /** One quad larger than the screen (it covers every pixel at 2.2 units with a 60 degree field), still,
     *  drawn 1, 2, 4 and 8 times without depth test: the cost of pixels alone. */
    private void fill() {
        CompositingMode noDepth = new CompositingMode();
        noDepth.setDepthTestEnable(false); noDepth.setDepthWriteEnable(false);
        extentX = 2000; extentY = 2600; still = true;
        Mesh quad = grid(1, 1, QUAD, null, noDepth);
        extentX = 800; extentY = 1000;
        int base = 0;
        for (int layers = 1; layers <= 8 && !stop; layers *= 2) {
            int[] r = measure(quad, layers, "RELLENO x" + layers);
            if (stop) break;
            if (layers == 1) base = r[2];
            out.addElement("RELLENO pantalla x" + layers + ": " + text(r) + (layers > 1 ? ", +" + (r[2] - base) / 10 + " ms" : ""));
        }
        still = false;
    }

    /** Positions rewritten every frame (CPU skinning as in the engine), against the same grid left still. */
    private void upload() {
        int[][] sizes = {{16, 24}, {32, 48}};
        for (int i = 0; i < sizes.length; i++) {
            int cols = sizes[i][0], rows = sizes[i][1], n = (cols + 1) * (rows + 1);
            VertexArray[] positions = new VertexArray[1];
            short[][] data = new short[1][];
            Mesh mesh = grid(cols, rows, QUAD, null, null, positions, data);
            int[] still = measure(mesh, 1, "SUBIDA " + n + " quieta");
            uploads = positions[0]; uploadData = data[0];
            int[] moving = measure(mesh, 1, "SUBIDA " + n);
            uploads = null; uploadData = null;
            if (stop) return;
            out.addElement("SUBIDA " + n + " vert/cuadro: " + text(moving) + ", +" + (moving[2] - still[2]) / 10 + " ms sobre quieta");
        }
    }

    // ---------------------------------------------------------------- measurement

    private VertexArray uploads;
    private short[] uploadData;

    /** Renders 'layers' copies of the mesh per frame. Returns {frames, avg frame ms x10, avg render ms x10,
     *  max frame ms, fps x10}. Render = bind..release; the rest of the frame is flushGraphics. */
    private int[] measure(Mesh mesh, int layers, String label) {
        Graphics g = getGraphics();
        status = label;
        int warm = quick ? QUICK_MS / 3 : WARMUP_MS, measured = quick ? QUICK_MS : MEASURE_MS;
        long start = System.currentTimeMillis(), measureFrom = -1;
        int frames = 0, max = 0;
        long frameSum = 0, renderSum = 0;
        step++;
        for (int f = 0; ; f++) {
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
            if (uploads != null) {                           // a small wave, so every frame really changes
                short d = (short)((f & 7) - 4);
                for (int i = 2; i < uploadData.length; i += 3) uploadData[i] = d;
                uploads.set(0, uploadData.length / 3, uploadData);
            }
            objectTransform.setIdentity();
            if (!still) objectTransform.postRotate((f % 40) - 20, 0, 1, 0);
            g3d.bindTarget(g, true, 0);
            try {
                g3d.clear(background);
                g3d.setCamera(camera, cameraTransform);
                for (int k = 0; k < layers; k++) g3d.render(mesh, objectTransform);
            } finally { g3d.releaseTarget(); }
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

    /** Progress and keys over the 3D frame (not drawn into captures' timing). */
    private void overlay(Graphics g, String label, long now) {
        int h = font.getHeight(), w = getWidth();
        g.setFont(font);
        g.setColor(0x000000); g.fillRect(0, 0, w, h * 2 + 2); g.fillRect(0, getHeight() - h - 2, w, h + 2);
        g.setColor(0xffff40); g.drawString(label, 2, 1, Graphics.TOP | Graphics.LEFT);
        g.setColor(0xffffff); g.drawString("Paso " + step + " de " + steps, 2, h + 1, Graphics.TOP | Graphics.LEFT);
        if (captured != null && now - capturedAt < 2500) { g.setColor(0x80ff80); g.drawString(captured, 2, getHeight() - h - 1, Graphics.TOP | Graphics.LEFT); }
        else { g.setColor(0xc0c0c0); g.drawString("0 captura   otra tecla: parar", 2, getHeight() - h - 1, Graphics.TOP | Graphics.LEFT); }
    }

    /** One frame of the current step into a mutable image, saved as PNG (JSR-75, loaded by name). Rendering
     *  into an Image may take a different driver path than the screen: this frame is never timed. */
    private void capture(Mesh mesh, int layers, String label) {
        try {
            Image image = Image.createImage(getWidth(), getHeight());
            Graphics ig = image.getGraphics();
            g3d.bindTarget(ig, true, 0);
            try {
                g3d.clear(background);
                g3d.setCamera(camera, cameraTransform);
                for (int k = 0; k < layers; k++) g3d.render(mesh, objectTransform);
            } finally { g3d.releaseTarget(); }
            ig.setFont(font); ig.setColor(0xffff40); ig.drawString("KiscPort Probe - " + label, 2, 1, Graphics.TOP | Graphics.LEFT);
            Saver saver = (Saver)Class.forName("kiscport.probe.ReportFile").newInstance();
            String where = saver.savePng("kiscport-3d-", image);
            image = null;
            out.addElement("CAPTURA " + label + ": " + where);
            captured = "Captura guardada";
        } catch (Throwable e) {
            out.addElement("CAPTURA " + label + " no guardada: " + ProbeCanvas.reason(e));
            captured = "Captura no guardada";
        }
        capturedAt = System.currentTimeMillis();
    }

    private static String text(int[] r) {
        return r[4] / 10 + "." + r[4] % 10 + " fps, " + r[1] / 10 + "." + r[1] % 10 + " ms (render " + r[2] / 10 + "." + r[2] % 10
            + "), max " + r[3] + " ms";
    }

    // ---------------------------------------------------------------- generated meshes

    private Mesh grid(int cols, int rows, int mode, Texture2D texture, CompositingMode compositing) {
        return grid(cols, rows, mode, texture, compositing, null, null);
    }

    /** cols x rows quads spanning a fixed area (x -0.8..0.8, y -1..1 unless extentX/Y say otherwise), 2 triangles each, counter-clockwise
     *  towards the camera. Vertex colours from the position; texture coordinates 0..1 when textured. */
    private Mesh grid(int cols, int rows, int mode, Texture2D texture, CompositingMode compositing, VertexArray[] keep, short[][] keepData) {
        int n = (cols + 1) * (rows + 1);
        short[] xyz = new short[n * 3], uv = new short[n * 2];
        byte[] rgb = new byte[n * 3];
        for (int j = 0, v = 0; j <= rows; j++) for (int i = 0; i <= cols; i++, v++) {
            xyz[v * 3] = (short)(i * 2 * extentX / cols - extentX); xyz[v * 3 + 1] = (short)(j * 2 * extentY / rows - extentY);
            uv[v * 2] = (short)(i * 255 / cols); uv[v * 2 + 1] = (short)(j * 255 / rows);
            rgb[v * 3] = (byte)(64 + i * 191 / cols); rgb[v * 3 + 1] = (byte)(64 + j * 191 / rows); rgb[v * 3 + 2] = (byte)(((i + j) & 1) == 0 ? 200 : 90);
        }
        VertexArray positions = new VertexArray(n, 3, 2); positions.set(0, n, xyz);
        VertexArray colors = new VertexArray(n, 3, 1); colors.set(0, n, rgb);
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, 0.001f, null);
        buffer.setColors(colors);
        if (texture != null) { VertexArray coords = new VertexArray(n, 2, 2); coords.set(0, n, uv); buffer.setTexCoords(0, coords, 1f / 255, null); }
        if (keep != null) { keep[0] = positions; keepData[0] = xyz; }
        int[][] strips = strips(cols, rows, mode);
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_BACK); polygon.setShading(PolygonMode.SHADE_SMOOTH);
        appearance.setPolygonMode(polygon);
        if (texture != null) appearance.setTexture(0, texture);
        if (compositing != null) appearance.setCompositingMode(compositing);
        return new Mesh(buffer, new TriangleStripArray(strips[0], strips[1]), appearance);
    }

    /** Index and strip-length arrays. Row r: a_i = (i, r), b_i = (i, r + 1); strip order b0 a0 b1 a1 ...
     *  keeps every triangle counter-clockwise (TRI repeats that winding explicitly). */
    static int[][] strips(int cols, int rows, int mode) {
        int quads = cols * rows;
        int count = mode == TRI ? quads * 6 : mode == QUAD ? quads * 4 : rows * (cols + 1) * 2;
        int[] index = new int[count], length = new int[mode == TRI ? quads * 2 : mode == QUAD ? quads : rows];
        int k = 0, s = 0;
        for (int r = 0; r < rows; r++) {
            if (mode == ROW) {
                for (int i = 0; i <= cols; i++) { index[k++] = (r + 1) * (cols + 1) + i; index[k++] = r * (cols + 1) + i; }
                length[s++] = (cols + 1) * 2;
                continue;
            }
            for (int i = 0; i < cols; i++) {
                int a0 = r * (cols + 1) + i, a1 = a0 + 1, b0 = a0 + cols + 1, b1 = b0 + 1;
                if (mode == QUAD) { index[k++] = b0; index[k++] = a0; index[k++] = b1; index[k++] = a1; length[s++] = 4; }
                else {
                    index[k++] = b0; index[k++] = a0; index[k++] = b1; length[s++] = 3;
                    index[k++] = b1; index[k++] = a0; index[k++] = a1; length[s++] = 3;
                }
            }
        }
        return new int[][] {index, length};
    }
}
