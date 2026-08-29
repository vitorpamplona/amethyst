package android.media;

import java.io.Closeable;
import java.io.IOException;

/** JVM stand-in for android.media.MediaDataSource: random-access bytes. */
public abstract class MediaDataSource implements Closeable {
    public abstract int readAt(long position, byte[] buffer, int offset, int size) throws IOException;

    public abstract long getSize() throws IOException;

    @Override
    public abstract void close() throws IOException;
}
