package javax.microedition.khronos.opengles;
/** Compile-only subset of the JSR-239 interface; never packaged. */
public interface GL10 extends GL {
    int GL_VENDOR = 0x1F00, GL_RENDERER = 0x1F01, GL_VERSION = 0x1F02, GL_EXTENSIONS = 0x1F03,
        GL_MAX_TEXTURE_SIZE = 0x0D33, GL_MAX_LIGHTS = 0x0D31, GL_MAX_TEXTURE_UNITS = 0x84E2;
    String glGetString(int name);
    void glGetIntegerv(int pname, int[] params, int offset);
}
