package kiscport.probe;

import java.io.InputStream;
import java.util.Hashtable;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Command;
import javax.microedition.lcdui.CommandListener;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Displayable;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.media.Control;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.PlayerListener;
import javax.microedition.media.control.VolumeControl;

/** KiscPort Probe (KISC-LABS): what a phone can do for a KiscPort port. Audio page: keys 1-9 run the MMAPI tests,
 *  0 cycles the WAV format; after a test the tester answers what was HEARD (keys 1-4, 0 skips): the API result
 *  and the audible verdict are logged side by side, never merged. Performance page: keys 1-6 and 9 run the
 *  synthetic M3G benchmark and the heap test. Report page: 1 saves the report (JSR-75). # next page (joystick
 *  left/right too, up/down scrolls), * stops every player. */
final class ProbeCanvas extends Canvas implements Runnable, CommandListener, PlayerListener {
    private static final String[] FORMATS = {"8k8", "8k16", "16k16", "11k8", "ima8k", "ima16k", "ms8k"};
    private static final String[] FORMAT_NAMES = {"PCM 8 bit 8 kHz", "PCM 16 bit 8 kHz", "PCM 16 bit 16 kHz",
        "PCM 8 bit 11 kHz (efectos del juego)", "IMA ADPCM 8 kHz", "IMA ADPCM 16 kHz", "MS ADPCM 8 kHz"};
    private static final String[] WAV = {"audio/x-wav", "audio/wav"}, MIDI = {"audio/midi", "audio/mid", "audio/x-midi"};
    private static final int LONG_MS = 1500, BEEP_MS = 420, MELODY_MS = 3000, FX_MS = 300;
    private static final String[] TESTS = {null, "WAV individual", "Dos WAV", "MIDI individual", "WAV + MIDI",
        "3-4 sonidos", "MIDIControl efectos", "Repeticion rapida", "WAV + tonos", "Matriz formatos (API)"};
    private static final String TWO = "1 Ambos a la vez 2 Solo el 1o 3 El 1o se corto 4 Nada";
    private static final String[] VERDICTS = {null, "1 Se oyo bien 2 No se oyo 3 Cortado/distorsion", TWO,
        "1 Se oyo bien 2 No se oyo 3 Cortado/distorsion", TWO, "Cuantos sonaban a la vez? 1-4",
        "1 Notas solas y sobre el tono 2 Solo solas 3 Ninguna nota 4 Nada", "1 Todos claros 2 Algunos perdidos/tarde 3 Solo algun grupo 4 Nada",
        TWO, null};

    private final KiscPortProbe midlet;
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private final Command exit = new Command("Salir", Command.EXIT, 1), back = new Command("Atras", Command.BACK, 1),
        shot = new Command("Captura", Command.SCREEN, 2), stop = new Command("Parar sonido", Command.SCREEN, 3);
    private final Vector log = new Vector(), players = new Vector();
    private final Hashtable labels = new Hashtable(), events = new Hashtable(), startAt = new Hashtable();
    private final String[] results = new String[TESTS.length];
    private final Object lock = new Object();
    private Thread worker;
    private Timer timer;
    private boolean alive = true;
    private int pending, verdictTest, format, scroll;
    /** Current screen and highlighted entry of a list screen. */
    private volatile int screen, sel;
    private volatile String running;
    private volatile long t0;
    private String[] properties, types, controls, apis, m3g, gl;
    private boolean glRequested;
    private MidiFx midi;
    /** Performance tests (screens Graphics and Memory). M3G ones run in M3GBench and the Mascot Capsule v3 one in
     *  MascotBench, each loaded by name: a phone without that API only fails inside its class. */
    static final int BENCH_GEOMETRY = 1, BENCH_SUBMIT = 2, BENCH_TEXTURES = 3, BENCH_FILL = 4, BENCH_UPLOAD = 5, BENCH_MEMORY = 6,
        BENCH_MASCOT = 7, BENCH_COMPARE = 8, BENCH_ALL = 9;
    private static final int MEMORY_CAP_KB = 8192, MEMORY_BLOCK_KB = 64;
    private static final String[] BENCH_NAMES = {null, "Mallas: 12 a 3072 triangulos", "Envio: TRI / QUAD / FILA (768)",
        "Texturas: 16 a 512 px", "Relleno de pantalla x1-x8", "Subida de vertices (skinning)",
        "Memoria: reservar hasta " + MEMORY_CAP_KB / 1024 + " MB (puede cerrar la app)",
        "Mascot Capsule v3: mallas, envio, texturas, relleno", "M3G contra Mascot: cual conviene aqui", "Todo (1-5, sin memoria)"};
    /** The four loads of the comparison, measured with both APIs (frame ms, which includes the work a driver leaves
     *  for flushGraphics). Same meshes as the separate tests. */
    static final String[] COMPARE_NAMES = {"mallas 768 tri", "mallas 3072 tri", "textura 128 px (384 tri)", "relleno pantalla x4"};
    /** A side wins a load when it is at least this much faster (percent of the other's frame time). */
    static final int COMPARE_MARGIN = 115;
    /** Frame ms x10 per API (0 M3G, 1 Mascot) and load; -1 = not measured. */
    private final int[][] compareMs = new int[2][COMPARE_NAMES.length];
    private final Vector compareLines = new Vector();
    private volatile int compareStage;
    private volatile String compareVerdict;
    private final String[] benchResults = new String[BENCH_NAMES.length];
    private final Vector benchLines = new Vector();
    private int benchRequest;
    private volatile boolean benchRunning;
    /** Short phases for the emulator smoke (set by reflection); a phone run never uses it. */
    boolean quick;
    private String saved;
    /** Screens. HOME lists the others; QUICK runs everything that needs no listening and ends on REPORT. */
    static final int HOME = 0, QUICK = 1, INFO = 2, GRAPHICS = 3, SOUND = 4, MEMORY = 5, REPORT = 6, LOG = 7, HELP = 8, VERDICT = 9;
    private static final String[] MENU = {null, "Prueba rapida (recomendada)", "Informacion del telefono", "Graficos 3D (M3G, Mascot)",
        "Sonido", "Memoria", "Informe y capturas", "Registro", "Ayuda"};
    private static final String[] MENU_HINT = {null, "Informacion + graficos + informe, unos 2 min, sin tocar nada",
        "Modelo, Java, APIs, M3G, OpenGL ES, audio", "Mallas, texturas, relleno, skinning; 7 Mascot", "9 pruebas; despues usted dice que oyo",
        "Cuanta memoria Java puede usar un port", "Ver, guardar en archivo, capturas PNG", "Todo lo ocurrido, con tiempos",
        "Teclas y como enviar los resultados"};
    /** Graphics screen entries: bench ids in list order. */
    private static final int[] GRAPHICS_TESTS = {BENCH_COMPARE, BENCH_ALL, BENCH_GEOMETRY, BENCH_SUBMIT, BENCH_TEXTURES, BENCH_FILL, BENCH_UPLOAD, BENCH_MASCOT};
    private boolean quickRun, captureRequest;
    private String toast;
    private long toastAt;
    private String[] reportCache;

