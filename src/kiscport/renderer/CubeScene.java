package kiscport.renderer;

import javax.microedition.lcdui.Graphics;
import javax.microedition.m3g.Appearance;
import javax.microedition.m3g.Background;
import javax.microedition.m3g.Camera;
import javax.microedition.m3g.Graphics3D;
import javax.microedition.m3g.Mesh;
import javax.microedition.m3g.PolygonMode;
import javax.microedition.m3g.Transform;
import javax.microedition.m3g.TriangleStripArray;
import javax.microedition.m3g.VertexArray;
import javax.microedition.m3g.VertexBuffer;
import kiscport.demo.DemoScene;

/** A colour cube (6 faces, 12 triangles, one strip per face as in the engine's QUAD mode). No allocation per frame. */
public final class CubeScene implements DemoScene {
    private final Graphics3D g3d = Graphics3D.getInstance();
    private final Camera camera = new Camera();
    private final Transform cameraTransform = new Transform(), cubeTransform = new Transform();
    private final Background background = new Background();
    private final Mesh cube;
    private int lastWidth, lastHeight;

    public CubeScene() {
        background.setColor(0x0b1220);
        cameraTransform.postTranslate(0, 0, 5);
        // Each face has its own 4 vertices (flat colour per face), counter-clockwise from outside.
        short[] xyz = {
            -1,-1,1, 1,-1,1, -1,1,1, 1,1,1,   1,-1,-1, -1,-1,-1, 1,1,-1, -1,1,-1,
            1,-1,1, 1,-1,-1, 1,1,1, 1,1,-1,   -1,-1,-1, -1,-1,1, -1,1,-1, -1,1,1,
            -1,1,1, 1,1,1, -1,1,-1, 1,1,-1,   -1,-1,-1, 1,-1,-1, -1,-1,1, 1,-1,1};
        int[] faceColors = {0x24a8ff, 0x4ce4ff, 0xc8ff66, 0xffc86b, 0x8f6bff, 0xff6b8f};
        byte[] rgb = new byte[72];
        for (int face = 0; face < 6; face++) for (int v = 0; v < 4; v++) {
            int o = (face * 4 + v) * 3, c = faceColors[face];
            rgb[o] = (byte)(c >> 16); rgb[o + 1] = (byte)(c >> 8); rgb[o + 2] = (byte)c;
        }
        VertexArray positions = new VertexArray(24, 3, 2); positions.set(0, 24, xyz);
        VertexArray colors = new VertexArray(24, 3, 1); colors.set(0, 24, rgb);
        VertexBuffer buffer = new VertexBuffer(); buffer.setPositions(positions, 1, null); buffer.setColors(colors);
        int[] index = new int[24], length = new int[6];
        for (int face = 0; face < 6; face++) {
            int b = face * 4;
            index[face * 4] = b; index[face * 4 + 1] = b + 1; index[face * 4 + 2] = b + 2; index[face * 4 + 3] = b + 3;
            length[face] = 4;
        }
        Appearance appearance = new Appearance();
        PolygonMode polygon = new PolygonMode();
        polygon.setCulling(PolygonMode.CULL_BACK); polygon.setShading(PolygonMode.SHADE_FLAT);
        appearance.setPolygonMode(polygon);
        cube = new Mesh(buffer, new TriangleStripArray(index, length), appearance);
    }

    public void render(Graphics g, int width, int height, int angleX, int angleY) {
        if (width != lastWidth || height != lastHeight) {
            camera.setPerspective(50, (float)width / height, 1, 100);
            lastWidth = width; lastHeight = height;
        }
        cubeTransform.setIdentity();
        cubeTransform.postRotate(angleY, 0, 1, 0);
        cubeTransform.postRotate(angleX, 1, 0, 0);
        g3d.bindTarget(g, true, 0);
        try {
            g3d.clear(background);
            g3d.setCamera(camera, cameraTransform);
            g3d.render(cube, cubeTransform);
        } finally { g3d.releaseTarget(); }
    }
}
