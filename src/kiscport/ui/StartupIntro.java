package kiscport.ui;

import java.io.IOException;
import javax.microedition.lcdui.Font;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;

/** Engine-owned MIDP 2.0 startup card; its owner posts the optional laser cue. All scaling happens on the PC.
 *  The owner calls advance with the system clock and rebase after loss of focus.
 *  No game, M3G or MMAPI dependencies; no per-frame allocations. */
public final class StartupIntro {
    public static final int DURATION_MS = 2200;
    public static final String DEFAULT_PREFIX = "/kiscport/intro/kisc-labs";
    private static final int[] WIDTHS = {304, 208, 128, 80};
    private final Font font = Font.getFont(Font.FACE_SYSTEM, Font.STYLE_PLAIN, Font.SIZE_SMALL);
    private final String prefix;
    private Image logo;
    private long previous = -1;
    private int elapsed, layoutWidth = -1, layoutHeight = -1;
    private boolean done;
    public int loadMs, decodedBytes;
    public String loadError;

    public StartupIntro(String resourcePrefix, int width, int height) {
        prefix = resourcePrefix == null ? DEFAULT_PREFIX : resourcePrefix;
        layout(width, height);
        if (logo == null) done = true;
    }

    /** At most one decoded logo, including after an orientation change. */
    private void layout(int width, int height) {
        if (width == layoutWidth && height == layoutHeight) return;
        layoutWidth = width; layoutHeight = height;
        logo = null; decodedBytes = 0;
        long start = System.currentTimeMillis();
        for (int i = 0; i < WIDTHS.length; i++) {
            if (WIDTHS[i] > width - 16) continue;
            try {
                Image candidate = Image.createImage(prefix + "-" + WIDTHS[i] + ".png");
                if (candidate.getWidth() > width - 16 || candidate.getHeight() > height - 4 * font.getHeight() - 16) continue;
                logo = candidate;
                decodedBytes = logo.getWidth() * logo.getHeight() * 4; // conservative RGBA estimate, not a heap measurement
                break;
            } catch (IOException e) { loadError = e.toString(); }
              catch (RuntimeException e) { loadError = e.toString(); }
              catch (OutOfMemoryError e) { loadError = "Memoria insuficiente intro"; break; }
        }
        loadMs += (int)(System.currentTimeMillis() - start);
    }

    public void rebase(long now) { previous = now; }
    public void advance(long now, boolean skip) {
        if (done) return;
        if (previous >= 0 && now > previous) {
            long delta = now - previous;
            elapsed = delta >= DURATION_MS - elapsed ? DURATION_MS : elapsed + (int)delta;
        }
        previous = now;
        if (skip || elapsed >= DURATION_MS) done = true;
    }
    public boolean finished() { return done; }
    public int elapsedMs() { return elapsed; }
    public void dispose() { logo = null; done = true; }

    public void draw(Graphics g, int width, int height) {
        layout(width, height);
        if (logo == null) { done = true; return; }
        int cx = g.getClipX(), cy = g.getClipY(), cw = g.getClipWidth(), ch = g.getClipHeight();
        Font saved = g.getFont();
        try {
            g.setColor(0); g.fillRect(0, 0, width, height);
            int w = logo.getWidth(), h = logo.getHeight();
            int x = (width - w) / 2, y = (height - h - 3 * font.getHeight() - 10) / 2;
            int lineY = y + h + 4;
            if (elapsed < 400) {
                int span = w * elapsed / 400;
                g.setColor(0x0045ba); g.fillRect((width - span) / 2, lineY - 1, span, 3);
                g.setColor(0x85edff); g.drawLine((width - span) / 2, lineY, (width + span) / 2, lineY);
            } else {
                if (elapsed < 1100) {
                    // Six horizontal shutters reveal the SINGLE composite, never invented layers.
                    int reveal = (elapsed - 400) * w / 700;
                    for (int i = 0; i < 6; i++) {
                        int top = y + h * i / 6, bottom = y + h * (i + 1) / 6;
                        g.setClip(cx, cy, cw, ch);
                        g.clipRect((i & 1) == 0 ? x : x + w - reveal, top, reveal, bottom - top);
                        g.drawImage(logo, x, y, Graphics.TOP | Graphics.LEFT);
                    }
                    g.setClip(cx, cy, cw, ch);
                } else g.drawImage(logo, x, y, Graphics.TOP | Graphics.LEFT);
                g.setColor(0x006adc); g.fillRect(x, lineY, w, 1);
                if (elapsed >= 1100 && elapsed < 1800) {
                    int flare = x + (elapsed - 1100) * (w - 12) / 700;
                    g.setColor(0x0759b8); g.fillRect(flare - 4, lineY - 2, 20, 5);
                    g.setColor(0x39cfff); g.fillRect(flare, lineY - 1, 12, 3);
                    g.setColor(0xffffff); g.fillRect(flare + 4, lineY, 4, 1);
                }
            }
            g.setFont(font);
            if (elapsed >= 1100 && font.stringWidth("RETRO GAME DEVELOPMENT") <= width - 8) {
                g.setColor(0xb7c9de);
                g.drawString("RETRO GAME DEVELOPMENT", width / 2, lineY + 7, Graphics.TOP | Graphics.HCENTER);
            }
            if (elapsed >= 1800) {
                g.setColor(0xdce6ff);
                int textY = lineY + font.getHeight() + 10;
                if (font.stringWidth("Powered by KiscPort Engine") <= width - 8)
                    g.drawString("Powered by KiscPort Engine", width / 2, textY, Graphics.TOP | Graphics.HCENTER);
                else {
                    g.drawString("Powered by", width / 2, textY, Graphics.TOP | Graphics.HCENTER);
                    g.drawString("KiscPort Engine", width / 2, textY + font.getHeight(), Graphics.TOP | Graphics.HCENTER);
                }
            }
        } finally {
            g.setClip(cx, cy, cw, ch); g.setFont(saved);
        }
    }
}
