package android.media;

import java.nio.ByteBuffer;

/** JVM stand-in for android.media.MediaCodec. See MediaCodecStubs for why this reports rather than works. */
public final class MediaCodec {
    public static final int BUFFER_FLAG_CODEC_CONFIG = 2;
    public static final int BUFFER_FLAG_END_OF_STREAM = 4;
    public static final int BUFFER_FLAG_KEY_FRAME = 1;
    public static final int INFO_TRY_AGAIN_LATER = -1;
    public static final int INFO_OUTPUT_FORMAT_CHANGED = -2;
    public static final int INFO_OUTPUT_BUFFERS_CHANGED = -3;
    public static final int CONFIGURE_FLAG_ENCODE = 1;

    public static final class BufferInfo {
        public int offset;
        public int size;
        public long presentationTimeUs;
        public int flags;

        public void set(int offset, int size, long presentationTimeUs, int flags) {
            this.offset = offset;
            this.size = size;
            this.presentationTimeUs = presentationTimeUs;
            this.flags = flags;
        }
    }

    public static class CodecException extends IllegalStateException {
        public CodecException(String message) { super(message); }
    }

    private MediaCodec() {}

    public static MediaCodec createEncoderByType(String mime) {
        MediaCodecStubs.unsupported("MediaCodec.createEncoderByType");
        return new MediaCodec();
    }

    public static MediaCodec createDecoderByType(String mime) {
        MediaCodecStubs.unsupported("MediaCodec.createDecoderByType");
        return new MediaCodec();
    }

    public void configure(MediaFormat format, Object surface, Object crypto, int flags) {}

    public void start() {}

    public void stop() {}

    public void flush() {}

    public void release() {}

    public MediaFormat getOutputFormat() { return new MediaFormat(); }

    public ByteBuffer getInputBuffer(int index) { return null; }

    public ByteBuffer getOutputBuffer(int index) { return null; }

    public int dequeueInputBuffer(long timeoutUs) { return INFO_TRY_AGAIN_LATER; }

    public int dequeueOutputBuffer(BufferInfo info, long timeoutUs) { return INFO_TRY_AGAIN_LATER; }

    public void queueInputBuffer(int index, int offset, int size, long presentationTimeUs, int flags) {}

    public void releaseOutputBuffer(int index, boolean render) {}
}