    ProbeCanvas(KiscPortProbe midlet) {
        this.midlet = midlet;
        setFullScreenMode(false);
        addCommand(back); addCommand(shot); addCommand(stop); addCommand(exit); setCommandListener(this);
        add("KiscPort Probe " + KiscPortProbe.VERSION + " (KISC-LABS)");
    }

    void resume() {
        synchronized (lock) {
            if (worker == null) { worker = new Thread(this); worker.start(); }
        }
        if (timer == null) {
            timer = new Timer();
            timer.schedule(new TimerTask() { public void run() { repaint(); } }, 0, 250);
        }
    }

    void interrupted(String why) { add("INTERRUPCION: " + why + " (se cierran reproductores)"); closeAll(); }

    void shutdown() {
        synchronized (lock) { alive = false; lock.notify(); }
        if (timer != null) timer.cancel();
        closeAll();
    }

    public void commandAction(Command c, Displayable d) {
        if (c == exit) midlet.exit();
        else if (c == back) back();
        else if (c == shot) requestCapture();
        else if (c == stop) { closeAll(); add("Sonido parado por el usuario"); }
    }

    /** Arrows move or scroll, OK/5 chooses, left or Atras goes back, # captures this screen, * stops the sound.
     *  Digits pick an entry of a list directly (and answer the listening question). */
    protected void keyPressed(int code) {
        if (screen == VERDICT) {
            if (code >= KEY_NUM1 && code <= KEY_NUM4) { answer(code - KEY_NUM0); return; }
            if (code == KEY_NUM0) { answer(0); return; }
        }
        if (code == KEY_POUND) { requestCapture(); return; }
        if (code == KEY_STAR) { closeAll(); add("Sonido parado (*)"); return; }
        int action = 0;
        try { action = getGameAction(code); } catch (IllegalArgumentException e) { }
        if (code >= KEY_NUM0 && code <= KEY_NUM9) { digit(code - KEY_NUM0); return; }   // digits before game actions
        boolean ok = action == FIRE;
        if (action == LEFT) { back(); return; }
        if (action == UP) { if (isList()) { if (sel > 0) sel--; } else if (scroll > 0) scroll--; repaint(); return; }
        if (action == DOWN) { if (isList()) { if (sel < listSize() - 1) sel++; } else scroll++; repaint(); return; }
        if (ok || action == RIGHT) choose(sel);
    }

    private boolean isList() { return screen == HOME || screen == GRAPHICS || screen == SOUND || screen == VERDICT; }

    private int listSize() {
        if (screen == HOME) return MENU.length - 1;
        if (screen == GRAPHICS) return GRAPHICS_TESTS.length;
        if (screen == SOUND) return TESTS.length;                  // entry 0: WAV format
        if (screen == VERDICT) return options(verdictTest).length + 1;
        return 0;
    }

    private void back() {
        if (screen == VERDICT || screen == HOME) return;           // the question must be answered (0 skips)
        open(HOME, 0);
    }

    private void open(int next, int entry) {
        screen = next; sel = entry; scroll = 0; reportCache = null;
        if (next == INFO && gl == null) synchronized (lock) { glRequested = true; lock.notify(); }
        if (next == LOG) scroll = Integer.MAX_VALUE;                // newest lines first in view
        repaint();
    }

    /** Digit shortcuts: HOME 1-8 opens, SOUND 1-9 runs and 0 cycles the format, GRAPHICS
     *  1-5, 7 (Mascot), 8 (comparison) and 9 run the same numbered tests as before, REPORT 1 saves, MEMORY 5 starts. */
    private void digit(int d) {
        if (screen == HOME) { if (d >= 1 && d < MENU.length) choose(d - 1); return; }
        if (screen == SOUND) { if (d == 0) cycleFormat(); else runSound(d); return; }
        if (screen == GRAPHICS) { if (d >= 1 && d <= 5 || d == BENCH_MASCOT || d == BENCH_COMPARE || d == 9) runBench(d); return; }
        if (screen == REPORT && d == 1) { request(-1); return; }
        if (screen == MEMORY && d == 5) { runBench(BENCH_MEMORY); return; }
        if (d == 5) choose(sel);
    }

    private void choose(int entry) {
        switch (screen) {
            case HOME: {
                int item = entry + 1;
                if (item == QUICK) startQuick(); else open(item, 0);
                return;
            }
            case GRAPHICS: runBench(GRAPHICS_TESTS[entry]); return;
            case SOUND: if (entry == 0) cycleFormat(); else runSound(entry); return;
            case MEMORY: runBench(BENCH_MEMORY); return;
            case REPORT: request(-1); return;
            case VERDICT: answer(entry < options(verdictTest).length ? entry + 1 : 0); return;
        }
    }

    private boolean busy() {
        if (running != null || benchRunning || quickRun) { show("Espere: hay una prueba en curso"); return true; }
        return false;
    }

    private void cycleFormat() { if (busy()) return; format = (format + 1) % FORMATS.length; add("Formato WAV: " + FORMAT_NAMES[format]); repaint(); }

    private void runSound(int test) {
        if (test < 1 || test >= TESTS.length || busy()) return;
        sel = test;
        synchronized (lock) { pending = test; lock.notify(); }
    }

    private void runBench(int b) { if (!busy()) request(b); }

    private void request(int b) {
        if (b == -1 && (running != null || benchRunning)) { show("Espere a que termine la prueba"); return; }
        synchronized (lock) { benchRequest = b; lock.notify(); }
    }

    private void startQuick() {
        if (busy()) return;
        quickRun = true;
        open(QUICK, 0);
        add("== Prueba rapida: informacion, graficos 1-5 e informe");
        request(BENCH_ALL);
    }

    private void requestCapture() { synchronized (lock) { captureRequest = true; lock.notify(); } }

    private void show(String message) { toast = message; toastAt = System.currentTimeMillis(); repaint(); }

    private void answer(int answer) {
        verdict(answer);
        open(SOUND, verdictTestDone);
    }
    private int verdictTestDone;

    /** Answer options of a test, from its VERDICTS line ("1 text 2 text ..."); test 5 asks for a number. */
    private static String[] options(int test) {
        if (test == 5) return new String[] {"1 sonido", "2 a la vez", "3 a la vez", "4 a la vez"};
        String v = test > 0 && test < VERDICTS.length ? VERDICTS[test] : null;
        if (v == null) return new String[0];
        Vector o = new Vector();
        for (int k = 1; k <= 4; k++) {
            int at = v.indexOf(k + " ");
            if (at < 0) break;
            int end = v.indexOf(" " + (k + 1) + " ", at);
            o.addElement(v.substring(at + 2, end < 0 ? v.length() : end));
        }
        return array(o);
    }

    private void verdict(int answer) {
        int test = verdictTest;
        verdictTest = 0;
        String heard = answer == 0 ? "sin respuesta" : answerText(test, answer);
        add("T" + test + " OIDO: " + heard);
        if (results[test] != null) results[test] = results[test] + " | OIDO: " + heard;
    }

    private static String answerText(int test, int answer) {
        if (test == 5) return answer + " a la vez";
        String v = VERDICTS[test];
        int at = v.indexOf(answer + " ");
        if (at < 0) return "respuesta " + answer;
        int end = v.indexOf(" " + (answer + 1) + " ", at);
        return v.substring(at + 2, end < 0 ? v.length() : end);
    }

