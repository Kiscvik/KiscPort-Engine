package kiscport.resources;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;

/** Small KTT1 text resource. CLDC only; no game names, timing or rendering policy. */
public final class TextTable {
    private TextTable() { }

    /** Takes ownership of input, including on failure. Missing optional resource returns null. */
    public static String[] read(InputStream input, int expectedCount) throws IOException {
        if (input == null) return null;
        DataInputStream in = new DataInputStream(input);
        try {
            if (expectedCount < 1 || expectedCount > 32 || in.readInt() != 0x4b545431
                    || in.readUnsignedShort() != expectedCount) throw new IOException("Invalid text table");
            String[] result = new String[expectedCount];
            for (int i = 0; i < expectedCount; i++) {
                int length = in.readUnsignedShort();
                if (length > 256) throw new IOException("Text card too long");
                char[] chars = new char[length];
                for (int c = 0; c < length; c++) chars[c] = in.readChar();
                result[i] = new String(chars);
            }
            if (in.read() != -1) throw new IOException("Trailing text data");
            return result;
        } finally { in.close(); }
    }
}
