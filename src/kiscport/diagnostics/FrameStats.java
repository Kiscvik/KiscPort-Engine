package kiscport.diagnostics;

public final class FrameStats {
    public int fps10, workMs, maxWorkMs;
    public int minFps10, medianFps10, maxFps10, samples, windows, maxIntervalMs;
    public long freeBytes, totalBytes, minFreeBytes, activeMs;
    // Last 600 completed one-second windows; 4800 payload bytes, allocated once.
    private final int[] recent = new int[600], ordered = new int[600];
    private long window, previous;
    private int frames, cursor;
    public void reset(long now) {
        frames = 0; fps10 = 0; workMs = 0; maxWorkMs = 0;
        samples = 0; windows = 0; cursor = 0; maxIntervalMs = 0; activeMs = 0;
        minFps10 = 0; medianFps10 = 0; maxFps10 = 0;
        window = now; previous = now; sampleMemory(); minFreeBytes = freeBytes;
    }
    /** Discard a partial window across pause/focus changes, preserve completed results. */
    public void rebase(long now) { window = now; previous = now; frames = 0; }
    public void frame(long now, int work) {
        if (now < previous) { rebase(now); return; }
        long interval = now - previous;
        activeMs += interval;
        if (interval > maxIntervalMs) maxIntervalMs = interval > 2147483647L ? 2147483647 : (int)interval;
        previous = now;
        workMs = work;
        if (work > maxWorkMs) maxWorkMs = work;
        frames++;
        long span = now - window;
        if (span >= 1000) {
            fps10 = (int)(frames * 10000L / span);
            record(fps10);
            frames = 0; window = now; sampleMemory();
            if (freeBytes < minFreeBytes) minFreeBytes = freeBytes;
        }
    }
    private void record(int value) {
        if (samples == recent.length) {
            int old = recent[cursor], at = 0;
            while (ordered[at] != old) at++;
            for (int i = at; i < samples - 1; i++) ordered[i] = ordered[i + 1];
        } else samples++;
        recent[cursor] = value; cursor = (cursor + 1) % recent.length;
        int at = samples - 1;
        while (at > 0 && ordered[at - 1] > value) { ordered[at] = ordered[at - 1]; at--; }
        ordered[at] = value; windows++;
        minFps10 = ordered[0]; maxFps10 = ordered[samples - 1];
        medianFps10 = (ordered[(samples - 1) / 2] + ordered[samples / 2]) / 2;
    }
    public void sampleMemory() {
        Runtime rt = Runtime.getRuntime(); freeBytes = rt.freeMemory(); totalBytes = rt.totalMemory();
    }
}