    // ---------------------------------------------------------------- worker

    public void run() {
        info();
        while (true) {
            int test, b = 0;
            synchronized (lock) {
                while (alive && pending == 0 && !glRequested && benchRequest == 0 && !captureRequest) { try { lock.wait(); } catch (InterruptedException e) { } }
                if (!alive) return;
                if (captureRequest) { captureRequest = false; test = -1; }
                else { test = pending; pending = 0; }
                if (test == 0 && benchRequest != 0) { b = benchRequest; benchRequest = 0; }
            }
            if (test == -1) { capture(); continue; }
            if (b != 0) { bench(b); continue; }                // outside the lock: keys stay responsive
            if (test == 0) { glProbe(); continue; }
            running = TESTS[test];
            t0 = System.currentTimeMillis();
            long free = free();
            add("== T" + test + " " + TESTS[test] + (usesFormat(test) ? " [" + FORMATS[format] + "]" : "") + " libre " + free / 1024 + " KB");
            String result;
            try { result = test(test); }
            catch (Throwable e) { result = "EXCEPCION " + e; }
            closeAll();
            add("T" + test + " API: " + result + "; libre tras cerrar " + free() / 1024 + " KB");
            results[test] = "T" + test + (usesFormat(test) ? " " + FORMATS[format] : "") + " API: " + result;
            running = null;
            if (VERDICTS[test] != null) { verdictTest = test; verdictTestDone = test; add("Pregunta: que oyo en T" + test); open(VERDICT, 0); }
        }
    }

    private static boolean usesFormat(int test) { return test == 1 || test == 2 || test == 4 || test == 5 || test == 6 || test == 7 || test == 8; }

    private String test(int test) throws Exception {
        String f = FORMATS[format];
        Player a, b, m;
        switch (test) {
            case 1:
                a = wav("A", "long", f);
                return started(a, "A") + wait(LONG_MS + 700) + ended("A", LONG_MS);
            case 2:
                a = wav("A", "long", f); b = wav("B", "beep", f);
                return started(a, "A") + pause(400) + started(b, "B") + wait(LONG_MS + 300) + ended("A", LONG_MS) + ended("B", BEEP_MS) + state(a, "A");
            case 3:
                m = midi("M", "melody");
                return started(m, "M") + wait(MELODY_MS + 600) + ended("M", MELODY_MS);
            case 4:
                a = wav("A", "long", f); m = midi("M", "melody");
                return started(a, "A") + pause(300) + started(m, "M") + wait(MELODY_MS + 400) + ended("A", LONG_MS) + ended("M", MELODY_MS);
            case 5: {
                a = wav("A", "long", f); m = midi("M", "melody"); b = wav("B", "beep", f);
                String r = started(a, "A") + pause(300) + started(m, "M") + pause(300) + started(b, "B") + pause(300) + tone(84, 300);
                return r + wait(MELODY_MS) + ended("A", LONG_MS) + ended("M", MELODY_MS) + ended("B", BEEP_MS);
            }
            case 6: return midiControl(f);
            case 7: return rapid(f);
            case 8: {
                a = wav("A", "long", f);
                String r = started(a, "A") + pause(300);
                for (int i = 0; i < 4; i++) r += tone(76 + i * 4, 150) + pause(200);
                return r + wait(LONG_MS) + ended("A", LONG_MS);
            }
            case 9: return matrix();
        }
        return "?";
    }

    /** Two notes alone, then a WAV tone and four notes over it; call times measured. */
    private String midiControl(String f) throws Exception {
        MidiFx fx = midiFx();
        if (fx == null) return "MIDIControl no disponible";
        String opened;
        Player holder = null;
        try { opened = fx.open(null); }
        catch (Throwable e) {
            add("device://midi: " + e);
            holder = midi("M", "melody");                     // prefetched, not started
            try { opened = fx.open(holder); } catch (Throwable x) { return "sin MIDIControl: " + x; }
        }
        add("MIDIControl: " + opened);
        midi = fx;
        int max = 0;
        for (int i = 0; i < 2; i++) { max = Math.max(max, drum(fx, 38)); sleep(350); }
        Player a = wav("A", "long", f);
        String r = "notas solas OK; " + started(a, "A") + pause(250);
        int[] keys = {38, 42, 49, 38};
        for (int i = 0; i < keys.length; i++) { max = Math.max(max, drum(fx, keys[i])); sleep(250); }
        r += wait(LONG_MS) + ended("A", LONG_MS) + " shortMidiEvent max " + max + " ms";
        fx.close(); midi = null;
        if (holder != null) close(holder);
        return r;
    }

    private int drum(MidiFx fx, int key) {
        int ms = fx.note(9, key, 120);
        add("+" + since() + " nota " + key + " (" + ms + " ms)");
        sleep(90);
        fx.note(9, key, 0);
        return ms;
    }

    /** Eight quick restarts per method; latency of each restart call; errors counted. */
    private String rapid(String f) throws Exception {
        StringBuffer r = new StringBuffer();
        Player b = wav("B", "beep", f);
        r.append(restarts(b, "WAV")).append("; ");
        close(b); sleep(500);
        Player x = midi("X", "fx");
        r.append(restarts(x, "MIDI")).append("; ");
        close(x); sleep(500);
        MidiFx fx = midiFx();
        if (fx != null) {
            try {
                add("MIDIControl: " + fx.open(null));
                int max = 0, sum = 0;
                for (int i = 0; i < 8; i++) { int ms = fx.note(9, 38, 120); sum += ms; max = Math.max(max, ms); sleep(70); fx.note(9, 38, 0); sleep(80); }
                r.append("MIDIControl media ").append(sum / 8).append(" max ").append(max).append(" ms; ");
            } catch (Throwable e) { r.append("MIDIControl ").append(e).append("; "); }
            fx.close();
            sleep(500);
        }
        int max = 0, sum = 0, errors = 0;
        for (int i = 0; i < 8; i++) {
            long t = System.currentTimeMillis();
            try { Manager.playTone(84, 100, 90); } catch (Throwable e) { errors++; }
            int ms = (int)(System.currentTimeMillis() - t); sum += ms; max = Math.max(max, ms);
            sleep(150);
        }
        r.append("playTone media ").append(sum / 8).append(" max ").append(max).append(" ms err ").append(errors);
        return r.toString();
    }

    private String restarts(Player p, String name) {
        if (p == null) return name + " no creado";
        int max = 0, sum = 0, errors = 0;
        for (int i = 0; i < 8; i++) {
            long t = System.currentTimeMillis();
            try {
                if (p.getState() == Player.STARTED) p.stop();
                p.setMediaTime(0);
                p.start();
            } catch (Throwable e) { errors++; if (errors == 1) add(name + " reinicio: " + e); }
            int ms = (int)(System.currentTimeMillis() - t); sum += ms; max = Math.max(max, ms);
            sleep(150);
        }
        return name + " reinicio media " + sum / 8 + " max " + max + " ms err " + errors;
    }

