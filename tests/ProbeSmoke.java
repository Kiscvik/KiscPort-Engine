import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;
import javax.microedition.midlet.MIDlet;
import javax.microedition.lcdui.Canvas;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** FreeJ2ME-Plus run of KiscPort Probe 1.4: menu navigation, information, the 9 audio tests with their
 *  listening question, the performance tests in quick mode (M3G and Mascot Capsule v3, each with a 3D capture, and
 *  the comparison that ends in a verdict),
 *  memory, the quick run that ends on a saved report, and PNG captures read back. The emulator plays audio
 *  through desktop Java and renders M3G and Mascot in software on the PC: it proves the MIDlet logic, never
 *  W900i/W995 mixing or speed. FreeJ2ME cannot run without its Mascot classes, so the absent case is checked by
 *  scripts/build_probe.py instead: only MascotBench links to com/mascotcapsule, and it is loaded by name. */
public final class ProbeSmoke {
    static Object canvas; static File evidence, files; static volatile int frames; static Class pngWriter;
    static final int UP = -1, DOWN = -2, LEFT = -3, FIRE = -5;
    static final int HOME = 0, INFO = 2, GRAPHICS = 3, SOUND = 4, MEMORY = 5, REPORT = 6, LOG = 7, HELP = 8, VERDICT = 9;

