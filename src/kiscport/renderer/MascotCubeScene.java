package kiscport.renderer;

import com.mascotcapsule.micro3d.v3.AffineTrans;
import com.mascotcapsule.micro3d.v3.Effect3D;
import com.mascotcapsule.micro3d.v3.FigureLayout;
import com.mascotcapsule.micro3d.v3.Graphics3D;
import javax.microedition.lcdui.Graphics;
import kiscport.demo.DemoScene;

/** ALPHA: the demo cube drawn with Mascot Capsule Micro3D v3 instead of M3G, to judge on the phone whether a
 *  Mascot renderer is worth it. Loaded by name; the only engine class that links to com.mascotcapsule.
 *  Same cube, colours, framing and turn as CubeScene, with the differences of the API: one colour per face, six
 *  QUADS in one renderPrimitives call, no depth buffer (the API sorts the polygons), integer maths with
 *  4096 = 1.0 and 4096 = 360 degrees. The calls are the ones KiscPort Probe test 7 ran on the W995 and the W900i:
 *  fillRect clear, bind, renderPrimitives, flush, release. No allocation per frame. */
public final class MascotCubeScene implements DemoScene {
    /** Model units: cube half side and camera distance in the ratio of CubeScene (1 and 5). 4096 = 1.0. */
    static final int HALF = 500, DISTANCE = 2500, NEAR = 100, FAR = 10000, ONE = 4096;
    private final Graphics3D g3d = new Graphics3D();
    private final FigureLayout layout = new FigureLayout();
    private final AffineTrans trans = new AffineTrans();
    private final Effect3D effect = new Effect3D();
    private final int[] vertices = new int[6 * 4 * 3], colors = new int[6], normals = new int[6 * 4 * 3],
        coords = new int[6 * 4 * 2];
    private final int command = Graphics3D.PRIMITVE_QUADS | Graphics3D.PDATA_COLOR_PER_FACE;
    private int lastWidth, lastHeight;

    public MascotCubeScene() {
        // CubeScene's faces (4 vertices each, strip order v0 v1 v2 v3); a quad goes around: v0 v1 v3 v2.
        int[] xyz = {
            -1,-1,1, 1,-1,1, -1,1,1, 1,1,1,   1,-1,-1, -1,-1,-1, 1,1,-1, -1,1,-1,
            1,-1,1, 1,-1,-1, 1,1,1, 1,1,-1,   -1,-1,-1, -1,-1,1, -1,1,-1, -1,1,1,
            -1,1,1, 1,1,1, -1,1,-1, 1,1,-1,   -1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        int[] around = {0, 1, 3, 2};
        int[] faceColors = {0x24a8ff, 0x4ce4ff, 0xc8ff66, 0xffc86b, 0x8f6bff, 0xff6b8f};
        for (int face = 0; face < 6; face++) {
            colors[face] = faceColors[face];
            for (int k = 0; k < 4; k++) {
                int from = (face * 4 + around[k]) * 3, to = (face * 4 + k) * 3;
                vertices[to] = xyz[from] * HALF; vertices[to + 1] = xyz[from + 1] * HALF; vertices[to + 2] = xyz[from + 2] * HALF;
            }
        }
        layout.setAffineTrans(trans);
    }

    /** Field angle (4096 = 360 degrees) across the width, meant to match CubeScene's 50 degrees vertical:
     *  tan(a/2) = (w/h) tan 25. Same search as Probe's MascotBench.angle. Whether the phone applies it to the width
     *  or the height is not confirmed: compare the cube's size with M3G on the phone. */
    static int angle(int w, int h) {
        double target = (double)w / h * Math.tan(25 * Math.PI / 180);
        int lo = 1, hi = 2047;
        while (lo < hi) {
            int mid = (lo + hi) / 2;
            if (Math.tan(mid * Math.PI / 4096) < target) lo = mid + 1; else hi = mid;
        }
        return lo;
    }

    public void render(Graphics g, int width, int height, int angleX, int angleY) {
        if (width != lastWidth || height != lastHeight) {
            layout.setPerspective(NEAR, FAR, angle(width, height));
            layout.setCenter(width / 2, height / 2);
            lastWidth = width; lastHeight = height;
        }
        // As CubeScene: postRotate(Y) then postRotate(X), so the turn is Ry * Rx. M3G's camera looks down -z with
        // y up; Mascot's looks down +z with y down the screen. Turning the view 180 degrees about x (negate y and
        // z: rows 1 and 2) maps one to the other without mirroring the cube.
        double ry = angleY * Math.PI / 180, rx = angleX * Math.PI / 180;
        int cy = (int)(Math.cos(ry) * ONE), sy = (int)(Math.sin(ry) * ONE);
        int cx = (int)(Math.cos(rx) * ONE), sx = (int)(Math.sin(rx) * ONE);
        trans.m00 = cy; trans.m01 = sy * sx >> 12;    trans.m02 = sy * cx >> 12;    trans.m03 = 0;
        trans.m10 = 0;  trans.m11 = -cx;              trans.m12 = sx;               trans.m13 = 0;
        trans.m20 = sy; trans.m21 = -(cy * sx >> 12); trans.m22 = -(cy * cx >> 12); trans.m23 = DISTANCE;
        layout.setAffineTrans(trans);
        g.setColor(0x0b1220); g.fillRect(0, 0, width, height);
        g3d.bind(g);
        try {
            g3d.renderPrimitives(null, 0, 0, layout, effect, command, 6, vertices, normals, coords, colors);
            g3d.flush();
        } finally { g3d.release(g); }
    }
}
