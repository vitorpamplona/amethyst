package android.media;

/**
 * JVM stand-in for android.media.AudioManager.
 *
 * Only the call/audio-routing code reaches for this, and that feature is
 * expected to be gated off on desktop, so the constants exist for compilation
 * and the methods report a plain, unmuted, speaker-out device rather than
 * pretending to model routing the desktop does not do.
 */
public class AudioManager {
    public static final int STREAM_MUSIC = 3;
    public static final int STREAM_VOICE_CALL = 0;
    public static final int MODE_NORMAL = 0;
    public static final int MODE_IN_COMMUNICATION = 3;
    public static final int RINGER_MODE_SILENT = 0;
    public static final int RINGER_MODE_VIBRATE = 1;
    public static final int RINGER_MODE_NORMAL = 2;
    public static final int AUDIOFOCUS_GAIN = 1;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT = 2;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK = 3;
    public static final int AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE = 4;
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT = -2;
    public static final int AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK = -3;
    public static final int AUDIOFOCUS_REQUEST_FAILED = 0;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
    public static final int AUDIOFOCUS_REQUEST_DELAYED = 2;
    public static final int GET_DEVICES_OUTPUTS = 2;
    public static final int GET_DEVICES_INPUTS = 1;
    public static final int SCO_AUDIO_STATE_DISCONNECTED = 0;
    public static final int SCO_AUDIO_STATE_CONNECTED = 1;

    public int getMode() { return MODE_NORMAL; }

    public void setMode(int mode) {}

    public int getRingerMode() { return RINGER_MODE_NORMAL; }

    public boolean isSpeakerphoneOn() { return true; }

    public void setSpeakerphoneOn(boolean on) {}

    public boolean isBluetoothScoOn() { return false; }

    public void setBluetoothScoOn(boolean on) {}

    public void startBluetoothSco() {}

    public void stopBluetoothSco() {}

    public boolean isMicrophoneMute() { return false; }

    public void setMicrophoneMute(boolean on) {}

    public int getStreamVolume(int streamType) { return 0; }

    public int getStreamMaxVolume(int streamType) { return 0; }

    public Object[] getDevices(int flags) { return new Object[0]; }

    public interface OnAudioFocusChangeListener {
        void onAudioFocusChange(int focusChange);
    }

    /**
     * Always granted, and that is the true answer rather than a convenient one.
     * Audio focus is Android's arbitration between apps for the one speaker a
     * phone has: a call takes it away, a notification ducks it. Desktop systems
     * mix instead — an app that opens an output line owns it, nothing revokes
     * it, and there is no ducking protocol to take part in. So the caller
     * really can play, its focus really will not be lost, and the change
     * listener really has nothing to report.
     */
    public int requestAudioFocus(AudioFocusRequest request) { return AUDIOFOCUS_REQUEST_GRANTED; }

    public int requestAudioFocus(OnAudioFocusChangeListener listener, int streamType, int durationHint) {
        return AUDIOFOCUS_REQUEST_GRANTED;
    }

    public int abandonAudioFocusRequest(AudioFocusRequest request) { return AUDIOFOCUS_REQUEST_GRANTED; }

    public int abandonAudioFocus(OnAudioFocusChangeListener listener) { return AUDIOFOCUS_REQUEST_GRANTED; }

    /**
     * Kept, never fired. Headphones are plugged and unplugged on a desktop too,
     * but the JDK exposes no notification for it — the mixer list has to be
     * polled — so this is a gap with a real fix, not an absent feature.
     */
    public void registerAudioDeviceCallback(AudioDeviceCallback callback, android.os.Handler handler) {
        if (callback == null) return;
        deviceCallbacks.add(callback);
        com.vitorpamplona.amethyst.stubs.PlatformGaps.report(
                "AudioManager.registerAudioDeviceCallback",
                "the JDK has no audio-device hotplug notification; polling AudioSystem.getMixerInfo() "
                        + "would give the desktop the same signal. Until then a headset plugged in "
                        + "mid-session goes unnoticed.");
    }

    public void unregisterAudioDeviceCallback(AudioDeviceCallback callback) { deviceCallbacks.remove(callback); }

    /** Registered callbacks, so a future poller has somewhere to deliver to. */
    public java.util.List<AudioDeviceCallback> registeredDeviceCallbacks() {
        return java.util.Collections.unmodifiableList(deviceCallbacks);
    }

    private final java.util.List<AudioDeviceCallback> deviceCallbacks =
            new java.util.concurrent.CopyOnWriteArrayList<>();
}
