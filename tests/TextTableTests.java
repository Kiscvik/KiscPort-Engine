import java.io.*;
import kiscport.resources.TextTable;

/** Host-only tests of the packaged CLDC reader with original synthetic text. */
public final class TextTableTests {
    static final class Stream extends ByteArrayInputStream {
        boolean closed;
        Stream(byte[] data) { super(data); }
        public void close() { closed = true; }
    }
    static void check(boolean value) { if (!value) throw new AssertionError(); }
    static byte[] table(String[] cards) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeInt(0x4b545431); out.writeShort(cards.length);
        for (int i = 0; i < cards.length; i++) {
            out.writeShort(cards[i].length()); out.writeChars(cards[i]);
        }
        return bytes.toByteArray();
    }
    static void rejected(byte[] data, int count) throws Exception {
        Stream stream = new Stream(data);
        try { TextTable.read(stream, count); throw new AssertionError("accepted malformed table"); }
        catch (IOException expected) { check(stream.closed); }
    }
    public static void main(String[] args) throws Exception {
        String[] cards = {"Synthetic card", "\u00a1Texto de prueba!", "", "\u0000\ud83d\ude80"};
        byte[] valid = table(cards);
        Stream stream = new Stream(valid);
        String[] actual = TextTable.read(stream, cards.length);
        check(stream.closed && actual.length == cards.length);
        for (int i = 0; i < cards.length; i++) check(cards[i].equals(actual[i]));
        check(TextTable.read(null, 8) == null);
        for (int n = 0; n < valid.length; n++) {
            byte[] truncated = new byte[n]; System.arraycopy(valid, 0, truncated, 0, n);
            rejected(truncated, cards.length);
        }
        rejected(valid, cards.length + 1); rejected(valid, 0); rejected(valid, 33);
        byte[] badMagic = (byte[])valid.clone(); badMagic[0] = 0; rejected(badMagic, cards.length);
        byte[] trailing = new byte[valid.length + 1]; System.arraycopy(valid, 0, trailing, 0, valid.length);
        rejected(trailing, cards.length);
        rejected(table(new String[] {new String(new char[257])}), 1);
        Stream maximum = new Stream(table(new String[] {new String(new char[256])}));
        check(TextTable.read(maximum, 1)[0].length() == 256 && maximum.closed);
        // Exercise the PC encoder output (synthetic fixture passed by scripts/test.py).
        String[] packed = TextTable.read(new FileInputStream(args[0]), 8);
        for (int i = 0; i < packed.length; i++) check(packed[i].equals("Fixture " + i + " \u00f1\ud83d\ude80"));
        System.out.println("TextTableTests passed: Unicode, encoder interoperability, limits, truncation, closure, absent resource");
    }
}
