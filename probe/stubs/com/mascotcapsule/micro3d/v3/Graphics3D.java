package com.mascotcapsule.micro3d.v3;
import javax.microedition.lcdui.Graphics;
/** Compile-only stub of the Mascot Capsule Micro3D v3 calls the probe uses; never packaged.
 *  The constants are inlined by the compiler: they must keep the documented values of the API. */
public class Graphics3D {
    public static final int PRIMITVE_TRIANGLES = 0x03000000;
    public static final int PRIMITVE_QUADS = 0x04000000;
    public static final int PDATA_COLOR_PER_FACE = 0x0800;
    public static final int PDATA_TEXURE_COORD = 0x3000;
    public Graphics3D() { }
    public final void bind(Graphics graphics) { }
    public final void renderPrimitives(Texture texture, int x, int y, FigureLayout layout, Effect3D effect,
        int command, int numPrimitives, int[] vertexCoords, int[] normals, int[] textureCoords, int[] colors) { }
    public final void flush() { }
    public final void release(Graphics graphics) { }
}
