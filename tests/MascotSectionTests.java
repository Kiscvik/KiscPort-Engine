package kiscport.renderer;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.microedition.lcdui.Graphics;
import javax.microedition.lcdui.Image;
import javax.microedition.m3g.Appearance;
import org.recompile.mobile.Mobile;
import org.recompile.mobile.MobilePlatform;

/** FreeJ2ME-Plus (desktop only; its Mascot is a Java reimplementation, not the phone's). The synthetic level of
 *  tests/mascot_fixture.py, with stage_0.bmp from scripts/mascot.py texture, read by MascotSection (beta) and by the
 *  M3G reader AssetSection: same triangles; then chunk 0 drawn with Mascot: orientation and colour key. */
public final class MascotSectionTests {
    static final int BACKGROUND = 0x102030;
    static void check(boolean ok, String label) { if (!ok) throw new AssertionError(label); System.out.println("PASS: " + label); }

    public static void main(String[] a) {
        try { run(new File(a[0])); System.out.println("MASCOT SECTION TESTS DONE"); System.exit(0); } catch (Throwable t) { t.printStackTrace(); System.exit(1); }
    }

    static void run(File evidence) throws Exception {
        // As DemoSmoke: with another encoding FreeJ2ME restarts the process and exits 0 without running anything.
        Mobile.textEncoding = "ISO_8859_1";
        Mobile.setPlatform(new MobilePlatform(240, 320), new Runnable() { public void run() { } });
        MascotSection.ROOT = AssetSection.ROOT = "/mascot-fixture/";
        check(MascotSection.loadPages() == 1, "Mascot beta: one stage page loaded from stage_0.bmp");
        MascotSection s0 = new MascotSection(0), s1 = new MascotSection(1);
        check(s0.triangles() == 4 && s0.calls() == 1 && s1.triangles() == 800 && s1.calls() == 4
            && s1.bytes() == 800 * MascotSection.BYTES_PER_TRIANGLE, "Mascot beta: chunks expanded in calls of at most 255 triangles");
        for (int chunk = 0; chunk < 2; chunk++) {
            AssetSection m3g = new AssetSection(chunk, new Appearance[] {new Appearance()}, null, 0, 0, null);
            m3g.activate();
            check(m3g.triangles == (chunk == 0 ? s0 : s1).triangles(), "Mascot beta: chunk " + chunk + " has the same triangles as the M3G reader");
        }
        try { new MascotSection(7); check(false, "missing chunk"); }
        catch (java.io.IOException e) { check(true, "Mascot beta: a missing chunk is an IOException"); }

        Image image = Image.createImage(240, 320);
        Graphics g = image.getGraphics();
        g.setColor(BACKGROUND); g.fillRect(0, 0, 240, 320);
        // Identity turn, chunk 0 moved up 250 and 2000 in front: the full-page quad (y -250..250) above the centre.
        MascotSection.view(new int[] {4096, 0, 0, 0, 0, 4096, 0, -250, 0, 0, 4096, 2000}, 100, 10000,
            MascotSection.angle(240, 320, 50), 120, 160);
        MascotSection.begin(g);
        s0.draw();
        MascotSection.end(g);
        int[] rgb = new int[240 * 320];
        image.getRGB(rgb, 0, 240, 0, 0, 240, 320);
        BufferedImage out = new BufferedImage(240, 320, BufferedImage.TYPE_INT_RGB);
        out.setRGB(0, 0, 240, 320, rgb, 0, 240);
        evidence.mkdirs();
        ImageIO.write(out, "png", new File(evidence, "mascot-section.png"));
        // Bounds of the first quad: first row with something drawn, down to the first empty row.
        int y0 = -1, y1 = -1, x0 = 240, x1 = -1;
        for (int y = 0; y < 320 && y1 < 0; y++) {
            boolean any = false;
            for (int x = 0; x < 240; x++) if ((rgb[y * 240 + x] & 0xffffff) != BACKGROUND) { any = true; if (x < x0) x0 = x; if (x > x1) x1 = x; }
            if (any && y0 < 0) y0 = y;
            if (!any && y0 >= 0) y1 = y - 1;
        }
        check(y0 >= 0 && y1 > y0 + 20 && x1 > x0 + 40, "Mascot beta: chunk 0 drawn (" + (x1 - x0 + 1) + " x " + (y1 - y0 + 1) + " px)");
        int[] blue = texel(rgb, x0, x1, y0, y1, 100, 30), red = texel(rgb, x0, x1, y0, y1, 66, 3);
        int hole = texel(rgb, x0, x1, y0, y1, 122, 58)[3], gradient = texel(rgb, x0, x1, y0, y1, 12, 40)[3];
        check(near(blue, 40, 60, 220), "Mascot beta: right half of the page is blue " + hex(blue));
        check(near(red, 230, 30, 30), "Mascot beta: red mark top-left of the right half (no V or U flip) " + hex(red));
        check((hole & 0xffffff) == BACKGROUND, "Mascot beta: colour key: the transparent hole shows the background");
        check((gradient & 0xffffff) != BACKGROUND && !near(split(gradient), 40, 60, 220), "Mascot beta: left half has the gradient");
    }

    /** Screen pixel at the centre of texel (u, v) of the 128 x 64 page, given the quad's bounds: {r, g, b, rgb}. */
    static int[] texel(int[] rgb, int x0, int x1, int y0, int y1, int u, int v) {
        int x = x0 + (2 * u + 1) * (x1 - x0 + 1) / 256, y = y0 + (2 * v + 1) * (y1 - y0 + 1) / 128;
        return split(rgb[y * 240 + x]);
    }
    static int[] split(int p) { return new int[] {(p >> 16) & 255, (p >> 8) & 255, p & 255, p}; }
    static boolean near(int[] c, int r, int g, int b) { return Math.abs(c[0] - r) <= 12 && Math.abs(c[1] - g) <= 12 && Math.abs(c[2] - b) <= 12; }
    static String hex(int[] c) { return Integer.toHexString(c[3] & 0xffffff); }
}
