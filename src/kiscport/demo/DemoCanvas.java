package kiscport.demo;

import java.io.IOException;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.game.GameCanvas;
import kiscport.diagnostics.FrameStats;
import kiscport.diagnostics.Hud;
import kiscport.input.InputState;
import kiscport.platform.KiscPortMIDlet;
import kiscport.platform.LoopPacer;
import kiscport.resources.TextTable;
import kiscport.ui.StartupIntro;

/** Engine demo: KISC-LABS intro, then a colour cube (M3G, loaded by name) turned with the joystick, a caption
 *  from a KTT1 text table and the FPS overlay. Loop: input -> fixed 20 ms steps -> draw -> flush -> rest. Input is
 *  cleared on pause and hide so no key stays pressed. Original content only. */
public final class DemoCanvas extends GameCanvas implements Runnable {
    public static final int TARGET_FPS = 30, TICK_MS = 20, TEXT_CARDS = 4;
    private final KiscPortMIDlet midlet;
    private final InputState input = new InputState();
    private final FrameStats stats = new FrameStats();
    private final Hud hud = new Hud();
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private StartupIntro intro;
    private DemoScene scene;
    private String sceneError;
    private String[] texts;
    private Thread thread;
    private volatile boolean running, paused, visible = true;
    private boolean showHud = true;
    /** Demo state, advanced in fixed steps: angles in tenths of a degree and the spin per step. */
    private int angleX = 200, angleY, spinX = 0, spinY = 15, accumulator, ticks, frames;
    private long last = -1;
    private int seenEvents;

    public DemoCanvas(KiscPortMIDlet midlet) {
        super(false);
        this.midlet = midlet;
        setFullScreenMode(true);
        String introSetting = midlet.getAppProperty("KiscPort-Intro");
        if (!"off".equals(introSetting)) intro = new StartupIntro(midlet.getAppProperty("KiscPort-Intro-Prefix"), getWidth(), getHeight());
        try { texts = TextTable.read(getClass().getResourceAsStream("/kiscport/demo/text.ktt"), TEXT_CARDS); }
        catch (IOException e) { texts = null; }
        try { scene = (DemoScene)Class.forName("kiscport.renderer.CubeScene").newInstance(); }
        catch (Throwable e) { sceneError = "M3G no disponible"; }
    }

    public synchronized void resumeLoop() {
        paused = false; last = -1; input.clear(); stats.rebase(System.currentTimeMillis());
        if (intro != null) intro.rebase(System.currentTimeMillis());
        if (thread == null) { running = true; thread = new Thread(this); thread.start(); }
        notifyAll();
    }
    public synchronized void pauseLoop() { paused = true; input.clear(); }
    public synchronized void stopLoop() { running = false; notifyAll(); }
    protected void showNotify() { synchronized (this) { visible = true; last = -1; input.clear(); notifyAll(); } }
    protected void hideNotify() { synchronized (this) { visible = false; input.clear(); } }

    protected void keyPressed(int code) {
        int action = 0;
        try {
            int a = getGameAction(code);
            action = a == LEFT ? InputState.LEFT : a == RIGHT ? InputState.RIGHT : a == UP ? InputState.JUMP
                : a == DOWN ? InputState.SLIDE : a == FIRE ? InputState.FIRE : 0;
        } catch (IllegalArgumentException e) { }
        if (code == KEY_NUM5) action = InputState.FIRE;
        input.press(code, action);                                          // other keys only skip the intro
    }
    protected void keyReleased(int code) { input.release(code); }

    public void run() {
        Graphics g = getGraphics();
        while (running) {
            synchronized (this) {
                while (running && (paused || !visible)) { try { wait(); } catch (InterruptedException e) { } }
                if (!running) return;
            }
            long now = System.currentTimeMillis();
            int keys = input.consume();
            int w = getWidth(), h = getHeight();
            if (intro != null && !intro.finished()) {
                int events = input.eventCount();
                intro.advance(now, events != seenEvents);                      // any key skips the intro
                seenEvents = events;
                intro.draw(g, w, h);
                if (intro.finished()) { intro.dispose(); intro = null; stats.reset(now); last = now; }
            } else {
                step(now, keys);
                if (scene != null) scene.render(g, w, h, angleX / 10, angleY / 10);
                else { g.setColor(0x0b1220); g.fillRect(0, 0, w, h); }
                overlay(g, w, h);
            }
            flushGraphics();
            frames++;
            int work = (int)(System.currentTimeMillis() - now);
            stats.frame(System.currentTimeMillis(), work);
            if ((frames & 31) == 0) stats.sampleMemory();
            try { Thread.sleep(LoopPacer.restMs(TARGET_FPS, work)); } catch (InterruptedException e) { }
        }
    }

    /** Elapsed time in whole 20 ms steps (at most 250 ms per frame): the cube turns at the same speed at any FPS. */
    private void step(long now, int keys) {
        int elapsed = last < 0 ? 0 : (int)(now - last);
        last = now;
        if (elapsed > 250) elapsed = 250;
        if (((keys >> 8) & InputState.FIRE) != 0) showHud = !showHud;
        if ((keys & InputState.LEFT) != 0) spinY = -30; else if ((keys & InputState.RIGHT) != 0) spinY = 30;
        if ((keys & InputState.JUMP) != 0) spinX = -30; else if ((keys & InputState.SLIDE) != 0) spinX = 30; else spinX = 0;
        accumulator += elapsed;
        while (accumulator >= TICK_MS) {
            accumulator -= TICK_MS; ticks++;
            angleY = (angleY + spinY + 3600) % 3600; angleX = (angleX + spinX + 3600) % 3600;
        }
    }

    private void overlay(Graphics g, int w, int h) {
        g.setFont(font);
        if (showHud) hud.stats(g, stats, TARGET_FPS, w);
        int y = h - font.getHeight() * 2 - 4;
        g.setColor(0x9aa9bd);
        if (sceneError != null) g.drawString(sceneError, w / 2, h / 2, Graphics.TOP | Graphics.HCENTER);
        if (texts != null) {
            g.drawString(texts[0], w / 2, y, Graphics.TOP | Graphics.HCENTER);
            g.drawString(texts[1 + (ticks / 150) % (TEXT_CARDS - 1)], w / 2, y + font.getHeight(), Graphics.TOP | Graphics.HCENTER);
        }
    }

    /** For tests: current yaw (tenths of a degree), fixed steps run, frames drawn. */
    public int angleY() { return angleY; }
    public int ticks() { return ticks; }
    public int frames() { return frames; }
}
