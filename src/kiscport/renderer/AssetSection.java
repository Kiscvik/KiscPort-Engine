package kiscport.renderer;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.*;

/** One converted chunk of a level (offline buffers only: all conversion happens on the PC).
 *  KPS1: a mesh with its atlas. KPS3: geometry referencing shared stage pages (submeshes).
 *  Files are read from ROOT: chunkN.kps and stage_N.png (set ROOT before the first load). */
final class AssetSection {
    /** Resource folder of the level being streamed (game-specific; ends with "/"). */
    public static String ROOT = "/kiscport/level/";

    Mesh mesh;
    int triangles;
    final int chunk;

    /** Geometry-only chunk (KPS3) using the stage texture pages loaded once by the caller.
     *  Nothing is read here: prepare() does one bounded step per call and activate() finishes, so the
     *  renderer can spread the work over the frames before the chunk is needed.
     *  visibility (may be null): the chunk's record of visibility.kpv at `at` (parts, then per
     *  part its triangle count and one mask byte per triangle: bit i = can be on screen while the
     *  camera is in interval i of this chunk). With it, views[i] holds only those triangles.
     *  times (may be null): ms of each step of the frame, at TIMES.. (diagnostics). */
    AssetSection(int chunk, Appearance[] stage, byte[] visibility, int at, int intervals, int[] times) {
        this.chunk = chunk; this.stage = stage; this.times = times;
        this.visibility = Strips.mode == Strips.NATURAL ? visibility : null; this.at = at; this.intervals = intervals;
    }

    /** Index into `times` of: open (even chunk), open (odd chunk), positions, texture coordinates +
     *  colours, triangles of one page, mesh assembly. */
    static final int TIMES = 11, TIME_COUNT = 6;
    /** One mesh per camera interval of this chunk (null entry: nothing visible), or null. */
    Mesh[] views;
    int[] viewTriangles;

    private final Appearance[] stage;
    private final int[] times;
    private byte[] visibility;
    private int at;
    private final int intervals;
    private DataInputStream input;
    private int step, count, parts, part;
    private byte[] block;
    private short[] values;
    private VertexArray positions, coords, colors;
    private IndexBuffer[] lists;
    private IndexBuffer[][] viewLists;
    private Appearance[] looks;

    /** One step: 0 open + header, 1 positions, 2 texture coordinates + colours, 3 triangles of one
     *  page (once per page). True when only activate() is left. Each array goes straight into its
     *  M3G object. */
    boolean prepare() throws IOException {
        if (step == 4) return true;
        long started = times != null ? System.currentTimeMillis() : 0;
        int slot;
        switch (step) {
        case 0:
            slot = chunk & 1;
            input = open(ROOT + "chunk" + chunk + ".kps");
            if (input.readInt() != 0x4b505333 || input.readUnsignedShort() != 3) throw new IOException("Formato de tramo invalido");
            count = input.readUnsignedShort(); parts = input.readUnsignedShort();
            if (count < 3 || count > 8192 || parts < 1 || parts > stage.length) throw new IOException("Limites de tramo invalidos");
            block = new byte[BLOCK]; values = new short[BLOCK / 2];
            step = 1; break;
        case 1:
            slot = 2;
            positions = positions(input, count, block, values, false);
            step = 2; break;
        case 2:
            slot = 3;
            coords = coords(input, count, 1, block, values);
            colors = colors(input, count, block);
            values = null;
            step = 3; break;
        default:
            slot = 4;
            if (part == 0) {
                lists = new IndexBuffer[parts]; looks = new Appearance[parts];
                // The visibility record must describe this very file; otherwise the chunk is drawn whole.
                if (visibility != null && (at + 2 > visibility.length || (((visibility[at] & 0xff) << 8) | (visibility[at + 1] & 0xff)) != parts)) visibility = null;
                at += 2;
                if (visibility != null) { viewLists = new IndexBuffer[intervals][parts]; viewTriangles = new int[intervals]; }
            }
            int page = input.readUnsignedShort(), n = input.readUnsignedShort();
            if (page >= stage.length || n < 1 || n > 4096) throw new IOException("Limites de tramo invalidos");
            int[][] strips = strips(input, n, count, block);
            lists[part] = new TriangleStripArray(strips[0], strips[1]); looks[part] = stage[page]; triangles += n;
            if (visibility != null && (at + 2 + n > visibility.length || (((visibility[at] & 0xff) << 8) | (visibility[at + 1] & 0xff)) != n)) {
                visibility = null; viewLists = null; viewTriangles = null;
            }
            if (visibility != null) {
                // The per-interval lists are extra memory: without room for them the chunk is drawn whole.
                try { views(strips[0], strips[1], at + 2); at += 2 + n; }
                catch (OutOfMemoryError e) { visibility = null; viewLists = null; viewTriangles = null; }
            }
            if (++part == parts) {
                if (input.read() != -1) throw new IOException("Datos adicionales de escena");
                block = null;
                abandon();
                step = 4;
            }
        }
        if (times != null) times[TIMES + slot] += (int)(System.currentTimeMillis() - started);
        return step == 4;
    }

