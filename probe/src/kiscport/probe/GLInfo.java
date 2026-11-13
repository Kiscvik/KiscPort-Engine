package kiscport.probe;

import java.util.Vector;
import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;
import javax.microedition.khronos.opengles.GL10;

/** JSR-239: EGL/GL vendor, renderer and limits from a 16x16 off-screen surface (pbuffer, else
 *  pixmap). Read-only probe: no rendering, no engine. */
final class GLInfo implements Info {
    public String[] lines() {
        Vector v = new Vector();
        EGL10 egl = (EGL10)EGLContext.getEGL();
        EGLDisplay d = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);
        int[] version = new int[2];
        if (d == null || !egl.eglInitialize(d, version)) {
            v.addElement("eglInitialize FALLO 0x" + Integer.toHexString(egl.eglGetError()));
            return toArray(v);
        }
        EGLContext ctx = null;
        EGLSurface surface = null;
        try {
            v.addElement("EGL " + version[0] + "." + version[1]);
            v.addElement("EGL_VENDOR " + egl.eglQueryString(d, EGL10.EGL_VENDOR));
            v.addElement("EGL_VERSION " + egl.eglQueryString(d, EGL10.EGL_VERSION));
            v.addElement("EGL_EXT " + egl.eglQueryString(d, EGL10.EGL_EXTENSIONS));
            EGLConfig[] config = new EGLConfig[1];
            int[] count = new int[1];
            String kind = "pbuffer";
            if (egl.eglChooseConfig(d, new int[] {EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PBUFFER_BIT, EGL10.EGL_NONE}, config, 1, count) && count[0] > 0)
                surface = egl.eglCreatePbufferSurface(d, config[0], new int[] {EGL10.EGL_WIDTH, 16, EGL10.EGL_HEIGHT, 16, EGL10.EGL_NONE});
            if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
                kind = "pixmap";
                surface = null;
                if (egl.eglChooseConfig(d, new int[] {EGL10.EGL_SURFACE_TYPE, EGL10.EGL_PIXMAP_BIT, EGL10.EGL_NONE}, config, 1, count) && count[0] > 0)
                    surface = egl.eglCreatePixmapSurface(d, config[0], javax.microedition.lcdui.Image.createImage(16, 16), null);
            }
            if (surface == null || surface == EGL10.EGL_NO_SURFACE) {
                v.addElement("Sin superficie pbuffer/pixmap 0x" + Integer.toHexString(egl.eglGetError()));
                return toArray(v);
            }
            int[] a = new int[1];
            StringBuffer c = new StringBuffer("Config " + kind + " RGB");
            if (egl.eglGetConfigAttrib(d, config[0], EGL10.EGL_RED_SIZE, a)) c.append(a[0]);
            if (egl.eglGetConfigAttrib(d, config[0], EGL10.EGL_GREEN_SIZE, a)) c.append(a[0]);
            if (egl.eglGetConfigAttrib(d, config[0], EGL10.EGL_BLUE_SIZE, a)) c.append(a[0]);
            if (egl.eglGetConfigAttrib(d, config[0], EGL10.EGL_DEPTH_SIZE, a)) c.append(" Z").append(a[0]);
            if (egl.eglGetConfigAttrib(d, config[0], EGL10.EGL_CONFIG_CAVEAT, a))
                c.append(a[0] == EGL10.EGL_SLOW_CONFIG ? " LENTA(SLOW)" : a[0] == EGL10.EGL_NONE ? " sin caveat" : " caveat 0x" + Integer.toHexString(a[0]));
            v.addElement(c.toString());
            ctx = egl.eglCreateContext(d, config[0], EGL10.EGL_NO_CONTEXT, null);
            if (ctx == null || ctx == EGL10.EGL_NO_CONTEXT || !egl.eglMakeCurrent(d, surface, surface, ctx)) {
                v.addElement("Contexto FALLO 0x" + Integer.toHexString(egl.eglGetError()));
                return toArray(v);
            }
            GL10 gl = (GL10)ctx.getGL();
            v.addElement("GL_VENDOR " + gl.glGetString(GL10.GL_VENDOR));
            v.addElement("GL_RENDERER " + gl.glGetString(GL10.GL_RENDERER));
            v.addElement("GL_VERSION " + gl.glGetString(GL10.GL_VERSION));
            int[] i = new int[1];
            gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_SIZE, i, 0); v.addElement("MAX_TEXTURE_SIZE " + i[0]);
            gl.glGetIntegerv(GL10.GL_MAX_LIGHTS, i, 0); v.addElement("MAX_LIGHTS " + i[0]);
            gl.glGetIntegerv(GL10.GL_MAX_TEXTURE_UNITS, i, 0); v.addElement("MAX_TEXTURE_UNITS " + i[0]);
            v.addElement("GL_EXT " + gl.glGetString(GL10.GL_EXTENSIONS));
        } finally {
            try { egl.eglMakeCurrent(d, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT); } catch (Throwable e) { }
            if (surface != null) try { egl.eglDestroySurface(d, surface); } catch (Throwable e) { }
            if (ctx != null) try { egl.eglDestroyContext(d, ctx); } catch (Throwable e) { }
            try { egl.eglTerminate(d); } catch (Throwable e) { }
        }
        return toArray(v);
    }

    private static String[] toArray(Vector v) {
        String[] out = new String[v.size()];
        v.copyInto(out);
        return out;
    }
}
