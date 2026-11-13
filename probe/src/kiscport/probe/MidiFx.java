package kiscport.probe;

import javax.microedition.media.Player;

/** MIDIControl access isolated behind an interface (JSR-135 full API, not in the MIDP 2.0 subset). */
interface MidiFx {
    /** MIDIControl of player p, or of a "device://midi" player when p is null. Returns a description. */
    String open(Player p) throws Exception;
    /** NOTE_ON on a channel (0-15); velocity 0 = note off. Returns the call time in ms. */
    int note(int channel, int key, int velocity);
    void program(int channel, int program);
    void close();
}
