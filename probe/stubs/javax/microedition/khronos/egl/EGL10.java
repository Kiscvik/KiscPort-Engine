package javax.microedition.khronos.egl;
/** Compile-only subset of the JSR-239 interface; never packaged. */
public interface EGL10 extends EGL {
    Object EGL_DEFAULT_DISPLAY = null;
    EGLContext EGL_NO_CONTEXT = null;
    EGLDisplay EGL_NO_DISPLAY = null;
    EGLSurface EGL_NO_SURFACE = null;
    int EGL_SUCCESS = 0x3000, EGL_NONE = 0x3038, EGL_SURFACE_TYPE = 0x3033, EGL_PBUFFER_BIT = 0x0001, EGL_PIXMAP_BIT = 0x0002,
        EGL_WINDOW_BIT = 0x0004, EGL_WIDTH = 0x3057, EGL_HEIGHT = 0x3056, EGL_VENDOR = 0x3053, EGL_VERSION = 0x3054,
        EGL_EXTENSIONS = 0x3055, EGL_RED_SIZE = 0x3024, EGL_GREEN_SIZE = 0x3023, EGL_BLUE_SIZE = 0x3022,
        EGL_DEPTH_SIZE = 0x3025, EGL_CONFIG_CAVEAT = 0x3027, EGL_SLOW_CONFIG = 0x3050;
    EGLDisplay eglGetDisplay(Object native_display);
    boolean eglInitialize(EGLDisplay display, int[] major_minor);
    boolean eglTerminate(EGLDisplay display);
    String eglQueryString(EGLDisplay display, int name);
    int eglGetError();
    boolean eglGetConfigs(EGLDisplay display, EGLConfig[] configs, int config_size, int[] num_config);
    boolean eglChooseConfig(EGLDisplay display, int[] attrib_list, EGLConfig[] configs, int config_size, int[] num_config);
    boolean eglGetConfigAttrib(EGLDisplay display, EGLConfig config, int attribute, int[] value);
    EGLContext eglCreateContext(EGLDisplay display, EGLConfig config, EGLContext share_context, int[] attrib_list);
    EGLSurface eglCreatePbufferSurface(EGLDisplay display, EGLConfig config, int[] attrib_list);
    EGLSurface eglCreatePixmapSurface(EGLDisplay display, EGLConfig config, Object native_pixmap, int[] attrib_list);
    boolean eglMakeCurrent(EGLDisplay display, EGLSurface draw, EGLSurface read, EGLContext context);
    boolean eglDestroySurface(EGLDisplay display, EGLSurface surface);
    boolean eglDestroyContext(EGLDisplay display, EGLContext context);
}
