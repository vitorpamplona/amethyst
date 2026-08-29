package android.os;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

/**
 * JVM stand-in for android.os.ParcelFileDescriptor.
 *
 * Its one job here is handing an open file to the PDF renderer, and the JDK has
 * a real FileDescriptor for that, so this wraps one rather than faking it.
 */
public final class ParcelFileDescriptor implements AutoCloseable {
    public static final int MODE_READ_ONLY = 0x10000000;
    public static final int MODE_READ_WRITE = 0x30000000;

    private final FileInputStream stream;
    private final File file;

    private ParcelFileDescriptor(File file, FileInputStream stream) {
        this.file = file;
        this.stream = stream;
    }

    public static ParcelFileDescriptor open(File file, int mode) throws FileNotFoundException {
        return new ParcelFileDescriptor(file, new FileInputStream(file));
    }

    public java.io.FileDescriptor getFileDescriptor() throws java.io.IOException {
        return stream.getFD();
    }

    /** The underlying file, which is what a JVM renderer actually wants. */
    public File getFile() { return file; }

    public long getStatSize() { return file.length(); }

    @Override public void close() throws java.io.IOException { stream.close(); }
}
