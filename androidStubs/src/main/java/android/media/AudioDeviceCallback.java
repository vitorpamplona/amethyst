package android.media;

/**
 * JVM stand-in for android.media.AudioDeviceCallback.
 *
 * Devices do come and go on a desktop — headphones get plugged in — but the JDK
 * has no notification for it; {@code AudioSystem.getMixerInfo()} has to be
 * polled. Registration is therefore kept but never fires, and
 * {@link AudioManager#registerAudioDeviceCallback} says so once.
 */
public abstract class AudioDeviceCallback {
    public void onAudioDevicesAdded(AudioDeviceInfo[] addedDevices) {}

    public void onAudioDevicesRemoved(AudioDeviceInfo[] removedDevices) {}
}
