import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import javax.microedition.midlet.MIDlet;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** FreeJ2ME-Plus run of the engine demo: intro, then the cube (or, without M3G, the loop keeps running and says
 *  so), fixed-step spin independent of frames, joystick input, HUD toggle, pause/resume. Desktop only. */
public final class DemoSmoke {
    static Object canvas; static File evidence;
    static final int RIGHT = -4, FIRE = -5;
    static Object field(Object o, String n) throws Exception { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    static int call(String m) throws Exception { Method x = canvas.getClass().getMethod(m); return ((Integer) x.invoke(canvas)).intValue(); }
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); System.out.println("PASS: " + label); }
    static void key(int code) throws Exception { MobilePlatform.keyPressed(code); Thread.sleep(80); MobilePlatform.keyReleased(code); Thread.sleep(300); }
    static void image(String name) throws Exception {
        Thread.sleep(300);
        BufferedImage r = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        org.recompile.mobile.PlatformImage s = Mobile.getPlatform().getLcdFrontbuffer();
        synchronized (s) { r.setData(s.getCanvas().getData()); }
        ImageIO.write(r, "png", new File(evidence, name + ".png"));
    }
    static int colours() throws Exception {
        org.recompile.mobile.PlatformImage s = Mobile.getPlatform().getLcdFrontbuffer();
        BufferedImage r = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        synchronized (s) { r.setData(s.getCanvas().getData()); }
        java.util.HashSet<Integer> set = new java.util.HashSet<Integer>();
        for (int y = 60; y < 260; y += 4) for (int x = 20; x < 220; x += 4) set.add(r.getRGB(x, y));
        return set.size();
    }

    public static void main(String[] a) {
        try { run(a); System.out.println("DEMO SMOKE SUCCESS"); System.exit(0); } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }

    static void run(String[] a) throws Exception {
        evidence = new File(a[1]); evidence.mkdirs();
        boolean m3g = !"absent".equals(a[2]);
        Mobile.textEncoding = "ISO_8859_1"; Mobile.limitFPS = 0; Mobile.maskIndex = 0;
        Mobile.setPlatform(new MobilePlatform(240, 320), new Runnable() { public void run() { } });
        Mobile.getPlatform().setPainter(new Runnable() { public void run() { } });
        check(Mobile.getPlatform().load(new File(a[0]).toURI().toString()), "load demo JAR");
        Mobile.getPlatform().loader.start();
        MIDlet midlet = Mobile.midlet; check(midlet != null, "MIDlet constructed");
        Thread.sleep(1200); canvas = field(midlet, "canvas");
        check(canvas != null, "demo canvas created");
        image("intro");
        check(field(canvas, "intro") != null, "KISC-LABS intro is showing");
        key(-1);                                                   // any key skips it
        Thread.sleep(500);
        check(field(canvas, "intro") == null, "a key skips the intro");
        check(field(canvas, "texts") != null, "KTT1 caption table loaded");
        if (m3g) check(field(canvas, "scene") != null && field(canvas, "sceneError") == null, "M3G cube scene loaded by name");
        else check(field(canvas, "scene") == null && "M3G no disponible".equals(field(canvas, "sceneError")), "without M3G the demo says so and keeps running");
        int f0 = call("frames"), t0 = call("ticks"), y0 = call("angleY");
        Thread.sleep(1500);
        int f1 = call("frames"), t1 = call("ticks"), y1 = call("angleY");
        check(f1 > f0 + 5, "the loop keeps drawing frames (" + (f1 - f0) + ")");
        check(t1 - t0 >= 60 && t1 - t0 <= 90, "about 75 fixed 20 ms steps in 1.5 s (" + (t1 - t0) + "), independent of frames");
        check(y1 != y0, "the cube turns");
        image(m3g ? "cube" : "no-m3g");
        if (m3g) check(colours() >= 4, "the cube is drawn (background and at least three flat faces)");
        boolean hud = ((Boolean) field(canvas, "showHud")).booleanValue();
        key(FIRE);
        check(((Boolean) field(canvas, "showHud")).booleanValue() != hud, "OK toggles the FPS overlay");
        MobilePlatform.keyPressed(RIGHT); Thread.sleep(400);
        check(((Integer) field(canvas, "spinY")).intValue() == 30, "joystick right turns the cube the other way");
        MobilePlatform.keyReleased(RIGHT); Thread.sleep(200);
        image(m3g ? "cube-hud-off" : "no-m3g-hud-off");
        Method pause = canvas.getClass().getMethod("pauseLoop"), resume = canvas.getClass().getMethod("resumeLoop");
        pause.invoke(canvas); Thread.sleep(300);
        int paused = call("frames"); Thread.sleep(600);
        check(call("frames") == paused, "pause stops the loop");
        resume.invoke(canvas); Thread.sleep(600);
        check(call("frames") > paused, "resume restarts it");
    }
}