    /** Index buffers of the current page for every camera interval: the strips (whole, in file order)
     *  with a triangle that can be on screen in that interval. masks: first mask byte of the page. */
    private void views(int[] indices, int[] lengths, int masks) {
        byte[] seen = new byte[lengths.length];
        for (int s = 0, t = masks; s < lengths.length; s++)
            for (int k = lengths[s] - 2; k > 0; k--) seen[s] |= visibility[t++];
        for (int i = 0; i < intervals; i++) {
            int kept = 0, size = 0;
            for (int s = 0; s < lengths.length; s++) if ((seen[s] >> i & 1) != 0) { kept++; size += lengths[s]; }
            if (kept == 0) continue;
            int[] out = new int[size], sizes = new int[kept];
            for (int s = 0, from = 0, w = 0, k = 0; s < lengths.length; from += lengths[s++]) {
                if ((seen[s] >> i & 1) == 0) continue;
                System.arraycopy(indices, from, out, w, lengths[s]);
                w += lengths[s]; sizes[k++] = lengths[s];
            }
            viewLists[i][part] = new TriangleStripArray(out, sizes);
            viewTriangles[i] += size - 2 * kept;
        }
    }

    /** True when every step ran ahead and only the mesh is left to assemble. */
    boolean prepared() { return step == 4; }

    /** Finishes any step left and builds the mesh. */
    void activate() throws IOException {
        if (mesh != null) return;
        try {
            while (!prepare()) { }
            long started = times != null ? System.currentTimeMillis() : 0;
            // Exact power-of-two position scale (1/256): with 1/250 a fixed-point M3G rounds the scale and the
            // far edge of a 5000 u chunk misses the start of the next one (dark seam on the W900i).
            VertexBuffer buffer = buffer(positions, CHUNK_POSITION_SCALE, coords, 256, colors);
            mesh = new Mesh(buffer, lists, looks);
            if (viewLists != null) {
                views = new Mesh[intervals];
                for (int i = 0; i < intervals; i++) {
                    int used = 0;
                    for (int p = 0; p < parts; p++) if (viewLists[i][p] != null) used++;
                    if (used == 0) continue;
                    IndexBuffer[] some = new IndexBuffer[used];
                    Appearance[] with = new Appearance[used];
                    for (int p = 0, k = 0; p < parts; p++) if (viewLists[i][p] != null) { some[k] = viewLists[i][p]; with[k++] = looks[p]; }
                    // Its own VertexBuffer (same arrays, no copy): on the W900i (M3G 1.0) the interval
                    // meshes, which shared the VertexBuffer of the whole mesh, were not drawn.
                    views[i] = new Mesh(buffer(positions, CHUNK_POSITION_SCALE, coords, 256, colors), some, with);
                }
            }
            positions = null; coords = null; colors = null; lists = null; looks = null; viewLists = null;
            if (times != null) times[TIMES + 5] += (int)(System.currentTimeMillis() - started);
        } finally { abandon(); }
    }

