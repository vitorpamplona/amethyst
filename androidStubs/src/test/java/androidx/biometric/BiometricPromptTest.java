package androidx.biometric;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The private-note lock. Failing open would unlock exactly what the prompt protects. */
class BiometricPromptTest {
    private boolean succeeded;
    private boolean errored;

    private final BiometricPrompt.AuthenticationCallback callback =
            new BiometricPrompt.AuthenticationCallback() {
                @Override public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                    succeeded = true;
                }

                @Override public void onAuthenticationError(int errorCode, CharSequence errString) {
                    errored = true;
                }
            };

    @AfterEach
    void reset() {
        BiometricPrompt.setPrompt(null);
    }

    private void authenticate() {
        new BiometricPrompt(null, Runnable::run, callback)
                .authenticate(new BiometricPrompt.PromptInfo.Builder().setTitle("Unlock").build());
    }

    @Test
    void failsClosedWithNoPromptInstalled() {
        authenticate();
        assertFalse(succeeded, "with no biometric prompt, content must STAY locked");
        assertTrue(errored);
    }

    @Test
    void aDeclinedPromptDoesNotUnlock() {
        BiometricPrompt.setPrompt((title, subtitle, negative) -> false);
        authenticate();
        assertFalse(succeeded);
        assertTrue(errored);
    }

    @Test
    void anAcceptedPromptUnlocks() {
        BiometricPrompt.setPrompt((title, subtitle, negative) -> true);
        authenticate();
        assertTrue(succeeded);
        assertFalse(errored);
    }

    @Test
    void availabilityReportsNoHardwareRatherThanSuccess() {
        assertEquals(BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE,
                BiometricManager.from(null).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG));

        BiometricPrompt.setPrompt((title, subtitle, negative) -> true);
        assertEquals(BiometricManager.BIOMETRIC_SUCCESS,
                BiometricManager.from(null).canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG));
    }
}
