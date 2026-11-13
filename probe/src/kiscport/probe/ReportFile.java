package kiscport.probe;

import java.io.OutputStream;
import java.util.Enumeration;
import javax.microedition.io.Connector;
import javax.microedition.io.file.FileConnection;
import javax.microedition.io.file.FileSystemRegistry;
import javax.microedition.lcdui.Image;

/** Writes the report (UTF-8 text) and captures (PNG) through JSR-75, in the first root that accepts them
 *  (memory card or phone memory). Loaded by name: without FileConnection the probe says so on screen. The
 *  phone may ask for permission (unsigned MIDlet). */
final class ReportFile implements Saver {
    public String save(String name, String text) throws Exception {
        final byte[] bytes = text.getBytes("UTF-8");
        return write(new String[] {name}, null, bytes);
    }

    public String savePng(String prefix, Image image) throws Exception {
        String[] names = new String[999];
        for (int i = 0; i < names.length; i++) names[i] = prefix + (i < 9 ? "00" : i < 99 ? "0" : "") + (i + 1) + ".png";
        return write(names, image, null);
    }

    /** First root that accepts a file; with several names, the first that does not exist yet. */
    private static String write(String[] names, Image image, byte[] bytes) throws Exception {
        String last = "sin raices de archivos";
        for (Enumeration e = FileSystemRegistry.listRoots(); e.hasMoreElements(); ) {
            String root = "file:///" + e.nextElement();
            for (int i = 0; i < names.length; i++) {
                String url = root + names[i];
                FileConnection file = null;
                try {
                    file = (FileConnection)Connector.open(url, Connector.READ_WRITE);
                    if (file.exists()) {
                        if (names.length > 1) continue;             // numbered capture: keep earlier ones
                        file.delete();
                    }
                    file.create();
                    OutputStream out = file.openOutputStream();
                    try {
                        if (image != null) PngWriter.write(image, out); else out.write(bytes);
                    } finally { out.close(); }
                    return url;
                } catch (Throwable x) { last = url + ": " + x; break; }   // this root refuses: try the next one
                finally { if (file != null) try { file.close(); } catch (Throwable x) { } }
            }
        }
        throw new Exception(last);
    }
}