    /** Closes the resource if a prepared chunk is not going to be used (or after its last byte). */
    void abandon() {
        if (input != null) { try { input.close(); } catch (IOException e) { } input = null; }
    }

    private DataInputStream open(String name) throws IOException {
        InputStream resource = getClass().getResourceAsStream(name);
        if (resource == null) throw new IOException("Recursos del nivel no incluidos");
        return new DataInputStream(resource);
    }

    /** Stage texture pages stage_0.png, stage_1.png... (at most 4), loaded once per section. */
    static Appearance[] stagePages() throws IOException {
        int n = 0;
        while (n < 4 && AssetSection.class.getResourceAsStream(ROOT + "stage_" + n + ".png") != null) n++;
        if (n == 0) throw new IOException("Recursos del nivel no incluidos");
        Appearance[] pages = new Appearance[n];
        for (int i = 0; i < n; i++) pages[i] = appearance(ROOT + "stage_" + i + ".png");
        return pages;
    }

    private static int lastTriangles, lastRadius;
    static int lastTriangles() { return lastTriangles; }
    /** Bounding-sphere radius (model units = u for objects) of the last KPS1 mesh read. */
    static int lastRadius() { return lastRadius; }

    static Appearance appearance(String atlas) throws IOException {
        Appearance appearance = new Appearance();
        // TMD flag FCE=0 (single sided) for every converted section/object polygon; with the
        // (x,-y,-z) axis change their front faces are CW, so CULL_FRONT keeps them (as in the source data).
        PolygonMode polygon = new PolygonMode(); polygon.setCulling(PolygonMode.CULL_FRONT);
        polygon.setShading(PolygonMode.SHADE_SMOOTH); appearance.setPolygonMode(polygon);
        CompositingMode compositing = new CompositingMode(); compositing.setAlphaThreshold(0.5f);
        appearance.setCompositingMode(compositing);
        Image image = Image.createImage(atlas);
        int w = image.getWidth(), h = image.getHeight();
        if (w != 256 && w != 128 || h > w || (h & (h - 1)) != 0) throw new IOException("Atlas fuera de presupuesto");
        Texture2D texture = new Texture2D(new Image2D(Image2D.RGBA, image));
        texture.setFiltering(Texture2D.FILTER_BASE_LEVEL, Texture2D.FILTER_NEAREST);
        texture.setWrapping(Texture2D.WRAP_CLAMP, Texture2D.WRAP_CLAMP);
        texture.setBlending(Texture2D.FUNC_MODULATE); appearance.setTexture(0, texture);
        return appearance;
    }

    /** Chunk positions are scaled by exactly 1/256; the renderer multiplies by CHUNK_SCALE = 256/250. */
    static final float CHUNK_POSITION_SCALE = 1.0f / 256, CHUNK_SCALE = 256.0f / 250;

    /** Resource bytes are read in blocks and decoded here. DataInputStream.readShort() per value costs
     *  two stream calls each (~11,000 values per chunk, during the run). Divisible by 6, 4, 3 and 2. */
    private static final int BLOCK = 2040;

    private static VertexArray positions(DataInputStream input, int count, byte[] block, short[] values, boolean bounds) throws IOException {
        VertexArray positions = new VertexArray(count, 3, 2);
        long max = 0;
        for (int first = 0; first < count; ) {
            int n = count - first < BLOCK / 6 ? count - first : BLOCK / 6;
            input.readFully(block, 0, n * 6);
            for (int i = 0, b = 0; i < n * 3; i++, b += 2) values[i] = (short)((block[b] << 8) | (block[b + 1] & 0xff));
            if (bounds) for (int i = 0; i < n * 3; i += 3) {
                long d = (long)values[i] * values[i] + (long)values[i + 1] * values[i + 1] + (long)values[i + 2] * values[i + 2];
                if (d > max) max = d;
            }
            positions.set(first, n, values);
            first += n;
        }
        if (bounds) lastRadius = (int)Math.sqrt((double)max) + 1;
        return positions;
    }

