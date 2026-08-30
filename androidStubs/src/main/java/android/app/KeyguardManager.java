package android.app;

import android.content.Intent;
import com.vitorpamplona.amethyst.stubs.PlatformGaps;

/**
 * JVM stand-in for android.app.KeyguardManager.
 *
 * The app asks this before raising a zap limit: "is the device secured, and if
 * so make the user prove it". Desktop OSes do have a credential prompt (polkit,
 * Windows Hello, macOS's LocalAuthentication), but nothing in the JDK reaches
 * one, so this reports **not secure** — which is the safe direction. The caller
 * then treats the device as unprotected and takes its own no-confirmation path
 * rather than believing a confirmation happened.
 *
 * {@link #createConfirmDeviceCredentialIntent} returning null is the platform's
 * own contract for "there is no credential to confirm", and the app already
 * handles it.
 */
public class KeyguardManager {
    /**
     * False, deliberately. Claiming a secure device would let a caller believe
     * a credential prompt is available and skip its own guard; claiming
     * insecure only loses a confirmation step that this build cannot show.
     */
    public boolean isDeviceSecure() {
        PlatformGaps.report(
                "KeyguardManager.isDeviceSecure",
                "desktop credential prompts exist (polkit, Windows Hello, LocalAuthentication) but the "
                        + "JDK reaches none of them, so the device reports as unsecured and confirmation "
                        + "flows take their no-credential path.");
        return false;
    }

    public boolean isKeyguardSecure() { return isDeviceSecure(); }

    public boolean isKeyguardLocked() { return false; }

    /** Null: there is no credential to confirm. Same contract as the platform's. */
    public Intent createConfirmDeviceCredentialIntent(CharSequence title, CharSequence description) { return null; }
}
