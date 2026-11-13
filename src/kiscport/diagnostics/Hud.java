package kiscport.diagnostics;

import javax.microedition.lcdui.Graphics;

/** No String/StringBuffer allocations in the draw path. */
public final class Hud {
    private final char[] digits = new char[24];
    public void number(Graphics g, long n, int x, int y) {
        int end = digits.length, start = end;
        boolean negative = n < 0;
        // Keep n negative so Long.MIN_VALUE is representable.
        if (n > 0) n = -n;
        do { digits[--start] = (char)('0' - (n % 10)); n /= 10; } while (n != 0);
        if (negative) digits[--start] = '-';
        g.drawChars(digits, start, end - start, x, y, Graphics.TOP | Graphics.LEFT);
    }
    public void stats(Graphics g, FrameStats s, int target, int width) {
        g.setColor(0); g.fillRect(0, 0, width, 48); g.setColor(0xffffff);
        g.drawString("FPS x10", 2, 0, 0); number(g, s.fps10, 65, 0);
        g.drawString("ms", 112, 0, 0); number(g, s.workMs, 134, 0);
        g.drawString("free KB", 2, 16, 0); number(g, s.freeBytes / 1024, 65, 16);
        g.drawString("target", 112, 16, 0); number(g, target, 165, 16);
        g.drawString("total KB", 2, 32, 0); number(g, s.totalBytes / 1024, 65, 32);
        g.drawString("max ms", 112, 32, 0); number(g, s.maxWorkMs, 175, 32);
    }
}
