package android.media;

/**
 * JVM stand-in for android.media.AudioFocusRequest.
 *
 * A faithful carrier for what the caller asked for. Whether the request is
 * granted is {@link AudioManager}'s answer, and on a desktop it always is —
 * see the note there.
 */
public final class AudioFocusRequest {
    private final int focusGain;
    private final AudioAttributes attributes;
    private final boolean acceptsDelayedFocusGain;
    private final boolean willPauseWhenDucked;
    private final AudioManager.OnAudioFocusChangeListener listener;

    private AudioFocusRequest(Builder builder) {
        this.focusGain = builder.focusGain;
        this.attributes = builder.attributes;
        this.acceptsDelayedFocusGain = builder.acceptsDelayedFocusGain;
        this.willPauseWhenDucked = builder.willPauseWhenDucked;
        this.listener = builder.listener;
    }

    public int getFocusGain() { return focusGain; }

    public AudioAttributes getAudioAttributes() { return attributes; }

    public boolean acceptsDelayedFocusGain() { return acceptsDelayedFocusGain; }

    public boolean willPauseWhenDucked() { return willPauseWhenDucked; }

    public AudioManager.OnAudioFocusChangeListener getOnAudioFocusChangeListener() { return listener; }

    public static final class Builder {
        private int focusGain;
        private AudioAttributes attributes;
        private boolean acceptsDelayedFocusGain;
        private boolean willPauseWhenDucked;
        private AudioManager.OnAudioFocusChangeListener listener;

        public Builder(int focusGain) { this.focusGain = focusGain; }

        public Builder setAudioAttributes(AudioAttributes value) {
            this.attributes = value;
            return this;
        }

        public Builder setAcceptsDelayedFocusGain(boolean value) {
            this.acceptsDelayedFocusGain = value;
            return this;
        }

        public Builder setWillPauseWhenDucked(boolean value) {
            this.willPauseWhenDucked = value;
            return this;
        }

        public Builder setOnAudioFocusChangeListener(AudioManager.OnAudioFocusChangeListener value) {
            this.listener = value;
            return this;
        }

        public AudioFocusRequest build() { return new AudioFocusRequest(this); }
    }
}
