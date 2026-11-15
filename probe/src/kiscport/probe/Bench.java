package kiscport.probe;

import javax.microedition.lcdui.Display;

/** Performance tests that need an optional API (M3G, Mascot Capsule v3). Loaded by name, so a phone without it only
 *  fails inside the implementing class. The implementation takes the screen while it runs and calls
 *  ProbeCanvas.benchDone with its result lines when it ends. */
interface Bench {
    /** test: ProbeCanvas.BENCH_* id; quick: short phases (emulator smoke only, never a phone result). */
    void start(ProbeCanvas owner, Display display, int test, boolean quick);
}