    static Object field(Object o, String n) throws Exception { Field f = o.getClass().getDeclaredField(n); f.setAccessible(true); return f.get(o); }
    static int intField(String n) throws Exception { return ((Integer) field(canvas, n)).intValue(); }
    static boolean flag(String n) throws Exception { return ((Boolean) field(canvas, n)).booleanValue(); }
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); System.out.println("PASS: " + label); }
    static void key(int code) throws Exception { MobilePlatform.keyPressed(code); Thread.sleep(60); MobilePlatform.keyReleased(code); Thread.sleep(300); }
    static void image(String name) throws Exception {
        Thread.sleep(350);
        BufferedImage r = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        org.recompile.mobile.PlatformImage s = Mobile.getPlatform().getLcdFrontbuffer();
        synchronized (s) { r.setData(s.getCanvas().getData()); }
        ImageIO.write(r, "png", new File(evidence, name + ".png"));
    }
    static void idle(int ms) throws Exception {
        long end = System.currentTimeMillis() + ms;
        Thread.sleep(200);
        while ((field(canvas, "running") != null || flag("benchRunning") || flag("quickRun")) && System.currentTimeMillis() < end) Thread.sleep(100);
        check(field(canvas, "running") == null && !flag("benchRunning"), "test finished");
    }
    static void home() throws Exception { if (intField("screen") != HOME) key(LEFT); check(intField("screen") == HOME, "left returns to the menu"); }
    static void open(int item, int screen) throws Exception { home(); key(Canvas.KEY_NUM0 + item); check(intField("screen") == screen, "menu " + item + " opens screen " + screen); }

    static void sound(int n, int answer) throws Exception {
        key(Canvas.KEY_NUM0 + n); idle(30000);
        String[] results = (String[]) field(canvas, "results");
        check(results[n] != null, "sound test " + n + " logged an API result");
        if (answer >= 0) {
            check(intField("screen") == VERDICT, "sound test " + n + " opens the listening question");
            if (n == 3) image("probe-question");
            key(Canvas.KEY_NUM0 + answer);
            check(intField("screen") == SOUND, "answering returns to the sound list");
            check(results[n].indexOf("OIDO") > 0, "verdict stored next to the API result, not merged");
        }
        System.out.println("EMULATOR (not hardware): " + results[n]);
    }

    static void bench(int n, String expect) throws Exception {
        key(Canvas.KEY_NUM0 + n);
        if (n == 1 || n == 7) { Thread.sleep(150); key(Canvas.KEY_NUM0); }    // 0 in the 3D screen: capture a frame
        if (n == 7) { key(Canvas.KEY_NUM5); key(DOWN); key(FIRE); }          // other keys keep the screen awake, never stop
        Thread.sleep(500);
        if (n != 6) image("probe-bench-" + n);
        if (n == 7) { image("probe-bench-7b"); Thread.sleep(500); image("probe-bench-7c"); Thread.sleep(500); image("probe-bench-7d"); }   // later steps (TEX, RELLENO)
        idle(120000);
        String r = ((String[]) field(canvas, "benchResults"))[n];
        check(r != null && r.indexOf(expect) >= 0, "performance test " + n + " reported " + expect);
        System.out.println("EMULATOR (not hardware): " + r);
    }

    /** FreeJ2ME resolves the file root freej2me_system/ against "/" (File("", path)), so JSR-75 writes fail in the
     *  emulator. The probe must then say so; the PNG encoder itself is checked on a real Probe screen drawn into
     *  an Image and written by PngWriter, then read back by ImageIO. */
    static void captureAttempt(String label) throws Exception {
        // The log keeps its last 200 lines, so indices shift once it is full: look at the lines after the last one seen.
        java.util.Vector log = (java.util.Vector) field(canvas, "log");
        Object before = log.isEmpty() ? null : log.lastElement();
        key(Canvas.KEY_POUND); Thread.sleep(1500);
        String last = "";
        for (int i = log.size() - 1; i >= 0 && log.elementAt(i) != before; i--)
            if (last.length() == 0 && String.valueOf(log.elementAt(i)).startsWith("Captura")) last = String.valueOf(log.elementAt(i));
        check(last.startsWith("Captura guardada") || last.startsWith("Captura no guardada"), label + ": # saved a capture or said why (" + last + ")");
    }
    static void encode(String name, String label) throws Exception {
        ClassLoader l = canvas.getClass().getClassLoader();
        Class imageClass = Class.forName("javax.microedition.lcdui.Image", true, l);
        Object image = imageClass.getMethod("createImage", int.class, int.class).invoke(null, 240, 320);
        Object g = imageClass.getMethod("getGraphics").invoke(image);
        Method draw = canvas.getClass().getDeclaredMethod("draw", Class.forName("javax.microedition.lcdui.Graphics", true, l), int.class, int.class);
        draw.setAccessible(true); draw.invoke(canvas, g, 240, 320);
        if (pngWriter == null) pngWriter = l.loadClass("kiscport.probe.PngWriter");   // FreeJ2ME's loader defines a class once
        Method write = pngWriter.getDeclaredMethod("write", imageClass, java.io.OutputStream.class);
        write.setAccessible(true);
        java.io.FileOutputStream out = new java.io.FileOutputStream(new File(files, name));
        try { write.invoke(null, image, out); } finally { out.close(); }
        png(name, label);
    }
    static void png(String name, String label) throws Exception {
        File f = new File(files, name);
        check(f.isFile(), label + ": " + name + " written");
        BufferedImage i = ImageIO.read(f);
        check(i != null && i.getWidth() == 240 && i.getHeight() == 320, label + ": valid 240x320 PNG");
        int distinct = 0, last = -1;
        for (int y = 0; y < 320; y += 7) for (int x = 0; x < 240; x += 7) { int p = i.getRGB(x, y); if (p != last) { distinct++; last = p; } }
        check(distinct > 20, label + ": the PNG has content, not a blank screen");
        ImageIO.write(i, "png", new File(evidence, "saved-" + name));
    }

    public static void main(String[] a) {
        try { run(a); System.out.println("PROBE SMOKE SUCCESS"); System.exit(0); } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }

    static void run(String[] a) throws Exception {
        evidence = new File(a[1]); evidence.mkdirs();
        files = new File(a[2]);
        Mobile.textEncoding = "ISO_8859_1"; Mobile.limitFPS = 0; Mobile.maskIndex = 0;
        Mobile.setPlatform(new MobilePlatform(240, 320), new Runnable() { public void run() { } });
        Mobile.getPlatform().setPainter(new Runnable() { public void run() { frames++; } });
        check(Mobile.getPlatform().load(new File(a[0]).toURI().toString()), "load probe JAR");
        Mobile.getPlatform().loader.start();
        MIDlet m = Mobile.midlet; check(m != null, "MIDlet constructed");
        Thread.sleep(1500); canvas = field(m, "canvas");
        long end = System.currentTimeMillis() + 15000;
        while (field(canvas, "controls") == null && System.currentTimeMillis() < end) Thread.sleep(100);
        check(field(canvas, "properties") != null && field(canvas, "types") != null && field(canvas, "apis") != null && field(canvas, "controls") != null, "properties, content types, APIs and controls gathered");
        check(intField("screen") == HOME, "starts on the menu");
        image("probe-home");
        key(DOWN); key(DOWN); check(intField("sel") == 2, "joystick down moves the selection");
        key(UP); check(intField("sel") == 1, "joystick up moves it back");
        key(FIRE); check(intField("screen") == INFO, "OK opens the selected entry (Informacion)");
        Thread.sleep(2500); check(field(canvas, "gl") != null, "JSR-239 probe ran or reported its absence");
        image("probe-info");
        for (int i = 0; i < 12; i++) key(DOWN);
        check(intField("scroll") > 0, "joystick down scrolls a long screen");
        image("probe-info-scrolled");

        // Sound: same 9 tests; the question has its own screen.
        open(4, SOUND); image("probe-sound");
        sound(9, -1); sound(1, 1); sound(2, 1); sound(3, 1); sound(4, 3); sound(5, 2); sound(6, 3); sound(7, 2); sound(8, 4);
        key(Canvas.KEY_NUM0); check(intField("format") == 1, "0 cycles the WAV format");
        sound(2, 0);
        captureAttempt("sound screen");
        encode("sound.png", "PngWriter on the sound screen");

        // Graphics in quick mode, with a 3D capture (0 inside the benchmark screen).
        Field q = canvas.getClass().getDeclaredField("quick"); q.setAccessible(true); q.setBoolean(canvas, true);
        open(3, GRAPHICS); image("probe-graphics-empty");
        key(Canvas.KEY_NUM4); Thread.sleep(250); key(Canvas.KEY_STAR); idle(60000);      // * stops a 3D test
        check(((String[]) field(canvas, "benchResults"))[4].indexOf("Detenida por el usuario") >= 0, "* in the 3D screen stops the test");
        bench(1, "GEO 12 tri QUAD"); bench(2, "ENVIO 768 tri FILA"); bench(3, "TEX 16x16"); bench(4, "RELLENO pantalla x8"); bench(5, "SUBIDA 1617");
        check(((String[]) field(canvas, "benchResults"))[1].indexOf("CAPTURA") >= 0, "0 in the 3D screen saved (or explained) a capture");
        bench(7, "MC3 GEO 12 tri QUADS");
        String mc3 = ((String[]) field(canvas, "benchResults"))[7];
        check(mc3.indexOf("MC3 TEX 256x256") >= 0 && mc3.indexOf("MC3 RELLENO pantalla x8") >= 0 && mc3.indexOf("MC3 ENVIO 768 tri TRIANGLES") >= 0,
            "Mascot test ran its four series");
        check(mc3.indexOf("detenida") < 0, "5, joystick and OK in the 3D screen did not stop the test");
        check(mc3.indexOf("MC3 CAPTURA") >= 0, "0 in the Mascot screen saved (or explained) a capture");
        bench(8, "fases rapidas de emulador");
        String verdict = (String) field(canvas, "compareVerdict");
        String cmp = String.valueOf(field(canvas, "compareLines"));
        check(verdict != null && cmp.indexOf("CMP M3G mallas 3072 tri") >= 0 && cmp.indexOf("CMP Mascot relleno pantalla x4") >= 0,
            "the comparison measured both APIs and gave a verdict: " + verdict);
        image("probe-graphics-verdict");
        image("probe-graphics");

        open(5, MEMORY); image("probe-memory-before");
        key(FIRE); idle(60000);                                     // OK starts the memory test on its screen
        String mem = ((String[]) field(canvas, "benchResults"))[6];
        check(mem != null && mem.indexOf("MEM reservados") >= 0, "memory test reported MEM reservados");
        System.out.println("EMULATOR (not hardware): " + mem);
        image("probe-memory");

        // Quick run from the menu: ends on the report, saved.
        home(); key(Canvas.KEY_NUM1);
        check(flag("quickRun"), "Prueba rapida starts from the menu");
        Thread.sleep(300); image("probe-quick");
        idle(120000);
        end = System.currentTimeMillis() + 10000;
        while (field(canvas, "saved") == null && System.currentTimeMillis() < end) Thread.sleep(100);
        check(intField("screen") == REPORT && field(canvas, "saved") != null, "the quick run ends on the report and saves it (or says why)");
        Method rm = canvas.getClass().getDeclaredMethod("report"); rm.setAccessible(true);
        String all = java.util.Arrays.toString((String[]) rm.invoke(canvas));
        check(all.indexOf("KISCPORT PROBE") >= 0 && all.indexOf("rend GEO") >= 0 && all.indexOf("audio T") >= 0 && all.indexOf("AVISO") >= 0
            && all.indexOf("VEREDICTO 3D") >= 0, "report lists identity, the 3D verdict, audio and performance, and flags the quick emulator phases");
        image("probe-report");
        captureAttempt("report screen");
        encode("report.png", "PngWriter on the report screen");
        System.out.println("EMULATOR report saved: " + field(canvas, "saved"));

        open(7, LOG); image("probe-log");
        open(8, HELP); image("probe-help");
        home();
        for (Object line : ((java.util.Vector) field(canvas, "log")).toArray()) System.out.println("LOG " + line);
    }
}
