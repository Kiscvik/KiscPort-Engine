package kiscport.probe;

import javax.microedition.lcdui.Image;

/** Optional saving to the phone's files (JSR-75), loaded by name like Info and Bench. Each method returns
 *  where it wrote. Captures are of KiscPort Probe's own screens: MIDP cannot read other apps' pixels. */
interface Saver {
    String save(String name, String text) throws Exception;
    /** prefix + first free number (001-999) + ".png". */
    String savePng(String prefix, Image image) throws Exception;
}
