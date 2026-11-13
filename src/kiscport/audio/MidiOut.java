package kiscport.audio;

/** Live MIDI output for effects over the WAV music. The JSR-135 MIDIControl is not in the MIDP 2.0
 *  subset, so its only user (MidiControlOut) is loaded by name: without it the phone gets a
 *  NoClassDefFoundError that the caller catches, and the effect falls back to Manager.playTone. */
interface MidiOut {
    /** Open one reusable MIDI player; returns how ("device://midi" or the silent MIDI holder). */
    String open() throws Exception;
    /** shortMidiEvent(status, data1, data2) on the open player. */
    void event(int status, int data1, int data2);
    void close();
}
