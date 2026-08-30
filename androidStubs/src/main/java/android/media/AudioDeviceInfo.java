package android.media;

/**
 * JVM stand-in for android.media.AudioDeviceInfo.
 *
 * Only the call-routing code enumerates devices, and that feature is excluded
 * from the desktop build; the constants exist so the type resolves.
 */
public final class AudioDeviceInfo {
    public static final int TYPE_BUILTIN_EARPIECE = 1;
    public static final int TYPE_BUILTIN_SPEAKER = 2;
    public static final int TYPE_WIRED_HEADSET = 3;
    public static final int TYPE_WIRED_HEADPHONES = 4;
    public static final int TYPE_BLUETOOTH_SCO = 7;
    public static final int TYPE_BLUETOOTH_A2DP = 8;
    public static final int TYPE_USB_HEADSET = 22;
    public static final int TYPE_BLE_HEADSET = 26;
    public static final int TYPE_BLE_SPEAKER = 27;
    public static final int TYPE_BLE_BROADCAST = 30;

    public int getType() { return TYPE_BUILTIN_SPEAKER; }

    public CharSequence getProductName() { return "Default audio device"; }

    public boolean isSink() { return true; }

    public boolean isSource() { return false; }
}
