package kiscport.probe;

import javax.microedition.lcdui.Display;
import javax.microedition.midlet.MIDlet;

/** KiscPort Probe (KISC-LABS): platform, audio and M3G performance of a phone, for KiscPort ports. Successor of
 *  the MMAPI diagnostic 1.0.0. Separate MIDlet-Name: installs next to any game. Synthetic content only: sounds
 *  from scripts/make_probe_sounds.py, meshes and textures generated in M3GBench. */
public final class KiscPortProbe extends MIDlet {
    static final String VERSION = "1.2.0";
    private ProbeCanvas canvas;
    protected void startApp() {
        if (canvas == null) canvas = new ProbeCanvas(this);
        Display.getDisplay(this).setCurrent(canvas);
        canvas.resume();
    }
    protected void pauseApp() { if (canvas != null) canvas.interrupted("pauseApp"); }
    protected void destroyApp(boolean unconditional) { if (canvas != null) canvas.shutdown(); }
    void exit() { destroyApp(true); notifyDestroyed(); }
}
