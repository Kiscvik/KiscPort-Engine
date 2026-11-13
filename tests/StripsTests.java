import java.io.DataInputStream;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/** Host test of kiscport.renderer.Strips : every mode must hand M3G exactly the triangles of
 *  the list. Strips are expanded with the triangle-strip rule (odd triangles swap their first two
 *  indices) and zero-area triangles are dropped. Synthetic meshes only. */
public final class StripsTests {
    static int passed;
    static Method inOrder, greedy;
    static Field mode;
    static void check(boolean ok, String name) { if (!ok) throw new AssertionError(name); passed++; System.out.println("PASS: " + name); }

    static List<int[]> expand(int[][] strips) {
        List<int[]> out = new ArrayList<int[]>();
        int[] idx = strips[0];
        int o = 0;
        for (int length : strips[1]) {
            if (length < 3) throw new AssertionError("strip shorter than 3");
            for (int i = 0; i + 2 < length; i++) {
                int a = idx[o + i], b = idx[o + i + 1], c = idx[o + i + 2];
                if (a == b || b == c || a == c) continue;
                out.add((i & 1) == 0 ? new int[] {a, b, c} : new int[] {b, a, c});
            }
            o += length;
        }
        if (o != idx.length) throw new AssertionError("index count does not match strip lengths");
        return out;
    }
    static int[] rotated(int[] t) {
        int k = t[0] <= t[1] && t[0] <= t[2] ? 0 : t[1] <= t[2] ? 1 : 2;
        return new int[] {t[k], t[(k + 1) % 3], t[(k + 2) % 3]};
    }
    static String key(List<int[]> tris, boolean anyRotation, boolean sorted) {
        String[] keys = new String[tris.size()];
        for (int i = 0; i < keys.length; i++) keys[i] = Arrays.toString(anyRotation ? rotated(tris.get(i)) : tris.get(i));
        if (sorted) Arrays.sort(keys);
        return Arrays.toString(keys);
    }
    static List<int[]> list(int[] tri, int n) {
        List<int[]> out = new ArrayList<int[]>();
        for (int i = 0; i < n; i++) {
            int a = tri[i * 3], b = tri[i * 3 + 1], c = tri[i * 3 + 2];
            if (a != b && b != c && a != c) out.add(new int[] {a, b, c});
        }
        return out;
    }
    /** Returns {strips, indices} summed over the modes checked, for the report. */
    static void verify(String name, int[] tri, int n, int vertices) throws Exception {
        List<int[]> source = list(tri, n);
        StringBuilder report = new StringBuilder();
        for (int m = 0; m < 3; m++) {
            mode.setInt(null, m);
            int[][] a = (int[][])inOrder.invoke(null, tri.clone(), n), g = (int[][])greedy.invoke(null, tri.clone(), n, vertices);
            if (!key(expand(a), false, false).equals(key(source, false, false))) throw new AssertionError(name + ": inOrder mode " + m + " changed the triangle list");
            if (!key(expand(g), true, true).equals(key(source, true, true))) throw new AssertionError(name + ": greedy mode " + m + " changed the triangle set");
            if (m == 0 && (a[1].length != n || g[1].length != n)) throw new AssertionError(name + ": SINGLE is not one strip per triangle");
            if (m == 2 && (a[1].length != 1 || g[1].length != 1)) throw new AssertionError(name + ": JOINED is not one strip");
            report.append(" m").append(m).append(" inOrder ").append(a[1].length).append('/').append(a[0].length)
                  .append(" greedy ").append(g[1].length).append('/').append(g[0].length);
        }
        check(true, name + ": " + n + " triangles kept in every mode (strips/indices:" + report + ")");
    }

    public static void main(String[] args) throws Exception {
        Class<?> c = Class.forName("kiscport.renderer.Strips");
        inOrder = c.getDeclaredMethod("inOrder", int[].class, int.class); inOrder.setAccessible(true);
        greedy = c.getDeclaredMethod("greedy", int[].class, int.class, int.class); greedy.setAccessible(true);
        mode = c.getDeclaredField("mode"); mode.setAccessible(true);

        verify("one triangle", new int[] {0, 1, 2}, 1, 3);
        verify("one quad", new int[] {0, 1, 2, 2, 1, 3}, 2, 4);
        verify("quads and singles mixed", new int[] {0, 1, 2, 2, 1, 3, 4, 5, 6, 7, 8, 9, 9, 8, 10, 11, 12, 13, 14, 15, 16, 16, 15, 17}, 8, 18);
        verify("strip of five", new int[] {0, 1, 2, 2, 1, 3, 2, 3, 4, 4, 3, 5, 4, 5, 6}, 5, 7);
        verify("fan around one vertex", new int[] {0, 1, 2, 0, 2, 3, 0, 3, 4, 0, 4, 5, 0, 5, 1}, 5, 6);
        verify("same edge three times", new int[] {0, 1, 2, 1, 0, 3, 1, 0, 4, 0, 1, 5}, 4, 6);
        Random random = new Random(717);
        for (int round = 0; round < 40; round++) {
            int vertices = 3 + random.nextInt(60), n = 1 + random.nextInt(200);
            int[] tri = new int[n * 3];
            for (int i = 0; i < n; i++) {
                int a = random.nextInt(vertices), b = random.nextInt(vertices), d = random.nextInt(vertices);
                if (i > 0 && random.nextInt(3) > 0) { a = tri[i * 3 - 1]; b = tri[i * 3 - 2]; }   // often continues as a quad
                while (b == a) b = (b + 1) % vertices;
                while (d == a || d == b) d = (d + 1) % vertices;
                tri[i * 3] = a; tri[i * 3 + 1] = b; tri[i * 3 + 2] = d;
            }
            verify("random mesh " + round, tri, n, vertices);
        }

        mode.setInt(null, 1);
        System.out.println("StripsTests passed: " + passed);
    }
}
