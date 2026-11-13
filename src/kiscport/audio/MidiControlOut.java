package kiscport.audio;

import java.io.ByteArrayInputStream;
import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.MIDIControl;

/** The only class that references MIDIControl (compile-only stub, never packaged; scripts/audit.py).
 *  Same route as the MMAPI diagnostic test 6, heard over a WAV on the W900i:
 *  device://midi realized + prefetched, or a prefetched, never started, silent MIDI file. */
final class MidiControlOut implements MidiOut {
    /** SMF type 0, 96 ppq: tempo 500000 then end of track after one bar. No notes. */
    private static final byte[] SILENT = {0x4d, 0x54, 0x68, 0x64, 0, 0, 0, 6, 0, 0, 0, 1, 0, 0x60,
        0x4d, 0x54, 0x72, 0x6b, 0, 0, 0, 12, 0, (byte)0xff, 0x51, 3, 7, (byte)0xa1, 0x20,
        (byte)0x83, 0, (byte)0xff, 0x2f, 0};
    private Player player;
    private MIDIControl control;

    public String open() throws Exception {
        String how = "device://midi";
        Exception first = null;
        try { control = control(Manager.createPlayer("device://midi")); }
        catch (Exception e) { first = e; close(); }
        if (control == null) {
            how = "MIDI mudo";
            try { control = control(Manager.createPlayer(new ByteArrayInputStream(SILENT), "audio/midi")); }
            catch (Exception e) { close(); throw first != null ? first : e; }
        }
        return how;
    }

    private MIDIControl control(Player p) throws Exception {
        player = p;
        p.realize(); p.prefetch();
        MIDIControl c = (MIDIControl)p.getControl("javax.microedition.media.control.MIDIControl");
        if (c == null) c = (MIDIControl)p.getControl("MIDIControl");
        if (c == null) throw new Exception("sin MIDIControl");
        return c;
    }

    public void event(int status, int data1, int data2) { control.shortMidiEvent(status, data1, data2); }

    public void close() {
        control = null;
        if (player != null) { try { player.close(); } catch (Throwable e) { } player = null; }
    }
}
