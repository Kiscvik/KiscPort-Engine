package kiscport.platform;

/** Opt-in light request. No thread, timers or allocations in the frame path. */
public final class BacklightControl {
    public static final int DURATION_MS = 15000, RENEW_MS = 5000;
    private final BacklightTarget target;
    private boolean enabled, engaged, failed;
    private long lastRequest;
    private int result, requests, releases;

    public BacklightControl(BacklightTarget device) { target = device; }
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized int requestCount() { return requests; }
    public synchronized int releaseCount() { return releases; }
    public synchronized boolean isEngaged() { return engaged; }
    public synchronized void setEnabled(boolean value) {
        if (enabled == value) return;
        suspend(); enabled = value; failed = false; result = 0;
    }
    public synchronized void update(long now, boolean allowed) {
        if (!enabled || !allowed) { suspend(); return; }
        if (failed) return;
        if (engaged && now >= lastRequest && now - lastRequest < RENEW_MS) return;
        lastRequest = now; requests++;
        // Mark before the call so an exception still attempts cancellation.
        engaged = true;
        try {
            if (target.requestBacklight(DURATION_MS)) result = 1;
            else { suspend(); result = 2; failed = true; }
        } catch (Exception e) { suspend(); result = 3; failed = true; }
          catch (Error e) { suspend(); result = 3; failed = true; }
    }
    public synchronized void suspend() {
        if (!engaged) return;
        engaged = false; releases++;
        try { target.requestBacklight(0); }
        catch (Exception e) { result = 3; failed = true; }
        catch (Error e) { result = 3; failed = true; }
    }
    /** Explicitly stop a firmware effect even if this instance did not start it. */
    public synchronized void cancel() {
        enabled = false; engaged = false; releases++;
        try { target.requestBacklight(0); }
        catch (Exception e) { result = 3; failed = true; }
        catch (Error e) { result = 3; failed = true; }
    }
    public synchronized String status() {
        if (!enabled) return "Luz OFF";
        if (failed) return result == 2 ? "Luz rechazada" : "Luz error API";
        return engaged ? "Luz solicitada" : "Luz en espera";
    }
}