    /** API-only: every WAV variant, both MIDI files and a tone, one after another. */
    private String matrix() {
        StringBuffer r = new StringBuffer();
        int ok = 0, total = 0;
        add("Se reproducen en orden: " + join(FORMATS) + ", melody, fx, tono");
        for (int i = 0; i < FORMATS.length; i++) {
            total++;
            long before = free();
            Player p = wav(FORMATS[i], "long", FORMATS[i]);
            if (p == null) { r.append(FORMATS[i]).append(" NO; "); continue; }
            add(FORMATS[i] + " heap usado " + (before - free()) / 1024 + " KB");
            String s = started(p, FORMATS[i]);
            sleep(LONG_MS + 500);
            String e = ended(FORMATS[i], LONG_MS);
            if (s.indexOf("FALLO") < 0) ok++;
            r.append(FORMATS[i]).append(s.indexOf("FALLO") < 0 ? " OK" : " FALLO").append(e).append("; ");
            close(p);
        }
        String[] mid = {"melody", "fx"};
        int[] length = {MELODY_MS, FX_MS};
        for (int i = 0; i < mid.length; i++) {
            total++;
            Player p = midi(mid[i], mid[i]);
            if (p == null) { r.append(mid[i]).append(" NO; "); continue; }
            String s = started(p, mid[i]);
            sleep(length[i] + 500);
            if (s.indexOf("FALLO") < 0) ok++;
            r.append(mid[i]).append(s.indexOf("FALLO") < 0 ? " OK" : " FALLO").append(ended(mid[i], length[i])).append("; ");
            close(p);
        }
        r.append(tone(72, 300));
        return ok + "/" + total + " iniciados: " + r.toString();
    }

    // ---------------------------------------------------------------- performance

    /** Runs a performance test (or saves the report, -1), from the worker thread. */
    private void bench(int b) {
        if (b == -1) { saveReport(); return; }
        running = BENCH_NAMES[b]; t0 = System.currentTimeMillis();
        add("== R" + b + " " + BENCH_NAMES[b] + (quick ? " (RAPIDA: solo emulador)" : ""));
        if (b == BENCH_MEMORY) { benchDone(b, memory(), null); return; }
        if (b == BENCH_COMPARE) {
            compareLines.removeAllElements(); compareVerdict = null; compareStage = 0;
            for (int api = 0; api < 2; api++) for (int i = 0; i < COMPARE_NAMES.length; i++) compareMs[api][i] = -1;
            benchRunning = true;
            if (!compareStart(0)) benchDone(b, new String[0], null);      // no M3G: straight to Mascot
            return;
        }
        boolean mascot = b == BENCH_MASCOT;
        try {
            // The API itself first: without it the test ends here, before taking the screen.
            if (mascot) Class.forName("com.mascotcapsule.micro3d.v3.Graphics3D");
            Bench bench = (Bench)Class.forName(mascot ? "kiscport.probe.MascotBench" : "kiscport.probe.M3GBench").newInstance();
            benchRunning = true;
            bench.start(this, Display.getDisplay(midlet), b, quick);
        } catch (Throwable e) {
            benchRunning = false;
            benchDone(b, new String[] {(mascot ? "MC3 Mascot Capsule v3 no disponible: " : "M3G no disponible: ") + e}, null);
        }
    }

    /** Starts one API of the comparison (0 M3G, 1 Mascot); false, with a line saying so, when it is missing. */
    private boolean compareStart(int stage) {
        try {
            if (stage == 1) Class.forName("com.mascotcapsule.micro3d.v3.Graphics3D");
            Bench bench = (Bench)Class.forName(stage == 1 ? "kiscport.probe.MascotBench" : "kiscport.probe.M3GBench").newInstance();
            bench.start(this, Display.getDisplay(midlet), BENCH_COMPARE, quick);
            return true;
        } catch (Throwable e) {
            compareLines.addElement("CMP " + (stage == 1 ? "Mascot Capsule v3" : "M3G") + " no disponible: " + e);
            return false;
        }
    }

    /** Frame time of one load (ms x10), reported by the bench of that API during the comparison. */
    void measured(int api, int load, int frameMs10) {
        if (api >= 0 && api < 2 && load >= 0 && load < COMPARE_NAMES.length) compareMs[api][load] = Math.max(1, frameMs10);
    }

    /** The comparison: each load, then the verdict over the sum of frame times (doing the four loads once). */
    private String[] compareFinish() {
        Vector v = new Vector();
        for (int i = 0; i < compareLines.size(); i++) v.addElement(compareLines.elementAt(i));
        long sumG = 0, sumM = 0;
        int both = 0, winsG = 0, winsM = 0;
        boolean anyG = false, anyM = false;
        for (int i = 0; i < COMPARE_NAMES.length; i++) {
            int g = compareMs[0][i], m = compareMs[1][i];
            anyG |= g > 0; anyM |= m > 0;
            if (g <= 0 || m <= 0) continue;
            both++; sumG += g; sumM += m;
            String who;
            if (g * 100 >= m * COMPARE_MARGIN) { winsM++; who = "Mascot " + times(g * 100 / m) + " mas rapido"; }
            else if (m * 100 >= g * COMPARE_MARGIN) { winsG++; who = "M3G " + times(m * 100 / g) + " mas rapido"; }
            else who = "parecidos";
            v.addElement("CMP " + COMPARE_NAMES[i] + ": M3G " + ms(g) + ", Mascot " + ms(m) + " -> " + who);
        }
        String verdict;
        if (both == 0) verdict = anyG ? "M3G (Mascot Capsule v3 no disponible o sin datos)" : anyM ? "Mascot Capsule v3 (M3G no disponible o sin datos)" : "sin datos";
        else if (sumG * 100 >= sumM * COMPARE_MARGIN) verdict = "Mascot Capsule v3, " + times(sumG * 100 / sumM) + " mas rapido en total (gana " + winsM + " de " + both + ")";
        else if (sumM * 100 >= sumG * COMPARE_MARGIN) verdict = "M3G, " + times(sumM * 100 / sumG) + " mas rapido en total (gana " + winsG + " de " + both + ")";
        else verdict = "empate (menos de " + (COMPARE_MARGIN - 100) + "% de diferencia): M3G, el estandar";
        if (quick) verdict += " [fases rapidas de emulador: no vale para un telefono]";
        compareVerdict = verdict;
        v.addElement("VEREDICTO para este telefono: " + verdict);
        return array(v);
    }

    private static String times(long percent) { return percent / 100 + "." + percent % 100 / 10 + "x"; }
    private static String ms(int ms10) { return ms10 / 10 + "." + ms10 % 10 + " ms"; }

    /** Called by M3GBench or MascotBench from its own thread when it ends (or by the memory test). The comparison
     *  calls it once per API: after M3G it starts Mascot, after Mascot it adds the verdict. */
    void benchDone(int b, String[] lines, Display display) {
        if (b == BENCH_COMPARE) {
            boolean stopped = false;
            for (int i = 0; i < lines.length; i++) {
                compareLines.addElement(lines[i]);
                if (lines[i].indexOf("etenida por el usuario") >= 0) stopped = true;
            }
            if (compareStage == 0 && !stopped) { compareStage = 1; if (compareStart(1)) return; }
            lines = compareFinish();
        }
        if (display != null) display.setCurrent(this);
        StringBuffer r = new StringBuffer();
        for (int i = 0; i < lines.length; i++) {
            add("R" + b + " " + lines[i]);
            synchronized (benchLines) { benchLines.addElement(lines[i]); }
            if (i > 0) r.append(" | ");
            r.append(lines[i]);
        }
        benchResults[b] = "R" + b + ": " + (b == BENCH_COMPARE && compareVerdict != null ? compareVerdict
            : lines.length == 0 ? "sin resultados" : r.toString());
        running = null; benchRunning = false; reportCache = null;
        if (b == BENCH_COMPARE && compareVerdict != null) show("Veredicto: " + compareVerdict);
        if (quickRun && b == BENCH_ALL) {
            quickRun = false;
            add("Prueba rapida terminada: se guarda el informe");
            open(REPORT, 0);
            request(-1);
        }
        repaint();
    }

