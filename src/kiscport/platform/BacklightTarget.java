package kiscport.platform;

/** MIDP backlight access, independent of optional M3G. */
public interface BacklightTarget {
    boolean requestBacklight(int durationMs);
}
