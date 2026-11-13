package kiscport.renderer;

/** Triangle lists -> triangle strips with exactly the same triangles.
 *  Sending every triangle as its own 3-index strip costs (~1,950 strips per frame). The converted
 *  data is almost all quads stored as (a,b,c)(c,b,d), which is the strip a,b,c,d as it stands.
 *  SINGLE: one strip per triangle (kept for A/B measurements on the phone).
 *  NATURAL: the strips found, each one separate (no extra indices: 4 per quad instead of 6).
 *  JOINED: the same strips linked into one with repeated indices (zero-area triangles, never drawn);
 *          a joined strip always starts at an even position, so no triangle changes its winding.
 *  No triangle is added, removed or re-split; only how the same list is handed to M3G changes.
 *  Pure int arrays (no M3G types): results are {indices, stripLengths} for TriangleStripArray. */
final class Strips {
    static final int SINGLE = 0, NATURAL = 1, JOINED = 2;
    /** Read when a mesh is built; the renderer sets it before anything is loaded. */
    static int mode = NATURAL;
    private Strips() { }

    /** File-order pass, O(n), for chunks loaded during the run: a triangle extends the current strip
     *  only when it continues it with its own vertex order, so every triangle keeps its three indices
     *  in the stored order. tri holds n triangles (3 indices each). */
    static int[][] inOrder(int[] tri, int n) {
        if (mode == SINGLE) return single(tri, n);
        // Pass 1: count strips and indices. q, r = last two indices of the open strip, length = its size.
        int strips = 0, total = 0, joined = 0, length = 0, q = 0, r = 0;
        for (int i = 0, o = 0; i < n; i++, o += 3) {
            if (length > 0 && (((length & 1) == 1) ? (tri[o] == r && tri[o + 1] == q) : (tri[o] == q && tri[o + 1] == r))) {
                length++;
            } else {
                if (length > 0) { strips++; total += length; joined += (joined == 0 ? 0 : 2 + (joined & 1)) + length; }
                length = 3;
            }
            q = r; r = tri[o + 2];
            if (length == 3) q = tri[o + 1];
        }
        if (length > 0) { strips++; total += length; joined += (joined == 0 ? 0 : 2 + (joined & 1)) + length; }
        boolean one = mode == JOINED;
        int[] out = new int[one ? joined : total], lengths = new int[one ? 1 : strips];
        // Pass 2: same walk, writing.
        int w = 0, s = 0, start = 0;
        length = 0;
        for (int i = 0, o = 0; i < n; i++, o += 3) {
            if (length > 0 && (((length & 1) == 1) ? (tri[o] == r && tri[o + 1] == q) : (tri[o] == q && tri[o + 1] == r))) {
                out[w++] = tri[o + 2]; length++;
            } else {
                if (length > 0) {
                    if (one) { out[w++] = r; if ((w & 1) == 0) out[w++] = r; out[w++] = tri[o]; }   // next strip starts even
                    else lengths[s++] = w - start;
                }
                start = w;
                out[w++] = tri[o]; out[w++] = tri[o + 1]; out[w++] = tri[o + 2]; length = 3;
            }
            q = r; r = tri[o + 2];
            if (length == 3) q = tri[o + 1];
        }
        if (one) lengths[0] = w; else lengths[s] = w - start;
        return new int[][] {out, lengths};
    }

    /** Greedy strips over shared edges for meshes built once per scene (characters, menu models):
     *  their triangles are not stored as quads. A triangle may be entered by any of its edges, so its
     *  indices can be rotated (same triangle, same facing). Adjacency is a per-vertex edge list; the
     *  work arrays are shorts (about 11 bytes per triangle, nothing kept). If they do not fit in
     *  memory the mesh is built with one strip per triangle. */
    static int[][] greedy(int[] tri, int n, int vertices) {
        if (mode == SINGLE || vertices > 32767 || n > 10000) return single(tri, n);
        try { return shared(tri, n, vertices); }
        catch (OutOfMemoryError e) { return single(tri, n); }
    }

    private static int[][] shared(int[] tri, int n, int vertices) {
        short[] head = new short[vertices], next = new short[n * 3];
        for (int i = 0; i < vertices; i++) head[i] = -1;
        for (int e = n * 3 - 1; e >= 0; e--) { next[e] = head[tri[e]]; head[tri[e]] = (short)e; }
        boolean[] used = new boolean[n];
        short[] out = new short[n * 3], lengths = new short[n];
        int w = 0, s = 0;
        for (int i = 0; i < n; i++) {
            if (used[i]) continue;
            used[i] = true;
            int o = i * 3, k = 0;
            // Start with the rotation whose last edge has a free neighbour, if any.
            for (int c = 0; c < 3; c++)
                if (edge(tri, head, next, used, tri[o + (c + 2) % 3], tri[o + (c + 1) % 3]) >= 0) { k = c; break; }
            int start = w;
            out[w++] = (short)tri[o + k]; out[w++] = (short)tri[o + (k + 1) % 3]; out[w++] = (short)tri[o + (k + 2) % 3];
            while (true) {
                // Next triangle shares the last two indices; odd positions are wound the other way round.
                int e = ((w - start) & 1) == 1 ? edge(tri, head, next, used, out[w - 1], out[w - 2])
                                               : edge(tri, head, next, used, out[w - 2], out[w - 1]);
                if (e < 0) break;
                int t = e / 3;
                used[t] = true; out[w++] = (short)tri[t * 3 + (e - t * 3 + 2) % 3];
            }
            lengths[s++] = (short)(w - start);
        }
        head = null; next = null; used = null;
        if (mode != JOINED) {
            int[] indices = new int[w], sizes = new int[s];
            for (int i = 0; i < w; i++) indices[i] = out[i];
            for (int i = 0; i < s; i++) sizes[i] = lengths[i];
            return new int[][] {indices, sizes};
        }
        int size = 0;
        for (int i = 0; i < s; i++) size += (size == 0 ? 0 : 2 + (size & 1)) + lengths[i];
        int[] indices = new int[size];
        int at = 0, from = 0;
        for (int i = 0; i < s; i++) {
            if (at > 0) {                                        // link: last index, (pad), first index
                indices[at] = indices[at - 1]; at++;
                if ((at & 1) == 0) { indices[at] = indices[at - 1]; at++; }   // next strip starts even
                indices[at++] = out[from];
            }
            for (int k = lengths[i]; k > 0; k--) indices[at++] = out[from++];
        }
        return new int[][] {indices, new int[] {size}};
    }

    /** Unused triangle with the directed edge u -> v, as edge index (triangle * 3 + corner of u), or -1. */
    private static int edge(int[] tri, short[] head, short[] next, boolean[] used, int u, int v) {
        for (int e = head[u]; e >= 0; e = next[e]) {
            int t = e / 3;
            if (!used[t] && tri[t * 3 + (e - t * 3 + 1) % 3] == v) return e;
        }
        return -1;
    }

    private static int[][] single(int[] tri, int n) {
        int[] indices = tri, lengths = new int[n];
        if (tri.length != n * 3) { indices = new int[n * 3]; System.arraycopy(tri, 0, indices, 0, n * 3); }
        for (int i = 0; i < n; i++) lengths[i] = 3;
        return new int[][] {indices, lengths};
    }
}
