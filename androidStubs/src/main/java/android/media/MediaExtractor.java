package android.media;

import java.nio.ByteBuffer;

/** JVM stand-in for android.media.MediaExtractor. See MediaCodecStubs. */
public final class MediaExtractor {
    public static final int SEEK_TO_CLOSEST_SYNC = 2;

    public static final int SAMPLE_FLAG_SYNC = 1;
    public static final int SAMPLE_FLAG_ENCRYPTED = 2;
    public static final int SAMPLE_FLAG_PARTIAL_FRAME = 4;

    public MediaExtractor() { MediaCodecStubs.unsupported("MediaExtractor"); }

    public void setDataSource(String path) {}

    public void setDataSource(
            android.content.Context context,
            android.net.Uri uri,
            java.util.Map<String, String> headers) {}

    public int getTrackCount() { return 0; }

    public MediaFormat getTrackFormat(int index) { return new MediaFormat(); }

    public void selectTrack(int index) {}

    public void unselectTrack(int index) {}

    public int readSampleData(ByteBuffer buffer, int offset) { return -1; }

    public long getSampleTime() { return -1L; }

    public int getSampleFlags() { return 0; }

    public int getSampleTrackIndex() { return -1; }

    public boolean advance() { return false; }

    public void seekTo(long timeUs, int mode) {}

    public void release() {}
}
