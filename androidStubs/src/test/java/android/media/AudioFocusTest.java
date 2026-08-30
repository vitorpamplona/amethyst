package android.media;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The nest service starts its audio muted unless the focus request comes back
 * GRANTED, on purpose — the earlier Android code played over a live call by
 * treating anything-but-FAILED as granted. So the value this returns is what
 * decides whether a desktop listener hears anything at all.
 */
class AudioFocusTest {
    private final AudioManager manager = new AudioManager();

    @Test
    void focusIsGrantedBecauseNothingCanTakeItAway() {
        AudioFocusRequest request =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                        .setAudioAttributes(
                                new AudioAttributes.Builder()
                                        .setUsage(AudioAttributes.USAGE_MEDIA)
                                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                                        .build())
                        .setAcceptsDelayedFocusGain(false)
                        .build();

        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.requestAudioFocus(request));
        assertEquals(AudioManager.AUDIOFOCUS_REQUEST_GRANTED, manager.abandonAudioFocusRequest(request));
    }

    @Test
    void theRequestCarriesEveryFieldTheCallerSet() {
        AudioAttributes attributes =
                new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION).build();
        AudioManager.OnAudioFocusChangeListener listener = focusChange -> {};

        AudioFocusRequest request =
                new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                        .setAudioAttributes(attributes)
                        .setAcceptsDelayedFocusGain(false)
                        .setWillPauseWhenDucked(true)
                        .setOnAudioFocusChangeListener(listener)
                        .build();

        assertEquals(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, request.getFocusGain());
        assertSame(attributes, request.getAudioAttributes());
        assertFalse(request.acceptsDelayedFocusGain());
        assertTrue(request.willPauseWhenDucked());
        assertSame(listener, request.getOnAudioFocusChangeListener());
    }

    @Test
    void theFocusConstantsAreThePlatformsOwn() {
        // The service switches on these to decide muted / ducked / stopped, so
        // a wrong value silently maps a transient loss onto "keep playing".
        assertEquals(1, AudioManager.AUDIOFOCUS_GAIN);
        assertEquals(2, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        assertEquals(3, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK);
        assertEquals(4, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE);
        assertEquals(-1, AudioManager.AUDIOFOCUS_LOSS);
        assertEquals(-2, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT);
        assertEquals(-3, AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK);
        assertEquals(0, AudioManager.AUDIOFOCUS_REQUEST_FAILED);
        assertEquals(2, AudioManager.AUDIOFOCUS_REQUEST_DELAYED);
    }

    @Test
    void deviceCallbacksAreKeptSoAPollerCanDeliverToThemLater() {
        AudioDeviceCallback callback = new AudioDeviceCallback() {};
        manager.registerAudioDeviceCallback(callback, null);
        assertTrue(manager.registeredDeviceCallbacks().contains(callback));

        manager.unregisterAudioDeviceCallback(callback);
        assertFalse(manager.registeredDeviceCallbacks().contains(callback));
    }

    @Test
    void registeringANullCallbackIsIgnoredRatherThanStored() {
        int before = manager.registeredDeviceCallbacks().size();
        manager.registerAudioDeviceCallback(null, null);
        assertEquals(before, manager.registeredDeviceCallbacks().size());
    }
}
