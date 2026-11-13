import kiscport.diagnostics.FrameStats;

/** Synthetic time tests of the exact measurement class packaged for CLDC. */
public final class FrameStatsTests {
    private static int passed;
    private static void check(boolean ok, String label) {
        if (!ok) throw new AssertionError(label);
        passed++; System.out.println("PASS: " + label);
    }
    private static long window(FrameStats s, long now, int fps) {
        for (int i = 1; i <= fps; i++) s.frame(now + i * 1000 / fps, 3);
        return now + 1000;
    }
    public static void main(String[] args) {
        FrameStats s = new FrameStats(); s.reset(0);
        check(s.samples == 0 && s.windows == 0 && s.freeBytes <= s.totalBytes, "empty measurement and real memory sample");
        long now = window(s, 0, 20);
        check(s.fps10 == 200 && s.minFps10 == 200 && s.medianFps10 == 200 && s.maxFps10 == 200, "20 FPS known clock");
        now = window(s, now, 30);
        check(s.medianFps10 == 250 && s.samples == 2, "even median uses both central windows");
        now = window(s, now, 10);
        check(s.minFps10 == 100 && s.medianFps10 == 200 && s.maxFps10 == 300, "unsorted samples produce exact median and range");
        check(s.activeMs == 3000 && s.maxIntervalMs == 100 && s.maxWorkMs == 3, "active time, frame interval and render work are distinct");
        s.rebase(9000); now = window(s, 9000, 20);
        check(s.activeMs == 4000 && s.windows == 4 && s.maxIntervalMs == 100, "suspension excluded without erasing completed results");
        s.frame(1, 50);
        check(s.windows == 4 && s.activeMs == 4000, "backward clock discards partial window");
        s.reset(0); now = 0;
        now = window(s, now, 1);
        for (int i = 0; i < 600; i++) now = window(s, now, 20);
        check(s.windows == 601 && s.samples == 600 && s.minFps10 == 200 && s.medianFps10 == 200, "rolling 600 windows evict oldest outlier");
        s.reset(now);
        check(s.windows == 0 && s.minFps10 == 0 && s.maxWorkMs == 0 && s.activeMs == 0, "reset clears previous scene and target results");
        s.frame(now + 1200, 1200);
        check(s.fps10 == 8 && s.maxIntervalMs == 1200, "slow window is retained, not filtered out");
        System.out.println("FRAME STATS SUCCESS: " + passed + " checks");
    }
}