    /** Java heap after a collection, then 64 KB blocks reserved until MEMORY_CAP_KB or OutOfMemoryError. The
     *  heap of these phones can grow: the reserved total, not freeMemory, tells how much a port can use. */
    private String[] memory() {
        Vector v = new Vector();
        Runtime rt = Runtime.getRuntime();
        System.gc();
        v.addElement("MEM tras GC: libre " + rt.freeMemory() / 1024 + " / total " + rt.totalMemory() / 1024 + " KB");
        Vector blocks = new Vector();
        int kb = 0, cap = quick ? 512 : MEMORY_CAP_KB;
        long t = System.currentTimeMillis();
        try {
            while (kb < cap) { blocks.addElement(new byte[MEMORY_BLOCK_KB * 1024]); kb += MEMORY_BLOCK_KB; }
            v.addElement("MEM reservados " + kb + " KB sin error (tope de la prueba)");
        } catch (OutOfMemoryError e) {
            blocks.removeAllElements();
            v.addElement("MEM OutOfMemoryError tras reservar " + kb + " KB");
        }
        int ms = (int)(System.currentTimeMillis() - t);
        long total = rt.totalMemory();
        blocks = null;
        System.gc();
        v.addElement("MEM total maximo visto " + total / 1024 + " KB; libre tras liberar " + rt.freeMemory() / 1024 + " KB; " + ms + " ms");
        return array(v);
    }

    /** Report: identity, platform, graphics, audio and performance results, one fact per line. */
    String[] report() {
        Vector v = new Vector();
        v.addElement("KISCPORT PROBE " + KiscPortProbe.VERSION);
        v.addElement("platform=" + System.getProperty("microedition.platform"));
        if (compareVerdict != null) v.addElement("VEREDICTO 3D: " + compareVerdict);
        if (properties != null) for (int i = 1; i < properties.length; i++) if (properties[i].indexOf("=null") < 0) v.addElement(properties[i]);
        Runtime rt = Runtime.getRuntime();
        v.addElement("heap libre/total=" + rt.freeMemory() / 1024 + "/" + rt.totalMemory() / 1024 + " KB");
        if (m3g != null) for (int i = 0; i < m3g.length; i++) v.addElement("m3g " + m3g[i]);
        if (apis != null) for (int i = 0; i < apis.length; i++) if (apis[i].startsWith("SI")) v.addElement("api " + apis[i].substring(4));
        for (int i = 1; i < results.length; i++) if (results[i] != null) v.addElement("audio " + results[i]);
        synchronized (benchLines) { for (int i = 0; i < benchLines.size(); i++) v.addElement("rend " + benchLines.elementAt(i)); }
        if (quick) v.addElement("AVISO: fases rapidas de emulador, no es un resultado de telefono");
        return array(v);
    }

    private void saveReport() {
        running = "Guardar informe"; t0 = System.currentTimeMillis();
        String[] lines = report();
        StringBuffer text = new StringBuffer();
        for (int i = 0; i < lines.length; i++) { text.append(lines[i]).append("\r\n"); System.out.println("PROBE-REPORT " + lines[i]); }
        try {
            Saver s = (Saver)Class.forName("kiscport.probe.ReportFile").newInstance();
            saved = s.save("kiscport-probe.txt", text.toString());
            add("Informe guardado en " + saved);
            show("Informe guardado");
        } catch (Throwable e) { saved = "no se pudo guardar (" + reason(e) + ")"; add("Informe " + saved + "; haga capturas con #"); show("No se pudo guardar el informe"); }
        running = null; reportCache = null;
    }

    // ---------------------------------------------------------------- players

    private Player wav(String label, String kind, String f) { return create(label, "/snd/" + kind + "_" + f + ".wav", WAV); }
    private Player midi(String label, String name) { return create(label, "/snd/" + name + ".mid", MIDI); }

    /** create + realize + prefetch with timings; tries each content type; null on failure (logged). */
    private Player create(String label, String resource, String[] contentTypes) {
        String last = null;
        for (int k = 0; k < contentTypes.length; k++) {
            Player p = null;
            long a = System.currentTimeMillis(), b = a, c = a;
            try {
                InputStream in = getClass().getResourceAsStream(resource);
                if (in == null) { add(label + ": recurso ausente " + resource); return null; }
                p = Manager.createPlayer(in, contentTypes[k]);
                b = System.currentTimeMillis();
                p.addPlayerListener(this);
                labels.put(p, label); events.put(label, new StringBuffer()); startAt.remove(label);
                p.realize(); c = System.currentTimeMillis();
                p.prefetch();
                long d = System.currentTimeMillis();
                players.addElement(p);
                add(label + " " + contentTypes[k] + " c" + (b - a) + " r" + (c - b) + " p" + (d - c) + " ms" + volume(p));
                return p;
            } catch (Throwable e) {
                last = contentTypes[k] + ": " + e;
                if (p != null) { labels.remove(p); close(p); }
            }
        }
        add(label + " FALLO crear " + last);
        return null;
    }

    private static String volume(Player p) {
        try {
            VolumeControl v = (VolumeControl)p.getControl("VolumeControl");
            return v == null ? " sin Volume" : " vol " + v.getLevel();
        } catch (Throwable e) { return " vol ?"; }
    }

    private String started(Player p, String label) {
        if (p == null) return label + " FALLO(no creado) ";
        long t = System.currentTimeMillis();
        try {
            p.start();
            int ms = (int)(System.currentTimeMillis() - t);
            startAt.put(label, new Long(since()));            // events are reported relative to this start
            add("+" + since() + " " + label + " start " + ms + " ms, estado " + stateName(p.getState()));
            return label + " start " + ms + "ms ";
        } catch (Throwable e) {
            add("+" + since() + " " + label + " start FALLO " + e);
            return label + " FALLO " + e + " ";
        }
    }

    private String tone(int note, int ms) {
        long t = System.currentTimeMillis();
        try {
            Manager.playTone(note, ms, 90);
            add("+" + since() + " tono " + note + " (" + (System.currentTimeMillis() - t) + " ms)");
            return "tono OK ";
        } catch (Throwable e) { add("tono FALLO " + e); return "tono FALLO "; }
    }

    private String state(Player p, String label) { return p == null ? "" : label + " final " + stateName(p.getState()) + " "; }

    /** Event summary of one player: first END_OF_MEDIA time against the expected length. */
    private String ended(String label, int expected) {
        StringBuffer e = (StringBuffer)events.get(label);
        if (e == null) return "";
        String s = e.toString();
        return "[" + label + (s.length() == 0 ? " sin eventos" : s) + " de " + expected + " ms] ";
    }

