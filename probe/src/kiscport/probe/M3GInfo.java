package kiscport.probe;

import java.util.Enumeration;
import java.util.Hashtable;
import javax.microedition.m3g.Graphics3D;

/** JSR-184 properties, vendor keys included, sorted. */
final class M3GInfo implements Info {
    public String[] lines() {
        Hashtable p = Graphics3D.getProperties();
        String[] out = new String[p.size()];
        int n = 0;
        for (Enumeration e = p.keys(); e.hasMoreElements(); ) { Object k = e.nextElement(); out[n++] = k + "=" + p.get(k); }
        ProbeCanvas.sort(out, n);
        return out;
    }
}