    private static VertexArray coords(DataInputStream input, int count, int vScale, byte[] block, short[] values) throws IOException {
        VertexArray coords = new VertexArray(count, 2, 2);
        for (int first = 0; first < count; ) {
            int n = count - first < BLOCK / 4 ? count - first : BLOCK / 4;
            input.readFully(block, 0, n * 4);
            for (int i = 0, b = 0; i < n * 2; i++, b += 2) {
                int v = (short)((block[b] << 8) | (block[b + 1] & 0xff));
                values[i] = (short)((i & 1) == 1 ? v * vScale : v);
            }
            coords.set(first, n, values);
            first += n;
        }
        return coords;
    }

    private static VertexArray colors(DataInputStream input, int count, byte[] block) throws IOException {
        VertexArray colors = new VertexArray(count, 3, 1);
        for (int first = 0; first < count; ) {
            int n = count - first < BLOCK / 3 ? count - first : BLOCK / 3;
            input.readFully(block, 0, n * 3);
            colors.set(first, n, block);
            first += n;
        }
        return colors;
    }

    /** U and V both in 1/width units (V pre-scaled for non-square pages); half-texel bias. */
    private static VertexBuffer buffer(VertexArray positions, float positionScale, VertexArray coords, int width, VertexArray colors) {
        VertexBuffer buffer = new VertexBuffer();
        buffer.setPositions(positions, positionScale, null);
        buffer.setTexCoords(0, coords, 1.0f / width, new float[] {0.5f / width, 0.5f / width, 0});
        buffer.setColors(colors);
        return buffer;
    }

    private static IndexBuffer triangles(DataInputStream input, int triangles, int count, byte[] block) throws IOException {
        int[][] strips = strips(input, triangles, count, block);
        return new TriangleStripArray(strips[0], strips[1]);
    }

    /** Reads a triangle list and returns it as strips: {indices, stripLengths} (Strips.inOrder). */
    private static int[][] strips(DataInputStream input, int triangles, int count, byte[] block) throws IOException {
        int[] indices = new int[triangles * 3];
        for (int first = 0; first < indices.length; ) {
            int n = indices.length - first < BLOCK / 2 ? indices.length - first : BLOCK / 2;
            input.readFully(block, 0, n * 2);
            for (int i = 0, b = 0; i < n; i++, b += 2) {
                int index = ((block[b] & 0xff) << 8) | (block[b + 1] & 0xff);
                if (index >= count) throw new IOException("Indice de escena invalido");
                indices[first + i] = index;
            }
            first += n;
        }
        return Strips.inOrder(indices, triangles);
    }

    /** KPS1 after the magic: version, vertices, triangles, reserved; V in texels of the page. */
    static Mesh readKps1(DataInputStream input, Appearance appearance) throws IOException {
        if (input.readUnsignedShort() != 1) throw new IOException("Version de escena invalida");
        int count = input.readUnsignedShort(), triangles = input.readUnsignedShort();
        if (input.readUnsignedShort() != 0 || count < 3 || count > 4096 || triangles < 1 || triangles > 4096)
            throw new IOException("Limites de escena invalidos");
        Image2D image = appearance.getTexture(0).getImage();
        // Small models (objects, can) do not need a whole block.
        int size = (count > triangles ? count : triangles) * 6;
        byte[] block = new byte[size < BLOCK ? size : BLOCK];
        short[] values = new short[block.length / 2];
        VertexArray positions = positions(input, count, block, values, true);
        VertexArray coords = coords(input, count, image.getWidth() / image.getHeight(), block, values);
        VertexBuffer buffer = buffer(positions, 1.0f / 250, coords, image.getWidth(), colors(input, count, block));
        lastTriangles = triangles;
        return new Mesh(buffer, triangles(input, triangles, count, block), appearance);
    }

    /** KPS1 block with its magic (objects container). */
    static Mesh read(DataInputStream input, Appearance appearance) throws IOException {
        if (input.readInt() != 0x4b505331) throw new IOException("Version de escena invalida");
        return readKps1(input, appearance);
    }
}
