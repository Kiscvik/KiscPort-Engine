package kiscport.platform;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;
import kiscport.demo.DemoCanvas;

/** Entry point of the KiscPort engine demo. */
public final class KiscPortMIDlet extends MIDlet implements BacklightTarget {
    private DemoCanvas canvas;
    private boolean destroyed;
    protected void startApp() {
        if (destroyed) return;
        if (canvas == null) canvas = new DemoCanvas(this);
        Display.getDisplay(this).setCurrent(canvas);
        canvas.resumeLoop();
    }
    protected void pauseApp() { if (canvas != null) canvas.pauseLoop(); }
    protected void destroyApp(boolean unconditional) {
        destroyed = true;
        if (canvas != null) canvas.stopLoop();
    }
    public void exit() { destroyApp(true); notifyDestroyed(); }
    public boolean requestBacklight(int durationMs) {
        // W900i physical test: positive durations cause visible flashing. Only cancellation is allowed.
        if (durationMs != 0) return false;
        return Display.getDisplay(this).flashBacklight(0);
    }
}