    public void playerUpdate(Player p, String event, Object data) {
        String label = (String)labels.get(p);
        if (label == null) return;
        long at = since();
        Long begun = (Long)startAt.get(label);
        long rel = begun == null ? at : at - begun.longValue();
        if (PlayerListener.STARTED.equals(event) || PlayerListener.VOLUME_CHANGED.equals(event) || PlayerListener.CLOSED.equals(event)) return;
        add("+" + at + " " + label + " " + event + (data == null ? "" : " " + data));
        StringBuffer e = (StringBuffer)events.get(label);
        if (e == null) return;
        if (PlayerListener.END_OF_MEDIA.equals(event)) e.append(" fin ").append(rel);
        else if (PlayerListener.STOPPED.equals(event)) e.append(" STOP ").append(rel);
        else if (PlayerListener.ERROR.equals(event) || PlayerListener.DEVICE_UNAVAILABLE.equals(event)) e.append(' ').append(event).append(' ').append(rel);
    }

    private void closeAll() {
        Vector copy;
        synchronized (players) { copy = new Vector(); for (int i = 0; i < players.size(); i++) copy.addElement(players.elementAt(i)); players.removeAllElements(); }
        for (int i = 0; i < copy.size(); i++) close((Player)copy.elementAt(i));
        if (midi != null) { midi.close(); midi = null; }
    }

    private void close(Player p) {
        if (p == null) return;
        players.removeElement(p);
        labels.remove(p);                                    // no CLOSED noise, no reference kept
        try { p.close(); } catch (Throwable e) { }
    }

    private MidiFx midiFx() {
        try { return (MidiFx)Class.forName("kiscport.probe.MidiImpl").newInstance(); }
        catch (Throwable e) { add("MIDIControl clase: " + e); return null; }
    }

    // ---------------------------------------------------------------- information

    private void info() {
        String[] keys = {"microedition.platform", "com.sonyericsson.java.platform", "microedition.configuration", "microedition.profiles",
            "microedition.media.version", "supports.mixing", "supports.audio.capture", "supports.video.capture", "supports.recording",
            "audio.encodings", "streamable.contents", "microedition.amms.version", "microedition.m3g.version", "microedition.locale",
            "microedition.jtwi.version", "microedition.msa.version", "microedition.io.file.FileConnection.version",
            "microedition.sensor.version", "microedition.global.version"};
        Vector v = new Vector();
        for (int i = 0; i < keys.length; i++) v.addElement(keys[i] + "=" + System.getProperty(keys[i]));
        Display d = Display.getDisplay(midlet);
        v.addElement("Canvas " + getWidth() + "x" + getHeight() + " colores " + d.numColors() + " alfa " + d.numAlphaLevels() + " doble buffer " + isDoubleBuffered());
        properties = array(v);
        add("supports.mixing=" + System.getProperty("supports.mixing") + " (NO garantiza combinaciones de formatos)");
        try {
            String[] t = Manager.getSupportedContentTypes(null), p = Manager.getSupportedProtocols(null);
            v = new Vector();
            v.addElement("Tipos " + t.length + ", protocolos " + p.length);
            for (int i = 0; i < t.length; i++) v.addElement("T " + t[i]);
            for (int i = 0; i < p.length; i++) v.addElement("P " + p[i]);
            types = array(v);
        } catch (Throwable e) { types = new String[] {"Manager: " + e}; }
        String[][] classes = {
            {"MMAPI Manager (JSR-135/MIDP2)", "javax.microedition.media.Manager"},
            {"VolumeControl", "javax.microedition.media.control.VolumeControl"},
            {"ToneControl", "javax.microedition.media.control.ToneControl"},
            {"MIDIControl (JSR-135)", "javax.microedition.media.control.MIDIControl"},
            {"TempoControl", "javax.microedition.media.control.TempoControl"},
            {"PitchControl", "javax.microedition.media.control.PitchControl"},
            {"RateControl", "javax.microedition.media.control.RateControl"},
            {"StopTimeControl", "javax.microedition.media.control.StopTimeControl"},
            {"AMMS (JSR-234)", "javax.microedition.amms.GlobalManager"},
            {"AMMS audio 3D", "javax.microedition.amms.control.audio3d.LocationControl"},
            {"M3G (JSR-184)", "javax.microedition.m3g.Graphics3D"},
            {"Mascot Capsule v3", "com.mascotcapsule.micro3d.v3.Graphics3D"},
            {"OpenGL ES EGL (JSR-239)", "javax.microedition.khronos.egl.EGL10"},
            {"OpenGL ES GL10 (JSR-239)", "javax.microedition.khronos.opengles.GL10"},
            {"OpenGL ES GL11 (JSR-239)", "javax.microedition.khronos.opengles.GL11"},
            {"SVG (JSR-226)", "javax.microedition.m2g.SVGImage"},
            {"Sensores (JSR-256)", "javax.microedition.sensor.SensorManager"},
            {"FileConnection (JSR-75)", "javax.microedition.io.file.FileConnection"},
            {"Nokia UI DeviceControl", "com.nokia.mid.ui.DeviceControl"},
            {"GameCanvas (MIDP 2.0)", "javax.microedition.lcdui.game.GameCanvas"}};
        v = new Vector();
        for (int i = 0; i < classes.length; i++) {
            String found;
            try { Class.forName(classes[i][1]); found = "SI"; } catch (Throwable e) { found = "no"; }
            v.addElement(found + "  " + classes[i][0]);
        }
        apis = array(v);
        m3g = optional("kiscport.probe.M3GInfo");
        controls();
        add("Informacion lista");
    }

    private String[] optional(String name) {
        try { return ((Info)Class.forName(name).newInstance()).lines(); }
        catch (Throwable e) { return new String[] {"No disponible: " + e}; }
    }

    private void glProbe() {
        synchronized (lock) { glRequested = false; }
        if (gl != null) return;
        running = "OpenGL ES";
        gl = new String[] {"Probando..."};
        gl = optional("kiscport.probe.GLInfo");
        running = null;
    }

    /** Controls of a realized WAV, MIDI, tone device and MIDI device player. */
    private void controls() {
        Vector v = new Vector();
        String[] names = {"VolumeControl", "MIDIControl", "ToneControl", "TempoControl", "PitchControl", "RateControl", "StopTimeControl", "MetaDataControl"};
        String[] what = {"WAV 8k8", "MIDI", "device://tone", "device://midi"};
        for (int k = 0; k < what.length; k++) {
            Player p = null;
            try {
                if (k == 0) p = Manager.createPlayer(getClass().getResourceAsStream("/snd/beep_8k8.wav"), "audio/x-wav");
                else if (k == 1) p = Manager.createPlayer(getClass().getResourceAsStream("/snd/fx.mid"), "audio/midi");
                else p = Manager.createPlayer(k == 2 ? "device://tone" : "device://midi");
                p.realize();
                StringBuffer s = new StringBuffer(what[k]).append(':');
                for (int i = 0; i < names.length; i++) if (p.getControl(names[i]) != null) s.append(' ').append(names[i]);
                Control[] all = p.getControls();
                s.append(" (").append(all == null ? 0 : all.length).append(" controles)");
                v.addElement(s.toString());
            } catch (Throwable e) { v.addElement(what[k] + ": " + e); }
            if (p != null) try { p.close(); } catch (Throwable e) { }
        }
        controls = array(v);
    }

