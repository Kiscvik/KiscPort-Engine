package kiscport.probe;

import javax.microedition.media.Manager;
import javax.microedition.media.Player;
import javax.microedition.media.control.MIDIControl;

final class MidiImpl implements MidiFx {
    private MIDIControl control;
    private Player own;

    public String open(Player p) throws Exception {
        Player q = p;
        if (q == null) {
            own = Manager.createPlayer("device://midi");
            own.realize(); own.prefetch();
            q = own;
        }
        control = (MIDIControl)q.getControl("javax.microedition.media.control.MIDIControl");
        if (control == null) control = (MIDIControl)q.getControl("MIDIControl");
        if (control == null) throw new Exception("sin MIDIControl en " + (p == null ? "device://midi" : "MIDI"));
        String banks;
        try { banks = control.isBankQuerySupported() ? "consulta bancos SI" : "consulta bancos NO"; }
        catch (Throwable e) { banks = "consulta bancos " + e; }
        return (p == null ? "device://midi" : "Player MIDI") + ", " + banks + ", vol c9 " + control.getChannelVolume(9);
    }

    public int note(int channel, int key, int velocity) {
        long t = System.currentTimeMillis();
        control.shortMidiEvent(MIDIControl.NOTE_ON | channel, key, velocity);
        return (int)(System.currentTimeMillis() - t);
    }

    public void program(int channel, int program) { control.setProgram(channel, -1, program); }

    public void close() {
        control = null;
        if (own != null) { try { own.close(); } catch (Throwable e) { } own = null; }
    }
}
