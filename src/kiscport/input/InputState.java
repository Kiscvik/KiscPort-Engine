package kiscport.input;

/** Event thread writes; game thread atomically consumes held/edge masks. */
public final class InputState {
    public static final int LEFT = 1, RIGHT = 2, JUMP = 4, SLIDE = 8, FIRE = 16;
    private final int[] codes = new int[16];
    private final int[] actions = new int[16];
    private final int[] historyCodes = new int[8];
    private final int[] historyKinds = new int[8];
    private int count, held, edges, events, previousPolled, blockedPolled;
    private long pendingAt = -1;
    private int eventLagMs;
    public synchronized void press(int code, int action) {
        record(code, 1);
        for (int i = 0; i < count; i++) if (codes[i] == code) return;
        if (count == codes.length) return;
        codes[count] = code; actions[count++] = action;
        edges |= action & ~held;
        held |= action;
        if (action != 0 && pendingAt < 0) pendingAt = System.currentTimeMillis();
    }
    public synchronized void release(int code) {
        record(code, 0);
        for (int i = 0; i < count; i++) {
            if (codes[i] == code) {
                count--; codes[i] = codes[count]; actions[i] = actions[count]; break;
            }
        }
        held = 0;
        for (int i = 0; i < count; i++) held |= actions[i];
    }
    public synchronized void repeat(int code) { record(code, 2); }
    private void record(int code, int kind) {
        int slot = events++ & 7;
        historyCodes[slot] = code; historyKinds[slot] = kind;
    }
    public synchronized int consume() {
        int result = held | (edges << 8); edges = 0;
        if (pendingAt >= 0) {
            long age = System.currentTimeMillis() - pendingAt;
            eventLagMs = age < 0 ? 0 : age > 2147483647L ? 2147483647 : (int)age;
            pendingAt = -1;
        }
        return result;
    }
    /** Alternative gameplay source. Never OR with delayed callback edges. */
    public synchronized int consumePolled(int actions) {
        actions &= 31;
        blockedPolled &= actions;
        actions &= ~blockedPolled;
        int result = actions | ((actions & ~previousPolled) << 8);
        previousPolled = actions;
        return result;
    }
    /** Require release after pause/focus/source/scene change. */
    public synchronized void resetPolled(int actions) { previousPolled = 0; blockedPolled = actions & 31; }
    public synchronized int eventLagMs() { return eventLagMs; }
    public synchronized void clear() {
        count = 0; held = 0; edges = 0; previousPolled = 0; blockedPolled = 0; pendingAt = -1;
    }
    public synchronized int eventCount() { return events; }
    public synchronized int historyCode(int index) { return historyCodes[index & 7]; }
    public synchronized int historyKind(int index) { return historyKinds[index & 7]; }
}
