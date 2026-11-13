import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import javax.imageio.ImageIO;

/** PC only. Pinned host JDK; never compiled into the MIDlet. */
public final class PrepareIntro {
    public static void main(String[] args) throws Exception {
        BufferedImage original = ImageIO.read(new File(args[0]));
        if (original == null) throw new IllegalArgumentException("Unsupported original image");
        int x = Integer.parseInt(args[2]), y = Integer.parseInt(args[3]);
        int w = Integer.parseInt(args[4]), h = Integer.parseInt(args[5]);
        BufferedImage crop = original.getSubimage(x, y, w, h);
        // Fixed 256-colour palette tailored to the supplied silver / blue composite.
        // No alpha, interlacing or dithering. Deterministic nearest-colour mapping on PC.
        byte[] red = new byte[256], green = new byte[256], blue = new byte[256];
        for (int i = 0; i < 64; i++) {
            red[i] = green[i] = blue[i] = (byte)(i * 255 / 63);
            red[i + 64] = (byte)(i * 55 / 63);
            green[i + 64] = (byte)(i * 220 / 63);
            blue[i + 64] = (byte)(i * 255 / 63);
            red[i + 128] = (byte)(i * 190 / 63);
            green[i + 128] = (byte)(i * 205 / 63);
            blue[i + 128] = (byte)(i * 255 / 63);
            red[i + 192] = (byte)(i * 15 / 63);
            green[i + 192] = (byte)(i * 90 / 63);
            blue[i + 192] = (byte)(i * 255 / 63);
        }
        IndexColorModel palette = new IndexColorModel(8, 256, red, green, blue);
        int[] widths = {80, 128, 208, 304};
        for (int width : widths) {
            int height = Math.max(1, (h * width + w / 2) / w);
            BufferedImage scaled = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(crop, 0, 0, width, height, null); g.dispose();
            BufferedImage indexed = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED, palette);
            for (int py = 0; py < height; py++) for (int px = 0; px < width; px++) {
                int rgb = scaled.getRGB(px, py), r = (rgb >> 16) & 255, gr = (rgb >> 8) & 255, b = rgb & 255;
                int best = 0, distance = Integer.MAX_VALUE;
                for (int i = 0; i < 256; i++) {
                    int dr = r - (red[i] & 255), dg = gr - (green[i] & 255), db = b - (blue[i] & 255);
                    int d = dr * dr + dg * dg + db * db;
                    if (d < distance) { best = i; distance = d; }
                }
                indexed.getRaster().setSample(px, py, 0, best);
            }
            File out = new File(args[1], "kisc-labs-" + width + ".png");
            ImageIO.write(indexed, "png", out);
            System.out.println(out.getName() + " " + width + "x" + height + " " + out.length() + " bytes");
        }
    }
}
