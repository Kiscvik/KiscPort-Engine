package javax.microedition.io.file;

/** Compile-only stub of the JSR-75 signatures the probe uses; never packaged. */
public interface FileConnection extends javax.microedition.io.StreamConnection {
    boolean exists();
    void create() throws java.io.IOException;
    void delete() throws java.io.IOException;
}
