package androidx.biometric;

import android.content.Context;

/**
 * JVM stand-in for androidx.biometric.BiometricManager.
 *
 * Desktop biometrics exist — Touch ID, Windows Hello — but reaching them needs
 * per-OS native calls, so availability comes from a {@link Prompt} the app
 * installs. With none installed the honest answer is "no hardware", which is a
 * state the app already handles by falling back to its passphrase path rather
 * than by unlocking.
 */
public final class BiometricManager {
    public static final int BIOMETRIC_SUCCESS = 0;
    public static final int BIOMETRIC_ERROR_HW_UNAVAILABLE = 1;
    public static final int BIOMETRIC_ERROR_NONE_ENROLLED = 11;
    public static final int BIOMETRIC_ERROR_NO_HARDWARE = 12;

    public static final class Authenticators {
        public static final int BIOMETRIC_STRONG = 0x000F;
        public static final int BIOMETRIC_WEAK = 0x00FF;
        public static final int DEVICE_CREDENTIAL = 1 << 15;

        private Authenticators() {}
    }

    private BiometricManager() {}

    public static BiometricManager from(Context context) { return new BiometricManager(); }

    public int canAuthenticate(int authenticators) {
        return BiometricPrompt.hasPrompt() ? BIOMETRIC_SUCCESS : BIOMETRIC_ERROR_NO_HARDWARE;
    }
}
