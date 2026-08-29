package android.media;

import java.nio.ByteBuffer;

/** JVM stand-in for android.media.MediaMuxer. See MediaCodecStubs. */
public final class MediaMuxer {
    public static final class OutputFormat {
        public static final int MUXER_OUTPUT_MPEG_4 = 0;
        public static final int MUXER_OUTPUT_WEBM = 1;
    }

    public MediaMuxer(String path, int format) { MediaCodecStubs.unsupported("MediaMuxer"); }

    public int addTrack(MediaFormat format) { return 0; }

    public void setOrientationHint(int degrees) {}

    public void start() {}

    public void writeSampleData(int trackIndex, ByteBuffer buffer, MediaCodec.BufferInfo info) {}

    public void stop() {}

    public void release() {}
}