    // ---------------------------------------------------------------- screen

    private static final int BG = 0x0b1220, BAR = 0x16233a, TEXT = 0xe8eef8, MUTED = 0x8fa3bf, ACCENT = 0x38d0ff, WARN = 0xffd040, GOOD = 0x70e090;

    protected void paint(Graphics g) { draw(g, getWidth(), getHeight()); }

    /** The whole screen, also used for captures (drawn into an Image of the same size). */
    private void draw(Graphics g, int w, int h) {
        int lh = font.getHeight();
        g.setFont(font);
        g.setColor(BG); g.fillRect(0, 0, w, h);
        g.setColor(BAR); g.fillRect(0, 0, w, lh + 4);
        String title = title();
        g.setColor(ACCENT); g.drawString(cut(title, w - 6), 3, 2, Graphics.TOP | Graphics.LEFT);
        if (font.stringWidth(title + "    KiscPort Probe") < w) { g.setColor(MUTED); g.drawString("KiscPort Probe", w - 3, 2, Graphics.TOP | Graphics.RIGHT); }
        int top = lh + 6, bottom = h - lh - 4;
        String status = running != null ? "Ejecutando: " + running + "  " + since() / 1000 + " s" : null;
        if (status != null) { g.setColor(WARN); top = wrap(g, status, top, w, bottom) + 2; }
        if (isList()) list(g, top, w, bottom); else text(g, body(), top, w, bottom);
        g.setColor(BAR); g.fillRect(0, h - lh - 3, w, lh + 3);
        boolean fresh = toast != null && System.currentTimeMillis() - toastAt < 3000;
        g.setColor(fresh ? GOOD : MUTED);
        g.drawString(fresh ? toast : footer(), 3, h - lh - 1, Graphics.TOP | Graphics.LEFT);
    }

    private String title() {
        switch (screen) {
            case HOME: return "Menu";
            case VERDICT: return "Que oyo? (T" + verdictTest + ")";
            default: return MENU[screen];
        }
    }

    private String footer() {
        switch (screen) {
            case HOME: return "OK entrar  # captura";
            case VERDICT: return "OK o 1-4 responder  0 omitir";
            case SOUND: return "OK probar  0 formato  * parar  < volver";
            case GRAPHICS: return "OK probar  # captura  < volver";
            case REPORT: return "OK o 1 guardar  # captura  < volver";
            case MEMORY: return "OK o 5 empezar  < volver";
            default: return "arriba/abajo leer  # captura  < volver";
        }
    }

    /** Text screens, one entry per string (wrapped). */
    private String[] body() {
        switch (screen) {
            case QUICK: {
                boolean done = benchResults[BENCH_ALL] != null;
                return new String[] {"Se mide el telefono sin tocar nada.", "",
                    "1. Informacion del telefono: " + (properties != null ? "lista" : "leyendo"),
                    "2. Graficos 3D (unos 70 s): " + (done ? "hecho" : benchRunning ? "en curso" : "en espera"),
                    "3. Informe: " + (saved != null ? saved : "al terminar"), "",
                    "La pantalla 3D muestra el progreso. Ahi, 0 guarda una captura y * la detiene; las demas teclas no la paran (pulse una para que la pantalla no se apague).",
                    "El sonido se prueba aparte (menu Sonido): necesita que usted escuche."};
            }
            case INFO: return concat(concat(concat(section("TELEFONO", properties), section("APIS PRESENTES", apis)),
                concat(section("M3G", m3g), section("OPENGL ES", gl))), concat(section("SONIDO: CONTROLES", controls), section("SONIDO: TIPOS", types)));
            case MEMORY: {
                String[] head = {"Reserva bloques de 64 KB hasta " + MEMORY_CAP_KB / 1024 + " MB o hasta que el telefono se queda sin memoria.",
                    "Dice cuanta memoria Java puede usar de verdad un port.", "Aviso: en algunos telefonos cierra la aplicacion. Si pasa, el ultimo valor del Registro es el limite.", ""};
                return benchResults[BENCH_MEMORY] == null ? concat(head, new String[] {"OK o 5 para empezar."}) : concat(head, split(benchResults[BENCH_MEMORY]));
            }
            case REPORT: {
                if (reportCache == null) reportCache = concat(new String[] {saved == null ? "OK o 1 guarda kiscport-probe.txt" : "Archivo: " + saved,
                    "# guarda una captura PNG de esta pantalla (kiscport-NNN.png).", ""}, report());
                return reportCache;
            }
            case LOG: {
                synchronized (log) { String[] out = new String[log.size()]; log.copyInto(out); return out; }
            }
            case HELP: return new String[] {"Moverse: joystick arriba/abajo. Elegir: OK o 5. Volver: izquierda o Atras.",
                "#: captura PNG de la pantalla actual. *: para el sonido.",
                "Empiece por Prueba rapida. Luego Sonido (responda lo que oyo) y, si acepta el riesgo, Memoria.",
                "Graficos 3D, 8: mide M3G y Mascot Capsule v3 con las mismas cargas y dice cual conviene en este telefono. 7: todas las pruebas con Mascot.",
                "Pruebas 3D: 0 captura, * (asterisco) detiene; cualquier otra tecla no hace nada, sirve para que la pantalla no se apague.",
                "Informe y capturas: OK guarda el informe. Los archivos van a la tarjeta o a la memoria del telefono; puede pedir permiso.",
                "Las capturas son de KiscPort Probe: Java ME no puede leer la pantalla de otras aplicaciones.",
                "Envie kiscport-probe.txt y las capturas en un issue de GitHub con el modelo y el firmware.",
                "Version " + KiscPortProbe.VERSION + ", KISC-LABS. Sin contenido de ningun juego."};
        }
        return new String[0];
    }

    private void list(Graphics g, int top, int w, int bottom) {
        int lh = font.getHeight(), n = listSize();
        String[] head = null;
        if (screen == VERDICT) head = new String[] {TESTS[verdictTest] + ": que se oyo?"};
        if (screen == GRAPHICS) head = compareVerdict != null ? new String[] {"Veredicto: " + compareVerdict}
            : new String[] {"Cada prueba dibuja 2 s por paso. Resultados tambien en Informe."};
        if (head != null) { g.setColor(MUTED); for (int i = 0; i < head.length; i++) top = wrap(g, head[i], top, w, bottom) + 2; }
        int rows = 2, per = lh * rows + 3, visible = Math.max(1, (bottom - top) / per);
        int first = sel - visible + 1; if (first < 0) first = 0;
        for (int i = first; i < n && top + per <= bottom + lh; i++) {
            boolean on = i == sel;
            if (on) { g.setColor(BAR); g.fillRect(0, top - 1, w, per - 1); g.setColor(ACCENT); g.fillRect(0, top - 1, 3, per - 1); }
            g.setColor(on ? TEXT : 0xc8d2e0);
            g.drawString(entry(i), 7, top, Graphics.TOP | Graphics.LEFT);
            String hint = hint(i);
            if (hint != null) { g.setColor(MUTED); g.drawString(cut(hint, w - 10), 7, top + lh, Graphics.TOP | Graphics.LEFT); }
            top += per;
        }
    }

