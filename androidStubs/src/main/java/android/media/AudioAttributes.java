package android.media;

/**
 * JVM stand-in for android.media.AudioAttributes.
 *
 * Describes how audio should be routed and ducked. Desktop has no equivalent
 * routing model, so the values are carried but not acted on; the builder shape
 * is preserved so call sites compile unchanged.
 */
public final class AudioAttributes {
    public static final int USAGE_MEDIA = 1;
    public static final int USAGE_VOICE_COMMUNICATION = 2;
    public static final int USAGE_ALARM = 4;
    public static final int USAGE_NOTIFICATION = 5;
    public static final int USAGE_ASSISTANCE_ACCESSIBILITY = 11;

    public static final int CONTENT_TYPE_SPEECH = 1;
    public static final int CONTENT_TYPE_MUSIC = 2;
    public static final int CONTENT_TYPE_SONIFICATION = 4;

    private final int usage;
    private final int contentType;

    private AudioAttributes(int usage, int contentType) {
        this.usage = usage;
        this.contentType = contentType;
    }

    public int getUsage() { return usage; }

    public int getContentType() { return contentType; }

    public static final class Builder {
        private int usage = USAGE_MEDIA;
        private int contentType = CONTENT_TYPE_MUSIC;

        public Builder setUsage(int usage) {
            this.usage = usage;
            return this;
        }

        public Builder setContentType(int contentType) {
            this.contentType = contentType;
            return this;
        }

        public Builder setLegacyStreamType(int streamType) { return this; }

        public AudioAttributes build() { return new AudioAttributes(usage, contentType); }
    }
}
