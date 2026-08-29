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
    public static final int AUDIOFOCUS_LOSS = -1;
    public static final int AUDIOFOCUS_REQUEST_GRANTED = 1;
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
}