    private String entry(int i) {
        switch (screen) {
            case HOME: return (i + 1) + "  " + MENU[i + 1];
            case GRAPHICS: { int b = GRAPHICS_TESTS[i]; return b + "  " + BENCH_NAMES[b]; }
            case SOUND: return i == 0 ? "0  Formato WAV: " + FORMAT_NAMES[format] : i + "  " + TESTS[i];
            case VERDICT: { String[] o = options(verdictTest); return i < o.length ? (i + 1) + "  " + o[i] : "0  Omitir"; }
        }
        return "";
    }

    private String hint(int i) {
        switch (screen) {
            case HOME: return MENU_HINT[i + 1];
            case GRAPHICS: { String r = benchResults[GRAPHICS_TESTS[i]]; return r == null ? "sin probar" : "hecho: " + r.substring(r.indexOf(':') + 2); }
            case SOUND: return i == 0 ? "OK cambia el formato" : results[i] == null ? "sin probar" : results[i];
        }
        return null;
    }

    private void text(Graphics g, String[] lines, int top, int w, int bottom) {
        int lh = font.getHeight(), room = (bottom - top) / lh, total = 0;
        for (int i = 0; i < lines.length; i++) total += lineCount(lines[i], w);
        int maxScroll = total > room ? total - room : 0;
        if (scroll > maxScroll) scroll = maxScroll;
        int skip = scroll, y = top;
        for (int i = 0; i < lines.length && y < bottom; i++) {
            String l = lines[i] == null ? "" : lines[i];
            boolean header = l.length() > 0 && l.charAt(0) == '\u25a0';
            g.setColor(header ? ACCENT : TEXT);
            if (header) l = l.substring(1);
            int rows = lineCount(l, w);
            if (skip >= rows) { skip -= rows; continue; }
            y = wrap(g, l, y, w, bottom, skip); skip = 0;
        }
        if (maxScroll > 0) {                                          // scroll bar
            int bar = Math.max(lh, (bottom - top) * room / total), at = top + (bottom - top - bar) * scroll / maxScroll;
            g.setColor(BAR); g.fillRect(w - 3, top, 3, bottom - top); g.setColor(ACCENT); g.fillRect(w - 3, at, 3, bar);
        }
    }

    /** A capture of the current screen as PNG, from the worker (file IO never blocks the keys). */
    private void capture() {
        int w = getWidth(), h = getHeight();
        try {
            Image image = Image.createImage(w, h);
            draw(image.getGraphics(), w, h);
            Saver s = (Saver)Class.forName("kiscport.probe.ReportFile").newInstance();
            String where = s.savePng("kiscport-", image);
            image = null;
            add("Captura guardada en " + where);
            show("Captura guardada");
        } catch (Throwable e) { add("Captura no guardada: " + reason(e)); show("Captura no guardada (ver Registro)"); }
    }

    /** A readable cause: the message without the Java class name when there is one. */
    static String reason(Throwable e) {
        if (e instanceof SecurityException) return "permiso denegado";
        if (e instanceof ClassNotFoundException || e instanceof NoClassDefFoundError) return "el telefono no tiene acceso a archivos (JSR-75)";
        String m = e.getMessage();
        return m == null || m.length() == 0 ? e.toString() : m;
    }

    private static String[] section(String title, String[] lines) {
        return concat(new String[] {"", "\u25a0" + title}, lines == null ? new String[] {"(leyendo)"} : lines);
    }

    private static String[] split(String result) {
        Vector v = new Vector();
        int at = result.indexOf(": ") + 2;
        while (at < result.length()) {
            int end = result.indexOf(" | ", at);
            if (end < 0) end = result.length();
            v.addElement(result.substring(at, end));
            at = end + 3;
        }
        return array(v);
    }

    private static String[] concat(String[] a, String[] b) {
        if (a == null) return b;
        if (b == null) return a;
        String[] out = new String[a.length + b.length];
        System.arraycopy(a, 0, out, 0, a.length); System.arraycopy(b, 0, out, a.length, b.length);
        return out;
    }

    private String cut(String s, int w) {
        if (font.stringWidth(s) <= w) return s;
        int end = s.length();
        while (end > 0 && font.stringWidth(s.substring(0, end)) + font.stringWidth("...") > w) end--;
        return s.substring(0, end) + "...";
    }

    private int lineCount(String s, int w) {
        if (s == null || s.length() == 0) return 1;
        int rows = 0, begin = 0;
        while (begin < s.length()) { begin = lineEnd(s, begin, w); rows++; }
        return rows;
    }

    /** End of the row that starts at begin: breaks at the last space that fits, or mid-word if none. */
    private int lineEnd(String s, int begin, int w) {
        int end = begin, px = 0, space = -1;
        while (end < s.length() && px + font.charWidth(s.charAt(end)) <= w - 8) {
            if (s.charAt(end) == ' ') space = end;
            px += font.charWidth(s.charAt(end)); end++;
        }
        if (end == begin) return begin + 1;
        if (end < s.length() && space > begin) return space + 1;
        return end;
    }

    private int wrap(Graphics g, String s, int y, int w, int h) { return wrap(g, s, y, w, h, 0); }

    /** Draws s wrapped from y, skipping its first 'skip' rows; returns the y after it. */
    private int wrap(Graphics g, String s, int y, int w, int h, int skip) {
        if (s == null || s.length() == 0) return skip > 0 ? y : y + font.getHeight();
        int begin = 0, lh = font.getHeight();
        while (begin < s.length() && y < h) {
            int end = lineEnd(s, begin, w);
            if (skip > 0) skip--;
            else { g.drawSubstring(s, begin, end - begin, 3, y, 0); y += lh; }
            begin = end;
        }
        return y;
    }

    // ---------------------------------------------------------------- helpers

    private void add(String line) {
        synchronized (log) {
            log.addElement(line);
            if (log.size() > 200) log.removeElementAt(0);
        }
        System.out.println("PROBE " + line);
    }

    private long since() { return System.currentTimeMillis() - t0; }
    private String pause(int ms) { sleep(ms); return ""; }
    private String wait(int ms) { sleep(ms); return "; "; }
    private static void sleep(long ms) { try { Thread.sleep(ms); } catch (InterruptedException e) { } }
    private static long free() { return Runtime.getRuntime().freeMemory(); }

    private static String stateName(int s) {
        return s == Player.STARTED ? "STARTED" : s == Player.PREFETCHED ? "PREFETCHED" : s == Player.REALIZED ? "REALIZED"
            : s == Player.UNREALIZED ? "UNREALIZED" : s == Player.CLOSED ? "CLOSED" : "?" + s;
    }

    private static String join(String[] a) {
        StringBuffer s = new StringBuffer();
        for (int i = 0; i < a.length; i++) { if (i > 0) s.append(", "); s.append(a[i]); }
        return s.toString();
    }

    private static String[] array(Vector v) { String[] out = new String[v.size()]; v.copyInto(out); return out; }

    static void sort(String[] a, int n) {
        for (int i = 1; i < n; i++) {
            String x = a[i];
            int j = i - 1;
            while (j >= 0 && a[j].compareTo(x) > 0) { a[j + 1] = a[j]; j--; }
            a[j + 1] = x;
        }
    }
}
