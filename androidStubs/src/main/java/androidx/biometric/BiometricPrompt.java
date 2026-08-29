package androidx.biometric;

import com.vitorpamplona.amethyst.stubs.PlatformGaps;
import java.util.concurrent.Executor;

/**
 * JVM stand-in for androidx.biometric.BiometricPrompt.
 *
 * Gates the private-note lock. Failing open would unlock the very thing the
 * prompt protects, so with no desktop prompt installed this calls
 * onAuthenticationError rather than onAuthenticationSucceeded — the app then
 * keeps the content locked, which is the safe direction.
 */
public final class BiometricPrompt {
    public static final int ERROR_HW_NOT_PRESENT = 12;
    public static final int ERROR_NEGATIVE_BUTTON = 13;
    public static final int ERROR_USER_CANCELED = 10;

    /** Installed by the desktop app; Touch ID, Windows Hello, or a passphrase dialog. */
    public interface Prompt {
        /** Returns true when the user authenticated. */
        boolean authenticate(String title, String subtitle, String negativeButtonText);
    }

    private static volatile Prompt prompt;

    public static void setPrompt(Prompt value) { prompt = value; }

    static boolean hasPrompt() { return prompt != null; }

    public abstract static class AuthenticationCallback {
        public void onAuthenticationSucceeded(AuthenticationResult result) {}

        public void onAuthenticationError(int errorCode, CharSequence errString) {}

        public void onAuthenticationFailed() {}
    }

    public static final class AuthenticationResult {
        public int getAuthenticationType() { return 0; }
    }

    public static final class PromptInfo {
        private final String title;
        private final String subtitle;
        private final String negativeButtonText;

        private PromptInfo(String title, String subtitle, String negativeButtonText) {
            this.title = title;
            this.subtitle = subtitle;
            this.negativeButtonText = negativeButtonText;
        }

        public static final class Builder {
            private String title = "";
            private String subtitle = "";
            private String negativeButtonText = "Cancel";

            public Builder setTitle(String value) {
                title = value;
                return this;
            }

            public Builder setSubtitle(String value) {
                subtitle = value;
                return this;
            }

            public Builder setDescription(String value) { return this; }

            public Builder setNegativeButtonText(String value) {
                negativeButtonText = value;
                return this;
            }

            public Builder setAllowedAuthenticators(int authenticators) { return this; }

            public Builder setConfirmationRequired(boolean required) { return this; }

            public PromptInfo build() { return new PromptInfo(title, subtitle, negativeButtonText); }
        }
    }

    private final AuthenticationCallback callback;

    public BiometricPrompt(Object activity, Executor executor, AuthenticationCallback callback) {
        this.callback = callback;
    }

    public void authenticate(PromptInfo info) {
        Prompt installed = prompt;
        if (installed == null) {
            PlatformGaps.report(
                    "BiometricPrompt",
                    "no desktop biometric prompt installed; Touch ID and Windows Hello both exist but need native calls");
            callback.onAuthenticationError(ERROR_HW_NOT_PRESENT, "No biometric prompt on this platform");
            return;
        }
        if (installed.authenticate(info.title, info.subtitle, info.negativeButtonText)) {
            callback.onAuthenticationSucceeded(new AuthenticationResult());
        } else {
            callback.onAuthenticationError(ERROR_USER_CANCELED, "Cancelled");
        }
    }

    public void cancelAuthentication() {}
}
