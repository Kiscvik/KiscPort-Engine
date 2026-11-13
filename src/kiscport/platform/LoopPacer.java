package kiscport.platform;

/** A slow frame must still let the VM schedule LCDUI/input and lifecycle work. */
public final class LoopPacer {
    private LoopPacer() { }
    public static long restMs(int targetFps, long workMs) {
        if (workMs < 0) workMs = 0;
        long rest = 1000 / targetFps - workMs;
        return rest > 0 ? rest : 1;
    }
}
