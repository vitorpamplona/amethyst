package android.media;

/**
 * JVM stand-in for android.media.MediaCodecInfo.
 *
 * Only the profile/level constants are used here, and those are the values
 * written into an AAC bitstream's header — not a device capability, so they are
 * the same numbers everywhere.
 */
public final class MediaCodecInfo {
    private MediaCodecInfo() {}

    public static final class CodecProfileLevel {
        private CodecProfileLevel() {}

        public static final int AACObjectMain = 1;
        public static final int AACObjectLC = 2;
        public static final int AACObjectSSR = 3;
        public static final int AACObjectLTP = 4;
        public static final int AACObjectHE = 5;
        public static final int AACObjectScalable = 6;
        public static final int AACObjectERLC = 17;
        public static final int AACObjectLD = 23;
        public static final int AACObjectHE_PS = 29;
        public static final int AACObjectELD = 39;

        public static final int AVCProfileBaseline = 0x01;
        public static final int AVCProfileMain = 0x02;
        public static final int AVCProfileHigh = 0x08;

        public int profile;
        public int level;
    }

    public static final class CodecCapabilities {
        public static final int COLOR_FormatYUV420Flexible = 0x7F420888;
        public static final int COLOR_FormatSurface = 0x7F000789;
    }
}
